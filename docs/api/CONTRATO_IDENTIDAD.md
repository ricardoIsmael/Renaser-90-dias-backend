# Contrato real de la API — `users`, `onboarding`, `phasecontracts`

Generado leyendo el código fuente en vivo (controllers, DTOs, casos de uso, servicios y
dominio) de:

- `src/main/java/com/renaser/os/users/infrastructure/adapter/in/rest/**`
- `src/main/java/com/renaser/os/onboarding/infrastructure/adapter/in/rest/**`
- `src/main/java/com/renaser/os/phasecontracts/infrastructure/adapter/in/rest/**`

**Base URL local:** `http://localhost:8080` (sin context-path, ver `application.yaml` —
no hay `server.port` ni `server.servlet.context-path` configurados, el puerto es el
8080 por defecto de Spring Boot).

**Regla global de autenticación (leer antes que nada):** `SecurityConfig` deja
`/api/v1/**` con `permitAll()` — **hoy no hay ningún filtro HTTP que bloquee nada**.
Toda la autorización real pasa por el header `X-Actor-Id` (un UUID), que cada caso de
uso usa para cargar el `User`/actor real desde la base y decidir ahí adentro (rol,
estado activo/suspendido, dueño del recurso). Esto es **temporal** — todos los
controllers lo dicen en su Javadoc: cuando se resuelva el JWT real de Supabase
(bloqueante B-2), `X-Actor-Id` se reemplaza por `@AuthenticationPrincipal`. Para probar
hoy: mandá siempre `X-Actor-Id: <uuid-de-un-usuario-que-exista-en-la-tabla-usuarios>`
en cada request que lo pida — si el UUID no existe en `usuarios`, da **404**, no 401/403.

**Formato de error estándar** (`ApiErrorResponse`, `shared/web/ApiErrorResponse.java`):

```json
{ "message": "texto del error", "timestamp": "2026-08-26T10:00:00Z" }
```

Mapeo de excepción de dominio → HTTP (`shared/web/GlobalExceptionHandler.java`):

| Excepción | HTTP | Cuándo |
|---|---|---|
| `NotAuthorizedException` | 403 | Rol sin permiso, o cuenta `SUSPENDED`, o recurso ajeno |
| `NoSuchElementException` | 404 | Entidad no encontrada |
| `IllegalArgumentException` / `ConstraintViolationException` | 400 | Validación de dominio, o comando self-validating fallido |
| `IllegalStateException` | 409 | Conflicto de estado (ya decidido, ya firmado, ya en curso) |
| `RateLimitExceededException` | 429 | Límite de solicitudes excedido |
| `MethodArgumentNotValidException` (`@Valid` en `@RequestBody`) | 400 | Validación Bean Validation del DTO de entrada |
| Falta `@RequestHeader`/`@RequestParam` | 400 | `"Falta el header obligatorio 'X-Actor-Id'"` / `"Falta el parametro obligatorio '...'"` |
| `DataIntegrityViolationException` (UNIQUE/CHECK de Postgres) | 409 | Carrera entre dos requests concurrentes |

---

## Módulo `users`

### 1. `POST /api/v1/users/me` — mi perfil

> **Trampa:** es **POST**, no GET, a pesar de ser una consulta ("actor" viaja en el
> header, no hay body que justifique POST — así está el código).

- **Headers:** `X-Actor-Id` (obligatorio).
- **Request body:** ninguno.
- **Response body** (`UserResponse`):
  ```json
  {
    "id": "uuid", "email": "string", "role": "ALCHEMIST|ADMIN|MENTOR_LEAD|MENTOR|TRAINEE",
    "status": "ACTIVE|SUSPENDED", "fullName": "string", "avatarUrl": "string|null",
    "bio": "string|null", "department": "string|null"
  }
  ```
- **Código de éxito:** 200 (sin `@ResponseStatus`, valor de retorno directo).
- **Quién puede llamarlo:** cualquier usuario activo, sobre sí mismo. `GetMyProfileUseCase.getMyProfile` solo llama a `RequireActiveUserGuard.of(actorId)` — no hay chequeo de rol.
- **Errores:** 404 si `X-Actor-Id` no existe (`"Usuario no encontrado: <id>"`); 403 si está `SUSPENDED` (`"Cuenta suspendida"`); 400 si falta el header o el UUID es inválido (`"UserId no es un UUID valido: ..."`).
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/users/me \
    -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
  ```

### 2. `PATCH /api/v1/users/me` — actualizar mi perfil

- **Headers:** `X-Actor-Id` (obligatorio).
- **Request body** (`UpdateMyProfileRequest`, sin `@Valid` en el controller — los 4 campos son opcionales, `null` = "no cambiar"):
  ```json
  { "fullName": "string|null", "avatarUrl": "string|null", "bio": "string|null", "department": "string|null" }
  ```
  **Trampa:** a propósito NO tiene `role`/`programDay`/`coherenceScore`/`leaguePoints`/`currentPhase` — ni el compilador los deja mandar (mass-assignment blindado).
- **Response body:** ninguno.
- **Código de éxito:** 204 No Content.
- **Quién puede llamarlo:** el propio usuario activo (`actorId` del header = quien se actualiza a sí mismo; no hay forma de actualizar a otro por acá).
- **Errores:** igual que `/me` (404 actor inexistente, 403 suspendido).
- **curl:**
  ```bash
  curl -X PATCH http://localhost:8080/api/v1/users/me \
    -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
    -H "Content-Type: application/json" \
    -d '{"fullName":"Ana Torres","bio":"Aprendiz de la cohorte 2026-Q3"}'
  ```

### 3. `POST /api/v1/users/invite` — invitar/crear usuario con rol explícito

- **Headers:** `X-Actor-Id` (obligatorio, debe ser ADMIN/ALCHEMIST).
- **Request body** (`InviteUserRequest`, con `@Valid`):
  ```json
  {
    "supabaseUserId": "uuid (string, @NotBlank)",
    "email": "string (@NotBlank @Email)",
    "fullName": "string (@NotBlank)",
    "role": "ALCHEMIST|ADMIN|MENTOR_LEAD|MENTOR|TRAINEE (@NotNull)"
  }
  ```
- **Response body** (`UserIdResponse`): `{ "userId": "uuid" }`
- **Código de éxito:** 201 Created (`HttpStatus.CREATED` explícito).
- **Quién puede llamarlo:** solo `actor.canManageRoles()` = ADMIN o ALCHEMIST (`User.invite` → `requireRoleManager`). El actor además debe estar activo (`RequireActiveUserGuard` corre primero).
- **Errores:** 403 `"Solo ADMIN/ALCHEMIST cambian roles"` si el actor no es ADMIN/ALCHEMIST; 403 `"Cuenta suspendida"`; 404 actor inexistente; 400 si `supabaseUserId` no es un UUID válido o falta algún campo `@NotBlank`.
- **Efecto colateral:** si `role == MENTOR`, crea automáticamente un `MentorProfile` vacío (nivel N0, estado GREEN).
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/users/invite \
    -H "X-Actor-Id: <uuid-admin-o-alchemist>" \
    -H "Content-Type: application/json" \
    -d '{
      "supabaseUserId": "22222222-2222-2222-2222-222222222222",
      "email": "nuevo.mentor@renaser.com",
      "fullName": "Carlos Mentor",
      "role": "MENTOR"
    }'
  ```

