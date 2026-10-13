# Prime Suspects: integración Director

> **Estado vigente (13-10-2026):** v0.8.1 incluye Prime como **jugable**. El recorrido **22/22 casos** está documentado en **JVM**; en Android constan **9/22 casos completos**, además de pruebas parciales de sonido, escenas y persistencia. No declarar campaña Android totalmente verificada. Este documento conserva la cronología de ensayos; [Estado actual](STATUS_ACTUAL.md).


Objetivo: ejecutar la copia local del usuario en el motor Director compartido,
reutilizando bytecode, score, casts, renderizado, audio y persistencia. Los recursos
comerciales y scripts recuperados permanecen privados. La rama es `intento-geminis`.
Huntsville y Vegas conservan sus rutas y comportamiento existentes.

## Bloque A: recuperación y diagnóstico

Se amplió `verify-local`, reutilizando los lectores, índices y decodificadores
existentes. La auditoría continúa después de archivos inválidos, limita recursos y
archivos, rechaza enlaces simbólicos y escribe informes privados sin sobrescribir.
Distingue integridad de bytes de recuperación y de semántica ejecutada.
El conversor web reconoce Prime antes del subconjunto de casts de Huntsville,
sin anunciarlo todavía como jugable.

- [x] Pruebas sintéticas de inventario mixto, corrupción, contextos de scripts,
  límites, enlaces simbólicos y escritura exclusiva.
- [x] Auditoría mediante `verify-local --output`, sin crear otra herramienta.
- [x] Identificación de Prime Suspects en el conversor existente.
- [x] Recuperación y validación de película y casts locales.
- [ ] Contraste independiente de scripts con LibreShockwave.
- [x] Verificación final y revisión de cambios públicos antes del commit.

## Evidencia local (2026-10-11)

La copia autorizada es `private/prime-suspect/game/JUGAR.exe`. Se ejecutó
directamente ese archivo, cuyo proceso hijo contiene el runtime Director.
Se capturó su película mediante lectura de memoria, sin modificar el ejecutable.
No se utiliza Launch.exe para continuar el trabajo.

Película recuperada: 12.773.753 bytes; SHA-256
`210a54856729bf4db9a28ed85d1b41a9c08967fce50b4acca20b95f1ddff869b`.
El lector validó 1.505 recursos compatibles. ProjectorRays recuperó 106 scripts
legibles con 711 handlers completos. La coincidencia de nombres con el índice
original está comprobada; el contraste independiente de instrucciones está pendiente.

Los 52 casts declaran `10.1.1#16`; sus mapas contienen 3.221 CASt, 3.013 ediM,
2.915 ALFA, 99 BITD y 108 XMED, sin Lscr. La auditoría inicial validó 9.773
recursos compatibles y los decodificadores existentes recuperaron 3.113 medios.
El paquete Director privado incorpora la película y los 52 casts; registra
3.497 bitmaps, 47 recursos Flash y 59 sonidos. Dos bitmaps carecen de datos.
La película declara escenario de 800 × 600, 30 FPS y 263 frames.
Todos los scripts, medios, informes y paquetes comerciales quedan fuera de Git.

## Bloque B: compatibilidad del motor

IMPLEMENTADO y PROBADO mediante pruebas sintéticas y recorrido privado:

- Propiedad legacy `exitLock` (tipo 7, identificador 8), contrastada con los
  mapas de propiedades de LibreShockwave.
- `startQuitMsg`/`closeQuitMsg`: registro del handler Lingo de cierre de ventana.
  El Xtra original `quitMsg.x32` describe este contrato y observa WM_CLOSE;
  el host Android lo adapta al botón Salir y al gesto/tecla Atrás.
- Recursos FileIO adicionales mediante `director-content --file RUTA=ARCHIVO`.
  Se verifican hashes, tamaños y rutas relativas. El archivo `data/dist.dat`
  original se incluye solo en el paquete privado, sin valores inventados.
  FileIO y Buddy API consultan los mismos datos; las escrituras y marcas de
  borrado se conservan en el almacén del usuario, separado del paquete.
- Coerción numérica de referencias de sprite a su canal, necesaria para
  expresiones como `spriteRef + 1`; contrastada con LibreShockwave.
