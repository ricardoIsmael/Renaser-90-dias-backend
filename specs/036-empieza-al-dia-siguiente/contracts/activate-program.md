# Contrato: `GET` y `POST /api/v1/onboarding/activate-program` (spec 036)

Misma ruta y mismo permiso (`USE_APP`, self por construcción, sin guard de rol: D-260/D-261). Solo cambia lo que se ofrece y lo que se acepta.

## GET

```json
{ "activated": false, "validStartDates": ["2026-11-03"], "startDate": null }
```

- `validStartDates`: **una** fecha, mañana en la zona de la persona (antes: mañana, +2 y +3). Vacía si `activated` es `true`.
- `startDate`: el Día 1 ya activado, o `null`.
- 404 si no es personal y no tiene fila (como hoy). 403 si la cuenta está suspendida.

## POST

Cuerpo **opcional**: `{ "startDate": "2026-11-03" }` o vacío / `{}`.

| Caso | Respuesta |
|---|---|
| Sin `startDate` o `startDate` = mañana (sin activar) | 200 `{ traineeProfileId, programDay: 0, programActivatedAt, startDate: mañana, expectedGraduationDate }` |
| `startDate` ≠ mañana (sin activar) | **400** «El programa empieza siempre mañana (2026-11-03, tu zona: America/Lima), recibida: 2026-11-05» |
| Ya activado, sin `startDate` o la misma `startDate` | 200 sin cambios (idempotente) |
| Ya activado con otra fecha | 409 «El programa ya fue activado el …, no se puede reactivar» |
| Personal sin fila | se crea ya activada con inicio mañana (D-261) |

`expectedGraduationDate` = `startDate + duración` (90 / 365 días) − ajustes, como siempre.
