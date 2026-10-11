# Prime Android automation

Approved scope: one local run covering 22 original cases, lifecycle persistence,
long-session measurements, import/catalogue and title isolation. No model calls.

1. Extend the existing debug bridge with structured readback; allow ADB launch
   only in the debug manifest. Preserve release access restrictions.
2. Add an ADB runner reusing the verified private driver's search and puzzle
   algorithms. All gameplay changes go through real stage touches.
3. Record bounded waits, screenshots, memory/gfx snapshots and original getters.
   Require all cases and final state; missing evidence is a failure.
4. Exercise checkpoint/restart at location and puzzle boundaries, compare original
   data, preserve other title stores and inspect the catalogue.
5. Compile, run a bounded WSA smoke, then launch the full run with local logs.
