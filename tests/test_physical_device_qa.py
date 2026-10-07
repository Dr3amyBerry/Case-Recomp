import subprocess
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "tools" / "android_physical_qa.sh"


class PhysicalDeviceQaHarnessTest(unittest.TestCase):
    def test_shell_syntax_and_safety_contract(self):
        result = subprocess.run(["bash", "-n", str(SCRIPT)], text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr)
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
