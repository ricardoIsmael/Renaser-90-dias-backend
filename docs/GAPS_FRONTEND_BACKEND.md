# Gaps entre el frontend (RenaserPlayStoreCopy) y el backend Java

> **SUPERADO — 2026-08-26.** Este documento se auditó contra `C:\RenaserPlayStoreCopy` (una copia usada solo para pruebas de esa sesión, con parches temporales que **no** están en la app real). La app real es `C:\renaserPlayStore`, que además resultó estar en un estado bien distinto (ya no usa Supabase, tiene su propio contrato `JAVA_BACKEND_CONTRACT.md` que no coincide con lo que construimos). Ver [`docs/PLAN_INTEGRACION_FRONTEND.md`](PLAN_INTEGRACION_FRONTEND.md) — reemplaza este documento por completo, con hallazgos verificados contra el repo real y los 14 módulos completos. Se deja este archivo como registro histórico, no como referencia vigente.

**Fecha:** 2026-08-25
**Alcance:** auditoría de qué llama el frontend (`C:\RenaserPlayStoreCopy`, Expo/React Native con build web) contra lo que el backend Java (`com.renaser.os`) realmente expone hoy. Generado con agentes de exploración en paralelo, uno por grupo de módulos, más revisión directa del módulo `users`.

Este documento es un inventario de huecos, no un plan de trabajo. No se tocó código de backend para generarlo — es puramente de lectura. Los únicos cambios de código de esta sesión fueron en el frontend (ver `git status` en `RenaserPlayStoreCopy`: `src/services/account-requests.ts`, `src/services/accountRequests.ts`, `src/services/profile.ts`, `.env`), corrigiendo rutas/campos hacia lo que el backend ya tiene.

---

## 0. Bloqueadores transversales (afectan a TODOS los módulos)

Estos cuatro puntos no son de un módulo en particular — hay que resolverlos para que cualquier otra cosa de este documento funcione.

### 0.1 CORS no está configurado — bloquea el 100% de las llamadas desde el navegador

`shared/web/SecurityConfig.java` no tiene ningún bean `CorsConfigurationSource` ni `.cors(...)` en la `SecurityFilterChain`. Un navegador (el build web de Expo, que es justo lo que se pidió conectar) va a rechazar **toda** llamada cross-origin a `/api/v1/**` con un error de CORS antes de que la ruta, el método o el body importen. Esto no afecta a la app nativa (iOS/Android), solo al target web — pero el target web es explícitamente el que se está conectando ahora.

### 0.2 Autenticación: el backend espera `X-Actor-Id`, el frontend manda `Authorization: Bearer <jwt>`

Los ~19 controllers que necesitan saber "quién hace la llamada" (`UserController`, `AccountRequestController`, `HabitTrackController`, `RocaDiariaController`, etc.) leen un header `X-Actor-Id` con `@RequestHeader`. Es un mecanismo temporal, documentado en el propio código como **"NO USAR EN PRODUCCIÓN"**, porque la validación real del JWT de Supabase (RS256 vía JWKS) está bloqueada por el punto **B-2** de `docs/MODULOS_A_AVANZAR.md` (sin confirmar todavía).

El frontend, en cambio, manda `Authorization: Bearer <token de sesión de Supabase>` en todos lados — nunca `X-Actor-Id`. Sin uno de los dos lados moviéndose, **ningún endpoint autenticado responde correctamente** (falta un `@RequestHeader` obligatorio → 400).

Esta sesión agregó `X-Actor-Id` (con el `user.id` de la sesión de Supabase) en los dos wrappers de fetch del módulo `users`/`account-requests` (`profile.ts`, `accountRequests.ts`) como puente temporal, junto al `Authorization` que ya mandaban. El resto de los servicios (habits, rocks, chat, etc. — ver abajo) **todavía no tienen ese puente** y van a fallar por esto además de por sus propios problemas de ruta.

### 0.3 Buena parte del frontend no pasa por el backend Java — habla directo con Supabase

Esto no es un bug de ruteo, es una decisión de implementación pendiente de migrar: varias pantallas leen/escriben tablas o Storage de Supabase directamente, ignorando controllers que **sí existen** en el backend Java. Aparece repetido módulo por módulo abajo, pero como patrón general:

