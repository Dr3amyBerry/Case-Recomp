"""Structural, offline comparison of recovered Lingo and bytecode listings.

The module never executes recovered Lingo. Source-text comparison is deliberately
conservative, while assembly comparison checks only handler boundaries plus
instruction addresses/opcode mnemonics; operands and runtime semantics remain
outside the claim.
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
ASM_INSTRUCTION = re.compile(r"^\s*\[\s*(\d+)\s*\]\s+([A-Za-z][A-Za-z0-9_]*)\b", re.I)
MAX_FILES = 4096
MAX_SCRIPT_BYTES = 2 * 1024 * 1024
SUPPORTED_ENCODINGS = {"utf-8", "mac_roman", "cp1252"}


def _encoding(value: str) -> str:
    normalized = value.lower().replace("-", "_")
    aliases = {"utf_8": "utf-8", "macroman": "mac_roman", "mac_roman": "mac_roman", "cp1252": "cp1252"}
    result = aliases.get(normalized, value.lower())
    if result not in SUPPORTED_ENCODINGS:
        raise InspectionError("unsupported recovered-Lingo text encoding")
    return result


def _text(path: Path, encoding: str) -> str:
    try:
        # utf-8-sig preserves the existing BOM-friendly behavior.
        codec = "utf-8-sig" if encoding == "utf-8" else encoding
        return read_local(path).decode(codec, "strict")
    except UnicodeDecodeError:
        raise InspectionError(f"recovered Lingo file is not valid {encoding}; choose encoding explicitly") from None


def handler_bodies(text: str) -> list[tuple[str, str]]:
    """Return (case-folded name, normalized text hash) for complete handlers."""
    found = []
    current_name = None
    body = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("--"):
            continue
        start = HANDLER_START.match(line)
        if start:
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


def assembly_handlers(text: str) -> list[tuple[str, str]]:
    """Hash handler instruction-address/opcode sequences, deliberately ignoring operands."""
    found = []
    current_name = None
    instructions: list[str] = []
    for line in text.splitlines():
        start = HANDLER_START.match(line)
        if start:
            current_name = start.group(2).casefold()
            instructions = []
            continue
        if current_name is None:
            continue
        instruction = ASM_INSTRUCTION.match(line)
        if instruction:
            address = int(instruction.group(1))
            opcode = instruction.group(2).replace("_", "").casefold()
            instructions.append(f"{address}:{opcode}")
        if HANDLER_END.match(line):
            found.append((current_name, sha256("\n".join(instructions).encode()).hexdigest()))
            current_name = None
            instructions = []
    return found


def _scan(path: Path, *, encoding: str = "utf-8", suffix: str = ".ls",
          parser=handler_bodies) -> dict[str, list[str]]:
    encoding = _encoding(encoding)
    if path.is_symlink() or not path.is_dir():
        raise InspectionError("Lingo recovery input must be an existing local directory")
    results: dict[str, list[str]] = defaultdict(list)
    count = 0
    for f in sorted(path.rglob("*")):
        if f.is_symlink():
            raise InspectionError("Lingo input contains a symbolic link")
        if not f.is_file() or f.suffix.lower() != suffix:
            continue
        count += 1
        if count > MAX_FILES or f.stat().st_size > MAX_SCRIPT_BYTES:
            raise InspectionError("Lingo input exceeds file count or size cap")
        for name, body_hash in parser(_text(f, encoding)):
            results[name].append(body_hash)
    return dict(results)


def _encoded(names, redact: bool):
    return [sha256(name.encode()).hexdigest() if redact else name for name in sorted(names)]


def compare_directories(left: Path, right: Path, *, redact: bool = False,
                        left_encoding: str = "utf-8", right_encoding: str = "utf-8") -> dict:
    """Compare handler names/text across two recovery outputs; never execute Lingo."""
    a = _scan(left, encoding=left_encoding)
    b = _scan(right, encoding=right_encoding)
    if not a and not b:
        raise InspectionError("neither directory contains complete Lingo handlers")
    shared = set(a) & set(b)
    unique = sorted(name for name in shared if len(a[name]) == len(b[name]) == 1)
    match = [name for name in unique if a[name][0] == b[name][0]]
    differs = [name for name in unique if a[name][0] != b[name][0]]
    ambiguous = sorted(name for name in shared if name not in unique)
    return {
        "schema_version": 2,
        "left_encoding": _encoding(left_encoding), "right_encoding": _encoding(right_encoding),
        "left_handlers": sum(map(len, a.values())), "right_handlers": sum(map(len, b.values())),
        "shared_names": len(shared), "same_name_and_normalized_text": len(match),
        "different_normalized_text": len(differs), "ambiguous_multiple_definitions": len(ambiguous),
        "left_only": _encoded(set(a) - set(b), redact), "right_only": _encoded(set(b) - set(a), redact),
        "matching": _encoded(match, redact), "different": _encoded(differs, redact),
        "ambiguous": _encoded(ambiguous, redact), "name_redaction": "sha256" if redact else "none",
        "semantics_verified": False,
        "caveat": "Handler names and normalized decompiler text do not prove semantic or runtime parity",
    }


def compare_assembly_directories(left: Path, right: Path, *, redact: bool = False,
                                 left_encoding: str = "utf-8", right_encoding: str = "utf-8") -> dict:
    """Compare ProjectorRays .lasm against LibreShockwave .lsasm structurally."""
    a = _scan(left, encoding=left_encoding, suffix=".lasm", parser=assembly_handlers)
    b = _scan(right, encoding=right_encoding, suffix=".lsasm", parser=assembly_handlers)
    if not a and not b:
        raise InspectionError("neither directory contains assembly handler listings")
    shared = set(a) & set(b)
    exact_names = []
    matched_records = 0
    for name in shared:
        ca, cb = Counter(a[name]), Counter(b[name])
        matched_records += sum((ca & cb).values())
        if ca == cb:
            exact_names.append(name)
    return {
        "schema_version": 1,
        "left_handlers": sum(map(len, a.values())), "right_handlers": sum(map(len, b.values())),
        "shared_names": len(shared), "exact_address_opcode_multiset_names": len(exact_names),
        "matched_handler_records": matched_records,
        "left_only": _encoded(set(a) - set(b), redact), "right_only": _encoded(set(b) - set(a), redact),
        "different_structure_names": _encoded(shared - set(exact_names), redact),
        "name_redaction": "sha256" if redact else "none",
        "operands_compared": False, "semantics_verified": False,
        "caveat": "Address/opcode sequence identity ignores operands, decompiler expressions, and runtime behavior",
    }


def compare_against_movie(left: Path, right: Path, movie: Path, *, redact: bool = True,
                          left_encoding: str = "utf-8", right_encoding: str = "utf-8") -> dict:
    """Compare recovered handler names to the validated original Lscr index."""
    from .lingo_index import index_movie

    index = index_movie(movie, redact=False)
    original = Counter(row["name"].casefold() for row in index["handlers"])
    a = _scan(left, encoding=left_encoding)
    b = _scan(right, encoding=right_encoding)
    original_names = set(original)
    a_names, b_names = set(a), set(b)
    return {
        "schema_version": 1,
        "original_compiled_scripts": index["script_count"], "original_compiled_handlers": index["handler_count"],
        "original_distinct_handler_names": len(original_names), "left_distinct_handler_names": len(a_names),
        "right_distinct_handler_names": len(b_names),
        "left_matching_original_names": len(original_names & a_names),
        "right_matching_original_names": len(original_names & b_names),
        "both_matching_original_names": len(original_names & a_names & b_names),
        "missing_from_both": _encoded(original_names - a_names - b_names, redact),
        "left_unindexed": _encoded(a_names - original_names, redact),
        "right_unindexed": _encoded(b_names - original_names, redact),
        "name_redaction": "sha256" if redact else "none",
        "bytecode_to_source_equivalence_verified": False, "semantics_verified": False,
        "note": "Only handler-name set intersections are compared; original bytecode is NOT reconstructed as Lingo text",
    }
