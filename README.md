# Case-Recomp — Director-to-Android static reconstruction research

A read-only/explicit-local-extraction toolkit for an independently obtained game installation, with the long-term goal of a playable Android port. **A synthetic Android shell is now buildable, but no original-game playable APK exists.** The repository contains **no commercial executables, movie/cast containers, extracted game media or recovered proprietary Lingo**.

## Phases

- [x] **1 —** inspect PE32/projector, classify Director/XFIR containers, create reproducible source-only CI.
- [x] **2 —** parse 39 Afterburner movie/cast archives, map and verify raw/zlib resources and initial load segment (ILS), expose create-only raw extraction.
- [x] **3 —** JPEG-to-JPEG/PNG and ID3/MP3 conversion, known PCM/WAV validation, bytecode export, local SHA-256 manifests, KEY* cast/alpha relationships, verified raw/PackBits ALFA composition, true-color BITD decoding, explicit third-party recovery adapters and reproducible tests.
- [x] **4 —** close indexed BITD fidelity, parse Score/cast/timeline structure, define scenario-v1, add fail-closed private behavior traces and expand the deterministic Kotlin runtime.
- [x] **5 —** private `.crcontent` importer, SHA-256 asset catalog, app-private storage, manifest migration, private bitmap/audio ports, save slots, runtime observer and emulator tests.
- [x] **6 —** boot/menu/map/scene verified against two independent native-projector sessions; a private `.crflow` v2 promotes 2 navigation rules (no timing gate). The earlier placeholder capture run was audited and rejected.
- [x] **7 —** API 26/33/36 emulator QA, lifecycle/persistence, corruption/migration, low-memory/audio interruption and deterministic performance guardrails.
- [x] **8 —** controlled synthetic debug APK, same-run byte reproducibility evidence, CycloneDX/provenance, external ADB process-kill/upgrade/save-backup QA and API 26/33/36 emulator validation; no production release/signing.
- [~] **9 —** first playable hidden-object scene: a private `.crscene` proof from native-projector trials drives verified finds, completion, return to map, lock and persistence for one observed target configuration.
- [~] **10 —** private Lingo decompilation plus a generic Kotlin Lingo VM that runs the user's own compiled scripts (bytecode bundle reader done).

## Install

Python 3.11+; the base static-analysis commands use only the standard library. Image and MP3 conversion require optional local decoders:

```bash
python -m pip install -e '.[media]'
python -m pip install -e '.[dev]'  # for coverage and tests
python -m coverage run --source=caserecomp -m unittest discover -s tests -q
python -m coverage report --fail-under=90
```

## Offline commands

```bash
python -m caserecomp scan /private/game --output /private/inventory.json
python -m caserecomp director-map /private/game/MysteryCaseFiles.exe --details --links
python -m caserecomp verify-local /private/game/data

# User-selected local extraction, no overwrite, no network
python -m caserecomp extract-movie /private/game/MysteryCaseFiles.exe --output /private/movie.dcr
python -m caserecomp extract-resources /private/game/MysteryCaseFiles.exe --tag Lscr --output /private/compiled-chunks
python -m caserecomp convert-local /private/game/MysteryCaseFiles.exe --output /private/converted --image-format png --include-bytecode
python -m caserecomp verify-export /private/converted
python -m caserecomp fidelity-file /private/reference.png /private/candidate.png --kind png
python -m caserecomp score-structure /private/game/MysteryCaseFiles.exe --output /private/score-structure.json
python -m caserecomp scenario-check fixtures/synthetic-scenario-v1.json
python -m caserecomp trace-plan /private/game/MysteryCaseFiles.exe --output /private/trace-plan.json
python -m caserecomp trace-compare /private/trace-plan.json /private/runtime-observation.json --output /private/trace-compare.json
python -m caserecomp private-content-package /private/converted /private/scenario.json --output /private/game.crcontent
python -m caserecomp private-content-verify /private/game.crcontent
python -m caserecomp slice-plan /private/game/MysteryCaseFiles.exe --menu-label PRIVATE_MENU --map-label PRIVATE_MAP --scene-label PRIVATE_SCENE --output /private/slice.json
python -m caserecomp slice-compare /private/slice.json /private/original-runtime-observation.json --output /private/slice-compare.json
python -m caserecomp slice-flow-proof /private/slice-compare.json /private/scenario.json --spec /private/slice.json --observation /private/original-runtime-observation.json --package-id PRIVATE_CRCONTENT_PACKAGE_ID --scene-id room-1 --output /private/verified.crflow
python -m caserecomp slice-capture-screen --output /private/menu.png --bbox 100 100 900 700
# Creates /private/menu-burst/{frames,capture-burst.json}; output must not exist.
python -m caserecomp slice-capture-screen --burst --count 240 --interval-ms 16 --output /private/menu-burst --bbox 100 100 900 700
python -m caserecomp slice-capture-trial /private/slice.json /private/trial-input.json --runtime-binary /private/MysteryCaseFiles.exe --output /private/trial-1.json
python -m caserecomp slice-capture-finalize /private/slice.json /private/trial-1.json /private/trial-2.json --output /private/original-runtime-observation.json

# Optional independently installed tooling; launches native code only with explicit command
python -m caserecomp external-export /private/movie.dcr --backend libreshockwave --binary /tools/libreshockwave_asset_extractor --output /private/ls-output
python -m caserecomp external-export /private/movie.dcr --backend projectorrays --binary /tools/projectorrays --output /private/pr-output
```

For a directory containing 38 casts, run `convert-local /private/game/data --output /private/casts-export --image-format png`. Output destination must be new, with an existing parent directory, **outside Git repositories and original-game folders**. For proprietary game data, use **private local paths only**; this repository's CI builds the open-source analyzer and uses entirely synthetic miniature test fixtures.

