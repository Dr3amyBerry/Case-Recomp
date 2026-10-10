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

## PDA: tres filas compactas solicitadas por el usuario

Tiempo, etiqueta de puntuacion y cifras usan ahora escala 70 % (antes 85 %), manteniendo sus centros y filas independientes. Adaptacion solicitada para Android, no coincidencia declarada con Windows. Nivel y objetivos permanecen intactos; cambio exclusivo de VegasVisualProfile.compactInfo.

APK privada: `local-output/vegas-audio-session/vegas-compact-hud-debug.apk`. Capturas reales WSA display 2: `hud-scene.png` y `hud-swap.png`. Compilacion correcta; prueba Android `original_resources_map_scene_and_four_bonuses`: PASS, 70.609 s. Verifica componentes de mapa, escena y cuatro bonus; no constituye recorrido E2E de la campana. Revision visual: tres filas dentro del panel, sin solaparse con instrucciones.

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


### PDA: tres textos compactos por peticion humana

Tiempo, etiqueta de puntuacion y cifra usan ahora el 55% de las metricas originales (antes 70%), conservando filas separadas y centradas. Es una adaptacion Android solicitada, exclusiva de `VegasVisualProfile.compactInfo`; no altera fuentes, Director ni Huntsville. APK y comprobaciones locales: `local-output/vegas-hud-compact/`. La aceptacion visual humana sigue pendiente.

Verificacion: compilacion y pruebas JVM correctas; prueba Android de mapa, escena y cuatro bonus correcta (70,351 s). Captura real: local-output/vegas-hud-compact/scene.png. No acredita cobertura completa de campana.


### PDA: pista original recuperada (V3)

La zona vacia encima de MAPA ahora contiene PISTA durante la escena. Utiliza sus texturas y atlas originales; muestra recarga y efecto sobre un objeto aun pendiente. Captura Android real y comparacion local: `local-output/vegas-hints/comparison.html`. El informe diferencia recursos extraidos de capturas Android, y el antes/despues contiene estados y objetivos distintos: no calcula una diferencia de pixeles enga?osa. No hay nueva referencia Windows equivalente; la fidelidad de la animacion y de todos los estados queda PARCIAL. El efecto ya no invade el PDA ni el letterboxing. Pruebas y pendientes detallados en el informe de auditoria.


### Opciones: PISTAS ILIMITADAS original habilitada

La casilla y su etiqueta ahora aparecen activas y admiten off/on/hover mediante las texturas originales. OK guarda, CANCEL/cierre restauran el preview y la opcion afecta la recarga real conservando su penalizacion. APK, captura WSA, matriz y HTML: `local-output/vegas-rapid-hints/`. Comparacion con captura Windows anterior de otro estado: referencia contextual, no aprobacion de todos los estados. Las otras tres casillas permanecen deshabilitadas hasta integrar sus contratos autenticos.

### PDA: tres l?neas m?s compactas (2026-10-10)

Por petici?n humana, tiempo, etiqueta de puntuaci?n y cifra utilizan escala 0.45 en vez de 0.55 (18.2% menos), conservando el centrado y la cifra en su propia l?nea. Cambio exclusivo del perfil Vegas. APK experimental compilada e instalada en WSA; prueba Android de men?, reanudaci?n y salida: 1/1, 3.763 s. La revisi?n visual humana del nuevo tama?o sigue pendiente; no se certifica fidelidad al original por este ajuste.

### Segunda fase jugable desde la primera (2026-10-10)

PARCIAL: di?logos originales, ocho colocaciones Android, checkpoint de pieza sostenida y salida con guardado. Atlas de reloj de arena/palanca corregidos con fotogramas nativos; comparaci?n alineada contra recurso extra?do verificada, referencia Windows pendiente. Informe y siete capturas PixelCopy reales en local-output/vegas-second-riddle/comparison.html; matriz coverage.json. V?ase el bloque correspondiente en MYSTERY_PI_ANDROID_AUDIT.md para pruebas, memoria, SHA de APK, fallos conservados y funciones de continuaci?n. Tercera fase y validaci?n integral pendientes.


