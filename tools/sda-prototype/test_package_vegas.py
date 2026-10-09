"""Packaging regression with synthetic, noncommercial resources."""
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from PIL import Image
from package_vegas import build_package

class SyntheticResources:
    def __init__(self, _path):
        self.data = {
            "ENVS.MSE": b'<xui><texture id="menu_back" uri="test_background.png"/><mainmenu><image x="0" y="0" tex="menu_back"/></mainmenu></xui>',
            "SCENE_VAULT.MSL": b"<xui/>",
        }
        self.entries = self.data
    def read(self, name):
        return self.data[name.upper()]
    def image(self, name):
        assert name == "test_background.png"
        return Image.new("RGBA", (800, 600), (10, 80, 30, 255))

class PackageCoverTest(unittest.TestCase):
    def test_cover_uses_xui_background_without_external_distributor_file(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "content.zip"
            with patch("package_vegas.Resources", SyntheticResources), patch("package_vegas.Image.open", side_effect=AssertionError("external image file accessed")):
                build_package(Path(directory) / "Resources.dll", target)
            with zipfile.ZipFile(target) as archive:
                manifest = json.loads(archive.read("manifest.json"))
                cover = archive.read(manifest["cover"])
                image = Image.open(io.BytesIO(cover))
                self.assertEqual(image.size, (320, 240))
                self.assertEqual(image.convert("RGB").getpixel((160, 120)), (10, 80, 30))
                self.assertEqual(manifest["files"]["cover.png"][0], len(cover))

    def test_non_four_by_three_background_keeps_its_aspect_ratio(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "content.zip"
            with patch("package_vegas.Resources", SyntheticResources), patch.object(SyntheticResources, "image", return_value=Image.new("RGBA", (800, 400))):
                build_package(Path(directory) / "Resources.dll", target)
            with zipfile.ZipFile(target) as archive:
                image = Image.open(io.BytesIO(archive.read("cover.png")))
                self.assertEqual(image.size, (320, 160))

if __name__ == "__main__":
    unittest.main()
