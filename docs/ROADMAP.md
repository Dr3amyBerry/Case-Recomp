# AUTOPILOT_V1 roadmap

- [x] Phase 1A: identify the game archive contents and original binary signatures without executing them.
- [x] Phase 1B: add a safe read-only inspector, synthetic unit tests and public CI scaffold.
- [ ] Phase 2: map the embedded Director movie and external casts; benchmark ProjectorRays and LibreShockwave; document Lingo handlers and asset formats.
- [ ] Phase 3: reproducible local extraction pipeline, provenance, validation and quality gates (no proprietary output committed).
- [ ] Phase 4: deterministic game-state reimplementation: menu, map, scene navigation, hidden-item search and minigames.
- [ ] Phase 5: Android runner with touch input, letterboxing, audio, lifecycle, persistence, and accessibility.
- [ ] Phase 6: local licensed-asset importer and level compatibility verification.
- [ ] Phase 7: unit/integration/UI tests, performance and security; target >90% coverage of newly written code.
- [ ] Phase 8: reproducible APK via opt-in GitHub Actions, documentation and release checklist.

## Verified baseline (2026-10-06)

Game: Mystery Case Files: Huntsville — Spanish edition, version 1.0, 2006.

- MysteryCaseFiles.exe: PE32/i386, 11,094,294 bytes; contains an embedded Director XFIR movie at offset 2,435,648.
- data/gallinita.exe: PE32/i386 and SWF data (FWS) in trailing bytes.
- data/01.cct: Director XFIR/CDGF, version string 8.5.1#104.
- data/empty.cct: same container, 485 bytes.
- data/: 21 numbered casts, 16 dat casts, one empty cast, plus gallinita.exe and Thumbs.db.

## Parallel tracks

A. Binary research and test fixtures; B. Android architecture and shell; C. CI/security/documentation. These can advance independently, but gameplay correctness requires analyzing locally licensed scripts and media.

## Deferred external decisions

- [PENDIENTE_APROBACION_HUMANA] Distribution of any package that would include third-party copyrighted media (not needed for the scanner).
- [PENDIENTE_APROBACION_HUMANA] Android production signing and store submission (not needed for local debug builds).

## Risks

- Technical: Director/XFIR is not a native Android format; the Android runner must reproduce or implement the relevant Lingo/Director behavior.
- Legal: never distribute a bundled proprietary executable or cast/media contents in the public repo.
- QA: current synthetic tests validate the inspector, not game behavior or successful Android execution.
