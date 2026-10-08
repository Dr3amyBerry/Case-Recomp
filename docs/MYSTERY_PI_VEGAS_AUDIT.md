# Mystery P.I.: The Vegas Heist ? auditor?a t?cnica inicial

Fecha: 2026-10-08. Inspecci?n local est?tica; no se ejecutaron ejecutables, instaladores, desinstaladores ni DLL originales. Estado de cat?logo propuesto: **investigaci?n, m?dulo no disponible, jugabilidad no verificada**.

## Identificaci?n fundada en contenido

La copia local se encuentra en `private/mystery-pi-vegas/game/`. Se inspeccionaron cabeceras PE, imports, directorios de recursos, versiones fijas, firmas de medios y cadenas tecnol?gicas. Las huellas SHA-256 completas y los ?ndices quedan en `research/static-audit.json` bajo la carpeta privada del juego. El script reproducible `static_audit.py` usa s?lo Python stdlib y el inspector existente. Desde la ra?z del repo:

```powershell
python -c "import runpy; runpy.run_path('private/mystery-pi-vegas/research/static_audit.py', run_name='__main__')"
```

El script lee bytes y no usa el loader de Windows.

| Archivo | Observaci?n comprobada |
|---|---|
| `MysteryPIVegas.exe` | 1.499.136 bytes, PE32 x86 (`0x014c`), versi?n fija de archivo/producto 1.0.0.1. El lector existente encuentra cero pel?culas Director reconocibles. |
| `Resources.dll` | 37.212.160 bytes, PE x86; sin imports en el directorio normal. 3.089 hojas de recursos: 3.088 de tipo `RAWDATA` y un manifest tipo 24. |
| `bass.dll` | 92.728 bytes, PE x86; versi?n fija de archivo 2.3.0.1 y producto 2.3.0.0. |
| Readme de la copia | Se presenta como The Vegas Heist, distribuci?n Zylom y V1.0.0.3L, build 2008-04-24. Esto difiere de la versi?n fija del exe; registrar ambas, no inventar una equivalencia. |

El ejecutable contiene referencias a SDA Software Associates y una descripci?n del driver SDL para SDA Game Framework. Importa BASS para samples/streams/play/pause/volume/sync, usa APIs Win32 de ventanas, archivos, tiempo y registro, y contiene referencias de drivers SDL/DirectDraw. **Conclusi?n con evidencia fuerte: esta copia sigue una ruta nativa x86 de SDA Game Framework con SDL/BASS, distinta de Director/Lingo.** El SDK/versi?n exacta del framework, el grado de enlace est?tico de SDL y el modelo completo de ejecuci?n todav?a no se han reconstruido.

La ausencia de una pel?cula reconocida por nuestro lector es evidencia negativa acotada, no una demostraci?n de que ning?n fichero oculto o ninguna otra edici?n pueda usar Director. Se combina con las referencias positivas a SDA y el esquema de recursos; no se identifica el motor s?lo por `MysteryPIVegas.exe`.

Para contraste, la copia de Huntsville tiene un contenedor `XFIR/FGDM` con identificador Director `8.5.1#104` dentro de su PE. No hay base para aplicar su bytecode, casts o reglas de fuentes a esta copia de Mystery P.I.

## Recursos observados y l?mites del an?lisis

Firmas al comienzo de hojas PE de `Resources.dll`:

| Firma | Hojas |
|---|---:|
| PNG | 2.684 |
| JPEG | 293 |
| Ogg | 41 |
| XML con declaraci?n sin BOM | 3 |
| Otras firmas, incluyendo texto con BOM/XUI/localizaci?n | 68 |

Son hojas, no un recuento de objetos ?nicos del juego. La extensi?n del recurso no sustituy? el an?lisis de cabecera. De los recursos de texto se identificaron **43 documentos con ra?z XUI**, contando BOM: 25 aceptados por un parser XML est?ndar y 18 rechazados por prefijos sin namespace enlazado. Se registraron tags por an?lisis l?xico independiente. No se corrigieron los originales ni se asumi? que el dialecto fuera XML est?ndar ?ntegro.

Los tags observados incluyen texturas, im?genes, fuentes/kerning, labels, audio y construcciones `mpi:*` para escenas y componentes de juego. Hay atlas PNG de fuentes y definiciones de kerning. La existencia de XUI no demuestra que toda la l?gica est? en esos documentos: el PE contiene c?digo nativo ejecutable. Un port basado s?lo en mover im?genes omitir?a comportamiento y no es una soluci?n de compatibilidad.

No se publican contenidos de escenas, coordenadas, listas de objetos, recursos originales, dumps de c?digo ni hashes de saves de usuario. Los informes p?blicos contienen ?nicamente resultados tecnol?gicos agregados; los ?ndices originales permanecen privados.

## Reutilizaci?n y diferencias