- Propiedad universal `.ilk`, incluyendo referencias de cast vacías.
- Verdad booleana de textos: un texto no vacío es verdadero, incluso `"0"`.
  Contrastado con `Datum::boolValue()` de LibreShockwave. La conversión numérica
  anterior interpretaba como falsas las fichas de memoria y saltaba el caso 2.
- `image.setAlpha()` numérico o con máscara de ocho bits y `extractAlpha()`.
  Pruebas sintéticas cubren RGB conservado, máscara, tamaños y profundidad.
  El recorrido original ya inicializa la escena de linterna de loc14; falta
  verificar visualmente el efecto y el uso de su batería.
  Las máscaras grises extraídas conservan la transparencia parcial y RGB;
  los valores escalares se limitan a 0–255. Para máscaras de color se usa su
  canal azul, mientras LibreShockwave usa luminancia y heurísticas de polaridad.
  Ese caso requiere contraste con Windows antes de afirmar fidelidad completa.
- Transparencia de fondo (ink 36) en formas blancas, igual que en bitmaps.
- El lector Score conserva referencias a miembros no estirados con tamaños
  cero. El renderizador existente obtiene el tamaño natural del bitmap.

Las pruebas nuevas reprodujeron los defectos antes de sus correcciones.
La prueba privada existente ahora falla explícitamente ante excepciones o
labels no alcanzadas; no se ocultan fallos para declarar éxito.

## Bloque C: primer recorrido

PROBADO en el motor JVM con el bytecode y recursos originales recuperados de
JUGAR.exe: splash, entrada de nombre, menú principal (frame 68), primer caso
(frame 85), botón Investigar, mapa (frame 93), tutorial y estación de Ciudad
Capital (loc18, frame 183). Un clic por hit-test alcanza el sprite 28, marca
la pista como encontrada y aumenta el contador original de 0 a 1, sin penalización.
Se renderizaron e inspeccionaron capturas privadas de menú, caso, mapa y localización.

PARCIAL: el recorrido usa el mismo runtime que Android; todavía no se ha probado
la interacción en un dispositivo ni la fidelidad comparada con Windows.
IMPLEMENTADO y PROBADO con paquetes sintéticos: importación y activación por
título. Huntsville conserva su clave activa y su almacén anterior. Prime tiene
selección propia y un almacén estable ligado a la película original, separado
del de Huntsville. Home permite importar y abrir Prime con estado En desarrollo;
el Launcher recibe el título seleccionado y lo conserva al recrearse.
La suite de la app y la compilación del APK pasaron; la revisión no encontró
defectos bloqueantes. Falta verificar este flujo en un dispositivo.
El recorrido JVM también recoge las ocho pistas de loc18 sin penalizaciones.
PROBADO en JVM: cerrar el aviso original de localización completada devuelve
al mapa y guarda las ocho pistas mediante los handlers originales. Un motor
nuevo, inicializado solo con ese almacén exportado, reconoce al jugador,
recupera las ocho marcas del inventario y vuelve al mapa al pulsar Jugar.
El contador temporal `m_itemsFound` no representa el inventario persistido;
la comprobación consulta las marcas originales de loc18. Los comandos y
guardados privados se mantuvieron fuera de Git. Para estos recorridos se
fuerza la ejecución de Gradle: sus entradas no incluyen los comandos externos.
NO VERIFICADO: portada en dispositivo, guardado al interrumpir una localización,
restauración en Android, todos los casos, puzles y desenlace.

