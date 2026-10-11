# Prime Suspects: integración Director

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
