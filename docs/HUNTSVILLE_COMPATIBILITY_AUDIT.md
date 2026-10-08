# Auditor?a de compatibilidad heredada de Huntsville

Fecha: 2026-10-08. Base p?blica: `9d43dda` (`main`). Esta auditor?a y las propuestas asociadas no modifican el motor. El archivo local no seguido `DirectorAndroidPorts.kt` se conserva y se incluye en la huella del ?rbol de trabajo; no forma parte de esa revisi?n Git. Los resultados JVM corresponden a ese ?rbol local, no a un checkout limpio del commit.

## M?todo y alcance

Se revis? el historial completo de los cuatro ?mbitos `android/engine/src/main`, `android/app/src/main`, `caserecomp` y `web/convert.py`: 93 commits, desde `93325b7` hasta las actualizaciones legales de `82edfa8`. Se contrastaron las implementaciones presentes, diffs de calibraci?n y pruebas existentes. El inventario individual se encuentra en [huntsville-adjustments.csv](huntsville-adjustments.csv); el inventario de archivos por commit queda en `private/mystery-pi-vegas/research/history-inventory.json`.

El historial mezcla implementaci?n inicial, ajustes de compatibilidad, pruebas e interfaz. La clasificaci?n de un commit es primaria: la tabla siguiente descompone commits que mezclan categor?as. No se infiere correcci?n universal a partir de un mensaje de commit ni de una captura de un ?nico t?tulo. Las referencias anteriores al traslado se resuelven con el manifiesto privado de reubicaci?n.

Categor?as: **G** error del motor gen?rico; **E** caracter?stica est?ndar del motor/formato original; **V** compatibilidad de versi?n; **H** particularidad leg?tima del t?tulo; **P** ajuste visual provisional; **D** soluci?n temporal/deuda. Lo legal y la distribuci?n sin efecto en ejecuci?n se marcan fuera de compatibilidad en el CSV.

## Inventario de decisiones

Las rutas Kotlin del motor est?n bajo `android/engine/src/main/kotlin/org/rigorcore/caserecomp/`; las de Android bajo `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/`.

