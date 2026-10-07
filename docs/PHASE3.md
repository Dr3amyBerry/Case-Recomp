# Phase 3 — verifiable local Director media conversion and tool evaluation

Status: independently developed Python pipeline complete for the **bounded formats listed below**, synthetically tested with positive and negative cases. Full cross-tool Lingo and SWA decoding on real input is **not** completed. No APK yet. Date: 2026-10-06 (America/Toronto).

## Input provenance (never committed)

Source: user's original local Spanish-language *Mystery Case Files: Huntsville* 1.0 installation, Director runtime 8.5.1#104. The 39 XFIR Afterburner containers comprise the embedded FGDM movie and 38 `.cct` libraries. Phase 2 established 7,018 resources: 6,989 zlib/raw readable, 29 compressed with a codec named `SWA Decompressor Xtra`.

### Empirical signatures, not just filename guesses

| Member | Example population | Validated evidence | Phase 3 handling |
|---|---:|---|---|
| `ediM` carrying JPEG | **2,108 JPEG** among 2,115 `ediM` in the complete dataset; main movie has **165 JPEG** | `FFD8`/`FFD9`, full Pillow decode and bounded dimensions | Export validated JPEG bytes or normalized RGB PNG, SHA-256 manifest |
| `ediM` carrying ID3/MP3 | **6** in main movie | `ID3` v2.3 and mutagen MPEG frames, rate/length/bitrate parsed | Preserve MP3 bytes, record stream metadata; no transcoding |
| `ediM` unclassified | **1** in main movie | Starts with zeros, neither JPEG nor ID3 | Skip and count as `unrecognized_ediM`; do not guess media type |
| `ALFA` | Main: 84; `01.cct`: 91 | Independent compressed alpha-like data, not a JPEG | Preserve as raw with `--include-raw`; **not combined with JPEG** |
| `BITD` | Main: 15 | Director native bitmap body, typically no JPEG header | Raw optional; bitmap geometry/CLUT/depth decoder pending |
| `Lscr` | Main: 80 | Compiled Director script data inside ILS | Optional `.lscr` extraction, no claim of Lingo source recovery |
| `snd ` | Main: 29 | Codec registry names **SWA** and no built-in decoder | Explicit `unsupported_SWA`; no fake WAV or MP3 |
| `XMED` | Main: 89 | Contains multiple structures incl. PFR1 fonts and opaque text | Optional raw; semantic conversion pending |

**Representative full local conversion results:**

- Main `MysteryCaseFiles.exe`, `--image-format png --include-bytecode`: **251 assets** verified: 165 PNG, 6 MP3, 80 raw `Lscr` scripts. 29 SWA remain unsupported, 1 unknown `ediM` skipped. All 251 exported files re-hashed, compared to manifest and deleted after local verification; no recovered Lingo or media uploaded.
- `data/01.cct` cast, `--image-format png`: **93 PNG** exported and verified; 91 ALFA masks remain separately encoded.
- Conversion is deterministic on the same decoder versions and source bytes. PNG generation may vary between Pillow versions; the manifest hashes are computed from actual written output, and no byte-for-byte reproducibility across different decoder versions is claimed.

### Format and fidelity caveats

1. Images: `ediM` is a **tag**, not a promise of JPEG. JPEG data are decoded and optionally transformed, but the game often stores alpha as a separate `ALFA` member; exported PNGs without paired ALFA can have transparency and composite differences. We must resolve `CASt`/`KEY*` associations and palette to reproduce scene rendering.
2. Audio: ID3-bearing embedded members may represent music tracks. `mutagen` validates a useful MPEG metadata subset; it does not guarantee every audio frame decodes. Native `snd` chunks compressed by SWA cannot safely be relabeled as WAV or MP3 without a proper decoder. Known PCM RIFF/WAVE members can be validated and copied when encountered.
3. Lingo: extraction preserves compiled bytecode. `Lnam`, `LctX`, `Lscr`, `KEY*` and cast references are needed to recover handler names and resolve global references; a decompiler should compare these against the main movie. None of the game's proprietary scripts is committed.
4. External tools: the adapter only captures outputs from explicitly provided third-party executables, hashes them and ensures they stay local. It cannot guarantee script correctness, runtime behavior, image alpha fidelity, sound playback or historical game logic.

## ProjectorRays versus LibreShockwave

| Property | ProjectorRays | LibreShockwave |
|---|---|---|
| Upstream | https://github.com/ProjectorRays/ProjectorRays | https://github.com/LibreShockwave/LibreShockwave |
| Code/license | C++17 / MPL-2.0 | C++20 / AGPL-3.0 |
| Frozen upstream commit tested in CI | `6f9bcebf626b43719abe2affcbbcb041d154d666` | `fca530f9ef388d7ff38fa6c7117feae5bb5411c6` |
| Advertised purpose | Restore Lingo and editable Director `.dir` / `.cst` | C++ SDK, decompiler, asset catalogue, experimental player/debugger |
| Media extraction | Converts protected/compressed movies/casts to editable projects | Extractor outputs `bitmaps/`, `sounds/`, `scripts/`, `text/`, `palettes/`, etc. |
| Lingo | Restore native source into editable project; stand-alone Lingo text not guaranteed | Asset extractor generates `.ls` and `.lsasm` (per upstream docs) |
| Audio | Upstream uses mpg123 for audio handling | Upstream supports MP3, PCM→WAV, IMA ADPCM (SWA compatibility on this exact file NOT verified) |
| Intended role | Cross-check decompiled project fidelity, handler extraction and cast relationships | Validate bitmap palette/alpha, script disassembly and media import; not yet a production Android VM |

