"""Hidden-Tk end-to-end prototype smoke check; never runs the original EXE.

Invokes registered Tk callbacks with a controlled clock. Geometry is read from
a separate save-restored scene, but every scored click uses the actual canvas UI.
No OS mouse/keyboard input, native code loading or native player files are used.
"""
import argparse
import json
from pathlib import Path
import re
import sys
import tempfile
import tkinter as tk
from unittest.mock import patch
import campaign_preview
from map_view import MapView
from progress import progress_path, read_state
from runtime import Resources
from startup import Startup


class Driver:
    def __init__(self, root, resources, save):
        self.root, self.resources, self.save = root, resources, save
        self.now = 1000000.0
        self.frames = self.clicks = 0

    def widgets(self, parent=None):
        for widget in (parent or self.root).winfo_children():
            yield widget
            yield from self.widgets(widget)

    def button(self, caption):
        return next(widget for widget in self.widgets()
                    if isinstance(widget, tk.Button) and widget.cget("text") == caption)

    def click(self, x, y):
        canvas = next(widget for widget in self.widgets() if isinstance(widget, tk.Canvas))
        for binding, kind in (("<Motion>", "6"), ("<Button-1>", "4"), ("<ButtonRelease-1>", "5")):
            command = re.search(r"\[([^\s]+)", canvas.bind(binding)).group(1)
            fields = ["0"] * 19
            fields[8], fields[9], fields[14], fields[15] = str(x), str(y), str(canvas), kind
            canvas.tk.call(command, *fields)
        self.clicks += 1

    def frame(self):
        # Cancel the timer at Tcl level, keeping its registered Python command.
        # Invoke the actual after wrapper: it draws, schedules the next frame and
        # unregisters itself. No copied GUI logic or direct Session mutation.
        for identity in self.root.tk.call("after", "info"):
            script, _kind = self.root.tk.call("after", "info", identity)
            command = self.root.tk.splitlist(script)[0]
            if command.endswith("draw"):
                self.root.tk.call("after", "cancel", identity)
                self.now += .04
                self.root.tk.call(command)
                self.frames += 1
                return
        raise AssertionError("missing live prototype draw callback")

    def state(self):
        return read_state(self.save)

    def flow(self):
        return Startup.restore(self.resources, self.state())

    def scene_card(self, name):
        flow = self.flow()
        card = next(card for card in MapView(self.resources, flow.session.level).cards if card.name == name)
        self.click(card.x + 20, card.y + 20)
        self.frame()
        assert self.state()["campaign"]["current"] == name

    def object_clicks(self, sets):
        oracle = self.flow().session.scene
        hits = 0
        for ids in sets:
            for identity in ids:
                sprite = oracle.objects[identity]
                if sprite.found:
                    continue
                position = None
                for y in range(max(0, sprite.y), min(600, sprite.y + sprite.image.height)):
                    for x in range(max(174, sprite.x), min(800, sprite.x + sprite.image.width)):
                        first = next((candidate for candidate in oracle.targets if oracle.objects[candidate].hit(x, y)), None)
                        if first == identity:
                            position = (x, y)
                            break
                    if position:
                        break
                assert position, "no exposed GUI hit pixel for " + identity
                self.click(*position)
                saved = self.state()["campaign"]
                assert saved["scenes"][saved["current"]]["objects"][identity]["found"]
                # Mark only the separate geometry copy to find later pixels.
                sprite.found = True
                hits += 1
                self.frame()
        return hits

    def until(self, predicate, limit=400):
        for _ in range(limit):
            self.frame()
            if predicate():
                return
        raise AssertionError("GUI did not reach the required boundary")

    def close(self):
        self.root.tk.call(self.root.protocol("WM_DELETE_WINDOW"))


def run_window(resources, save, resume, action):
    root = tk.Tk()
    root.withdraw()
    driver = Driver(root, resources, save)
    errors = []

    def failure(kind, error, traceback):
        errors.append(str(error))
        root.destroy()

    def exercise():
        assert root.state() == "withdrawn" and not root.winfo_ismapped()
        action(driver)
        driver.close()

    root.report_callback_exception = failure
    root.after(100, exercise)
    argv = ["campaign_preview.py", "--resources", str(resources.path), "--save", str(save), "--player-startup"]
    if resume:
        argv.append("--resume")
    try:
        with patch.object(tk, "Tk", return_value=root), patch.object(sys, "argv", argv), \
                patch.object(campaign_preview.time, "monotonic", side_effect=lambda: driver.now):
            campaign_preview.main()
    finally:
        if root.tk.call("info", "commands", str(root)):
            root.destroy()
    assert not errors, errors
    return {"frames": driver.frames, "canvas_clicks": driver.clicks}


def check(resources, save):
    report = {"native_executed": False, "window_withdrawn": True, "controlled_clock": True}

    def first(driver):
        driver.click(300, 330)
        driver.frame()
        entry = next(widget for widget in driver.widgets() if isinstance(widget, tk.Entry))
        driver.root.setvar(entry.cget("textvariable"), " Dream ")
        driver.button("Crear perfil").invoke()
        state = driver.state()
        assert state["player"]["name"] == "Dream" and state["campaign"] is None
        driver.click(300, 330)
        driver.frame()
        driver.scene_card("vault")
        report["first_batch_hits"] = driver.object_clicks(driver.flow().session.scene.active_sets)
        driver.until(lambda: driver.state()["campaign"]["phase"] == "scene_complete")
        assert driver.state()["campaign"]["completed"] == 10
        report["first_batch_sets"] = 10
        report["modal_saved"] = driver.state()

    report["first_window"] = run_window(resources, save, False, first)
    assert read_state(save) == report["modal_saved"]

    def second(driver):
        assert driver.state() == report["modal_saved"]
        driver.button("OK").invoke()
        state = driver.state()
        assert state["campaign"]["phase"] == "map"
        assert state["campaign"]["clock"] == report["modal_saved"]["campaign"]["clock"]
        driver.scene_card("slots")
        before = driver.state()
        assert before["campaign"]["points"] == report["modal_saved"]["campaign"]["points"]
        report["second_batch_hits"] = driver.object_clicks(driver.flow().session.scene.active_sets[:1])
        driver.until(lambda: driver.state()["campaign"]["completed"] == 11)
        state = driver.state()
        assert state["campaign"]["points"] > before["campaign"]["points"]
        report["continued_saved"] = state

    report["second_window"] = run_window(resources, save, True, second)
    assert read_state(save) == report["continued_saved"]

    def third(driver):
        assert driver.state() == report["continued_saved"]
        flow = driver.flow()
        assert flow.player.name == "Dream" and flow.session.current == "slots"
        assert flow.session.completed == 11 and flow.session.phase == "scene"

    report["third_window"] = run_window(resources, save, True, third)
    state = read_state(save)
    assert state == report.pop("continued_saved")
    report.pop("modal_saved")
    report.update(player=state["player"], completed_sets=state["campaign"]["completed"],
                  points=state["campaign"]["points"], elapsed=state["campaign"]["clock"]["elapsed"],
                  current=state["campaign"]["current"], all_restorations_equal=True)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", required=True)
    parser.add_argument("--output", required=True, help="own report JSON under local-output/sda-prototype")
    args = parser.parse_args()
    output = progress_path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    resources = Resources(args.resources)
    # Dedicated disposable saves; never overwrite an existing user's progress.
    with tempfile.TemporaryDirectory(prefix="gui-smoke-", dir=output.parent) as directory:
        report = check(resources, Path(directory) / "player.json")
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
