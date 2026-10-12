"""Local Ravenhearst Android QA through the Director debug bridge; no model/network API.

Plays the campaign on a device with touches, keys and the game's own handlers: hidden
object rooms, map floors, the diary jigsaw (its Space-key rotation) and the locked doors
through the game's own help line (phone: skip the lock for a time penalty). Checks the
app's mid-scene checkpoint by killing and relaunching the app in a room, at a door and in
the jigsaw. Owned content and the output stay private. Requires the debug APK and ADB.
"""
import argparse
import hashlib
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from caserecomp.pipeline import guard_destination

PKG = "org.rigorcore.caserecomp.synthetic.debug"
ACT = PKG + "/org.rigorcore.caserecomp.app.DirectorLauncherActivity"
REGISTRY = "registry/hkey_current_user\\software\\big fish games\\games\\mystery case files - ravenhearst\\data\\"
POINT = re.compile(r"point\((-?\d+(?:\.\d+)?), (-?\d+(?:\.\d+)?)\)")


def fresh_seed():
    """Registry values with no agents: the game starts at name entry."""
    return {REGISTRY + f"{n}_dat": "0" for n in range(1, 6)}


def point(text):
    match = POINT.search(str(text))
    if not match:
        raise ValueError("not a point: " + str(text))
    return int(float(match[1])), int(float(match[2]))


class FinalLock(Exception):
    """The last lock has no help line: the player solves it."""


