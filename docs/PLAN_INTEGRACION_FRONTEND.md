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
| **Ranking: ¿agregador de un solo llamado?** | El frontend espera `GET /ranking` devolviendo cohorte + célula + 3 rankings en un solo objeto. El backend real es `GET /ranking/{tipo}` plano, un tipo a la vez, sin agrupar por célula (ese dato vive en `community`, no en `points`). | Depende de si el producto quiere mantener la pantalla actual tal cual (necesita un endpoint agregador nuevo, cruzando `points`+`community`) o rediseñarla sobre 4 llamadas + armado en el cliente. **Pregunta abierta, no la resuelve este documento** |
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
| 1 | No existe `TraineeProfile` como dominio (`programDay`, `startDate`, `phase`, metas) — solo `ParticipacionPrograma` | Perfil de aprendiz completo, `activate-program`, `start-date`, dashboards admin |
| 2 | Sin lectura de respuestas de onboarding (`GET /onboarding/answers`) — solo escritura | Resumir/hidratar un onboarding a medio terminar |
| 3 | Sin validación de "Meta Maestra" en texto libre (solo existe la de audio V90) | Pantalla `SixPsValidation` para metas escritas |
| 4 | Sin endpoint de avatar genérico en `users` | Subir foto de perfil |
| 5 | Sin flujo de baja de cuenta (GDPR) — requisito de Google Play/Apple | Cumplimiento de tienda, no solo UX |
| 6 | Sin módulo admin de `staff` (listar, invitar con generación de contraseña, cambiar estado, editar a otro usuario) | Panel de administración de personal |
| 7 | Sin módulo admin de `trainees` (listar, detalle, editar día de programa) | Panel de administración de aprendices |
| 8 | Sin dashboard admin de onboarding (agregado) | Panel admin |
| 9 | Sin listado/borrado admin de solicitudes de cuenta, ni auto-check de email/estado propio | Panel admin de altas, pantalla de "mi solicitud" |

### `habits` / `rocks` / `evidence` / `points`

**Actualizado 2026-08-26 (noche) — lote de 3 agentes en paralelo cerró 8 de estos 14 gaps, más uno resuelto de rebote. Verificado contra el código real, no contra el reporte de los agentes.**

| # | Gap | Estado |
|---|---|---|
| 10 | `GET /habit-tracks/today` no trae catálogo del hábito | ✅ **Cerrado.** `ConsultarTracksDelDiaConCatalogoUseCase` + `TracksDelDiaProyeccionService`, una proyección por lote (sin N+1) |
| 11 | `habitsAdmin.ts` completo sin backend (catálogo, horarios, guías/adjuntos) | ⬜ Sigue abierto |
| 12 | Sin `habit-preferences`, `weekly-habit-days`, renombrado de hábito, `habit-unlocks` | ✅ **Cerrado.** `HabitPreferenceController`, `WeeklyHabitDayController`, `HabitRenameController`, `HabitUnlockController` (este último solo lectura — el algoritmo de escalonamiento en sí no se portó) |
| 13 | Cierre de racha "Día sin celular" no acepta evidencia | ✅ **Cerrado.** `CerrarRachaCommand` exige evidencia, colgada del registro que inició la racha |
| 14 | No confirmado si `habits` tiene su propio `upload-url` para evidencia | ✅ **Resuelto de rebote** al cerrar el #13: `POST /habit-tracks/racha/phone-free/evidence/upload-url`, mismo patrón que `rocks`/`onboarding` |
| 15 | Sin dashboard agregado `GET /rocks` | ✅ **Cerrado.** `DashboardRocasController` — semáforo, grilla semanal, compuertas de Ley II. Los umbrales exactos se recuperaron del backend viejo, citados línea por línea |
| 16 | Sin Diario Nocturno (`journal/today`) | ✅ **Cerrado, y en `habits` no en `rocks`** — es el mismo concepto que `EntradaDiario` (dominio ya existía). `JournalTodayController`, cierra también el #31 |
| 17 | `publishedToWall`/`esPrincipal` no aceptados al completar una Roca | 🟡 **Mitad cerrado.** `esPrincipal` sí; `publishedToWall` sigue abierto — necesita un puerto de `community.api` que hoy no existe |
| 18 | `DestinoVerdugo` sin valor para Código Renaser (`RADAR`) | ⬜ Sigue abierto |
| 19 | Sin listado `GET /evidence` | ✅ **Cerrado.** `ListarEvidenciaUseCase`, paginación por keyset, alcance de mentor verificado contra la asignación real |
| 20 | Panel admin de evidencia: sin listado; el "override" no tiene equivalente | ✅ **Cerrado.** `ListarEvidenciaAdminUseCase` + `EvidenciaAdminController`. El "override" resultó ser un gap real (devolver puntos de una penalización revertida), no un remapeo — se construyó reutilizando el estado `ANULADA_ADMIN` ya existente, sin tocar el esquema |
| 21 | Sin agregador `GET /home` (resumen del día) | ⬜ Sigue abierto |
| 22 | "Logros" no es un concepto de dominio en `points` | ⬜ Sigue abierto |

