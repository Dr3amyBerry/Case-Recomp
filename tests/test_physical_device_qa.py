import os
import shutil
import subprocess
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "tools" / "android_physical_qa.sh"


def _working_bash() -> str | None:
    """Find a bash that runs; on Windows PATH may resolve to the WSL stub without a distro."""
    candidates = [shutil.which("bash")]
    if os.name == "nt":
        git = shutil.which("git")
        if git:
            candidates.append(str(Path(git).resolve().parents[1] / "bin" / "bash.exe"))
    for candidate in filter(None, candidates):
        try:
            probe = subprocess.run([candidate, "-c", "exit 0"], capture_output=True, timeout=30)
        except (OSError, subprocess.TimeoutExpired):
            continue
        if probe.returncode == 0:
            return candidate
    return None


class PhysicalDeviceQaHarnessTest(unittest.TestCase):
    def test_shell_syntax(self):
        bash = _working_bash()
        if bash is None:
            self.skipTest("no working bash interpreter (Windows may only expose the WSL launcher)")
        result = subprocess.run([bash, "-n", str(SCRIPT)], text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_safety_contract(self):
        text = SCRIPT.read_text(encoding="utf-8")
        for required in (
            'ANDROID_SERIAL',
            'ADB=(adb -s "$SERIAL")',
            'ro.kernel.qemu',
            'physical-device QA refuses emulators',
            'synthetic debug package already exists on selected device',
            'trap cleanup EXIT',
            'org.rigorcore.caserecomp.synthetic.debug',
            'screen=MAP',
            'versionCode=',
            'ALLOW_BACKUP',
            '"budget_mode":"informational"',
        ):
            self.assertIn(required, text)
        self.assertNotIn("assembleRelease", text)
        self.assertNotIn(".crcontent", text)
        self.assertNotIn(".crflow", text)


if __name__ == "__main__":
    unittest.main()
