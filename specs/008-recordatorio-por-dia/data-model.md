# Data Model: Recordatorio por día (spec 008)

## `horario_semanal_habito` (V39, V40; V111 suma columnas)

| Columna | Tipo | Antes | V111 |
|---|---|---|---|
| `participante_id`, `habito_id`, `dia_semana` | PK | igual | igual |
| `hora_disparo` | `time NULL` | hora propia del día | igual; `NULL` = hereda la hora general |
| `hora_limite` | `time NULL` | igual | igual |
| `activo` | `boolean NOT NULL` | `false` = día apagado | igual |
| `recordatorio_activo` | `boolean NULL` | — | **nuevo**. `NULL` = hereda el general; `false` = «Sin aviso» ese día; `true` = usa `antelaciones_recordatorio` |
| `antelaciones_recordatorio` | `smallint[] NULL` | — | **nuevo**. Antelaciones en minutos, de la más temprana a la más tardía. Mismo rango que V81 (`AntelacionesDelRecordatorio`) |

**CHECK nuevo** (reemplaza el de V40):

- `NOT activo OR hora_disparo IS NOT NULL OR recordatorio_activo IS NOT NULL`: una fila encendida dice algo propio, sea la hora o el aviso.
- `recordatorio_activo IS DISTINCT FROM true OR cardinality(antelaciones_recordatorio) >= 1`: un aviso activo tiene al menos una antelación.

### Estados de una fila

| `activo` | `hora_disparo` | `recordatorio_activo` | Significado |
|---|---|---|---|
| false | NULL | (ignorado) | Ese día no va (V40) |
| true | HH:mm | NULL | Hora propia y aviso general (como hoy) |
| true | HH:mm | true/false | Hora propia y aviso propio |
| true | NULL | true/false | Hora general y aviso propio (**nuevo**) |
| — | — | — | Sin fila: hora y aviso generales |

### Transiciones

- **Fijar la hora** (`PUT …/weekdays/{wd}`, sin cambio de contrato): `upsert` de `hora_disparo` y `hora_limite`. **Conserva** `recordatorio_activo` y `antelaciones_recordatorio`. Cobra cupo, igual que hoy.
- **Fijar el aviso** (`PUT …/weekdays/{wd}/reminder`, nuevo): `upsert` del aviso, que conserva la hora. Si el aviso pedido es **igual al general**, el aviso vuelve a `NULL`. Si la fila queda sin nada propio (encendida, sin hora y sin aviso), se borra. No cobra cupo.
- **Apagar** (`DELETE …/active`): sin cambio. El aviso queda guardado, pero no aplica mientras el día esté apagado.
- **Quitar** (`DELETE …/weekdays/{wd}`): borra la fila, hora y aviso.
- **Hoy y días pasados**: la app no permite cambiarlos (candado). El servidor no agrega una regla nueva: el cambio de aviso rige desde la próxima vez que se resuelve ese día.

## Resolución por día (servidor y app)

Capas, de la más general a la más específica: preferencia general → cambio general pendiente (D-91) → **día de la semana** → fecha puntual. Cada capa pisa solo los campos que trae:

- La hora: si la fila del día trae `hora_disparo`.
- El aviso: si la fila del día trae `recordatorio_activo`, que fija `recordatorioActivo` y `antelaciones`. Con `minutosRecordatorio` = la más temprana (D-217).

## Dominio (backend)

- **`HorarioSemanal(diaSemana, preferencia, activo)`**: la validación pasa a «activo ⇒ hora **o** aviso propio». `preferencia.recordatorio…` solo cuenta como propio si viene marcado. Se agrega un `RecordatorioDelDia(activo, antelaciones)` opcional para no confundir «hereda» con «sin aviso».
- **`EditarHorarioSemanalUseCase`**: suma `fijarRecordatorio(actor, habito, dia, activo, antelaciones)`, y `DiaDeLaSemana` suma `recordatorioActivo`, `antelaciones` y `recordatorioPropio`.

## App (estado del editor)

- `horarioSemanal[dia]` suma `aviso: { activo: boolean; antelaciones: number[] }` y `avisoPropio: boolean`.
- `avisoInicialDeLosMarcados(dias)`: devuelve el aviso común o `'distintos'`.
- `queGuardar({ movioLaRueda, cambioElAviso })`: devuelve `'hora' | 'aviso' | 'ambos'`.

## Alarma local

| Esquema | Cuándo | Disparadores | Identificadores |
|---|---|---|---|
| Diario (hoy) | Ningún día con hora o aviso propios ni apagado | `DAILY` por antelación | `habito-{id}-inicio` + otros (como hoy) |
| Por día | Algún día distinto | `WEEKLY` por día encendido × antelación | `habito-{id}-d{1..7}-{n}` |