### Conversion scope and limitations

* `ediM` JPEG images are decoded/validated with Pillow. `KEY*` ownership links `CASt`/`ediM`/`ALFA`; 1,988 linked masks are validated as raw or PackBits 8-bit planes and can be composed into RGBA PNG. `BITD` 16/32-bit true-color rendering is implemented. The five indexed BITD members used by this title are also rendered through an independently corroborated subset of Director System Windows palette `-102`; unknown legacy palette indices still fail closed.
* `ediM` resources starting `ID3` can be real MP3 streams; `mutagen` checks MPEG audio metadata and the original bytes are preserved. This is not an exhaustive frame-by-frame decoder validation.
* `snd ` members using SWA remain unsupported by the generic Director decoder, but an explicit local FFmpeg path can decode their validated MPEG payload to WAV. All 29 private resources were cross-checked against LibreShockwave payload extraction; historical loop/timing equivalence remains open.
* `Lscr` contains **compiled bytecode**, not readable Lingo. The `--include-bytecode` flag exports the source bytes as `.lscr`. Use separately vetted ProjectorRays or LibreShockwave for decompilation and gameplay reconstruction.
* The ProjectorRays adapter requests `--dump-scripts` in a private output directory; recovered `.ls` files are hashed as **unverified Lingo**, not claimed correct.
* External adapter outputs are hashed and provenance-tracked but **not** semantically certified. Third-party tools are not sandboxed by the Python process: use trusted binaries in an OS sandbox.

## Current architecture and evidence inventory

- [Game files, private Drive working folders, Director/Lingo route and actual Android playability](docs/PROJECT_AUDIT_2026-10-07.md)
- [Old system versus new engine: keep, reconsider and future deletion candidates (no deletion)](docs/RETIREMENT_CANDIDATES.md)

The whole-game target is the Kotlin Director/Lingo interpreter. The current Android Activity still uses the older synthetic `GameRuntime` until the new engine is proven on-device.

## Documentation

- [Post-Phase-8 — physical-device synthetic QA](docs/POST_PHASE8_DEVICE_QA.md)
- [Phase 8 — controlled synthetic debug packaging](docs/PHASE8.md)
- [Phase 6B — native runtime capture consensus](docs/PHASE6B.md)
- [Phase 7 QA preparation](docs/PHASE7_QA.md)
- [Phase 10 — private decompilation and Lingo VM](docs/PHASE10.md)
- [Phase 9 — first playable hidden-object scene](docs/PHASE9.md)
- [Phase 6 — fail-closed verified vertical slice and `.crflow` proof](docs/PHASE6.md)
- [Phase 5 — private local content import, asset catalog, slots and emulator tests](docs/PHASE5.md)
- [Phase 4B — private behavior traces, deterministic persistence and Android shell](docs/PHASE4B.md)
- [Phase 4 — palette closure, Score structure, scenario-v1 and Kotlin engine](docs/PHASE4.md)
- [Phase 3 — conversion, Lingo recovery, external-tool evaluation, test matrix](docs/PHASE3.md)
- [Phase 3B.1 — KEY* cast-member associations and validation](docs/PHASE3B_RELATIONSHIPS.md)
- [Phase 3B.4 — cross-tool fidelity evidence and Phase 4 test contract](docs/PHASE3B_FIDELITY.md)
- [Phase 2 — format reverse engineering](docs/REVERSE_ENGINEERING.md)
- [Full roadmap and outstanding work](docs/ROADMAP.md)

## Licenses and boundaries

ProjectorRays is MPL-2.0; LibreShockwave is AGPL-3.0. Neither is included here. Their source compilation is tested on GitHub using **pinned revisions and synthetic fixtures only**, and outputs are never published. ProjectorRays and LibreShockwave licensing, the user's game license, and future Android redistributions need separate review. This toolkit cannot circumvent commercial licensing, game protections, or third-party distribution rights.

## Phase 3B.2 — alpha and SWA (explicit opt-in)

The normal conversion preserves existing opaque image behavior. To composite
KEY*-linked, geometrically validated ALFA masks into RGBA PNGs and optionally
extract 29 SWA-encoded sounds via a **locally installed, trusted FFmpeg**:

```bash
python -m caserecomp convert-local /private/game --output /private/converted \
  --image-format png --alpha-mode best-effort --decode-swa --ffmpeg /usr/bin/ffmpeg
python -m caserecomp verify-export /private/converted
python -m caserecomp fidelity-file /private/reference.png /private/candidate.png --kind png
python -m caserecomp score-structure /private/game/MysteryCaseFiles.exe --output /private/score-structure.json
python -m caserecomp scenario-check fixtures/synthetic-scenario-v1.json
python -m caserecomp trace-plan /private/game/MysteryCaseFiles.exe --output /private/trace-plan.json
python -m caserecomp trace-compare /private/trace-plan.json /private/runtime-observation.json --output /private/trace-compare.json
python -m caserecomp private-content-package /private/converted /private/scenario.json --output /private/game.crcontent
python -m caserecomp private-content-verify /private/game.crcontent
python -m caserecomp compare-lingo /private/pr-dumps /private/ls-dumps \
  --output /private/lingo-audit.json --redact-names
```

Use `--alpha-mode strict` to stop and rollback on an unsupported paired mask.
**This is not a complete Director renderer**: all linked ALFA planes and all 21 BITD members found across the private installation now have verified decode paths, but Director ink effects, Score/runtime behavior and original-game Lingo semantics remain open. See
[Phase 3B media research](docs/PHASE3B_MEDIA.md) and the
[Android engine prototype](android/README.md).
