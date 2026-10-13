# Case Recomp

**ES:** Aplicación Android para jugar a títulos clásicos de *Mystery Case Files* y otros juegos compatibles de PC de Big Fish utilizando tus propios archivos originales. **No incluye ningún juego.**

**EN:** Play supported classic *Mystery Case Files* and Big Fish PC games on Android with your own lawfully usable original game files. **No commercial game assets are included.**

**Accesos rápidos / Quick links:** [Descargar APK / Download](https://github.com/Dr3amyBerry/Case-Recomp/releases/latest) · [Convertidor web / Converter](https://dr3amyberry.github.io/Case-Recomp/) · [Errores / Issues](https://github.com/Dr3amyBerry/Case-Recomp/issues) · [Estado actual / Current status](docs/STATUS_ACTUAL.md) · [Índice / Docs index](docs/INDICE.md) · [Documentación técnica / Developer docs](docs/DEVELOPER.md)

[Español](#español) · [English](#english)

---

## Español

**Case Recomp** permite jugar en Android a juegos de PC de Big Fish usando **tu propia copia original del juego**. La app no incluye ningún juego. Tienes que convertir los archivos de tu juego en un archivo `.zip` e importarlo en la app.

| Juego | Estado |
|---|---|
| Mystery Case Files: **Huntsville** | Jugable |
| Mystery Case Files: **Prime Suspects** | Jugable |
| Mystery Case Files: **Ravenhearst** | Jugable, en revisión (candados y capítulos finales por confirmar) |
| Mystery P.I.: **The Vegas Heist** | **Alpha**: jugable de principio a fin, pero con errores conocidos y detalles por confirmar frente al original |

**Alcance de las pruebas y pendientes por juego:** [estado actual](docs/STATUS_ACTUAL.md). «Jugable» no equivale a fidelidad completa para cualquier edición.

> ⚠️ **Si encuentras un error** (algo que no funciona, se ve o suena distinto al juego original), **escríbenos** abriendo un [issue en GitHub](https://github.com/Dr3amyBerry/Case-Recomp/issues): di qué juego, qué hacías y qué pasó, sin adjuntar archivos originales del juego ni información privada. **Se entrega tal cual, sin garantías de ningún tipo.**

### Avisos legales y protección del usuario

**Proyecto independiente:** Case Recomp no está afiliado, patrocinado ni autorizado por Big Fish Games ni por otros titulares de los juegos citados. Los nombres de juegos identifican compatibilidad; sus marcas, imágenes y contenidos pertenecen a sus respectivos titulares. La app no incluye contenido comercial. **Tener una copia original no autoriza automáticamente cualquier conversión o elusión de protecciones:** consulta las leyes y licencias aplicables y no compartas ZIPs convertidos.

[**Licencia PolyForm Noncommercial**](LICENSE) · [**Aviso legal (Venezuela)**](LEGAL.md) · [**Privacidad**](PRIVACY.md) · [**Términos**](TERMS.md) · [**Derechos y reclamaciones**](COPYRIGHT_POLICY.md) · [**Licencias de terceros**](THIRD_PARTY_NOTICES.md) · [**Estado de licencia del código**](LICENSING_STATUS.md).

**Situación de licencias y compatibilidad en Venezuela:** [evaluación de interoperabilidad](docs/INTEROPERABILITY_VENEZUELA.md) y [estado de licencia del motor](LICENSING_STATUS.md).

**Contacto legal y privacidad:** [contact@rigorcore.com](mailto:contact@rigorcore.com). Usa Issues solo para errores técnicos no sensibles.

**El convertidor procesa el contenido del juego en tu navegador**, pero descarga Pyodide desde una CDN y está alojado en GitHub Pages: consulta la política de privacidad para conocer esos servicios externos. No publiques archivos del juego ni datos personales en issues.

**Permisos de Case Recomp:** puedes descargar, consultar, modificar y compartir gratuitamente el motor y la APK para **fines no comerciales**, de acuerdo con la [licencia PolyForm Noncommercial 1.0.0](LICENSE). **El uso comercial del código original por terceros requiere permiso separado y escrito**. Si solo quieres jugar, descarga la APK gratis y utiliza tus propios archivos del juego de forma lícita; la licencia del motor **no incluye ni autoriza redistribuir el juego**. Para solicitar permisos comerciales: [contact@rigorcore.com](mailto:contact@rigorcore.com). [Detalles](LICENSING_STATUS.md).

### Cómo sacar el archivo .zip de tu juego

Necesitas el juego **ya instalado en tu PC** (Windows). **Ejemplo Huntsville:** es la carpeta que contiene `MysteryCaseFiles.exe` y una carpeta `data` llena de archivos `.cct`. Normalmente está en una ruta como:

- `C:\Program Files (x86)\Big Fish Games\Mystery Case Files Huntsville\`
- o la carpeta que elegiste al instalarlo.

Si no la encuentras: haz clic derecho en el acceso directo del juego → **Abrir ubicación del archivo**.

#### Opción A: con la página web (recomendada)

El convertidor reconoce los cuatro juegos: elige la carpeta de Huntsville, Prime Suspects o Ravenhearst (la que tiene el `.exe` y la carpeta `data`) o la de The Vegas Heist (la que tiene `Resources.dll`). Algunas ediciones de Prime Suspects guardan el juego en un ejecutable protegido que la página no puede leer; la página lo avisa.

1. Abre **[el convertidor de Case Recomp](https://dr3amyberry.github.io/Case-Recomp/)** en Chrome, Edge o Firefox, en el PC.
2. Arrastra la carpeta del juego a la página o pulsa en el recuadro y elígela. Si el navegador pregunta si quieres "subir" los archivos, acepta: en realidad **no se suben a ningún sitio**, la conversión se hace dentro de tu navegador.
3. La página detecta el juego. Cuando salga *Mystery Case Files: Huntsville — Compatible*, pulsa **Crear archivo para la app**. Tarda menos de un minuto.
4. Pulsa **Descargar huntsville.director.zip**.

#### Opción B: con Python (para usuarios avanzados)

Con Python 3.11 o superior y Pillow (`pip install Pillow`), desde una copia de este repositorio:

```
python -m caserecomp director-content "RUTA\DEL\JUEGO\MysteryCaseFiles.exe" --cast-dir "RUTA\DEL\JUEGO\data" --cover newLogo --output "C:\Users\TU_USUARIO\Desktop\huntsville.director.zip"
```

El archivo de salida no puede estar dentro de la carpeta del repositorio.

### Cómo pasarlo a la app

1. Descarga la APK de la app desde [Releases](https://github.com/Dr3amyBerry/Case-Recomp/releases) e instálala en tu Android. Hay tres versiones, que se pueden instalar a la vez (cada una guarda sus propias partidas):
   - **Case Recomp**: la normal, con la mejor imagen.
   - **Case Recomp Lite**: para dispositivos modestos; dibuja los juegos a su resolución original.
   - **Case Recomp Super Lite**: para los más lentos; además no suaviza la imagen y va a 30 fotogramas por segundo.
2. Copia `huntsville.director.zip` al móvil, por ejemplo a la carpeta **Descargas**, con un cable USB, Google Drive o como prefieras.
3. Abre **Case Recomp**, pulsa **Importar** en la tarjeta de Huntsville y elige el `.zip`.
4. Cuando aparezca la carátula, pulsa **Jugar**.

> El `.zip` contiene los datos de tu juego. Es solo para ti: **no lo compartas ni lo subas a internet.**

Los demás juegos se importan igual, cada uno en su propia tarjeta. En Ravenhearst, los candados de las puertas se pueden saltar con el teléfono de ayuda del propio juego y el rompecabezas del diario tiene un botón **Girar pieza**. The Vegas Heist se puede jugar completo, pero **aún faltan cosas por confirmar** frente al juego original: si ves algo raro, escríbenos.

---

## English

**Case Recomp** lets you play Big Fish PC games on Android using **your own original copy of the game**. The app includes no game. You convert your game's files into a `.zip` file and import it into the app.

| Game | Status |
|---|---|
| Mystery Case Files: **Huntsville** | Playable |
| Mystery Case Files: **Prime Suspects** | Playable |
| Mystery Case Files: **Ravenhearst** | Playable, under review (locks and later chapters still being checked) |
| Mystery P.I.: **The Vegas Heist** | **Alpha**: playable start to finish, with known bugs and details still to be confirmed against the original |

**Verification scope and open issues by game:** [current project status](docs/STATUS_ACTUAL.md). “Playable” does not guarantee full fidelity on every PC edition.

> ⚠️ **If you find a bug** (something that does not work, or looks or sounds different from the original game), **write to us** by opening a [GitHub issue](https://github.com/Dr3amyBerry/Case-Recomp/issues): say which game, what you were doing and what happened; do not attach original game assets, proprietary screenshots or private information. **Provided as is, without warranty of any kind.**

### Legal and privacy notices

**Independent project:** Case Recomp is not affiliated with, sponsored or approved by Big Fish Games or other rights holders. Game names identify compatibility; all original trademarks and game content belong to their respective owners. No commercial game is included. **Possessing an original copy does not automatically allow all conversions or circumvention:** follow applicable licence terms and laws. Do not share converted ZIPs.

[**PolyForm Noncommercial licence**](LICENSE) · [**Legal (Venezuela)**](LEGAL.md) · [**Privacy**](PRIVACY.md) · [**Terms**](TERMS.md) · [**Copyright requests**](COPYRIGHT_POLICY.md) · [**Third-party notices**](THIRD_PARTY_NOTICES.md) · [**Code licensing status**](LICENSING_STATUS.md).

**Licensing and Venezuelan interoperability:** [assessment](docs/INTEROPERABILITY_VENEZUELA.md) and [source-code licensing status](LICENSING_STATUS.md).

**Legal and privacy contact:** [contact@rigorcore.com](mailto:contact@rigorcore.com). Use Issues only for non-sensitive technical reports.

The converter processes game content in the browser, but Pyodide is fetched from a CDN and the site is hosted on GitHub Pages. Read the privacy notice for details. Do not post proprietary game files or private data in public issues.

**Case Recomp permissions:** you may download, inspect, modify and share the engine and APK for **noncommercial purposes**, subject to the [PolyForm Noncommercial 1.0.0 licence](LICENSE). **Commercial use of the original project code by third parties requires separate written permission**. Players may download the free APK and play using their own lawfully usable original game files; the motor licence **does not cover the game**. Commercial permission: [contact@rigorcore.com](mailto:contact@rigorcore.com). [Details](LICENSING_STATUS.md).

### How to get the .zip file from your game

You need the game **installed on your PC** (Windows). **Huntsville example:** its folder holds `MysteryCaseFiles.exe` and a `data` folder full of `.cct` files. It is usually at a path like:

- `C:\Program Files (x86)\Big Fish Games\Mystery Case Files Huntsville\`
- or the folder you chose when installing it.

If you cannot find it: right-click the game's shortcut → **Open file location**.

#### Option A: with the web page (recommended)

The converter recognises all four games: choose the Huntsville, Prime Suspects or Ravenhearst folder (the one with the `.exe` and the `data` folder) or The Vegas Heist folder (the one with `Resources.dll`). Some Prime Suspects editions keep the game inside a protected executable the page cannot read; the page says so.

1. Open **[the Case Recomp converter](https://dr3amyberry.github.io/Case-Recomp/)** in Chrome, Edge or Firefox, on your PC.
2. Drop the game folder onto the page, or click the box and choose it. If the browser asks whether to "upload" the files, accept: they are **not sent anywhere**, the conversion runs inside your browser.
3. The page detects the game. When it shows *Mystery Case Files: Huntsville — Supported*, press **Create file for the app**. It takes under a minute.
4. Press **Download huntsville.director.zip**.

#### Option B: with Python (advanced users)

With Python 3.11 or newer and Pillow (`pip install Pillow`), from a copy of this repository:

```
python -m caserecomp director-content "PATH\TO\GAME\MysteryCaseFiles.exe" --cast-dir "PATH\TO\GAME\data" --cover newLogo --output "C:\Users\YOUR_USER\Desktop\huntsville.director.zip"
```

The output file cannot be inside the repository folder.

### How to load it into the app

1. Download the app's APK from [Releases](https://github.com/Dr3amyBerry/Case-Recomp/releases) and install it on your Android. There are three versions, which can be installed side by side (each keeps its own saves):
   - **Case Recomp**: the standard one, best picture.
   - **Case Recomp Lite**: for modest devices; draws the games at their original resolution.
   - **Case Recomp Super Lite**: for the slowest; also unsmoothed scaling and 30 frames per second.
2. Copy `huntsville.director.zip` to your phone, e.g. into **Downloads**, by USB cable, Google Drive or however you like.
3. Open **Case Recomp**, tap **Importar** (Import) on the Huntsville card and choose the `.zip`.
4. When the cover appears, tap **Jugar** (Play).

> The `.zip` holds your game's data. It is for you only: **do not share it or upload it anywhere.**

The other games are imported the same way, each on its own card. In Ravenhearst, door locks can be skipped with the game's own help-line phone, and the diary jigsaw has a **Girar pieza** (rotate piece) button. The Vegas Heist can be played through, but **some things are still to be confirmed** against the original game: if anything looks wrong, write to us.

---

Developer documentation / Documentación para desarrolladores: [docs/DEVELOPER.md](docs/DEVELOPER.md).

Contribuciones y protección de derechos / Contributions and rights: [CONTRIBUTING.md](CONTRIBUTING.md).

### Local files by game

Keep each game and its derived files in its own git-ignored directory:

```text
private/
  huntsville/
    game/                      # original Huntsville installation
    huntsville-decompiled/      # recovered Huntsville scripts and casts
    huntsville-first-playable/  # scene captures and trials
    huntsville-m3/              # VM bundles, packages and local sessions
    case-recomp-phase6/         # navigation captures and evidence
  mystery-pi-vegas/
    game/                      # original The Vegas Heist installation
  prime-suspect/
    game/                      # personal Prime Suspects installation (example)
  ravenhearst/
    game/                      # personal Ravenhearst installation (example)
  tools/                       # shared reverse engineering tools
  signing/                     # local Android signing material
```

New decompilations, exports and captures belong under the corresponding game
folder. Huntsville outputs are specific to Huntsville; they are not Mystery P.I.
outputs. Historical local manifests and capture logs retain their recorded paths;
`private/relocation-2026-10-08.json` maps the previous locations to the new ones.

### Multigame architecture / Arquitectura multijuego

[Architecture and incremental plan](docs/MULTIGAME_ARCHITECTURE.md) · [Huntsville audit](docs/HUNTSVILLE_COMPATIBILITY_AUDIT.md) · [Versioned compatibility profiles](docs/GAME_COMPATIBILITY_PROFILE.md) · [Mystery P.I. technical audit](docs/MYSTERY_PI_VEGAS_AUDIT.md) · [Regression and acceptance plan](docs/MULTIGAME_REGRESSION_PLAN.md).

These documents include historical proposals and audits. The implemented SDA runtime and current four-game catalogue are described in [project status](docs/STATUS_ACTUAL.md).
