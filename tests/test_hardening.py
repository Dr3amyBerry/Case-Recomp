"""Failure-mode tests for Director, CLI, conversion, and hostile file layouts."""
import io
import json
import os
import struct
import tempfile
import unittest
import wave
import zlib
from pathlib import Path
from unittest.mock import patch

from caserecomp.__main__ import main
from caserecomp.director import DirectorArchive, _bounded_zlib, _fourcc, _varint, open_archive
from caserecomp.inspector import InspectionError, _pe_header, _director_header, inspect_bytes, scan
from caserecomp.pipeline import convert_local, guard_destination, input_archives, verify_export, _jpeg_dimensions, _wave_metadata
from caserecomp.verification import verify_directory
from tests.test_director import afterburner_fixture, file_chunk, pe_with_movie, varint
from tests.test_pipeline import generate_jpeg, generate_wav, media_cast


class ParserHardening(unittest.TestCase):
    def test_varint_overflow_and_fourcc(self):
        with self.assertRaises(InspectionError):
            _varint(b'\xff\xff\xff\xff\x7f', 0)
        with self.assertRaises(InspectionError):
            _fourcc(b'B\x00D!')
        with self.assertRaises(InspectionError):
            _fourcc(b'longer')

    def test_zlib_truncated_bad_size_and_cap(self):
        good=zlib.compress(b'ABCDE')
        for data, expected, limit in [(good, 4, 10),(good, 5, 4),(good[:-2],5,5)]:
            with self.assertRaises(InspectionError):
                _bounded_zlib(data,expected,limit)

    def test_director_bad_headers(self):
        raw=afterburner_fixture()
        for before,after in [(b'revF',b'broken'[:4]),(b'rdcF',b'BAD!'),(b'PMBA',b'ZERO'),(b'IEGF',b'BAD!')]:
            mutated=raw.replace(before,after,1)
            with self.subTest(chunk=before), self.assertRaises(InspectionError):
                DirectorArchive(mutated)
        with self.assertRaises(InspectionError):
            DirectorArchive(b'XFIR'+b'\x00'*12)

    def test_director_invalid_version_string(self):
        raw=afterburner_fixture()
        at=raw.find(b'8.5.1#104')
        self.assertNotEqual(at,-1)
        altered=bytearray(raw)
        altered[at-1]=10 # Fver version length declared too long
        with self.assertRaises(InspectionError):
            DirectorArchive(bytes(altered))

    def test_corrupt_fver_chunk_size(self):
        raw=bytearray(afterburner_fixture())
        raw[16]=127
        with self.assertRaises(InspectionError):
            DirectorArchive(bytes(raw))

    def test_duplicate_or_wrong_codec_member(self):
        raw=afterburner_fixture()
        original=b'\x00'*32
        self.assertIn(original, zlib.decompress(raw[raw.find(b'rdcF')+5: raw.find(b'PMBA')]))
        # Shift codec map into an unsupported index without mutating the source file.
        with patch.object(DirectorArchive,'_codec_name',return_value='unsupported'):
            parsed=DirectorArchive(raw)
            with self.assertRaises(InspectionError):
                parsed.get_resource(6)

    def test_corrupt_pe_optional_table(self):
        pe=bytearray(pe_with_movie(afterburner_fixture(b'MDGF')))
        struct.pack_into('<H',pe,0x86,1000)
        with self.assertRaises(InspectionError):
            _pe_header(bytes(pe))
        self.assertIsNone(_pe_header(b'not an executable'))

    def test_director_header_small(self):
        self.assertIsNone(_director_header(b'XFIR'))
        self.assertIsNone(_director_header(b'RIFF'+b'\xff'*20))
        self.assertIsNone(_director_header(b'garbage padding'))

    def test_stress_overlarge_file_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root)/'bad.cct'
            path.write_bytes(b'not director')
            with self.assertRaises(InspectionError):
                open_archive(path)


