"""Generate canonical deterministic trace from the Python SDA prototype for differential testing against Kotlin."""
import json
import sys
from pathlib import Path

# Add prototype to path
root = Path(__file__).resolve().parent
sys.path.insert(0, str(root))

from selection import NativeRandom, native_shuffle, TargetDeck
from runtime import Score, Sprite, Scene
from clock import LevelClock
from motion import FoundMotion, native_round
from target_rows import TargetRow
from history import HistoryMark, replay_history, prune_scene_history
from test_selection import FixtureResources


def generate_trace():
    trace = {}

    # 1. RNG traces
    rng_traces = {}
    for seed in [1, 42, 1337, 0x7fffffff, 0xffffffff]:
        rng = NativeRandom(seed)
        rng_traces[str(seed)] = [rng.next() for _ in range(10)]
    trace["rng"] = rng_traces

    # 2. Shuffle traces
    shuffle_traces = []
    for seed, start in [(1, 0), (1, 4), (1337, 0), (1337, 5)]:
        items = list(range(20))
        shuffled = native_shuffle(items, seed, start=start)
        shuffle_traces.append({
            "seed": seed,
            "start": start,
            "result": shuffled,
        })
    trace["shuffle"] = shuffle_traces

    # 3. TargetDeck traces
    deck = TargetDeck(list(range(25)))
    b1 = deck.next_batch(42)
    c1 = deck.cursor
    b2 = deck.next_batch(99)
    c2 = deck.cursor
    trace["deck"] = {
        "batch1": list(b1),
        "cursor1": c1,
        "batch2": list(b2),
        "cursor2": c2,
        "order": list(deck.order),
    }

    # 4. Score traces
    score = Score(points=10000)
    score_events = []
    for fast in [True, True, True, False, True, True]:
        gain = score.found(fast)
        score_events.append({"action": "found", "fast": fast, "gain": gain, "points": score.points})
    for ms in [0, 100, 200, 300, 400, 500, 2600, 2700]:
        penalized = score.miss(ms)
        score_events.append({"action": "miss", "ms": ms, "penalized": penalized, "points": score.points})
    score.hint()
    score_events.append({"action": "hint", "points": score.points})
    trace["score"] = score_events

    # 5. Clock traces
    clock = LevelClock(limit=1320.0, elapsed=1130.0)
    clock_events = []
    for step in [5.0, 4.0, 1.0, 60.0, 110.0, 10.0]:
        events = clock.advance(step)
        clock_events.append({
            "step": step,
            "events": list(events),
            "displaySeconds": clock.display_seconds(),
            "text": clock.text(),
            "elapsed": clock.elapsed,
        })
    trace["clock"] = clock_events

    # 6. Motion traces
    motion = FoundMotion(original_x=20, original_y=200, width=8, height=8)
    motion_samples = []
    for frame in range(120):
        motion.update(0.04)
        if frame in [0, 5, 10, 15, 20, 25, 30, 40, 50, 60, 80, 100]:
            motion_samples.append({
                "frame": frame,
                "x": motion.x,
                "y": motion.y,
                "drawWidth": motion.draw_width,
                "drawHeight": motion.draw_height,
                "scale": round(motion.scale, 4),
                "phase": motion.phase,
                "pulses": motion.pulses,
                "removed": motion.removed,
            })
    trace["motion"] = motion_samples

    # 7. Scene traces
    scene = Scene(FixtureResources(), "SCENE_FIXTURE.MSL", [("a", "b")])
    scene_steps = []
    scene_steps.append({"step": "initial", "remaining": scene.remaining_captions(), "saved": scene.saved_captions()})
    
    r1 = scene.click(20, 20)
    scene_steps.append({"step": "click_a", "result": r1, "remaining": scene.remaining_captions(), "saved": scene.saved_captions()})
    
    for _ in range(50):
        scene.advance(0.04)
    scene_steps.append({"step": "advance_50", "remaining": scene.remaining_captions(), "saved": scene.saved_captions()})
    
    r2 = scene.click(30, 20)
    scene_steps.append({"step": "click_b", "result": r2, "remaining": scene.remaining_captions(), "saved": scene.saved_captions()})
    
    for _ in range(50):
        scene.advance(0.04)
    scene_steps.append({
        "step": "advance_end",
        "remaining": scene.remaining_captions(),
        "saved": scene.saved_captions(),
        "points": scene.score.points,
        "batch_retired": scene.batch_retired,
    })
    trace["scene"] = scene_steps

    out_path = Path(__file__).resolve().parents[2] / "fixtures" / "sda_differential_trace.json"
    out_path.write_text(json.dumps(trace, indent=2), encoding="utf-8")
    print(f"Wrote differential trace to {out_path} ({out_path.stat().st_size} bytes)")


if __name__ == "__main__":
    generate_trace()