- `users.ts` (perfil propio) — Supabase directo, ignora `UserController`.
- `radar.ts` — Supabase directo (`radar_entries`), ignora `RadarController`.
- Buena parte de `onboarding.ts` (estado, respuestas, media, registro de V90) — Supabase directo, ignora `EstadoOnboardingController`, `CuestionarioController`, `RespuestaController`, `MediaController`.
- `TestimoniosPanel` (community) — Supabase directo, ignora `TestimonioController`.
- `cursos.ts`: `listarCursosBloqueados()` y marcar/desmarcar lección completada — Supabase directo, ignoran endpoints ya construidos.
- `rocks.ts` — sube evidencia directo a Storage, ignora `POST /{id}/evidence/upload-url`.
- `evidencias.ts` — cae a listar desde Storage directo porque el endpoint de listado no existe en el backend (ver §4).
- Chat — usa Supabase Realtime para saber cuándo refrescar, en vez de la infraestructura WebSocket/STOMP+Redis que el backend ya tiene armada (ver §3.4).

### 0.4 Dos validaciones por IA son stubs "NoOp" — nunca dan un resultado real

- `evidence/infrastructure/adapter/out/ia/NoOpEvidenciaValidacionIAAdapter` — siempre devuelve `NO_DISPONIBLE`.
- `onboarding/infrastructure/adapter/out/ia/NoOpV90ValidacionIAAdapter` (Filtro de las 6 Ps) — siempre devuelve `NO_DISPONIBLE`.

En ambos casos, después de 3 reintentos del scheduler el item cae a `REVISION_MANUAL`. No es un problema de ruteo — es lógica de negocio real todavía no implementada (falta credencial/integración de Gemini, ya documentado como **D-39**). Cualquier pantalla que espere una respuesta síncrona de la IA (ej. `SixPsValidation` en `onboarding.ts`, con `missingPs`/`feedback`) no va a poder recibirla hoy.

---

## 1. Módulo `users` (incluye account-requests, mentor-profile)

### 1.1 Rutas que el frontend llamaba y el backend no tiene (ya corregidas en el frontend esta sesión)

| Frontend llamaba | Backend real | Estado |
|---|---|---|
| `POST /api/v1/account-requests/check-email` | No existe ningún controller para esto | Deshabilitado en frontend, devuelve `null` |
| `POST /api/v1/account-requests/exists` | No existe | Deshabilitado, devuelve `null` |
| `POST /api/v1/account-requests/verify-email` | No existe | Deshabilitado, devuelve `{deliverable: null}` |
| `GET /api/v1/account-requests/me` | No existe | Deshabilitado, devuelve `{kind: "unknown"}` |
| `GET/POST/DELETE /api/v1/admin/account-requests/**` (listado admin, delete) | No existe ningún controller admin para listar/borrar solicitudes | Listado deshabilitado (`[]`), delete deshabilitado (`false`) |
| `POST /api/v1/admin/account-requests/{id}/approve` \| `/reject` | Sí existen, pero en `/api/v1/account-requests/{id}/approve`\|`/reject` (sin `/admin/`) | Corregido en frontend |
| `GET /api/v1/users/me` | Existe como `POST /me`, no `GET` | Corregido en frontend |
| `PATCH /api/v1/users/me/trainee-profile` | No existe — `UserController` solo tiene `PATCH /me` con `fullName/avatarUrl/bio/department`, ningún campo del perfil de aprendiz | Deshabilitado en frontend (error 501 explícito) |

### 1.2 Gaps que quedan (no se pueden resolver solo del lado del frontend)

