"""Bounded, offline Director Afterburner (XFIR) map/resource reader.

This intentionally DOES NOT decompile Lingo or render Director media. Raw
resources may be extracted only on the user's local machine; never commit them.
"""

from __future__ import annotations

import collections
import hashlib
import os
import re
import shutil
import stat
import zlib
from dataclasses import dataclass
from pathlib import Path

from .inspector import InspectionError, MAX_FILE_BYTES, _director_header

MAX_MAP_BYTES = 8 * 1024 * 1024
MAX_CODECS_BYTES = 16 * 1024
MAX_RESOURCE_BYTES = 128 * 1024 * 1024
MAX_ENTRIES = 50000
AB_KIND = {b"MDGF": "movie", b"CDGF": "cast"}  # stored backwards on XFIR


def _varint(data: bytes, pos: int) -> tuple[int, int]:
    """Read a bounded MSB-first unsigned base-128 integer, maximum uint32."""
    value = 0
    for _ in range(5):
        if pos >= len(data):
            raise InspectionError("truncated Afterburner variable integer")
        digit = data[pos]
        pos += 1
        value = (value << 7) | (digit & 0x7f)
        if value > 0xffffffff:
            raise InspectionError("Afterburner integer exceeds uint32")
        if not digit & 0x80:
            return value, pos
    raise InspectionError("overlong Afterburner integer")


def _bounded_zlib(payload: bytes, expected: int, limit: int) -> bytes:
    if expected > limit:
        raise InspectionError("decompressed resource exceeds size cap")
    try:
        obj = zlib.decompressobj()
        decoded = obj.decompress(payload, expected + 1)
        if len(decoded) > expected or obj.unconsumed_tail:
            raise InspectionError("decompressed resource exceeds its declared size")
        decoded += obj.flush(expected + 1 - len(decoded))
        if len(decoded) != expected or not obj.eof or obj.unused_data:
            raise InspectionError("truncated, trailing, or mis-sized compressed resource")
        return decoded
    except zlib.error as exc:
        raise InspectionError(f"invalid zlib stream: {exc}") from exc


def _fourcc(data: bytes) -> str:
    if len(data) != 4 or not all(32 <= c <= 126 for c in data):
        raise InspectionError("invalid resource fourCC")
    return data[::-1].decode("ascii")


@dataclass(frozen=True)
class Resource:
    id: int
    tag: str
    offset: int  # 0xffffffff means content is in the initial load segment
    compressed_size: int
    uncompressed_size: int
    compression_index: int

    @property
    def in_ils(self) -> bool:
        return self.offset == 0xffffffff