### 4. `PATCH /api/v1/users/{id}/role` — cambiar el rol de otro usuario

- **Headers:** `X-Actor-Id` (obligatorio, debe ser ADMIN/ALCHEMIST).
- **Path variable:** `id` = UUID del usuario objetivo.
- **Request body** (`UpdateUserRoleRequest`, con `@Valid`): `{ "newRole": "ALCHEMIST|ADMIN|MENTOR_LEAD|MENTOR|TRAINEE" }`
- **Response body:** ninguno.
- **Código de éxito:** 204 No Content.
- **Quién puede llamarlo:** ADMIN/ALCHEMIST (`User.changeRole` → `requireRoleManager`).
- **Trampa:** el usuario **objetivo** se carga SIN chequear si está activo/suspendido (`UserAccountService.requireUser`, comentario explícito en el código: "un usuario suspendido puede seguir siendo el destino de una operación") — solo el **actor** pasa por `RequireActiveUserGuard`.
- **Errores:** 404 `"Usuario no encontrado: <id>"` si el `id` del path no existe; 403 si el actor no es ADMIN/ALCHEMIST o está suspendido.
- **Efecto colateral:** igual que `/invite`, si `newRole == MENTOR` y no tiene perfil, se crea uno vacío. Migrar datos entre perfiles al cambiar de rol **no está implementado a propósito** (fuera de alcance documentado).
- **curl:**
  ```bash
  curl -X PATCH http://localhost:8080/api/v1/users/22222222-2222-2222-2222-222222222222/role \
    -H "X-Actor-Id: <uuid-admin-o-alchemist>" \
    -H "Content-Type: application/json" \
    -d '{"newRole": "MENTOR_LEAD"}'
  ```

### 5. `POST /api/v1/account-requests` — alta pública (autoregistro)

> **PUBLIC_ENDPOINT** — el único de los tres módulos que NO pide `X-Actor-Id` (ver
> comentario `PUBLIC_ENDPOINT` en el controller).

- **Headers:** ninguno obligatorio.
- **Request body** (`SubmitAccountRequestRequest`, con `@Valid`):
  ```json
  {
    "supabaseUserId": "uuid (string, @NotBlank)",
    "email": "string (@NotBlank @Email)",
    "fullName": "string (@NotBlank)",
    "phone": "string (@NotBlank)",
    "city": "string|null"
  }
  ```
  **Trampa:** sin campo `role` a propósito (blindaje anti mass-assignment). `supabaseUserId` viaja como `String` pero **debe ser un UUID válido**: `AccountRequestService.submit` hace `UserId.of(command.supabaseUserId())`, que parsea con `UUID.fromString` y tira `IllegalArgumentException` (400) si no lo es.
- **Response body** (`AccountRequestIdResponse`): `{ "accountRequestId": "uuid" }`
- **Código de éxito:** **202 Accepted** (no 201, aunque crea el recurso — así está en el código).
- **Quién puede llamarlo:** cualquiera, sin autenticar.
- **Errores:** 429 `"Limite de solicitudes por hora excedido para IP <ip>"` — 60 solicitudes/hora por IP (`RATE_LIMIT_PER_HOUR = 60`, contado en Postgres vía `countSubmittedFromIpSince`); 400 por validación de campos.
- **Detalle interno útil para depurar:** el servicio registra una compensación transaccional — si la fila de `AccountRequest` no llega a persistirse, se intenta borrar el usuario ya creado en Supabase Auth (`SupabaseAdminAuthPort.deleteUser`). Hoy ese puerto es un **adaptador NoOp** (`NoOpSupabaseAdminAuthAdapter`, sin credenciales de Supabase Admin API) — solo loguea un `WARN`, no borra nada de verdad.
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/account-requests \
    -H "Content-Type: application/json" \
    -d '{
      "supabaseUserId": "33333333-3333-3333-3333-333333333333",
      "email": "aprendiz.nuevo@renaser.com",
      "fullName": "Sofia Aprendiz",
      "phone": "+51987654321",
      "city": "Lima"
    }'
  ```

### 6. `POST /api/v1/account-requests/{id}/approve` — aprobar solicitud

- **Headers:** `X-Actor-Id` (obligatorio, debe ser ADMIN/ALCHEMIST).
- **Path variable:** `id` = UUID de la solicitud (`AccountRequestId`).
- **Request body:** ninguno.
- **Response body:** ninguno.
- **Código de éxito:** 204 No Content.
- **Quién puede llamarlo:** ADMIN/ALCHEMIST (`AccountRequest.approve` → `requireManager` → `actor.canManageRoles()`).
- **Errores:** 404 `"Solicitud no encontrada: <id>"`; 403 `"Solo ADMIN/ALCHEMIST deciden solicitudes de alta"`; **409** `"La solicitud ya fue decidida: <status>"` si ya estaba `APPROVED`/`REJECTED` (`requirePending()` → `IllegalStateException`).
- **Efecto colateral, en UNA sola transacción:** crea el `User` (fuerza rol `TRAINEE`), crea la fila de `ParticipacionPrograma` (día 0, `programaActivadoEn = null` — el reloj del programa arranca **pausado**, se activa después con primer login + Ficha + Términos, fuera de este alcance), y marca la solicitud `APPROVED`.
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/account-requests/<accountRequestId>/approve \
    -H "X-Actor-Id: <uuid-admin-o-alchemist>"
  ```

