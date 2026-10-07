"""Deterministic local-only asset conversion for supported Director members.

Proprietary assets stay on the operator's machine. Unknown formats are skipped
rather than silently mislabeled as valid Android media.
"""
from __future__ import annotations

from collections import Counter
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import json
import wave

from .director import open_archive, read_local, exclusive_write
from .inspector import InspectionError
from .relationships import CastRelationships

MAX_ASSET_COUNT = 12000
MAX_EXPORT_BYTES = 512 * 1024 * 1024
MAX_IMAGE_PIXELS = 32_000_000
SUPPORTED_INPUT = {".cct", ".dcr", ".exe", ".bin"}


def _inside(path: Path, parent: Path) -> bool:
    return path == parent or parent in path.parents


def guard_destination(source: Path, destination: Path) -> None:
    """Refuse unexpected tree mutations, symlinked parents, and repo outputs."""
    source = source.absolute()
    dest = destination.absolute()
    if dest.exists() or dest.is_symlink():
        raise InspectionError("output directory already exists; refusing overwrite")
    if not dest.parent.is_dir():
        raise InspectionError("output parent must already exist")
    for node in (dest.parent, *dest.parent.parents):
        if node.is_symlink():
            raise InspectionError("output parent includes a symbolic link")
        if (node / ".git").exists():
            raise InspectionError("refusing to write extracted commercial assets inside a Git repository")
    if source.is_dir() and _inside(dest, source):
        raise InspectionError("output must be outside source tree")
    if source.is_symlink():
        raise InspectionError("source may not be a symbolic link")


def input_archives(source: Path, *, limit: int = 100) -> list[Path]:
    if source.is_symlink():
        raise InspectionError("refusing symbolic link input")
    if source.is_file():
        return [source]
    if not source.is_dir():
        raise InspectionError("input must be a file or a directory")
    items = []
    for path in sorted(source.rglob("*")):
        if path.is_symlink():
            raise InspectionError("input tree includes symbolic link")
        if path.is_file() and (path.suffix.lower() in SUPPORTED_INPUT or path.name.lower().endswith(".cct.bin")):
            items.append(path)
            if len(items) > limit:
                raise InspectionError("too many input archives")
    if not items:
        raise InspectionError("no Director containers found in source directory")
    return items


def _jpeg_dimensions(data: bytes) -> tuple[int, int]:
    """Validate a JPEG by decoding in Pillow, including dimensions and truncation."""
    try:
        from PIL import Image, UnidentifiedImageError
    except ImportError as exc:
        raise InspectionError("image decoding requires: pip install '.[images]'") from exc
    if len(data) < 4 or not data.startswith(b"\xff\xd8") or not data.endswith(b"\xff\xd9"):
        raise InspectionError("not a complete JPEG image")
    try:
        with Image.open(BytesIO(data)) as picture:
            if picture.format != "JPEG":
                raise InspectionError("resource is not JPEG")
            width, height = picture.size
            if width < 1 or height < 1 or width * height > MAX_IMAGE_PIXELS:
                raise InspectionError("JPEG dimensions exceed cap")
            picture.load()  # force full decode, not just marker sniffing
            return width, height
    except (ValueError, OSError, UnidentifiedImageError, SyntaxError) as exc:
        raise InspectionError("cannot decode JPEG image") from exc


def _as_png(data: bytes) -> bytes:
    from PIL import Image
    with Image.open(BytesIO(data)) as picture:
        picture.load()
        result = BytesIO()
        picture.convert("RGB").save(result, format="PNG", optimize=True)
        return result.getvalue()


def _wave_metadata(data: bytes) -> dict:
    try:
        with wave.open(BytesIO(data), "rb") as reader:
            channels, sample_width = reader.getnchannels(), reader.getsampwidth()
            rate, frames = reader.getframerate(), reader.getnframes()
            if not 1 <= channels <= 8 or not 1 <= sample_width <= 4:
                raise InspectionError("unsupported WAV channel count or bit depth")
            if not 8000 <= rate <= 192000 or frames > 30 * 60 * rate:
                raise InspectionError("WAV size or sample rate exceeds cap")
            if len(reader.readframes(frames)) != frames * channels * sample_width:
                raise InspectionError("truncated PCM audio samples")
            return {"channels": channels, "rate": rate, "frames": frames, "sample_width": sample_width}
    except (wave.Error, EOFError, ValueError, OSError) as exc:
        raise InspectionError("invalid PCM WAV stream") from exc


