# Módulo `support` — estado, reglas extraídas y decisiones

**Documentos hermanos:** `CLAUDE.MD` (arquitectura y convenciones) · [`MODULOS_A_AVANZAR.md`](MODULOS_A_AVANZAR.md) · [`PLAN_DE_MODULOS.md`](PLAN_DE_MODULOS.md) §"9. `support`" (semilla de este módulo) · [`MODULO_USERS.md`](MODULO_USERS.md) (canon de estructura, `users.api`)

**Alcance:** tickets al mentor (Sistema de Tickets de Mentoría + Biblioteca de Sabiduría) y tickets de soporte técnico (Contactar Soporte). Dos agregados independientes, `ticketmentor/` y `ticketsoporte/`, sin relación entre sí salvo que ambos son "alguien le escribe a alguien y espera una respuesta".

---

## 0. Paso 0 — Reglas extraídas del repo viejo (ANTES de codificar)

Repo viejo clonado en `RenaserBack` (scratchpad de esta sesión). Cada regla cita archivo:línea.

### 0.1 Tickets al mentor (`MentorTicket`)

Fuente: `docs/FEATURE_TICKETS.md`, `src/features/tickets/{schema,repository,service}.ts`, `src/app/api/v1/tickets/**`.

| Regla | Fuente |
|---|---|
| Los 3 campos (`blockDescription`, `attemptedSolutions`, `smartGoalImpact`) son obligatorios, sin ellos no hay nada que enviar | `FEATURE_TICKETS.md:22-24` |
| Solo el mentor de la **propia célula** del aprendiz responde o guarda en biblioteca — nunca cualquier mentor (`requireMentorScope`) | `FEATURE_TICKETS.md:25-27`, `service.ts:178,213` |
| Biblioteca de Sabiduría es **por célula**, nunca global | `FEATURE_TICKETS.md:28-29` |
| Guardar en biblioteca exige `status === 'ANSWERED'` | `FEATURE_TICKETS.md:30`, `service.ts:216` |
| Estado: `OPEN` hasta que el mentor responde, después `ANSWERED`. Sin más estados — **nunca se reabre ni se cierra aparte** | `FEATURE_TICKETS.md:62` |
| Sin `updatedAt`: una transición (responder) + un flag opcional (guardar), cada uno con su propio timestamp | `FEATURE_TICKETS.md:68-72` |
| Sin flujo de borrado — registro permanente | `FEATURE_TICKETS.md:73-76` |
| `POST /api/v1/tickets`: solo `TRAINEE` (`requireRole(['TRAINEE'])`) | `tickets/route.ts:41` |
| `GET /api/v1/tickets`: `TRAINEE` (propios) o `MENTOR` (los de su célula) — **ningún otro rol**, ni ADMIN | `tickets/route.ts:21`, `service.ts:139-161` |
| `GET /api/v1/admin/tickets`: `MENTOR_LEAD`/`ADMIN`/`ALCHEMIST`, vista de plataforma de solo lectura — ruta separada **a propósito** para no ensuciar la semántica de `GET /tickets` | `admin/tickets/route.ts:4-9,23` |
| `POST /tickets/{id}/answer`: solo `MENTOR` | `tickets/[id]/answer/route.ts:22` |
| `POST /tickets/{id}/save-to-library`: solo `MENTOR` | `tickets/[id]/save-to-library/route.ts:22` |
| `GET /tickets/library?q=`: `TRAINEE` o `MENTOR` únicamente | `tickets/library/route.ts:20` |
| Un aprendiz sin célula no puede abrir ticket (huérfano invisible) | `service.ts:114-116` |
| Al guardar en biblioteca se indexa en RAG (`ticket_answer:${cellId}`), fire-and-forget, fallo no fatal | `FEATURE_TICKETS.md:79-95`, `service.ts:220-230` |
| Respuesta de creación/responder/guardar es el **ticket completo** (200/201 con el `MentorTicketDto` entero, no solo el id) | `tickets/route.ts:53`, `[id]/answer/route.ts:40`, `[id]/save-to-library/route.ts:29` |
| Validación: los 3 campos de apertura `min(1).max(2000)`; `mentorAnswer` `min(1).max(4000)`; `q` `min(1).max(2000)` | `schema.ts:28-49` |

### 0.2 Tickets de soporte técnico (`SupportTicket`)

Fuente: `docs/FEATURE_SUPPORT.md`, `src/features/support/{schema,repository,service}.ts`, `src/app/api/v1/support-tickets/**`, `src/app/api/v1/admin/support-tickets/**`.

