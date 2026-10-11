# CASE-RECOMP — GOAL DEFINITIVO V3

## RECONSTRUCCIÓN ACELERADA DE MYSTERY P.I.: THE VEGAS HEIST, APROVECHAMIENTO DEL PSEUDOCÓDIGO SDA Y VALIDACIÓN FINAL COMPLETA

**Documento consolidado:** método nuevo V3 + alcance técnico completo del Goal original. Ninguna obligación final de calidad, jugabilidad, compatibilidad, seguridad o cobertura ha sido eliminada; lo que cambia es **cómo y cuándo** se investiga y se comprueba.

**Instrucción para Codex:** aplica la Parte I como política de ejecución de máxima prioridad; aplica la Parte II como especificación completa de fidelidad y criterios técnicos. Si un procedimiento anterior obliga a compilar, capturar, comparar, documentar o crear un commit por microcambio, queda expresamente reemplazado por la cadencia agrupada V3. Los criterios **finales** de validación y honestidad de pruebas siguen siendo obligatorios. Las rutas y hashes citados son referencias históricas: verifica HEAD y el trabajo local en vez de asumir que no han avanzado.

---

# PARTE I — ESTRATEGIA Y EJECUCIÓN V3 (OBLIGATORIA Y PRIORITARIA)

## V3.0 — Mandato ejecutivo: más implementación, menos microauditorías

Actúa como desarrollador principal autónomo. **La velocidad útil es el criterio operativo principal:** reconstruir pantallas originales completas, resolver defectos de alto impacto, recuperar lógica nativa y ampliar la jugabilidad Android sin desperdiciar tiempo en decenas de verificaciones mínimas. La fidelidad y la seguridad no se reducen: se verifican **de manera agrupada, automatizada y al final de cada bloque importante**.

- Primero implementa componentes ausentes (fondos, capas, botones, menú, PDA, overlays, resultados) y corrige fallos funcionales reales; después pule pequeñas desviaciones de color o píxeles.
- Un bloque debería reparar **varias** partes relacionadas y producir una mejora visible en la APK. Evita el patrón «un caption → captura Windows → captura WSA → HTML → 12 páginas de reporte → un commit».
- No uses la cantidad de commits, pruebas aisladas o informes como medida de progreso. Usa pantallas completas, mecánicas operativas, defectos cerrados, cobertura real e incidencias críticas.
- No sacrifiques autenticidad, veracidad de las evidencias, rendimiento, compatibilidad o capacidad multijuego. La optimización de tokens **no autoriza** saltarse pruebas que protejan correcciones de riesgo, datos o controles.

**Objetivo inmediato:** que los cuatro minijuegos se dibujen con sus fondos, marcos, tableros, overlays y controles originales completos; completar después PDA, pausa, menús y pantallas faltantes; aprovechar el pseudocódigo para las mecánicas pendientes; terminar y validar el desenlace y los 25 niveles.

**Alcance inequívoco:** este Goal desarrolla **exclusivamente Mystery P.I.: The Vegas Heist**. La copia local de *Lost in Los Angeles* puede servir únicamente como referencia **estática, puntual y opcional** de ingeniería inversa del motor SDA. **PROHIBIDO** iniciar su juego/EXE, portarlo, implementarlo en Android, ampliar sus minijuegos, probarlo como segundo título o retrasar por él las mejoras de Vegas. La autorización de ejecución del juego original de la Parte II se refiere **únicamente a Vegas**.

## V3.1 — Estado previo y continuación segura

Antes de editar, consulta una sola vez el HEAD actual de `origin/intento-geminis`, el último commit local, los archivos modificados/no seguidos y el estado de las pruebas pertinentes. Parte del HEAD real; **no hagas reset, checkout destructivo, borrado, limpieza indiscriminada ni sobrescritura de cambios ajenos**.

Evidencia histórica conocida:

- `90bbfbd`: primer render de mapa/PDA/minijuegos originales.
- `69cfa3e`, `d93ff93`, `3ed88f5`, `fb1a3cf`: atlas originales, objetivos, WordSearch y fondo estable dorado.
- `1646d20`, `f20b7ba`, `b1fbe82`: pausa y estados nativos de botones, navegación MAPA por liberación.
- `8f6a707`: pruebas reportadas de importación real y restauración entre procesos.
- `aa51fcf`: portada privada de catálogo derivada del fondo real del menú.
- `98e22bb`: cabecera original `NIVEL: 1` en el PDA.

**Importante — trabajo más reciente posiblemente sin publicar:** el registro de la última sesión muestra que Codex estaba implementando la desaparición de los captions de objetivos durante la pausa. Modificó, según el registro, la interfaz `drawHud(..., paused)`, `SdaGameView`, `VegasVisualProfile` y la prueba instrumentada; había comprobado antes 1.790 píxeles indebidos y estaba ejecutando la prueba de reaparición al reanudar. **No asumas que ese trabajo está terminado ni que falta:** verifica `git status`, el diff, la compilación y la última prueba antes de repetir, revertir o reescribir. Si está correcto, intégralo y sigue con un bloque mayor; no dediques otro ciclo largo a la misma microcorrección.

Los conteos históricos de pruebas Kotlin/Python son orientativos, no sustituyen una ejecución actual ni certifican cobertura de toda la campaña. Nunca marques E2E total a partir de pruebas de motor o fixtures.

## V3.2 — Reglas de ahorro de tokens, contexto y tiempo

**Estrategia obligatoria de investigación y ejecución:**

1. **Buscar antes de leer:** `rg`, índices JSON/SQLite, mapas de dependencias, manifiestos XUI y búsquedas de símbolos en lugar de abrir cientos de archivos completos.
2. **Leer rangos:** muestra únicamente funciones, hunks y dependencias afectadas. Conserva resúmenes cortos con enlaces/rutas a evidencias locales; no copies logs enormes al contexto.
3. **No repetir:** reutiliza reportes Windows, capturas, pruebas, métricas, Ghidra y estado de implementación ya válidos; actualiza solo lo que cambió.
4. **Script antes de tarea repetitiva:** si necesitas repetir más de unas pocas veces una inspección, clasificación, extracción, comparación o ejecución ADB, crea o adapta una herramienta.
5. **Una hipótesis, una consulta dirigida:** evita horas de exploración teórica sin implementación resultante. Si un camino de ingeniería inversa no rinde, regístralo y cambia a otro bloqueo productivo.
6. **Agrupar cambios:** resuelve conjunto de componentes por subsistema; compila y prueba después de cambios coherentes, no tras cada línea.
7. **Salida compacta:** al usuario, explica en unas pocas líneas lo que está realmente terminado, lo que falló, las rutas de resultados y los commits. La evidencia detallada se guarda localmente, no se vuelca en la conversación.
8. **No sobreingeniería:** sin framework universal especulativo para juegos SDA aún no auditados; abstrae únicamente las funciones genuinamente compartidas y configura el resto por título.
9. **Prioridad por impacto:** elementos ausentes o que impiden jugar > fallos funcionales > capas/controles/animaciones principales > tipografía/escalado > diferencias mínimas de color o 1–2 px.
10. **Sin trampas de ahorro:** las pruebas fallidas y las incertidumbres no se silencian ni se eliminan de la matriz; una regresión de funcionalidad crítica se detiene y corrige.

El objetivo de tokens es reducir **trabajo repetitivo y lecturas innecesarias**, no imponer un límite artificial que provoque errores o pérdida de contexto esencial.

## V3.3 — Bloques sustanciales y política de commits

- Cada bloque debe incluir un conjunto significativo de cambios vinculados: normalmente una familia entera de pantallas/controles o un subsistema con varias funciones. Ajusta el tamaño por riesgo, no por un conteo arbitrario de líneas.
- Dentro del bloque usa checkpoints locales y pruebas focalizadas; no es obligatorio publicar un commit para cada caption, fondo o botón.
- Al cerrar un bloque: compilar, ejecutar pruebas relevantes, una tanda WSA cuando haya cambios de UI, revisar regresiones pertinentes, actualizar reportes **compactos** y publicar 1 o pocos commits cohesivos.
- Si una mejora es pequeña pero imprescindible para corregir una regresión que bloquea todo, permite un hotfix separado.
- No metas en un solo commit semanas de trabajo sin verificaciones intermedias. Divide cuando cambie el subsistema o el riesgo, no por tamaño cosmético.
- Proporciona `HEAD`, lista corta de mejoras visibles, test real ejecutado, defectos restantes y enlace/hash de commit. No recrees múltiples documentos prácticamente iguales.

## V3.4 — Ingeniería inversa eficiente: usar el código nativo ya recuperado

**Meta:** emular la ventaja de Huntsville (reutilización de lógica recuperada) tanto como permita la arquitectura real de Vegas, **sin fingir que C++ nativo es bytecode Lingo**.

Se dispone de una exportación histórica de **5.906 funciones reconocidas por Ghidra** de `MysteryPIVegas.exe`; no equivale a fuentes C++ ni a cobertura completa de funciones indirectas. La documentación ubica recursos `Resources.dll`/RAWDATA, XML/XUI, fábricas, callbacks, vtables y procedimientos nativos. El proyecto ya posee código Kotlin, scripts Python y exportaciones privadas. **No reexportes todo automáticamente, no leas 5.906 funciones manualmente y no transpiles a ciegas.**

### A. Mejorar la recuperación original, pero con retorno inmediato

Evalúa de forma acotada, basada en evidencia:

- Si hay símbolos reales disponibles en la copia legítima (PDB, mapas, RTTI MSVC, cadenas y metadatos de clases); no presupongas que existen ni los inventes.
- Renombrado masivo seguro de símbolos identificados, anotaciones de estructuras, parámetros, retorno, calling convention, clases y miembros; propagación de tipos en Ghidra.
- Reconstrucción de **vtables, llamadas indirectas y fábricas**, entradas de callback, estado de nodos, jerarquía de componentes y relaciones XUI ↔ constructor ↔ evento ↔ renderer.
- Uso de Ghidra P-code, decompilación localizada, XREF, call graph y exportación estructurada para pruebas diferenciales, no para crear miles de archivos Kotlin compilables por generación ciega.
- Comparación con otros binarios legítimos de la misma edición **solo si están ya disponibles**; no descargar código comercial ni hacer búsquedas interminables para encontrar fuentes inéditas.
- Si una herramienta alternativa de decompilación o análisis de binarios mejora un bloqueo demostrable, valora una prueba limitada, con procedencia segura y sin instalaciones masivas.

