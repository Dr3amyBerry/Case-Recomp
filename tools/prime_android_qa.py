"""Local Prime Android QA using the existing Director bridge; no model/network API.

Owned content and output must stay private. Requires the debug APK and ADB.
Gameplay writes are touch/key events, never setters or solve handlers.
"""
import argparse
import hashlib
import json
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from caserecomp.pipeline import guard_destination

PKG = "org.rigorcore.caserecomp.synthetic.debug"
ACT = PKG + "/org.rigorcore.caserecomp.app.DirectorLauncherActivity"
SOURCE = "210a54856729bf4db9a28ed85d1b41a9c08967fce50b4acca20b95f1ddff869b"
PREFIX = hashlib.sha256(("prime_suspects/" + SOURCE).encode()).hexdigest() + "/"

class UnreachableSprite(RuntimeError):
    pass

def resume_progress(report):
    cases = report.get("cases", [])
    lifecycle = report.get("lifecycle", [])
    if (not cases or cases != list(range(1, len(cases) + 1)) or len(cases) >= 22 or
            report.get("isolation") is not True or
            not {"location", "puzzle-1", "puzzle-2"}.issubset(lifecycle)):
        raise ValueError("resume requires contiguous completed cases and verified lifecycle/isolation")
    return len(cases) + 1, list(cases), list(lifecycle)