| Regla | Fuente |
|---|---|
| Distinto de `MentorTicket` a propósito: esto es soporte al cliente (cuenta/facturación/bug), lo resuelve `ADMIN`/`ALCHEMIST`, **un mentor no tiene ningún alcance acá** | `FEATURE_SUPPORT.md:20-25` |
| `category` es **opcional** (fix 2026-08-03): obligar a autodiagnosticar antes de poder mandar el mensaje era una barrera real; ausente → `OTHER` **en el servicio**, no en el schema ni en el dominio | `FEATURE_SUPPORT.md:27-33`, `schema.ts:9-19`, `service.ts:27` |
| `POST /api/v1/support-tickets`: **cualquier autenticado**, `auth: "required"` pero SIN `requireRole` — "a suspended account can still reach support" | `FEATURE_SUPPORT.md:58,89-93`, `support-tickets/route.ts:18-32` |
| **Nunca gatear este endpoint por `TraineeProfile`/`SUSPENDED`**: "a hard 404/403 there would be the one case this feature must never produce" | `FEATURE_SUPPORT.md:92-93`, `service.ts:15-19` |
| `GET /api/v1/support-tickets`: historial propio, cualquier autenticado, sin gate de rol | `service.ts:39-48`, `support-tickets/route.ts:34-42` |
| `GET /api/v1/admin/support-tickets` y `POST .../{id}/resolve`: solo `ADMIN`/`ALCHEMIST` | `admin/support-tickets/route.ts:20`, `[id]/resolve/route.ts:23` |
| Resolver un ticket ya `RESOLVED` es **idempotente** (no-op, no error) — evita 4xx confuso si dos pestañas de admin resuelven a la vez | `service.ts:79-83` |
| Sin paginación (igual que `MentorTicket` en su momento) | `FEATURE_SUPPORT.md` (repository.ts:36) |
| `subject` máx 200, `message` mín 10 máx 4000, `clientLog` máx 4000 (opcional, ring buffer de requests recientes, nunca el body completo) | `schema.ts:17-32` |
| Adjunto (repo viejo): `attachmentUrl` — URL pública ya subida por el cliente **directo a Supabase Storage**, bucket `Evidence`, subcarpeta `{userId}/support/` | `RenaserPlayStoreCopy/src/services/support.ts:32,124-170` |

### 0.3 Cambios forzados por la BD nueva (no inventados, consecuencia del esquema)

| Cambio | Por qué |
|---|---|
| `tickets_mentor` no tiene columna de célula (a diferencia de `MentorTicket` + `TraineeProfile.cellId` del repo viejo) | `V1__baseline_renaser.sql:1425-1443` — el diseño nuevo solo tiene `participante_id` |
| `tickets_mentor.participante_id` referencia `participantes_programa.usuario_id`, **no** un `TraineeProfile.id` propio como antes — es literalmente el `userId` del aprendiz | `V1__baseline_renaser.sql:1427`, ver `participantes_programa` en `V1__baseline_renaser.sql:255-261` |
| La búsqueda de biblioteca usa **full-text nativo de Postgres** (`tickets_mentor_biblioteca_fts`, índice GIN parcial), no RAG/embeddings como en el repo viejo (`ragService.retrieveContext`) | `V1__baseline_renaser.sql:1440-1443` vs `FEATURE_TICKETS.md:79-95` |
| Adjuntos de soporte: `adjunto_bucket` + `adjunto_ruta` (nunca URL), firmada al leer — D-34, reemplaza el flujo directo a Supabase Storage | `V1__baseline_renaser.sql:1452-1453` (comentario `P-03`) |

---

## 1. Qué se construyó

### 1.1 Dominio (`domain/model/`)

- **`ticketmentor/`**: `TicketMentor` (máquina `EstadoTicketMentor` ABIERTO→RESPONDIDO, sin reapertura, `guardarEnBiblioteca()` exige RESPONDIDO — mismo invariante que el `CHECK respondido_coherente` del SQL), `TicketMentorId`, `EstadoTicketMentor`.
- **`ticketsoporte/`**: `TicketSoporte` (`EstadoTicketSoporte` ABIERTO→RESUELTO, `resolver()` idempotente), `TicketSoporteId`, `CategoriaSoporte`, `EstadoTicketSoporte`, `AdjuntoSoporte` (value object bucket+ruta, D-34: nunca una URL).
- **Decisión de nombres, distinta de `users`:** los enums/campos de dominio quedaron en **español** (`EstadoTicketMentor.ABIERTO/RESPONDIDO`, `CategoriaSoporte.TECNICO/CUENTA/...`), a diferencia de `users` (`UserRole.TRAINEE`, inglés). Motivo: el encargo nombró explícitamente las clases en español (`AbrirTicketMentorUseCase`, `GuardarEnBibliotecaUseCase`...) y `PLAN_DE_MODULOS.md` ya usa español para los 13 módulos restantes — `users` es la excepción histórica (construida primero, siguiendo `CLAUDE.MD` §5 tal cual estaba redactado entonces), no el patrón a copiar para módulos nuevos. La traducción español↔inglés para el contrato HTTP se hace explícita en los DTOs de salida/entrada (§2).

### 1.2 Aplicación

- **`ports/in/ticketmentor/`**: `AbrirTicketMentorUseCase`, `ResponderTicketMentorUseCase`, `GuardarEnBibliotecaUseCase`, `BuscarBibliotecaUseCase`, `ListarTicketsMentorUseCase` (dos vistas: `propios`/`todos`, fieles a `GET /tickets` vs `GET /admin/tickets`).
- **`ports/in/ticketsoporte/`**: `AbrirTicketSoporteUseCase`, `ListarTicketsSoporteUseCase` (`misTickets`/`todos`), `ResolverTicketSoporteUseCase`, `SolicitarUrlAdjuntoSoporteUseCase` (nuevo, D-34), `TicketSoporteVista` (proyección ticket + URL de lectura ya firmada — ver §2.3).
- **`ports/out/`**: `Load`/`Save` por agregado + `BuscarBibliotecaPort` (full-text).
- **`services/`**: `TicketMentorService`, `TicketSoporteService` — una clase por agregado (D-27), ambas dependen de `users.api.UserSummaryFinder` para resolver rol/estado del actor (ver §3, riesgo de arquitectura).

