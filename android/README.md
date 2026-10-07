# Case-Recomp Android engine: architecture prototype

A synthetic debug APK shell now exists as source and CI output, but no original gameplay implementation exists yet. `engine/` contains a **pure Kotlin**, Android-independent reducer with immutable `Session` state, generic scene/object transitions, versioned scenario-v1 frame ranges, content-neutral engine events, synthetic hit regions, deterministic state/event replay traces and aspect-fit touch coordinate mapping. Every scenario in tests is synthetic. Its state and rules are placeholders, *not claims about Mystery Case Files gameplay*.

## Native target architecture

- **Importer:** locally-owned Director content → Python `convert-local` → versioned, hash-checked private manifest and RGBA PNG/WAV/MP3 assets. Copy into Android app-private storage only after a user-initiated local import; do not bundle original media in a public APK.
- **Engine:** Kotlin deterministic state reducer. `ScenarioV1` mirrors the public contract; frame advancement and emitted events are deterministic. `SessionSnapshotV1`, `GameClock`, lifecycle state, render model and audio ports are now implemented and source-tested. Later stages must consume privately verified traces instead of invented original-game transitions.
- **Renderer:** the initial shell uses a native custom `View` with synthetic shapes and a 640×360 synthetic design viewport. Rendering and input share the same aspect-fit letterbox transform. Director ink effects, animation and original layouts remain pending.
- **Audio:** Android Media3 or platform decoder for local converted streams, pause/resume and lifecycle, synchronized game clock and original loop metadata to be verified.
- **Integration:** view state → engine events, viewport touch positions → game coordinates, local assets → render resources. Offline by default; no banking, accounts or analytics required.
- **QA:** Kotlin smoke/JVM tests cover reducer, persistence, lifecycle, render model, touch mapping, replay and simulated audio. GitHub CI also assembles the synthetic debug APK; device/emulator matrices remain pending.

## Tested locally without Android SDK

```bash
kotlinc android/engine/src/main/kotlin/org/rigorcore/caserecomp/Engine.kt \
  android/engine/src/main/kotlin/org/rigorcore/caserecomp/Runtime.kt \
  android/engine/src/test/kotlin/org/rigorcore/caserecomp/EngineSmoke.kt \
  android/engine/src/test/kotlin/org/rigorcore/caserecomp/RuntimeSmoke.kt \
  -include-runtime -d /tmp/case-engine.jar
java -jar /tmp/case-engine.jar
```

Expected output ends in a PASS message covering replay, persistence, lifecycle, render, input and simulated audio.

For the Android shell, CI uses AGP 9.4.1 + Gradle 9.6.0 + JDK 17 and runs `gradle :engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`.

## Pending dependencies

Until recovered Lingo, cast, score and target layout have been validated against a running original, the application must not pretend that the generic prototype's puzzles represent the original. Future deliverables: separate Android Gradle module, app-private import UI, deterministic time model, bitmap/ink renderer, save/load, multilingual text, sound, tests on emulator/device, and reproducible signed package.


## Phase 4 boundary

The Python `score-structure` command can privately derive DRCF/CAS*/CASt/VWSC/VWLB timing and reference structure. None of that private report is bundled here. Public Kotlin tests intentionally use `fixtures/synthetic-scenario-v1.json`-equivalent geometry and names, not Huntsville data.


## Phase 5 private-content path

The app can import a user-created `.crcontent` ZIP through Android's document picker. The repository and APK contain no commercial media. Imported files are verified and committed under `filesDir/private-content/packages/<sha256>`; images/audio are resolved only through manifest bindings. Long-press the menu screen to open the picker. Save slots and runtime trace drafts also remain under app-private storage. See `../docs/PHASE5.md`.

## Phase 6 verified-flow gate

Private imported content is fail-closed: without an app-private `.crflow` proof bound to the scenario SHA-256, navigation remains on MENU. A proof produced from independently observed original-runtime evidence unlocks only MENU→MAP and MAP→one SCENE, with optional controlled timing thresholds. Synthetic content continues to use the unrestricted test gate. Object-finding and other original rules remain blocked until separately verified.

## Phase 7 QA

Instrumentation is green on API 26, 33 and 36. QA covers Activity/runtime recreation, persisted-slot recovery, historical save migration, corrupt-save fallback, multiple aspect ratios, low-memory bitmap eviction, private package/proof removal and Android audio-focus interruption handling. JVM tests also execute fixed deterministic performance guardrails. Private original-game content remains gated by `.crflow` and no game assets are bundled.
