"""Synthetic Director 8.5 Lscr/CASt fixtures for the compiled-Lingo bundle reader."""
import base64
import json
import struct
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from caserecomp.__main__ import main
from caserecomp.inspector import InspectionError
from caserecomp.lingo_bytecode import (_extended_to_float, build_lingo_bundle, parse_lscr, parse_script_member)

NAMES = ("me", "count", "gGame", "tick", "total", "pName")
CODE = bytes([0x41, 0x05, 0x01])  # pushInt8 5; ret


def extended(value: float) -> bytes:
    """Encode a positive finite float as an 80-bit big-endian extended value."""
    import math
    mantissa, exponent = math.frexp(value)          # value = mantissa * 2**exponent, 0.5 <= mantissa < 1
    return struct.pack(">HQ", exponent - 1 + 16383, int(mantissa * 2 ** 64))


def lscr(script_number=6, literals=None, name_id=3) -> bytes:
    literals = literals if literals is not None else [(4, 7, b""), (1, 0, b"hi\0"), (9, 0, struct.pack(">d", 1.5)),
                                                       (9, 0, extended(2.5))]
    body = bytearray(92)
    struct.pack_into(">H", body, 18, script_number)
    def table(values):
        offset = len(body); body.extend(b"".join(struct.pack(">h", v) for v in values)); return offset
    props = table([5]); globs = table([2]); args = table([0, 1]); locs = table([4])
    code_offset = len(body); body.extend(CODE)
    handler_offset = len(body)
    body.extend(struct.pack(">hHIIHIHIHIIHHII", name_id, 0, len(CODE), code_offset, 2, args, 1, locs, 0, 0, 0, 0, 0, 0, 0))
    data = bytearray(); records = []
    for kind, value, raw in literals:
        if kind == 4:
            records.append((kind, value))
        else:
            records.append((kind, len(data))); data.extend(struct.pack(">I", len(raw)) + raw)
    lit_offset = len(body); body.extend(b"".join(struct.pack(">II", k, v) for k, v in records))
    lit_data = len(body); body.extend(data)
    struct.pack_into(">HI", body, 60, 1, props); struct.pack_into(">HI", body, 66, 1, globs)
    struct.pack_into(">HI", body, 72, 1, handler_offset); struct.pack_into(">HI", body, 78, len(records), lit_offset)
    struct.pack_into(">II", body, 84, len(data), lit_data)
    return bytes(body)


def script_member(name=b"game manager", script_number=7, script_type=7, member_type=11) -> bytes:
    items = b"" + bytes([len(name)]) + name           # item 0 (source text) empty, item 1 = Pascal name
    table = struct.pack(">H", 2) + struct.pack(">II", 0, 0) + struct.pack(">I", len(items)) + items
    info = bytearray(20); struct.pack_into(">I", info, 0, 20); struct.pack_into(">i", info, 16, script_number)
    info += table
    specific = struct.pack(">H", script_type)
    return struct.pack(">iii", member_type, len(info), len(specific)) + bytes(info) + specific


class LscrTests(unittest.TestCase):
    def test_parse_names_literals_and_bytecode(self):
        script = parse_lscr(lscr(), NAMES)
        self.assertEqual(script["script_number"], 6)
        self.assertEqual((script["properties"], script["globals"]), (["pName"], ["gGame"]))
        self.assertEqual(script["literals"], [{"type": "int", "value": 7}, {"type": "string", "value": "hi"},
                                              {"type": "float", "value": 1.5}, {"type": "float", "value": 2.5}])
        handler = script["handlers"][0]
        self.assertEqual((handler["name"], handler["arguments"], handler["locals"], handler["globals"]),
                         ("tick", ["me", "count"], ["total"], []))
        self.assertEqual(base64.b64decode(handler["bytecode"]), CODE)

    def test_string_literals_use_the_mac_character_set(self):
        script = parse_lscr(lscr(literals=[(1, 0, b"Peque\x96o N\xbc1\0")]), NAMES)
        self.assertEqual(script["literals"], [{"type": "string", "value": "Pequeño Nº1"}])

    def test_negative_int_and_zero_extended(self):
        script = parse_lscr(lscr(literals=[(4, 0xFFFFFFFF, b""), (9, 0, bytes(10))]), NAMES)
        self.assertEqual(script["literals"], [{"type": "int", "value": -1}, {"type": "float", "value": 0.0}])
        self.assertEqual(_extended_to_float(extended(1000.0)), 1000.0)

    def test_rejects_malformed_scripts(self):
        bad = [
            lscr()[:50],
            lscr(name_id=40),
            lscr(literals=[(2, 0, b"")]),
            lscr(literals=[(9, 0, b"abc")]),
            lscr(literals=[(9, 0, b"\x7f\xff" + bytes(8))]),
        ]
        truncated = bytearray(lscr()); struct.pack_into(">I", truncated, 88, len(truncated) + 9); bad.append(bytes(truncated))
        table = bytearray(lscr()); struct.pack_into(">HI", table, 72, 2000, 92); bad.append(bytes(table))
        props = bytearray(lscr()); struct.pack_into(">HI", props, 60, 1, len(props)); bad.append(bytes(props))
        lits = bytearray(lscr()); struct.pack_into(">HI", lits, 78, 900, 92); bad.append(bytes(lits))
        for data in bad:
            with self.assertRaises(InspectionError):
                parse_lscr(data, NAMES)