class DirectorArchive:
    def __init__(self, contents: bytes):
        header = _director_header(contents)
        if not header or header["signature"] != "XFIR" or contents[8:12] not in AB_KIND:
            raise InspectionError("requires an XFIR/Afterburner Director movie or cast")
        declared = header["declared_payload_bytes"] + 8
        if declared != len(contents):
            raise InspectionError("Director container length does not match input")
        self.kind = AB_KIND[contents[8:12]]
        self.data = contents
        self.version = None
        self.codecs: list[str] = []
        self.entries: dict[int, Resource] = {}
        self.payload_start = 0
        self._ils: dict[int, bytes] | None = None
        self._parse()

    def _parse(self) -> None:
        data = self.data
        pos = 12
        if data[pos:pos + 4] != b"revF":
            raise InspectionError("missing Afterburner Fver chunk")
        length, pos = _varint(data, pos + 4)
        end = pos + length
        if end > len(data) or length > 4096:
            raise InspectionError("invalid Fver size")
        ver, next_pos = _varint(data, pos)
        if ver >= 0x401:
            _, next_pos = _varint(data, next_pos)
            _, next_pos = _varint(data, next_pos)
        if ver >= 0x501:
            if next_pos >= end:
                raise InspectionError("truncated Fver version string")
            version_len = data[next_pos]
            next_pos += 1
            if next_pos + version_len != end:
                raise InspectionError("invalid Fver version string length")
            self.version = data[next_pos:end].decode("latin1")
        pos = end
        if data[pos:pos + 4] != b"rdcF":
            raise InspectionError("missing compression codec registry Fcdr")
        length, pos = _varint(data, pos + 4)
        end = pos + length
        if end > len(data) or length > MAX_CODECS_BYTES:
            raise InspectionError("invalid Fcdr size")
        # The codec table doesn't declare its expanded size, but has a low cap.
        compressed_registry = data[pos:end]
        try:
            dec = zlib.decompressobj()
            registry = dec.decompress(compressed_registry, MAX_CODECS_BYTES + 1)
            if len(registry) > MAX_CODECS_BYTES or dec.unconsumed_tail:
                raise InspectionError("codec table too large")
            registry += dec.flush()
            if len(registry) > MAX_CODECS_BYTES or not dec.eof or dec.unused_data:
                raise InspectionError("invalid codec table")
        except zlib.error as exc:
            raise InspectionError("invalid Fcdr compression") from exc
        if len(registry) < 2:
            raise InspectionError("truncated Fcdr table")
        count = int.from_bytes(registry[:2], "little")
        if count > 64:
            raise InspectionError("too many codec descriptors")
        cursor = 2 + count * 16  # GUID records precede the list of names.
        if cursor > len(registry):
            raise InspectionError("truncated codec identifier array")
        for _ in range(count):
            zero = registry.find(b"\x00", cursor)
            if zero < 0 or zero - cursor > 512:
                raise InspectionError("unterminated codec name")
            self.codecs.append(registry[cursor:zero].decode("latin1"))
            cursor = zero + 1
        if cursor != len(registry):
            raise InspectionError("trailing codec-table bytes")
        pos = end
        if data[pos:pos + 4] != b"PMBA":
            raise InspectionError("missing compressed resource map ABMP")
        length, pos = _varint(data, pos + 4)
        end = pos + length
        if end > len(data) or length > MAX_MAP_BYTES:
            raise InspectionError("invalid ABMP length")
        map_codec, pos = _varint(data, pos)
        expanded_size, pos = _varint(data, pos)
        if map_codec >= len(self.codecs) or not any(word in self.codecs[map_codec].lower() for word in ("ziplib", "zlib")):
            raise InspectionError("unsupported ABMP map compression")
        mapping = _bounded_zlib(data[pos:end], expanded_size, MAX_MAP_BYTES)
        cursor = 0
        _, cursor = _varint(mapping, cursor)  # map version
        _, cursor = _varint(mapping, cursor)  # number of handles / free slots
        count, cursor = _varint(mapping, cursor)
        if count > MAX_ENTRIES:
            raise InspectionError("resource map entry cap exceeded")
        for _ in range(count):
            resource_id, cursor = _varint(mapping, cursor)
            offset, cursor = _varint(mapping, cursor)
            compressed_size, cursor = _varint(mapping, cursor)
            uncompressed_size, cursor = _varint(mapping, cursor)
            compression, cursor = _varint(mapping, cursor)
            if cursor + 4 > len(mapping):
                raise InspectionError("truncated map resource tag")
            tag = _fourcc(mapping[cursor:cursor + 4])
            cursor += 4
            if resource_id in self.entries or compression >= len(self.codecs):
                raise InspectionError("duplicate resource ID or invalid codec index")
            if compressed_size > MAX_RESOURCE_BYTES or uncompressed_size > MAX_RESOURCE_BYTES:
                raise InspectionError("individual resource exceeds safety limit")
            self.entries[resource_id] = Resource(resource_id, tag, offset, compressed_size, uncompressed_size, compression)
        if cursor != len(mapping):
            raise InspectionError("unparsed trailing bytes in resource map")
        if data[end:end + 4] != b"IEGF":
            raise InspectionError("missing FGEI initial-load-segment marker")
        _, self.payload_start = _varint(data, end + 4)
        if self.payload_start >= len(data):
            raise InspectionError("missing initial load segment")
        initial = [r for r in self.entries.values() if r.tag == "ILS "]
        if len(initial) != 1 or initial[0].offset != 0:
            raise InspectionError("expected one initial load segment at offset 0")
        for entry in self.entries.values():
            if not entry.in_ils and self.payload_start + entry.offset + entry.compressed_size > len(data):
                raise InspectionError(f"resource {entry.id} exceeds declared container size")

    def _codec_name(self, index: int) -> str:
        name = self.codecs[index].lower()
        if "ziplib" in name or "zlib" in name:
            return "zlib"
        if "null" in name:
            return "raw"
        return "unsupported"

    def _decode(self, entry: Resource, raw: bytes) -> bytes:
        codec = self._codec_name(entry.compression_index)
        if codec == "zlib":
            return _bounded_zlib(raw, entry.uncompressed_size, MAX_RESOURCE_BYTES)
        if codec == "raw":
            if len(raw) != entry.uncompressed_size:
                raise InspectionError("raw resource length mismatch")
            return raw
        raise InspectionError(f"unsupported codec for resource {entry.id}: {self.codecs[entry.compression_index]}")

    def _load_ils(self) -> dict[int, bytes]:
        if self._ils is not None:
            return self._ils
        segment = next(r for r in self.entries.values() if r.tag == "ILS ")
        compressed = self.data[self.payload_start:self.payload_start + segment.compressed_size]
        uncompressed = self._decode(segment, compressed)
        cursor = 0
        out: dict[int, bytes] = {}
        while cursor < len(uncompressed):
            res_id, cursor = _varint(uncompressed, cursor)
            entry = self.entries.get(res_id)
            if entry is None or not entry.in_ils or res_id in out:
                raise InspectionError("invalid ILS entry reference")
            end = cursor + entry.compressed_size
            if end > len(uncompressed):
                raise InspectionError("truncated ILS entry")
            out[res_id] = uncompressed[cursor:end]
            cursor = end
        in_ils = {r.id for r in self.entries.values() if r.in_ils}
        if set(out) != in_ils:
            raise InspectionError("ILS resource map and body disagree")
        self._ils = out
        return out

    def get_resource(self, resource_id: int) -> bytes:
        entry = self.entries.get(resource_id)
        if entry is None:
            raise InspectionError(f"unknown resource {resource_id}")
        if entry.in_ils:
            # ILS entries are already decompressed by the enclosing segment.
            data = self._load_ils()[resource_id]
            if len(data) != entry.uncompressed_size:
                raise InspectionError("initial segment resource size mismatch")
            return data
        pos = self.payload_start + entry.offset
        return self._decode(entry, self.data[pos:pos + entry.compressed_size])

    def summary(self, *, resource_details: bool = False) -> dict:
        counts = dict(sorted(collections.Counter(e.tag for e in self.entries.values()).items()))
        stats = {
            "container": "XFIR",
            "kind": self.kind,
            "director_version": self.version,
            "codecs": self.codecs,
            "resource_count": len(self.entries),
            "initial_load_resource_count": sum(e.in_ils for e in self.entries.values()),
            "tags": counts,
        }
        if resource_details:
            stats["resources"] = [vars(r) | {"in_ils": r.in_ils} for r in self.entries.values()]
        return stats


