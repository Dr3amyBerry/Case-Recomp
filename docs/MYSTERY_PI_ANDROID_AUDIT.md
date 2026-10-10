# Auditoría SDA Android: relevo de implementación

Fecha: 2026-10-09. Referencia: `e38b8f9`, rama `intento-geminis`, sincronizada con origin al comenzar. Alcance vinculante: `GUION ACTUAL.MD`, conservado localmente. **Vegas no está acreditado como campaña completa ni como reproducción fiel en Android.**

## Hallazgos del estado recibido

| Componente / función | Clasificación inicial | Evidencia y efecto |
|---|---|---|
| `SdaRng.next`, `sdaShuffle`, selección | REAL para los vectores diferenciales; PARCIAL en reanudación | LCG y shuffle coinciden con Python recuperado. `SdaScene.restore` no restaura `deckCursor`/`deckOrder`. |
| `SdaScore.found/miss/hint` | REAL en pruebas escalares; PARCIAL en campaña | Cantidades recuperadas y vectores diferenciales. Falta conectar pistas/puerta de primera penalización y coleccionables. |
| `SdaClock.advance` | REAL en escalares; PARCIAL en integración | Eventos previos al incremento; campaña usa `isExpired` inmediato en vez del evento nativo. Activity recorta dt a 0,1 s: puede perder tiempo real. |
| `SdaMotion`, `SdaTargetRows` | REAL en vectores escalares; PARCIAL en render/restauración | Actualización depende del número de frames. Restore no guarda target/step ni recalcula dimensiones escaladas. |
| `SdaXui`, `SdaLevels`, loader bonus | PARCIAL | XUI tenía filtros; niveles y bonus usaban otros factories, valores por defecto y errores silenciados. |
| `SdaStrings` | PARCIAL | Parser de tablas básico; atlas/kerning y localización UI Android pendientes. |
| `SdaContent.open/read/verifyAll` | PARCIAL | SHA por entrada existe. Manifest sin límite, tamaño total/recuento/rutas/version/duplicados no validados; identidad por nombre declarado no acredita edición. |
| `SdaScene.click/advance` | PARCIAL | Hit-test alfa y retiro real. Historial existe, pero campaña cachea escenas y limpia historia entre niveles; restore admite referencias inválidas y no restaura deck. |
| `SdaCampaign.advance` | PARCIAL, defecto crítico | Sumaba `ids.size` por fila: un objetivo compuesto adelantaba el bonus. Python suma una fila. |
| `SdaCampaign.rank/levelSummary` | SIMULADA para rangos; fórmula de velocidad incorrecta | Rangos dependen del índice/longitud. `004189c0` compara puntos e hitos, con límites estrictos; `00418600` usa segundos/minutos restantes ×100, no ×10. Parámetros de rango deben seguirse hasta el perfil antes de fijar su integración. |
| `SdaTileRotGame`, `SdaTileSwapGame` | PARCIAL | Celdas responden a coordenadas. Rotación nativa retira filas/columnas (`00459ce0`, `0045a780`), permite ambos botones y tiene puntuación/animación adicional; implementación lo omite. Shuffle Python usa `random.Random`, Kotlin LCG: bonus no tiene paridad acreditada. |
| `SdaWordSearchGame.clickPixel` | SIMULADA | Ignora x/y y marca siguiente palabra. Loader usaba lista Vegas fija o primera línea, no referencia `text` del recurso. |
| `SdaJigsawGame.clickPixel` | SIMULADA | Cualquier clic coloca siguiente pieza; no geometría/arrastre real. Python también contiene bandeja/tolerancias que requieren corroboración independiente. |
| `SdaMasterRiddleGame` | SIMULADA | Listas de pasos inventadas y clic genérico; no ocho adivinanzas ni mecanismos nativos. Python tiene tres clases, pero posiciones/orden de fase 3 no son evidencia independiente por existir comentarios. |
| `SdaCampaign.restore` | PARCIAL | Recrea bonus desde semilla y descarta estado guardado. Falta validar coherencia, edición, contadores, fase y referencias. |
| `SdaProfileStorage`, `PrivateSdaRepository` | PARCIAL | Perfiles JSON disponibles; escritura no atómica/path de ID sin validar. Checkpoints usan claves globales y launcher no selecciona perfil. |
| `SdaGameView` | PARCIAL / SIMULADA en puzles | Escenas muestran bitmaps, resto rectángulos/textos. Sólo ACTION_DOWN; mapa vertical deja escenas 6–9 fuera de 600 px. `step` dispara callback cada frame de modal; input de final permite salto genérico. |
| `SdaLauncherActivity.launchSdaPackage` | PARCIAL | Carga de checkpoint pero view recibe escena nueva, distinta de escena restaurada del campaign. Sin atlas/audio. Guarda en UI thread, no actualización de perfil. |
| `HomeActivity` / catálogo | PARCIAL | Separación Director/SDA existe; usuario elige importador SDA y nombre del paquete no verifica edición. Director no debe cambiar para resolverlo. |
| `package_vegas.build_package` | PARCIAL | Extrae texturas, omite otras URI (audio/atlas), LEVELS_2 y errores se imprimen y continúan. |
| Prueba `campaign_complete_playthrough_from_level_1_to_finale` | SIMULADA | Escribe contadores/fase/índice y usa solve. Decoder sustituye alfa por cuadrados opacos. Es distinta según existan ZIP privados. |
| Pruebas Android en WSA / rendimiento | NO VERIFICADA en este relevo | ADB detecta dispositivo 127.0.0.1:58526. No se instaló/ejecutó APK en esta fase; no se declara prueba visual ni campaña Android. |
| Audio, coleccionables, UI original completa | NO IMPLEMENTADA en integración | Recursos/código nativo identificados, sin cadena de uso jugable verificable. |

## Recursos: comprobación local

El ZIP recibido `local-output/vegas_full.zip` tiene 2.994 entradas, 2.993 archivos declarados y 31.532.794 bytes comprimidos. Una exploración de atributos `uri` en nodos texture/sound/music/font de sus XML encuentra **176 referencias ausentes**. Esta exploración parcial ya contradice que el paquete esté completo; aún falta cierre de includes y referencias indirectas. Datos/artes originales y salida detallada permanecen privados.

## Corrección del fallo de CI y alcance de las nuevas pruebas

La prueba recibida no es un recorrido integral. En CI, sin ZIP privado, usa tres niveles sintéticos; en local utiliza 25. La fórmula por nivel asigna al último nivel sintético el rango 11, mientras la aserción exige 15; con 25 niveles llega a 15. Por eso las suites locales pasan y no refutan el fallo reportado de CI. Cambiar el esperado ocultaría esa dependencia y conservaría un rango sin respaldo nativo.

Se reemplaza por pruebas deterministas de recursos propios: clicks alfa → retiro de componentes → una fila → modal → otra escena → bonus mediante clics de celdas → siguiente nivel. No se fuerza `completedObjects`, `phase`, `levelIndex` ni se usa `solveBonus`. **Es un recorrido unitario de dos escenas y un bonus de rotación parcial, no los 25 niveles de Vegas.** La corrección de rangos originales continúa pendiente en un perfil de edición.

## Cambios verificables de este bloque

- Contar una vez cada objetivo compuesto después de retirar su fila; conservar puntos/tiempo en el modal y reanudación del fixture.
- Rechazar entrada de escenas/retorno al mapa desde bonus; un clic fuera de fase no vuelve a sumar su recompensa.
- `SdaXml.parse` centraliza límites de bytes, UTF-8 estricto, DTD/entidades, resolver externo prohibido, profundidad y número de nodos para escenas, niveles y bonus.
- Niveles rechazan valores requeridos ausentes, NaN, escenas vacías/duplicadas y rutas inválidas; no inventan tiempos/objetivos ni eliminan niveles silenciosamente.
- Loader rechaza recursos inexistentes/extensiones desconocidas, elimina fallback TileRot y lista fija Vegas; usa la referencia de palabras del XUI. Las **mecánicas** WordSearch/Jigsaw/fin todavía son simuladas y deben reconstruirse.
- Un recurso bonus inválido no modifica la fase ni escena del checkpoint completado.

## Continuación exacta

1. `SdaBonus.kt`: `SdaWordSearchGame.clickPixel`, `SdaJigsawGame.clickPixel`, `SdaMasterRiddleGame`, `SdaBonusLoader` y restauración. Contrastar `bonus.py` con Ghidra antes de portarlo; sustituir las pruebas que celebran clics arbitrarios en `SdaBonusUnitTest.kt`.
2. `SdaCampaign.kt`: `rank`, `levelSummary`, `confirmLevelComplete`, `advance`, `snapshot/restore`; `SdaScene.kt.restore` y `SdaTargetDeck` para recuperación exacta y validación transaccional. Separar parámetros Vegas de core.
3. `package_vegas.py.build_package`, `SdaContent.open/verifyAll`, `PrivateSdaRepository`: cierre de dependencias, seguridad e instalación/perfil/checkpoint independiente.
4. `SdaGameView.drawMap/drawBonus/drawFinale/onTouchEvent/step`, `SdaLauncherActivity.launchSdaPackage/frameCallback/autoSave`: render original, eventos completos, escena restaurada, audio y lifecycle.
5. Arnés privado completo, matriz de 25 niveles y prueba APK por ADB; auditoría regresión Director y CI. Todos los niveles siguen **NO VERIFICADOS** como ejecución completa Android.

## Registro de pruebas

- Baseline: engine/app ejecutados con `--rerun-tasks`, BUILD SUCCESSFUL (recursos privados presentes); no acredita campaña real.
- RED del nuevo bloque: 7 pruebas, 6 fallidas por causas esperadas (contador, fases/recompensas, fallback y XML).
- GREEN focalizado: 7 pruebas aprobadas.
- Recorrido privado JVM nuevo: 1 prueba ejecutada, no omitida; nivel 1 con 18 filas completas por inputs alfa reales → snapshot JSON/restauración idéntica → bonus de rotación cargado. No resuelve el bonus ni acredita Android.
- Cierre: `gradle -p android --no-daemon :engine:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`: engine 128 y app 23, cero fallos/errores/omitidas; APK debug compila. Suite Python: 69 pruebas, OK. Los tests de mecanismos simulados recibidos siguen pendientes de sustitución.
- `git diff --check`: limpio. Código Director y archivos ajenos excluidos del commit; la regresión JVM existente está incluida en las suites. No se acredita regresión visual/sonora en dispositivo.
- CI remoto pendiente después del push; no se presenta la ejecución local con recursos privados como CI equivalente.

No se han cambiado Director, el perfil Huntsville, APK/release aprobados ni los archivos locales ajenos. No se ejecutó el EXE ni se usó input del escritorio. Objetivo global **pendiente**.

## Segundo bloque: recuperación del estado de bonus

`SdaBonusLoader.restore` recupera rotaciones, permutación y selección del tablero, palabras/piezas del modelo recibido y recompensa; contrasta identidad, semilla, dimensiones y contenido con los recursos. `SdaCampaign.restore` prepara escenas/bonus/reloj en variables temporales, valida objetivos retirados, fases y referencias, y sólo modifica la sesión al terminar. Tres pruebas nuevas fallaron antes de corregir y ahora pasan (rotación parcial, intercambio/selección parcial, payload inválido sin mutación).

Esto acredita **persistencia del modelo actual**, no fidelidad de WordSearch/Jigsaw/fin. `restoreLegacyFinale` conserva checkpoints diagnósticos del final recibido; su aceptación no valida las reglas inventadas. La migración al final real seguirá siendo un cambio explícito que preserve los archivos antiguos. Persistencia del deck, estado completo de motion y namespace de instalación/perfil siguen pendientes.

Cierre local del segundo bloque: engine 131, app 23, cero fallos/errores/omitidas; APK debug compilada. Python no se modificó desde sus 69 pruebas aprobadas. La CI de `af4b96c` terminó correctamente: Android, empaquetado reproducible, instrumentación API 26/33/36 y Python. Es instrumentación de la aplicación base; **no** prueba SDA integral ni recorrido privado de Vegas. CI del nuevo commit deberá comprobarse por separado.


## Bloque limitado: rotación y recorrido nivel 1 → nivel 2

Rama confirmada por el usuario: `intento-geminis`; sustituye la solicitud inicial de `main`. Se conserva todo cambio local ajeno y no se modifica Huntsville, Director ni APK/release aprobados.

Implementado: `SdaTileRotGame.rotatePixel` distingue giro primario/ secundario, impide clics fuera del tablero o sobre fichas retiradas, bloquea filas/columnas correctas y acumula 250 puntos base por línea. Referencia independiente de la implementación: exportaciones locales `00459c20`, `00459ce0`, `0045a780`, `00459bb0` y geometría de `TILEROTGAME01.TRG`; no se publican exportaciones ni recursos comerciales. `SdaCampaign.clickBonus` acredita solamente el incremento de puntos de línea y evita recompensas repetidas mediante sus fases existentes. `SdaGameView.drawBonus` oculta las fichas retiradas y `onTouchEvent` distingue botón secundario. El render sigue siendo diagnóstico, sin imagen original del puzle.

Guardado: `SdaBonusLoader.restore`/`SdaTileRotGame.restoreLocks` recuperan y validan fichas retiradas y puntos de líneas. Checkpoints anteriores sin esos campos infieren filas/columnas correctas sin añadir puntos retrospectivos. Se mantienen los archivos antiguos. Hay pruebas de reanudación exacta, rechazo de locks inconsistentes sin mutación y ausencia de duplicación de puntos.

Verificación nueva: RED del bloqueo (1 fallo esperado) → GREEN; suites finales engine **134**, app **23**, cero fallos, errores u omitidas; `:app:assembleDebug` y `git diff --check` correctos. `SdaPrivateLevelJourneyUnitTest.private_first_level_solves_rotation_resumes_and_advances_to_level_two` ejecutó recursos privados reales: 18 filas por hits alfa → tablero 4×6 resuelto exclusivamente por entradas → snapshot/reanudación de fila parcial y resultado → mapa del nivel 2 → snapshot/reanudación del nuevo nivel. Resultado del modelo: 192500 puntos y 69.761215 segundos acumulados, sin asignar contadores/fases ni utilizar `solveBonus`. Esto **no** acredita puntuación nativa exacta ni ejecución Android. CI del commit previo `abfb4f5` terminó correctamente; la CI del nuevo bloque queda pendiente.

Estado exacto: nivel 1 y transición al 2 **funcionales en el recorrido JVM privado**; rotación **funcional con reglas de retiro recuperadas, aún parcial en fidelidad**; otros bonus **incompletos**; niveles 2–25 **sin recorrido completo**; desenlace **simulado/incompleto**. Los datos originales confirman 25 niveles; último: pista 25, 90 objetivos, 3120 segundos, 9 escenas. Esto no determina ni valida su condición final de desenlace.

Continuación exacta, en orden:

1. `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaBonus.kt`: inicialización RNG de `SdaTileRotGame`, umbral/bonus rápido de `rotatePixel`; reglas reales de `SdaTileSwapGame.clickPixel`, `SdaWordSearchGame.clickPixel`, `SdaJigsawGame.clickPixel` y `SdaMasterRiddleGame`. No usar sus clics arbitrarios como evidencia de finalización.
2. `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaCampaign.kt`: `levelSummary`, `confirmLevelComplete`, `rank`, `advance` y transiciones finales; corregir puntuación/tiempos con evidencia y conservar perfiles. La recompensa fija y el speed bonus recibidos siguen sin certificar.
3. `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt`: `drawBonus`, `drawFinale`, `onTouchEvent`; `SdaLauncherActivity.kt`: carga de escena restaurada, callbacks y `autoSave`. La ruta no se ha demostrado en dispositivo.
4. `android/engine/src/test/kotlin/org/rigorcore/caserecomp/sda/SdaPrivateLevelJourneyUnitTest.kt`: ampliar el recorrido con inputs reales a los bonus siguientes y al desenlace; conservar la distinción entre fixture, JVM privado y APK. `SdaCampaignUnitTest.kt`: guardado y puntuación en cada nueva mecánica.

No se ejecutó el original ni se controló mouse/teclado del usuario. El objetivo global sigue pendiente.


## Bloque siguiente: intercambio de fichas

`SdaTileSwapGame` ahora usa el shuffle de sufijos recuperado de `00457500`: rechaza intercambio consigo mismo y repite si deja la identidad actual correcta. La semilla de la prueba representa un estado RNG explícito; la continuidad global del RNG nativo entre fases todavía debe verificarse. `00457ed0` y `00458e30` confirman que **se retira cada ficha correcta individualmente**, a diferencia de las líneas de rotación. El modelo bloquea esa ficha, impide seleccionarla/moverla y suma 250 puntos base por colocación. El componente de velocidad nativo de 150 puntos aún no se aplica ni se certifica. El render diagnóstico deja de dibujar fichas retiradas.

`SdaTileSwapGame.restoreLocks` y `SdaBonusLoader.restore` conservan locks, selección, permutación y puntos de colocación; rechazan locks sobre identidades incorrectas, selección retirada y puntajes incompatibles. La migración de checkpoints anteriores infiere posiciones correctas retiradas excepto la selección activa, conservando puntos sin crédito retroactivo. `SdaCampaign.clickBonus` acredita diferencias de puntaje de colocación una sola vez y mantiene las fases de resultado/siguiente nivel.

Pruebas: dos reproducciones RED (mezcla nativa y retiro individual) → GREEN. Fixture de campaña completa el intercambio por inputs, reanuda un tablero parcial, verifica recompensa única y llega al siguiente nivel. Prueba privada independiente carga **el bonus del nivel 4**, resuelve sus 36 fichas exclusivamente con coordenadas y verifica checkpoint/reanudación exactos: 9000 puntos base de colocación. **No** constituye un recorrido de los niveles 2/3/4 ni confirma la puntuación total nativa. Las dos pruebas privadas se ejecutaron sin omisiones. Cierre local: engine **138**, app **23**, cero fallos/errores/omitidas, APK debug compilada y `git diff --check` limpio.

Estado actualizado: rotación e intercambio tienen entradas, retiro y guardado funcionales en pruebas JVM; ambos siguen parciales en fidelidad/render/timing. WordSearch, Jigsaw, final y campaña 1–25 siguen incompletos. No se tocó Director/Huntsville, recursos privados ni cambios ajenos; no se ejecutó el original ni se usó input del escritorio.

Continuación inmediata: `SdaBonus.kt` (`SdaWordSearchGame.clickPixel`, generación de tablero desde `0045c0e0`, `SdaJigsawGame.clickPixel`, scoring/timing y `SdaMasterRiddleGame`); `SdaCampaign.kt` (`levelSummary`, `confirmLevelComplete`, `rank`, `advance`); `SdaGameView.kt` (`drawBonus`, gestos/render originales). `SdaPrivateLevelJourneyUnitTest.private_level_four_swap_bonus_solves_through_inputs_and_resumes` es el probe independiente de intercambio; conservar su alcance explícito al ampliar el recorrido real. Objetivo global pendiente.


## Bloque de progresión: bonus de tiempo y timeout

