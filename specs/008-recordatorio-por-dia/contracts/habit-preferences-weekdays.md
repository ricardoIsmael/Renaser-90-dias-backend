# Contrato: aviso por día de la semana (spec 008)

Base: `/api/v1/habit-preferences`. Permiso: `USE_APP`, solo sobre hábitos propios (como hoy).

## `PUT /{habitId}/weekdays/{weekday}/reminder` (nuevo)

`weekday`: `MONDAY`..`SUNDAY`.

```json
{ "reminderEnabled": true, "reminderMinutesList": [30, 10] }
```

- `reminderEnabled: false`: ese día va sin aviso. La lista se ignora y puede venir vacía.
- `reminderEnabled: true`: la lista lleva entre 1 y N antelaciones, con el rango de `AntelacionesDelRecordatorio`. Fuera de rango responde `400`.
- Si coincide con el recordatorio general, el día vuelve a heredarlo y la respuesta lo dice con `customReminder: false`.
- **204**: guardado. **400**: valor inválido. **403**: sin permiso o hábito ajeno. **404**: hábito inexistente. **No consume cupo semanal.**
- Prueba negativa obligatoria: un actor sin `USE_APP` recibe 403.

## `PUT /{habitId}/weekdays/{weekday}` (sin cambio de contrato)

`{triggerTime, limitTime}`. Fija la hora y **conserva** el aviso propio del día (FR-013).

## `DELETE /{habitId}/weekdays/{weekday}` (sin cambio de contrato)

Borra hora **y** aviso propios (FR-006).

## `GET /{habitId}/weekdays` (campos aditivos)

```json
{ "weekdays": [
  { "weekday": "SATURDAY", "triggerTime": "09:00:00", "limitTime": null,
    "custom": true, "active": true,
    "reminderEnabled": true, "reminderMinutesList": [30], "customReminder": true }
] }
```

- `reminderEnabled` y `reminderMinutesList` traen el aviso **resuelto** del día: el propio o, si no hay, el general.
- `customReminder` vale `true` solo si el aviso es propio.

## `GET /` (lista de preferencias, campo aditivo por hábito)

Cada ítem suma `weekdays` con las 7 entradas en el mismo formato de arriba (sin `limitTime`), ya resueltas. Un cliente anterior lo ignora.

## Compatibilidad

| Cliente | Backend | Resultado |
|---|---|---|
| APK anterior | nuevo | Ignora los campos nuevos. Guardar la hora no borra el aviso. El teléfono sigue con la alarma diaria general (como hoy) |
| APK nuevo | anterior | No llegan `customReminder` ni `weekdays`: la app no ofrece aviso por día y usa el esquema diario (como hoy) |