- **`UserResponse` no incluye `traineeProfile`** — el backend nunca va a devolver `personalChallengeName`, `startDate`, `isProgramCompleted`, `programCompletedAt`, `postProgramDay` en `POST /api/v1/users/me`. Falta el campo en la proyección de salida y probablemente el caso de uso que lo arme.
- **No hay endpoint para editar el perfil de aprendiz** (`personalChallengeName`, etc.) — falta un `PATCH /api/v1/users/me/trainee-profile` o equivalente.
- **`ApproveAccountRequestUseCase` no acepta `role`/`department`/`bio`** — la pantalla admin de aprobación deja elegir un rol, pero el backend lo ignora silenciosamente (el body no se lee: no hay `@RequestBody` en `AccountRequestController.approve`). Falta decidir cómo se asigna el rol al aprobar.
- **No hay listado GET de account requests** en ningún path — la pantalla admin de solicitudes pendientes no tiene de dónde leer.
- **`users.ts`** (perfil propio: nombre, teléfono, avatar) sigue yendo directo a Supabase — el dominio `User` del backend ni siquiera tiene campo `phone`, así que no hay endpoint al que migrar esa función sin antes decidir si `phone` se suma al dominio.
- **`SubmitAccountRequestRequest` exige `supabaseUserId`** (`@NotBlank`) — el frontend solo lo tiene disponible si ya existe sesión (verificación de OTP hecha antes). Si alguien llega a este paso sin sesión, el backend va a rechazar la solicitud por validación. Es un hueco de flujo real, no solo de wiring.

---

## 2. Módulo `academy`

| Problema | Detalle |
|---|---|
| `POST /api/v1/classroom/clase-diaria` no existe | `claseDiaria.ts:completarClaseDiaria()` llama un POST; `ClaseDiariaController` solo tiene `@GetMapping`. 405 garantizado. |
| Endpoints sin usar por el frontend | `GET /api/v1/cursos/bloqueados` (el frontend usa un RPC de Supabase en su lugar); `POST/DELETE /api/v1/lecciones/{id}/complete` (el frontend escribe directo a la tabla `leccion_progreso` de Supabase). |

---

## 3. Módulo `community`, `calendar`, `chat`

### 3.1 Ranking (cruza con `points`, ver §6)

`community.ts:getRanking()` llama `GET /api/v1/ranking` esperando `{cohortName, celulas, miCelula, ...}` (ranking por célula). El backend real es `GET /api/v1/ranking/{tipo}` (requiere el segmento `{tipo}`) y devuelve una lista plana `[{participanteId, fullName, posicion, puntaje}]`. Ni la ruta ni la forma de la respuesta coinciden — esto rompe también `useMiRanking.ts` (tarjeta de ranking en Home).

### 3.2 Testimonios y categorías de wall

- `TestimonioController` (`GET/POST /api/v1/testimonios`) existe pero el frontend nunca lo llama — `TestimoniosPanel` lee/escribe la tabla `testimonios` de Supabase directo, y sube fotos al bucket `testimonios-media` directo.
- `WallCategoryAdminController` (`/api/v1/admin/wall-categories`) no tiene ningún llamador en el frontend — no se encontró pantalla de administración de categorías.

### 3.3 Admin de cohortes y células — varios métodos HTTP no coinciden

| Frontend | Backend real | Problema |
|---|---|---|
| `POST /api/v1/admin/cohorts/{id}` (update) | `@PatchMapping("/{id}")` | Método equivocado, 405 |
| `POST /api/v1/admin/cohorts/{id}/status` | `@PatchMapping("/{id}/status")` | Método equivocado, 405 |
| `GET /api/v1/admin/cohorts/{cohortId}/cells` | No existe ese path anidado; es `GET /api/v1/admin/cells?cohortId=...` | Ruta no existe |
| `POST /api/v1/admin/cohorts/{cohortId}/cells` (crear célula) | Es `POST /api/v1/admin/cells` con `{name, cohortId, videoCallUrl}` en el body | Ruta no existe |
| `POST /api/v1/admin/cells/{id}` (update) | `@PatchMapping("/{id}")` | Método equivocado, 405 |
| `POST /api/v1/admin/cells/{id}/mentor` | `@PutMapping("/{id}/mentor")` | Método equivocado, 405 |
| `GET /api/v1/admin/cells/mentores-disponibles`, `/mentores`, `/aprendices-disponibles` | No existen — y además colisionarían con `GET /{id}` (fallaría el parseo de UUID) | No implementados |
| `POST /api/v1/admin/cells/{cellId}/trainees`, `DELETE .../{traineeProfileId}` | No existe ningún endpoint de asignación de aprendices a células — `CelulaAdminController` solo maneja mentor + sesión + CRUD | No implementado en absoluto |

