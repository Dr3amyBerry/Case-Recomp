"""Public legal notices and links must remain accessible in app/site/docs."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class LegalPublicationTest(unittest.TestCase):
    def test_required_public_notices_exist(self):
        for path in ("LEGAL.md", "PRIVACY.md", "TERMS.md", "COPYRIGHT_POLICY.md",
                     "THIRD_PARTY_NOTICES.md", "LICENSING_STATUS.md",
                     "LEGAL_RELEASE_CHECKLIST.md"):
            with self.subTest(path=path):
                self.assertGreater((ROOT / path).stat().st_size, 300)

    def test_user_facing_web_and_readme_have_notices(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        web = (ROOT / "web/index.html").read_text(encoding="utf-8")
        for path in ("LICENSE", "LEGAL.md", "PRIVACY.md", "TERMS.md", "THIRD_PARTY_NOTICES.md"):
            self.assertIn(path, readme)
            self.assertIn(path, web)
        self.assertIn("legalTitle:", web)
        self.assertIn("Proyecto independiente", web)

    def test_app_shows_legal_notice_and_links(self):
        home = (ROOT / "android/app/src/main/kotlin/org/rigorcore/caserecomp/app/HomeActivity.kt").read_text(encoding="utf-8")
        self.assertIn("showLegalNotices()", home)
        self.assertIn("LEGAL.md", home)
        self.assertIn("PRIVACY.md", home)
        self.assertIn("TERMS.md", home)
        self.assertIn("PolyForm Noncommercial 1.0.0", home)
        self.assertIn("mailto:contact@rigorcore.com", home)

    def test_legal_contact_is_published_consistently(self):
        for path in ("LEGAL.md", "PRIVACY.md", "TERMS.md", "COPYRIGHT_POLICY.md",
                     "LEGAL_RELEASE_CHECKLIST.md", "README.md", "web/index.html"):
            with self.subTest(path=path):
                self.assertIn("contact@rigorcore.com",
                              (ROOT / path).read_text(encoding="utf-8"))

    def test_license_audit_records_verified_third_parties(self):
        notices = (ROOT / "THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8")
        for library, license_id in (("mutagen", "GPL-2.0-or-later"),
                                    ("Pyodide", "MPL-2.0"),
                                    ("Pillow", "MIT-CMU")):
            with self.subTest(library=library):
                self.assertIn(library, notices)
                self.assertIn(license_id, notices)
        policy = (ROOT / "LICENSING_STATUS.md").read_text(encoding="utf-8")
        self.assertIn("pendiente", policy.lower())
        self.assertIn("LICENSE", policy)
        self.assertIn("INTEROPERABILITY_VENEZUELA.md",
                      (ROOT / "README.md").read_text(encoding="utf-8"))
        self.assertTrue((ROOT / "docs/INTEROPERABILITY_VENEZUELA.md").is_file())

    def test_polyform_license_and_web_bundle_contain_notice(self):
        license_text = (ROOT / "LICENSE").read_text(encoding="utf-8")
        self.assertIn("PolyForm Noncommercial License 1.0.0", license_text)
        self.assertIn("## Personal Uses", license_text)
        self.assertIn("## Distribution License", license_text)
        workflow = (ROOT / ".github/workflows/pages.yml").read_text(encoding="utf-8")
        self.assertIn('cp web/index.html web/worker.js web/convert.py LICENSE site/', workflow)
        self.assertIn('z.write("LICENSE", "LICENSE")', workflow)
        terms = (ROOT / "TERMS.md").read_text(encoding="utf-8")
        self.assertIn("PolyForm Noncommercial", terms)

    def test_proprietary_packages_remain_gitignored(self):
        rules = (ROOT / ".gitignore").read_text(encoding="utf-8")
        for ext in ("*.director.zip", "*.crflow", "*.crscene", "*.keystore", "*.jks"):
            self.assertIn(ext, rules)


if __name__ == "__main__":
    unittest.main()
