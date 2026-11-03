# Contrato de la API de pagos (spec 032)

Todo en inglés camelCase, como el resto de la API que consume la app. Las fechas son `yyyy-MM-dd`; los montos, decimales con hasta dos cifras; las monedas, `PEN` o `USD`.

## La persona

### `GET /api/v1/me/ingreso` (USE_APP · funcionalidad `pagos`)
```json
{ "steps": [ { "key": "DIAGNOSIS" | "AGREEMENT" | "PAYMENTS", "done": true } ],
  "payments": null | {
    "mode": "NONE" | "SCHEDULE",
    "currency": null | "PEN" | "USD",
    "installments": [ { "id": "uuid", "dueDate": "2026-11-15", "amount": 1000.00, "status": "PAID" | "PENDING",
                        "statusChangedAt": null | "instant", "statusChangedByName": null } ],
    "confirmation": null | { "state": "NOT_YET" | "CONFIRMED" | "OUTDATED", "confirmedAt": null | "instant", "name": null | "..." } } }
```
- `payments: null` (y `steps: []`) = no hay nada que mostrar: la app no dibuja la fila «Mi ingreso».
- `steps`: solo los que le aplican, en orden. `DIAGNOSIS` solo si le tocó; `AGREEMENT` = términos aceptados; `PAYMENTS` = hecho si es `NONE` o todas las cuotas están pagadas.
- `confirmation` es `null` con `NONE`. `OUTDATED` = confirmó otro cronograma y Administración cambió fechas o montos desde entonces.
- `statusChangedByName` va siempre `null` para la persona.

### `POST /api/v1/me/ingreso/cronograma/confirmacion` (USE_APP)
Cuerpo `{ "name": "Beto Aprendiz", "reviewed": true }` → la misma respuesta. 400 si `reviewed` no es `true` o el nombre falta (1 a 120); 409 si no tiene un cronograma. Idempotente: confirmar lo ya confirmado no cambia nada.

## Administración y Alquimista (`MANAGE_PAYMENTS`; 403 para cualquier otro rol, Soporte TI incluido)
Base: `/api/v1/admin/pagos/personas/{personaId}`. Todas devuelven `{ "personName": "...", "payments": <como arriba, con statusChangedByName> | null }`.

| Verbo y ruta | Cuerpo | Efecto | Errores |
|---|---|---|---|
| `GET` | — | lee el pago de la persona | 404 persona |
| `PUT` | `{ "mode": "NONE" }` o `{ "mode": "SCHEDULE", "currency": "PEN", "installments": [ { "dueDate", "amount" } ] }` | anota «sin cronograma» o arma el cronograma entero (reemplaza el anterior). Desde D-315 `installments` puede ir vacío u omitido (0 a 60): «con cronograma» sin cuotas todavía, que se llenan con `POST /cuotas` | 400 inválido (moneda, monto, fecha, más de 60); 409 si ya hay cuotas pagadas |
| `POST /cuotas` | `{ "dueDate", "amount" }` | suma una cuota pendiente | 409 sin cronograma o con 60 |
| `PUT /cuotas/{cuotaId}` | `{ "dueDate", "amount" }` | cambia una cuota pendiente | 404 cuota ajena; 409 pagada |
| `DELETE /cuotas/{cuotaId}` | — | quita una cuota pendiente | 404; 409 pagada o última |
| `PUT /cuotas/{cuotaId}/estado` | `{ "status": "PAID" \| "PENDING" }` | marca; idempotente; deja quién y cuándo | 404 |

## Aceptar una solicitud (spec 025)
`POST /api/v1/account-requests/{id}/approve` suma el campo opcional `payment` (mismo cuerpo del `PUT` de arriba). Sin él, nada se anota. Un `payment` inválido es 400 y deshace la aceptación.
`GET /api/v1/account-requests/acceptance-options` suma `paymentScheduleAvailable: true`.
