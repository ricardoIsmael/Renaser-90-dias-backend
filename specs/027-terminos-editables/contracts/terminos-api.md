# Contrato: términos y condiciones editables (spec 027, D-304)

Todo es **aditivo**. Un cliente anterior ignora lo nuevo y un servidor anterior no tiene estas rutas: la app usa entonces el texto que lleva dentro.

## La persona

### `GET /api/v1/onboarding/terms`  (USE_APP; sin interruptor: la app lo lee al abrir)
```json
{ "formation": { "code": "ESCUELA", "name": "Escuela Renaser" },
  "version": { "id": "…", "formation": "ESCUELA", "state": "PUBLICADA", "number": 2, "title": "Términos y condiciones", "introduction": "…",
               "clauses": ["…", "…"], "checkboxText": "He leído y acepto…", "note": "Cambió el reembolso", "asksNewAcceptance": true,
               "publishedAt": "…", "updatedAt": "…" },
  "pending": "NONE | FIRST_TIME | NEW_VERSION | FIRST_IN_FORMATION", "blocks": false, "lastAcceptedNumber": 1, "changeNote": "Cambió el reembolso" }
```
- Los términos son los **vigentes de SU formación** (la del aprendiz; la activa del personal; la Escuela para Administración y Alquimista). Nunca un borrador ni los de otra formación.
- `{formacion}` y `{duracion}` del texto ya vienen reemplazados con el nombre de su formación y su duración.
- `version` es `null` si su formación todavía no publicó términos (la app usa el texto que lleva). `blocks` = `NEW_VERSION` o `FIRST_IN_FORMATION`: la app no deja usar el resto hasta aceptar.

### `POST /api/v1/onboarding/milestones`  `{ "milestone": "TERMINOS", "termsVersionId": "…" }`  (USE_APP)
El hito de siempre; con `TERMINOS` además deja la **constancia** (persona, versión, hora y la firma que dibujó), todo en la misma transacción. `termsVersionId` es opcional: sin él (una app anterior) se anota la
vigente de su formación. **409** `Los términos cambiaron mientras leías: léelos de nuevo.` si esa versión ya no es la vigente (nada queda registrado contra la equivocada).

### `GET /api/v1/onboarding/terms/accepted`  → `{ "version": {…}, "acceptedAt": "…", "signatureUrl": "https://…" | null }`  ·  **404** si nunca aceptó
La versión que la persona aceptó por última vez (la de su formación; si no, la más reciente de cualquiera), **aunque haya una más nueva**, con los marcadores `{formacion}` y `{duracion}` ya reemplazados.
`signatureUrl` es el enlace de lectura (vence a los 15 minutos) de la firma que dibujó **ella** al aceptar; `null` si no quedó firma (aceptó antes de las versiones sin firma guardada) o el archivo ya no está.
Solo ve lo suyo: la persona nunca recibe la constancia de otra. La app lo muestra en Yo → Mi onboarding → Términos y condiciones (P3, FR-023).
El texto de la versión que aceptó por última vez (de su formación; si no, la última de cualquiera), aunque haya una más nueva.

## Administración (ADMIN y ALQUIMISTA activos; el guard real es `GuardiaDeTerminos`, y responde 403 a cualquier otro rol o a una cuenta suspendida)

| Ruta | Qué hace |
|---|---|
| `GET /api/v1/admin/terms` | Las tres formaciones: `{ formation, name, current, hasDraft, acceptedCount, needsReview }` (`needsReview` = sin ninguna versión publicada). |
| `GET /api/v1/admin/terms/{formation}` | `{ formation, name, current, draft, versions: [{ version, acceptedCount, publishedByName }] }`, la más nueva primero. |
| `GET /api/v1/admin/terms/{formation}/versions/{n}` | El texto de esa versión. 404 si no existe. |
| `GET /api/v1/admin/terms/{formation}/versions/{n}/acceptances` | `{ number, accepted: [{id, name}], missing: [{id, name}] }`: quiénes aceptaron esa versión (o una posterior) y quiénes de la formación todavía no. |
| `PUT /api/v1/admin/terms/{formation}/draft` | `{ title, introduction, clauses, checkboxText, baseNumber }`. Crea o actualiza el borrador (a lo sumo uno por formación). **409** si `baseNumber` ya no es la versión vigente (otra persona publicó); **400** si pasa de los límites. |
| `DELETE /api/v1/admin/terms/{formation}/draft` | Descarta el borrador (204, aunque no hubiera). |
| `POST /api/v1/admin/terms/{formation}/versions/{n}/restore` | Pone esa versión como borrador (reemplaza el que hubiera). No se publica sola. |
| `POST /api/v1/admin/terms/{formation}/publish` | `{ note, asksNewAcceptance, baseNumber }`. El borrador pasa a ser la versión siguiente. **400** con cláusulas vacías / sin cláusulas / nota > 500; **409** sin borrador, sin cambios respecto de la vigente (`No hay cambios respecto de la versión vigente.`) o con la base vencida. |
| `GET /api/v1/admin/terms/people/{id}` | La constancia de una persona: `[{ formation, number, acceptedAt, signatureUrl }]` (la firma vence a los 15 min). |

## Con la spec 025
- `GET /api/v1/account-requests/acceptance-options` suma `termsPublished` por formación.
- `POST /api/v1/account-requests/{id}/approve` responde **409** si alguna de las formaciones elegidas no tiene términos publicados (FR-021). Administración y Alquimista, que no eligen formación, no se ven afectados.

## Para otras specs
`users.api.TerminosPublicados` (lo implementa `onboarding`) y `users.api.IntegrantesDeFormacionFinder` (lo implementa `users`) son las dos costuras entre módulos; ninguna crea un ciclo.
