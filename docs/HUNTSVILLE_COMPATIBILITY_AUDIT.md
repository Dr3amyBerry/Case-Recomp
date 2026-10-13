# Auditoría de compatibilidad heredada de Huntsville

Fecha: 2026-10-08. Base pública: `9d43dda` (`main`). Esta auditoría y las propuestas asociadas no modifican el motor. El archivo local no seguido `DirectorAndroidPorts.kt` se conserva y se incluye en la huella del Árbol de trabajo; no forma parte de esa revisión Git. Los resultados JVM corresponden a ese Árbol local, no a un checkout limpio del commit.

## Método y alcance

Se revisó el historial completo de los cuatro Ámbitos `android/engine/src/main`, `android/app/src/main`, `caserecomp` y `web/convert.py`: 93 commits, desde `93325b7` hasta las actualizaciones legales de `82edfa8`. Se contrastaron las implementaciones presentes, diffs de calibración y pruebas existentes. El inventario individual se encuentra en [huntsville-adjustments.csv](huntsville-adjustments.csv); el inventario de archivos por commit queda en `private/mystery-pi-vegas/research/history-inventory.json`.

El historial mezcla implementación inicial, ajustes de compatibilidad, pruebas e interfaz. La clasificación de un commit es primaria: la tabla siguiente descompone commits que mezclan categorías. No se infiere corrección universal a partir de un mensaje de commit ni de una captura de un Único título. Las referencias anteriores al traslado se resuelven con el manifiesto privado de reubicación.

Categorías: **G** error del motor genérico; **E** característica estándar del motor/formato original; **V** compatibilidad de versión; **H** particularidad legítima del título; **P** ajuste visual provisional; **D** solución temporal/deuda. Lo legal y la distribución sin efecto en ejecución se marcan fuera de compatibilidad en el CSV.

## Inventario de decisiones

Las rutas Kotlin del motor están bajo `android/engine/src/main/kotlin/org/rigorcore/caserecomp/`; las de Android bajo `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/`.

