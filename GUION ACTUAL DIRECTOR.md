# MISIÓN: Mystery Case Files: Prime Suspects — Reconstrucción Android

> **Nota de vigencia — 2026-10-13:** Este **guion conserva los objetivos y reglas de su fecha**. Las instrucciones que citan `intento-geminis` son históricas: actualmente se identificó únicamente la rama `main`. No repetir fases completadas ni reinterpretar prioridades sin revisar el código y la evidencia vigentes. [Estado actual](docs/STATUS_ACTUAL.md).

## Objetivo principal

Comienza el desarrollo de **Mystery Case Files: Prime Suspects** dentro del repositorio `Dr3amyBerry/Case-Recomp`.

El objetivo es reconstruir el juego original para Android con la mayor fidelidad funcional, visual y sonora posible, aprovechando la arquitectura y las herramientas que ya tenemos.

**Trabaja directamente. No te limites a elaborar planes o investigar indefinidamente.**

## 1. Regla fundamental: UN SOLO MOTOR

Este proyecto es una plataforma para ejecutar múltiples juegos, no una colección de aplicaciones o motores independientes.

- Reutiliza el motor existente y sus sistemas comunes.
- Cada juego tiene su propio perfil o adaptador de configuración.
- El comportamiento específico de Prime Suspects debe integrarse mediante su perfil, recursos, scripts y extensiones justificadas.
- No dupliques sistemas de renderizado, audio, entradas, escenas, scripts, perfiles de jugadores o persistencia si ya existen.
- Las mejoras genéricas deben beneficiar también a los juegos compatibles existentes.
- Mantén separados los datos de cada título: perfiles, partidas, progreso, opciones y recursos.
- Respeta las diferencias técnicas entre Director y SDA mediante los adaptadores apropiados, sin mezclar reglas específicas en el núcleo común.
- No rompas Huntsville, Vegas ni ningún juego existente.

**Prime Suspects debe ser un juego adicional dentro de nuestra plataforma, no otro proyecto desde cero.**

## 2. PRIORIDAD ABSOLUTA: DECOMPILACIÓN

Antes de reconstruir manualmente comportamientos, investiga y confirma el formato real de Prime Suspects.

Si utiliza Macromedia/Adobe Director, examina sus archivos, ejecutable, películas, casts y scripts.

**La decompilación es nuestra principal ventaja. Hay que explotarla.**

Prioriza:

1. Detectar la versión y el formato de Director.
2. Localizar películas, casts, recursos y bytecode Lingo.
3. Extraer el máximo código Lingo recuperable.
4. Reconstruir handlers, scripts de película, frames, sprites, variables globales, eventos y flujo de navegación.
5. Recuperar las relaciones entre scripts, recursos, escenas y estados.
6. Comparar el comportamiento reconstruido con el ejecutable original.
7. Integrar el código y la semántica recuperados en nuestro intérprete o motor existente.

Si tenemos herramientas propias de descompilación, reutilízalas y mejóralas. Si es necesario, desarrolla extractores o analizadores adicionales que puedan reutilizarse en futuros juegos Director.

**Prefiero recuperar 200 handlers reales de Lingo que escribir 200 aproximaciones manuales.**

No supongas que un script fue recuperado correctamente solo porque puede visualizarse parcialmente. Diferencia código recuperado, reconstruido e inferido.

Si una herramienta no consigue extraer algo, identifica rápidamente el bloqueo, prueba alternativas razonables y continúa con trabajo útil. No consumas horas persiguiendo un detalle secundario sin resultados.

## 3. Reutilizar Huntsville como acelerador

Examina cuidadosamente la implementación actual de Mystery Case Files: Huntsville y la infraestructura Director.

Identifica qué puede funcionar directamente para Prime Suspects:

- Carga de películas y recursos.
- Ejecución de Lingo.
- Casts y sprites.
- Renderizado de escenarios.
- Navegación entre escenas.
- Mouse y adaptación táctil.
- Animaciones y temporizadores.
- Sonido y música.
- Variables y estado del juego.
- Guardado y restauración.
- Interfaz original.

No reconstruyas funcionalidades ya existentes.

Cuando Prime Suspects necesite una instrucción Lingo nueva, impleméntala de forma genérica siempre que corresponda.

**El desarrollo de Prime Suspects también debe fortalecer el motor Director para nuestros próximos juegos.**

## 4. Trabaja en BLOQUES GRANDES

No quiero microtareas innecesarias ni decenas de commits con cambios insignificantes.

Organiza el trabajo en bloques funcionales grandes, según las dependencias reales:

**Bloque A — Recuperación de contenido y Lingo**

Confirmar Director, extraer recursos, identificar películas y scripts, recuperar el código posible y elaborar un inventario verificable de funcionalidades.

**Bloque B — Compatibilidad del motor**