`SdaCampaign.speedBonus` centraliza la aritmética recuperada de `00418600`: segundos restantes truncados, componente minuto/segundo (módulo 3600), multiplicador 100. `levelSummary` y `confirmLevelComplete` usan el mismo cálculo. No se incluyen horas completas ni se usa el multiplicador 10 recibido. Esto corrige la **fórmula escalar**; las condiciones nativas de elegibilidad, separación de relojes de bonus y otros componentes de puntuación aún requieren validación. No acredita puntuación total original.

`SdaCampaign.advance` consume `SdaClockEvent.Timeout` en vez de consultar `isExpired` después de almacenar elapsed. Se mantiene la comprobación nativa previa a actualizar tiempo y su compuerta de cambios de segundo módulo 60, ya recuperadas en `campaign-clock-slice/0041dc40`. No se cambia `SdaClock` ni el motor Director.

Pruebas RED: 3 fallos esperados por fórmula ×10 y timeout inmediato. GREEN y cierre: engine **141**, app **23**, cero fallos/errores/omitidas; APK debug compilada y diff check limpio. Casos de fórmula: segundos fraccionarios, límite de minuto, hora completa y varias horas. Recorrido de fixture hasta resultado verifica guardado/reanudación, bonus mostrado = acreditado, tiempo acumulado y rechazo de segunda confirmación sin mutación. El timeout conserva exactamente el checkpoint al reanudar y detiene actualizaciones posteriores. Las dos pruebas privadas siguen pasando sin omisión; el resultado del modelo del recorrido nivel 1→2 cambia a **305000 puntos**, 69.761215 segundos acumulados. El score total todavía no se certifica como original.

Sopa de letras: las exportaciones `0045d140`/`0045d200` permiten contrastar ocho direcciones, límites y solapamientos compatibles; `0045d760` distingue misma celda, líneas rectas/diagonales y selección inválida. Pero `0045c0e0` perdió operaciones FPU/parámetros y marca bloques retirados, por lo que no sustenta una generación determinista fiel. **No se implementó ni se publicó un generador sustituto**. Siguiente paso exacto: recuperar esa función con parámetros/FPU correctos desde el proyecto Ghidra existente, sin ejecutar el original; después sustituir `SdaWordSearchGame.clickPixel`, su estado y `SdaGameView.drawBonus/onTouchEvent` con pruebas negativas y reanudación del tablero real. El Python recibido usa generación ajena; `tools/sda-prototype/campaign.py.level_summary/confirm_level_complete` también conserva el multiplicador antiguo y no debe tratarse como referencia nativa en este punto.

Continúan pendientes los bonus WordSearch/Jigsaw, scoring completo/ranks/historia, desenlace real, importación/perfil y recorrido integral Android. Objetivo global pendiente; cambios locales ajenos conservados. No se ejecutó el original ni se controló el escritorio.


## Recuperación de instrucciones y mezcla de rotación

El usuario autorizó explícitamente leer solo el programa ya almacenado en el proyecto Ghidra. La revisión automática rechazó inicialmente `-process` por confundir su nombre de programa con abrir el EXE. Se comprobó la semántica en `support/analyzeHeadlessREADME.html` instalado (líneas 175–182); después se permitió la lectura con `-readOnly -noanalysis`. **No** se importó, abrió ni ejecutó el archivo original del directorio del juego. La lectura del proyecto generó listados privados, sin modificar el programa almacenado. No se utilizó input del escritorio.

Herramienta genérica nueva: `tools/ghidra/ExportStoredInstructions.java`. Se verificó compilación/ejecución y los registros de éxito por función: `0045c0e0` 919 instrucciones, `0046e990` 7, `00459400` 582. Las dos primeras ejecuciones fallidas por ruta/argumentos no se contaron como recuperación; Ghidra puede devolver cero con errores de script. Los listados y constantes extraídas permanecen en `private/mystery-pi-vegas/research/wordsearch-instructions`, excluidos de Git.

Implementado: `SdaRng.nextScaled` reproduce la multiplicación por la constante float almacenada conservando precisión amplia del producto x87. `SdaTileRotGame` usa `floor(scaled * 4)` y vuelve a sortear cuando sale cero, según las instrucciones/constantes de `00459400`, reemplazando `%3+1`. El estado inicial del RNG sigue siendo explícito; su continuidad nativa global entre fases no queda acreditada. Se conservan los tableros grabados por `SdaBonusLoader.restore`, que recupera las rotaciones del checkpoint en lugar de reemplazarlas por la nueva mezcla.

Verificación: fixture de 24 cuartos de giro reconstruido independientemente en Python a partir de las instrucciones/constantes, RED antes del cambio → GREEN. Caso límite CRT 32767 evita redondear prematuramente a float (que produciría 4), resultado correcto 3. Cierre: engine **143**, app **23**, cero fallos/errores/omitidas; APK debug compilada, `git diff --check` limpio. Ambos probes privados pasan: recorrido nivel 1→2 y bonus independiente de intercambio del nivel 4. No equivale a campaña completa Android ni validación dinámica original.

Continuación concreta WordSearch: las instrucciones completas ya están disponibles en el directorio privado indicado. `0045c0e0` consume RNG para fuentes al crear cada casilla; reduce el pool a **10**, no seis, y luego ordena/invierte antes de intentar colocaciones. No portar el corte de seis ni la mezcla Python. `0045d140` valida ocho direcciones/solapamientos; `0045d200` llama a `0045d0d0` por carácter, consumiendo RNG al variar mayúsculas/minúsculas. La generación restante rellena caracteres y rechaza palabras accidentales con `0045d540`/`0045d660`. Debe recuperar el comparador de `0045cd70`, el consumo completo y las selecciones/gestos antes de sustituir `SdaWordSearchGame.clickPixel` y su snapshot/renderer. No se añadió todavía un generador sustituto. Estas observaciones cambian el siguiente paso; no certifican el bonus como jugable.

No se modificaron perfiles/guardado durante la investigación independiente: siguen pendientes su escritura atómica y aislamiento por instalación/perfil. Director/Huntsville y archivos ajenos intactos. Objetivo global pendiente.


## WordSearch: núcleo funcional, pendiente de integración

Lectura autorizada del proyecto Ghidra existente con `-readOnly -noanalysis`: comparador de longitud, variación de case y eventos de selección recuperados. `tools/ghidra/ExportStoredInstructions.java` añade lectura de memoria almacenada acotada a 4096 bytes por raíz y 12 raíces, verificada en Ghidra. Listados y bytes permanecen privados. No se importa, abre ni ejecuta el EXE original ni se controla el escritorio.

`SdaWordSearchBoard.kt`: consumo RNG por casillas, reducción del pool a 10, orden estable por longitud seguido de inversión, ocho direcciones, solapamientos, variación ASCII de case y relleno con rechazo local de palabras accidentales. `begin`/`end` exigen extremos de una colocación registrada, aceptan sentido inverso y rechazan repeticiones, posiciones inválidas y selecciones de una sola casilla. Solo acredita 250 puntos base por palabra. Los límites diagnósticos fallan explícitamente; no hay tablero de emergencia inventado.

Verificación: RED (clase inexistente) → GREEN; engine **146**, app **23**, cero fallos/errores/omitidas; APK debug compilada y diff limpio. `SdaPrivateLevelJourneyUnitTest.private_wordsearch_resources_generate_and_accept_only_recorded_paths` genera los siete recursos WSG con sus pools originales sin normalizar case, semilla 8, comprueba las palabras sobre sus casillas y resuelve mediante extremos registrados rechazando duplicados. Pasan también los probes privados anteriores. No constituye comparación dinámica original ni recorrido Android. Locale/codificación nativa de acentos, continuidad RNG global y bonus rápido de 150 puntos siguen pendientes.

Continuación exacta:
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaWordSearchBoard.kt`: checkpoint completo de tablero/colocaciones/selección/retiros y comprobación de locale/RNG/bonus rápido.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaBonus.kt`: sustituir clic arbitrario de `SdaWordSearchGame.clickPixel`, preservar case en `SdaBonusLoader.load`, restaurar tablero completo en `restore`.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaCampaign.kt`: integrar selección y crédito incremental; comprobar elegibilidad nativa de recompensa final.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt`: confirmar geometría XUI, conectar down/move/up/cancel y dibujar tablero/selecciones reales.
- `android/engine/src/test/kotlin/org/rigorcore/caserecomp/sda/SdaPrivateLevelJourneyUnitTest.kt`: recorrido de campaña nivel 2 con guardado/reanudación y transición; el nuevo probe es solo del núcleo.

Estado: nivel 1→2 probado en JVM; rotación/intercambio parciales; WordSearch tiene núcleo funcional pero su clase de juego conserva clics simulados. Jigsaw, niveles posteriores y desenlace incompletos. Task 3 y objetivo global abiertos. Huntsville, Director, APK aprobada y cambios ajenos intactos.


## WordSearch integrado: recorrido privado nivel 1 → 2 → mapa del 3

`SdaWordSearchGame` sustituye el clic que encontraba automáticamente la siguiente palabra por selección de coordenadas (`beginPixel`, `movePixel`, `endPixel`, cancelación). `clickPixel` conserva una alternativa de dos toques sobre extremos reales; no ignora coordenadas. `SdaBonusLoader.load` preserva el case del pool y obtiene origen de la imagen correspondiente en `ENVS.MSE`, dimensiones de las texturas del control WSG y recursos normal/selected/locked. No se fijan palabras de Vegas ni dimensiones del tablero en el minijuego. La dependencia de `ENVS.MSE`/prefijo `wordsearch_` sigue pendiente de perfilar por edición; no se declara compatibilidad con otros títulos.

Checkpoint completo: `SdaWordSearchBoard.state` y constructor de restauración leen tablero lógico/visible, colocaciones, palabras retiradas, RNG final y selección sin regenerar desde semilla. Validan tamaños, caracteres, recorridos rectos/diagonales, correspondencia con el pool y extremos. `SdaWordSearchGame.state` añade geometría, extremo actual y puntos base; `SdaBonusLoader.restore` valida identidad. Un test restaura con una semilla distinta y obtiene el tablero grabado, demostrando que no depende de regeneración. La campaña rechaza un tablero inválido sin modificar su estado. Los checkpoints simulados antiguos sin tablero no pueden certificar progreso auténtico: se rechazan y el launcher conserva el guardado, muestra error y evita iniciar/autoguardar silenciosamente una campaña nueva. No se eliminan archivos privados.

`SdaCampaign.beginBonusSelection/moveBonusSelection/endBonusSelection/cancelBonusSelection` conectan el gesto con el crédito incremental de 250 puntos base por palabra y la transición a resultado una sola vez. `SdaGameView.drawBonus` dibuja las texturas privadas del tablero, caracteres y estado de selección/retiro; gestos down/move/up/cancel mantienen el puntero primario y el escalado. `SdaLauncherActivity.wireViewCallbacks` guarda tras entradas del bonus. Las letras siguen utilizando una fuente Android: atlas originales, líneas/animaciones y presentación completa pendientes. La recompensa final de 25000 heredada sigue sin certificar elegibilidad nativa; no se presenta el total del modelo como score original exacto. Locale/acentos, RNG global y bonus rápido siguen pendientes.

Verificación:
- RED por APIs inexistentes → GREEN; engine **147**, app **23**, cero fallos/errores/omitidas; APK debug y APK de instrumentación compiladas. `git diff --check` limpio.
- `SdaPrivateLevelJourneyUnitTest.private_first_two_levels_solve_bonuses_resume_and_advance_to_level_three`: objetivos por hits alfa reales en niveles 1 y 2, rotación por inputs, WordSearch por extremos reales, selección parcial y resultado reanudados exactamente, rechazo transaccional de tablero inválido, mapa del nivel 3 alcanzado. Sin escribir contadores/fases/índices ni usar solve. Modelo: 651800 puntos, 176.49323 segundos acumulados; no acredita fidelidad total de scoring.
- Los siete recursos WSG privados se cargan, resuelven con coordenadas de píxel y restauran durante selecciones; no se suben sus datos. Bonus independiente de intercambio nivel 4 también pasa.
- WSA Android **API 33**, por ADB: instalación debug y ejecución exclusiva de `SdaWordSearchInstrumentationTest`, **OK (1 test)**. Fixture propio en ZIP temporal, tres tamaños (800×600, 1600×1200, 1000×600), Canvas real, arrastre, cancelación, restauración a mitad del gesto, palabras, puntuación incremental y siguiente nivel. No usa ni altera repositorios/perfiles/partidas privadas. Es cobertura Android de estas APIs y View, no prueba de recursos comerciales, importación o campaña completa Android. El paquete independiente de Huntsville no se reinstaló.
- CI del commit anterior `5baf3df`: Android y Python finalizados correctamente. CI de este bloque pendiente de publicación/ejecución.

Estado vigente: niveles 1 y 2 completos y transición al 3 **probados en JVM con recursos privados**; WordSearch funcional en lógica/gestos/restauración y Android sintético, **PARCIAL** en fidelidad visual/nativa. Nivel 3 y posteriores sin recorrido completo; Jigsaw y desenlace siguen simulados/incompletos. Task 3 y objetivo global abiertos.

Continuación exacta:
1. `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaBonus.kt`: reconstruir `SdaJigsawGame` con evidencia y sustituir su clic arbitrario; formato JSW, colocación/arrastre/soltado/tolerancias y checkpoint. Es el próximo obstáculo de campaña.
2. `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaWordSearchBoard.kt` y `SdaWordSearchGame`: verificar locale/codificación/RNG global y temporizador rápido antes de afirmar paridad original.
3. `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt`: atlas/líneas originales en `drawBonus`, renderer real de las otras familias; probar recursos privados por Android sin sustituirlos por fixtures.
4. `android/engine/src/test/kotlin/org/rigorcore/caserecomp/sda/SdaPrivateLevelJourneyUnitTest.kt`: ampliar el recorrido normal desde mapa del 3, con Jigsaw genuino, guardado y transición. Conservar la separación entre pruebas sintéticas Android y privadas JVM.
5. `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaCampaign.kt`: gates de recompensa/rangos y final nativo; `SdaLauncherActivity`/`PrivateSdaRepository`: selección/restauración de escena, perfiles y escritura atómica. No considerar el rechazo seguro de un save como solución de aislamiento/migración.


## Jigsaw: núcleo de colocación y giro recuperado, integración pendiente

Se leyó únicamente el proyecto Ghidra existente con `-readOnly -noanalysis`: exportaciones genéricas acotadas `004382d0` (70 instrucciones), `004384e0` (47), `004365b0` (84), `00435f60` (396), tabla de clase y bloque de eventos `00436780`. Archivos y listados permanecen privados. No se abre/importa/ejecuta el EXE original ni se utiliza el escritorio.

Hallazgo independiente: la supuesta tolerancia Python de 20 píxeles no corresponde a `004382d0`. La función exige giro cero y centro de la imagen dentro del rectángulo destino: incluye límite izquierdo/superior y excluye derecho/inferior. `00435f60` añade 4/9 píxeles al rectángulo renderizado; los límites de aceptación restan 9/14. Resultado para máscara sin girar: centro = posición sostenida + mitad entera de máscara; mínimo destino+5, máximo exclusivo destino+tamañoMáscara−5. Destinos se trasladan por el origen de la imagen de fondo; el probe usa un mismo marco local. El dispatcher `00436780` utiliza primario para recoger/colocar, secundario para restar 90° y devuelve a bandeja tras intento fallido. No copiar la bandeja ni la tolerancia inventadas por Python. Se corrigen comentarios/documentación históricos; su implementación Python aún necesita reemplazo.

`SdaJigsawBoard.kt` implementa selección explícita de pieza pendiente, posición sostenida, giro lógico de cuarto, validación de colocación recuperada, retiro único y 250 puntos base por pieza. Generación: mezcla de cada posición con índice de todo el array y después `floor(scaled*4)` por pieza en el orden resultante; constante de 90° recuperada del proyecto. Un fixture propio calculado independientemente en Python comprueba orden, ángulos y RNG final. El núcleo modela el giro terminado; todavía no la animación de 0,25 s ni su bloqueo de entrada. No modifica ni sustituye todavía `SdaJigsawGame`.

Checkpoint del núcleo: definiciones, orden inicial, orientación, piezas retiradas, selección, posición sostenida y RNG final. Restauración valida referencias/permutación/ángulos/selección y utiliza datos grabados en vez de regenerar desde semilla. El probe cambia la semilla al restaurar y compara estado idéntico. La posición física/orden dinámico de bandeja, scroll y animaciones todavía no forman parte del checkpoint.

Pruebas: RED por clase inexistente → GREEN. Cuatro unitarias nuevas prueban límites, giro obligatorio, selección, duplicados, RNG y restauración/rechazo. `SdaPrivateLevelJourneyUnitTest.private_jigsaw_piece_kernel_rotates_places_and_resumes_all_masks` carga las 24 definiciones y dimensiones alfa privadas, selecciona/gira/coloca por APIs del núcleo y reanuda una pieza sostenida; 6000 puntos base, sin asignar retiros ni llamar solve. Es un probe del núcleo: **no** acredita hit-test de bandeja, clics de píxel, renderer, nivel 3 ni Android. Suite final engine **152**, app **23**, cero fallos/errores/omitidas; APK debug compila, diff limpio. Probes privados anteriores siguen pasando. CI de `27b2a98`: Android y Python completos con éxito; CI nueva pendiente.

Estado vigente: niveles 1 y 2 y mapa del 3 probados en JVM; WordSearch parcial en fidelidad; Jigsaw tiene núcleo funcional pero clase/UI recibidas siguen simuladas. Nivel 3 y posteriores, final y objetivo global incompletos.

Continuación exacta:
1. `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaBonus.kt`: `SdaBonusLoader.load/restore` debe recuperar piezas/imageinfos/texturas JSW y origen de fondo; reemplazar `SdaJigsawGame.clickPixel` por eventos que utilicen `SdaJigsawBoard` y checkpoint completo. No volver al contador genérico de 24 piezas.
2. `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaJigsawBoard.kt`: integrar selección por geometría real, giro/entrada durante animación, bandeja dinámica y score rápido recuperado. No convertir `select(id)` del probe en un supuesto hit-test de pantalla.
3. Proyecto/listados privados: `00436780` dispatcher completo, `00439160` inserción inicial, `00439090` retorno, `00439890`/`004399c0` geometría, `004394a0` scroll; estudiar solamente esos componentes imprescindibles antes de inventar posiciones. `00435f60` genera piezas mediante máscaras y emboss/shadow; renderer original pendiente.
4. `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt` y `SdaCampaign.kt`: recoger/mover/girar/colocar, render alfa y transición de resultado. `SdaPrivateLevelJourneyUnitTest` debe ampliar recorrido normal desde mapa del 3 cuando esta integración exista.
5. `tools/sda-prototype/bonus.py::JigsawGame.place/click_pixel/load`: retirar la aproximación de 20px/bandeja fija con evidencia y crear referencia ejecutable independiente. El comentario corregido no cambia esas reglas.


## Jigsaw: bandeja y coordinación de entradas, integración pendiente

