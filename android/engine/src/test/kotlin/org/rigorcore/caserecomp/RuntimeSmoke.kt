package org.rigorcore.caserecomp

private fun runtimeScenario(): ScenarioV1 = ScenarioV1(
    id = "runtime-synthetic",
    designWidth = 320,
    designHeight = 240,
    scenes = listOf(
        ScenarioSceneV1("one", 1, 10, listOf(
            ScenarioTargetV1("alpha", GameRect(20f, 20f, 80f, 80f), 1),
            ScenarioTargetV1("beta", GameRect(60f, 60f, 120f, 120f), 2),
        )),
    ),
)

fun runtimeSmoke() {
    val scenario = runtimeScenario()
    val clock = DeterministicClock(100)
    val store = InMemorySessionStore()
    val audio = RecordingAudioPort()
    val runtime = GameRuntime(scenario, clock, store, audio)
    runtime.onCreate(); runtime.onStart(); runtime.onResume()
    check(runtime.lifecycle == LifecycleState.RESUMED)
    check(runtime.dispatch(Input.Start).session.screen == Screen.MAP)
    runtime.dispatch(Input.EnterScene("one"))
    val input = runtime.inputForTap(180f, 140f, 640f, 480f)
    check(input == Input.FindObject("beta"))
    runtime.dispatch(checkNotNull(input))
    val frame = runtime.renderFrame()
    check(frame.screen == Screen.SCENE && frame.foundCount == 1 && frame.targets.first { it.id == "beta" }.found)
    clock.advanceBy(50)
    runtime.onPause(); runtime.onStop()
    val encoded = checkNotNull(store.value)
    val snapshot = checkNotNull(SessionSnapshotCodecV1.decode(encoded))
    check(snapshot.savedAtMillis == 150L)
    check(snapshot.foundByScene["one"] == setOf("beta"))

    val restored = GameRuntime(scenario, clock, store, RecordingAudioPort())
    restored.onCreate(); restored.onStart(); restored.onResume()
    check(restored.session.found("one") == setOf("beta"))
    restored.dispatch(Input.FindObject("alpha"))
    check(restored.session.screen == Screen.COMPLETE)
    restored.onPause()
    check(audio.cues == listOf(AudioCue.START, AudioCue.TARGET_FOUND))

    val corrupted = InMemorySessionStore(encoded.replace("frame=1", "frame=9"))
    val fresh = GameRuntime(scenario, clock, corrupted)
    fresh.onCreate()
    check(fresh.session == Session())

    val otherScenario = scenario.copy(id = "different")
    val incompatible = GameRuntime(otherScenario, clock, store)
    incompatible.onCreate()
    check(incompatible.session == Session())

    val deterministicA = SessionSnapshotCodecV1.encode(snapshot)
    val deterministicB = SessionSnapshotCodecV1.encode(snapshot)
    check(deterministicA == deterministicB)

    check(runCatching { clock.advanceBy(-1) }.isFailure)
    check(runCatching { runtime.onResume() }.isFailure)
}
