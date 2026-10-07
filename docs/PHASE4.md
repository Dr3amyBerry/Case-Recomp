# Phase 4 — palette closure, Score model and scenario-v1

Phase 4 moves Case-Recomp from media recovery toward a deterministic, content-neutral game engine. The public repository still contains no original art, audio, recovered Lingo source, cast names, marker text, or original scenario coordinates. Private verification uses the supplied installation and discards generated outputs.

## System Windows palette -102

Five indexed BITD members in the main movie select Director palette -102. Four are 4-bit and one is 8-bit. Director expands a 4-bit sample across the full byte range (0, 17, …, 255); it is not interpreted as a direct 0–15 palette index.

For this title only five expanded indices are exercised: 0, 19, 119, 136 and 255. Their RGB values are independently corroborated against LibreShockwave revision fca530f9ef388d7ff38fa6c7117feae5bb5411c6 and ScummVM revision 13d61c20ed494d03303674531a6a933effa4374d. Case-Recomp deliberately fails closed for unused -102 indices that were not needed for this game.

Private fidelity result: all 5/5 indexed BITD images are pixel-identical to LibreShockwave output. Combined with prior true-colour work, all 21 BITD resources across the private installation now have verified decode paths for the formats exercised by this title.

## Score, cast and Lingo structure

The bounded parser in caserecomp.score handles DRCF, CAS*, CASt, VWSC, VWLB and the Lscr script-number field. Marker labels are hashed rather than emitted, and no recovered Lingo source is stored.

Private aggregate findings for the main movie:
- stage 800×600, tempo 30, Windows platform;
- 203 Score frames, Director-6-era score layout, 120 effective displayed channels;
- 387 CASt resources and 643 logical CAS* slots;
- 28 markers, forming 29 anonymous marker-delimited timeline segments including the pre-marker segment;
- 2,377 structural behavior references;
- CAS* + CASt script metadata resolves 2,086 behavior references to 38 compiled script resources;
- the existing compiled index remains 80 Lscr scripts / 527 handlers / 287 unique handler names.

These are structural references, not proof that every behavior executes or that decompiled Lingo is semantically equivalent to Director runtime behavior.

## scenario-v1

The public interchange contract is case-recomp-scenario version 1. It contains only engine-neutral dimensions, anonymous scene IDs, frame ranges, hit rectangles, z-order and generic events. schemas/scenario-v1.schema.json documents the JSON representation, while caserecomp.scenario performs strict bounded validation and canonical SHA-256 fingerprinting.

The committed fixture fixtures/synthetic-scenario-v1.json is entirely synthetic and exists only for regression tests.

Useful commands:

    python -m caserecomp scenario-check fixtures/synthetic-scenario-v1.json
    python -m caserecomp score-structure /private/MysteryCaseFiles.exe --output /private/score-structure.json

score-structure output is private because it may contain structural timing/identifier metadata derived from the owned game. It must not be committed.

## Kotlin engine

The pure Kotlin prototype is still Android-independent. It now supports scenario-v1 data classes, scene frame ranges, deterministic AdvanceFrame input, content-neutral engine events, deterministic event/state replay, z-order hit-testing and letterboxed touch transforms. Synthetic smoke tests exercise the contract without any game asset or original rule.

This is not an APK and is not yet a port of Huntsville gameplay.

## Phase 4 fidelity gates

A Director-derived behavior may enter the portable engine only after:
1. media fidelity is checked from decoded pixels/PCM rather than compressed file bytes;
2. timeline state and sprite geometry can be reproduced from a private reference trace;
3. hit targets survive viewport coordinate transformation;
4. observable state/event behavior is reproduced by a synthetic regression test;
5. replay gives the same terminal state and event sequence for the same input trace.

Production signing, store deployment and redistribution of commercial assets remain outside this phase.
