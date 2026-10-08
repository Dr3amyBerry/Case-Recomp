# Phase 9 — first playable hidden-object scene

Phase 6 verified navigation into the first scene (Score frames 128–132). Phase 9 makes that scene playable: tapping verified hidden objects, registering finds, persisting them, closing the scene and returning to the map, all backed by observation of the owned native Windows projector. Nothing original is committed: screenshots, save data, masks, the observation and the proof stay private.

## Original behaviour observed

Observed on the native projector with controlled trials (scripted input, 30 ms screen bursts):

- **Target list.** Each profile draws its own list of 8 entries (one may count twice) from the objects placed in the scene. Two profiles had different lists that shared one entry. The scene artwork is identical across profiles; only the list differs. The selection is saved per profile.
- **Find.** Clicking a listed object hides it, removes its list line and lowers the case-wide "items needed" counter by exactly 1. The first visible response arrives 94–187 ms after the click (30 ms sampling).
- **Miss.** Clicking the background, or an object that is not on the current list, changes nothing: no penalty, no timer change (6.36 s of real time = 6 s on the clock across five rapid misses), no cooldown and no visual feedback.
- **Hit test.** The click goes to the topmost visible object sprite under the pointer, using its alpha matte. A click inside a sprite's rectangle but on a transparent pixel, or on a pixel covered by a higher sprite, misses. Objects that have been found are hidden, so clicks pass through them.
- **Completion.** The last find opens a modal "well done" dialog and pauses the timer. Only its accept button returns to the map. The completed location is no longer highlighted on the map, cannot be entered again and stays that way after the projector is restarted.
- **Tutorial hints.** One-time dialogs, shown once per profile on timers rather than in response to clicks, pause play. They are recorded but not modelled.

## Evidence

Two controlled trials started from the same saved-profile snapshot, so their initial state was identical. Each trial made 7 misses, 8 finds and the closing acknowledge (16 steps). After every step, the play area of the two trials is byte-identical. The list text matches apart from an animated background texture, and the counter drops by exactly 1 per find. One trial also restarted the projector and confirmed the persisted lock.

| Artifact | SHA-256 |
|---|---|
| source (projector) | `0ef4a73bceae060970d2513f5f6132663843064d7106b724db46cac810a4b28d` |
| private scene observation | `489b7956e7503bbb5d52fe614aac7f5730b4686932f064f01fd5ebeb723a009f` |
| trial set | `d193904961b6a1530133c272b52dc66e47c16b3ac9267e8178384f07d9d7931c` |
| private `.crscene` binding | `ecd8119bd34287489e3927e73b343b7c9abf6a530b40bb2264ba0b43d1c748db` |
| bound private `.crcontent` package | `769848b090498b446e5bf9ed5ab298c4775efda67f6499409f8388ec806884b8` |

## Score / cast / Lingo relationships

- Every verified target corresponds to exactly one Score sprite at the scene entry frame, drawn from the location's external cast. The Score position is the sprite's registration point at its centre. The region that changes on screen after a find lies inside that sprite.
- Hit masks are the sprite's alpha minus every higher-channel sprite. This model reproduces all 16 observed trial clicks. It also reproduces 13 of 15 earlier exploratory clicks; the 2 mismatches sit on occlusion edges where the converted alpha of a covering sprite is imprecise.
- Every object sprite carries the same item behaviour, whose compiled handlers include `mouseDown` and a hide handler, which is consistent with the observed click-to-hide response. (An earlier draft of this document named a different script and an overlay dispatcher; that conclusion came from an off-by-one script link, fixed in `score.member_script_number`, and is withdrawn.) Which handler runs on a click is now read from the privately decompiled Lingo (Phase 10) rather than inferred.

## Implementation

- `case-recomp-scene-observation` v1 (Python, private input). Trials must reach exact observable consensus. Every find must lie inside exactly its own unfound target mask and change the scene and counter. Every miss must leave both unchanged. The closing acknowledge must land in the dialog region and return to MAP. The lock and a restart persistence check are required.
- `case-recomp-verified-scene` v1, `.crscene` (`scene-proof` CLI). It contains only bit-packed hit masks and generic rules (miss: none; find: hide and counter −1; completion: acknowledge then MAP, scene locked; counter start). It is bound to package, scenario hash, scene and source like `.crflow` v2.
- Kotlin engine:
  - `HitMask` on targets; found targets no longer intercept taps.
  - `SceneCompletion.ACKNOWLEDGE_THEN_MAP` with `Input.Acknowledge` and locking.
  - `VerifiedSceneProofParser` and `VerifiedSceneGate`, which allows finds only for proven targets.
- Android shell: `PrivateVerifiedSceneRepository` binds `.crscene` to the active `.crcontent` (package, scenario hash, trace-plan source, scene, design) and re-validates it on load. The long press imports content, then flow, then scene. A completion dialog button is drawn.

Public tests use synthetic data only. A Python-generated synthetic `.crscene` fixture is parsed by the Kotlin unit tests (cross-language binding parity) and by the instrumentation tests.

Private validation (local, not in CI): the real observation passes the public validator, and the real `.crscene` is generated from it. The Kotlin engine, on the JVM with the real `.crcontent`, `.crflow` v2 and `.crscene`, replays all 16 original trial clicks with identical outcomes: misses, target ids, counter values, completion dialog, return to MAP, persistence across a mid-scene restart and the lock after completion.

## Reconstructed share of the first scene loop

Functional loop (enter from map → verified finds → register → persist → complete → verified return and lock): **6 of 6 steps** for the observed target configuration.

Not yet reconstructed or verified:

- the rule that draws a profile's target list: only 2 profiles (16 distinct list entries) were observed, and only the 8 entries of one profile are verified, playable targets;
- list text, counter display and HUD rendering on Android (the counter is computed but not drawn);
- timer, hint button, tutorial dialogs, audio cues, find animations and other cosmetic fidelity;
- one Score sprite at the scene entry frame has no converted bitmap and is excluded from occlusion;
- attribution of the click to a specific compiled handler.

Rough estimate: about **55 %** of the full first-scene experience (all of the gameplay logic for one configuration, none of the list randomisation or presentation fidelity).
