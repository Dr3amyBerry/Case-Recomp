# Regresi?n y aceptaci?n multijuego

Fecha: 2026-10-08. Complementa [arquitectura](MULTIGAME_ARCHITECTURE.md). El objetivo es comparar el comportamiento antes y despu?s de cada modificaci?n propuesta, sin confundir compilaci?n verde con compatibilidad de un juego.

## L?nea base ejecutada

| Verificaci?n | Resultado de esta sesi?n | Alcance real |
|---|---|---|
| Python unittest discover | 276 tests, 0 fallos; 2 skipped | Parsers, export, integridad, evidencias y web; no GPU/audio Android. |
| `:engine:testDebugUnitTest` offline | 81 tests, 0 fallos/errors/skips | VM, Director, Flash, legacy, budgets y un arn?s privado de Huntsville. |
| `:app:testDebugUnitTest` offline | 3 tests, 0 fallos/errors/skips | Shell/viewport; no es la suite de instrumentaci?n ni goldens de fuentes Android. |
| Arn?s privado JVM Huntsville | 1 test ejecutado en los 81 anteriores; termin? sin fallo | Bundle local, secuencia existente, reloj determinista, renderer JVM; no validaci?n integral del juego. |
| Auditor?a Mystery P.I. | PE/imports/recursos/firmas/versiones inspeccionados | Sin ejecuci?n del original, decodificaci?n audiovisual completa ni runtime Android. |
| Huellas de c?digo | 135 archivos id?nticos al inicio | No hubo cambio de motor, config o tests de implementaci?n durante la auditor?a. |
| WSA, emulator e instrumentaci?n | No ejecutados en esta sesi?n | Los resultados de docs previas son hist?ricos. No se afirma aceptaci?n nueva. |

Comandos reproducibles desde el repo:

```powershell
python -m unittest discover -s tests -v
Push-Location android
gradle --offline --no-daemon --console=plain :engine:testDebugUnitTest :app:testDebugUnitTest
Pop-Location
```

El test `PrivateBootSmokeTest.kt` es local/no seguido. Si el bundle no existe, usa assume y puede omitirse; un checkout p?blico debe registrar ese skip, no contarlo como Huntsville comprobado. Si existe, puede generar logs/shots/saves en el ?rea M3: dirigir sesiones de comparaci?n a copias de trabajo privadas, preservando goldens inmutables. No enviar sus entradas ni salidas a CI p?blica. Esta ejecuci?n gener? outputs del arn?s; no recaptur? ni reemplaz? el projector nativo como referencia.

Logs privados: `private/mystery-pi-vegas/research/python-baseline-tests.txt` y `android-baseline-tests.txt`. JUnit XML actuales: `android/{engine,app}/build/test-results/testDebugUnitTest/`. Registrar el ?rbol local exacto y el archivo no seguido `DirectorAndroidPorts.kt`, adem?s del Git SHA.

## Matriz por ?rea: tests existentes y aceptaci?n pendiente

