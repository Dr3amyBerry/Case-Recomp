# Phase 8 — controlled synthetic debug packaging

Phase 8 produces only a synthetic debug APK. The release variant is disabled in Gradle, the debug application ID is suffixed with `.debug`, OS backup remains disabled, and no production signing configuration exists.

## CI evidence bundle

The artifact bundle contains two **synthetic** APKs used only for QA:

- `case-recomp-synthetic-baseline.apk` — versionCode 7, used to test Android's upgrade path.
- `case-recomp-synthetic-debug.apk` — versionCode 8, the controlled Phase 8 candidate.

The candidate is built twice from the same revision in one workflow signing context and must be byte-identical. The bundle also contains SHA-256 sums, build provenance, a CycloneDX 1.6 SBOM and a reproducible build report. Provenance records the Git material, builder, toolchain, variant, release-disabled state and both APK hashes. The runner-local debug signing key can change the outer APK signature between clean runners, so Phase 8 does **not** claim cross-run identity of the signed debug APK. The ZIP-entry manifest digest is provenance-tracked separately; it remained identical across the verified `f79ab8bb` and `ceaa2a50` runs.

## External emulator QA

API 26 runs an ADB harness **outside** the instrumentation process. It:

1. installs versionCode 7,
2. measures cold startup,
3. enters synthetic MAP and immediately force-stops the process,
4. verifies the autosaved state after relaunch,
5. upgrades in-place to versionCode 8 and verifies the save survives,
6. performs an app-level backup/restore of the synthetic save slot using `run-as`,
7. measures total PSS and gfxinfo,
8. verifies OS backup remains disabled.

The runtime now persists each accepted state transition before audio/observer side effects, so abrupt process termination does not rely on lifecycle callbacks.

API 26/33/36 all run the complete Android instrumentation suite. Runtime metrics are uploaded only from API 26 to keep measurements comparable.

## Preliminary emulator budgets

- debug APK <= 15 MiB
- cold startup <= 5000 ms
- total PSS <= 256 MiB
- render jank <= 25% when the platform exposes a parseable `gfxinfo` jank percentage

These are broad regression guardrails, not production performance promises. A missing jank percentage is reported as `null`, never fabricated.

## Backup boundary

Android OS/cloud backup is intentionally disabled because app-private imports can contain locally owned commercial media. QA therefore backs up and restores **only the synthetic save-slot XML**. No `.crcontent`, `.crflow` or imported media is backed up by the Phase 8 harness.

## Release/debug separation

- release variant disabled by `androidComponents`
- no `assembleRelease` task accepted by CI
- no production signing config
- debug package is `org.rigorcore.caserecomp.synthetic.debug`
- debug manifest keeps `allowBackup=false`, cleartext disabled and an explicit synthetic label
- no store upload or production artifact step exists

## Huntsville boundary

Huntsville remains fail-closed. No native-projector capture consensus is present in CI, so no original-game `.crflow` is generated or bundled.

## Verified closure — 2026-10-07

Phase 8 is closed for the synthetic/emulator scope at implementation commit `ceaa2a50e1c9a54bb96b7abab3928814233ac523`.

- Android workflow run `37662244224`: **success**.
- Python workflow run `37662244071`: **215/215 tests**, one intentional skip, **90% coverage**, Python 3.11/3.12/3.13 green.
- Android JVM/build guardrails: **success**.
- Instrumented API 26: **success**.
- Instrumented API 33: **success**.
- Instrumented API 36: **success**.
- Candidate versionCode 8 SHA-256: `790ac9989002e7aa7b8c9e1ea10445d00fcd0a3456fcebcb9d266478079e91be`.
- Baseline versionCode 7 SHA-256: `a9450b1a91fdd01acd7116ea5f6dbe0d11e141a7f06c0069b4b8b80125294b68`.
- Stable APK ZIP-entry manifest SHA-256: `42041dbfee5d42a3db0dab47a309c1d91053ff8d1c648c965a52ab6812751007`.
- API 26 cold startup: **362 ms** (budget <= 5000 ms).
- API 26 total PSS: **16,677 KiB** (budget <= 262,144 KiB).
- API 26 gfxinfo jank: **0.00%** (budget <= 25%).
- Save state remained `screen=MAP` before force-stop, after relaunch, after the 7→8 in-place upgrade and after synthetic app-level restore.
- `process_kill_restore`, `upgrade_install_preserves_save` and `synthetic_save_backup_restore`: **pass**.
- OS/cloud backup: **disabled**; `ALLOW_BACKUP` absent from package flags.
- Release variant: **disabled**; production signing: **not configured**.
- Original commercial game assets in the public artifact: **none**.

The evidence generator also fails closed if the first-build SHA is omitted or differs, labels the Git material with its actual SHA-1 object identifier rather than misrepresenting it as SHA-256, and keeps the CycloneDX component version aligned with its bom-ref.

Huntsville remains outside this closure: no original-game rule is promoted and no original-game `.crflow` is bundled without independent native-projector capture consensus.

## Remaining production work

Physical-device profiling, production rights review, production signing policy and any distribution decision remain outside Phase 8.
