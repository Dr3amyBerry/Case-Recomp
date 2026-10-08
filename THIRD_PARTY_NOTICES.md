# Case Recomp — Inventario de terceros y avisos de licencia

**2026-10-08 · Inventario inicial, no certificación de cumplimiento.** Identifica dependencias públicas detectadas; los detalles exactos de versiones, módulos transitivos y avisos que deben reproducirse en cada distribución aún requieren SBOM/revisión del artefacto concreto.

| Componente | Cómo se utiliza según el código revisado | Referencia de licencia / tarea |
|---|---|---|
| **Pyodide 0.26.4** | Runtime Python descargado por el navegador mediante jsDelivr en `web/worker.js` | **MPL-2.0** según https://github.com/pyodide/pyodide ; confirmar licencia exacta del release, distribución de dependencias y avisos |
| **Pillow** | Dependencia de imágenes en Pyodide (`loadPackage(["Pillow"])`) y conversiones opcionales de Python | **MIT-CMU** según https://github.com/python-pillow/Pillow/blob/main/LICENSE ; conservar texto y atribución cuando la distribución lo requiera |
| **mutagen** | Decodificación/metadatos de audio opcionales en `pyproject.toml` | Confirmar versión/archivo LICENSE del paquete efectivamente resuelto y sus avisos |
| **jsDelivr** | CDN que sirve el cargador Pyodide al navegador | Servicio externo, **no procesador de archivos del juego** según flujo revisado. Política: https://www.jsdelivr.com/terms/privacy-policy |
| **GitHub Pages / Actions / Releases** | Hospedaje, builds y APKs | Condiciones y privacidad de GitHub; no constituyen una licencia del juego |
| **Android SDK / AndroidX / Gradle / Kotlin / JUnit** | Dependencias de compilación y pruebas | Inventariar artefactos/versiones y sus licencias en SBOM reproducible; no atribuir licencia única sin comprobación |
| **ProjectorRays** | Herramienta externa de investigación, no incluida como dependencia de runtime en este repo | Documentación previa del proyecto indica MPL-2.0; verificar archivo LICENSE del commit exacto antes de distribuir/copiar derivados |
| **LibreShockwave** | Herramienta externa de referencia, no incluida en APK/conversor | Documentación previa indica AGPL-3.0; verificar LICENSE y si hubo incorporación de código a Case Recomp |

Los juegos, casts, música, sprites, Lingo y portadas **no son dependencias libres ni reciben una licencia por aparecer en esta lista**. No incorporar automáticamente archivos comerciales a paquetes o tests.

## Antes de una versión candidata de lanzamiento

1. Producir inventarios/SBOM de **APK release real**, convertidor web (incluyendo Pyodide y paquetes web) y herramientas Python, con versiones y hashes.
2. Obtener textos de licencias desde los artefactos efectivos; adjuntar los avisos exigibles en distribución binaria y en web/Acerca de según cada licencia.
3. Distinguir la mera ejecución externa de herramientas de la incorporación o adaptación real de su código; si existe código copiado/derivado, auditar sus obligaciones específicas.
4. Revisar cambios de Pyodide/CDN, software de FFmpeg opcional, paquetes transitivos y scripts descargados.
5. Conservar evidencia del análisis en un registro público sanitizado, sin subir archivos del juego.