| Ajuste / commits | Evidencia actual | Clase | Destino y comprobación antes de generalizar |
|---|---|---|---|
| Inspección PE y contenedores Director: `93325b7`, `45976fe` | `inspector.py`, `director.py`: límites y cabeceras reconocibles | E/G | Detector de formatos compartido; conservar rechazo de truncados y tamaños; no convertir todo PE en Director. |
| JPEG, BITD, ALFA, relaciones KEY y propietarios: `d93dcad`, `81f75cc`, `af16192`, `64fccc9`, `c30e6f4` | `bitmap.py`, `relationships.py`, `pipeline.py` y pruebas | E/G | Adaptador de formato Director; alpha/colores y propietarios mediante fixtures sintéticos. |
| Índices de Lingo y comparación de decompiladores: `4c9f4e1` | `lingo_index.py`, `lingo_compare.py`, `backends.py` | E | Herramientas offline reutilizables para Director; dumps privados no son un perfil. |
| Scenario/Score y pruebas de escena manuales: `e755775`, `107d0f5`, `7474959`, `3b4664a`, `5efb8a2` | `Engine.kt`, `Runtime.kt`, `VerifiedScene.kt`, `VerifiedFlow.kt` | D + E de Score | Conservar como referencia y shell sintético. No transcribir más reglas comerciales como arquitectura principal. |
| Importación, ciclo de vida y recuperación legacy: `f0e570a`, `4961524`, `864159b`, `a423d10`, `52e6ffd`, `f79ab8b` | repositorios privados, slots, pruebas de resiliencia | G/E | Servicio Android compartido mediante adaptadores de formatos; preservar muerte de proceso y persistencia. |
| Cadena de evidencia y fuentes: `3ae366f`, `ceb17d7`, `3aecc12`, `0bf499f`, `f3b3b1a`, `19ad205`, `92f4f5f`, `4b038e6`, `30293eb`, `a3295c1`, `c851a80`, `fe46bd0`, `d96552d`, `3055036`, `cd112d5`, `de0dd4d`, `6105635` | validadores de pruebas, capturas, enlaces y timings | G/E | Infraestructura de regresión; no trasladar decisiones de aceptación a configuraciones del juego. |
| Herramientas Windows y smart cast: `c7e94ce`, `a02d6b5` | `executables.py`, código Android | G | Mantener en infraestructura; pruebas Windows y compilación. |
| Enlace Lscr y registros Score de 48 bytes: `578a70f`, `e2ff7a8`, `bd241b0` | `lingo_bytecode.py`, `score.py`, `movie_bundle.py` | G/E/V | Director; validar variantes estructurales reales, no seleccionar por nombre de juego. |
| Literales MacRoman y retornos de carro: `e1e5dbb` | `lingo_bytecode.py` | G/V | Decoder elegido por formato/versión y evidencia de codificación; fixture con acentos y RETURN. No extender a cadenas UTF-8 de SDA. |
| VM, tipos, opcodes, budgets y dispatch: `09194ac`, `56d83b0` | `lingo/LingoBundle.kt`, `LingoVm.kt`, `LingoNatives.kt` | E/V | Módulo Director/Lingo. El subconjunto implementado no prueba todas las versiones ni todos los opcodes. |
| RETURN inmediato y opcodes legacy en `752e20a` | `LingoVm.kt`, propiedades legacy y chunks | G/V | Corrección semántica del intérprete; fixtures D7/D8.5, llamadas y presupuestos. |
| Score, casts, eventos, actores, imágenes y audio en `752e20a`, `9b6d95c` | `director/DirectorRuntime.kt`, `DirectorSprite.kt`, `DirectorImage.kt`, `DirectorSound.kt` | E/V | Núcleo Director 8.5, no motor universal ni lógica de Huntsville. |
| Buddy API, FileIO, Enhancer y red offline en `752e20a` | `director/DirectorXtras.kt` | E/D/V | Adaptadores de Xtra con capacidades declaradas y modos comprobados. No confundir no-op con implementación completa. |
| Flash AVM1, formas y compositor: `8a92286`, `9e2c371` | `flash/`, `StageRenderer.kt` | E/D | Biblioteca de tecnología reutilizable, con lista explícita del subconjunto. No se exige a juegos sin SWF. |
| Paquete Director, verificación total e importación segura: `05f0868`, `7e829af`, `39775a4` | `director_content.py`, `DirectorContent.kt`, `PrivateDirectorRepository.kt` | E/G | Preservar validación específica de Director; factorizar solo transporte/integridad comprobados. |
| Lanzador y viewport: `6d967fc`, `9a74dd2`, `078b95b` | `DirectorLauncherActivity.kt`, `DirectorViewport.kt` | E/D | Servicio de sesión/presentación; hoy acoplado a Director. Pruebas de importación y gestos. |
| API 26 e independencia de saves: `bc99824` | `AndroidDirectorStore`, repositorio por SHA de ZIP | G | Mantener namespace exacto para rollback; cambio de ZIP genera otro namespace aunque el juego sea el mismo. |
| Límites de stage/canales/Score: `89ee12e` | constructor de sesión y `DirectorContent` | G | Límites por tecnología dentro de un presupuesto global; no desactivar por perfil. |
| Máscaras Flash y JPEG EOI/SOI: `106ea05`, `467450b` | `Swf.kt`, `FlashPlayer.kt`, `StageRenderer.kt` | G/E | Correcciones de formato. Validar PNG/JPEG y máscara de clip sintéticos y privados. |
| Tamaño real de casts y teclas de control: `4484371` | `castFileSize`, Xtras y entrada Android | G/E | FileIO/Buddy API Director; teclas traducidas por adaptador. Reutilizar transporte, no constantes Lingo en SDA. |
| Sprite no estirado: `a60c3c2` | tamaño del miembro y bandera stretch | E/G | Director; comparar tamaño, registro, stretch y hit-test, no regla visual por título. |
| Text Xtra y primer run visible: `d52502a`, `cd4192c` | `_xmed_text` en `movie_bundle.py`; `TextLayout.kt` | E/D/V | Parser Director versionado. La reducción a un estilo por miembro pierde runs; el fit heurístico es deuda separada. |
| Indentación y colores de Score: `cb94281`, `e882ec3` | texto Xtra, recolorizado de miembro | E/G | Director y compositor; fixtures de indents, palette, fore/back y alpha. |
| Cache de medios y cadence: `cea8cb1` | presupuesto `mediaCacheBytes`, bucle Android | G/E | Presupuesto Android común; caches de texto/SWF y copias ARGB también cuentan. Medir latencia p95/p99 y acumulación. |
| Hit-testing de texto y handlers de mouse: `467450b`, `e2b4731` | `activeSpriteAt`, eventos de `DirectorRuntime` | E/G | Director; transparentes, frame/movie fallback y mouseUpOutside. No convertirlo en hit-test universal. |
| CloneSprite/RemoveSprite: `68e3a01` | `FlashPlayer.kt` | E | AVM1; instancias, profundidades y handlers, sin nombre de título. |
| SWA → MP3 sin FFmpeg y padding: `6451586`, `1e849bd` | `audio.py`, pipeline, `AndroidDirectorSound` | E/G | Extracción/decodificación Director y servicio de audio Android; no equivale a implementar Ogg/BASS. |
| Pausa/resume y fin del stage: `1e849bd`, `2777e6b` | listeners y lifecycle del lanzador | G/E | Contrato de sesión común; probar prepareAsync pendiente durante pause y focus de audio. |
| Parámetros `(sprite n)` y `(member n of castLib m)`: `b688ae2` | parser literal y resolución runtime | E/G/V | Director/Lingo; mantener semántica y rechazos para formatos inválidos. |
| HTML tables/font/tabs: `bddf559`, `e224bd6` | `TextLayout.kt`, HTML → texto/paragraphs | E/D | Director text subset; múltiples estilos HTML/runs siguen incompletos. Comparación de tablas y baseline. |
| Ink matte desde borde y 32-bit opaco: `f915139` | compositor y tests de ink | E/G | Director; comprobar varios bordes/colores/alpha. Evidencia de Huntsville no demuestra universalidad de la heurística de borde. |
| Centro de `new(#bitmap)`: `e224bd6` | `DirectorCast.kt`, registros de imagen | E/G | Semántica Director, probar nuevos bitmaps y rotación. |
| Compositor 2×: `e6bcb8e` | scale del stage, texto a resolución mayor, Flash muestreado a pixel de stage | E/D | Calidad de presentación común opcional; 4× memoria del framebuffer, no mejora universal de fidelidad Flash. |
| Rollover táctil y mouse hover: `10079b3` | `DirectorStageView.kt`: down retrasado 100 ms, lift posterior | E/D | Adaptación Android configurable de entrada; preservar orden mouseMove/down/up. No convertir 100 ms en requisito SDA. |
| Touch ring, bridge, F12 e import/save debug: `e224bd6`, `a2c1203`, `5895d36`, `808f80e` | `DirectorDebugBridge.kt`, extras, stage | E/D | Diagnósticos y UX; bridge es debug. No son reglas de juego ni prueba de compatibilidad. |
| Familias Android, ancho, tamaño, baseline, overflow: `6c03811` | `AndroidDirectorPorts.kt` | P/D; embedded flags candidato V | Separar mecanismo de fuente y datos calibrados. Requiere pruebas de otra película y otro Android antes de promoverlo. |
| Tekton italic 0.68, ancho 0.95: `252ba53` | comparaciones por substring de fuente | P | Excepción temporal de edición de Huntsville, nunca activación por fuente en cualquier juego. |
| Margen centrado 6 px, leading blank 0.75, pitch Tekton italic 1.32: `cfeadd2` | constantes del rasterizador Android | P/D | Encapsular solo al migrar con baseline; decidir por mediciones, no declarar regla Director universal. |
| Palatino ancho 0.88 y nudges: `747d659` | rasterizador, `presentation.json`, `StageRenderer.nudges` | P/D/H candidato | Nudge es corrección provisional; miembro por nombre no identifica juego. Solo excepción justificada, enlazada a huellas/miembro. |
| Home con cards y cover: `fc7e955` | `HomeActivity.GAMES`, repositorio activo Único | H/D | Título/carátula en catálogo declarativo; carátula privada del contenido. Servicios y lifecycle comunes. |
| Convertidor por nombres de casts y `ready`: `6b2bcbe` | `web/convert.py:TITLES`, `scan`, `_movie_candidates` | D/H | Sustituir por registro de detección por contenido. Nombres no verifican título/edición. |
| Focus highlight Android 8: `7c6343b` | stage y view focus | G | Arreglo de plataforma, no excepción de Huntsville. Prueba API 26 con teclado y tacto. |

