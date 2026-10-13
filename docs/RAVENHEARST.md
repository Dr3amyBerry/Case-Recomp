# Ravenhearst: integración Director

> **Actualizado 13-10-2026:** título **jugable, en revisión** en v0.8.1. El bloque de más abajo que declara Android «NO VERIFICADO» refleja su fecha inicial; hay nuevos commits y tests. [Estado actual](STATUS_ACTUAL.md).

## Cambios posteriores al primer bloque

- Motor Director/Flash integrado, menús, candados, puzle del diario y adaptaciones Android de teclado, rotación y textos.
- Los commits de octubre 11–12 incorporan reanudación a mitad de habitación, candado y diario (`10c6cd3`), así como el bot de pruebas `tools/ravenhearst_android_qa.py` y mejoras de navegación.
- Falta confirmar los quince candados por vías naturales, todos los capítulos y final en Android, los efectos y sonido Flash, la comparación con Windows y sesiones de estabilidad. Una prueba que forzó desbloqueos no representa una campaña completa.


Objetivo: ejecutar la copia local del usuario de *Mystery Case Files: Ravenhearst*
en el motor Director compartido, igual que Huntsville y Prime Suspects. Los recursos
comerciales y los scripts descompilados permanecen privados y fuera de Git.

## Origen

La copia es la edición Big Fish de `Ravenhearst.exe` (proyector Director MX 2004,
`10.1.1#16`), con casts en `data/game` (`room1`…`room32`, `puzz`, `sfx`) y
`data/language/interface_us.cct`. A diferencia de Prime, la película está en claro
dentro del ejecutable: `director-content` la lee directamente, sin captura de memoria.
SHA-256 del ejecutable, que identifica el título en la app:
`2ca01a3489399d7647c27f7ea312f33b83951f428f5c31b0f6a73ce213440174`.

El paquete privado registra 3.726 bitmaps, 92 sonidos (sin SWA: no requiere FFmpeg)
y 25 recursos Flash; 14 bitmaps no tienen datos en el original.

## Cambios compartidos necesarios

- Mapa Afterburner: se ignoran las ranuras libres (fourCC nulo y tamaños cero).
- Varios casts internos: la película guarda `room10` (lib 12, sin ruta) dentro del
  exe. Cada CAS* se asocia por KEY* al ID de su librería en MCsL. El bundle añade
  `internal_casts` solo cuando existen; los medios van a `media/internal<N>/`.
- Scripts en casts externos: `interface_us.cct` tiene su propio contexto Lingo
  (por ejemplo `item class`). Esos scripts conservan su tabla de nombres y su cast;
  `script("…")` y los behaviours de esa librería los resuelven.
- Paleta System Windows (−102): se aceptan los 250 índices en que coinciden
  dirplayer-rs y LibreShockwave; los 6 discrepantes siguen rechazados.
- `baKeyIsDown` (Buddy API) con códigos de tecla virtual de Windows.
- VOID se compara como 0 frente a números (`findPos(...) <> VOID`), contrastado
  con LibreShockwave. Sin esto no se podía crear el jugador.
- Flash: una etiqueta JPEGTables vacía ya no rompe la carga.
- Índices de parámetros de behaviour fuera de rango se ignoran al empaquetar.

Huntsville genera exactamente las mismas huellas `movie.json`/`lingo.json` que
antes. Los recorridos JVM privados de Prime (ubicación interrumpida y memoria
parcial) pasan con las mismas verificaciones.

## App

`ravenhearst` se identifica por el SHA-256 del ejecutable. Tiene almacén propio
ligado a ese origen (como Prime), así que reimportar el paquete conserva partidas.
Presentación: la calibración general de texto de Huntsville, sin sus desplazamientos
específicos ni la tolerancia de matte de Prime. Portada: miembro `title`.
El conversor web reconoce la carpeta por sus casts.

## Bloque 2: puertas Flash, puzle del diario y ajustes

- Texto: Ravenhearst y Prime usan los desplazamientos del nombre de Huntsville
  (campo y cursor, sprites 33/31, y su lista de usuarios). En Ravenhearst,
  «Pistas ocultas restantes» se centra y baja (sprite 699); el número no se mueve.
- `gotoNetPage` abre la URL en el navegador; `objeto.handler(#nombre)` existe.
- ActionScript (SWF 8) de los 15 candados: DefineFunction2 con registros y
  precargas, closures, `this` correcto en métodos, Array, Math, métodos de texto,
  setInterval con el reloj del runtime, hitTest, attachMovie, depths, arrastre,
  ratón local, módulo, igualdad estricta y bit a bit. `sprite(n).funcion()` llama
  a funciones ActionScript; `getVariable` también ve `_global`.
- SWF: DefineButton2 (estados, área hit, acciones por condición), DefineShape4,
  DefineMorphShape2 (solo forma inicial) y sonidos MP3 propios de cada Flash.
- Puzle del diario: `createMatte`/`createMask` y `#maskImage`/`#maskOffset`,
  `new(#bitmap, member(n))` sustituye el miembro, imágenes nuevas en blanco,
  `point`/`rect` modificables con `p[1] = x`, `the rightMouseUp`.
  En Android, «Girar pieza» aparece solo en el puzle: se pulsa con otro dedo
  mientras se arrastra (equivale a la barra espaciadora del original).

PROBADO en JVM: los 15 candados con 300 clics/arrastres aleatorios sin avisos
de ActionScript; capítulo 1 completo (22 pistas, puerta abierta forzando su
variable de desbloqueo) y el puzle del diario resuelto pieza a pieza (30 piezas,
giros incluidos) hasta «Encontraste una anotación del diario».
NO VERIFICADO: resolver los candados jugando, sonido de los Flash en dispositivo,
morph shapes animados y textos dinámicos dentro de los Flash.

## Estado inicial de la investigación (histórico)

PROBADO en JVM con el bytecode original: splash, carta inicial, entrada de nombre,
menú principal, selección de modo, diario, mapa, entrada al pórtico y recogida de
dos objetos (pistas restantes 22 → 20).

NO VERIFICADO: dispositivo Android, guardado/restauración en la app, puzles,
resto de habitaciones, final, audio audible y fidelidad frente a Windows.