PROBADO en JVM para el primer caso: tras restaurar loc18, el recorrido completa
las ocho pistas de loc1 y alcanza las seis necesarias de loc7 (22 en total).
El aviso original conduce a `metagame1`. El conductor privado existente resuelve
el tablero por clics, incluido el tutorial, y el handler original devuelve
`getPuzzleCompleted() = 1` después de 39 intercambios. Se conservan capturas
privadas. El conductor permite elegir el canal del diálogo de Prime (572),
manteniendo el valor anterior de Huntsville (275); no se modificó el motor
para forzar la solución ni se alteró directamente el estado del tablero.
La secuencia posterior al puzle termina y el botón original Siguiente inicia
el segundo caso: `getPlayerRoundNum() = 2`, inventario nuevo y tablero de memoria.
Se exportó el almacén original y otro motor restauró el caso 2, mostró su
presentación y alcanzó su mapa mediante Investigar. El cierre del primer caso
y esta transición están probados en JVM; el puzle de memoria sigue pendiente.
Al ampliar el caso 2 apareció el salto indebido al caso 3 causado por la verdad
de textos. Tras corregirlo, la búsqueda conserva el caso 2 y alcanza loc14.
El conductor ya recoge la batería por clic en el sprite original 158 y completa
la búsqueda del caso 2, incluida loc14. Se verifican `getPlayerRoundNum() = 2`,
`getPlayerMetaGameNum() = 2` y entrada a `metagame2` (frame 258).
El conductor empareja las fichas mediante clics y el getter original confirma
`gMemoryGameBoard.getPuzzleCompleted() = 1`. Se renderizó e inspeccionó la
captura privada posterior, con la foto y el rótulo INOCENTE. Al cerrar el aviso
había una ficha seleccionada: el conductor respeta esa selección y comienza
por su pareja. Queda pendiente contrastar la entrega del clic del aviso con
Windows. Las suites completas de motor y app y la compilación del APK pasaron
tras las correcciones de texto y alpha.
El tablero de memoria del caso 2 también termina su secuencia posterior:
Siguiente inicia el caso 3 (`getPlayerRoundNum() = 3`) y se alcanza su mapa.
El almacén original de ese mapa se exportó para continuar la verificación.
El recorrido privado completa también los casos 3 a 10, alternando tableros
de piezas y memoria y verificando sus getters de finalización y cada número
de caso siguiente. El caso 11 queda iniciado en el mapa con un almacén original
exportado. La búsqueda visita primero la localización de batería indicada por
los datos originales para poder investigar las escenas oscuras; mueve el ratón
y deja actualizar la iluminación antes de pulsar. No modifica inventarios ni
tableros directamente.
El recorrido siguiente completa también los casos 11–20, verificando cada
tablero y transición mediante getters originales. El caso 21 queda abierto
en el mapa con su almacén exportado. El conductor reconoce el mismo botón
Siguiente tanto en su imagen normal como en rollover, sin cambiar el juego.
El recorrido privado ya completa el caso 21 y verifica la transición al 22.
La búsqueda del último caso alcanza `metagame4`; siete movimientos legales
por clic llevan la caja al estado original `endAnim`. Tras su animación y
cerrar el aviso, aparece la carta final con el nombre del jugador, el diamante
recuperado y la medalla. Se renderizó e inspeccionó esa captura privada.
Así queda probado el recorrido hasta la carta final en JVM, con guardados
intermedios y bytecode original; no equivale a fidelidad final en Android.
PROBADO posteriormente: el getter original confirma el estado final 23 antes
y después del regreso al menú. Otro motor, inicializado solo con el almacén
final exportado, reconoce al jugador y conserva `getPlayerRoundNum() = 23`.
Ambos recorridos privados pasaron sus comprobaciones. La ejecución inicial
había sido impedida por un límite de uso de la revisión automática; al volver
a estar disponible se ejecutó sin saltar esa revisión.

## Verificación y siguientes bloques

La suite Python pasó 290 pruebas (dos omitidas). La verificación Android
completa pasó las pruebas del motor y la app y la compilación del APK.
No hay dispositivo ADB conectado. La revisión del bloque detectó y se corrigieron
probes y borrado de FileIO y límites de archivos auxiliares.
Los recursos comerciales no entran en Git.

Prioridad pendiente:

1. Verificar en dispositivo la activación por título y sus portadas, conservando
   el paquete y las partidas actuales de Huntsville.
2. Verificar guardados interrumpidos en cada tipo de puzle y restauración
   de escena; el recorrido completo de los 22 casos ya pasó en JVM.
3. Contrastar scripts e instrucciones con una ejecución de LibreShockwave.
4. Comparar Windows/Android y medir audio, animación y memoria en sesiones largas.

El recorrido hasta la carta final está PROBADO en JVM. La entrega completa
Android y la fidelidad final siguen NO VERIFICADAS. El Launcher aplica el
checkpoint existente a Prime usando sus handlers originales. Quedan pendientes el contraste independiente de
instrucciones, audio y animación frente a Windows, medidas de memoria en
sesiones largas y pruebas de importación/restauración/jugabilidad en dispositivo.

