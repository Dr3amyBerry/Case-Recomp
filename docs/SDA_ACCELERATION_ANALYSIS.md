# SDA: análisis de aceleración y comparación con un segundo título (2026-10-09)

Análisis de solo lectura (Claude) para la continuación del Goal V3 (`GUION ACTUAL.MD`). No se publica código, pseudocódigo ni recursos comerciales.

## Vegas: datos del ejecutable

- `MysteryPIVegas.exe`: 1,5 MB, PE x86 32 bits, linker 8.0 (Visual Studio 2005), sin empaquetar. Requisitos originales: Pentium 350 MHz, DirectX 7.
- Imagen por DirectDraw (carga dinámica, con OpenGL/GDI alternativos); sin Direct3D. Audio con `bass.dll` (17 funciones). ~220 funciones de Windows importadas.
- Sin PDB y sin RTTI propio (solo clases de la librería estándar): ningún decompilador recuperará nombres originales.
- SDA no tiene bytecode: XUI describe la interfaz; la lógica está compilada. No existe un "intérprete SDA" análogo al de Lingo.

## Herramientas evaluadas

- IDA Pro/Hex-Rays, Binary Ninja, RetDec (mantenimiento limitado) y angr: mejora marginal frente a Ghidra 12.1.4; ninguna produce C++ compilable. El cuello de botella es la semántica, no el decompilador.
- OOAnalyzer (CMU SEI, plugin Kaiju para Ghidra): recuperación de clases C++ MSVC sin RTTI.
- Recompilación estática (remill, rev.ng, McSema): inmadura para Win32 GUI con llamadas indirectas. Útil, como mucho, para funciones aisladas usadas como referencia en PC.
- Emulación (runner x86 + HLE de Win32, Winlator/Wine + Box64): vía alternativa no adoptada. El usuario mantiene el motor Kotlin del Goal V3.
- Acelerador compatible con V3.4: ejecutar funciones x86 originales aisladas en un emulador de CPU en PC (p. ej. Unicorn) como **juez automático** de las traducciones Kotlin.

## Segundo título: Mystery P.I. – Lost in Los Angeles (copia local fuera del repositorio)

- Mismo motor SDA, versión posterior: APIs Unicode (W), zlib, audio BASS + OGG.
- Recursos en un archivo `.z`: `u32 count`, después por entrada `u32 offset, u32 raw, u32 comp, u32 nameLen, nombre UTF-16LE`. Algunos nombres llevan relleno extra; el inicio de la entrada siguiente es `offset+comp`. Los datos comienzan tras el índice y están comprimidos con zlib cuando `comp != raw`. Se extrajeron las 3.046 entradas sin errores, y el índice cuadra exactamente con el tamaño del archivo.
- **El ejecutable conserva RTTI con 134 clases del namespace `sda`** (`xui*` del framework y `mpi*` del juego: TileRot, TileGame, JigSaw, WordSearch, Pda, Hint, Map, Clock, MainMenu, Levels, EyeSpy*, FinalPuzzle*, Match*, Solitaire, PlaceGame, SpotDiff, WordJumble…).

| Comparación XUI/recursos | Vegas | Los Angeles | Comunes |
|---|---|---|---|
| Etiquetas | 87 | 90 | 61 (70 % de Vegas) |
| Atributos en etiquetas comunes | 559 | 496 | 405 (72 % de Vegas) |

- Comunes: framework XUI, escenas de objetos ocultos (`.msl`/`.mse`), PDA, mapa, reloj y puntuación, TileRot (`.trg`), TileGame (`.tgl`), Jigsaw (`.jsw`) con atributos nuevos.
- Solo Vegas: desenlace (adivinanzas, huellas, fichas), WordSearch (`.wsg`), algunos controles de mapa/finale.
- Solo Los Angeles: puzle final y siluetas, Jigsaw final, Match 1/2/3, Solitario, PlaceGame, insignias/carretes.
- Los porcentajes miden vocabulario compartido, no comportamiento idéntico.

## Conclusiones para el Goal V3

1. La arquitectura "motor SDA + módulos de mecánicas + adaptador por juego" está respaldada: framework, escenas y 3 de 4 minijuegos son reutilizables. Por juego quedan el lector de recursos, el desenlace, los minijuegos nuevos y las diferencias de versión.
2. **Acelerador principal recomendado:** usar el RTTI de Los Angeles para nombrar Vegas con **Ghidra Version Tracking** (incluido en Ghidra) o BinDiff. Así se transfieren nombres de clases, vtables y métodos al mismo motor sin RTTI (aplica V3.4.A).
3. La copia de Los Angeles es un reempaquetado de terceros (`JUGAR.exe`, cabecera modificada): sirve de referencia técnica, pero no como evidencia definitiva de comportamiento.
