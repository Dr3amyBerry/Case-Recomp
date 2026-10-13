# Mystery P.I.: The Vegas Heist — auditoría técnica inicial

> **Nota de vigencia (2026-10-13):** Esta auditoría estática inicial era correcta en su fecha, pero el diagnóstico «sin módulo Android/jugabilidad» fue superado por la implementación SDA posterior. Ver [Estado actual](STATUS_ACTUAL.md) e [Índice documental](INDICE.md). Este cambio no vuelve a ejecutar pruebas.

Fecha: 2026-10-08. Inspección local estática; no se ejecutaron ejecutables, instaladores, desinstaladores ni DLL originales. Estado de catálogo propuesto: **investigación, módulo no disponible, jugabilidad no verificada**.

## Identificación fundada en contenido

La copia local se encuentra en `private/mystery-pi-vegas/game/`. Se inspeccionaron cabeceras PE, imports, directorios de recursos, versiones fijas, firmas de medios y cadenas tecnológicas. Las huellas SHA-256 completas y los Índices quedan en `research/static-audit.json` bajo la carpeta privada del juego. El script reproducible `static_audit.py` usa solo Python stdlib y el inspector existente. Desde la raíz del repo:

```powershell
python -c "import runpy; runpy.run_path('private/mystery-pi-vegas/research/static_audit.py', run_name='__main__')"
```

El script lee bytes y no usa el loader de Windows.

| Archivo | Observación comprobada |
|---|---|
| `MysteryPIVegas.exe` | 1.499.136 bytes, PE32 x86 (`0x014c`), versión fija de archivo/producto 1.0.0.1. El lector existente encuentra cero películas Director reconocibles. |
| `Resources.dll` | 37.212.160 bytes, PE x86; sin imports en el directorio normal. 3.089 hojas de recursos: 3.088 de tipo `RAWDATA` y un manifest tipo 24. |
| `bass.dll` | 92.728 bytes, PE x86; versión fija de archivo 2.3.0.1 y producto 2.3.0.0. |
| Readme de la copia | Se presenta como The Vegas Heist, distribución Zylom y V1.0.0.3L, build 2008-04-24. Esto difiere de la versión fija del exe; registrar ambas, no inventar una equivalencia. |

El ejecutable contiene referencias a SDA Software Associates y una descripción del driver SDL para SDA Game Framework. Importa BASS para samples/streams/play/pause/volume/sync, usa APIs Win32 de ventanas, archivos, tiempo y registro, y contiene referencias de drivers SDL/DirectDraw. **Conclusión con evidencia fuerte: esta copia sigue una ruta nativa x86 de SDA Game Framework con SDL/BASS, distinta de Director/Lingo.** El SDK/versión exacta del framework, el grado de enlace estático de SDL y el modelo completo de ejecución todavía no se han reconstruido.

La ausencia de una película reconocida por nuestro lector es evidencia negativa acotada, no una demostración de que ningún fichero oculto o ninguna otra edición pueda usar Director. Se combina con las referencias positivas a SDA y el esquema de recursos; no se identifica el motor solo por `MysteryPIVegas.exe`.

Para contraste, la copia de Huntsville tiene un contenedor `XFIR/FGDM` con identificador Director `8.5.1#104` dentro de su PE. No hay base para aplicar su bytecode, casts o reglas de fuentes a esta copia de Mystery P.I.

## Recursos observados y límites del análisis

Firmas al comienzo de hojas PE de `Resources.dll`:

| Firma | Hojas |
|---|---:|
| PNG | 2.684 |
| JPEG | 293 |
| Ogg | 41 |
| XML con declaración sin BOM | 3 |
| Otras firmas, incluyendo texto con BOM/XUI/localización | 68 |

Son hojas, no un recuento de objetos Únicos del juego. La extensión del recurso no sustituyó el análisis de cabecera. De los recursos de texto se identificaron **43 documentos con raíz XUI**, contando BOM: 25 aceptados por un parser XML estándar y 18 rechazados por prefijos sin namespace enlazado. Se registraron tags por análisis léxico independiente. No se corrigieron los originales ni se asumió que el dialecto fuera XML estándar Íntegro.

Los tags observados incluyen texturas, imágenes, fuentes/kerning, labels, audio y construcciones `mpi:*` para escenas y componentes de juego. Hay atlas PNG de fuentes y definiciones de kerning. La existencia de XUI no demuestra que toda la lógica está en esos documentos: el PE contiene código nativo ejecutable. Un port basado solo en mover imágenes omitiría comportamiento y no es una solución de compatibilidad.

No se publican contenidos de escenas, coordenadas, listas de objetos, recursos originales, dumps de código ni hashes de saves de usuario. Los informes públicos contienen Únicamente resultados tecnológicos agregados; los Índices originales permanecen privados.

## Reutilización y diferencias

