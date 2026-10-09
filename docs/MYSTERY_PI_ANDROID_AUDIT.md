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
