# AUTOPILOT_V1 — Case-Recomp roadmap

Work is committed directly to `main`; no branches created unless explicitly requested. Never commit copyrighted game media/executables. All existing repository source is retained.

## Roadmap

- [x] **Phase 1:** identify edition, installed file layout, PE projector, and Director container signatures; implement read-only hashing inventory and tests.
- [x] **Phase 2A:** map the main XFIR movie and all 38 compressed Director cast files, parse their Afterburner indices, and validate 7,018 resources.
- [x] **Phase 2B:** read local zlib and uncompressed resources, reconstruct ILS entries containing 80 compiled Lingo resources, implement selective extraction, tests and docs; determine unsupported SWA set.
- [ ] **Phase 2C:** run and benchmark ProjectorRays and LibreShockwave locally; prove Lingo bytecode -> handler disassembly and media conversion on this edition. Comparison from upstream READMEs exists, but executable integration has not been verified.
- [ ] **Phase 3:** semantic asset extraction and validation (bitmaps, alpha, scripts, fonts, text, sounds), verifiable provenance, asset import format.
- [ ] **Phase 4:** deterministic gameplay state machine and actual level/puzzle recreation from recovered scripts and data.
- [ ] **Phase 5:** Android frontend and runtime (touch, aspect ratio, sound, renderer, lifecycle, persistence and accessibility).
- [ ] **Phase 6:** install-time licensed-asset importer and media compatibility, without repackaged commercial data.
- [ ] **Phase 7:** integration, device and regression QA, performance, safety; target >90% coverage for maintainable new code.
- [ ] **Phase 8:** debug APK via controlled CI; review signing, licensing, production distribution and any store requirements separately.

## Verified baseline (2026-10-06)

Source game: Mystery Case Files: Huntsville, Spanish, RealNetworks version 1.0. Director runtime version `8.5.1#104` from XFIR headers.

- PE32 `MysteryCaseFiles.exe`, 11,094,294 bytes; embedded `XFIR/FGDM` movie offset **2,435,648**, length **8,658,642**.
- 38 `XFIR/FGDC` CCT libraries (21 numbered, 16 `dat`, 1 `empty`), all structurally decoded.
- **39 containers, 7,018 indexed resources, 6,989 decoded or copied**, 29 known unsupported `snd ` SWA-codec chunks.
- **80 `Lscr`** compiled scripts in the main movie's initial load segment (not decompiled into Lingo source).
- Detailed counts, risk assessment and commands: [REVERSE_ENGINEERING.md](REVERSE_ENGINEERING.md).

## Parallel nonblocking tracks

- A: Director / Lingo semantics and local tool comparison.
- B: portable game-state logic and cross-platform rendering research.
- C: testing/CI/security/docs. No external tool copies or proprietary media required for these tasks.

## Deferred decisions

- [PENDIENTE_APROBACION_HUMANA] Rights review before any redistribution of third-party commercial art, audio or embedded programs.
- [PENDIENTE_APROBACION_HUMANA] Production Android signing, store publishing or permissions for externally visible releases.

No production credentials are requested or stored. No APK is claimed until device or emulator tests demonstrate actual gameplay.
