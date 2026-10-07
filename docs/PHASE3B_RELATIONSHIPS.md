# Phase 3B.1 — KEY* cast-member relationships (structural, not rendered)

Status: **implemented and validated on privately supplied game data**, 2026-10-06. This analysis does not redistribute original bytes, screenshots, Lingo or converted audio/images. The next Phase 3B tasks (alpha channel decoding, native BITD, SWA and executable Lingo semantics) remain open.

## What the KEY* table means

Director/Afterburner movies and casts contain a `KEY*` resource describing relationships between a *section* (resource ID) and its *owner* (usually a `CASt` member ID). Reading this table is more reliable than assuming adjacent numeric resource IDs. The parsing model was checked against LibreShockwave's `cpp/src/chunks/KeyTableChunk.cpp` at revision `fca530f9ef388d7ff38fa6c7117feae5bb5411c6`.

For the inspected Director 8.5 Afterburner containers, each table has:

- A 12-byte little-endian header: two 16-bit record/header-size fields (both 12 in the supplied set), 32-bit **allocated** count and 32-bit **used** count.
- Exactly `allocated * 12` bytes of entry storage after that header. Only the **used** records are authoritative; trailing reserved entries must not be interpreted as real cast references.
- Each used entry: 32-bit `section_id`, 32-bit `owner_id` and reversed four-byte tag such as `ediM`, `ALFA`, `BITD`, `Thum`, or `snd `.
- Entries may mention sections not present in the current archive map (for example `Thum` records). They are reported as **unavailable references**, not assumed to be corrupted chunks. When an entry points to a present resource, the decoded tag must match the resource map or parsing fails closed.

`caserecomp.relationships.CastRelationships` exposes `resources_for(owner_id, tag)`, `owners_of(section_id)`, `image_alpha_links(archive)`, and an aggregate `summary`. Only unique `ediM` owners that are actual `CASt` resources can be linked to a unique existing `ALFA` associated with the same owner. Ambiguous ownership is intentionally skipped.

## Local result on the user's original installation

Inspected: one embedded movie plus 38 `.cct` casts = **39** Director containers. All 39 `KEY*` tables validated against their Afterburner resource maps.

| Structural observation | Count |
|---|---:|
| Allocated records | 8,692 |
| Used records | 6,814 |
| References pointing to present resources | 4,468 |
| References to unavailable resources | 2,346 |
| `CASt` members with at least one valid linked resource | 2,270 |
| `ediM` resources with an unambiguous `CASt` owner | 2,115 |
| Unambiguously matched `ediM` + `ALFA` per `CASt` owner | **1,988** |

The last number is an **association count**, not a count of transparent PNGs. The current converter exports an image without applied alpha and records its `cast_member_id`, `alpha_resource_id` (if unique), `alpha_applied=false`, and `association_method=KEY*-cast-owner`. It never generates a transparency channel from opaque mask bytes. A user can explicitly export raw `ALFA` with `--include-raw`, but those bytes are not a normalized alpha plane.

Full offline conversion was repeated after adding relationships: **2,194 exported files** (2,108 PNG, 6 original MP3, 80 original bytecode chunks), totaling **53,420,657 bytes**, with 2,194 SHA-256 checks passing. All 39 relationship maps had status `parsed`; **29 `snd ` resources using SWA** are still skipped. All export files, including any recovered original content, were removed from the temporary private test directory immediately after verification.

## Verification and CLI

```bash
python -m caserecomp director-map /private/game/data/01.cct --links
python -m caserecomp convert-local /private/game/data --image-format png --include-raw --output /private/exported
python -m caserecomp verify-export /private/exported
python -m coverage run --source=caserecomp -m unittest discover -s tests -q
python -m coverage report --fail-under=90
```

`director-map --links` emits only structural counts. `convert-local` writes the additional `relationship_maps` field to its manifest. If a file has an absent or malformed `KEY*`, conversion can proceed without inventing relationships, and the manifest records status `unavailable` with a reason. This is deliberately a **partial-fidelity** export, not a completed game conversion.

The ProjectorRays adapter now explicitly calls `decompile --dump-scripts -o <private directory> <file>` and recognizes `.dir`, `.cst`, and `.ls` outputs. It records `.ls` as `recovered-lingo-unverified`: the tool's output, if present, is not proof of behavioral equivalence. Real-game ProjectorRays and LibreShockwave execution remains an independent sandbox-only verification step; our tests use synthetic stubs and CI upstream build probes, and CI never receives original game data.

## Explicit remaining blockers

1. Decode `ALFA` RLE/compression and original bitmap geometry/palette/bit depth before applying alpha and comparing pixels.
2. Recover complete `BITD` and sprites, respecting Director ink effects and animation ordering.
3. Decode SWA using a verified independent backend, checking decoded frame counts, rates and actual playback.
4. Test recovered `.ls` scripts against original timeline, handler ordering, variable names and level transitions in an isolated sandbox.
5. Only after those checks derive stable JSON models for an Android engine; current exports are **not an APK**.

## Safety and licensing

Resource contents and recovered script text must stay outside public GitHub repositories, CI artifacts and logs. ProjectorRays is MPL-2.0 and LibreShockwave is AGPL-3.0; the parsing technique is independently implemented with Python standard library and does not vendor either tool's source code. Running a third-party native decompiler is **opt-in** and must happen in an isolated environment with no production credentials or network access.
