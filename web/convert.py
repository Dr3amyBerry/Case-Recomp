"""Browser converter (runs in Pyodide): a game folder -> content ZIP for the app.

The page writes the user's chosen folder into Pyodide's in-memory file system; nothing
leaves the user's computer. Director games give projectors (.exe), movies and external
casts; SDA games (The Vegas Heist) give their Resources.dll.
"""
from __future__ import annotations

import json
from pathlib import Path

from caserecomp.director import embedded_movie, read_local
from caserecomp.director_content import build_director_content
from caserecomp.inspector import InspectionError
from caserecomp.movie_bundle import CAST_SUFFIXES

MOVIE_SUFFIXES = {".exe", ".dir", ".dxr", ".dcr"}

# Specific signatures first: Prime Suspects includes Huntsville's cast subset.
# Detection identifies a candidate; it does not certify runtime compatibility.
TITLES = [
    {"id": "prime_suspects", "name": "Mystery Case Files: Prime Suspects", "ready": True,
     "casts": {"dat1.cct", "dat22.cct", "01.cct", "29.cct"}, "cover": None},
    {"id": "huntsville", "name": "Mystery Case Files: Huntsville", "ready": True,
     "casts": {"dat1.cct", "dat16.cct", "01.cct", "21.cct"}, "cover": "newLogo"},
    {"id": "ravenhearst", "name": "Mystery Case Files: Ravenhearst", "ready": True,
     "casts": {"interface_us.cct", "room1.cct", "room32.cct", "puzz.cct", "sfx.cct"}, "cover": "title"},
]

# SDA titles are recognised by RAWDATA entries inside Resources.dll.
SDA_TITLES = [
    {"id": "vegas_heist", "name": "Mystery P.I.: The Vegas Heist", "ready": True,
     "resources": {"LEVELS_1.XUI", "UNLIMITED.XUI", "ENVS.MSE"}},
]


def _sda_scan(files: list[Path]):
    """(Resources.dll path, SDA title) for an SDA game folder, else None."""
    for path in files:
        if path.name.lower() != "resources.dll":
            continue
        from runtime import Resources
        try:
            entries = set(Resources(path).entries)
        except (ValueError, OSError):
            continue
        title = next((t for t in SDA_TITLES if t["resources"] <= entries), None)
        if title:
            return path, title, len(entries)
    return None


def _movie_candidates(files: list[Path]) -> list[Path]:
    found = []
    for path in files:
        suffix = path.suffix.lower()
        if suffix == ".exe":
            try:
                embedded_movie(read_local(path))
            except (InspectionError, ValueError, OSError):
                continue
            found.append(path)
        elif suffix in MOVIE_SUFFIXES:
            found.append(path)
    # A projector beside its casts first, then the largest file.
    return sorted(found, key=lambda p: (p.suffix.lower() != ".exe", -p.stat().st_size))


def scan(root: str) -> str:
    """JSON report of what the folder holds: the game movie, its casts, the title detected."""
    files = [p for p in Path(root).rglob("*") if p.is_file()]
    sda = _sda_scan(files)
    if sda:
        path, title, count = sda
        return json.dumps({"engine": "sda", "movie": None, "movie_name": path.name, "resources": count,
                           "casts": [], "title": {k: title[k] for k in ("id", "name", "ready")}})
    movies = _movie_candidates(files)
    casts: dict[str, Path] = {}
    for path in sorted(files):
        if path.suffix.lower() in CAST_SUFFIXES:
            casts.setdefault(path.name.lower(), path)
    title = next((t for t in TITLES if t["casts"] <= set(casts)), None)
    return json.dumps({
        "engine": "director",
        "movie": str(movies[0]) if movies else None,
        "movie_name": movies[0].name if movies else None,
        "casts": sorted(p.name for p in casts.values()),
        "title": {k: title[k] for k in ("id", "name", "ready")} if title else None,
    })


def convert(root: str, output: str) -> str:
    """Build the package for the app at [output]; returns a JSON summary."""
    report = json.loads(scan(root))
    if report["engine"] == "sda":
        from package_vegas import build_package
        path, title, _ = _sda_scan([p for p in Path(root).rglob("*") if p.is_file()])
        build_package(path, Path(output), full_campaign=True)
        import zipfile
        with zipfile.ZipFile(output) as archive:
            entries = len(archive.namelist())
        return json.dumps({"title": report["title"], "entries": entries, "extension": ".sda.zip"})
    if not report["movie"]:
        raise InspectionError("no Director game was found in this folder")
    files = [p for p in Path(root).rglob("*") if p.is_file() and p.suffix.lower() in CAST_SUFFIXES]
    casts: dict[str, Path] = {}
    for path in sorted(files):
        casts.setdefault(path.name.lower(), path)
    title = next((t for t in TITLES if report["title"] and t["id"] == report["title"]["id"]), None)
    manifest = build_director_content(Path(report["movie"]), list(casts.values()), Path(output),
                                      cover=title["cover"] if title else None)
    return json.dumps({"title": report["title"], "entries": len(manifest["entries"]), "extension": ".director.zip",
                       "counts": manifest["counts"], "skipped": manifest["skipped"],
                       "cover": manifest.get("cover")})