class Bot:
    def __init__(self, args):
        self.args = args
        if not args.package.is_file():
            raise ValueError("package must be an existing private file")
        guard_destination(args.package, args.out)
        self.out = args.out.resolve()
        self.out.mkdir(parents=True, exist_ok=False)
        self.started = time.monotonic()
        self.levels = []
        self.lifecycle = []
        self.skipped_doors = []

    # ---- device -------------------------------------------------------------------------

    def adb(self, *args, binary=False):
        result = subprocess.run([self.args.adb, "-s", self.args.serial, *args], capture_output=True, timeout=180, check=True)
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

    def shell(self, *args):
        return self.adb("shell", " ".join(shlex.quote(str(x)) for x in args))

    def event(self, kind, **data):
        row = {"seconds": round(time.monotonic() - self.started, 2), "event": kind, **data}
        with (self.out / "events.jsonl").open("a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        print(json.dumps(row, ensure_ascii=False), flush=True)

    def bridge(self, command):
        answer = self.shell("am", "broadcast", "-a", "org.rigorcore.caserecomp.DIRECTOR_DEBUG", "--es", "cmd", command)
        match = re.search(r'data="(.*)"\s*$', answer, re.S)
        if not match or match[1].startswith("error:"):
            raise RuntimeError("bridge: " + answer[-500:])
        return match[1].replace("\\n", "\n")

    def call(self, target, method, *args):
        return json.loads(self.bridge("|".join(["calljson", target, method, *map(str, args)])))

    def prop(self, name, field):
        return json.loads(self.bridge(f"propjson|global:{name}|{field}"))

    def inst(self, sprite, field):
        return json.loads(self.bridge(f"instprop|{sprite}|{field}"))

    def label(self):
        return self.bridge("label").split(" ", 1)[1].lower()

    def state(self):
        return str(self.call("global:gGameManager", "getGameState")).lstrip("#").lower()

    def dialog_up(self):
        return self.call("global:gGameManager", "getBoolDialogUp") == 1

    def sleep(self, seconds):
        time.sleep(seconds)

    def play_wait(self, seconds):
        """Waits for game animations, divided by --speed; lifecycle waits stay real-time."""
        self.sleep(seconds / self.args.speed)

    def wait(self, condition, seconds=120):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            try:
                if condition():
                    return
            except RuntimeError:
                pass  # the receiver is absent while the Activity loads
            self.sleep(.4)
        raise TimeoutError("condition not reached; last label=" + self.label())

    def touch(self, x, y):
        for action in ("move", "down", "up"):
            self.bridge(f"touch|{action}|{x}|{y}")
            time.sleep(.1)

    def hit(self, sprite):
        answer = self.bridge(f"hit|{sprite}")
        return None if answer == "none" else tuple(map(int, answer.split()))

    def click(self, sprite):
        where = self.hit(sprite)
        if where is None:
            return False
        self.touch(*where)
        return True

    def sprites(self):
        rows = {}
        for row in self.bridge("sprites|1|800").splitlines():
            parts = row.split("|")
            rows[int(parts[0])] = parts[1]
        return rows

    def named(self, name):
        for n, member in self.sprites().items():
            if member in (name, name[:-1] + "B", name[:-1] + "b"):
                if self.click(n):
                    return
        raise RuntimeError("no button " + name)

    def buttons(self, sprite):
        rows = {}
        for row in self.bridge(f"flashbuttons|{sprite}").splitlines():
            x, y, name = row.split(" ", 2)
            rows[name] = (int(x), int(y))
        return rows

    def capture(self, name):
        (self.out / (name + ".png")).write_bytes(self.adb("exec-out", "screencap", "-p", binary=True))

    # ---- session ------------------------------------------------------------------------

    def launch(self, initial=False):
        self.shell("am", "force-stop", PKG)
        args = ["am", "start", "-n", ACT, "--es", "title_id", "ravenhearst", "--ez", "play", "true"]
        if initial:
            args += ["--es", "import_external", "qa-raven.zip"]
            if self.args.fresh:
                args += ["--es", "import_save", "qa-raven.json"]
        self.shell(*args)
        self.wait(lambda: self.label() == "splash2", seconds=300)
        self.sleep(2)
        self.touch(400, 300)
        self.wait(lambda: self.label() not in ("splash2", "splash1"), seconds=60)

    def start_game(self):
        self.wait(lambda: self.label() in ("entername", "mainmenu"), seconds=60)
        if self.label() == "entername":
            self.sleep(2)
            self.bridge("key|" + self.args.agent + "\\r")
            self.wait(lambda: self.label() == "mainmenu")
        self.sleep(2)
        self.named("main_startA")
        self.play_wait(3)

    # ---- dialogs ------------------------------------------------------------------------

    def dialog(self):
        if not self.dialog_up():
            return False
        buttons = self.buttons(719)
        modes = sorted(name for name in buttons if "mode" in name.lower())
        if "yes_mc" in buttons and "no_mc" in buttons:
            # Help line: choose yes, then accept (the game removes this level's hints and up to five minutes).
            self.touch(*buttons["yes_mc"]); self.sleep(.3)
            if "ok_mc" in buttons:
                self.touch(*buttons["ok_mc"])
            self.skipped_doors.append(self.level())
            self.event("door_skipped_by_help_line", level=self.level())
        elif modes:
            # Game mode choice: the first (detective, timed), then accept.
            self.touch(*buttons[modes[0]]); self.sleep(.3)
            if "ok_mc" in buttons:
                self.touch(*buttons["ok_mc"])
        else:
            point = buttons.get("ok_mc") or next(iter(buttons.values()), None)
            if point is None:
                return False
            self.touch(*point)
        self.play_wait(3)
        return True

    # ---- scenes -------------------------------------------------------------------------

    def level(self):
        return self.call("global:gUserData", "getPlayerData", "#currentLevel")

    def pending_rooms(self):
        items = self.call("global:gUserData", "getPlayerData", "#itemData")
        return [row[0] for row in items if isinstance(row, list) and any(
            isinstance(item, list) and (0 in item[1] if isinstance(item[1], list) else item[1] == 0) for item in row[1:])]

    def map_step(self, tried_tabs):
        pending = self.pending_rooms()
        rooms = {}
        for row in self.bridge("callsprites|1|130|getLocNum").splitlines():
            n, value = row.split("=", 1)
            if value.isdigit():
                rooms[int(n)] = int(value)
        visible = self.sprites()
        for n, loc in rooms.items():
            if n in visible and loc in pending and self.call(f"sprite:{n}", "getSelectable") == 1:
                self.event("room", level=self.level(), location=loc, locked=self.inst(n, "m_boolLocked"))
                if self.click(n):
                    self.play_wait(4)
                    return
        tabs = [n for n in sorted(visible) if 260 <= n <= 300 and n not in tried_tabs and
                self.bridge(f"callsprites|{n}|{n}|gettablabelsprite").split("=", 1)[-1] not in ("<void>", "")]
        if not tabs:
            raise RuntimeError(f"no reachable room with clues on any floor; pending={pending}")
        tried_tabs.add(tabs[0])
        self.click(tabs[0])
        self.play_wait(2)

    def room_step(self):
        loc = self.prop("gLocationManager", "m_locationNum")
        rows = self.prop("gLocationManager", "m_userDataArray")
        clicked = False
        for entry in rows[1:] if rows else []:
            if not isinstance(entry, list):
                continue
            data = self.call("global:gItemManager", "getItemData", loc, entry[0])
            sprites = data[-1] if isinstance(data[-1], list) else [data[-1]]
            flags = entry[1] if isinstance(entry[1], list) else [entry[1]]
            for sprite, flag in zip(sprites, flags):
                if flag != 0:
                    continue
                if self.dialog():
                    return
                if self.click(sprite):
                    clicked = True
                    self.wait(lambda: not self.call("global:gLocationManager", "getBoolGrabInProgress"), seconds=30)
                    self.play_wait(1)
                    if "location" not in self.lifecycle and self.args.lifecycle:
                        self.checkpoint("location")
                        return
                else:
                    self.event("item_unreachable", location=loc, sprite=sprite)
        if not clicked and not self.dialog():
            # Nothing left to click here: back to the map.
            self.named("mapButtonA")
            self.play_wait(3)

    def door_step(self):
        if "door" not in self.lifecycle and self.args.lifecycle:
            self.checkpoint("door")
            return
        phone = self.buttons(712)
        if not phone:
            raise FinalLock(f"level {self.level()} lock has no help line: solve it by hand")
        self.touch(*next(iter(phone.values())))
        self.play_wait(2)
        self.dialog()

    def jigsaw(self):
        board = "global:gJigGameBoard"
        if "jigsaw" not in self.lifecycle and self.args.lifecycle:
            self.checkpoint("jigsaw")
            return
        for _ in range(200):
            if self.label() != "metagame":
                return
            if self.dialog():
                continue
            tiles = self.call(board, "getTileSprites")
            visible = self.sprites()
            piece = next((n for n in tiles if n in visible and not self.inst(n, "m_boolSnapped")
                          and not self.inst(n, "m_boolSlidingBack")), None)
            if piece is None:
                if all(self.inst(n, "m_boolSnapped") for n in tiles):
                    self.finish_jigsaw()
                    return
                self.play_wait(1)
                continue
            snap = point(self.call(f"sprite:{piece}", "getSnapPoint"))
            if not self.click(piece):
                self.play_wait(1)
                continue
            self.sleep(.3)
            for _ in range(4):
                rotation = float(self.bridge(f"prop|sprite:{piece}|rotation"))
                if int(rotation) % 360 == 0:
                    break
                # What the Space key (and the app's Rotate button) does while a piece is held.
                self.call(board, "spaceToRotate", 1)
                self.sleep(.3)
            dx, dy = point(self.inst(piece, "m_clickOffset"))
            x, y = snap[0] - dx, snap[1] - dy
            self.bridge(f"touch|move|{x}|{y}"); self.sleep(.3)
            self.touch(x, y)
            self.play_wait(1)
        raise RuntimeError("jigsaw did not finish")

    def finish_jigsaw(self):
        level = self.level()
        self.wait(lambda: bool(self.buttons(65)) or self.level() != level, seconds=90)
        self.capture(f"level-{level}-diary-page")
        if self.level() == level:
            self.touch(*next(iter(self.buttons(65).values())))
        self.wait(lambda: self.level() != level, seconds=60)
        self.levels.append(level)
        self.event("level_complete", level=level)

    # ---- checkpoint ---------------------------------------------------------------------

    def checkpoint(self, kind):
        """Kill the app mid-scene and check the checkpoint brings back the same scene and inventory."""
        self.sleep(6)  # the periodic save, then the Activity pause saves again
        before_label = self.label()
        before_items = self.call("global:gUserData", "getPlayerData", "#itemData")
        if before_label.startswith("loc"):
            before_items = self.prop("gLocationManager", "m_userDataArray")
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.sleep(2)
        self.launch()
        self.wait(lambda: self.label() == before_label, seconds=120)
        self.sleep(2)
        if before_label.startswith("loc") and self.prop("gLocationManager", "m_userDataArray") != before_items:
            raise AssertionError("room inventory changed after the app restarted")
        self.lifecycle.append(kind)
        self.event("restart_verified", checkpoint=kind, scene=before_label)

    # ---- run ----------------------------------------------------------------------------

    def run(self):
        self.adb("get-state")
        remote = f"/sdcard/Android/data/{PKG}/files/import"
        self.shell("mkdir", "-p", remote)
        self.adb("push", str(self.args.package.resolve()), remote + "/qa-raven.zip")
        seed = self.out / "seed.json"
        seed.write_text(json.dumps(fresh_seed()), encoding="utf-8")
        self.adb("push", str(seed), remote + "/qa-raven.json")
        self.launch(initial=True)
        self.start_game()
        tried_tabs = set()
        last = None
        for _ in range(20000):
            label = self.label()
            if label != last:
                self.event("scene", label=label, level=self.level())
                if label == "map":
                    tried_tabs = set()
                last = label
            if label in ("outro",) or self.level() >= self.args.until + 1:
                break
            if label != "metagame" and self.dialog():
                continue
            if label == "diary":
                self.named("closeDiaryTab"); self.play_wait(3)
            elif label == "map":
                self.map_step(tried_tabs)
            elif label.startswith("loc"):
                self.room_step()
            elif label == "door":
                try:
                    self.door_step()
                except FinalLock as stop:
                    self.event("stopped_at_final_lock", reason=str(stop))
                    break
            elif label == "metagame":
                self.jigsaw()
            elif label == "mainmenu":
                self.named("main_startA"); self.play_wait(3)
            else:
                self.play_wait(1)
        self.capture("final")
        self.event("finished", levels=self.levels, lifecycle=self.lifecycle, doors_skipped=self.skipped_doors)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", type=Path, required=True, help="private ravenhearst.director.zip")
    parser.add_argument("--out", type=Path, required=True, help="new PRIVATE output directory")
    parser.add_argument("--speed", type=float, default=3, help="game animation wait divisor (1..4)")
    parser.add_argument("--agent", default="Bot", help="agent name typed on a fresh start")
    parser.add_argument("--keep-save", dest="fresh", action="store_false",
                        help="continue the device's current Ravenhearst agents instead of starting fresh")
    parser.add_argument("--no-lifecycle", dest="lifecycle", action="store_false", help="skip the kill/restore checks")
    parser.add_argument("--until", type=int, default=20, help="stop after completing this level")
    args = parser.parse_args()
    if not 1 <= args.speed <= 4:
        parser.error("--speed must be between 1 and 4")
    bot = Bot(args)
    result = {"passed": False}
    try:
        bot.run()
        result["passed"] = True
    except Exception as error:
        result["error"] = f"{type(error).__name__}: {error}"
        bot.event("failed", error=result["error"])
        try:
            bot.capture("failure")
        except Exception:
            pass
    finally:
        result.update(levels=bot.levels, lifecycle=bot.lifecycle, doors_skipped=bot.skipped_doors,
                      seconds=round(time.monotonic() - bot.started))
        (bot.out / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        try:
            (bot.out / "logcat.txt").write_text(bot.shell("logcat", "-d", "-t", "3000"), encoding="utf-8")
        except Exception:
            pass
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
