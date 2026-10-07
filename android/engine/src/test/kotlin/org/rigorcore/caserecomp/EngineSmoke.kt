package org.rigorcore.caserecomp

/** Runnable with kotlinc and kotlin; no proprietary game rules or Android SDK. */
fun main() {
    val model = Scenario("synthetic", listOf(
        SceneDefinition("roomA", setOf("book", "lamp")),
        SceneDefinition("roomB", setOf("clock"))
    ))
    val game = Engine(model)
    var session = Session()
    check(game.update(session, Input.FindObject("book")) == session)
    session = game.update(session, Input.Start)
    check(session.screen == Screen.MAP)
    session = game.update(session, Input.EnterScene("roomA"))
    check(session.screen == Screen.SCENE)
    session = game.update(session, Input.FindObject("wrong"))
    check(session.found("roomA").isEmpty())
    session = game.update(session, Input.FindObject("book"))
    check(session.found("roomA") == setOf("book"))
    check(game.update(session, Input.FindObject("book")) == session)
    session = game.update(session, Input.BackToMap)
    check(session.screen == Screen.MAP)
    session = game.update(session, Input.EnterScene("roomA"))
    session = game.update(session, Input.FindObject("lamp"))
    check(session.screen == Screen.MAP)
    session = game.update(session, Input.EnterScene("roomB"))
    session = game.update(session, Input.FindObject("clock"))
    check(session.screen == Screen.COMPLETE)
    check(game.update(session, Input.Reset) == Session())
    val viewport = LetterboxViewport(640f, 480f)
    check(viewport.gamePoint(0f, 0f, 1280f, 720f) == null)
    val point = viewport.gamePoint(640f, 360f, 1280f, 720f)
    check(point != null && point.x == 320f && point.y == 240f)
    check(viewport.gamePoint(Float.NaN, 0f, 1280f, 720f) == null)
    println("Kotlin engine smoke: PASS (states, discoveries, margins)")
}