### 1.3 Infraestructura

- **REST** (`adapter/in/rest/`): ver tabla de endpoints §2.
- **Persistencia** (`adapter/out/persistence/`): JPA + mappers a mano (D-28), enums `*Jpa` en español espejo de los tipos Postgres (`EstadoTicketMentorJpa`, `CategoriaSoporteJpa`, `EstadoTicketSoporteJpa`) — aunque en este módulo casi todos los valores ya coinciden textualmente con el dominio (ambos en español), se mantiene el tipo `*Jpa` separado igual, por la misma razón que `NivelMentorJpa` en `users` (`N0..N3` coincide y aun así hay un tipo aparte): la entidad JPA nunca debe importar un tipo de dominio.
- **Eventos** (`api/`): `TicketMentorAbiertoEvent`, `TicketMentorRespondidoEvent` — publicados via `ApplicationEventPublisher` dentro de la transacción (Spring Modulith los persiste en su outbox). Sin consumidores todavía (`notifications` no existe, Ola 3).

---

## 2. Endpoints construidos

### 2.1 Tickets al mentor (`TicketMentorController` — `/api/v1/tickets`, `TicketMentorAdminController` — `/api/v1/admin/tickets`)

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `POST` | `/api/v1/tickets` | `TRAINEE` | 201, `MentorTicketDto` completo |
| `GET` | `/api/v1/tickets?cursor=` | `TRAINEE` (propios) / `MENTOR` (todos*) | 200, `{tickets, nextCursor}` |
| `POST` | `/api/v1/tickets/{id}/answer` | `MENTOR`* | 200, ticket actualizado |
| `POST` | `/api/v1/tickets/{id}/save-to-library` | `MENTOR`* | 200, ticket actualizado |
| `GET` | `/api/v1/tickets/library?q=` | `TRAINEE`/`MENTOR` | 200, `{results: string[]}` |
| `GET` | `/api/v1/admin/tickets?cursor=` | `MENTOR_LEAD`/`ADMIN`/`ALCHEMIST` | 200, `{tickets, nextCursor}` |

`*` = guard de célula pendiente, ver §3 (deuda B-5).

Las rutas `/api/v1/admin/tickets` **no estaban en la lista literal de rutas del encargo** ("rutas fieles al contrato viejo: `/api/v1/tickets`, `/api/v1/tickets/:id/answer`, `/api/v1/tickets/:id/save-to-library`, `/api/v1/tickets/library?q=`, `/api/v1/support-tickets`"), pero el encargo SÍ pidió explícitamente `ListarTicketsMentorUseCase` como caso de uso a construir. Sin esta ruta, la mitad de ese caso de uso (`todos`) queda inalcanzable — se agregó para no dejar código muerto y para no romper el contrato viejo (que sí tenía esta ruta). Ver §5 para la pregunta explícita al respecto si el criterio del encargo era literal.

### 2.2 Tickets de soporte (`TicketSoporteController` — `/api/v1/support-tickets`, `TicketSoporteAdminController` — `/api/v1/admin/support-tickets`)

| Método | Ruta | Rol | Devuelve |
|---|---|---|---|
| `POST` | `/api/v1/support-tickets` | cualquier autenticado, **sin gate** | 201, `SupportTicketDto` |
| `GET` | `/api/v1/support-tickets` | cualquier autenticado, **sin gate** | 200, lista propia |
| `POST` | `/api/v1/support-tickets/attachments/upload-url` | cualquier autenticado | 200, `{bucket, path, uploadUrl}` — **endpoint nuevo, D-34** |
| `GET` | `/api/v1/admin/support-tickets?status=` | `ADMIN`/`ALCHEMIST` | 200, lista completa |
| `POST` | `/api/v1/admin/support-tickets/{id}/resolve` | `ADMIN`/`ALCHEMIST` | 200, ticket actualizado |

Mismo caso que arriba: las rutas `/admin/support-tickets/**` no estaban en la lista literal, pero sirven `ResolverTicketSoporteUseCase` y `ListarTicketsSoporteUseCase.todos`, explícitamente pedidos.

### 2.3 Cambio deliberado de contrato: adjuntos (D-34)

El flujo viejo (`RenaserPlayStoreCopy/src/services/support.ts:124-170`) subía el archivo **directo a Supabase Storage** (bucket público `Evidence`) y mandaba `attachmentUrl` ya armada en el body de `POST /support-tickets`. Eso ya no es posible: la app no tiene credenciales de S3 (D-34, `docs/PLAN_DE_MODULOS.md`). Flujo nuevo:

1. App llama `POST /support-tickets/attachments/upload-url` → recibe `{bucket, path, uploadUrl}` (`uploadUrl` firmada por `AlmacenamientoPort.firmarSubida`, hoy `NoOpAlmacenamientoAdapter` — placeholder, D-34 sigue sin credenciales AWS reales).
2. App sube el archivo directo a `uploadUrl`.
3. App llama `POST /support-tickets` con `attachmentBucket`+`attachmentPath` (en vez del viejo `attachmentUrl`).
4. Al leer (`GET /support-tickets`, `GET /admin/support-tickets`), el ticket devuelve `attachmentUrl` **recién firmada en ese momento** (`AlmacenamientoPort.firmarLectura`, válida 30 min) — el campo de salida SÍ se llama igual que antes, solo cambia cómo se llena.

