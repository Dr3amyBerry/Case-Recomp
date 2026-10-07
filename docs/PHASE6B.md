# Phase 6B — native runtime capture consensus

The repository can now prepare and ingest a private second-source observation from the original native projector. This environment cannot execute that Windows projector (Wine is unavailable), so no Huntsville `.crflow` is claimed here.

## Capture pipeline

1. Run the owned original projector on a local desktop.
2. Use `slice-capture-screen` to create private PNG screenshots at the four stage-entry checkpoints.
3. Prepare a private capture-input JSON describing the observed entry frame/marker and monotonic navigation timestamps.
4. `slice-capture-trial` verifies the runtime binary SHA-256 against the private slice plan, hashes decoded RGBA pixels, removes local screenshot paths, and emits a private trial.
5. Repeat at least twice under controlled conditions.
6. `slice-capture-finalize` requires exact visual consensus before emitting an `independent-original-runtime` observation.
7. Feed that observation to `slice-compare`; only a fully matching result can produce `.crflow`.

Internal Score sprite/handler hashes are explicitly **static-bound evidence**. The independent channel is the native-projector identity, stage/marker/frame observation, pixel consensus, navigation sequence and controlled timing probes.

Timing is fail-closed: ordinary transition latency is recorded for QA but never becomes an Android input gate. A non-zero `not_before_ms` is emitted only when repeated trials include explicit rejected-before / accepted-at probes that agree within the configured tolerance.

## Current status

The private static Huntsville ranges remain 1–67, 68–92, 93–97 and 98–102. Because no native-projector screenshots were captured in this environment, original rules promoted in this run remain **0**.
