# Director/XFIR reverse engineering — Phase 2

This document records reproducible *structure-only* inspection of a privately supplied installed game. The **original executables, media, compressed casts, decompiled Lingo, and extracted output are deliberately absent** from the public repository. No executable was run.

## Source-set inventory and proven observations

**Input:** Spanish edition of *Mystery Case Files: Huntsville*, RealNetworks distribution, source `MysteryCaseFiles.exe` and `data/*.cct`, inspected 2026-10-06. The installer README identifies edition 1.0; the Director `Fver` chunk reports version string `8.5.1#104`.

- PE32/i386 `MysteryCaseFiles.exe` (11,094,294 bytes) contains one structurally valid `XFIR` **movie** (`FGDM`, bytes stored as `MDGF` in the little-endian file) at file offset **2,435,648**. The embedded movie's length is **8,658,642 bytes**, including the 12-byte container prefix.
- The `data` directory contains **38** `XFIR` **cast** libraries (`FGDC`, bytes stored as `CDGF`): `01.cct` through `21.cct`, `dat1.cct` through `dat16.cct`, and `empty.cct`.
- Each of these **39 Director containers** has independently passed structural parsing of `Fver`, `Fcdr`, `ABMP`, resource entries, and `FGEI` / `ILS` data.
- **7,018** resources are indexed: **872** in the movie and **6,146** across casts.
- Resource counts: `CASt` 2,353; `ediM` 2,115; `ALFA` 1,994; `XMED` 105; `Lscr` 80; `BITD` 21; `snd ` 29; the rest are structural data (`ILS`, `KEY*`, `DRCF`, `LctX`, `Lnam`, `MCsL`, etc.).
- `Lscr` scripts (80) are mapped inside the **initial load segment (ILS)** of the movie. The `.cct` libraries in this input set contain no `Lscr` tag.
- **6,989 / 7,018** resources were decoded or copied with size and stream integrity checks. **29 `snd `** records require the external **SWA Decompressor Xtra**; they are *not decoded* by this tool. This is a deliberate unsupported status, not success.
- Decoding a resource into bytes is **not** conversion to a playable Android image/audio asset, and parsing `Lscr` chunks is **not** Lingo decompilation.

The source folder also contains `data/gallinita.exe` (another PE executable), logs, HTML pages and installation files. `gallinita.exe` is not classified as a Director movie by the bounded movie extraction tool. Their runtime purpose is not yet established.

## Container layout (empirically validated)

```text
PE32 projector [Win32]                 (original; never executed)
   prefix / PE sections
   ...
   0x00252A40: XFIR ... MDGF / FGDM  (offset 2,435,648 decimal)
                Fver: Afterburner / Director version
                Fcdr: compressed table of codec GUIDs and names
                ABMP: zlib compressed resource index
                  id, offset, stored size, decoded size, codec, tag
                FGEI: start of initial load segment
                  ILS: zlib block containing id + raw bytes of
                       indexed members (including Lscr)
                  media resources: compressed/raw content by map offsets
```

Offsets of externally stored map entries are **relative to the first byte of the ILS compressed block**, not the start of the containing PE, and `0xffffffff` marks resources represented inside the decompressed ILS. The header's declared length excludes the initial eight bytes. Tags in `XFIR` are stored byte-reversed (`MDGF`, `CDGF`, `revF`, `rdcF`, `PMBA`, `IEGF`). The names in the Fcdr codec registry include **"ziplib"** (the historical name), `null` (uncompressed), and—for the movie—**SWA**. Generic zlib decoding must not be applied to SWA media.

## Tool comparison (source review, not a benchmark)

| Candidate | Project/release claims | Limitation | Intended role |
|---|---|---|---|
| [ProjectorRays](https://github.com/ProjectorRays/ProjectorRays) | Decompile Director Lingo and produce editable `.dir` or `.cst`; MPL-2.0 | Native build/decompiler and outputs need verification against this particular game | Offline recovered-Lingo reference; **not yet executed in this environment** |
| [LibreShockwave](https://github.com/LibreShockwave/LibreShockwave) | C++20 runtime; parses `RIFX/XFIR/RIFF/FFIR`, Afterburner, resources and bytecode; browser/WASM and debugger; AGPL-3.0 | Player/VM is still developing; Android portability and feature completeness not confirmed | Preferred investigation path for host runtime or parser reuse; **not yet built here** |
| Case-Recomp native Python decoder | 39/39 input archives parsed; 6,989/7,018 resources decoded; pure Python standard library | No bitmap conversion, no SWA support, no Lingo disassembler, no native Android runtime | Reliable local inventory, raw extraction and testable compatibility baseline |

**License note:** MPL-2.0 and AGPL-3.0 carry different obligations; importing source from these projects is a separate licensing decision. Case-Recomp currently ships no code or binary from either third party. Repository comparisons above describe functionality advertised by maintainers, not tested cross-tool equivalence.

### External-tool commands to run locally (optional)

These are examples from upstream documentation, not operations this repo automatically performs:

```bash
# ProjectorRays, on a machine with compiled tool installed:
./projectorrays decompile /private/game/01.cct
# LibreShockwave, after separately installing and building:
./build.sh --target libreshockwave_asset_extractor --no-tests
./cmake-build-debug/cpp/libreshockwave_asset_extractor /private/game/data /private/output
```

Keep these paths **outside the repository**; no GitHub Action downloads the user-provided game or uploads generated output.

## Reproducible commands for our own offline tools

```bash
python -m unittest discover -s tests -v
python -m caserecomp director-map /private/game/MysteryCaseFiles.exe
python -m caserecomp director-map /private/game/data/01.cct --details
python -m caserecomp verify-local /private/game/data
# Optional, EXPLICIT local extraction; output must not already exist:
python -m caserecomp extract-movie /private/game/MysteryCaseFiles.exe --output /private/output/movie.dcr
python -m caserecomp extract-resources /private/game/MysteryCaseFiles.exe --tag Lscr --output /private/output/compiled-lingo
# Selected output is RAW bytes, not decompiled source, editable artwork or Android media.
```

`verify-local` reports decoded/unsupported/corrupt resource counts without writing files or printing recovered bytecode. `extract-movie` and `extract-resources` **create-only** outputs: they refuse overwrites and rollback partial selected-resource extraction on failure. The compressor and mapped data have explicit size checks, bounded decompression, duplicate ID rejection and truncated-input checks. No executable is launched.

**Source-only CI:** tests use independent, synthetic miniature XFIR/PE fixtures. The real-game validation above was performed locally only; it cannot be reproduced in public CI without access to an authorized local installation. The project explicitly avoids putting commercial input files in Git or Actions artifacts.

## Remaining tasks before claiming full Android support

1. Benchmark ProjectorRays and LibreShockwave **on the same locally supplied game files**; measure recovered Lingo handler count and output correctness.
2. Decode the 80 `Lscr` chunks into named Lingo handlers; follow `Lnam`, `LctX`, `KEY*` and `MCsL` relationships. No recovered code has been shipped.
3. Add image decoding for `ediM`/`BITD`/`ALFA`, text resolution (`XMED`/`STXT`) and sound (SWA via a separately vetted decoder), with reproducible semantic verification.
4. Model game rules and transitions before implementing their equivalents on Android. The logs mention `mainMenu`, `map`, level indices and 42 puzzle-data slots, but log messages alone do not prove all state transitions.
5. Evaluate runtime strategy (open-source Director VM port vs targeted Android engine) with licensing, performance, rendering, audio and touch requirements; no Android APK has been produced yet.
