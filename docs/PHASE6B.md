# Phase 6B — native runtime capture consensus

Phase 6 is **closed**. Two independent native Windows projector sessions were accepted and a private Huntsville `.crflow` v2 promotes **2 navigation rules**. The earlier placeholder attempt was rejected; it is preserved below as historical audit evidence. Gameplay inside the first scene is the next phase.

## Capture pipeline

1. Run the owned original projector on a local desktop.
2. Use `slice-capture-screen` to create private PNG screenshots at the four stage-entry checkpoints. For animated stages, use `--burst`: it creates a new private directory containing numbered PNGs and `capture-burst.json`, which records decoded-pixel and PNG hashes for every frame.
3. Prepare a private capture-input JSON describing the observed entry frame/marker and monotonic navigation timestamps.
4. `slice-capture-trial` verifies the runtime binary SHA-256 against the private slice plan, hashes decoded RGBA pixels, removes local screenshot paths, and emits a private trial.
5. Repeat at least twice under controlled conditions.
6. `slice-capture-finalize` requires exact visual consensus before emitting an `independent-original-runtime` observation.
7. Feed that observation to `slice-compare`.
8. `slice-flow-proof` must receive the original plan and observation again; it recomputes the comparison and rejects a stale or hand-edited comparison before any `.crflow` is emitted.

Internal Score sprite/handler hashes are explicitly **static-bound evidence**. The independent channel is the native-projector identity, stage/marker/frame observation, pixel consensus, navigation sequence and controlled timing probes.

Timing is fail-closed: ordinary transition latency is recorded for QA but never becomes an Android input gate. A non-zero `not_before_ms` is emitted only when repeated trials include explicit rejected-before / accepted-at probes that agree within the configured tolerance.

### Animated-stage burst procedure

Run a burst at the same stage entry in each fresh session, with the same bounding box and cadence:

```powershell
python -m caserecomp slice-capture-screen --burst --count 240 --interval-ms 16 `
  --output private/menu-session-1 --bbox 100 100 900 700