### 7. `POST /api/v1/account-requests/{id}/reject` — rechazar solicitud

- **Headers:** `X-Actor-Id` (obligatorio, ADMIN/ALCHEMIST).
- **Path variable:** `id` = UUID de la solicitud.
- **Request body** (`RejectAccountRequestRequest`, con `@Valid`): `{ "reason": "string (@NotBlank)" }`
- **Response body:** ninguno.
- **Código de éxito:** 204 No Content.
- **Quién puede llamarlo:** ADMIN/ALCHEMIST.
- **Errores:** igual que `approve` (404, 403, 409 `"La solicitud ya fue decidida: <status>"`); 400 si `reason` viene vacío.
- **Efecto colateral:** libera el email intentando `deleteUser` en Supabase (mismo adaptador NoOp que en el alta — no borra de verdad hoy).
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/account-requests/<accountRequestId>/reject \
    -H "X-Actor-Id: <uuid-admin-o-alchemist>" \
    -H "Content-Type: application/json" \
    -d '{"reason": "Datos de contacto inconsistentes"}'
  ```

### 8. `PATCH /api/v1/users/{mentorId}/mentor-profile` — actualizar perfil de mentor

> **Trampa:** la ruta lleva `{mentorId}` en el **path**, distinto del `X-Actor-Id` del
> header — no es "mi perfil de mentor", es el perfil de un mentor específico.

- **Headers:** `X-Actor-Id` (obligatorio).
- **Path variable:** `mentorId` = UUID del mentor.
- **Request body** (`UpdateMentorProfileRequest`, **sin** `@Valid` en el controller — campos `null` = "no cambiar"):
  ```json
  { "newLevel": "N0|N1|N2|N3|null", "newOperationalStatus": "GREEN|YELLOW|RED|null", "newBio": "string|null" }
  ```
- **Response body:** ninguno.
- **Código de éxito:** 204 No Content.
- **Quién puede llamarlo (regla mixta, no es "todo o nada"):**
  - `newLevel` y/o `newOperationalStatus` presentes → **solo ADMIN/ALCHEMIST** (`requireRoleManager`).
  - `newBio` presente → **el propio mentor** (`actorId == mentorId`) **o** ADMIN/ALCHEMIST (`requireSelfOrRoleManager`).
  - Se puede mandar `newBio` sola sin ser ADMIN, pero si además mandás `newLevel` en la misma request sin ser ADMIN, la request entera falla en 403.
- **Errores:** 404 `"Perfil de mentor no encontrado: <mentorId>"` si el mentor no tiene `MentorProfile` (ver alta previa en `/invite` o `/role`); 403 `"Solo ADMIN/ALCHEMIST cambian nivel o estado operativo de un mentor"`; 403 `"Solo el propio mentor o ADMIN/ALCHEMIST editan esta bio"`.
- **curl (el propio mentor cambia su bio):**
  ```bash
  curl -X PATCH http://localhost:8080/api/v1/users/<mentorId>/mentor-profile \
    -H "X-Actor-Id: <mentorId-mismo>" \
    -H "Content-Type: application/json" \
    -d '{"newBio": "5 años acompañando cohortes de Fase II"}'
  ```

### 9. `GET /api/v1/mentor/activate-tracking` — estado de mi seguimiento personal

> **Trampa de path:** vive bajo `/api/v1/mentor/...` aunque pertenece al agregado
> `participante` de `users` — el path se conserva tal cual porque así lo consume ya la
> app móvil (contrato viejo).

- **Headers:** `X-Actor-Id` (obligatorio).
- **Response body** (`SelfTrackingStatusResponse`): `{ "active": true|false }`
- **Código de éxito:** 200.
- **Quién puede llamarlo:** cualquier usuario activo, sobre sí mismo. **A diferencia de `activate`/`deactivate`, NO exige rol de staff** — un TRAINEE puede consultar (su participación es obligatoria, consultarla es legítimo).
- **Errores:** 404/403 estándar de actor.
- **curl:**
  ```bash
  curl http://localhost:8080/api/v1/mentor/activate-tracking \
    -H "X-Actor-Id: <uuid-mentor>"
  ```

### 10. `POST /api/v1/mentor/activate-tracking` — activar seguimiento personal (staff)

- **Headers:** `X-Actor-Id` (obligatorio, debe ser MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST).
- **Request body:** ninguno.
- **Response body** (`ActivateSelfTrackingResponse`): `{ "traineeProfileId": "uuid", "programDay": 1 }`
- **Código de éxito:** 200.
- **Quién puede llamarlo:** MENTOR, MENTOR_LEAD, ADMIN o ALCHEMIST — **TRAINEE explícitamente afuera** (su participación ya es obligatoria por otra vía). Self-only: no hay forma de activarlo para otro usuario.
- **Errores:** 403 `"El seguimiento personal opcional es solo para MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST"`; **409** `"Ya activaste tu seguimiento personal de habitos y objetivos"` si ya estaba activo (`IllegalStateException`).
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/mentor/activate-tracking \
    -H "X-Actor-Id: <uuid-mentor>"
  ```

### 11. `DELETE /api/v1/mentor/activate-tracking` — desactivar seguimiento personal

