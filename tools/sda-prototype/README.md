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
supplied clock seed. History replay is available through the Scene API; campaign allocation and native player files remain pending.
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
CLI pool includes all scene sets because it supplies no original player history.
The Scene API can replay scoped history marks before selecting or restoring sets. The pool does not imply a campaign assignment of ten required hits:
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
the native player serializer and sampling the original runtime clock remain pending. Tests use synthetic fixtures and
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
the Scene API now replays typed history points to retire the correct components;
loading the native player file and full campaign context remain pending.

## History replay and fresh-campaign pruning

`history.py` models typed in-memory `HistoryMark(text, x, y)` observations from
00424740 and common filtering paths in 00423680. It retains original scoped
captions and first-bracket variant parsing. Compound components use strict point
interior tests; single sets take a separate path that can hide/remove them without
that test. Nonzero variant mismatches remove matching sets; the zero-variant
exceptions and shifted-child iteration are preserved.

The Scene API accepts `history`, an explicit `history_variant`, and
`saved_captions` along with a seed. After a diagnostic scene constructed with an
explicit variant has retired its found-object animations, its observed list can
be reconstructed in memory:

```python
restored = Scene(resources, scene.name, seed=1,
                 history=scene.history, history_variant=scene.history_variant,
                 saved_captions=scene.saved_captions())
```

This restores scene-object/list state only. It does not restore score, clock,
player selection or campaign state, and does not read/write an original save.
The CLI still starts a diagnostic selection; no native-save option is claimed.

`prune_scene_history` recovers 00423000's freshness threshold: it counts affected
sets using caption substrings in scoped history excluding the literal `[0]`.
If fewer than ten sets are unaffected, it removes that scoped nonzero history.
Exactly ten keeps it. `prune_previous_history=True` requests this fresh-campaign
path explicitly; it cannot be combined with saved-list restoration. Correct route
selection still depends on recovered player/campaign context.

## Play another batch and preserve experimental progress

```powershell
# New diagnostic session, with original target rows and autosave.
python tools/sda-prototype/prototype.py --resources private/mystery-pi-vegas/game/Resources.dll --scene SCENE_VAULT.MSL --seed 8 --target-list --save local-output/sda-prototype/progress.json --interactive
# Resume the same session after closing it.
python tools/sda-prototype/prototype.py --resources private/mystery-pi-vegas/game/Resources.dll --scene SCENE_VAULT.MSL --resume --target-list --save local-output/sda-prototype/progress.json --interactive
```

`Siguiente tanda` enables after all active objects finish their retirement animations.
It advances the recovered deck without resetting score, elapsed time or found state.
Native boundary diagnostics still apply; full campaign routing and pool refresh at
that boundary are not invented. The surrounding buttons are diagnostic controls;
the original menu's player/map/dialog routing is not yet connected.

`progress.py` writes the versioned `case-recomp-sda-prototype/1` format atomically.
It preserves current sets, candidate/deck order and cursor, score/fast-chain/miss
history, elapsed/since-found timers, history marks and each object's visibility
and complete experimental motion state. The save binds to the original DLL hash.
Loading different resources or an unsupported schema fails before returning a
scene. Offline time is not added to gameplay timers.

With `--save`, the window saves after clicks, hints, batch changes, every five
seconds and on close. `Guardar progreso` also saves manually. The supported path
is JSON under `local-output/sda-prototype/`; the approved APK and original player
saves are outside this output directory. A new headless render with `--save` also
writes its initial progress; a resumed headless render only reads that progress.

Backend tests complete a first batch, enter a second, write/load a real JSON file
and continue finding objects. Mid-animation restoration preserves the stored
state. A private check also invoked the actual Tk next/save/close callbacks with
the window withdrawn, without moving the mouse or opening the original game.
This is an experimental playable loop, not native player-file or campaign
compatibility. The campaign shell below now models the level clock and global
set count; original startup UI, victory/bonus and differential comparison remain
pending.


## First-level experimental campaign

```powershell
# Open the shell, click Nueva partida experimental, then a recovered map card.
python tools/sda-prototype/campaign_preview.py --resources private/mystery-pi-vegas/game/Resources.dll --save local-output/sda-prototype/campaign.json --seed 8
# Resume the same campaign after closing.
python tools/sda-prototype/campaign_preview.py --resources private/mystery-pi-vegas/game/Resources.dll --save local-output/sda-prototype/campaign.json --resume
```

`campaign.py` reads the original `LEVELS_1.XUI` rather than assigning an invented
per-scene quota. The first level requires 18 completed sets across vault/slots,
with a 1320-second clock. The map now draws the original scene cards, arranged
in declaration order with the native count-dependent layout. Each scene starts
its recovered ten-set selection;
compound targets can require multiple clicks, but count once after all components
retire. Use the original PDA map button to reach the other location. The shell stops at
`objects_complete`, which is the object objective boundary, not the original
bonus round, clue collection, victory screen or next-level progression.

