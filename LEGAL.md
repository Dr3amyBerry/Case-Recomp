# Case Recomp — Aviso legal y evaluación de riesgos (Venezuela)

**Versión:** 2026-10-08 · **Estado:** documentación pública para la beta, no certificación jurídica ni autorización de titulares.

## Identidad y alcance

Case Recomp es un proyecto independiente de **compatibilidad de formatos y ejecución**, alojado en https://github.com/Dr3amyBerry/Case-Recomp, mantenido bajo la cuenta pública del repositorio. La identidad jurídica del titular del proyecto y el domicilio legal siguen pendientes de declarar. **Contacto designado para asuntos legales y privacidad: [contact@rigorcore.com](mailto:contact@rigorcore.com)**. El mantenedor proporcionó esta dirección; su recepción efectiva aún no se ha verificado.

**No afiliación.** Case Recomp NO está afiliado, patrocinado, autorizado ni respaldado por Big Fish Games, BFG Entertainment u otros titulares de los juegos. Los nombres *Mystery Case Files*, *Huntsville*, *Prime Suspects* y *Ravenhearst*, así como logotipos, personajes, escenas, música y otros materiales, pertenecen a sus respectivos titulares. Mencionar un título indica una compatibilidad técnica pretendida, **no** un derecho de marca, licencia o respaldo.

La aplicación pública está diseñada para distribuir solamente el motor y permitir que una persona seleccione **localmente** el contenido de su instalación obtenida legítimamente. El usuario no debe compartir públicamente el ZIP generado, el ejecutable original ni los casts, bytecode o recursos extraídos. Ninguna advertencia sustituye el permiso que pueda ser necesario para copiar, transformar o utilizar contenido ajeno.

## Derecho aplicable: punto de partida venezolano

- **Derechos de autor (Venezuela):** Ley sobre el Derecho de Autor de 1993, arts. 2 y 17 (programas protegidos); arts. 41–42 (reproducción, distribución y adaptaciones sujetas a los derechos pertinentes); art. 44, numerales 5–6 (supuestos específicos de respaldo y carga en memoria por usuario lícito). **Estas disposiciones no equivalen a una autorización general para convertir, decompilar o redistribuir videojuegos.** Fuente primaria indexada en OMPI: https://www.wipo.int/wipolex/es/legislation/details/3989
- **Marcas:** Ley de Propiedad Industrial venezolana (marcas y denominaciones comerciales), con sus límites y derechos respectivos. https://www.wipo.int/wipolex/es/legislation/details/3985
- **Delitos informáticos:** el art. 25 de la Ley Especial contra los Delitos Informáticos contempla determinadas conductas de apropiación de propiedad intelectual con requisitos propios, entre ellos finalidad de obtener provecho económico. **No se presume delito ni se infiere una excepción solo por gratuidad**. https://www.wipo.int/wipolex/es/legislation/details/10223
- **Condiciones de terceros:** los términos de Big Fish publicados con modificación de 2026-04-08 restringen ingeniería inversa y modificaciones, con salvedad de actividades expresamente permitidas por la ley aplicable. La edición histórica de RealNetworks podría regirse por otro EULA: **no se ha verificado el contrato original de esa instalación**. https://www.bigfishgames.com/mobile-terms-of-use.html
- **Distribución internacional:** alojar APK y sitio en GitHub expone el proyecto a usuarios y titulares de otros países. Excepciones de interoperabilidad existentes en otras jurisdicciones no pueden asumirse como una licencia mundial ni trasladarse automáticamente al caso venezolano.

**Precaución:** no hay un dictamen jurídico definitivo sobre la licitud del proceso de conversión o la distribución de Case Recomp. Antes de promoverlo comercialmente o distribuir masivamente, un abogado competente en propiedad intelectual venezolana e internacional debe revisar el EULA concreto, la cadena de derechos y cada mecanismo de conversión.

## Qué se ha verificado del repositorio público

Revisión de `main` en 2026-10-08: árbol Git examinado sin ejecutables comerciales, `.cct`, imágenes/sonidos originales, scripts Lingo del juego ni ZIP privados versionados; `android/app/src/main/res/raw/synthetic_scenario_v1.json` es material sintético de pruebas. La inspección del árbol actual **no sustituye una auditoría del historial completo, paquetes ya distribuidos o binarios**.

El APK se obtiene desde GitHub Releases. La web usa Pyodide y Pillow en el navegador; las copias que el usuario seleccione se procesan en memoria y se descargan localmente en el diseño actualmente revisado. Véase [Privacidad](PRIVACY.md). No se garantiza la ausencia de transferencias de terceros, extensiones del navegador ni servicios de hosting fuera del código auditado.

