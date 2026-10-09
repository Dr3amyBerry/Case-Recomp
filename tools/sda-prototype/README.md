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
python -m unittest discover -s tools/sda-prototype -p test_runtime.py -v
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
native graph/parent clipping, compound target sets, special collectibles, font atlas decoding/localization,
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
