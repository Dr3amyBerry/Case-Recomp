# Case-Recomp — Catálogo de juegos candidatos a compatibilidad

> **Estado:** propuesta de investigación y planificación, **no** declaración de compatibilidad.  
> **Actualización:** 2026-10-08.  
> **Cobertura:** títulos de PopCap/SpinTop y Big Fish (incluidos estudios asociados o juegos distribuidos por estas empresas), ordenados desde los más antiguos hasta 2025.  
> **Regla:** cada versión/edición requiere análisis de sus archivos originales, identificación del motor, pruebas y aprobación antes de marcarla como compatible.

## Objetivo

Registrar juegos de interés para extender **Case-Recomp** sin reconstruir el HUB ni duplicar motores. El proyecto conserva el motor **Director/Lingo** para títulos compatibles y estudia un futuro módulo para **SDA Game Framework**. Los perfiles por juego deben configurar diferencias comprobadas, no sustituir las reglas originales.

**No todos los juegos listados son de crímenes.** Se incluyen también puzles, objetos ocultos, aventuras y arcades clásicos como candidatos exploratorios para futuras tecnologías.

## Prioridad de investigación y posibles próximos soportados

| Juego | Estado de planificación | Evidencia / situación | Próximo paso |
|---|---|---|---|
| Mystery Case Files: Huntsville | Referencia existente | Director 8.5/Lingo comprobado en la copia usada; versión Android revisada visualmente por el usuario. | Mantener como prueba de regresión; no rehacer el motor. |
| Mystery P.I.: The Vegas Heist | **Prioridad actual** — investigación activa | SDA Game Framework, SDL/BASS y recursos PE/XUI identificados en la copia investigada. | Continuar análisis Ghidra/Frida, callbacks y lógica de escenas; construir primer slice jugable verificable. |
| Mystery Case Files: Prime Suspects | Candidato próximo | Se ha documentado uso de Director/Flash en el juego, pero su compatibilidad con el runtime actual no se ha probado. | Auditar instalación y diferencias frente a Huntsville sin heredar sus ajustes visuales. |
| Mystery Case Files: Ravenhearst | Candidato próximo | Relacionado con la primera generación de Mystery Case Files; motor y edición concreta aún requieren inspección. | Identificar formatos, VM y necesidades de escena/puzles. |
| Mystery P.I.: The Lottery Ticket | Candidato siguiente para SDA | Misma saga y estudio asociado que The Vegas Heist; la reutilización real del runtime no está verificada. | Comparar binarios/recursos y contratos SDA una vez exista el primer slice. |
| Amazing Adventures: The Lost Tomb | Candidato para validar SDA | Juego de SpinTop; tecnología concreta y compatibilidad no verificadas. | Inspeccionar ejecutable y formatos para comprobar que el módulo SDA no dependa de Mystery P.I. |
| Mystery P.I.: Lost in Los Angeles | Candidato de expansión SDA | Misma saga; arquitectura concreta por verificar. | Auditar sus binarios después del primer juego SDA funcional. |
| Mystery Case Files: Madame Fate | Candidato de expansión Director | Saga Mystery Case Files; subsistemas y formato de la edición por verificar. | Probar capacidades más avanzadas después de Prime Suspects/Ravenhearst. |

**Prioridad de ejecución acordada:** continuar **Mystery P.I.: The Vegas Heist** antes de iniciar un port de Prime Suspects. Huntsville queda como referencia aprobada y debe conservar su comportamiento. Las otras posiciones son propuestas, no promesas de soporte.

## Catálogo cronológico: 2001–2012

