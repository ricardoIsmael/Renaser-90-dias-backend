# Mapa de endpoints — referencia de trabajo para conectar la app

**Fecha:** 2026-08-26
**Para qué sirve:** que puedas avanzar con el frontend sabiendo exactamente qué existe hoy y qué no. Cuando te topes con algo que falta, me escribís "falta esto" y lo construyo.

---

## 0. Cómo llamar a cualquier endpoint (hoy)

Todos los endpoints autenticados leen el actor de un header:

```
X-Actor-Id: <uuid del usuario>
```

Mandá también `Authorization: Bearer <token>` en paralelo — hoy el backend lo ignora, pero cuando se active la validación real de JWT (bloqueante **B-2**) el cambio va a ser sacar `X-Actor-Id` y nada más. Mandando los dos desde ahora, esa migración es de una línea.

**CORS ya está configurado** para `localhost:8081`, `localhost:19006` y `localhost:3000`. Si tu build web corre en otro puerto, avisame y lo agrego (`CORS_ORIGENES`).

**Errores:** todos devuelven el mismo sobre — `{"message": "...", "timestamp": "..."}` — con el status que corresponda (400/403/404/409/429). Nunca un stacktrace.

**Subida de archivos:** el patrón en todo el backend es de **dos pasos**, nunca subida directa:
1. `POST .../upload-url` → devuelve `{uploadUrl, bucket, ruta}`
2. `PUT` el archivo crudo a esa `uploadUrl`
3. Confirmás mandando `bucket` + `ruta` al endpoint de negocio (no una URL pública)

---

## 1. Lo que YA EXISTE — 144 endpoints

### `users` — identidad, altas, roles

| Verbo | Ruta |
|---|---|
| POST | `/api/v1/account-requests` |
| POST | `/api/v1/account-requests/{id}/approve` |
| POST | `/api/v1/account-requests/{id}/reject` |
| POST | `/api/v1/users/me` |
| PATCH | `/api/v1/users/me` |
| POST | `/api/v1/users/invite` |
| PATCH | `/api/v1/users/{id}/role` |
| PATCH | `/api/v1/users/{mentorId}/mentor-profile` |
| PUT | `/api/v1/participants/{traineeId}/mentor` |
| GET · POST · DELETE | `/api/v1/mentor/activate-tracking` |
| PATCH | `/api/v1/users/me/trainee-profile` |
| POST | `/api/v1/users/me/avatar/upload-url` |
| PATCH | `/api/v1/users/me/avatar` |
| GET · POST · DELETE | `/api/v1/users/me/account-deletion` |

> Ojo: el perfil propio es **`POST /users/me`**, no `GET`.

### `onboarding`

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/onboarding/questionnaire` |
| GET · PUT | `/api/v1/onboarding/state` |
| POST | `/api/v1/onboarding/answers` |
| POST | `/api/v1/onboarding/milestones` |
| POST | `/api/v1/onboarding/complete` |
| POST | `/api/v1/onboarding/media/upload-url` |
| POST | `/api/v1/onboarding/media` |
| GET · POST | `/api/v1/onboarding/v90-recordings` |
| GET · POST | `/api/v1/onboarding/v90-recordings/{id}/validation` |

> Las respuestas se guardan por **`questionId` numérico**, no por `questionKey` string — resolvelo primero con `GET /questionnaire`.

### `phasecontracts` — Pacto de Sangre

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/phase-contracts` |
| GET | `/api/v1/phase-contracts/pending` |
| POST | `/api/v1/phase-contracts/upload-url` |
| POST | `/api/v1/phase-contracts` |

> El `POST` final **no lleva body** — el backend ya sabe la ruta de la firma del paso `upload-url`.