### 3.4 Calendar — subida de portada del evento rota

`calendar.ts:uploadEventCover()` hace un único `POST /api/v1/calendar/events/{id}/portada` (multipart). El backend espera un flujo de dos pasos: `POST /{id}/portada/upload-url` (pide URL prefirmada) → `POST /{id}/portada/confirm`. El frontend no llama a ninguno de los dos; llama a un tercer path que no existe. Resto de `calendar.ts` (CRUD de eventos, RSVP) coincide bien.

### 3.5 Chat — prefijo de ruta completo desalineado

**Todo** `chat.ts` llama a `/api/v1/conversations/...`. El backend monta ambos controllers bajo `/api/v1/chat/conversations/...` — falta el segmento `/chat/` en cada llamada del frontend:

- `getConversations`, `getMessages`, `sendTextMessage/sendMediaMessage` → falta `/chat/` en el path.
- `startDirectConversation` → además del prefijo, el campo del body no coincide: frontend manda `{userId}`, backend espera `{otherUserId}`.
- `markConversationRead` → path Y método mal: frontend usa `PUT`, backend es `POST`.
- `deleteMessage` → no existe ningún mapping de borrado en `MensajeController` (más allá del prefijo).
- `getMyCellConversation`, `getMembers`, `getGlobalChatMembers`, `renameGlobalConversation` → no existe ningún controller para "conversación global" ni para listar miembros; no hay ese concepto en el backend hoy.

**Dato a favor:** el backend sí tiene infraestructura de tiempo real lista — `chat/infrastructure/adapter/in/websocket/WebSocketConfig` registra un endpoint STOMP en `/ws` con `RedisChatSubscriberConfig` haciendo fan-out entre instancias vía Redis Pub/Sub hacia `/topic/conversaciones/{id}` (exactamente el diseño de la §5.2.1 del documento de arquitectura). El frontend no lo usa: en su lugar se suscribe a Supabase Realtime (`postgres_changes` sobre la tabla `messages`) para saber cuándo refrescar — y ese refresco, al llamar al REST con el prefijo equivocado, hoy tampoco funcionaría.

**Push tokens:** `registerPushToken(token, platform)` → `POST /api/v1/push-tokens` sí coincide con `TokenPushController` (campos y valores compatibles, incluida la normalización de mayúsculas del `platform`). No es un gap.

---

## 4. Módulo `habits`

| Frontend | Problema |
|---|---|
| `PATCH /api/v1/habit-tracks/{id}` (`updateHabitStatus`) | No existe ningún `PATCH` en `HabitTrackController`. Solo hay `POST /{id}/complete`, con nombres de campo en español (`respuestaTexto`, `calificacionProductividad`) y sin soporte para volver a `PENDING`. |
| `POST /api/v1/habit-tracks/{trackId}/phone-free/complete` \| `/break` | El backend NO lleva `{id}` en estas dos rutas (`RachaController` resuelve la racha activa del lado del servidor): son `POST /api/v1/habit-tracks/phone-free/complete` y `/break` a secas. |
| `PATCH /api/v1/habit-preferences/{habitId}`, `PUT /api/v1/weekly-habit-days/{habitId}`, `PUT`/`DELETE /api/v1/habits/{habitId}/rename`, `GET`/`PUT /api/v1/habit-unlocks[...]` | Ninguno de estos existe — no hay controllers de preferencias, día semanal elegido, renombrado, ni plan de desbloqueo de hábitos. |
| `habitsAdmin.ts` completo (`/api/v1/admin/habits*`: CRUD, horarios, guías, adjuntos) | No hay **ningún** controller admin de hábitos en todo el backend. Archivo entero sin contraparte. |

Sin uso por el frontend (existen pero nadie los llama): `SantuarioController` completo, `RadarController` completo (`radar.ts` usa la tabla `radar_entries` de Supabase directo en su lugar).

---

## 5. Módulo `rocks`

