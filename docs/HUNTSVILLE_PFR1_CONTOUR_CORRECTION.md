# Huntsville: corrección experimental de contornos PFR1

Fecha: 2026-10-08. Estado: **CURVAS CORREGIDAS; REVISIÓN VISUAL HUMANA PENDIENTE**.
Este resultado sustituye el diagnóstico de deformaciones de la prueba 0.1.
No autoriza incorporar fuentes al motor Director, cambiar el perfil de Huntsville
ni reemplazar la APK aprobada.

## Referencia independiente

Se probaron los seis payloads PFR1 originales, sin modificar sus encabezados, con
Pillow/FreeType 2.13.3. Todos fallan con `unknown file format`. El
[lector oficial de FreeType](https://raw.githubusercontent.com/freetype/freetype/master/src/pfr/pfrload.c)
exige la firma PFR0 en `pfr_header_check`. Por tanto, FreeType no sirve aquí como
lector independiente del original PFR1. Sí carga y rasteriza el CFF/OpenType final.
No se falseó una aceptación cambiando la firma a PFR0.

Se compiló un adaptador independiente para el lector Rust de
[DirPlayer](https://github.com/igorlira/dirplayer-rs), revisión
`68376fbb4494a6bbad4c70081ecdcb99814a74c9`, sin ejecutar su VM.
Leyó los dos PFR1 originales y sus 225 registros por cara.
LibreShockwave reconoce a DirPlayer entre sus antecedentes: son implementaciones
separadas, pero no deben presentarse como algoritmos sin ascendencia compartida.
Esta comparación tampoco equivale a comprobar píxeles del renderer nativo Director.

## Causa y corrección

LibreShockwave `fca530f9ef388d7ff38fa6c7117feae5bb5411c6` devolvía la coordenada
actual cuando `orusLookup` recibía dirección cero. En las curvas ortogonales de
`processCurve` esa dirección es implícita: se obtiene comparando la coordenada
actual con la anterior; después se busca el siguiente ORU en ese sentido.
La devolución inmediata colapsaba controles y extremos de las curvas.

La corrección pasa la coordenada anterior a las cuatro consultas implícitas de X/Y:

- Actual mayor que anterior: siguiente ORU estrictamente superior.
- Actual menor que anterior: siguiente ORU estrictamente inferior.
- Coordenadas iguales: conservar la coordenada.

No contiene ajustes por carácter ni coordenadas específicas de Tekton. Se conserva
el checkout externo y se genera una copia privada con un parche AGPL-3.0 identificado.
El preparador exige el hash del archivo upstream y rechaza sobrescribir salidas.
La prueba C++ cubre ambos sentidos, igualdad, búsqueda estricta, dirección explícita
y tabla vacía; falla contra la implementación anterior.

Se corrigieron además dos pérdidas de precisión en nuestra conversión: el JSON C++
ahora conserva `max_digits10` y FontTools usa `roundTolerance=0`, evitando redondear
las coordenadas fraccionarias de componentes a enteros. Las curvas siguen siendo
cúbicas CFF. El anterior problema `loca/indexToLocFormat` del conversor TTF externo
es un fallo distinto y no participa en esta ruta.

## Comparación completa

Se normaliza únicamente la arista recta final explícita/implícita de cada contorno.
Se comparan tipos de comandos, controles, extremos, avances y métricas; no se
compara solamente una selección visual de letras.

| Medida | Tekton normal | Tekton itálica |
|---|---:|---:|
| Registros PFR1 comprobados | 225 | 225 |
| Registros con geometría igual al lector Rust, tolerancia 0,001 | 214 | 215 |
| Compuestos con truncamiento entero identificable | 10 | 10 |
| Otra diferencia de punto fijo inferior a una unidad | 1 (`ì`) | 0 |
| Error máximo entre lectores, unidades de fuente | 0,949219 | 0,964844 |
| Caracteres Unicode convertidos y comprobados en CFF | 219 | 219 |
| Error máximo CFF frente a contornos corregidos | 0,0000005 | 0,0000005 |

Ambos lectores coinciden en estructura y avances de los 450 registros. Las 21
excepciones de coordenadas se informan expresamente: Rust aplica matrices y
redondeos enteros en componentes, mientras el parser C++ conserva fracciones.
En `ì` normal, una coordenada es 648,978516 frente a 649: el punto fijo previo a
la traslación no equivale a truncar la coordenada final. La comparación **no es
bit a bit** en esos compuestos. No se ha elegido aún qué política reproduce mejor
el renderer nativo ni se ha recuperado su hinting/kerning.

Antes del arreglo, 217 registros normales y 214 itálicos divergían del lector Rust
en al menos una unidad o en la estructura de comandos. Después no quedan diferencias
de estructura ni divergencias de una unidad o más. El CFF conserva todos los
contornos convertidos, incluidos los compuestos fraccionarios.

## Nueva prueba visual y validación

La versión **0.2-experimental** del laboratorio independiente está instalada y abierta
en WSA, package `org.rigorcore.caserecomp.fontlab`. Permite texto editable, tamaño
nominal y selección de itálica. No comparte partidas con el juego.

Artefactos locales privados, conservando la prueba 0.1 como evidencia anterior:

- `local-output/huntsville-font-experiment-2026-10-08-v2/tekton-normal-comparison.png`.
- `local-output/huntsville-font-experiment-2026-10-08-v2/tekton-italic-comparison.png`.
- `local-output/huntsville-font-experiment-2026-10-08-v2/comparison-android.png`.
- `local-output/huntsville-font-experiment-2026-10-08-v2/tekton-fontlab-v2-debug.apk`.
- `experiment-reference.json` en ese directorio: resultados y hashes de artefactos.

Las láminas tienen cuatro columnas: LibreShockwave anterior; contornos del PFR1
leídos por Rust; LibreShockwave corregido; CFF de FontTools rasterizado por FreeType.
Las primeras tres se dibujan directamente con Matplotlib/Agg, sin generar otra fuente.
La lámina Android compara la aproximación sans-serif aprobada con las fuentes nuevas.
Se revisaron visualmente ambas caras: desaparecen las deformaciones de `C`, `a`,
`e`, `g`, `9`, palabras y acentos de las muestras.

Validación: 282 tests Python, dos omitidos; regresión C++ satisfactoria; build del
laboratorio y prueba instrumentada en WSA satisfactorias. La prueba Android confirma
carga, cobertura de las muestras y dibujo; no certifica fidelidad al renderer nativo.
Los 39 originales y ambas APK congeladas conservan sus hashes. No se tocó el motor,
el perfil revisión 6 ni el proyecto Android principal.

La incorporación a Huntsville permanece pendiente de comparación nativa de métricas,
punto fijo/hinting y revisión visual/autorización humana. Este experimento permite
revisar la corrección sin alterar la referencia aprobada.

Referencia de la APK experimental 0.2: SHA-256 `db66c1c41474f482b275352c517924e98aa0b1a68c97cee16f86d7b04ca33922`.

## Dos builds jugables para comparación (2026-10-08)

El usuario aclaró que quería dos versiones del **juego**, no dos ventanas del
laboratorio de glifos. Esa petición autoriza las APK experimentales separadas;
no constituye aprobación del nuevo renderer para la build de referencia.

Se prepararon dos copias privadas del proyecto Android mediante
`tools/font-experiment/prepare_game_comparison.py`. La APK Android conserva el
renderer actual; la APK Tekton carga los OTF corregidos normal/itálica solo para
caras `tekto`, con escala nominal de tamaño/anchura. Las demás caras mantienen
su ruta actual. El ajuste de cajas y el paso de línea se conservan para esta prueba;
no se han vuelto a calibrar posiciones o métricas. Los fuentes del motor y el
archivo de perfil revisión 6 son idénticos en ambas copias y el proyecto principal
no se modifica.

Ambas compilaron, se instalaron y están abiertas en WSA con títulos distintos:
`Huntsville - Android experimental` y `Huntsville - Tekton experimental`.
Se importó el mismo `private/huntsville/huntsville-m3/huntsville.director.zip`
por la ruta ADB existente. Se comprobó visualmente el caso 1 en las dos ventanas
con texto Android/Tekton dentro de la escena del juego; sin errores AndroidRuntime
observados. La captura conservó foco y puntero.

La revisión automática rechazó exportar partidas/preferencias de la app aprobada.
No se efectuó esa copia: cada experimento usa sus propios datos. Los paquetes son
`org.rigorcore.caserecomp.huntsville.androidfonts.debug` y
`org.rigorcore.caserecomp.huntsville.tektonfonts.debug`; no sustituyen la app aprobada.

Artefactos privados en `local-output/huntsville-playable-font-comparison-2026-10-08/`:
`huntsville-android-experimental-debug.apk`,
`huntsville-tekton-experimental-debug.apk`, `android-game.png`, `tekton-game.png`
y `reference.json` con hashes. Las dos APK aprobadas conservan sus hashes anteriores.
La revisión visual humana y la decisión de adoptar Tekton siguen pendientes.

## Decisión humana tras probar el juego

El 2026-10-08 el usuario eligió las letras Android: «nos quedamos con android,
las letras de android son mejores». Tekton no se adopta en la versión actual.
La investigación y sus artefactos se conservan; no hay autorización para sustituir
el renderer Android por Tekton. Los posteriores ajustes de volumen y botón de apoyo
se documentan en [HUNTSVILLE_OPTIONS.md](HUNTSVILLE_OPTIONS.md).
