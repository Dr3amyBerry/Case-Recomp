"""Entirely synthetic KEY* association and conversion tests."""

import struct
import unittest
from dataclasses import dataclass

from caserecomp.inspector import InspectionError
from caserecomp.relationships import CastRelationships


@dataclass
class StubResource:
    id: int
    tag: str


class StubArchive:
    def __init__(self, records, *, capacity=None, declared_size=None, header=(12, 12)):
        self.entries = {
            3: StubResource(3, "KEY*"),
            5: StubResource(5, "CASt"),
            7: StubResource(7, "ediM"),
            8: StubResource(8, "ALFA"),
            11: StubResource(11, "CASt"),
            12: StubResource(12, "BITD"),
        }
        if capacity is None:
            capacity = len(records)
        content = struct.pack("<HHII", *header, capacity, len(records))
        content += b"".join(struct.pack("<II4s", ident, owner, tag[::-1].encode("ascii")) for ident, owner, tag in records)
        content += bytes(max(0, capacity - len(records)) * 12)
        if declared_size is not None:
            content = content[:declared_size]
        self.payload = content

    def get_resource(self, ident):
        if ident != 3:
            raise AssertionError(ident)
        return self.payload


class KeyTableTests(unittest.TestCase):
    def setUp(self):
        self.archive = StubArchive([(7, 5, "ediM"), (8, 5, "ALFA"), (31, 5, "Thum"),
                                    (12, 11, "BITD")], capacity=6)
        self.key = CastRelationships(self.archive)

    def test_allocated_and_used_differ(self):
        self.assertEqual((self.key.capacity, self.key.used), (6, 4))
        self.assertEqual(len(self.key.records), 4)
        self.assertTrue(self.key.records[0].present)
        self.assertFalse(self.key.records[2].present)
        self.assertEqual(self.key.summary(self.archive)["unavailable_references"], 1)

    def test_owner_and_resource_lookups(self):
        self.assertEqual(self.key.resources_for(5, "ediM"), (7,))
        self.assertEqual(self.key.resources_for(5), (7, 8))
        self.assertEqual(self.key.owners_of(8), (5,))
        self.assertEqual(self.key.owners_of(31), ())
        self.assertEqual(self.key.resources_for(500), ())

    def test_image_alpha_pair_kept_as_metadata(self):
        self.assertEqual(self.key.image_alpha_links(self.archive), {7: {
            "cast_member_id": 5, "alpha_resource_id": 8, "alpha_applied": False,
            "association_method": "KEY*-cast-owner"}})
        result = self.key.summary(self.archive)
        self.assertEqual(result["images_with_alpha_reference"], 1)
        self.assertEqual(result["images_with_cast_owner"], 1)
        self.assertEqual(result["linked_cast_members"], 2)
        self.assertEqual(result["reference_tags"]["Thum"], 1)

    def test_no_alpha_or_multiple_alpha_is_not_guessed(self):
        record = StubArchive([(7, 5, "ediM")])
        self.assertIsNone(CastRelationships(record).image_alpha_links(record)[7]["alpha_resource_id"])
        record = StubArchive([(7, 5, "ediM"), (8, 5, "ALFA"), (9, 5, "ALFA")])
        record.entries[9] = StubResource(9, "ALFA")
        self.assertIsNone(CastRelationships(record).image_alpha_links(record)[7]["alpha_resource_id"])

    def test_unknown_owner_not_a_cast_skipped(self):
        record = StubArchive([(7, 999, "ediM"), (8, 999, "ALFA")])
        self.assertEqual(CastRelationships(record).image_alpha_links(record), {})

    def test_missing_key_table(self):
        record = StubArchive([])
        del record.entries[3]
        with self.assertRaisesRegex(InspectionError, "KEY"):
            CastRelationships(record)

    def test_duplicate_key_tables(self):
        record = StubArchive([])
        record.entries[4] = StubResource(4, "KEY*")
        with self.assertRaisesRegex(InspectionError, "KEY"):
            CastRelationships(record)

    def test_bad_key_header_and_truncation(self):
        with self.assertRaisesRegex(InspectionError, "truncated"):
            CastRelationships(StubArchive([], declared_size=6))
        with self.assertRaisesRegex(InspectionError, "layout"):
            CastRelationships(StubArchive([], header=(10, 12)))
        with self.assertRaisesRegex(InspectionError, "size"):
            CastRelationships(StubArchive([(7, 5, "ediM")], declared_size=15))

    def test_invalid_key_count(self):
        record = StubArchive([(7, 5, "ediM")])
        record.payload = record.payload[:8] + struct.pack("<I", 99) + record.payload[12:]
        with self.assertRaisesRegex(InspectionError, "count"):
            CastRelationships(record)
        record.payload = record.payload[:4] + struct.pack("<I", 90000) + record.payload[8:]
        with self.assertRaisesRegex(InspectionError, "count"):
            CastRelationships(record)

    def test_invalid_fourcc_and_duplicate(self):
        record = StubArchive([(7, 5, "ediM"), (7, 5, "ediM")])
        with self.assertRaisesRegex(InspectionError, "duplicate"):
            CastRelationships(record)
        record = StubArchive([(7, 5, "ediM")])
        record.payload = record.payload[:-4] + b"\x00\x00\x00\x00"
        with self.assertRaisesRegex(InspectionError, "tag"):
            CastRelationships(record)

    def test_refuses_inconsistent_existing_resource_tag(self):
        record = StubArchive([(7, 5, "ALFA")])
        with self.assertRaisesRegex(InspectionError, "contradicts"):
            CastRelationships(record)

    def test_shared_alpha_is_ambiguous(self):
        record = StubArchive([(7, 5, "ediM"), (8, 5, "ALFA"), (8, 11, "ALFA")])
        self.assertIsNone(CastRelationships(record).image_alpha_links(record)[7]["alpha_resource_id"])

    def test_multiple_owners_same_resource(self):
        record = StubArchive([(7, 5, "ediM"), (7, 11, "ediM")])
        key = CastRelationships(record)
        self.assertEqual(key.owners_of(7), (5, 11))
        self.assertEqual(key.image_alpha_links(record), {})


