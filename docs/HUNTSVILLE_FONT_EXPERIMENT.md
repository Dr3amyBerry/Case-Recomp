# Huntsville: fuentes originales y prueba experimental

Registro de la prueba 0.1; diagn?stico actualizado en [correcci?n PFR1](HUNTSVILLE_PFR1_CONTOUR_CORRECTION.md).

Fecha: 2026-10-08. Estado: **PFR1 EXTRAÍDO; CONVERSIÓN EXPERIMENTAL CON DEFECTOS VISUALES**.
No se adoptó un renderer nuevo ni se reemplazó la build aprobada.

## Sincronización y protección de la referencia

El pull avanzó `main` de `1cdaaa9` a `2ad0d30`, incorporando
`docs/GAME_SUPPORT_CANDIDATES.md`. No había PR abiertos. Python CI y Android CI del
commit remoto terminaron correctamente. Se inspeccionaron los workflows y los
artefactos del run Android `37857846790`: APK sintética y métricas, sin código
nuevo generado exclusivamente en Actions. Los workflows hacen checkout del repositorio;
no hay un paso que genere/parchee fuentes antes de compilar.

Los cambios locales pendientes de Mystery P.I. se conservaron sin integrarlos en
este experimento. El archivo heredado no seguido `DirectorAndroidPorts.kt` permanece
sin cambios, como ya consta en la referencia aprobada; no es código perdido en CI.

Se comprobaron otra vez las dos copias congeladas de
[la referencia aprobada](HUNTSVILLE_APPROVED_REFERENCE.md). Sus hashes coinciden:

- Release: `d445bc57ad12944de8e254a79ea293523771ee8ca8d35c127ec44bb71eb447da`.
- Debug: `3d48bc72842e182fbbf78e284e4278b2a0a54fc1d661480aadb8b0b840765b37`.

No se modificaron `AndroidDirectorPorts.kt`, el perfil revisión 6 ni el motor Director.
El laboratorio usa un proyecto Gradle separado y el package
`org.rigorcore.caserecomp.fontlab`; no comparte partidas o preferencias del jugador.

## Hallazgo en los originales

Se leyeron `MysteryCaseFiles.exe` y los **38 casts `.cct`** con el lector Afterburner
existente. El ejecutable contiene **seis fuentes embebidas** como Xtras tipo 15,
subtipo `font`, con recursos XMED cuyo encabezado es PFR1. No aparecen como miembros
convencionales tipo 16. Esta diferencia explica por qué una búsqueda limitada a
ese tipo no encuentra las fuentes.

Las seis caras son Tekton normal, Tekton itálica, Digital Readout Thick,
Typical Writer, AmericanTypewriter Medium y Palatino Linotype. Los `.cct` contienen
referencias a caras en textos, pero no miembros adicionales con payloads de fuente.
Los mapas Fmap/FXmp y las tablas de caras de textos no equivalen a datos de glifos.

Tekton normal e itálica contienen cada una **225 registros PFR1**, resolución de
contornos de **1.000 unidades**. La auditoría privada guarda hashes de cada original,
relaciones KEY*, identificadores de miembros/recursos y payloads sin modificar.
No se publica ningún archivo comercial ni sus contornos.

## Recuperación y límites

Se probó localmente el parser externo de
[LibreShockwave](https://github.com/LibreShockwave/LibreShockwave), revisión
`fca530f9ef388d7ff38fa6c7117feae5bb5411c6`. Su API anuncia lectura de PFR1 y conversión
de fuentes; aqué se verificó contra estos recursos concretos.

El primer TTF de su conversor tenía `head.indexToLocFormat = 1` pero una tabla
`loca` de offsets cortos. FontTools detectó la inconsistencia; no se usa ese TTF en
el laboratorio. El camino final exporta contornos con un adaptador pequeño y
construye CFF OpenType usando FontTools, preservando curvas cúbicas y avances de la
salida del parser. Es una conversión experimental, no la fuente OTF/TTF de autoría.

Se verificaron estructura OpenType, cobertura de muestras españolas y carga/dibujo
con Android Paint. Cada OTF expone 219 caracteres Unicode después de excluir el
registro nulo y códigos Windows-1252 indefinidos. La imagen revisada muestra
**deformaciones de contornos en algunos caracteres**. Por tanto, la extracción del
PFR1 es real, pero la fidelidad del decodificador de contornos sigue pendiente.
No se atribuye ese defecto al diseño original de Tekton.

No están verificados/reconstruidos el hinting, kerning, métricas completas de Director,
selección de variantes, tratamiento de todas las codificaciones y pixel equivalencia.
No se retiran los ajustes de anchura, tamaño, cajas, baseline o cursor del perfil aprobado.

## Comparación preparada para revisión humana

La app independiente **Tekton - prueba experimental** permite escribir texto,
cambiar tamaño y elegir regular/itálica. Arriba de cada par dibuja sans-serif Android
con las escalas actuales de Tekton; abajo dibuja la fuente recuperada al tamaño nominal.
Las líneas muestran la baseline de cada muestra. Es comparación de glifos y avances;
no reproduce toda la lógica de cajas y wrapping del renderer aprobado.

Artefactos locales: `local-output/huntsville-font-experiment-2026-10-08/`.
Auditoría, PFR1, contornos y primeras conversiones:
`private/huntsville/font-research/`. El código reproducible está en
[tools/font-experiment](../tools/font-experiment/README.md).

La prueba instrumentada en WSA cargó ambas fuentes y dibujó las muestras sin abrir
una actividad. Resultado: PASS para carga/cobertura/dibujo; **no PASS de fidelidad**.
Las cuatro pruebas sintéticas comprueban preservación de cúbicas/avances/Unicode,
rechazo de sobrescritura y rechazo de contornos inválidos o glifos vacíos.

La adopción queda fuera de este experimento: primero corregir las deformaciones,
contrastar con el original y obtener la revisión visual solicitada por el usuario.

## Corrección de contornos (prueba 0.2)

El fallo de dirección implícita de ORU se corrigió únicamente en el parser de
investigación. La comparación cubre los 450 registros de Tekton normal/itálica,
con diferencias menores de una unidad en 21 compuestos y conservación de los
contornos en CFF. Véanse los resultados, límites y nueva prueba independiente en
[HUNTSVILLE_PFR1_CONTOUR_CORRECTION.md](HUNTSVILLE_PFR1_CONTOUR_CORRECTION.md).
La prueba 0.1 anterior se conserva como evidencia; no se adopta la fuente en Huntsville.
