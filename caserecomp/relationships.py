"""Bounded Director KEY* association reader (cast ownership; no media decoding)."""

from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass
import struct

from .director import DirectorArchive, MAX_ENTRIES
from .inspector import InspectionError

KEY_HEADER = struct.Struct("<HHII")
KEY_ENTRY = struct.Struct("<II4s")


@dataclass(frozen=True)
class KeyRecord:
    section_id: int
    owner_id: int
    tag: str
    present: bool


class CastRelationships:
    """Indexed resource-owner relations from an archive's KEY* resource."""

    def __init__(self, archive: DirectorArchive):
        matches = [r for r in archive.entries.values() if r.tag == "KEY*"]
        if len(matches) != 1:
            raise InspectionError("expected exactly one KEY* cast resource table")
        data = archive.get_resource(matches[0].id)
        if len(data) < KEY_HEADER.size:
            raise InspectionError("truncated KEY* header")
        header_size, entry_size, capacity, used = KEY_HEADER.unpack_from(data)
        if header_size != 12 or entry_size != 12:
            raise InspectionError("unsupported KEY* record layout")
        if used > capacity or capacity > MAX_ENTRIES:
            raise InspectionError("invalid KEY* record count")
        if len(data) != KEY_HEADER.size + capacity * KEY_ENTRY.size:
            raise InspectionError("KEY* table size disagrees with record capacity")
        self.capacity = capacity
        self.used = used
        self.records: tuple[KeyRecord, ...] = self._parse(data, used, archive)
        grouped: dict[int, dict[str, list[int]]] = defaultdict(lambda: defaultdict(list))
        referenced: dict[int, set[int]] = defaultdict(set)
        for item in self.records:
            if item.present:
                grouped[item.owner_id][item.tag].append(item.section_id)
                referenced[item.section_id].add(item.owner_id)
        self._by_owner = {
            owner: {tag: tuple(sorted(ids)) for tag, ids in tags.items()}
            for owner, tags in grouped.items()
        }
        self._by_resource = {rid: tuple(sorted(owners)) for rid, owners in referenced.items()}

    @staticmethod
    def _parse(data: bytes, used: int, archive: DirectorArchive) -> tuple[KeyRecord, ...]:
        records = []
        seen = set()
        for i in range(used):
            position = KEY_HEADER.size + i * KEY_ENTRY.size
            section_id, owner_id, fourcc = KEY_ENTRY.unpack_from(data, position)
            if any(byte < 32 or byte > 126 for byte in fourcc):
                raise InspectionError("invalid KEY* resource tag")
            tag = fourcc[::-1].decode("ascii")
            key = (section_id, owner_id, tag)
            if key in seen:
                raise InspectionError("duplicate KEY* association")
            seen.add(key)
            present = section_id in archive.entries
            if present and archive.entries[section_id].tag != tag:
                raise InspectionError("KEY* tag contradicts resource map")
            records.append(KeyRecord(section_id, owner_id, tag, present))
        return tuple(records)

    def resources_for(self, owner_id: int, tag: str | None = None) -> tuple[int, ...]:
        tags = self._by_owner.get(owner_id, {})
        if tag is not None:
            return tags.get(tag, ())
        return tuple(sorted(rid for ids in tags.values() for rid in ids))

    def owners_of(self, section_id: int) -> tuple[int, ...]:
        return self._by_resource.get(section_id, ())

    def image_alpha_links(self, archive: DirectorArchive) -> dict[int, dict]:
        """Associate resource IDs by verified CASt owner; never guess by adjacency.

        Multiple image/alpha candidates or multiple owners remain unpaired.
        Returned masks are references, not normalized transparency pixels.
        """
        relationships: dict[int, dict] = {}
        for owner_id, owned in sorted(self._by_owner.items()):
            if owner_id not in archive.entries or archive.entries[owner_id].tag != "CASt":
                continue
            media_ids = owned.get("ediM", ())
            if len(media_ids) != 1:
                continue
            alpha_ids = owned.get("ALFA", ())
            image = media_ids[0]
            if len(self.owners_of(image)) != 1:
                continue
            alpha = (alpha_ids[0] if len(alpha_ids) == 1 and
                     len(self.owners_of(alpha_ids[0])) == 1 else None)
            relationships[image] = {
                "cast_member_id": owner_id,
                "alpha_resource_id": alpha,
                "alpha_applied": False,
                "association_method": "KEY*-cast-owner",
            }
        return relationships

    def bitd_links(self, archive: DirectorArchive) -> dict[int, dict]:
        """Return unique BITD -> bitmap CASt owner links from KEY*."""
        result: dict[int, dict] = {}
        for owner_id, owned in sorted(self._by_owner.items()):
            if owner_id not in archive.entries or archive.entries[owner_id].tag != "CASt":
                continue
            ids = owned.get("BITD", ())
            if len(ids) != 1:
                continue
            bitmap_id = ids[0]
            if len(self.owners_of(bitmap_id)) != 1:
                continue
            result[bitmap_id] = {
                "cast_member_id": owner_id,
                "association_method": "KEY*-cast-owner",
            }
        return result

    def summary(self, archive: DirectorArchive) -> dict:
        active = sum(record.present for record in self.records)
        linked = self.image_alpha_links(archive)
        bitd = self.bitd_links(archive)
        cast_members = {rid for rid, r in archive.entries.items() if r.tag == "CASt"}
        linked_cast_owners = {record.owner_id for record in self.records if record.present and record.owner_id in cast_members}
        return {
            "allocated_records": self.capacity,
            "used_records": self.used,
            "existing_resources": active,
            "unavailable_references": self.used - active,
            "linked_cast_members": len(linked_cast_owners),
            "images_with_cast_owner": len(linked),
            "images_with_alpha_reference": sum(info["alpha_resource_id"] is not None for info in linked.values()),
            "bitd_with_cast_owner": len(bitd),
            "reference_tags": dict(sorted(Counter(record.tag for record in self.records).items())),
            "alpha_decoded": False,
        }
