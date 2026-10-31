# Contrato: «Solo esta semana» (hábitos)

Base `/api/v1/habit-preferences`. Todos: `@RequiresPermission(USE_APP)`, `@Funcionalidad("habitos.ajustes")`. Cuenta `SUSPENDED` ⇒ 403. Fechas `YYYY-MM-DD` en la zona del participante; horas `HH:mm:ss` (se acepta `HH:mm`); días `MONDAY..SUNDAY`.
Compatibilidad: todo campo nuevo es opcional. Un APK anterior no manda ni lee nada de esto.

## `GET /{habitId}/week?weekStart=YYYY-MM-DD`

La semana (lunes a domingo) de UN hábito, ya resuelta: el plan de siempre con lo «solo esta semana» encima. `weekStart` debe ser lunes (400 si no). El hábito debe corresponderle a la persona (404 si no existe o no le corresponde).

```json
{
  "weekStart": "2026-11-02",
  "weekEnd": "2026-11-08",
  "days": [
    {
      "date": "2026-11-07",
      "weekday": "SATURDAY",
      "triggerTime": "09:00:00",
      "limitTime": null,
      "active": true,
      "reminderEnabled": true,
      "reminderMinutesList": [30, 0],
      "customTime": true,
      "customReminder": false,
      "alarm": { "sound": true, "vibration": true, "voice": false, "repeat": false },
      "customAlarm": false,
      "customDate": true,
      "editable": true
    }
  ]
}
```

- Los siete días siempre. Los campos de aviso/alarma son los de `weekdays[]` de `GET /habit-preferences` (spec 008/009): **resueltos** para esa fecha, y `customTime/customReminder/customAlarm` valen `true` si el valor es propio **del día de la semana o de la fecha**.
- `customDate`: la fecha tiene fila propia («solo esta semana»). `editable`: la fecha es posterior a hoy.
- `active=false` si el día de la semana está apagado en el plan de siempre o la fecha está apagada.

## `PUT /{habitId}/week`

Guarda **solo lo tocado** en varias fechas de una semana. Todo o nada. `204`.

```json
{
  "weekStart": "2026-11-02",
  "days": [
    { "date": "2026-11-07", "triggerTime": "09:00", "limitTime": null },
    { "date": "2026-11-08", "reminderEnabled": true, "reminderMinutesList": [30], "alarm": { "sound": true, "vibration": false, "voice": false, "repeat": true } },
    { "date": "2026-11-06", "active": false },
    { "date": "2026-11-05", "reset": true }
  ]
}
```

Reglas:
- `weekStart` es lunes; cada `date` cae en esa semana y es **posterior a hoy** (400 si no). 400 si `days` está vacío, repite una fecha o mezcla `reset: true` con otros campos.
- Un campo ausente no se toca (se conserva el de la fila que ya hubiera). `reminderEnabled` y `reminderMinutesList` van juntos (activo ⇒ al menos una antelación, 0 a 32767, sin repetir). `alarm`: los cuatro booleanos (sonido y vibración no pueden ser `false` a la vez; la voz necesita el sonido).
- `triggerTime` respeta el tope del día (D-122). `limitTime` se acomoda como siempre.
- Lo que queda igual a lo que ya rige ese día de la semana se guarda como «hereda». Una fila sin nada propio se borra.
- `active: false` en un hábito obligatorio del programa ⇒ **409**. `active: true` sobre un día apagado en el plan de siempre ⇒ 400.
- No cobra cupo ni escribe en el historial de reacomodos.

## `DELETE /{habitId}/week?weekStart=YYYY-MM-DD`

Vuelve al plan de siempre: borra las filas propias de ese hábito en esa semana, **solo fechas posteriores a hoy**. Idempotente. `204`.

## `GET /habit-preferences` (ampliado, aditivo)

Cada ítem de `habits` suma `dateOverrides`: las fechas con fila propia desde la fecha consultada (hoy si no se pasa `date`) hasta 20 días después, ordenadas, con la misma forma de un día de `/week` (sin `limitTime`):

```json
{ "date": "2026-11-07", "weekday": "SATURDAY", "triggerTime": "09:00:00", "active": true,
  "reminderEnabled": true, "reminderMinutesList": [30, 0], "customTime": true, "customReminder": false,
  "alarm": { "sound": true, "vibration": true, "voice": false, "repeat": false }, "customAlarm": false }
```

**Siempre presente en el servidor nuevo** (lista vacía si no hay ninguna fecha propia). Ausente ⇒ servidor anterior: la app no ofrece «Solo esta semana» y se comporta como siempre.

## Errores

400 (valor inválido, fecha fuera de la semana o no posterior a hoy), 403 (permiso o cuenta suspendida), 404 (hábito que no existe o no le corresponde), 409 (apagar un obligatorio).
