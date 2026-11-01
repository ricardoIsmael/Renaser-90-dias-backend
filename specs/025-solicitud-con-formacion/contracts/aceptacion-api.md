# Contrato: aceptar una solicitud con formación (spec 025, D-302)

Todo es **aditivo**. Un cliente anterior ignora los campos nuevos y un cuerpo sin ellos se acepta como siempre (Escuela, su duración, sin diagnóstico). Los errores usan el cuerpo de siempre (`{ message }`).

## Aceptar

### `POST /api/v1/account-requests/{id}/approve`  (APPROVE_ACCOUNT_REQUEST; ADMIN y ALQUIMISTA activos)
```json
{ "role": "TRAINEE", "rolPersonalizadoId": null,
  "formations": ["DIPLOMADO"], "durationDays": 365, "withDiagnosis": true }
```
- `role`, `rolPersonalizadoId`: como en D-265 y D-272. Sin cuerpo, o sin `role`, entra como `TRAINEE`.
- `formations` (códigos): aprendiz, **una**; mentor, guía de aprendiz y líder de mentores, **una o más**; Administración y Alquimista, se **ignora** (ven las tres). Vacío o ausente = Escuela, para la app anterior.
- `durationDays`: solo el aprendiz; obligatoria si la formación ofrece más de una duración; ausente si ofrece una (se aplica sola). Para el personal se ignora.
- `withDiagnosis`: `true` = la ficha con diagnóstico en lugar de la estándar. Vale para **cualquier rol** (025-1 B).
- **204** si se aceptó. Todo ocurre en UNA transacción: si algo falla no queda nada cambiado.
- **400** `Un aprendiz entra a una sola formación. Elige una.` · `Elige la duración del programa en «…».` · `«…» no ofrece esa duración.` · `«…» está desactivada: no recibe personas nuevas.` · `No existe la formación «…».`
- **409** `La solicitud ya fue decidida: …` (otra persona la decidió primero; la lectura lleva el candado de la fila) · `Todavía no hay un diagnóstico publicado: acepta a la persona sin diagnóstico.`
- **403** a cualquier otro rol o cuenta suspendida; **404** si la solicitud no existe.

### `GET /api/v1/account-requests/acceptance-options`  (APPROVE_ACCOUNT_REQUEST)
```json
{ "formations": [ { "code": "DIPLOMADO", "name": "Diplomado Renaser", "description": "…",
                    "durationsDays": [90, 365], "durationLabels": ["3 meses", "1 año"], "defaultDurationDays": 90 } ],
  "diagnosisAvailable": true }
```
Solo las formaciones **activas**, en su orden. `diagnosisAvailable` = hay una implementación de `users.api.FichaAlAceptar` (spec 026) **y** un diagnóstico publicado; sin la 026 es `false`.
Un servidor anterior responde 404: la app deja la hoja solo con el rol y **no manda formaciones**.

## Listar

### `GET /api/v1/account-requests?status=&formation=&page=&size=`
`formation` (opcional): las **pendientes siempre** (todavía no tienen formación) y las aceptadas que la eligieron. Cada fila suma:
```json
{ "reviewedByName": "Ana", "reviewedAt": "2026-10-31T15:00:00Z",
  "acceptance": { "formations": ["DIPLOMADO"], "durationDays": 365, "role": "TRAINEE",
                  "customRoleId": null, "customRoleName": null, "withDiagnosis": true } }
```
`acceptance` es `null` en una pendiente o rechazada. Las aceptadas antes de las formaciones traen `formations: ["ESCUELA"]`, `role: null`, `durationDays: null`, `withDiagnosis: false` y sin rol personalizado.
`customRoleId` y `customRoleName` (V142): si se aceptó con un rol personalizado, su id y **el nombre que tenía ese día** (una foto: si después se renombra, esto no cambia; si se borra, el id queda en `null` y el nombre se conserva). `role` sigue siendo el rol base. En el resto, `null`.

## La ficha de la persona (US4, escenario 1)

### `GET /api/v1/admin/trainees/{id}`  (MANAGE_TRAINEES)  — campos nuevos, aditivos
```json
{ "...": "...", "acceptance": { "formations": ["DIPLOMADO"], "durationDays": 365, "role": "TRAINEE", "customRoleId": null,
                               "customRoleName": null, "withDiagnosis": true },
  "acceptedByName": "Ana", "acceptedAt": "2026-10-31T15:00:00Z" }
```
`acceptance` tiene **exactamente la misma forma** que en la lista (`AccountRequestResponse.Acceptance`), y `acceptedByName` / `acceptedAt` son `reviewedByName` / `reviewedAt` de esa solicitud. Los tres faltan (`null`) si la cuenta no entró por una solicitud (la invitó Administración, o es anterior al panel) o su solicitud no está aceptada; `acceptedByName` es `null` si quien aceptó ya no existe. Es lo que se decidió **ese día**: la formación, la duración y el rol vivos de la persona están en `formation`, `programDuration` y `role`, y pueden haber cambiado.

## Cambiar la duración de quien ya empezó (025-2 A)

### `POST /api/v1/admin/trainees/{id}/duracion`  `{ "duracionDias": 365, "motivo": "Pidió más tiempo" }`  → 204  (MANAGE_TRAINEES)
Solo aprendices. Conserva su día; el último pasa a ser el nuevo. Queda en `ajustes_dia_programa` (quién, cuándo, de cuánto a cuánto).
**409** si no es aprendiz, ya terminó su programa, ya tiene esa duración o su día actual no cabe en la nueva (`nueva <= día`); **400** si falta el motivo (3 a 280) o su formación no ofrece esa duración; **404** si no existe.
`GET /api/v1/admin/trainees/{id}` → `lastDayAdjustment` suma `previousDuration` y `newDuration` (solo cuando el ajuste fue un cambio de duración).
Quien **todavía no empezó** cambia de duración (y de formación) con `POST /admin/trainees/{id}/formacion` de la 022, que no deja historia.

## Para la spec 026 (la ficha con diagnóstico)
La 025 no guarda la marca de la persona. Hace falta, en `onboarding`, un bean que implemente `users.api.FichaAlAceptar`:
```java
boolean hayDiagnosticoPublicado();
void conDiagnostico(UserId persona, UserId quien);   // delega en FichaDeLaPersonaUseCase.asignarDiagnostico(persona, quien)
```
Se invoca dentro de la transacción de aceptar. `FichaAlAceptarImplementadaTest` rompe el build si, con la 026 integrada (existe `onboarding.api.FichaDeLaPersonaUseCase`), nadie lo implementa.
