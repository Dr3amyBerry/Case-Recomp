"""Compare PFR readers and CFF round trips without distributing glyph data."""
import argparse
import json
import math
from pathlib import Path
from fontTools.pens.recordingPen import RecordingPen
from fontTools.ttLib import TTFont


def operations(contours):
    result = []
    for contour in contours:
        row = []
        for kind, x, y, x1, y1, x2, y2 in contour:
            if kind == 0:
                row.append(('moveTo', ((x, y),)))
            elif kind == 1:
                row.append(('lineTo', ((x, y),)))
            elif kind == 2:
                row.append(('curveTo', ((x1, y1), (x2, y2), (x, y))))
            else:
                raise ValueError('unknown command')
        result.extend(row + [('closePath', ())])
    return normalize(result)


def normalize(commands):
    result, start = [], None
    for kind, points in commands:
        if kind == 'moveTo':
            start = points
        if kind == 'closePath' and result and result[-1] == ('lineTo', start):
            result.pop()  # Explicit and implicit final straight edge are equivalent.
        result.append((kind, points))
    return result


def distance(left, right):
    if len(left) != len(right):
        return None
    error = 0.0
    for (ka, pa), (kb, pb) in zip(left, right):
        if ka != kb or len(pa) != len(pb):
            return None
        for a, b in zip(pa, pb):
            error = max(error, *(abs(x-y) for x, y in zip(a, b)))
    return error


def unicode_code(code):
    if code == 0:
        return None
    if 128 <= code <= 159:
        try:
            return ord(bytes([code]).decode('cp1252'))
        except UnicodeDecodeError:
            return None
    return code


def compare(source, reference, font_path, tolerance=0.001):
    source = json.loads(Path(source).read_text(encoding='utf-8'))
    reference = json.loads(Path(reference).read_text(encoding='utf-8'))
    font = TTFont(font_path)
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    records = {g['code']: g for g in reference['glyphs']}
    if len(records) != len(reference['glyphs']):
        raise ValueError('duplicate reference code')
    failures, max_reference, max_cff, converted = [], 0.0, 0.0, 0
    quantized, fixed_point = [], []
    metadata = all(source[k] == reference[k] for k in ('units','metric_units','ascender','descender'))
    if not metadata or set(records) != {g['code'] for g in source['glyphs']}:
        failures.append({'stage':'font metadata'})
    for record in source['glyphs']:
        code = record['code']
        expected = operations(record['contours'])
        other = records.get(code)
        error = distance(expected, operations(other['contours'])) if other else None
        if error is not None and error > tolerance:
            truncated = [(k, tuple(tuple(math.trunc(v) for v in point) for point in pts)) for k, pts in expected]
            quantization_error = distance(truncated, operations(other['contours']))
            if quantization_error is not None and quantization_error <= tolerance:
                quantized.append(code)
            elif error < 1 and all(float(v).is_integer() for _, pts in operations(other['contours']) for point in pts for v in point):
                fixed_point.append(code)  # Matrix rounding before offset; report separately.
            else:
                failures.append({'code':code,'stage':'PFR reference','error':error})
        elif error is None:
            failures.append({'code':code,'stage':'PFR reference','error':error})
        if other and record['width'] != other['width']:
            failures.append({'code':code,'stage':'advance'})
        if error is not None:
            max_reference = max(max_reference, error)
        uni = unicode_code(code)
        if uni is None:
            continue
        converted += 1
        pen = RecordingPen()
        glyphs[cmap[uni]].draw(pen)
        error = distance(expected, normalize(pen.value))
        width = round(record['width'] * source['units'] / source['metric_units'])
        if error is None or error > tolerance or glyphs[cmap[uni]].width != width:
            failures.append({'code':code,'stage':'FontTools CFF','error':error})
        if error is not None:
            max_cff = max(max_cff, error)
    return {'pfr_records':len(source['glyphs']), 'cff_characters':converted,
        'tolerance_font_units':tolerance, 'max_reference_error':max_reference,
        'max_cff_error':max_cff, 'reference_integer_truncation_codes':quantized,
        'reference_other_fixed_point_codes':fixed_point,
        'reference_exact_records':len(source['glyphs'])-len(quantized)-len(fixed_point)-sum(f.get('stage')=='PFR reference' for f in failures),
        'reference_bit_exact':max_reference <= tolerance,
        'cff_passed':not any(f.get('stage')=='FontTools CFF' for f in failures),
        'failures':failures, 'passed_with_reported_fixed_point_differences':not failures}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source'); parser.add_argument('reference'); parser.add_argument('font')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    result = compare(args.source, args.reference, args.font)
    with Path(args.output).open('x', encoding='utf-8') as output:
        json.dump(result, output, indent=2)
    print(json.dumps(result))
    raise SystemExit(0 if result['passed_with_reported_fixed_point_differences'] else 1)
