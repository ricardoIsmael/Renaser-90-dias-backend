# Data Model: Planificación de semana y mes

## Tabla ampliada: `renaser.horarios_habito_por_fecha`

Clave: `(participante_id, habito_id, fecha)`. Una fila = «lo que esta persona hace con este hábito ESE día» (V37, V38). Con 028 también dice el aviso con varias antelaciones y «Cómo suena».

| Columna | Tipo | Desde | Significado |
|---|---|---|---|
| `hora_disparo` | `time` NULL | V37/V38 | Hora propia de ese día. `NULL` = sigue a la del día de la semana / general. |
| `hora_limite` | `time` NULL | V37 | Límite propio (solo con hora). |
| `activo` | `boolean` NOT NULL | V38 | `false` = ese día no va. |
| `recordatorio_activo` | `boolean` **NULL** | V37 → V131 | `NULL` = hereda el aviso; `false` = ese día sin aviso; `true` = avisa. Las filas anteriores a 028 nunca son `NULL`. |
| `minutos_recordatorio` | `smallint` NULL | V37 | Antelación más temprana (la que lee el APK anterior). |
| `antelaciones_recordatorio` | `smallint[]` NULL | **V130** | Todas las antelaciones (minutos), de la más temprana a la más tardía. Solo con `recordatorio_activo = true`. `NULL` en una fila anterior a 028: vale `minutos_recordatorio`. |
| `alarma_sonido`, `alarma_vibracion`, `alarma_voz`, `alarma_repetir` | `boolean` NULL | **V130** | «Cómo suena» ese día; cada campo `NULL` = como la general / como el día de la semana (spec 009). |
| `creado_en`, `actualizado_en` | `timestamptz` | V37 | — |

**CHECK nuevos.** V130: sin antelaciones negativas; antelaciones solo con aviso activo; sonido y vibración no pueden ser `false` a la vez; la voz necesita el sonido. V131 reemplaza
`horarios_fecha_activo_exige_hora` por «fila encendida ⇒ hora, aviso o alarma propios» (una fila encendida sin nada propio no dice nada: se borra).

**Compatibilidad.** Toda fila existente queda con las columnas nuevas en `NULL` y `recordatorio_activo` no nulo: se lee y se escribe como hasta hoy.

## Dominio (habits, `domain/model/preferencia/`)

| Tipo | Rol |
|---|---|
| `SemanaCalendaria` (record `lunes`) | Lunes a domingo. `de(fecha)`, `siguiente()`, `fecha(i)`, `contiene(fecha)`, `requireLunes(fecha)`. |
| `FechaPropia` (record) | Una fila de la tabla: `habitoId, fecha, activo, horaDisparo, horaLimite, RecordatorioDelDia recordatorio (null = hereda), AjusteDeAlarma alarma (null = hereda)`. Invariante: `activo ⇒ hora ∨ recordatorio ∨ alarma`. |
| `CambioDeFecha` (record) | Lo que el cliente tocó de UNA fecha: cada campo `Optional`-ish (ausente = no tocado) o `reset`. `aplicarA(FechaPropia existente, SemanaDelHabito.Dia base)` ⇒ `Optional<FechaPropia>` (vacío = borrar). Normaliza a «hereda» lo igual al día de la semana. |
| `PlanDeLaSemana` | `resolver(semana, base 7 días, filas)` ⇒ `DiaDeLaFecha(fecha, Dia, propioDeLaFecha, editable)`. La base es `SemanaDelHabito.resolver` (precedencia de siempre); la capa de fecha va encima, por campo. |
| `RepeticionDeSemana` | `planear(modelo, siguiente, hoy, aplicables)` ⇒ `PlanDeRepeticion(escrituras, borrados, excluidos, reemplazaLoPropio)`; `huella(...)`. Pura. |

`HorarioPorFecha` (V37/V38) no cambia: sigue siendo lo que escribe `PATCH /habit-preferences/{id}` con fecha y `PATCH …/days/{date}`.

## Puertos (habits, `application/ports/`)

- `LoadPreferenciaHorarioPort`: `fechasPropiasEntre(participante, habitoIds, desde, hasta)` ⇒ `List<FechaPropia>` (una consulta por rango, sin N+1).
- `SavePreferenciaHorarioPort`: `guardarFechasPropias(participante, List<FechaPropia>)` (upsert por fila, conserva `creado_en`), `borrarFechasPropias(participante, habitoIds|todos, desde, hasta)` ⇒ cuántas.
- In: `PlanDeLaSemanaUseCase` (`consultar`, `guardar`, `volverAlPlanDeSiempre`), `RepetirSemanaUseCase` (`ofrecer`, `repetir`, `deshacerSemana`).

## App (AsyncStorage, solo lectura/escritura local)

- `renaser.habitos.repetirSemana.descartada.<cuenta>` = `{ modelo, siguiente, huella }` (FR-012).
- `EsquemaGuardado` de las alarmas suma `fechas` (las fechas propias recordadas, para reprogramar sin leer el servidor), igual que ya recuerda `dias`.
