# Control of an existing native Windows reference

The user explicitly requested normal Windows execution and window control without
interfering with their desktop. This tool does not start a game, activate a window,
move the system pointer, send global keys or enable virtualization.

```powershell
python tools/native-reference/background_window.py <pid> inspect
python tools/native-reference/background_window.py <pid> capture --output <private-output.png>
python tools/native-reference/background_window.py <pid> click --x <client-x> --y <client-y>
```

Requires Windows and Pillow for captures. Target a single visible, non-minimized
window belonging to the PID. Input coordinates are window client pixels; re-inspect
size after a resize. PrintWindow captures the target client, including when another
window covers it; support depends on the renderer. No desktop screenshot fallback.

Input uses target-specific PostMessage mouse events. Verify the resulting game
state: successful message submission does not guarantee event consumption when
inactive. Before/after cursor and foreground are observations; concurrent user
input can change either. No attempt is made to restore or override the user's focus.
Private game media and reference captures belong outside Git.
