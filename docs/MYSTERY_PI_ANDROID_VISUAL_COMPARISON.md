# Vegas: primera integración visual Android (2026-10-09)

APK experimental privada: `local-output/vegas-visual-debug.apk`.
SHA-256: `8d5d648970f6328b19d249481badaf914d6977686c9de037897605e3a40468bf`.
Aplicación WSA: `org.rigorcore.caserecomp.synthetic.debug`, Android 13/API 33.
Huntsville, Director y la APK aprobada no se modificaron. No se ejecutó el EXE ni se controlaron dispositivos de entrada del escritorio.

## Comparación con los recursos originales

Comparación visual local: `local-output/vegas-visual-wsa-final/comparison.html`.
Incluye recursos originales a la izquierda y framebuffer WSA a la derecha; `source-hashes.json` conserva las huellas de los recursos. Estos archivos privados no se publican en Git.
Es una comparación con los recursos extraídos, no con capturas del ejecutable original.

| Pantalla / captura privada | Recurso y binding original | Resultado y diferencias |
| --- | --- | --- |
| `vegas-map.png` | ENVS.MSE, map_backgroundstart → map_backgroundstart.jpg; scenebutton, texscene/texnormal | Fondo, miniaturas y marcos originales. Disposición recuperada de 0043eec0, orden de declaración; admite 1–9 tarjetas. Fuentes Android: el texto de misión se solapa parcialmente. Marcadores de rango y transiciones pendientes. |
| `vegas-scene.png` | SCENE_VAULT.MSL, sc_vault_background.jpg; sprites originales; pdacontrol → ui_side01/ui_side02 | Fondo, objetos y PDA originales, controles MAPA y objetivos funcionales. El reloj usa su rectángulo XUI, pero su caption y puntuaciones largas se recortan con la fuente provisional. Animación de filas y métricas originales pendientes. |
| `vegas-rotation.png` | tilerotgame_vault → mini_vault.jpg, (172,95), 612×408 | Cada tile recorta su imagen original y aplica sus cuartos de vuelta; desaparecen los números provisionales. Sombras, relieve, referencia PDA y animación suave pendientes. |
| `vegas-wordsearch.png` | wordsearch02.wsg, tile_normal_tex → wordsearch_normal.jpg; selected/locked existentes | Tiles originales y 10 palabras del tablero real. Letras Android y lista provisional; líneas animadas y fuentes bitmap pendientes. |
| `vegas-jigsaw.png` | jigsaw_dollar → mini_dollar.jpg; piezas y flechas originales existentes | Piezas reales, recorte alfa, bandeja, referencia y paging. Botón GIRAR PIEZA provisional. SOLVER comparte zona con flecha superior: la flecha recibe prioridad de entrada, pero sigue pendiente recuperar la colocación/visibilidad nativa del botón. |
| `vegas-swap.png` | tilegame_limo → mini_limo.jpg, (172,96), 612×408 | Recortes originales según la permutación real, sin números provisionales. Selección con borde Android; relieve, sombras y desplazamiento animado pendientes. |

Inspeccionadas las seis capturas. La primera prueba capturaba el fotograma anterior/splash: fue descartada. Las capturas finales esperan al compositor WSA y muestran las seis pantallas correctas, a 1600×900 con viewport del juego de 960×720 y bandas conservando 4:3.

## Verificación funcional

- `:engine:testDebugUnitTest`: 173 pruebas, cero fallos; `:app:testDebugUnitTest`: 23 pruebas, cero fallos.
- `:app:assembleDebug` y `:app:assembleDebugAndroidTest`: correctos.
- `SdaPrivateVisualInstrumentationTest.original_resources_map_scene_and_four_bonuses`: aprobada en WSA, no omitida. Abre una Activity real, selecciona tarjeta y regresa al mapa mediante MotionEvents, captura su framebuffer con UiAutomation y resuelve rotación, Word Search, Jigsaw e intercambio mediante entrada a la View. Completa los niveles 1–4, guarda/restaura cada resultado y llega normalmente al mapa del nivel 5. La preparación de objetivos usa APIs normales de campaña; no prueba que un humano complete todos los objetivos desde el launcher. No hay estados forzados, llamadas a solve ni mutación de fases/índices.
- `SdaPrivateJigsawInstrumentationTest`: aprobada en WSA tras el cambio, conserva el recorrido Android del nivel 3 a 4 y sus 24 piezas.
- Revisión independiente de código: corrigió la selección ambigua de mapscreencaption; no encontró otro bloqueo. Los IDs duplicados se resuelven en el contenedor mapunderlay, sin relajar la búsqueda genérica.

## Separación y continuidad exacta

- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaUiDocument.kt`: `component`, `texture`, `caption`. Lectura genérica de XUI, sin IDs, layouts ni reglas de Vegas.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaResourceCanvas.kt`: `label`, `button`, `image`. Continuar con fuentes bitmap y estados visuales genéricos de controles.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/VegasVisualProfile.kt`: `base`, `drawHud`, `drawMap`, `drawBonusBase`, `drawTileBonus`. Recuperar visibilidad y ubicación por modo, instrucciones/PDA del bonus, marcadores y efectos con evidencia. Los IDs/layouts específicos permanecen aquí.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt`: `drawBonus`, `onTouchEvent`. Reemplazar el control de giro temporal y añadir las animaciones y estados de pulsación sin alterar las reglas de los minijuegos.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaLauncherActivity.kt`: creación de la View con perfil Vegas y reutilización de la escena restaurada; los demás títulos no activan este perfil.
- `android/app/src/androidTest/kotlin/org/rigorcore/caserecomp/app/SdaPrivateVisualInstrumentationTest.kt`: `display`, `touch`; ampliar la comparación al launcher/importación y a niveles posteriores, con aserciones visuales adicionales.
- Desenlace: `SdaCampaign.confirmLevelComplete`, `SdaRiddleResources.loadSecond`, `SdaGameView.drawFinale`. Sigue pendiente la transición auténtica primera→segunda fase, la integración Android de la segunda y la tercera. No hay campaña completa de principio a fin todavía.

Este bloque entrega la APK y seis capturas reales con recursos originales; la fidelidad visual completa, el audio y el desenlace siguen pendientes. No equivale a aprobación humana ni al cierre del objetivo global.
