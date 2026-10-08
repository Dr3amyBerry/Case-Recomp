# Case Recomp — Política de privacidad / Privacy notice

**Revisión / Revised:** 2026-10-08 · **Alcance:** web de conversión alojada en GitHub Pages, repositorio público y APK beta de Case Recomp.

## Español

### 1. Responsable y contacto

El proyecto se mantiene desde https://github.com/Dr3amyBerry/Case-Recomp . **Correo para privacidad y solicitudes de datos: [contact@rigorcore.com](mailto:contact@rigorcore.com)**, proporcionado por el mantenedor. Su recepción aún no se ha comprobado y la identidad jurídica y domicilio del responsable siguen pendientes de publicación. Para fallos generales: https://github.com/Dr3amyBerry/Case-Recomp/issues; **no publique datos sensibles en issues públicos**. Antes de distribuir ampliamente la aplicación, se debe habilitar y anunciar un canal privado verificable.

### 2. Qué sucede con los archivos del juego

- **Convertidor web:** según el código público revisado, los archivos que seleccione se leen en el navegador, se copian al sistema de archivos de memoria de Pyodide y se transforman allí. El ZIP resultante se entrega como descarga local; **no existe en el código revisado una subida de los archivos del juego a un servidor de conversión propio**.
- **App Android:** el usuario selecciona un ZIP mediante el selector de documentos. El archivo importado, la portada y los datos de partida se guardan en almacenamiento privado de la app; no se incluye el juego en el APK.
- **No publicación:** NO comparta archivos del juego, carpetas exportadas ni capturas privadas mediante issues, solicitudes de asistencia o plataformas públicas.
- **Eliminación:** al desinstalar o borrar datos de la app, Android normalmente elimina los datos privados de esa aplicación; copias que el usuario conserve fuera de la app (ZIP descargado, backups) se gestionan por separado. Para el convertidor, cierre la página y elimine el ZIP de Descargas si ya no lo necesita. El navegador, sus extensiones y el sistema operativo pueden mantener cachés o trazas fuera del control del proyecto.

### 3. Datos y almacenamiento identificados

En el código examinado **no se identificaron** cuentas, inicio de sesión, SDK de anuncios ni telemetría del juego, y el manifiesto de la app no declara permisos de Internet. Sí existen almacenamiento local de importaciones/partidas y una preferencia de idioma del sitio en `localStorage`. Estas observaciones se limitan a la revisión del código de la versión indicada y no garantizan que un servicio externo no trate metadatos de conexión.

El sitio obtiene Pyodide desde **jsDelivr** y se hospeda en **GitHub Pages**. Esos servicios (y el navegador/red) pueden procesar dirección IP, solicitud HTTP, fecha/hora y datos de seguridad conforme a sus propias políticas, **aunque los archivos del juego no se suban al conversor**. Consulte: https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement y https://www.jsdelivr.com/terms/privacy-policy .

Los reportes públicos, issues, estrellas y visitas al repositorio se procesan por GitHub conforme a sus reglas; cualquier dato que decida publicar será visible según la configuración de GitHub.

### 4. Finalidad, fundamento y conservación

Finalidades identificadas: conversión local solicitada; apertura de ZIPs del usuario y ejecución del juego; recuperación de partidas local; preferencia de idioma y soporte voluntario en GitHub. **No se declara una base legal global ni un plazo de conservación en servidores propios sin haberlos verificado.** Los ZIPs, partidas y preferencias se conservan localmente hasta que el usuario los borra, limpia los datos o desinstala, sujeto a copias externas del propio usuario y al comportamiento del sistema.

### 5. Derechos, menores y cambios

Si desea acceder, corregir o eliminar datos procesados por servicios externos, consulte los canales del proveedor correspondiente. Para datos exclusivos de la app, puede borrar sus datos o desinstalarla. Para solicitudes privadas al mantenedor no resueltas en el dispositivo, escriba a [contact@rigorcore.com](mailto:contact@rigorcore.com); confirme la recepción. No se ha verificado un servicio dirigido específicamente a menores ni un proceso de verificación de edad: **no se debe anunciar cumplimiento infantil o clasificación de edad sin evaluación adicional**.

Si el proyecto incorpora analytics, anuncios, crash reporting, cuentas, subida de archivos o nuevos terceros, deberá actualizar este aviso **antes** de activar esas funciones y analizar bases legales, permisos y reglas de los destinos de distribución.

## English

### 1. Publisher and contact

Case Recomp is maintained via https://github.com/Dr3amyBerry/Case-Recomp . **Privacy and data requests: [contact@rigorcore.com](mailto:contact@rigorcore.com)**, supplied by the maintainer but not independently delivery-tested. The controller's legal identity and address remain unconfirmed. General, non-sensitive bugs may be reported at https://github.com/Dr3amyBerry/Case-Recomp/issues . **Never post personal data, proprietary game files or confidential information in public issues.** A functioning private contact route is required before broader distribution.

### 2. Local files and game data

According to the inspected public source, the browser converter reads user-selected game files **inside the browser** using an in-memory Pyodide file system; the resulting package is downloaded locally. No upload to a developer-operated conversion server was found in the inspected workflow. Android imports a user-selected ZIP into app-private storage and stores local save data and an imported cover image. The app does not package the commercial game.

Uninstalling or clearing app data normally removes app-private content; copies held elsewhere are managed separately by the user. Browser caches, extensions and backups may behave differently.

### 3. Third parties and technical data

No account system, advertising SDK, in-game analytics or Android INTERNET permission was identified in the inspected version. The site stores a language preference in browser `localStorage`. **GitHub Pages** hosts the site and **jsDelivr** supplies the Pyodide loader; they may process IP addresses, request logs and security metadata under their own policies even though game file contents are not uploaded to a developer server. GitHub issues and releases are separately covered by GitHub's privacy notice.

Links: https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement ; https://www.jsdelivr.com/terms/privacy-policy

### 4. Purpose, retention, rights and updates

Local conversion, import/playback, saves, language preference and voluntary support requests are the identified purposes. Imported data remains locally until removed by the user or app/system cleanup; this is not a promise about external backups or provider retention. No general legal basis or server-side retention period is asserted without evidence.

Users may delete local app data or uninstall. For service-provider requests use the corresponding provider's privacy tools. For private maintainer requests, use [contact@rigorcore.com](mailto:contact@rigorcore.com); delivery and response still require verification. Any future accounts, analytics, uploads, advertising or cloud synchronisation require updated disclosures and legal review before launch.

**Scope limitation:** this notice is based on repository code as of 2026-10-08, not an independent network forensic audit of all browsers, devices or hosting services.
