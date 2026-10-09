# Experimental Director font recovery

This is a separate research tool and standalone Android glyph lab. It does not
change the production renderer, game bundles, save data or approved APKs.
Recovered fonts, contour JSON, comparison images and font-bearing APKs stay private.

## Audit and recover locally

From the repository root (Python 3.11+, optional `.[font-research]` dependencies):

```powershell
python tools/font-experiment/audit_fonts.py --game private/huntsville/game --output private/huntsville/font-audit-new
```

The audit reads the executable and every `data/*.cct`. It distinguishes font Xtras
(type 15 with `font` subtype) from normal font cast members (type 16), and extracts
only linked XMED payloads starting with PFR1. It refuses an existing output directory
or an output inside the original game directory.

For the experimental contour parser, obtain the external
[LibreShockwave source](https://github.com/LibreShockwave/LibreShockwave) privately.
The tested revision is `fca530f9ef388d7ff38fa6c7117feae5bb5411c6`.
Its native parser is AGPL-3.0; the linked research executable is local only and is
not linked into the player or the Android lab. Only the separately licensed
AGPL-3.0 correction patch and its license notice are kept here. `pfr_convert.cpp` is the small adapter using its public API.

```powershell
python tools/font-experiment/prepare_parser.py private/tools/LibreShockwave/cpp/src/font/Pfr1Font.cpp <new-private-parser-directory>/Pfr1Font.cpp
clang++ -std=c++20 -O2 -I private/tools/LibreShockwave/cpp/include tools/font-experiment/pfr_convert.cpp <new-private-parser-directory>/Pfr1Font.cpp private/tools/LibreShockwave/cpp/src/font/PfrBitReader.cpp -o <private-parser.exe>
# Use the PFR filename recorded in the private audit; output must not exist.
private/huntsville/font-research/pfr_outline.exe <input.pfr> <private-contours.json>
python tools/font-experiment/build_font.py <private-contours.json> <private-output.otf> --family "Recovered Experiment"
```

The independent FontTools builder retains the parser's cubic contours in CFF
OpenType, rather than approximating them as quadratic TrueType outlines. It scales
advances between metric/outline units, translates Windows-1252 codes, rejects empty
non-space glyphs and validates required Spanish characters. It does **not** establish
that the external parser decoded every contour correctly. Version 0.2 fixes the
implicit ORU direction and preserves fractional coordinates; validation against a
second reader still reports fixed-point differences in compound glyphs. Original hinting and kerning are not recovered.

## Separate Android lab

Place only `tekton-recovered.otf` and `tekton-italic-recovered.otf` in a private assets
directory. The standalone project is independent of `android/settings.gradle.kts`:

```powershell
gradle -p tools/font-experiment/android --no-daemon assembleDebug assembleDebugAndroidTest -PrecoveredFontsDir=<absolute-private-assets-directory>
```

Set ANDROID_HOME and JAVA_HOME as for the main Android project. The APK uses package
`org.rigorcore.caserecomp.fontlab`, a different package and data directory from the
game. It has no network/storage permissions and no game import or save integration.
Install the private APK locally; do not publish an APK containing recovered fonts.

The activity allows editable text, nominal size and italic selection. Each pair
shows Android sans-serif with the approved Tekton size/width scales, then the recovered
font at nominal size. It compares glyph shapes/advances; it does not reproduce the
full game's wrapping, box fitting, nudges, line pitch or timing.

`FontLabProbe` renders a private comparison PNG without starting an activity:

```powershell
adb -s <device> install <fontlab-debug.apk>
adb -s <device> install <fontlab-debug-androidTest.apk>
adb -s <device> shell am instrument -w org.rigorcore.caserecomp.fontlab.test/org.rigorcore.caserecomp.fontlab.FontLabProbe
```

The PNG is in the lab's private `files/comparison-android.png`. Retrieve it via
`adb exec-out run-as ... cat ...` using binary-safe redirection. The probe checks
font loading and sample coverage, not fidelity to native Director. Synthetic contour
conversion tests are in `tests/test_font_experiment.py`.

## Corrected parser and comparison

See [measured results](../../docs/HUNTSVILLE_PFR1_CONTOUR_CORRECTION.md).
`prepare_parser.py` verifies the exact pinned upstream source hash, applies the
AGPL patch to a new private copy, and refuses overwrite. It never patches the
external checkout. Compile `pfr_direction_test.cpp` with that private directory
and the upstream `cpp/include` directory in the include path, linking upstream
`PfrBitReader.cpp`, to exercise the implicit-direction regression.

For a separate reader, obtain DirPlayer revision
`68376fbb4494a6bbad4c70081ecdcb99814a74c9` privately. Set `PFR_REFERENCE_DIR`
to its absolute `vm-rust/src/director/chunks/pfr1` directory and compile
`pfr_reference.rs` with Rust edition 2021. Its modules are external GPL-3.0 source;
no full VM, runtime normalization or FontTools conversion is used in this reader.
Invoke the executable with original PFR1 input and a new private JSON output.
The tested portable Rust 1.90.0 Windows GNU compiler used LLVM-MinGW clang as linker;
local libgcc/libgcc_eh aliases to compiler-rt builtins/libunwind supplied its GNU
link names. No global Rust installation or system changes were made.

```powershell
python tools/font-experiment/compare_outlines.py <corrected.json> <reference.json> <corrected.otf> --output <new-private-report.json>
python tools/font-experiment/render_comparison.py <before.json> <reference.json> <corrected.json> <corrected.otf> <new-private-image.png> --title "Tekton comparison"
```

The validator compares every record, advance, curve control and endpoint. It
normalizes only the explicit/implicit final straight closing edge. CFF tolerance
is 0.001 font units. Reference differences are reported separately, including
integer truncation and other bounded fixed-point differences; a successful result
with reported differences does not assert bit-exact native equivalence.
The image draws three outline sources directly with Matplotlib/Agg and rasterizes
the final CFF with Pillow/FreeType. FreeType 2.13.3 rejected all six original PFR1
payloads; it is not an independent original PFR1 decoder.