Persistencia parcial PROBADA en JVM: durante loc18 se recogieron tres pistas
y se ejecutaron los handlers originales `setPlayerLocData`,
`setPlayerPuzzleData` y `updatePrefs`, sin salir de la escena ni reiniciar el
tablero. Un motor nuevo recuperó las tres marcas, volvió al mapa y entró de
nuevo en loc18 conservándolas. Esta prueba establece el contrato de guardado;
la restauración directa de escena y las pruebas de pausa/cierre en dispositivo
siguen pendientes.

IMPLEMENTADO y probado con la película original en la prueba JVM de la app: el checkpoint existente
acepta Prime y ejecuta esos handlers periódicamente, al pausar y al salir.
El guardado real mediante `checkpoint.save()` conserva tres pistas en loc18;
un runtime nuevo importa ese almacén y confirma las tres marcas antes y después
de volver a la localización. Se reutilizó el conductor privado existente.
También pasó el recorrido con `afterFrame()` activo en cada tick, cubriendo
el guardado periódico. En el caso final se conserva el tablero original con
`setPlayerPuzzleData(VOID)` y se actualizan pistas, tiempo y demás metadatos.
La ruta y la clave de bookmark de Huntsville se conservan; Prime no consume
ese bookmark ni escribe en él. La app y el APK pasan pruebas/compilación.
Prime restaura por su flujo original desde el menú; falta restaurar directamente
la escena interrumpida y verificar recreación de la app. La caja de gemas final
no tiene tablero serializado por el original y conserva su reinicio original.

Persistencia del puzle de memoria PROBADA en la prueba JVM de la app: en el
caso 2 se resolvieron dos parejas por clic y se guardó con el checkpoint real,
manteniendo también `afterFrame()` activo. Un runtime nuevo importó ese almacén
y recuperó exactamente los 42 valores del tablero, incluidas las cuatro fichas
resueltas. Después completó las parejas restantes por clic y el getter original
confirmó `getPuzzleCompleted() = 1`. El caso ya iniciado vuelve directamente al
mapa por el flujo original, sin repetir su libro inicial. Esto no prueba todavía
pausa/cierre ni recreación en un dispositivo Android.

Audio del paquete verificado: los 59 MP3 recuperados se decodificaron con el
FFmpeg local mediante `caserecomp.audio.decode_swa`, con errores estrictos y
los límites existentes. Todos produjeron PCM16 no vacío a 44.100 Hz, mono o
estéreo; el informe privado conserva hashes del paquete, FFmpeg y cada entrada.
El recorrido de restauración/completado de memoria también comprobó que cada
llamada de sonido original encuentra bytes en `DirectorContent.sound`, incluidos
ambientes y efectos. Esta prueba usa una salida sin reproducción ni duración
simulada: no demuestra sincronización, continuidad, bucles ni fidelidad audible
en Android o frente a Windows.

Audio al pausar: corregida en el servicio compartido la carrera en que un
`onPrepared` posterior a `pauseAll()` iniciaba la pista en segundo plano.
La pista preparada espera a `resumeAll()` y conserva ocupado su canal para
los scripts; las repeticiones finitas aplican la misma condición al reiniciar.
La carga asíncrona del Launcher crea el audio pausado y lo reanuda solo al
presentar una sesión con la Activity activa, cubriendo la pausa durante la carga.
Se añadió una prueba al archivo de instrumentación existente, con el argumento
`privateSoundPackage` apuntando al paquete privado Prime. La app y el APK de
pruebas compilan; la prueba MediaPlayer sigue NO EJECUTADA por falta de dispositivo.

Pruebas puntuales WSA ejecutadas posteriormente: el test MediaPlayer de preparación
durante pausa y arranque al reanudar pasó sin omisiones. La app cargó Prime con
su almacén original de prueba; las tres pistas de loc18 se conservaron después
de `ActivityScenario.recreate()` (prueba pasada, 4,599 s). La transición explícita
a CREATED se atascó en la Activity auxiliar de WSA y se retiró del conductor;
no se cuenta como prueba de pausa completa. No se borraron datos de Huntsville.

## Bot local Android

