# Data model: Términos y condiciones editables (spec 027)

Dos tablas nuevas, justificadas abajo. Reutiliza `formaciones` (V125), `estado_onboarding` (V1), `respuestas_onboarding` y `medias_onboarding` (la firma que ya se guarda).

## Migraciones (V148–V150)

### V148 — `terminos_versiones` y `aceptaciones_de_terminos`
**`terminos_versiones`**: una fila por versión de los términos de una formación.
| Columna | Tipo | Notas |
|---|---|---|
| `id` | `uuid` PK | |
| `formacion` | `text` FK `formaciones` | cada formación, los suyos (FR-020) |
| `estado` | `text` `BORRADOR` \| `PUBLICADA` | |
| `numero` | `integer` NULL | 1, 2, 3… dentro de su formación; el borrador no lo tiene. `CHECK (estado='PUBLICADA') = (numero IS NOT NULL AND publicada_en IS NOT NULL)` |
| `titulo`, `introduccion`, `texto_casilla` | `text` | título 1–120; introducción ≤ 2000; casilla 1–200 |
| `clausulas` | `text[]` | 1–100; la numeración sale de la posición (FR-003). Cada una ≤ 2000 (lo impone el dominio) |
| `nota` | `text` NULL ≤ 500 | lo que cambió |
| `pide_nueva_aceptacion` | `boolean` | la decisión de Administración al publicar (FR-015) |
| `base_numero` | `integer` NULL | de qué versión vigente partió el borrador: detecta que otra persona publicó mientras se editaba (FR-008) |
| `publicada_en`, `publicada_por` | | `publicada_por` `ON DELETE SET NULL` |
| `creada_en`, `actualizada_en`, `actualizada_por` | | |
- Índice único parcial `(formacion, numero) WHERE PUBLICADA`; índice único parcial `(formacion) WHERE BORRADOR` (a lo sumo un borrador por formación).
- **Una publicada es inmutable**: el trigger `terminos_versiones_constancia` impide editarla o borrarla (las constancias la referencian). Solo deja pasar el `SET NULL` de una cuenta borrada.
- **Por qué `text[]` y no `jsonb` ni una tabla de cláusulas**: es una lista ordenada de textos que se lee y se escribe siempre entera (como `formaciones.duraciones_dias`); nadie une una cláusula con nada.

**`aceptaciones_de_terminos`**: la constancia. Solo se agregan filas.
| Columna | Tipo | Notas |
|---|---|---|
| `id` | `uuid` PK | |
| `usuario_id` | `uuid` FK `usuarios` `ON DELETE CASCADE` | se borra con la cuenta (Q12-A) |
| `version_id` | `uuid` FK `terminos_versiones` | la versión que aceptó |
| `aceptada_en` | `timestamptz` | |
| `firma_media_id` | `bigint` FK `medias_onboarding` `ON DELETE SET NULL` | la firma que dibujó (la que ya se guarda; no se duplica) |
- **Por qué una tabla y no una columna en `estado_onboarding`**: quien aceptó la versión 1 y luego la 2 figura en las dos; con una sola columna se perdería qué aceptó antes, y «cuántas personas aceptaron cada versión» (FR-018) no se podría contar.
- `estado_onboarding.terminos_aceptados_en` se sigue escribiendo: la app lo lee para saber si ya pasó por los términos.

### V149 — la versión 1 de la Escuela y los borradores
Inserta la versión 1 PUBLICADA de la Escuela con las 23 cláusulas de `terminosData.ts` de la app (el texto de siempre, sin cambiar una coma; título «Términos y condiciones», su introducción y la casilla «He leído y acepto los Términos y Condiciones») y, para cada otra formación, un BORRADOR copia de ese texto. **«Por revisar» no es una columna**: es una formación sin ninguna versión publicada.

### V150 — lo que ya estaba aceptado
Cada fila de `estado_onboarding` con `terminos_aceptados_en` pasa a ser una aceptación de la versión 1 de la Escuela, con esa fecha y la firma de su respuesta a `terms_signature` (SC-003: nadie vuelve a firmar).

## Dominio (sin Spring)
- `ContenidoDeTerminos(titulo, introduccion, clausulas, textoCasilla)`: `loQueFaltaParaPublicar()` (FR-007), `exigirQueSePuedaGuardar()` (límites), `dicenLoMismo`.
- `VersionDeTerminos`: `borrador(...)`, `conContenido`, `publicada(numero, nota, pideNueva, quien, ahora)` (inmutable una vez publicada).
- `ReglasDeTerminos.quePide(vigente, ultimaAceptada, aceptoAlgunaVez, publicadas)` → `QuePideLaAceptacion`:
  `NADA` · `PRIMERA_VEZ` (el onboarding la lleva; no bloquea) · `NUEVA_VERSION` (alguna versión posterior a la última aceptada pidió aceptar de nuevo) · `PRIMERA_EN_LA_FORMACION` (ya aceptó los de otra formación). Las dos últimas **bloquean** la app (FR-016).
