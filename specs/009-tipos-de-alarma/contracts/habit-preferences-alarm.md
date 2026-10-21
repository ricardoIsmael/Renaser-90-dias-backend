# Contrato: combinación de alarma (spec 009)

## `PUT /api/v1/habit-preferences/{habitId}/alarm` y `PUT …/weekdays/{weekday}/alarm` (nuevos)

```json
{ "sound": true, "vibration": true, "voice": false, "repeat": true }
```

| Respuesta | Cuándo |
|---|---|
| 204 | Se guardó. |
| 400 | `sound=false` y `vibration=false` a la vez, o `voice=true` con `sound=false`. |
| 403 | Sin `USE_APP`, o el hábito es de otra persona. Hay prueba negativa obligatoria. |
| 404 | El hábito no existe. |

Ninguno de los dos consume cupo semanal.

## Lecturas (campos aditivos)

`GET …/weekdays` y `GET /habit-preferences` → `weekdays[]` suman:

```json
"alarm": { "sound": true, "vibration": true, "voice": false, "repeat": true }, "customAlarm": false
```

Cada ítem de `GET /habit-preferences` suma también `alarm`, que es la combinación general ya resuelta.

## Compatibilidad

| Situación | Qué pasa |
|---|---|
| APK anterior | Ignora los campos nuevos y suena como hoy. |
| APK nuevo con backend anterior | Faltan `alarm` y `customAlarm`, así que no se muestra «Cómo suena». |
