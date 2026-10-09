"""Playable experimental campaign shell; does not execute the original EXE."""
import argparse
from pathlib import Path
import time
from campaign import Session
from runtime import Resources
from progress import progress_path, write_state, read_state


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", required=True)
    parser.add_argument("--save", required=True)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--seed", type=int, default=8)
    args = parser.parse_args()
    progress_path(args.save)
    resources = Resources(args.resources)
    session = Session.restore(resources, read_state(args.save)) if args.resume else None
    import tkinter as tk
    from PIL import ImageTk
    window = tk.Tk()
    window.title("Mystery P.I. - experimental campaign")
    status = tk.StringVar(value="Experimental campaign shell - original dialogs and bonus rounds pending")
    tk.Label(window, textvariable=status).pack()
    controls = tk.Frame(window)
    controls.pack()
    canvas = tk.Canvas(window, width=800, height=600, highlightthickness=0)
    canvas.pack()
    image_id = canvas.create_image(0, 0, anchor="nw")
    last, last_save = time.monotonic(), time.monotonic()
    photo = None

    def persist():
        nonlocal last_save
        if session:
            write_state(session.state(), args.save)
        last_save = time.monotonic()

    def rebuild():
        for button in controls.winfo_children():
            button.destroy()
        if session is None:
            tk.Button(controls, text="Nueva partida experimental", command=start).pack(side=tk.LEFT)
        elif session.phase == "map":
            for name in session.level.scenes:
                tk.Button(controls, text=name, command=lambda scene=name: enter(scene)).pack(side=tk.LEFT)
        elif session.phase == "scene":
            tk.Button(controls, text="Elegir escena", command=to_map).pack(side=tk.LEFT)
        else:
            tk.Label(controls, text="Objetivos completados; bonus pendiente" if session.phase == "objects_complete"
                     else "Tiempo agotado").pack(side=tk.LEFT)
        if session:
            tk.Button(controls, text="Guardar progreso", command=persist).pack(side=tk.LEFT)

    def start():
        nonlocal session
        session = Session(resources, args.seed)
        rebuild()
        persist()

    def enter(name):
        nonlocal last
        session.enter(name)
        last = time.monotonic()
        rebuild()
        persist()

    def to_map():
        session.to_map()
        rebuild()
        canvas.itemconfigure(image_id, image="")
        persist()

    def click(event):
        nonlocal last
        if session:
            now = time.monotonic()
            previous = session.phase
            session.advance(now - last)
            last = now
            session.click(event.x, event.y)
            if session.phase != previous:
                rebuild()
            persist()

    def draw():
        nonlocal last, photo
        now = time.monotonic()
        if session:
            previous = session.phase
            session.advance(now - last)
            if session.phase != previous:
                rebuild()
                persist()
            status.set(f"Nivel {session.level.clue} | {session.clock.text()} | "
                       f"Objetivos: {session.remaining} | Puntos: {session.points} | {session.phase}")
            if session.scene is not None:
                photo = ImageTk.PhotoImage(session.scene.render(True))
                canvas.itemconfigure(image_id, image=photo)
            if now - last_save >= 5:
                persist()
        last = now
        window.after(40, draw)

    def close():
        persist()
        window.destroy()

    window.protocol("WM_DELETE_WINDOW", close)
    canvas.bind("<Button-1>", click)
    rebuild()
    draw()
    window.mainloop()


if __name__ == "__main__":
    main()