class RelationshipIntegrationTests(unittest.TestCase):
    def test_cli_links_and_conversion_manifest_on_synthetic_cast(self):
        from contextlib import redirect_stdout
        from io import StringIO
        from pathlib import Path
        from tempfile import TemporaryDirectory
        from caserecomp.__main__ import main
        from caserecomp.pipeline import convert_local, verify_export
        from tests.test_pipeline import media_cast, generate_jpeg
        import json

        with TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "synthetic.cct"
            source.write_bytes(media_cast(
                assets=[("ediM", generate_jpeg(), 1), ("ALFA", b"UNDECODED MASK", 1),
                        ("CASt", b"SYNTHETIC CAST METADATA", 1)],
                keys=[(100, 102, "ediM"), (101, 102, "ALFA")]))
            captured = StringIO()
            with redirect_stdout(captured):
                self.assertEqual(main(["director-map", str(source), "--links"]), 0)
            stats = json.loads(captured.getvalue())
            self.assertEqual(stats["relationships"]["images_with_alpha_reference"], 1)
            self.assertEqual(stats["relationships"]["used_records"], 2)

            output = root / "decoded"
            manifest = convert_local(source, output, image_format="png", include_raw=True)
            image = next(a for a in manifest["assets"] if a["tag"] == "ediM")
            self.assertEqual((image["cast_member_id"], image["alpha_resource_id"]), (102, 101))
            self.assertFalse(image["alpha_applied"])
            self.assertEqual(image["association_method"], "KEY*-cast-owner")
            self.assertEqual(manifest["relationship_maps"][0]["status"], "parsed")
            mask = next(a for a in manifest["assets"] if a["tag"] == "ALFA")
            self.assertEqual((output / mask["file"]).read_bytes(), b"UNDECODED MASK")
            self.assertEqual(verify_export(output)["verified_files"], manifest["asset_count"])

    def test_invalid_key_map_degrades_explicitly_without_faking_links(self):
        from tempfile import TemporaryDirectory
        from pathlib import Path
        from caserecomp.pipeline import convert_local
        from tests.test_pipeline import media_cast
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            archive = root / "sample.cct"
            archive.write_bytes(media_cast())
            manifest = convert_local(archive, root / "output")
            self.assertEqual(manifest["relationship_maps"][0]["status"], "unavailable")
            self.assertNotIn("cast_member_id", manifest["assets"][0])


if __name__ == "__main__":
    unittest.main()
