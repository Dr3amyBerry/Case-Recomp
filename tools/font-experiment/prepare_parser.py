"""Prepare a patched research copy; never edit the external checkout."""
import argparse
import hashlib
import re
from pathlib import Path

SOURCE_SHA256 = '8e411d7ca096d6d8d0e61b5a433202766a6006c8df26891da8f28bf5eaa77696'


def prepare(source, output):
    source, output = Path(source), Path(output)
    data = source.read_bytes()
    if hashlib.sha256(data).hexdigest() != SOURCE_SHA256:
        raise ValueError('parser differs from pinned LibreShockwave revision')
    lines = data.decode('utf-8').splitlines(keepends=True)
    patch = Path(__file__).with_name('pfr1-implicit-direction.patch').read_text(encoding='utf-8-sig').splitlines()
    result, cursor, index = [], 0, 0
    while index < len(patch):
        match = re.match(r'@@ -(\d+)(?:,\d+)? \+\d+(?:,\d+)? @@', patch[index])
        if not match:
            index += 1
            continue
        start = int(match[1])-1
        result.extend(lines[cursor:start]); cursor = start; index += 1
        while index < len(patch) and not patch[index].startswith('@@'):
            line = patch[index]; index += 1
            if line.startswith((' ', '-')):
                if lines[cursor].rstrip('\r\n') != line[1:]:
                    raise ValueError('patch context mismatch')
                cursor += 1
            if line.startswith((' ', '+')):
                result.append(line[1:]+'\n')
    result.extend(lines[cursor:])
    with output.open('x', encoding='utf-8', newline='\n') as handle:
        handle.write(''.join(result))
    return hashlib.sha256(output.read_bytes()).hexdigest()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source'); parser.add_argument('output')
    args = parser.parse_args()
    print(prepare(args.source, args.output))
