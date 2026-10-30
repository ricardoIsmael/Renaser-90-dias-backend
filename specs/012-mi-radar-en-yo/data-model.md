# Data Model: Mi Radar en Yo

## Cambios en la base (V119, sin tablas nuevas)

> **Corregido 2026-10-30 (spec 019, V122).** `horas_mi_radar` se eliminó y `dias_mi_radar` guarda `{dia, modo, veces, sinRadar}`; se suma
> `mi_radar_encendido`. Las horas se calculan con el Despertar y el Dormir. Ver `specs/019-radar-automatico/plan.md`.

> **Corregido 2026-10-29 (cuarta ronda).** Decía una sola columna de horas, las mismas todos los días. Mi Radar es ahora por día de la
> semana: se suma `dias_mi_radar` y V119 se modificó (no se creó V120) porque no se publicó.

| Tabla | Columna | Tipo | Regla |
|---|---|---|---|
| `participantes_programa` | `horas_mi_radar` | `smallint[]` NULL | Las horas de TODOS los días. `NULL` = apagado. Si no es NULL: de 1 a 12 elementos, cada uno entre 3 y 23 (CHECK). El dominio ordena y rechaza repetidos. |
| `participantes_programa` | `dias_mi_radar` | `jsonb` NULL | Los días con horas propias: `[{"dia": 6, "horas": [11]}, {"dia": 7, "horas": []}]`, lunes = 1; `horas: []` = «Sin Radar ese día»; `NULL` = ningún día propio. CHECK: es un arreglo y `horas_mi_radar` no es NULL. El dominio valida el resto (día 1–7 sin repetir, horas con las mismas reglas). |
| `registros_radar` | `hora_prevista` | `smallint` NULL | Entre 0 y 23 (CHECK). La calcula el servidor al registrar; los registros anteriores quedan en NULL. |

El índice `radar_un_registro_por_hora` (V52) no cambia.

## Dominio (`habits/domain/model/radar`)

### MiRadar (valor)

- `horas`: las de todos los días; lista ordenada, sin repetidos, de 1 a 12 elementos, cada uno entre 3 y 23. Vacía = apagado.
- `dias`: lista de `DiaPropio(dia 1..7, horas)` ordenada por día, sin días repetidos; las horas de un día tienen las mismas
  reglas pero pueden ser vacías («Sin Radar ese día»). Apagado no tiene días: sin `horas` no puede haber `dias`.
- `apagado()`, `encendido(horas)`, `encendido(horas, dias)`. Si no cumple, lanza la excepción de validación del proyecto (400).
- `estaEncendido()`, `horasDelDia(DayOfWeek)`: las propias del día o, si no tiene, las de todos los días.

### HorarioDelRadar (funciones puras, con el momento LOCAL de la persona)

| Día del programa | `puedeRegistrar(local)` | `horaPrevista(local)` |
|---|---|---|
| 0–7 | siempre, como hoy | la hora, si está entre 8 y 19 |
| ≥ 8, apagado | no | — |
| ≥ 8, encendido, día de la semana sin horas | no | — |
| ≥ 8, encendido | si la hora está entre 3 y 23 | la hora, si es una de las `horasDelDia` de ese día de la semana |

### RegistroRadar (ya existe)

- Suma `horaPrevista: Integer` (nullable). El resto no cambia.

## App

- `MiRadarApi`: `{ encendido: boolean, horas: number[], dias: { dia: number, horas: number[] }[] }` (`dias` ausente = `[]`).
- `RegistroRadarApi`: suma `horaPrevista?: number | null`.
- `HistorialRadarApi`: suma `miRadar?: MiRadarApi | null`.
- Almacenamiento local: `radar.invitacionDia8.<usuarioId>` (programada sí o no).

## Estados de Mi Radar

```text
Días 1–7 ── (fin del día 7) ──> Apagado ── encender (1–12 horas) ──> Encendido
                                   ^                                   │
                                   └──────────── apagar ───────────────┘
```

Cambiar las horas con Mi Radar encendido rige en el acto. El día 90 no cambia el estado.