```

`--output` is a create-only directory in this mode. Compare the `pixel_sha256` values in the two private `capture-burst.json` manifests and select a digest present in every session; use each matching `frames/NNNNNN.png` in that session's capture-input JSON. This preserves the existing exact-pixel consensus rule while making the raw capture cadence, frame list and hashes reproducible in the repository. Do not commit the bursts or manifests: they are screenshots of proprietary runtime content.

## Current status

The accepted Windows run uses ranges 1-67, 68-92, 93-127 and 128-132. MENU to MAP and MAP to SCENE are verified, both with `not_before_ms = 0`. No timing gate or hidden-object interaction was promoted by Phase 6. See the native Windows closure below for the accepted evidence and digests.


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

Real Huntsville captures and proofs remain private, outside the repository. The accepted private run promotes **2 navigation rules**; public tests contain synthetic evidence only.


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


### Final Android semantic boundary hardening

Android v2 parsing now requires `version` and every `not_before_ms` to be actual integer JSON numbers. Numerically integral floating spellings such as `2.0` or `100.0` are rejected even when a caller recomputes a syntactically valid `binding_sha256`.

The app repository and runtime gate also independently require every proof rule that enters `SCENE` to reference a scene ID present in the active, hash-bound scenario. This closes the remaining gap where a proof could be structurally valid, package/scenario-hash bound and correctly re-hashed while still naming a nonexistent scene.

No v1 migration was added because reconstructing the missing v2 evidence chain would reduce the fail-closed guarantee.


Python and Android now share the same strict numeric interpretation for the proof version: `version: 2.0` is rejected even if its binding digest is recomputed. This prevents cross-language acceptance differences at the `.crflow` trust boundary.

The timing domain is also cross-language aligned: `not_before_ms` must be a non-negative integer JSON number within signed 64-bit range. Android preserves it as `Long`; Python rejects values above `Long.MAX_VALUE`.


## Android source binding to packaged trace-plan

Android now parses and validates the packaged Phase 4B `trace-plan.json` rather than treating it as an opaque hash-only file. Its exact schema, observation contract, step bounds and hashes are checked both at import and every later package load.

A `.crflow` can be imported or recovered only when the active `.crcontent` contains such a validated trace plan and `crflow.source_sha256 == trace-plan.source_sha256`.

The trace-plan file hash is already bound into the `.crcontent` manifest and package ID, while the `.crflow` is independently bound to that package ID. Combined with the packager-side check against `convert-local source_archives[].sha256`, this closes the local source-integrity chain without adding invented provenance. A content package without a trace plan remains usable for private media/scenario loading but cannot unlock verified original navigation.


## Audit of the 2026-10-07 Windows run (historical branch `feat/phase6-vertical-slice-flow`)

A Windows run on the machine holding the owned projector produced a v1 `fixtures/vertical-slice.crflow` claiming two promoted rules (MENU → MAP at 25 ms, MAP → SCENE at 40 ms). That proof was **not** merged. Re-auditing its private inputs against current `main` gave:

| Link | Result |
|---|---|
| Projector SHA-256 | `0ef4a73bceae060970d2513f5f6132663843064d7106b724db46cac810a4b28d`, genuine owned file |
| `convert-local source_archives[0].sha256` → `trace-plan.source_sha256` → `slice-plan.source_sha256` | all equal the projector digest |
| Slice plan regenerated with current `main` | byte-identical to the historical plan (static ranges 1–67, 68–92, 93–97, 98–102 confirmed) |
| Native screenshots | **not native captures**: each of the four PNGs is one flat RGB color over 800×600, written within 25 ms of each other |
| Capture trials | 2 files, but both reference the same four PNGs; the second input equals the first except for its trial ID and +1 ms on each `visible_at_ms` |
| Gate probes | identical hand-authored 24/25 ms and 39/40 ms values in both inputs; no probing log or capture record exists |
| Scenario binding | `synthetic-phase4` / `room-a`, the canonical hash of the public `fixtures/synthetic-scenario-v1.json`, not a private Huntsville scenario |
| Private Huntsville `.crcontent` / package ID | none exists |

The pre-audit pipeline accepted these placeholders and emitted an `independent-original-runtime` observation. `slice-capture-trial` now rejects a menu/map/scene screenshot that is a single flat color while claiming an observed marker, and both trial building and validation require the four stage screenshots of a trial to be visually distinct.

Result for that historical run: **0 trials accepted.** The historical 25 ms / 40 ms values are discarded because no observation supports them. Phase 6 was then closed with a new native capture run, described below.


## Native Windows closure (2026-10-07)

The owned projector was run on the same Windows machine and driven through boot → menu → map → first case scene in fresh processes. Nothing below is committed except digests and aggregate facts; screenshots, scenario, `.crcontent` and `.crflow` stay private.

**Stage identification from static data, not guesses.** Each stage was matched against bitmaps the owned movie places on the Score at that stage's entry frame (mean absolute RGB error, 0–255): boot frame 1 = 0.9, menu = 22.4 (button overlays), map = 10.2, scene = 18.5. The scene reached from the Case 1 map is drawn from a location cast whose member sizes match 82 sprites at Score frame 128 versus at most 1 for any other location cast. The historical plan's scene label (frame 98) is a different location, so the slice plan was regenerated with the frame-128 scene label: stages 1–67, 68–92, 93–127, 128–132.

**Trials.** Two independent sessions (`native-session-3`, `native-session-4`), each a fresh projector process with measured click and visibility timestamps. Menu, map and scene contain looping animations (button art, a magnifier icon, a noise texture), so single-shot captures differ between sessions. Each stage was therefore captured as a ~4 s burst and consensus used a frame that appears byte-for-byte in both sessions (common distinct frames: boot 10, menu 42, map 1, scene 22), choosing the one closest to the static entry bitmap. The exact-pixel consensus rule was not relaxed.

| Artifact | SHA-256 |
|---|---|
| source (projector) | `0ef4a73bceae060970d2513f5f6132663843064d7106b724db46cac810a4b28d` |
| `spec_sha256` | `0d61d61842db76404f8a012d8046292f510c6f1bf813f0a3e27fdf98cf3809c1` |
| `observation_sha256` | `d6f9ce73f9b2c46cae5ee96596462f3b88c5b669f336bbaaf31ee147d5f22b31` |
| `capture_consensus_sha256` | `cefc7e446471938af7b43e38675253d707f42408b945b507bc7dc651e9437dd8` |
| private `.crcontent` package ID | `769848b090498b446e5bf9ed5ab298c4775efda67f6499409f8388ec806884b8` |
| private scenario SHA-256 | `bcfcf4f606726d8326106baf9b16aca662ceb167d24ce522bc6b8e2aa922ae61` |
| `.crflow` v2 `binding_sha256` | `5847cb8e2734bafaaeb0fbc19a7b78fc23f82bf772271e969ebd2cfc85731667` |

**Promoted rules: 2**, `MENU + start → MAP` and `MAP + enter-scene → SCENE`, both with `not_before_ms = 0`. No gate probes were measured, so `timing_verified` is false and no timing gate is promoted. The menu was observed to ignore an early click for a short period after appearing; that behavior is recorded privately but not quantified at millisecond resolution and is not promoted.

**Private scenario.** One scene (Score frames 128–132, 800×600 design). Its target rectangles are the Score sprite regions present at the scene entry frame. They are structural geometry, not verified findable objects; object finding remains blocked.

**Android validation (private, local emulator API 35, not in CI).** A local-only instrumented test imported the real `.crcontent` (trace-plan source = projector digest) and the v2 proof, and navigation reached SCENE only through the verified gate (deny-all without it). It rejected, without replacing the valid proof: a different source, package or scenario (all with recomputed binding), a nonexistent scene (recomputed binding), an edited evidence chain, a wrong binding and the legacy v1 proof. A correctly bound proof was refused for a package without a trace plan, and a package with a corrupted trace plan was refused at import. On the Python side, a duplicated trial, a stale comparison and a mismatched slice plan were each rejected.
