# Case-Recomp: audited source, private evidence, current architecture and playability

> **Documentation-only audit** of `main` at `9e2c37115d14009b25e473197407da4d37abcdd0`. Reviewed the connected original-game Drive folder, the separately uploaded five-folder private workspace, selected manifests and runtime logs, and the public GitHub code. No private game executable, ZIP, .ls script, screenshot or audio was executed or committed. We did not independently rerun the Windows projector, inspect every image, unpack all archives or compute raw-file SHA-256 for every original object. Distinguish *manifest consistency* from *fresh behavioural proof*.

## Decision and current state

The scalable whole-game direction is **a generic Director 8.5/Lingo VM in Kotlin**, running the owner's original compiled Lscr bytecode with a reconstructed Score/Cast runtime, Flash/SWF subset, Xtra substitutes and software stage renderer. ProjectorRays/LibreShockwave are local reference/decompilation tools, **not** automatic Android converters. Existing native-projector Phase 6/9 observations are regression references; manually authoring `.crscene` rules for every new scene is **not** the principal implementation plan.

At this snapshot `android/app/.../MainActivity.kt` **still uses `GameRuntime`, not `DirectorRuntime` or `StageRenderer`**. The new engine is a Kotlin/JVM subsystem and has not been integrated into the Android app's launch/render path. Therefore **no original-game Android APK is demonstrated playable**, even if public Python/Android/API 26/33/36 CI is green.

A private `huntsville-m3/boot-smoke.txt` log reports a local run reaching splash, user entry, original menu, map, first scene and **three finds**. That is a concrete M3 progress record but **not an independently rerun Android test** or proof that all graphics/audio/timing match the original. The log has empty warnings and separate “unknown builtins” diagnostic counts, which require interpretation rather than silent dismissal.

## The two Drive input sets

Exact Google Drive IDs, private downloads and local Windows profile paths are intentionally omitted from this **public** document.

| Source/folder (private) | Observed actual content | Role / confidence |
|---|---|---|
| Original installed game | `MysteryCaseFiles.exe` (11,094,294 bytes), `data/` containing **38 .cct Director cast archives**, auxiliary installer/config/log items | Source of truth; Drive directory metadata verified, original binaries not launched in this audit |
| `case-recomp-phase6/` | `AUDIT.md`, `casts/` (38 directories), `sessions/`, `regen/`, `live/`, `tamper/`, `final/`, private scenario and capture scripts | Original navigation observations, accepted/rejected proof trail, negative import cases |
| `huntsville-first-playable/` | `scene-observation.private.json`, private `.crscene`, `trials/trial-A` and `trial-B`, capture shots, masks/click probes, cast experiments, small helper scripts | Phase 9 target/miss/found/completion reference and experiments |
| `huntsville-decompiled/` | 38 cast directories, editable main `.dir`, manifest, private Lingo bundle and **80 .ls files** in `main/movie/casts/Internal/` | ProjectorRays output for human analysis; **not** executable Android source |
| `huntsville-m3/` | `movie-bundle.private.json`, `lingo-bundle.private.json`, `huntsville.director.zip` (~58 MB), `shots/`, `swf/`, extracted SWFs, `boot-smoke.txt`, `session.txt`, experiment scripts and serialized intermediates | Current private Director/JVM smoke inputs and reference artifacts; ZIP internals not independently audited |
| `tools/` | ProjectorRays executable plus a local source/reference file | Decompilation/reference, **not** an Android runtime binary |

### What the inspected private JSON manifests establish

- `case-recomp-movie-bundle` v1: **28 labels, 38 cast descriptors, 387 internal member records and 38 external casts**.
- `case-recomp-lingo-bundle` v1: **1,608 names and 80 scripts**.
- `case-recomp-scene-observation` v1: **8 targets, 2 trials**, first scene identifier `loc7`.
- `case-recomp-vertical-slice-comparison` v1: `verified=true`, evidence kind `independent-original-runtime`, **4 stage checks, 3 described transitions**.
- The movie, Lingo, scene and Phase 6 comparison manifests **declare the same source SHA-256**. This is a useful cross-manifest identity check, **not** proof every underlying source byte was freshly rehashed here.
- The `.ls` readable listings help analyze game scripts; the new VM runs **compiled Lscr bytecode from the owner's private content**, not those listings shipped inside the APK.

### Evidence integrity: keep failures as well as successes

The private `case-recomp-phase6/AUDIT.md` documents two different outcomes. A historical v1 attempt used flat-colour/repeated image captures, manually supplied 25/40 ms timings and a synthetic scenario: **rejected, zero original rules**. Later independent live native-projector sessions 3 and 4 used bursts and real scene evidence; the private `.crcontent` + `.crflow` v2 promotion supports **two original navigation rules without timing gates**. `tamper/` contains deliberately invalid proofs/packages (wrong binding/source/scene, corrupt/missing trace, v1) useful for future regression tests. Preserve the rejected material as an audit trail; never promote or delete it merely to simplify a folder listing.

