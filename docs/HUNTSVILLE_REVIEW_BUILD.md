# Huntsville Stage 1 review build

Estado: **PENDIENTE_VALIDACION_HUMANA**. Fecha: 2026-10-08.

Build actualizada: nombre y cursor bajados 6 pixeles del escenario para alinearlos con Agente.

Implementacion: [e2c4027](https://github.com/Dr3amyBerry/Case-Recomp/commit/e2c402749f3bdc05ff88e581503da4a6f9f9f946).
La entrega posterior cambia solo documentacion; el codigo de la APK corresponde a este commit.
[Lista exacta de ajustes, identidad y evidencia](HUNTSVILLE_PRESENTATION_ISOLATION.md).

## APK local firmada

Archivo: `local-output/huntsville-profile-review/case-recomp-0.6.0-huntsville-profile-e2c4027-release.apk`.
Version: 0.6.0; versionCode: 8; minSdk: 26; targetSdk: 36.
Application ID: `org.rigorcore.caserecomp.synthetic` (conservado).
Tamano: 903751 bytes.
SHA-256: `30663efe195755c91e32ed1a06d764b1ec2ce2ab791fe6b36f86d92ab7ab88cb`.
Certificado SHA-256: `8be7a724e1feaa07de177433b1cdbde283d28793716aebb0f2b21a5a8061a5d2`.
`apksigner verify` paso y el certificado coincide con la release instalada previamente en WSA.

La APK esta instalada en WSA mediante `adb install -r`; el HUB se abre.
No se publico una release de produccion ni se subieron recursos comerciales.
La unica entrada assets de la APK es `case_recomp_license.txt`; el renderer anterior
congelado y los fixtures sinteticos pertenecen exclusivamente a la APK de pruebas.

APK debug alternativa: `local-output/huntsville-profile-review/case-recomp-0.6.0-huntsville-profile-e2c4027-debug.apk`.
SHA-256: `a5c9953fd0e13147737fd1c30a560f8929c72cb4dea660d567b0c137109d3e6d`; ID con sufijo `.debug`, datos separados de release.
Los artefactos y su JSON de metadatos son locales e ignorados por Git.
El archivo heredado no seguido DirectorAndroidPorts.kt se mantuvo sin cambios;
la APK reproduce ese arbol local documentado, no un checkout limpio.

## Instalacion y revision

Desde la raiz del repo en PowerShell, para actualizar WSA:

```powershell
adb -s 127.0.0.1:58526 install -r .\local-output\huntsville-profile-review\case-recomp-0.6.0-huntsville-profile-e2c4027-release.apk
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
- Kotlin: 11 app + 82 engine, sin fallos ni omisiones locales; incluye arnese privado.
- Android WSA API 33: 18 pruebas pasaron; tres nuevas de presentacion/persistencia.
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
