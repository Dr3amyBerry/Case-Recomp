# Huntsville: reloj y guardado de sesion

Estado: **PENDIENTE_VALIDACION_HUMANA**. Etapa 1; Mystery P.I. sigue pendiente de autorizacion.

El reloj de reproduccion es monotono y excluye el tiempo que la actividad pasa en
segundo plano. El temporizador del caso sigue perteneciendo al juego original.
Salir, pausar la app y el autoguardado cada cinco segundos de reproduccion llaman
los handlers nativos de persistencia de inventario, tablero, pistas y preferencias.
Esos registros y un marcador de reanudacion se escriben en una misma transaccion,
dentro del espacio de guardados del paquete verificado. Un error al guardar con
Salir muestra un aviso y mantiene la actividad abierta para intentar de nuevo.

El marcador identifica usuario, indice y caso, y conserva escena, tiempo restante,
tiempo acumulado y el informe abierto. Al arrancar se comprueba esa identidad antes
de reanudar mapa, escena, puzle o menu. Metadatos corruptos o de otra partida no
seleccionan escenas arbitrarias. Importar una partida por la ruta debug borra el
marcador anterior. La reanudacion espera a que termine el primer exitFrame de cada
span de Score: adelantar la transicion hacia el mapa producia la pantalla negra.

Esto es un checkpoint de la partida nativa, no una imagen completa de la VM como
un savestate de emulador. Animaciones, seleccion de una pieza y dialogos transitorios
se reconstruyen; no se serializan pila de scripts, canales de sonido ni cada frame.
Una terminacion abrupta puede volver al ultimo autoguardado. La reanudacion automatica
se habilita solamente para las huellas Huntsville revisadas del perfil, no por nombre.

El perfil visual revision 5 desplaza cuatro pixeles de escenario a la izquierda
los botones Caso 1..15, sus insignias/relojes/candados y los textos crimeCopy_1..16.
Las areas interactivas de los botones acompanian el desplazamiento. Los ajustes del
nombre, cursor, Cambiar usuario y lista de agentes se conservan.

La prueba del tablero detecto un defecto general del lookup de listas de propiedades:
un nombre consultado como string no encontraba la clave almacenada como simbolo.
El motor normaliza solamente esos nombres de propiedades, sin cambiar la igualdad
entre valores generales ni introducir nombres de juegos en el motor. La regresion
sintetica comprueba lectura, escritura sin duplicados, borrado y claves ausentes.
Referencia de sintaxis: [Director Scripting Reference, getaProp](https://eclass.hmu.gr/modules/document/file.php/TP194/drmx2004_scripting_ref.pdf).

La evidencia privada de WSA y las copias de seguridad se mantienen en
private/huntsville/profile-isolation/2026-10-08/checkpoint-qa/. No se incorporan datos
comerciales ni partidas al repositorio. El archivo heredado no seguido
DirectorAndroidPorts.kt permanece sin cambios y fuera del commit.

## Validacion final (2026-10-08)

- Kotlin: 14 pruebas app y 84 engine, sin errores, fallos ni omisiones locales.
- WSA API 33: 19 pruebas Android pasaron, incluida escritura atomica y rollback.
- Partida privada: salir, forzar cierre, esperar fuera de la app y volver conserva
  escena, inventario y tiempo acumulado; el reloj no vuelve al limite del caso.
- Cinco segundos en segundo plano no reducen el tiempo restante.
- Puzle parcial: un intercambio normal de dos piezas queda identico despues de Salir,
  forzar cierre y reabrir; 761000 ms antes, 759000 ms tras dos segundos activos.
- El arranque restaura la escena visible, sin la pantalla negra de la primera prueba.

Estos resultados no sustituyen la aprobacion visual humana ni prueban un savestate
completo de la VM. No se inicia la Etapa 2.