Referencia privada del proyecto Ghidra en lectura: `00439320`/`00439580` calculan separación mediante división entera altura/cantidad, centrado con dimensiones de imagen transformada y viewport acotado. El único caller recuperado (`00467000`) pasa padding 31; ENVS declara x=10,y=88,w=130,h=269,cuatro casillas. `00439230` retira la pieza y `00439090` la devuelve al índice anterior, ajustando la última página. `00436780` recorre componentes en orden inverso y exige canal alfa >0. Se verificó el exportador genérico `refs:` en Ghidra; listados y recursos permanecen privados.

`SdaJigsawTray.rectangles/hitTest/take/returnPiece/state` implementan esos mecanismos y guardan orden/viewport. El llamador debe proporcionar dimensiones y píxeles transformados reales: no se inventa el redondeo del escalado nativo 0.45. `SdaJigsawInteraction.pickPixel/dropHeld/rotateHeld/state` conecta bandeja y núcleo, conserva índice de devolución, selección, ángulos y posición, y rechaza checkpoints inconsistentes. Giro representa únicamente la rotación completada; animación nativa de 0.25 s pendiente. Ninguna clase nueva sustituye aún `SdaJigsawGame` en la campaña.

Verificación RED → GREEN: **155 engine + 23 app**, cero fallos/errores/omitidas; `:app:assembleDebug` correcto. Tres tests nuevos con imágenes alfa propias verifican geometría, transparencia, paginación, devolución, colocación y checkpoint a mitad de selección restaurado con otra semilla. No constituyen validación Android del rompecabezas ni comparación dinámica con el original. EXE, escritorio, Director, Huntsville y APK aprobada intactos.

Continuación exacta:
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaJigsawTray.kt`: conectar dimensiones/píxeles después del escalado y giro nativos; comprobar orden real de componentes.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaJigsawInteraction.kt`: adaptar movimiento centrado y bloqueo durante animación; mantener checkpoint coordinado.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaBonus.kt`: reemplazar clic artificial de `SdaJigsawGame`, cargar JSW/ENVS y restaurar interacción completa en `SdaBonusLoader`.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt`: composición original máscara/emboss/sombra, bandeja, giro y gestos; luego conectar campaña y guardado.
- `android/engine/src/test/kotlin/org/rigorcore/caserecomp/sda/SdaPrivateLevelJourneyUnitTest.kt`: extender recorrido privado al bonus del nivel 3 solamente después de integración auténtica.

Estado vigente: niveles 1→2→mapa del 3 probados en JVM, WordSearch Android sintético probado anteriormente; bonus Jigsaw de nivel 3, campaña posterior y desenlace del nivel 25 siguen incompletos. Task 3 y objetivo global abiertos.


## Jigsaw integrado: nivel 3 → mapa del 4, JVM y Android API 33

Se eliminó `SdaJigsawGame.clickPixel` que colocaba la siguiente pieza con cualquier clic y su prueba falsa de cuatro clics en (0,0). La clase nueva coordina bandeja/núcleo, selección por alfa de la imagen transformada, giro secundario, movimiento centrado y colocación. Solo la colocación válida retira la pieza y acredita 250 puntos base. Un clic en el tablero sin pieza seleccionada no avanza. Saltar conserva recompensa cero sin fabricar piezas colocadas. El checkpoint incluye interacción completa, origen, recompensa y marca explícita de salto; la restauración rechaza contadores simulados antiguos sin tablero genuino, que el launcher conserva al rechazar la partida.

`SdaJigsawResources.load` lee componentes/infos/texturas JSW y ENVS, obtiene el fondo correspondiente al bonus, suma su origen a los objetivos locales y recorta RGB según coordenadas de imageinfo. `00438000` usa un byte de la máscara como alfa: los JPEG son opacos en su canal alfa, por lo que se utiliza su intensidad en escala de grises. `SdaPixelSource.getArgb` es una capacidad explícita; fuentes que solo proporcionan alfa fallan al componer, sin sustitutos opacos. `SdaJigsawRaster` conserva píxeles ARGB y transforma las piezas. Arrows normal/disabled usan recursos originales. `SdaGameView` dibuja las piezas reales, bandeja, piezas retiradas y pieza sostenida; acepta movimiento/hover y selección/colocación mediante dos toques, giro secundario o botón táctil propio. `SdaCampaign` acredita puntos base incrementalmente y conecta la finalización con el resultado y siguiente nivel. El guardado del launcher recibe las entradas del bonus mediante su callback existente.

**Límites de fidelidad:** transformaciones raster usan una política explícita de prototipo (muestreo nearest y dimensiones truncadas), no se certifica equivalencia con la biblioteca gráfica SDA delegada por `00490770`. Relieve/sombra se cargan y verifican pero todavía no se componen. La animación de giro de 0.25 s y su bloqueo, el bonus rápido de 150, RNG global y elegibilidad de recompensa final heredada de 25000 siguen pendientes. El botón táctil de giro es adaptación Android; no se presenta como interfaz original. No se declara fidelidad visual completa ni finalización de la campaña.

Pruebas:
- RED→GREEN de recorte/intensidad de JPEG, rotación y composición; RED→GREEN al reemplazar prueba de clics arbitrarios por selección/giro/colocación reales. Un test adicional detectó pérdida de recompensa cero al restaurar un salto y acredita la corrección.
- `:engine:testDebugUnitTest` **157**, `:app:testDebugUnitTest` **23**, cero fallos/errores/omitidas. `:app:assembleDebug` y `:app:assembleDebugAndroidTest` correctos, diff limpio.
- `SdaPrivateLevelJourneyUnitTest.private_first_two_levels_solve_bonuses_resume_and_advance_to_level_three` ahora extiende el recorrido original a objetos/bonus del nivel 3 → reanudación con pieza sostenida y resultado → mapa del 4: 24 piezas, 6000 puntos base. Nombre histórico del test pendiente de actualizar. Puntuación de modelo 1074700, totalElapsed 321.5729; no es referencia de scoring nativo completo.
- Android WSA `127.0.0.1:58526`, API 33: `SdaPrivateJigsawInstrumentationTest.real_first_three_levels_and_jigsaw_view_inputs_reach_four` y `SdaWordSearchInstrumentationTest` **OK (2 tests)**. La primera utiliza el ZIP comercial exclusivamente local, sin recursos empaquetados/publicados. Preparación de niveles 1/2 por APIs y objetivos alfa válidos; nivel 3 por MotionEvents para sus objetos, Jigsaw montado en actividad Android, Canvas con comparación de píxeles opacos en bandeja, entradas primary/secondary/move para las 24 piezas, restauración en mitad de selección y resultado, y botón de siguiente nivel → mapa del 4. Escenas se seleccionan mediante API; no prueba todo el flujo de importación/launcher/perfiles ni constituye revisión visual humana. No fuerza fases, índices, contadores ni llama solve.
- Log privado `local-output/sda-jigsaw-android-final.log`. APK instalada únicamente en paquete de depuración `.synthetic.debug`; APK aprobada de Huntsville intacta. Copia privada de prueba en files/caserecomp-private-jigsaw.zip, sin sobrescribir perfiles/checkpoints existentes. El test privado requiere argumento `privateSdaPackage`; CI pública no lleva el ZIP y omite ese test opcional.

Continuación exacta:
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaJigsawRaster.kt::transform/crop`: contrastar muestreo/rounding y bordes con biblioteca gráfica almacenada; componer relieve/sombra sin inventar offsets.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaJigsawGame.kt::rotateHeld/clickPixel/state`: animación nativa y bloqueo, scoring rápido y continuidad RNG global.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaJigsawResources.kt::load`: mover bindings de edición ENVS/prefijos/padding a perfil cuando existan variantes verificadas.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt::drawBonus/onTouchEvent`: interacción de cancelación/multitouch, UI original completa y contraste visual humano. No se acredita arrastrar-soltar al levantar el dedo: el flujo vigente recoge con un toque, mueve y coloca con otro.
- `android/app/src/androidTest/kotlin/org/rigorcore/caserecomp/app/SdaPrivateJigsawInstrumentationTest.kt`: recorrer también mapa/launcher/perfil reales, niveles posteriores y guardado a disco entre sesiones, sin alterar partidas ajenas.
- `tools/sda-prototype/bonus.py::JigsawGame`: sigue usando regla aproximada de 20px; no es referencia independiente ni paridad con Kotlin.

Estado vigente: primer nivel y bonus de rotación, WordSearch del segundo y Jigsaw del tercero tienen rutas funcionales probadas; progresión hasta mapa del 4 acreditada. Niveles 4–25, desenlace auténtico, paridad visual/audio/scoring y flujo completo de perfiles/importación siguen incompletos. Task 3 y objetivo global permanecen abiertos. Ningún EXE original ejecutado, control del mouse/teclado del usuario ni cambios en Director/Huntsville/recursos privados publicados.


## Progresión privada de 25 niveles y núcleo de la primera adivinanza

`SdaPrivateLevelJourneyUnitTest.private_campaign_inputs_reach_level_twenty_five_finale_entry_without_forced_state` comienza en el nivel 1, encuentra objetivos mediante píxeles alfa expuestos, resuelve las cuatro familias de bonus con entradas válidas y restaura cada resultado/transición. Llegó al resultado del nivel 25 y luego a la entrada **heredada** `FINALE_1`. No modifica índices/fases/contadores ni llama solve; no ejecuta los pasos inventados de MasterRiddle. **Es una ruta JVM del modelo recibido/corregido, no campaña completa Android ni prueba de fidelidad nativa:** siguen abiertos rank/gates, historia/RNG global, clocks/scoring y minijuegos/render/audio originales completos. La entrada al desenlace no certifica sus transiciones originales.

Matriz del recorrido JVM (todas las filas PARCIALES respecto a aceptación final; puntos/segundos son del modelo, no referencia del original). Cada fila tiene resultado y checkpoint restaurados. Evidencia Android previa solo cubre la ruta hasta el mapa del 4, con alcance descrito en la sección anterior.

| Nivel | Objetivos | Bonus | Puntos antes de confirmar | Segundos del modelo |
|---|---:|---|---:|---:|
| 1 | 18 | tilerot | 180000 | 69.761 |
| 2 | 27 | wordsearch | 482500 | 104.482 |
| 3 | 37 | jigsaw | 885500 | 145.080 |
| 4 | 45 | tilegame | 1362400 | 175.235 |
| 5 | 28 | wordsearch | 1788300 | 108.882 |
| 6 | 46 | tilerot | 2275400 | 180.434 |
| 7 | 56 | wordsearch | 2845300 | 215.348 |
| 8 | 38 | jigsaw | 3326200 | 146.919 |
| 9 | 28 | tilerot | 3687500 | 109.402 |
| 10 | 66 | wordsearch | 4276000 | 256.661 |
| 11 | 47 | jigsaw | 4877800 | 180.914 |
| 12 | 57 | tilegame | 5450700 | 222.267 |
| 13 | 37 | tilerot | 5905900 | 143.720 |
| 14 | 47 | wordsearch | 6346500 | 183.873 |
| 15 | 56 | tilegame | 6904100 | 217.747 |
| 16 | 38 | jigsaw | 7357300 | 148.879 |
| 17 | 76 | tilerot | 8016400 | 291.989 |
| 18 | 46 | wordsearch | 8580700 | 178.954 |
| 19 | 66 | jigsaw | 9161800 | 257.261 |
| 20 | 56 | tilegame | 9784000 | 216.748 |
| 21 | 76 | tilerot | 10460800 | 296.550 |
| 22 | 47 | wordsearch | 11009100 | 184.393 |
| 23 | 67 | jigsaw | 11609600 | 261.022 |
| 24 | 78 | tilegame | 12300400 | 302.711 |
| 25 | 90 | tilerot | 13141600 | 351.481 |

Hallazgo indispensable del desenlace: `firstriddle` en ENVS contiene **25 riddlepiece**, ocho con destino/caption y 17 sin target. El Python de ocho objetos omite señuelos. El primer probe falló al resolver targets vacíos; se corrigió para conservar los 25, rechazar un señuelo y resolver los ocho destinos. No se filtraron los distractores para hacer pasar el juego.

Referencia estática: factory `00468000` enlaza primera fase y límite default de 1500 s; vtable `0050808c`, slot 8 = evento `0044ddd0`, actualización `0044ce70`. Esas dos funciones no están reconocidas como funciones en el proyecto guardado: se extrajeron solamente 1520/3936 bytes almacenados y se decodificaron privadamente con Capstone. No se crearon funciones, importó ni ejecutó el original. El evento compara puntero relativo al origen del fondo contra hotspot half-open (`00451a40`), rechaza target nulo, exige placeorder igual al contador actual o -1. `0044e760` incrementa contador y comprueba itemstobeplaced; `0044e3c0`/`0044e620` retiran/devuelven al índice conservado. Listados/pseudocódigo permanecen privados en `research/finale-instructions` y `research/finale-exact`.

`SdaRiddleBoard.select/dropScreen/state` implementa ese núcleo después de completar las animaciones, con objetivos definidos por el llamador, señuelos explícitos, orden de bandeja e índice de devolución, historia de colocaciones y restauración validada. No inventa score, RNG, scroll ni recursos. El probe privado comprueba 25 bindings, ocho captions resueltas, ocho hotspots/order, selección pendiente restaurada y posiciones finales del desplazamiento declaradas en XUI (-713 hasta 0), quedando 17 señuelos. Los endpoints de desplazamiento se suministran al núcleo; no hay animación/controlador de scroll implementado. **No se integra aún en `SdaMasterRiddleGame` ni en campaña/Android.** Las tres fases de desenlace siguen incompletas.

Verificación: RED→GREEN del núcleo y rechazo de señuelos; engine **163**, app **23**, cero fallos/errores/omitidas; `:app:assembleDebug` correcto, diff limpio. Nueva ruta JVM privada de 25 niveles y probe de adivinanza pasan. No se repitió la instrumentación Android anterior: no hubo cambios en su vista/ruta en este bloque. CI Android y Python de `90021a0` completadas correctamente.

`ExportNativeSlice.java` añade modo genérico `only:<dirección>` para hasta 12 funciones ya reconocidas, sin expansión a llamadas/callers ni discover. RED por modo inexistente, rechazo comprobado de raíz no reconocida y GREEN final de dos helpers: ambos `decompileCompleted=true`, salida **exactamente dos funciones**. El exit code de Ghidra por sí solo no sirve: las ejecuciones con script error no se contaron como recuperación. Documentación de uso en tools/ghidra/README.md.

Continuación exacta:
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaRiddleBoard.kt`: núcleo listo para bindings, sin scoring/animación. Mantener señuelos, placeorder y coordinates relativas al fondo.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaBonus.kt::SdaMasterRiddleGame` y `SdaCampaign.kt::confirmLevelComplete/finishBonusInput/restore`: sustituir únicamente cuando existan controladores auténticos; hoy conservan desenlace simulado y reward/transiciones no certificadas.
- `android/app/src/main/kotlin/org/rigorcore/caserecomp/app/SdaGameView.kt::drawFinale/onTouchEvent`: integrar bandeja de 25 objetos, geometría/captions/scroll/fades/eventos reales; no portar los clics arbitrarios del Python.
- Referencia privada `finale-instructions/0044ddd0.capstone.txt`, `0044ce70.capstone.txt`: estados/timers de primera fase y devolución/scroll. `full-decompile/0044c3a0.c`: inicialización/mezcla/bandeja. Estos mecanismos faltan antes de una primera fase Android auténtica.
- `private_first_riddle_hotspots_order_and_recorded_scroll_endpoints` y `private_campaign_inputs_reach_level_twenty_five_finale_entry_without_forced_state` en `SdaPrivateLevelJourneyUnitTest.kt`: extender hacia desenlace real sin escribir estados ni certificarlos mediante la clase heredada.

Estado: progresión JVM hasta resultado 25 y entrada heredada del desenlace verificada; campañas posteriores al 3 no acreditadas por pantalla Android, desenlace completo no jugable auténticamente. Objetivo global abierto. Huntsville, Director, APK aprobada, partidas y cambios ajenos conservados.


## Primera adivinanza: bandeja y bindings preparados, Android pendiente

`SdaRiddleInteraction` conecta el núcleo de colocación con mezcla, cuatro casillas, viewport y entradas por coordenadas. Referencia `0044c3a0`: mezcla cada índice contra todo el array mediante RNG escalado × número total, sin rejection ni draws de ángulo. Se recuperaron **533 instrucciones** de esa función del proyecto Ghidra existente en lectura, con registro de éxito, sin importar/ejecutar EXE ni alterar funciones. Fixture independiente CRT para seis IDs/semilla 8: orden [2,3,0,1,4,5], RNG final 2831887398. Checkpoint restaura orden, RNG final, viewport y selección sin generar otra mezcla, incluso con semilla 999.

Geometría original de itemlistarea: x10/y116/w131/h273 y cuatro casillas de altura entera 68. El píxel sobrante y388 no pertenece a una casilla. `0044ddd0` selecciona por rectángulo completo de casilla, sin exigir alfa; esto difiere de Jigsaw. `0044eb00` centra imágenes transformadas, limita viewport a tamaño-4 y habilita flechas por límites. Retirar/devuelve al índice anterior, pero no incrementa viewport al volver una pieza al final como hace Jigsaw. Se rechaza desplazar mientras hay pieza sostenida. El componente trabaja en límites de animación completada: aún no representa los estados/timers de entrada, devolución/fade y desplazamiento de `0044ce70`.

`SdaRiddleResources.load(content, resource, controllerId, stringsResource)` obtiene IDs desde el perfil/llamador y lee el schema de primera adivinanza: 25 piezas/imágenes, ocho destinos/captions, 17 señuelos, tray, origen/fondo, destinos/fade/alpha/screenscrollup y límite default 1500 s de `00468000`. Comprueba las imágenes referenciadas mediante lectura y hash del paquete; no genera reemplazos. Devuelve URIs para el decoder/renderer existente; no dibuja ni publica esas imágenes. El probe privado compara los bindings con lectura independiente del XML, recorre casillas paginadas de la mezcla real, rechaza un señuelo, reanuda cada pieza seleccionada y acepta ocho destinos con los endpoints de scroll del recurso. Quedan 17 señuelos sin retirar.

RED→GREEN de dos tests propios y carga de recursos; cierre engine **165**, app **23**, cero fallos/errores/omitidas; APK debug compilada y diff limpio. Pasan nuevamente la ruta JVM 25 niveles y el probe privado de ocho objetivos. No se ejecutó una nueva prueba Android: `SdaMasterRiddleGame`, campaña y vista de desenlace no fueron integrados todavía. CI Android/Python de `8e8aaa1` success. No constituye desenlace jugable ni fidelidad visual/temporal completa. Huntsville/Director/APK aprobada/cambios ajenos intactos.