- **Headers:** `X-Actor-Id` (obligatorio, mismo set de roles que activar).
- **Response body** (`DeactivateSelfTrackingResponse`): `{ "deactivated": true|false }`
- **Código de éxito:** 200. **Idempotente**: si ya estaba inactivo, responde `{"deactivated": false}`, no es un error.
- **Errores:** 403 mismo mensaje que activar.
- **curl:**
  ```bash
  curl -X DELETE http://localhost:8080/api/v1/mentor/activate-tracking \
    -H "X-Actor-Id: <uuid-mentor>"
  ```

### 12. `PUT /api/v1/participants/{traineeId}/mentor` — asignar/reasignar mentor

- **Headers:** `X-Actor-Id` (obligatorio, ADMIN/ALCHEMIST).
- **Path variable:** `traineeId` = UUID del aprendiz.
- **Request body** (`AssignMentorRequest`, con `@Valid`): `{ "mentorId": "uuid (@NotNull)" }`
- **Response body:** ninguno.
- **Código de éxito:** 204 No Content.
- **Quién puede llamarlo:** solo ADMIN/ALCHEMIST (`actor.canManageRoles()`).
- **Errores:** 403 `"Solo ADMIN/ALCHEMIST asignan mentor a un participante"`; 404 `"El usuario <mentorId> no tiene perfil de mentor"` si el `mentorId` no tiene `MentorProfile`; 404 `"Participante no inscripto en el programa: <traineeId>"` si el trainee no tiene fila de `ParticipacionPrograma`.
- **Trampa:** NO actualiza ningún contador de mentores (`total_trainees_managed` fue eliminado del esquema a propósito, es derivable con `COUNT`).
- **curl:**
  ```bash
  curl -X PUT http://localhost:8080/api/v1/participants/<traineeId>/mentor \
    -H "X-Actor-Id: <uuid-admin-o-alchemist>" \
    -H "Content-Type: application/json" \
    -d '{"mentorId": "<mentorId-con-perfil>"}'
  ```

---

## Módulo `onboarding`

Todos los endpoints de este módulo son **self-service**: el `X-Actor-Id` del header es
siempre el dueño del recurso, no hay concepto de "actuar en nombre de otro". La única
regla de autorización es "cuenta activa" (403 si `SUSPENDED`, 404 si no existe) — **sin
chequeo de rol**, cualquier rol puede usar onboarding.

### 13. `GET /api/v1/onboarding/state` — obtener mi estado de onboarding

- **Headers:** `X-Actor-Id`.
- **Response body** (`EstadoOnboardingResponse`):
  ```json
  {
    "userId": "uuid", "currentFlow": "string|null", "currentSection": "string|null",
    "currentStep": 0, "flowProgress": "string(json crudo)|null",
    "termsAcceptedAt": "instant|null", "pactAcceptedAt": "instant|null",
    "pactSignedAt": "instant|null", "rocksSyncAcceptedAt": "instant|null",
    "startedAt": "instant", "lastActivityAt": "instant",
    "completed": false, "completedAt": "instant|null"
  }
  ```
- **Código de éxito:** 200.
- **Trampa:** si es la primera vez que el usuario abre onboarding, este GET **crea la fila** (`ObtenerEstadoOnboardingUseCase`, comentario explícito: "el primer GET crea la fila"). No hay un endpoint separado "iniciar onboarding".
- **curl:**
  ```bash
  curl http://localhost:8080/api/v1/onboarding/state -H "X-Actor-Id: <uuid-trainee>"
  ```

### 14. `PUT /api/v1/onboarding/state` — avanzar cursor de UI

- **Headers:** `X-Actor-Id`.
- **Request body** (`AvanzarEstadoRequest`, sin `@Valid` explícito en el DTO pero sí `@Valid @RequestBody` en el controller — todos los campos son opcionales, null = "no cambiar"):
  ```json
  { "flow": "string|null", "section": "string|null", "step": 0, "flowProgress": "string(json)|null" }
  ```
  `flowProgress` es JSON crudo, **dato opaco** — el backend no lo interpreta ni valida.
- **Response body:** `EstadoOnboardingResponse` (igual que el GET).
- **Código de éxito:** 200.
- **curl:**
  ```bash
  curl -X PUT http://localhost:8080/api/v1/onboarding/state \
    -H "X-Actor-Id: <uuid-trainee>" -H "Content-Type: application/json" \
    -d '{"flow": "onboarding_v90", "section": "ficha_personal", "step": 2}'
  ```

### 15. `POST /api/v1/onboarding/milestones` — aceptar un hito

- **Headers:** `X-Actor-Id`.
- **Request body** (`AceptarHitoRequest`, con `@Valid`): `{ "milestone": "TERMINOS|PACTO|PACTO_FIRMADO|ROCAS_SYNC" }`
- **Response body:** `EstadoOnboardingResponse`.
- **Código de éxito:** 200.
- **Trampa:** cada llamada **re-actualiza el timestamp** del hito (no es "una sola vez", es idempotente pero pisa la fecha anterior). `PACTO_FIRMADO` es la firma del **Pacto de Fase I dentro del onboarding** — no confundir con `phasecontracts` (que firma las fases II-IV).
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/onboarding/milestones \
    -H "X-Actor-Id: <uuid-trainee>" -H "Content-Type: application/json" \
    -d '{"milestone": "TERMINOS"}'
  ```

### 16. `POST /api/v1/onboarding/complete` — completar onboarding

- **Headers:** `X-Actor-Id`.
- **Request body:** ninguno.
- **Response body:** `EstadoOnboardingResponse` con `completed: true`.
- **Código de éxito:** 200.
- **Trampa:** es una acción **explícita** — no hay regla automática de "completo cuando respondiste todo + V90 aprobado" (decisión de alcance, sin confirmar). Idempotente: completar dos veces conserva el `completedAt` original.
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/onboarding/complete -H "X-Actor-Id: <uuid-trainee>"
  ```

### 17. `GET /api/v1/onboarding/questionnaire?flow={flow}` — obtener cuestionario

