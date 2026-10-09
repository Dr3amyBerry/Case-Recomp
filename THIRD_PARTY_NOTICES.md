# Case Recomp — Inventario de terceros y avisos de licencia

**2026-10-08 · Inventario inicial, no certificación de cumplimiento.** Identifica dependencias públicas detectadas; los detalles exactos de versiones, módulos transitivos y avisos que deben reproducirse en cada distribución aún requieren SBOM/revisión del artefacto concreto.

| Componente | Cómo se utiliza según el código revisado | Referencia de licencia / tarea |
|---|---|---|
| **Pyodide 0.26.4** | Runtime Python descargado por el navegador mediante jsDelivr en `web/worker.js` | **MPL-2.0** según https://github.com/pyodide/pyodide ; confirmar licencia exacta del release, distribución de dependencias y avisos |
| **Pillow** | Dependencia de imágenes en Pyodide (`loadPackage(["Pillow"])`) y conversiones opcionales de Python | **MIT-CMU** según https://github.com/python-pillow/Pillow/blob/main/LICENSE ; conservar texto y atribución cuando la distribución lo requiera |
| **mutagen** | Dependencia Python opcional (extra `media` / `dev` en `pyproject.toml`; no empaquetada deliberadamente en la APK) | **GPL-2.0-or-later**, según [metadatos oficiales](https://github.com/quodlibet/mutagen/blob/main/pyproject.toml) y [licencia upstream](https://github.com/quodlibet/mutagen/blob/main/COPYING). Si se redistribuye o incorpora, revisar el alcance de las obligaciones GPL según la relación técnica y la forma de distribución; **no se presupone que la GPL cubra por sí sola todo el motor**. |
| **jsDelivr** | CDN que sirve el cargador Pyodide al navegador | Servicio externo, **no procesador de archivos del juego** según flujo revisado. Política: https://www.jsdelivr.com/terms/privacy-policy |
| **GitHub Pages / Actions / Releases** | Hospedaje, builds y APKs | Condiciones y privacidad de GitHub; no constituyen una licencia del juego |
| **Android SDK / AndroidX / Gradle / Kotlin / JUnit** | Dependencias de compilación y pruebas | Inventariar artefactos/versiones y sus licencias en SBOM reproducible; no atribuir licencia única sin comprobación |
| **ProjectorRays** | Herramienta externa de investigación, no incluida como dependencia de runtime en este repo | Documentación previa del proyecto indica MPL-2.0; verificar archivo LICENSE del commit exacto antes de distribuir/copiar derivados |
| **LibreShockwave** | Parser externo usado localmente por `tools/font-experiment/pfr_convert.cpp`; ejecutable de investigación privado. No se enlaza en la APK principal ni en la app de glifos | AGPL-3.0; revisión de investigación `fca530f9ef388d7ff38fa6c7117feae5bb5411c6`. Fuentes upstream conservadas en `private/tools`; parche de investigaci?n AGPL-3.0 y aviso de licencia en `tools/font-experiment` |
| **FontTools** | Extra Python opcional `font-research`; construye la fuente experimental desde contornos privados. No se empaqueta en Android | [MIT, fuente oficial](https://github.com/fonttools/fonttools/blob/main/LICENSE) |

Los juegos, casts, música, sprites, Lingo y portadas **no son dependencias libres ni reciben una licencia por aparecer en esta lista**. No incorporar automáticamente archivos comerciales a paquetes o tests.

## Diferenciar **uso local**, **descarga de CDN** y **redistribución**

- **APK Android:** el código revisado enlaza bibliotecas AndroidX/SDK y código propio; no se ha identificado que empaquete directamente Pyodide, Pillow, mutagen, ProjectorRays o LibreShockwave. Debe verificarse el contenido real del APK firmado antes de anunciar cumplimiento.
- **Convertidor web:** el sitio publica `web/convert.py` y un ZIP del paquete `caserecomp`; `web/worker.js` descarga Pyodide y Pillow desde infraestructura de terceros. No equivale necesariamente a distribuir el código de sus proyectos en GitHub, pero la experiencia del usuario incluye software de terceros y hay que conservar sus avisos según el modo efectivo de entrega.
- **Herramientas Python locales:** instalar opcionalmente mutagen (GPL-2.0-or-later) o ejecutar FFmpeg/ProjectorRays/LibreShockwave por separado no equivale automáticamente a incorporar sus fuentes en el motor. Si la herramienta se integra, incluye, modifica o redistribuye, vuelven a evaluarse sus obligaciones.
- **Dependencias transitivas:** Python, Emscripten, wasm, módulos de Pillow, AndroidX y otros componentes pueden tener avisos adicionales que no se identifican con una única etiqueta de licencia. Verificar el inventario real por versión.
- **Titularidad:** citar una licencia de terceros **no concede derechos** sobre juegos comerciales, ni determina la licencia del código propio.

## Antes de una versión candidata de lanzamiento

1. Producir inventarios/SBOM de **APK release real**, convertidor web (incluyendo Pyodide y paquetes web) y herramientas Python, con versiones y hashes.
2. Obtener textos de licencias desde los artefactos efectivos; adjuntar los avisos exigibles en distribución binaria y en web/Acerca de según cada licencia.
3. Distinguir la mera ejecución externa de herramientas de la incorporación o adaptación real de su código; si existe código copiado/derivado, auditar sus obligaciones específicas.
4. Revisar cambios de Pyodide/CDN, software de FFmpeg opcional, paquetes transitivos y scripts descargados.
5. Conservar evidencia del análisis en un registro público sanitizado, sin subir archivos del juego.

## Fuentes verificadas (2026-10-08)

- Pyodide: [licencia MPL-2.0](https://github.com/pyodide/pyodide) y [texto oficial](https://www.mozilla.org/en-US/MPL/2.0/).
- Pillow: [MIT-CMU, archivo LICENSE upstream](https://github.com/python-pillow/Pillow/blob/main/LICENSE).
- Mutagen: [GPL-2.0-or-later, pyproject](https://github.com/quodlibet/mutagen/blob/main/pyproject.toml) y [COPYING](https://github.com/quodlibet/mutagen/blob/main/COPYING).
- ProjectorRays: [licencia MPL-2.0, proyecto original](https://github.com/ProjectorRays/ProjectorRays).
- LibreShockwave: [licencia AGPL-3.0, proyecto original](https://github.com/LibreShockwave/LibreShockwave).
- Coverage.py (solo desarrollo): [Apache-2.0](https://github.com/coveragepy/coveragepy/blob/main/CITATION.cff).

Las referencias enlazan a los titulares; **no se han importado textos jurídicos externos bajo el nombre de una licencia propia del motor**. Conservar copias de avisos exigibles de las *versiones efectivamente distribuidas* es tarea pendiente.

## Licencia del código original de Case Recomp

[PolyForm Noncommercial License 1.0.0](LICENSE) cubre únicamente las partes del proyecto cuyos respectivos titulares tienen derechos para licenciar. **No** sustituye las licencias MPL, MIT-CMU, GPL, AGPL u otras aplicables a herramientas, bibliotecas y dependencias externas. La descarga y uso gratuito de la APK no incluye derechos sobre recursos comerciales de videojuegos aportados por el usuario. Para el alcance y la reserva de explotación comercial, consulte [LICENSING_STATUS.md](LICENSING_STATUS.md).

## External PFR1 reference and patch licensing

`tools/font-experiment/pfr1-implicit-direction.patch` modifies LibreShockwave's
AGPL-3.0 parser. The patch retains that license, separately from this repository's
license; see `tools/font-experiment/LICENCE-LibreShockwave.txt` and the pinned upstream
revision `fca530f9ef388d7ff38fa6c7117feae5bb5411c6`. The corresponding complete upstream
source remains available from https://github.com/LibreShockwave/LibreShockwave.
The prepared source and linked research binaries remain private; neither parser is
linked into either Android APK.

DirPlayer's GPL-3.0 PFR1 parser at `68376fbb4494a6bbad4c70081ecdcb99814a74c9`
(https://github.com/igorlira/dirplayer-rs) is used as an external local reference via
`pfr_reference.rs`; its implementation is not copied into this repository. Rust
1.90.0 (MIT/Apache-2.0 compiler) was used privately to build that adapter. Matplotlib
(PSF-based license) is an optional local comparison rasterizer; it is not packaged
in Android. FreeType in Pillow rasterizes the resulting OpenType fonts, not PFR1.
