# Mystery P.I. Vegas: investigación de código nativo

Fecha: 2026-10-08. Etapa 2 autorizada después del
[cierre humano de Huntsville](HUNTSVILLE_APPROVED_REFERENCE.md).
Estado: **INVESTIGACIÓN NATIVA; JUGABILIDAD TODAVÍA NO VERIFICADA**.

## Trabajo ejecutado

Se importó MysteryPIVegas.exe en Ghidra 12.1.4 como PE x86, se completó el
análisis automático y se conservaron proyecto, referencias, grafo de llamadas y
pseudocódigo dentro de private/mystery-pi-vegas/research/. No se volvió a ejecutar
el inventario anterior de recursos. El SHA-256 del ejecutable se comprobó contra
la auditoría existente; los originales permanecen sin cambios.

Las herramientas originales del proyecto están en tools/ghidra/ExportNativeSlice.java.
Ghidra procede de su [release oficial](https://github.com/NationalSecurityAgency/ghidra/releases/tag/Ghidra_12.1.4_build);
el ZIP se verificó contra el SHA-256 publicado por GitHub. El JDK local satisface
los requisitos de la [distribución oficial](https://github.com/NationalSecurityAgency/ghidra).
Los dumps de código, nombres de recursos, coordenadas y partidas no se publican.

## Hallazgos del código, no sólo del inventario

| Área | Evidencia estática revisada | Lo que aún requiere comprobación |
|---|---|---|
| Arranque y recursos | El arranque registra Resources.dll. La capa de streams usa LoadLibraryA y, para la fuente PE, FindResourceA, SizeofResource, LoadResource y LockResource sobre RAWDATA. | Errores de carga, orden real y lifetimes en ejecución. |
| Entrada XUI | Carga bytes en memoria, configura callbacks de apertura/cierre y alimenta un parser. Otro helper separa explícitamente prefijo y nombre local por dos puntos. | Compatibilidad exacta de codificación, entidades, errores y todos los dialectos. |
| Creación de nodos | Hay registro/búsqueda de fábricas y creación por llamadas virtuales; callbacks mantienen contexto de nodos y atributos. Los componentes del juego tienen loaders nativos propios. | Cierre del conjunto de clases y targets indirectos necesarios para menú y escena. |
| Traversal y eventos | Un helper transforma traverse/update/render/events en una máscara. El bucle del juego obtiene eventos a través de una interfaz de driver y contiene decisiones nativas. | Orden, consumo/bubbling, coordenadas, captura y efectos de hit/miss. |
| Botones | El loader consume textura, fuente y audio según estados normal/hover/pushed/disabled, además de caption y offsets. | Hit-testing, transición entre estados y callbacks de acción. |
| Fuentes | El código consume atlas, baseline, spacing, spacewidth y characterset; otro loader registra pares de caracteres y spacing de kerning. | Métricas completas, Unicode/localización, alpha y comparación pixel a pixel. |
| Escenas y acciones | Loaders nativos resuelven referencias a texturas, fuentes, capas y acciones. El XUI configura componentes cuya implementación permanece en x86. | Reglas de selección, pistas, penalizaciones, victoria y guardado. |
| Animación | Se localizaron atributos y referencias de animaciones y sus componentes. | No se ha reconstruido ni verificado toda la interpolación, timing o finalización. |

Los nombres de roles son asignaciones del analista, no símbolos originales recuperados.
La ausencia de PDB y los tipos/prototipos inferidos impiden tratar el pseudocódigo
como fuente compilable. Los recorridos de punteros virtuales pueden cruzar límites
de tablas contiguas; se usan para encontrar candidatos, no como ABI demostrado.
Las direcciones y funciones que sustentan cada fila constan en native-review-notes.json
privado, junto a las referencias y el código decompilado correspondiente.

## Decisión para la primera prueba jugable

Se descarta una reproducción que sólo dibuje XUI e invente reglas de objetos.
La evidencia muestra dependencias de fábricas, callbacks y reglas nativas. Un
intérprete compatible debe cubrir esas dependencias; traducción nativa o emulación
x86 deben preservar ABI, servicios y estado del código original. Todavía no hay
mediciones ni cierre de dependencias que permitan declarar viable una de ellas.

La elección definitiva y el slice jugable quedan pendientes. No se añadió un
runtime SDA vacío, una escena simulada ni compatibilidad nueva al launcher Android.
El contrato de prueba exige menú → escena, acierto/fallo, pista, animación, audio,
pausa, guardado/reapertura y salida usando las reglas recuperadas. Primero se
contrasta en Windows aislado; después se conecta a los servicios Android existentes.

## Contraste con el original y control de ventana

El usuario sustituyo expresamente la peticion de aislamiento por ejecutar el juego
normalmente en Windows. No se habilito Windows Sandbox. Se lanzo el original desde
su carpeta, y se comprobaron el menu y la carga real de Resources.dll y bass.dll
en el proceso x86 con Frida. La referencia no se presenta como ejecucion aislada.

La decompilacion completa exporto 5.906 de 5.906 funciones reconocidas por Ghidra;
ademas se revisaron tres cortes dirigidos de 600 funciones, 1.402 unicas. Esto no
acredita todos los targets indirectos, codigo no detectado o logica ya reconstruida.

El usuario pidio poder seguir trabajando mientras se maneja el juego en ventana.
La herramienta tools/native-reference/background_window.py selecciona una unica
ventana visible por PID, captura su cliente con PrintWindow y envia WM_MOUSEMOVE,
WM_LBUTTONDOWN y WM_LBUTTONUP mediante PostMessage. No llama a SetForegroundWindow,
SetCursorPos, SendInput o a teclas globales. Rechaza ventanas minimizadas, multiples
ventanas ambiguas y coordenadas fuera del cliente. Registra foco y cursor antes y
despues; el usuario conserva el control del escritorio.

La captura y un clic reversible en Cambiar jugador funcionaron en el original;
el cursor y la ventana activa se mantuvieron iguales. Esa prueba se hizo mientras
el juego tenia el foco. Sigue pendiente comprobar la entrega/consumo de eventos
cuando otra aplicacion tiene el foco y si el driver pausa al desactivar la ventana.
No se confunde PostMessage aceptado con accion de juego demostrada.

Las capturas privadas estan en native-reference/. La instrumentacion Frida solo
se uso para comprobar modulos; no hay aun traza completa de callbacks, hit/miss,
animacion, guardado o cierre de una escena. El original arrancado no es el prototipo
reconstruido de SDA. Los pasos 3 y 4 permanecen pendientes de completar.
