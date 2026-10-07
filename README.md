# Case-Recomp — Director-to-Android research

Independent, **read-only** tooling for analyzing a user's own copy of **Mystery Case Files: Huntsville (Spanish)**. This repository does **not** include the game, its media, converted assets, decompiled proprietary source, or an APK.

## Current state

**Phase 1:** structural inventory and signature checks for Windows PE projectors and Director cast files (.cct / XFIR). This is not yet a decompiler or Android port. The long-term goal is an Android runner/reimplementation with a local-user-data import flow.

## Quick start

Requires Python 3.11+ (standard library only).

    python -m unittest discover -s tests -v
    python -m caserecomp scan /path/to/your/installed-game --output inventory.json

The scanner only reads files and produces file sizes, SHA-256 fingerprints, PE section metadata and recognized Director container headers. It never executes the Windows binaries. Do not commit inventory reports containing personal file paths or proprietary game resources.

## Architecture and next steps

- caserecomp/inspector.py: bounded PE/Director signature inventory.
- tests/: synthetic tests, no copyrighted fixtures.
- docs/ROADMAP.md: milestones, constraints and deferred decisions.
- .github/workflows/ci.yml: Python unit tests on three runtimes.

Evaluate ProjectorRays (https://github.com/ProjectorRays/ProjectorRays; MPL-2.0) and LibreShockwave (https://github.com/LibreShockwave/LibreShockwave) under their own licenses. Verify compressed-cast support against the user's exact input before choosing a runtime. Native x86 PE code cannot simply be compiled as an Android APK.

## Legal and security constraints

Keep commercial executables, CCT libraries and extracted media in a private local directory outside Git. Do not upload them to public GitHub repositories, Actions caches or artifacts. Users must obtain their own properly licensed copies; this project does not bypass DRM or distribute commercial game content.
