# Huntsville presentation isolation: Stage 1

Estado: **APROBADO_HUMANAMENTE**. [Referencia congelada y autorizacion de Etapa 2](HUNTSVILLE_APPROVED_REFERENCE.md).

## Scope and baseline

Baseline source: `08b64fadb5c45e1f702f20b27ab5fe5e6cc37c47` on `main`.
The previous [history audit](HUNTSVILLE_COMPATIBILITY_AUDIT.md) and
[93-commit inventory](huntsville-adjustments.csv) are the historical evidence.
This implementation changes two existing app files and adds a small app-side
registry. HomeActivity, cards, repositories/importers, storage, DirectorRuntime,
Lingo VM, TextLayout, StageRenderer and DirectorStageView remain byte-identical.
No second engine, deleted legacy files or new branch.

Private baseline and candidate logs, APKs, screenshots, sprite traces and data
backup live in `private/huntsville/profile-isolation/2026-10-08/`.
They are ignored by Git. Existing references were not overwritten.
The local untracked `DirectorAndroidPorts.kt` was preserved unchanged; it has no
callers. Local APKs include it, so they describe the recorded workspace rather
than a pristine checkout. Private JVM smoke tests are also ignored local files.

WSA was reached with ADB at `127.0.0.1:58526`, API 33, screen 1600x900.
The baseline and candidate debug app use `org.rigorcore.caserecomp.synthetic.debug`,
version 0.6.0-debug/code 8. Movie stage 800x600, rasterizer scale 2;
the viewport displays it at 1.2 screen pixels per stage pixel (origin 320,105).
Both runs use Android's same system substitute fonts, not recovered font binaries.
A full app-private files/preferences backup was made before testing. Instrumentation
mutates synthetic imports/preferences, so the original backup is restored afterward.

## Classification and moved values

A = generic correctness, B = technology/version behavior, C = measured title
calibration, D = provisional workaround. Discovery during Huntsville testing alone
is insufficient evidence for C. In this stage C/D presentation parameters move;
A/B mechanics remain in their existing modules.

All values below were previously global in AndroidDirectorPorts.kt. They now live
in `DirectorPresentationProfiles.HUNTSVILLE_ES`, schema 1/revision 1, and are passed
to AndroidDirectorText by DirectorLauncherActivity. Their old precedence is kept
independently for width, size, pitch and skew, even for overlapping face aliases.

| Exception | Preserved value | Class / evidence |
|---|---|---|
| Typewriter width | 0.90 | C/D, Android font substitution, 6c03811 |
| Palatino width / pitch | 0.88 / 1.08 | C/D, 6c03811 and 747d659 |
| Times width / pitch | 0.86 / 1.08 | C/D, 6c03811 |
| Tekton italic width / size / pitch | 0.95 / 0.68 / 1.32 | C/D, 252ba53 and cfeadd2 |
| Tekton width / size / pitch | 1.15 / 0.80 / 1.12 | C/D, 6c03811 |
| Readout width / size / skew | 0.90 / 0.95 / -0.18 | C/D, 6c03811 |
| Default calibrated line pitch | 1.25 times font size | D, platform substitution |
| Embedded face ending in * | Weight/slant from face name | D, candidate B not proven universally |
| Center wrap margin | 6 stage pixels on each side | C/D, cfeadd2 |
| First nonfixed baseline | min(platform ascent, size * 0.78) | C/D, 6c03811 |
| Leading empty paragraph | 0.75 times pitch | C/D, cfeadd2 |
| Minimum overflow pitch | 0.75 times normal pitch | D, 6c03811 |
| Condense attempts | 1.0, 0.95, 0.90, 0.85 | D, substitute-font fit |
| Last-resort size attempts | 0.90, 0.80, 0.70 | D, substitute-font fit |
| Maximum grown text box | 4 times authored height | D, substitute-font fit |
| Optional cluesFoundLabel nudge | (-5, 0) stage pixels | C/D, 747d659 and private presentation.json |

Fit thresholds, iteration order and drawing behavior are preserved. The mechanisms
are activated only when calibrated box parameters are supplied. Retirement requires
measured font/Director-version evidence plus private before/after review; these
values are not claimed as universal Director semantics.

The nudge remains optional: absence of the old external import/presentation.json
keeps the old no-offset behavior. A present file can request only the exact reviewed
member/offset above. Unsupported keys/values are rejected as a whole. The reviewed
member is a noninteractive caption (sprite 238, zero behavior flag in recorded
map/scene traces); its drawing offset does not move any input target. StageRenderer's
optional offset primitive and DirectorRuntime hit testing are unchanged.

## Generic behavior retained

