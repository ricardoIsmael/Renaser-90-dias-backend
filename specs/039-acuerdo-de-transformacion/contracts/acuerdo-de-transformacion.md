# Contrato: Acuerdo de transformación (spec 039, D-312)

Todo es **aditivo**. Un cliente anterior ignora lo nuevo y un servidor anterior no lo manda: la app no dibuja entonces «Mi aceptación» ni el cronograma.

## `GET /api/v1/onboarding/terms`  (suma `agreement`)

```json
{ "formation": {...}, "version": {...}, "pending": "FIRST_TIME", "blocks": false, "lastAcceptedNumber": null, "changeNote": null,
  "agreement": {
    "participantName": "Ana María Pérez Soto",
    "program": "Formación RENASER · 90 días",
    "startDate": "2026-11-03",
    "endDate": "2027-01-31",
    "started": false,
    "schedule": {
      "currency": "PEN",
      "installments": [ { "id": "…", "dueDate": "2026-11-15", "amount": 500.00, "status": "PENDING" } ],
      "fingerprint": "9f2c…"
    }
  } }
```
- `agreement` es `null` si la formación de la persona no tiene términos publicados.
- `participantName`: el nombre completo del perfil. `program`: `«{Formación|Diplomado|Certificación} RENASER · {90 días|3 meses|1 año}»`; para quien no tiene programa, solo `«Formación RENASER»`.
- `startDate` / `endDate`: `null` si la persona no tiene programa que mostrar (personal sin fila o sin activar). Con programa activado son las reales (`fechaInicio` y `ultimaFechaDelPrograma`); sin activar, las que tendrá: mañana en su zona (spec 036) y mañana + duración − 1. `started`: si ya activó.
- `schedule` solo con `pending = FIRST_TIME`, cronograma sin confirmar (o desactualizado) y misma forma que `payments` de «Mi ingreso» (`dueDate`, `amount`, `status`). `fingerprint` es la huella de la spec 032 (moneda, fechas y montos).

## `POST /api/v1/onboarding/milestones`  (suma dos campos opcionales)

```json
{ "milestone": "TERMINOS", "termsVersionId": "…", "agreementStartDate": "2026-11-03", "scheduleFingerprint": "9f2c…" }
```
- `agreementStartDate`: el inicio que la persona vio en «Mi aceptación». Si ya no es el de ahora → **409** `Pasó la medianoche y tus fechas cambiaron: léelas de nuevo.` (nada se registra).
- `scheduleFingerprint`: la huella del cronograma que la persona vio y aceptó. Si ya no es la vigente (o ya no tiene cronograma) → **409** `Tu cronograma cambió mientras leías: revísalo de nuevo.` Si coincide: en la misma transacción queda confirmado el cronograma (nombre completo del perfil, hora y huella), como `POST /me/ingreso/confirmar` de la spec 032.
- Sin ellos (app anterior, quien ya estaba dentro, sin cronograma) no se confirma nada; la constancia guarda igual el programa, el inicio y el fin calculados en ese momento.

## `GET /api/v1/onboarding/terms/accepted`  (suma `agreement`)

`"agreement": { "program": "…", "startDate": "…", "endDate": "…", "scheduleConfirmed": true }` o `null` si la aceptación es anterior a esta spec.

## `GET /api/v1/admin/terms/people/{id}`  (suma a cada elemento)

`program`, `startDate`, `endDate` y `scheduleConfirmed` (nulos / `false` en las aceptaciones anteriores).

## Base de datos (V162)

`aceptaciones_de_terminos` suma `programa_mostrado text`, `inicio_mostrado date`, `fin_mostrado date`, `cronograma_huella text` (todas nulas en lo anterior), y se insertan tres filas de `terminos_versiones` (ids fijos `00000000-0000-4000-8000-0000000162NN`).
