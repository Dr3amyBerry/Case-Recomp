package org.rigorcore.caserecomp.app

import org.rigorcore.caserecomp.GameRect
import org.rigorcore.caserecomp.ScenarioEventV1
import org.rigorcore.caserecomp.ScenarioSceneV1
import org.rigorcore.caserecomp.ScenarioTargetV1
import org.rigorcore.caserecomp.ScenarioV1

object SyntheticContent {
    fun scenario(): ScenarioV1 = ScenarioV1(
        id = "android-shell-synthetic",
        designWidth = 640,
        designHeight = 360,
        scenes = listOf(
            ScenarioSceneV1(
                id = "synthetic-room",
                frameStart = 1,
                frameEnd = 30,
                targets = listOf(
                    ScenarioTargetV1("circle", GameRect(80f, 90f, 180f, 190f), 1),
                    ScenarioTargetV1("square", GameRect(390f, 100f, 510f, 220f), 2),
                ),
            ),
        ),
        events = listOf(ScenarioEventV1("midpoint", "frame-reached", "synthetic-room", frame = 15)),
    )
}
