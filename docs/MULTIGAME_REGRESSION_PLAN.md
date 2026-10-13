# Regresión y aceptación multijuego

Fecha: 2026-10-08. Complementa [arquitectura](MULTIGAME_ARCHITECTURE.md). El objetivo es comparar el comportamiento antes y después de cada modificación propuesta, sin confundir compilación verde con compatibilidad de un juego.

## Línea base ejecutada

| Verificación | Resultado de esta sesión | Alcance real |
|---|---|---|
| Python unittest discover | 276 tests, 0 fallos; 2 skipped | Parsers, export, integridad, evidencias y web; no GPU/audio Android. |
| `:engine:testDebugUnitTest` offline | 81 tests, 0 fallos/errors/skips | VM, Director, Flash, legacy, budgets y un arnés privado de Huntsville. |
| `:app:testDebugUnitTest` offline | 3 tests, 0 fallos/errors/skips | Shell/viewport; no es la suite de instrumentación ni goldens de fuentes Android. |
| Arnés privado JVM Huntsville | 1 test ejecutado en los 81 anteriores; terminó sin fallo | Bundle local, secuencia existente, reloj determinista, renderer JVM; no validación integral del juego. |
| Auditoría Mystery P.I. | PE/imports/recursos/firmas/versiones inspeccionados | Sin ejecución del original, decodificación audiovisual completa ni runtime Android. |
| Huellas de código | 135 archivos idénticos al inicio | No hubo cambio de motor, config o tests de implementación durante la auditoría. |
| WSA, emulator e instrumentación | No ejecutados en esta sesión | Los resultados de docs previas son históricos. No se afirma aceptación nueva. |

Comandos reproducibles desde el repo:

```powershell
python -m unittest discover -s tests -v
Push-Location android
gradle --offline --no-daemon --console=plain :engine:testDebugUnitTest :app:testDebugUnitTest
Pop-Location
```

El test `PrivateBootSmokeTest.kt` es local/no seguido. Si el bundle no existe, usa assume y puede omitirse; un checkout público debe registrar ese skip, no contarlo como Huntsville comprobado. Si existe, puede generar logs/shots/saves en el Área M3: dirigir sesiones de comparación a copias de trabajo privadas, preservando goldens inmutables. No enviar sus entradas ni salidas a CI pública. Esta ejecución generó outputs del arnés; no recapturó ni reemplazó el projector nativo como referencia.

Logs privados: `private/mystery-pi-vegas/research/python-baseline-tests.txt` y `android-baseline-tests.txt`. JUnit XML actuales: `android/{engine,app}/build/test-results/testDebugUnitTest/`. Registrar el Árbol local exacto y el archivo no seguido `DirectorAndroidPorts.kt`, además del Git SHA.

## Matriz por Área: tests existentes y aceptación pendiente

| Área | Base sintética existente | Referencia privada y criterio de aceptación |
|---|---|---|
| Inicio/menú | `DirectorRuntimeUnitTest`, `LingoVmUnitTest`, `DirectorContentUnitTest` | Splash, nombre, usuarios, menú y salida: mismo orden de eventos y estado observable, sin nueva excepción ni warnings no explicados. |
| Escena/navegación | `test_score`, `test_relationships`, runtime, verified-flow/scene | menú → mapa → primera escena, finds/misses, hints, completion, return, next case y time-out; confirmar contadores/progreso y estado persistente. |
| Imagen/alpha/ink | `test_phase3b_bitmap_coverage`, `test_phase3b_media`, `test_fidelity`, `StageRendererUnitTest` | Checkpoints privados de foreground/background/matte/opaque. Igualdad exacta donde pipeline sea determinista; tolerancias de codec documentadas y localizadas. |
| Texto y fuentes | `test_movie_bundle`, `StageRendererUnitTest`; coverage Android actual limitada | Goldens en Android por edición, provider y versión de SO: wrap, baseline, kerning, indents, bold/italic, acentos, fixedline y clipping. Una imagen JVM AWT no acredita Android fonts. |
| Input/eventos | runtime + `DirectorViewportUnitTest`; `DirectorEntryGestureInstrumentationTest` disponible | Tacto rápido/largo, cancel, drag, hover, teclado/IME, out-of-stage release; timestamps y move/down/up/leave, sin hit desplazado por nudge. |
| Animación/Flash | `FlashPlayerUnitTest`, parser/renderer | Timeline, máscaras, clone/remove, z-order, rollover y dialog; misma duración dentro de tolerancia declarada en ticks. |
| Audio | `test_swa`, `test_phase3b_media`, SoundOutput sintético | Music/effects/loops/fades/fin de canal; latencia y sync medidas en dispositivo. Pause durante prepare, interruptions y return no reinician ni duplican. |
| Guardados/recuperación | runtime slots, Xtras, private-content/flow/scene tests; lifecycle instrumentation | Comparar claves/estado de progreso después de force-stop, reimport/upgrade y relaunch. Dos juegos con claves iguales nunca comparten save. |
| Rendimiento/memoria | `PerformanceBudgetUnitTest` y límites de imports | Startup y p95/p99 de ticks/frame/input, PSS/heap/cache, peak buffers y 20 ciclos abrir/cerrar. La prueba legacy de budget no demuestra budget del stage Director ni SDA. |
| Integridad/failures | `test_hardening`, `DirectorContentUnitTest`, repositorios instrumentados | Hash incorrecto incluso en entrada no usada, zip traversal/bomb, imagen gigante, unknown schema/ABI, import cancelado y rollback. |