Phase 9's independent scene trials establish a golden behaviour reference for **one target configuration**: hit masks, finds/misses, counters, acknowledge/return and persistence. They do not establish randomized target selection or full-game equivalence.

## The chosen whole-game data flow

```text
Private original executable + 38 CCT casts
  -> caserecomp/director.py / score.py / relationships.py / bitmap.py / audio.py
  -> caserecomp/lingo_bytecode.py  -> private compiled Lingo bundle
  -> caserecomp/movie_bundle.py   -> private Score/Cast/movie bundle
  -> caserecomp/director_content.py -> private Director content ZIP
  -> Kotlin engine/lingo/* (LingoVm)
  -> Kotlin engine/director/* (DirectorRuntime, Xtras, StageRenderer)
  -> Kotlin engine/flash/* (embedded SWF/AVM1 subset)
  -> [NOT YET IMPLEMENTED] Android source-bound import + graphics/audio/text/input/lifecycle adapters
  -> [NOT YET VERIFIED] Original game running and playable on Android
```

The older Phase 5 `.crcontent` and the newer private `huntsville.director.zip` are **different content formats**. A migration must preserve hash/path/size validation, source binding, user-initiated import, app-private storage and failure-to-load defaults. Do not treat them as interchangeable.

### Earlier architecture retained as validation, not the main original-game engine

The Android Activity currently instantiates `GameRuntime` from `Engine.kt`/`Runtime.kt`, draws `GameShellView`, and checks Phase 6/9 `.crflow/.crscene` proofs through private repositories. These files **are actively used today**; removing them now breaks the synthetic shell and existing tests. `caserecomp/content_bundle.py`, `scenario.py`, `vertical_slice.py`, `verified_scene.py`, `trace.py`, and `runtime_capture.py` are still useful as legacy-contract producers and projector validation utilities.

ProjectorRays and LibreShockwave were **already** compared against original `Lscr` in Phases 2C/3B: 80 scripts, 527 handlers, 287 names. Decompiled code did not alone certify semantics; later work discovered/fixed an off-by-one cast/script linkage (3/80 to 80/80 correct). Phase 10 moved to executing original compiled bytecode because manual observation of each rule does not scale. In retrospect, an early *VM feasibility spike* should have been considered in parallel much sooner, but decoder, Score/cast, fidelity and QA work remain valuable.

## Honest milestone ledger: progress toward a playable game

| Gate | State now | Required acceptance |
|---|---|---|
| Owned source and 38 casts | Available privately | Read-only source + reproducible provenance |
| Movie/Lingo package generation | Implemented; private bundles observed | Repeatable extraction and strict validation |
| JVM Lingo VM/Director subset/SWF/software renderer | Implemented with synthetic tests | Reproduce native projector's events and pixels at selected golden checkpoints |
| Local M3 boot/menu/map/scene/3 finds | **Reported by private log** | Re-run from clean source bundle + compare screenshots/events |
| **DirectorRuntime integrated into Android Activity** | **NOT DONE** | Import actual private package and launch actual original-menu UI |
| First original scene playable on Android using Lingo VM | **NOT DONE** | Correct clicks, completion, save and projector parity |
| All cases/puzzles/audio/Flash/saves | **NOT VERIFIED** | Broad gameplay and media fidelity matrix |
| Production signing/distribution | Disabled/pending rights review | Explicit independent approval |

GitHub CI success is a statement about **synthetic tests and shell emulators**; it cannot substitute for the private-game gate above. Whole-game completion percentages cannot responsibly be derived from phase count.

## One unambiguous next route

1. Re-run the already-recorded M3 local private smoke from a clean reproducible bundle and current source SHA; compare event order and screenshots against native Windows projector.
2. Add Android-only adapters for decoded images, text, sounds, storage/Xtras, frame clock, touch/key input, lifecycle and disposal. Keep the generic VM and game bytes separate from platform code.
3. Implement a **debug-only Director launcher** without removing `GameRuntime`; import the new package securely (it is not the old `.crcontent`).
4. Prove original menu displayed and clickable, then map, then `loc7`, then verified finds, completion and save, with visual and behaviour goldens.
5. Extend to remaining targets, scenes, profiles, SWF, audio and puzzles. Remove old execution paths only after equivalent QA and an expressly approved deletion plan.

See [RETIREMENT_CANDIDATES.md](RETIREMENT_CANDIDATES.md) for path-level classification and strict no-delete gates.

## Public/private boundary

Never commit private Drive IDs/downloads, owned executable/movie/cast bytes, decompiled `.ls` source, compiled game scripts, screenshots, sounds, private player/profile information or unredacted capture traces. Keep a **private**, separately stored inventory with SHA-256, origin, producer/version, last regression run and retention class. This audit was strictly a documentation update: no deletion, file move or public release.