| Ajuste / commits | Evidencia actual | Clase | Destino y comprobaci?n antes de generalizar |
|---|---|---|---|
| Inspecci?n PE y contenedores Director: `93325b7`, `45976fe` | `inspector.py`, `director.py`: l?mites y cabeceras reconocibles | E/G | Detector de formatos compartido; conservar rechazo de truncados y tama?os; no convertir todo PE en Director. |
| JPEG, BITD, ALFA, relaciones KEY y propietarios: `d93dcad`, `81f75cc`, `af16192`, `64fccc9`, `c30e6f4` | `bitmap.py`, `relationships.py`, `pipeline.py` y pruebas | E/G | Adaptador de formato Director; alpha/colores y propietarios mediante fixtures sint?ticos. |
| ?ndices de Lingo y comparaci?n de decompiladores: `4c9f4e1` | `lingo_index.py`, `lingo_compare.py`, `backends.py` | E | Herramientas offline reutilizables para Director; dumps privados no son un perfil. |
| Scenario/Score y pruebas de escena manuales: `e755775`, `107d0f5`, `7474959`, `3b4664a`, `5efb8a2` | `Engine.kt`, `Runtime.kt`, `VerifiedScene.kt`, `VerifiedFlow.kt` | D + E de Score | Conservar como referencia y shell sint?tico. No transcribir m?s reglas comerciales como arquitectura principal. |
| Importaci?n, ciclo de vida y recuperaci?n legacy: `f0e570a`, `4961524`, `864159b`, `a423d10`, `52e6ffd`, `f79ab8b` | repositorios privados, slots, pruebas de resiliencia | G/E | Servicio Android compartido mediante adaptadores de formatos; preservar muerte de proceso y persistencia. |
| Cadena de evidencia y fuentes: `3ae366f`, `ceb17d7`, `3aecc12`, `0bf499f`, `f3b3b1a`, `19ad205`, `92f4f5f`, `4b038e6`, `30293eb`, `a3295c1`, `c851a80`, `fe46bd0`, `d96552d`, `3055036`, `cd112d5`, `de0dd4d`, `6105635` | validadores de pruebas, capturas, enlaces y timings | G/E | Infraestructura de regresi?n; no trasladar decisiones de aceptaci?n a configuraciones del juego. |
| Herramientas Windows y smart cast: `c7e94ce`, `a02d6b5` | `executables.py`, c?digo Android | G | Mantener en infraestructura; pruebas Windows y compilaci?n. |
| Enlace Lscr y registros Score de 48 bytes: `578a70f`, `e2ff7a8`, `bd241b0` | `lingo_bytecode.py`, `score.py`, `movie_bundle.py` | G/E/V | Director; validar variantes estructurales reales, no seleccionar por nombre de juego. |
| Literales MacRoman y retornos de carro: `e1e5dbb` | `lingo_bytecode.py` | G/V | Decoder elegido por formato/versi?n y evidencia de codificaci?n; fixture con acentos y RETURN. No extender a cadenas UTF-8 de SDA. |
| VM, tipos, opcodes, budgets y dispatch: `09194ac`, `56d83b0` | `lingo/LingoBundle.kt`, `LingoVm.kt`, `LingoNatives.kt` | E/V | M?dulo Director/Lingo. El subconjunto implementado no prueba todas las versiones ni todos los opcodes. |
| RETURN inmediato y opcodes legacy en `752e20a` | `LingoVm.kt`, propiedades legacy y chunks | G/V | Correcci?n sem?ntica del int?rprete; fixtures D7/D8.5, llamadas y presupuestos. |
| Score, casts, eventos, actores, im?genes y audio en `752e20a`, `9b6d95c` | `director/DirectorRuntime.kt`, `DirectorSprite.kt`, `DirectorImage.kt`, `DirectorSound.kt` | E/V | N?cleo Director 8.5, no motor universal ni l?gica de Huntsville. |
| Buddy API, FileIO, Enhancer y red offline en `752e20a` | `director/DirectorXtras.kt` | E/D/V | Adaptadores de Xtra con capacidades declaradas y modos comprobados. No confundir no-op con implementaci?n completa. |
| Flash AVM1, formas y compositor: `8a92286`, `9e2c371` | `flash/`, `StageRenderer.kt` | E/D | Biblioteca de tecnolog?a reutilizable, con lista expl?cita del subconjunto. No se exige a juegos sin SWF. |
| Paquete Director, verificaci?n total e importaci?n segura: `05f0868`, `7e829af`, `39775a4` | `director_content.py`, `DirectorContent.kt`, `PrivateDirectorRepository.kt` | E/G | Preservar validaci?n espec?fica de Director; factorizar s?lo transporte/integridad comprobados. |
| Lanzador y viewport: `6d967fc`, `9a74dd2`, `078b95b` | `DirectorLauncherActivity.kt`, `DirectorViewport.kt` | E/D | Servicio de sesi?n/presentaci?n; hoy acoplado a Director. Pruebas de importaci?n y gestos. |
| API 26 e independencia de saves: `bc99824` | `AndroidDirectorStore`, repositorio por SHA de ZIP | G | Mantener namespace exacto para rollback; cambio de ZIP genera otro namespace aunque el juego sea el mismo. |
| L?mites de stage/canales/Score: `89ee12e` | constructor de sesi?n y `DirectorContent` | G | L?mites por tecnolog?a dentro de un presupuesto global; no desactivar por perfil. |
| M?scaras Flash y JPEG EOI/SOI: `106ea05`, `467450b` | `Swf.kt`, `FlashPlayer.kt`, `StageRenderer.kt` | G/E | Correcciones de formato. Validar PNG/JPEG y m?scara de clip sint?ticos y privados. |
| Tama?o real de casts y teclas de control: `4484371` | `castFileSize`, Xtras y entrada Android | G/E | FileIO/Buddy API Director; teclas traducidas por adaptador. Reutilizar transporte, no constantes Lingo en SDA. |
| Sprite no estirado: `a60c3c2` | tama?o del miembro y bandera stretch | E/G | Director; comparar tama?o, registro, stretch y hit-test, no regla visual por t?tulo. |
| Text Xtra y primer run visible: `d52502a`, `cd4192c` | `_xmed_text` en `movie_bundle.py`; `TextLayout.kt` | E/D/V | Parser Director versionado. La reducci?n a un estilo por miembro pierde runs; el fit heur?stico es deuda separada. |
| Indentaci?n y colores de Score: `cb94281`, `e882ec3` | texto Xtra, recolorizado de miembro | E/G | Director y compositor; fixtures de indents, palette, fore/back y alpha. |
| Cache de medios y cadence: `cea8cb1` | presupuesto `mediaCacheBytes`, bucle Android | G/E | Presupuesto Android com?n; caches de texto/SWF y copias ARGB tambi?n cuentan. Medir latencia p95/p99 y acumulaci?n. |
| Hit-testing de texto y handlers de mouse: `467450b`, `e2b4731` | `activeSpriteAt`, eventos de `DirectorRuntime` | E/G | Director; transparentes, frame/movie fallback y mouseUpOutside. No convertirlo en hit-test universal. |
| CloneSprite/RemoveSprite: `68e3a01` | `FlashPlayer.kt` | E | AVM1; instancias, profundidades y handlers, sin nombre de t?tulo. |
| SWA?MP3 sin FFmpeg y padding: `6451586`, `1e849bd` | `audio.py`, pipeline, `AndroidDirectorSound` | E/G | Extracci?n/decodificaci?n Director y servicio de audio Android; no equivale a implementar Ogg/BASS. |
| Pausa/resume y fin del stage: `1e849bd`, `2777e6b` | listeners y lifecycle del lanzador | G/E | Contrato de sesi?n com?n; probar prepareAsync pendiente durante pause y focus de audio. |
| Par?metros `(sprite n)` y `(member n of castLib m)`: `b688ae2` | parser literal y resoluci?n runtime | E/G/V | Director/Lingo; mantener sem?ntica y rechazos para formatos inv?lidos. |
| HTML tables/font/tabs: `bddf559`, `e224bd6` | `TextLayout.kt`, HTML?texto/paragraphs | E/D | Director text subset; m?ltiples estilos HTML/runs siguen incompletos. Comparaci?n de tablas y baseline. |
| Ink matte desde borde y 32-bit opaco: `f915139` | compositor y tests de ink | E/G | Director; comprobar varios bordes/colores/alpha. Evidencia de Huntsville no demuestra universalidad de la heur?stica de borde. |
| Centro de `new(#bitmap)`: `e224bd6` | `DirectorCast.kt`, registros de imagen | E/G | Sem?ntica Director, probar nuevos bitmaps y rotaci?n. |
| 2? compositor: `e6bcb8e` | scale del stage, texto a resoluci?n mayor, Flash muestreado a pixel de stage | E/D | Calidad de presentaci?n com?n opcional; 4? memoria del framebuffer, no mejora universal de fidelidad Flash. |
| Rollover t?ctil y mouse hover: `10079b3` | `DirectorStageView.kt`: down retrasado 100 ms, lift posterior | E/D | Adaptaci?n Android configurable de entrada; preservar orden mouseMove/down/up. No convertir 100 ms en requisito SDA. |
| Touch ring, bridge, F12 e import/save debug: `e224bd6`, `a2c1203`, `5895d36`, `808f80e` | `DirectorDebugBridge.kt`, extras, stage | E/D | Diagn?sticos y UX; bridge es debug. No son reglas de juego ni prueba de compatibilidad. |
| Familias Android, ancho, tama?o, baseline, overflow: `6c03811` | `AndroidDirectorPorts.kt` | P/D; embedded flags candidato V | Separar mecanismo de fuente y datos calibrados. Requiere pruebas de otra pel?cula y otro Android antes de promoverlo. |
| Tekton italic 0.68, ancho 0.95: `252ba53` | comparaciones por substring de fuente | P | Excepci?n temporal de edici?n de Huntsville, nunca activaci?n por fuente en cualquier juego. |
| Margen centrado 6 px, leading blank 0.75, pitch Tekton italic 1.32: `cfeadd2` | constantes del rasterizador Android | P/D | Encapsular s?lo al migrar con baseline; decidir por mediciones, no declarar regla Director universal. |
| Palatino ancho 0.88 y nudges: `747d659` | rasterizador, `presentation.json`, `StageRenderer.nudges` | P/D/H candidato | Nudge es correcci?n provisional; miembro por nombre no identifica juego. S?lo excepci?n justificada, enlazada a huellas/miembro. |
| Home con cards y cover: `fc7e955` | `HomeActivity.GAMES`, repositorio activo ?nico | H/D | T?tulo/car?tula en cat?logo declarativo; car?tula privada del contenido. Servicios y lifecycle comunes. |
| Convertidor por nombres de casts y `ready`: `6b2bcbe` | `web/convert.py:TITLES`, `scan`, `_movie_candidates` | D/H | Sustituir por registro de detecci?n por contenido. Nombres no verifican t?tulo/edici?n. |
| Focus highlight Android 8: `7c6343b` | stage y view focus | G | Arreglo de plataforma, no excepci?n de Huntsville. Prueba API 26 con teclado y tacto. |