### Third phase original composition and earned reel code (2026-10-10)

PARCIAL. Third entry/start dialogs, original wall/PDA/clock/door and 21 interactive-item frames now render in Android. Real inputs operate hammer/hourglass/scale/reader and power/coin/arm/reels, with activity reopen retaining the same visible code. Six real PixelCopy captures, an extracted-background reference, explicit coverage and navigable report: local-output/vegas-third-riddle/comparison.html. Restored versus pre-close RGBA pixels are identical at the same viewport; this proves that one visual restore state, not Windows fidelity. No equivalent Windows third-phase capture yet. Fingerprint, clock interaction, keypad/LED, opening/end transitions and third performance validation remain pending. Tests, APK hash, initial capture failure and exact continuation functions are recorded in MYSTERY_PI_ANDROID_AUDIT.md.


### Third phase held print, reader and LED/keypad (2026-10-10)

PARCIAL. Four new Android PixelCopy captures and original extracted LED atlas at local-output/vegas-fingerprint-keypad/comparison.html. Real tactile input places the print on the powered reader, rejects an invalid boundary, shows wrong-code error and accepts the live reel symbols, producing the original green unlock indicator. No equivalent Windows capture yet; door slide, moneyroom/newspaper, clock/pre-spin behavior and final completion remain pending. Full tests/limits/continuation addresses are in MYSTERY_PI_ANDROID_AUDIT.md.


### Earned door, moneyroom, newspaper and native final menu (2026-10-10)

PARCIAL: original resources and source-derived final sequence render and complete via WSA inputs. Six real window captures plus a clearly labelled derived transparent-dialog composition, source JPG comparisons and coverage at local-output/vegas-ending/comparison.html. Completed menu/activity reopen retains identical RGBA pixels and points. Equivalent Windows final-state capture, exact opacity composition, native score breakdown/rank/profile unlocks and full campaign E2E remain pending; no 100%-fidelity/Goal-completion claim. Tests, timing bounds, checkpoint versions and point PSS/bitmap samples are in MYSTERY_PI_ANDROID_AUDIT.md.


### PDA text: user-approved legible layout

Time, score caption and numeric score use the original atlas at .80 scale, in three centered rows. User visually approved this exact presentation; do not recalibrate it. The .36 experiment was rejected as illegible and is not retained. Debug build/app JVM checks pass; three WSA display2 visual regressions pass (74.329s). Private evidence: local-output/vegas-ending/pda-legible-scene.png and pda-legible-android.log.


### Player-menu block

local-output/vegas-profiles/comparison.html links four real WSA dialog surfaces (selection, creation/avatar, blank-name error, selected-player main menu), the experimental APK and coverage.json. Native ENVS.MSE containers, tiled frames, button states, icons, list highlight, caret and atlases are used. Windows equivalents are PENDING; screenshots must not be described as Windows originals. Original resource bounds drive placement; explicit fitwidth error-caption adaptation prevents overflow without changing PDA typography. Functional touch/IME/profile-save route passes, while complete visual identity/profile-fader/highscore/deletion coverage remains PARCIAL.

### Whole Android campaign evidence (2026-10-10)

Private navigable report: local-output/vegas-campaign-e2e/comparison.html; machine-readable coverage.json and all-25-evidence/journey.jsonl. Real Android captures cover25 sequentially earned levels,135 scene visits, all four bonus families, maps/results; optional unvisited scenes are not covered. The joined ending continuation uses an Android-earned checkpoint and passed all three phases. Functional PASS does not certify graphical fidelity. Windows counterparts, aligned differences/metrics, process-death restoration, exhaustive menu/state/scene variants and other resolutions remain NO_VERIFICADO. Existing approved PDA text dimensions are unchanged.