Esto es un cambio de contrato real en el INPUT (no en el output), coordinado a propósito (mismo patrón que el resto de D-34) — **no se puede liberar sin actualizar la app** en paralelo.

### 2.4 Riesgo de migración documentado: `traineeProfileId`

`TicketMentorResponse.traineeProfileId` conserva el **nombre** del campo viejo por compatibilidad de forma, pero su **valor** cambió de semántica: antes era un `TraineeProfile.id` propio (una entidad con su propia identidad); ahora es literalmente `participanteId` = el `userId` del aprendiz. Si la app usa ese campo para algo más que mostrarlo (p. ej. compararlo contra un id guardado localmente), puede romperse en silencio. No se puede evitar sin cambiar el nombre del campo (lo que sería OTRA ruptura) — queda documentado para quien coordine el release.

---

## 3. Riesgo de arquitectura (a resolver en `users`, fuera de mi alcance)

`TicketMentorService` y `TicketSoporteService` importan `com.renaser.os.users.domain.model.user.UserRole` y `UserStatus` para leer `UserSummary.role()`/`.status()`. Esos dos tipos **no** están declarados bajo el `@NamedInterface("api")` de `users` — solo `users.api.UserSummary`/`UserSummaryFinder` lo están, y `UserSummary` expone campos tipados con `UserRole`/`UserStatus`, que viven en `users.domain.model.user` (paquete interno).

Esto significa que `users.api.UserSummary` ya venía con una fuga de tipos internos desde antes de que existiera un segundo módulo — nadie lo había notado porque `support` es el primer consumidor real de `UserSummaryFinder`. Si `ArchitectureTest.modulesDoNotLeakInternals` (Spring Modulith `ApplicationModules.verify()`) falla por esto al correr `./mvnw clean test`, **la corrección es en `users`**, no en `support` (fuera de mi alcance de edición según el encargo). Dos arreglos posibles, a decidir por quien tenga permiso de tocar `users`:

1. Mover `UserRole`/`UserStatus` a un paquete cubierto por `@NamedInterface`, o
2. Cambiar `UserSummary` para exponer `role`/`status` como `String` (rompe la comodidad de comparar con el enum, pero cierra la fuga).

No se implementó ningún workaround dentro de `support` para evitar esto (como duplicar los enums) porque hubiera sido inventar una solución no pedida a un problema de otro módulo — se documenta y se deja que el supervisor decida.

---

## 4. Deudas conocidas (documentadas, no ignoradas)

| # | Deuda | Bloqueada por | Dónde |
|---|---|---|---|
| D-S1 | Guard de célula real (`requireMentorScope`: "el mentor solo responde tickets de su célula") — hoy solo se verifica `rol == MENTOR`, más grueso que el viejo scope | B-5 (matriz de permisos `MENTOR_LEAD`, `docs/MODULOS_A_AVANZAR.md` §2) + falta de un puerto hacia `participantes_programa.celula_id` desde `support` | `ResponderTicketMentorUseCase`, `GuardarEnBibliotecaUseCase`, javadoc + comentario en `TicketMentorService` |
| D-S2 | Búsqueda de biblioteca sin scope de célula (antes era "never global") — la tabla nueva no tiene columna de célula | Diseño de BD (V1 baseline), no es algo que `support` pueda resolver sin una migración fuera de mi alcance | `BuscarBibliotecaUseCase`, `docs/MODULO_SUPPORT.md` §5 |
| D-S3 | `propios()`/`todos()` de `TicketMentor` para rol `MENTOR` devuelven **todos** los tickets, no solo los de su célula (mismo origen que D-S1) | B-5 | `ListarTicketsMentorUseCase` |
| D-S4 | Indexación semántica al guardar en biblioteca (RAG) no implementada — el repo viejo la hacía (`ragService.indexContent`) | Módulo `rag` no existe todavía (Ola 5) | `GuardarEnBibliotecaUseCase` |
| D-S5 | `AlmacenamientoPort` sigue siendo `NoOpAlmacenamientoAdapter` (`shared`, no tocado acá) — las URLs firmadas son placeholders (`about:blank#...`) | D-34, credenciales AWS S3 pendientes | `shared/infrastructure/storage/NoOpAlmacenamientoAdapter.java` (existente, no editado) |
| D-S6 | Bucket S3 hardcodeado como placeholder `"renaser-files"` en `TicketSoporteService` — no hay todavía un bucket real definido a nivel de proyecto | D-34 | `TicketSoporteService.BUCKET_PLACEHOLDER` |
| D-S7 | Admin inbox de soporte no muestra `userFullName`/`email` del solicitante (el viejo `AdminSupportTicket` los traía embebidos) — `users.api.UserSummary` no expone email, y resolver el nombre por ticket implicaría N+1 llamadas o un caso de uso compuesto nuevo, fuera de alcance de esta tarea | Diseño de `users.api` | `TicketSoporteResponse` |
| D-S8 | Riesgo de arquitectura de §3 (fuga de `UserRole`/`UserStatus` desde `users.api`) | Corrección pendiente en `users` | `TicketMentorService`, `TicketSoporteService` |
| D-S9 | `X-Actor-Id` como resolución de actor temporal (D-29 de `users`, mismo patrón, mismo bloqueante B-2) | B-2 | Los 4 controllers |
| D-S10 | Test de reflexión "todo endpoint declara `@RequiresPermission` o `@PublicEndpoint`" (CLAUDE.MD §0.3) no se construyó — ese mecanismo no existe todavía ni en `users` (bloqueado por R-2/B-5) | R-2/B-5 | — |

