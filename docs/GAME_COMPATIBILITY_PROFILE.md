# Game Compatibility Profile — especificación propuesta v1

Estado: contrato de diseño, no resolver implementado. Un perfil describe una edición identificada y sus necesidades comprobadas; no contiene juego, código nativo, scripts, escenas reconstruidas ni una VM. Corregir errores generales en el módulo correspondiente y referenciarlos mediante su versión mínima.

## Tres versiones independientes

- `schemaVersion`: estructura del perfil, entero 1. Versiones mayores desconocidas se rechazan antes de ejecutar.
- `profileVersion`: revisión semántica de decisiones y evidencia de esa edición. Todo cambio de huella/configuración/capacidad/excepción genera una revisión inmutable.
- `moduleVersion` / `hostAbiVersion`: implementación de runtime y contrato de servicios, negociados por el gestor. No confundir con versión comercial del juego ni con 8.5.1 de Director.

El catálogo declara estado por revisión, edición y scope: `research`, `recognized`, `slice-verified`, `device-verified`, `full-verified`, `unsupported`. Los estados no son flags suministrados por un ZIP. Una versión del perfil no acredita jugabilidad por sí sola.

## Campos y reglas

| Campo | Contenido | Regla |
|---|---|---|
| `format`, `schemaVersion` | `case-recomp-game-compatibility-profile`, 1 | Obligatorios; schema cerrado, no tolerar typo como un default. |
| `profileId`, `profileVersion` | identificador estable y semver | Revision Única; resolver registra digest del JSON canonizado. |
| `game` | id, título de catálogo, edición, idioma/región, versión declarada y observada | Distinguir ambas versiones; null cuando no se sepa. Idioma del readme no prueba región. |
| `detection` | grupos de fuente alternativos; cada grupo lista todos sus fingerprints requeridos | OR de grupos de edición; AND dentro del grupo. Coincidencia sobre bytes, roles y graph; nombre/ruta son hints. |
| `engine` | `moduleId`, familia, versión original observada y rango semver del módulo/ABI | Banda de formato comprobada; no prometer toda familia ni todos SDKs. |
| `capabilities` | lista versionada de required y optional | No faltar required ni cambiar al silent fallback; optional solo con degradación publicada que no altere progreso. |
| `video` | resolución lógica/source rule, alpha/color, política de scaling | Respetar stage del contenido; mismatch con perfil no se corrige estirando arbitrariamente. |
| `audio` | formatos, loops, channel/fade/completion requirements | El perfil solicita semántica; host/módulo la implementan. |
| `input` | pointer/keyboard/text y touch policy id/versión | Mapeos de plataforma, no reglas para encontrar objetos. |
| `fonts` | providers/assets/aliases y calibración opcional registrada | Preferir glyphs originales/atlas privados; ninguna regla por substring global para calibraciones del título. |
| `storage` | adapter id/version, save family/schema y migration policy | No ruta Windows absoluta, claves, usernames o contenido guardado. |
| `exceptions` | ids de implementaciones acotadas registradas con parámetros tipados y evidencia | Declarativas; no arbitrary Lingo, JS, eval o patch de memoria. |
| `evidence` | referencias a tests/goldens/resultados por scope | Publicar ids/resultados agregados; archivos originales privados fuera del repo. |

Fingerprints: `sha256` de 64 hex, tamaño, rol semántico (`projector`, `cast`, `resource-library`, `runtime-library`) e id interno. Ejemplo concreto de títulos comerciales reside en archivos privados del registro hasta revisar qué metadata publicar. El SHA del ZIP de exportación no sustituye huellas de originales; cambiar compresión no debería cambiar identidad del juego.

La elección exacta exige un grupo completo y una versión de registro confiable. `allOf` parcial, nombre comercial, strings de título, casts con nombres compartidos o versión autoafirmada son insuficientes. Si falta una biblioteca usada para efectos obligatorios, estado `incomplete`, no exact. Una variante alternativa solo entra con evidence id y fingerprints revisados.

## Ejemplo sintético declarativo

Es ilustrativo; hashes, ids, versiones y evidence son placeholders, **no perfil activable ni evidencia real de Huntsville/Mystery P.I.** El futuro validador debe rechazar placeholders al registrar producción.