### `habits` — hábitos, Santuario, radar

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/habit-tracks/today` |
| POST | `/api/v1/habit-tracks/{id}/complete` |
| POST | `/api/v1/habit-tracks/{id}/evidence` |
| POST | `/api/v1/habit-tracks/{id}/phone-free/start` |
| POST | `/api/v1/habit-tracks/phone-free/complete` |
| POST | `/api/v1/habit-tracks/phone-free/break` |
| POST | `/api/v1/habit-tracks/{id}/santuario/start` · `/complete` · `/break` |
| POST | `/api/v1/radar` |
| GET | `/api/v1/radar/latest` |
| GET | `/api/v1/radar/history` |
| GET | `/api/v1/habit-preferences` |
| PATCH | `/api/v1/habit-preferences/{habitId}` |

> `phone-free/complete` y `/break` **no llevan `{id}`** — el backend resuelve la racha activa por actor.
> `habit-preferences`: horario personal de cada hábito, cuota semanal de cambios y cambios programados —
> contrato completo en `docs/api/CONTRATO_DIA_A_DIA.md` §1.7. El resto del hueco #12
> (`weekly-habit-days`, `habits/{id}/rename`, `habit-unlocks`) existe desde el 2026-08-26 y todavía no
> está volcado a esta tabla — ver `docs/MODULO_HABITS.md` §12.

### `rocks` — rocas y Modo Verdugo

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/rocks/today` |
| GET | `/api/v1/rocks/tomorrow` |
| GET | `/api/v1/rocks/master` |
| GET · POST | `/api/v1/rocks/weekly` |
| PATCH | `/api/v1/rocks/weekly/{id}` |
| PATCH | `/api/v1/rocks/weekly/{id}/review` |
| POST | `/api/v1/rocks/plan` |
| POST | `/api/v1/rocks/{id}/evidence/upload-url` |
| POST | `/api/v1/rocks/{id}/evidence` |
| GET · POST | `/api/v1/enforcer-events` |

### `evidence`

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/evidence/{id}` |
| POST | `/api/v1/admin/evidence/{id}/review` |
| POST | `/api/v1/admin/evidence/{id}/void` |

### `points`

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/points/{participanteId}` |
| POST | `/api/v1/points/adjustments` |
| GET | `/api/v1/ranking/{tipo}` — `tipo` ∈ `LEAGUE`, `CELL` (`GENERAL`/`COHORT` todavía no) |

### `academy`

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/cursos` |
| GET | `/api/v1/cursos/bloqueados` |
| GET | `/api/v1/cursos/{id}` · `/preview` · `/secciones` |
| GET | `/api/v1/lecciones/{id}` · `/preview` |
| POST · DELETE | `/api/v1/lecciones/{id}/complete` |
| GET | `/api/v1/classroom/clase-diaria` |
| GET | `/api/v1/academia/recomendacion` |

### `community` — muro, células, cohortes, testimonios

| Verbo | Ruta |
|---|---|
| GET · POST | `/api/v1/wall` |
| GET | `/api/v1/wall/mine` · `/hidden` · `/categories` · `/latest-author` |
| PATCH · DELETE | `/api/v1/wall/{id}` |
| DELETE | `/api/v1/wall/{id}/permanent` |
| POST | `/api/v1/wall/{id}/react` · `/restore` |
| POST | `/api/v1/wall/media/upload-url` |
| GET · POST | `/api/v1/wall/{postId}/comments` |
| PATCH · DELETE | `/api/v1/wall/{postId}/comments/{commentId}` |
| GET | `/api/v1/me/cell` · `/api/v1/me/cell/members` |
| GET · POST | `/api/v1/testimonios` |
| GET · POST | `/api/v1/admin/cells` |
| GET · PATCH · DELETE | `/api/v1/admin/cells/{id}` |
| PUT · DELETE | `/api/v1/admin/cells/{id}/mentor` |
| POST | `/api/v1/admin/cells/{id}/session` |
| GET · POST | `/api/v1/admin/cohorts` |
| GET · PATCH · DELETE | `/api/v1/admin/cohorts/{id}` |
| PATCH | `/api/v1/admin/cohorts/{id}/status` |
| GET · POST | `/api/v1/admin/wall-categories` |
| PATCH · DELETE | `/api/v1/admin/wall-categories/{key}` |
| POST | `/api/v1/admin/wall-categories/reorder` |

> `GET /admin/cells` **exige** `?cohortId=<uuid>`. Actualizar usa **PATCH** (no POST); asignar mentor usa **PUT** (no POST).

### `calendar`

| Verbo | Ruta |
|---|---|
| GET · POST | `/api/v1/calendar/events` |
| GET · PUT · DELETE | `/api/v1/calendar/events/{id}` |
| PUT | `/api/v1/calendar/events/{id}/rsvp` |
| POST | `/api/v1/calendar/events/{id}/cancel-occurrence` |
| POST | `/api/v1/calendar/events/{id}/portada/upload-url` · `/portada/confirm` |

### `chat`

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/chat/conversations` |
| POST | `/api/v1/chat/conversations/direct` — body `{otherUserId}` |
| POST | `/api/v1/chat/conversations/{id}/read` |
| GET · POST | `/api/v1/chat/conversations/{conversationId}/messages` |
| WS | `/ws` → suscripción a `/topic/conversaciones/{id}` (STOMP, con autorización en el handshake) |