| Año | Juego | Empresa / estudio asociado | Género / interés |
|---|---|---|---|
| 2001 | Bejeweled | PopCap | Puzles clásicos |
| 2003 | Zuma | PopCap | Arcade |
| 2004 | Insaniquarium Deluxe | PopCap | Simulación / arcade |
| 2005 | Mystery Case Files: Huntsville | Big Fish | Crímenes y objetos ocultos |
| 2006 | Mystery Case Files: Prime Suspects | Big Fish | Investigación de robo |
| 2006 | Hidden Expedition: Titanic | Big Fish | Objetos ocultos y exploración |
| 2006 | Mystery Case Files: Ravenhearst | Big Fish | Investigación y puzles |
| 2007 | Peggle | PopCap | Arcade y física |
| 2007 | Azada | Big Fish | Misterio y puzles |
| 2007 | Hidden Expedition: Everest | Big Fish | Exploración |
| 2007 | Mystery P.I.: The Lottery Ticket | PopCap / SpinTop | Investigación privada |
| 2007 | Amazing Adventures: The Lost Tomb | PopCap / SpinTop | Objetos ocultos |
| 2007 | Mystery Case Files: Madame Fate | Big Fish | Investigación de una muerte |
| 2008 | Mystery P.I.: The Vegas Heist | PopCap / SpinTop | Investigación de robo |
| 2008 | Amazing Adventures: Around the World | PopCap / SpinTop | Exploración |
| 2008 | Hidden Expedition: Amazon | Big Fish | Aventura |
| 2008 | Mystery Case Files: Return to Ravenhearst | Big Fish | Misterio y aventura |
| 2008–2009 | Mystery P.I.: The New York Fortune | PopCap / SpinTop | Investigación privada |
| 2009 | Escape Rosecliff Island | PopCap / SpinTop | Escape y objetos ocultos |
| 2009 | Mystery P.I.: Lost in Los Angeles | PopCap / SpinTop | Persona desaparecida |
| 2009 | Drawn: The Painted Tower | Big Fish | Aventura artística |
| 2009 | Plants vs. Zombies | PopCap | Estrategia |
| 2009 | Mystery Case Files: Dire Grove | Big Fish | Desapariciones |
| 2009 | Dark Tales: Edgar Allan Poe's Murders in the Rue Morgue | Big Fish / ERS | Investigación criminal |
| 2010 | Mystery P.I.: The London Caper | PopCap / SpinTop | Investigación |
| 2010 | Escape Whisper Valley | PopCap / SpinTop | Escape y misterio |
| 2010 | Mystery Trackers: The Void | Big Fish / Elephant | Desapariciones |
| 2010 | Mystery Case Files: 13th Skull | Big Fish | Investigación paranormal |
| 2010 | Drawn: Dark Flight | Big Fish | Aventura |
| 2010–2011 | Mystery P.I.: Stolen in San Francisco | PopCap / SpinTop | Robo e investigación |
| 2011 | Mystery Trackers: Raincliff | Big Fish / Elephant | Desapariciones |
| 2011 | Grim Tales: The Bride | Big Fish / Elephant | Misterio familiar |
| 2011 | Mystery P.I.: The Curious Case of Counterfeit Cove | PopCap | Investigación |
| 2011 | Escape the Emerald Star | PopCap / SpinTop | Escape |
| 2011 | Mystery Case Files: Escape from Ravenhearst | Big Fish | Misterio y aventura |
| 2011 | Drawn: Trail of Shadows | Big Fish | Aventura |
| 2012 | Amazing Adventures: Riddle of the Two Knights | PopCap / SpinTop | Objetos ocultos |
| 2012 | Mystery Case Files: Shadow Lake | Big Fish | Investigación paranormal |

## Catálogo cronológico: 2013–2025