def embedded_movie(executable: bytes) -> tuple[int, bytes]:
    """Recover a complete XFIR/FGDM byte stream from a PE projector, read-only."""
    if len(executable) > MAX_FILE_BYTES:
        raise InspectionError("projector exceeds maximum input size")
    if executable[:2] != b"MZ":
        raise InspectionError("expected a Windows PE projector")
    # Verify PE image structure before scanning for a movie.
    from .inspector import _pe_header
    if _pe_header(executable) is None:
        raise InspectionError("invalid Windows PE projector")
    found = []
    pos = 0
    while True:
        pos = executable.find(b"XFIR", pos)
        if pos == -1:
            break
        hdr = _director_header(executable, pos)
        if hdr and executable[pos + 8:pos + 12] == b"MDGF":
            end = pos + 8 + hdr["declared_payload_bytes"]
            if end <= len(executable):
                found.append((pos, executable[pos:end]))
        pos += 4
    if len(found) != 1:
        raise InspectionError(f"expected exactly one embedded Director movie, found {len(found)}")
    DirectorArchive(found[0][1])  # Validate map before allowing extraction
    return found[0]


_O_NOFOLLOW = getattr(os, "O_NOFOLLOW", 0)


def _same_unlinked_file(path: Path, opened: os.stat_result) -> bool:
    """Without O_NOFOLLOW (Windows), confirm the opened handle is the non-link path entry."""
    try:
        entry = os.lstat(path)
    except OSError:
        return False
    return not stat.S_ISLNK(entry.st_mode) and (entry.st_dev, entry.st_ino) == (opened.st_dev, opened.st_ino)