> El prefijo es `/api/v1/chat/...`, no `/api/v1/conversations/...`. Marcar leído es **POST**, no PUT.

### `notifications`

| Verbo | Ruta |
|---|---|
| GET | `/api/v1/notifications` |
| PUT | `/api/v1/notifications/{id}/read` · `/read-all` |
| GET · PATCH | `/api/v1/notification-preferences` |
| POST | `/api/v1/push-tokens` |

> El campo `type` viene con los valores del enum **en español** (`RECORDATORIO_HABITO`, `MENSAJE_CHAT`, `LOGRO_DESBLOQUEADO`…). La BD está congelada con esos valores: la traducción a etiquetas/íconos va en el cliente.

### `support` — tickets a mentor y soporte técnico

| Verbo | Ruta |
|---|---|
| GET · POST | `/api/v1/tickets` |
| POST | `/api/v1/tickets/{id}/answer` · `/save-to-library` |
| GET | `/api/v1/tickets/library` |
| GET | `/api/v1/admin/tickets` |
| GET · POST | `/api/v1/support-tickets` |
| POST | `/api/v1/support-tickets/attachments/upload-url` |
| GET | `/api/v1/admin/support-tickets` |
| POST | `/api/v1/admin/support-tickets/{id}/resolve` |

> Regla deliberada: una cuenta **SUSPENDIDA sí puede** abrir tickets de soporte — es su único canal para reclamar la suspensión. No es un bug.

### `rag` — RenasIA y Espejo de Sombra

| Verbo | Ruta |
|---|---|
| POST | `/api/v1/renasia/mensajes` — streaming **SSE** (`text/event-stream`) |
| GET | `/api/v1/renasia/mensajes` — historial paginado por cursor |
| GET | `/api/v1/espejo-sombra` · `/api/v1/espejo-sombra/{id}` |
| POST | `/api/v1/admin/conocimiento` |

> El streaming son eventos SSE reales (`data: ...`), no texto plano — el cliente necesita parsearlos como SSE. Cuota: 25 mensajes/día por aprendiz, después 429.

---

## 2. Lo que FALTA — y la buena noticia sobre reutilizar

**Tu instinto era correcto: casi nada de esto requiere inventar módulos nuevos.** Lo verifiqué tabla por tabla.

De las **12 tablas sin uso**, **10 se activan** construyendo endpoints que faltan — y **ninguna requiere una tabla nueva**. Las 2 que quedan fuera (`permisos`, `rol_permiso`) es por una decisión de arquitectura ya tomada (D-21: los permisos van en el enum `UserRole`, no en tablas de junction), no por olvido.