class IOHardening(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.root=Path(self.tmp.name)
        self.source=self.root/'input.cct'
        self.source.write_bytes(media_cast())
        self.out=self.root/'result'

    def tearDown(self):
        self.tmp.cleanup()

    def test_input_archives_enforces_limit_and_input_type(self):
        (self.root/'another.cct').write_bytes(media_cast())
        with self.assertRaises(InspectionError):
            input_archives(self.root,limit=1)
        with self.assertRaises(InspectionError):
            input_archives(self.root/'missing')

    def test_symlink_nested_input_not_walked(self):
        link=self.root/'hidden.cct'
        link.symlink_to(self.source)
        with self.assertRaises(InspectionError):
            input_archives(self.root)

    def test_bad_output_sym_and_parent(self):
        self.out.symlink_to(self.source)
        with self.assertRaises(InspectionError):
            guard_destination(self.source,self.out)
        self.out.unlink()
        parent=self.root/'repoint'
        parent.symlink_to(self.root)
        with self.assertRaises(InspectionError):
            guard_destination(self.source,parent/'out')

    def test_jpeg_pixel_limit(self):
        with patch('caserecomp.pipeline.MAX_IMAGE_PIXELS',1):
            with self.assertRaises(InspectionError):
                _jpeg_dimensions(generate_jpeg())

    def test_wav_wrong_rate_and_channels(self):
        data=bytearray(generate_wav())
        data[24:28]=struct.pack('<I',5000)
        with self.assertRaises(InspectionError):
            _wave_metadata(bytes(data))
        data=bytearray(generate_wav())
        data[22:24]=struct.pack('<H',10)
        with self.assertRaises(InspectionError):
            _wave_metadata(bytes(data))

    def test_bad_audio_is_skipped(self):
        self.source.write_bytes(media_cast([('snd ',b'RIFFbad wave',1)]))
        record=convert_local(self.source,self.out)
        self.assertEqual(record['asset_count'],0)
        self.assertEqual(record['skipped']['invalid_wav'],1)

    def test_export_fails_closed_if_asset_write_fails(self):
        with patch('caserecomp.pipeline._publish_asset',side_effect=InspectionError('simulated')):
            with self.assertRaises(InspectionError):
                convert_local(self.source,self.out)
        self.assertFalse(self.out.exists())

    def test_manifest_count_mismatch(self):
        convert_local(self.source,self.out)
        mf=self.out/'manifest.json'
        obj=json.loads(mf.read_text())
        obj['asset_count']=10
        mf.write_text(json.dumps(obj))
        with self.assertRaises(InspectionError):
            verify_export(self.out)

    def test_manifest_filename_backslash_invalid(self):
        convert_local(self.source,self.out)
        mf=self.out/'manifest.json'
        obj=json.loads(mf.read_text())
        obj['assets'][0]['file']='dir\\evil'
        mf.write_text(json.dumps(obj))
        with self.assertRaises(InspectionError):
            verify_export(self.out)

    def test_manifest_invalid_json_and_export_link(self):
        convert_local(self.source,self.out)
        mf=self.out/'manifest.json'
        mf.write_text('{corrupt')
        with self.assertRaises(InspectionError):
            verify_export(self.out)
        mf.unlink()
        mf.symlink_to(self.source)
        with self.assertRaises(InspectionError):
            verify_export(self.out)

    def test_output_symlink_asset_rejected(self):
        convert_local(self.source,self.out)
        asset=next((self.out/'archive-000/images').iterdir())
        asset.unlink()
        asset.symlink_to(self.source)
        with self.assertRaises(InspectionError):
            verify_export(self.out)

    def test_cli_readonly_subcommands(self):
        self.assertEqual(main(['director-map',str(self.source)]),0)
        self.assertEqual(main(['scan',str(self.root)]),0)
        self.assertEqual(main(['verify-local',str(self.root)]),0)
        self.assertEqual(main(['extract-resources',str(self.source),'--output',str(self.out),'--tag','KEY*']),0)
        self.assertEqual(main(['extract-resources',str(self.source),'--output',str(self.out),'--tag','KEY*']),2)

    def test_cli_inventory_create_only(self):
        out=self.root/'report.json'
        self.assertEqual(main(['scan',str(self.root),'--output',str(out)]),0)
        self.assertEqual(main(['scan',str(self.root),'--output',str(out)]),2)

    def test_verify_unrecognized_and_invalid(self):
        (self.root/'another.exe').write_bytes(b'garbage')
        (self.root/'bad.cct').write_bytes(b'invalid')
        record=verify_directory(self.root)
        self.assertGreaterEqual(record['unrecognized_files'],1)
        self.assertEqual(len(record['invalid_archives']),1)

    def test_scanner_max_entries(self):
        for n in range(3):
            (self.root/f'scan{n}.txt').write_text('stuff')
        result=scan(self.root,max_files=1)
        self.assertTrue(result['errors'])


if __name__=='__main__':
    unittest.main()

class Mp3Tests(unittest.TestCase):
    @staticmethod
    def pretend_id3():
        return b'ID3' + bytes(200)

    def test_mp3_invalid_and_too_short(self):
        from caserecomp.pipeline import _mp3_metadata
        for payload in (b'ID3',b'NOT-MP3'):
            with self.assertRaises(InspectionError):
                _mp3_metadata(payload)
        from mutagen import MutagenError
        with patch('mutagen.mp3.MP3',side_effect=MutagenError('invalid')):
            with self.assertRaises(InspectionError):
                _mp3_metadata(self.pretend_id3())

    def test_mp3_valid_mock_metadata_is_exported(self):
        from caserecomp.pipeline import _mp3_metadata
        from types import SimpleNamespace
        info=SimpleNamespace(info=SimpleNamespace(length=12.5,sample_rate=44100,channels=2,bitrate=128000))
        with patch('mutagen.mp3.MP3',return_value=info):
            self.assertEqual(_mp3_metadata(self.pretend_id3())['duration_seconds'],12.5)
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);src=root/'audio.cct';dst=root/'out'
                src.write_bytes(media_cast([('ediM',self.pretend_id3(),1)]))
                manifest=convert_local(src,dst)
                self.assertEqual(manifest['asset_count'],1)
                self.assertEqual(manifest['assets'][0]['format'],'mp3-id3')
                self.assertEqual(verify_export(dst)['verified_files'],1)

    def test_mp3_metadata_out_of_bounds(self):
        from caserecomp.pipeline import _mp3_metadata
        from types import SimpleNamespace
        info=SimpleNamespace(info=SimpleNamespace(length=99999,sample_rate=44100,channels=2,bitrate=128000))
        with patch('mutagen.mp3.MP3',return_value=info):
            with self.assertRaises(InspectionError):
                _mp3_metadata(self.pretend_id3())

    def test_mp3_invalid_header_is_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);src=root/'audio.cct';dst=root/'out'
            src.write_bytes(media_cast([('ediM',self.pretend_id3(),1)]))
            obj=convert_local(src,dst)
            self.assertEqual(obj['asset_count'],0)
            self.assertEqual(obj['skipped']['invalid_MP3'],1)
