"""Fixtures contain no copyrighted inputs; only generated RGB and PCM media."""
import hashlib
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
from caserecomp.inspector import InspectionError
from caserecomp.pipeline import (MAX_IMAGE_PIXELS, _as_png, _jpeg_dimensions, _wave_metadata,
                                convert_local, guard_destination, input_archives, verify_export)
from tests.test_director import varint, file_chunk


def generate_jpeg():
    from PIL import Image
    stream = io.BytesIO()
    Image.new('RGB', (3, 2), (17, 89, 193)).save(stream, 'JPEG')
    return stream.getvalue()


def generate_wav():
    stream = io.BytesIO()
    with wave.open(stream, 'wb') as sound:
        sound.setnchannels(1)
        sound.setsampwidth(2)
        sound.setframerate(8000)
        sound.writeframes(b'\0\0\1\0' * 10)
    return stream.getvalue()


def media_cast(assets=None, kind=b'CDGF', keys=None):
    """Build small, valid compressed XFIR cast with selectable raw, zlib or SWA members."""
    assets = assets or [('ediM', generate_jpeg(), 1), ('Lscr', b'compiled-fake-lingo', 1),
                        ('snd ', generate_wav(), 1), ('ALFA', b'opaque alpha', 1)]
    codecs = [b'Macromedia ziplib compression', b'Macromedia null Compressor', b'SWA Decompressor Xtra']
    registry = struct.pack('<H', 3) + bytes(3 * 16) + b'\0'.join(codecs) + b'\0'
    fver = varint(0x501) + varint(1) + varint(0x73A) + b'\x09' + b'8.5.1#104'
    if keys is None:
        key_payload = b'initial'  # legacy negative-test placeholder
    else:
        key_payload = struct.pack('<HHII', 12, 12, len(keys) + 2, len(keys))
        key_payload += b''.join(struct.pack('<II4s', rid, owner, tag[::-1].encode('ascii'))
                                for rid, owner, tag in keys)
        key_payload += bytes(24)  # two reserved, unused records
    ils_body = varint(1) + key_payload
    ils_zip = zlib.compress(ils_body)
    members = [(0, 0, len(ils_zip), len(ils_body), 0, 'ILS '),
               (1, 0xffffffff, len(key_payload), len(key_payload), 0, 'KEY*')]
    trailing = bytearray(ils_zip)
    for i, (tag, data, codec) in enumerate(assets, 100):
        raw = zlib.compress(data) if codec == 0 else data
        members.append((i, len(trailing), len(raw), len(data), codec, tag))
        trailing += raw
    mapping = varint(1) + varint(20) + varint(len(members))
    for ident, offset, clen, ulen, codec, tag in members:
        mapping += b''.join(varint(x) for x in (ident, offset, clen, ulen, codec)) + tag[::-1].encode()
    abmp = varint(0) + varint(len(mapping)) + zlib.compress(mapping)
    chunks = file_chunk(b'revF', fver) + file_chunk(b'rdcF', zlib.compress(registry))
    chunks += file_chunk(b'PMBA', abmp) + b'IEGF\x00' + bytes(trailing)
    body = kind + chunks
    return b'XFIR' + struct.pack('<I', len(body)) + body


class ConversionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.source = self.root / 'test.cct'
        self.source.write_bytes(media_cast())
        self.output = self.root / 'private-conversion'

    def tearDown(self):
        self.tmp.cleanup()

    def test_jpeg_conversion_and_bytecode_opaque_audio(self):
        info = convert_local(self.source, self.output, include_bytecode=True, include_raw=True)
        self.assertEqual(info['asset_count'], 4)  # JPEG, Lscr, ALFA, KEY* not selected
        self.assertEqual(info['skipped'].get('snd '), None)
        self.assertIn('KEY*', info['skipped'])
        self.assertTrue((self.output / 'archive-000/images/00000100.jpg').is_file())
        self.assertEqual((self.output / 'archive-000/images/00000100.jpg').read_bytes(), generate_jpeg())
        self.assertEqual(verify_export(self.output)['verified_files'], 4)
        self.assertTrue(any(x.get('decompiled') is False for x in info['assets']))
        self.assertEqual([x for x in info['assets'] if x['tag'] == 'ediM'][0]['alpha_applied'], False)

    def test_png_decoding_and_wav_info(self):
        info = convert_local(self.source, self.output, image_format='png')
        self.assertEqual(info['asset_count'], 2)
        self.assertEqual(info['assets'][0]['format'], 'png')
        self.assertEqual(info['assets'][1]['format'], 'pcm-wav')
        from PIL import Image
        with Image.open(self.output / info['assets'][0]['file']) as img:
            self.assertEqual(img.size, (3, 2))
        self.assertEqual(verify_export(self.output)['verified_files'], 2)

    def test_pcm_wav_bad_truncated_rejected(self):
        data = generate_wav()[:-1]
        with self.assertRaises(InspectionError):
            _wave_metadata(data)
        with self.assertRaises(InspectionError):
            _wave_metadata(b'rubbish')

    def test_copies_supported_audio_and_omits_unsupported_codec(self):
        self.source.write_bytes(media_cast([('snd ', b'compressed SWA', 2),
                                            ('snd ', generate_wav(), 1)]))
        info = convert_local(self.source, self.output)
        self.assertEqual(info['skipped']['unsupported_SWA'], 1)
        self.assertEqual(info['asset_count'], 1)

    def test_invalid_jpeg_skipped_not_relabelled(self):
        self.source.write_bytes(media_cast([('ediM', b'not a jpeg', 1)]))
        info = convert_local(self.source, self.output)
        self.assertEqual(info['asset_count'], 0)
        self.assertEqual(info['skipped']['unrecognized_ediM'], 1)
        self.assertEqual(verify_export(self.output)['verified_files'], 0)

    def test_images_validate_dimensions(self):
        self.assertEqual(_jpeg_dimensions(generate_jpeg()), (3, 2))
        self.assertTrue(_as_png(generate_jpeg()).startswith(b'\x89PNG'))
        with self.assertRaises(InspectionError):
            _jpeg_dimensions(b'\xff\xd8truncated\xff\xd9')
        with self.assertRaises(InspectionError):
            _jpeg_dimensions(b'bad')

    def test_create_only_destination_never_overwrites(self):
        self.output.mkdir()
        sentinel = self.output / 'sentinel'
        sentinel.write_bytes(b'PRESERVE')
        with self.assertRaises(InspectionError):
            convert_local(self.source, self.output)
        self.assertEqual(sentinel.read_bytes(), b'PRESERVE')

    def test_invalid_config_rejected_before_write(self):
        with self.assertRaises(InspectionError):
            convert_local(self.source, self.output, image_format='webp')
        with self.assertRaises(InspectionError):
            convert_local(self.source, self.output, max_assets=0)
        with self.assertRaises(InspectionError):
            convert_local(self.source, self.output, max_assets=1)
        self.assertFalse(self.output.exists())

    def test_input_tree_and_bad_archive(self):
        folder = self.root / 'input'
        folder.mkdir()
        (folder / 'a.exe').write_bytes(b'not director')
        (folder / 'test.cct').write_bytes(self.source.read_bytes())
        r = convert_local(folder, self.output)
        self.assertEqual(r['asset_count'], 2)
        self.assertEqual(len(r['source_archives']), 1)
        self.assertEqual(r['source_archives'][0]['name'], 'test.cct')

    def test_no_archive_fails_without_output(self):
        self.source.unlink()
        with self.assertRaises(InspectionError):
            convert_local(self.root, self.output)
        self.assertFalse(self.output.exists())

    def test_single_bad_archive_fails(self):
        self.source.write_bytes(b'unrecognized')
        with self.assertRaises(InspectionError):
            convert_local(self.source, self.output)
        self.assertFalse(self.output.exists())

    def test_guard_output_placement_and_links(self):
        with self.assertRaises(InspectionError):
            guard_destination(self.root, self.root / 'inner')
        link = self.root / 'symlink'
        link.symlink_to(self.source)
        with self.assertRaises(InspectionError):
            input_archives(link)
        self.source.unlink()
        with self.assertRaises(InspectionError):
            input_archives(self.root)
        self.assertFalse(self.output.exists())

    def test_git_parent_blocked(self):
        (self.root / '.git').mkdir()
        with self.assertRaises(InspectionError):
            guard_destination(self.source, self.output)

    def test_output_requires_existing_parent(self):
        with self.assertRaises(InspectionError):
            guard_destination(self.source, self.root / 'missing' / 'result')

    def test_manifest_integrity_tampering(self):
        convert_local(self.source, self.output)
        path = next((self.output / 'archive-000/images').glob('*.jpg'))
        path.write_bytes(b'CORRUPT')
        with self.assertRaises(InspectionError):
            verify_export(self.output)

    def test_manifest_schema_and_duplicate_path_checks(self):
        convert_local(self.source, self.output)
        manifest_file = self.output / 'manifest.json'
        meta = json.loads(manifest_file.read_text())
        meta['assets'].append(meta['assets'][0])
        meta['asset_count'] += 1
        manifest_file.write_text(json.dumps(meta))
        with self.assertRaises(InspectionError):
            verify_export(self.output)
        meta['assets'][1]['file'] = '../evil'
        manifest_file.write_text(json.dumps(meta))
        with self.assertRaises(InspectionError):
            verify_export(self.output)

    def test_manifest_version_and_total(self):
        convert_local(self.source, self.output)
        f = self.output / 'manifest.json'
        info = json.loads(f.read_text())
        info['schema_version'] = 9
        f.write_text(json.dumps(info))
        with self.assertRaises(InspectionError):
            verify_export(self.output)
        info['schema_version'] = 1
        info['bytes'] = -10
        f.write_text(json.dumps(info))
        with self.assertRaises(InspectionError):
            verify_export(self.output)

    def test_missing_manifest_and_symlink(self):
        self.output.mkdir()
        with self.assertRaises(InspectionError):
            verify_export(self.output)
        f = self.output / 'manifest.json'
        f.symlink_to(self.source)
        with self.assertRaises(InspectionError):
            verify_export(self.output)

    def test_cli_commands(self):
        self.assertEqual(main(['convert-local',str(self.source),'--output',str(self.output),'--image-format','png']),0)
        self.assertEqual(main(['verify-export',str(self.output)]),0)
        self.assertEqual(main(['convert-local',str(self.source),'--output',str(self.output)]),2)


if __name__ == '__main__':
    unittest.main()