```json
{
  "format": "case-recomp-game-compatibility-profile",
  "schemaVersion": 1,
  "profileId": "synthetic.director.fixture.en",
  "profileVersion": "1.0.0",
  "game": {
    "id": "synthetic-director-fixture",
    "editionId": "fixture-en",
    "title": "Synthetic Director Fixture",
    "language": "en",
    "region": null,
    "declaredVersion": "fixture-1",
    "observedVersion": "fixture-1"
  },
  "detection": {
    "match": "exact-source-set",
    "sourceSets": [{
      "id": "synthetic-set-a",
      "requiredFiles": [{
        "id": "movie", "role": "projector", "size": 100,
        "sha256": "1111111111111111111111111111111111111111111111111111111111111111",
        "pathHint": "fixture.exe"
      }],
      "evidenceIds": ["synthetic.identity.test"]
    }]
  },
  "engine": {
    "moduleId": "director-lingo", "family": "director",
    "originalVersion": "8.5.1", "moduleVersionRange": ">=1.0.0 <2.0.0",
    "hostAbiRange": ">=1.0.0 <2.0.0"
  },
  "capabilities": {
    "required": ["director.score@1", "input.pointer@1", "storage.kv@1"],
    "optional": []
  },
  "video": {"resolutionSource": "content", "logicalWidth": 800,
    "logicalHeight": 600, "scaling": "letterbox", "alpha": "straight"},
  "audio": {"formats": ["wav"], "looping": true, "fades": false},
  "input": {"pointer": true, "keyboard": true, "textCommit": true,
    "touchPolicy": "immediate-pointer@1"},
  "fonts": {"provider": "platform-default@1", "calibrations": []},
  "storage": {"adapter": "director-logical-store@1", "saveFamilyId": "fixture",
    "saveSchemaVersion": 1, "migration": "explicit-only"},
  "exceptions": [],
  "evidence": [{"id": "synthetic.identity.test", "kind": "synthetic-test",
    "scope": "identity", "reference": "synthetic-fixture"}]
}
```

La allowlist de providers/policies/exceptions pertenece al runtime instalado. Se negocia por id/versión, sin reflection ni carga de clases indicada por el perfil. Toda capability tiene descriptor `{id, version, completeness, evidenceScope}`; declarar `director.xtra.fileio.text@1` no habilita FileIO binario completo ni APIs de red.

## Excepciones justificadas y retirables

Cada excepción requiere:

1. `id` e implementación registrada, no una función de juego.
2. `reason`: divergencia observada y por qué no es un error genérico ya corregible.
3. `scope`: edición/fingerprints y, si es visual, identidad de resource/cast/member estable, nunca solo nombre.
4. `parameters`: parámetros tipados, límites rígidos y defaults explícitos.
5. `evidenceIds` y `testIds`: reproducir problema, demostrar corrección y ausencia de fuga a otro perfil.
6. `retireWhen`: criterio falsable (p.ej. recuperación de métricas originales o arreglo de un bug Xtra), prueba para retirar y revisión responsable.

Nudges de Huntsville requieren comparar dibujo y hit-test; no deben mover objetos interactivos sin verificar eventos. Retirar cuando su fuente/registro/layout se reproduzca sin offset y los goldens de texto/input pasen. Calibraciones de Tekton/Palatino requieren provider Android/versión y referencias medidas; retirar cuando el provider original o sus métricas comprobadas den el mismo resultado. Un margen o una ventana no-op no se vuelve estándar por meterlo en un perfil.

No puede haber excepciones para omitir hashes, relajar sandbox, saltar source binding, marcar método desconocido como Éxito o cambiar score/progreso de juego. Excepciones pendientes sin test se registran como investigación, no se activan en producción. Preservar baseline legacy durante la migración es distinto de acreditar nuevos perfiles.

## Validación y resolución

Orden obligatorio: estructura cerrada y tamaño → versión de schema → unicidad/referencias → fingerprints completos → evidence revisada → módulo/ABI → capacidades → parámetros/presupuestos → plan canonizado. Duplicados de ids, allOf vacío, rangos desconocidos, excepción sin criterio de retiro o evidencia inexistente se rechazan.

Se limita tamaño/profundidad, número de source sets/archivos/excepciones y longitudes de cadenas. El perfil nunca eleva límites de host; solo selecciona configuración dentro de límites o más estricta. Las huellas declaradas en un manifest solo cuentan como coincidencia exacta si pueden recomputarse sobre originales accesibles o si están ligadas a una identificación autenticada de procedencia comprobada. Los hashes de un bundle convertido no permiten reconstruir por sí solos la huella del exe original. La ruta legacy sigue disponible con identidad no verificada cuando falte ese enlace. El catálogo del producto, instalado y versionado, es la autoridad de estado de compatibilidad. Un perfil contenido en un juego importado es metadata no confiable hasta contrastarlo.

Persistir `profileId`, revisión, digest de perfil/config, registry revision, module version, host ABI, source set y package digest con cada resultado de QA. Actualizar registro o runtime revalida el plan; no cambia una sesión viva. Versiones desconocidas muestran explicación, no toman perfil de Huntsville como default.

## Aplicación a las dos copias locales

Huntsville: candidato edición española Director 8.5.1, fuente multiarchivo, Xtras/Flash observados; calibraciones temporales aislables cuando tengan regresiones. Aún hay que convertir evidence histórica en un conjunto de aceptación reproducible antes de marcar full-verified.

Mystery P.I.: identidad/fingerprints locales y candidata familia SDA; versión exacta del framework desconocida. Separar versión fija PE de versión declarada por readme. No emitir un perfil ejecutable mientras falte módulo y contrato de capacidades; catalogue research. Director no es fallback.
