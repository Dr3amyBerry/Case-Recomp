# Game Compatibility Profile ? especificaci?n propuesta v1

Estado: contrato de dise?o, no resolver implementado. Un perfil describe una edici?n identificada y sus necesidades comprobadas; no contiene juego, c?digo nativo, scripts, escenas reconstruidas ni una VM. Corregir errores generales en el m?dulo correspondiente y referenciarlos mediante su versi?n m?nima.

## Tres versiones independientes

- `schemaVersion`: estructura del perfil, entero 1. Versiones mayores desconocidas se rechazan antes de ejecutar.
- `profileVersion`: revisi?n sem?ntica de decisiones y evidencia de esa edici?n. Todo cambio de huella/configuraci?n/capacidad/excepci?n genera una revisi?n inmutable.
- `moduleVersion` / `hostAbiVersion`: implementaci?n de runtime y contrato de servicios, negociados por el gestor. No confundir con versi?n comercial del juego ni con 8.5.1 de Director.

El cat?logo declara estado por revisi?n, edici?n y scope: `research`, `recognized`, `slice-verified`, `device-verified`, `full-verified`, `unsupported`. Los estados no son flags suministrados por un ZIP. Una versi?n del perfil no acredita jugabilidad por s? sola.

## Campos y reglas

| Campo | Contenido | Regla |
|---|---|---|
| `format`, `schemaVersion` | `case-recomp-game-compatibility-profile`, 1 | Obligatorios; schema cerrado, no tolerar typo como un default. |
| `profileId`, `profileVersion` | identificador estable y semver | Revision ?nica; resolver registra digest del JSON canonizado. |
| `game` | id, t?tulo de cat?logo, edici?n, idioma/regi?n, versi?n declarada y observada | Distinguir ambas versiones; null cuando no se sepa. Idioma del readme no prueba regi?n. |
| `detection` | grupos de fuente alternativos; cada grupo lista todos sus fingerprints requeridos | OR de grupos de edici?n; AND dentro del grupo. Coincidencia sobre bytes, roles y graph; nombre/ruta son hints. |
| `engine` | `moduleId`, familia, versi?n original observada y rango semver del m?dulo/ABI | Banda de formato comprobada; no prometer toda familia ni todos SDKs. |
| `capabilities` | lista versionada de required y optional | No faltar required ni cambiar al silent fallback; optional s?lo con degradaci?n publicada que no altere progreso. |
| `video` | resoluci?n l?gica/source rule, alpha/color, pol?tica de scaling | Respetar stage del contenido; mismatch con perfil no se corrige estirando arbitrariamente. |
| `audio` | formatos, loops, channel/fade/completion requirements | El perfil solicita sem?ntica; host/m?dulo la implementan. |
| `input` | pointer/keyboard/text y touch policy id/versi?n | Mapeos de plataforma, no reglas para encontrar objetos. |
| `fonts` | providers/assets/aliases y calibraci?n opcional registrada | Preferir glyphs originales/atlas privados; ninguna regla por substring global para calibraciones del t?tulo. |
| `storage` | adapter id/version, save family/schema y migration policy | No ruta Windows absoluta, claves, usernames o contenido guardado. |
| `exceptions` | ids de implementaciones acotadas registradas con par?metros tipados y evidencia | Declarativas; no arbitrary Lingo, JS, eval o patch de memoria. |
| `evidence` | referencias a tests/goldens/resultados por scope | Publicar ids/resultados agregados; archivos originales privados fuera del repo. |

Fingerprints: `sha256` de 64 hex, tama?o, rol sem?ntico (`projector`, `cast`, `resource-library`, `runtime-library`) e id interno. Ejemplo concreto de t?tulos comerciales reside en archivos privados del registro hasta revisar qu? metadata publicar. El SHA del ZIP de exportaci?n no sustituye huellas de originales; cambiar compresi?n no deber?a cambiar identidad del juego.

La elecci?n exacta exige un grupo completo y una versi?n de registro confiable. `allOf` parcial, nombre comercial, strings de t?tulo, casts con nombres compartidos o versi?n autoafirmada son insuficientes. Si falta una biblioteca usada para efectos obligatorios, estado `incomplete`, no exact. Una variante alternativa s?lo entra con evidence id y fingerprints revisados.

