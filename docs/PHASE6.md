# Phase 6 — verified vertical slice gate

Phase 6 narrows gameplay reconstruction to one private flow: boot, menu, map and entry into a single scene. The repository still does not contain original media, marker names, Lingo source or original coordinates.

## Private static plan

`slice-plan` hashes the entry sprite state and the set of compiled handlers structurally linked through Score/CAS*/CASt/Lscr for four stages selected by **user-supplied private marker names**. Marker names are never emitted; only their hashes are retained.

Private validation on the owned Spanish Huntsville movie produced four ordered ranges: frames 1–67, 68–92, 93–97 and 98–102. Entry sprite counts are 1, 19, 61 and 95; linked script counts are 0, 15, 18 and 17. These facts do not prove navigation semantics.

## Independent observation requirement

A `case-recomp-vertical-slice-observation` must independently observe the original runtime. It must match the movie SHA-256 and the stage entry frame, sprite fingerprint and handler-set fingerprint. Synthetic observations are accepted only for regression testing and **can never produce a verified-flow proof**.

Controlled timing is optional. Timing is promoted only when the observation explicitly marks it controlled and contains at least two trials; otherwise generated navigation rules use zero timing threshold.

## .crflow proof

`slice-flow-proof` emits `case-recomp-verified-flow` only after an independent-original-runtime comparison passes. The v2 proof is bound to the private-content package ID, private scenario ID, canonical scenario SHA-256 and the Phase 6B evidence-chain hashes. It contains only generic transitions:

- MENU + start -> MAP
- MAP + enter-scene -> SCENE

Boot is evidence-verified outside the Android state machine. Object finding, puzzle rules, back navigation and other original behaviors remain blocked until separately verified.

## Current original-game status

The private static plan is complete, but no independent original-runtime observation was available in this environment. Therefore the current verified original-rule count remains **0**. Android Phase 6 is intentionally fail-closed for private content without a valid `.crflow` proof.


## Android .crflow v2 boundary

Android accepts only `case-recomp-verified-flow` version 2. Version 1/legacy proofs are rejected rather than migrated because the missing package binding and evidence-chain hashes cannot be reconstructed securely from the old proof alone.

The parser requires an exact schema, including:

- `package_id`,
- `scenario_id` and `scenario_sha256`,
- `source_sha256`,
- `evidence_chain.spec_sha256`,
- `evidence_chain.observation_sha256`,
- `evidence_chain.capture_consensus_sha256`,
- exactly the two expected navigation rule IDs and shapes.

The app repository verifies the proof package ID against the active `.crcontent` manifest before writing it to app-private storage and repeats that validation every time the proof is loaded. A rejected import does not replace an already-valid proof. A stale, legacy or manipulated stored proof loads as no proof, so private content falls back to the deny-all gate.
