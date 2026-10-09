# Ghidra static exports

ExportNativeSlice.java executes inside Ghidra, not inside the inspected executable.
Use official Ghidra 12.1.4 and JDK 21. Headless can import a PE and retain its project:

```powershell
& <ghidra>/support/analyzeHeadless.bat <private-project-dir> <project> -import <private-input.exe> -scriptPath tools/ghidra -postScript ExportNativeSlice.java <private-output> -analysisTimeoutPerFile 300 -max-cpu 2
```

For an existing project, use `-process <program-name> -noanalysis` instead of import.
Optional roots after output are function addresses, `vtable:<address>` pointer-run
candidates, or `all` for all recognized functions (maximum 10,000). Normal slices
are capped at 600; each decompilation has a 15-second timeout. Directed roots add
three levels of direct callees, plus immediate callers. Indirect calls require
explicit roots; pointer runs do not establish vtable boundaries.

Output includes functions, references, calls, completion/error status and private C
pseudocode files. Inspect completion status and headless logs: Ghidra may exit zero
even if a script throws. Decompiled output is not recovered source and inferred
prototypes can be wrong. Keep proprietary inputs, projects and exports private.

Use quoted roots in PowerShell (for example `'00492800,00492de0'`); otherwise
PowerShell can interpret commas as arrays and hexadecimal-looking values as
numbers before passing them to the headless batch launcher.

A root such as `discover:004b71d0` explicitly disassembles and defines a missing
function at an executable address in the private project. Existing overlapping
functions are rejected. This changes analysis metadata only, never the input PE.
Review each discovered function and its decompilation warnings; an exported
function count does not prove coverage of callbacks or indirect targets.