class Bot:
    def __init__(self, args):
        self.args = args
        for source in (args.package, args.save):
            if not source.is_file():
                raise ValueError("input must be an existing private file")
            guard_destination(source, args.out)
        self.out = args.out.resolve()
        self.out.mkdir(parents=True, exist_ok=False)
        self.started = time.monotonic()
        self.sampled = 0
        self.completed = []
        self.lifecycle = []

    def adb(self, *args, binary=False):
        result = subprocess.run([self.args.adb, "-s", self.args.serial, *args],
                                capture_output=True, timeout=90, check=True)
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

    def shell(self, *args):
        return self.adb("shell", " ".join(shlex.quote(str(x)) for x in args))

    def event(self, kind, **data):
        row = {"seconds": round(time.monotonic() - self.started, 2), "event": kind, **data}
        with (self.out / "events.jsonl").open("a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
        print(json.dumps(row), flush=True)

    def bridge(self, command):
        answer = self.shell("am", "broadcast", "-a", "org.rigorcore.caserecomp.DIRECTOR_DEBUG", "--es", "cmd", command)
        match = re.search(r'data="(.*)"\s*$', answer, re.S)
        if not match or match[1].startswith("error:"):
            raise RuntimeError("bridge: " + answer[-500:])
        return match[1].replace("\\n", "\n")

    def prop(self, name, field):
        return json.loads(self.bridge(f"propjson|global:{name}|{field}"))

    def batch(self, commands):
        return json.loads(self.bridge("batchjson|" + json.dumps(commands)))

    def call(self, target, method, *args):
        return json.loads(self.bridge("|".join(["calljson", target, method, *map(str, args)])))

    def label(self):
        return self.bridge("label").split(" ", 1)[1].lower()

    def wait(self, condition, seconds=120):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            try:
                if condition():
                    return
            except RuntimeError:
                pass  # Receiver absent while the asynchronous Activity loads.
            self.sleep(.4)
        raise TimeoutError("condition not reached; last label=" + self.label())

    def sleep(self, seconds):
        time.sleep(seconds)
        if time.monotonic() - self.sampled > 60:
            self.sampled = time.monotonic()
            tag = str(int(self.sampled - self.started))
            for service in ("meminfo", "gfxinfo"):
                (self.out / f"{service}-{tag}.txt").write_text(self.shell("dumpsys", service, PKG), encoding="utf-8")
            if not self.shell("pidof", PKG).strip():
                raise RuntimeError("app process disappeared")

    def gameplay_wait(self, seconds):
        # Lifecycle persistence and endurance sampling always keep real-time waits.
        self.sleep(seconds / self.args.speed)

    def touch(self, x, y):
        for action in ("move", "down", "up"):
            self.bridge(f"touch|{action}|{x}|{y}")
            time.sleep(.12)

    def click(self, channel):
        hit = self.bridge(f"hit|{channel}")
        if hit == "none":
            # Timed native hints can open between inventory queries and the touch.
            self.dialog()
            hit = self.bridge(f"hit|{channel}")
        if hit == "none":
            raise UnreachableSprite(f"sprite {channel} has no reachable hit")
        self.touch(*map(int, hit.split()))

    def named(self, name):
        for row in self.bridge("sprites|1|600").splitlines():
            parts = row.split("|")
            if parts[1] in (name, name[:-1] + "B"):
                self.click(int(parts[0])); return
        raise RuntimeError("no button " + name)

    def dialog(self):
        if self.call("global:gGameManager", "getBoolDialogUp"):
            point = self.bridge("flashbutton|572")
            if point != "none":
                self.touch(*map(int, point.split())); self.sleep(2)

    def capture(self, name):
        (self.out / (name + ".png")).write_bytes(self.adb("exec-out", "screencap", "-p", binary=True))

    def launch(self, initial=False):
        if initial:
            # A visible debug Activity may still hold the previous test session.
            # Recreate it so onCreate consumes the newly imported seed.
            self.shell("am", "force-stop", PKG)
        args = ["am", "start", "-n", ACT, "--es", "title_id", "prime_suspects", "--ez", "play", "true"]
        if initial:
            args += ["--es", "import_external", "qa-prime.zip", "--es", "import_save", "qa-prime.json"]
        self.shell(*args)
        if initial:
            self.wait(lambda: self.label() == "splash2")
            self.touch(400, 300)
            self.wait(lambda: self.label() == "mainmenu")
        else:
            # A checkpoint resumes directly; do not click through its boot transition.
            self.wait(lambda: self.label() in ("mainmenu", "map", "casebook") or
                      self.label().startswith(("loc", "metagame")))
            self.sleep(2)

    def inventory(self):
        return self.prop("gUserData", "m_userDataArray")

    def resume_game(self):
        if self.label().startswith(("loc", "metagame")):
            return
        if self.label() == "map":
            self.sleep(2)
            return
        if self.label() == "mainmenu":
            self.named("main_startA")
            self.wait(lambda: self.label() in ("casebook", "map"))
        if self.label() == "casebook":
            self.sleep(2); self.touch(551, 476)
            self.wait(lambda: self.label() == "map")
        self.sleep(2)

    def checkpoint(self, kind, board=None):
        # Allow periodic save, then let the actual Activity pause persist it too.
        self.sleep(6)
        before = self.inventory()
        pieces = self.prop(board, "m_gameBoardArray") if board else None
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.sleep(2)
        self.shell("am", "force-stop", PKG)
        self.launch()
        # Native clocks advance during lifecycle callbacks; compare inventory rows.
        if self.inventory()[:-4] != before[:-4]:
            raise AssertionError("inventory changed after process restart")
        self.resume_game()
        if board:
            self.search()
            self.dialog()
            if self.prop(board, "m_gameBoardArray") != pieces:
                raise AssertionError("partial board changed after process restart")
        self.lifecycle.append(kind)
        self.event("restart_verified", checkpoint=kind)

    def search(self):
        deferred = set()
        for _ in range(40):
            label = self.label()
            if label.startswith("metagame"):
                return
            self.dialog()
            if label.startswith("loc"):
                sprites = self.bridge("sprites|158|158")
                if "battery" in sprites:
                    self.click(158); self.sleep(2)
                loc = self.prop("gLocationManager", "m_locationNum")
                rows = self.prop("gLocationManager", "m_userDataArray")
                for entry in rows:
                    if not isinstance(entry, list):
                        continue
                    data = self.call("global:gItemManager", "getItemData", loc, entry[0])
                    channels = data[-1] if isinstance(data[-1], list) else [data[-1]]
                    flags = entry[1] if isinstance(entry[1], list) else [entry[1]]
                    for channel, flag in zip(channels, flags):
                        if flag == 0:
                            try:
                                self.click(channel)
                            except UnreachableSprite:
                                deferred.add(loc)
                                self.event("item_inaccessible", location=loc, sprite=channel)
                                continue
                            self.wait(lambda: not self.call("global:gLocationManager", "getBoolGrabInProgress"), seconds=30)
                            if "location" not in self.lifecycle:
                                self.checkpoint("location")
                                return self.search()
                self.gameplay_wait(4)
                # Only inaccessible locations are deferred; missed touches can be retried.
                self.dialog()
                if self.label().startswith("loc"):
                    self.named("mapButtonA")
                    self.wait(lambda: self.label() == "map")
            elif label == "map":
                pending = [row[0] for row in self.inventory() if isinstance(row, list)
                           and any(isinstance(item, list) and (0 in item[1] if isinstance(item[1], list) else item[1] == 0) for item in row[1:])]
                battery = self.call("global:gUserData", "getPuzzleData", "#batteryArray")
                channels = list(range(30, 115, 3))
                values = self.batch([f"calljson|sprite:{n}|getLocNum" for n in channels])
                choices = list(zip(channels, map(json.loads, values)))
                choices.sort(key=lambda pair: pair[1] != (battery[0] if battery[1] == 0 else None))
                channel = next((n for n, loc in choices if loc in pending and loc not in deferred), 127)
                self.click(channel); self.gameplay_wait(5)
            else:
                raise RuntimeError("unexpected search scene " + label)
        raise RuntimeError("search did not reach original puzzle")

    def puzzle(self, partial=False):
        label = self.label()
        board = "gMemoryGameBoard" if label == "metagame2" else "gMetaGameBoard"
        for step in range(100):
            if self.call("global:" + board, "getPuzzleCompleted") == 1:
                return board
            self.dialog()
            if board == "gMemoryGameBoard":
                data = self.prop(board, "m_gameBoardArray")
                selected = self.prop(board, "m_selectedTilesArray")
                pairs = {}
                for i, value in enumerate(data):
                    if value != 0:
                        pairs.setdefault(value, []).append(i + 70)
                groups = sorted(pairs.values(), key=lambda group: not any(n in selected for n in group))
                pair = groups[0][:2]
                if len(pair) != 2:
                    raise RuntimeError("unpaired original memory tiles")
                for n in pair:
                    if n not in self.prop(board, "m_selectedTilesArray"):
                        self.click(n); self.gameplay_wait(.9)
                self.gameplay_wait(3)
                self.wait(lambda: not self.prop(board, "m_boolHold"), seconds=30)
            else:
                if self.call("global:gHintManager", "getBoolHintTutorial"):
                    pair = (64, 68)
                else:
                    rows = self.bridge("sprites|63|104").splitlines()
                    names = {int(row.split("|")[0]): row.split("|")[1] for row in rows}
                    channels = sorted(names)  # Original solved tiles leave the stage.
                    commands = [command for n in channels for command in
                                (f"calljson|sprite:{n}|getBoardIndex",
                                 f"calljson|global:{board}|getTileNumFromString|{names[n]}")]
                    values = list(map(json.loads, self.batch(commands)))
                    tiles = [(n, values[i * 2], values[i * 2 + 1]) for i, n in enumerate(channels)]
                    wrong = next((tile for tile in tiles if tile[2] and tile[1] != tile[2]), None)
                    if wrong is None:
                        raise RuntimeError("no legal swap but original puzzle incomplete")
                    pair = (wrong[0], next(n for n, index, _ in tiles if index == wrong[2]))
                for n in pair:
                    self.click(n); self.sleep(.15)
                self.gameplay_wait(2.5)
                self.wait(lambda: not self.call("global:" + board, "getTileSelectedArray"), seconds=30)
            if partial:
                return board
        raise RuntimeError("puzzle did not complete")

    def protected(self):
        records = {}
        for filename in ("case-recomp-director-user-data.xml", "case-recomp-director.xml"):
            raw = self.shell("run-as", PKG, "cat", "shared_prefs/" + filename)
            if raw.startswith("cat:") or not raw.strip():
                continue
            for node in ET.fromstring(raw):
                key = node.attrib.get("name", "")
                if not key.startswith(PREFIX) and key != "active/prime_suspects":
                    records[filename + "/" + key] = ET.tostring(node, encoding="unicode")
        return records

    def run(self):
        self.adb("get-state")
        before = self.protected()
        self.protected_before = before
        remote = f"/sdcard/Android/data/{PKG}/files/import"
        self.shell("mkdir", "-p", remote)
        self.adb("push", str(self.args.package.resolve()), remote + "/qa-prime.zip")
        self.adb("push", str(self.args.save.resolve()), remote + "/qa-prime.json")
        start_case = 1
        resume = getattr(self.args, "resume_report", None)
        if resume:
            prior = json.loads(resume.read_text(encoding="utf-8"))
            start_case, self.completed, self.lifecycle = resume_progress(prior)
            self.event("resume_prior_run", report=str(resume.resolve()), cases=self.completed)
            self.shell("input", "keyevent", "KEYCODE_HOME")
            self.sleep(2)
            self.shell("am", "force-stop", PKG)
        self.launch(initial=resume is None)
        if self.call("global:gUserData", "getPlayerRoundNum") != start_case:
            raise RuntimeError("saved round does not match expected case " + str(start_case))
        self.resume_game()
        for case in range(start_case, 23):
            self.event("case_start", case=case)
            self.search()
            self.dialog()
            if case <= 2:
                board = self.puzzle(partial=True)
                self.checkpoint("puzzle-" + str(case), board)
            if case < 22:
                self.puzzle()
                self.completed.append(case)
                self.capture(f"case-{case}-complete")
                self.wait(lambda: "briefNextA" in self.bridge("sprites|1|600") or
                          "briefNextB" in self.bridge("sprites|1|600"), seconds=90)
                self.named("briefNextA")
                self.wait(lambda: self.call("global:gUserData", "getPlayerRoundNum") == case + 1)
                self.wait(lambda: self.label() == "casebook")
                self.touch(551, 476); self.wait(lambda: self.label() == "map")
            else:
                links = self.prop("gGemBoard", "m_connectionArray")
                def solve(mask, moves):
                    if len(moves) == len(links) - 1:
                        return moves
                    for source, targets in enumerate(links):
                        if mask & (1 << source):
                            continue
                        for target in targets:
                            target -= 1
                            if not mask & (1 << target):
                                result = solve(mask | (1 << target), moves + [(source, target)])
                                if result is not None:
                                    return result
                    return None
                moves = solve(0, [])
                if moves is None:
                    raise RuntimeError("no legal path through original gem graph")
                for source, target in moves:
                    self.click(source + 19); self.sleep(.1); self.click(target + 19)
                    self.wait(lambda: self.prop("gGemBoard", "m_state") != "sliding", seconds=30)
                self.wait(lambda: self.prop("gGemBoard", "m_state") == "endAnim")
                self.sleep(17); self.dialog(); self.sleep(10)
                if self.call("global:gUserData", "getPlayerRoundNum") != 23:
                    raise AssertionError("original ending not reached")
                self.completed.append(22); self.capture("ending")
        while time.monotonic() - self.started < self.args.soak_seconds:
            self.sleep(30)
        if self.protected() != before:
            raise AssertionError("another title's settings/save changed")
        self.shell("am", "start", "-n", PKG + "/org.rigorcore.caserecomp.app.HomeActivity")
        self.sleep(3); self.capture("catalogue")
        xml = ""
        cover = False
        for _ in range(8):
            self.shell("uiautomator", "dump", "/sdcard/qa-prime-catalogue.xml")
            xml = self.shell("cat", "/sdcard/qa-prime-catalogue.xml")
            root = ET.fromstring(xml)
            cover = any(node.get("class") == "android.widget.ImageView" and
                        node.get("content-desc") == "Prime Suspects" for node in root.iter("node"))
            if cover:
                break
            self.shell("input", "swipe", "400", "650", "400", "200", "500")
            self.sleep(1)
        (self.out / "catalogue.xml").write_text(xml, encoding="utf-8")
        self.capture("catalogue-cover")
        if not cover:
            raise AssertionError("imported Prime cover ImageView not visible")
        if set(self.lifecycle) != {"location", "puzzle-1", "puzzle-2"}:
            raise AssertionError("required lifecycle checkpoints missing")
        if self.completed != list(range(1, 23)):
            raise AssertionError("not all original cases completed")
        self.event("finished", cases=self.completed, lifecycle=self.lifecycle, isolation=True, cover=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--save", type=Path, required=True, help="owned case-1 seed; copied only at initial launch")
    parser.add_argument("--out", type=Path, required=True, help="new PRIVATE output directory")
    parser.add_argument("--soak-seconds", type=int, default=3600)
    parser.add_argument("--speed", type=float, default=2, help="gameplay wait divisor (1..4); lifecycle and soak stay real-time")
    parser.add_argument("--resume-report", type=Path, help="continue current native save, carrying verified cases from a prior local result")
    args = parser.parse_args()
    if not 1 <= args.speed <= 4:
        parser.error("--speed must be between 1 and 4")
    bot = Bot(args)
    result = {"passed": False}
    try:
        bot.run(); result["passed"] = True
    except Exception as error:
        result["error"] = str(error)
        bot.event("failed", error=str(error))
        try:
            bot.capture("failure")
        except Exception:
            pass
    finally:
        if args.resume_report:
            result["prior_report"] = str(args.resume_report.resolve())
            if args.resume_report.is_file():
                result["prior_report_sha256"] = hashlib.sha256(args.resume_report.read_bytes()).hexdigest()
        try:
            if hasattr(bot, "protected_before"):
                result["isolation"] = bot.protected() == bot.protected_before
                if not result["isolation"]:
                    result["passed"] = False
        except Exception as error:
            result["isolation_error"] = str(error)
            result["passed"] = False
        samples = []
        for path in bot.out.glob("meminfo-*.txt"):
            text = path.read_text(encoding="utf-8")
            match = re.search(r"TOTAL PSS:\s*(\d+)", text) or re.search(r"^\s*TOTAL\s+(\d+)", text, re.M)
            if match:
                samples.append(int(match[1]))
        result.update(cases=bot.completed, lifecycle=bot.lifecycle, seconds=time.monotonic() - bot.started,
                      memory_samples=len(samples), peak_pss_kb=max(samples, default=None),
                      performance_acceptance="measured; no device-specific frame/memory target asserted")
        (bot.out / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        try:
            (bot.out / "logcat.txt").write_text(bot.shell("logcat", "-d", "-t", "3000"), encoding="utf-8")
        except Exception as error:
            result["logcat_error"] = str(error)
            (bot.out / "result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
