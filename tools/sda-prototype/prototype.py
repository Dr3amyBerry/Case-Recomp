"""Render a private scene or open the experimental scene interaction window."""
import argparse
import hashlib
import json
from pathlib import Path
import time
from runtime import Resources, Scene


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", required=True)
    parser.add_argument("--scene", required=True)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--targets", help="comma-separated sets; join compound object IDs with +")
    selection.add_argument("--seed", type=int, help="replay native first batch using a supplied clock seed")
    parser.add_argument("--target-list", action="store_true", help="draw original atlas target rows")
    parser.add_argument("--render")
    parser.add_argument("--interactive", action="store_true")
    args = parser.parse_args()
    if not args.render and not args.interactive:
        parser.error("choose --render or --interactive")
    if args.render:
        root = Path(__file__).resolve().parents[2]
        resolved = Path(args.render).resolve()
        if not any(resolved.is_relative_to(root / folder) for folder in ("private", "local-output")):
            parser.error("render output must be inside private/ or local-output/")
    targets = [tuple(item.split("+")) for item in args.targets.split(",")] if args.targets else None
    scene = Scene(Resources(args.resources), args.scene, targets, args.seed)
    if args.render:
        out = Path(args.render)
        out.parent.mkdir(parents=True, exist_ok=True)
        scene.render(args.target_list).save(out)
        out.with_suffix(".json").write_text(json.dumps({
            "resources_sha256": hashlib.sha256(Path(args.resources).read_bytes()).hexdigest(),
            "scene": args.scene, "targets": scene.active_sets, "objects": len(scene.objects), "seed": args.seed,
            "mode": "experimental supplied candidate pool; native history filtering/campaign not implemented",
        }, indent=2), encoding="utf-8")
    if not args.interactive:
        return
    import tkinter as tk
    from PIL import ImageTk
    window = tk.Tk()
    window.title("Vegas - experimental SDA scene")
    status = tk.StringVar()
    tk.Label(window, textvariable=status).pack()
    canvas = tk.Canvas(window, width=800, height=600, highlightthickness=0)
    canvas.pack()
    image_id = canvas.create_image(0, 0, anchor="nw")
    last = time.monotonic()
    photo = None

    def draw():
        nonlocal last, photo
        now = time.monotonic()
        scene.advance(now - last)
        last = now
        photo = ImageTk.PhotoImage(scene.render(args.target_list))
        canvas.itemconfigure(image_id, image=photo)
        remaining = scene.remaining_captions()
        status.set(f"Points: {scene.score.points} | Targets: " + ", ".join(remaining))
        window.after(50, draw)

    def click(event):
        nonlocal last
        now = time.monotonic()
        scene.advance(now - last)
        last = now
        print(json.dumps(scene.click(event.x, event.y)), flush=True)

    canvas.bind("<Button-1>", click)
    tk.Button(window, text="Hint penalty (-7500), research control", command=scene.score.hint).pack()
    draw()
    window.mainloop()


if __name__ == "__main__":
    main()