`clock.py` reproduces the scalar timer's float32 storage and unusual pre-update
check. The update compares integer seconds modulo 60 before storing elapsed;
warning IDs occur at remaining 181/120/60/10, and timeout is checked against the
old elapsed value. The native pause flag suppresses elapsed storage but does not
suppress that check. Display truncates elapsed first, then remaining time, and
clamps the result to zero. Large frames do not synthesize skipped warnings.
The shell uses a separate map/input boundary and freezes outside its scene phase;
this policy has not been verified against the original graph's pause/dialog flow.

Campaign saves use `case-recomp-sda-campaign/1` and contain all visited scene
snapshots, total points, retired-set accounting, current scene/phase, level index,
seed and full clock. Atomic output and the resource hash restrictions are shared
with diagnostic scene saves, but the formats are distinct. The shell caches scene
instances when switching locations; original destruction/reconstruction, native
profile files and scene variant allocation are pending. It resets the scoring
chain/miss buffer on entry, matching fresh score-node construction; this still
needs native runtime comparison. It adds no offline elapsed time.

Private checks completed the ten vault sets, saved/loaded at the map boundary,
entered slots and completed eight more, ending at 18 sets and 331500 points.
Both intermediate and final campaign snapshots matched after file round trips.
The actual Tk new-game, scene selection, map, save and close callbacks also passed
with the window withdrawn. No original EXE or global mouse/keyboard input was used.
The full synthetic suite now has 40 passing tests. The original menu remains a
static preview; these buttons explicitly belong to an experimental campaign shell.


## Recovered scene-selection cards

`map_view.py` reads `mapunderlay` from ENVS, filters its scene buttons to the
current level, and replays 0043eec0's layouts for one through nine visible cards.
The circuit-map `mapscreenlevels` is a separate component and is not used as the
scene-card container. In level one, declaration order places slots at (278,211)
and vault at (477,211), independent of the level's scene-list order.

The normal/hover/pushed textures and thumbnails come from the original resources.
004524d0 draws thumbnails first at (13,11), then the button frame, then separate
name/count labels at (19,115)/(160,97). Pushed state offsets the thumbnail and
labels by two pixels, leaving the frame in place. The temporary labels retain
their own clipping rectangles, original atlas fonts and localized captions.
Unvisited cards show the native default ten; visited cards use remaining saved
captions, which count retirement rather than click flags.

The pointer model preserves idle/hover/pushed/dragged-outside/disabled states,
half-open bounds, hover-before-press and release activation. Dragging away and
releasing cancels; dragging back permits activation. State three uses the hover
texture. Activations are consumed after a rendered frame and produce the reviewed
302 scene-name route (00452b40 → 00405b70 → 00414af0). The prototype adapter then
enters the cached scene; original transition overlays and deferred graph passes
still require reconstruction.

The campaign canvas uses these cards in place of the separate scene-name buttons.
New-game and save remain experimental surrounding controls. The partial PDA
below supplies return-to-map. Player display, marker animations, audio and
fade/background mode transitions remain pending. The original EXE has not been used to approve visual equivalence.
Private renders confirm initial counts 10/10 and restored counts slots=10,
vault=0. Actual Tk motion/down/up callbacks selected vault, returned to the map,
selected slots and saved on close with a hidden root and no global input.
The suite has 43 passing tests; three new tests cover ordering/counts, pointer
capture/cancellation/deferred activation, and pushed/dragged drawing behavior.


## Partial PDA and native map action

`pda_view.py` renders the recovered PDA backgrounds and atlas labels, with the
campaign clock, grouped score, level number and remaining target count. Scene
rows render above the PDA background. Clock captions retain the recovered
35-pixel width; the value starts at x+35 and retains its default left alignment.
Score formatting inserts literal commas in groups of three digits.

The original mapbutton uses its declared textures, fonts, offsets and pointer
states. Its action 301 is consumed after rendering to return to the experimental
map, preserving progress. It replaces the surrounding Tk return button. The
visible PDA column blocks scene clicks as an explicit experimental input policy;
native parent clipping and transition overlays are not yet reproduced.

Private renders show the Spanish clock caption clipping within the recovered
width. This is recorded for comparison with the original, not corrected through
an unverified layout change. Other PDA controls, hints, collectibles, native
profile startup and campaign completion remain pending. No original EXE was run.

The hidden-root GUI check loaded 175500 points, entered slots through a map card,
returned using the PDA and saved on close without adding a miss or resetting the
clock. Two synthetic PDA tests bring the suite to 45 passing tests. All captures
and real-resource observations remain private.
