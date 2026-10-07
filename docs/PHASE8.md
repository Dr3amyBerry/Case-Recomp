# Phase 8 — controlled synthetic debug packaging

Phase 8 produces only a synthetic debug APK. The release variant is disabled in Gradle, the debug application ID is suffixed with `.debug`, OS backup remains disabled, and no production signing configuration exists.

## CI evidence bundle

The artifact bundle contains two **synthetic** APKs used only for QA:

- `case-recomp-synthetic-baseline.apk` — versionCode 7, used to test Android's upgrade path.
- `case-recomp-synthetic-debug.apk` — versionCode 8, the controlled Phase 8 candidate.

The candidate is built twice from the same revision and must be byte-identical. The bundle also contains SHA-256 sums, build provenance, a CycloneDX 1.6 SBOM and a reproducible build report. Provenance records the Git material, builder, toolchain, variant, release-disabled state and both APK hashes.

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

## Remaining production work

Physical-device profiling, production rights review, production signing policy and any distribution decision remain outside Phase 8.
