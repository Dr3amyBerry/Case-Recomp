# Phase 6B — native runtime capture consensus

The repository can now prepare and ingest a private second-source observation from the original native projector. This environment cannot execute that Windows projector (Wine is unavailable), so no Huntsville `.crflow` is claimed here.

## Capture pipeline

1. Run the owned original projector on a local desktop.
2. Use `slice-capture-screen` to create private PNG screenshots at the four stage-entry checkpoints.
3. Prepare a private capture-input JSON describing the observed entry frame/marker and monotonic navigation timestamps.
4. `slice-capture-trial` verifies the runtime binary SHA-256 against the private slice plan, hashes decoded RGBA pixels, removes local screenshot paths, and emits a private trial.
5. Repeat at least twice under controlled conditions.
6. `slice-capture-finalize` requires exact visual consensus before emitting an `independent-original-runtime` observation.
7. Feed that observation to `slice-compare`.
8. `slice-flow-proof` must receive the original plan and observation again; it recomputes the comparison and rejects a stale or hand-edited comparison before any `.crflow` is emitted.

Internal Score sprite/handler hashes are explicitly **static-bound evidence**. The independent channel is the native-projector identity, stage/marker/frame observation, pixel consensus, navigation sequence and controlled timing probes.

Timing is fail-closed: ordinary transition latency is recorded for QA but never becomes an Android input gate. A non-zero `not_before_ms` is emitted only when repeated trials include explicit rejected-before / accepted-at probes that agree within the configured tolerance.

## Current status

The private static Huntsville ranges remain 1–67, 68–92, 93–97 and 98–102. Because no native-projector screenshots were captured in this environment, original rules promoted in this run remain **0**.


## Post-Phase-8 hardening

Native trial handling is now fail-closed at additional boundaries:

- every trial is bound to the canonical SHA-256 of the exact vertical-slice plan,
- two files with the same hashed trial ID cannot satisfy the repeated-trial requirement,
- persisted trial privacy flags are mandatory,
- stage dimensions and marker evidence are validated,
- `input_at_ms`, `visible_at_ms` and `latency_ms` must be internally consistent,
- timing probes must contain exactly a rejected-before and accepted-at value in valid order,
- consensus records a hash of the distinct trial set,
- observation validation checks dimensions, latency summaries and timing tolerance,
- the comparison records plan, observation and native-consensus digests,
- `slice-flow-proof` recomputes the comparison from the supplied plan/observation instead of trusting a comparison file by itself.

This protects against accidental duplication, stale files and local JSON editing. It is an integrity chain, not remote attestation: a hostile local actor controlling every private input can still fabricate private evidence. Therefore real Huntsville promotion still requires independently obtained native-projector captures and human provenance discipline.

No real Huntsville capture trial has been added to the repository; original rules promoted remain **0**.


## Android import boundary

The Python proof emitted after this chain is now `.crflow` v2 and requires the target private-content `package_id`. Android independently validates the complete `evidence_chain`, package/scenario bindings and a canonical `binding_sha256` covering the whole v2 proof before persistence and again on load. The digest detects stale or partially edited proofs but is not remote attestation or a signature.

Legacy v1 proofs are intentionally not migrated. They do not contain enough information to recreate the v2 chain without weakening the fail-closed guarantee. Re-run the private evidence pipeline and explicitly generate a v2 proof instead.


### Canonical Android persistence and interrupted-import recovery

The Android repository now persists only the canonical UTF-8 representation of a proof **after** v2 parsing, binding-hash validation and package/scenario checks. Equivalent caller-controlled JSON whitespace or key ordering is not retained.

The `.tmp`/`.bak` commit path is also fail-closed across interruption:

- stale `.tmp` files are never promoted,
- if the active `.crflow` is missing but a `.bak` remains, the backup is restored only after full v2 parser validation and active package/scenario binding,
- malformed or cross-package backups are deleted rather than recovered,
- a failed replacement leaves the previous valid proof recoverable.

This is crash-consistency hardening only; it does not turn the local evidence chain into remote attestation.

`clearFor()` removes the active proof and all transactional `.tmp`/`.bak` copies, so an explicitly cleared proof cannot later reappear through crash recovery. If a target exists but is corrupt while a package-bound backup remains valid, recovery restores only that fully validated backup.
