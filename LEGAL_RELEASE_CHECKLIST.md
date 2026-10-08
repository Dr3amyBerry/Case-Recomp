# Case Recomp — Checklist legal de distribución (Venezuela + público internacional)

**Fecha:** 2026-10-08 · **Estado:** varias tareas bloqueantes para lanzamiento amplio. Las marcas de estado reflejan hallazgos del código, **no** una certificación jurídica.

| Riesgo | Estado | Trabajo pendiente y criterio de cierre |
|---|---|---|
| Paquetes originales dentro del árbol Git actual | **No observados** | Auditar historia de Git, ZIPs de releases y todos los artefactos existentes; no inferir que se revisó desde siempre |
| Arquitectura local de conversión | **Observada en código** | Prueba dinámica en navegador con red registrada, incluida carga de dependencias CDN, sin enviar contenido comercial |
| Derechos sobre copia/transformación del juego | **ABIERTO / alto** | Obtener EULA exacto de la edición, documentar cadena de titularidad y dictamen sobre conversión/compatibilidad por jurisdicción |
| Ley venezolana de derecho de autor | **Revisada en fuentes** | Abogado verifica aplicación de arts. 2,17,41,42,44 al procedimiento real, sin suponer excepción general |
| Riesgos de marca/afiliación | **Avisos añadidos** | Revisar creatividades públicas y posible conflicto del nombre «Case Recomp» antes de invertir en branding |
| Licencia del motor | **ABIERTO / alto** | Titularidad y permiso de autores, auditoría de código de terceros, decisión explícita de licencia |
| Avisos de dependencias | **Inventario inicial verificado parcialmente** | Mutagen GPL-2.0-or-later (Python opcional), Pyodide/ProjectorRays MPL-2.0, LibreShockwave AGPL-3.0, Pillow MIT-CMU; SBOM de APK/web/Python y avisos por artefacto siguen pendientes |
| Identidad del responsable | **PARCIAL / alto** | Correo designado: **contact@rigorcore.com**. Confirmar entrega/atención; establecer identidad jurídica y domicilio o medio legal exigible |
| Privacidad | **Aviso y contacto publicado** | Verificar recepción del correo, tráfico web y política de logs de proveedores |
| Aceptación de condiciones | **ABIERTO** | Evaluar con abogado necesidad de aceptación y aviso accesible antes de cargar archivos |
| APK beta publicada previamente | **Publicada** | Comprobar que release/artefactos no contienen contenido ajeno y revisar avisos; sustituir beta si requiere cambios materiales |
| Condiciones de distribución global | **ABIERTO** | Evaluar mercados de destino, obligaciones de consumidor, menores, impuestos/monetización solo si procede |
| Reportes de copyright | **Correo designado / operación pendiente** | Verificar que contact@rigorcore.com recibe y atiende comunicaciones y asignar responsable |
| Seguridad | **ABIERTO** | Verificar ausencia de bypass DRM, cambios de permisos/red, ruta de importación segura y proveedores externos |

## Criterios de lanzamiento

**Beta limitada:** mantener aviso de no afiliación/no juegos incluidos, privacidad y términos visibles, sin asegurar legitimidad universal; recabar incidencias no sensibles. El lanzamiento actual no constituye aprobación legal.

**Difusión masiva, monetización o publicación en tiendas:** no recomendar hasta resolver identidad/contacto, licencia/cadena de derechos, EULA del título, privacidad y licencias de terceros. Solicitar revisión profesional en Venezuela y jurisdicciones objetivo.

Fuentes: [LEGAL.md](LEGAL.md), [PRIVACY.md](PRIVACY.md), [COPYRIGHT_POLICY.md](COPYRIGHT_POLICY.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [LICENSING_STATUS.md](LICENSING_STATUS.md).

**Nota:** la ausencia de una «ley integral de datos personales» identificada en esta revisión no implica ausencia de protección constitucional/sectorial o de leyes de otros países. Evitar afirmaciones absolutas.

**Matiz venezolano:** la existencia de otros proyectos de recompilación no determina que los propios actos de conversión o distribución estén autorizados. El artículo 44 de la Ley sobre el Derecho de Autor enumera supuestos concretos de copia de seguridad/carga en memoria de programas, sin resolver por sí mismo la licitud de un port completo. Fuente: [OMPI Lex, Ley sobre el Derecho de Autor](https://www.wipo.int/wipolex/es/legislation/details/3989). [Ver análisis](docs/INTEROPERABILITY_VENEZUELA.md).
