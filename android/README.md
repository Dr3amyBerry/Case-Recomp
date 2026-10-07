# Case-Recomp Android engine: architecture prototype

No APK and no original gameplay implementation exists yet. `engine/` currently contains a **pure Kotlin**, Android-independent reducer with immutable `Session` state, generic scene/object transitions, synthetic hit regions, deterministic replay traces and aspect-fit touch coordinate mapping. Every scenario in tests is synthetic. Its state and rules are placeholders, *not claims about Mystery Case Files gameplay*.

## Native target architecture

- **Importer:** locally-owned Director content → Python `convert-local` → versioned, hash-checked private manifest and RGBA PNG/WAV/MP3 assets. Copy into Android app-private storage only after a user-initiated local import; do not bundle original media in a public APK.
- **Engine:** Kotlin deterministic state reducer. Later stages must read verified level/timeline/Lingo models instead of invented transitions. Persistence will serialize data with explicit versioning and migration.
- **Renderer:** Android `SurfaceView`/Compose bridge (to be selected after profiling), virtual 640×480 design viewport as *placeholder*, fitted without distorting aspect ratio; letterboxed areas ignore taps. Draw order, Director ink effects and animation are pending.
- **Audio:** Android Media3 or platform decoder for local converted streams, pause/resume and lifecycle, synchronized game clock and original loop metadata to be verified.
- **Integration:** view state → engine events, viewport touch positions → game coordinates, local assets → render resources. Offline by default; no banking, accounts or analytics required.
- **QA:** Kotlin smoke tests for reducer correctness, touch mapping and deterministic scene transitions. On-device test matrices and a debug APK workflow are not yet implemented.

## Tested locally without Android SDK

```bash
kotlinc android/engine/src/main/kotlin/org/rigorcore/caserecomp/Engine.kt \
  android/engine/src/test/kotlin/org/rigorcore/caserecomp/EngineSmoke.kt \
  -include-runtime -d /tmp/case-engine.jar
java -jar /tmp/case-engine.jar
```

Expected: `Kotlin engine smoke: PASS (states, discoveries, margins, hit-test, replay)`.

## Pending dependencies

Until recovered Lingo, cast, score and target layout have been validated against a running original, the application must not pretend that the generic prototype's puzzles represent the original. Future deliverables: separate Android Gradle module, app-private import UI, deterministic time model, bitmap/ink renderer, save/load, multilingual text, sound, tests on emulator/device, and reproducible signed package.