- **Headers:** `X-Actor-Id`.
- **Query param:** `flow` (`@RequestParam` obligatorio, ej. `onboarding_v90`).
- **Response body** (`CuestionarioResponse`):
  ```json
  {
    "flow": "onboarding_v90",
    "sections": [{
      "sectionKey": "string", "title": "string", "description": "string|null", "order": 0,
      "questions": [{
        "id": 1, "questionKey": "string", "text": "string",
        "type": "TEXTO|AREA_TEXTO|NUMERO|ESCALA|SELECCION_UNICA|SELECCION_MULTIPLE|AUDIO|FIRMA|CASILLA|FECHA|ARCHIVO",
        "scaleConfig": "string(json)|null", "required": true, "order": 0,
        "validationRules": "string(json)|null", "conditional": false, "parentQuestionId": null,
        "options": [{ "order": 0, "value": "string", "label": "string" }]
      }]
    }]
  }
  ```
- **Código de éxito:** 200.
- **Nota:** `actorId` acá solo sirve para exigir cuenta activa; el catálogo no es específico de un usuario. `id` de cada pregunta (`questionId`) es el que se usa después en `/answers`.
- **curl:**
  ```bash
  curl "http://localhost:8080/api/v1/onboarding/questionnaire?flow=onboarding_v90" \
    -H "X-Actor-Id: <uuid-trainee>"
  ```

### 18. `POST /api/v1/onboarding/answers` — guardar respuesta

- **Headers:** `X-Actor-Id`.
- **Request body** (`GuardarRespuestaRequest`, con `@Valid`):
  ```json
  {
    "questionId": 1,
    "textValue": "string|null", "numberValue": "number|null", "booleanValue": true,
    "scaleValue": 7, "jsonValue": "string(json)|null", "mediaId": null
  }
  ```
  **Trampa importante:** el DTO no valida cuál campo va con cuál tipo de pregunta — eso lo
  valida el **dominio** (`Respuesta.crear`), después de que el servicio carga la pregunta.
  Mapeo real tipo→slot (`Respuesta`, javadoc del agregado):
  | Tipo de pregunta | Campo obligatorio |
  |---|---|
  | `TEXTO`, `AREA_TEXTO`, `FECHA` (ISO-8601), `SELECCION_UNICA` | `textValue` |
  | `NUMERO` | `numberValue` |
  | `ESCALA` | `scaleValue` (1..10) |
  | `SELECCION_MULTIPLE` | `jsonValue` (array serializado) |
  | `CASILLA` | `booleanValue` |
  | `AUDIO`, `FIRMA`, `ARCHIVO` | `mediaId` (ningún valor tipado) |

  Mandar más de un valor tipado a la vez, o el valor equivocado para el tipo, da 400.
- **Response body** (`RespuestaResponse`):
  ```json
  {
    "id": 10, "questionId": 1, "textValue": null, "numberValue": null, "booleanValue": true,
    "scaleValue": null, "jsonValue": null, "mediaId": null,
    "acceptedAt": "instant|null", "answeredAt": "instant", "updatedAt": "instant"
  }
  ```
- **Código de éxito:** 200 (`HttpStatus.OK` explícito, no 201, aunque puede estar creando la fila — es un upsert).
- **Trampa:** es **upsert por `(usuarioId, preguntaId)`** — volver a mandar `questionId` ya respondido actualiza, no duplica.
- **Errores:** 404 `"Pregunta no encontrada: <id>"`; 400 `"Una respuesta de tipo <tipo> requiere exactamente el valor <slot> y ningun otro slot"`, `"Una respuesta admite un solo valor no nulo..."`, `"valorEscala debe estar entre 1 y 10: <n>"`, `"Una respuesta de tipo <tipo> requiere mediaId"`.
- **curl (pregunta tipo ESCALA):**
  ```bash
  curl -X POST http://localhost:8080/api/v1/onboarding/answers \
    -H "X-Actor-Id: <uuid-trainee>" -H "Content-Type: application/json" \
    -d '{"questionId": 5, "scaleValue": 8}'
  ```

### 19. `POST /api/v1/onboarding/media/upload-url` — paso 1 de subida: pedir URL firmada

- **Headers:** `X-Actor-Id`.
- **Request body** (`UrlSubidaMediaRequest`, con `@Valid`):
  ```json
  { "flow": "string|null", "questionKey": "string|null", "kind": "AUDIO|FIRMA|DOCUMENTO", "contentType": "string (@NotBlank, ej. audio/mp4)" }
  ```
- **Response body** (`UrlSubidaMediaResponse`): `{ "uploadUrl": "string", "bucket": "string", "path": "string" }`
- **Código de éxito:** 200.
- **⚠️ Trampa de entorno importante:** el puerto de almacenamiento (`AlmacenamientoPort`) hoy es un **adaptador NoOp** (`NoOpAlmacenamientoAdapter`, faltan credenciales AWS S3 — bloqueante D-34). `uploadUrl` en este entorno viene como `about:blank#pendiente-s3/<path>`, **no es una URL real donde se pueda hacer PUT**. Para probar el flujo end-to-end con archivo real hace falta configurar S3 primero; mientras tanto, el paso 2 (`registrar`) igual funciona si le mandás `bucket`/`path` a mano.
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/onboarding/media/upload-url \
    -H "X-Actor-Id: <uuid-trainee>" -H "Content-Type: application/json" \
    -d '{"flow": "onboarding_v90", "questionKey": "audio_fase1", "kind": "AUDIO", "contentType": "audio/mp4"}'
  ```
  **Usar del response:** `path` y `bucket` van en el body del paso 2.

### 20. `POST /api/v1/onboarding/media` — paso 2 de subida: confirmar/registrar

- **Headers:** `X-Actor-Id`.
- **Request body** (`RegistrarMediaRequest`, con `@Valid`):
  ```json
  {
    "flow": "string|null", "questionKey": "string|null", "kind": "AUDIO|FIRMA|DOCUMENTO",
    "bucket": "string (@NotBlank, del paso 1)", "path": "string (@NotBlank, del paso 1)",
    "mime": "string|null", "sizeBytes": 123456, "durationSeconds": 12.5, "metadata": "string(json)|null"
  }
  ```
- **Response body** (`MediaResponse`):
  ```json
  {
    "id": 7, "flow": "string", "questionKey": "string", "kind": "AUDIO",
    "bucket": "string", "path": "string", "mime": "string", "sizeBytes": 123456,
    "durationSeconds": 12.5, "metadata": "string|null", "createdAt": "instant"
  }
  ```
- **Código de éxito:** 201 Created.
- **Usar del response:** el `id` (`mediaId`) se usa en `/answers` (campo `mediaId`) o en `/v90-recordings` (campo `mediaId`).
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/onboarding/media \
    -H "X-Actor-Id: <uuid-trainee>" -H "Content-Type: application/json" \
    -d '{
      "flow": "onboarding_v90", "questionKey": "audio_fase1", "kind": "AUDIO",
      "bucket": "onboarding-media", "path": "onboarding/<uuid>/audio/<uuid-generado-por-el-paso-1>",
      "mime": "audio/mp4", "sizeBytes": 245000, "durationSeconds": 12.5
    }'
  ```