| Necesidad | Reutilizar | Trabajo pendiente y límite |
|---|---|---|
| Importación e integridad | streaming, SHA-256, límites, staging y almacenamiento privado existentes | Adaptador PE/resources y paquete propio; no pasar por `DirectorContent` ni usar `.director.zip` para SDA. |
| Presentación Android | viewport, letterboxing, superficie, reloj, lifecycle y controles de sesión | Exportar contratos sin tipos Director; preservar origen de coordenadas, alpha/recortes/blend del framework. |
| PNG/JPEG | decoder Android acotado y manejo de alpha | Verificar alfa, orientación y atlas con fixtures; no importar heurísticas Director de ink automáticamente. |
| Fuentes | infraestructura de imágenes y layout como base | Servicio de atlas/kerning; usar datos originales privados. No heredar Palatino/Tekton o margen centrado de Huntsville. |
| Audio | pausa, resume, recursos privados y backend de reproducción | Ogg, canales, looping, fading y callbacks BASS observados; verificar códec exacto de Ogg. No se ha ejecutado un decoder ni probado sincronización. |
| Entrada | captura Android touch/mouse/teclado | Adaptador SDL/SDA de eventos y coordenadas; no exigir mouseDown retrasado 100 ms. |
| Guardados | namespace, persistencia y archivos privados | Reconstruir formatos, ruta lógica y atomicidad nativas. Imports de registro solo prueban lecturas de esas APIs, no que los saves residan en registro. |
| Animación/UI | reloj, scheduling y servicio de presentación | Dialecto XUI, scene graph, componentes nativos, orden y callbacks. Flash no es una dependencia demostrada. |
| Diagnósticos | métricas, límites y harness sintético | Traza tecnológica SDA con eventos comprobados; no simular Éxito cuando falte un componente. |

Estas mejoras comunes también beneficiarían a Huntsville: presupuesto global, instalación por juego, namespaces estables y migración explícita, audio preparado durante pause, backend de efectos, identificación por fuente y diagnóstico de capacidades faltantes. No se cambia ahora su runtime para conseguirlas.

## Estrategia de módulo adicional

Propuesta: identificador de investigación `sda-native`, con parser offline PE/RAWDATA/XUI y módulo de ejecución separado. No afirmar que exista una versión universal de SDA soportada. Versionar cada contrato reconstruido con evidencia, no con un nombre comercial.

| Ruta | Evaluación | Dependencias y prueba de decisión |
|---|---|---|
| Runtime compatible SDA reconstruido | Dirección preferida si se logra definir el contrato compartido de recursos, componentes y eventos | Análisis estático de llamadas y native traces independientes. Demostrar cierre de dependencias de lógica de una escena antes de anunciar viabilidad; no copiar lógica en perfiles. |
| Traducción/recompilación de código nativo con capa ABI | Investigación alternativa si XUI depende extensamente de lógica compilada | Determinar ABI, relocations, APIs usadas y control de runtime; prototipo aislado con programa x86 sintético. Sin viabilidad demostrada en esta auditoría. |
| Emulación x86 y compatibilidad Windows/SDL | Alternativa evaluable, no recomendación de instalación ni backend presente | Presupuesto CPU/memoria, soporte Android y compatibilidad de API. No cargar DLL x86 en Android ARM directamente. |
| Parchear Director o reescribir cada escena MPI a mano | Descartada arquitectónicamente | Mezcla tecnologías o crea un port por título y pierde comportamiento original. |

El paso siguiente es elaborar un grafo de llamadas/recursos y un inventario de capacidades a partir del código nativo, sin alterar originales; después capturar comportamiento de referencia en un entorno controlado. No hay promesa de ejecución Android ni plazo estimable antes de cerrar esa incertidumbre.

## Próximos hitos de investigación

1. Determinar codificación/localización y dialecto XUI real, sintaxis de prefijos, fuentes/atlas y referencias entre recursos. Prueba: fixture sintético de cada forma, truncados y límites.
2. Delimitar qué resuelve el framework y qué reside en componentes nativos, incluidos los `mpi:*`. Prueba: escena de referencia y grafo de dependencias; sin sustituir su lógica mediante perfiles.
3. Capturar startup, menú, una escena, aciertos/fallos, animación, audio, guardado/reapertura y salida. Guardar evidencia privada por versión/huella y acciones reproducibles.
4. Seleccionar ruta de ejecución según resultados de 1–3. El catálogo sigue en investigación mientras falta un módulo o una capacidad obligatoria.
5. Integrar un slice detrás de las interfaces comunes y ejecutar los controles cruzados de [MULTIGAME_REGRESSION_PLAN.md](MULTIGAME_REGRESSION_PLAN.md), manteniendo Huntsville sin variaciones.

Referencias del formato PE: [Microsoft PE/COFF](https://learn.microsoft.com/en-us/windows/win32/debug/pe-format). BASS: [documentación del proveedor](https://www.un4seen.com/doc/bass/bass.html). La documentación actual BASS 2.4 no prueba semántica/ABI de la DLL local 2.3: [notas de migración](https://www.un4seen.com/doc/bass/upgrade.html). La atribución SDA procede del binario local; no se atribuye a PopCap ni se deduce un SDK exacto de esas cadenas.