## Lo que necesita perfil y lo que permanece en el motor

Mantener en Director: opcodes, dispatch, Score, casts, stretch, ink, audio channels, parámetros Lingo, FileIO/Xtras comprobados y Flash. La configuración selecciona variantes documentadas; no contiene otra VM ni escenas del juego.

Candidatos de perfil de Huntsville: edición española de la copia observada, fuentes originales identificadas y calibración sustituta para una plataforma concreta, carátula elegida, necesidad observada de Buddy API/FileIO/Flash, variantes de entorno comprobadas, desplazamiento provisional del Único miembro presente en `presentation.json`. Tener el mismo nombre de fuente o miembro en otro juego no justifica aplicar estos valores.

No se comprobó una particularidad H que requiera reemplazar la lógica del juego en el núcleo. Las reglas observadas se ejecutan desde los datos/Lingo originales. Una propiedad medida en Huntsville todavía puede ser estándar: queda como candidata hasta conseguir evidencia suficiente.

## Riesgos que ya existen

1. `AndroidDirectorText` aplica constantes calibradas globalmente: Palatino 0.88, Times 0.86, Tekton italic tamaño 0.68 y margen 6. Solo hay evidencia de calibración de un título; no se autoriza cambiar sus valores durante la auditoría.
2. El rasterizador ignora los flags bold/italic de runs de fuentes cuyo nombre acaba en `*` y decide por el nombre. La semántica exacta de font embedding no está demostrada para otras ediciones/versiones.
3. El loader de `presentation.json` también se ejecuta en release, aunque su comentario mencione debug/ADB. No enlaza offsets a package SHA, edición ni perfil. Desplaza dibujo pero mantiene coordenadas Lingo/hit-test; puede separar la imagen del Área pulsable.
4. `StandardXtras` reporta Éxito en algunos cambios de ventana/pantalla sin realizarlos; simula red offline y FileIO de cadenas. No es un filesystem binario ni un Win32 genérico. Capacidades deben distinguir implementación completa, parcial y sustituto.
5. La VM falla con `handler not defined` cuando se agotan todos los dispatches. Los contadores privados de `count/getat` no son por sí solos métodos sin implementar: el arnés cuenta llamadas en el host antes del fallback a métodos de lista.
6. `HomeActivity` muestra nombres fijos, abre siempre el paquete Director activo y no verifica título de la tarjeta. El paquete prueba integridad y enlace de fuente, no pertenencia a un título conocido.
7. `AndroidDirectorSound` prepara asíncronamente; su callback arranca al terminar. La pausa actual solo registra canales que ya estaban reproduciendo. Hay que probar la carrera pause → prepared y foco de audio; esta auditoría no declara un fallo observado en dispositivo.
8. El budget de imágenes del runtime no cubre de forma conjunta framebuffer, copia Bitmap, caches texto/SWF y audio. No hay presupuesto global multisesión comprobado.
9. `DirectorAndroidPorts.kt`, no seguido, presenta otro decoder/rasterizador/store/audio de menor protección. No se encontraron callers por esos nombres; el archivo sigue entrando en la compilación local. No borrarlo ni adoptarlo como servicio compartido sin revisión específica.