Performance sampled on the automated route: PSS85,579-349,557KiB. Memory growth requires cache/lifetime investigation; this is not FPS/leak certification. The HTML explicitly labels screenshots as Android and does not substitute extracted textures for Windows captures. See MYSTERY_PI_ANDROID_AUDIT.md for test provenance, retained failures, exact continuation files and remaining work.

### Recoverable SDA player removal (2026-10-10)

Original Vegas selectplayer value2 opens deletedlg; cancel8 preserves the player, confirm5 requests generic DELETE_PROFILE using the quoted player name and original resources. PrivateSdaRepository.removeProfile archives only that shared SDA player's metadata and campaign namespace slots under private-sda/removed-profiles/<UUID>. Other players and Director remain untouched. Active preferences switch together; failed moves/commit are rolled back. Failed pre-delete checkpoint saving now aborts deletion and preserves the live campaign. SharedPreferences rollback restores previous strings and absent keys after in-memory mutation. Archives have no restoration UI yet; power-loss atomicity is not certified.

Validation: APK build and230 JVM tests pass; final real Android touch/IME/profile cancel/delete/reopen and preference-failure tests PASS2 in23.233s on WSA display2. Failed-save regression first reproduced deletion proceeding; correction rejects deletion and retains the same live campaign. Logs: local-output/vegas-profiles/removal-save-gate-red.log, removal-final-build.log, removal-final-android.log. Private experimental APK: vegas-player-removal-debug.apk, SHA25667739dfd6fcd9c2ab8578029f34bbfd80efa3d0fa6482c2c83e920a8b4df165a. Existing user files/preferences restored after tests.

Last-player fresh default_pi bootstrap and fallback ordering are Android adaptations; Windows counterparts and deletion-specific visual fidelity remain NO_VERIFICADO. No new cosmetic capture loop. PDA fonts/layout and Director/Huntsville unchanged. Goal ACTIVE, not complete. Native final rank/total/bestscore/unlocks, process-death persistence, Windows comparisons and exhaustive visual/performance coverage remain pending.

Continue at PrivateSdaRepository.removeProfile / AndroidSdaPreferences.setStrings; SdaLauncherActivity.autoSave / showResourceMenu; VegasVisualProfile player menus; SdaPrivateProfilesInstrumentationTest; PrivateSdaProfileRemovalUnitTest; SdaPreferenceRollbackInstrumentationTest. Native final-score continuation remains VegasScoreRules and VegasVisualProfile.riddleDialog, native00419c90/004189c0/004191e0. No private commercial material published.

Current functional integration evidence:local-output/vegas-process-persistence/index.html links25 sequential Android levels(135 scene visits), joined three-phase ending and11 exact-state process-stop/reopen checks, including partial selections. APK/UI rendering and approved PDA typography are unchanged in this block. Reports label technical replay separately from E2E; JSON state equality is not pixel/Windows fidelity approval. Native Windows counterparts, exhaustive states/variants and full visual acceptance remain NO_VERIFICADO/PENDIENTE. See audit for current-run provenance; retained historical files are not new coverage.


### Native all-objects completion panel (2026-10-10)

Replaced the experimental Vegas pre-bonus objectives card and invented start-minigame caption with ENVS `allobjectspickeddialog`: original panel textures, atlas labels and OK button (action 334; native handler 00412300). The profile centers the 400x250 panel in the 800x600 viewport and retains its child coordinates. Rendering and input share the resource-derived button rectangle. Existing start-bonus/persistence transitions are unchanged. Post-bonus results continue to use their separate native resources; no arbitrary recentering of their children.

Validation: debug and instrumentation APK builds and 235 JVM tests passed; private-resource Android composition test passed (1 test), checking panel/button geometry and rendered alpha. This is component evidence, not a new campaign run or comparison with the Windows executable. E2E/Jigsaw input scripts now obtain this button from the profile. Windows alignment and remaining dialogs are NO VERIFICADO. Collectible loading is implemented separately; collection, persisted counters and PDA integration remain pending. Director/Huntsville and approved PDA typography were not changed.


