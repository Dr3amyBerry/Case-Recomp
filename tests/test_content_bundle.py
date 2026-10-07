from __future__ import annotations

from hashlib import sha256
from pathlib import Path
import json
import tempfile
import unittest
import zipfile

from caserecomp.content_bundle import build_private_content_bundle, verify_private_content_bundle, validate_private_content_manifest
from caserecomp.inspector import InspectionError
from caserecomp.__main__ import main


SOURCE_HASH = "1" * 64

SCENARIO = {
    "format": "case-recomp-scenario", "version": 1, "id": "private-synthetic",
    "design": {"width": 64, "height": 48},
    "scenes": [{"id": "room", "frame_start": 1, "frame_end": 2,
                "targets": [{"id": "target", "rect": [1, 2, 10, 12], "z": 1}]}],
    "events": [],
}


def _manifest(root: Path, assets: list[tuple[str, bytes, str]]) -> None:
    rows = []
    total = 0
    for i, (rel, data, fmt) in enumerate(assets):
        path = root / rel; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        rows.append({"archive_index": 0, "resource_id": i + 1, "tag": "ediM", "format": fmt,
                     "file": rel, "bytes": len(data), "sha256": sha256(data).hexdigest()})
        total += len(data)
    doc = {"schema_version": 1,
           "source_archives": [{"index": 0, "name": "synthetic.dcr", "sha256": SOURCE_HASH,
                                "director_version": "synthetic", "kind": "movie"}],
           "asset_count": len(rows), "bytes": total,
           "assets": rows, "skipped": {}, "relationship_maps": [], "resource_tags": {}, "warnings": []}
    (root / "manifest.json").write_text(json.dumps(doc), encoding="utf-8")


class PrivateContentBundleTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.base = Path(self.tmp.name)
        self.export = self.base / "export"; self.export.mkdir()
        _manifest(self.export, [("archive-000/images/a.png", b"PNGDATA", "png"),
                                ("archive-000/audio/a.wav", b"WAVDATA", "pcm-wav"),
                                ("archive-000/raw/x.bin", b"RAW", "opaque")])
        self.scenario = self.base / "scenario.json"; self.scenario.write_text(json.dumps(SCENARIO), encoding="utf-8")
    def tearDown(self): self.tmp.cleanup()

    def test_bundle_round_trip_and_determinism(self):
        bindings = self.base / "bindings.json"
        bindings.write_text(json.dumps({"format":"case-recomp-private-bindings","version":1,
            "scene_backgrounds":{"room":"archive-000/images/a.png"},
            "targets":{"room":{"target":"archive-000/images/a.png"}},
            "audio":{"START":"archive-000/audio/a.wav"}}), encoding="utf-8")
        a=self.base/"a.crcontent"; b=self.base/"b.crcontent"
        first=build_private_content_bundle(self.export,self.scenario,a,bindings_path=bindings)
        second=build_private_content_bundle(self.export,self.scenario,b,bindings_path=bindings)
        self.assertEqual(first["package_id"], second["package_id"])
        self.assertEqual(a.read_bytes(), b.read_bytes())
        checked=verify_private_content_bundle(a)
        self.assertEqual(checked["asset_count"],2)
        with zipfile.ZipFile(a) as zf:
            names=set(zf.namelist()); self.assertIn("content-manifest.json",names); self.assertNotIn("archive-000/raw/x.bin",names)
            manifest=json.loads(zf.read("content-manifest.json"))
            self.assertEqual(manifest["bindings"]["scene_backgrounds"]["room"], manifest["assets"][1 if manifest["assets"][0]["media_type"].startswith("audio") else 0]["id"])

    def test_unbound_bundle_is_allowed_but_still_private_catalog(self):
        out=self.base/"x.crcontent"; result=build_private_content_bundle(self.export,self.scenario,out)
        self.assertEqual(result["asset_count"],2); self.assertFalse(result["trace_plan_included"])
        self.assertEqual(verify_private_content_bundle(out)["scenario_id"],"private-synthetic")

    def test_reject_binding_unknown_asset_and_wrong_media(self):
        for payload in [
            {"format":"case-recomp-private-bindings","version":1,"scene_backgrounds":{"room":"missing.png"}},
            {"format":"case-recomp-private-bindings","version":1,"scene_backgrounds":{"room":"archive-000/audio/a.wav"}},
        ]:
            path=self.base/"bad.json"; path.write_text(json.dumps(payload),encoding="utf-8")
            with self.assertRaises(InspectionError): build_private_content_bundle(self.export,self.scenario,self.base/"bad.zip",bindings_path=path)

    def test_verify_rejects_tampered_asset_and_zip_slip(self):
        out=self.base/"ok.zip"; build_private_content_bundle(self.export,self.scenario,out)
        with zipfile.ZipFile(out) as zf:
            entries={n:zf.read(n) for n in zf.namelist()}
        asset=next(n for n in entries if n.startswith("assets/")); entries[asset]=b"tampered"
        bad=self.base/"tampered.zip"
        with zipfile.ZipFile(bad,"w") as zf:
            for n,d in entries.items(): zf.writestr(n,d)
        with self.assertRaises(InspectionError): verify_private_content_bundle(bad)
        slip=self.base/"slip.zip"
        with zipfile.ZipFile(slip,"w") as zf: zf.writestr("../evil",b"x"); zf.writestr("content-manifest.json",b"{}")
        with self.assertRaises(InspectionError): verify_private_content_bundle(slip)

    def test_changed_conversion_asset_is_rejected(self):
        (self.export/"archive-000/images/a.png").write_bytes(b"changed")
        with self.assertRaises(InspectionError): build_private_content_bundle(self.export,self.scenario,self.base/"bad.zip")

    def test_requires_supported_assets_and_create_only(self):
        other=self.base/"raw";other.mkdir();_manifest(other,[("raw.bin",b"raw","opaque")])
        with self.assertRaises(InspectionError): build_private_content_bundle(other,self.scenario,self.base/"raw.zip")
        out=self.base/"exists.zip";out.write_bytes(b"x")
        with self.assertRaises(InspectionError): build_private_content_bundle(self.export,self.scenario,out)

    def test_trace_plan_is_included_and_cli_verifies(self):
        plan = self.base / "trace.json"
        plan.write_text(json.dumps({
            "format":"case-recomp-behavior-trace","version":1,"kind":"private-plan",
            "source_sha256":SOURCE_HASH,
            "steps":[{"frame":1,"sprite_count":0,"behavior_count":0,"script_count":0,
                      "sprite_sha256":"2"*64,"handler_set_sha256":"3"*64}],
            "observation_contract":{"required":["frame","sprite_count","sprite_sha256",
                                                   "handler_set_sha256","observable_state_sha256"],
                                    "optional":["input_kind","event_kind"]},
        }),encoding="utf-8")
        out=self.base/"trace.crcontent"
        result=build_private_content_bundle(self.export,self.scenario,out,trace_plan_path=plan)
        self.assertTrue(result["trace_plan_included"])
        self.assertTrue(verify_private_content_bundle(out)["trace_plan_included"])
        self.assertEqual(main(["private-content-verify",str(out)]),0)
        cli=self.base/"cli.crcontent"
        self.assertEqual(main(["private-content-package",str(self.export),str(self.scenario),"--output",str(cli)]),0)
        self.assertTrue(cli.is_file())

    def test_trace_plan_must_match_conversion_source_and_exact_contract(self):
        def write_plan(path: Path, source: str = SOURCE_HASH, *, extra: bool = False) -> None:
            doc = {
                "format":"case-recomp-behavior-trace","version":1,"kind":"private-plan",
                "source_sha256":source,
                "steps":[{"frame":1,"sprite_count":0,"behavior_count":0,"script_count":0,
                          "sprite_sha256":"2"*64,"handler_set_sha256":"3"*64}],
                "observation_contract":{"required":["frame","sprite_count","sprite_sha256",
                                                       "handler_set_sha256","observable_state_sha256"],
                                        "optional":["input_kind","event_kind"]},
            }
            if extra:
                doc["unexpected"] = True
            path.write_text(json.dumps(doc), encoding="utf-8")

        wrong = self.base/"wrong-source.json"; write_plan(wrong, "9"*64)
        with self.assertRaises(InspectionError):
            build_private_content_bundle(self.export,self.scenario,self.base/"wrong-source.crcontent",trace_plan_path=wrong)

        extra = self.base/"extra-plan.json"; write_plan(extra, extra=True)
        with self.assertRaises(InspectionError):
            build_private_content_bundle(self.export,self.scenario,self.base/"extra-plan.crcontent",trace_plan_path=extra)

        manifest = json.loads((self.export/"manifest.json").read_text(encoding="utf-8"))
        manifest["source_archives"] = []
        (self.export/"manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        matching = self.base/"matching.json"; write_plan(matching)
        with self.assertRaises(InspectionError):
            build_private_content_bundle(self.export,self.scenario,self.base/"no-provenance.crcontent",trace_plan_path=matching)

    def test_more_binding_fail_closed_cases(self):
        payloads = [
            {"format":"case-recomp-private-bindings","version":1,"scene_backgrounds":{"missing":"archive-000/images/a.png"}},
            {"format":"case-recomp-private-bindings","version":1,"targets":{"room":{"missing":"archive-000/images/a.png"}}},
            {"format":"case-recomp-private-bindings","version":1,"audio":{"BOOM":"archive-000/audio/a.wav"}},
            {"format":"case-recomp-private-bindings","version":1,"audio":{"START":"archive-000/images/a.png"}},
            {"format":"case-recomp-private-bindings","version":1,"extra":{}},
        ]
        for i,payload in enumerate(payloads):
            path=self.base/f"binding-{i}.json"; path.write_text(json.dumps(payload),encoding="utf-8")
            with self.assertRaises(InspectionError):
                build_private_content_bundle(self.export,self.scenario,self.base/f"binding-{i}.zip",bindings_path=path)

    def test_manifest_validation_rejects_bad_catalog_and_package_id(self):
        out=self.base/"ok2.zip"; build_private_content_bundle(self.export,self.scenario,out)
        with zipfile.ZipFile(out) as zf: manifest=json.loads(zf.read("content-manifest.json"))
        bad=dict(manifest); bad["package_id"]="0"*64
        with self.assertRaises(InspectionError): validate_private_content_manifest(bad)
        bad=json.loads(json.dumps(manifest)); bad["assets"][0]["id"]="sha256-"+"0"*64
        with self.assertRaises(InspectionError): validate_private_content_manifest(bad)
        bad=json.loads(json.dumps(manifest)); bad["assets"][0]["media_type"]="application/octet-stream"
        with self.assertRaises(InspectionError): validate_private_content_manifest(bad)

    def test_verify_rejects_bad_manifest_scenario_and_duplicate_names(self):
        out=self.base/"good.zip"; build_private_content_bundle(self.export,self.scenario,out)
        with zipfile.ZipFile(out) as zf: entries={n:zf.read(n) for n in zf.namelist()}
        bad_json=self.base/"badjson.zip"
        with zipfile.ZipFile(bad_json,"w") as zf:
            for n,d in entries.items(): zf.writestr(n,b"{" if n=="content-manifest.json" else d)
        with self.assertRaises(InspectionError): verify_private_content_bundle(bad_json)
        bad_scenario=self.base/"badscenario.zip"
        with zipfile.ZipFile(bad_scenario,"w") as zf:
            for n,d in entries.items(): zf.writestr(n,b"{}" if n=="scenario.json" else d)
        with self.assertRaises(InspectionError): verify_private_content_bundle(bad_scenario)
        duplicate=self.base/"duplicate.zip"
        with zipfile.ZipFile(duplicate,"w") as zf:
            for n,d in entries.items(): zf.writestr(n,d)
            zf.writestr("scenario.json",entries["scenario.json"])
        with self.assertRaises(InspectionError): verify_private_content_bundle(duplicate)


if __name__ == "__main__": unittest.main()
