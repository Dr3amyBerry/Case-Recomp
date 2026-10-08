from __future__ import annotations
import struct
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from caserecomp.inspector import InspectionError
from caserecomp.score import (analyze_movie_structure, build_scene_segments, lingo_script_number, member_script_number,
                              parse_cast_member, parse_cast_order, parse_frame_labels,
                              parse_movie_config, parse_score)


def movie_config() -> bytes:
    data = bytearray(84)
    struct.pack_into(">HHhhhhhh", data, 0, 84, 0x73A, 5, 10, 605, 810, 1, 10)
    struct.pack_into(">h", data, 54, 30)
    struct.pack_into(">h", data, 56, 2)
    struct.pack_into(">hh", data, 76, -1, -3)
    return bytes(data)


def cast_member(member_type=1, script=7) -> bytes:
    info = bytearray(20); struct.pack_into(">i", info, 16, script)
    specific = bytes(24)
    return struct.pack(">iii", member_type, len(info), len(specific)) + info + specific


def labels() -> bytes:
    strings = b"first\x00second"
    return (struct.pack(">H", 2) + struct.pack(">HHHH", 1, 0, 2, 6) +
            struct.pack(">i", len(strings)) + strings)


def score_fixture() -> bytes:
    rec = bytearray(24)
    rec[0] = 1; rec[1] = 2
    struct.pack_into(">HH", rec, 4, 1, 2)
    struct.pack_into(">hh", rec, 12, 20, 10)
    struct.pack_into(">HH", rec, 16, 30, 40)
    rec[21] = 200; rec[22] = 0x20
    frame_header = struct.pack(">iiiHHHH", 0, 0, 2, 14, 24, 8, 0)
    f0body = struct.pack(">HH", 24, 6 * 24) + bytes(rec)
    frame_data = frame_header + struct.pack(">H", len(f0body) + 2) + f0body + struct.pack(">H", 2)
    primary = struct.pack(">iiiiiHiHiiii", 1, 2, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0)
    secondary = struct.pack(">HHi", 1, 2, 4)
    entries = [frame_data, b"", primary, secondary, b"[x:1]"]
    offsets = [0]
    for e in entries: offsets.append(offsets[-1] + len(e))
    header = struct.pack(">6i", 0, -3, 12, len(entries), len(entries) + 1, offsets[-1])
    return header + struct.pack(f">{len(offsets)}i", *offsets) + b"".join(entries)


