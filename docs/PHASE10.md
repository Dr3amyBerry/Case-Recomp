# Phase 10 — private decompilation and a Kotlin Lingo VM

Phases 6 and 9 reconstructed behaviour by observing the original projector one rule at a time. That is reliable but does not scale to a whole game. Phase 10 changes the source of truth:

1. **Private decompilation.** The user's own movie is decompiled locally (ProjectorRays through the `external-export` adapter) to read the real game logic. The decompiled Lingo never enters the repository.
2. **A generic Lingo VM.** Instead of hand-translating game code into Kotlin, the repository gets a clean-room Kotlin virtual machine for Director 8.5 Lingo bytecode and the subset of the Director runtime the game uses. At runtime it executes the compiled scripts from the user's own files. The repository contains no game code, original or translated.
3. **Spot verification.** Projector observation (Phase 6/9 tooling) becomes a regression check at selected checkpoints. The Phase 9 scene, with 16 verified steps, is the first reference test.

## Measured scope (owned Huntsville movie, private inventory)

| Area | Size |
|---|---|
| Compiled scripts | 80 (43 behaviours, 36 parent scripts, 1 movie script), all in the main movie; external casts hold media only |
| Handlers / bytecode instructions | 527 / 27,696 |
| Distinct opcodes | 56, all from the documented Director 5+ set |
| Director global functions | about 40 (sprite, member, point, rect, random, string, sound, go, updateStage, castLib, math) |
| Object / movie properties | about 50 sprite/member properties, 12 movie properties, 12 `the` builtins |
| Native object methods | list methods (getAt, setAt, add, deleteAt, findPos, duplicate, property lists), image (copyPixels, fill, duplicate) |
| Xtras | Buddy API (registry save data, window info) → app storage; FileIO (logs) → app files; display enhancer → no-op; four network calls → stubs |

The remaining ~250 called methods are defined by the game's own parent scripts and run inside the VM.

## Milestones

- **M1 — compiled bytecode bundle (done).** `lingo-bundle` reads every Lscr and script cast member into a `case-recomp-lingo-bundle` JSON: names, properties, globals, literals, and each handler's arguments, locals and bytecode, with no source text. On the owned movie it matches the independent decompiler for 80/80 scripts (type, member name, handler names and arguments), and 1,577 of 1,581 string literals appear verbatim in the decompiled text; the other four are the RETURN character, which the decompiler prints as a constant. This work exposed and fixed an off-by-one link between script cast members and Lscr chunks (`score.member_script_number`).
- **M2 — Kotlin Lingo VM (done).** `org.rigorcore.caserecomp.lingo` implements the value model, the 56 opcodes, Director message dispatch, `new`, chunk expressions, `value()` and the list/point/rect natives, with Director services behind a `LingoHost` interface. Synthetic bytecode tests cover each feature. Private checks on the owned movie: 27 of 36 parent-script constructors run unmodified with a neutral host; the other 9 fail only on host values or globals that the real startup sequence provides, with no VM opcode or stack error. The game's own inventory-draw handler produces exactly the structure the game saves for a profile: two crime locations with 8 items each, multi-count items, case number and time limit.
- **M3 — Director runtime subset:** Score playback (frames, labels, `go`, frame scripts), behaviours and event dispatch (`beginSprite`, `exitFrame`, mouse events), sprite and member properties, bitmap/text rendering with inks and blend, and sound.
- **M4 — Xtra substitutes:** registry-backed saves through app storage, file logging, window/display no-ops and network stubs.
- **M5 — run Huntsville:** boot → menu → map → first scene inside the VM, checked against the Phase 9 reference trial, then the remaining locations, puzzles and cases.

## Private artefacts (never committed)

Decompiled Lingo, the bytecode bundle, inventories and every game-derived file live under the git-ignored `private/` folder of the checkout (e.g. `private/huntsville/huntsville-m3`, `private/huntsville/huntsville-decompiled`). The repository holds only the generic reader, VM and runtime code, plus synthetic tests.

## Private-source audit and transition ownership

See [PROJECT_AUDIT_2026-10-07.md](PROJECT_AUDIT_2026-10-07.md) for the original game Drive inventory, private Phase 6/9 and M3 folders, manifest cross-checks and honest Android playability gates. A local M3 log records a splash/menu/map/scene sequence plus three object finds but **no original-game Android success is established**. See [RETIREMENT_CANDIDATES.md](RETIREMENT_CANDIDATES.md): the older `GameRuntime` and proof import paths remain active in `MainActivity` and must not be removed without a working migration.

## Debug APK Director integration

An isolated `DirectorLauncherActivity` and `DirectorStageView` now supply the Android image/text/audio/storage/input adapters and a private, source-bound `director-content` ZIP import path. A debug long-press chooser from the existing shell exposes this without removing the legacy app or shipping original game content. Consult [android/README.md](../android/README.md) for use and limitations. This is an **integration candidate** awaiting the Windows/TECNO original-game smoke comparison, not an assertion that M5 or the first Android scene is playable.