def _mp3_metadata(data: bytes) -> dict:
    """Validate the ID3/MPEG Audio stream enough to identify playable MP3 data."""
    if not data.startswith(b"ID3") or len(data) < 64:
        raise InspectionError("resource is not a supported ID3/MP3 stream")
    try:
        from mutagen.mp3 import MP3
        from mutagen import MutagenError
    except ImportError as exc:
        raise InspectionError("MP3 probing requires: pip install '.[media]'") from exc
    try:
        stream = MP3(BytesIO(data))
        duration = stream.info.length
        rate = stream.info.sample_rate
        channels = stream.info.channels
        bitrate = stream.info.bitrate
        if not 0 < duration <= 1800 or not 8000 <= rate <= 192000 or not 1 <= channels <= 2 or bitrate <= 0:
            raise InspectionError("MP3 stream metadata violates safety bounds")
        return {"rate": rate, "channels": channels, "bitrate": bitrate, "duration_seconds": round(duration, 3)}
    except (MutagenError, ValueError, OSError, TypeError) as exc:
        raise InspectionError("invalid MPEG audio stream") from exc


def _publish_asset(root: Path, relative: str, data: bytes, entry: dict) -> dict:
    if len(data) > MAX_EXPORT_BYTES:
        raise InspectionError("single export exceeds size cap")
    name = root / relative
    name.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    exclusive_write(name, data)
    return {**entry, "file": relative, "bytes": len(data), "sha256": sha256(data).hexdigest()}