### Categoría A — Reutilización pura: el dominio ya existe, solo falta exponerlo

Esto es lo más barato del backlog. Hay dominio, puertos y persistencia construidos y probados; falta el caso de uso y el controller.

| Falta | Qué ya existe (se reutiliza) | Tabla que activa |
|---|---|---|
| Escribir entrada de diario | `EntradaDiario` + puertos + JPA + mapper, completos | *(ninguna nueva — `entradas_diario` ya se lee)* |
| Coherencia diaria + semáforo | `RegistrarCoherenciaDiariaUseCase` **ya construido, nadie lo llama** | `historial_coherencia` |
| Cupo y diferido de cambio de horario | `PreferenciaHorario` + `CambioHorarioPendiente` en dominio | `historial_cambios_horario` |
| Renombrar hábito (bebidas tóxicas) | `Habito.claveSistema` ya es la identidad estable | `renombres_habito` |
| Desbloqueo escalonado por rampa | `HorarioHabito.aplicaEnDia(diaPrograma, tipoDia)` ya resuelve por día | `desbloqueos_habito` |
| Hábitos personales | `Habito.plantillaClave` (ámbito PERSONAL) ya modelado | `dias_semanales_habito` |
| Auditoría de cambio de rol | `PATCH /users/{id}/role` ya funciona, solo no deja rastro | `auditoria_cambios_rol` |
| Listado de evidencias | Repositorio y proyección ya existen, falta la consulta | *(ninguna)* |
| Ranking por célula | `points` ya calcula y guarda snapshots | `ranking_celulas` |
| Ciclos de intoxicación | `TipoDia.INTOXICACION` + `Habito.obligatorioEnIntoxicacion` ya modelados | *(ninguna)* |

### Categoría B — Falta el endpoint, el resto está

| Falta | Nota |
|---|---|
| `POST /classroom/clase-diaria` (completar la clase) | El `GET` ya existe |
| Listado de la cola de revisión de evidencia | `review`/`void` ya existen, falta el `GET` |
| Listado/borrado admin de solicitudes de alta | El `approve`/`reject` ya existen |
| Asignar cohorte/célula/mentor/fecha al aprobar | Hoy el `approve` no recibe body |
| Directorio de usuarios (para buscar con quién chatear) | Nada equivalente hoy |
| Miembros y nombre del chat global | El auto-join ya funciona por evento |

### Categoría C — Concepto que no existe todavía

| Falta | Nota |
|---|---|
| Logros / badges | No hay dominio de "logro" en ningún módulo |
| Reportar alucinación de RenasIA | Ni campo ni ruta (el frontend ya tiene la UI) |
| Tarjetas de bienvenida | Activaría `mensajes_bienvenida` |
| Audioterapia + sincronización con Drive | Activaría `audioterapias`; el puerto de catálogo existe sin adaptador |
| Paneles admin de `staff` y `trainees` | Listar/editar personal y aprendices |
| Baja de cuenta (GDPR) | Requisito de Google Play / Apple |

---

## 3. Cómo trabajamos de acá en adelante

Vos avanzás con el frontend usando §1. Cuando te choques con algo:

**Escribime así:** *"falta el endpoint para [qué querés hacer], en la pantalla [cuál]"* — con eso me alcanza. No necesito la ruta que imaginabas; con saber qué necesita hacer la pantalla, yo veo si ya existe con otro nombre, si se arma reutilizando algo, o si hay que construirlo.

**Antes de construir cualquier cosa nueva voy a verificar primero si se puede reutilizar** — es la regla que pediste, y §2 muestra que en la mayoría de los casos aplica.

**Queda para después** (decisión tuya de hoy): el motor diario del programa (generar tracks → coherencia → semáforo → Verdugo) y el resto del análisis de cumplimiento de los 45 requisitos, que está en [`CUMPLIMIENTO_REQUISITOS.md`](CUMPLIMIENTO_REQUISITOS.md).