### Experimental boundary and reproducible probes

The container executing the Python tests cannot resolve `github.com` (DNS unavailable), so no honest claim can be made that upstream programs were built or executed locally on the original commercial inputs. Instead:

- `tests/test_backends.py` **executes synthetic stub programs** that implement each tool's documented argument contract. Those tests confirm local staging, explicit opt-in execution, directory outputs, safe bounds, rollback on subprocess failure, timeouts, symlink rejection and SHA-256 manifests. This is a tested adapter, not a real upstream decompilation.
- `.github/workflows/upstream-probes.yml` pins each real upstream repository revision, builds the native binaries in isolated GitHub-hosted jobs, and calls each once with an **entirely synthetic miniature Afterburner cast**. These fixture runs are informational, since they intentionally lack real editable Director cast relationships.
- **No proprietary game files** are sent to GitHub Actions. Real-game upstream comparisons require an independently installed, vetted binary on a local sandboxed workstation. Do not store their scripts/artwork in public build logs.

When a build probe completes successfully, confirm that this proves **buildability** at its pinned revision, not successful decompilation of a real game. A failure or nonzero synthetic-fixture exit code is not evidence of a deficiency on valid historical game files.

## Runbook

```bash
python -m pip install -e '.[dev]'
python -m coverage run --source=caserecomp -m unittest discover -s tests -q
python -m coverage report -m --fail-under=90

# Source file and output path must be private; output must not exist.
python -m caserecomp convert-local /private/game/MysteryCaseFiles.exe --output /private/converted --image-format png --include-bytecode
python -m caserecomp verify-export /private/converted

# Cast collection
python -m caserecomp convert-local /private/game/data --output /private/casts --image-format png
python -m caserecomp verify-export /private/casts

# After manually building a vetted, separately licensed recovery tool:
python -m caserecomp external-export /private/movie.dcr --output /private/libreshockwave-output --backend libreshockwave --binary /tools/libreshockwave_asset_extractor
python -m caserecomp external-export /private/movie.dcr --output /private/projectorrays-output --backend projectorrays --binary /tools/projectorrays
```

The local conversion manifest contains `schema_version`, source file name and SHA-256, archive kind/version, exported member ID/tag/type/size/hash and skipped categories. The manifest itself is **private metadata**: if published, it may reveal provenance and original filenames. Every export is create-only with rollback on conversion error; `verify-export` rejects missing output files, mismatched hashes, symlinks, duplicate paths, traversal attempts, inconsistent totals and unsupported schema versions.

**Threat model:** The Python extractor checks file sizes, decompression and restricted filenames, does not run game code, refuses source symlinks, skips unknown codecs and never overwrites output. Native third-party executables are not confined by Python's filesystem checks; run only reviewed binaries in a VM/container without sensitive mounts, credentials or internet. GitHub Actions runs use read-only permissions and source-only input, not privileged store/deployment tokens.

## Outstanding work

- [x] Local JPEG full decode and JPEG/PNG conversion, ID3/MP3 metadata validation and extraction, known WAV validation.
- [x] Raw `Lscr` extraction with byte-preserving hashes, without pretending that it is Lingo source.
- [x] Source-only native-tool build probes and explicit local execution adapters.
- [x] Synthetic tests and CI coverage threshold at **90%** or above.
- [ ] Verify ProjectorRays/LibreShockwave **with an actual user-owned movie and casts in a protected local sandbox**, compare Lingo handlers and asset fidelity; no external benchmarks claimed until observed.
- [ ] Associate `CASt` / `KEY*` images with ALFA and implement alpha masks, BITD, palette/color-space rules.
- [ ] Decode SWA `snd` with a verified audio backend and verify output duration/playback in a real renderer.
- [ ] Model deterministic level transitions from reconstructed Lingo and add golden tests; only then start Android frontend integration.

## Verified full-dataset conversion and experimental CI results

The complete **39-archive** local-run was performed with `--image-format png --include-bytecode`. The resulting manifest was re-read and validated without exposing media or bytecode. Aggregate counts: **2,108 decoded PNGs**, **6 metadata-validated MP3 streams**, **80 raw compiled-Lingo fragments**, for **2,194 verified files (53,420,657 bytes)**. Skipped: **29 SWA**, **1 unclassified ediM**. Assets were removed when the temporary directory closed.

The upstream-source experiment is documented in [GitHub Actions run 37564274290](https://github.com/Dr3amyBerry/Case-Recomp/actions/runs/37564274290) (pinned source revisions; no proprietary inputs):
- **ProjectorRays** compiled with C++17 dependencies; running `decompile` on the deliberately incomplete synthetic CCT **aborted (exit 134)** with a read-past-end-of-stream runtime error. The job's successful status means the *build and informational probe step ran*, not that decompilation succeeded.
- **LibreShockwave** compiled its `libreshockwave_asset_extractor` target and accepted the same synthetic CCT (**exit 0**): `members=0 png=0 text=0 sounds=0 palettes=0 raw=0 scripts=0 errors=0`. This verifies the tool starts and parses a tiny empty-style container, **not** that game media are recovered.

Both builds succeeded with the pinned upstream commits; no original-game file was used by the Actions jobs. The next milestone is to run vetted tools against the real private game input in an isolated host environment, compare exact Lingo handler resolution and multimedia rendering, and determine whether LibreShockwave can decode this game's 29 SWA-compressed entries. No such downstream equivalence is yet claimed.
