# Case-Recomp — Director-to-Android static reconstruction research

A read-only/explicit-local-extraction toolkit for an independently obtained game installation, with the long-term goal of a playable Android port. **No working Android APK exists.** The repository contains **no commercial executables, movie/cast containers, extracted game media or recovered proprietary Lingo**.

## Phases

- [x] **1 —** inspect PE32/projector, classify Director/XFIR containers, create reproducible source-only CI.
- [x] **2 —** parse 39 Afterburner movie/cast archives, map and verify raw/zlib resources and initial load segment (ILS), expose create-only raw extraction.
- [x] **3 —** JPEG-to-JPEG/PNG and ID3/MP3 conversion, known PCM/WAV validation, bytecode export, local SHA-256 manifests, KEY* cast/alpha relationships, verified raw/PackBits ALFA composition, true-color BITD decoding, explicit third-party recovery adapters and reproducible tests.
- [ ] **4–8 —** reconstruct the gameplay state machine, implement Android rendering/touch/audio, import individually licensed user data locally, test on devices and build the APK.

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

# Optional independently installed tooling; launches native code only with explicit command
python -m caserecomp external-export /private/movie.dcr --backend libreshockwave --binary /tools/libreshockwave_asset_extractor --output /private/ls-output
python -m caserecomp external-export /private/movie.dcr --backend projectorrays --binary /tools/projectorrays --output /private/pr-output
```

For a directory containing 38 casts, run `convert-local /private/game/data --output /private/casts-export --image-format png`. Output destination must be new, with an existing parent directory, **outside Git repositories and original-game folders**. For proprietary game data, use **private local paths only**; this repository's CI builds the open-source analyzer and uses entirely synthetic miniature test fixtures.

### Conversion scope and limitations

* `ediM` JPEG images are decoded/validated with Pillow. `KEY*` ownership links `CASt`/`ediM`/`ALFA`; 1,988 linked masks are validated as raw or PackBits 8-bit planes and can be composed into RGBA PNG. `BITD` 16/32-bit true-color rendering is implemented; indexed BITD exposes verified palette indices but does not invent RGB until its palette is resolved.
* `ediM` resources starting `ID3` can be real MP3 streams; `mutagen` checks MPEG audio metadata and the original bytes are preserved. This is not an exhaustive frame-by-frame decoder validation.
* `snd ` members using SWA remain unsupported by the generic Director decoder, but an explicit local FFmpeg path can decode their validated MPEG payload to WAV. All 29 private resources were cross-checked against LibreShockwave payload extraction; historical loop/timing equivalence remains open.
* `Lscr` contains **compiled bytecode**, not readable Lingo. The `--include-bytecode` flag exports the source bytes as `.lscr`. Use separately vetted ProjectorRays or LibreShockwave for decompilation and gameplay reconstruction.
* The ProjectorRays adapter requests `--dump-scripts` in a private output directory; recovered `.ls` files are hashed as **unverified Lingo**, not claimed correct.
* External adapter outputs are hashed and provenance-tracked but **not** semantically certified. Third-party tools are not sandboxed by the Python process: use trusted binaries in an OS sandbox.

## Documentation

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
python -m caserecomp compare-lingo /private/pr-dumps /private/ls-dumps \
  --output /private/lingo-audit.json --redact-names
```

Use `--alpha-mode strict` to stop and rollback on an unsupported paired mask.
**This is not a complete Director renderer**: all linked ALFA planes now decode, but five indexed BITD members still require a verified System-Windows RGB palette and original-game Lingo semantics remain open. See
[Phase 3B media research](docs/PHASE3B_MEDIA.md) and the
[Android engine prototype](android/README.md).