| Frontend | Problema |
|---|---|
| `GET /api/v1/rocks` (`getDashboard`) | No existe ese mapping raíz — `RocaDiariaController` solo tiene `/today`, `/tomorrow`, `/plan`, `/{id}/evidence[/upload-url]`. |
| `GET`/`PUT /api/v1/journal/today` (diario) | No existe ningún controller de diario/journal en absoluto. |

Sin uso por el frontend: `RocaMaestraController` (`GET /api/v1/rocks/master`); `POST /{id}/evidence/upload-url` (rocks.ts sube evidencia directo a Supabase Storage en su lugar).

---

## 6. Módulo `evidence`

| Frontend | Problema |
|---|---|
| `GET /api/v1/evidence` (listado) | `EvidenciaController` solo tiene `GET /{id}` (uno por uno) — no hay endpoint de listado. `evidencias.ts` cae permanentemente a listar desde Supabase Storage por esto. |
| `GET /api/v1/admin/evidence-review`, `POST /api/v1/admin/evidence-review/{id}/override` | Ruta base equivocada y acción inexistente — el backend real es `EvidenciaAdminController` en `/api/v1/admin/evidence` con `POST /{id}/review` (aprobar/rechazar con notas) y `POST /{id}/void`. No es solo un typo de ruta: `override` como acción no existe, hay que usar `review`/`void`. |

Ver también §0.4 — la validación automática por IA de evidencia es un stub NoOp.

---

## 7. Módulo `onboarding`

La mayor parte de la capa REST de este módulo existe en el backend pero **el frontend no la usa**: `EstadoOnboardingController` (`GET`/`PUT /state`, `POST /milestones`), `CuestionarioController` (`GET /questionnaire`), `RespuestaController` (`POST /answers`), `MediaController` (`POST /media/upload-url`, `POST /media`) — todo bypaseado, el frontend lee/escribe directo las tablas `onboarding_state`, `onboarding_answers`, `onboarding_media` de Supabase.

Además:

| Frontend | Problema |
|---|---|
| `POST /api/v1/onboarding/activate-program` | No existe. |
| `PATCH /api/v1/onboarding/start-date` | No existe. |
| `POST /api/v1/onboarding/smart/validate` | No existe. |
| `POST /api/v1/onboarding/v90/validate` + poll `GET .../v90/validate?recordingId=` | El backend real es `POST /api/v1/onboarding/v90-recordings/{id}/validation` (202, async) y `GET /api/v1/onboarding/v90-recordings/{id}/validation` — el id va en el path, no en query string, y el segmento es distinto (`v90-recordings`, no `v90`). |
| `GET /api/v1/admin/onboarding` (dashboard admin) | No existe. |

Sin uso por el frontend: `POST /api/v1/onboarding/v90-recordings` (registrar grabación) — el frontend escribe directo a la tabla `variables_90_recordings` de Supabase.

Fuera del alcance original pero encontrado: `shadowMirror.ts` llama `GET`/`PUT /api/v1/journal/shadow-mirror` y `GET`/`POST /api/v1/journal/shadow-mirror/weekly-report` — no existe ningún controller para esto en los 45 `@RestController` del backend. Funcionalidad no construida del lado del servidor.

Ver también §0.4 — la validación del Filtro de las 6 Ps también es un stub NoOp.

---

## 8. Módulo `points`

- **`PuntajeController` (`GET /{participanteId}`, `POST /adjustments`) no tiene ningún llamador en el frontend** — ninguna pantalla pide el puntaje individual ni ajustes manuales.
- El ranking (`RankingController`) sí se intenta usar, pero con el mismo problema de forma/ruta descrito en §3.1.
- `logros.ts` (logros/streaks) llama `GET /api/v1/profile/logros` — un path que no está en ninguno de los controllers relevados; parece un módulo/endpoint distinto (`profile`) no cubierto por este inventario, o directamente no implementado. Vale confirmarlo aparte.

---

## 9. Módulo `phasecontracts` (Pacto de Sangre)