## Lo que necesita perfil y lo que permanece en el motor

Mantener en Director: opcodes, dispatch, Score, casts, stretch, ink, audio channels, par?metros Lingo, FileIO/Xtras comprobados y Flash. La configuraci?n selecciona variantes documentadas; no contiene otra VM ni escenas del juego.

Candidatos de perfil de Huntsville: edici?n espa?ola de la copia observada, fuentes originales identificadas y calibraci?n sustituta para una plataforma concreta, car?tula elegida, necesidad observada de Buddy API/FileIO/Flash, variantes de entorno comprobadas, desplazamiento provisional del ?nico miembro presente en `presentation.json`. Tener el mismo nombre de fuente o miembro en otro juego no justifica aplicar estos valores.

No se comprob? una particularidad H que requiera reemplazar la l?gica del juego en el n?cleo. Las reglas observadas se ejecutan desde los datos/Lingo originales. Una propiedad medida en Huntsville todav?a puede ser est?ndar: queda como candidata hasta conseguir evidencia suficiente.

## Riesgos que ya existen

1. `AndroidDirectorText` aplica constantes calibradas globalmente: Palatino 0.88, Times 0.86, Tekton italic tama?o 0.68 y margen 6. S?lo hay evidencia de calibraci?n de un t?tulo; no se autoriza cambiar sus valores durante la auditor?a.
2. El rasterizador ignora los flags bold/italic de runs de fuentes cuyo nombre acaba en `*` y decide por el nombre. La sem?ntica exacta de font embedding no est? demostrada para otras ediciones/versiones.
3. El loader de `presentation.json` tambi?n se ejecuta en release, aunque su comentario mencione debug/ADB. No enlaza offsets a package SHA, edici?n ni perfil. Desplaza dibujo pero mantiene coordenadas Lingo/hit-test; puede separar la imagen del ?rea pulsable.
4. `StandardXtras` reporta ?xito en algunos cambios de ventana/pantalla sin realizarlos; simula red offline y FileIO de cadenas. No es un filesystem binario ni un Win32 gen?rico. Capacidades deben distinguir implementaci?n completa, parcial y sustituto.
5. La VM falla con `handler not defined` cuando se agotan todos los dispatches. Los contadores privados de `count/getat` no son por s? solos m?todos sin implementar: el arn?s cuenta llamadas en el host antes del fallback a m?todos de lista.
6. `HomeActivity` muestra nombres fijos, abre siempre el paquete Director activo y no verifica t?tulo de la tarjeta. El paquete prueba integridad y enlace de fuente, no pertenencia a un t?tulo conocido.
7. `AndroidDirectorSound` prepara as?ncronamente; su callback arranca al terminar. La pausa actual s?lo registra canales que ya estaban reproduciendo. Hay que probar la carrera pause?prepared y foco de audio; esta auditor?a no declara un fallo observado en dispositivo.
8. El budget de im?genes del runtime no cubre de forma conjunta framebuffer, copia Bitmap, caches texto/SWF y audio. No hay presupuesto global multisesi?n comprobado.
9. `DirectorAndroidPorts.kt`, no seguido, presenta otro decoder/rasterizador/store/audio de menor protecci?n. No se encontraron callers por esos nombres; el archivo sigue entrando en la compilaci?n local. No borrarlo ni adoptarlo como servicio compartido sin revisi?n espec?fica.

