# AUTOPILOT_V1 — Case-Recomp roadmap

Work is committed directly to `main`; no branches created unless explicitly requested. Never commit copyrighted game media/executables. All existing repository source is retained.

## Roadmap

- [x] **Phase 1:** identify edition, installed file layout, PE projector, and Director container signatures; implement read-only hashing inventory and tests.
- [x] **Phase 2A:** map the main XFIR movie and all 38 compressed Director cast files, parse their Afterburner indices, and validate 7,018 resources.
- [x] **Phase 2B:** read local zlib and uncompressed resources, reconstruct ILS entries containing 80 compiled Lingo resources, implement selective extraction, tests and docs; determine unsupported SWA set.
- [ ] **Phase 2C:** Real-game comparison of ProjectorRays and LibreShockwave remains pending. Phase 3 adds reproducible pinned build probes and simulated adapter tests, which are not substitutes for full-game decompilation.
- [x] **Phase 3A:** validated `ediM` JPEG/PNG, ID3/MP3, known PCM/WAV, bytecode extraction, private manifest, SHA-256 verification, and synthetic external-tool adapters.
- [x] **Phase 3B.1:** read KEY* allocated/used table records and associate CASt/ediM/ALFA without guessing by adjacent resource IDs; publish synthetic tests and reproducible proof.
- [x] **Phase 3B.2 (partial):** 1,968 verified JPEG+ALFA pixel compositions and 29 SWA MPEG→WAV local decodes; 21 BITD stream probes. Full BITD color fidelity and recovered Lingo semantics remain open.
- [x] **Phase 3B.3 (partial):** verified 80 compiled Lscr script directories, 1,608 Lnam names, 527 handler spans (287 distinct); optional source-recovery name cross-check against original index. No readable original Lingo recovered yet.
- [ ] **Phase 3B.3 (remaining):** resolve 20 unusual ALFA masks, BITD native geometry/palette, original-runtime fidelity and genuine game-source Lingo decompiler comparison.
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

## Phase 3 reproducibility

See [PHASE3.md](PHASE3.md). Real main-movie conversion: 165 PNG, 6 MP3, 80 raw compiled Lingo chunks, all 251 outputs verified; 29 SWA and one unknown `ediM` remain unconverted. CI requires >=90% source coverage; pinned public source build probes for upstream tools never receive game content.

Phase 3B.1 validated all 39 `KEY*` maps and 1,988 unambiguous image-to-alpha references; see [PHASE3B_RELATIONSHIPS.md](PHASE3B_RELATIONSHIPS.md). The decoder still does not apply alpha.

## Parallel nonblocking tracks

- A: Director / Lingo semantics and local tool comparison.
- B: portable game-state logic and cross-platform rendering research.
- C: testing/CI/security/docs. No external tool copies or proprietary media required for these tasks.

## Deferred decisions

- [PENDIENTE_APROBACION_HUMANA] Rights review before any redistribution of third-party commercial art, audio or embedded programs.
- [PENDIENTE_APROBACION_HUMANA] Production Android signing, store publishing or permissions for externally visible releases.

No production credentials are requested or stored. No APK is claimed until device or emulator tests demonstrate actual gameplay.

## Phase 3B.2 results

See [PHASE3B_MEDIA.md](PHASE3B_MEDIA.md). Real-game private output (39 containers) produced 2,223 hash-verified files and was discarded after QA. Kotlin prototype: [../android/README.md](../android/README.md). No APK is claimed.

## Phase 3B.3 handler index (additional verified result)

Read-only `lingo-index` analyses the original embedded Director movie without executing it. Real-file validation: 1,608 Lnam symbols, 92 LctX entries, 80 Lscr scripts, 527 compiled handler spans, and 287 unique casefolded names (27 repeated names). Bytecode not decompiled into Lingo; see [PHASE3B_LINGO_INDEX.md](PHASE3B_LINGO_INDEX.md). `compare-lingo --reference-movie` provides structural name recall checks for future *local* decompiler outputs only.
