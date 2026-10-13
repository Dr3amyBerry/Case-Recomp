# Estado actual de Case Recomp — 13 de octubre de 2026

> **Fuente central de vigencia.** Basado en la rama `main`, el release `v0.8.1`, los commits y los informes de pruebas publicados. Las pruebas privadas citadas son **resultados registrados con anterioridad**, no ejecuciones realizadas durante esta actualización documental. [Índice](INDICE.md) · [Roadmap](ROADMAP.md).

## Producto

Case Recomp es una **aplicación Android multijuego** que ejecuta conversiones de juegos aportados por el usuario, sin distribuir medios comerciales. Los títulos **Huntsville, Prime Suspects y Ravenhearst** utilizan Director/Lingo/Flash; **Mystery P.I.: The Vegas Heist** utiliza el motor SDA/XUI y su perfil específico.

| Juego | Estado público v0.8.1 | Evidencia registrada | Sin acreditar como completo |
|---|---|---|---|
| **Mystery Case Files: Huntsville** | Jugable | Referencia Android aprobada para una edición, motor Director e importación de contenido | Todas las ediciones y dispositivos; fidelidad universal |
| **Mystery Case Files: Prime Suspects** | Jugable | **22/22 casos JVM**, usando handlers y medios originales; **9/22 casos completos en Android** en la última contabilidad documentada; pruebas WSA de puzzles, checkpoints, audio y efectos | Casos Android 10–22, campaña Android integral y comparación audiovisual completa |
| **Mystery Case Files: Ravenhearst** | Jugable, en revisión | Director/Flash, capítulo inicial y diario JVM; bot Android, correcciones de presentación y reanudación de salas, cerraduras y diario documentadas en commits | Todos los candados y capítulos resueltos de forma natural en Android, final y paridad audiovisual |
| **Mystery P.I.: The Vegas Heist** | **Alpha**, recorrido jugable | Recorrido **25 niveles Android/WSA** mediante entrada normal, desenlace enlazado desde checkpoint ganado, **11 casos de parada forzada/reapertura** con estado equivalente | Fidelidad total Windows/Android, ranking/desbloqueos originales, estados opcionales, RAM/FPS sostenidos |

«Jugable», «implementado», «probado JVM», «probado WSA», «probado en dispositivo físico» y «fidelidad aprobada» significan cosas diferentes. La etiqueta pública no es una certificación total.

## Versión y tres variantes

GitHub Release **[v0.8.1](https://github.com/Dr3amyBerry/Case-Recomp/releases/tag/v0.8.1)** publicado el 12-10-2026. Android: `versionName=0.8.1`, `versionCode=12`, `minSdk=26`, `compileSdk=36`, `targetSdk=36`.

| APK | Descripción |
|---|---|
| `case-recomp-0.8.1.apk` | Normal, presentación estándar |
| `case-recomp-0.8.1-lite.apk` | Lite, composición Director a resolución lógica original |
| `case-recomp-0.8.1-superlite.apk` | Super Lite, Lite sin filtro de escalado, límite de 30 FPS |

Son tres variantes del **mismo producto multijuego**, no un APK por juego. Pueden coexistir mediante distintos identificadores de app, pero cada una conserva **sus propias partidas**. Ninguna APK pública contiene juegos o assets comerciales.

## Arquitectura implementada

- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/director/`: casts, recursos, Score, sprites, DirectorRuntime, StageRenderer, audio.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/lingo/` y `flash/`: VM Lingo, scripts/Xtras y Flash/ActionScript.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/`: XUI, escenas, RNG, score, bonus, campaña y serialización.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/games/vegas/`: comportamientos específicos del juego separados del SDA genérico.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/`: `HomeActivity`, `DirectorLauncherActivity`, `SdaLauncherActivity`, vistas, sonido, importadores y persistencia privada.
- `caserecomp/`, `web/` y `tools/`: inspección, conversión local, pruebas e investigación.

Las [especificaciones declarativas futuras](GAME_COMPATIBILITY_PROFILE.md) **no equivalen** a un resolver universal implementado para toda edición comercial.

## Qué demuestra cada prueba

| Evidencia | Demuestra | No demuestra |
|---|---|---|
| Python/Kotlin/JVM | Parsing, estados y recorridos ejecutados en el motor | UI Android o fidelidad audiovisual |
| GitHub Actions público en `main` | Compilación, pruebas y emuladores **sintéticos**; HEAD revisado verde | Pruebas con juegos comerciales privados |
| WSA/emulador privado | Touch, capturas y rutas ejecutadas en Android | Todos los móviles y ediciones |
| ADB con force-stop | Continuidad en **los estados explícitamente ensayados** | Todos los cortes o saves posibles |
| Comparación con Windows original | Escenas/estados concretos alineados | Fidelidad visual y sonora del juego completo |

Esta revisión modifica documentación, **no compila APK ni ejecuta nuevas pruebas**. No convertir cifras históricas en resultados actuales sin repetir su procedimiento.

## Pendientes priorizados

1. **Prime:** casos Android 10–22, QA del bot, estabilidad, audio, efectos y comparación Windows. La corrección específica del «Insecto de plástico» de loc12 es una adaptación a una anomalía comprobada en la copia original, no recuperación de coordenadas nativas.
2. **Ravenhearst:** verificar todos los candados y capítulos sin forzar desbloqueos, puzzle diario, final, audio y persistencia real en Android.
3. **Vegas:** paridad de pantallas/animaciones/sonido Windows, desglose de puntuación y rangos, desbloqueos, modos opcionales y mediciones sostenidas de RAM/GC/FPS. Su recorrido 25/25 no certifica identidad perfecta.
4. **Huntsville:** proteger la referencia aprobada y comprobar regresiones tras cambios en Director/Flash/audio.
5. **Infraestructura:** dispositivos físicos, distintos Android, aislamiento de guardados por título/variante, cobertura por edición y gates de publicación legal.

## Documentación viva e histórica

- **Vigente:** este estado, [README](../README.md), [roadmap](ROADMAP.md), [guía Android](../android/README.md), [índice](INDICE.md).
- **Pruebas por título:** [Huntsville](HUNTSVILLE_APPROVED_REFERENCE.md), [Prime](PRIME_SUSPECTS.md), [Ravenhearst](RAVENHEARST.md), [Vegas](MYSTERY_PI_ANDROID_AUDIT.md).
- **Histórico:** `PHASE*.md`, auditorías anteriores, antiguos planes y guiones conservan los resultados/limitaciones de su fecha. «No existe APK» puede describir correctamente octubre 7 y ser falso para v0.8.1.
- En adelante registrar fecha, commit, entorno, test ejecutado, resultado, scope, errores no resueltos y evidencia; no inventar porcentajes globales.

No distribuir binarios, scripts recuperados, assets ni partidas comerciales de las instalaciones originales.