### `academy` / `community` / `calendar`

| # | Gap | Bloquea |
|---|---|---|
| 23 | `POST /classroom/clase-diaria` (completar) no existe — explícitamente diferido a coordinar con `habits` | Marcar la clase diaria como completada |
| 24 | `GET /ranking` agregado (cohorte + célula + 3 rankings en un llamado) no existe — ver decisión en §3 | Pantalla de Ranking tal como está diseñada hoy |
| 25 | Panel admin de células/cohortes incompleto: sin dashboard cross-cohorte, sin `mentores-disponibles`/`mentores`/`aprendices-disponibles`, sin asignar/quitar aprendiz de célula; varios campos de respuesta faltan (`rankingPosition`, `coherenceScoreGroup`, shapes anidados) | Panel admin de células |
| 26 | `TestimonioController` existe pero `TestimoniosPanel.tsx` sigue hablando directo con Supabase (lectura, escritura y foto) | No es gap de backend — es remapeo pendiente, listado en Fase 1 por completitud |

### `chat` / `notifications` / `support` / `rag`

| # | Gap | Bloquea |
|---|---|---|
| 27 | Sin endpoint de directorio de usuarios/miembros (`GET /members`) | Buscador de DM, roster del chat global |
| 28 | Sin miembros/rename de la conversación global | Pantalla de info del chat global |
| 29 | Mensajes de chat sin nombre/avatar del emisor ni preview de respuesta resuelto — solo ids crudos | UI de chat con nombres/avatares sin una vuelta extra por usuario |
| 30 | Sin "marcar/reportar mensaje" en Renasia — el frontend ya tiene la UI, el backend no tiene ni el campo ni la ruta | Feature de moderación de chat con IA |
| 31 | ~~Sin caso de uso ni controller para escribir una entrada de diario~~ | ✅ **Cerrado 2026-08-26** — ver #16, es el mismo trabajo (Bitácora Nocturna) |

---

## 6. Preguntas abiertas que requieren decisión del producto (no se inventan, CLAUDE.MD §0.6)

- **¿Cuál proyecto de Supabase es el real?** (bloqueante para Fase 0)
- **¿Se asigna rol al aprobar una solicitud de alta, o toda alta pública es siempre `APRENDIZ`?** — la pantalla admin deja elegir, el backend lo ignora hoy.
- **¿El campo `phone` va al dominio `User`?** — el frontend lo edita, el backend no lo tiene.
- **Mapeo semántico de `SNOOZED`/`POSTPONED` (Verdugo)** contra `POSTERGADO`/`POSPUESTO_30` del backend — no es obvio cuál es cuál.
- **¿Espejo de Sombra debería poder generarse on-demand?** (ver §3) — si la respuesta es sí, falta definir el criterio de "cuántas entradas de diario alcanzan".
- **¿Vale la pena un endpoint agregador de Ranking**, o se rediseña la pantalla sobre las 4 llamadas planas que ya existen?

---

## 7. Lo que NO hay que hacer

- **No construir el proxy genérico que pide `JAVA_BACKEND_CONTRACT.md`.** Es la decisión central de este documento (§0) — se saltearía toda la autorización de negocio ya construida y probada.
- **No renombrar el enum `TipoNotificacion` (ni ningún enum de dominio) al inglés.** La BD está congelada; se traduce en el cliente.
- **No cambiar el flujo de firma de `phase-contracts` en el backend** — es determinístico y está probado; el frontend se adapta.
- **No retirar `X-Actor-Id` antes de que el frontend mande JWT real y B-2 esté cerrado.**
- **No tocar la BD** — sigue vigente D-40: ni tablas nuevas, ni ALTER, ni seeds desde código.
- **No migrar los bypasses a Supabase en bloque** — uno por uno, verificando cada uno contra la app real antes de seguir con el próximo.
- **No inventar ninguna de las decisiones de negocio de §6.**