def convert_local(source: Path, destination: Path, *, image_format: str = "jpg", include_bytecode: bool = False,
                  include_raw: bool = False, max_assets: int = MAX_ASSET_COUNT) -> dict:
    """Convert validated JPEG/PCM and optionally export Lingo bytecode or opaque raw members.

    Unknown formats are reported in counts, not declared converted. All writes
    are local, create-only, with rollback on error.
    """
    if image_format not in ("jpg", "png"):
        raise InspectionError("supported image formats are jpg and png")
    if not 1 <= max_assets <= MAX_ASSET_COUNT:
        raise InspectionError("invalid max asset cap")
    guard_destination(source, destination)
    paths = input_archives(source)
    archives = []
    for path in paths:
        try:
            archive, _ = open_archive(path)
        except InspectionError:
            if source.is_file():
                raise
            # Siblings such as gallinita.exe are not Director archives.
            continue
        archives.append((path, archive))
    if not archives:
        raise InspectionError("no valid Director archive found")
    records = []
    link_tables = []
    skipped = Counter()
    totals = Counter()
    output_bytes = 0
    destination.mkdir(mode=0o700)
    try:
        for index, (path, archive) in enumerate(archives):
            folder = f"archive-{index:03d}"
            try:
                links = CastRelationships(archive)
                image_owners = links.image_alpha_links(archive)
                link_tables.append({"archive_index": index, "status": "parsed", **links.summary(archive)})
            except InspectionError as exc:
                # Degrade explicitly for older/invalid KEY* without inventing a pairing.
                image_owners = {}
                link_tables.append({"archive_index": index, "status": "unavailable", "reason": str(exc)})
            for entry in sorted(archive.entries.values(), key=lambda r: r.id):
                totals[entry.tag] += 1
                if entry.tag == "ediM":
                    data = archive.get_resource(entry.id)
                    if data.startswith(b"ID3"):
                        try:
                            extra = {"format": "mp3-id3", "metadata_validated": True, **_mp3_metadata(data)}
                        except InspectionError:
                            skipped["invalid_MP3"] += 1
                            continue
                        relative = f"{folder}/audio/{entry.id:08d}.mp3"
                    elif data.startswith(b"\xff\xd8"):
                        try:
                            w, h = _jpeg_dimensions(data)
                        except InspectionError:
                            skipped["invalid_ediM"] += 1
                            continue
                        if image_format == "png":
                            data = _as_png(data)
                        ext = ".png" if image_format == "png" else ".jpg"
                        relative = f"{folder}/images/{entry.id:08d}{ext}"
                        extra = {"format": image_format, "width": w, "height": h, "alpha_applied": False}
                        if entry.id in image_owners:
                            extra.update(image_owners[entry.id])
                    else:
                        skipped["unrecognized_ediM"] += 1
                        continue
                elif entry.tag == "Lscr" and include_bytecode:
                    data = archive.get_resource(entry.id)
                    relative = f"{folder}/bytecode/{entry.id:08d}.lscr"
                    extra = {"format": "compiled-lingo", "decompiled": False}
                elif entry.tag == "snd ":
                    if archive._codec_name(entry.compression_index) == "unsupported":
                        skipped["unsupported_SWA"] += 1
                        continue
                    data = archive.get_resource(entry.id)
                    if not data.startswith(b"RIFF"):
                        skipped["unknown_audio"] += 1
                        continue
                    try:
                        extra = {"format": "pcm-wav", **_wave_metadata(data)}
                    except InspectionError:
                        skipped["invalid_wav"] += 1
                        continue
                    relative = f"{folder}/audio/{entry.id:08d}.wav"
                elif entry.tag in {"ALFA", "BITD", "XMED", "Lnam", "CASt"} and include_raw:
                    try:
                        data = archive.get_resource(entry.id)
                    except InspectionError:
                        skipped["opaque_undecodable"] += 1
                        continue
                    relative = f"{folder}/raw/{entry.id:08d}-{entry.tag}.bin"
                    extra = {"format": "opaque", "converted": False}
                else:
                    skipped[entry.tag] += 1
                    continue
                if len(records) >= max_assets or output_bytes + len(data) > MAX_EXPORT_BYTES:
                    raise InspectionError("aggregate export safety limit exceeded")
                record = _publish_asset(destination, relative, data, {"archive_index": index, "resource_id": entry.id, "tag": entry.tag, **extra})
                output_bytes += len(data)
                records.append(record)
        manifest = {
            "schema_version": 1,
            "source_archives": [{"index": index, "name": path.name, "sha256": sha256(read_local(path)).hexdigest(),
                                 "director_version": archive.version, "kind": archive.kind}
                                for index, (path, archive) in enumerate(archives)],
            "asset_count": len(records), "bytes": output_bytes,
            "assets": records, "skipped": dict(sorted(skipped.items())),
            "relationship_maps": link_tables,
            "resource_tags": dict(sorted(totals.items())),
            "warnings": ["JPEG exports do not include independent ALFA masks; KEY* associations record potential mask IDs but are not an alpha decoder",
                         "Raw Lscr chunks are NOT decompiled Lingo",
                         "SWA-compressed snd chunks are not converted by the built-in decoder; ID3/MP3 media are exported as original streams"],
        }
        exclusive_write(destination / "manifest.json", (json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8"))
        return manifest
    except BaseException:
        # Never erase a pre-existing directory: destination was created in this call.
        import shutil
        shutil.rmtree(destination)
        raise


def verify_export(destination: Path) -> dict:
    """Verify every manifest hash, count, path and length without changing data."""
    path = destination / "manifest.json"
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 16 * 1024 * 1024:
        raise InspectionError("missing or oversized conversion manifest")
    try:
        manifest = json.loads(read_local(path))
    except (ValueError, UnicodeDecodeError) as exc:
        raise InspectionError("invalid conversion manifest JSON") from exc
    if manifest.get("schema_version") != 1 or not isinstance(manifest.get("assets"), list):
        raise InspectionError("unsupported conversion manifest version")
    if len(manifest["assets"]) != manifest.get("asset_count") or len(manifest["assets"]) > MAX_ASSET_COUNT:
        raise InspectionError("conversion manifest count mismatch")
    total = 0
    checked = set()
    for record in manifest["assets"]:
        rel = record.get("file", "")
        if not isinstance(rel, str) or rel.startswith("/") or "\\" in rel:
            raise InspectionError("unsafe exported asset path")
        components = Path(rel).parts
        if not components or any(c in {"..", "."} for c in components):
            raise InspectionError("unsafe exported asset relative path")
        if rel in checked:
            raise InspectionError("duplicate exported asset path")
        checked.add(rel)
        file = destination / rel
        if any(p.is_symlink() for p in (file, *file.parents) if p != destination.parent):
            raise InspectionError("export contains a symbolic link")
        data = read_local(file)
        if len(data) != record["bytes"] or sha256(data).hexdigest() != record["sha256"]:
            raise InspectionError("exported asset integrity mismatch")
        total += len(data)
    if total != manifest.get("bytes"):
        raise InspectionError("manifest total bytes mismatch")
    return {"verified_files": len(checked), "verified_bytes": total, "manifest_sha256": sha256(read_local(path)).hexdigest()}
