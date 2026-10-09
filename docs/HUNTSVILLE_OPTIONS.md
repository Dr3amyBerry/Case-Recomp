# Huntsville: volumen y botón de apoyo

Fecha: 2026-10-08. Estado: implementado y comprobado en WSA con letras Android.

El usuario eligió explícitamente conservar las letras Android tras comparar las dos
builds jugables. Tekton queda como experimento descartado para la versión actual;
no se incorporan fuentes recuperadas al player. La APK de referencia congelada
se conserva y los cambios se prueban en el package experimental Android separado.

## Volumen

El script original de opciones calcula el porcentaje a partir de una posición
sin limitar, actualiza el administrador de sonido y limita el deslizador después.
El canal de audio ya recortaba a 0..255, pero el administrador y el texto podían
quedar negativos. La inicialización usaba además un recorrido mayor que el real,
restando medio ancho del deslizador en vez de su ancho completo.

`HuntsvilleOptions` se activa únicamente con el perfil de Huntsville verificado.
Después del frame y del input, corrige los valores inválidos del administrador,
obtiene el volumen del recorrido limitado durante el arrastre y actualiza el texto.
En reposo posiciona el deslizador según su recorrido real y el volumen guardado.
El audio/valor interno queda en 0..255 y el marcador conserva el formato original
00..99, sin signos negativos. También trata música y efectos sonoros.

No se modifican el motor Director, el perfil de presentación ni los archivos
comerciales. La corrección vive en el adaptador Android del juego.

## Botón

El control «Pantalla completa» y su casilla se cubren con un botón nativo dibujado
en su mismo espacio: **Apoya al desarrollador**. El click se consume en la vista
antes de enviarlo al comportamiento original; por tanto no alterna pantalla completa.
Una liberación dentro del botón abre el navegador con
`https://www.paypal.com/paypalme/dreamypay` mediante ACTION_VIEW. Cancelar o soltar
fuera no ejecuta la acción. No se muestra este reemplazo sobre el panel de ayuda.

## Validación

- 18 tests unitarios Android satisfactorios, incluidos cuatro casos nuevos de
  límites, valores guardados inválidos, geometría y posición restaurada.
- APK normal y copia jugable Android compiladas correctamente.
- WSA: arrastre de ambos deslizadores fuera del recorrido por izquierda/derecha
  produce 0/255 y muestra 00/99. Valores negativos enviados al administrador
  se corrigen a cero en música y efectos.
- WSA: el click emitió ACTION_VIEW hacia PayPal mediante BrowserIntentHandler;
  `gScreen.getFull()` permaneció en 1 antes y después.
- El puente ADB de debug permite `touch|down/move/up/cancel|x|y` para ejercitar
  la ruta real de la vista sin mover el cursor global. Sigue limitado al permiso
  android.permission.DUMP y no se registra en release.
- Las APK congeladas de referencia conservan sus hashes.

La copia jugable actualizada usa
`org.rigorcore.caserecomp.huntsville.androidfonts.debug`, con sus propias preferencias.
Artefactos privados y hashes en `local-output/huntsville-options-2026-10-08/`.