class ScoreTests(unittest.TestCase):
    def test_config_cast_and_script_tables(self):
        cfg = parse_movie_config(movie_config())
        self.assertEqual((cfg.width, cfg.height, cfg.tempo, cfg.platform), (800, 600, 30, 2))
        self.assertEqual((cfg.default_palette_cast, cfg.default_palette_member), (-1, -3))
        member = parse_cast_member(99, cast_member())
        self.assertEqual((member.resource_id, member.member_type, member.script_number), (99, 1, 7))
        self.assertEqual(parse_cast_order(struct.pack(">III", 10, 0, 30)), (10, 0, 30))
        lscr = bytearray(20); struct.pack_into(">H", lscr, 18, 42)
        self.assertEqual(lingo_script_number(bytes(lscr)), 42)
        self.assertEqual(member_script_number(bytes(lscr)), 43)

    def test_labels_are_hashed_not_exposed(self):
        result = parse_frame_labels(labels())
        self.assertEqual([x.frame for x in result], [1, 2])
        self.assertTrue(all(len(x.label_digest) == 64 for x in result))
        self.assertNotIn("first", repr(result))

    def test_score_sprites_spans_behaviors_and_segments(self):
        score = parse_score(score_fixture())
        self.assertEqual((score.frame_count, score.channel_count, score.displayed_channels), (2, 8, 8))
        self.assertEqual(score.sprite_record_size, 24)
        self.assertEqual(len(score.sprites), 2)
        self.assertEqual(len(score.sprite_spans), 1)
        span = score.sprite_spans[0]
        self.assertEqual((span.start_frame, span.end_frame, span.state.channel), (1, 2, 6))
        self.assertTrue(span.state.flip_h)
        self.assertEqual(len(score.behaviors), 1)
        behavior = score.behaviors[0]
        self.assertEqual((behavior.start_frame, behavior.end_frame, behavior.channel), (1, 2, 6))
        self.assertEqual((behavior.cast_lib, behavior.cast_member, behavior.parameter_entry), (1, 2, 4))
        segments = build_scene_segments(score, parse_frame_labels(labels()))
        self.assertEqual([(x.start_frame, x.end_frame) for x in segments], [(1, 1), (2, 2)])
        self.assertTrue(all(x.active_sprite_spans == 1 for x in segments))

    def test_legacy_displayed_channels(self):
        score = parse_score(score_fixture())
        object.__setattr__(score, "frames_version", 13); object.__setattr__(score, "channel_count", 1006)
        self.assertEqual(score.displayed_channels, 120)
        object.__setattr__(score, "frames_version", 7)
        self.assertEqual(score.displayed_channels, 48)

    def test_director7_records_use_every_declared_channel(self):
        # Frames version 13 with 48-byte records: a sprite past the old 120-channel cap.
        rec = bytearray(48); rec[0] = 1
        struct.pack_into(">HH", rec, 4, 1, 9)
        struct.pack_into(">hh", rec, 12, 5, 7)
        struct.pack_into(">HH", rec, 16, 3, 4)
        channels, channel = 300, 280
        frame_header = struct.pack(">iiiHHHH", 0, 0, 1, 13, 48, channels, 0)
        body = struct.pack(">HH", 48, channel * 48) + bytes(rec)
        frame_data = frame_header + struct.pack(">H", len(body) + 2) + body + struct.pack(">H", 2)
        entries = [frame_data, b""]
        offsets = [0, len(frame_data), len(frame_data)]
        header = struct.pack(">6i", 0, -3, 12, len(entries), len(entries) + 1, offsets[-1])
        score = parse_score(header + struct.pack(">3i", *offsets) + frame_data)
        self.assertEqual(score.displayed_channels, channels)
        self.assertEqual([(s.channel, s.cast_member, s.x, s.y) for s in score.sprites], [(channel, 9, 7, 5)])

    def test_director7_sprite_colours(self):
        from caserecomp.score import _channel_state
        record = bytearray(48)
        record[0], record[1] = 16, 8
        record[2], record[3] = 0x20, 0xFC  # red bytes of foreground and background
        record[5], record[7] = 1, 3  # cast lib 1, member 3
        struct.pack_into(">hhHH", record, 12, 10, 20, 5, 6)
        record[20] = 0x30  # both colours are RGB
        record[24], record[25], record[26], record[27] = 0x28, 0xB4, 0x4B, 0x29
        state = _channel_state(bytes(record), 0, 48, 1, 6)
        self.assertEqual((state.fore_color, state.back_color), ("#20284b", "#fcb429"))
        record[20] = 0
        state = _channel_state(bytes(record), 0, 48, 1, 6)
        self.assertEqual((state.fore_color, state.back_color), (0x20, 0xFC))

    def test_strict_failures(self):
        cases = [
            lambda: parse_movie_config(b"x" * 57),
            lambda: parse_movie_config(bytes(84)),
            lambda: parse_cast_member(1, b"x"),
            lambda: parse_cast_member(1, struct.pack(">iii", 1, -1, 0)),
            lambda: lingo_script_number(b"x"),
            lambda: parse_cast_order(b""),
            lambda: parse_cast_order(b"abc"),
            lambda: parse_frame_labels(b""),
            lambda: parse_score(b"x" * 10),
        ]
        for fn in cases:
            with self.subTest(fn=fn):
                with self.assertRaises(InspectionError): fn()

    def test_bad_label_and_score_offsets(self):
        bad = bytearray(labels()); struct.pack_into(">H", bad, 4, 999)
        with self.assertRaises(InspectionError): parse_frame_labels(bytes(bad))
        bad = bytearray(score_fixture()); struct.pack_into(">i", bad, 24, 1)
        with self.assertRaises(InspectionError): parse_score(bytes(bad))

    def test_private_analyzer_uses_cas_logical_mapping(self):
        resources = {
            1: movie_config(),
            2: score_fixture(),
            3: labels(),
            4: struct.pack(">II", 0, 200),
            200: cast_member(member_type=11, script=7),
        }
        lscr = bytearray(20); struct.pack_into(">H", lscr, 18, 6); resources[300] = bytes(lscr)  # member script 7 -> Lscr 6
        tags = {1: "DRCF", 2: "VWSC", 3: "VWLB", 4: "CAS*", 200: "CASt", 300: "Lscr"}
        fake = SimpleNamespace(
            kind="movie",
            entries={rid: SimpleNamespace(tag=tag) for rid, tag in tags.items()},
            get_resource=lambda rid: resources[rid],
        )
        fake_index = {"script_count": 1, "handler_count": 2, "unique_handler_names": 2}
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "movie.bin"; source.write_bytes(b"synthetic")
            with patch("caserecomp.score.open_archive", return_value=(fake, 0)), \
                 patch("caserecomp.score.index_archive", return_value=fake_index):
                report = analyze_movie_structure(source)
        self.assertEqual(report["stage"]["width"], 800)
        self.assertEqual(report["score"]["anonymous_marker_segments"], 2)
        self.assertEqual(report["cast"]["logical_slots"], 2)
        self.assertEqual(report["lingo"]["score_behavior_refs_resolved_to_script"], 1)
        self.assertEqual(report["lingo"]["score_behavior_script_resources"], 1)
        self.assertEqual(report["lingo"]["handlers"], 2)

    def test_analyzer_rejects_non_movie_and_missing_singletons(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "x"; source.write_bytes(b"x")
            fake = SimpleNamespace(kind="cast", entries={}, get_resource=lambda rid: b"")
            with patch("caserecomp.score.open_archive", return_value=(fake, 0)):
                with self.assertRaises(InspectionError): analyze_movie_structure(source)
            fake.kind = "movie"
            with patch("caserecomp.score.open_archive", return_value=(fake, 0)):
                with self.assertRaises(InspectionError): analyze_movie_structure(source)


if __name__ == "__main__": unittest.main()