`tools/prime_android_qa.py` ejecuta por ADB los 22 casos con entradas táctiles,
getters originales y esperas acotadas. Reutiliza el puente Director y los
algoritmos del conductor privado: búsqueda, permuta, memoria y grafo de gemas.
Necesita el APK debug actualizado, WSA/dispositivo conectado y un guardado
privado del caso 1. Ese guardado sustituye el progreso Prime de prueba; los
otros títulos se comparan antes/después, incluso cuando el recorrido falla.

Desde PowerShell, en la raíz del repositorio:

```powershell
$prime = Join-Path $env:LOCALAPPDATA 'Temp/case-recomp-prime-suspects'
python tools/prime_android_qa.py --adb C:/platform-tools/adb.exe --serial 127.0.0.1:58526 --package "$prime/prime-suspects-score.director.zip" --save "$prime/saves/prime-interrupted-location.json" --out "$prime/bot-$(Get-Date -Format yyyyMMdd-HHmmss)"
```

No requiere servicios/modelos ni consume tokens al ejecutarse. El directorio
de salida debe ser nuevo, privado y fuera de Git. Produce `events.jsonl`,
`result.json`, capturas, logcat y muestras de `meminfo`/`gfxinfo` cada minuto.
El tiempo mínimo de sesión es una hora (`--soak-seconds`), además del tiempo
necesario para la campaña completa. Registra pico PSS; no atribuye aprobación
de rendimiento a un objetivo de dispositivo que todavía no esté definido.

Incluye cierre/reinicio en ubicación y tableros de piezas/memoria, comparación
del inventario/tablero recuperado, verificación de portada como ImageView
accesible en catálogo y de los almacenes de otros títulos. El informe falla
si falta cualquier caso, checkpoint, portada o aislamiento. Las consultas
por lotes evitan agotar el reloj original con una llamada ADB por ficha.

Implementación compilada; dos pruebas de protección de salida pasaron.
El smoke WSA ya recuperó el inventario tras cierre de proceso en ubicación.
El recorrido completo del bot está EN EJECUCIÓN: todavía no demuestra los
22 casos en Android ni reemplaza la comparación visual/sonora contra Windows.

Revisión visual WSA: la captura del bot detenido mostró texto del panel demasiado
grande y un rectángulo claro alrededor de EN PAUSA. Prime reutiliza ahora la
calibración de texto de Huntsville, sin sus offsets específicos. El bitmap de
pausa tenía fondo JPEG casi blanco (píxel de esquina 255/254/252), que el matte
exacto no retiraba. Una tolerancia de 12, configurable en el renderer y activada
solo en Prime, elimina ese borde conectado al exterior. Huntsville conserva 0.
La segunda captura WSA confirma el borde eliminado y el texto dentro del panel;
queda pendiente contraste contra Windows para calibración final. Pasaron las
pruebas del renderer y la app y la compilación del APK.

El bot-run-2 terminó con error `64` después de recuperar el tablero parcial:
las fichas resueltas ya no estaban en escena. El solver consulta ahora solamente
los canales visibles, como el conductor privado original. Ese recorrido sigue
sin contar como campaña Android completada.

### Linterna táctil de Prime (2026-10-11)

Solo `title_id=prime_suspects` instala `PrimeFlashlight`: detecta en el Score
el comportamiento activo `flashlight script` (ubicaciones 5, 14 y 17 del original),
sin asumir un número de caso. Debajo de Teclado aparece «Prender foco»; requiere
la batería original y se deshabilita durante diálogos y durante el pulso. Ilumina
el escenario durante cinco segundos, con parpadeo el último segundo, y se cancela
al pausar o cambiar de comportamiento. No modifica inventario, batería ni Score.
Sin ratón ni dedo, el punto Lingo se aparca completamente fuera del escenario;
el dedo ilumina mientras toca y arrastra, y al soltar desaparece el portal.
Huntsville y Vegas no instalan ese adaptador ni ese cambio de entrada.

