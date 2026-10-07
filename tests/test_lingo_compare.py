"""All handler texts are invented and synthetic."""
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from caserecomp.lingo_compare import compare_directories, handler_bodies
from caserecomp.inspector import InspectionError
from caserecomp.__main__ import main
import json


class HandlerComparisonTests(TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.left, self.right = self.root / "projectorrays", self.root / "libreshockwave"
        self.left.mkdir(); self.right.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def put(self, left, right):
        (self.left / "one.ls").write_text(left)
        (self.right / "one.ls").write_text(right)

    def test_case_insensitive_same_name_and_body(self):
        self.put("on MouseUp\n   go to 2\nend\n", "on mouseup\nGO TO 2\nend\n")
        report = compare_directories(self.left, self.right)
        self.assertEqual(report["same_name_and_normalized_text"], 1)
        self.assertFalse(report["semantics_verified"])
        self.assertEqual(report["matching"], ["mouseup"])

    def test_body_diff_and_only_one_side(self):
        self.put("on choose\nreturn 1\nend\non onlyLeft\nend", "on CHOOSE\nreturn 2\nend\nfunction onlyRight\nend")
        report = compare_directories(self.left, self.right, redact=True)
        self.assertEqual(report["different_normalized_text"], 1)
        self.assertEqual(len(report["left_only"]), 1)
        self.assertEqual(len(report["right_only"]), 1)
        self.assertEqual(len(report["different"][0]), 64)
        self.assertEqual(report["name_redaction"], "sha256")

    def test_ambiguous_duplicate_handlers_are_not_matched(self):
        self.put("on ping\nend\nfunction ping\nend", "on ping\nend")
        report = compare_directories(self.left, self.right)
        self.assertEqual(report["ambiguous_multiple_definitions"], 1)
        self.assertEqual(report["same_name_and_normalized_text"], 0)

    def test_comments_and_incomplete_handlers(self):
        handlers = handler_bodies("-- on fake\non valid\n  -- ignored\nset x=2\nend valid\non unfinished\n")
        self.assertEqual(len(handlers), 1)
        self.assertEqual(handlers[0][0], "valid")

    def test_no_complete_handlers_or_inputs(self):
        with self.assertRaises(InspectionError):
            compare_directories(self.left, self.right)
        with self.assertRaises(InspectionError):
            compare_directories(self.root / "missing", self.right)

    def test_utf8_bom_and_invalid_encoding(self):
        (self.left / "one.ls").write_bytes(b"\xef\xbb\xbfon main\nend\n")
        (self.right / "one.ls").write_text("on main\nend")
        self.assertEqual(compare_directories(self.left, self.right)["shared_names"], 1)
        (self.left / "one.ls").write_bytes(b"\xff\xff")
        with self.assertRaises(InspectionError):
            compare_directories(self.left, self.right)

    def test_refuse_symbolic_links(self):
        self.put("on draw\nend", "on draw\nend")
        (self.left / "shortcut.ls").symlink_to(self.left / "one.ls")
        with self.assertRaises(InspectionError):
            compare_directories(self.left, self.right)

    def test_cli_create_only_report(self):
        self.put("on stageReady\nend", "on stageReady\nend")
        result = self.root / "audit.json"
        self.assertEqual(main(["compare-lingo", str(self.left), str(self.right), "--output", str(result), "--redact-names"]), 0)
        self.assertEqual(json.loads(result.read_text())["shared_names"], 1)
        self.assertEqual(main(["compare-lingo", str(self.left), str(self.right), "--output", str(result)]), 2)

    def test_unreadable_large_file(self):
        from unittest.mock import patch
        self.put("on test\nend", "on test\nend")
        with patch("caserecomp.lingo_compare.MAX_SCRIPT_BYTES", 4):
            with self.assertRaises(InspectionError):
                compare_directories(self.left, self.right)

class CrossIndexComparisonTests(TestCase):
    """Synthetic Lscr reference index compared against synthetic .ls outputs."""

    def test_match_miss_unindexed_and_privacy(self):
        from caserecomp.lingo_compare import compare_against_movie
        from unittest.mock import patch
        with TemporaryDirectory() as tmp:
            root = Path(tmp)
            a, b = root / 'a', root / 'b'
            a.mkdir(); b.mkdir()
            (a / 'one.ls').write_text('on mouseUp\nend\non unexpected\nend')
            (b / 'one.ls').write_text('on MouseUP\nend\non prepare\nend')
            reference = {'script_count': 2, 'handler_count': 4, 'handlers': [
                {'name': 'mouseup'}, {'name': 'prepare'}, {'name': 'missing'}, {'name': 'missing'}]}
            with patch('caserecomp.lingo_index.open_archive') as open_archive:
                # The index is mocked at the API boundary: no game files in tests.
                with patch('caserecomp.lingo_index.index_movie', return_value=reference) as mocked:
                    report = compare_against_movie(a, b, root / 'fake.dcr')
                    self.assertTrue(mocked.call_args.kwargs['redact'] is False)
                    self.assertEqual(report['original_distinct_handler_names'], 3)
                    self.assertEqual(report['both_matching_original_names'], 1)
                    self.assertEqual(report['left_matching_original_names'], 1)
                    self.assertEqual(report['right_matching_original_names'], 2)
                    self.assertEqual(len(report['missing_from_both']), 1)
                    self.assertEqual(len(report['left_unindexed']), 1)
                    self.assertEqual(len(report['missing_from_both'][0]), 64)
                    self.assertFalse(report['semantics_verified'])
                    clear = compare_against_movie(a, b, root / 'fake.dcr', redact=False)
                    self.assertEqual(clear['missing_from_both'], ['missing'])
                    self.assertEqual(clear['left_unindexed'], ['unexpected'])

    def test_cli_combined_report_and_no_overwrite(self):
        from unittest.mock import patch
        with TemporaryDirectory() as tmp:
            root=Path(tmp)
            a,b=root/'a',root/'b'; a.mkdir();b.mkdir()
            (a/'a.ls').write_text('on advance\nend')
            (b/'b.ls').write_text('on advance\nend')
            reference=root/'movie.dcr';reference.write_bytes(b'synthetic')
            out=root/'report.json'
            data={'script_count':1,'handler_count':1,'handlers':[{'name':'advance'}]}
            with patch('caserecomp.lingo_index.index_movie',return_value=data):
                result=main(['compare-lingo',str(a),str(b),'--reference-movie',str(reference),
                             '--output',str(out),'--redact-names'])
                self.assertEqual(result,0)
                report=json.loads(out.read_text())
                self.assertEqual(report['compiled_index_crosscheck']['both_matching_original_names'],1)
                self.assertEqual(main(['compare-lingo',str(a),str(b),'--reference-movie',str(reference),
                    '--output',str(out)]),2)
