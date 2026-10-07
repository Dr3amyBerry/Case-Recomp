"""Structural, offline comparison of recovered Lingo handler texts.

The tool does not claim semantic equivalence, recover bytecode, or call third
party software. It accepts locally produced .ls texts from separate providers.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from hashlib import sha256
from pathlib import Path
import re

from .director import read_local
from .inspector import InspectionError

HANDLER_START = re.compile(r"^\s*(on|function)\s+([A-Za-z_][A-Za-z0-9_]*)\b", re.I)
HANDLER_END = re.compile(r"^\s*end(?:\s+[A-Za-z_][A-Za-z0-9_]*)?\s*(?:--.*)?$", re.I)
MAX_FILES = 4096
MAX_SCRIPT_BYTES = 2 * 1024 * 1024


def handler_bodies(text: str) -> list[tuple[str, str]]:
    """Return (case-folded name, normalized *text* hash) for complete handlers.

    This is deliberately a conservative text comparison, not a Lingo parser:
    comments, strings, formatting and decompiler choices may affect equivalence.
    """
    found = []
    current_name = None
    body = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("--"):
            continue
        start = HANDLER_START.match(line)
        if start:
            # Incomplete handler, or multiple nested declarations: discard the earlier one.
            current_name = start.group(2).casefold()
            body = [re.sub(r"\s+", " ", stripped.casefold())]
            continue
        if current_name is not None:
            if stripped:
                body.append(re.sub(r"\s+", " ", stripped.casefold()))
            if HANDLER_END.match(line):
                found.append((current_name, sha256("\n".join(body).encode("utf-8")).hexdigest()))
                current_name = None
                body = []
    return found


def _scan(path: Path) -> dict[str, list[str]]:
    if path.is_symlink() or not path.is_dir():
        raise InspectionError("Lingo recovery input must be an existing local directory")
    results: dict[str, list[str]] = defaultdict(list)
    count = 0
    for f in sorted(path.rglob("*")):
        if f.is_symlink():
            raise InspectionError("Lingo input contains a symbolic link")
        if not f.is_file() or f.suffix.lower() != ".ls":
            continue
        count += 1
        if count > MAX_FILES or f.stat().st_size > MAX_SCRIPT_BYTES:
            raise InspectionError("Lingo input exceeds file count or size cap")
        try:
            text = read_local(f).decode("utf-8-sig", "strict")
        except UnicodeDecodeError:
            # Director output can carry text in Western code pages; do not silently guess.
            raise InspectionError("recovered Lingo file is not UTF-8; convert explicitly first") from None
        for name, body_hash in handler_bodies(text):
            results[name].append(body_hash)
    return dict(results)


def compare_directories(left: Path, right: Path, *, redact: bool = False) -> dict:
    """Compare unique handler names and text-normalized bodies, never execute Lingo."""
    a, b = _scan(left), _scan(right)
    if not a and not b:
        raise InspectionError("neither directory contains complete Lingo handlers")
    shared = set(a) & set(b)
    unique = sorted(name for name in shared if len(a[name]) == len(b[name]) == 1)
    match = [name for name in unique if a[name][0] == b[name][0]]
    differs = [name for name in unique if a[name][0] != b[name][0]]
    ambiguous = sorted(name for name in shared if name not in unique)
    def encode(names):
        return [sha256(name.encode()).hexdigest() if redact else name for name in sorted(names)]
    return {
        "schema_version": 1,
        "left_handlers": sum(map(len, a.values())),
        "right_handlers": sum(map(len, b.values())),
        "shared_names": len(shared),
        "same_name_and_normalized_text": len(match),
        "different_normalized_text": len(differs),
        "ambiguous_multiple_definitions": len(ambiguous),
        "left_only": encode(set(a) - set(b)),
        "right_only": encode(set(b) - set(a)),
        "matching": encode(match),
        "different": encode(differs),
        "ambiguous": encode(ambiguous),
        "name_redaction": "sha256" if redact else "none",
        "semantics_verified": False,
        "caveat": "Handler names and normalized decompiler text do not prove semantic or runtime parity",
    }