- `GET /api/v1/phase-contracts/pending` — coincide bien.
- **`POST /api/v1/phase-contracts` (firmar) — el backend ignora el body por completo.** `ContratoController.firmar()` no tiene `@RequestBody`; el backend calcula la ruta del archivo de firma él mismo, de forma determinística: `firmas/{participanteId}/fase_{numero}.svg` en el bucket `onboarding-signatures`. El frontend, en cambio, sube el SVG directo a Supabase Storage en `${user.id}/pacto_fase/${phase}.svg` — **una ruta distinta**. Resultado: una vez firmado, el listado (`GET /`) va a generar una URL firmada apuntando a un archivo que nunca se subió ahí. Esto no es un problema de wiring, es una inconsistencia de datos real una vez que ambos lados "funcionen".
- El frontend nunca llama `POST /api/v1/phase-contracts/upload-url`, que es el primer paso del flujo que el backend sí espera.
- El frontend nunca llama `GET /api/v1/phase-contracts` (historial de contratos firmados) — no hay pantalla que lo muestre.

---

## 10. Módulo `support`

Este es el módulo con **mejor alineación** de todo el relevamiento — casi todo coincide:

- Tickets de soporte (`support.ts`, `supportTickets.ts` admin) y tickets a mentor (`tickets.ts`, incluyendo biblioteca de respuestas) coinciden en ruta, método y forma del body/respuesta.
- Único hueco: `POST /api/v1/support-tickets/attachments/upload-url` no lo llama nadie — el frontend sube adjuntos directo a Supabase Storage y pasa la URL pública, que el backend acepta por un camino de compatibilidad legado (`AbrirTicketSoporteRequest.rutaDesdeUrl()`). Funciona hoy, pero dejaría de andar si ese camino de compatibilidad se retira.
- Riesgo aparte (no de ruteo): `tickets.ts:getTickets()` cae a leer la tabla `mentor_tickets` de Supabase directo si la respuesta REST viene vacía — puede mostrar datos inconsistentes con lo que ve el backend.

---

## 11. Módulo `notifications`

- Listado y marcar-leído (`GET /`, `PUT /{id}/read`, `PUT /read-all`) coinciden.
- **Vocabulario de `type` completamente distinto entre frontend y backend.** El frontend espera valores en inglés (`DAILY_HABIT_REMINDER`, `MENTOR_MESSAGE`, `CHAT_MESSAGE`, `TICKET_ANSWERED`, etc.); el enum real del backend (`TipoNotificacion`) está en español (`RECORDATORIO_HABITO`, `MENSAJE_MENTOR`, `MENSAJE_CHAT`, `TICKET_RESPONDIDO`, etc. — y además tiene `TICKET_ABIERTO`, `SANTUARIO_ROTO`, `HABITO_PERSONAL_MODIFICADO` sin ningún equivalente del lado del frontend). Resultado: toda notificación que llegue del servidor va a caer en la categoría genérica/vacía del lado del cliente — íconos y agrupación rotos en silencio, sin error visible.
- **Las preferencias de notificación son 100% locales** (`AsyncStorage`) — la pantalla de configuración nunca llama `GET`/`PATCH /api/v1/notification-preferences`, así que lo que el servidor pueda usar para decidir qué notificar nunca refleja lo que el usuario eligió. El día que se conecte, va a chocar con el mismo problema de vocabulario español/inglés de arriba (`ActualizarPreferenciasRequest` hace `TipoNotificacion.valueOf(...)`, que explota con un string en inglés).
- Registro de push token: **coincide bien**, sin problemas.

---

## Resumen — qué desbloquea más de un lado

1. **CORS** (§0.1) — sin esto, cero llamadas desde el build web funcionan, sin importar todo lo demás.
2. **`X-Actor-Id` vs JWT real** (§0.2) — sin esto, cero endpoints autenticados funcionan.
3. Después de esos dos, los módulos más cerca de andar tal cual están son **`support`** y (parcialmente) **`community`** (cells/cohorts, con métodos HTTP a corregir) y **`calendar`** (salvo portada). Los más lejos son **`habits`**, **`onboarding`** y **`chat`** — no por poco código en el backend, sino porque el frontend hoy directamente no pasa por él en buena parte de esos flujos.
