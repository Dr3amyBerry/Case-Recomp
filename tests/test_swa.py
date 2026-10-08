from __future__ import annotations

import unittest

from caserecomp.audio import swa_mpeg_audio
from caserecomp.inspector import InspectionError


def mpeg2_layer3_frame() -> bytes:
    """One MPEG-2 layer III frame at 48 kbit/s, 22.05 kHz: 72 * 48000 // 22050 = 156 bytes."""
    return bytes([0xFF, 0xF3, 0x60, 0xC4]) + bytes(156 - 4)


class SwaTests(unittest.TestCase):
    def test_header_is_dropped_and_frames_kept(self):
        frames = mpeg2_layer3_frame() * 3
        self.assertEqual(swa_mpeg_audio(bytes(range(82)) + frames), frames)

    def test_broken_or_missing_frames_are_rejected(self):
        frames = mpeg2_layer3_frame() * 2
        for bad in (bytes(82) + frames[:-10], bytes(82) + frames + b"junk", bytes(200)):
            with self.assertRaises(InspectionError):
                swa_mpeg_audio(bad)


if __name__ == "__main__":
    unittest.main()
