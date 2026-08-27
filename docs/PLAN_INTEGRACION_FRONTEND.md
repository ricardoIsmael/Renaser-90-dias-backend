# Plan de integración frontend ↔ backend Java

**Fecha:** 2026-08-26
**Reemplaza por completo** a la versión anterior de este documento (2026-08-25, auditada contra `RenaserPlayStoreCopy` — una copia usada solo para pruebas, con parches temporales míos que **no** están en la app real).
**Insumo:** 4 agentes de exploración en paralelo, uno por grupo de módulos, leyendo el código real de **ambos** repos — `C:\renaserPlayStore` (la app real, confirmada por el usuario) y `C:\Users\Usuario\Documents\renaser-backend\renaser-backend` (los 14 módulos, completos, 1112 tests en verde).

---

## 0. El hallazgo que cambia todo el enfoque

La app real (`C:\renaserPlayStore`) **ya no usa el SDK de Supabase**. Fue reescrita para hablar con "un backend Java" a través de un archivo (`src/lib/supabase.ts`) que conserva el nombre y la forma de la API de Supabase (para no tocar los ~65 archivos que ya lo importaban) pero por debajo hace `fetch` plano. El contrato que ese shim asume está documentado en el propio repo del frontend: [`docs/JAVA_BACKEND_CONTRACT.md`](file:///C:/renaserPlayStore/docs/JAVA_BACKEND_CONTRACT.md).

Ese contrato pide un **proxy genérico estilo PostgREST**: `GET /api/v1/{tabla}?col=eq.valor&order=...`, RPC genérico (`POST /api/v1/rpc/progreso_cursos`), storage genérico por bucket, y auth propia por OTP. **El backend que construimos no es eso** — expone casos de uso de negocio (`POST /habit-tracks/{id}/complete`, `GET /rocks/today`, etc.) con toda la autorización real adentro (mentor asignado, actor suspendido, gates de Academia). Un proxy genérico sobre tablas crudas se saltearía esa autorización por completo — es el mismo patrón que se corrigió esta sesión en `community`/`support` (E-38, E-42), reintroducido por el shape del contrato.

**Decisión ya tomada con el dueño del producto: se adapta el frontend para llamar a los endpoints de negocio reales.** No se construye un proxy genérico en el backend. Este documento es el plan de esa adaptación.

---

## 1. Estrategia de autenticación

Dos preguntas distintas, con dos respuestas distintas — no mezclarlas:

### 1.1 ¿Quién emite el token? → Supabase, siempre fue así por diseño

`CLAUDE.md` §11/§5.3.1 ya lo decidió: Spring Security actúa como **Resource Server**, valida el JWT que **Supabase emite** — el backend Java nunca emite tokens propios. Pero `src/services/auth.ts` de la app real hoy llama a `supabase.auth.*` (el shim), que apunta al backend Java buscando rutas `/api/v1/auth/login`, `/api/v1/auth/otp/send`, `/api/v1/auth/oauth`, etc. — **ninguna existe, ni debe existir ahí**.

**Acción:** `auth.ts` tiene que volver a hablar con el SDK real de Supabase (`@supabase/supabase-js`), no con el shim. Esto requiere:
- Reponer `EXPO_PUBLIC_SUPABASE_URL` y `EXPO_PUBLIC_SUPABASE_ANON_KEY` en `.env` (hoy **ausentes** en `C:\renaserPlayStore\.env`).
- **Decisión pendiente y bloqueante:** ¿cuál proyecto de Supabase es el real? La versión anterior de este documento detectó dos proyectos distintos (`qchpxyaiipghayyfmthg` en el `.env` viejo de la Copy vs `apvnaigldsjqeloiolcu` en el backend) — hay que confirmar contra el `.env` real (hoy vacío de esas variables) cuál tiene los usuarios de producción.
- Todo lo demás (`profile.ts`, `habits.ts`, `rocks.ts`, etc.) sigue usando `API_BASE_URL` contra el backend Java, pasando el `access_token` de la sesión de Supabase.

### 1.2 ¿Cómo sabe el backend quién llama? → `X-Actor-Id` hoy, JWT real cuando cierre B-2

Los 14 módulos, sin excepción, leen el actor de `X-Actor-Id: <uuid>` — un puente temporal y documentado como inseguro (`SecurityConfig` hoy hace `anyRequest().permitAll()`). Migrar a validación real de JWT (RS256/JWKS de Supabase) está bloqueado por **B-2** (confirmar que Supabase usa claves asimétricas, no el secreto legacy HS256).

**Acción recomendada — no esperar a B-2 para poder probar el resto del plan:**
1. El frontend manda **los dos headers** en toda llamada autenticada al backend Java: `Authorization: Bearer <access_token de Supabase>` (a futuro, inofensivo hoy) y `X-Actor-Id: <mismo user.id>` (lo que el backend usa en la práctica ahora mismo).
2. Cuando B-2 cierre y el Resource Server esté validando de verdad, se retira `X-Actor-Id` de las 45 llamadas de una — es un cambio mecánico porque nunca se dejó de mandar `Authorization` en paralelo.
3. **Mientras tanto, el backend no se expone a internet** — ya lo dice `CLAUDE.md`. Esto es solo para desarrollo local contra datos de prueba.

---

## 2. Estrategia de storage: patrón consistente, no por endpoint

El backend implementa el mismo patrón en varios módulos (`onboarding` media, `phasecontracts` firmas, `rocks` evidencia): **pedir una URL firmada al backend → subir el archivo directo a esa URL → avisarle al backend `bucket`+`ruta` para que registre el recurso**. El frontend hoy hace lo contrario en la mayoría de los casos: sube directo a Supabase Storage con su propio cliente y le manda al backend la URL pública resultante.

No es un detalle de nombres de campo — es un protocolo distinto. **Cada pantalla que sube un archivo necesita revisar si su módulo de backend ya tiene un endpoint `.../upload-url`** (confirmado que sí en `onboarding`, `phasecontracts`, `rocks`) y adoptar el flujo de dos pasos ahí. Para `habits` (evidencia de hábito), el endpoint real espera `bucket`+`rutaStorage` pero no quedó confirmado si tiene su propio `upload-url` dedicado — **hay que confirmarlo antes de asumir el mismo patrón** (ver GAP-14).

---

## 3. Plan por fases

```
FASE 0   Desbloqueo de auth        →  sin esto, nada de lo demás se puede probar de punta a punta
FASE 1   Remapeo mecánico          →  rutas/verbos/shapes que YA tienen backend real, solo hay que apuntar bien
FASE 2   Huecos reales de backend  →  código nuevo, priorizado (§5)
FASE 3   Decisiones de arquitectura →  chat a WebSocket, streaming de Renasia, Espejo de Sombra on-demand, ranking agregado
```

### FASE 0 — Desbloqueo de auth `[bloqueante, ver §1]`

1. Confirmar proyecto de Supabase real (decisión del dueño).
2. Reponer env vars de Supabase en `.env`, revertir `auth.ts` al SDK real.
3. Todo servicio de negocio manda `Authorization` + `X-Actor-Id` en paralelo (§1.2).
4. CORS ya está resuelto (esta sesión, commit `4344831`) — no es bloqueante, ya se puede probar contra el build web.

### FASE 1 — Remapeo mecánico (frontend, bajo riesgo)

Esto es la mayoría del trabajo en volumen: **decenas de llamadas** donde el backend real ya hace lo que la pantalla necesita, pero el frontend le pega a la ruta/verbo/forma equivocada. Ejemplos representativos de cada módulo (la lista completa de cada archivo la tienen los 4 informes de esta sesión, referenciados por módulo):

| Módulo | Ejemplos de remapeo |
|---|---|
| `users` | `getMyProfile`: `GET`→`POST /users/me`. `updateUserProfile`: shape snake_case→camelCase, sin `phone`. `updateStaffRole`: `admin/staff/{id}/role`→`/users/{id}/role` |
| `onboarding` | `saveAnswers` debe resolver `questionKey→questionId` numérico vía `GET /questionnaire` antes de escribir. Media: adoptar el flujo `upload-url`→`PUT`→`POST /media` |
| `phasecontracts` | `firmarPactoFase` no lleva body — el backend ya sabe la ruta de la firma del paso `upload-url` anterior |
| `academy` | `listarCursosBloqueados`/`marcarLeccionCompletada`/`desmarcarLeccion`: dejar de usar Supabase directo, apuntar a `GET /cursos/bloqueados` y `POST`/`DELETE /lecciones/{id}/complete` que ya existen |
| `community` | `updateCell`/`updateCohort`/`updateCohortStatus`: `POST`→`PATCH`. `assignMentor`: `POST`→`PUT` |
| `calendar` | Portada de evento: reescribir del multipart de un paso al flujo `upload-url`→`PUT`→`confirm` |
| `habits` | `updateHabitStatus`: `PATCH /habit-tracks/{id}`→`POST .../complete` (y no hay "descompletar"). Evidencia: `fileUrl`→`bucket`+`rutaStorage` |
| `rocks` | `submitEvidence`: mismo cambio de shape + adoptar `POST .../evidence/upload-url`. `updateWeeklyRock`: 3 campos sueltos→un array `accionesCriticas` |
| `enforcer` (Verdugo) | `relatedEntityType`/`outcome`: traducir valores (`HABIT→REGISTRO_HABITO`, `COMPLETED→COMPLETADO`) — confirmar con el equipo el mapeo de `SNOOZED`/`POSTPONED` |
| `radar` | Migración limpia de Supabase directo a `POST/GET /radar*` — sin gaps, los 3 endpoints ya existen y calzan |
| `chat` | Prefijo `/chat/` faltante en todas las rutas; `startDirectConversation` body `userId`→`otherUserId`; `markConversationRead` `PUT`→`POST` |
| `notifications` | Traducir el enum `type` (backend en español, frontend en inglés) — **en el cliente**, nunca renombrar el enum del backend (la BD está congelada) |
| `support` | Ya casi todo calza — solo migrar `uploadSupportAttachment` al flujo de URL firmada |

**Regla de esta fase:** se corrige del lado del frontend salvo que el propio informe marque el ítem como GAP real (ver §5) — el backend es el lado con 1112 tests, no se le pide que se adapte a una forma que el frontend inventó sin verificación.

### FASE 2 — Huecos reales de backend

Ver la tabla consolidada en §5. Prioridad sugerida dentro de esta fase: primero lo que **bloquea pantallas que ya existen y se usan** (ranking, evidencia listado, clase diaria), después lo que es **funcionalidad nueva completa** (admin de hábitos, journal, logros).

### FASE 3 — Decisiones de arquitectura (no son bugs, son elecciones)

| Decisión | Opciones | Recomendación |
|---|---|---|
| **Chat: ¿migrar a WebSocket/STOMP ya?** | (a) Mantener polling de 15s del lado cliente (el backend lo soporta sin cambios). (b) Migrar `RealtimeChannel` (`supabase.ts`) al STOMP/`/ws` real, ya construido y endurecido contra una vulnerabilidad real (E-37: auth en el handshake y en la suscripción). | **(b) migrar ahora.** La inversión del backend ya está hecha y probada; el polling deja hasta 15s de latencia en una conversación en vivo, que es justo donde más se nota; el swap queda aislado a `RealtimeChannel`, no toca las pantallas |
| **Renasia: formato de streaming** | El backend emite SSE real (`text/event-stream`, `Flux<String>` → Spring lo enmarca como `data: ...\n\n`). El frontend (`renasia.ts`) espera texto plano sin envoltura y hoy mostraría literalmente `data: ` y líneas en blanco en el chat. (a) el frontend agrega un parser de SSE real. (b) el backend cambia a `text/plain`. | **(a).** SSE es el estándar para este caso de uso (reconexión, multiplexado futuro); el parser es un cambio chico y localizado en un solo lugar (`streamAsk`) |
| **Espejo de Sombra: ¿generación on-demand?** | El frontend (`shadowMirror.ts`) espera poder generar un informe "ahora" (`canGenerate`, `POST .../weekly-report`). El backend genera **solo** por scheduler, lunes 03:00 UTC, sobre la semana anterior completa, para todos los participantes — decisión ya tomada y documentada (`docs/MODULO_RAG.md`). | **Ajustar la expectativa del frontend**, no el backend: "tu informe de la semana pasada llega automáticamente cada lunes". Si el producto de verdad quiere on-demand, es un caso de uso nuevo a diseñar (no inventar la regla de "cuántas entradas alcanzan", CLAUDE.MD §0.6) |
| **Ranking: ¿agregador de un solo llamado?** | El frontend espera `GET /ranking` devolviendo cohorte + célula + 3 rankings en un solo objeto. El backend real es `GET /ranking/{tipo}` plano, un tipo a la vez, sin agrupar por célula (ese dato vive en `community`, no en `points`). | **Decidido y construido (2026-08-26): sí, agregador nuevo.** Ver gap #24 — `GET /api/v1/ranking` (sin tipo) ya compone LEAGUE+CELL+GENERAL+célula del actor. **No** replica 1:1 el contrato viejo (`celulas`/`miCelula`/`miCelulaPorHabitos` por célula) — ver el propio gap #24 para el porqué |
| **Escritura de entradas de diario** | El dominio (`EntradaDiario`, puertos, persistencia) ya existe en `habits` — pero **no hay ningún caso de uso ni controller que lo escriba**. Bloquea Shadow Mirror y cualquier otro tipo de entrada de diario. | Construir `POST /api/v1/entradas-diario` (o ruta similar) — es trabajo de backend real, no una decisión de producto |

---

## 4. Estado de la base de datos (re-verificado 2026-08-26 contra el código real, no contra el análisis viejo)

**78 de 90 tablas en uso (87%).** Las 12 restantes, agrupadas:

- **RBAC superado (D-21, 3 tablas)** — `permisos`, `rol_permiso`, `auditoria_cambios_rol`. Decisión ya tomada: los permisos se resuelven con el enum `UserRole` en código. `roles` (la 4ta tabla del set RBAC original) sí está en uso — `academy.RolesCatalogo` la lee por SQL nativo para traducir `roles.clave → UserRole`.
- **Funciones de `habits` no construidas (7 tablas)** — `categorias_habito`, `iconos_habito`, `audioterapias`, `dias_semanales_habito`, `historial_cambios_horario`, `revisiones_semanales_sin_celular`, `mensajes_bienvenida`. **Coincide exactamente** con GAP-11/GAP-12 de este documento (`habitsAdmin.ts` entero, `weekly-habit-days`, `habit-preferences`, `habits/:id/rename`) — dos auditorías independientes, misma conclusión: esas funciones del frontend nunca tuvieron backend.
- **Ranking por célula (1 tabla)** — `ranking_celulas`, documentado como pendiente en el propio código de `community` (`MiCelulaResponse`). Coincide con GAP-24 (ranking agregado).
- **Catálogo de grupos sin lectura (1 tabla)** — `grupos` (`id, nombre, creado_en`). `academy` sí usa su tabla hija `miembros_grupo`, pero nada resuelve todavía `grupoId → nombre` — puede ser una función pendiente, no necesariamente un gap bloqueante.

(El módulo `rag`, construido esta sesión, ya usa sus 6 tablas — `base_conocimiento`, `conversaciones_renasia`, `mensajes_renasia`, `fuentes_mensaje_renasia`, `informes_espejo_sombra`, `preguntas_confrontacion` — por eso el número subió de 75 a 78 respecto al análisis anterior.)

---

## 5. Tabla consolidada de GAPs reales de backend

Todo lo que sigue es trabajo de **código nuevo en el backend**, no un simple remapeo de URL. Ordenado por módulo; dentro de cada uno, lo que bloquea una pantalla ya construida primero.

### `users` / `onboarding` / `phasecontracts`

| # | Gap | Bloquea |
|---|---|---|
| 1 | ~~No existe `TraineeProfile` como dominio (`programDay`, `startDate`, `phase`, metas) — solo `ParticipacionPrograma`~~ | ✅ **Cerrado 2026-08-26 — y el enunciado original era falso.** Verificado contra D-33 (docs/MODULO_USERS.md §6.bis): `ParticipacionPrograma` **es** el `TraineeProfile`, decisión ya tomada el 24, no un hueco nuevo. Lo que de verdad faltaba eran 3 columnas sin mapear (`tipo_meta`, `nombre_reto_personal`, `programa_completado_en` — existían en el baseline, `ParticipacionProgramaJpaEntity` las excluía a propósito) y 2 endpoints puntuales, no un agregado nuevo. Cerrado con: (a) los 3 campos mapeados en dominio/JPA/mapper con traducción explícita `TipoMeta`↔`TipoMetaJpa` (D-36); (b) `POST /api/v1/users/me` enriquecido con `traineeProfile` (`GetMyFullProfileUseCase`, compone `User`+`ParticipacionPrograma` en la capa de aplicación — nunca en el controller, §5.4.6); (c) `PATCH /api/v1/users/me/trainee-profile` (`UpdateTraineeProfileUseCase`) para `personalChallengeName`, self-only. Formas de request/response calcadas de `services/profile.ts` del frontend real |
| 2 | ~~Sin lectura de respuestas de onboarding (`GET /onboarding/answers`) — solo escritura~~ | ✅ **Cerrado 2026-08-26.** `GET /api/v1/onboarding/answers?flow=X` (`ObtenerRespuestasUseCase`, en `RespuestaService`): respuestas ya guardadas del actor, agrupadas por sección del cuestionario (secciones sin ninguna respuesta no aparecen). Alcance propio: nunca lee respuestas de otro usuario (no hay parámetro de "usuario objetivo") |
| 3 | ~~Sin validación de "Meta Maestra" en texto libre (solo existe la de audio V90)~~ | ✅ **Cerrado 2026-08-26.** `POST /api/v1/onboarding/master-goal/validation` (`ValidarMetaMaestraUseCase`), body `{ text }`, shape de respuesta idéntico a `SixPsValidation` del frontend real (`accepted`/`missingPs`/`feedback`/`pendingReview`). **Ruta nueva, no `/onboarding/smart/validate`** del backend viejo — pendiente de remapeo en Fase 1 cuando se cablee `validateMetaMaestra` en `src/services/onboarding.ts`. **Divergencia deliberada del patrón async+polling** de V90: es SÍNCRONO — el texto se valida ANTES de guardarse como respuesta (confirmado contra `diseno-destino.tsx`: valida un borrador en memoria y solo hace `flush()` del autosave si `accepted`), así que no hay una fila propia donde colgar un estado `PROCESANDO`/intentos sin violar D-40 (BD congelada). Detalle completo en el javadoc de `ValidacionMetaMaestraPort`/`ValidarMetaMaestraUseCase`. Sin integración de IA real todavía (mismo estado que V90): `NoOpMetaMaestraValidacionIAAdapter` siempre responde "no disponible", y el caso de uso lo trata fail-open (`pendingReview: true`), igual que documentaba el backend viejo |
| 4 | ~~Sin endpoint de avatar genérico en `users`~~ | ✅ **Cerrado 2026-08-26.** Mismo patrón de dos pasos que `rocks`/`habits`/`calendar`: `POST /api/v1/users/me/avatar/upload-url` (`SolicitarUrlAvatarUseCase`, bucket compartido `renaser-files`, ruta `avatares/{userId}`) → cliente sube directo → `PATCH /api/v1/users/me/avatar` (`ConfirmarAvatarUseCase`) con `{bucket, ruta}`. **Limitación documentada a propósito, no un gap silencioso**: `usuarios.avatar_url` ya es un string plano que `community`/`chat`/`mentor` consumen tal cual vía `UserSummary` — cambiar esa semántica a "requiere resolución" rompería esos consumidores (fuera de alcance). La confirmación resuelve una URL de lectura firmada de 7 días (la validez más larga razonable) y la persiste tal cual, en vez del patrón "guardar solo la ruta y resolver en cada lectura" que sí usa `calendar` para su portada — swap pendiente el día que exista un adaptador de storage real con URL pública permanente (hoy todo el sistema sigue en `NoOpAlmacenamientoAdapter`, D-34) |
| 5 | ~~Sin flujo de baja de cuenta (GDPR) — requisito de Google Play/Apple~~ | ✅ **Cerrado 2026-08-26.** Portado 1:1 de `features/account-deletion` del backend viejo: soft-delete diferido, **14 días de gracia confirmados** (no un supuesto — coincide el comentario de `usuarios.baja_solicitada_en` en el baseline SQL con `DIAS_DE_GRACIA` del backend viejo), configurable sin recompilar (`renaser.users.account-deletion.grace-period-days`). `GET/POST/DELETE /api/v1/users/me/account-deletion` (mismo estilo de recurso que `/mentor/activate-tracking`, D-34): `POST` exige `{confirmacion:"ELIMINAR"}`, es idempotente (repetirla no reinicia el contador), y el acceso se conserva durante la gracia a propósito (si no, no habría forma de arrepentirse — `DELETE` cancela). Cron diario (`PurgarCuentasBajaScheduler`, 04:15 UTC) hace el hard delete real. A diferencia del backend viejo (Prisma + Supabase Auth: 26 tablas + Storage + Auth borrados a mano en `borrarCuenta`), acá **un solo `DELETE FROM usuarios`** alcanza: las ~30 FK del baseline son `ON DELETE CASCADE` (o `SET NULL` en las de auditoría) y desde D-49 el propio backend es dueño de credenciales/identidades — el email queda libre de inmediato por el propio `UNIQUE`. Demostrado con un test de integración contra Postgres real: crear cuenta → pedir baja → purgar → registrar de nuevo con el mismo email → funciona (`AccountDeletionIntegrationTest`) |
| 6 | ~~Sin módulo admin de `staff` (listar, invitar con generación de contraseña, cambiar estado, editar a otro usuario)~~ | ✅ **Cerrado 2026-08-26.** `GET/POST /api/v1/admin/staff`, `POST /invite` (genera contraseña temporal de 20 bytes aleatorios, la hashea con el `PasswordEncoder` ya declarado y la envía por `EnviarEmailPort.enviarInvitacionStaff` — nuevo método del mismo puerto que ya usaba el reset de contraseña, reutilizado tal como pedía el encargo, no duplicado), `PATCH /{id}/status` (suspender revoca todas las sesiones vía `CerrarTodasLasSesionesUseCase`, docs/MODULO_AUTH.md §7.4), `PUT /{id}` (editar nombre/avatar/bio/departamento de otro usuario, sin `role`: eso sigue siendo `PATCH /users/{id}/role`). Nueva pieza reutilizable: `RequireAdminGuard` (fail-closed, nunca lanza para un actor inexistente — E-42) |
| 7 | ~~Sin módulo admin de `trainees` (listar, detalle, editar día de programa)~~ | ✅ **Cerrado 2026-08-26.** `GET /api/v1/admin/trainees` (paginado), `GET /{id}` (detalle: usuario + resumen de `ParticipacionPrograma`), `PUT /{id}/program-day` (`ParticipacionPrograma.fijarDia`, nuevo método de dominio — mismo límite [0,90] que ya imponía `avanzarDia`, no es una regla nueva). En los tres, el recurso objetivo se carga ANTES que el gate de admin (E-42) |
| 8 | ~~Sin dashboard admin de onboarding (agregado)~~ | ✅ **Cerrado 2026-08-26.** `GET /api/v1/admin/onboarding/dashboard` — vive DENTRO de `onboarding` (no en `users`): compone sus propios puertos (`LoadEstadoOnboardingPort.contarResumen`, `LoadGrabacionV90Port.contarPorEstado` nuevos) más `users.api.UserSummaryFinder`/`ParticipacionProgramaFinder` para el gate de rol y el total de aprendices — nunca reutiliza `ConsultarActorPort` de este mismo módulo porque ese puerto es compartido por 6 servicios existentes y no expone rol |
| 9 | ~~Sin listado/borrado admin de solicitudes de cuenta, ni auto-check de email/estado propio~~ | ✅ **Cerrado 2026-08-26.** `GET/DELETE /api/v1/account-requests` (admin, paginado) + `GET /api/v1/account-requests/{id}/status` **PUBLIC_ENDPOINT** — decisión de diseño: se resuelve por el `AccountRequestId` que el cliente ya guardó del 202 de `submit`, no por email (evita abrir una enumeración de qué emails tienen solicitud) |

### `habits` / `rocks` / `evidence` / `points`

**Actualizado 2026-08-26 (noche) — lote de 3 agentes en paralelo cerró 8 de estos 14 gaps, más uno resuelto de rebote. Verificado contra el código real, no contra el reporte de los agentes.**

| # | Gap | Estado |
|---|---|---|
| 10 | `GET /habit-tracks/today` no trae catálogo del hábito | ✅ **Cerrado.** `ConsultarTracksDelDiaConCatalogoUseCase` + `TracksDelDiaProyeccionService`, una proyección por lote (sin N+1) |
| 11 | `habitsAdmin.ts` completo sin backend (catálogo, horarios, guías/adjuntos) | 🟡 **Mayormente cerrado (2026-08-26).** CRUD admin completo de catálogo (`HabitoAdminController`), horarios (`HorarioHabitoAdminController`, PATCH real con distinción "ausente" vs "null explícito") y guías con `closePrevious` (`GuiaHabitoAdminController`) — todos `/api/v1/admin/habits/**`, solo ADMIN/ALCHEMIST. Adjuntos de guía solo tipo ENLACE (`GuiaAdjuntoAdminController`): la subida de archivo (`POST .../guide-attachments/upload`, IMAGEN/AUDIO) **no se construyó** — necesita multipart (no hay precedente en este backend) y un puerto de almacenamiento que reciba bytes (`AlmacenamientoPort` hoy solo firma URLs), ninguno de los dos existe todavía. `habitType`/`tipo` y `claveSistema` quedan protegidos como invariantes: no editables después de creado (ver `Habito.actualizarDetalles` javadoc) |
| 12 | Sin `habit-preferences`, `weekly-habit-days`, renombrado de hábito, `habit-unlocks` | ✅ **Cerrado.** `HabitPreferenceController`, `WeeklyHabitDayController`, `HabitRenameController`, `HabitUnlockController` (este último solo lectura — el algoritmo de escalonamiento en sí no se portó) |
| 13 | Cierre de racha "Día sin celular" no acepta evidencia | ✅ **Cerrado.** `CerrarRachaCommand` exige evidencia, colgada del registro que inició la racha |
| 14 | No confirmado si `habits` tiene su propio `upload-url` para evidencia | ✅ **Resuelto de rebote** al cerrar el #13: `POST /habit-tracks/racha/phone-free/evidence/upload-url`, mismo patrón que `rocks`/`onboarding` |
| 15 | Sin dashboard agregado `GET /rocks` | ✅ **Cerrado.** `DashboardRocasController` — semáforo, grilla semanal, compuertas de Ley II. Los umbrales exactos se recuperaron del backend viejo, citados línea por línea |
| 16 | Sin Diario Nocturno (`journal/today`) | ✅ **Cerrado, y en `habits` no en `rocks`** — es el mismo concepto que `EntradaDiario` (dominio ya existía). `JournalTodayController`, cierra también el #31 |
| 17 | `publishedToWall`/`esPrincipal` no aceptados al completar una Roca | ✅ **Cerrado 2026-08-26.** `community.api.PublicarEnMuroPort` (nuevo) crea una publicación real en el Muro (`HITO_AUTOMATICO`) desde una evidencia ya subida; `RocaDiariaService.completar()` lo invoca cuando `publishedToWall=true`, dentro de la misma transacción. Solo evidencia visual (FOTO/VIDEO/CAPTURA) — TEXTO/AUDIO rechazados con 400 al construir el comando |
| 18 | `DestinoVerdugo` sin valor para Código Renaser (`RADAR`) | 🟡 **Investigado a fondo (2026-08-26), dos hallazgos distintos.** (1) *Hábitos personales por FK: YA FUNCIONA, verificado, sin cambio de código.* `registros_habito.habito_id` referencia la tabla unificada `habitos` (P-12: SISTEMA y PERSONAL en la misma tabla) y `VerificarDestinoVerdugoPersistenceAdapter.registroHabitoPerteneceA` filtra solo por dueño, nunca joinea `habitos.ambito` — un evento Verdugo contra el track de un hábito PERSONAL pasa igual que uno SISTEMA. Confirmado con test nuevo contra la base real (`VerificarDestinoVerdugoPersistenceAdapterTest`). (2) *Código Renaser: sigue bloqueado, no se inventó nada.* Agregar `DestinoVerdugo.CODIGO_RENASER` necesitaría una columna nueva en `eventos_verdugo` (hoy el CHECK `verdugo_un_destino` solo conoce `registro_habito_id`/`roca_diaria_id` — BD congelada, D-40) **y** una regla de negocio que no existe en ningún lado: `registros_radar` es un log append-only sin `diaFin`/estado pendiente (confirmado en el propio código: "no hay 'uno por día'... el gating de horario es UX del cliente, nunca una restricción de servidor") — Verdugo dispara sobre un *plazo vencido*, y Código Renaser no tiene plazo de servidor. Sin esa regla confirmada por negocio, no se implementa (CLAUDE.MD "no inventar reglas de negocio") |
| 19 | Sin listado `GET /evidence` | ✅ **Cerrado.** `ListarEvidenciaUseCase`, paginación por keyset, alcance de mentor verificado contra la asignación real |
| 20 | Panel admin de evidencia: sin listado; el "override" no tiene equivalente | ✅ **Cerrado.** `ListarEvidenciaAdminUseCase` + `EvidenciaAdminController`. El "override" resultó ser un gap real (devolver puntos de una penalización revertida), no un remapeo — se construyó reutilizando el estado `ANULADA_ADMIN` ya existente, sin tocar el esquema |
| 21 | Sin agregador `GET /home` (resumen del día) | 🟡 **Parcialmente cerrado (2026-08-26).** `GET /api/v1/home` existe en `points` y devuelve `puntosLiga`/`coherencia`/`rachaActual`/`rachaMaxima` (dominio propio) — eso es lo único que `points` puede componer sin inventar nada. El resto del `HomeSummaryResponse` que espera el frontend (`programDay`, `currentPhase`, `weekStatus`, `habitsToday`, `rocksToday`, `radarCheckinsToday`, etc.) sigue sin backend: no hay finder en `habits.api` para habitos/rocas del día, `calendar.api` solo publica un evento (no un finder de "próximo evento"), y `notifications` todavía no declara ningún paquete `api` (`@NamedInterface`). La respuesta actual documenta esos 3 huecos explícitamente en un campo `bloqueos` en vez de devolver datos inventados. El agregador final (con `programDay`/`currentPhase`, que son de `users`/`TraineeProfile`, gap #1) probablemente no debería vivir en `points` — es una decisión de qué módulo lo compone, no resuelta acá |
| 22 | "Logros" no es un concepto de dominio en `points` | ⬜ **Sigue abierto — pero con hallazgo real (2026-08-26), no inventado.** El backend viejo SÍ tenía "logros": `GET /api/v1/profile/logros` (`RenaserBack/src/app/api/v1/profile/logros/route.ts`, ref. "P-05") → `profileService.getLogros` (`RenaserBack/src/features/profile/service.ts:274-329`) → tipo `LogrosResponse` (`RenaserBack/src/features/profile/schema.ts:43-58`). **No es una lista de insignias con nombre/criterio server-side** — el comentario del propio endpoint lo dice literal: *"Datos crudos para las insignias de Logros en el móvil — el móvil decide cuáles se ven desbloqueadas, esta ruta solo agrega los números reales"*. El shape exacto: `{ programDay, streak, totalHabitsCompleted, firstHabitCompletedAt, totalRocksCompleted, firstRockCompletedAt, bestRocksStreakDays, radarEntriesCount, firstRadarEntryAt }`. Confirmado 1:1 contra la app real: `C:\renaserPlayStore\src\types\logros.ts`, `src\services\logros.ts` (`GET /api/v1/profile/logros`, sin cambios) y `src\hooks\useLogros.ts` esperan exactamente ese contrato. **Por qué no se construyó igual:** ninguno de sus 9 campos es dominio de `points` (mi alcance) — `programDay` es de `TraineeProfile` (`users`, gap #1, todavía no existe como dominio), los 4 campos de hábitos/rocas necesitan finders que hoy no existen en `habits.api`/`rocks.api` (conteos y "primera vez completado", más `bestRocksStreakDays` que es la racha más larga histórica de rocas — lógica portable desde `service.ts:248-270`, `longestRocksStreakDays`), y los 2 de radar necesitan un finder de `habits.api` (el Radar vive dentro de `habits`, paquete `habits.application.*.radar`, pero `habits.api` hoy no expone nada de eso). Construirlo en `points` sin esos datos sería inventar un dominio fantasma. **Recomendación:** es un caso de uso de `users`/perfil (o quien termine siendo dueño de `TraineeProfile`), que compone via `habits.api` + `rocks.api` una vez que esos módulos expongan los finders — no una tarea de `points` |

### `academy` / `community` / `calendar`

| # | Gap | Bloquea |
|---|---|---|
| 23 | `POST /classroom/clase-diaria` (completar) no existe — explícitamente diferido a coordinar con `habits` | ✅ **Cerrado.** Investigado contra el repo viejo: son dos escrituras relacionadas, no un solo concepto — `completeClaseDiaria` (`clase-diaria/service.ts:60-90`) cierra el hábito de catálogo `DAILY_CLASS` (`habits/service.ts:1751-1811`) y *después* marca `leccion_progreso`. `habits.api.CompletarClaseDiariaHabitoUseCase` (nuevo) expone la primera mitad; `academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase` orquesta ambas reusando `CompletarLeccionUseCase` ya existente. Ver `docs/MODULO_ACADEMY.md` §6 (AC-13, cerrada) |
| 24 | `GET /ranking` agregado (cohorte + célula + 3 rankings en un llamado) no existe — ver decisión en §3 | ✅ **Cerrado 2026-08-26 (parcial a propósito).** `GET /api/v1/ranking` (sin `{tipo}`) en `points` — `ConsultarRankingAgregadoUseCase`/`RankingAgregadoService` — devuelve en un llamado: `liga`/`coherenciaIndividual`/`general` (los 3 snapshots LEAGUE/CELL/GENERAL que `points` ya generaba) + `celula` del actor, vía `community.api.CelulaFinder.celulaDeParticipante` (metodo nuevo, extension minima de esa interfaz — implementado en `CelulaService`). **NO replica el contrato viejo 1:1**: `celulas` (ranking de células dentro del cohort, ordenadas por `coherenceScoreGroup`/`rankingPosition`) y `miCelula`/`miCelulaPorHabitos` (ranking de miembros DENTRO de la célula propia) se dejaron afuera a propósito — la tabla `ranking_celulas` existe en el baseline SQL pero sin dueño decidido (`docs/MODULO_POINTS.md` Q-1/Q-1b: ¿la puebla `points` o `community`? y con qué fórmula — el cron viejo `coherence-group-score` promedia `coherenceScore` de los miembros activos de cada célula y rankea dentro del cohort, `RenaserBack/src/app/api/cron/coherence-group-score/route.ts`, formula real y citable si se decide implementarla). Construirlo sin esa decisión hubiera sido inventar quién es dueño de una tabla — se documenta como pregunta abierta en vez de asumir |
| 25 | Panel admin de células/cohortes incompleto | 🟡 **Mitad cerrado 2026-08-26.** `GET /admin/cells/dashboard` (cross-cohorte), `/mentores-disponibles`, `/mentores`, `/aprendices-disponibles` construidos — ver docs/MODULO_COMMUNITY.md sec. 8. Sigue abierto: asignar/quitar aprendiz de célula (escribe `participantes_programa.celula_id`, tabla de `users`, sin puerto de escritura público — requiere tocar `users`, fuera de alcance de este cambio) y `rankingPosition`/`coherenceScoreGroup` (siguen en `points`, que todavía no expone un finder de ranking por célula — mismo Q-1/Q-1b que documenta `CelulaFinder.celulaDeParticipante`) |
| 26 | `TestimonioController` existe pero `TestimoniosPanel.tsx` sigue hablando directo con Supabase (lectura, escritura y foto) | No es gap de backend — es remapeo pendiente, listado en Fase 1 por completitud |

### `chat` / `notifications` / `support` / `rag`

| # | Gap | Bloquea |
|---|---|---|
| 27 | Sin endpoint de directorio de usuarios/miembros (`GET /members`) | ✅ **Cerrado 2026-08-26.** `GET /api/v1/chat/members?cursor=&q=` (`MiembroController`/`ListarDirectorioMiembrosUseCase`/`MiembroService`). Se apoya en que TODO usuario activo es participante de la conversación GLOBAL por auto-join (`V1__baseline_renaser.sql:1293-1295`) — no hizo falta pedirle una capacidad nueva de búsqueda a `users.api`. Devuelve los CINCO roles (confirmado contra `MiembrosPanel.tsx` real, no el comentario desactualizado de `chat/types.ts` que decía "solo TRAINEE/MENTOR"), excluye al propio actor y a los `SUSPENDED` (no tiene sentido ofrecerlos como destino de un DM nuevo — `obtenerOCrear` los rechaza igual), filtra por nombre *contains* case-insensitive, cursor keyset por id. **Nota de escala, documentada en `MiembroService`:** el filtro/paginación se resuelve en memoria sobre la lista completa de participantes de GLOBAL (no hay forma de empujarlo a SQL sin cruzar el límite del módulo hacia la tabla `usuarios` de `users`) — aceptable al tamaño de comunidad actual; si la base de usuarios activos crece mucho, la solución es una capacidad de búsqueda nueva en `users.api` (trabajo de otro módulo) |
| 28 | Sin miembros/rename de la conversación global | ✅ **Cerrado 2026-08-26.** `GET /api/v1/chat/conversations/global/members` (`ListarMiembrosGlobalUseCase`, mismo `MiembroService` — roster informativo, NO filtra por `UserStatus` a propósito, a diferencia de #27) y `PATCH /api/v1/chat/conversations/global/name` (`RenombrarConversacionGlobalUseCase`/`ConversacionService.renombrar`, dominio: `Conversacion.renombrada`). Restringido a ADMIN/ALCHEMIST (`UserRole.canManageRoles()`) — **no es una suposición propia**: confirmado literal contra `GlobalChatInfoSheet.tsx` de la app real ("'admin' ya representa ADMIN + ALCHEMIST juntos... es el mismo criterio que usa el backend (isAdminRole) para aceptar el PATCH") |
| 29 | Mensajes de chat sin nombre/avatar del emisor ni preview de respuesta resuelto — solo ids crudos | ✅ **Cerrado 2026-08-26.** `MensajeService.listar` arma una proyección de lectura (`MensajeEnriquecido`) que resuelve nombre/avatar del emisor y el preview de "respuesta a" para toda una página en, como mucho, dos consultas EN LOTE (`LoadMensajePort.porIds` para los mensajes originales citados + `UserSummaryFinder.findByIds` para todos los emisores involucrados) — nunca una consulta por mensaje, verificado con test dedicado. El preview trunca a los primeros 80 caracteres (`MensajeEnriquecido.LARGO_PREVIEW`) — **valor propio, no confirmado por producto**, ver `docs/BITACORA_ERRORES.md` si hace falta ajustarlo. `MensajeResponse` mantiene un overload sin enriquecer (`from(Mensaje)`) para el `lastMessage` de `ConversacionResumenResponse`, que el frontend real no pide con nombre/avatar |
| 30 | ~~Sin "marcar/reportar mensaje" en Renasia~~ | ❌ **No es un gap — es D-49 (RAG), decisión ya tomada el 2026-08-25: "el dueño decidió quitar la funcion de marcar un mensaje del chat de la IA"** (`docs/MODULO_RAG.md`). Este item se coló al reescribir este documento el 26/08 sin cruzar esa decisión ya firmada. Reconfirmado 2026-08-26 noche: sigue rechazado, no se construye |
| 31 | ~~Sin caso de uso ni controller para escribir una entrada de diario~~ | ✅ **Cerrado 2026-08-26** — ver #16, es el mismo trabajo (Bitácora Nocturna) |

---

## 6. Preguntas abiertas que requieren decisión del producto (no se inventan, CLAUDE.MD §0.6)

- **¿Cuál proyecto de Supabase es el real?** (bloqueante para Fase 0)
- **¿Se asigna rol al aprobar una solicitud de alta, o toda alta pública es siempre `APRENDIZ`?** — la pantalla admin deja elegir, el backend lo ignora hoy.
- **¿El campo `phone` va al dominio `User`?** — el frontend lo edita, el backend no lo tiene.
- **Mapeo semántico de `SNOOZED`/`POSTPONED` (Verdugo)** contra `POSTERGADO`/`POSPUESTO_30` del backend — no es obvio cuál es cuál.
- **¿Espejo de Sombra debería poder generarse on-demand?** (ver §3) — si la respuesta es sí, falta definir el criterio de "cuántas entradas de diario alcanzan".
- ~~¿Vale la pena un endpoint agregador de Ranking...?~~ — **Resuelto 2026-08-26**: sí, construido (gap #24). Queda abierta la sub-pregunta de `docs/MODULO_POINTS.md` Q-1/Q-1b: ¿quién puebla `ranking_celulas` (ranking de células dentro de un cohort) — `points` o `community` — y con qué fórmula (candidata: la del cron viejo `coherence-group-score`, citada en el gap #24)?
- **¿Quién es dueño del caso de uso "Logros" (gap #22)?** El backend viejo lo definía completo (`GET /api/v1/profile/logros`, shape confirmado 1:1 contra la app real) pero ninguno de sus campos es dominio de `points` — depende de `TraineeProfile.programDay` (`users`, gap #1, todavía no existe) más finders nuevos en `habits.api`/`rocks.api` (conteos, primera fecha, racha más larga de rocas, radar). ¿Se construye cuando `TraineeProfile` exista, como caso de uso de `users` que compone vía esos dos `api`, o se prefiere otro dueño?

---

## 7. Lo que NO hay que hacer

- **No construir el proxy genérico que pide `JAVA_BACKEND_CONTRACT.md`.** Es la decisión central de este documento (§0) — se saltearía toda la autorización de negocio ya construida y probada.
- **No renombrar el enum `TipoNotificacion` (ni ningún enum de dominio) al inglés.** La BD está congelada; se traduce en el cliente.
- **No cambiar el flujo de firma de `phase-contracts` en el backend** — es determinístico y está probado; el frontend se adapta.
- **No retirar `X-Actor-Id` antes de que el frontend mande JWT real y B-2 esté cerrado.**
- **No tocar la BD** — sigue vigente D-40: ni tablas nuevas, ni ALTER, ni seeds desde código.
- **No migrar los bypasses a Supabase en bloque** — uno por uno, verificando cada uno contra la app real antes de seguir con el próximo.
- **No inventar ninguna de las decisiones de negocio de §6.**
