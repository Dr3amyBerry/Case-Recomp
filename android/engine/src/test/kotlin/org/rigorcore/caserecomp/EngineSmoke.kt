package org.rigorcore.caserecomp

/** Runnable with kotlinc/java; no proprietary game rules or Android SDK. */
fun main() {
    val versioned = ScenarioV1(
        id = "synthetic",
        designWidth = 640,
        designHeight = 480,
        scenes = listOf(
            ScenarioSceneV1("roomA", 10, 20, listOf(
                ScenarioTargetV1("book", GameRect(10f, 10f, 100f, 100f), 1),
                ScenarioTargetV1("lamp", GameRect(60f, 60f, 140f, 140f), 2),
            )),
            ScenarioSceneV1("roomB", 30, 35, listOf(
                ScenarioTargetV1("clock", GameRect(200f, 100f, 300f, 200f), 1),
            )),
        ),
        events = listOf(ScenarioEventV1("marker", "frame-reached", "roomA", frame = 15)),
    )
    check(runCatching { versioned.copy(events = listOf(ScenarioEventV1("bad", "frame-reached", "roomA", frame = 999))) }.isFailure)
    check(runCatching { versioned.copy(events = listOf(ScenarioEventV1("bad", "target-found", "roomA", target = "missing"))) }.isFailure)
    val model = versioned.engineScenario()
    val game = Engine(model)
    var session = Session()
    check(game.update(session, Input.FindObject("book")) == session)
    session = game.update(session, Input.Start)
    check(session.screen == Screen.MAP)
    session = game.update(session, Input.EnterScene("roomA"))
    check(session.screen == Screen.SCENE && session.frame == 10)
    val advance = game.step(session, Input.AdvanceFrame(5))
    session = advance.session
    check(session.frame == 15)
    check(advance.events == listOf(EngineEvent.FrameReached("roomA", 15)))
    session = game.update(session, Input.AdvanceFrame(99))
    check(session.frame == 20)
    session = game.update(session, Input.FindObject("wrong"))
    check(session.found("roomA").isEmpty())
    session = game.update(session, Input.FindObject("book"))
    check(session.found("roomA") == setOf("book"))
    check(game.update(session, Input.FindObject("book")) == session)
    session = game.update(session, Input.BackToMap)
    check(session.screen == Screen.MAP)
    session = game.update(session, Input.EnterScene("roomA"))
    val completedA = game.step(session, Input.FindObject("lamp"))
    session = completedA.session
    check(session.screen == Screen.MAP)
    check(completedA.events.contains(EngineEvent.SceneCompleted("roomA")))
    session = game.update(session, Input.EnterScene("roomB"))
    val completed = game.step(session, Input.FindObject("clock"))
    session = completed.session
    check(session.screen == Screen.COMPLETE)
    check(completed.events.last() == EngineEvent.ScenarioCompleted)
    check(game.update(session, Input.Reset) == Session())

    val viewport = LetterboxViewport(versioned.designWidth.toFloat(), versioned.designHeight.toFloat())
    check(viewport.gamePoint(0f, 0f, 1280f, 720f) == null)
    val point = viewport.gamePoint(640f, 360f, 1280f, 720f)
    check(point != null && point.x == 320f && point.y == 240f)
    check(viewport.gamePoint(Float.NaN, 0f, 1280f, 720f) == null)
    val layout = checkNotNull(versioned.layout("roomA"))
    val roomSession = game.update(game.update(Session(), Input.Start), Input.EnterScene("roomA"))
    check(game.inputForTap(roomSession, layout, GamePoint(70f, 70f)) == Input.FindObject("lamp"))
    check(game.inputForTap(roomSession, layout, GamePoint(500f, 400f)) == null)

    val trace = listOf(
        Input.Start, Input.EnterScene("roomA"), Input.AdvanceFrame(5), Input.FindObject("book"),
        Input.FindObject("lamp"), Input.EnterScene("roomB"), Input.FindObject("clock")
    )
    val replayA = game.replay(inputs = trace)
    val replayB = game.replay(inputs = trace)
    check(replayA == replayB)
    check(replayA.finalSession.screen == Screen.COMPLETE)
    check(replayA.states.size == trace.size + 1)
    check(replayA.events.contains(EngineEvent.FrameReached("roomA", 15)))
    runtimeSmoke()
    println("Kotlin engine smoke: PASS (scenario-v1, frames, events, hit-test, deterministic replay, persistence, lifecycle, render, input, simulated audio)")
}
