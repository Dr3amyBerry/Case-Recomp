# Case-Recomp — legacy source and private artifact retirement candidates

> **THIS IS NOT A DELETION PLAN TO EXECUTE AUTOMATICALLY.** Audit baseline `9e2c37115d14009b25e473197407da4d37abcdd0`. Proposed categories only: **no files were deleted, changed, or moved** in this documentation commit. “Superseded as the preferred whole-game architecture” is not the same as “unused”.

## Classification and public code

| Repository paths | Now | Action / preconditions |
|---|---|---|
| `caserecomp/{inspector,director,executables,score,relationships,bitmap,audio,pipeline,fidelity,verification}.py` | **KEEP primary** | Parsers, media and fidelity checks are still needed by the new Director runtime. |
| `caserecomp/{lingo_bytecode,lingo_index,lingo_compare,backends,movie_bundle,director_content}.py` | **KEEP primary** | Compiled bytecode, decompiler cross-check and new content packaging. |
| `android/engine/src/main/kotlin/org/rigorcore/caserecomp/{lingo,director,flash}/` | **KEEP primary** | Lingo VM, Director runtime, Xtra adapters, Flash and StageRenderer. |
| `android/engine/.../{Engine,Runtime,VerifiedFlow,VerifiedScene,PrivateContent,PrivateTracePlan}.kt` | **TRANSITIONAL but ACTIVE** | Still referenced by current synthetic shell and proof tests. Cannot delete before new Android Director integration, save migration and regression parity. |
| `android/app/.../{MainActivity,GameShellView,SyntheticContent}.kt` | **TRANSITIONAL but ACTIVE** | Current launcher/UI uses `GameRuntime`; replace or adapt only after VM debug launch works. |
| `android/app/.../{PrivateContentRepository,PrivateVerifiedFlowRepository,PrivateVerifiedSceneRepository}.kt` | **TRANSITIONAL but ACTIVE** | Existing `.crcontent/.crflow/.crscene` secure import. New `director-content` requires an explicit safe equivalent. |
| `android/app/.../{AndroidPorts,PrivateMedia,PrivateRuntimeObserver}.kt` | **KEEP; evaluate reuse** | Lifecycle, audio, bitmap/storage integration can be adapted. Check real callers first. |
| `caserecomp/{content_bundle,scenario}.py` | **TRANSITIONAL/reference** | Original `.crcontent`/scenario contracts remain current test/input dependencies, although no longer chosen for full-game script authoring. |
| `caserecomp/{runtime_capture,vertical_slice,verified_scene,trace}.py` | **KEEP golden reference** | Native projector observations and behavioural regression probes remain essential. Stop manually promoting every game rule as the main implementation method. |
| `fixtures/*`, `tests/*`, Kotlin unit and instrumented tests | **KEEP QA** | Synthetic and binding regressions; no proprietary artifacts. |
| `tools/{android_external_qa,android_physical_qa,phase8_emulator_qa}.sh`, `tools/phase8_evidence.py` | **KEEP QA** | Test infrastructure is reusable. Repeating unchanged synthetic device runs is low-value work, but removing checks is wrong. |
| Historical Phase 4–9 docs | **KEEP historical record** | Explain legacy limitation, don't erase failed trials/security lessons. |

**Implementation workflow no longer preferred:** manually transcribe a growing number of Huntsville scenes/puzzles into bespoke `ScenarioV1`/`.crscene` definitions as the long-term mechanism. The **new selected mechanism** is executing private original compiled Lingo with a compatible runtime and using these proofs as reference. This is a **policy for future implementation**, not an instruction to delete old files.

## Private Drive data / exact folder cleanup candidates

| Private area | Classification | Reason and safe future action |
|---|---|---|
| Original `MysteryCaseFiles.exe`, `data/*.cct` | **NEVER AUTO-DELETE** | Master owned source. |
| `huntsville-decompiled/main/`, all casts, 80 `.ls` listings, manifest | **PRESERVE** | Recoverable but valuable independent source/reference; don't upload. |
| `huntsville-decompiled/lingo-bundle.private.json` | **PRESERVE; possible duplicate** | Compare full SHA and producer against `huntsville-m3/lingo-bundle.private.json` before even considering deduplication. Name/size equality is not proof. |
| `huntsville-m3/movie-bundle.private.json`, `lingo-bundle.private.json`, `huntsville.director.zip` | **PRESERVE ACTIVE** | Current private VM source data; format differs from previous `.crcontent`. |
| `huntsville-m3/shots/`, `boot-smoke.txt`, `session.txt`, SWF samples, local probes | **PRESERVE REFERENCE** | Potential golden frame/event evidence and Flash tests. |
| `huntsville-m3/old.pkl`, `new.pkl` | **CANDIDATE GENERATED** | Possibly intermediate serialized objects; determine producers/consumers and reproduce before approved deletion. Never deserialize untrusted pickle. |
| `huntsville-m3/__pycache__`, `huntsville-first-playable/__pycache__` | **CANDIDATE CACHE** | Typically regenerable Python bytecode; inspect and request approval before cleanup. |
| `case-recomp-phase6/final/` `.crcontent` + `.crflow` v2 | **PRESERVE REFERENCE** | Authoritative historical source-bound navigation reference until parity verified. |
| `case-recomp-phase6/sessions/session-3,4/`, observation/comparison | **PRESERVE GOLDEN** | Accepted native projector evidence: irreplaceable without controlled recapture. |
| `case-recomp-phase6/sessions/session-1,2/`, `regen/`, `live/` | **ARCHIVE IN PLACE** | Early experiments and regeneration trail; don't silently erase. |
| `case-recomp-phase6/tamper/`, legacy v1 proof | **PRESERVE NEGATIVE QA** | Known-invalid artifacts document false evidence/weakening risk. Never promote. |
| `case-recomp-phase6/casts/` | **PRESERVE until dedup test** | 38 converted directories may be recomputable, but hash/provenance/consumers must be compared. |
| `huntsville-first-playable/scene-observation.private.json`, private `.crscene`, `trials/trial-A,B/` | **PRESERVE GOLDEN** | Projector click/find/miss/completion/persistence reference. |
| `huntsville-first-playable/obs/`, click probes, PNG/JSON captures, scripts, masks | **PRESERVE REGRESSION** | Necessary to diagnose pixel/occlusion/behaviour differences. |
| `huntsville-first-playable/cast-convert/` and exploratory contact sheets | **POSSIBLE GENERATED DUPLICATES** | Only candidate after private source hashes, reproduction and use audit. |
| `tools/projectorrays`, `tools/projectorrays-src` | **PRESERVE TOOL** | Reference decompiler; assess license/security before changes, never bundle blindly into APK. |

### Conditions before ANY future removal

1. User explicitly authorizes **named paths**, separately from this audit.
2. Verify each candidate's SHA-256, whether original/recoverable, unique evidence and producer/version in a private manifest.
3. Search all repository references **and local Windows scripts**; a GitHub-only grep is insufficient.
4. Reproduce alleged caches/duplicates from immutable original inputs, compare output and identify dependent workflows.
5. Archive unique independent native-projector trials and Phase 9 golden evidence privately; ensure rollback.
6. For code, replace it with validated Android Director integration and run Python, Kotlin/JVM, emulator API 26/33/36 and golden original-game tests.
7. Review individual deletions as a separate task/commit; document reason and recovery. No automated directory-wide cleanup.

**This audit removed nothing.** The only concrete “stop using” recommendation is the **manual per-rule approach as the primary route to the full game**, not deletion of existing functional code or evidence.