### 21. `POST /api/v1/onboarding/v90-recordings` — registrar grabación V90

- **Headers:** `X-Actor-Id`.
- **Request body** (`RegistrarGrabacionV90Request`, con `@Valid`):
  ```json
  {
    "phase": "string (@NotBlank)", "axis": "string (@NotBlank)", "index": 0,
    "questionKey": "string|null", "mediaId": 7, "durationSeconds": 12.5, "transcript": "string|null"
  }
  ```
  `index` es `short`, `@PositiveOrZero`. `mediaId` (`@NotNull Long`) debe ser un media **ya
  registrado y del mismo `X-Actor-Id`** — si es de otro usuario o no existe, 404.
- **Response body** (`GrabacionV90Response`):
  ```json
  {
    "id": 3, "phase": "string", "axis": "string", "index": 0, "questionKey": "string|null",
    "recorded": true, "mediaId": 7, "durationSeconds": 12.5,
    "validationStatus": "PENDIENTE", "validationAttempts": 0, "recordedAt": "instant"
  }
  ```
- **Código de éxito:** 201 Created.
- **Trampa:** es **upsert por `(usuarioId, phase, axis, index)`** — re-grabar el mismo slot reinicia el veredicto de validación a `PENDIENTE` con 0 intentos, aunque ya tuviera un intento previo.
- **Errores:** 404 `"Media no encontrada para este usuario: <mediaId>"`.
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/onboarding/v90-recordings \
    -H "X-Actor-Id: <uuid-trainee>" -H "Content-Type: application/json" \
    -d '{"phase": "fase1", "axis": "proposito", "index": 0, "questionKey": "audio_fase1", "mediaId": 7, "durationSeconds": 12.5}'
  ```

### 22. `GET /api/v1/onboarding/v90-recordings` — listar mis grabaciones

- **Headers:** `X-Actor-Id`.
- **Response body:** `GrabacionV90Response[]` (mismo shape que el POST).
- **Código de éxito:** 200.

### 23. `POST /api/v1/onboarding/v90-recordings/{id}/validation` — pedir validación IA

> **Trampa:** `{id}` acá es **numérico** (`Long`), no UUID — a diferencia de casi todos
> los demás recursos del sistema.

- **Headers:** `X-Actor-Id`.
- **Path variable:** `id` = id numérico de la grabación (del response del paso 21).
- **Request body:** ninguno.
- **Response body** (`ValidacionV90Response`): `{ "status": "PROCESANDO", "attempts": 0, "feedback": null }`
- **Código de éxito:** **202 Accepted** — responde de inmediato, el trabajo real corre `@Async` (patrón preservado del backend viejo).
- **Quién puede llamarlo:** el dueño de la grabación (`grabacion.usuarioId().equals(actorId)`, si no → 403 `"Esta grabacion no pertenece al usuario"`).
- **Errores:** 404 grabación inexistente; 403 grabación ajena; **409** en varios estados de la máquina de la grabación: `"No se puede validar un slot sin audio grabado todavia"`, `"Esta grabacion ya tiene un veredicto final: <estado>"`, `"Ya hay un intento de validacion en curso para esta grabacion"`, `"Ya se agotaron los 3 intentos de validacion"`.
- **⚠️ Trampa de entorno:** `ValidacionIAPort` es hoy un **adaptador NoOp** (`NoOpV90ValidacionIAAdapter`, sin integración de IA real en este alcance) — **siempre** responde "no disponible". Tras 3 intentos (`MAX_INTENTOS = 3`), toda grabación cae a `REVISION_MANUAL`. No hay forma de obtener `APROBADA`/`RECHAZADA` en este entorno todavía.
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/onboarding/v90-recordings/3/validation \
    -H "X-Actor-Id: <uuid-trainee>"
  ```

### 24. `GET /api/v1/onboarding/v90-recordings/{id}/validation` — consultar estado (polling)

- **Headers:** `X-Actor-Id`.
- **Path variable:** `id` numérico.
- **Response body** (`ValidacionV90Response`): `{ "status": "PENDIENTE|PROCESANDO|APROBADA|RECHAZADA|REVISION_MANUAL", "attempts": 1, "feedback": "string(json)|null" }`
- **Código de éxito:** 200.
- **curl:**
  ```bash
  curl http://localhost:8080/api/v1/onboarding/v90-recordings/3/validation -H "X-Actor-Id: <uuid-trainee>"
  ```

---

## Módulo `phasecontracts`

Autorización basada en **rol + progreso del participante** (`ConsultarProgresoParticipantePort`,
que delega en el contrato público de `users`). No hay concepto de "propio recurso" —
el `X-Actor-Id` del header **es** siempre `participanteId`.

### 25. `GET /api/v1/phase-contracts` — listar mis contratos firmados

