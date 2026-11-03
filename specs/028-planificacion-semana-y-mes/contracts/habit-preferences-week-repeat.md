# Contrato: repetir la semana (hábitos)

Base `/api/v1/habit-preferences`. `@RequiresPermission(USE_APP)`, `@Funcionalidad("habitos.ajustes")`; cuenta `SUSPENDED` ⇒ 403. Solo hábitos (decisión 028-3). Sin tablas nuevas.

## `GET /week-repeat`

La oferta de repetir **la semana en curso en la siguiente**. `204` si no hay oferta (sin Día 1, la modelo sin cambios propios, la siguiente ya igual, o la siguiente fuera del programa).

```json
{
  "sourceWeekStart": "2026-10-26",
  "targetWeekStart": "2026-11-02",
  "fingerprint": "9f2c4a1b7d3e8a05",
  "replacesOwnChanges": false,
  "habits": [
    {
      "habitId": "…", "title": "Despertar",
      "days": [
        { "date": "2026-11-07", "weekday": "SATURDAY", "active": true, "triggerTime": "09:00:00",
          "reminderEnabled": true, "reminderMinutesList": [30], "customTime": true, "customReminder": true,
          "alarm": null, "replaces": false }
      ]
    }
  ],
  "excluded": [ { "habitId": "…", "title": "…", "reason": "NOT_AVAILABLE" } ]
}
```

- `habits[].days[]`: lo que quedaría en la **siguiente** semana (fecha destino) por cada fecha que cambia (copiada o devuelta al plan de siempre). `triggerTime`/aviso/`alarm` vienen solo si son propios de esa fecha (`null`/ausente = el plan de siempre). `active=false` = ese día no va. `replaces`: esa fecha ya tenía un cambio propio distinto.
- `fingerprint`: huella de las filas de la **semana modelo** (para «Ahora no»).
- `replacesOwnChanges`: alguna fecha de la siguiente ya tenía un cambio propio distinto.
- `excluded`: hábitos de la modelo que no se repiten. `reason`: `NOT_AVAILABLE` (desactivado o ya no le corresponde).

## `POST /week-repeat`

```json
{ "sourceWeekStart": "2026-10-26", "targetWeekStart": "2026-11-02", "replace": false }
```

Aplica la repetición (se vuelve a planear en el servidor con lo que hay ahora). Todo o nada, idempotente. `200`:

```json
{ "targetWeekStart": "2026-11-02", "copiedDays": 5, "removedDays": 0, "habits": 3, "excluded": [] }
```

- Ambos son lunes (400), distintos (400), y la semana destino tiene al menos una fecha posterior a hoy (400).
- Si reemplazaría cambios propios y `replace` es `false` ⇒ **409** (la app pide confirmación y reintenta con `true`).
- Solo escribe fechas posteriores a hoy. No toca pausas, registros, puntos, racha ni semáforo; no cobra cupo.

## `DELETE /weeks/{weekStart}`

«Deshacer» / «Volver al plan de siempre» de TODA la semana: borra las filas propias de todos los hábitos de la persona en esa semana, solo fechas posteriores a hoy. `200` `{ "removedDays": 5 }`. Idempotente (0 si no había nada). `weekStart` lunes (400 si no).

## Errores

400, 403, 409 (reemplazo sin confirmar). Un hábito que no le corresponde nunca se escribe: se omite y se lista en `excluded`.
