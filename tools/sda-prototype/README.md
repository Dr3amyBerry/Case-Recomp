# Experimental SDA scene prototype

This independent Python prototype reads original RAWDATA resources as data. It
never launches `MysteryPIVegas.exe`, loads a native DLL or edits Director/Android.
It implements a bounded scene interaction slice from reviewed native behavior.
It is not campaign compatibility or a replacement for the approved player.

Dependencies: Python 3.11+, Pillow and pefile; Tkinter only for interactive mode.
Use the local packages already installed for research. Commercial inputs and all
rendered artifacts must remain in ignored `private/` or `local-output/` directories.
No game media, extracted XUI or decompiled code belongs in this tool directory.

```powershell
python -m unittest discover -s tools/sda-prototype -p 'test_*.py' -v
python tools/sda-prototype/prototype.py --resources private/mystery-pi-vegas/game/Resources.dll --scene SCENE_VAULT.MSL --targets obj16,obj5,obj17 --render private/mystery-pi-vegas/research/prototype/vault-initial.png
```

`--render` does not open a window. `--interactive` explicitly opens the prototype
window, not the original game. It draws original scene sprites and accepts clicks
on the scene canvas. The text and hint-penalty button are research controls.
Targets can be explicit single/compound sets or a recovered shuffle batch from a
supplied clock seed. History/overlap filtering and campaign allocation are pending.
The prototype does not read or write original player saves.

Implemented primitives:

- RAWDATA resource lookup, UTF-8/BOM XUI and SDA `mpi:` names.
- Original scene positions/textures and draw order for image/eyespyimage nodes.
- Half-open object bounds plus a nonzero original texture alpha pixel.
- Found-object state; base 5000 and fast-chain bonuses from 00453430.
- Hint score subtraction and rapid-miss history from 00453950/00453830.

Remaining dependencies include original menu routing, campaign selection,
native graph/parent clipping, special collectibles, full text state,
found/hint animations, audio, pause, original serialization and completion rules.
Found sprites now follow a recovered two-pulse/upward lifecycle. The first miss-penalty explanation
and its native gate are not wired to the diagnostic scene, which permits penalties.
Native differential comparison is pending a specific hypothesis and user access.

Rebuild the private direct-call graph from the existing exports:

```powershell
python tools/sda-prototype/map_dependencies.py --export private/mystery-pi-vegas/research/full-decompile --export private/mystery-pi-vegas/research/input-recovery-slice --output private/mystery-pi-vegas/research/sda-dependencies.json
```

`roles.json` contains analyst-assigned addresses, not recovered symbol names.
The graph traverses three direct-call levels from those roots. It verifies that
all role roots were decompiled; it does not claim indirect-call closure or correct
inferred C prototypes. See `docs/MYSTERY_PI_SDA_RECONSTRUCTION.md` for evidence.

## Original atlas text and static menu preview

`fonts.py` recovers horizontal atlas runs (alpha > 4, transparent closing column),
UTF-16 character mapping, float spacing advances, original pixel crops, byte-pair
kerning and escaped line breaks. It never substitutes a system font. The supported
vertical advance escape has one or two digits; other style escapes fail explicitly.
The scene can draw original target rows; the surrounding Tk controls remain diagnostic.

```powershell
python tools/sda-prototype/font_probe.py --resources private/mystery-pi-vegas/game/Resources.dll --output private/mystery-pi-vegas/research/prototype/fonts
python tools/sda-prototype/menu_preview.py --resources private/mystery-pi-vegas/game/Resources.dll --output private/mystery-pi-vegas/research/prototype/menu-initial.png
```

Both commands are headless. Outputs include PNGs and JSON evidence. They require
an output inside this repository's ignored `private/` or `local-output/` directory.
The font report records missing resources and charset/run count discrepancies;
matching counts do not prove native visual equivalence.

The menu preview reads the original XUI child declarations, fonts and translations.
It assumes its parent is activated for rendering, respects explicit child render
flags and captures button action values without dispatching them. It does not bind
a player name, run logo/fader animations, simulate unlock state or start a campaign.
Normal captions use only global offsets; pushed-only caption offsets are excluded.
Label alignment follows 0048aad0, including the one-pixel anchor adjustment.

## Target batches and compound sets

```powershell
python tools/sda-prototype/prototype.py --resources private/mystery-pi-vegas/game/Resources.dll --scene SCENE_VAULT.MSL --seed 8 --target-list --render private/mystery-pi-vegas/research/prototype/vault-native-batch.png
python tools/sda-prototype/prototype.py --resources private/mystery-pi-vegas/game/Resources.dll --scene SCENE_VAULT.MSL --targets obj24+obj25+obj26+obj77 --target-list --interactive
```

The second command opens only the experimental window when explicitly run.
`--seed` and `--targets` are mutually exclusive. `--seed` replays the native
32-bit Visual C RNG and forward shuffle, then selects up to ten sets. The supplied
pool currently includes all scene sets; native history/overlap filtering is not
implemented. The pool does not imply a campaign assignment of ten required hits:
a compound set has several objects, each with its own score and found state.
Localized comma-separated captions change as objects are found.

`--target-list` uses scene atlas fonts and recovered row positions (start y=121,
width=146, per-set height), without a fabricated system font. It does not render
the complete PDA, found-text label animations or native completion effects. Clearing
this diagnostic batch does not start a new scene or report a campaign victory.

`selection.TargetDeck` exposes the recovered strict boundary comparison instead
of silently substituting modulo wrapping. If a subsequent batch reaches a child
index equal to the pool size, it stops with a diagnostic; native transition context
at that boundary is unresolved. Saved-prefix ordering is available independently through restore_batch; restoring
original component history and sampling the original runtime clock remain pending. Tests use synthetic fixtures and
known RNG vectors; no original game data is committed.

## Found-object lifecycle and saved-list ordering

`motion.py` recovers the scalar lifecycle from 004278e0/00427bd0: two scale pulses
between 1.0 and 1.25, initial 0.85-second delay, then upward velocity decrements
of 0.5 per update capped at -10 until the image leaves the top of the scene.
`advance(seconds)` represents one frame; movement/scale steps are per update,
while the delay subtracts elapsed seconds. It must not be replaced by a single
large elapsed-time update when reproducing a sequence of native frames.

The native x86 initializer uses the saved rectangle height for both center axes
(004279cd–004279ee); this unusual horizontal anchor is preserved. State calculations
use stored float32 values. Pixel resizing currently uses Pillow bilinear filtering,
which is experimental and has not been compared with SDA's native surface scaling.
Native rendering cadence, inherited alpha, parent clipping and image-cache details
remain unresolved; the animation is not a claim of native pixel equivalence.

The scene tracks found objects separately from objects whose animation retired.
`remaining_captions()` reflects clicks immediately. `saved_captions()` follows
00421350/0042a3e0 and counts retirement flags instead. Neither writes original saves.
Found images render after their original scene container images, in click order.

`TargetDeck.restore_batch(variants, saved_captions, seed)` implements matching of
saved captions against set variants, ten-slot compaction, active-prefix ordering
and native suffix shuffling. Later sets win a caption collision, as in the native
outer loop; cursor advances ten regardless of the compacted count. This helper
restores ordering only and is not yet exposed as original-save compatibility:
original history rectangles are still needed to retire the correct components.