### Native centering and first collection dialogs (2026-10-10)

`center="true"` was parsed as ordinary fixed x by Android. Native parser 004651c0 calls 004020b0: confirmed x86 sets x to parent width / 2 minus control width / 2 (separate integer divisions), retaining y. Generic `SdaUiNode.buttonX`, `SdaResourceCanvas.rect/button` and `SdaResourceMenuView` now share this geometry using the dialog panel/container width. Explicit `center="false"` remains unchanged, including the original minigame result OK at x422/y456. No arbitrary title-wide offsets or approved PDA font changes.

Vegas now displays the original `foundthekey` / `foundthepokerchip` panels through their leavedialog overlay references after the first collected item of each kind in the current campaign. Native 00439f50 / 0041cbd0 call the once-only dialog registry 00445500; 00445810 suppresses the related introductory notice. Current adapter uses the already-persisted first-kind count to avoid repeated notices after reopening. It saves collection before opening, pauses input/time, and resumes on dismiss; original OK actions338/1011 and atlas/button resources are used. Collection timing remains immediate: native pulse/flight and their completion-time dialog ordering are still pending. Native per-profile tutorial flag compatibility across a new campaign is not certified.

Validation: 242 JVM tests, zero failures/errors; debug/test APK builds; private Android composition/touch test confirms both centered original OK buttons plus all-objects panel; real launcher earned-checkpoint replay on WSA display2 confirms both first-found dialogs, UI OK, asynchronous close/resume, autosave, actual process stop/reopen (PIDs674 -> 932), exact state equality and cleanup preserving user files/preferences. No new 25-level Android E2E or Windows fidelity approval is claimed. Frida remains available; static native slices and one targeted instruction read resolved the layout without another hook pilot.

Private evidence: `local-output/vegas-collectibles/native-dialogs-process-verified/report.html`, prepare/verify/cleanup logs and JSON; APK `local-output/vegas-collectibles/vegas-native-collection-dialogs-debug.apk`. Continue with native collector motion in 0041cbd0/0041ce90 and 00439f50/0043a4f0, PDA meter transitions and optional modes. Director/Huntsville, main and approved APKs remain unchanged.

### Native collection meter crops (2026-10-10)

Vegas PDA key/chip fill now copies original pixels at native size, bottom aligned, preserving alpha. VegasCollectionMeterRules implements recovered key crop 50 + trunc(count * promoted original .04f * -45) and chip crop 43 + trunc(count / 25 * -37). The key float constant must retain x87 promotion: count25 crops at6, not5. Native evidence:00443ea0 and chip renderer0041d6d0; chip0 retains the original zero-height state. Generic SDA has no new Vegas coordinates or resource paths. Approved PDA typography, Director/Huntsville and main remain untouched.

Validation:245 JVM tests, zero failures/errors; debug and test APK builds successful; private WSA original-resource instrumentation PASS1 (0.922s), checking every visible pixel/color/alpha and crop placement at counts0,1,5,25. Logs:local-output/vegas-profiles/collection-meters-green.log and collection-meters-android.log. Experimental APK:local-output/vegas-collectibles/vegas-collection-meters-debug.apk. No host mouse/keyboard use or proprietary resources published.

Status:resource composition PARCIAL; Windows matching capture and animation NO VERIFICADO. Pulse/flight, animated meter transitions, native per-profile tutorial flags, optional unlock modes and a new integrated25-level run remain pending. Continue in VegasVisualProfile.drawCollectionMeters, VegasCollectionMeterRules and SdaPrivateCollectibleResourcesInstrumentationTest; native collector motion references0041cbd0/0041ce90 and00439f50/0043a4f0. Goal remains active.
