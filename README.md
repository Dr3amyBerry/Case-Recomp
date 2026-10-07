# Case-Recomp — Director-to-Android static reconstruction research

A read-only/explicit-local-extraction toolkit for an independently obtained game installation, with the long-term goal of a playable Android port. **No working Android APK exists.** The repository contains **no commercial executables, movie/cast containers, extracted game media or recovered proprietary Lingo**.

## Phases

- [x] **1 —** inspect PE32/projector, classify Director/XFIR containers, create reproducible source-only CI.
- [x] **2 —** parse 39 Afterburner movie/cast archives, map and verify raw/zlib resources and initial load segment (ILS), expose create-only raw extraction.
- [x] **3 —** JPEG-to-JPEG/PNG and ID3/MP3 conversion, known PCM/WAV validation, bytecode export, local SHA-256 manifests and verification, explicit third-party recovery adapters, expanded synthetic tests and build probes.
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
python -m caserecomp director-map /private/game/MysteryCaseFiles.exe --details
python -m caserecomp verify-local /private/game/data

# User-selected local extraction, no overwrite, no network
python -m caserecomp extract-movie /private/game/MysteryCaseFiles.exe --output /private/movie.dcr
python -m caserecomp extract-resources /private/game/MysteryCaseFiles.exe --tag Lscr --output /private/compiled-chunks
python -m caserecomp convert-local /private/game/MysteryCaseFiles.exe --output /private/converted --image-format png --include-bytecode
python -m caserecomp verify-export /private/converted

# Optional independently installed tooling; launches native code only with explicit command
python -m caserecomp external-export /private/movie.dcr --backend libreshockwave --binary /tools/libreshockwave_asset_extractor --output /private/ls-output
python -m caserecomp external-export /private/movie.dcr --backend projectorrays --binary /tools/projectorrays --output /private/pr-output
```

For a directory containing 38 casts, run `convert-local /private/game/data --output /private/casts-export --image-format png`. Output destination must be new, with an existing parent directory, **outside Git repositories and original-game folders**. For proprietary game data, use **private local paths only**; this repository's CI builds the open-source analyzer and uses entirely synthetic miniature test fixtures.

### Conversion scope and limitations

* `ediM` JPEG images are fully decoded/validated using Pillow and written as JPEG or PNG; opaque `ALFA` masks are **not** yet applied. `BITD` and unknown member types are not invented as fake images.
* `ediM` resources starting `ID3` can be real MP3 streams; `mutagen` checks MPEG audio metadata and the original bytes are preserved. This is not an exhaustive frame-by-frame decoder validation.
* `snd ` members with the historical SWA compression registry remain explicitly unsupported. Known raw PCM/WAV can be validated and copied as WAV.
* `Lscr` contains **compiled bytecode**, not readable Lingo. The `--include-bytecode` flag exports the source bytes as `.lscr`. Use separately vetted ProjectorRays or LibreShockwave for decompilation and gameplay reconstruction.
* External adapter outputs are hashed and provenance-tracked but **not** semantically certified. Third-party tools are not sandboxed by the Python process: use trusted binaries in an OS sandbox.

## Documentation

- [Phase 3 — conversion, Lingo recovery, external-tool evaluation, test matrix](docs/PHASE3.md)
- [Phase 2 — format reverse engineering](docs/REVERSE_ENGINEERING.md)
- [Full roadmap and outstanding work](docs/ROADMAP.md)

## Licenses and boundaries

ProjectorRays is MPL-2.0; LibreShockwave is AGPL-3.0. Neither is included here. Their source compilation is tested on GitHub using **pinned revisions and synthetic fixtures only**, and outputs are never published. ProjectorRays and LibreShockwave licensing, the user's game license, and future Android redistributions need separate review. This toolkit cannot circumvent commercial licensing, game protections, or third-party distribution rights.
