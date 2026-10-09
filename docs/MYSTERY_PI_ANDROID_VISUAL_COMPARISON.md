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