## Reglas de propiedad intelectual del proyecto

1. **Nunca** incluir, publicar, adjuntar a issues o subir a CI elementos de juego originales o convertidos: EXE, DLL, CCT, DCR, SWF, bitmaps, pistas de audio, `.ls/.lscr`, ZIP Director, capturas extraídas, registros de usuario con contenido sensible.
2. Compilar/probar el motor con datos sintéticos públicos; comparaciones con instalaciones reales solo en dispositivos y ubicaciones privadas del titular.
3. Para compatibilidad, preferir nombres de títulos en **texto nominativo** y materiales gráficos propios. Una portada obtenida del ZIP del usuario debe quedarse privada en el dispositivo y no formar parte de la APK ni del sitio.
4. No declarar «oficial», «autorizado», «legal en todos los países» ni «libre de derechos».
5. Respetar los acuerdos de distribución de juegos, las restricciones anticircunvención y las excepciones específicas que sean aplicables. **No introducir mecanismos de elusión de DRM sin revisión legal específica**.
6. Responder de buena fe a notificaciones fundamentadas de titulares, preservar pruebas de procedencia y recurrir a los mecanismos de GitHub para solicitudes de retirada. Ver [Política de derechos](COPYRIGHT_POLICY.md).

## Estado de la licencia del motor

El mantenedor ha publicado la [**PolyForm Noncommercial License 1.0.0**](LICENSE) respecto del software original cuyos titulares están facultados para licenciarlo. Permite descargar, modificar y compartir el motor para fines no comerciales, incluyendo entretenimiento personal de usuarios de la APK; **no concede explotación comercial a terceros**. Los autores mantienen los derechos sobre sus aportaciones y pueden autorizar usos comerciales por separado; el mantenedor no puede atribuirse derechos sobre código ajeno sin autorización. Es **código disponible con uso no comercial**, no software de código abierto aprobado por la OSI. La licencia no abarca obras de Big Fish ni dependencias con licencia propia. Véase [alcance de la licencia](LICENSING_STATUS.md).

## Publicación, edad y límites de responsabilidad

Esta es una **beta experimental**. No se garantiza funcionamiento, equivalencia, compatibilidad completa ni conservación indefinida de partidas; las exclusiones de garantía y responsabilidad solo operan **en la medida permitida por ley**. Ni este aviso ni los términos anulan derechos irrenunciables de consumidores o usuarios.

El proyecto no vende contenido de juegos ni verifica automáticamente su titularidad. El distribuidor/mantenedor debe revisar obligaciones sobre consumidores, avisos y edad objetivo cuando defina una operación comercial, publicidad, cuentas o distribución en tiendas.

## Documentos relacionados

- [Privacidad / Privacy](PRIVACY.md)
- [Términos / Terms](TERMS.md)
- [Derechos de autor y notificaciones](COPYRIGHT_POLICY.md)
- [Licencias y avisos de terceros](THIRD_PARTY_NOTICES.md)
- [Estado de licencia del código](LICENSING_STATUS.md)
- [Plan de pendientes de cumplimiento](LEGAL_RELEASE_CHECKLIST.md)

**Contacto:** para incidencias técnicas no sensibles: https://github.com/Dr3amyBerry/Case-Recomp/issues . **No publique allí datos personales, llaves, ZIPs del juego ni denuncias con material propietario.** **Contacto privado legal y de privacidad: [contact@rigorcore.com](mailto:contact@rigorcore.com)**. Debe verificarse el funcionamiento del buzón y completarse la identificación legal del operador antes de una distribución más amplia.

## Sobre otros proyectos de recompilación en Venezuela

Que existan videojuegos recompilados, motores de compatibilidad o portadores que no hayan recibido reclamaciones conocidas **no demuestra una autorización legal general**, una licencia de los titulares ni una sentencia que avale este caso. Tampoco la gratuidad o no incluir contenidos comerciales en el APK hace desaparecer todos los derechos de reproducción, adaptación, contrato o medidas tecnológicas.

En Venezuela, el artículo 17 define la protección de los programas de computación; el artículo 44 numerales 5 y 6 se refiere a una copia de seguridad y a la carga en memoria para el usuario lícito, respectivamente. No equiparar esas excepciones con una libertad general de descompilar/convertir videojuegos. Una opinión jurídica aplicable a Case Recomp debería identificar **la edición exacta del juego, su EULA, los actos técnicos concretos del conversor, las jurisdicciones destinatarias y los derechos de terceros**. Fuente primaria: https://www.wipo.int/wipolex/es/legislation/details/3989 .
