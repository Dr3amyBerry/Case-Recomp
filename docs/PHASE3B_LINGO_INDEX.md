# Phase 3B.3 — Director 8.5 handler metadata and decompiler cross-check

**Status: validated on the provided original movie, without executing it.** The reader is a bounded, read-only structural analyzer. No recovered proprietary handler text, bytecode or original game resource is checked into the public repository.

## Source-of-truth structure

The main `XFIR/FGDM` movie stores its compiled scripts (`Lscr`) inside the `ILS ` initial-load segment. The associated `Lnam` symbol table stores MacRoman-length-prefixed names, and `LctX` stores 12-byte script-directory entries pointing to resource IDs. `caserecomp.lingo_index` follows **actual resource identifiers** from the archive map instead of relying on contiguous, guessed cast IDs. For each `Lscr`, it reads the Director 8.5 handler table (46 bytes per record) and validates name IDs, bytecode offsets and bytecode-length boundaries.

Private inspection of the exact supplied projector:

| Property | Verified count |
|---|---:|
| `Lnam` names | **1,608** |
| `LctX` context entries (including free slots) | **92** |
| Referenced compiled `Lscr` scripts | **80** |
| Named handler records | **527** |
| Distinct case-insensitive handler symbols | **287** |
| Handler symbols appearing more than once | **27** |

The report includes per-handler `script_id`, optionally redacted handler-name SHA-256, bytecode size and bytecode SHA-256; **it never copies bytecode or reconstructed proprietary Lingo into the report**. Source names are redacted by default; explicitly request `--show-names` for a private-only report.

**Important limitation:** a handler's name and exact bytecode span are *not* its executable semantics. No event ordering, stage commands, engine compatibility, or original Lingo source is established by this structural index.

## Cross-checking decompiler outputs

ProjectorRays (`6f9bcebf626b43719abe2affcbbcb041d154d666`, MPL-2.0) supports `decompile --dump-scripts`, producing human-readable `.ls` when it succeeds. LibreShockwave (`fca530f9ef388d7ff38fa6c7117feae5bb5411c6`, AGPL-3.0) includes a Lingo decompiler and an asset extractor that may export `.ls`/`.lsasm`. Their source builds and tool help are separately verified. A private real-game run now produced 80 source listings and 80 assembly listings from each tool. ProjectorRays text is decoded explicitly as `mac_roman`; LibreShockwave output is UTF-8. Both contain **527 complete handlers** and **287 distinct names**, and all **287/287** names from the original compiled index occur on both sides.

The independently authored `compare-lingo` command counts exact common names and conservatively normalized text-body matches across two locally generated `.ls` trees. With `--reference-movie` it *also* checks each provider's names against the actual game's `Lnam/LctX/Lscr` index, producing missing/unindexed name sets. Results from both providers can differ because of decompilation strategy or recovered source formatting: name-set parity and equal normalized source text do **not** establish bytecode-level or runtime equivalence.

```bash
# Reports must be private paths OUTSIDE any Git repository.
python -m caserecomp lingo-index /private/game/MysteryCaseFiles.exe \
  --output /private/reports/game-lingo-index.json

# Optional names in private-only report:
python -m caserecomp lingo-index /private/game/MysteryCaseFiles.exe \
  --output /private/reports/game-lingo-readable.json --show-names

# After successful *separately installed* tool runs with .ls files:
python -m caserecomp compare-lingo /private/lingo/projectorrays /private/lingo/libreshockwave \
  --reference-movie /private/game/MysteryCaseFiles.exe \
  --output /private/reports/lingo-crosscheck.json --redact-names
```

The generated `lingo-index` report rejects overwrite and refuses writing inside `.git` trees; binary input is never run. CLI prints counts and not the full symbol list. Use an isolated, low-privilege local OS sandbox for third-party native tools. Do not export `.ls` or compiled resources as GitHub Actions artifacts, caches, commits, or release attachments.

## Fidelity and remaining work

1. Generate verified **real-game** `.ls` outputs with both pinned decompilers on an offline isolated workstation, capturing tool version, exit code and reproducible chunk/hash provenance; investigate errors instead of claiming absent output succeeded.
2. Preserve the measured baseline: 527/527 handler records and 287/287 names align structurally. Assembly address/opcode sequences match for 527/527 records, but operands and runtime semantics are not certified. Phase 4 must use deterministic state/event traces rather than trusting either decompiler text as unquestioned source.
3. Preserve `Lnam/LctX/Lscr` resource relations across 38 external casts when script references exist; do not infer that cast libraries contain all needed controller code.
4. Integrate script behavior into the Android Kotlin state machine only after golden replay fixtures, audio/visual frame comparisons, and deterministic save/load tests exist.

## Tests

Purely generated synthetic fixtures cover malformed `Lnam`, bogus `LctX` IDs, truncated `Lscr` handler spans, index joins, name-redaction, report overwrite protection and cross-provider membership checks. The original-game count audit stays private; public CI uses no copyrighted content and requires 90% or more Python source coverage.
