"""External-tool adapters are tested using generated executables, not third-party binaries."""
import json
import os
import tempfile
import unittest
from pathlib import Path

from caserecomp.__main__ import main
from caserecomp.backends import _binary, run_backend
from caserecomp.inspector import InspectionError
from caserecomp.pipeline import verify_export
from tests.test_pipeline import media_cast


STUB_PROJECTOR = '''#!/usr/bin/env python3
import pathlib,sys
assert sys.argv[1:4] == ['decompile','--dump-scripts','-o']
output=pathlib.Path(sys.argv[4])
(output/'cast.cst').write_bytes(b'SYNTHETIC CST')
c=(output/'cast'/'casts');c.mkdir(parents=True)
(c/'script.ls').write_text('on synthetic\nend\n')
'''
STUB_LIBRE = '''#!/usr/bin/env python3
import pathlib,sys
p=pathlib.Path(sys.argv[2]);d=p/'cast.cct'/'scripts';d.mkdir(parents=True);(d/'handler.ls').write_text('on test\\nend')
'''


class AdapterTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.source = self.base/'scene.cct'
        self.source.write_bytes(media_cast())
        self.dest = self.base/'results'
        self.tool = self.base/'tool'

    def tearDown(self):
        self.tmp.cleanup()

    def make_tool(self, code):
        self.tool.write_text(code)
        self.tool.chmod(0o700)
        return self.tool

    def test_projectorrays_stub_isolated_staging(self):
        input_before = self.source.read_bytes()
        info = run_backend('projectorrays',self.source,self.dest,self.make_tool(STUB_PROJECTOR))
        self.assertEqual(info['asset_count'],2)
        self.assertIn('cast.cst', [asset['file'] for asset in info['assets']])
        self.assertIn('cast/casts/script.ls', [asset['file'] for asset in info['assets']])
        self.assertEqual(self.source.read_bytes(),input_before)
        self.assertEqual(verify_export(self.dest)['verified_files'],2)
        self.assertEqual(info['verified_semantics'],False)
        self.assertIn('recovered-lingo-unverified', [asset['format'] for asset in info['assets']])

    def test_libre_stub_captures_script(self):
        info=run_backend('libreshockwave',self.source,self.dest,self.make_tool(STUB_LIBRE))
        self.assertEqual(info['assets'][0]['file'],'cast.cct/scripts/handler.ls')
        self.assertEqual(verify_export(self.dest)['verified_files'],1)

    def test_backend_cli(self):
        self.make_tool(STUB_PROJECTOR)
        self.assertEqual(main(['external-export',str(self.source),'--output',str(self.dest),
                               '--backend','projectorrays','--binary',str(self.tool)]),0)
        self.assertEqual(main(['verify-export',str(self.dest)]),0)

    def test_external_failure_rollback(self):
        self.make_tool('#!/usr/bin/env python3\nimport sys\nsys.exit(3)\n')
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.source,self.dest,self.tool)
        self.assertFalse(self.dest.exists())

    def test_external_no_output_fails(self):
        self.make_tool('#!/usr/bin/env python3\npass\n')
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.source,self.dest,self.tool)
        self.assertFalse(self.dest.exists())

    def test_external_bad_extension(self):
        self.make_tool('''#!/usr/bin/env python3
import pathlib,sys
(pathlib.Path(sys.argv[4])/'cast.txt').write_text('not accepted')
''')
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.source,self.dest,self.tool)

    def test_external_symlink_output_rejected(self):
        self.make_tool('''#!/usr/bin/env python3
import pathlib,sys
p=pathlib.Path(sys.argv[4]);(p/'cast.cst').symlink_to(p/'cast.cst')
''')
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.source,self.dest,self.tool)
        self.assertFalse(self.dest.exists())

    def test_timeout_and_invalid_config(self):
        self.make_tool('#!/usr/bin/env python3\nimport time\ntime.sleep(2)\n')
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.source,self.dest,self.tool,timeout=1)
        for vendor, timeout in [('unknown',5),('projectorrays',0),('projectorrays',9000)]:
            with self.assertRaises(InspectionError):
                run_backend(vendor,self.source,self.dest,self.tool,timeout=timeout)

    def test_tool_missing_or_not_executable(self):
        with self.assertRaises(InspectionError):
            _binary(self.tool)
        self.tool.write_text('no execute')
        with self.assertRaises(InspectionError):
            _binary(self.tool)
        self.tool.chmod(0o700)
        self.assertEqual(_binary(self.tool),self.tool)

    def test_symlink_tool_fails(self):
        self.make_tool(STUB_PROJECTOR)
        link=self.base/'link'
        link.symlink_to(self.tool)
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.source,self.dest,link)

    def test_directory_input_external_fails(self):
        self.make_tool(STUB_PROJECTOR)
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.base,self.dest,self.tool)

    def test_invalid_source_never_runs_tool(self):
        self.make_tool(STUB_PROJECTOR)
        self.source.write_bytes(b'bad binary')
        with self.assertRaises(InspectionError):
            run_backend('projectorrays',self.source,self.dest,self.tool)
        self.assertFalse(self.dest.exists())


if __name__ == '__main__':
    unittest.main()
