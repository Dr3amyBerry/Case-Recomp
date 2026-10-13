# Android — arquitectura y guía vigentes

> **Revisado el 13-10-2026 · Case Recomp v0.8.1.** El shell sintético original fue una fase del proyecto, pero **ya no es el único reproductor**. La aplicación Android tiene catálogo para Huntsville, Prime Suspects, Ravenhearst y Vegas Heist. Consultar [Estado actual](../docs/STATUS_ACTUAL.md), [Índice](../docs/INDICE.md) y [Roadmap](../docs/ROADMAP.md).

## Diseño real

Case Recomp integra **dos familias de motores** en el mismo producto multijuego: Director/Lingo/Flash para Huntsville, Prime y Ravenhearst, y SDA/XUI con perfil Vegas para The Vegas Heist. No hay una APK independiente por título. La importación y los guardados son privados por título y versión; no incluir recursos, archivos originales, juegos, scripts propietarios o saves en el repositorio público.

| Componente | Responsabilidad |
|---|---|
| `:engine` `director/` | Película/casts, score, sprites, imagen, audio y StageRenderer |
| `:engine` `lingo/` | LingoVm y handlers / Xtras |
| `:engine` `flash/` | SWF/ActionScript (incluidos candados de Ravenhearst) |
| `:engine` `sda/` | XUI, escenas, objetivos, reloj, puntuaciones, minijuegos y campaña |
| `:engine` `games/vegas/` | Mecánicas del título sin contaminar SDA genérico |
| `:app` `HomeActivity.kt` | Catálogo, portadas y selección de importación |
| `:app` `DirectorLauncherActivity.kt` / `DirectorStageView.kt` | Playback Director, input, render, audio y lifecycle |
| `:app` `SdaLauncherActivity.kt` / `SdaGameView.kt` | Playback SDA, perfiles, input y guardado |
| `:app` `PrivateDirectorRepository.kt` / `PrivateSdaRepository.kt` | Paquetes, seguridad y datos privados aislados |

Los prototipos `ScenarioV1`, `.crcontent`, `.crflow` y la UI sintética permanecen como infraestructura de investigación/QA; no deben describirse como una limitación de la actual capacidad de ejecutar los cuatro títulos. La [especificación de perfiles](../docs/GAME_COMPATIBILITY_PROFILE.md) incluye propuestas no implementadas universalmente.

## Android, versiones y compilaciones

`app/build.gradle.kts` define Android `minSdk 26`, `targetSdk 36`, `compileSdk 36`, JDK 17, `versionName 0.8.1` y `versionCode 12`.

```bash
cd android
gradle --no-daemon :engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
gradle --no-daemon :app:assembleDebugAndroidTest
gradle --no-daemon :app:assembleRelease
gradle --no-daemon :app:assembleRelease -Plite
gradle --no-daemon :app:assembleRelease -PsuperLite
```

| APK | Identificación | Presentación |
|---|---|---|
| Normal | paquete base | Composición estándar |
| Lite | sufijo de paquete `.lite` | Director dibujado a resolución lógica original |
| Super Lite | sufijo `.superlite` | Lite + escalado sin suavizar y máximo 30 FPS |

Estas tres versiones se pueden instalar juntas, pero **no sincronizan las partidas**. El release actual está en [v0.8.1](https://github.com/Dr3amyBerry/Case-Recomp/releases/tag/v0.8.1). La firma release requiere únicamente el archivo privado local `private/signing/release.properties`; no subir claves a GitHub. `-PbundledContentDir` empaqueta contenido **solo en compilaciones personales del propietario**, nunca en APK públicas.

## Entrada, guardado, seguridad y rendimiento

- La conversión/importación del juego se realiza con archivos que el usuario tiene autorización para usar. Director y SDA no comparten un loader o formato privado suplantado.
- La identidad del juego depende de recursos/huellas de la edición validada, no de un nombre arbitrario de ZIP. Probar reimportación, modificación, corrupción y limpieza.
- Aislar paquetes y partidas por título, perfil/edición y variante APK. Probar cierres, errores de escritura y `force-stop` sin perjudicar otros títulos.
- Mantener las calibraciones exclusivas de Prime/Huntsville en Director y las exclusivas de Vegas en su perfil; los subsistemas comunes deben seguir siendo genéricos.
- Para RAM/FPS medir PSS, bitmaps, cachés, heap/GC y fotogramas **durante uso real**, no únicamente durante un microbenchmark.

## Matriz de pruebas y límites

1. Suites Python/Gradle/JVM: demuestran parsers y código del motor, no la experiencia Android.
2. GitHub Actions y emuladores API 26/33/36: verifican infraestructura **con fixtures sintéticos**, no el contenido comercial privado.
3. WSA/ADB y dispositivos físicos: registrar entrada real, versión, edición, capturas, escenas y resultados. `ActivityScenario.recreate` no equivale a matar proceso.
4. Fidelidad: comparar Windows original y Android en el mismo estado/fotograma; una textura extraída o una compilación verde **no** constituye aprobación visual.
5. Estado documentado a 13-10-2026: Vegas tiene 25 niveles Android/WSA y final enlazado; Prime 22 casos JVM y 9 Android registrados; Ravenhearst tiene runtime y pruebas parciales; Huntsville conserva la referencia aprobada. [Detalle](../docs/STATUS_ACTUAL.md).

## Referencias

[Desarrollo](../docs/DEVELOPER.md) · [Arquitectura](../docs/MULTIGAME_ARCHITECTURE.md) · [Regresión](../docs/MULTIGAME_REGRESSION_PLAN.md) · [Huntsville](../docs/HUNTSVILLE_APPROVED_REFERENCE.md) · [Prime](../docs/PRIME_SUSPECTS.md) · [Ravenhearst](../docs/RAVENHEARST.md) · [Vegas](../docs/MYSTERY_PI_ANDROID_AUDIT.md).

Los registros de `PHASE*.md` conservan su alcance histórico. **Actualizar una guía no equivale a ejecutar tests nuevos.**
