"""Playable experimental campaign shell; does not execute the original EXE."""
import argparse
import time
from campaign import Session
from map_view import MapView
from pda_view import PdaView
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
    map_view = MapView(resources, session.level) if session else None
    pda_view = PdaView(resources)
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
            tk.Label(controls, text="Elige una tarjeta del mapa").pack(side=tk.LEFT)
        elif session.phase == "scene":
            tk.Label(controls, text="Usa el botón del PDA para volver al mapa").pack(side=tk.LEFT)
        else:
            tk.Label(controls, text="Objetivos completados; bonus pendiente" if session.phase == "objects_complete"
                     else "Tiempo agotado").pack(side=tk.LEFT)
        if session:
            tk.Button(controls, text="Guardar progreso", command=persist).pack(side=tk.LEFT)

    def start():
        nonlocal session, map_view
        session = Session(resources, args.seed)
        map_view = MapView(resources, session.level)
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
        persist()

    def click(event):
        nonlocal last
        if session:
            pda_view.pointer("down", event.x, event.y)
            if pda_view.owns(event.x, event.y):
                return
        if session and session.phase == "map":
            map_view.pointer("down", event.x, event.y)
            return
        if session:
            now = time.monotonic()
            previous = session.phase
            session.advance(now - last)
            last = now
            session.click(event.x, event.y)
            if session.phase != previous:
                rebuild()
            persist()

    def pointer(event, kind):
        if session:
            pda_view.pointer(kind, event.x, event.y, bool(event.state & 0x100))
        if session and session.phase == "map":
            map_view.pointer(kind, event.x, event.y, bool(event.state & 0x100))

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
            if session.phase == "map":
                photo = ImageTk.PhotoImage(pda_view.render(map_view.render(session), session))
                canvas.itemconfigure(image_id, image=photo)
                action = map_view.consume_activation()
                if action:
                    enter(action["scene"])
            elif session.scene is not None:
                stage = pda_view.render(session.scene.render(False), session)
                session.scene.draw_target_list(stage)
                photo = ImageTk.PhotoImage(stage)
                canvas.itemconfigure(image_id, image=photo)
            action = pda_view.consume_activation()
            if action == 301 and session.phase == "scene":
                to_map()
            if now - last_save >= 5:
                persist()
        last = now
        window.after(40, draw)

    def close():
        persist()
        window.destroy()

    window.protocol("WM_DELETE_WINDOW", close)
    canvas.bind("<Button-1>", click)
    canvas.bind("<ButtonRelease-1>", lambda event: pointer(event, "up"))
    canvas.bind("<Motion>", lambda event: pointer(event, "move"))
    canvas.bind("<B1-Motion>", lambda event: pointer(event, "move"))
    rebuild()
    draw()
    window.mainloop()


if __name__ == "__main__":
    main()