| ?rea | Base sint?tica existente | Referencia privada y criterio de aceptaci?n |
|---|---|---|
| Inicio/men? | `DirectorRuntimeUnitTest`, `LingoVmUnitTest`, `DirectorContentUnitTest` | Splash, nombre, usuarios, men? y salida: mismo orden de eventos y estado observable, sin nueva excepci?n ni warnings no explicados. |
| Escena/navegaci?n | `test_score`, `test_relationships`, runtime, verified-flow/scene | menu?map?primera escena, finds/misses, hints, completion, return, next case y time-out; confirmar contadores/progreso y estado persistente. |
| Imagen/alpha/ink | `test_phase3b_bitmap_coverage`, `test_phase3b_media`, `test_fidelity`, `StageRendererUnitTest` | Checkpoints privados de foreground/background/matte/opaque. Igualdad exacta donde pipeline sea determinista; tolerancias de codec documentadas y localizadas. |
| Texto y fuentes | `test_movie_bundle`, `StageRendererUnitTest`; coverage Android actual limitada | Goldens en Android por edici?n, provider y versi?n de SO: wrap, baseline, kerning, indents, bold/italic, acentos, fixedline y clipping. Una imagen JVM AWT no acredita Android fonts. |
| Input/eventos | runtime + `DirectorViewportUnitTest`; `DirectorEntryGestureInstrumentationTest` disponible | Tacto r?pido/largo, cancel, drag, hover, teclado/IME, out-of-stage release; timestamps y move/down/up/leave, sin hit desplazado por nudge. |
| Animaci?n/Flash | `FlashPlayerUnitTest`, parser/renderer | Timeline, m?scaras, clone/remove, z-order, rollover y dialog; misma duraci?n dentro de tolerancia declarada en ticks. |
| Audio | `test_swa`, `test_phase3b_media`, SoundOutput sint?tico | Music/effects/loops/fades/fin de canal; latencia y sync medidas en dispositivo. Pause durante prepare, interruptions y return no reinician ni duplican. |
| Guardados/recuperaci?n | runtime slots, Xtras, private-content/flow/scene tests; lifecycle instrumentation | Comparar claves/estado de progreso despu?s de force-stop, reimport/upgrade y relaunch. Dos juegos con claves iguales nunca comparten save. |
| Rendimiento/memoria | `PerformanceBudgetUnitTest` y l?mites de imports | Startup y p95/p99 de ticks/frame/input, PSS/heap/cache, peak buffers y 20 ciclos abrir/cerrar. La prueba legacy de budget no demuestra budget del stage Director ni SDA. |
| Integridad/failures | `test_hardening`, `DirectorContentUnitTest`, repositorios instrumentados | Hash incorrecto incluso en entrada no usada, zip traversal/bomb, imagen gigante, unknown schema/ABI, import cancelado y rollback. |

Nuevas pruebas deben reproducir una divergencia o contrato observable. Los tests actuales de texto JVM no sustituyen goldens del rasterizador Android.

## Comparaci?n antes/despu?s de cada generalizaci?n

1. Congelar originales y goldens con source set, hashes, fechas/clock, seed, snapshot de save, input stream y expectativa de estado. Mantener capturas native de `private/huntsville/case-recomp-phase6` y `huntsville-first-playable` como evidencia independiente; no reescribirlas con capturas del nuevo runtime.
2. Preparar directorios separados baseline/candidate y copias de saves. Registrar Git SHA + diff del ?rbol, module/ABI, profile digest, versi?n de builder/decoder, ZIP SHA, Android/modelo, stage scale y pol?ticas font/input.
3. Ejecutar mismo fixture/acciones en implementaci?n anterior y candidata; extraer eventos, estado, frames checkpoint, audio timings y allocations. El wrapper M2 debe dar sem?ntica id?ntica sin cambiar defaults.
4. Comparar tanto baseline?candidate como candidate?native. Estar igual a un baseline con defecto no demuestra fidelidad original; una correcci?n intencional debe tener divergencia y aceptaci?n separadas.
5. Publicar s?lo m?tricas y fallos sint?ticos; mantener screenshots, strings/objetos, Lingo y saves originales privados. Guardar resultado por scope y edici?n, con links de evidence locales.
6. Si cambia texto/gesto/Flash/audio, repetir su conjunto afectado y los gates de startup/save; no sustituirlos por benchmarks del shell. Cualquier diferencia sem?ntica inesperada bloquea promoci?n.

Comparaci?n visual: regiones/estado seleccionados antes de ejecutar, reloj o random fijado cuando se pueda. Si hay contenido din?mico inevitable, m?scaras justificadas previamente; nunca enmascarar despu?s el fallo observado. No establecer una tolerancia global 0.8/255 por el dato hist?rico de JPEG. Alpha y coordenadas siguen una aceptaci?n distinta. Revisar input hit-test junto a cambios de texto/registro/nudge.