- **Headers:** `X-Actor-Id`.
- **Response body** (`ContratoFaseResponse[]`):
  ```json
  [{
    "id": "uuid", "phase": "FASE_1_RENACER|FASE_2_DESARROLLO|FASE_3_GUERRERO_ALQUIMISTA|FASE_4_ASCENSION",
    "phaseLabel": "string", "bucket": "string", "signatureUrl": "string (URL firmada de lectura, 15 min)",
    "signedAt": "instant"
  }]
  ```
- **Código de éxito:** 200.
- **Quién puede llamarlo:** TRAINEE (sobre sí mismo) o **MENTOR** (`ROLES_PUEDEN_CONSULTAR = {TRAINEE, MENTOR}`) — **trampa:** no hay chequeo de que el MENTOR sea el mentor asignado a ese trainee, alcanza con tener rol MENTOR.
- **Errores:** 404 `"Participante no encontrado: <id>"` si el actor no tiene fila de `ParticipacionPrograma` con `inscrito=true`; 403 `"Cuenta suspendida"`; 403 `"Rol sin permiso para esta operacion: <rol>"` si es ADMIN/ALCHEMIST/MENTOR_LEAD.
- **curl:**
  ```bash
  curl http://localhost:8080/api/v1/phase-contracts -H "X-Actor-Id: <uuid-trainee>"
  ```

### 26. `GET /api/v1/phase-contracts/pending` — contrato pendiente de firmar

- **Headers:** `X-Actor-Id`.
- **Response body** (`ContratoPendienteResponse`): `{ "pending": true|false, "phase": "FASE_2_DESARROLLO|null", "phaseLabel": "string|null" }`
- **Código de éxito:** 200.
- **Quién puede llamarlo:** TRAINEE o MENTOR (mismo set que `listar`).
- **Regla de dominio:** según el día de programa del participante (`FasePrograma.faseAFirmarEnDia`) — Fase II se desbloquea día 8, Fase III día 35, Fase IV día 65. Si ya está firmada, `pending: false`.
- **curl:**
  ```bash
  curl http://localhost:8080/api/v1/phase-contracts/pending -H "X-Actor-Id: <uuid-trainee>"
  ```

### 27. `POST /api/v1/phase-contracts/upload-url` — pedir URL firmada para subir la firma

- **Headers:** `X-Actor-Id`.
- **Request body:** ninguno.
- **Response body** (`UrlFirmaResponse`): `{ "uploadUrl": "string", "bucket": "string", "path": "string" }`
- **Código de éxito:** 200.
- **Quién puede llamarlo:** solo TRAINEE (`ROLES_PUEDEN_FIRMAR = {TRAINEE}`).
- **Errores:** 400 `"Todavia no corresponde firmar ningun pacto de fase"` si está en Fase I o la fase actual todavía no desbloqueó firma; **409** `"El pacto de la fase <n> ya fue firmado"` si ya existe `ContratoFase` para esa fase.
- **⚠️ Misma trampa de entorno que onboarding:** usa el mismo `AlmacenamientoPort` NoOp — `uploadUrl` es un placeholder `about:blank#...`, no una URL real de S3 en este entorno.
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/phase-contracts/upload-url -H "X-Actor-Id: <uuid-trainee>"
  ```

### 28. `POST /api/v1/phase-contracts` — firmar el pacto de fase

- **Headers:** `X-Actor-Id`.
- **Request body:** ninguno.
- **Response body** (`ContratoFaseResponse`, variante `deFirma` — **`signatureUrl` viene `null`**, a diferencia del listado que sí trae URL de lectura):
  ```json
  { "id": "uuid", "phase": "FASE_2_DESARROLLO", "phaseLabel": "Fase II · El Desarrollo", "bucket": "string", "signatureUrl": null, "signedAt": "instant" }
  ```
- **Código de éxito:** 201 Created.
- **Quién puede llamarlo:** solo TRAINEE.
- **Errores:** 400 `"La Fase 1 se firma en el Pacto del onboarding, no aqui"` (usar `/api/v1/onboarding/milestones` con `PACTO_FIRMADO` en su lugar); 400 `"Todavia no te toca firmar el pacto de <fase> (se desbloquea el dia <n> de programa)"`.
- **Trampas:**
  1. **Idempotente solo si NO es Fase I** — si ya existe un `ContratoFase` para esa fase, devuelve el existente **sin volver a firmar** (no es un error).
  2. **No verifica que el paso 27 (upload-url) se haya usado de verdad**: `firmar()` calcula una ruta determinística (`firmas/{participanteId}/fase_{numero}.svg`) y crea la fila igual, sin comprobar que el archivo exista en el bucket. La "subida real" del SVG de firma es responsabilidad del cliente contra la URL firmada, el backend no la valida en este endpoint.
  3. **No hay endpoint en estos 3 módulos que avance `diaPrograma`** — para probar Fase II en adelante hace falta que el día de programa del trainee ya esté en 8+ (eso lo escribe el módulo `habits`, fuera de este alcance). Con un trainee recién aprobado (día 0), tanto `upload-url` como `firmar` van a dar 400 "todavía no corresponde".
- **curl:**
  ```bash
  curl -X POST http://localhost:8080/api/v1/phase-contracts -H "X-Actor-Id: <uuid-trainee>"
  ```

---

## Flujos completos para probar

### Flujo A — Alta pública → aprobación por admin → primer login del aprendiz

1. **Alta pública** (sin header):
   ```bash
   curl -X POST http://localhost:8080/api/v1/account-requests \
     -H "Content-Type: application/json" \
     -d '{
       "supabaseUserId": "a0000000-0000-0000-0000-000000000001",
       "email": "flujo.a@renaser.com", "fullName": "Flujo A Test",
       "phone": "+51999999999", "city": "Lima"
     }'
   ```
   → 202, response `{ "accountRequestId": "<REQ_ID>" }`. Guardar `REQ_ID`.

2. **Aprobar** (header `X-Actor-Id` = un usuario ADMIN o ALCHEMIST **ya existente** en la
   tabla `usuarios`, cargado por otra vía — este flujo no crea admins):
   ```bash
   curl -X POST http://localhost:8080/api/v1/account-requests/<REQ_ID>/approve \
     -H "X-Actor-Id: <ADMIN_ID>"
   ```
   → 204. Esto crea el `User` con id **igual al `supabaseUserId` del paso 1**
   (`a0000000-0000-0000-0000-000000000001`), rol `TRAINEE`, y su fila de
   `ParticipacionPrograma` (día 0, sin activar).

3. **Primer login** (el propio aprendiz, mismo id que `supabaseUserId`):
   ```bash
   curl -X POST http://localhost:8080/api/v1/users/me \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001"
   ```
   → 200, `UserResponse` con `role: "TRAINEE"`, `status: "ACTIVE"`.

---

### Flujo B — Onboarding completo (cuestionario → respuestas → media → V90 → hitos → completar)

Usa el mismo `X-Actor-Id` de trainee del Flujo A en todos los pasos.

1. **Traer el cuestionario:**
   ```bash
   curl "http://localhost:8080/api/v1/onboarding/questionnaire?flow=onboarding_v90" \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001"
   ```
   → tomar el `id` de alguna pregunta de la respuesta (`sections[].questions[].id`) como `QUESTION_ID`, y fijarse su `type` para elegir el campo de valor correcto (ver tabla del endpoint 18).

2. **Guardar una respuesta** (ejemplo con pregunta tipo `ESCALA`):
   ```bash
   curl -X POST http://localhost:8080/api/v1/onboarding/answers \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001" -H "Content-Type: application/json" \
     -d '{"questionId": <QUESTION_ID>, "scaleValue": 8}'
   ```

3. **Subir media — paso 1, pedir URL:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/onboarding/media/upload-url \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001" -H "Content-Type: application/json" \
     -d '{"flow": "onboarding_v90", "questionKey": "audio_fase1", "kind": "AUDIO", "contentType": "audio/mp4"}'
   ```
   → tomar `bucket` y `path` de la respuesta (en este entorno son de un adaptador NoOp — no hay subida real a S3 que hacer, ver trampa del endpoint 19).

