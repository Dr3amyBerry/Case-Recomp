import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "tools" / "phase8_evidence.py"


class Phase8EvidenceTest(unittest.TestCase):
    def make_apk(self, path: Path, payload: bytes) -> str:
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("payload.bin", payload)
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def fixture(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        candidate = root / "candidate.apk"
        baseline = root / "baseline.apk"
        candidate_sha = self.make_apk(candidate, b"candidate")
        self.make_apk(baseline, b"baseline")
        return root, candidate, baseline, candidate_sha

    def command(self, root, candidate, baseline, candidate_sha):
        return [
            sys.executable, str(SCRIPT),
            "--apk", str(candidate),
            "--baseline-apk", str(baseline),
            "--output", str(root / "evidence"),
            "--git-sha", "a" * 40,
            "--commit-timestamp", "2026-10-07T13:43:50-04:00",
            "--first-build-sha", candidate_sha,
        ]

    def test_provenance_and_sbom_identifiers_are_semantically_consistent(self):
        root, candidate, baseline, candidate_sha = self.fixture()
        result = subprocess.run(
            self.command(root, candidate, baseline, candidate_sha),
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        output = root / "evidence"
        provenance = json.loads((output / "provenance.json").read_text())
        sbom = json.loads((output / "sbom.cdx.json").read_text())
        self.assertEqual({"sha1": "a" * 40}, provenance["materials"][0]["digest"])
        self.assertEqual(candidate_sha, provenance["artifact"]["sha256"])
        component = sbom["metadata"]["component"]
        self.assertEqual(candidate_sha, component["hashes"][0]["content"])
        self.assertIn("@" + component["version"] + "?type=apk", component["bom-ref"])

    def test_reproducibility_mismatch_fails_closed(self):
        root, candidate, baseline, candidate_sha = self.fixture()
        command = self.command(root, candidate, baseline, candidate_sha)
        command[-1] = "0" * 64
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("not byte reproducible", result.stderr)
        report = json.loads((root / "evidence" / "build-report.json").read_text())
        self.assertFalse(report["reproducible_same_revision"])

    def test_first_build_hash_is_mandatory(self):
        root, candidate, baseline, candidate_sha = self.fixture()
        command = self.command(root, candidate, baseline, candidate_sha)[:-2]
        result = subprocess.run(command, text=True, capture_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("--first-build-sha", result.stderr)


if __name__ == "__main__":
    unittest.main()
