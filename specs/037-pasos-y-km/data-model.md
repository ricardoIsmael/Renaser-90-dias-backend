# Modelo de datos — spec 037

**V161** (única migración). Sin tablas nuevas.

## `registros_habito` (cambia)

| Columna | Tipo | Notas |
|---|---|---|
| `valor_medido` | `numeric(7,2)` | ya existe (V84). Con `HEALTH_CONNECT`: los km del día |
| `origen_medicion` | `text` | ya existe. El CHECK pasa de `('MANUAL')` a `('MANUAL','HEALTH_CONNECT')` |
| `pasos_medidos` | `integer NULL` | pasos del día; `>= 0`; solo con `HEALTH_CONNECT` |
| `fuente_medicion` | `varchar(60) NULL` | etiqueta legible de la fuente («Galaxy Watch», «Tu teléfono»); obligatoria con `HEALTH_CONNECT`, prohibida con `MANUAL` |
| `dispositivo_medicion` | `text NULL` | `TELEFONO` \| `RELOJ` \| `OTRO`; mismo patrón que `fuente_medicion` |
| `medicion_leida_en` | `timestamptz NULL` | instante de la lectura en el teléfono; mismo patrón |
| `medicion_por_revisar` | `boolean NOT NULL DEFAULT false` | `true` solo con `HEALTH_CONNECT` |

CHECKs (todos evaluables con los datos de la fila, regla 04):
- origen, fuente, dispositivo y hora de lectura **van juntos** (los tres últimos no nulos si y solo si el origen es `HEALTH_CONNECT`);
- `pasos_medidos` solo con `HEALTH_CONNECT`;
- `medicion_por_revisar` solo con `HEALTH_CONNECT`.

Se conservan los de V84 (`valor >= 0`, valor y origen juntos, solo un `COMPLETADO` lleva medición).

**Por qué columnas y no una tabla:** son atributos del registro del día (clave `participante, hábito, fecha`); una tabla `lecturas_de_pasos` repetiría esa clave y habría que mantener coherentes dos filas. **Por qué no en `respuesta_texto`:** es texto libre de la persona. **Por qué `text` + CHECK y no un enum de Postgres:** un valor nuevo de enum no se puede usar en la transacción que lo crea (E-187); ampliar un CHECK es una sola migración.

## `habitos` (cambia)

| Columna | Tipo | Notas |
|---|---|---|
| `meta_medicion` | `numeric(7,2) NULL` | meta diaria del hábito medible. `NULL` = la del programa (5 km). CHECK `> 0 AND <= 50` |

No se siembra: `NULL` significa «5 km por defecto» (la constante vive en `PoliticaKilometros.META_POR_DEFECTO_KM`). Un hábito copiado a otra formación (spec 023) queda en el valor por defecto, que es lo seguro.

## Dominio (`habits`)

- `OrigenMedicion`: `MANUAL`, `HEALTH_CONNECT`.
- `MedicionDiaria(valor, origen, lectura)`: `lectura` es `null` si es `MANUAL`; la secundaria `(valor, origen)` se conserva para los llamadores de D-226.
- `LecturaAutomatica(pasos, fuente, dispositivo, leidaEn, porRevisar)` y `DispositivoDeMedicion { TELEFONO, RELOJ, OTRO }`.
- La unidad (km) la sigue diciendo la política (`PoliticaKilometros`); la meta se lee con `MetaDeMedicionPort`.

## Lectura

- Agenda de hoy (`GET /habit-tracks/today`): `medicion` suma `meta`, `origenDelDia`, `fuenteDelDia`.
- Ficha del acompañamiento (`ObligacionDelDia.medicion`): `valor`, `origen`, `fuente`, `dispositivo`, `pasos`, `leidaEn`, `porRevisar`.
- Ranking: `kilometrosAutomaticos` (ids de quienes tienen algún día automático hasta la fecha del corte).
