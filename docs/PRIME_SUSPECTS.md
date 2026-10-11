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
NO VERIFICADO: importación y activación aisladas por título en la app, portada,
restauración de una partida de Prime, todos los casos, puzles y desenlace.
La app conserva todavía una única selección activa Director; su separación
por título es el siguiente trabajo necesario antes de habilitar Prime en Home.

## Verificación y siguientes bloques

La suite Python pasó 290 pruebas (dos omitidas). La verificación Android
completa pasó las pruebas del motor y la app y la compilación del APK.
No hay dispositivo ADB conectado. La revisión del bloque detectó y se corrigieron
probes y borrado de FileIO y límites de archivos auxiliares.
Los recursos comerciales no entran en Git.

Prioridad pendiente:

1. Separar activación, recursos y persistencia de cada título en la app sin
   perder el paquete y las partidas actuales de Huntsville.
2. Ampliar el recorrido original: más objetos, regreso al mapa, puzles,
   progresión, guardados y restauración; no traducir manualmente el Lingo.
3. Contrastar scripts e instrucciones con una ejecución de LibreShockwave.
4. Comparar Windows/Android y medir audio, animación y memoria en sesiones largas.

El juego completo y la fidelidad final siguen NO VERIFICADOS.
