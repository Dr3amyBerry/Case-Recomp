# Phase 4B — private behavior traces and Phase 5 Android shell

Phase 4B adds a **fail-closed verification bridge** between the static Director model and observable runtime behavior, while starting an Android application shell that contains only synthetic content.

No original art, audio, recovered Lingo source, frame-label text, original coordinates, game executables, CCT/DCR/DIR/CST files or private trace outputs belong in GitHub.

## 1. Private behavior trace contract

`caserecomp.trace` introduces `case-recomp-behavior-trace` v1. A private trace plan is derived from the owned Director movie and records only:

- frame number;
- active-sprite count;
- SHA-256 fingerprint of the structural sprite state at that frame;
- active Score behavior count;
- count of linked compiled scripts;
- SHA-256 fingerprint of the linked handler-bytecode set.

The plan intentionally omits cast names, marker text, coordinates, media and readable Lingo. It is still derived from the commercial game and therefore remains private.

```bash
python -m caserecomp trace-plan /private/MysteryCaseFiles.exe \
  --output /private/trace-plan.json
```

Private validation against the supplied movie produced **29 observation checkpoints**, selected from frame 1, frame 203 and the 28 marker positions. The result contained hashes/counts only.

### Observation gate

A runtime observer must provide the same frame/sprite/handler fingerprints plus an `observable_state_sha256`. Generic input/event categories may also be attached. `trace-compare` rejects a different source hash, missing checkpoints, duplicate frames or any structural mismatch.

```bash
python -m caserecomp trace-compare \
  /private/trace-plan.json /private/runtime-observation.json \
  --output /private/comparison.json
```

Only a comparison with `verified=true` may be passed to `trace-synthetic-fixture`. Promotion produces a content-neutral scenario with synthetic IDs and geometry; it never copies original labels or coordinates.

```bash
python -m caserecomp trace-synthetic-fixture \
  /private/comparison.json \
  --output /private/verified-synthetic.json
```

**Current boundary:** the original movie has a valid private trace plan, but no independent runtime observer has yet supplied the required observable-state trace. Therefore **no Huntsville behavior is claimed verified or promoted into the public fixtures**. The committed `verified-trace-scenario-v1.json` exercises the promotion gate with a fully synthetic trace only.

## 2. Kotlin persistence and deterministic clock

The pure Kotlin engine now includes:

- `GameClock` and `DeterministicClock`;
- versioned `SessionSnapshotV1`;
- deterministic canonical persistence encoding with SHA-256 integrity check;
- `SessionStore` abstraction and in-memory test implementation;
- lifecycle state machine (`NEW → CREATED → STARTED → RESUMED → PAUSED/STOPPED → DESTROYED`);
- `RenderFrame` / `RenderTarget` content-neutral render model;
- touch-to-game input routing using the same letterbox transform as rendering;
- `AudioPort`, `NoopAudioPort` and `RecordingAudioPort` for simulated audio tests.

Restoration fails closed when the checksum is wrong, scenario ID changes, a scene/target is unknown or a persisted frame falls outside its scene range.

## 3. Android shell

`android/` is now a real Gradle project with two modules:

- `:engine` — Android library containing the deterministic Kotlin engine;
- `:app` — minimal native Android shell using `Activity` + custom `View`.

The app contains a synthetic 640×360 room with two synthetic targets. It renders simple shapes, applies aspect-fit letterboxing, converts taps through the same viewport transform, persists state through `SharedPreferences`, and uses a no-op audio port. It bundles **no game assets**.

Build stack selected for the 2026-10 toolchain:

- Android Gradle Plugin `9.4.1`;
- Gradle `9.6.0` in CI;
- JDK 17;
- compile SDK 36, target SDK 36, min SDK 26.

AGP 9.x built-in Kotlin is used; no redundant `org.jetbrains.kotlin.android` plugin is applied.

## 4. Tests

The source-only Python gate remains `>=90%` coverage. New trace tests cover format validation, fail-closed comparison, script-link hashing, private-plan generation with mocks, CLI promotion and safe fixture generation.

Kotlin tests cover:

- session save/restore and checksum corruption;
- deterministic clock;
- lifecycle ordering;
- pure render model;
- letterboxed input routing;
- simulated audio cues;
- synthetic scenario completion.

GitHub Android CI installs the required SDK, runs JVM unit tests for `:engine` and `:app`, then assembles a debug APK that contains only synthetic content. The debug APK is a **shell**, not a playable Huntsville port and not a distribution artifact for the commercial game.


## Post-Phase-8 source provenance link

A private trace plan is now accepted into a `.crcontent` package only when its `source_sha256` is present in the verified `convert-local` manifest's `source_archives` metadata. Both values originate from SHA-256 over the local Director source bytes. This creates a deterministic integrity link from conversion input to the packaged trace plan without publishing the private source or adding synthetic provenance.
