import unittest
import xml.etree.ElementTree as ET
from package_vegas import referenced_resources

class PackageResourceTests(unittest.TestCase):
 def test_graphics_music_and_effects_are_preserved(self):
  xml=b'<xui><texture id="t" uri="photo.jpg"/><audiostream id="music" uri="theme.ogg"/><sfx id="click" uri="click.ogg"/><image tex="t"/><sfx id="alias" uri="click.ogg"/></xui>'
  self.assertEqual(["photo.jpg","theme.ogg","click.ogg"],referenced_resources(xml))
 def test_case_aliases_do_not_duplicate_zip_entries(self):
  self.assertEqual(["Click.ogg"],referenced_resources(b'<xui><sfx uri="Click.ogg"/><sfx uri="click.ogg"/></xui>'))
 def test_invalid_xml_is_not_silently_accepted(self):
  with self.assertRaises(ET.ParseError): referenced_resources(b'<xui><sfx uri="missing.ogg"></xui>')

if __name__=='__main__': unittest.main()