## Estado de pruebas y evidencia

- Python: `python -m unittest discover -s tests -v`: 276 pruebas, 0 fallos, 2 omitidas.
- JVM Android: `gradle --offline --no-daemon --console=plain :engine:testDebugUnitTest :app:testDebugUnitTest`: BUILD SUCCESSFUL; engine 81 + app 3, 0 fallos/errores/omitidas. De esas 84, una es el arnés privado `PrivateBootSmokeTest`; 83 son los otros tests.
- El arnés JVM se ejecutó con los bundles privados de Huntsville. Genera `boot-smoke.txt` y capturas del runtime en su directorio local; esas salidas no son nuevas capturas del projector original. Que el arnés termine no prueba igualdad visual, audio Android ni el final del juego.
- Los reportes WSA previos de `PHASE10_ANDROID_QA.md` son evidencia histórica, no comprobaciones de esta sesión. No se lanzó ni instaló APK, no se capturó el projector nativo ni se midió rendimiento en dispositivo.
- Se registraron SHA-256 de 135 archivos de código/configuración antes del análisis. La verificación final confirmó los 135 archivos idénticos. También confirmó por SHA-256 los 69 archivos originales de ambos juegos y 5.829 archivos de referencias nativas/decompilación sin cambios respecto al manifiesto de reubicación (excluyendo helpers y salidas generadas del arnés M3). No se implementó generalización; no existe resultado antes/después de una modificación del runtime.

Ver [plan multijuego](MULTIGAME_ARCHITECTURE.md), [perfiles](GAME_COMPATIBILITY_PROFILE.md), [Mystery P.I.](MYSTERY_PI_VEGAS_AUDIT.md) y [regresiones](MULTIGAME_REGRESSION_PLAN.md).