Comparaci?n de rendimiento: mismo dispositivo y build, warm/cold diferenciados y carga t?rmica registrada. Umbral inicial propuesto de investigaci?n: p95 ? baseline?1.10, pico memoria ? baseline?1.10 y sin crecimiento retenido tras ciclos; ratificar budgets por dispositivo/carga antes de hacerlos gate. FPS correcto no justifica degradaci?n de input/audio ni supera l?mite de memoria del host.

## Casos nuevos obligatorios para gestor/perfiles

Estos son casos de aceptaci?n a implementar junto con M1?M6; no se afirman ejecutados porque el gestor a?n no existe.

| Caso | Resultado requerido |
|---|---|
| Originales completos, hashes conocidos y m?dulo/capacidades presentes | Perfil exacto y plan de lanzamiento determinista. |
| Renombrar exe/carpeta preservando bytes/dependencias | Misma identidad, sin usar basename como autoridad. |
| Mismo nombre, bytes de otra edici?n; o cambio de un byte en cast critical | Sin perfil exacto ni excepci?n del juego anterior. |
| Exe conocido + cast incompleto, distinto o duplicado en otro root | Incomplete/ambiguous; no casar archivos ajenos por nombre. |
| Dos juegos en selecci?n | Particionar candidatos verificables o informar mixed-input; no combinar ni elegir el mayor. |
| PE x86 SDA sin pel?cula Director | Ruta de detector SDA/unknown, nunca DirectorRuntime como fallback. |
| Perfil/schema mayor, ABI desconocida, capability required faltante | Rechazo expl?cito antes de VM y preservaci?n de instalaci?n activa. |
| Manifest afirma Huntsville pero source fingerprints no coinciden | Rechazo de autoridad de identidad; integridad ZIP no acredita t?tulo. |
| Director sint?tico con Palatino/Tekton o member name igual | No recibe calibraciones/offsets de Huntsville. |
| Perfil con nudge/evidence inexistente o fuera de l?mites | Rechazo; sin c?digo/script arbitrario ni relajaci?n de integridad. |
| Dos instalaciones usan misma registry key/resource name | Sin cruce de save, media cache, audio, trace ni presentation. |
| Cambio de ZIP por cover/compression, misma fuente | Identidad igual; migraci?n de saves expl?cita y rollback, no uni?n autom?tica. |
| Switch de juego mientras audio prepara o input down pendiente | Callbacks de generaci?n anterior ignorados y recursos cerrados. |
| Fallo parse/start/import, proceso muerto o cancelar picker | Nada parcialmente activo; legacy package/saves recuperables. |

## Compatibilidad cruzada y promoci?n de cat?logo

Huntsville se mide con su edici?n real y un Director sint?tico que tenga fuentes/miembros hom?nimos. A?adir otra pel?cula Director con su evidencia permite decidir qu? ajustes son de versi?n y cu?les de t?tulo; no declarar Prime Suspects/Ravenhearst compatibles por ser de la serie.

Cuando haya segundo m?dulo: SDL/SDA sint?tico con atlas/Ogg/eventos; Huntsville con la misma secuencia; Mystery P.I. slice frente al original. Despu?s otro fixture SDA sin componentes MPI para demostrar que el m?dulo no est? codificado exclusivamente para The Vegas Heist.

Escala de promoci?n: research ? identified ? parser-verified ? runtime-slice ? device-verified ? full-verified, registrando cada scope de manera separada. En el cat?logo se proyecta a los estados especificados en el perfil; una observaci?n est?tica s?lo permite identified/research. Para full se exige matriz de escenas/puzzles/final, configuraciones, variantes de input, guardados y audio de la edici?n. No generalizar la aceptaci?n entre regiones o versiones sin evidencia.

No se repitieron instrumentaci?n API 26/33/36 ni pruebas WSA durante esta auditor?a. Se ejecutar?n al implementar cambios que afecten esa frontera y antes de promoci?n. No se ha comparado una candidata de generalizaci?n porque el c?digo sigue intacto; esta ausencia es un estado expl?cito, no un resultado positivo.