---

## 5. Preguntas abiertas para el supervisor

1. **¿Las rutas `/api/v1/admin/tickets` y `/api/v1/admin/support-tickets/**` deben existir?** No estaban en la lista literal del encargo, pero sin ellas `ListarTicketsMentorUseCase.todos`, `ListarTicketsSoporteUseCase.todos` y `ResolverTicketSoporteUseCase` quedan sin forma de invocarse desde HTTP. Se construyeron por fidelidad al contrato viejo y para no dejar casos de uso huérfanos — a confirmar si es lo que se quería.
2. **¿`AbrirTicketMentorUseCase` debería aceptar otros roles además de `TRAINEE`?** La BD nueva reformuló "participante del programa" como algo abierto a cualquier rol (`participantes_programa`, ver `V1__baseline_renaser.sql:255-261`), pero el repo viejo restringía la apertura de tickets a `TRAINEE`. Se mantuvo la restricción vieja (`TRAINEE` únicamente) por fidelidad de contrato — a confirmar si el nuevo modelo de "programa abierto a todos" implica que otros roles también deberían poder abrir tickets de mentoría.
3. **¿La búsqueda de biblioteca (D-S2) debe acotarse por célula de alguna forma ya disponible?** Hoy es global. Si hay una forma de inferir la célula del actor sin tocar `users` (por ejemplo si `support` obtuviera su propio puerto hacia esa info en el futuro), avisar para acotar el índice full-text con un filtro adicional.
4. **`userFullName`/email en el inbox de soporte (D-S7):** ¿vale la pena que `users.api.UserSummary` incluya email, o se resuelve distinto (el panel admin llama a `users` aparte)?

---

## 6. Estado de las pruebas (escritas, no corridas — CLAUDE.MD prohíbe ejecutar Maven en este encargo)

| Tipo | Archivo | Cubre |
|---|---|---|
| Unit dominio | `TicketMentorTest` | máquina ABIERTO→RESPONDIDO, invariante de guardar en biblioteca, validación de los 3 campos |
| Unit dominio | `TicketSoporteTest` | máquina ABIERTO→RESUELTO idempotente, mensaje mín 10 chars, `AdjuntoSoporte` bucket+ruta obligatorios |
| Unit servicio (fakes a mano, sin Mockito — sin precedente de Mockito en este repo, ver `pom.xml`) | `TicketMentorServiceTest` | reglas de rol de TICKETS-01..05, seguridad (rol sin permiso → `NotAuthorizedException`, actor `SUSPENDED` → `NotAuthorizedException`), no-inyección de estado en el alta |
| Unit servicio | `TicketSoporteServiceTest` | reglas de rol S-01/S-02 + admin, **pruebas de seguridad inversas** (un actor `SUSPENDED` SÍ puede abrir/ver sus propios tickets de soporte — regla deliberada, no un agujero), URL de lectura firmada al leer nunca al guardar |
| Integración Testcontainers | `TicketMentorPersistenceAdapterTest` | round-trip, traducción de estados en las dos direcciones, paginación por participante/global, **búsqueda full-text en español contra Postgres real** (coincide por relevancia, respeta el filtro `guardado_en_biblioteca`, no trae respondidos-no-guardados), traducción de violación de FK a `IllegalStateException` legible |
| Integración Testcontainers | `TicketSoportePersistenceAdapterTest` | round-trip con y sin adjunto, traducción de estados y categorías, filtro por estado, aislamiento por usuario |

**Lo que quedó explícitamente sin verificar** (CLAUDE.MD §0.2): no se ejecutó `./mvnw clean test` (prohibido en este encargo) — el supervisor debe correrlo. Puntos de mayor riesgo si algo falla:
- El riesgo de `ArchitectureTest` de §3.
- Si `EstadoTicketMentorJpa`/`CategoriaSoporteJpa`/`EstadoTicketSoporteJpa` necesitan `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` con algún ajuste adicional para los tipos Postgres nativos (se siguió el patrón exacto de `users`, pero no se verificó en vivo).
- La query nativa de búsqueda full-text (alias `as descripcionBloqueo`/`as respuestaMentor` para la proyección de interfaz `BibliotecaFtsRow`) — patrón estándar de Spring Data pero no antes usado en este repo.

---

## 7. Archivos fuera de mi alcance que `support` necesitaría eventualmente

No se tocaron (prohibido por el encargo), pero quedan anotados para quien continúe:

- `users`: agregado `ParticipantePrograma` (para poder validar antes de escribir que el actor está inscripto, en vez de confiar en que la FK falle) y matriz de permisos (B-5, para el guard de célula real).
- `pom.xml`: nada nuevo requerido — todas las dependencias usadas (`spring-data-jpa`, `spring-boot-starter-validation`, Testcontainers, `spring-modulith-*`) ya estaban.

---