| Año | Juego | Saga / publicación | Género / interés |
|---|---|---|---|
| 2013 | Mystery Case Files: Fate's Carnival | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2014 | Mystery Case Files: Dire Grove, Sacred Grove | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2015 | Mystery Case Files: Key to Ravenhearst | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2015 | Mystery Case Files: Ravenhearst Unlocked | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2016 | Mystery Case Files: Broken Hour | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2017 | Mystery Case Files: The Black Veil | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2017 | Mystery Case Files: The Revenant's Hunt | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2018 | Mystery Case Files: Rewind | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2018 | Mystery Case Files: The Countess | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2019 | Mystery Case Files: Moths to a Flame | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2019 | Mystery Case Files: Black Crown | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2020 | Mystery Case Files: The Harbinger | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2020 | Mystery Case Files: Crossfade | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2021 | Mystery Case Files: Incident at Pendle Tower | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2022 | Mystery Case Files: The Last Resort | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2023 | Mystery Case Files: The Dalimar Legacy | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2023 | Mystery Case Files: A Crime in Reflection | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2024 | Mystery Case Files: The Riddle of Mrs. Bishop | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |
| 2025 | Mystery Case Files: House That Love Built | Mystery Case Files / Big Fish y estudios asociados | Misterio, investigación, puzles y objetos ocultos |

## Criterios antes de desbloquear un juego en el HUB

1. **Identidad:** inspección de ejecutable, recursos, edición e integridad; no bastan el título o el nombre del archivo.
2. **Motor:** identificar tecnología y comprobar si hay un runtime compatible; no asumir Director ni SDA por pertenecer a la misma saga.
3. **Extracción:** validar imágenes, fuentes, textos, audio, escenas y dependencias sin distribuir contenido comercial.
4. **Ejecución:** lograr una prueba interactiva real (menú → escena → acierto/error → progresión y guardado), con reglas derivadas del juego original, no escenas estáticas simuladas.
5. **Android:** comprobar entrada, audio, resolución, rendimiento y persistencia en hardware representativo.
6. **Regresión:** asegurar que el nuevo juego no altere Huntsville ni otros títulos compatibles.
7. **Estado de catálogo:** diferenciar `investigación`, `prototipo`, `verificado en dispositivo` y `soporte completo`.

## Límites y verificaciones pendientes

- Esta lista conserva **años y nombres de referencia de la recopilación inicial**. En los títulos con fechas en rango (`2008–2009`, `2010–2011`), y para ediciones/regiones particulares, la fecha exacta sigue pendiente de corroboración documental.
- "PopCap", "Big Fish" y los estudios indicados reflejan vínculos de desarrollo o distribución según cada título, **no necesariamente propiedad del código ni derechos de redistribución**.
- **Huntsville** tiene la base Director/Lingo integrada y un perfil de presentación revisado. Esto no acredita automáticamente otras ediciones de Huntsville.
- **The Vegas Heist** tiene análisis SDA y decompilación estática Ghidra documentados, **pero ningún prototipo SDA jugable en Android confirmado**.
- **Prime Suspects** es un candidato prometedor para reutilizar Director/Flash, pero aún se necesita contrastar el motor y formatos de su copia original.
- Los demás títulos tienen **compatibilidad y motor pendiente de verificar** hasta que haya auditorías por versión.
- No almacenar ejecutables comerciales, recursos, partidas privadas ni dumps de código del juego en el repositorio público.

## Documentación relacionada

- [Investigación de Mystery P.I.](MYSTERY_PI_VEGAS_AUDIT.md)
- [Investigación nativa de Mystery P.I.](MYSTERY_PI_NATIVE_RESEARCH.md)
- [Auditoría de compatibilidad de Huntsville](HUNTSVILLE_COMPATIBILITY_AUDIT.md)
- [Referencia aprobada de Huntsville](HUNTSVILLE_APPROVED_REFERENCE.md)
- [Arquitectura multijuego](MULTIGAME_ARCHITECTURE.md)
- [Perfiles de compatibilidad](GAME_COMPATIBILITY_PROFILE.md)
- [Pruebas de regresión multijuego](MULTIGAME_REGRESSION_PLAN.md)

---

_Este archivo es una lista de candidatos. No afirma jugabilidad, soporte legal de redistribución ni fechas de entrega._
