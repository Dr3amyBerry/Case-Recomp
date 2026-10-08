"""Private Director content package for the Kotlin Director runtime.

One zip holds what the runtime needs to play the user's own movie: the movie bundle
(score, labels, cast table), the compiled-Lingo bundle, and each cast member's media
keyed by cast file and member number (RGBA PNG for bitmaps, SWF for Flash members,
WAV/MP3 for sounds). Packages built from commercial titles contain their media and
code: they are private, created locally, and must never be committed or shared.
"""
from __future__ import annotations

import json
import struct
import zipfile
from collections import Counter
from hashlib import sha256
from pathlib import Path

from .director import DirectorArchive, open_archive, read_local
from .inspector import InspectionError
from .lingo_bytecode import build_lingo_bundle
from .movie_bundle import _member_record, build_movie_bundle
from .relationships import CastRelationships
from .score import parse_cast_order

FORMAT = "case-recomp-director-content"
VERSION = 1
MAX_PACKAGE_BYTES = 1024 * 1024 * 1024
_ZIP_TIME = (1980, 1, 1, 0, 0, 0)


def cast_key(file_name: str) -> str:
    """Media folder of a cast file: its lower-case stem ("internal" for the movie's own cast)."""
    return Path(file_name).stem.lower() if file_name else "internal"


def _zip_write(zf: zipfile.ZipFile, name: str, data: bytes, compress: bool = True) -> None:
    info = zipfile.ZipInfo(name, _ZIP_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED if compress else zipfile.ZIP_STORED
    info.external_attr = 0o600 << 16
    zf.writestr(info, data)


def _bitmap(archive: DirectorArchive, rid: int, media: dict) -> bytes:
    from .bitmap import compose_jpeg_alpha
    from .pipeline import _as_png
    if "ediM" in media:
        data = archive.get_resource(media["ediM"])
        if not data.startswith(b"\xff\xd8"):
            raise InspectionError("bitmap ediM is not JPEG")
        if "ALFA" in media:
            return compose_jpeg_alpha(data, archive.get_resource(media["ALFA"]))[0]
        return _as_png(data)
    if "BITD" in media:
        from .bitmap import decode_bitd_indexed, decode_bitd_truecolor, parse_bitmap_cast_member
        info = parse_bitmap_cast_member(archive.get_resource(rid))
        decode = decode_bitd_indexed if info.indexed else decode_bitd_truecolor
        return decode(archive.get_resource(media["BITD"]), info)[0]
    raise InspectionError("bitmap member has no image data")


def _flash(archive: DirectorArchive, media: dict) -> bytes:
    data = archive.get_resource(media["XMED"])
    starts = [i for i in (data.find(b"FWS"), data.find(b"CWS")) if i >= 0]
    if not starts:
        raise InspectionError("Flash member holds no SWF")
    swf = data[min(starts):]
    if len(swf) < 8:
        raise InspectionError("truncated SWF")
    if swf.startswith(b"FWS"):
        size = struct.unpack_from("<I", swf, 4)[0]
        if size > len(swf):
            raise InspectionError("truncated SWF")
        swf = swf[:size]
    return swf


def _sound(archive: DirectorArchive, media: dict, ffmpeg: Path | None) -> tuple[bytes, str]:
    if "ediM" in media:
        data = archive.get_resource(media["ediM"])
        if data.startswith(b"ID3") or data[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"):
            return data, "mp3"
        raise InspectionError("unrecognised sound media")
    if "snd" in media:
        entry = archive.entries[media["snd"]]
        if archive._codec_name(entry.compression_index) == "unsupported":
            if ffmpeg is None:
                raise InspectionError("SWA sound needs FFmpeg")
            from .audio import decode_swa, swa_encoded_resource
            return decode_swa(swa_encoded_resource(archive, entry), ffmpeg)[0], "wav"
        data = archive.get_resource(media["snd"])
        if data.startswith(b"RIFF"):
            return data, "wav"
        raise InspectionError("unrecognised sound resource")
    raise InspectionError("sound member has no audio data")


def _archive_media(archive: DirectorArchive, key: str, ffmpeg: Path | None, skipped: Counter):
    order_ids = [rid for rid, entry in archive.entries.items() if entry.tag == "CAS*"]
    if not order_ids:
        return
    relations = CastRelationships(archive)
    for number, rid in enumerate(parse_cast_order(archive.get_resource(order_ids[0])), start=1):
        if not rid:
            continue
        member = _member_record(archive, relations, rid, number)
        media = member.get("media", {})
        kind = member["type"] if member["type"] != "xtra" else member.get("xtra", "xtra")
        try:
            if kind == "bitmap":
                yield f"media/{key}/{number}.png", _bitmap(archive, rid, media), key, number, kind
            elif kind == "flash":
                yield f"media/{key}/{number}.swf", _flash(archive, media), key, number, kind
            elif kind == "sound":
                data, ext = _sound(archive, media, ffmpeg)
                yield f"media/{key}/{number}.{ext}", data, key, number, kind
        except InspectionError as exc:
            skipped[f"{kind}: {exc}"] += 1


def build_director_content(movie: Path, casts: list[Path], output: Path, *, ffmpeg: Path | None = None) -> dict:
    """Write a private, deterministic content zip (create-only) and return its manifest."""
    if output.exists():
        raise InspectionError("output already exists")
    movie_bundle = build_movie_bundle(movie, casts)
    lingo_bundle = build_lingo_bundle(movie)
    entries: list[dict] = []
    skipped: Counter = Counter()
    total = 0
    tmp = output.with_name(output.name + ".partial")
    if tmp.exists():
        raise InspectionError("stale partial output exists")
    try:
        with zipfile.ZipFile(tmp, "x") as zf:
            def add(name: str, data: bytes, **meta) -> None:
                nonlocal total
                total += len(data)
                if total > MAX_PACKAGE_BYTES:
                    raise InspectionError("content package exceeds safety cap")
                # Media is already compressed; JSON benefits from deflate.
                _zip_write(zf, name, data, compress=name.endswith(".json"))
                entries.append({"path": name, "bytes": len(data), "sha256": sha256(data).hexdigest(), **meta})

            add("movie.json", json.dumps(movie_bundle, ensure_ascii=False, sort_keys=True).encode("utf-8"))
            add("lingo.json", json.dumps(lingo_bundle, ensure_ascii=False, sort_keys=True).encode("utf-8"))
            sources = [(movie, "")] + [(path, path.name) for path in sorted(casts, key=lambda p: p.name.lower())]
            for path, name in sources:
                archive, _ = open_archive(path)
                for item_path, data, key, number, kind in _archive_media(archive, cast_key(name), ffmpeg, skipped):
                    add(item_path, data, cast=key, member=number, kind=kind)
            manifest = {
                "format": FORMAT, "version": VERSION,
                "source_sha256": {cast_key(name): sha256(read_local(path)).hexdigest() for path, name in sources},
                "entries": entries,
                "counts": dict(sorted(Counter(e.get("kind", "bundle") for e in entries).items())),
                "skipped": dict(sorted(skipped.items())),
                "notice": "private media and compiled code of the user's own title; do not redistribute",
            }
            _zip_write(zf, "manifest.json", json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=1).encode("utf-8"))
        tmp.replace(output)
    except BaseException:
        tmp.unlink(missing_ok=True)
        raise
    return manifest