## Auditoría de arquitectura (2026-08-28) — agente automático

Alcance: `src/main/java/com/renaser/os/support/` completo (32 archivos de producción + 13 de test), contra `CLAUDE.md` §5.1, §5.1.2, §5.3.4, §5.3.5, §5.4.1–§5.4.10. Solo lectura — no se corrió `./mvnw`, no se modificó ningún `.java`. A diferencia de otros módulos auditados el mismo día, `support` llegó en muy buen estado: la mayoría de la deuda que el propio `docs/MODULO_SUPPORT.md` (§3, §4) ya documentaba resultó, al verificar contra el código actual, **ya resuelta** — el documento quedó desactualizado en esos puntos, no el código. Se señala explícitamente dónde.

### 1. Autenticación del actor — sin violaciones, los 4 controllers migrados

Búsqueda dirigida por el patrón de `community/TestimonioController` (header `X-Actor-Id` falsificable en vez de sesión real):

```
grep -rn "X-Actor-Id" src/main/java/com/renaser/os/support/   → 1 sola coincidencia, un COMENTARIO (TicketSoporteService.java:127), no código
grep -rn "RequestHeader" src/main/java/com/renaser/os/support/ → vacío
```

Los 4 controllers REST del módulo resuelven el actor exclusivamente vía `@ActorAutenticado UserId actor` (sesión real primero, `ActorAutenticadoArgumentResolver`):

- `TicketMentorController.java:51,59,65,73,81` (`abrir`, `propios`, `responder`, `guardarEnBiblioteca`, `buscarEnBiblioteca`)
- `TicketMentorAdminController.java:24` (`todos`)
- `TicketSoporteController.java:38,47,52` (`abrir`, `misTickets`, `solicitarUrlAdjunto`)
- `TicketSoporteAdminController.java:35,42` (`todos`, `resolver`)

**Nota sobre la documentación:** `docs/MODULO_SUPPORT.md` §4 (D-S9) todavía lista "`X-Actor-Id` como resolución de actor temporal ... Los 4 controllers" como deuda abierta, bloqueada por B-2. Eso ya no es así — el código migró (probablemente en el mismo commit `b824c4b` que migró los otros 63 controllers). D-S9 debería marcarse resuelta; la única traza que queda del patrón viejo es el comentario javadoc de `TicketSoporteService.java:127`, que describe un comportamiento (404 en vez de 409 ante un actor inexistente) que sigue siendo cierto independientemente de cómo se resuelva el actor, así que no hace falta tocar ese comentario.

### 2. `domain/` — sin violaciones de pureza

```
grep -rl "org.springframework.\|jakarta.persistence." src/main/java/com/renaser/os/support/domain/   → vacío
grep -rl "support.application\|support.infrastructure"  src/main/java/com/renaser/os/support/domain/  → vacío
```

`TicketMentor.java` y `TicketSoporte.java` (los dos agregados) son `final`, con constructor privado (`private final` fields, factory methods `abrir()`/`rehydrate()`), sin setters públicos — las transiciones de estado (`responder()`, `guardarEnBiblioteca()`, `resolver()`) son los únicos puntos de mutación y cada uno hace cumplir su propio invariante (§5.3.2, §5.4.5):

- `TicketMentor.guardarEnBiblioteca()` (línea 60-65) exige `estado.estaRespondido()` — el mismo invariante que el `CHECK respondido_coherente` de la migración SQL, documentado como decisión deliberada de duplicar la regla en dominio y en base.
- `TicketSoporte.resolver()` (línea 52-60) es idempotente por diseño (`if (estado.estaResuelto()) return;`), consistente con la regla de negocio documentada en §0.2 ("resolver un ticket ya RESOLVED es idempotente").

Las excepciones de dominio son `IllegalArgumentException`/`IllegalStateException` planas, sin conocimiento de HTTP — coherente con §5.4.4. Ningún archivo de `domain/` loguea (`grep` de `log\.\|Logger\|slf4j` vacío) — coherente con §5.4.9.

### 3. Regla de subcarpetas de `domain/` (§5.1.2) — correcta

`domain/model/` tiene dos subcarpetas, `ticketmentor/` y `ticketsoporte/`. Aplicando la regla real (subcarpeta = agregado independiente, no capa, no "para ordenar"): son dos agregados genuinamente distintos — `TicketMentor` (mentoría, máquina ABIERTO→RESPONDIDO, con `TicketMentorId` propio) y `TicketSoporte` (soporte técnico, máquina ABIERTO→RESUELTO, con `TicketSoporteId` propio, con su propio value object `AdjuntoSoporte`) — sin relación entre sí, cada uno con su propio ciclo de vida y su propio repositorio. El propio `docs/MODULO_SUPPORT.md` §"Alcance" ya lo dice explícitamente: "Dos agregados independientes... sin relación entre sí salvo que ambos son 'alguien le escribe a alguien y espera una respuesta'". Es exactamente el caso `dddsample-core` (una carpeta por raíz de agregado), no el caso `buckpal` (una sola historia, plano) — la subcarpeta está bien puesta, no es sobre-ingeniería.

Dentro de cada subcarpeta no hay sub-subcarpetas por capa (nada de `ticketmentor/entities/`, `ticketmentor/valueobjects/`) — cumple la prohibición dura de §5.1.2.

### 4. Controllers "tontos" — sin violaciones

