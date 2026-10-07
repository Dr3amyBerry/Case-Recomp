# Case-Recomp — legacy Director-to-Android research

An independent, open-source toolkit for **offline static analysis** of a user's own licensed copy of *Mystery Case Files: Huntsville* (Spanish). Long-term goal: a functioning Android implementation that imports user-owned assets locally. **No Android app or playable APK exists yet.**

The repository does **not** contain the original Windows executables, cast libraries, decoded multimedia, proprietary Lingo, DRM removal, or bundled commercial content.

## Verified progress

- [x] Phase 1: PE32/Director signature inspector and safe inventory.
- [x] Phase 2A: locate and validate embedded XFIR movie and 38 CCT files; parse Afterburner `Fver`/`Fcdr`/`ABMP`/`FGEI`/`ILS`, index resources and decode zlib/raw entries.
- [x] Phase 2B: implement CLI for read-only map / compatibility verification, create-only *local* movie and selected resource extraction, synthetic unit tests, and source-only CI.
- [ ] Phase 2C: externally benchmark ProjectorRays and LibreShockwave on the same source; Lingo decompilation and full media decoding remain open.
- [ ] Phase 3+: user-data importer, Android runtime, rendering, sound, gameplay, UI, and APK QA.

## Requirements

- Python **3.11+**, standard library only.
- A separately obtained, legally usable copy of the game. Do **not** place it in the Git repository or upload it to public Actions.

## Run

```bash
python -m unittest discover -s tests -v
python -m caserecomp scan /private/game --output /private/out/inventory.json
python -m caserecomp director-map /private/game/MysteryCaseFiles.exe
python -m caserecomp director-map /private/game/data/01.cct --details
python -m caserecomp verify-local /private/game/data
```

**Optional raw extraction, locally only:**

```bash
python -m caserecomp extract-movie /private/game/MysteryCaseFiles.exe --output /private/out/movie.dcr
python -m caserecomp extract-resources /private/game/MysteryCaseFiles.exe --tag Lscr --output /private/out/scripts-raw
python -m caserecomp extract-resources /private/game/data/01.cct --tag ALFA --id 123 --output /private/out/selected-raw
```

Outputs are never overwritten. Resource extraction is intentionally filtered to requested IDs/tags and produces **raw binary chunks** rather than readable code or Android-ready files. The tool does not execute source binaries.

## Documentation

- [Reverse-engineering findings, local reproducibility, tool comparison](docs/REVERSE_ENGINEERING.md)
- [Development roadmap and remaining decisions](docs/ROADMAP.md)

## Boundaries

The PE scanner and Afterburner reader check sizes, resource IDs, codec types and decompression integrity. The confirmed dataset contains 7,018 Director resources across 39 containers; 6,989 are readable and **29 SWA audio resources require a separate decoder**. These are compatibility metrics, **not** gameplay coverage or decompilation fidelity.

ProjectorRays is MPL-2.0 and LibreShockwave is AGPL-3.0. Neither project is vendored or included here. Any redistribution of game assets, proprietary Lingo or third-party source requires a separate rights/license assessment. This project does not provide commercial game files.
