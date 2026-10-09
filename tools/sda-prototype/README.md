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
Targets are explicit single-object sets, not the native campaign's random choices.
The prototype does not read or write original player saves.

Implemented primitives:

- RAWDATA resource lookup, UTF-8/BOM XUI and SDA `mpi:` names.
- Original scene positions/textures and draw order for image/eyespyimage nodes.
- Half-open object bounds plus a nonzero original texture alpha pixel.
- Found-object state; base 5000 and fast-chain bonuses from 00453430.
- Hint score subtraction and rapid-miss history from 00453950/00453830.

Remaining dependencies include original menu routing, campaign selection,
native graph/parent clipping, compound target sets, special collectibles, full text state,
found/hint animations, audio, pause, original serialization and completion rules.
Found sprites currently disappear immediately. The first miss-penalty explanation
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
The independent text layer is not yet wired into the scene's diagnostic Tk controls.

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