Ningún controller de los 4 tiene `@Transactional` (`grep` de `@Transactional` en el módulo solo encuentra coincidencias en `application/services/`, nunca en `infrastructure/adapter/in/rest/`), ninguno inyecta un `Repository`/`Port out` (los 4 constructores solo reciben `UseCase` de `ports/in/`), y ninguno supera ~25 líneas por endpoint contando la desserialización de parámetros de ruta.

El único patrón "con lógica" que aparece en los controllers es la traducción de enum wire↔dominio (`parseCategoria`/`parseEstado` en `TicketSoporteController`/`TicketSoporteAdminController`, `parseCursor` repetido en `TicketMentorController`/`TicketMentorAdminController`) — es mapeo de formato de transporte (§5.4.1, Full Mapping en la frontera web), no una regla de negocio: no decide nada sobre el dominio, solo traduce un string HTTP a un enum o una excepción de formato. Es el mismo criterio que ya usan `TicketMentorResponse`/`TicketSoporteResponse` en la dirección de salida.

**Nota menor, no bloqueante:** `parseCursor` está duplicado literalmente (mismo cuerpo, mismo javadoc) en `TicketMentorController.java:87-96` y `TicketMentorAdminController.java:30-39`. No es una violación de ninguna regla de `CLAUDE.md` (no hay techo de "no duplicar 10 líneas"), pero es candidato natural a moverse a un método estático compartido si se quiere pulir — no se reporta como hallazgo de arquitectura, solo se anota.

### 5. Lombok — correcto: nada de `@Data`/`@Setter`/`@NoArgsConstructor` público en `domain/`

```
grep -rn "@Data\b\|@Setter\b\|@NoArgsConstructor" src/main/java/com/renaser/os/support/
  → TicketMentorJpaEntity.java:19-20  (@Data, @NoArgsConstructor)
  → TicketSoporteJpaEntity.java:19-20 (@Data, @NoArgsConstructor)
```

Las dos únicas coincidencias están en `infrastructure/adapter/out/persistence/*/`, exactamente donde §5.4.5 las permite. `domain/` usa `@Getter @Accessors(fluent = true) @AllArgsConstructor(access = PRIVATE) @EqualsAndHashCode(of = "id")` en los dos agregados (`TicketMentor.java:14-17`, `TicketSoporte.java:14-17`) — el patrón exacto verificado contra `buckpal` en §5.4.5 de `CLAUDE.md`.

### 6. Nombres prohibidos — sin coincidencias

`grep` de clases `*Util`/`*Helper`/`*Manager`/`*Processor` sueltos: vacío. Los puertos siguen la convención de nombrar por intención de negocio, no por tecnología (§5.4.8): `LoadTicketMentorPort`, `SaveTicketMentorPort`, `BuscarBibliotecaPort` — ninguno delata que detrás hay JPA/Postgres. El adaptador sí nombra la tecnología donde corresponde (`TicketMentorPersistenceAdapter`, no un genérico `TicketMentorRepository` renombrado).

### 7. Tamaño — dentro de los techos duros de §5.4.8

Los dos archivos más grandes del módulo son las clases de aplicación: `TicketMentorService.java` (171 líneas) y `TicketSoporteService.java` (153 líneas) — ambas muy por debajo del techo de 300. Ningún método individual se acerca a 40 líneas; el método más largo (`TicketMentorService.responder`, 9 líneas efectivas) es representativo del resto. Los comandos con más de 3 parámetros (`AbrirTicketMentorCommand`, `AbrirTicketSoporteCommand`) ya están agrupados en `record` self-validating, tal como pide §5.4.3 — no hay ningún método público con más de 4 parámetros sueltos. Anidamiento: ningún método pasa de 2 niveles (`if` + `switch` como máximo), todos con guard clauses (`requireActor`, `requireTicket`, `requireRol`, `requireMentorAsignado`, `requireAdmin`, `requireActorExiste`, `requireActorActivo`) en vez de anidar.

### 8. Validación en 3 niveles — implementada correctamente

- **Sintáctica** (`adapter/in/rest/*/Request`): `@NotBlank`, `@Size` en los 4 `record` de entrada (`AbrirTicketMentorRequest`, `ResponderTicketMentorRequest`, `AbrirTicketSoporteRequest`, `SolicitarUrlAdjuntoRequest`, `ResolverTicketSoporteRequest`).
- **Contrato del caso de uso** (self-validating): `AbrirTicketMentorCommand` (`AbrirTicketMentorUseCase.java:20-23`) y `AbrirTicketSoporteCommand` (`AbrirTicketSoporteUseCase.java:24-27`) llaman `SelfValidating.validateConstructorArgs(...)` en el constructor compacto — imposible construirlos mal vengan de HTTP, de un test o (a futuro) de un listener de evento, tal como exige §5.4.3.
- **Semántica en `domain/`**: `TicketMentor.abrir`/`TicketSoporte.abrir` validan invariantes de negocio en código plano (`requireNotBlank`, `requireMensaje` con el mínimo de 10 caracteres) — nunca con anotaciones.

Ningún `record` de entrada tiene un campo `role`, `estado` o similar que el actor no deba poder setear — coherente con el blindaje de mass-assignment de §5.3.3/§5.4.1.

### 9. Excepciones — el único traductor a HTTP es `GlobalExceptionHandler`

