"""Read Director font references and extract PFR1 XMED members into a new local directory."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from caserecomp.director import open_archive
from caserecomp.relationships import CastRelationships
from caserecomp.movie_bundle import info_items, _pascal, parse_xmed_text


def audit(game, destination):
    game, destination = Path(game).resolve(), Path(destination).resolve()
    if destination.exists() or destination == game or game in destination.parents:
        raise ValueError('output must be a new directory outside the original game')
    files = [game / 'MysteryCaseFiles.exe', *sorted((game / 'data').glob('*.cct'))]
    if not files[0].is_file():
        raise ValueError('MysteryCaseFiles.exe not found')
    destination.mkdir(parents=True)
    report = []
    for path in files:
        archive, _ = open_archive(path)
        relationships = CastRelationships(archive)
        fonts, references = [], Counter()
        for entry in archive.entries.values():
            data = archive.get_resource(entry.id) if entry.tag in ('CASt', 'XMED') else b''
            if entry.tag == 'XMED' and not data.startswith(b'PFR1'):
                references.update(parse_xmed_text(data).get('fonts', []))
            if entry.tag != 'CASt':
                continue
            kind, info_length, _ = struct.unpack_from('>iii', data)
            spec = data[12 + info_length:]
            if kind != 16 and not (kind == 15 and spec[4:8] == b'font'):
                continue
            items = info_items(data[12:12 + info_length])
            name = _pascal(items[1]) if len(items) > 1 else ''
            children = []
            for rid in relationships.resources_for(entry.id, 'XMED'):
                payload = archive.get_resource(rid)
                record = {'id': rid, 'size': len(payload), 'sha256': hashlib.sha256(payload).hexdigest(),
                    'pfr1': payload.startswith(b'PFR1')}
                if record['pfr1']:
                    relative = f'{path.stem}-member-{entry.id}-resource-{rid}.pfr'
                    (destination / relative).write_bytes(payload)
                    record['extracted'] = relative
                children.append(record)
            fonts.append({'member_resource': entry.id, 'name': name, 'kind': kind, 'xmed': children})
        report.append({'file': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'tags': dict(Counter(e.tag for e in archive.entries.values())),
            'font_members': fonts, 'references': dict(references)})
    (destination / 'audit.json').write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    return {'files': len(report), 'casts': len(files)-1,
        'font_members': sum(len(r['font_members']) for r in report),
        'pfr1_payloads': sum(c['pfr1'] for r in report for f in r['font_members'] for c in f['xmed'])}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game', required=True); parser.add_argument('--output', required=True)
    args = parser.parse_args()
    print(json.dumps(audit(args.game, args.output)))
