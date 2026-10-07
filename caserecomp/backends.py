"""Optional, explicit adapters for separately installed Director recovery tools.

No third-party code/binaries are bundled, fetched, or executed implicitly.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import stat
import subprocess
import tempfile
from pathlib import Path

from .director import embedded_movie, exclusive_write, open_archive, read_local
from .inspector import InspectionError
from .pipeline import MAX_ASSET_COUNT, MAX_EXPORT_BYTES, guard_destination

BACKENDS = {"projectorrays", "libreshockwave"}


def _binary(path: Path) -> Path:
    if path.is_symlink() or not path.is_file():
        raise InspectionError("external tool must be an existing regular file")
    info = path.stat()
    if not info.st_mode & stat.S_IXUSR:
        raise InspectionError("external tool is not executable")
    return path.absolute()


def _stage_archive(source: Path, destination: Path) -> Path:
    contents = read_local(source)
    if contents.startswith(b"MZ"):
        _, contents = embedded_movie(contents)
        name = "movie.dcr"
    else:
        archive, _ = open_archive(source)
        name = "movie.dcr" if archive.kind == "movie" else "cast.cct"
    dest = destination / name
    exclusive_write(dest, contents)
    return dest


def _copy_results(source: Path, target: Path, *, allowed_extensions: set[str] | None = None) -> tuple[list[dict], int]:
    assets, total = [], 0
    for file in sorted(source.rglob("*")):
        if file.is_symlink():
            raise InspectionError("external tool produced a symbolic link")
        if not file.is_file():
            continue
        relative = file.relative_to(source)
        if any(part.startswith(".") or part in ("..", ".") for part in relative.parts):
            raise InspectionError("external tool produced unsafe filename")
        if allowed_extensions and file.suffix.lower() not in allowed_extensions:
            continue
        if len(assets) >= MAX_ASSET_COUNT or file.stat().st_size + total > MAX_EXPORT_BYTES:
            raise InspectionError("external output exceeds safety cap")
        data = read_local(file)
        output = target / relative
        output.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
        exclusive_write(output, data)
        assets.append({"file": relative.as_posix(), "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                       "format": "recovered-lingo-unverified" if file.suffix.lower() == ".ls" else "external-unknown",
                       "converted": "unverified"})
        total += len(data)
    if not assets:
        raise InspectionError("tool returned no recognized export files")
    return assets, total


def run_backend(provider: str, source: Path, destination: Path, binary: Path, *, timeout: int = 180) -> dict:
    """Execute an operator-installed tool on private staged data and capture its outputs.

    This executes third-party native code! Run only trusted binaries in a sandbox.
    It is never invoked as part of public CI or normal local conversion.
    """
    if provider not in BACKENDS:
        raise InspectionError("unknown optional backend")
    if not 1 <= timeout <= 3600:
        raise InspectionError("tool timeout must be 1..3600 seconds")
    guard_destination(source, destination)
    if source.is_dir():
        raise InspectionError("external adapter accepts one source file per run")
    executable = _binary(binary)
    # Resolve and verify before permitting any external execution.
    archive, _ = open_archive(source)
    with tempfile.TemporaryDirectory(prefix="caserecomp-tool-", dir=destination.parent) as temporary:
        root = Path(temporary)
        staged = root / "input"
        staged.mkdir(mode=0o700)
        staged_file = _stage_archive(source, staged)
        produced = root / "generated"
        produced.mkdir(mode=0o700)
        if provider == "projectorrays":
            # Explicit output directory prevents changes to staged original and
            # enables ProjectorRays' opt-in Lingo script dump.
            args = [str(executable), "decompile", "--dump-scripts", "-o", str(produced), str(staged_file)]
        else:
            args = [str(executable), str(staged), str(produced)]
        try:
            completed = subprocess.run(args, cwd=root, capture_output=True, timeout=timeout, check=False,
                                       env={"PATH": os.environ.get("PATH", ""), "HOME": str(root)}, shell=False)
        except (subprocess.TimeoutExpired, OSError) as exc:
            raise InspectionError("external tool failed to start or exceeded timeout") from exc
        if completed.returncode != 0:
            raise InspectionError(f"external tool returned nonzero exit status {completed.returncode}")
        if provider == "projectorrays":
            # Dedicated output also contains casts/*.ls where supported.
            results_root = produced
            allowed = {".cst", ".dir", ".ls"}
        else:
            results_root = produced
            allowed = None
        if not any(p.is_file() and (allowed is None or p.suffix.lower() in allowed) for p in results_root.rglob("*")):
            raise InspectionError("external tool produced no expected outputs")
        destination.mkdir(mode=0o700)
        try:
            assets, total = _copy_results(results_root, destination, allowed_extensions=allowed)
            record = {
                "schema_version": 1,
                "source_archives": [{"index": 0, "name": source.name, "kind": archive.kind,
                                     "director_version": archive.version,
                                     "sha256": hashlib.sha256(read_local(source)).hexdigest()}],
                "backend": provider, "verified_semantics": False,
                "warnings": ["Tool outputs have been hashed, not semantically validated as working game media or Lingo",
                             "Do not upload third-party decompiled data or recovered game files"],
                "asset_count": len(assets), "bytes": total, "assets": assets,
            }
            exclusive_write(destination / "manifest.json", (json.dumps(record, indent=2, sort_keys=True) + "\n").encode())
            return record
        except BaseException:
            shutil.rmtree(destination)
            raise