class MemberTests(unittest.TestCase):
    def test_script_member_metadata(self):
        self.assertEqual(parse_script_member(script_member()),
                         {"name": "game manager", "script_number": 7, "script_type": "parent"})
        self.assertIsNone(parse_script_member(script_member(member_type=1)))

    def test_rejects_malformed_members(self):
        for data in (b"x", script_member(script_type=9), script_member()[:-1] + b""):
            with self.assertRaises(InspectionError):
                parse_script_member(data)


class BundleTests(unittest.TestCase):
    def archive(self, *, kind="movie", extra=None):
        resources = {
            1: struct.pack(">II", 0, 0) + struct.pack(">HH", 20, len(NAMES)) + b"".join(bytes([len(n)]) + n.encode() for n in NAMES),
            2: None, 3: struct.pack(">I", 300), 300: script_member(), 400: lscr(),
        }
        tags = {1: "Lnam", 2: "LctX", 3: "CAS*", 300: "CASt", 400: "Lscr", **(extra or {})}
        return SimpleNamespace(kind=kind, entries={rid: SimpleNamespace(tag=t) for rid, t in tags.items()},
                               get_resource=lambda rid: resources[rid])

    def test_bundle_links_scripts_to_members(self):
        with patch("caserecomp.lingo_bytecode.open_archive", return_value=(self.archive(), 0)), \
                patch("caserecomp.lingo_bytecode.read_lnam", return_value=NAMES), \
                patch("caserecomp.lingo_bytecode.read_lctx", return_value=(1, (400,))), \
                patch("caserecomp.lingo_bytecode.read_local", return_value=b"movie"):
            bundle = build_lingo_bundle(Path("movie.dir"))
        self.assertEqual(bundle["format"], "case-recomp-lingo-bundle")
        self.assertEqual(bundle["names"], list(NAMES))
        script = bundle["scripts"][0]
        self.assertEqual(script["member"], {"number": 1, "name": "game manager", "script_type": "parent"})
        self.assertEqual(script["context_index"], 1)

    def test_bundle_rejects_casts_and_ambiguous_tables(self):
        for archive in (self.archive(kind="cast"), self.archive(extra={5: "Lnam"})):
            with patch("caserecomp.lingo_bytecode.open_archive", return_value=(archive, 0)), \
                    self.assertRaises(InspectionError):
                build_lingo_bundle(Path("movie.dir"))

    def test_cli_writes_create_only_bundle(self):
        bundle = {"format": "case-recomp-lingo-bundle", "version": 1, "scripts": []}
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "movie.dir"; source.write_bytes(b"x")
            out = Path(tmp) / "bundle.json"
            with patch("caserecomp.lingo_bytecode.build_lingo_bundle", return_value=bundle):
                self.assertEqual(main(["lingo-bundle", str(source), "--output", str(out)]), 0)
                self.assertEqual(json.loads(out.read_text(encoding="utf-8")), bundle)
                self.assertEqual(main(["lingo-bundle", str(source), "--output", str(out)]), 2)


if __name__ == "__main__":
    unittest.main()