**Decisión obligatoria en tiempo acotado:** clasifica `traducción semiautomática útil`, `reconstrucción mediante datos XUI`, `emulación/reimplementación genérica`, `requiere contraste de ejecución`, o `no rentable por ahora`; usa de inmediato el método ganador.

### A.1. Prueba opcional y acotada: RTTI de Los Angeles como mapa estático para funciones de Vegas

**Único propósito autorizado: acelerar el desarrollo de Vegas.** Ya existe `docs/SDA_ACCELERATION_ANALYSIS.md` con un análisis preliminar: una copia posterior de SDA atribuida a *Lost in Los Angeles* exhibe 134 nombres de clases del namespace `sda`, 61 etiquetas XUI compartidas y 405 pares etiqueta-atributo comunes con Vegas. **Son candidatos de correlación, no equivalencia funcional ni transferencia automática demostrada.** La copia procede de un portable de terceros y puede estar modificada.

**Ubicación local mostrada por el usuario:** `C:\Users\Admin\Desktop\new_togit\case-recomp\private\mystery-pi-angeles\` (carpetas `game/`, `extraccion/` y `extract_z.py`). Comprueba su existencia y contenido real mediante lectura, sin asumir que todos los ficheros estén íntegros. **No mover, borrar, reextraer ni modificar esos originales y derivados**; no es necesario iniciar ningún ejecutable. Mantener la ruta y sus datos fuera de Git y de la APK.

**Evaluación rápida, sin port de Los Angeles:**

1. Reutiliza `docs/SDA_ACCELERATION_ANALYSIS.md`, el proyecto Ghidra de Vegas y la evidencia estática existente; no repitas la extracción de 3.046 recursos ni el análisis completo de 5.906 funciones.
2. Si la infraestructura está disponible, prueba una correlación **estática** entre los binarios mediante Ghidra Version Tracking (o herramienta equivalente ya disponible), comenzando por unas pocas clases de alto impacto compartidas: `mpiPda`, `mpiHint`, `mpiMap`, `mpiTileRotGame`, `mpiJigSawGame`, `xuiButton` y `xuiFont`.
3. Contrasta candidatos con estructura de vtables, XREF, secuencias de código y llamadas, cadenas, firmas y diferencias de versión. Distingue coincidencias de alta confianza, ambiguas y rechazadas. Los nombres transferidos son **inferidos para Vegas**, no sus símbolos originales confirmados. Evita renombrados masivos o transferencia de tipos automática sin revisión; preserva el proyecto Ghidra original mediante copia o mecanismo no destructivo.
4. Registra un resultado **compacto y reproducible** bajo `local-output/` (sin pseudocódigo comercial publicado): número de coincidencias verificadas, funciones de Vegas identificadas, contratos Kotlin o renderizadores desbloqueados y casos no confiables.
5. **Límite de esfuerzo:** un piloto breve (orientativamente 60–90 minutos de trabajo efectivo, sin convertirlo en un bloqueo). Si no produce beneficios verificables para PDA, menús, fondos, composición de bonus o eventos **de Vegas**, ciérralo como no rentable por ahora y vuelve inmediatamente al Bloque 1 de Vegas. Si funciona, automatiza únicamente el subconjunto de transferencias confiables que facilite código útil inmediato.

**Instrumentación dinámica opcional con Frida — EXCLUSIVAMENTE Vegas (dentro del mismo piloto, NO una fase adicional):**

- Comprueba primero si Frida ya funciona en `private/tools/frida` o en el entorno Windows; no reinstales ni descargues herramientas innecesariamente. Usa **únicamente el ejecutable original de Vegas** en una sesión de análisis controlada, conforme a las autorizaciones de la Parte II. **Nunca ejecutes ni instrumentes Los Angeles.**
- Si el emparejamiento estático es ambiguo o poco rentable, prueba hooks puntuales sobre el parser/fábrica XUI y la creación de objetos de **Vegas**. Intenta registrar `etiqueta XUI → contexto de creación → constructor/objeto → vtable → slots de métodos`, con direcciones normalizadas, referencias y trazas mínimas. **No des por hecho** que esa correspondencia es directa, que todas las clases se instancian o que el índice de un método virtual significa lo mismo entre versiones.
- Corrobora cada relación con Ghidra (XREF, escritura de vptr en constructores, flujo de llamadas, firmas y evidencia de uso real). Distingue datos observados, hipótesis y coincidencias descartadas; no renombres masivamente métodos o clases no demostrados.
- Mantén hooks **temporales y reversibles** solo en el proceso de prueba de Vegas; no parchees ni redistribuyas EXE/DLL, no instrumentes otros procesos y no alteres partidas originales. Guarda scripts y reportes breves sin contenido comercial en ubicaciones permitidas; trazas, direcciones y datos sensibles del juego quedan en `local-output/`/carpetas privadas fuera de Git.
- **Frida y Version Tracking comparten el mismo límite de tiempo del punto 5** (no suman 60–90 minutos cada uno). Si no desbloquean trabajo concreto del motor, escenas, fondos, Jigsaw o eventos **de Vegas**, detén la investigación y continúa de inmediato el Bloque 1. **Prohibido iniciar desarrollo de Los Angeles o sustituir el motor Android nativo por emulación.**

**Prohibiciones específicas:** no ejecutar `JUGAR.exe` ni otro EXE de Los Angeles; no abrir su interfaz, jugar, importar su `.z` en Case-Recomp, instalar una versión Android, crear perfil/adaptador/launcher de Los Angeles, desarrollar Match/Solitario/PlaceGame ni iniciar pruebas visuales o E2E de ese título. **Cero desarrollo funcional de Los Angeles.** Únicamente se permite lectura/análisis estático como referencia cruzada para Vegas. Tampoco ejecutar Windows/WSA de Los Angeles por conveniencia ni usar sus imágenes para sustituir recursos de Vegas.

El resultado de esta evaluación **no es un nuevo hito independiente**: debe aprovecharse dentro de V3.4 y desembocar de inmediato en implementación de Vegas. Todo el resto de requisitos V3 conserva su prioridad y alcance.

### B. Scripts reutilizables, no pseudocódigo en el chat

Crea/adapta scripts bajo `tools/` y usa archivos privados ya presentes. Preferiblemente una CLI con subcomandos o varios scripts simples y composables, no una plataforma nueva compleja.

Deben poder:

1. Indexar funciones por dirección, nombre inferido, strings y XREF, con almacenamiento consultable (JSONL o SQLite).
2. Buscar dependencias directas, indirectas recuperadas, vtables, fábricas y callbacks asociados.
3. Agrupar funciones por familias: recursos, XUI, render, fuentes, botones, eventos, escena, hit-testing, puntuación, hints, minijuegos, guardado, menús, finale y audio.
4. Cruzar clases/componentes XUI y atributos con rutas de código nativo y su equivalente Kotlin, identificando faltantes.
5. Extraer **solo slices relevantes** de pseudocódigo con tamaño acotado y links internos, no miles de funciones al contexto.
6. Detectar duplicación de algoritmos ya implementados; proponer reutilización, no reescritura.
7. Producir un mapa `función/contrato → Kotlin/estado → evidencia → prueba → prioridad`.
8. Exportar pequeñas tablas de constantes/formatos/algoritmos verificables y generar fixtures para pruebas diferenciales.
9. Reportar advertencias de tipos, variables no inicializadas, punteros y rutas indirectas no resueltas; no presentar el pseudocódigo como ABI demostrado.
10. Servir a cualquier futuro título SDA sin incrustar nombres o IDs exclusivos de Vegas en la herramienta base.

### C. Traducción híbrida a Kotlin

- Para funciones puras y algoritmos determinados: puede generarse un **borrador** Kotlin, seguido de revisión de tipos, enteros con signo, overflow, x87/float, RNG, alineación, alfa, hit-testing, orden de eventos y pruebas equivalentes.
- Para subsistemas basados en datos: interpreta recursos y XUI mediante funciones genéricas SDA, evitando traducir decenas de procedimientos que los recursos ya describen.
- Para comportamiento nativo no descrito: recupera algoritmos concretos y traduce selectivamente su semántica.
- Compara resultados con el EXE original **cuando desbloquee decisiones**; utiliza fixtures originales y pruebas Kotlin deterministas para ciclos rápidos.
- No intentes emular todo x86, compilar el binario Windows en Android, ni portar automáticamente todas las 5.906 funciones sin una factibilidad demostrada.

**Prioridad de análisis de código:** funciones que permitan agregar varios controles o fondos de golpe (jerarquía de escenas, construcción de menús, capas, callbacks de pistas, composición de bonus, resultados y finales) antes que microajustes individuales.

## V3.5 — Recuperación masiva de imágenes, fondos y capas faltantes

**Ésta es la prioridad visual inmediata.** Los minijuegos muestran piezas o letras, pero algunas pantallas no recuperan el **fondo completo, marcos, elementos de decoración, bandejas, overlays o controles originales**. No pulas primero la letra de un botón cuando la pantalla carece del tablero o fondo original.

### Inventario automatizado de recursos y draw calls

Reutiliza y amplía el análisis de `ENVS.MSE`, `LEVELS_1.XUI`, `LEVELS_2.XUI`, documentos `.TRG`, `.TGL`, `.WSG`, `.JSW`, escenas XUI, STRINGS y RAWDATA del paquete privado.

- Identifica nodos de background/start/end, imágenes, sprites, capas/orden z, marcos, máscaras alfa, tableros, bandejas, texturas de piezas, estados de control, etiquetas, fuentes, sonidos, acciones, overlays y secuencias animadas.
- Extrae dimensiones, posiciones, anchors, transformaciones, clipping, alpha, binding, visibilidad, contexto y dependencias.
- Construye un manifiesto local **pantalla/variante → nodos/recurso → dónde debe dibujarse → si Android lo dibuja realmente → implementación faltante**. La presencia en ZIP no implica que esté renderizado.
- Señala imágenes incorrectamente sustituidas, recursos de ejemplo/debug, fondos ausentes, recortes indebidos, orden de capas errado, elementos dibujados durante fase equivocada o botones visibles sin interacción.
- Genera un diff compacto de recursos no utilizados o mal vinculados con clasificación por valor visual: fondos grandes primero, después composición de paneles/controles, luego detalles.
- Usa primero geometría XUI y comportamiento nativo; solo crea calibración específica dentro de Vegas cuando esté corroborada.

### Implementación por familias

**TileRot:** imagen/fondo original, cuadrícula, distribución, piezas, rotación 90°, máscara, marcos, sombreado, retirement, animación y pantalla de finalización.

**TileSwap:** fondos/tablero, fichas, posiciones, selección, intercambios, sombras, retirement, efectos, resultado y puntuación.

**WordSearch:** fondo, panel, celdas, atlas original, lista, wslabel, selección normal/selected/locked, líneas/trazos animados, marca de palabras completadas, overlays, HUD, puntuación y finalización.

**Jigsaw:** fondo real, bandeja, tablero, piezas con máscaras, relieve/sombras, posición/rotación, flechas, paginación, imagen de referencia, controles de giro originales/adaptación táctil separada, efectos y finalización; eliminar `GIRAR PIEZA` y demás controles temporales superpuestos solo cuando exista una interacción equivalente comprobada.

**Desenlace:** inventariar fondos, objetos, bandejas, rótulos, diálogos, overlay, efectos y transiciones de sus tres fases. La integración funcional auténtica puede cerrarse después de las principales pantallas, pero no reemplazar con gráficos ficticios.

**Regla:** dibuja las capas en su orden correcto con los recursos originales, no reproduzcas solo la textura interactiva principal. Trabaja las cuatro familias en una campaña de implementación y test suficientemente grande antes de pulir detalles menores.

## V3.6 — PDA completo y defectos concretos conocidos

No más mejoras de una etiqueta por ciclo. Reconstruye **todos** los controles y elementos disponibles del PDA mediante sus componentes XUI y las funciones nativas relevantes:

- Base/fondo/texture lateral/bordes/marcos/separadores; reloj, puntuación, nivel, total de objetos, jugador, rango, llaves/fichas y coleccionables; indicadores y estados; listas de objetivos.
- Pistas (incluyendo recarga), menú, mapa, pausa, resolver bonus cuando corresponda, textos/tooltip/diálogo y callbacks; ningún botón visible aparentemente activo sin interacción real y verificada.
- Normal/hover/pushed/selected/disabled, presión/cancelación/UP dentro, alpha, orden de capas, clipping, animation, cambios entre mapa/escena/bonus/resultados.
- Tipografías atlas, metrics/kerning, texto largo y acentos, saltos de línea, alineación y límites; evitar solapamientos en 4:3/16:9.

**Revisiones obligatorias:**

1. `VegasVisualProfile.base()` dibuja PAUSA en contextos donde `pauseRect()` quizá no habilita interacción; comprueba **qué hace el Windows original por contexto** y corrige simultáneamente visibilidad, enabled y hit-test. No muestres un botón aparentemente utilizable si no lo es.
2. En pausa, los captions de objetivos desaparecen en Windows; la última sesión intentó pasar `paused` al renderer de escena. **Verifica si el cambio pendiente ya corrige ocultar y restaurar sin alterar objetivos, puntuación ni reloj; no lo rehagas si ya está bien.**
3. Mantén el comportamiento de pausa de consumir completamente el gesto de reanudación para evitar click-through; verifica otros contextos si el original permite pausa en bonus/mapa.
4. Pistas/menú/indicadores/collectibles faltantes: busca bindings originales y handlers nativos, implementa comportamiento real antes de declarar «completo».
5. Si algunos elementos solo aparecen tras un evento/nivel, no fuerces su presencia permanente: preserva visibilidad condicional del original.

**Prioriza completar en un solo bloque sustancial** en lugar de abrir una auditoría Windows+Android para cada caption. Prueba una campaña de estados del PDA por bloque.

## V3.7 — Menú original, mapas, escenas, diálogos y pantallas de resultados

- Portada de catálogo `cover.png` y **menú principal interno de Vegas son componentes distintos**. La miniatura basada en `mm_background.jpg` es correcta para la tarjeta, pero no implementa el menú nativo.
- Reconstruye la pantalla inicial/logos, `mainmenu`, nueva partida, continuar, perfil/jugador, opciones, ayuda/instrucciones, confirmaciones, diálogos, dificultad solo si existe en el original y salida/regreso.
- Usa los fondos, controles, estados y transiciones originales; integra navegación, selección, guardado y retorno a la APK multijuego.
- Mapa: fondo dorado estable, transición azul→dorado pendiente, títulos/nivel/contadores, tarjetas, marcos, miniaturas, escenas bloqueadas/activas/completadas, layouts 1–9, hover y pressed, selección, transición, estado del PDA.
- Escenas: fondo fotográfico, sprites alfa, objetos encontrados, jerarquía de capas, objetivos/rows, reloj, pista, aciertos/fallos, overlays, efectos y visibilidad, proporciones correctas.
- Resultados: bonus resuelto naturalmente vs botón `Resolver puzle`, puntos/penalizaciones, bonificación por tiempo, rango, transición de nivel; no reemplazar por una tarjeta Android de diagnóstico.
- Mantén la importación privada funcional, la selección de juego correcta y el estado de perfil/campaña separadamente por juego/edición.

## V3.8 — Política visual: cobertura total AL FINAL, muestreo inteligente DURANTE

**No elimines las comparaciones Windows↔Android ni la matriz final de 25 niveles. Cambia la frecuencia y el coste de producirlas.**

- Primero aprovecha las capturas válidas **ya existentes** del ejecutable y de WSA. Captura nuevas referencias Windows en **sesiones por familia** (menús+perfiles, mapa+PDA, cuatro bonus, resultados+final), no tras cada commit.
- Si un cambio solo corrige un offset de un texto y ya existe un fixture válido, verifica con prueba acotada y agrúpalo para la siguiente tanda de WSA.
- Cada bloque visual sustancial requiere una comprobación representativa de las pantallas cambiadas en Android real; no un informe multipágina para cada control.
- Reserva HTML con overlays, diferencias y métricas MAE/PSNR/SSIM para **hitos** (familia completa, PDA completo, menú, campaña y cierre). La comparación automática puede ejecutarse en lote y guardarse en `local-output` sin verter el resultado al contexto.
- Al validar diferencias numéricas, alinea viewport, aspecto, escala, letterboxing, desplazamiento, fotograma y estado; no compares clocks/objetos aleatorios distintos como si fueran idénticos. Identifica recorte, región y estado equivalente.
- Distingue: `Windows original ejecutándose`, `recurso extraído`, `Android/WSA ejecutándose`, `prueba sintética`, `prueba de componente`, `E2E`. Nunca sustituyas una procedencia por otra.
- Para una pantalla compleja cuya fidelidad no se ha comprobado, deja `PARCIAL`/`NO VERIFICADO`; no bloquees el desarrollo de otras partes por no calcular una métrica marginal.
- Al cierre, sí exige **cobertura exhaustiva** de menús/estados/25 niveles/variantes, con evidencia propia o declaración explícita de bloqueo/no verificación. Ningún nivel se aprueba implícitamente por usar el mismo renderer.

**Optimización específica:** herramienta de capturas por lotes Windows y Android, normalización y HTML/JSON a partir de manifiestos. Tras construirla, reutilízala para todos los bloques.

## V3.9 — Plan de pruebas por capas y cadencia de compilación

### Iteración local rápida

- `rg`/análisis estático y pruebas Python/Kotlin **focalizadas** del código tocado.
- Evita reinstalar/reabrir WSA después de cada modificación que no afecte directamente a interacción/pantalla crítica.
- Ejecuta tests de UI inmediatamente para cambios de hit-testing, gestos, persistencia, render de pantalla crítica o rutas que puedan bloquear avance; no los pospongas ciegamente.
- Si falla una prueba, corrige la causa; no la desactives ni reduzcas su aserción para simular avance.

### Cierre de bloque sustancial

- Compila la APK; ejecuta suites Kotlin del subsistema y regresiones relacionadas.
- Instala la APK experimental en WSA/API33 y ejecuta batería de pantallas/estados modificados **agrupada**, con MotionEvents/ADB/UI reales cuando corresponda.
- Captura solo evidencia representativa nueva, reusando baseline. Comprueba logs, crashes, ANR, rutas de carga y memoria de recursos.
- Para modificaciones profundas de núcleo o persistencia, añade pruebas relevantes extra y verifica que Director/Huntsville no sufrió cambios ni regresiones.

### Hitos y cierre final

- Prueba compilaciones y capacidades API 26/33/36 disponibles; 800×600, 4:3, 16:9, distintas densidades/orientaciones/tamaños con entradas y letterboxing.
- Compara Windows/Android de familias representativas; audita 25 niveles y variantes; valida guardados y cierre/reapertura de proceso; rendimiento prolongado y regresiones de Huntsville.
- No uses resultados de test Kotlin, una imagen cargada, un `adb install` o un screenshot como prueba de campaña completa.

**Pruebas E2E separadas de fixtures:** automatiza catálogo → selección/importación ZIP privado → menú interno/perfil → mapa → escenas → objetos mediante clicks alfa legítimos → minijuego con gestos válidos → puntuación/resultados → siguiente nivel → nivel 25 → fases del desenlace → final. Nada de asignar directamente fases, objetos encontrados o índices, ni de acreditar `solveBonus()` como jugabilidad. Tests de carga técnica de estados pueden preparar un nivel, pero deben quedar rotulados como técnicos, no E2E.

## V3.10 — Priorización y fases grandes, sin perder el alcance original

**Fase 0. Reconocimiento corto, preservar WIP y preparar herramientas.** Confirma HEAD/diff; comprueba si la corrección de objetivos ocultos en pausa se completó. Recicla scripts y crea **solo** los índices/batch scripts que tengan uso inmediato. Termina esta fase con capacidad efectiva de auditar y renderizar recursos faltantes; no crees meses de infraestructura.

**Bloque 1. CUATRO MINIJUEGOS COMPLETOS.** Recupera en conjunto los fondos, marcos, bandejas, tableros, overlays, controles y capas para TileRot, TileSwap, WordSearch y Jigsaw. Corrige sus estados visuales principales y defectos de navegación evidentes. Primera gran prueba agrupada WSA, un conjunto de commits coherentes.

**Bloque 2. PDA COMPLETO + PAUSA + PISTAS + CONTROLES.** Finaliza todas las etiquetas y contadores, pista/recarga, menú, coleccionables, nivel/rango/jugador, normal/hover/pushed/disabled, contexto, visibilidad y entrada. Verifica la corrección de ocultar objetivos al pausar y el botón PAUSA fuera de escena. Prueba batería de estados en Windows y Android una sola vez por hito.

**Bloque 3. MENÚ ORIGINAL + MAPA + ESCENAS + RESULTADOS.** Integra menú interno y perfiles/opciones/ayuda; fondo/transición del mapa, tarjetas 1–9 y HUD; fondos/capas de escenas; overlays, resultados de bonus y nivel; reemplaza UI de diagnóstico.

**Bloque 4. FUENTES, NITIDEZ, ANIMACIÓN, AUDIO Y RENDIMIENTO.** Reutiliza atlas originales, verifica alpha/clipping/kerning/line breaks, filtros fotográficos vs UI, animaciones nativas medibles, sonidos de interacciones cuando sean recursos existentes, control de memoria/cachés y adaptaciones táctiles. Prioriza problemas visibles o que bloquean interacción, no variaciones microscópicas.

**Bloque 5. DESENLACE AUTÉNTICO.** Reconstruye recursos y lógica real de fases primera, segunda y tercera; riddle con 8 objetivos/decoys donde corresponda, colocación en pared/báscula y secuencia de bóveda, transiciones, recompensas, sala del dinero, pantalla final. Usa nativo y XUI; no declares completado hasta interaccionar sin forzar estado.

**Bloque 6. E2E 25 NIVELES + PERSISTENCIA.** Automatiza el recorrido real de interfaz de todos los niveles y las variantes de bonus; checkpoints legítimos, timeouts, entradas, resultados, guardado/restauración de mapa, escena, pieza/selección parcial, bonus, resultado y desenlace; proceso Android cerrado/reabierto. Registra bloqueos honestamente y repara regresiones.

**Bloque 7. CIERRE GENERAL.** Cobertura completa de menús, gráficos, controles, estados y 25 niveles; campaña Windows/Android de capturas representativas y matriz granular; 800×600, 4:3, 16:9, densidades, rendimiento, RAM/FPS/GC/audio/bitmaps; regresión Director; APK experimental, paquete privado correcto, HTML, reportes compactos y Git.

**No te quedes atascado en la Fase 0.** Empieza a integrar recursos y fondos inmediatamente después del inventario mínimo. Si una fase está bloqueada por un problema puntual que no compromete otras funciones, deja un issue reproducible y continúa con elementos independientes sin falsear estado de validación.

## V3.11 — Conservación íntegra del contrato de fidelidad y aceptación

No se elimina ningún requisito del original: siguen siendo objetivos finales obligatorios **fuentes, métricas, colores, transparencias, clipping, estilos, escena completa, efectos, animaciones, controles, minijuegos, desenlace, 25 niveles, opciones, perfiles, guardado, rendimiento, seguridad, privacidad y APK multijuego**.

Clasifica cada pantalla/estado como `APROBADO`, `PARCIAL`, `DEFECTUOSO`, `NO IMPLEMENTADO`, `NO VERIFICADO` o `BLOQUEADO` según evidencias reales. La matriz final debe distinguir fixture/JVM, componente Android, captura WSA, Windows original y E2E real. No eleves el estado por conteo de tests o recursos enumerados.

Conserva exactitud de originales: no inventar fuentes, sprites, botones, audio, escenas ni reglas; no blanquear errores; no poner fondos genéricos donde hay fondos originales; no distribuir recursos del juego. Las pequeñas diferencias aceptables a mitad de trabajo son **pendientes explícitos**, no fidelidad aprobada.

## V3.12 — Entregables y ahorro de documentación

**Durante cada bloque:** únicamente código, pruebas pertinentes, resumen corto de progreso, issues restantes, commit/push en `origin/intento-geminis`, y una APK/capturas si el bloque cambió sustancialmente UI. Guardar logs y recursos privados en `local-output`, sin volcarlos al chat ni a Git.

**Al cerrar cada hito principal:** regenerar con scripts un HTML navegable con comparación Windows/Android válida, diffs y métricas cuando aporten valor; actualizar cobertura JSON/CSV; actualizar los documentos maestros con un resumen incremental y no duplicar pasajes enteros.

**Al cerrar el Goal:** nueva APK experimental, capturas originales/Android etiquetadas, HTML comparativo, matriz completa de pantallas/estados/25 niveles, informes breves de resolución, tipografía, animaciones, rendimiento (PSS/JVM/native/bitmap/GC/FPS), arquitectura SDA/perfil, pruebas JVM/instrumentadas/E2E y commits. Reutilizar `docs/MYSTERY_PI_ANDROID_VISUAL_COMPARISON.md`, `docs/MYSTERY_PI_ANDROID_AUDIT.md` y solo actualizar `docs/MULTIGAME_ARCHITECTURE.md` cuando haya cambio estructural real.

**No hagas once informes distintos por cada microcorrección**; el contrato de entregables del Goal original se exige para **el conjunto consolidado**, no para cada pequeño parche.

## V3.13 — Seguridad, límites y arquitectura innegociables

- Trabajar exclusivamente en la rama `intento-geminis`, desde HEAD actual; no tocar `main`, no crear ramas extra, merges, force-push, releases ni APK aprobadas. Respeta estado local y colaboradores.
- Huntsville/Director/Lingo intactos. No copies calibraciones por título al núcleo genérico; la única APK integra ambos motores mediante perfiles.
- SDA genérico: texturas, XUI, fuentes, input, composición, estados, animación, audio/guardar con contratos reutilizables. Vegas: fuentes/recursos por edición, coordenadas, nodos, layouts, reglas confirmadas y ajustes particulares. **Nada de `if(gameId == "vegas_heist")` dentro del motor SDA genérico.**
- Ejecutar EXE original, controlar mouse/teclado y WSA/ADB está autorizado para la investigación del usuario, sin pedir permiso por cada clic; no toques otras aplicaciones, el SO, cuentas, partidas ajenas o recursos originales. No instalar herramientas superfluas ni usar admin si no hace falta; aislar binarios antiguos si existe riesgo.
- No publicar binarios comerciales, DLL/EXE, pseudocódigo de terceros, assets, capturas privadas, secretos, perfiles ni partidas. Scripts e índices sin contenido propietario incrustado pueden versionarse cuando corresponda.
- Mantener integridad de ZIP importados, SHA/hash y perfiles; no modificar saves existentes sin respaldo y restauración. Evita cambios destructivos o indiscriminados en el PC.
- La fidelidad final se comprueba con el juego real y Android real, pero la **cadencia de comparación es inteligente y agrupada**. No ocultar limitaciones, no confundir original vs extraído, no saltar gameplay real mediante APIs para afirmar E2E.

## V3.14 — Orden de inicio inmediato para Codex

1. Verifica HEAD y **WIP local**; termina la regresión pendiente de objetivos ocultos en pausa solo si de verdad sigue incompleta, sin generar un informe gigante por ello.
2. Construye un **índice mínimo de recursos/XUI y funciones Ghidra** usando scripts existentes, que informe fondos/capas/controles faltantes en las cuatro familias de bonus. **Solo si desbloquea trabajo inmediato de Vegas**, realiza el piloto estático V3.4.A.1 con las referencias locales de Los Angeles; nunca ejecutes ni portes ese juego y abandona la correlación si no ofrece resultados confiables rápidamente.
3. Implementa en un **bloque sustancial** fondos, tableros, marcos, overlays y elementos faltantes de TileRot/TileSwap/WordSearch/Jigsaw, reutilizando su lógica ya implementada y los bindings originales.
4. Cierra ese bloque con pruebas Kotlin focalizadas, WSA agrupado, captura de las **cuatro** familias, comparación rápida con referencias Windows existentes y un commit/push coherente.
5. Prosigue al bloque sustancial de PDA + pausa + menú/pistas, con la misma política de eficiencia.
6. Después completa menús, escenas, mapas, resultados, animaciones y fases del desenlace; al final, auditoría íntegra de 25 niveles, persistencia y regresión multijuego.

**MANDATO FINAL V3:** recupera más código útil, utiliza scripts, termina más pantallas por bloque, produce menos texto y capturas redundantes, preserva la arquitectura y verifica honestamente la calidad final. No conviertas este Goal en otra sucesión de microauditorías.

---

# PARTE II — ESPECIFICACIÓN TÉCNICA ÍNTEGRA DEL GOAL ORIGINAL (ACTUALIZADA SOLO EN LA CADENCIA DE TRABAJO)

**Esta parte conserva todos los requisitos de alcance del Goal original**: 20 secciones de renderizado, fuentes, auditoría de menús/mapas/25 niveles, cuatro minijuegos, desenlace, comparación Windows, WSA, cobertura, entrada, animaciones, rendimiento, aislamiento Director, entregables, aceptación y seguridad. Sus indicaciones antiguas de **orden, número de capturas/commits y frecuencia de pruebas** han sido reemplazadas expresamente por la Parte I.

---

## CONTRATO ORIGINAL COMPLETO — RECONSTRUCCIÓN VISUAL FIEL Y MOTOR SDA MULTIJUEGO

## MISIÓN PRINCIPAL

Actúa como arquitecto senior de software, especialista en Kotlin/Android, ingeniería inversa, motores gráficos 2D, renderizado, tratamiento de imágenes, interfaces de videojuegos, automatización de pruebas y análisis comparativo visual.

Tu misión es **reconstruir, corregir, optimizar y validar exhaustivamente toda la presentación visual de Mystery P.I.: The Vegas Heist en Android**, manteniendo la arquitectura multijuego de Case-Recomp.

**LA META FINAL ES LA FIDELIDAD VISUAL; LA PRIORIDAD OPERATIVA V3 ES RECUPERAR PANTALLAS Y FUNCIONES COMPLETAS CON RAPIDEZ, REUTILIZANDO LOS RECURSOS Y LA INGENIERÍA INVERSA EXISTENTES.**

Hemos avanzado considerablemente en la lógica y los minijuegos, pero todavía existen diferencias visibles respecto al juego original: baja nitidez en el PDA, fuentes inexactas, solapamientos, controles provisionales, animaciones faltantes y pantallas que requieren mayor fidelidad.

No quiero continuar acumulando funcionalidades sin comprobar cómo se ven y cómo funcionan realmente en Android.

Quiero transformar la APK experimental actual en una experiencia visual fiel, limpia, nítida, estable y completamente comprobada.

No es un rediseño moderno ni una reinterpretación artística de Vegas. **El objetivo es reproducir su interfaz, recursos, distribución, efectos, comportamiento y estética originales con la mayor precisión técnicamente posible.**

---

# 1. REPOSITORIO Y REGLAS INNEGOCIABLES

Repositorio:

https://github.com/Dr3amyBerry/Case-Recomp

Rama autorizada:

`intento-geminis`

Referencia inicial conocida:

`90bbfbd`

Antes de trabajar, comprueba el HEAD real de `origin/intento-geminis` y utiliza el estado más reciente. No retrocedas ni sobrescribas trabajo posterior.

### Reglas Git

- Trabaja exclusivamente en `intento-geminis`.

- No modifiques `main`.

- No crees ramas adicionales.

- No realices merges.

- No utilices force-push.

- Respeta cambios existentes y sesiones concurrentes.

- Crea commits coherentes y comprobables por bloques sustanciales de cambios relacionados; evita microcommits, pero separa correcciones de riesgo o bugs independientes.

- Publica únicamente en `origin/intento-geminis`.

- No alteres releases ni APK aprobadas.

- Mantén los recursos comerciales originales, capturas privadas, ejecutables y partidas fuera del repositorio.

- No subas credenciales, datos personales ni archivos privados de investigación.

**Huntsville y el motor Director/Lingo deben permanecer intactos.**

No alteres su código para implementar mejoras específicas de Vegas.

Ejecuta pruebas de regresión para garantizar que la APK multijuego continúa funcionando.

---

# 2. NUEVA AUTORIZACIÓN EXPRESA — USO DEL PC Y DEL JUEGO ORIGINAL

## ESTA AUTORIZACIÓN SUSTITUYE LAS RESTRICCIONES ANTERIORES SOBRE EJECUTAR EL EXE Y UTILIZAR MOUSE/TECLADO

Anteriormente estaba prohibido abrir el ejecutable original de Vegas y controlar el escritorio.

**ESA RESTRICCIÓN QUEDA REVOCADA PARA ESTE GOAL.**

Ahora tienes mi autorización expresa para:

1. Abrir y ejecutar el `.exe` original de Mystery P.I.: The Vegas Heist instalado o disponible en mi PC.

2. Utilizar directamente el mouse y el teclado.

3. Navegar por todos los menús del juego original.

4. Entrar en partidas, mapas, escenas y minijuegos.

5. Probar sus botones, animaciones, interacciones y efectos.

6. Tomar capturas y registrar vídeos de las pantallas originales.

7. Comprobar resoluciones, dimensiones, tipografías, colores y distribución.

8. Comparar la ejecución original con nuestra versión Android.

9. Utilizar directamente WSA mediante ADB, instrumentación Android o herramientas gráficas disponibles.

10. Instalar, abrir, probar y capturar la APK experimental de Case-Recomp.

**No estaré utilizando el PC mientras trabajas. Puedes utilizar mouse y teclado autónomamente para realizar estas pruebas.**

No necesitas detenerte para pedir autorización adicional por cada clic, apertura del juego o captura.

### Condiciones de seguridad

- Trabaja sobre los archivos y aplicaciones necesarios para esta investigación.

- No modifiques el ejecutable comercial ni sus archivos originales.

- No elimines partidas o configuraciones existentes; realiza copias si necesitas modificar estados para probar.

- No accedas a aplicaciones ajenas al proyecto.

- No instales software externo innecesario.

- Evita ejecutar el juego con permisos de administrador si no son necesarios.

- Si existe riesgo al ejecutar un binario antiguo, utiliza un entorno aislado o una configuración segura.

- No transmitas recursos propietarios a servicios externos.

- No confundas autorización para ejecutar el juego con autorización para alterar el sistema operativo indiscriminadamente.

### Objetivo de la ejecución original

**Utiliza el juego original como principal referencia visual y de interacción.**

Hasta ahora se comparaban principalmente recursos extraídos con capturas de Android.

Eso ya no es suficiente.

Quiero comparaciones entre:

**A. Juego original ejecutándose en Windows.**

**B. Recursos originales extraídos y sus metadatos.**

**C. Nuestro juego ejecutándose realmente en Android/WSA.**

Si una captura del original no puede obtenerse, identifícala como pendiente. No sustituyas su procedencia.

No afirmes que la interfaz coincide con el original solamente porque utiliza sus texturas.

---

# 3. ARQUITECTURA MULTIJUEGO — REGLA OBLIGATORIA

## CASE-RECOMP NO ES UN PORT EXCLUSIVO DE VEGAS

Case-Recomp es una plataforma con una sola APK que admite diferentes motores y juegos.

Nuestra arquitectura debe mantenerse así:

### A. Motor Director/Lingo

Motor independiente que utiliza Huntsville y otros títulos compatibles.

NO DEBE MODIFICARSE PARA CORREGIR VEGAS.

### B. Motor SDA genérico

Debe ser reutilizable para distintos videojuegos desarrollados sobre SDA.

Puede implementar capacidades comunes como:

- Renderizado 2D.

- Composición de capas.

- Carga y decodificación de recursos.

- Interpolación y escalado.

- Transparencias.

- Tratamiento de imágenes.

- Máscaras alfa.

- Transformaciones.

- Gestión de sprites.

- Fuentes y texto.

- Animaciones.

- Temporizadores visuales.

- Manejo de eventos.

- Controles táctiles y de mouse.

- Minijuegos y mecánicas reutilizables.

- Persistencia del estado.

- Compatibilidad con diferentes tamaños de pantalla.

Estas capacidades no deben conocer los nombres ni los ajustes exclusivos de Vegas.

### C. Perfiles por juego o edición

Cada título debe tener su propio perfil visual y, cuando corresponda, un perfil de comportamiento.

Para Vegas, comienza examinando:

`VegasVisualProfile.kt`

También revisa:

`SdaResourceCanvas.kt`

`SdaUiDocument.kt`

`SdaGameView.kt`

`SdaLauncherActivity.kt`

Y los archivos de configuración o adaptadores correspondientes.

Los ajustes específicos de Vegas deben permanecer fuera del núcleo genérico.

## EJEMPLOS OBLIGATORIOS

Si el PDA de Vegas requiere desplazar un icono seis píxeles, eso pertenece al perfil de Vegas.

Si una fuente de Vegas necesita reducirse un 8%, eso pertenece al perfil de Vegas.

Si cierta imagen requiere un tipo particular de interpolación, el motor debe ofrecer el filtro genérico y el perfil debe seleccionar cuál utilizar.

Si una escena necesita un recorte o desplazamiento especial respaldado por el original, sus valores pertenecen al perfil o a sus recursos.

Si otro juego SDA utiliza diferentes posiciones, filtros, fuentes o dimensiones, debe poder especificarlos independientemente.

**PROHIBIDO:**

- Introducir coordenadas exactas de Vegas dentro del motor genérico.

- Añadir rutas de texturas comerciales directamente al núcleo.

- Implementar correcciones específicas mediante `if (gameId == "vegas_heist")` dentro de la lógica genérica.

- Aplicar cambios globales para corregir un problema exclusivo de una edición.

- Contaminar futuros juegos SDA con ajustes visuales de Vegas.

- Duplicar motores completos para solucionar diferencias que puedan resolverse mediante perfiles.

### Prioridad de datos visuales

Utiliza este orden:

1. Valores originales recuperados del juego y sus recursos XUI/MSE.

2. Comportamiento observado directamente al ejecutar el original.

3. Parámetros respaldados por ingeniería inversa.

4. Ajustes específicos documentados y comprobados visualmente.

No introduzcas desplazamientos arbitrarios para conseguir que una captura aislada parezca correcta.

No sobreingenierices compatibilidad con juegos SDA todavía no investigados.

---

# 4. FASE PRIORITARIA — RESOLUCIÓN, NITIDEZ Y ESCALADO

## PROBLEMA PRINCIPAL

Actualmente diversas imágenes de Vegas, especialmente elementos del PDA, presentan baja nitidez o pixelación cuando se amplían en Android.

Huntsville dispone de un tratamiento de escalado que suaviza su framebuffer mediante filtrado bilineal.

Quiero que Vegas alcance una presentación visual de calidad equivalente o superior, sin perder la fidelidad estética del original.

### Investigación inicial

Analiza en profundidad:

- `DirectorStageView.kt`

- `DirectorViewport.kt`

- `SdaGameView.kt`

- `SdaResourceCanvas.kt`

- `VegasVisualProfile.kt`

- Los decodificadores de imágenes SDA.

- Los decodificadores y renderizadores Director.

- Los recursos gráficos originales de Vegas.

- Las dimensiones originales de cada textura.

- Las dimensiones lógicas de la interfaz.

Determina exactamente:

- Cómo se decodifican los bitmaps.

- Si alguna imagen se reduce durante la carga.

- Qué tamaños tienen las imágenes originales.

- Cuándo se escala cada imagen.

- Qué interpolación aplica Android.

- Si el filtrado es uniforme.

- Si existe pérdida de calidad por redimensionamiento repetido.

- Si los recortes introducen errores de muestreo.

- Si las máscaras y transparencias presentan bordes defectuosos.

- Si las posiciones fraccionarias producen imágenes borrosas.

- Si existen diferencias por densidad de pantalla.

## Implementa una política de escalado configurable

El motor debe ofrecer capacidades reutilizables de renderizado de calidad.

El perfil de cada juego debe decidir cómo utilizarlas.

Evalúa, compara y aplica según corresponda:

- Nearest-neighbor para recursos que deban conservar bordes pixelados.

- Bilinear para fotografías o imágenes ampliadas que lo requieran.

- Filtrado de mayor calidad cuando exista soporte y beneficio comprobado.

- Precisión de coordenadas.

- Transformaciones sin pérdida acumulativa.

- Tratamiento correcto del alfa.

- Caché de bitmaps.

- Gestión de memoria.

- Decodificación a resolución original.

### Reglas importantes

**Interpolar no significa generar resolución real.**

Si el recurso original tiene una resolución baja, no afirmes que se convirtió en alta resolución simplemente porque se suavizaron sus píxeles.

Si hay recursos originales de mayor calidad, prioriza utilizarlos.

Puedes investigar estrategias de reconstrucción o mejora de imágenes cuando aporten una diferencia visible, pero no alteres sus contenidos ni inventes detalles gráficos.

No utilices ampliación mediante IA como sustituto automático de la fidelidad original.

No subas imágenes comerciales a servicios externos.

Cualquier versión procesada deberá producirse de manera reproducible a partir de los recursos legítimos del usuario, sin reemplazar destructivamente los originales.

### Atención especial al PDA

Mantén una cobertura completa de los siguientes aspectos, preferentemente mediante inventarios automáticos y auditorías agrupadas:

- Fondo del PDA.

- Bordes y marcos.

- Texturas laterales.

- Iconos.

- Botones.

- Listas de objetivos.

- Reloj.

- Puntuación.

- Indicadores.

- Fuentes.

- Separadores.

- Capas transparentes.

- Texturas utilizadas durante minijuegos.

- Cambios de estado.

- Solapamientos.

- Recortes.

- Animaciones.

**El PDA debe ser una de las primeras pantallas corregidas y verificadas visualmente.**

Genera capturas ANTES y DESPUÉS por bloque visual sustancial, no por cada modificación aislada; reutiliza referencias válidas anteriores.

---

# 5. TIPOGRAFÍA Y TEXTOS ORIGINALES

No acepto que la interfaz final dependa indiscriminadamente de fuentes Android genéricas si el original utiliza una tipografía diferente.

Investiga:

- Fuentes originales.

- Recursos bitmap de letras.

- Atlas tipográficos.

- Métricas.

- Kerning.

- Altura de líneas.

- Anchura.

- Interlineado.

- Alineación.

- Contenedores.

- Desplazamientos.

- Sombreado.

- Color.

- Estilos.

- Representación de acentos.

Compara directamente la apariencia de los textos del juego original y de la APK.

Corrige:

- Letras borrosas.

- Fuentes demasiado grandes.

- Fuentes demasiado pequeñas.

- Texto cortado.

- Solapamientos.

- Texto fuera de botones.

- Puntuaciones que no caben.

- Objetivos superpuestos.

- Diferencias de alineación.

- Saltos de línea incorrectos.

- Textos que desaparecen.

- Captions con interpretación incorrecta.

Implementa capacidades tipográficas genéricas en SDA.

Los ajustes de métricas exclusivos de Vegas pertenecen a su perfil.

Si no es posible reproducir alguna fuente, documenta la limitación y usa la alternativa más cercana verificable; no declares fidelidad exacta.

---

# 6. AUDITORÍA VISUAL DE TODO EL JUEGO ORIGINAL

## NO QUIERO REVISAR SOLAMENTE SEIS CAPTURAS

Debes construir un inventario exhaustivo de las pantallas del videojuego original, reutilizando capturas ya existentes y sesiones de Windows agrupadas; no es necesario volver a recorrer todo el juego por cada pequeño cambio.

Utiliza mouse, teclado, capturas y vídeos cuando sean necesarios.

Investiga todos los componentes disponibles.

### A. Inicio y menús

- Pantalla inicial.

- Logos.

- Menú principal.

- Botones.

- Opciones.

- Perfiles.

- Nueva partida.

- Continuar.

- Confirmaciones.

- Diálogos.

- Ayuda e instrucciones.

- Selección de dificultad si existe.

- Pantallas adicionales confirmadas por el original.

### B. Mapa de investigación

- Fondos.

- PDA.

- Selección de escenas.

- Miniaturas.

- Marcos.

- Tarjetas.

- Contadores.

- Lugares bloqueados.

- Lugares disponibles.

- Lugares completados.

- Información de niveles.

- Indicadores.

- Transiciones.

- Diferentes distribuciones de una a nueve escenas.

### C. Los 25 niveles

Revisa las escenas originales que corresponden a cada nivel.

Comprueba:

- Fondo.

- Resolución.

- Objetos.

- Sprites.

- Orden de capas.

- Transparencias.

- Colores.

- Iluminación.

- Objetivos.

- Animaciones.

- HUD.

- PDA.

- Temporizador.

- Pistas.

- Penalizaciones.

- Resultados.

Cada escena debe tener una identificación inequívoca en el inventario.

No consideres suficiente que un fondo se cargue correctamente: debe aparecer con la escala, posición, recorte y capas correctos.

### D. Los cuatro minijuegos

Analiza profundamente:

**TileRot**

- Textura original.

- Distribución del tablero.

- Rotaciones.

- Sombras.

- Bordes.

- Animaciones.

- Piezas retiradas.

- Estados completados.

**TileSwap**

- Texturas.

- Selección.

- Intercambio.

- Animaciones.

- Piezas retiradas.

- Efectos.

- Finalización.

**WordSearch**

- Fondo.

- Celdas.

- Letras.

- Fuente original.

- Palabras.

- Selección.

- Trazos.

- Marcadores.

- Animaciones.

- Puntuación.

- Estados completados.

**Jigsaw**

- Bandeja.

- Piezas.

- Transparencia.

- Máscaras.

- Sombras.

- Relieve.

- Posición.

- Rotación.

- Animaciones.

- Controles.

- Flechas.

- Paginación.

- Efectos de colocación.

- Imágenes de referencia.

Inspecciona las distintas variantes gráficas utilizadas a lo largo de la campaña.

### E. Desenlace

Revisa las tres fases originales y documenta:

- Pantallas.

- Imágenes.

- Fondo.

- Objetos.

- Bandeja.

- Diálogos.

- Indicaciones.

- Animaciones.

- Temporización.

- Efectos especiales.

- Transiciones.

- Secuencia narrativa.

- Pantalla final.

No sustituyas ninguna fase por interfaces inventadas.

La lógica pendiente del desenlace debe seguir identificada como pendiente, pero sus recursos y requisitos visuales deben investigarse ahora.

---

# 7. COMPARACIÓN REAL WINDOWS VS ANDROID

Quiero un proceso reproducible de comparación visual.

Para cada pantalla o estado relevante de la auditoría final, conserva evidencia rastreable, que puede capturarse y procesarse automáticamente y en lotes:

1. Captura del juego original Windows.

2. Captura equivalente de Android/WSA.

3. Identificador de pantalla, nivel y estado.

4. Resolución de ambas capturas.

5. Coordenadas y región utilizada.

6. Recursos originales asociados.

7. Resultado de la comparación.

8. Defectos encontrados.

9. Estado de corrección.

## Alineación previa

Antes de calcular diferencias, alinea correctamente las capturas.

Ten en cuenta:

- Resolución nativa.

- Viewport.

- Relación de aspecto.

- Letterboxing.

- Escala.

- Desplazamientos.

- Estados temporales.

- Animaciones.

- Puntero del mouse.

- Diferencias de color del sistema.

- Elementos dinámicos.

No utilices una comparación directa píxel a píxel de dos capturas desalineadas.

### Métricas comparativas

Utiliza técnicas apropiadas como:

- Diferencia absoluta de píxeles.

- MAE.

- PSNR.

- SSIM cuando sea útil.

- Medidas de nitidez y preservación de bordes.

- Comparación de regiones.

- Detección de desplazamientos.

- Comprobación de límites.

- Comparación de máscaras alfa.

- Identificación de clipping.

- Comparación de colores.

Las métricas deben estar acompañadas de inspección visual en hitos representativos; no calcules ni inspecciones manualmente métricas extensas después de cada microajuste.

Un resultado numérico favorable no garantiza que la pantalla sea correcta.

### Resultado obligatorio

Genera un HTML navegable donde pueda abrir:

- Captura original.

- Captura Android.

- Comparación lado a lado.

- Diferencias resaltadas.

- Superposición con transparencia.

- Resultado de las métricas.

- Problemas.

- Estado.

- Cambios realizados.

Quiero poder revisar fácilmente si la APK se parece o no al juego original.

No presentes una imagen extraída del paquete como una captura del ejecutable original.

---

# 8. PRUEBAS ANDROID DIRECTAMENTE EN WSA

## TIENES AUTORIZACIÓN PARA USAR WSA Y ADB DIRECTAMENTE

No te limites a compilar una APK y afirmar que funciona.

Debes:

- Compilar la aplicación.

- Instalarla en WSA.

- Iniciarla.

- Interactuar con sus pantallas.

- Utilizar ADB.

- Ejecutar pruebas instrumentadas.

- Tomar capturas reales.

- Leer logs.

- Revisar errores.

- Repetir pruebas relevantes tras correcciones y ejecutar las suites amplias al cerrar cada bloque o hito de riesgo.

Utiliza la interfaz real de Android cuando corresponda.

No sustituyas pruebas E2E de usuario con llamadas internas al modelo.

Las pruebas unitarias e instrumentadas pueden seguir existiendo y deben conservarse, pero su cobertura debe clasificarse correctamente.

### Comprobar resoluciones y formatos

Verifica, cuando el entorno lo permita:

- Lienzo lógico original 800×600.

- Pantallas 4:3.

- Pantallas 16:9.

- Diferentes densidades.

- Resoluciones mayores.

- Ventanas pequeñas.

- Diferentes orientaciones.

- Letterboxing.

- Escalado de entradas táctiles.

Comprueba que:

- Nada se deforme.

- El PDA conserve sus proporciones.

- Las imágenes no aparezcan exageradamente pixeladas.

- El texto continúe legible.

- Los botones sigan siendo seleccionables.

- El hit-test corresponda al objeto mostrado.

- No existan botones fuera de pantalla.

- La interfaz no se corte.

---

# 9. COBERTURA TOTAL DE MENÚS, GRÁFICOS Y ESTADOS

Construye una matriz de cobertura para absolutamente todas las pantallas y elementos descubiertos.

No basta con decir que existen 196 o 200 pruebas.

Quiero saber **qué se ha probado exactamente**.

Cada elemento deberá registrar:

- Identificador.

- Juego/edición.

- Pantalla.

- Nivel, si aplica.

- Recurso gráfico.

- Estado.

- Interacción.

- Prueba ejecutada.

- Dispositivo.

- Captura de evidencia.

- Comparación con original.

- Resultado.

- Defecto asociado.

- Estado de resolución.

### Estados que deben probarse

- Normal.

- Presionado.

- Seleccionado.

- Deshabilitado.

- Hover, cuando exista.

- Animando.

- Completado.

- Bloqueado.

- Desbloqueado.

- Error.

- Cancelado.

- Pausado.

- Restaurado.

No inventes estados que el original no tenga.

### Pruebas de interacción

Comprueba cada menú y botón mediante eventos reales de la interfaz.

Incluye:

- Entrar.

- Salir.

- Volver.

- Confirmar.

- Cancelar.

- Seleccionar.

- Arrastrar.

- Soltar.

- Girar.

- Desplazar.

- Reintentar.

- Pausar.

- Reanudar.

- Guardar.

- Restaurar.

No consideres que un botón está probado porque existe su rectángulo o callback.

Verifica el resultado visible de la interacción.

---

# 10. PRUEBA INTEGRAL DE LOS 25 NIVELES

Quiero ampliar las pruebas existentes hasta disponer de un recorrido integral de la campaña en Android.

La validación debe distinguir claramente:

**Prueba del motor Kotlin/JVM.**

**Prueba instrumentada Android de un componente aislado.**

**Prueba de pantalla Android con recursos originales.**

**Prueba E2E desde el menú y la importación.**

Estas categorías NO son equivalentes.

### Recorrido objetivo

Catálogo → Importación → Inicio → Perfil → Mapa → Escenas → Objetivos → Bonus → Resultado → Siguiente nivel.

Repetir hasta el nivel 25.

Después:

Primera fase → Segunda fase → Tercera fase → Finalización.

### Condiciones obligatorias

- No modificar manualmente `levelIndex`.

- No forzar `completedObjects`.

- No asignar fases de victoria artificialmente.

- No acreditar puzles mediante `solveBonus()` en pruebas de jugabilidad.

- No saltar escenas sin registrarlo.

- No inventar respuestas o mecanismos.

- No utilizar imágenes sintéticas como demostración de fidelidad comercial.

- No marcar como aprobada una fase no implementada.

- No confundir comprobación del motor con recorrido de pantalla.

Se permiten entradas automatizadas, velocidades de prueba y checkpoints ganados legítimamente, pero deben quedar identificados.

### Restauración

Comprueba guardado y reanudación:

- Desde el mapa.

- Durante una escena.

- Tras encontrar objetos.

- En cada familia de minijuegos.

- Con piezas seleccionadas.

- Con selecciones parciales.

- Durante resultados.

- Durante el desenlace.

Incluye pruebas de cierre y reapertura real de la aplicación y, cuando sea viable, muerte y recreación del proceso Android.

La simple serialización/restauración en memoria no debe presentarse como prueba de persistencia entre procesos.

---

# 11. ANIMACIONES Y EFECTOS VISUALES

Una interfaz estática con las texturas correctas no representa necesariamente el juego original.

Estudia las animaciones originales directamente en Windows.

Investiga y reconstruye:

- Duraciones.

- Fotogramas.

- Interpolaciones.

- Transiciones.

- Rotaciones.

- Movimiento de objetos.

- Efectos de selección.

- Transparencias animadas.

- Aparición y desaparición.

- Efectos de colocación.

- Scroll.

- Pistas.

- Cambios del PDA.

- Estados de botones.

- Efectos de finalización.

Utiliza capacidades genéricas del motor para ejecutar animaciones.

Los parámetros particulares de Vegas deben permanecer en su perfil o recursos.

No inventes duraciones si pueden medirse directamente.

No alteres la lógica de los minijuegos para conseguir una animación visual.

Separa correctamente estado de juego, renderizado y animación.

---

# 12. ELIMINAR CONTROLES PROVISIONALES

Identifica todos los componentes de depuración que siguen apareciendo en Vegas.

Ejemplos:

- Botones genéricos de giro.

- Marcos Android temporales.

- Textos de diagnóstico.

- Fondos de relleno.

- Indicadores provisionales.

- Etiquetas que no coinciden con el original.

- Controles colocados arbitrariamente.

- Botones superpuestos.

Sustitúyelos por los controles originales cuando existan y sea posible reconstruirlos.

Cuando una adaptación táctil sea imprescindible porque el original utiliza mouse, implementa una alternativa ergonómica, pero mantenla separada de la fidelidad del modo original.

No contamines el motor con botones exclusivos de Vegas.

No elimines un control funcional sin proporcionar una alternativa comprobada.

---

# 13. RENDIMIENTO Y MEMORIA

La mejora de nitidez NO debe convertir Vegas en una aplicación excesivamente pesada.

Mide:

- Uso de RAM.

- Heap Java/Kotlin.

- Memoria nativa.

- Consumo de bitmaps.

- Caché de texturas.

- Tiempo de decodificación.

- Tiempo de carga de pantallas.

- FPS.

- Tiempo de renderizado.

- GC.

- Fugas de memoria.

- Comportamiento al cambiar escenas.

- Estabilidad tras uso prolongado.

No cargues todos los recursos de los 25 niveles simultáneamente.

No crees copias ampliadas de cada textura sin necesidad.

Reutiliza bitmaps cuando corresponda y libera recursos.

Compara el rendimiento antes y después de aplicar filtros y optimizaciones.

No sacrifiques la jugabilidad ni la estabilidad por suavizar imágenes.

---

# 14. HUNTSVILLE Y DIRECTOR — AISLAMIENTO ABSOLUTO

El objetivo de este Goal es mejorar la presentación de Vegas sobre SDA.

No quiero modificaciones de comportamiento en Huntsville.

Puedes estudiar su sistema de escalado como referencia.

**No debes copiar ajustes exclusivos de Huntsville a Vegas ni viceversa.**

Si creas capacidades genéricas reutilizables para SDA, deben quedar dentro del sistema SDA o en utilidades compartidas justificadas.

Cualquier modificación verdaderamente compartida debe ser mínima, demostrablemente compatible y sometida a pruebas de regresión.

Comprueba:

- Inicio de Huntsville.

- Renderizado.

- Interacciones.

- Importación.

- Compatibilidad de partidas.

- Menús.

- Regresiones funcionales.

El motor Director debe continuar trabajando como antes.

---

# 15. ORDEN OBLIGATORIO DE TRABAJO — ACTUALIZADO A V3

La presentación visual sigue siendo prioritaria, pero la recuperación masiva de fondos, marcos, recursos y pantallas completas va antes del microajuste de píxeles. **El plan de bloques de la Parte I constituye el orden operativo vigente.** El desglose anterior se conserva en el Anexo C como trazabilidad histórica; no exige microauditorías por cambio.

# 16. ENTREGABLES OBLIGATORIOS

Quiero un directorio de resultados bien organizado bajo `local-output`, sin incluir materiales privados en Git.

Como mínimo debe contener:

**A. APK experimental**

Una APK nueva de Vegas con las mejoras visuales disponibles.

**B. Capturas del original Windows**

Organizadas por pantalla, nivel, minijuego y estado.

**C. Capturas Android/WSA**

Las mismas pantallas y estados correspondientes.

**D. HTML comparativo**

Comparaciones lado a lado, superposiciones, diferencias y métricas.

**E. Matriz de cobertura**

Todas las pantallas, menús, escenas, recursos y estados investigados.

**F. Informe visual**

Problemas, correcciones, evidencias y pendientes.

**G. Informe de resolución**

Qué métodos de escalado se compararon y cuáles se eligieron para cada categoría de recurso.

**H. Informe de rendimiento**

Consumo de memoria, tiempos de carga, renderizado y regresiones.

**I. Informe de arquitectura**

Qué cambios pertenecen al motor genérico SDA, al perfil Vegas y a la capa Android.

**J. Informe de pruebas**

Pruebas Kotlin/JVM, instrumentadas, E2E y comparativas, claramente separadas.

**K. Registro Git**

Commits, archivos modificados y estado de publicación.

Actualiza:

`docs/MYSTERY_PI_ANDROID_VISUAL_COMPARISON.md`

`docs/MYSTERY_PI_ANDROID_AUDIT.md`

`docs/MULTIGAME_ARCHITECTURE.md` si la arquitectura lo requiere.

Agrega documentación específica adicional cuando sea necesario.

Los resultados locales deben tener rutas claras y reproducibles.

---

# 17. CRITERIOS DE ACEPTACIÓN VISUAL

Una pantalla solo podrá marcarse como visualmente validada si:

1. Se identificaron sus recursos correctos.

2. Se observó su equivalente original, cuando sea accesible.

3. Se ejecutó realmente en Android.

4. Se capturó evidencia.

5. Se compararon proporciones y posiciones.

6. Se revisaron nitidez y transparencia.

7. Se comprobó la tipografía.

8. Se verificaron sus estados visuales.

9. Se probaron sus controles.

10. Se comprobaron solapamientos y recortes.

11. Se evaluó el escalado.

12. Se documentaron sus diferencias restantes.

Utiliza estados explícitos:

- **APROBADO:** existe evidencia suficiente de funcionamiento y fidelidad para el alcance declarado.

- **PARCIAL:** funciona, pero tiene diferencias conocidas.

- **DEFECTUOSO:** existe un problema reproducible.

- **NO IMPLEMENTADO:** falta el componente.

- **NO VERIFICADO:** existe, pero no se probó lo suficiente.

- **BLOQUEADO:** existe una dependencia o limitación concreta.

No conviertas automáticamente una pantalla en APROBADA porque una prueba unitaria pasó.

No ocultes diferencias pequeñas para aumentar artificialmente el porcentaje de cobertura.

---

# 18. PROHIBICIÓN DE ATAJOS

Queda prohibido:

- Usar capturas ficticias.

- Afirmar que se ejecutó el original sin haberlo hecho.

- Presentar recursos extraídos como capturas del juego funcionando.

- Comparar imágenes desalineadas sin avisarlo.

- Marcar menús como probados sin abrirlos.

- Omitir estados visuales porque las pruebas son difíciles.

- Reemplazar elementos originales por gráficos genéricos sin documentarlo.

- Modificar el motor para ajustar coordenadas particulares de Vegas.

- Introducir excepciones comerciales dentro del núcleo SDA.

- Saltar niveles para afirmar cobertura E2E total.

- Forzar resultados de puzles.

- Desactivar pruebas fallidas.

- Publicar recursos comerciales privados.

- Alterar Huntsville.

- Declarar calidad visual perfecta sin evidencia.

- Dar por concluido este Goal después de generar unas pocas capturas.

---

# 19. FORMA DE TRABAJO — ACTUALIZADA A V3

Se mantiene la autorización para ejecutar herramientas locales, Windows, WSA y ADB dentro de los límites establecidos. Trabaja autónomamente. **Por bloque sustancial**: consulta la evidencia ya disponible, implementa varias mejoras relacionadas, ejecuta pruebas focalizadas, compila, realiza una tanda agrupada de pruebas/capturas Android, comprueba regresiones, actualiza documentación compacta, crea un commit y publica. Las pruebas integrales y el HTML completo se realizan por hito y al cierre. Una incidencia crítica se corrige inmediatamente; un defecto menor no debe paralizar otras mejoras independientes. No declares un bloque completo con fallos conocidos esenciales ni falsifiques pruebas.

# 20. RESULTADO FINAL ESPERADO

Quiero que, al abrir Mystery P.I.: The Vegas Heist dentro de Case-Recomp en Android, el juego se vea lo más parecido posible al original de Windows.

Especialmente:

- PDA nítido.

- Imágenes sin pixelación innecesaria.

- Fuentes correctas.

- Menús originales.

- Mapa fiel.

- Escenas bien compuestas.

- Objetos correctamente representados.

- Cuatro minijuegos visualmente reconstruidos.

- Transparencias y sombras.

- Animaciones auténticas.

- Controles coherentes.

- Textos sin cortes.

- Sin solapamientos.

- Escalado correcto.

- Buen rendimiento.

- Funcionamiento probado directamente en Android.

Y al mismo tiempo:

**El motor SDA debe continuar siendo genérico, extensible y reutilizable mediante perfiles independientes por juego.**

Vegas puede tener tantas calibraciones específicas como sean necesarias y estén justificadas, pero todas deben permanecer en su perfil o adaptadores separados.

Director/Huntsville no debe verse afectado.

La APK debe mantener la arquitectura de múltiples motores.

El objetivo no es solamente aumentar la cantidad de tests. Es conseguir resultados visibles, comprobables y auténticos.

---

# ORDEN FINAL

**EMPIEZA AHORA.**

Tu prioridad inmediata es:

**1. Reutilizar las evidencias Windows/Android disponibles; realizar una nueva campaña de captura solo si desbloquea varios cambios importantes.**

**2. Recuperar primero todos los fondos, marcos, capas y elementos ausentes de minijuegos y PDA, sin esperar a perfeccionar la nitidez marginal.**

**3. Corregir fuentes, cortes, solapamientos, posiciones y controles provisionales.**

**4. Conservar cobertura exhaustiva como criterio final, automatizando inventarios y reservando la auditoría visual profunda para hitos de integración y cierre.**

**5. Extender la validación a todos los menús, mapas, niveles, escenas y minijuegos en WSA.**

**6. Mantener cada ajuste exclusivo de Vegas separado del motor genérico SDA.**

**7. Entregar capturas antes/después, HTML comparativo, APK experimental, matriz de pruebas y commits verificados.**

**8. No declarar el trabajo terminado hasta que su cobertura y sus limitaciones estén demostradas.**

No quiero más pantallas simplemente funcionales que se vean como prototipos.

**Quiero que Case-Recomp reproduzca visualmente Vegas con fidelidad, calidad y pruebas reales, mientras construimos un motor SDA reutilizable para futuros juegos.**

Comienza por la auditoría visual y ejecuta los primeros bloques de mejora sin esperar a finalizar el resto del desenlace.

# ANEXO C — HISTORIAL DE PLANIFICACIÓN V1 (CONSERVADO, PROCEDIMIENTO SUSTITUIDO)

El siguiente orden y la cadencia detallada proceden del Goal original. **Se mantienen para demostrar que no se eliminó ningún objetivo**, pero la ejecución, tamaños de bloque y frecuencia de pruebas/capturas se rigen exclusivamente por la Parte I V3 y las secciones 15/19 actualizadas.

# 15. ORDEN OBLIGATORIO DE TRABAJO

## LA PRESENTACIÓN VISUAL VA PRIMERO

No quiero que vuelvas inmediatamente a implementar las fases restantes del desenlace mientras el PDA, las fuentes y otras pantallas sigan mostrando defectos visibles importantes.

Organiza el trabajo en bloques sustanciales y verificables de varias mejoras relacionadas, no en microtareas por control.

### BLOQUE 1 — Auditoría visual comparativa

1. Examinar el código actual.

2. Abrir Vegas original en Windows.

3. Abrir Vegas Android en WSA.

4. Crear inventario de pantallas.

5. Capturar y comparar interfaces.

6. Identificar problemas de fidelidad.

7. Clasificar problemas por prioridad.

8. Verificar separación motor/perfil.

Entrega la matriz inicial y un informe de diferencias reales.

### BLOQUE 2 — Resolución y PDA

1. Corregir escalado.

2. Mejorar nitidez.

3. Corregir PDA.

4. Revisar transparencias.

5. Solucionar solapamientos.

6. Corregir fuentes y posiciones prioritarias.

7. Probar diferentes resoluciones.

8. Generar comparaciones antes/después.

Este es el primer bloque de cambios visuales que quiero evaluar.

### BLOQUE 3 — Mapa, HUD, escenas y menús

1. Reconstruir componentes originales.

2. Ajustar posiciones.

3. Corregir fuentes.

4. Integrar estados visuales.

5. Probar menús.

6. Revisar variantes de mapas.

7. Comparar escenas.

8. Verificar navegación Android.

### BLOQUE 4 — Cuatro minijuegos

1. Revisar todos los elementos gráficos.

2. Corregir texturas.

3. Mejorar nitidez.

4. Recuperar sombras y efectos.

5. Integrar animaciones.

6. Eliminar controles provisionales.

7. Probar gestos y resultados.

8. Comparar con los minijuegos originales.

### BLOQUE 5 — Desenlace visual

1. Auditar visualmente las tres fases originales.

2. Completar sus recursos gráficos.

3. Preparar efectos y transiciones.

4. Corregir pantallas existentes.

5. Documentar la funcionalidad pendiente.

No declares completo el desenlace sin integración auténtica de sus fases.

### BLOQUE 6 — Pruebas exhaustivas Android

1. Recorrer todos los menús.

2. Revisar los 25 niveles.

3. Capturar sus escenas.

4. Probar las variantes de minijuegos.

5. Validar estados visuales.

6. Probar diferentes resoluciones.

7. Probar guardados y reapertura.

8. Verificar regresiones.

9. Corregir defectos descubiertos.

### BLOQUE 7 — Cierre visual

1. Repetir las comparaciones.

2. Validar correcciones.

3. Medir rendimiento final.

4. Revisar controles.

5. Generar APK experimental.

6. Completar HTML comparativo.

7. Documentar cobertura.

8. Identificar cualquier diferencia pendiente.

Después de estabilizar la presentación visual, continúa el desarrollo funcional restante del desenlace conforme al plan general del proyecto.

---


# 19. FORMA DE TRABAJO

Tienes autorización para ejecutar las herramientas locales necesarias dentro del alcance definido.

Trabaja autónomamente.

No necesito aprobar cada clic, compilación, captura, prueba ni commit.

**Organiza fases con contenido sustancial, límites comprobables y commits por conjunto; tampoco hagas un único cambio gigantesco sin pruebas intermedias.**

En cada bloque:

1. Analiza.

2. Investiga el original.

3. Identifica la diferencia.

4. Implementa la corrección.

5. Prueba en Kotlin cuando corresponda.

6. Compila Android.

7. Ejecuta en WSA.

8. Captura el resultado.

9. Compara con Windows.

10. Revisa regresiones.

11. Documenta.

12. Realiza commit y push.

No consideres un bloque terminado únicamente porque compila: exige pruebas funcionales/visuales relevantes agrupadas al final del bloque.

Si una prueba revela un defecto, corrígelo antes de declarar el componente aprobado.

Si hay una imposibilidad real, explica el bloqueo, conserva las evidencias y continúa con otros elementos independientes que puedan avanzarse de forma segura.

No inventes resultados para aparentar progreso.

---