La corrección compartida de `copyPixels` se limita a planos de índices de alfa
creados por `extractAlpha`, conservados por `duplicate`/`crop`: el círculo blanco
RGBA de `shadowViewArea` se convierte a índice blanco 0 para abrir el portal.
Las imágenes ordinarias de ocho bits conservan la representación anterior.
Pasaron las pruebas de portal, imagen ordinaria, duración/parpadeo, renderer,
perfiles y la regresión completa del motor. WSA confirmó visualmente el portal
y el encendido/apagado del escenario en una copia privada de prueba con batería.
Otra secuencia de capturas WSA confirmó escena sin portal al entrar, portal en
dos posiciones al tocar/arrastrar y desaparición al soltar, sin luz residual
en la esquina.

El bot admite `--speed 2` (predeterminado; rango 1..4), que divide solo las esperas
de juego. Guardado, cierre/reanudación, muestreo y sesión larga siguen en tiempo
real; esto no garantiza duplicar el rendimiento total, dado el coste de ADB.
El bot-run-4 pasó los casos 1..3 y restauró ubicación y puzles 1/2 antes de detenerse
para instalar este ajuste; no constituye una ejecución completa ni demuestra
las verificaciones finales de portada y aislamiento. El nuevo recorrido debe
validar de nuevo todos los controles a la velocidad seleccionada.

El bot-run-6 completó casos 1..6, los tres checkpoints y aislamiento; se detuvo
en el caso 7 porque un diálogo nativo «Sugerencia» bloqueaba el objeto 62.
Ahora, si no encuentra un punto accesible, cierra el diálogo mediante su botón
original y vuelve a comprobar el mismo objeto; si sigue inaccesible, falla.
`--resume-report` conserva los casos y checkpoints de un informe local anterior
con casos contiguos y aislamiento comprobado; exige que el caso guardado sea el
siguiente, sin importar de nuevo la semilla. El nuevo resultado referencia el
informe previo y su SHA-256; sus tiempos y muestras pertenecen al nuevo tramo.
Cinco pruebas del bot pasaron. La campaña completa sigue pendiente.
Tras cerrar el aviso, el sprite 62 de `loc12` seguía sin miembro: el registro
67 del VWSC original de ese frame es vacío, aunque la tabla de objetos referencia
«Insecto de plástico». El bot registra objetos inaccesibles y visita otras
ubicaciones; no inventa sprites ni marca objetos encontrados. Solo cuenta un
caso cuando el puzle y el cambio de ronda originales lo confirman. Falta contrastar
esa discrepancia de la escena contra el ejecutable nativo en Windows.
El bot-run-8 agotó el límite porque faltaba ejecutar el botón MAPA al terminar
una ubicación bloqueada. El bot ahora pulsa `mapButtonA`, espera la transición
y excluye esa ubicación al elegir la siguiente en la misma búsqueda. Una prueba
reproduce el bloqueo y verifica que selecciona otra ubicación; seis pruebas
del bot pasaron. El bot-run-9 retoma el caso 7 desde el guardado nativo.
El bot-run-9 volvió a agotar la búsqueda: la espera de recogida reducida a 1,9 s
permitía tocar durante `m_boolGrabInProgress`, y el original ignoraba esos toques.
La recogida ahora espera `getBoolGrabInProgress() == 0` con timeout de 30 s;
solo se aplazan ubicaciones con objetos inaccesibles, para poder volver a las
que aún conservan pistas. El bot-run-10 continúa el guardado del caso 7.

Pendiente prioritario: corregir objetos pedidos pero desaparecidos/inaccesibles
en escena, empezando por «Insecto de plástico», sprite 62 de loc12. Saltarlos en
el bot no resuelve el fallo; contrastar Score, comportamiento y resultado con
el ejecutable original, restaurar la visualización/interacción y verificar todos
los casos. No dar esta incidencia por resuelta mientras solo se evite el objeto.

Visor de rayos X: el original activa `xray script` y `xray device sprite script`
en loc20, loc24 y loc25; es distinto de la linterna de loc5/14/17. Petición Android:
visor siempre visible, inicio centrado y movimiento con el dedo, conservando la
última posición al soltar; aviso reutilizando el diálogo y gráficos originales.
Revisar el círculo negro del portal y la imagen del dispositivo antes de cerrar
la adaptación. Los relámpagos `light1`..`light6` corresponden a recoger la batería,
que activa la alimentación original de ambos efectos.