Nuevas pruebas deben reproducir una divergencia o contrato observable. Los tests actuales de texto JVM no sustituyen goldens del rasterizador Android.

## Comparación antes/después de cada generalización

1. Congelar originales y goldens con source set, hashes, fechas/clock, seed, snapshot de save, input stream y expectativa de estado. Mantener capturas native de `private/huntsville/case-recomp-phase6` y `huntsville-first-playable` como evidencia independiente; no reescribirlas con capturas del nuevo runtime.
2. Preparar directorios separados baseline/candidate y copias de saves. Registrar Git SHA + diff del Árbol, module/ABI, profile digest, versión de builder/decoder, ZIP SHA, Android/modelo, stage scale y políticas font/input.
3. Ejecutar mismo fixture/acciones en implementación anterior y candidata; extraer eventos, estado, frames checkpoint, audio timings y allocations. El wrapper M2 debe dar semántica idéntica sin cambiar defaults.
4. Comparar tanto baseline → candidate como candidate → native. Estar igual a un baseline con defecto no demuestra fidelidad original; una corrección intencional debe tener divergencia y aceptación separadas.
5. Publicar solo métricas y fallos sintéticos; mantener screenshots, strings/objetos, Lingo y saves originales privados. Guardar resultado por scope y edición, con links de evidence locales.
6. Si cambia texto/gesto/Flash/audio, repetir su conjunto afectado y los gates de startup/save; no sustituirlos por benchmarks del shell. Cualquier diferencia semántica inesperada bloquea promoción.

Comparación visual: regiones/estado seleccionados antes de ejecutar, reloj o random fijado cuando se pueda. Si hay contenido dinámico inevitable, máscaras justificadas previamente; nunca enmascarar después el fallo observado. No establecer una tolerancia global 0.8/255 por el dato histórico de JPEG. Alpha y coordenadas siguen una aceptación distinta. Revisar input hit-test junto a cambios de texto/registro/nudge.

Comparación de rendimiento: mismo dispositivo y build, warm/cold diferenciados y carga térmica registrada. Umbral inicial propuesto de investigación: p95 ≤ baseline × 1.10, pico memoria ≤ baseline × 1.10 y sin crecimiento retenido tras ciclos; ratificar budgets por dispositivo/carga antes de hacerlos gate. FPS correcto no justifica degradación de input/audio ni supera límite de memoria del host.

## Casos nuevos obligatorios para gestor/perfiles

Estos son casos de aceptación a implementar junto con M1–M6; no se afirman ejecutados porque el gestor aún no existe.

| Caso | Resultado requerido |
|---|---|
| Originales completos, hashes conocidos y módulo/capacidades presentes | Perfil exacto y plan de lanzamiento determinista. |
| Renombrar exe/carpeta preservando bytes/dependencias | Misma identidad, sin usar basename como autoridad. |
| Mismo nombre, bytes de otra edición; o cambio de un byte en cast critical | Sin perfil exacto ni excepción del juego anterior. |
| Exe conocido + cast incompleto, distinto o duplicado en otro root | Incomplete/ambiguous; no casar archivos ajenos por nombre. |
| Dos juegos en selección | Particionar candidatos verificables o informar mixed-input; no combinar ni elegir el mayor. |
| PE x86 SDA sin película Director | Ruta de detector SDA/unknown, nunca DirectorRuntime como fallback. |
| Perfil/schema mayor, ABI desconocida, capability required faltante | Rechazo explícito antes de VM y preservación de instalación activa. |
| Manifest afirma Huntsville pero source fingerprints no coinciden | Rechazo de autoridad de identidad; integridad ZIP no acredita título. |
| Director sintético con Palatino/Tekton o member name igual | No recibe calibraciones/offsets de Huntsville. |
| Perfil con nudge/evidence inexistente o fuera de límites | Rechazo; sin código/script arbitrario ni relajación de integridad. |
| Dos instalaciones usan misma registry key/resource name | Sin cruce de save, media cache, audio, trace ni presentation. |
| Cambio de ZIP por cover/compression, misma fuente | Identidad igual; migración de saves explícita y rollback, no unión automática. |
| Switch de juego mientras audio prepara o input down pendiente | Callbacks de generación anterior ignorados y recursos cerrados. |
| Fallo parse/start/import, proceso muerto o cancelar picker | Nada parcialmente activo; legacy package/saves recuperables. |

## Compatibilidad cruzada y promoción de catálogo

Huntsville se mide con su edición real y un Director sintético que tenga fuentes/miembros homónimos. Añadir otra película Director con su evidencia permite decidir qué ajustes son de versión y cuáles de título; no declarar Prime Suspects/Ravenhearst compatibles por ser de la serie.

Cuando haya segundo módulo: SDL/SDA sintético con atlas/Ogg/eventos; Huntsville con la misma secuencia; Mystery P.I. slice frente al original. Después otro fixture SDA sin componentes MPI para demostrar que el módulo no está codificado exclusivamente para The Vegas Heist.

Escala de promoción: research → identified → parser-verified → runtime-slice → device-verified → full-verified, registrando cada scope de manera separada. En el catálogo se proyecta a los estados especificados en el perfil; una observación estática solo permite identified/research. Para full se exige matriz de escenas/puzzles/final, configuraciones, variantes de input, guardados y audio de la edición. No generalizar la aceptación entre regiones o versiones sin evidencia.

No se repitieron instrumentación API 26/33/36 ni pruebas WSA durante esta auditoría. Se ejecutarán al implementar cambios que afecten esa frontera y antes de promoción. No se ha comparado una candidata de generalización porque el código sigue intacto; esta ausencia es un estado explícito, no un resultado positivo.