## Estado de pruebas y evidencia

- Python: `python -m unittest discover -s tests -v`: 276 pruebas, 0 fallos, 2 omitidas.
- JVM Android: `gradle --offline --no-daemon --console=plain :engine:testDebugUnitTest :app:testDebugUnitTest`: BUILD SUCCESSFUL; engine 81 + app 3, 0 fallos/errores/omitidas. De esas 84, una es el arn?s privado `PrivateBootSmokeTest`; 83 son los otros tests.
- El arn?s JVM se ejecut? con los bundles privados de Huntsville. Genera `boot-smoke.txt` y capturas del runtime en su directorio local; esas salidas no son nuevas capturas del projector original. Que el arn?s termine no prueba igualdad visual, audio Android ni el final del juego.
- Los reportes WSA previos de `PHASE10_ANDROID_QA.md` son evidencia hist?rica, no comprobaciones de esta sesi?n. No se lanz? ni instal? APK, no se captur? el projector nativo ni se midi? rendimiento en dispositivo.
- Se registraron SHA-256 de 135 archivos de c?digo/configuraci?n antes del an?lisis. La verificaci?n final confirm? los 135 archivos id?nticos. Tambi?n confirm? por SHA-256 los 69 archivos originales de ambos juegos y 5.829 archivos de referencias nativas/decompilaci?n sin cambios respecto al manifiesto de reubicaci?n (excluyendo helpers y salidas generadas del arn?s M3). No se implement? generalizaci?n; no existe resultado antes/despu?s de una modificaci?n del runtime.

Ver [plan multijuego](MULTIGAME_ARCHITECTURE.md), [perfiles](GAME_COMPATIBILITY_PROFILE.md), [Mystery P.I.](MYSTERY_PI_VEGAS_AUDIT.md) y [regresiones](MULTIGAME_REGRESSION_PLAN.md).