def read_local(path: Path) -> bytes:
    """Read from one opened file descriptor, refusing symlinks on every platform."""
    nofollow = _O_NOFOLLOW
    if not nofollow and path.is_symlink():
        raise InspectionError("input must be a regular file within size cap")
    flags = os.O_RDONLY | nofollow | getattr(os, "O_BINARY", 0)
    with os.fdopen(os.open(path, flags), "rb") as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size > MAX_FILE_BYTES:
            raise InspectionError("input must be a regular file within size cap")
        if not nofollow and not _same_unlinked_file(path, info):
            raise InspectionError("input changed or became a symbolic link while opening")
        data = stream.read(MAX_FILE_BYTES + 1)
        if len(data) > MAX_FILE_BYTES:
            raise InspectionError("input grew beyond maximum permitted size")
        return data


def open_archive(path: Path) -> tuple[DirectorArchive, int]:
    data = read_local(path)
    if data[:2] == b"MZ":
        position, data = embedded_movie(data)
        return DirectorArchive(data), position
    return DirectorArchive(data), 0


def exclusive_write(path: Path, data: bytes) -> None:
    """Secure create-only write; never follow a destination symlink or overwrite."""
    if path.is_symlink():
        raise InspectionError("refusing destination symlink")
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY | getattr(os, "O_NOFOLLOW", 0), 0o600)
    try:
        with os.fdopen(fd, "wb") as target:
            target.write(data)
    except BaseException:
        path.unlink(missing_ok=True)
        raise


def extract_movie(executable: Path, destination: Path) -> dict:
    offset, contents = embedded_movie(read_local(executable))
    exclusive_write(destination, contents)
    return {"offset": offset, "size": len(contents), "sha256": hashlib.sha256(contents).hexdigest()}


def extract_resources(archive_path: Path, destination: Path, *, tags: set[str] | None = None, ids: set[int] | None = None) -> dict:
    if not tags and not ids:
        raise InspectionError("select one or more resource tags or IDs explicitly")
    archive, _ = open_archive(archive_path)
    selected = [r for r in archive.entries.values() if (tags and r.tag in tags) or (ids and r.id in ids)]
    if not selected:
        raise InspectionError("no matching resources")
    if destination.is_symlink() or destination.exists():
        raise InspectionError("destination must be a new local directory")
    # Parent must already exist: no surprise writes in unknown directories.
    destination.mkdir(mode=0o700)
    created = []
    try:
        for r in selected:
            safe_tag = re.sub(r"[^A-Za-z0-9_-]", "_", r.tag)
            name = f"resource-{r.id:05d}-{safe_tag}.bin"
            payload = archive.get_resource(r.id)
            exclusive_write(destination / name, payload)
            created.append({"id": r.id, "tag": r.tag, "name": name, "size": len(payload), "sha256": hashlib.sha256(payload).hexdigest()})
        return {"count": len(created), "files": created}
    except BaseException:
        shutil.rmtree(destination)
        raise