Continuación concreta:
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaRiddleResources.kt::load`: bindings disponibles para crear controlador de primera fase; imágenes se decodifican mediante SdaContent, no con sustitutos.
- `android/engine/src/main/kotlin/org/rigorcore/caserecomp/sda/SdaRiddleInteraction.kt::pickPixel/dropScreen/scroll/imageRect/state`: entradas y restauración disponibles; conectar reloj/animaciones y transformación original de imágenes.
- Referencia privada `finale-instructions/0044ce70.capstone.txt` y `full-decompile/00451f60.c`, `00452080.c`: temporización/estados y cambio entre imagen original y reducida. No portar tiempos arbitrarios.
- `SdaBonus.kt::SdaMasterRiddleGame`, `SdaCampaign.kt::confirmLevelComplete/restore/finishBonusInput`, `SdaGameView.kt::drawFinale/onTouchEvent`: siguen pendientes de sustituir el desenlace simulado por estos componentes y el resto de fases auténticas.

Objetivo global y Task 3 abiertos. La progresión JVM hasta resultado 25 sigue siendo parcial; no se acredita campaña Android completa ni desenlace original.


## Hito Android: Jigsaw del nivel 3 mediante pantalla (2026-10-09)

Se amplió `SdaPrivateJigsawInstrumentationTest.real_first_three_levels_and_jigsaw_view_inputs_reach_four`: la vista de objetivos del nivel 3 está montada en HomeActivity; el botón de objetos completados abre Jigsaw mediante MotionEvent y su callback, sin llamar directamente a startBonus en ese nivel. Se comprueban una a una las 24 colocaciones, 6000 puntos base, checkpoint con pieza sostenida y resultado restaurados sin cambios, y botón de continuar que alcanza MAP con clue 4. No se escriben fases, índices, contadores, ni se llama solve. Se conservan las comprobaciones de píxeles de piezas reales. No se modificó lógica de producción ni Huntsville/Director.

Verificación fresca: assembleDebug y assembleDebugAndroidTest correctos; instrumentación privada en WSA API 33 **OK (1 test), sin omisión**, con ZIP comercial local. Capturas revisadas de resultado del nivel 3 y mapa del nivel 4, guardadas exclusivamente en `local-output/jigsaw-level-three-result.png` y `local-output/jigsaw-level-four-map.png`. Log `local-output/sda-jigsaw-screen-android.log`. Capturas y paquete comercial no se incluyen en Git.

Alcance: prueba automatizada de pantalla de objetivos/bonus/resultado, no revisión visual humana ni recorrido completo desde importación. Niveles 1/2, selección de escenas y avance temporal se preparan mediante APIs normales; no mediante estados forzados. La bandeja se resuelve con coordenadas obtenidas del modelo. Persistencia comprobada por serialización/restauración en memoria, no cierre y reapertura de proceso. Continúan pendientes fidelidad de transformación/sombra/animaciones, puntuación nativa completa y desenlace.

Continuación: `SdaPrivateJigsawInstrumentationTest` para recorrer selección de escenas/importación y reanudación entre procesos; `SdaJigsawGame.rotateHeld`, `SdaJigsawRaster.transform` y `SdaGameView.drawBonus/onTouchEvent` para límites de fidelidad ya documentados. La integración auténtica de las adivinanzas sigue pendiente en SdaCampaign/SdaMasterRiddleGame; objetivo global abierto.


## Primera adivinanza conectada a campaña y pantalla Android (2026-10-09)

`SdaFirstRiddleGame` conecta bindings, bandeja mezclada de cuatro casillas, 25 imágenes originales, ocho destinos ordenados y 17 señuelos con entradas de selección/colocación. Flechas normal/disabled y papel/caption proceden de ENVS; coordenadas, hotspots, desplazamientos y textos se leen de los recursos. La pieza sostenida sigue el puntero y recupera sus coordenadas al restaurar. Al colocar se aplica el endpoint de desplazamiento declarado por el destino. `SdaCaptionRuns` interpreta avances verticales de una/dos cifras y newline según la referencia existente `tools/sda-prototype/fonts.py` (`004725b0/0047285f`); no interpreta los números como caracteres. Los marcadores dejan de aparecer literalmente en pantalla. Métricas Android y presentación siguen siendo provisionales.

El llamador proporciona `SdaRiddleBinding`; el adaptador de Vegas en `SdaLauncherActivity` suministra ENVS/firstriddle. El motor de campaña no contiene esos nombres para iniciar partidas nuevas. `confirmLevelComplete(content)` carga/valida primero la fase antes de acreditar resultado: si falta binding/contenido, conserva el resultado. `restore` reconstruye íntegramente la fase desde recursos y checkpoint, sin regenerar mezcla. `clickBonus` no concede una recompensa inventada ni cambia a fase 2 cuando se colocan los ocho destinos. El reloj del nivel anterior ya no se reutiliza para agotar una adivinanza: el límite default de 1500 s continúa como metadata, pendiente de reconstruir el controlador temporal auténtico.

`SdaMasterRiddleGame` queda exclusivamente como compatibilidad de lectura de checkpoints simulados antiguos: clics no alteran el estado y solve lanza unsupported. El skip de campaña se restringe a BONUS ordinario. Se eliminan las transiciones ficticias y el botón «COMPLETAR PASO» del desenlace. Las listas heredadas se conservan para validar los archivos recibidos, no para generar juego nuevo. Una fase heredada muestra que su controlador no es compatible. No se migran silenciosamente esos saves a objetos originales.

Evidencia ejecutada:
- RED por API de controlador/integración/parser inexistente → GREEN. Fixtures propias: coordenadas, rechazo de señuelo y orden incorrecto, selección/puntero restaurados, scroll, captions, imposibilidad de skip y de completar legado con clics arbitrarios. Resultado final sin binding preservado antes de acreditar puntos.
- `private_campaign_inputs_reach_level_twenty_five_finale_entry_without_forced_state`: recorre los 25 niveles y sus bonus mediante entradas normales, entra al controlador nuevo y coloca los ocho destinos usando flechas y coordenadas; restaura cada pieza sostenida y estado final. Quedan 17 señuelos y backgroundY=0, sin aumentar puntos durante la adivinanza. No completa campaña ni entra a fase 2.
- El recorrido anterior produce únicamente en `local-output/sda-earned-first-riddle.json` un checkpoint de entrada ganado, antes de resolver la adivinanza. `SdaPrivateFirstRiddleInstrumentationTest.earned_finale_checkpoint_resumes_and_eight_screen_placements_complete_first_phase` lo reanuda en Android y coloca los ocho objetos mediante MotionEvents de SdaGameView montada en HomeActivity; pagina, restaura pieza sostenida, comprueba callbacks de guardado y dibuja después de cada entrada. No asigna fases, índices, contadores ni usa solve. Este handoff JVM→Android no acredita recorrido Android de los 25 niveles ni reanudación por el launcher tras muerte de proceso.
- Cierre: engine **168**, app **23**, cero fallos/errores/omitidas; debug APK y test APK compiladas. WSA API 33: primera adivinanza y regresión Jigsaw nivel 3→4 **OK (2 tests)**. Logs privados `local-output/sda-first-riddle-final.log` y `sda-first-riddle-android-final.log`. Capturas entrada/final de adivinanza exclusivamente locales; inspección confirmó imágenes reales, papel y pistas legibles. CI Android/Python de `14bcb08` success; CI de este bloque aún por comprobar tras push.

Límites explícitos: controlador trabaja en límites de animación completada. Fades, estados de bloqueo, desplazamiento animado, diálogos de entrada/completado, PDA/indicator, audio, render nativo de fuente, RNG global y reloj propio siguen pendientes. Es primera fase funcional parcial, no fidelidad nativa íntegra. Tras ocho colocaciones permanece FINALE_1 resuelta y muestra siguiente fase pendiente: se evita inventar su transición. Fases 2/3 y finalización auténtica NO jugables todavía. Huntsville/Director/APK aprobada/releases/partidas/cambios ajenos intactos; recursos, pseudocódigo, capturas y checkpoint privados fuera de Git.

Continuación exacta:
- `SdaFirstRiddleGame.clickPixel/movePixel/state/load`: conectar estados/timers originales de `private/.../finale-instructions/0044ce70.capstone.txt`, retorno y scroll de `0044ddd0.capstone.txt` y helpers `0044e760/0044e3c0`; mantener señuelos y guardado real.
- `SdaCampaign.confirmLevelComplete/clickBonus/restore`: conectar diálogo final de primera fase y controlador auténtico siguiente cuando su evidencia esté recuperada; nunca restaurar las transiciones MasterRiddle ficticias.
- `SdaRiddleResources.load`, `SdaGameView.drawFinale/onTouchEvent`: completar los bindings visuales/PDA, animaciones, audio y métricas originales; hoy flechas se activan en DOWN como el resto de la interfaz provisional.
- `SdaPrivateLevelJourneyUnitTest.private_campaign_inputs_reach_level_twenty_five_finale_entry_without_forced_state` y `SdaPrivateFirstRiddleInstrumentationTest`: extender hacia fases 2/3 y desenlace final auténtico; prueba Android posterior debe partir del checkpoint ganado, no fabricar estado.

Objetivo global abierto; este bloque sustituye una parte simulada por interacción recuperada y verificable, no acredita finalización total.


## Segunda adivinanza: validación nativa y bindings recuperados (2026-10-09)

Referencia indispensable recuperada del programa ya almacenado en Ghidra, readOnly/noanalysis, sin importar/ejecutar EXE ni crear funciones: vtable 0050810c (40 bytes), dos referencias a 00451360 desde 00451160; evento 00450950 (2048 bytes), región de inicialización 0044f600/0044f630 (1984 bytes), update 00450140 (2064 bytes), 133 instrucciones de helper 00451160. Logs y Capstone privados en `local-output/sda-second-riddle-reference*.log` y `private/.../research/second-riddle-instructions`. Región 0044f600 empieza por consulta de tipo y contiene la inicialización 0044f630; no confundirlas. Salidas de exportador comprobadas individualmente, no solo exit code.

`00450c9c–00450d7c`: al soltar, obtiene dimensiones de la imagen original restaurada por 00451f60; forma rectángulo de ancho/alto 2*tolerance en puntero−mitadImagen−tolerance, resta origen del fondo y prueba destinationx/y truncados con 00451a40 half-open. Invirtiendo la comparación, el puntero válido está en **(destino+mitad−tolerance, destino+mitad+tolerance]**, no abs(distancia)<=30 ni hotspot del primer riddle. Placeorder coincide con contador o -1. `00451160` incrementa colocados y al llegar a itemstobeplaced entra en estado 7; update abre diálogo posteriormente. Las coordenadas/tolerancia fijas del Python SecondRiddleGame contradicen estos datos y no se usaron como verdad nativa.

`SdaRiddleDropZone.piece` convierte esa condición de destino a hotspot entero del núcleo existente, conservando los bordes asimétricos. `SdaRiddleResources.loadSecond` acepta explícitamente riddlephase2; el loader de primera fase continúa rechazando ese schema. Lee ocho piezas, destino/tolerancia/order, dimensiones originales mediante decoder, imágenes originales y URIs distintas de targettex/textureshowninpda, bandeja/flechas/fondo. Todos los recursos referenciados se verifican mediante SdaContent. `timeLimit` ahora es nullable: segunda fase sin límite inferido, pues factory **00467cc0 no lee timelimit**; primera mantiene el default respaldado por 00468000. No se aplica un reloj supuesto.

Pruebas: RED por convertidor inexistente → GREEN; crosscheck independiente con la expresión nativa original y 49 combinaciones de bordes para dimensiones impares; truncamiento de destinos negativos/fraccionales y rechazo de NaN/tolerancia cero. `SdaPrivateSecondRiddleUnitTest.original_eight_destinations_use_native_tolerance_and_resume_without_fake_mechanisms` usa XML y dimensiones privados, comprueba límites inferior excluido/superior incluido en las ocho piezas, colocación en orden inverso (todos los orders -1), paginación, retorno inválido y selección sostenida restaurada con semilla distinta. Decodifica también las ocho texturas de destino/PDA. Ocho piezas colocadas y bandeja vacía sin forzar campos del núcleo.

Cierre: engine **171**, app **23**, cero fallos/errores/omitidas; APK debug compilada, diff limpio y revisión de código sin hallazgos críticos/importantes. Logs `local-output/sda-second-riddle-final.log`. El recorrido privado de 25 niveles/primera adivinanza también pasó dentro de esa suite. No se repitió instrumentación Android: este bloque no modifica app, campaña ni entradas/render de primera fase. CI Android/Python de 5caa872 success; CI del nuevo bloque pendiente tras push.

**Estado:** segunda fase dispone de núcleo y bindings probados; todavía NO se puede iniciar/jugar desde Android. No se añade una transición artificial. `00412300` evento 0x3f7 (=1015, botón OK del diálogo de primera fase en XUI) solicita estado global 0xd, correspondiente a secondriddle/+648 en 00404220. Este enlace permite implementar la próxima transición con evidencia; aún falta vincular el diálogo y su temporización. Fases 2/3/desenlace completo siguen incompletos. Huntsville/Director/APK aprobada/cambios ajenos intactos; datos comerciales y listados permanecen privados.

Continuación exacta:
- `SdaRiddleResources.loadSecond` y `SdaRiddleDropZone.piece`: bindings/reglas listos para controlador de segunda fase. Reutilizar SdaRiddleInteraction, no volver a inventar tableros/tolerancias.
- `SdaCampaign`, `SdaFirstRiddleGame`, `SdaGameView`: conectar confirmación legítima de primera fase (ENVS riddlesdialogcompletecontainer, botón value=1015) al controlador siguiente, cargando antes de mutar/acreditar y conservando checkpoint.
- Referencia privada `00451360.c`: cambio a targettex por 00452020 y efectos especiales: cup modifica dos bandejas, hourglass y slotarm ajustan dimensiones/fotograma, clock/lever se reparentan conservando coordenadas. Estos efectos no están implementados ni pueden omitirse presentando el renderer como original.
- `second-riddle-instructions/bytes-00450950-2048.capstone.txt`, `bytes-00450140-2064.capstone.txt`, `bytes-0044f600-1984.capstone.txt`; `00451850.c`/00451050 para viewport y retorno; factory 00467cc0 para escala/bandejas/containerforplaced; 00412300/00404220 para diálogo/entrada. No reconstruir más grafo salvo función indispensable.
- `SdaPrivateSecondRiddleUnitTest`: ampliar desde kernel a campaña y pantalla y luego transición de segunda a tercera, manteniendo pruebas sin solve ni estados forzados.

Task 4 y objetivo global permanecen abiertos.


## 2026-10-09 — Atlas originales y primera comparación Windows real (PARCIAL)

El GUION ACTUAL.MD vigente autoriza ejecutar el original y utilizar el escritorio. Se abrió el juego original con Shell.Application, se observaron menú principal, Opciones (canceladas sin guardar), mapa nivel 1 y escena Vault con tutorial. Las capturas Windows son de ejecución real, no recursos extraídos. El perfil existente se conservó; no se forzaron niveles ni victorias.

Cambios: SdaAtlasFont implementa columnas alfa >4, charset UTF-16, truncado x87 del avance, kerning y alineación de cada línea conforme a fonts.py. SdaResourceCanvas dibuja los atlas originales declarados por XUI, sin copiar bitmaps por fuente. Fotografías/fondos y piezas grandes usan bilineal; UI y atlas conservan nearest. No se aumenta resolución ni se alteran recursos originales. El presupuesto genérico SDA admite atlas largos hasta 16384 por lado, conservando 16M píxeles totales; el límite anterior de 4096 rechazaba atlas originales de 4097–9639 píxeles de ancho.

Pruebas: 176 engine + 23 app, cero fallos/errores/omisiones; assembleDebug y assembleDebugAndroidTest correctos. Test privado SdaPrivateVisualInstrumentationTest aprobado en WSA (24.88s): mapa, escena y cuatro bonus, niveles 1–4 mediante entradas normales y bonus con MotionEvents; preparación de objetos por APIs de campaña. NO demuestra E2E de catálogo/importación/perfil ni recorrido Android de los 25 niveles. Revisión independiente de código: sin incidencias críticas/importantes.

Evidencias privadas: local-output/vegas-audit/comparison.html; coverage.json y coverage.csv inventarían 3707 bindings visuales de todos los documentos y 2928 dimensiones de imágenes, con cero errores XML; niveles 1–25 incluidos. Son inventario, NO certificación de estados. Ningún binding recibe APROBADO automáticamente. Capturas originales en windows/; capturas Android finales en android-after/files/; APK vegas-atlas-debug.apk SHA256 3a781b0d05cc1ffb0cab5f5b450bf65ac74bb081398e1c8fb93e8a863104615e. Antes en local-output/vegas-visual-wsa-final. HTML normaliza viewport y muestra diferencias/superposición de Android antes/después; sus métricas de cambio NO miden fidelidad Windows. La escena Windows inicial contiene tutorial y debe compararse con el mismo estado o documentar la diferencia.

Defectos observados: caption TIEMPO recortado también en Windows; Android carece de controles PISTA/MENÚ/PAUSA, indicadores de coleccionables/rango/perfil y ciertos captions de objetivos restantes; mapa presenta tonalidad distinta. Las filas de objetivos conservan fuente/geometry provisional pese a dibujarse con atlas. WordSearch conserva letras Android. Jigsaw conserva control de giro temporal y solapamiento RESOLVER/flecha. Botones no reproducen aún todos sus estados; animaciones y efectos siguen pendientes. Atlas corrige el solapamiento del bloque de misión del mapa, pero no constituye validación integral de fidelidad.

Continuación exacta: SdaResourceCanvas.font/label/button (estados y color), VegasVisualProfile.base/drawHud/drawMap (fuente de filas, objetivos restantes, controles e indicadores del PDA, tonalidad nativa), SdaGameView.drawBonus/onTouchEvent (atlas WordSearch y giro táctil separado de modo original), SdaPrivateVisualInstrumentationTest (registrar viewport/memoria durante proceso vivo), HomeActivity.selectSdaZip/onActivityResult y SdaLauncherActivity (recorrido E2E real). Desenlace: SdaCampaign.confirmLevelComplete y SdaGameView.drawFinale siguen pendientes de integración auténtica de las fases 2–3.

Rendimiento: dumpsys tras finalizar instrumentación indicó “No process found”; NO es una medición de RAM/FPS. Se conserva este fallo de observación. Medir durante la próxima ejecución viva, con baseline y comparación. Falta certificar todos los menús/estados, las 25 escenas-contextos, variantes de bonus, checkpoints entre procesos y regresión Android Huntsville. Director/Huntsville/main/APK aprobadas no se modifican. Objetivo global continúa abierto.


Verificación adicional del mismo bloque: test privado aprobado también con wm size 800x600 (23.944s) y capturas en android-800x600/files/. Aprobado además 1280x960/density240 (24.427s); se conservan sus resultados separados; el override de WSA se restaura después de cada ejecución. La matriz aún no certifica todos los estados ni todas las interacciones.

Medición con proceso vivo durante la prueba 1280x960: PSS total 91743KB (~89.6MiB), heap nativo asignado 41245KB, Dalvik asignado 6516KB. gfxinfo: 76 frames, 2 janky (2.63%), p50 14ms/p95 16ms/p99 150ms; métrica legacy 88.16% y alta latencia de entrada 68, que deben investigarse, no ocultarse. Evidencias meminfo-live.txt/gfxinfo-live.txt. Es muestra instrumentada puntual; falta baseline equivalente, carga/bitmap bytes y estabilidad prolongada. No se afirma 60FPS sostenidos.


## 2026-10-09 — Filas de objetivos PDA (bloque PARCIAL)

Se sustituye la fuente del reloj en los objetivos por el font original declarado en cada eyespyset. El perfil Vegas toma x/y/w/h/font de SCENE_*.MSL (edición observada: x0, y120, w150, h20), sin offsets adicionales en el núcleo. La proyección genérica SdaTargetPresentation conserva índice, caption de grupos y alfa/retirada del modelo; SdaResourceCanvas.label dibuja el atlas con opacidad y nearest. No cambia puntuación, temporizador, batching ni condición de victoria. El contador totalitems se dibuja también en escena; su caption del paquete difiere de la edición Windows observada (OBJ. TOTALES frente a OBJ. RESTANTES), pendiente de resolver por edición.

Pruebas: 177 engine +23 app, cero fallos/errores; APK y test compilados. La prueba WSA ampliada pasa (24.943s): clicks normales completan una fila, su animación existente la retira, y la captura real deja vacía esa fila manteniendo visibles las otras nueve. Incluye aserciones sobre píxeles dorados y viewport medido, con reintentos acotados para capturas incompletas del compositor WSA. El primer intento capturó un fotograma incompleto; comparación con render software diagnóstico y repetición confirmaron el estado correcto. Render software NO se presenta como evidencia WSA. La preparación/clock siguen siendo de componente, no E2E. Las cuatro familias de bonus vuelven a completarse normalmente en la misma prueba y se alcanza nivel 5.

Comparación privada local-output/vegas-target-rows/comparison.html: Windows original, Android previo, Android nuevo y retirada. El original fue dejado en PAUSA tras observar la escena limpia, sin encontrar objetos ni cambiar preferencias. Las listas Windows/Android difieren por selección aleatoria; no se afirma equivalencia de todos los píxeles. retirement-pixel-check.json registra slots: antes [153,227,243,77,145,182,173,158,201,103]; tras retirada [0,227,243,77,145,182,173,158,201,103]. APK vegas-target-rows-debug.apk SHA256 b580c317379f4d9b0d067d272ae2712b2888b8b688a0d5cb45bff5eed39b67a9. Director/Huntsville, main, APK aprobadas y cambios locales ajenos intactos.

Continuación: VegasVisualProfile.drawHud/base (controles e indicadores ausentes, captions de edición); SdaResourceCanvas.button (estados reales); SdaGameView.drawBonus (letras y lista WordSearch con font1/font2/wslabel, efectos y controles temporales); HomeActivity/SdaLauncherActivity (catálogo/importación reales y pruebas E2E); desenlace permanece pendiente. La corrección de filas no completa la auditoría visual ni los 25 niveles.


## 2026-10-09 — WordSearch: atlas y estados táctiles (PARCIAL)

VegasVisualProfile.drawWordSearch sustituye letras Android por el atlas declarado en WordSearchTiles.font1 (9639×51), con tiles normal/selected/locked originales y fuentes wslabel del PDA. La selección font1 y anclaje centro−5/top proceden de 0045c0e0/00461d80; no se publican los recursos ni el pseudocódigo. SdaResourceCanvas.atlasText ofrece el servicio genérico, sin IDs o coordenadas Vegas. El caché conserva únicamente el conjunto del bonus actual. No cambia tablero, gestos, puntuación ni reglas. No se aumenta la resolución real.

Verificación: 177 engine +23 app, cero fallos/errores/omisiones, APK y androidTest compilados. La aserción de tinta blanca falla con el renderer anterior (0 píxeles) y pasa con el atlas original. WSA a 800×600: 25.955s; ejecución final 1600×900: 26.962s, test code 0 (no omisión). DOWN/MOVE/UP seleccionan una palabra completa; se comprueba selectedCells contra la ruta, foundWords=1 tras soltar y se capturan normal/selected/dragging/locked. Las cuatro familias de bonus se completan por eventos Android y avanzan de nivel 1 a 5. Es prueba visual de componente, con preparación de objetivos por APIs: NO recorrido E2E de catálogo/importación ni campaña de 25 niveles. La compilación inicial usó por error el módulo inexistente engine-sda; se corrigió a engine antes de verificar.

Evidencia privada: local-output/vegas-word-atlas/comparison.html, coverage.json, build_report.py, android/files/, android-800x600/files/. El HTML diferencia atlas extraído, capturas WSA y Windows pendiente, e incluye antes/después, diferencia y superposición. APK vegas-word-atlas-debug.apk SHA256 1f87ca01d7046f02fa41913a74b1bd379393eb991d032be74c9017ec03c2a2c0. Ninguna captura/atlas comercial se publica. Revisión independiente del bloque: sin incidencias críticas/importantes.

Pendiente: capturar WordSearch original Windows, contrastar lista/espaciado y letras en sus estados; reproducir líneas animadas y marca de palabras completadas en PDA; probar variantes posteriores y recursos, memoria/rendimiento sostenido, catálogo/importación reales y guardado entre procesos. Los controles PDA ausentes, los estados de botones, los 25 niveles y desenlace siguen abiertos. No se considera WordSearch visualmente APROBADO solo por usar sus texturas.

Continuar en VegasVisualProfile.drawWordSearch/base/drawHud, SdaResourceCanvas.button, SdaGameView.onTouchEvent/drawBonus y SdaPrivateVisualInstrumentationTest; HomeActivity/SdaLauncherActivity para E2E real. Director/Huntsville/main/APK aprobadas y cambios ajenos intactos. Objetivo global activo.


## 2026-10-09 — Fondo estable del mapa: contraste Windows real (PARCIAL)

Causa confirmada: VegasVisualProfile.drawMap dibujaba map_backgroundstart (azul) permanentemente. ENVS.MSE declara background y goldbackground; 00469b60 los enlaza, y 0043f580/0043ff30 funden el primero hacia el segundo hasta dejar el dorado. Al volver desde la escena al mapa mediante mouse autorizado, la ejecución original confirma el estado estable dorado. El perfil ahora resuelve mapunderlay.goldbackground para ese estado. No modifica el núcleo SDA ni aplica tintado, filtros globales o aumento artificial de resolución. El fundido animado sigue PENDIENTE, no se presenta como implementado.

RED: la aserción instrumentada del fondo falla en (750,100), diferencias RGB [-139,-11,113] frente al recurso dorado. GREEN: 177 engine +23 app, sin fallos/errores/omisiones; APK y androidTest compilados. WSA real: 28.756s, test code0, mapa/escena y cuatro bonus con eventos Android; preparación de objetivos de componente, no E2E de importación. Tres muestras descubiertas de Android difieren de Windows [-1,0,0], [-2,-2,-1], [0,0,0]. Son verificaciones de color locales; NO prueban fidelidad de toda la pantalla.

Artefactos privados en local-output/vegas-map-background/: comparison.html, coverage.json, color-samples.json, build_report.py, android/, windows-map-stable-client.png, recursos extraídos etiquetados por separado y vegas-gold-map-debug.apk. SHA256 APK 8a5645f31ce3f329efbc1b1875760807eb48f8bae69fb2e5f6ac93f480abff11. PrintWindow devolvió negro al tener el juego enfocado; esos archivos se conservan como fallo de captura y NO se usan para validar. La evidencia Windows procede exclusivamente de su rectángulo cliente visible 800×600, sin otras aplicaciones. El original quedó en el mapa, cuyo reloj permaneció detenido; no se resolvieron objetos ni se cambiaron preferencias.

HTML incluye original Windows, Android antes/después, recursos azul/dorado, diferencia y superposición. Se documentan las diferencias de reloj y elementos ausentes del PDA. No se declara APROBADO el mapa: falta fundido, controles/indicadores, todos los layouts/niveles/estados, alfa/textos y E2E. Revisión independiente: sin errores importantes. Continuar en VegasVisualProfile.drawMap/base, SdaResourceCanvas.image/button y SdaPrivateVisualInstrumentationTest; recuperar período/activación completos antes de animar. Director/Huntsville/main/APK aprobadas y cambios ajenos se conservan. Objetivo global permanece activo.


## 2026-10-09 — Pausa original de escena (PARCIAL)

Se observa y captura nuevamente la pausa real del original Windows. El primer intento conservado mostraba el mapa (cursor movido durante navegación); NO se utiliza como evidencia de pausa. Referencia válida: local-output/vegas-pause/windows-scene-paused-retry.png, rectángulo cliente exclusivo 800×600. El original queda pausado sin resolver objetivos ni cambiar preferencias.

VegasVisualProfile carga pdadownpausebutton y su overlay eyespypauseoverlay desde ENVS.MSE: frame rgba(0,0,0,165), dos labels y atlas originales. SdaResourceCanvas.frame es una capacidad genérica; posiciones, recursos y captions permanecen en el perfil. SdaGameView implementa pausa transitoria de presentación para escenas: congela step (reloj y animaciones), consume el gesto completo de reanudación y evita click-through. SdaLauncherActivity reutiliza autoSave al cambiar pausa. No se altera la fase de campaña ni se reinventa la mecánica. La pausa UI no se serializa; el checkpoint conserva el elapsed existente. Restauración entre procesos continúa NO VERIFICADA.

RED específico: pause must freeze clock expected0 but5. GREEN: 177 engine +23 app, cero fallos/errores/omisiones; APK y androidTest compilados. WSA30.209s, test code0: pulsar pausa, 5s de step sin cambiar elapsed/sinceFound/puntos, soltar, capturar overlay, reanudar sin click-through y avanzar1s. Aserciones visibles comprueban tinta blanca del título y alfa165 en tres regiones contra la captura anterior. La misma prueba completa cuatro familias de bonus con MotionEvents y avanza1→5; preparación de objetivos mediante APIs, no E2E de importación/campaña.

Comparación privada local-output/vegas-pause/comparison.html y coverage.json: Windows/Android, antes/pausa/reanudación, diferencia y superposición normalizadas. Bounds de tinta blanca del título: Windows [317,254,653,278], Android [317,254,652,278]; la diferencia de1px en borde derecho no prueba igualdad total de tipografía/alfa. Objetivos aleatorios y reloj difieren. APK vegas-pause-debug.apk SHA256 b6ebaec077deb9a43ca6ed1781e224c568ae95b6555006c4dd8a93b8efe6b0d2. Revisión independiente sin incidencias críticas/importantes. Capturas/recursos/APK permanecen privados.

Pendiente: estados presionado/hover de pausa, pausa en bonus y otros contextos, menú/pistas/coleccionables del PDA, E2E real, guardado entre procesos, auditoría25niveles y desenlace. Continuar en SdaResourceCanvas.button, VegasVisualProfile.base/drawHud/pauseRect, SdaGameView.onTouchEvent y SdaLauncherActivity.wireViewCallbacks; tests SdaPrivateVisualInstrumentationTest. Director/Huntsville/main/APK aprobadas y cambios ajenos intactos. Objetivo global activo.


## 2026-10-09 — Estados originales del botón PAUSA (PARCIAL)

Referencia Windows real: hover, mantener botón presionado, soltar dentro y arrastrar fuera antes de soltar. El original no pausa en DOWN: solo UP dentro activa el overlay; UP fuera cancela. Capturas exclusivas del cliente del juego en local-output/vegas-button-states/windows-pause-*.png. Se deja el original nuevamente pausado; no se encuentran objetivos ni se alteran preferencias.

SdaButtonPresentation recupera estados nativos0–4 desde bindings XUI: texnormal/texhover/texpushed/texdisabled, fuentes por estado y fallback a font base (00487e56), offsets globales más captionoffset únicamente presionado (0048d003/004882e9). Un tex de variante ausente no se inventa ni sustituye silenciosamente. SdaResourceCanvas.button dibuja la variante y centra el caption en la geometría del control, usando las dimensiones máximas de sus texturas como el constructor original. SdaVisualProfile.pointer permite que cada perfil aplique los estados; Vegas conserva sus IDs y recursos. SdaGameView mantiene captura del puntero de pausa, cancela en UP fuera/CANCEL y activa al liberar dentro; el gesto no pasa a objetos. Hover real Android también actualiza el estado.

Verificación: RED con versión anterior falla en original pause activates on release. 179 engine +23 app, cero fallos/errores/omisiones; APK y test compilados. WSA final32.253s, test code0: hover, presionado sin pausar, arrastre fuera/cancelación, liberación dentro/pausa y reanudación; reloj/animaciones y alfa/título conservan sus verificaciones. Mapa, escena, retirada de fila y las cuatro familias de bonus continúan pasando. Es prueba de componente/pantalla con objetivos preparados por APIs; NO E2E de catálogo ni campaña25niveles. Una compilación inicial falló por smart cast nullable entre módulos; se corrigió con variable local antes de compilar la APK comprobada. Revisión independiente sin errores importantes.

Comparación privada comparison.html/coverage.json/build_report.py incluye ROI PAUSA (72,557)-(144,600), lado a lado Windows/Android y estado cancelado. MAE del recorte hover4.795, presionado3.141 en escala RGB0–255; Android normalizado desde viewport(15,23)-(785,600) con nearest. Estas métricas incluyen remuestreo y no certifican igualdad exacta ni todo el PDA. APK vegas-button-states-debug.apk SHA256 a71cd10c7ebbe511ba93e123625b8c4ef43a27b547313552dd8be2362d4feb2c.

Solo se valida el gesto de PAUSA en escena; MAPA/RESOLVER conservan por ahora su activación previa en DOWN y quedan pendientes de contraste/control completo. Estados disabled/normal de todos los controles, otras densidades/contextos, menús/pistas/indicadores, guardado entre procesos,25niveles y desenlace siguen abiertos. Continuar en SdaButtonPresentation, SdaResourceCanvas.button/rect, VegasVisualProfile.buttonState/base/drawHud, SdaGameView.onTouchEvent/onHoverEvent y SdaPrivateVisualInstrumentationTest. Director/Huntsville/main/APK aprobadas y cambios locales ajenos intactos. Objetivo global activo.


## 2026-10-09 — MAPA: gesto y navegación originales (PARCIAL)

Windows confirma hover/presionado de MAPA y navegación solo al liberar dentro; arrastrar fuera antes de liberar mantiene la escena. Se capturan ambos estados y el mapa estable. El primer fotograma tras liberar fue negro durante transición; se conserva como captura inválida y no se usa para validar. El original queda pausado, sin encontrar objetos ni cambiar preferencias.

SdaGameView reutiliza captura de puntero para PAUSA/RETURN_MAP, sin duplicar el motor ni introducir IDs de Vegas. Al soltar se comprueba nuevamente rectángulo/fase disponibles y se invoca el callback existente; UP fuera/CANCEL no pasa a objetos. Renderer/bindings de estados permanecen en el perfil/recursos.

RED: MAPA must activate on release expectedSCENE butMAP. GREEN: 179 engine +23 app sin fallos/errores/omisiones; APK/test compilados. WSA34.973s, test code0, hover/presionado/cancelación/navegación con capturas reales. Regresión de pausa, fila retirada, cuatro bonus por MotionEvents y avance1→5 aprobada para alcance de componente, objetivos preparados por APIs; no E2E de importación ni25niveles.

Evidencias privadas local-output/vegas-map-controls/comparison.html, coverage.json, android/, windows-map-*.png y vegas-map-controls-debug.apk. SHA2560fc4f2d334819029ecca84c9008155ab763cf61473d39c6f6447d842b5555193. ROI(16,388)-(134,418), MAEhover3.999/presionado4.510 RGB0–255 tras normalizar viewport Android medido con nearest. Objetivos/reloj difieren; no se certifica igualdad total de pantalla. Revisión independiente sin errores importantes.

Pendiente: transición animada, otros contextos/resoluciones, RESOLVER y menú/pistas/indicadores del PDA, catálogo/importación/perfiles reales, guardado entre procesos,25niveles y desenlace. Continuar en SdaGameView.controlRect/onTouchEvent, VegasVisualProfile.base/drawMap/drawHud, SdaPrivateVisualInstrumentationTest; HomeActivity.selectSdaZip/onActivityResult/playSda y SdaLauncherActivity para E2E real. Director/Huntsville/main/APK aprobadas y cambios ajenos intactos. Objetivo global activo.


## 2026-10-09 — Catálogo/importación reales y restauración entre procesos (PARCIAL)

Se ejecuta la ruta mediante eventos ADB sobre la interfaz Android real: catálogo, tarjeta Vegas, Importar de nuevo, DocumentsUI/Descargas, selección del ZIP privado, regreso y Jugar (SDA). SdaLauncherActivity restaura la escena slots del nivel 1. No se prepara el modelo ni se fuerzan objetivos/niveles para esta prueba. El menú principal y selección de perfiles originales no aparecen: NO IMPLEMENTADO. La tarjeta usa una portada RealArcade incorrecta: DEFECTUOSO, pendiente de corregir. No se acredita campaña completa ni fidelidad visual de todas estas pantallas.

Se pausa, sale y fuerza el cierre de la APK experimental. Tras abrir catálogo y lanzar de nuevo, el PID cambia de 7404 a 7795; logs confirman restauración. Se comparan los checkpoints persistidos en SharedPreferences: escena, fase, nivel, puntuación 5000, objetivos, semilla y demás valores se conservan. Solo avanzan clockElapsed, scenes.slots.elapsed y scenes.slots.sinceFound, todos 2,14419 segundos (tolerancia 0,0001), transcurridos antes de volver a pausar. La pausa de presentación no persiste; no se afirma que el juego reabra pausado. Restauración funcional comprobada para esta escena; mapa, bonus, resultados y desenlace siguen NO VERIFICADOS entre procesos.

Antes de importar se respaldan recursos/preferencias SDA. Al concluir, con la APK detenida, se restaura exactamente el XML original y se comprueban byte a byte los tres archivos del respaldo. No se toca Director ni se eliminan paquetes. Un toque durante desplazamiento no abrió launcher: launcher-after-stop.png muestra catálogo y no es evidencia de escena restaurada. El directorio profiles vacío tampoco acredita guardado; se utilizan los checkpoints reales de SharedPreferences.

Evidencia privada: local-output/vegas-e2e-import/comparison.html, coverage.json, process-restore-check.json, preservation-check.json, performance.json, capturas Android, logs y respaldo. No se publican ZIP, capturas ni partidas. Medición de sesión real: PSS total 56949 KiB, RSS 160304 KiB; SdaLauncherActivity 2390 frames, un janky (0,04%), p95/p99 5 ms. Es una muestra, no comparación antes/después ni estabilidad prolongada. APK probada: la de b1fbe82, vegas-map-controls-debug.apk; no hay cambio de motor en este bloque documental.

Continuar en HomeActivity.selectSdaZip/onActivityResult/playSda (portada y navegación), SdaLauncherActivity y PrivateSdaRepository (menús/perfiles y más contextos de persistencia). PDA, variantes, 25 niveles y desenlace mantienen sus pendientes. Director/Huntsville/main/APK aprobadas y cambios ajenos intactos. Objetivo global activo.


## 2026-10-09 — Portada Vegas: origen corregido y verificado en catálogo Android

La portada incorrecta observada en el recorrido real provenía de tools/sda-prototype/package_vegas.py: leía distributor.jpg desde una ruta fija. El catálogo y el importador mostraban correctamente esa portada errónea; no se modifican HomeActivity, Director, Huntsville ni el núcleo SDA. El empaquetador Vegas ahora resuelve la primera imagen de fondo del mainmenu en (0,0) y su textura desde ENVS.MSE: mm_background.jpg (800×600). Genera una miniatura PNG dentro de 320×240 con Lanczos y conserva proporciones. Es una miniatura de un recurso original extraído, no una captura del ejecutable ni una reconstrucción del menú completo. No implica aumento real de resolución.

RED sintético falla al acceder al archivo externo distributor.jpg. GREEN: dos regresiones de origen/proporción y 71 pruebas Python de SDA completas, sin fallos. Paquete comercial privado verificado: 2993 entradas con longitud/SHA correctos; solo difieren cover.png y manifest.json frente a vegas_full.zip. Los otros 2992 recursos permanecen iguales. En WSA, mediante catálogo y DocumentsUI reales, se importa el nuevo ZIP, se observa la portada correcta y se abre/pausa la escena slots guardada con puntuación 5000. Al terminar, se restaura el XML original y se verifican byte a byte los tres archivos del respaldo; ambos paquetes permanecen disponibles. La aplicación queda detenida.

Evidencias privadas: local-output/vegas-cover/comparison.html, coverage.json, metrics.json, package-verification.json, preservation-check.json, capturas antes/después y vegas-menu-cover-full.zip. ROI Android [528,161,748,326], 220×165; MAE RGB 5,747 frente a miniatura remuestreada con bilinear. Incluye diferencias de muestreo/encaje y no demuestra igualdad exacta ni fidelidad de toda la pantalla. Estado APROBADO únicamente para origen y aparición de la portada tras importación; menú/perfiles originales siguen NO IMPLEMENTADO, campaña 25 niveles y comparación completa NO VERIFICADO.

No se genera otra APK para este bloque: no cambió el código Android; se probó la APK b1fbe82 ya instalada. Para usar la portada corregida hay que importar el nuevo paquete privado (los paquetes antiguos conservan sus bytes). Revisión independiente sin incidencias importantes. Continuar en build_package/test_package_vegas para empaquetado, y VegasVisualProfile/SdaLauncherActivity para menú/PDA, controles pendientes y auditoría visual general. Director/Huntsville/main/APK aprobadas y cambios ajenos intactos. Objetivo global activo.


## 2026-10-09 — Cabecera original NIVEL en PDA de escena

Defecto confirmado: drawHud omitía cluelabel; el original Windows muestra NIVEL: 1 sobre el reloj, mientras la captura Android anterior tenía cero tinta de texto en esa región. ENVS.MSE aporta posición (10,42), tamaño 132×22 y atlas fnt_pdainfo_lrg. El formato nativo de 004433d0, slot 0, es "%s: %d". VegasVisualProfile.levelLabel reutiliza esos datos y currentLevel.clue para escena; también corrige el separador omitido en el mapa. No se introducen offsets arbitrarios ni se altera el motor SDA/Director o Huntsville.

RED real WSA: original PDA level header missing: 0. GREEN: 179 engine +23 app sin fallos/errores/omisiones, APK y test compilados; WSA 34,817 s, código de test 0, con cabecera, pausa, estados de MAPA, fila retirada y cuatro bonus con MotionEvents. Continúa siendo prueba de componente/pantalla con preparación de objetivos mediante APIs, no E2E de los 25 niveles.

Se captura de nuevo el cliente Windows original, tras reanudar y antes de volver a pausa sin encontrar objetos. Comparación privada en local-output/vegas-pda-level/comparison.html y coverage.json, métricas en metrics.json. Android normalizado desde viewport (15,23)-(785,600) hacia 800×600 con nearest. ROI (10,42)-(142,64): ambos límites de tinta [43,6,92,14], 115 píxeles claros Windows y 114 Android, MAE RGB 3,969. El remuestreo y colores difieren ligeramente; no se certifica igualdad exacta ni el resto de la escena. Cabecera nivel 1 APROBADA para este alcance; mapa PARCIAL y niveles 2–25 NO VERIFICADOS visualmente.

APK experimental vegas-pda-level-debug.apk SHA256 5a903c762ea5c9a53f353c10bba6b0d8ee27ad0316307384a0e74ceda28aa0a3. Capturas, recursos y APK permanecen privados. Revisión independiente sin incidencias importantes. Pendientes del PDA: pista y recarga, menú, llaves/fichas y sus contadores, nombre/rango, cabecera de bonus, animaciones y todos los estados/resoluciones. Observación nueva: durante la pausa Windows desaparecen las filas de objetivos, a diferencia de Android; requiere contraste de activación y documentación/corrección propia, no aprobación de la pausa completa.

Continuar en VegasVisualProfile.base/drawHud/drawBonusBase, SdaPrivateVisualInstrumentationTest y SdaLauncherActivity. Para pistas revisar únicamente los handlers imprescindibles ya exportados (eyespyhint y score.hint), sin inventar reglas ni mostrar un botón sin interacción comprobada. Director/Huntsville/main/APK aprobadas y cambios ajenos intactos. Objetivo global activo.


## V3 grouped bonus composition (2026-10-09) ? PARCIAL

VegasVisualProfile now draws the original anonymous minigame overlay background/frame, four HOWTOPLAY headings, TileRot/TileSwap empty-PDA panels, goal/instruction captions and the active reference thumbnail from ENVS.MSE. Coordinates and resource IDs remain exclusively in the Vegas profile. Jigsaw removes the provisional rotate button/counter when a visual profile is active; a second finger rotates the held piece, while native secondary-click rotation remains available. This is an Android touch adaptation, not an original Windows gesture. Objective captions disappear during pause and return on resume.

Validation: 202 JVM tests; debug and instrumentation APK builds; private WSA component test PASS in 37.278 s. The fixture launches on Android display 0 to match UiAutomation capture, independently of the Windows host monitor. Assertions cover original background samples for all four bonuses, two-finger Jigsaw rotation, existing bonus completion/input checks and pause caption visibility. This is component coverage with legitimate model progression, not catalogue-to-campaign E2E. Director/Huntsville code is unchanged; fresh full Huntsville UI regression is still pending.

Private artifacts: local-output/vegas-v3-bonus/{comparison.html,block-result.json,android/,grouped-build.log,grouped-instrumentation.log,vegas-v3-bonus-debug.apk}. APK SHA256: 906e794bd03bee92f7257f9a8db4f87e517c0c9915af735266883d654c1d381d. HTML identifies extracted-resource provenance and does not claim Windows bonus comparison.

Remaining visible defects: SOLVER overlaps the Jigsaw upper tray arrow and TileRot/TileSwap goal text; nested WordSearch PDA instructions are still absent; the Jigsaw dimmed full-image guide lacks native confirmation; tile relief/selection textures and animations remain incomplete. Continue in VegasVisualProfile.drawBonusBase/drawTileBonus/drawWordSearch and SdaGameView.drawBonus/onTouchEvent. All four families remain PARCIAL until original Windows-equivalent states and these defects are resolved. Campaign menus, finale, 25-level E2E, multi-resolution and performance validation remain outstanding.

Frida 17.23.3 / frida-tools 14.11.0 already installed. No hooks or Los Angeles execution were needed: resource-driven XUI reconstruction supplied immediate implementation evidence; bounded native hooks remain optional for unresolved runtime layouts.


### V3 bonus PDA visibility correction (2026-10-09)

Native evidence resolves the SOLVER overlap without changing original coordinates: 0046ac50 registers solvebutton in slot 2; 00443b80 controls node flags; 00410300 hides slot 2 during minigame entry. Vegas no longer draws that map control in bonus screens. The generic visual-profile contract now offers nullable bonusSolveRect; Vegas returns null, eliminating the invisible solve shortcut as well. Other profiles retain their prior default, and the no-profile experimental renderer is unchanged.

The previously reported missing WordSearch PDA caption was actually already rendered from a direct underlay label and obscured by SOLVER, not absent because of nesting. It is now visible. TileRot/TileSwap goal captions and the Jigsaw upper arrow are no longer overlaid. Private before/after comparison: local-output/vegas-v3-bonus/controls/comparison.html; APK SHA256 4f9a867e0ece95633996f8b6245b9349abe061fe9fb0fffa040c04c1a8d50dac. 202 JVM tests/builds and real WSA four-bonus component test PASS (36.011 s), including a null solve-hitbox assertion in each family and existing normal puzzle completion/rotation/paging checks. No campaign E2E or new original-Windows bonus capture is claimed. Relief, selected tile textures, animations, guide-image confirmation and the remaining V3 campaign/finale coverage are still pending.


### V3 TileRot/TileSwap relief and retirement (2026-10-09)

VegasVisualProfile.drawTileBonus now renders native shadow/emboss resources from each bonus definition, replaces the provisional yellow swap selection rectangle with tile_selected, and omits retired tiles plus their relief/shadows. 00459400 positions rotation relief/shadows at grid origin minus (6,4); 00457500 places swap overlays at their tile origins; 0045a780/00458e30 remove retired nodes. Resource IDs and offsets remain in the Vegas profile. Shadows render below the tile layer. Bitmap decoding remains at native resolution; the bonus-definition cache retains only the current resource, without enlarged bitmap copies or rule changes.

Builds and 202 JVM tests passed. The first Android attempt failed at PDA header capture before reaching bonus (retained log); unchanged assertions passed on repetition in 37.612 s, including all four bonus completions, checkpoint checks, selected-swap capture and a naturally earned retired rotation row. Visual inspection of real WSA captures confirmed native relief, gold selection and removal of the completed row. This is component coverage, not 25-level campaign E2E. No new Windows bonus capture or full performance measurement is claimed.

Private before/after report, captures and APK: local-output/vegas-v3-bonus/relief/. APK SHA256 568055c25aa2c6f68096fcd1c313b5d74bffa391c87e08bd6c5eb8a99719c13d. Pending: native hover/rotation selection and movement/fade timings, confirmed Windows-equivalent comparison, Jigsaw guide, full PDA/menu states, campaign/finale E2E, multiple formats and performance. Continue in VegasVisualProfile.drawTileBonus and the corresponding native event callbacks, preserving the existing retirement logic in SdaBonus.kt. Director/Huntsville and unrelated local changes remain untouched.


### V3 original bonus result screens (2026-10-09)

Replaced the provisional LEVEL_COMPLETE panel in the Vegas profile with tilegamewon: original shared underlay/frame, congratulation atlas caption, Polaroid frame, correct evidence photo/caption and native OK button. 004429d0 selects image and label by equal child index; 00410240 supplies clue minus one. ENVS contains 25 photos and 25 captions. The generic SDA view offers optional drawLevelComplete/levelCompleteRect hooks, captures the profile-provided OK button and confirms on release; title IDs and coordinates remain exclusively in VegasVisualProfile. The previous generic fallback remains available to profiles without this capability. Engine rewards/checkpoint/next-level rules are unchanged.

202 JVM tests/builds and private real-Android four-bonus component test PASS in 41.824 s. After normal bonus completion, each of clues 1?4 is captured; same-process checkpoint restoration preserves the result; OK DOWN leaves LEVEL_COMPLETE and OK UP reaches the next MAP. This now tests result confirmation through View MotionEvents instead of directly calling confirmLevelComplete in that part of the fixture. It remains component coverage, not catalogue/import/profile campaign E2E or cross-process restoration. Clues 5?25 have identified resources, not verified screen coverage.

Private APK, four result captures and HTML: local-output/vegas-v3-bonus/results/. APK SHA256 2d76dc58778aea407fdfd6005a82423ebdc6a0500ee3f737d2c348702b4e60cd. Windows-equivalent result capture, native animation/audio, full PDA visibility states, menus/options/hints, remaining results, finale and full performance/campaign validation remain pending. Continue in VegasVisualProfile.drawLevelComplete and SdaGameView.controlRect/onTouchEvent; menu recovery starts with ENVS menudlg2/mainoptionsdlgeyespy and dialogimg composition. Director/Huntsville and unrelated WIP were preserved.


### V3 25-level bonus/result component coverage (2026-10-09)

The private instrumentation fixture accepts campaignLevels (default 4; explicit 25 for full resource variants). It starts at level 1, progresses without forced indices/completion flags/solveBonus, solves each bonus with View MotionEvents, restores same-process result checkpoints, and confirms native OK by DOWN/UP. The last OK uses the legitimate first-riddle binding/content and enters FINALE_1. Hidden-object preparation mixes model and View inputs; catalogue/import/profile routing and exhaustive scene UI are not part of this fixture. Available scene names in coverage are metadata, not visited-screen evidence.

Real WSA result: PASS, 162.605 s, 25 consecutive levels, 7 TileRot / 7 WordSearch / 6 Jigsaw / 5 TileSwap. All 25 bonus screenshots and 25 result screenshots exist; finale entry screenshot exists. This proves component playability/progression through level 25 to finale entry, not completion of the three finale phases or campaign E2E. Test APK compiled; production code unchanged from the previous tested block. The 202 JVM results remain the previous block's results, not a fresh claim for this test-only extension.

Private artifacts: local-output/vegas-campaign25/{comparison.html,coverage.json,summary.json,android/,instrumentation.log,meminfo-during.log,vegas-campaign25-debug.apk}. APK SHA256 2d76dc58778aea407fdfd6005a82423ebdc6a0500ee3f737d2c348702b4e60cd. A single during-run memory sample reports PSS 166836 KiB, RSS 269668 KiB; not peak memory, FPS, leak proof or a baseline comparison. Windows-equivalent captures, other densities/aspects, process persistence and Huntsville UI regression remain pending.

Defects found: high accumulated scores visibly clip the PDA score label; finale entry still uses provisional text and lacks full PDA composition. All levels remain visually PARCIAL, not APROBADO. Next implementation: VegasVisualProfile.base and native score-width handling; SdaGameView.drawFinale original fonts/PDA; menu/options/hints; authentic phases 2/3 and final E2E. Keep normal four-level regression via default campaignLevels and use campaignLevels=25 for campaign variants. Director/Huntsville and unrelated WIP remain unchanged.


### V3 score unclipping, first-riddle atlas/PDA and secondary-display capture (2026-10-09)

00453ee0 concatenates the original score caption and comma-formatted number directly; 0048aad0 uses the label rectangle to calculate text anchors but does not clip to it. SdaResourceCanvas.label now offers optional clipToBounds (default unchanged); only the Vegas score opts out and removes the added separator space. The level-25 capture now displays the full 13,141,100 value. This is original alignment, not arbitrary font shrinking.

First-riddle drawing now delegates optional base/caption hooks to the profile. Vegas resolves the controller's paper/riddlelabel and original fnt_riddle atlas, draws pdacontrol and the empty puzzle tray panel beneath the interactive pieces. Existing piece/drop rules and campaign rewards remain unchanged. The independent finale timing/timeout contract is not implemented here; the shown clock still comes from the campaign. Later phases and native animations remain pending.

Instrumentation supports visualDisplayId and copies the real Android window surface with PixelCopy, using window-local viewport coordinates. This replaces default-display screenshots that became unreliable when moving WSA between host monitors; it never draws the View into a bitmap. visualDisplayId=2 kept the app on the Windows secondary monitor, verified by window coordinates. A prior full-display run failed at level 23 during frame comparison after moving the window; its log is preserved. With window capture, the full 25-level component run PASSed in 164.436 s, including first-riddle entry. 202 JVM tests/builds also passed in this block. This remains component coverage, not import/profile E2E, exhaustive scene coverage or finale completion.

Private APK/captures/aligned before-after report: local-output/vegas-finale-visual/. APK SHA256 485dc56d5c81f35f87d29ad586d79a8371630d06d74ab259a5274335f1739fd7. Comparison derivatives crop/normalize the logical 800?600 stage; raw capture provenance is retained. No original Windows screen equivalence, supersampling or new resource resolution is claimed. Continue in VegasVisualProfile.drawRiddleBase/drawRiddleCaption, SdaGameView.drawFinale and SdaCampaign.advance/first-riddle timing after resolving native semantics. Director/Huntsville and unrelated WIP remain unchanged.


### V3 user-reported PDA/retired-tile defects (2026-10-09)

Fixed the missing full-photo layer beneath TileRot/TileSwap pieces. Native 00457500/00459400 enable the source image and the XUI tile_background_overlay (black alpha 100); retired tiles still lose their emboss/shadow as recovered, but now reveal the dimmed photo instead of black. No bonus/scoring rules changed. Jigsaw had omitted jigsawemptypdaimage/jigsawemptybottompdaimage; restoring those native backing layers removes the mismatched rectangle around the lower arrow. The original arrow files are opaque RGB JPEGs, not PNGs with broken alpha. No commercial image was edited.

Vegas HUD enumerates visible target rows after their original fade/removal, compacting upward while retaining stable model/checkpoint indices. At the user's express request, the whole clock line is centered and raised two logical pixels, and the score line lowered four. These are intentional Android layout adaptations, not claimed native coordinates. The native MENU face is restored and activates on release; generic SDA exposes only a profile rectangle and callback. Its Android host adapter freezes the clock and offers resume or save-and-return. The original menudlg2 options/instructions/main-menu screens remain PARCIAL/NO IMPLEMENTADO, not certified complete. The visible PAUSE button in bonus/map and the independent first-riddle timing also remain pending.

Validation: 202 JVM tests (0 failures/errors), debug/test APK builds; fresh real-window WSA component test passed for levels 1-4 and all four bonus families, including pixel checks of the retired photo, compacted rows and Jigsaw backing. The same suite initially caught a separate MENU UTF-8 text defect; after fixing it, the focused launcher/dialog test passed in 1.874 s. Real Android View/ListView events exercised MENU, resume and saved exit; the activity reached DESTROYED and its checkpoint parsed. Existing campaign/scene checkpoint preference values were restored and checked exactly. This is not catalogue/import E2E, cross-process persistence, all-25 scene coverage or original Windows equivalence. All visible tests used display 2 (second host monitor).

Private artifacts: local-output/vegas-pda-fixes/{vegas-pda-fixes-debug.apk,comparison.html,result.json,android/,before/,normalized/,resources/,component-and-menu-initial.log,menu-instrumentation.log}. APK SHA256 e67ae9473a4d0fbf1b4b11a2f7919ef6bcabb38cf456e0b4ef4c4cd3a22d0921. HTML compares aligned real Android before/after frames and labels extracted JPEGs separately; change MAE is not a fidelity score. Source bitmap cache is reused, with no enlarged runtime copies. Performance/aspect sweeps and native Windows-equivalent captures remain pending. Director/Huntsville, approved APKs and unrelated local changes were untouched.

Continue: VegasVisualProfile.base/drawBonusBase/drawHud for remaining PDA visibility/hints/collectibles and score-width cases; SdaLauncherActivity.wireViewCallbacks replaces the partial host menu only when native menudlg2/dialogimg/actions are reconstructed; SdaGameView.controlRect/onTouchEvent preserve release/cancel/pause semantics. SdaPrivateVisualInstrumentationTest contains the visual and launcher regression entry points. Full Goal V3 remains active.


### User-requested compact score header (2026-10-09)

Time, PUNTUACION and the numeric score now use the original atlas at 85%, in three separate centered rows. The level/header and objective instructions are unchanged. This explicitly supersedes the previous single-line score and first two-row attempt: its numeral collided with the instructions. Only the Vegas profile supplies these adaptations; score/timer rules are unchanged. A real-window WSA regression requires numeric ink above the instructions; the first-level map/scene/bonus component run passed in 30.056 s on display 2, with app JVM tests and builds passing. Not a new all-four/all-25 validation. WSA had stopped; its installed service/startup task was restarted in the background and connectivity verified before the successful test. Private APK and before/after captures: local-output/vegas-score-compact/. SHA256 0bd2aa1710cbf8ae2363592ccce01d621dca254a94a562e72515d0c4927af11b.

Next native-menu blocker resolved statically: dialogimg factory calls 0041e050, renderer 0041e0f0, and 00465530 assigns the nine mpi_diag textures via 0041e0d0. Original corners/edges/center are available; implement tiled composition, not stretched artwork. Frida 17.23.3 remains installed; hooks were unnecessary for this identification. Native menu/options/instructions and the full Goal V3 remain pending.


### V3 ? marco nativo y menu XUI (avance parcial)

`SdaResourceCanvas.tiledPanel` repite las nueve texturas a dimensiones nativas y recorta el ultimo mosaico, sin crear copias ampliadas. `SdaResourceMenuView` interpreta el contenedor y botones; `VegasVisualProfile.menuView` mantiene IDs, layout y acciones de Vegas fuera del servicio generico. El launcher usa el menu de recursos en lugar de la lista Android.

WSA API33/display2: `launcher_menu_resume_and_saved_exit` PASS (1.689 s), con eventos DOWN/UP, liberacion fuera y CANCEL; guarda checkpoint y restaura exactamente las preferencias previas del test. `original_dialog_tiles_repeat_without_stretching` PASS (0.486 s): muestras de pixeles/alfa en 325x320, 470x430 y 560x420. Build experimental y suites JVM pasan. No son pruebas de campa?a E2E ni nueva regresion visual de Huntsville.

**PARCIAL:** reanudar y guardar/volver al catalogo operativos. El boton MENU PRINCIPAL conserva temporalmente ese retorno al catalogo; OPTIONS/INSTRUCTIONS avisan que falta implementacion. Portada nativa, opciones, ayuda, hover y audio siguen pendientes. No se declara completo el menu ni el Goal. Captura PixelCopy del dialogo: las zonas transparentes se ven negras al capturar solo su ventana; la composicion con el fondo sigue NO VERIFICADA. Comparacion equivalente Windows pendiente.

Resultados privados: `local-output/vegas-native-menu/` (APK, Android antes/despues, HTML, coverage.json y logs). Continuar en `VegasVisualProfile.menuView`, `SdaResourceMenuView.onDraw/onTouchEvent` y `SdaLauncherActivity.wireViewCallbacks`: integrar `mainmenuunderlay`, `mainoptionsdlg` y overlays de instrucciones con comportamiento autentico. Frida 17.23.3 confirmado; no se ejecuto otro titulo ni se amplio el piloto porque los bindings estaticos ya desbloquearon este cambio. Director/Huntsville y WIP ajeno intactos.


### V3 ? portada interna y ayuda navegable

Supera la limitacion de `8287ec3`: `MENU PRINCIPAL` abre ahora `mainmenuunderlay`, no el catalogo. La portada tambien aparece al lanzar la campa?a; mantiene el reloj pausado hasta continuar. `SALIR` guarda y vuelve al host multijuego. Se dibujan fondos, tarjeta generica, boton de campa?a, candado y controles originales. `SdaResourceMenuView.show/onDraw` compone nodos y cambia pantallas; IDs, visibilidad y callbacks quedan en `VegasVisualProfile.menuView`.

Ayuda general: cuatro paginas originales con captions/imagenes/atlas, flechas ida/vuelta y HECHO. Ayuda contextual: TileRot, TileSwap, WordSearch y Jigsaw, con retorno al menu y reanudacion. No se tradujeron instrucciones de mouse a texto tactil inventado. Controles sin `value` no se capturan como SALIR.

Pruebas: 202 JVM, 0 fallos/errores; build APK/test APK correcto. WSA API33/display2: bateria de 3 tests PASS (76.090 s), incluyendo renderer de mosaicos, cuatro familias con entradas Android y resultados hasta nivel 5, portada/ayuda/guardado. Tras recuperar el candado confirmado por Windows, prueba del launcher PASS (3.985 s). Preparacion de escenas tecnica: NO es E2E de la campa?a completa. Preferencias previas restauradas por el test. Director/Huntsville sin cambios; nueva regresion visual integral de Huntsville pendiente.

Estado **PARCIAL**: opciones/audio, perfiles, nombre/avatar, records, modo ilimitado/coleccionables, faders, sonidos, fresh-profile iniciar/continuar, logo externo y variante `mainoverlaydlg5` pendientes. La ayuda de escenas usa por ahora la introduccion general; la ruta nativa `eyespyinstructionsdlg*` sigue pendiente. `center` y transiciones necesitan contraste nativo. No se declara completo el menu ni el Goal.

Evidencia privada: `local-output/vegas-navigation/` (APK, nueve capturas Android, referencia Windows existente, HTML, overlay/diff, matriz JSON y logs). Comparacion Windows/Android normalizada a 800x600: metrica limitada a fondo estatico, con estados de jugador diferentes; no aprueba interfaz completa. PSS 64,684 KiB/RSS 164,532 KiB en una muestra del menu vivo (1 actividad); no sustituye perfilado FPS/GC/before-after.

Continuacion: `VegasVisualProfile.menuView` (mainoptionsdlg, perfiles y ojos/escenas); `SdaResourceMenuView.onDraw/onTouchEvent` (sliders/checkboxes y flags nativos); `SdaLauncherActivity.showResourceMenu/wireViewCallbacks` (servicios de opciones y perfil). Frida sigue 17.23.3; reutilizados XUI/evidencias sin ejecutar Los Angeles ni ampliar la investigacion.


### V3 ? audio privado incluido en la conversion (prerrequisito)

El empaquetador recogia solo `texture`; omitia `sfx` y `audiostream`. `tools/sda-prototype/package_vegas.py:referenced_resources/build_package` incluye ahora esas dependencias, deduplica aliases de mayusculas y reporta referencias ausentes sin sustituirlas. La conversion falla ante XML invalido en vez de ocultarlo. Paquete nuevo independiente: `local-output/vegas-audio/vegas-full-with-audio.zip`, con 40 OGG unicos (2,759,436 bytes); todos los hashes de recursos existentes coinciden con `vegas-menu-cover-full.zip`. Los paquetes anteriores se conservan. El ZIP historico `vegas_full.zip` usa otra portada; no se presento como baseline equivalente.

Pruebas: 3 Python PASS. `SdaPrivateAudioInstrumentationTest.packaged_music_and_effect_decode_and_play_on_android` PASS en WSA (0.146 s): cuatro pistas y ButtonClick2 preparados/reproducidos por MediaPlayer real a volumen cero, con duracion positiva y cache temporal eliminada. No prueba escucha, mezcla, volumen UI ni fidelidad audiovisual. `launcher_menu_resume_and_saved_exit` con el nuevo ZIP PASS (3.641 s), display2, conservando preferencias. Build de test APK correcto; codigo productivo Android/Director/Huntsville intacto.

**PARCIAL/PENDIENTE:** conectar reproduccion al launcher/menu, sliders originales de musica/efectos, persistencia y cancelar/OK; verificar mezcla, pausa, cambios de escena y audio E2E. `PadLockOpen.ogg` y `TombDoor.ogg` no estan en RAWDATA; se registran ausentes y no se inventan. Otras referencias graficas obsoletas/ausentes quedan en package.log; no equivalen a pantallas activas aprobadas. Logs/JSON de codec, matriz y paquete comercial permanecen fuera de Git en `local-output/vegas-audio/`.

Continuar en `SdaLauncherActivity.showResourceMenu`, `VegasVisualProfile.menuView` (mainoptionsdlg) y `SdaResourceMenuView.onDraw/onTouchEvent` (sliders); crear servicio generico de reproduccion SDA con cierre de players y volumen acotado. Frida 17.23.3 ya confirmado: no hizo falta otro piloto ni ejecutar Los Angeles.


### V3 ? reproduccion real de musica y clics del menu

`SdaAudioSession` aporta reproduccion Android asincrona a partir de `audiostream`/`sfx`, volumen acotado 0..100, hasta ocho efectos simultaneos, pausa/reanudacion y liberacion de players/caches propios. No contiene IDs comerciales. `VegasVisualProfile.audioSession/menuView` selecciona mainmenutrack y defaults originales de los sliders (50/75); los clics usan el `sfx` del nodo XUI. `SdaLauncherActivity` conecta lifecycle y cierre; al continuar se detiene la musica del menu. Paquetes anteriores sin OGG siguen abriendo, con diagnostico de ausencia y sin inventar sonidos.

Pruebas finales: 202 JVM/0 fallos/errores, build de APK/test APK correcto; 2 tests de audio WSA PASS (2.253 s), menu/guardado con paquete nuevo PASS (3.893 s) y con paquete anterior PASS (3.644 s). La prueba usa eventos Android para AYUDA y confirma que arranca el efecto; pausa/stop/resume son callbacks reales al abrir el catalogo en la MISMA tarea y display2. Cierre comprueba cero players y cero caches; preferencias previas conservadas. No prueba escucha humana ni campa?a completa.

Se conservaron logs de los intentos iniciales fallidos: ActivityScenario.moveToState usaba un auxiliar en otro display/proceso, que no pausaba esta ventana WSA. Se sustituyo por el catalogo real en el mismo display, manteniendo las aserciones de lifecycle. Corregido registro de slots: identidad estable al cambiar ready, para que release elimine todos los reproductores. Auxiliares de test cerrados tras finalizar.

**PARCIAL:** sliders/OK/CANCEL/persistencia de volumen en mainoptionsdlg siguen pendientes, asi como musica y efectos de escenas/bonus, hover, loops/fades y contraste de timing/audio nativo. Solo se aplica loop si XUI lo declara; el comportamiento nativo restante no se da por reconstruido. Director/Huntsville y WIP ajeno intactos; regresion visual completa aun pendiente. APK y logs privados en `local-output/vegas-audio-session/`, usando el paquete con OGG de `local-output/vegas-audio/`.

Continuar en `SdaAudioSession.setMusicVolume/setEffectsVolume`, `VegasVisualProfile.menuView/audioSession` (mainoptionsdlg, guardar/cancelar) y `SdaResourceMenuView.onDraw/onTouchEvent` (sliders originales). El Goal completo sigue activo; Frida ya esta instalado y no se repitio el piloto ni se ejecuto Los Angeles.

### V3: volumen operativo en opciones originales (PARCIAL)

`SdaResourceMenuView` dibuja sliders con las texturas y offsets XUI, captura el tirador y conserva el punto de agarre; recorrido = ancho de barra menos ancho de tirador. Clamp y formula contrastados con 0048b980/0048bd66, renderer 0048bc32 y parser 0048db6c ya exportados. CANCEL Android restaura el valor inicial del gesto. No hay IDs comerciales en este servicio. `VegasVisualProfile.menuView/audioSession` conecta opciones desde menu principal y pausa, previsualiza musica/efectos, restaura al cancelar/cerrar y guarda al aceptar en preferencias propias de Vegas. La reapertura de Activity verifica los valores guardados; no equivale a muerte/reinicio del proceso.

Prueba nueva primero falla al no abrir opciones (log conservado), luego 3 pruebas Android de audio/opciones PASS 4.934 s, incluyendo ambos extremos, cancelacion, persistencia, pausa, cierre y restauracion exacta de preferencias ajenas al test. Regresion menu/guardado PASS 3.924 s. Compilacion y suites JVM correctas. Artefactos privados: `local-output/vegas-options/` con APK, captura WSA display 2, referencia Windows previa reutilizada, HTML lado a lado, cobertura y logs. Ningun recurso comercial se publica.

**Pendientes explicitos:** casillas de fullscreen, pistas ilimitadas, modo relax y aceleracion se muestran deshabilitadas; no se inventaron sus reglas. Comparacion real detecta marco mas estrecho que Windows y desbordamiento de barra/texto de hardware: investigar dimensiones dinamicas nativas antes de aprobar fidelidad. Hover, contraste de gestos/audio Windows y persistencia entre procesos pendientes. Continuar en `VegasVisualProfile.menuView` (geometria y callbacks), `SdaResourceMenuView` (hover/checkboxes) y handlers nativos de opciones. Frida 17.23.3 confirmado; XUI y slices existentes bastaron para el volumen, sin ampliar piloto ni ejecutar Los Angeles. Director/Huntsville y WIP ajeno intactos. Goal V3 permanece activo.

### V3: marco de opciones y hover corregidos

La captura Windows `options-sendinput.png` se reutiliza como referencia real. Matching de esquinas opacas con las texturas originales identifica limites exteriores (114,85)-(687,515): 573x430. La edicion espanola ahora calibra ese marco y centra el contenedor exclusivamente en `VegasVisualProfile`; no modifica el XUI privado ni impone coordenadas al motor. El algoritmo nativo de autoajuste aun no esta recuperado; otras ediciones conservan sus datos XUI. Captura WSA posterior confirma barras, texto y botones dentro del marco. `SdaResourceMenuView` incorpora hover de botones/tiradores y rollover solo al entrar, con texturas/sonidos XUI; hover no activa acciones.

**Correccion de evidencia anterior:** la ampliacion pausa/CANCEL/cierre del test tenia un nullable sin resolver y su build fallo; aquella ejecucion uso una test APK previa. No certificaba las aserciones ampliadas. Se corrige el nullable y se espera cierre/layout reales entre gestos. APK de pruebas reconstruida exitosamente: las 3 pruebas de audio/opciones ahora pasan con todas esas aserciones, geometria y pixel del tirador hover (5.016 s). Regresion de navegacion/guardado PASS 4.049 s; 202 JVM sin fallos. Logs originales de fallo y actuales se conservan.

Resultados privados: `local-output/vegas-options/comparison-layout.html`, APK `vegas-options-layout-debug.apk`, capturas antes/despues, overlay y `layout-coverage.json` con MAE solo de muestras opacas de las tres esquinas alineadas. No es una metrica global ni aprobacion de toda la pantalla: fondo, volumen y casillas tienen estados distintos. Las casillas, atenuacion modal del fondo, ajuste automatico nativo, gestos/audio equivalentes Windows, proceso y campana/desenlace completos siguen pendientes. Los handlers 00407400/004075b0 confirman orden y persistencia de casillas, pero no se habilitan sin integrar sus efectos reales. Continuar por esas casillas y las reglas de reloj/pistas, preservando el perfil por titulo. Director/Huntsville y WIP intactos; Goal activo.

### V3: efectos reales de acierto y fallo en escenas

El perfil Vegas resuelve `foundsfx`/`notfoundsfx` del eyespyarea original; `SdaLauncherActivity` reproduce esos IDs mediante `SdaAudioSession` desde los callbacks reales de objetos y misses. Respeta los valores de volumen guardados y no altera score, hit-test ni reglas. El servicio generico conserva el ID del ultimo efecto efectivamente iniciado para verificar el decoder esperado. Director/Huntsville y WIP intactos.

Prueba nueva: launcher -> menu principal -> mapa -> escena con eventos Android, objeto alpha legitimo y fallo sobre fondo sin objetivos, sin forzar fase/objetos. Primero falla por ausencia del efecto, luego 4 pruebas Android audio/opciones PASS 6.475 s con ambas reproducciones efectivas a volumen cero; navegacion/guardado PASS 4.349 s. Build y 202 JVM sin fallos. El helper toca el centro del pixel para evitar redondeo en viewport escalado. Preferencias/checkpoints anteriores restaurados y comprobados. No es escucha humana ni E2E desde catalogo/importacion ni campana completa.

Se corrige ademas el extra de dos helpers: `private_sda_package` era ignorado y abria el paquete activo previo; ahora todos usan `SdaLauncherActivity.EXTRA_PACKAGE_PATH`. Las comprobaciones ampliadas se repiten con el ZIP privado con audio correcto; se conservan logs fallidos. Artefactos privados en `local-output/vegas-scene-audio/`: APK, evidencia JSON con escena/objeto/efectos, cobertura y logs.

Pendientes: playlist/musica gameplay, crossfades, bonus/finale/penalizaciones, contraste audible original y casillas. 00407400/004075b0 verifican orden/guardar opciones; 0041db80/0041dca0 verifican supresion de warnings/timeout y caption relax. Antes de habilitar relax hay que integrar y contrastar las distintas bonificaciones temporales de 00418600/0045b150; no basta con ocultar el reloj. Continuar en `VegasVisualProfile.sceneFeedback/menuView`, callbacks de `SdaLauncherActivity`, `SdaAudioSession` y `SdaCampaign` para los contratos pendientes. Frida ya instalado; no hubo otro piloto ni ejecucion de Los Angeles. Goal activo.


### V3: musica de campa?a y bucles originales

- `VegasMusicState.select` reproduce el dispatcher nativo 00405080: menu, investigacion (primero eyespy2, alternancia solo al cambiar contexto), bonus. `VegasVisualProfile.campaignMusic` obtiene los audiostream del musicplayer XUI. Mapa/escena comparten contexto; bonus/resultados conservan su tema. No se inventa gamefinish, ausente del XUI disponible.
- `SdaAudioSession.playMusic` acepta una politica generica de bucle; Vegas solicita bucle porque 0048f1da carga BASS con flag 4, confirmado como BASS_SAMPLE_LOOP en el header del [SDK oficial](https://www.un4seen.com/bass.html). No cambia el comportamiento predeterminado de otros perfiles.
- `SdaLauncherActivity.updateCampaignMusic` espera al cierre del menu inicial, sigue las fases normales/restauradas y no recrea decoders al cambiar de escena o cerrar pausa. Las solicitudes fallidas no se reintentan cada fotograma. Reutiliza cache sin texturas nuevas; solo un reproductor musical activo.
- Verificacion: 204 JVM, cero fallos/errores/omitidos; 5 Android de audio (6,853 s) y 1 de menu/guardado/salida (3,958 s), WSA display 2. Ruta launcher/menu/mapa/escena/pausa por MotionEvents; bonus probado como adaptador/decoder aislado, NO recorrido de campa?a. Reproduccion silenciada; no acredita escucha comparativa ni duracion de un ciclo entero.
- La prueba roja expuso musica ausente. La primera integracion detecto seleccion prematura antes del menu, que consumia una variante; corregido y repetido sin rebajar aserciones.
- APK/evidencia/logs privados: `local-output/vegas-campaign-music/`. APK SHA256 `730c3e13c4a827fa69f32cebfa4a122db6fbd7efe7e5cd4ec23a0478a9185ab2`.
- Frida 17.23.3 disponible. No se ejecuto otro piloto ni Los Angeles: el slice existente desbloqueo este bloque directamente.
- Pendiente: fundidos/crossfade originales (XUI 2/0.75/0.75), audio del desenlace, comparacion audible Windows/Android, persistencia de posicion musical y validacion de retornos especiales que reinician el selector (00418bf0/00418e20/00419000). Continuar en `VegasMusicState.select`, `VegasVisualProfile.campaignMusic`, `SdaAudioSession.playMusic` y `SdaLauncherActivity.updateCampaignMusic`. No se declara Vegas terminado ni Huntsville validado visualmente por estas pruebas.


### V3: pista funcional del PDA, recarga y checkpoint

- Boton original PISTA habilitado solo en escena y listo; consume UP dentro, respeta CANCEL, reproduce `hintsfx` y revela sin recoger un miembro pendiente de un objetivo pendiente. Seleccion por dos muestreos, como 00421800; RNG MSVC existente, sembrado con uptime Android (no se acredita identidad con la secuencia temporal Windows).
- Penalizacion existente de 7500 con suelo cero, sin cambiar cadena de aciertos; modelo generico `SdaHint` parametrizado por el perfil Vegas: recarga 20/30/45/67.5 s, multiplicador 1.5 y tope 67.5. Constructor 00426f70 y metodos virtuales omitidos 00427050/00427150/00427570 recuperados por lectura puntual de vtable e instrucciones del PE; no se ejecuto ni altero el EXE. Frida no fue necesario.
- Atlas `eyespyanimhint`, intervalo nativo 0.14 s y centrado en objeto. Boton de recarga con base atenuada y revelado horizontal. Indicacion recortada fuera del PDA; viewport SDA recortado para impedir efectos en letterboxing. Los tiempos/capas/pixeles completos de la animacion requieren contraste Windows; fidelidad visual PARCIAL.
- Campo opcional `hintState` en checkpoint de campa?a, con restauracion validada antes de mutar. Partidas antiguas sin campo siguen legibles; recarga y objeto revelado se conservan al cerrar/reabrir actividad. No es prueba de muerte del proceso.
- 208 JVM correctas; Android audio/pista/volumen/menu: 5 pruebas (27.94 s), incluye recarga de veinte segundos via Choreographer sin acelerar ni asignar reloj; mapa/escena/cuatro bonus: 1 prueba correcta (69.688 s). Evidencia real de una pista: puntuacion 7500 -> 0, objetivo pendiente, cancelacion sin consumo. Materiales privados en `local-output/vegas-hints/`: APK, captura, recursos, HTML, JSON y logs.
- Pendientes: opcion rapidhints (00419680/0x489), contraste Windows de animacion y caption de recarga, guardado durante pista con muerte real del proceso, coleccionables y desenlace completo. Continuacion: `SdaHint.advance/state/restore`, `SdaCampaign.requestHint`, `VegasVisualProfile.hintPolicy/hintRect/drawHud`, `SdaGameView.controlRect`, `SdaLauncherActivity.wireViewCallbacks` y `SdaPrivateAudioInstrumentationTest.scene_found_and_missed_audio_follow_real_android_input`.
- Director/Huntsville/main y WIP ajeno intactos. Regresion JVM compartida no certifica Huntsville visualmente ni campa?a Vegas completa.

APK final: SHA256 `783947b84d0aba71e1266a66177d4e60e9f19b51c3b453cf0b63d73f4ef4dff6`; instalacion y recorrido de pista/reapertura/recarga repetidos sobre esa APK: correctos (22.725 s). Atlas nuevo 2200x190: 1,672,000 bytes teoricos en ARGB8888, cache unica sin ampliaciones; esto no sustituye medicion integral de PSS/FPS.


### V3: casillas XUI y opcion PISTAS ILIMITADAS funcional

- `SdaResourceMenuView` ofrece estado booleano, texturas off/on/hover originales, captura de gesto, cancelacion, UP dentro y exclusion de controles deshabilitados. Sigue el puntero capturado; un segundo dedo no confirma el primero. No conoce IDs ni opciones comerciales.
- Vegas habilita exclusivamente la segunda casilla de las opciones (`@ID_OPTIONS_HINTS`, native rapidhints/0x489). `VegasVisualProfile` maneja preview, OK, CANCEL, cierre y preferencias; `SdaHint.advance(immediate)` ofrece la capacidad generica. En 00427050, 00419680 fuerza elapsed=delay; se conserva el crecimiento 1.5 hasta 67.5 y la penalizacion existente de 7500. No se convierte en pistas gratuitas.
- Configuracion cargada al abrir campa?a; opciones globales por titulo en preferencias, recarga pendiente en checkpoint. Guardar la opcion no fuerza objetos, indices, resultados ni avance. CANCEL durante recarga real mantiene la espera normal; OK permite la siguiente recarga en el frame loop real.
- 209 JVM sin fallos/errores/omitidos; 5 Android audio/opciones/pista (28.461 s) y 1 menu/ayuda/guardado/salida (3.883 s), exclusivamente WSA display 2. Comprueban casilla, CANCEL, UP fuera, segundo dedo, opcion no implementada deshabilitada, preview, OK, reabrir actividad, recarga normal de veinte segundos y recarga inmediata real. No es muerte de proceso ni E2E de campa?a.
- Captura real y comparacion local `local-output/vegas-rapid-hints/comparison.html`; referencia Windows anterior identificada, con estado/volumen distintos. Fidelidad de todos los estados marcada PARCIAL. APK privada SHA256 `b15044f084eee9a17be8da22a125fdc8075b7d3b4fdde777395ad0f184d811f0`.
- Incidencia de prueba: el primer barrido guardo los vol?menes silenciados al pulsar OK. Se corrigio el respaldo/restauracion de las tres preferencias afectadas, con aserciones y repeticion correcta. Recuperar valores personales anteriores queda pendiente de la respuesta del usuario; no se adivinan ni se declara restauracion retroactiva.
- Pendientes: modo relax y sus reglas reales de reloj/bonus, pantalla completa Android, hardware, estados hover originales comparados Windows, muerte de proceso y desenlace. Continuar en `VegasVisualProfile.openOptions/cancelOptions`, `SdaResourceMenuView.onTouchEvent/onDraw`, `SdaHint.advance`, `SdaCampaign.advance` y las dos pruebas de scene/options de `SdaPrivateAudioInstrumentationTest`. Director/Huntsville/main y WIP ajeno intactos. Goal V3 activo.

### Desenlace: primera fase ? ocho colocaciones reales de segunda fase (2026-10-10)

PARCIAL. SdaPlacementRiddleGame reutiliza bandeja/entrada/checkpoint sin duplicar el motor. SdaSecondRiddleGame carga las ocho piezas y distingue textura sostenida, PDA y textura colocada; exige confirmar el di?logo inicial y conserva esa confirmaci?n. SdaCampaign.continueFirstRiddle carga antes de mutar y rechaza una primera fase incompleta o recursos ausentes, conservando el checkpoint. Vegas aporta exclusivamente bindings ENVS, di?logos originales 1015/1016, capas y calibraciones nativas. No se toca Director/Huntsville ni se revive el finale simulado heredado.

WSA/display 2: eventos Android completaron ocho colocaciones de fase uno, OK original ? fase dos, OK original ? ocho colocaciones sin forzar resultados. Cerrar/reabrir actividad restaur? una pieza sostenida; MEN? ? men? original ? SALIR persisti? fase/puntuaci?n y la prueba restaur? las claves previas del usuario. Es continuaci?n desde checkpoint ganado por recorrido JVM; NO es E2E de 25 niveles Android ni muerte de proceso. La puntuaci?n permanece intacta; no se inventan recompensas ni reloj para segunda fase.

La inspecci?n visual detect? atlas completo incorrecto; se corrigi? con 00451360/00489221/004893f3: reloj de arena 142?261/frame 1/(186,134), palanca 90?293/frame 0; taza retira las dos bandejas est?ticas. El canvas ofrece recorte gen?rico sin copias de bitmap. Captura real alineada al centro de p?xel y referencia interpolada verifican canales con tolerancia 26. Se conserva el fallo inicial del test y la captura defectuosa. El test previo del cero de puntuaci?n se normaliz? por ?rea .45? respecto del l?mite .55? tras la reducci?n solicitada; no se elimina su comprobaci?n.

Pruebas: 209 JVM, cero fallos/omitidas; Android ruta desenlace + mapa/escena/cuatro bonus: 2/2 en 78.208 s; ruta repetida tras corregir atlas: 1/1 en 8.893 s; men?/regreso/guardado: 1/1 en la tanda anterior. Memoria puntual: PSS 133341?123405 KiB, bitmaps del riddle 6089432 bytes en ambos puntos, cach? UI 13864520?12984348 bytes entre actividades diferentes; NO demuestra optimizaci?n, FPS o ausencia de fugas.

Resultados privados: local-output/vegas-second-riddle/{vegas-second-riddle-debug.apk,comparison.html,coverage.json,memory.tsv,android/,before/,resources/,logs}. APK SHA256 d19d7c53b33b38821128b390e43dab55a2e6ff060d35249e80600380fd9948a8. No se publican APK, recursos, capturas ni partidas. Frida 17.23.3 confirmado; no se necesitaron hooks ni se ejecut? Los Angeles.

Pendientes: fase tercera/OK 1017 y final aut?ntico; animaciones y temporizaci?n de ambas fases, reloj/timeout nativo de fase uno, mecanismos posteriores; comparaci?n Windows equivalente y E2E integral, muerte de proceso, otras resoluciones/rendimiento. OK de segunda fase queda deshabilitado; se puede cerrar el di?logo y guardar/salir. Continuar en SdaSecondRiddleGame.load/start/state, SdaCampaign.continueFirstRiddle/restore, SdaGameView.drawFinale/onTouchEvent, VegasVisualProfile.riddleDialog/drawRiddlePlaced/drawRiddleDecorations, SdaLauncherActivity.showRiddleDialog y SdaPrivateFinaleProgressionInstrumentationTest. No se declara campa?a terminada.

### Third-phase interactive-item interpreter (2026-10-10)

PARCIAL, engine component only. Added SdaInteractiveResources.load and SdaInteractiveItems: resource-defined image/frame selection, alpha hit testing, ordered duplicate names, firststep, conditions and per-step native timing. Evidence: factories 00468c40/00468c90, initialization 00435890/00435920, condition lookup 004359c0/00433720, update 00433650, advance 00431080, frame application 004310d0 and event 00432e80. Advance is strictly elapsed > frametime, at most one step per item/update; conditions belong to the next step. Completion is a named flag, not visiting an index; a callback may return false even though the animation advances. Default frame time is .125 seconds. No Vegas IDs or assumed victory enter the generic interpreter.

The private resource probe loads all 21 original items (including both keypad names in order), 13 hourglass steps/frames 1..13, and drives real alpha clicks through hammer -> hourglass -> scale -> lever -> reader and plug -> slotmachine power. The component restores JSON midway through animation. It does not execute the native third-controller callbacks, generate slot/keypad results or credit campaign victory. RED missing interpreter was retained; an initial test miscounted hourglass steps as 14, corrected against the actual resource to 13. All four new tests and the full suite pass: 213 JVM, zero failures/errors/skipped; debug/test APKs compiled. No Android UI was changed, so this block does not claim new WSA evidence. Existing two-phase Android evidence remains scoped to 4caffe8.

Private artifacts: local-output/vegas-interactive-items/{coverage.json,interactive-items-*.log}; targeted native callback instructions in private/mystery-pi-vegas/research/third-riddle-instructions/00433a60.capstone.txt, never published. Frida remains available; static instructions recovered the missing callback string arguments without a new hook pilot or any Los Angeles execution.

Next implementation: bind a third controller in SdaCampaign and restore; reuse SdaInteractiveItems.advance/click/frame/state and SdaInteractiveResources.load for the original screen. Interpret indispensable title callbacks in a separate Vegas adapter, using 00433a60 plus its recovered instructions (slot codes, blocked arm, fingerprint, keypad, clock); integrate original OK 1017 -> third entry and OK 1018 -> start through VegasVisualProfile.riddleDialog/SdaLauncherActivity.showRiddleDialog. Do not enable that transition with a fabricated victory condition. The third phase, door/moneyroom/newspaper and complete campaign remain NOT IMPLEMENTED/NO VERIFICADO in Android. All earlier fidelity, 25-level E2E, process-death and performance requirements remain open.


### Third phase entry, mechanisms and Android restore (2026-10-10)

PARCIAL. Original OK 1017 now resolves the third interactive controller before mutating the second-phase save; OK 1018 acknowledges start. SDA owns the generic interactive wrapper/factory, atlas/alpha input, update, rewind and checkpoint contracts. VegasThirdRiddleController owns the title-specific callbacks and symbols; VegasVisualProfile owns original scene/PDA/dialog/resource bindings. No changes by this block to Director/Huntsville/main/approved APKs. The pre-existing three WIP files remain excluded.

Confirmed native callbacks now operate hammer -> hourglass -> scale/lever/reader, blocked arm/recovery, plug -> power -> coin -> arm -> four reels. The resource gate prevents inserting the visible coin before power; an initial test assumption was corrected against that gate. Native RNG scaling uses an explicit checkpointed state; continuity with the original global campaign RNG is NOT verified. Symbols, visibility, started state and mid-spin item progress serialize/restore. Fingerprint/poker-card controls deliberately do not credit unimplemented modes; door/ending victory remains unavailable.

Validation: 214 JVM, zero failures/errors/skipped; experimental debug/test APKs compile. WSA API33/display 2: one real-input route passes in 15.699 s, from an earned JVM first-phase checkpoint through both placement riddles and the third-phase mechanics/code, then native menu/exit and activity reopen. Three Android regressions pass in 73.695 s (original tiled dialogs; map/scene/four bonus families; launcher menu/resume/save/exit). User checkpoint and option preferences are restored by the test. This is NOT catalogue-to-25-level Android E2E or process-death evidence. The initial capture test failed because the modal window remained above the atlas; it retains the original pixel assertions and now captures unobstructed placement via native menu/resume. Initial failure log retained locally.

Private APK/captures/checkpoint/HTML/coverage and logs: local-output/vegas-third-riddle/. APK SHA256: d39b031712ae8bd6e2b84a2687cf057965b8bfd4a43e90eabf3333e1b7ee8fb3. Six PixelCopy Android images include initial, hourglass/reader, power, spin, code and restored code; the code/restored images have identical RGBA pixels at the same viewport. The HTML distinguishes the extracted background from executable captures: equivalent Windows reference remains pending. No third-phase RAM/FPS/leak claim; no enlarged bitmap copies were introduced.

Continue in android/engine/src/main/kotlin/org/rigorcore/caserecomp/games/vegas/VegasThirdRiddleController.kt (completed/click/advance/state: fingerprint, poker/clock, keypad, native door and ending), VegasVisualProfile.kt (drawInteractiveRiddleBase/drawInteractiveRiddleOverlay/riddleDialog), SdaCampaign.kt (advance/restore and genuine final transition), and SdaPrivateFinaleProgressionInstrumentationTest.kt (extend its earned_first_phase_dialog_to_eight_second_phase_placements_and_saved_reopen input route). Native private evidence remains 00432e80, 004334d0, 00433a60 recovered instructions, 00432450, 00434750/004347d0/00434840. Next: authentic fingerprint/clock/keypad, door/moneyroom/newspaper and final score/rank; then true process-death/25-level Android E2E and full visual/performance gates. This supersedes earlier third-entry NOT IMPLEMENTED status, not the remaining ending requirements.


### Third phase fingerprint and original keypad (2026-10-10)

PARCIAL. VegasThirdRiddleController now implements fingerprint pickup/pointer-centred move, powered-reader drop at the native half-open rectangle (594,334,57,158), invalid-drop return over .5s and rearming the initial interactive step. Its v2 checkpoint includes held/return positions, keypad/LED input, error time and door-open flag; old v1 saves remain readable. Generic SDA supplies controller.move and restartItem; every commercial coordinate/name stays in the Vegas adapter. Android touch/hover moves the held print; VegasVisualProfile renders its original image and original LED atlas (native 30px frames).

Native 004313a0 instructions recover all nine keypad rectangles in bottom/middle/top row insertion order; 004334d0 compares the entered four-symbol sequence to the generated reel code, advances lock, and sets the original input cursor. Wrong code uses four error frames and blinking, then clears after strict >1.65s (blink >.3s). 00434840 maps symbols to LED frames. Correct live reel code triggers dooropen and the original green lock image, preserving campaign score. The final controller deliberately stays unsolved: native door sliding/moneyroom/newspaper/completion are NOT yet connected. Pre-spin random-code/clock behavior and original global RNG continuity remain unverified; the implemented playable route uses the actual four completed reels.

Validation: expanded native-resource JVM probe covers unpowered reader, excluded upper boundary, strict .5s return, held/return restore, wrong-code timeout, partial-code restore and earned code -> door-open callback without assigning completion flags. Full APK/testAPK compile and all 214 JVM tests pass, zero failures/errors/skipped. One WSA/display2 real-input journey passes in 18.937s: both placement phases, third mechanisms/reels/activity reopen, held print, invalid boundary/return, valid reader, wrong code and real tactile key presses producing green unlock. User prefs/checkpoints restored by test. No process-death/25-level Android E2E claim; the three earlier Android visual regressions belong to e2d8427, not a newly repeated suite.

Private APK SHA256 4e069e965cf4e4f4cd36a3f6ebdf8700aaf573acc63ef42c10360871c022ad4a; four new PixelCopy captures, extracted LED reference, coverage and HTML at local-output/vegas-fingerprint-keypad/. Build/RED/green logs are under local-output/vegas-third-riddle/fingerprint-keypad-*.log; Android log under the new directory. Extracted atlas is explicitly NOT a Windows capture; no equivalent Windows frame or RAM/FPS certification is claimed.

Next exact functions: VegasThirdRiddleController.advance/completed/state and VegasVisualProfile.drawInteractiveRiddleBase/drawInteractiveRiddleOverlay for native states 6..12; SdaLauncherActivity.showRiddleDialog for completion OK 1019; SdaCampaign for a genuine completion transition; extend SdaPrivateFinaleProgressionInstrumentationTest's input journey through the ending. Newly recovered private 00432450 instructions/constants: state6 >0s; state7 door/lock move 1.875px per native update with retained fractional remainder, transition checks previous door x>800; state8 1s crossfade; state9 2s wait; state10 3s newspaper fade; state12 .7s before completed dialog/solved flag. The door step copies the current native score into the shared score; it does not award an invented bonus. Native profile/rank finalization still needs its bounded follow-up before claiming the ending complete. Original private instruction slices remain outside Git.
