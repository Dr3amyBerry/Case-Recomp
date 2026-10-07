# Phase 7 QA preparation

Phase 7 begins only after the Phase 6B evidence gate remains green. No private game media belongs in CI.

## Required gates

| Area | Gate |
|---|---|
| Python analysis | Python 3.11/3.12/3.13, coverage >=90% |
| Native capture | >=2 controlled native-projector trials, exact pixel consensus at boot/menu/map/scene |
| Static/runtime bridge | source SHA, entry frame, Score sprite fingerprint and handler-set fingerprint match |
| Timing | navigation timing is promoted only when explicit rejection/acceptance probes agree within tolerance |
| Android engine | deterministic replay, persistence restore, verified-flow fail-closed behavior |
| Visual | deterministic RenderFrame golden plus emulator bitmap render |
| Input | letterbox transform and emulator touch path |
| Audio | lifecycle pause/resume/release and cue isolation |
| Storage | .crcontent/.crflow remain app-private and hash-bound |
| Emulator | API 36 instrumented suite; Phase 7 should extend to min/mid SDK representatives |
| Distribution | no commercial media, .crcontent, .crflow, APK or AAB committed |

## Device matrix planned

Phase 7 should exercise at least minSdk 26, a middle supported API, and target API 36; small/large aspect ratios; rotation/recreation; low-memory process recreation; audio interruption; corrupt/old save slots; invalid private package/proof; and repeated import/removal.

## Performance budgets to establish

Measure rather than guess: cold shell startup, content import throughput, first bitmap decode, steady-state bitmap cache, frame render time, input-to-state latency and audio cue startup. Budgets must be based on representative devices before becoming release gates.