`domain/` y `application/` lanzan `IllegalArgumentException`, `IllegalStateException`, `NotAuthorizedException`, `NoSuchElementException` — ninguna con `@ResponseStatus`, ninguna construida con un código HTTP. `grep` de `ResponseStatus|HttpStatus|HttpServletRequest|ResponseEntity` en `domain/` y `application/` da vacío. `shared/web/GlobalExceptionHandler.java` ya tiene `@ExceptionHandler` para las cuatro (líneas 35, 90, 95, 100) — es el único punto del sistema que decide el status code, tal como exige §5.4.4.

### 10. Mapeo — asimetría correcta (MapStruct hacia adentro, a mano hacia afuera)

No hay ningún `@Mapper` de MapStruct en el módulo (`TicketMentorPersistenceMapper`/`TicketSoportePersistenceMapper` son clases planas con métodos `toDomain`/`toEntity` escritos a mano, `@Component` package-private). Esto **se aparta** del criterio por defecto de §5.4.5 ("MapStruct para `JpaEntity ↔ dominio`, su caso de uso legítimo") — el propio `TicketSoportePersistenceMapper.java:11` lo declara a propósito ("Traduccion a mano, no MapStruct — mismo criterio que TicketMentorPersistenceMapper"). No es una violación: §5.4.5 dice dónde MapStruct **puede** usarse, no que sea obligatorio, y con solo dos entidades chicas (10-12 campos cada una) el costo de un mapper a mano es bajo y evita el riesgo abierto que el propio `CLAUDE.md` señala sobre MapStruct 1.6.3/1.7.0-beta contra JDK 25. Se documenta como desviación consciente, no como hallazgo.

Hacia la frontera web, el mapeo es a mano en ambas direcciones (`TicketMentorResponse.from`, `TicketSoporteResponse.from`, con traducción explícita español↔inglés de enums) — correcto según §5.4.1/§5.4.5.

### 11. Guard de célula del mentor (E-38) — confirmado corregido, y una brecha real que sigue abierta

Contexto pedido explícitamente: verificar que el patrón de E-38 (`docs/BITACORA_ERRORES.md`) siga bien implementado.

- **`responder()` y `guardar()` SÍ están protegidos.** `TicketMentorService.requireMentorAsignado` (líneas 149-156) resuelve el mentor real asignado al aprendiz vía `users.api.ParticipacionProgramaFinder.deParticipante(...).map(ParticipacionPrograma::mentorId)` y compara contra el actor — ya no basta con `rol == MENTOR`. Este puerto (`users.api.ParticipacionProgramaFinder`) es nuevo desde que se escribió `docs/MODULO_SUPPORT.md` (§3 lo daba como bloqueante pendiente) y ya está resuelto: `users.api` expone `UserRole`/`UserStatus`/`ParticipacionProgramaFinder` como tipos públicos de primera clase, cerrando también la fuga de tipos internos que el documento señalaba en §3/D-S8. **`docs/MODULO_SUPPORT.md` §3 y D-S8 deberían marcarse resueltos** — quedaron desactualizados.
- **Pero `propios()` (el listado, `GET /api/v1/tickets`) NO tiene el mismo scope.** `TicketMentorService.java:112-114`: si el actor es `MENTOR`, la rama llama `loadTicketMentorPort.todos(cursor, ...)` — es decir, un mentor autenticado ve en su bandeja **todos los tickets de mentoría de la plataforma**, no solo los de sus propios aprendices. Esto ya está documentado como deuda abierta en `docs/MODULO_SUPPORT.md` §4 (D-S1, D-S3, bloqueadas por B-5 — falta la matriz de permisos de `MENTOR_LEAD` y un puerto de célula), así que **no es un hallazgo nuevo**, pero vale remarcarlo con precisión ahora que se confirmó la corrección de E-38: la brecha de exposición real hoy no está en quién puede *responder* un ticket ajeno (eso ya cierra), sino en quién puede *leer* el contenido de tickets ajenos (`descripcionBloqueo`, `solucionesIntentadas`, `impactoMetaSmart` de aprendices de otras células) a través del listado. Dado que ya existe `ParticipacionProgramaFinder.miembrosActivosDeCelula`/`miembrosDeCelula` en `users.api`, cerrar D-S3 ya no está bloqueado por "falta de un puerto hacia `participantes_programa`" (como decía D-S1 originalmente) — el puerto ya existe, solo falta usarlo en `propios()`. Vale que quien retome B-5 lo sepa: la pieza que faltaba ya está disponible.

### 12. Resumen

Módulo limpio contra las reglas de `CLAUDE.md` revisadas: sin violaciones de autenticación, sin fugas de pureza de dominio, subcarpetas de agregado correctas, controllers tontos, Lombok bien acotado, sin nombres prohibidos, dentro de todos los techos de tamaño, validación en 3 niveles, excepciones sin conocimiento de HTTP. El hallazgo de mayor relevancia no es una violación de arquitectura sino documentación desactualizada: `docs/MODULO_SUPPORT.md` §3/§4 lista como abiertas tres deudas (D-S8 fuga de tipos, D-S9 `X-Actor-Id`, y parcialmente D-S1) que el código ya resolvió — y señala con precisión nueva la única pieza de D-S3 que sigue pendiente y ya no tiene bloqueante técnico real.