Verificación puntual del visor (2026-10-11): Prime conserva la posición táctil
al soltar, centra el visor en (330,300) al entrar en una escena de rayos X y
mantiene visible el dispositivo aunque su batería no esté alimentada. No cambia
las reglas nativas de batería ni los efectos morados. Comprobado en WSA en loc20
y loc24. La puerta de loc24 conservaba RGB bajo su transparencia; la decodificación
Android premultiplicada lo perdía y `setAlpha(255)` dejaba un círculo negro.
Decodificar sin premultiplicar elimina ese círculo; capturas antes/después privadas.
Pruebas puntuales de posición, alfa y renderizador y compilación APK correctas.

El guardado de prueba del caso 7 termina con dos piezas intercambiadas. La silueta
con interrogación está en el recurso original `primeSuspectPhoto19`, elegido por
el script para este guardado; no es un sustituto por falta de imagen. Se preparó
un perfil separado `PrimeCaso7` en el registro del original, con copia previa.
El ejecutable original muestra «Director Player Error: Script Error. Continue?»;
el mensaje no identifica el handler ni permite atribuir todavía una causa.
Los objetos ausentes, incluido el sprite 62 de loc12, siguen pendientes de
restauración fiel: no se dan por corregidos por completar el caso desde otras escenas.

Auditoría del insecto de plástico (2026-10-11): la tabla nativa de loc12 pide
el sprite 62, pero su canal Score 67 en frame 153 tiene tipo, cast y miembro
iguales a cero y no tiene comportamiento asociado. La captura de la película
de JUGAR y la captura del buffer del runtime contienen el mismo VWSC; el paquete
El paquete base Android conserva esa ausencia. El bitmap `plastic bug`, miembro 44 de `12.cct`,
sí existe (38x39). No es un fallo de transparencia ni una imagen omitida por el
empaquetado: falta la colocación del objeto en la escena fuente capturada.
Esto identifica la discrepancia estructural; aún no demuestra si esa edición
del original también la presenta visualmente o la completa mediante otro mecanismo.
Informe detallado privado: `missing-plastic-bug-audit.json` en la carpeta temporal
de Prime. No se han inventado coordenadas ni modificado el guardado para marcarlo hallado.

Intento de contraste nativo puntual: perfil privado `PrimeInsecto`, caso 7, loc12,
solo el insecto pendiente. Se abrió JUGAR directamente; los avisos repetidos
«Director Player Error» impidieron superar la pantalla inicial y llegar a loc12.
La entrada dirigida y los hooks Frida fueron temporales y autorizados expresamente;
se retiraron al terminar. Se cerró únicamente el proceso de esta prueba y se
restauraron las preferencias anteriores. Resultado: confirmación visual nativa
pendiente; este intento no demuestra presencia ni ausencia del insecto en Windows.

Contraste nativo completado tras cerrar todas las instancias anteriores: una sola
instancia de JUGAR (proceso padre e hijo) llegó al menú, caso 7 y loc12 sin el error
de script. La Sala de Teatro de Mingo pide únicamente «Insecto de plástico», pero
el objeto no aparece. Una pista nativa consume un marcador y dibuja los destellos
en la esquina superior izquierda, coherente con el sprite vacío situado en (0,0).
La ausencia también se reproduce en esta copia del original, por lo que no la
introduce Android. Se comprobó solo esa escena y ese objeto. Hooks retirados tras
cada entrada; capturas y resultado conservados en privado. Se dejó una sola
instancia del original abierta en loc12. El error de arranque desapareció en esta
prueba al eliminar las instancias simultáneas; no se atribuyen a eso otros errores.

Reparación en nuestra copia Android (2026-10-11): `PrimeSceneRepairs` agrega
únicamente el sprite 62 ausente de loc12 al cargar el perfil Prime reconocido
por SHA del original. Usa el bitmap original `12.cct/44` y el comportamiento
original `Internal/13`: las pistas, recogida y guardado siguen siendo nativos.
La posición (305,470) es nueva, porque la fuente no contiene coordenadas del
insecto. El cast comienza como `empty.cst`; se conserva la carga dinámica
original de `12.cct`. No reemplaza un sprite o comportamiento ya existente.
No altera Huntsville ni Vegas ni marca objetos encontrados desde el bot.

