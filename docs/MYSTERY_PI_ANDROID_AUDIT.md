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