## Ejemplo sint?tico declarativo

Es ilustrativo; hashes, ids, versiones y evidence son placeholders, **no perfil activable ni evidencia real de Huntsville/Mystery P.I.** El futuro validador debe rechazar placeholders al registrar producci?n.

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

La allowlist de providers/policies/exceptions pertenece al runtime instalado. Se negocia por id/versi?n, sin reflection ni carga de clases indicada por el perfil. Toda capability tiene descriptor `{id, version, completeness, evidenceScope}`; declarar `director.xtra.fileio.text@1` no habilita FileIO binario completo ni APIs de red.

## Excepciones justificadas y retirables

Cada excepci?n requiere:

1. `id` e implementaci?n registrada, no una funci?n de juego.
2. `reason`: divergencia observada y por qu? no es un error gen?rico ya corregible.
3. `scope`: edici?n/fingerprints y, si es visual, identidad de resource/cast/member estable, nunca s?lo nombre.
4. `parameters`: par?metros tipados, l?mites r?gidos y defaults expl?citos.
5. `evidenceIds` y `testIds`: reproducir problema, demostrar correcci?n y ausencia de fuga a otro perfil.
6. `retireWhen`: criterio falsable (p.ej. recuperaci?n de m?tricas originales o arreglo de un bug Xtra), prueba para retirar y revisi?n responsable.

Nudges de Huntsville requieren comparar dibujo y hit-test; no deben mover objetos interactivos sin verificar eventos. Retirar cuando su fuente/registro/layout se reproduzca sin offset y los goldens de texto/input pasen. Calibraciones de Tekton/Palatino requieren provider Android/versi?n y referencias medidas; retirar cuando el provider original o sus m?tricas comprobadas den el mismo resultado. Un margen o una ventana no-op no se vuelve est?ndar por meterlo en un perfil.

No puede haber excepciones para omitir hashes, relajar sandbox, saltar source binding, marcar m?todo desconocido como ?xito o cambiar score/progreso de juego. Excepciones pendientes sin test se registran como investigaci?n, no se activan en producci?n. Preservar baseline legacy durante la migraci?n es distinto de acreditar nuevos perfiles.

## Validaci?n y resoluci?n

Orden obligatorio: estructura cerrada y tama?o ? versi?n de schema ? unicidad/referencias ? fingerprints completos ? evidence revisada ? m?dulo/ABI ? capacidades ? par?metros/presupuestos ? plan canonizado. Duplicados de ids, allOf vac?o, rangos desconocidos, excepci?n sin criterio de retiro o evidencia inexistente se rechazan.

Se limita tama?o/profundidad, n?mero de source sets/archivos/excepciones y longitudes de cadenas. El perfil nunca eleva l?mites de host; s?lo selecciona configuraci?n dentro de l?mites o m?s estricta. Las huellas declaradas en un manifest s?lo cuentan como coincidencia exacta si pueden recomputarse sobre originales accesibles o si est?n ligadas a una identificaci?n autenticada de procedencia comprobada. Los hashes de un bundle convertido no permiten reconstruir por s? solos la huella del exe original. La ruta legacy sigue disponible con identidad no verificada cuando falte ese enlace. El cat?logo del producto, instalado y versionado, es la autoridad de estado de compatibilidad. Un perfil contenido en un juego importado es metadata no confiable hasta contrastarlo.

Persistir `profileId`, revisi?n, digest de perfil/config, registry revision, module version, host ABI, source set y package digest con cada resultado de QA. Actualizar registro o runtime revalida el plan; no cambia una sesi?n viva. Versiones desconocidas muestran explicaci?n, no toman perfil de Huntsville como default.

## Aplicaci?n a las dos copias locales

Huntsville: candidato edici?n espa?ola Director 8.5.1, fuente multiarchivo, Xtras/Flash observados; calibraciones temporales aislables cuando tengan regresiones. A?n hay que convertir evidence hist?rica en un conjunto de aceptaci?n reproducible antes de marcar full-verified.

Mystery P.I.: identidad/fingerprints locales y candidata familia SDA; versi?n exacta del framework desconocida. Separar versi?n fija PE de versi?n declarada por readme. No emitir un perfil ejecutable mientras falte m?dulo y contrato de capacidades; catalogue research. Director no es fallback.
