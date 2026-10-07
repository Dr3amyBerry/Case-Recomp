"""Explicit executable checks for operator-selected local tools.

POSIX relies on the owner execute bit. Windows has no execute bit, so a tool is
accepted only when it is a PE image with a Windows executable suffix, or a
Python script with a ``#!...python`` first line, which is run by the current
interpreter. Anything else is refused instead of being handed to the OS loader.
"""
from __future__ import annotations

import os
import stat
import sys
from pathlib import Path

from .inspector import InspectionError

WINDOWS_EXECUTABLE_SUFFIXES = {".exe", ".com"}


def tool_command(path: Path, *, windows: bool | None = None) -> list[str]:
    """Return the argv prefix that runs ``path`` or raise if it is not executable."""
    if windows is None:
        windows = os.name == "nt"
    if path.is_symlink() or not path.is_file():
        raise InspectionError("external tool must be an existing regular file")
    if not windows:
        if not path.stat().st_mode & stat.S_IXUSR:
            raise InspectionError("external tool is not executable")
        return [str(path)]
    with path.open("rb") as handle:
        lead = handle.read(256)
    if lead.startswith(b"MZ") and path.suffix.lower() in WINDOWS_EXECUTABLE_SUFFIXES:
        return [str(path)]
    first_line = lead.split(b"\n", 1)[0]
    if first_line.startswith(b"#!") and b"python" in first_line:
        return [sys.executable, str(path)]
    raise InspectionError("external tool is not executable")