| Necesidad | Reutilizar | Trabajo pendiente y l?mite |
|---|---|---|
| Importaci?n e integridad | streaming, SHA-256, l?mites, staging y almacenamiento privado existentes | Adaptador PE/resources y paquete propio; no pasar por `DirectorContent` ni usar `.director.zip` para SDA. |
| Presentaci?n Android | viewport, letterboxing, superficie, reloj, lifecycle y controles de sesi?n | Exportar contratos sin tipos Director; preservar origen de coordenadas, alpha/recortes/blend del framework. |
| PNG/JPEG | decoder Android acotado y manejo de alpha | Verificar alfa, orientaci?n y atlas con fixtures; no importar heur?sticas Director de ink autom?ticamente. |
| Fuentes | infraestructura de im?genes y layout como base | Servicio de atlas/kerning; usar datos originales privados. No heredar Palatino/Tekton o margen centrado de Huntsville. |
| Audio | pausa, resume, recursos privados y backend de reproducci?n | Ogg, canales, looping, fading y callbacks BASS observados; verificar c?dec exacto de Ogg. No se ha ejecutado un decoder ni probado sincronizaci?n. |
| Entrada | captura Android touch/mouse/teclado | Adaptador SDL/SDA de eventos y coordenadas; no exigir mouseDown retrasado 100 ms. |
| Guardados | namespace, persistencia y archivos privados | Reconstruir formatos, ruta l?gica y atomicidad nativas. Imports de registro s?lo prueban lecturas de esas APIs, no que los saves residan en registro. |
| Animaci?n/UI | reloj, scheduling y servicio de presentaci?n | Dialecto XUI, scene graph, componentes nativos, orden y callbacks. Flash no es una dependencia demostrada. |
| Diagn?sticos | m?tricas, l?mites y harness sint?tico | Traza tecnol?gica SDA con eventos comprobados; no simular ?xito cuando falte un componente. |

Estas mejoras comunes tambi?n beneficiar?an a Huntsville: presupuesto global, instalaci?n por juego, namespaces estables y migraci?n expl?cita, audio preparado durante pause, backend de efectos, identificaci?n por fuente y diagn?stico de capacidades faltantes. No se cambia ahora su runtime para conseguirlas.

## Estrategia de m?dulo adicional

Propuesta: identificador de investigaci?n `sda-native`, con parser offline PE/RAWDATA/XUI y m?dulo de ejecuci?n separado. No afirmar que exista una versi?n universal de SDA soportada. Versionar cada contrato reconstruido con evidencia, no con un nombre comercial.

| Ruta | Evaluaci?n | Dependencias y prueba de decisi?n |
|---|---|---|
| Runtime compatible SDA reconstruido | Direcci?n preferida si se logra definir el contrato compartido de recursos, componentes y eventos | An?lisis est?tico de llamadas y native traces independientes. Demostrar cierre de dependencias de l?gica de una escena antes de anunciar viabilidad; no copiar l?gica en perfiles. |
| Traducci?n/recompilaci?n de c?digo nativo con capa ABI | Investigaci?n alternativa si XUI depende extensamente de l?gica compilada | Determinar ABI, relocations, APIs usadas y control de runtime; prototipo aislado con programa x86 sint?tico. Sin viabilidad demostrada en esta auditor?a. |
| Emulaci?n x86 y compatibilidad Windows/SDL | Alternativa evaluable, no recomendaci?n de instalaci?n ni backend presente | Presupuesto CPU/memoria, soporte Android y compatibilidad de API. No cargar DLL x86 en Android ARM directamente. |
| Parchear Director o reescribir cada escena MPI a mano | Descartada arquitect?nicamente | Mezcla tecnolog?as o crea un port por t?tulo y pierde comportamiento original. |

El paso siguiente es elaborar un grafo de llamadas/recursos y un inventario de capacidades a partir del c?digo nativo, sin alterar originales; despu?s capturar comportamiento de referencia en un entorno controlado. No hay promesa de ejecuci?n Android ni plazo estimable antes de cerrar esa incertidumbre.

## Pr?ximos hitos de investigaci?n

1. Determinar codificaci?n/localizaci?n y dialecto XUI real, sintaxis de prefijos, fuentes/atlas y referencias entre recursos. Prueba: fixture sint?tico de cada forma, truncados y l?mites.
2. Delimitar qu? resuelve el framework y qu? reside en componentes nativos, incluidos los `mpi:*`. Prueba: escena de referencia y grafo de dependencias; sin sustituir su l?gica mediante perfiles.
3. Capturar startup, men?, una escena, aciertos/fallos, animaci?n, audio, guardado/reapertura y salida. Guardar evidencia privada por versi?n/huella y acciones reproducibles.
4. Seleccionar ruta de ejecuci?n seg?n resultados de 1?3. El cat?logo sigue en investigaci?n mientras falta un m?dulo o una capacidad obligatoria.
5. Integrar un slice detr?s de las interfaces comunes y ejecutar los controles cruzados de [MULTIGAME_REGRESSION_PLAN.md](MULTIGAME_REGRESSION_PLAN.md), manteniendo Huntsville sin variaciones.

Referencias del formato PE: [Microsoft PE/COFF](https://learn.microsoft.com/en-us/windows/win32/debug/pe-format). BASS: [documentaci?n del proveedor](https://www.un4seen.com/doc/bass/bass.html). La documentaci?n actual BASS 2.4 no prueba sem?ntica/ABI de la DLL local 2.3: [notas de migraci?n](https://www.un4seen.com/doc/bass/upgrade.html). La atribuci?n SDA procede del binario local; no se atribuye a PopCap ni se deduce un SDK exacto de esas cadenas.
