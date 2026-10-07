# Post-Phase-8 — physical-device synthetic QA

Phase 8 is closed on emulators. This document prepares the next non-blocking validation step on a **real Android device** without enabling release builds, production signing, Huntsville behavior or proprietary content.

## Safety boundary

The physical-device harness is manual-only and is not called by GitHub Actions.

It:

- requires an explicit `ANDROID_SERIAL`; it never relies on ADB's implicit default device,
- rejects `emulator-*` serials and devices reporting `ro.kernel.qemu=1`,
- operates only on `org.rigorcore.caserecomp.synthetic.debug`,
- aborts if that synthetic package already exists, so it cannot overwrite pre-existing app data,
- installs only the synthetic versionCode 7 and 8 QA APKs,
- backs up/restores only the synthetic save-slot XML,
- imports no `.crcontent`, `.crflow` or commercial media,
- verifies OS backup remains disabled,
- uninstalls the synthetic package and removes its temporary device-side save on exit, including failure paths.

Release remains disabled and no production signing material is required.

## Manual command

Connect exactly one intended physical device, enable USB debugging, identify it with `adb devices`, then explicitly select it:

```bash
export ANDROID_SERIAL='<physical-device-serial>'
tools/android_physical_qa.sh \
  /private/case-recomp-synthetic-baseline.apk \
  /private/case-recomp-synthetic-debug.apk \
  /private/physical-qa-run-001
```

The output directory must be new.

## Functional checks

The harness validates the same synthetic persistence boundary proven on API 26 in Phase 8:

1. install versionCode 7,
2. cold-start the debug shell,
3. enter synthetic MAP and verify immediate autosave,
4. perform a literal `am force-stop` and verify MAP survives relaunch,
5. perform an in-place versionCode 7 → 8 upgrade and verify the save survives,
6. clear application data and restore only the synthetic save XML,
7. verify MAP again,
8. collect PSS and `gfxinfo`,
9. verify Android OS/cloud backup remains disabled,
10. remove the synthetic package.

Physical-device performance is **informational** in this stage. Phase 8 emulator budgets are not silently promoted to production-device requirements.

## Evidence retained

The harness writes:

- `device-properties.txt` — API, ABI, screen size and density; no device serial is persisted,
- `startup.txt`,
- save snapshots before/after force-stop, upgrade and restore,
- `meminfo.txt`,
- `gfxinfo.txt`,
- `package.txt`,
- `runtime-metrics.json`.

No proprietary content is included in these outputs.

## Pending external action

- [PENDIENTE_DISPOSITIVO_FISICO] Run this harness on representative physical Android devices.
- [PENDIENTE_DISPOSITIVO_FISICO] Establish production performance budgets only after multiple real-device samples exist.

A physical-device run does not change the Phase 6B evidence state and cannot promote Huntsville rules.
