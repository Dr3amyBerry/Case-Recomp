# Huntsville Stage 1 review build

Estado: **PENDIENTE_VALIDACION_HUMANA**. Fecha: 2026-10-08.

Build actualizada: cursor separado del nombre y subido 3 pixeles respecto a la build anterior,
Cambiar usuario 6 pixeles a la derecha
y filas de agentes 6 pixeles mas abajo; el area de clic del boton acompana su dibujo.
El texto de minutos se baja 5 pixeles para compartir linea con Limite de tiempo,
verificado visualmente en WSA con el informe del caso 2. Las 14 pruebas app pasan.

Ademas, informe y botones de casos cuatro pixeles a la izquierda, reloj conservado
y reanudacion de la partida al salir, con la pantalla negra corregida.
[Alcance y pruebas del checkpoint](HUNTSVILLE_SESSION_CHECKPOINT.md).

Implementacion: [0cb7683](https://github.com/Dr3amyBerry/Case-Recomp/commit/0cb7683e680139a03500fcf3860a14859880ba8b).
La entrega posterior cambia solo documentacion; el codigo de la APK corresponde a este commit.
[Lista exacta de ajustes, identidad y evidencia](HUNTSVILLE_PRESENTATION_ISOLATION.md).

## APK local firmada

Archivo: `local-output/huntsville-profile-review/case-recomp-0.6.0-huntsville-profile-0cb7683-release.apk`.
Version: 0.6.0; versionCode: 8; minSdk: 26; targetSdk: 36.
Application ID: `org.rigorcore.caserecomp.synthetic` (conservado).
Tamano: 911791 bytes.
SHA-256: `d445bc57ad12944de8e254a79ea293523771ee8ca8d35c127ec44bb71eb447da`.
Certificado SHA-256: `8be7a724e1feaa07de177433b1cdbde283d28793716aebb0f2b21a5a8061a5d2`.
`apksigner verify` paso y el certificado coincide con la release instalada previamente en WSA.

La APK esta instalada en WSA mediante `adb install -r`; el HUB se abre.
No se publico una release de produccion ni se subieron recursos comerciales.
La unica entrada assets de la APK es `case_recomp_license.txt`; el renderer anterior
congelado y los fixtures sinteticos pertenecen exclusivamente a la APK de pruebas.

APK debug alternativa: `local-output/huntsville-profile-review/case-recomp-0.6.0-huntsville-profile-0cb7683-debug.apk`.
SHA-256: `3d48bc72842e182fbbf78e284e4278b2a0a54fc1d661480aadb8b0b840765b37`; ID con sufijo `.debug`, datos separados de release.
Los artefactos y su JSON de metadatos son locales e ignorados por Git.
El archivo heredado no seguido DirectorAndroidPorts.kt se mantuvo sin cambios;
la APK reproduce ese arbol local documentado, no un checkout limpio.

## Instalacion y revision

Desde la raiz del repo en PowerShell, para actualizar WSA:

```powershell
adb -s 127.0.0.1:58526 install -r .\local-output\huntsville-profile-review\case-recomp-0.6.0-huntsville-profile-0cb7683-release.apk
adb -s 127.0.0.1:58526 shell am start -n org.rigorcore.caserecomp.synthetic/org.rigorcore.caserecomp.app.HomeActivity
```

Para otro dispositivo, seleccionar su serial con `adb devices` y sustituir el de WSA.
Actualizar sobre la aplicacion existente, conservando sus datos; no hace falta reimportar
los paquetes reconocidos. Verificar el hash con `Get-FileHash <ruta-apk> -Algorithm SHA256`.
Si se usa un dispositivo nuevo, pulsar Importar en la tarjeta Huntsville y seleccionar
el ZIP Director privado por el flujo habitual. Los archivos del juego no van en la APK.

1. Abrir Case Recomp, revisar las tarjetas y pulsar Jugar en Huntsville.
2. Comprobar pantalla inicial, nombre/seleccion de usuario y menu principal.
3. Abrir el mapa y una escena. Revisar textos Tekton/Palatino/Times, interlineado,
   baselines, margenes, posiciones y el panel de objetos/Elementos necesarios.
4. Probar pistas, temporizador, dialogos, puzles, botones, cambio de escena y vuelta al menu.
5. Cargar una partida anterior, avanzar/guardar, cerrar y abrir para comprobar continuidad.
6. Comparar con tu referencia anterior y comunicar cualquier diferencia con pantalla y pasos.
7. Autorizar explicitamente la Etapa 2 solo cuando aceptes esta build.

## Resultados comprobados

- Python: 276 pruebas, 2 omitidas por plataforma; baseline y candidata verdes.
- Kotlin: 14 app + 84 engine, sin fallos ni omisiones locales; incluye arnese privado.
- Android WSA API 33: 19 pruebas pasaron, incluida persistencia atomica y rollback.
- Comparacion Android: 1.152 combinaciones sinteticas con pixeles/dimensiones identicos
  al rasterizador anterior para Huntsville; metricas de ancho e interlineado iguales.
- Las mismas fuentes en contenido desconocido usan parametros genericos.
- Huellas parciales/metadatos falsificados no seleccionan el perfil; JSON manipulado
  no elige perfil ni introduce offsets/codigo sin revision.
- Tres ZIP privados existentes reconocidos, verificados y sin reescritura/reimportacion.
- Nueve capturas deterministas JVM identicas; estas no prueban fidelidad Android.
- WSA privado: menu, mapa y escena funcionan; caption estatico identico y todas las
  93 filas de sprites de la escena conservadas. Capturas completas tienen animacion.
- Copia original debug restaurada: sus 11 archivos verificados exactamente por SHA-256;
  paquete activo y partidas originales conservados. Release se actualizo con `-r`.
- APK debug/release y APK de instrumentacion compiladas; firma release verificada.

## Pendiente

La aprobacion visual humana, dialogos/puzles completos, campana completa, comparacion
con el juego nativo, otros Android y ediciones no registradas siguen pendientes.
Un paquete cuya huella no este revisada conserva importacion/guardados y usa texto
generico con aviso; requiere revision privada para agregar su huella al registro.
Las limitaciones previas de fuentes embebidas, texto multirun, Flash/Xtras, audio,
memoria y seleccion del paquete activo en el HUB permanecen documentadas.

El punto de control procede de la directiva del usuario: "DETEN COMPLETAMENTE EL
TRABAJO AL TERMINAR LA ETAPA 1". No se ha iniciado la Etapa 2 durante este trabajo.

## Visible bot run (2026-10-08)

The existing private wsa_autoplay.py bot completed the current case 1 on WSA.
It resumed the existing Robot save rather than starting a new inventory. Result:
1 remaining object tapped, puzzle solved after 37 swaps, final label map, exit 0.
Runtime duration: 221 seconds. All game actions were normal ADB touch input from
the bot; the assistant observed state without clicking through the level.

Two private recordings cover the run:
private/huntsville/profile-isolation/2026-10-08/bot-demo/case-bot-demo-01.mp4
and case-bot-demo-02.mp4 in the same directory. Logs, result.json and completion
screenshots are beside them. Recordings and game content are not uploaded to Git.
The run continues the DEBUG save, whose original files/preferences backup is kept
in profile-isolation/2026-10-08/wsa-debug-data-before.tar. Demo progress is left
available in debug; release saves are separate and unaffected by bot progression.
APK release was updated with install -r afterward, without changing the foreground
debug game. Visual acceptance and Stage 2 authorization remain pending.
