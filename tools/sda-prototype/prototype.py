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
    selection.add_argument("--resume", action="store_true", help="resume experimental progress from --save")
    parser.add_argument("--save", help="experimental progress JSON inside local-output/sda-prototype/")
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
    resources = Resources(args.resources)
    if args.resume:
        if not args.save:
            parser.error("--resume requires --save")
        from progress import load
        scene = load(resources, args.save)
        if scene.name != args.scene:
            parser.error("saved scene differs from --scene")
    else:
        scene = Scene(resources, args.scene, targets, args.seed)
    if args.save:
        from progress import progress_path
        progress_path(args.save)
    if args.render:
        out = Path(args.render)
        out.parent.mkdir(parents=True, exist_ok=True)
        scene.render(args.target_list).save(out)
        out.with_suffix(".json").write_text(json.dumps({
            "resources_sha256": hashlib.sha256(Path(args.resources).read_bytes()).hexdigest(),
            "scene": args.scene, "targets": scene.active_sets, "objects": len(scene.objects), "seed": args.seed,
            "mode": "experimental scene progress; native campaign/player format not implemented",
        }, indent=2), encoding="utf-8")
    if not args.interactive:
        if args.save and not args.resume:
            from progress import save
            save(scene, args.save)
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
    last_save = last
    photo = None

    def persist():
        nonlocal last_save
        if args.save:
            from progress import save
            save(scene, args.save)
        last_save = time.monotonic()

    def next_batch():
        scene.next_batch(int(time.monotonic() * 1000) & 0xffffffff)
        persist()

    def close():
        persist()
        window.destroy()

    window.protocol("WM_DELETE_WINDOW", close)

    def draw():
        nonlocal last, photo
        now = time.monotonic()
        scene.advance(now - last)
        last = now
        photo = ImageTk.PhotoImage(scene.render(args.target_list))
        canvas.itemconfigure(image_id, image=photo)
        remaining = scene.remaining_captions()
        status.set(f"Points: {scene.score.points} | Targets: " + ", ".join(remaining))
        ready = scene.deck is not None and scene.batch_retired
        next_button.configure(state=tk.NORMAL if ready else tk.DISABLED)
        if args.save and now - last_save >= 5:
            persist()
        window.after(50, draw)

    def click(event):
        nonlocal last
        now = time.monotonic()
        scene.advance(now - last)
        last = now
        print(json.dumps(scene.click(event.x, event.y)), flush=True)
        persist()

    canvas.bind("<Button-1>", click)
    def hint():
        scene.score.hint()
        persist()

    tk.Button(window, text="Hint penalty (-7500), research control", command=hint).pack()
    next_button = tk.Button(window, text="Siguiente tanda", command=next_batch, state=tk.DISABLED)
    next_button.pack()
    if args.save:
        tk.Button(window, text="Guardar progreso", command=persist).pack()
        persist()
    draw()
    window.mainloop()


if __name__ == "__main__":
    main()
