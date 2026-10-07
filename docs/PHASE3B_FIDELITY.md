# Phase 3B.4 — cross-tool fidelity evidence and Phase 4 test contract

**Status: privately verified on the supplied game; no recovered game assets or Lingo text are stored in Git.** Public CI continues to use synthetic fixtures only.

## Lingo consensus: ProjectorRays vs LibreShockwave vs original compiled index

The pinned native tools were executed locally on the privately extracted main Director movie. ProjectorRays output required `mac_roman`; LibreShockwave output was valid UTF-8. The repository does not transcode either side silently.

| Check | Result |
|---|---:|
| ProjectorRays complete handlers | **527** |
| LibreShockwave complete handlers | **527** |
| Original compiled handler records | **527** |
| Distinct handler names in each provider/original index | **287 / 287 / 287** |
| Original handler names found by both tools | **287 / 287** |
| Assembly handler records with identical address/opcode sequence | **527 / 527** |
| Distinct assembly names with identical multisets | **287 / 287** |
| Unique-name normalized source bodies exactly equal | **241** |
| Unique-name normalized source bodies different | **19** |
| Names ambiguous because they have multiple definitions | **27** |

The assembly comparison intentionally ignores operands, expression reconstruction and runtime behavior. Consequently, even 527/527 identical address/opcode sequences do **not** prove semantic equivalence. Source-text disagreement is expected from different decompiler renderings. The correct next step for Phase 4 is deterministic event/state replay, not selecting one decompiler as unquestioned source of truth.

## ALFA

All **1,988** uniquely `KEY*`-linked JPEG/ALFA pairs now decode successfully. The earlier 20 failures were not corrupt masks: they are exact-size **raw 8-bit alpha scanlines**. The other **1,968** masks are PackBits-compressed. `decode_alpha_plane()` now accepts only these two layouts and validates exact geometry before composition.

Result: **1,988/1,988** candidate masks pass JPEG RGB + ALFA → RGBA PNG round-trip pixel checks. This verifies the decoded pixel planes, not Director stage ink/compositing semantics.

## BITD

All **21** `BITD` members are associated to their owning `CASt` records by `KEY*` and their geometry/pitch parses cleanly.

| Depth | Count | Current verification |
|---|---:|---|
| 16-bit | **6** | rendered locally |
| 32-bit | **10** | rendered locally |
| 4-bit indexed | **4** | PackBits + palette indices validated; RGB palette pending |
| 8-bit indexed | **1** | PackBits + palette indices validated; RGB palette pending |

The 16 true-color outputs were compared privately against LibreShockwave's decoded PNGs and were **16/16 pixel-identical** after RGBA decode. The five indexed members all declare Director palette id `-102` (System Windows). `decode_bitd_indices()` now reconstructs exact palette indices without inventing RGB values. RGB conversion remains pending until a separately verified palette implementation/reference is chosen.

## SWA

All 29 historical `SWA Decompressor Xtra` resources contain an 82-byte Director/SWA wrapper followed by MPEG audio. For **29/29**, the bytes after that wrapper exactly match LibreShockwave's extracted MP3 payload. Separately, the existing explicit FFmpeg path decodes all 29 to bounded PCM16 WAV candidates. This strongly validates payload recovery but still does not prove historical Macromedia-Xtra timing/loop behavior.

## Fidelity API

`caserecomp.fidelity` compares **decoded data**, not file-container bytes:

- PNG → RGBA dimensions + SHA-256 of decoded pixels.
- WAV → channel/sample-width/rate/frame geometry + SHA-256 of decoded PCM frames.
- `python -m caserecomp fidelity-file LEFT RIGHT --kind png|wav` performs a private pair comparison without writing game data.

These checks are designed for future captured reference frames/audio, emulator output, and renderer regression testing.

## Phase 4 contract

Phase 4 may only promote recovered behavior into Kotlin when a synthetic or private test fixture states the expected transition explicitly. The engine now supports deterministic replay and synthetic hit regions; future original-game traces can be compared to the same reducer without embedding original coordinates or media in public tests.

No APK or complete gameplay reconstruction is claimed by this document.
