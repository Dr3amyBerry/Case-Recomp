# Phase 7 - Android QA matrix, lifecycle resilience and performance guardrails

Phase 7 expands verification without changing the fail-closed original-game rule policy. No native-projector capture trial is bundled in CI, therefore private Huntsville navigation remains blocked unless a user imports a separately verified `.crflow`.

## Verified emulator matrix

GitHub Actions runs the full instrumentation suite on API **26**, **33** and **36**, all x86_64. The Phase 7 run passed on all three platforms, covering minSdk, an intermediate Android release and target API 36.

## Lifecycle and persistence

The instrumented suite recreates `MainActivity`, verifies the active save slot, redraws multiple aspect ratios, triggers `onTrimMemory`, then constructs a fresh runtime from app-private storage. A literal self-kill is not used because it would terminate the instrumentation runner itself; the persistence/process-recreation boundary is instead exercised by destroying the Activity/runtime and constructing a new runtime instance.

## Save migration and corruption

The session codec accepts a checksummed historical synthetic v0 format and migrates it to `SessionSnapshotV1` with `savedAtMillis=0`. Corrupt checksums and unknown versions fail closed.

`SharedPreferencesSlotSessionStore` migrates the historical `case-recomp-session/snapshot` autosave into `case-recomp-session-slots/slot.autosave` once, removing the old key only after the new write succeeds.

## Private package and proof lifecycle

Private content is revalidated on every load, including asset size/hash and optional trace-plan hash. Re-importing a content-addressed package repairs a corrupted local copy through quarantine-and-replace. Packages can be deactivated or removed, and `.crflow` proofs can be removed independently.

## Low-memory and audio interruption

`TRIM_MEMORY_RUNNING_LOW` evicts cached private bitmaps so later loads reverify app-private files. Private media uses Android audio focus; loss/transient/duck interruptions pause playback, focus gain can resume a previously interrupted cue, and lifecycle pause/destroy abandon focus and release resources.

## Aspect ratios and performance

Instrumentation renders 320x180, 180x320 and 400x400 after Activity recreation. Existing letterbox hit-testing remains the input source of truth.

The engine JVM suite runs fixed deterministic performance workloads: 50,000 viewport/render iterations and 5,000 session encode/decode round-trips. The 15-second limit is only a catastrophic-regression guardrail, not a claimed device performance budget.

## Phase 7 result

- Python 3.11/3.12/3.13: green.
- Python tests: 212/212.
- Python coverage: 90%.
- Android build/JVM/performance guardrails: green.
- Instrumented API 26: green.
- Instrumented API 33: green.
- Instrumented API 36: green.
- Proprietary game resources committed: 0.

## Release boundary

Phase 7 does not create a redistributable game build. Real-device performance budgets, literal OS process-kill testing, production signing, and any redistribution of third-party game data remain separate review steps.