Implementar las instrucciones Lingo, eventos, comportamientos de sprites, renderizado y mecanismos genéricos que Prime Suspects necesite y que el motor todavía no soporte.

**Bloque C — Primer recorrido jugable**

Integrar portada, menús, perfil del juego, navegación, escenas, interacción real con objetos y transición de estados usando los recursos originales.

**Bloque D — Juego completo**

Integrar progresión, niveles, puzles, pistas, puntuación, temporizadores, diálogos, pantallas especiales y desenlace conforme al código recuperado.

**Bloque E — Fidelidad, persistencia y optimización**

Verificar audio, animaciones, guardados, restauración, rendimiento, consumo de memoria y comportamiento frente al original de Windows.

Estos bloques son una guía, no una excusa para detenerte constantemente. Si varias partes están relacionadas, resuélvelas juntas de forma segura.

## 5. Forma de trabajar

Quiero ejecución autónoma y eficiente:

- Inspecciona primero lo imprescindible del repositorio.
- Lee las instrucciones y convenciones existentes, incluido `GUION ACTUAL.MD` cuando corresponda.
- Usa el código, herramientas, descompiladores y documentación ya disponibles.
- No solicites confirmación para cada implementación técnica ordinaria.
- No crees ramas nuevas sin autorización; respeta la rama de trabajo existente.
- Haz commits descriptivos por bloque de trabajo coherente.
- No reemplaces funcionalidades originales con interfaces inventadas si pueden recuperarse del juego.
- Evita refactorizaciones generales que no aporten directamente al objetivo.
- No desperdicies tiempo repitiendo análisis ya documentados.
- No sacrifiques estabilidad ni calidad por producir commits rápidamente.
- Conserva los archivos originales y no publiques recursos comerciales privados.
- Si hay información insuficiente, investiga lo necesario, documenta la limitación y continúa donde exista trabajo verificable.

**La regla es sencilla: investigar para implementar, no investigar por investigar.**

## 6. Pruebas obligatorias

Cada bloque debe producir resultados comprobables.

Ejecuta las pruebas que correspondan, incluyendo:

- Compilación del motor y de Android.
- Pruebas de scripts y comandos Lingo recuperados.
- Navegación y jugabilidad real.
- Guardado y recuperación de partidas.
- Regresiones de juegos existentes.
- Comparaciones con Windows cuando se disponga del original ejecutable.
- Validación de memoria y recursos gráficos en recorridos prolongados.

No consideres completada una funcionalidad únicamente porque compila.

No marques como verificada la fidelidad visual sin evidencia real y comparable.

Si una prueba no puede realizarse, indícalo como **NO VERIFICADO**, sin inventar resultados.

## 7. Criterios de calidad del motor

Durante la implementación:

- Prioriza reutilización y arquitectura limpia.
- Reduce trabajo repetido y asignaciones innecesarias.
- Evita cargar o decodificar recursos repetidamente sin necesidad.
- Libera correctamente bitmaps, buffers y recursos nativos.
- Evita crecimiento progresivo injustificado de RAM.
- Mantén compatibles los perfiles y las partidas de otros títulos.
- No introduzcas soluciones temporales difíciles de mantener cuando se pueda recuperar el comportamiento real de Lingo.

No optimices a ciegas: mide cuando resulte necesario y resuelve defectos concretos.

## 8. Documentación y seguimiento

Mantén un seguimiento centralizado y actualizado de Prime Suspects, con:

- Estado de la decompilación.
- Scripts Lingo recuperados.
- Funcionalidades implementadas.
- Extensiones genéricas añadidas al motor.
- Pruebas ejecutadas y resultados.
- Diferencias conocidas frente al original.
- Trabajo pendiente ordenado por prioridad.

Distingue claramente **IMPLEMENTADO**, **PROBADO**, **PARCIAL** y **NO VERIFICADO**.

No crees documentación redundante ni repartas los pendientes entre múltiples archivos sin necesidad.

## 9. Instrucción final: EMPIEZA A IMPLEMENTAR

Tu primera acción debe ser comprobar el soporte actual de Director, localizar los recursos disponibles de Prime Suspects y determinar la ruta más rápida hacia una reconstrucción basada en el código Lingo original.

Después, comienza inmediatamente el primer bloque de implementación viable.

No termines tu trabajo únicamente con un informe de investigación o un plan.

Quiero que aproveches todo lo ya construido, recuperes la mayor cantidad posible de Lingo y avances en bloques grandes, con commits y pruebas reales.

**Prioridad de trabajo:**

DECOMPILACIÓN → REUTILIZACIÓN DEL MOTOR → INTEGRACIÓN REAL → JUEGO COMPLETO → FIDELIDAD → OPTIMIZACIÓN.

El objetivo no es crear una imitación de Prime Suspects.

**El objetivo es ejecutar y reconstruir Prime Suspects sobre nuestro motor compartido, recuperando tanto comportamiento original como sea técnicamente posible.**

Empieza.