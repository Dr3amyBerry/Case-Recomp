# Recompilación e interoperabilidad de juegos: evaluación jurídica para Venezuela

**Revisión:** 2026-10-08 · **Ámbito:** Case Recomp, un motor Android independiente que interpreta archivos originales proporcionados por el usuario. **No es un dictamen legal ni autoriza copiar o distribuir obras ajenas.**

## Qué se conoce del proyecto

- El código público integra un runtime Android/Kotlin para Director/Lingo y herramientas Python/convertidor web; los usuarios seleccionan localmente los archivos de su instalación.
- El árbol de Git revisado no mostraba archivos comerciales de juego. **No se revisaron de manera exhaustiva el historial, los APK publicados, los artefactos históricos y todas las carpetas privadas**.
- El proyecto menciona compatibilidad con juegos cuyos nombres y activos siguen siendo propiedad de sus titulares.
- El código se distribuye en GitHub y el APK se ofrece en GitHub Releases; el acceso internacional importa aunque el desarrollador resida en Venezuela.

## Derecho venezolano: distinguir supuestos

**Ley sobre el Derecho de Autor** (1993), texto en OMPI Lex: https://www.wipo.int/wipolex/es/legislation/details/3989

1. **Artículo 17:** reconoce los programas de computación y define su productor.
2. **Artículo 42:** comprende la comunicación, reproducción o distribución de obras adaptadas o transformadas entre las facultades reguladas por el derecho de explotación.
3. **Artículo 44, numerales 5 y 6:** permite en términos específicos una copia de seguridad de un programa y su introducción en memoria para su utilización por usuario lícito, con reservas contractuales señaladas en la disposición. **Ninguno es una excepción general y explícita para publicar un port, subir medios convertidos o descompilar cualquier programa**.
4. Los actos técnicos concretos (lectura de formatos, extracción de bytecode, reproducción temporal, conversión a PNG/MP3, guardado de ZIP y ejecución privada) requieren **análisis individual**, con contratos y territorios aplicables.

La existencia de otros proyectos similares o la ausencia de demandas conocidas no sustituye las condiciones legales de cada actividad. Algunas jurisdicciones contemplan excepciones especiales de interoperabilidad, pero sus requisitos no son intercambiables sin análisis.

## Qué debe revisar un profesional competente

- Titular actual y EULA exacto de la distribución concreta de **Huntsville**, incluida cualquier cláusula de modificación, copia, activación o ingeniería inversa.
- Si el procedimiento incluye elusión de medidas tecnológicas y qué disposiciones se aplicarían en los territorios relevantes.
- Diferencia entre el **motor original del proyecto** (código propio que puede licenciarse) y **recursos y scripts comerciales** (titularidad ajena).
- Seguridad jurídica de distribuir una APK y un convertidor mundialmente accesibles, aunque el juego lo aporte cada usuario.
- Derechos sobre aportaciones hechas por terceros y sobre fragmentos procedentes de herramientas de descompilación, si alguno fue incorporado.
- Avisos de marcas/no afiliación, privacidad de CDN, reglas de soporte y mecanismo privado de reclamaciones.

## Reglas operativas hasta completar la revisión

- No publicar ZIPs Director, instaladores, CCTs, sonidos, sprites, tipografías o scripts de los juegos.
- No animar a descargar contenido sin autorización; exigir copia obtenida legítimamente y aclarar que poseer una copia no autoriza cualquier conversión.
- No crear funciones para eludir activaciones/DRM sin análisis jurídico específico.
- Mantener pruebas públicas **sintéticas** y dejar comparaciones con originales en entornos privados.
- No atribuir a una licencia de código del motor derechos de juegos o bibliotecas externas.
- Mantener disponible [contacto legal privado](../LEGAL.md) y un registro de reclamaciones sin divulgar contenido sensible.

**Conclusión acotada:** la separación de motor e insumos del usuario disminuye algunos riesgos de redistribución, pero no demuestra autorización legal completa de todo el proceso. Solicitar dictamen escrito sobre la instalación/EULA concretos antes de difusión masiva o monetización.
