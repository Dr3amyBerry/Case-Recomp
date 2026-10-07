# Phase 3B.2: alpha masks, BITD, SWA audio and Lingo comparison

**Status: implemented and privately tested as of 2026-10-06.** The supplied game files, private conversions, WAV samples, masks, scripts, and source-code reconstructions remain outside the public repository. All public test fixtures use synthetic samples. This document reports precisely what has and has not been verified.

## `CASt` ↔ `KEY*` ↔ `ediM` / `ALFA`

`caserecomp.relationships.CastRelationships` identifies members by `KEY*` ownership, refusing ambiguous or absent mappings. `caserecomp.bitmap.decode_alpha_plane()` expands *Director PackBits* RLE to 8-bit gray alpha, one row at a time, discarding the final pad byte when the JPEG has an odd width. `compose_jpeg_alpha()` pairs decoded ALFA with the JPEG **only after both are known to belong to one cast owner**; it validates dimensions, writes an RGBA PNG and verifies each decoded RGB and alpha pixel after PNG round-trip. Neither an arbitrary adjacent ID nor a blank/opaque placeholder is accepted as decoded alpha.

Private end-to-end run: **1,988** uniquely linked `ediM` + `ALFA`; **1,968** masks decoded and successfully applied; **20** masks rejected by strict PackBits/geometry checks (including 19 malformed or differently encoded runs and 1 mismatched length). Invalid/unhandled masks are reported, **not** silently rendered as transparent. `--alpha-mode strict` refuses a mismatched mask and rolls back the entire new output directory. `--alpha-mode best-effort` exports the validated opaque PNG and marks `alpha_decode_status=unsupported-mask`; default `off` preserves Phase 3A behavior. Not all `ediM` are JPEG; no alpha is applied to unsupported member types.

**Pixel-fidelity scope:** PNG re-read equals the decoded JPEG RGB plus the decoded ALFA plane exactly. This does **not** establish bit-exact equivalence to Macromedia Director's stage renderer, ink modes, matte/mask semantics, palette, or actual in-game compositing. Full visual match awaits captured comparison frames from a verified original runtime.

## `BITD`

All **21** private `BITD` resources can be expanded as finite PackBits candidate byte streams, but that alone is insufficient to recover images. `probe_bitd()` returns expansion size and explicitly `bitmap_rendered=false`. Correct native conversion depends on each owning `CASt`'s dimensions, row stride, color depth, palette/CLUT, and possible Director-specific ink behavior. **No BITD has been mislabeled PNG.** Unknown color formats remain raw opt-in.

## Director SWA (`snd `) — optional FFmpeg audio decode

The main movie contains **29** `snd ` resources whose Afterburner compression descriptor names `SWA Decompressor Xtra`. The existing generic resource decoder correctly refuses this compressor. Investigation of the **on-disk compressed payload** established that FFprobe recognizes MPEG Audio framing after the SWA prefix. A private test fed all 29 encoded chunks to installed FFmpeg with a forced MPEG demuxer (`-f mp3`), a bounded WAV output and strict decoder errors; **29/29** produced nonempty PCM16 WAV (22,050 Hz mono; **1,914,582** WAV bytes in a probe run).

`caserecomp.audio.swa_encoded_resource()` reads the raw on-disk codec payload without claiming that Director's SWA proprietary uncompressed representation has been reconstructed. The explicit `--decode-swa --ffmpeg /absolute/path/to/ffmpeg` option invokes a locally vetted FFmpeg binary, checks timeout, output byte cap, valid PCM WAV headers, frame count, channel/rate bounds, and SHA-256 after private export. The default remains **off**. This decoder produces audible PCM candidates, **not verified bit-exact output from Macromedia Xtra**: loop points, delay, original SWA settings and hardware playback remain to compare.

**Combined private reproducibility run** (the original movie and all 38 casts, `--image-format png --alpha-mode best-effort --decode-swa --include-bytecode`): 39 containers, **2,223** files: 2,108 PNG (1,968 applied ALFA), 29 WAV, 6 ID3/MP3, 80 raw Lscr chunks; **59,559,339 bytes** exported and reverified using SHA-256; temporary extracted files removed after completion. 20 candidate ALFA masks remain unsupported. The exported images/audio are not uploaded to GitHub and are not CI fixtures.

## Lingo handlers

Pinned tool builds: ProjectorRays `6f9bcebf626b43719abe2affcbbcb041d154d666` (MPL-2.0), LibreShockwave `fca530f9ef388d7ff38fa6c7117feae5bb5411c6` (AGPL-3.0). The existing CI source-only probes established buildability, not full-game Lingo recovery. Tool invocation remains opt-in and local. ProjectorRays requests `--dump-scripts`; LibreShockwave exports `.ls` and `.lsasm` when available.

`caserecomp.lingo_compare.compare_directories()` reads private `*.ls` outputs and compares **complete** `on NAME` / `function NAME` handlers case-insensitively with conservative whitespace-only normalization; duplicate name collisions are flagged as ambiguous. Each side's body is hashed and only normalized text identity is reported. An optional `--redact-names` mode replaces handler names with SHA-256 identifiers. **This is a structural cross-tool comparison, not bytecode-to-Lingo decompilation or runtime semantic verification**. A real game comparison remains pending until decompilers produce verified `.ls` outputs in a sandbox. The absence of these outputs must be reported, not fabricated.

## Verified local commands

```bash
python -m caserecomp director-map /private/game/data/01.cct --links
python -m caserecomp convert-local /private/game \
  --output /private/export-p3b --image-format png --alpha-mode best-effort \
  --decode-swa --ffmpeg /usr/bin/ffmpeg --include-bytecode
python -m caserecomp verify-export /private/export-p3b
python -m caserecomp compare-lingo /private/recovered/projectorrays /private/recovered/libreshockwave \
  --output /private/lingo-comparison.json --redact-names
python -m coverage run --source=caserecomp -m unittest discover -s tests -q
python -m coverage report --fail-under=90
```

## Boundaries and next research

1. Decode 20 unhandled ALFA edge cases and BITD color/palette metadata before claiming complete pixel fidelity.
2. Compare sample WAVs and timing in an original Director runtime; external FFmpeg decoding alone is not a substitute for audio fidelity testing.
3. Recover the original 80 `Lscr` bytecode units to readable Lingo with vetted, separately licensed native tools; compare names, handler bodies, event order, state transitions and original gameplay in a sandbox.
4. Android `android/engine` is a **pure Kotlin, tested architectural prototype** (menu, map, scene, targets, letterbox input) using synthetic scenarios. It is not an Android application and does not implement proprietary level design or original game logic. Its asset importer, renderer, timer, audio mixer, save data and in-game scripted behavior are future work.

**Safety:** third-party executables consume untrusted binary inputs; execute only on trusted software in an isolated environment without sensitive credentials or networks. Files remain private and outputs must stay outside Git repositories. CI does not have access to game files.

## Follow-up — verified Lingo handler directory

In parallel with visual/audio conversion, `lingo-index` now traces **80** original
`Lscr` bytecode containers through `LctX` into a **1,608**-symbol `Lnam` table.
It structurally validates **527** handler bytecode spans (**287** distinct names).
These are symbol names and body hashes, **not recovered Lingo text**. The
`compare-lingo --reference-movie` mode checks independently recovered `.ls`
handler names against the original index if such results exist. Details and
limitations: [PHASE3B_LINGO_INDEX.md](PHASE3B_LINGO_INDEX.md).
