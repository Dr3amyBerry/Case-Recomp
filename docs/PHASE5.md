# Phase 5 — private local content import and Android runtime boundaries

Phase 5 adds a **local-only licensed-content bridge**. The repository and APK remain free of Huntsville media. A user first converts an independently obtained installation on their own machine, then packages that local output into a deterministic `.crcontent` ZIP. Android imports the ZIP through the system document picker, verifies it, and copies it into app-private storage.

## Private content package

The Python packager accepts only assets already present in a successful `convert-local` manifest. It includes supported Android media (`image/png`, PCM WAV and MPEG audio), deduplicates by SHA-256, and excludes raw Director chunks and compiled/decompiled Lingo.

```bash
python -m caserecomp private-content-package \
  /private/converted \
  /private/scenario.json \
  --bindings /private/bindings.json \
  --trace-plan /private/trace-plan.json \
  --output /private/huntsville.crcontent

python -m caserecomp private-content-verify /private/huntsville.crcontent
```

`bindings.json` is optional and private. It can map scenario backgrounds, target images and generic audio cues to files in the conversion manifest. No binding is guessed from filenames or adjacent Director IDs.

The v1 package contains:

- `content-manifest.json` — package/scenario/catalog digests and private bindings;
- `scenario.json` — a validated `case-recomp-scenario` v1 document;
- `assets/<sha256>.<ext>` — deduplicated private media;
- optional `trace-plan.json` — the hash-only Phase 4B plan.

The package ID is a SHA-256 over the canonical manifest body. ZIP paths, entry counts, expanded bytes, media types, per-file byte lengths and SHA-256 digests are bounded and verified. Packaging is deterministic and create-only.

## Android import

`PrivateContentRepository` extracts into `filesDir/private-content/.import-*`, validates the complete manifest/scenario/catalog, then renames the staging directory under `filesDir/private-content/packages/<package-id>`. Only after that succeeds is the active-package pointer committed.

A failed hash, unsafe path, oversized archive, duplicate entry or malformed scenario deletes staging and leaves the previously active package unchanged. No external-storage write permission is requested.

The Android manifest parser accepts current v1 and contains an explicit synthetic v0→v1 migration path. The Python packager emits v1 only.

## Private renderer and audio

The custom View remains rule-free. It consumes the same `RenderFrame` used for input and can optionally resolve private bindings through:

- `AppPrivateBitmapAssetLoader` — PNG-only, SHA-verified, bounded LRU cache;
- `LifecycleMediaAudioPort` — app-private WAV/MP3 files, pause/resume/release tied to runtime lifecycle.

If a binding is absent or invalid, the renderer falls back to synthetic geometry and audio is silent rather than reading arbitrary files.

## Save slots

The engine now exposes `SlotSessionStore`; Android stores independent snapshots in `SharedPreferencesSlotSessionStore`. `SlotSessionAdapter` presents one selected slot to `GameRuntime`. Existing v1 session snapshots retain deterministic encoding and checksum validation.

## Runtime observer

`RuntimeTraceRecorder` records deterministic runtime drafts containing frame/screen/input/event categories plus SHA-256 fingerprints of observable session and render state. `AppPrivateRuntimeObserver` persists them under `filesDir/private-traces`.

These files deliberately contain:

```json
"promotion_allowed": false
```

because the Android runtime still cannot independently reproduce the original Director sprite/handler fingerprints required by Phase 4B. Runtime drafts therefore **cannot promote Huntsville rules**. They are one side of the future comparison, not proof of semantic equivalence.

## Instrumented tests

The emulator test creates a wholly synthetic `.crcontent` ZIP at runtime. It verifies:

- import into `filesDir`;
- package/scenario/asset hashes;
- private PNG decoding;
- renderer bitmap path and touch routing;
- slot isolation;
- app-private runtime trace output;
- fail-closed rejection after an asset-hash mismatch.

GitHub CI also keeps JVM tests and `assembleDebug`. No private package or resulting APK is uploaded as an artifact.


## Trace-plan source binding

When a `.crcontent` includes `trace-plan.json`, packaging now requires that the plan's `source_sha256` exactly match one of the existing `source_archives[].sha256` records produced by `convert-local`. The packager does not invent provenance: it reuses the conversion manifest's already-recorded source archive digest and rejects a plan from any other source.

The private trace-plan schema is also validated exactly before packaging, including the generated observation contract and per-step fields. Packages without a trace plan remain valid content packages, but they cannot provide an independent source binding for a verified-flow proof.
