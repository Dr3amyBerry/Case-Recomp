# Huntsville: cierre de la Etapa 1

Estado: **APROBADO_HUMANAMENTE**. Fecha: 2026-10-08, America/Caracas.

El usuario aprobo el estado revisado y autorizo explicitamente la investigacion de
Mystery P.I. en su instruccion ?Paso 1 ? Cerrar formalmente Huntsville? seguida de
?Paso 2 ? Decompilar Mystery P.I. con Ghidra?. Esta aprobacion cierra la etapa de
Huntsville; no acredita compatibilidad de Mystery P.I. ni una campa?a completa probada.

Referencia de codigo: `0cb7683e680139a03500fcf3860a14859880ba8b`. Documentacion aprobada: `72c997b`.
Perfil Huntsville revision 6; version APK 0.6.0, versionCode 8.

| Artefacto aprobado | SHA-256 |
|---|---|
| Release | `d445bc57ad12944de8e254a79ea293523771ee8ca8d35c127ec44bb71eb447da` |
| Debug usada en WSA | `3d48bc72842e182fbbf78e284e4278b2a0a54fc1d661480aadb8b0b840765b37` |

Copias preservadas localmente en `local-output/huntsville-approved-2026-10-08/`.
`approved-reference.json` conserva rutas, tama?os, identidad, firma y aprobacion.
Los originales, APKs, screenshots y saves siguen privados y fuera de Git.
El certificado release es `8be7a724e1feaa07de177433b1cdbde283d28793716aebb0f2b21a5a8061a5d2`.
El archivo heredado no seguido DirectorAndroidPorts.kt forma parte del arbol local
con el que se compilo la referencia; no se agrego ni modifico en estos commits.

La referencia incluye alineacion de nombre/cursor/lista, botones y texto de casos,
limite de tiempo, reloj pausado fuera de la actividad y checkpoint nativo al salir.
Las pruebas registradas y sus limitaciones estan en
[revision de build](HUNTSVILLE_REVIEW_BUILD.md) y
[checkpoint](HUNTSVILLE_SESSION_CHECKPOINT.md).

El perfil se selecciona por pares exactos de huellas de movie.json y lingo.json.
Una nueva conversion puede necesitar registrar un par verificado para conservar
los ajustes. No se amplia el reconocimiento por nombre ni se migran saves entre
ZIPs automaticamente. Esto no bloquea la investigacion autorizada de Mystery P.I.

Las secciones historicas con PENDIENTE_VALIDACION_HUMANA reflejan el estado anterior
a esta aprobacion. Para futuras regresiones, conservar la APK y este commit, comparar
la misma instalacion privada, partida y secuencia de entrada y consultar
[MULTIGAME_REGRESSION_PLAN.md](MULTIGAME_REGRESSION_PLAN.md).
