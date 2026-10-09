"""Build an experimental OpenType font from privately parsed PFR1 contours."""
import argparse
import json
from pathlib import Path

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.recordingPen import RecordingPen
from fontTools.ttLib import TTFont


def build_font(source, output, family):
    output = Path(output)
    if output.exists():
        raise ValueError('output already exists')
    data = json.loads(Path(source).read_text(encoding='utf-8'))
    units, metric_units = data['units'], data['metric_units']
    if not 16 <= units <= 16384 or not 16 <= metric_units <= 16384:
        raise ValueError('invalid font units')
    records = data['glyphs']
    if not 1 <= len(records) <= 1024:
        raise ValueError('invalid glyph count')
    order, cmap = ['.notdef'], {}
    charstrings = {'.notdef': T2CharStringPen(0, None).getCharString()}
    metrics = {'.notdef': (0, 0)}
    for record in records:
        code = record['code']
        if code == 0:
            continue
        # PFR character codes in this Windows Director source use Windows-1252.
        if 128 <= code <= 159:
            try:
                code = ord(bytes([code]).decode('cp1252'))
            except UnicodeDecodeError:
                continue
        if not 1 <= code <= 0xffff or code in cmap:
            raise ValueError('invalid or duplicate character')
        name = f'uni{code:04X}'
        advance = round(record['width'] * units / metric_units)
        if not 0 <= advance <= 65535:
            raise ValueError('invalid glyph advance')
        pen = RecordingPen()
        for contour in record['contours']:
            if not contour or contour[0][0] != 0:
                raise ValueError('contour does not start with moveTo')
            for command in contour:
                kind, x, y, x1, y1, x2, y2 = command
                if kind == 0:
                    pen.moveTo((x, y))
                elif kind == 1:
                    pen.lineTo((x, y))
                elif kind == 2:
                    pen.curveTo((x1, y1), (x2, y2), (x, y))
                else:
                    raise ValueError('unknown contour command')
            pen.closePath()
        if not record['contours'] and code not in (32, 160):
            raise ValueError(f'empty non-space glyph {code}')
        charstring_pen = T2CharStringPen(advance, None, roundTolerance=0)
        pen.replay(charstring_pen)
        charstring = charstring_pen.getCharString()
        bounds = BoundsPen(None)
        pen.replay(bounds)
        metrics[name] = (advance, round(bounds.bounds[0]) if bounds.bounds else 0)
        charstrings[name] = charstring
        cmap[code] = name
        order.append(name)
    builder = FontBuilder(units, isTTF=False)
    builder.setupGlyphOrder(order)
    builder.setupCharacterMap(cmap)
    builder.setupHorizontalMetrics(metrics)
    builder.setupHorizontalHeader(ascent=data['ascender'], descent=data['descender'])
    ps_name = ''.join(c for c in family if c.isascii() and c.isalnum())
    builder.setupNameTable({'familyName': family, 'styleName': 'Regular',
        'uniqueFontIdentifier': ps_name, 'fullName': family, 'psName': ps_name,
        'version': 'Version 0.1 experimental PFR recovery'})
    builder.setupOS2(sTypoAscender=data['ascender'], sTypoDescender=data['descender'],
        usWinAscent=max(0, data['ascender']), usWinDescent=max(0, -data['descender']))
    builder.setupPost()
    builder.setupCFF(ps_name, {'FullName': family, 'FamilyName': family, 'Weight': 'Regular'}, charstrings, {})
    builder.save(output)
    checked = TTFont(output, checkChecksums=2)
    checked.ensureDecompiled()
    for char in 'Agente: Dream Caso 12345 \u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00fc\u00a1\u00bf':
        if ord(char) not in checked.getBestCmap():
            raise ValueError(f'missing sample character {ord(char)}')
    return {'characters': len(cmap), 'glyphs': len(order), 'units': units,
        'kerning_recovered': False, 'original_hinting_preserved': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source'); parser.add_argument('output'); parser.add_argument('--family', required=True)
    args = parser.parse_args()
    print(json.dumps(build_font(args.source, args.output, args.family)))
