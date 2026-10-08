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

## On-device QA with Windows Subsystem for Android (2026-10-08)

WSA (Android 13, 1600×900) on the test PC runs the debug APK with the private package. Nothing proprietary leaves the PC.

**Setup**
1. `adb connect 127.0.0.1:58526`, accept the RSA prompt inside WSA once, check `adb devices`.
2. `gradle :app:assembleDebug` and `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
3. Push the private ZIP to the app's own folder and import it without the document picker:
   `adb push huntsville.director.zip /sdcard/Android/data/org.rigorcore.caserecomp.synthetic.debug/files/import/` then
   `adb shell am start -n org.rigorcore.caserecomp.synthetic.debug/org.rigorcore.caserecomp.app.MainActivity --ez director true --es import_external huntsville.director.zip`.
   Later launches only need `--ez director true`. (In Git Bash set `MSYS_NO_PATHCONV=1` so device paths are not rewritten.)
4. `adb shell setprop log.tag.CaseRecompDirector DEBUG` enables debug-only diagnostics: touch → stage point and hit sprite, fps/tick/draw timings every 5 s, each sound started, and **F12** (`adb shell input keyevent KEYCODE_F12`) logs every on-stage sprite with a point where a click reaches it.
5. Saves exported from the private JVM harness can be applied with `--es import_save <file>.json` (pushed to the same import folder) to jump to any case.

When the stage is shown larger than 800×600 the compositor works at 2× (text drawn at that resolution, bitmaps enlarged pixel-exactly, Flash sampled per stage pixel) and the view scales the frame to the window with bilinear filtering; `--ei stage_scale 1|2` forces a scale for comparison. Stage coordinates map to the 1600×900 WSA display as `screen = (320 + ceil(1.2·x), 105 + ceil(1.2·y))`.

**Verified on WSA:** splash, name entry (keyboard), user list/new user, main menu, case report, map, hidden-object scenes (objects found, list and counter updated, pickup animation, completion dialog), idle and hurry-up prompts, crime computer puzzle including its tutorial, the "ATRAPADO" epilogue and rank badges, the next case, saves persisted across restarts, the final case and the ending newspaper; main theme, ambient music and effects play; music pauses with the app; the Exit button closes the stage; options (volume sliders, help pages), pause menu, case report, hint tokens and highlights, the best-agents leaderboard (with the finished game's score) and the time-out/retry path (checked in the harness). Scenes run at a steady 30 fps (≈5 ms render) and ~90 MB PSS.

**Fixed in this round (engine-generic):** Flash clip masks and strict-decoder JPEG data; Text Xtra styles (font, size, bold, colour, alignment, indents, member rect) and Director-like wrapping; score stretch flag (bitmap/text sprites use member size); score sprite colours colorize text; matte text hit-testing; only sprites with mouse handlers receive the mouse; cast member numbering from each cast's minMember; real cast file sizes for FileIO probes; control-character keys; Flash `duplicateMovieClip`/`removeMovieClip`; SWA and zero-padded MP3 audio without FFmpeg; byte-budgeted media cache and fixed-rate frame pacing; `(sprite n)`/`(member n of castLib m)` behaviour parameters; HTML tables, `<font>` tags and tab stops in `member.html`; Lingo `quit` closing the Android stage; matte ink keyed from the image edge (background transparent drops all white) and fully opaque 32-bit members taking their ink; touch rollovers (a touch moves the pointer first so mouseEnter feedback shows, mouseDown follows after 100 ms or on release, and the pointer leaves the stage after the release) and hover from a connected mouse; 2× compositing with sharp text and smooth scaling (scenes 30 fps at ≈14 ms, main menu ≈22 ms on WSA).

## Known limitations / next engineering tasks

- Rendering is software compositing on the UI thread (fast enough now: ~5 ms per scene frame on WSA).
- Text uses Android system families in place of the title's embedded fonts; per-face width/size factors approximate the originals, so a few labels wrap or size slightly differently from Windows. Only the style of a member's first visible run is applied (no per-run styling yet).
- Score behaviours that reference members missing from every cast (internal 863–866, 909) are ignored, as Director does.
- Every sound start writes a short temporary file for `MediaPlayer`; a `SoundPool` path for short effects would lower latency.
- The Director launcher is still a debug-only path reached from the synthetic shell; release builds stay disabled.
- The VM must not quietly fall back to the manually reconstructed Phase 9 game for unimplemented native semantics. Keep those proofs as independent regression references.
- After C–G pass on the real device, add **specific regression tests for the divergences actually observed**, not another generic round of shell benchmarking.

**Retirement rule:** nothing from the old GameRuntime, verified-flow/scene modules, original game or golden private captures is eligible for removal until the equivalent new Director on-device path has been demonstrated and the user has explicitly approved named deletions. See [RETIREMENT_CANDIDATES.md](RETIREMENT_CANDIDATES.md).