Director parsing, cast/Score registration, ink/stretch/alpha composition, sprite
coordinates and event dispatch, Flash/AVM1, Lingo, Xtras and audio are unchanged.
TextLayout continues authored alignment, tab handling, paragraph indentation,
word wrapping and fixedlinespace. Android decoding limits, font-family fallback,
underline/color, scale rendering and bitmap memory cap remain generic.

Unrecognized packages use authored size with unit width/size multipliers, native
Android font spacing/ascent, authored style flags, full leading blank pitch and the
authored text box height. They receive no calibrated fit, centered inset or offsets.
Generic appearance is a platform fallback, not a claim of Director font fidelity.

## Identity, integrity and legacy imports

The registry hashes the actual verified movie.json AND lingo.json bytes, never a
folder, card title, face name or a caller-provided source hash. The reviewed pair
list covers local v1, current v2 with/without cover, and four historical movie
snapshots already imported on WSA (six distinct movie hashes; common Lingo hash).
Digests are public identity references; no commercial bytes are committed.

The existing importer entry size/hash checks, source binding checks and repository
whole-ZIP digest verification remain required and unchanged. Identical presentation
and script bytes intentionally share a profile even if packaging/cover differs;
this verifies the reviewed conversion snapshot, not ownership or executable provenance.
Profile selection does not exempt altered media or entries from integrity checks.

Old packages need no reimport or ZIP rewrite. Existing SHA256-of-ZIP identifiers,
active selection and package-prefixed save keys do not change. A conversion/edition
whose pair has not been reviewed falls back to generic presentation and displays a
notice. Its import and saves remain accessible. Supporting another legacy edition
requires verifying its private snapshot and adding a reviewed pair; title metadata
cannot opt it in. This is the safe transition for packages with insufficient identity.

Profiles are Kotlin data, with no scripts, reflection or executable configuration.
The optional legacy JSON is capped at 64 KiB, accepts only nudge, and accepts only
an exact two-integer reviewed offset. It cannot select a profile, introduce another
member or bypass bundle verification.

## Verification and limits

Baseline Python: 276 tests, 2 platform skips. Candidate: same results.
Kotlin candidate: app 10 and engine 81 tests, no failures/skips (includes private
smoke harness); seven new JVM tests check identity pairs, unknown/synthetic content
with homonymous fonts, preserved parameter precedence, malformed configs and three
existing private ZIPs without modifying them.

WSA: all 18 instrumentation tests passed, including the three new presentation tests.
The old rasterizer is frozen in androidTest only. Eight synthetic font combinations,
three texts, three alignments, two fixed-line settings, two scales and two box sizes
produce 1,152 identical pixel arrays, dimensions and matching width/pitch metrics.
A separate Android test verifies generic fallback and unchanged save namespace.
Existing tests cover import/render/navigation, entry gestures, lifecycle and proof
rejection; their synthetic fixtures are only in the test APK.

Private WSA captures exercise initial screen, main menu, map and search scene with
HUD/object list/timer/hint controls. The static cluesFoundLabel crop is exactly equal.
All 93 baseline scene sprite rows remain present and identical; one transient fuzz
sprite is additionally visible. Map glass moves three pixels with animation and
menu hover state differs; whole-screen captures are not deterministic comparisons.
Baseline filenames ending scene-dialog/map-report do not prove an open modal: the
bridge reported no active Flash button. Nine deterministic private JVM screenshots are pixel-identical before/after, including
name entry and map prompts; this uses the unchanged AWT test rasterizer, so it does
not independently validate Android font fidelity. Original WSA save preferences
and all 11 original app-private files were restored byte-for-byte after testing.
No assertion of modal/puzzle visual fidelity
is made from those filenames or from JVM tests.

Debug, test and signed release APK builds succeeded. Release app ID, version code,
signing key and storage remain unchanged. Game resources are imported privately;
no commercial game files are packaged in the review APK. Artifact hash, source
commit and installation instructions are recorded in [the review delivery document](HUNTSVILLE_REVIEW_BUILD.md).

## Human checkpoint and existing problems

Review fonts, baselines, caption positions, menus, username selection/creation,
map, search HUD/list, timer, hints, dialogs, puzzles, navigation and save/reload on
the intended device. Original native-game comparisons, full campaign/puzzle coverage,
other Android versions and other unreviewed editions are still pending.

Existing mixed-run text fidelity, missing exact embedded fonts, incomplete Flash/Xtra
semantics, audio lifecycle concerns, memory budgeting and the fixed active-package
hub behavior were not changed. See the history audit for their evidence and limits.
No production release is published. Mystery P.I. work is stopped until explicit
human approval of Stage 1, as required by the user's checkpoint.

## Follow-up requested by the reviewer: name-entry vertical alignment

