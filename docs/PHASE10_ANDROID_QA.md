# Phase 10 — Android Director VM private acceptance checklist

> **Execution handoff, not a claim of passing original-game QA.** The public repository holds a **debug-only experimental Director launcher** plus synthetic tests. All original Huntsville ZIPs, screenshots, restored Windows states, bytecode/decompiled scripts and user save files stay outside GitHub.

## What was implemented

- An isolated, non-exported `DirectorLauncherActivity` in the debuggable Android app; the existing synthetic `MainActivity` / `GameRuntime` remains intact.
- ZIP import through Android's user-selected document picker, separate from the earlier Phase 5 `.crcontent` format. A cap on compressed size, manifest/path/count/entry sizes, complete streaming per-entry SHA-256 verification, source identity agreement between manifest/movie/Lingo, stored ZIP SHA-256 and package-specific app-private save storage.
- Software stage output from `DirectorRuntime` / `LingoVm` / `StageRenderer` with Android PNG/JPEG decoder, text rasterizer/metrics, SoundOutput/MediaPlayer, offline StandardXtras, view input and aspect-fit touch mapping.
- Fixed frame pacing (bounded 1–60 FPS based on Score tempo), pause/resume, keyboard button and debug-only startup route. The original legacy import/proof flow stays available.

These components exist in code; they are **not verified to reproduce Huntsville on real hardware yet**.

## Required private inputs (never commit)

1. Owner-provided original executable plus 38 casts for provenance and native-projector comparison.
2. The locally created `huntsville-m3/huntsville.director.zip` director-content package with the movie, bytecode and media. This ZIP **contains proprietary game content**. It does **not** belong in CI/artifacts or a public APK. Confirm its SHA-256 and run the standalone Python package builder again if it is stale or corrupt.
3. The native projector golden screenshots/trials in `case-recomp-phase6/` and `huntsville-first-playable/` and the local M3 script/boot smoke report.

## Build/install steps on the Windows test PC

1. `git fetch --all --prune`; verify a clean `main` checkout at the desired commit. Do not create branches.
2. Build/debug-only from `android/` with `gradle --no-daemon :engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`. No `assembleRelease` exists; signing/distribution remains disabled.
3. Install only the locally produced debug APK with a user-selected `adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk`. Check which app package already exists before installing. Do not uninstall personal apps or erase saves automatically.
4. Transfer the privately created Director ZIP through a controlled local method to a location visible in the Android document picker (USB transfer or explicit `adb push` to the user's private test-device Documents directory). **Never send the game bundle to GitHub Actions**.
5. Open the synthetic shell. Long-press its screen (hold > Android long-press threshold, then release). Select **Launch Director VM (private ZIP)**, then **Import private Director ZIP**. The separate launcher can also be reached via the debuggable options menu on devices that expose it.
6. The import must either reject safely with a meaningful message or open the private Director stage. The existing shell remains usable if the import fails. Input letters with **Keyboard**. If runtime errors occur, preserve a redacted local logcat and the exact input/step sequence.

## Acceptance ladder — do not jump from a green build to “Huntsville playable”

| Gate | Pass criterion | Evidence |
|---|---|---|
| A. Debug packaging | Builds with release still disabled; synthetic unit tests green | CI logs + APK SHA |
| B. Synthetic Android VM | Synthetic Director ZIP imports and renders on emulator API 26/33/36; wrong source rejects | Instrumented test report |
| C. Private ZIP import | Owned ZIP validates without leaking into logs/commits, source hashes agree | Local redacted import report + package SHA |
| D. Original splash + name entry | Stage is nonblank and corresponds to projector images, keyboard works | Redacted diff metrics + private PNGs |
| E. Menu → map | Actual Lingo click handler causes the observed screen transition | Native vs Android frame/event checkpoints |
| F. Map → `loc7` | Original Scene frame 128 reached with valid cast media, dialog behavior compared | Frame and event trace |
| G. Three M3 reference finds | Same three logged objects can be clicked; no unexpected miss / counter discrepancy | Run `huntsville-m3/session.txt` equivalent inputs; no public screenshots |
| H. First scene completion + save | All 8 targets in one observed configuration, completion/return/reopen and restored save consistent | Compare Phase 9 trial A/B and native projector |
| I. Full game | Other scene configurations, cases/puzzles, flash, audio, timing, Xtras and save fidelity | Multi-case acceptance matrix |

**B–I are distinct gates.** Even B succeeding does not establish C–I. Do not claim full playability or a performance target on the basis of shell PSS/jank measurements.

## What to collect privately when something fails

- Main/parent Git SHA, Android version/model, package ZIP SHA-256, media conversion/FFmpeg flags and loader output.
- Stage label, Score frame, sprite/active-sprite identity, last input, `DirectorRuntime.warnings` and bounded Lingo call trace if available.
- Original projector reference screenshot/timepoint, Android screenshot and a private image diff; document whether failure is semantic, render, audio, Flash, cast-loading or platform Xtra.
- Whether saves survive on-device force-stop/relaunch and whether an earlier `.crcontent` package remains usable through the old shell.
- Keep all proprietary logs, user profiles, private screenshots and raw Lingo out of GitHub; publish only sanitized aggregate issue summaries and synthetic reproductions.

## Known limitations / next engineering tasks

- The new Android launcher currently uses software rendering on the UI thread and a simple frame scheduler. Measure/optimize only after real-game correctness is established.
- Exact Director text metrics/fields, Flash actions, alpha/ink modes, Xtra behavior, sound duration/loop semantics and save compatibility can differ from Windows.
- Audio is a best-effort `MediaPlayer` adapter. Undecoded SWA sounds in an input package cannot play until its private generator is run with a vetted FFmpeg conversion path.
- The VM must not quietly fall back to the manually reconstructed Phase 9 game for unimplemented native semantics. Keep those proofs as independent regression references.
- After C–G pass on the real device, add **specific regression tests for the divergences actually observed**, not another generic round of shell benchmarking.

**Retirement rule:** nothing from the old GameRuntime, verified-flow/scene modules, original game or golden private captures is eligible for removal until the equivalent new Director on-device path has been demonstrated and the user has explicitly approved named deletions. See [RETIREMENT_CANDIDATES.md](RETIREMENT_CANDIDATES.md).
