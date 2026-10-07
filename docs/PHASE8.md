# Phase 8 — controlled synthetic debug packaging

Phase 8 produces only a synthetic debug APK. The release variant is disabled in Gradle, the debug application ID is suffixed with `.debug`, OS backup remains disabled, and no production signing configuration exists.

## CI evidence bundle

The artifact bundle contains the debug APK, SHA-256 checksum, build provenance, CycloneDX 1.6 SBOM and build/runtime reports. The build job performs two clean debug builds from the same revision and requires byte-identical APK SHA-256 values.

## External emulator QA

An API 36 external ADB harness runs outside the instrumentation process. It measures cold start, captures meminfo/gfxinfo, force-stops and relaunches the app to verify process-kill persistence, performs an in-place `adb install -r` upgrade and verifies the synthetic save remains, and performs a deliberate save-slot-only backup/restore using `run-as`.

OS/cloud backup intentionally remains disabled because app-private imports can contain locally owned commercial game media. The backup/restore test therefore covers only the synthetic save-slot file.

## Preliminary budgets

- debug APK <= 15 MiB
- emulator cold start <= 5000 ms
- total PSS <= 256 MiB
- render jank target <= 25% (reported; promote to hard gate only after stable frame-stat parsing)

These are emulator guardrails, not production promises. Real-device budgets require representative physical hardware.

## Release boundary

No release variant, release signing, store upload or production artifact is generated. Huntsville remains fail-closed without native-projector capture consensus and a matching private `.crflow`.