The reviewer reported the pre-existing name/caret height mismatch with the baked-in
Agente caption. Profile revision 2 now supplies (0,+6) stage-pixel drawing offsets
for sprite 33/member enterNameField1 and sprite 31/member cursor. These offsets are
always enabled for a verified Huntsville snapshot, independently of legacy JSON.
The shared name member on user-list sprite 277 stays at its authored location.

StageRenderer gains an optional generic scopedNudges map, keyed by sprite number
AND member name, with an empty default. It combines scoped offsets with the existing
optional member offsets before stage scaling. There are no title conditionals in
Director, no changed text calibration, Lingo positions, saves or package identifiers.
This follow-up intentionally corrects the old appearance at the user's request;
the original Stage 1 preservation results above describe the earlier revision.

WSA reproduces Dream in the real name dialog before/after. Name and caret now draw
6 stage pixels lower (7.2 screen pixels at this viewport), near the caption baseline.
Recorded sprite rectangles remain unchanged. Private captures are under
private/huntsville/profile-isolation/2026-10-08/name-alignment/. The user-list capture
is not pixel-identical because of runtime/hover animation; its exclusion is checked
by scoped-offset tests, without claiming a deterministic whole-dialog comparison.

Updated Kotlin suite: 11 app + 82 engine tests pass; all 18 Android tests pass on WSA.
Two new tests check profile isolation and actual scoped composition at scales 1/2,
including a shared member on another sprite, a mismatched member, unchanged bounds
and empty-default rendering. Previous font pixel-equivalence tests still pass.
This build remains PENDIENTE_VALIDACION_HUMANA; no Stage 2 authorization is inferred.

## Follow-up: cursor spacing, change-user button and user-list rows

Profile revision 3 implements the reviewer's three additional requests:

- Name-dialog caret (sprite 31/member cursor): offset (+12,+6), leaving a small
  horizontal gap after the final glyph. The previous vertical correction is kept.
- Menu change-user button (sprite 19): (+6,0) for BOTH changeUserA and changeUserB,
  preserving its alignment through rollover swaps.
- Agent-list names (sprites 277..281/members enterNameField1..5): (0,+6), including
  the first row. Name-dialog sprite 33 remains (0,+6).

Only a verified Huntsville snapshot receives these values. No font metrics or
commercial game data are changed. The button's input area follows its drawing:
DirectorRuntime accepts an optional empty-default spriteHitOffsets map keyed by
sprite and member. Sprite lookup, active lookup, release-outside and Flash coordinate
conversion use the translated point. The app supplies only the two menu-button
entries. Lingo rectangles and saves remain unchanged. Generic packages have no
input/drawing offsets; rows/caret keep their existing input behavior.

WSA captures in private/huntsville/profile-isolation/2026-10-08/user-alignment/
show the separated caret, button placement and lower list row (the saved test user
is Robot). Tapping the moved button at its displayed position opens the user list;
New then opens the name dialog, where Dream was entered without confirming/saving.
Kotlin: 11 app + 83 engine tests pass. All 18 Android tests pass, including the
unchanged 1,152-case text comparison. The new input-offset test checks moved and
old control areas, wrong-member exclusion, default behavior and unchanged bounds.
Private app data is restored after these checks. Human visual acceptance remains
pending, and Stage 2 is still gated by explicit approval.

## Follow-up: caret raised slightly and a visible bot demonstration

Profile revision 4 raises only the name-dialog caret by 3 stage pixels relative to
revision 3: sprite 31/member cursor now uses (+12,+3). Name, button and list-row
offsets remain as previously reviewed. App JVM tests and all 18 WSA instrumentation
tests pass. A visible one-case run of the existing private wsa_autoplay.py bot is
requested by the reviewer. The bot reads debug state and sends normal Android taps,
including object collection and tile swaps, rather than setting completion flags.
Run evidence and recordings remain private. This request does not authorize Stage 2.

## Follow-up: timer, session checkpoint and case-report alignment

Profile revision 5 moves case-report buttons, their status icons and narrative text
four stage pixels left; button input areas follow the drawing. Other offsets remain.
The verified Huntsville edition now uses native save handlers plus a resume bookmark,
with atomic persistence, paused background time and a stable-frame restore sequence
that fixes the black screen reported during review.
See [checkpoint scope and limitations](HUNTSVILLE_SESSION_CHECKPOINT.md).

## Follow-up: time-limit baseline

Profile revision 6 moves only sprite 208/timeLimitField down five stage pixels.
Its original top was 64 versus 69 for sprite 207/timeLimitLabel; both now share the
same displayed top. The countdown timer is unchanged. This calibration is scoped
to the verified Huntsville edition; generic packages have no inherited offset.
