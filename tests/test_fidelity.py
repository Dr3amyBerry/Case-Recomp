"""Synthetic media fidelity tests; no game media."""
from __future__ import annotations

from io import BytesIO
from pathlib import Path
import tempfile
import unittest
import wave

from PIL import Image, PngImagePlugin

from caserecomp.fidelity import compare_png, compare_wav
from caserecomp.inspector import InspectionError


class FidelityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def png(self, name, pixels, metadata=None):
        path = self.root/name
        im = Image.new("RGBA", (2, 1)); im.putdata(pixels)
        info = PngImagePlugin.PngInfo()
        if metadata: info.add_text("note", metadata)
        im.save(path, pnginfo=info, compress_level=9 if metadata else 1)
        return path

    def wav(self, name, frames, rate=8000):
        path = self.root/name
        with wave.open(str(path), "wb") as out:
            out.setnchannels(1); out.setsampwidth(2); out.setframerate(rate); out.writeframes(frames)
        return path

    def test_png_compares_pixels_not_container_bytes(self):
        a=self.png("a.png",[(1,2,3,4),(5,6,7,8)],"a")
        b=self.png("b.png",[(1,2,3,4),(5,6,7,8)],"different")
        self.assertNotEqual(a.read_bytes(), b.read_bytes())
        result=compare_png(a,b)
        self.assertTrue(result["exact_pixels"])
        self.assertTrue(result["dimensions_match"])

    def test_png_detects_pixel_difference(self):
        a=self.png("a.png",[(1,2,3,4),(5,6,7,8)])
        b=self.png("b.png",[(1,2,3,4),(5,6,7,9)])
        self.assertFalse(compare_png(a,b)["exact_pixels"])

    def test_png_rejects_non_png(self):
        a=self.root/"x.png"; a.write_bytes(b"not png")
        with self.assertRaises(InspectionError): compare_png(a,a)

    def test_wav_compares_pcm_not_riff_container(self):
        pcm=b"\x01\x00\x02\x00\x03\x00"
        a=self.wav("a.wav",pcm); b=self.wav("b.wav",pcm)
        result=compare_wav(a,b)
        self.assertTrue(result["audio_geometry_match"])
        self.assertTrue(result["exact_pcm"])

    def test_wav_detects_geometry_or_sample_change(self):
        a=self.wav("a.wav",b"\x01\x00\x02\x00")
        b=self.wav("b.wav",b"\x01\x00\x03\x00")
        c=self.wav("c.wav",b"\x01\x00\x02\x00",rate=11025)
        self.assertFalse(compare_wav(a,b)["exact_pcm"])
        self.assertFalse(compare_wav(a,c)["audio_geometry_match"])

    def test_wav_rejects_invalid(self):
        a=self.root/"x.wav"; a.write_bytes(b"RIFFbad")
        with self.assertRaises(InspectionError): compare_wav(a,a)