4. **Subir media — paso 2, registrar:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/onboarding/media \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001" -H "Content-Type: application/json" \
     -d '{
       "flow": "onboarding_v90", "questionKey": "audio_fase1", "kind": "AUDIO",
       "bucket": "<BUCKET del paso 3>", "path": "<PATH del paso 3>",
       "mime": "audio/mp4", "sizeBytes": 245000, "durationSeconds": 12.5
     }'
   ```
   → tomar `id` de la respuesta como `MEDIA_ID`.

5. **Registrar grabación V90** (usa `MEDIA_ID` del paso 4):
   ```bash
   curl -X POST http://localhost:8080/api/v1/onboarding/v90-recordings \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001" -H "Content-Type: application/json" \
     -d '{"phase": "fase1", "axis": "proposito", "index": 0, "mediaId": <MEDIA_ID>, "durationSeconds": 12.5}'
   ```
   → tomar `id` de la respuesta como `RECORDING_ID`.

6. **Pedir validación** (usa `RECORDING_ID` del paso 5):
   ```bash
   curl -X POST http://localhost:8080/api/v1/onboarding/v90-recordings/<RECORDING_ID>/validation \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001"
   ```
   → 202, `status: "PROCESANDO"`.

7. **Consultar estado (polling):**
   ```bash
   curl http://localhost:8080/api/v1/onboarding/v90-recordings/<RECORDING_ID>/validation \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001"
   ```
   → en este entorno (sin IA real), tras repetir el paso 6 hasta 3 veces sobre el mismo `RECORDING_ID` que siga en `PENDIENTE`, termina en `status: "REVISION_MANUAL"`.

8. **Aceptar hitos** (repetir con `TERMINOS`, `PACTO`, `PACTO_FIRMADO`, `ROCAS_SYNC` según corresponda):
   ```bash
   curl -X POST http://localhost:8080/api/v1/onboarding/milestones \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001" -H "Content-Type: application/json" \
     -d '{"milestone": "TERMINOS"}'
   ```

9. **Completar onboarding:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/onboarding/complete \
     -H "X-Actor-Id: a0000000-0000-0000-0000-000000000001"
   ```
   → 200, `completed: true`.

---

### Flujo C — Firma del Pacto de Sangre (`phasecontracts`)

**Advertencia antes de empezar:** un trainee recién aprobado está en día 0 de programa
(Fase I). Ninguno de los 3 módulos en este alcance expone un endpoint para avanzar
`diaPrograma` (eso lo escribe `habits`, fuera de este alcance) — así que con datos
recién creados por el Flujo A, los pasos 2 y 3 de abajo van a devolver 400 "todavía no
corresponde". Para probarlos de verdad hace falta un trainee cuya fila de
`participantes_programa.dia_programa` ya esté en 8 o más (Fase II), puesto a mano en la
base o generado por otra vía.

1. **Consultar pendiente:**
   ```bash
   curl http://localhost:8080/api/v1/phase-contracts/pending -H "X-Actor-Id: <TRAINEE_ID_DIA_8+>"
   ```
   → si `pending: true`, tomar `phase` de la respuesta.

2. **Pedir URL de firma:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/phase-contracts/upload-url -H "X-Actor-Id: <TRAINEE_ID_DIA_8+>"
   ```
   → (en este entorno, `uploadUrl` es un placeholder — no hay subida real a S3 que hacer, ver trampa del endpoint 27). Tomar `path`/`bucket` si se necesita verificar la ruta determinística.

3. **Firmar** (no depende de que el paso 2 se haya "completado" de verdad, ver trampa del endpoint 28):
   ```bash
   curl -X POST http://localhost:8080/api/v1/phase-contracts -H "X-Actor-Id: <TRAINEE_ID_DIA_8+>"
   ```
   → 201, `ContratoFaseResponse` con `signatureUrl: null`.

4. **Verificar en el listado** (ahora con URL de lectura firmada):
   ```bash
   curl http://localhost:8080/api/v1/phase-contracts -H "X-Actor-Id: <TRAINEE_ID_DIA_8+>"
   ```
   → 200, el contrato recién firmado aparece con `signatureUrl` no nulo.
