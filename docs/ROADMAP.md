# AUTOPILOT_V1 — Case-Recomp roadmap

Work is committed directly to `main`; no branches created unless explicitly requested. Never commit copyrighted game media/executables. All existing repository source is retained.

## Roadmap

- [x] **Phase 1:** identify edition, installed file layout, PE projector, and Director container signatures; implement read-only hashing inventory and tests.
- [x] **Phase 2A:** map the main XFIR movie and all 38 compressed Director cast files, parse their Afterburner indices, and validate 7,018 resources.
- [x] **Phase 2B:** read local zlib and uncompressed resources, reconstruct ILS entries containing 80 compiled Lingo resources, implement selective extraction, tests and docs; determine unsupported SWA set.
- [x] **Phase 2C:** real private ProjectorRays + LibreShockwave run completed; 527 handlers / 287 names agree with the original compiled index and 527/527 assembly handler records match on address+opcode structure. Semantics remain unproven.
- [x] **Phase 3A:** validated `ediM` JPEG/PNG, ID3/MP3, known PCM/WAV, bytecode extraction, private manifest, SHA-256 verification, and synthetic external-tool adapters.
- [x] **Phase 3B.1:** read KEY* allocated/used table records and associate CASt/ediM/ALFA without guessing by adjacent resource IDs; publish synthetic tests and reproducible proof.
- [x] **Phase 3B.2:** 1,988/1,988 verified JPEG+ALFA compositions; 29 SWA payloads cross-checked against LibreShockwave and locally decoded; 16 true-color BITD pixel-identical to LibreShockwave, five indexed BITD index planes verified.
- [x] **Phase 3B.3 (partial):** verified 80 compiled Lscr script directories, 1,608 Lnam names, 527 handler spans (287 distinct); optional source-recovery name cross-check against original index. No readable original Lingo recovered yet.
- [x] **Phase 3B.3 (remaining media):** independently corroborate the System-Windows `-102` entries exercised by the title and verify all five indexed BITD members pixel-for-pixel against LibreShockwave. Runtime behavior semantics remain a Phase 4/5 task.
- [x] **Phase 4:** bounded DRCF/CAS*/CASt/VWSC/VWLB parser, scenario-v1, indexed BITD closure, private trace-plan/compare gate, synthetic trace promotion and deterministic Kotlin replay are implemented. No original rule is promoted without an independent observable trace.
- [x] **Phase 5:** private `.crcontent` import, SHA-256 catalog, app-private storage, v0→v1 manifest migration, private bitmap/audio ports, save slots, runtime-draft observer and emulator instrumentation are implemented.
- [~] **Phase 6:** the first boot/menu/map/scene path is structurally fingerprinted; Phase 6B adds a native-projector screenshot/timing capture consensus and `.crflow` proof gate. Static validation found ranges 1–67, 68–92, 93–97 and 98–102. **0 original rules are promoted in CI** because no native Windows projector run is available there.
- [x] **Phase 7:** API 26/33/36 emulator matrix, lifecycle/runtime recreation, save migration/corruption, private content/proof removal, low-memory bitmap eviction, audio-focus interruption handling, aspect-ratio rendering and deterministic performance guardrails are implemented and green. Literal OS process-kill testing and real-device performance budgets remain for Phase 8.
- [~] **Phase 8:** controlled synthetic debug APK, reproducibility check, SHA-256/provenance/CycloneDX SBOM, external ADB process-kill/upgrade/save-backup QA and emulator startup/memory/render metrics. Release variant/signing/distribution remain disabled.

## Verified baseline (2026-10-06)

Source game: Mystery Case Files: Huntsville, Spanish, RealNetworks version 1.0. Director runtime version `8.5.1#104` from XFIR headers.

- PE32 `MysteryCaseFiles.exe`, 11,094,294 bytes; embedded `XFIR/FGDM` movie offset **2,435,648**, length **8,658,642**.
- 38 `XFIR/FGDC` CCT libraries (21 numbered, 16 `dat`, 1 `empty`), all structurally decoded.
- **39 containers, 7,018 indexed resources, 6,989 decoded or copied**, 29 known unsupported `snd ` SWA-codec chunks.
- **80 `Lscr`** compiled scripts in the main movie's initial load segment (not decompiled into Lingo source).
- Detailed counts, risk assessment and commands: [REVERSE_ENGINEERING.md](REVERSE_ENGINEERING.md).

## Phase 3 reproducibility

See [PHASE3.md](PHASE3.md). Real main-movie conversion: 165 PNG, 6 MP3, 80 raw compiled Lingo chunks, all 251 outputs verified; 29 SWA and one unknown `ediM` remain unconverted. CI requires >=90% source coverage; pinned public source build probes for upstream tools never receive game content.

Phase 3B.1 validated all 39 `KEY*` maps and 1,988 unambiguous image-to-alpha references; see [PHASE3B_RELATIONSHIPS.md](PHASE3B_RELATIONSHIPS.md). All linked ALFA planes now decode, and Phase 4 closes the five indexed BITD members used by the main movie.

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


## Phase 4 structural baseline

See [PHASE4.md](PHASE4.md). Private validation identifies 203 Score frames, 120 displayed channels, 387 CASt resources, 643 logical CAS* slots, 28 markers and 29 anonymous marker-delimited segments. CAS* resolution links 2,086 structural Score behavior references to 38 compiled script resources. These are structural facts only; game-rule semantics still require reference traces.


## Phase 4B trace baseline

The private `trace-plan` command generated 29 hash-only observation checkpoints for the owned main movie. This is a verification plan, **not** proof of gameplay semantics. No Huntsville behavior has been promoted to public fixtures because an independent runtime observation trace has not yet been supplied. Public promotion tests use synthetic data only. See [PHASE4B.md](PHASE4B.md).
