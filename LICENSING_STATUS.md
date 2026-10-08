# Case Recomp — Estado de licencia del código (pendiente de decisión)

**Fecha:** 2026-10-08.

**Estado comprobado:** el repositorio no tiene aún una licencia general `LICENSE`. Este documento **no concede, modifica ni revoca** licencias de copyright. Mientras no exista autorización suficiente y una licencia publicada, rigen las leyes aplicables y los derechos de cada autor/contribuyente, además de los permisos limitados que pudieran resultar de las condiciones de GitHub.

Referencia oficial: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository

**No elegir MIT, Apache 2.0, GPL u otra licencia automáticamente:** la decisión requiere confirmar quién ostenta los derechos del código generado, incorporado y escrito por colaboradores, comprobar atribuciones, auditar dependencias y fijar los objetivos reales de redistribución. Una licencia de código del motor **no** puede licenciar contenido de Big Fish ni marcas ajenas.

## Pasos pendientes de aprobación expresa

1. Identificar al titular o titulares del código original y acordar su consentimiento cuando corresponda.
2. Auditar orígenes y uso de código de herramientas/terceros, incluyendo cualquier fragmento derivado de ProjectorRays o LibreShockwave; confirmar las obligaciones de reciprocidad/atribución aplicables.
3. Decidir si el motor será de código abierto y qué usos permitir; comparar licencias concretas con asesoría.
4. Añadir la licencia elegida, avisos de copyright verificados y, si procede, política de contribuciones; revisar APK, web y documentación.
5. Separar expresamente la licencia del motor de los derechos sobre archivos que convierta cada usuario.

**Aviso para visitantes:** la posibilidad de ver el código en GitHub no implica autorización general para copiarlo, modificarlo o distribuirlo fuera de los permisos aplicables.

## Alternativas para una licencia del motor (pendiente de autorización del titular)

| Elección | Efecto principal | Consideración |
|---|---|---|
| **Apache-2.0** | Autoriza usos y redistribución, incluidos comerciales, con avisos y concesión de patentes limitada a contribuciones | Licencia permisiva; las concesiones pueden ser irrevocables en los términos legales ([ASF](https://www.apache.org/licenses/LICENSE-2.0.html)) |
| **MIT** | Permite uso, modificación y redistribución incluso comercial, manteniendo aviso de copyright y licencia | Permisiva; no impide que terceros publiquen derivados cerrados |
| **GPL-3.0-or-later** | Impone obligaciones de código fuente y reciprocidad al distribuir derivados cubiertos | Revisar compatibilidad con el código y dependencias existentes |
| **Sin licencia pública** | Los terceros no reciben un permiso general para reutilizar el código | No impide que el repositorio sea público; no convierte al motor en software libre |

**Decisión aún no recibida:** elegir una licencia puede otorgar derechos de reproducción, modificación y distribución a terceros, en algunos casos de forma irrevocable. La solicitud general de «añadir las licencias» se entiende como autorización para **documentar y cumplir licencias ajenas**, no como selección informada de un régimen específico para el motor. Cuando el titular confirme **qué libertades desea conceder y su derecho a hacerlo**, se podrá añadir el texto oficial íntegro a `LICENSE` y los avisos correspondientes. No inventar titulares, cesiones ni acuerdos de contribución.