Verificado puntualmente en WSA: imagen visible y alcanzable, toque real cambia
el objeto 29 de pendiente a encontrado; tras pausa, cierre de proceso y reanudación
permanece encontrado y no vuelve a aparecer. Capturas privadas `before.png` y
`after.png` en `insect-repair-161105`. APK instalada. Pasan los 45 tests de app
seleccionados. La ejecución más amplia encontró aparte un fallo del checkpoint
privado de Huntsville: método `getPlayerMetaGameNum` no disponible en esa sesión;
no se presenta como una validación completa de todos los juegos.

Opciones Android (2026-10-11): Prime reemplaza el control de pantalla completa
por «Apoya al desarrollador», reutilizando estilo y enlace de Huntsville. Usa
los sprites nativos 558/561 y respeta la pestaña de ayuda. La calibración de
volumen de Huntsville no se aplica a Prime. APK instalada, 45 tests de app pasan
y botón comprobado visualmente en las opciones de Prime en WSA.

Estimación de avance global: aproximadamente 90%, orientativa, no medida automática.
Campaña completa JVM: 22/22. Campaña Android documentada: 9/22 completos (41%);
restan casos 10–22, fidelidad audiovisual y estabilidad/rendimiento prolongados
antes de declarar entrega final. Las pruebas puntuales posteriores no cuentan
como casos completos adicionales.

Salida y continuación Android (2026-10-11): Prime ahora usa un marcador propio
`prime-bookmark-v2`, separado del marcador v1 de Huntsville. Conserva usuario,
ronda, escena/puzle y reloj, además del inventario, pistas, batería y tableros
serializados por los handlers originales. Se guarda periódicamente (5 s), al
pausar y antes de salir; las escrituras del checkpoint se confirman juntas.
«Salir» detiene frames/reloj, guarda antes del cierre y vuelve a Home. Un fallo
real de escritura evita salir y permite reintentar. Home muestra «Continuar» si
existe un marcador válido fuera del menú interno; al abrir se reconstruye la
escena directamente mediante los handlers originales. La importación de un
seed invalida los marcadores previos del mismo namespace.

La caja final añade un checkpoint específico de campos de tablero y posiciones
de las siete gemas, estado del botón Reset, tiempos relativos y frame Flash.
La lupa conserva su última posición. La batería sigue siendo nativa: al recogerla
aparecen rayos morados; su indicador lateral pulsa con energía, y los modos
linterna/rayos X consultan el flag de batería. El diálogo original avisa si falta.

Pruebas puntuales WSA usando el botón real SALIR y el botón Continuar del selector:
- loc12: mismo inventario y misma escena, reloj no reiniciado.
- metagame1: mismo tablero de piezas.
- metagame2: mismas parejas resueltas del tablero parcial.
- metagame4: una gema y sus siete posiciones conservadas; tablero sin movimientos
  conserva resetB y Reset vuelve a limpiar el tablero correctamente.
- loc20 alimentada: lupa vuelve a point(201,334), con el mismo inventario/batería.
Capturas y resultados privados en `resume-check-163712`, `164520`, `164356`,
`165056` y `165210`. 47 tests de app y 7 tests del bot pasan; APK instalada.
La edición nativa revisada selecciona únicamente puzles 1/2 (y la caja 4 al final);
no se forzó el puzle de palabras 3 para atribuirle validación de campaña.

Esto conserva el estado jugable reconstruido, no un volcado completo de RAM
como el save state de un emulador. No demuestra identidad de todos los efectos,
sonidos, overlays temporales ni estados AVM internos; la rama de animación final
conserva su frame y temporizador pero no se probó de extremo a extremo. La campaña
Android 10–22 continúa pendiente; estas pruebas no agregan casos completos.

Portadas del selector (2026-10-11): Prime usa el bitmap original interno 140,
con el logo MCF/Prime Suspects. Vegas usa `mpi_title.jpg`, el letrero de neón
de su intro. Los paquetes ya importados reciben una caché de portada nueva
sin reimportar ni cambiar su SHA/partidas. Huntsville conserva su mecanismo
anterior. El generador de paquetes Vegas también prefiere la imagen de título,
con fallback al fondo XUI para recursos antiguos. Verificado visualmente en
WSA; captura privada `covers/selector-101.png`. Pasan 47 tests de app y tres
pruebas de empaquetado Vegas; APK instalada. Los recursos comerciales siguen
fuera del repositorio.
