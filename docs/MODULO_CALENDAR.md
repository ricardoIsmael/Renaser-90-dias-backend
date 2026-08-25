# Módulo `calendar` — Eventos, recurrencia, RSVP y recordatorios

**Fecha:** 2026-08-24
**Ola:** ver `docs/PLAN_DE_MODULOS.md` — construido en paralelo con `academy` y `community`
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/MODULOS_A_AVANZAR.md` (qué y en qué orden) · `docs/MODULO_ROCKS.md`/`docs/MODULO_SUPPORT.md` (patrones replicados acá)

---

## 0. Estado

🔄 **Construido, sin ejecutar `./mvnw clean test` todavía** (regla del encargo: este agente no corre Maven — lo corre el supervisor).

Cambios de alcance durante la construcción, ambos por decisión del dueño del proyecto y con prioridad sobre el encargo original:

- **D-40 quedó ANULADA.** El encargo original pedía migrar `roles_destino_evento.rol_id smallint` a una columna enum nativa (`V5__calendar_roles_destino_evento.sql`). Se canceló: **la base de datos es inmutable en esta fase** (ver CL-1 en §5). No se creó ningún archivo de migración Flyway.
- **`niveles_membresia` sin seed.** Los niveles reales llegan en una migración de datos posterior, fuera de este módulo (ver CL-2 en §5).

---

## 1. Paso 0 — reglas extraídas del código viejo

Repo Next.js clonado en `C:\Users\Usuario\Documents\Backend90dias\RenaserBack\src\features\calendar\`. App móvil clonada en `…\scratchpad\RenaserPlayStoreCopy\src\features\calendar\`. Archivos leídos completos, citas archivo:línea.

### 1.1 `recurrence.ts` — expansión de ocurrencias (dominio puro, el de más valor)

`expandOccurrences(spec, range, overrides)` (`recurrence.ts:47-135`): genera las ocurrencias de un evento suelto o recurrente dentro de `[range.from, range.to]`, aplicando overrides (excepciones). Reglas portadas literal en `ExpansorOcurrencias`:

- Sin `frequency` → una sola ocurrencia, la de `startsAt` (`recurrence.ts:69-72`).
- `WEEKLY` con `byWeekday` no vacío → itera semana por semana, generando cada día de la lista, saltando los candidatos anteriores a `startsAt` (`recurrence.ts:89-108`).
- El resto (`DAILY`/`MONTHLY`/`WEEKLY` sin `byWeekday`) → candidato = `startsAt + interval*n` unidades, calculado SIEMPRE desde el original, nunca encadenado desde el anterior — el comentario `recurrence.ts:113-116` explica por qué: encadenar deja un `MONTHLY` clampeado (31 ene → 28 feb) pegado en 28 para siempre; recalcular desde el original re-clampea cada vez (marzo vuelve a 31). Portado en `ExpansorOcurrencias.expandirSimple`, verificado en `ExpansorOcurrenciasTest.mensualClampeaFinDeMes`.
- `MAX_ITERATIONS = 2000` (`recurrence.ts:53`): tope de seguridad contra rangos sin `until`/`count`. Portado igual.
- **Asimetría deliberada** (`recurrence.ts:41-46`): el corte de la serie (`hasta`/`repeticiones`/rango) se evalúa contra el slot ORIGINAL; el filtro final de "está en el rango pedido" se evalúa contra el instante EFECTIVO (post-excepción). Portado en `ExpansorOcurrencias.dentroDeLaSerie` (usa el slot) vs. el `Consumer` de `recolectar` (usa el efectivo).

**Por qué el puerto Java es más simple que el original:** el JS reimplementaba aritmética de calendario consciente de zona horaria a mano sobre `Intl` (`recurrence.ts:143-220`, `addLocalCalendarUnit`/`utcToLocalParts`/`timeZoneOffsetMinutes`) porque JavaScript no tiene un tipo "fecha-hora local con zona" nativo. `java.time.ZonedDateTime` sí lo tiene: `plusDays`/`plusMonths`/`minusDays` preservan la hora de pared local, resuelven el desplazamiento (incluido DST) en cada paso, y `plusMonths` clampa el día de mes automáticamente — sin código adicional. Ningún comportamiento de negocio cambió, solo la implementación.

### 1.2 `reminders.ts` — las 3 clases de regla y la semántica crítica de `recordatoriosPersonalizados`

- Tres clases (`reminders.ts:38-43`, tipo `ReminderRule`): `minutesBefore`/`daysBefore`/`timeOfDay`. Portadas en `TipoReglaRecordatorio` + `ReglaRecordatorio`.
- **`daysBefore` NO es "mismo día calendario N días antes con reajuste de zona"** — pese a que el comentario del repo viejo (`reminders.ts:118-120`) dice "a la misma hora de pared", la implementación real (`reminders.ts:128-131`) resta milisegundos fijos (`occurrenceStart.getTime() - rule.value * 86400000`), sin ningún reajuste de zona horaria. **Se portó el comportamiento REAL, no el comentario** (`CalculadoraRecordatorios.instantesPara`, caso `DIAS_ANTES`, con javadoc explicando la discrepancia) — CLAUDE.MD §5.3.4 manda evitar drift de comportamiento no pedido, y "arreglar" esto sería justamente eso.
- `MAX_REGLAS_POR_EVENTO = 5` (`reminders.ts:33`): regla de NEGOCIO confirmada ("Cinco es el número que pidió el negocio"). El CHECK de la tabla (`orden BETWEEN 1 AND 10`) es solo margen de crecimiento futuro, no la reemplaza — portado como `Evento.MAX_REGLAS_RECORDATORIO = 5`, validado en el agregado.
- **La semántica MÁS IMPORTANTE del módulo** (`reminders.ts:75-89`, `parseReminderRules`/`reglasEfectivas`): `reminderRules == null` en el evento → hereda las reglas del TIPO; `reminderRules == []` → el admin decidió que este evento no avisa. En la BD nueva, esa distinción NO puede vivir en "0 filas vs sin filas" (ambiguo) — el propio baseline ya la resolvió con el flag `eventos.recordatorios_personalizados` (comentario en `V1__baseline_renaser.sql:1174-1178`: `false` = hereda del tipo, ignora las filas; `true` = rigen las filas, 0 filas = no avisa). Portado EXACTO en `Evento.reglasRecordatorioEfectivas()`.
- `diasDeVentana(rules, minimo)` (`reminders.ts:198-210`): sin esto, una regla "7 días antes" nunca se generaría (el evento no entra en la ventana de 3 días hasta que ya es tarde, sin ningún error). Portado en `CalculadoraRecordatorios.diasDeVentana`.

### 1.3 `eventTypes.ts` — reglas por tipo de evento

`EVENT_TYPE_RULES` (`eventTypes.ts:71-133`): tabla única por `CalendarEventType`. Se portaron los **recordatorios por defecto** y **`requiresEligibility`** (`ReglasPorTipoEvento`). **NO se portó** la plantilla de recurrencia/hora/duración/ubicación por defecto de cada tipo: en el repo viejo esos campos son solo PRELLENADO del formulario del panel admin — `service.ts`/`schema.ts` nunca los valida ni los fuerza contra el evento ya guardado (el admin puede elegir cualquier fecha/duración/ubicación sin importar el tipo, verificado: no hay ningún `refine` en `schema.ts` que los ate al `eventType`). No es una regla de negocio de backend, es un detalle de UI del cliente que crea el evento — si el futuro panel admin la necesita, es prellenado de formulario, no lógica de este módulo.

### 1.4 `audience.ts` — resolución de audiencia y nivel

- `canViewEvent(viewer, event, hasCourseAccess)` (`audience.ts:57-72`): ADMIN/ALCHEMIST pasan siempre; después, un `switch` exhaustivo por `audienceType` (sin `default`, a propósito — si se agrega un valor nuevo, deja de compilar en vez de fallar en silencio). Portado literal en `ResolverAudiencia.puedeVer`.
- `programProgressPercent(programDay)` = `min(100, round(programDay/90*100))` (`audience.ts:34-36`) y `resolveLevelRank(pct, levels)` = el rango más alto cuyo `minProgressPercent <= pct` (`audience.ts:39-46`). Portados en `ProgresoNivel`.

### 1.5 `mentoriaEligibility.ts` — elegibilidad especial de `MENTORIA_ALQUIMISTA` (NO portado el cálculo real)

`esElegible(userId, role)` (`mentoriaEligibility.ts:150-172`): ADMIN/ALCHEMIST/MENTOR siempre elegibles ("rol_privilegiado"); un TRAINEE necesita ≥80% (`UMBRAL_VERDE`) de cumplimiento **semanal** de hábitos+rocas de los últimos 7 días CERRADOS (`obtenerPorcentajeSemanal`, `mentoriaEligibility.ts:100-121`, combina `computeDailyCompletionHistory` de `habits`/`rocks`). **Este cálculo depende de datos de `habits` y `rocks` que NO están en el contrato público que este encargo me dio** (ni `habits.api` ni `rocks.api` exponen algo así hoy). Se declaró el puerto `ConsultarElegibilidadEventoPort` con un adaptador NoOp que devuelve `false` para todo TRAINEE — nunca se inventó el cálculo. Ver §6, es la pregunta abierta #1 para el supervisor. El bypass de staff (ADMIN/ALCHEMIST/MENTOR) SÍ se portó, y vive en el servicio de aplicación (`AccesoEventoService.puedeAcceder`), no en el puerto — el puerto nunca se consulta para esos roles.

### 1.6 `reminderService.ts` — generación y despacho de recordatorios

- `resolveAudience` (`reminderService.ts:70-119`): una consulta EN LOTE por evento, nunca una por aprendiz. El padrón de partida son los aprendices activos (y, solo en `ROLES`, los roles que el evento nombra) — **no** "todo el que `canViewEvent` dejaría pasar" (eso incluiría a cada ADMIN en cada evento). Portado en `RecordatorioService.resolveAudience` + `ResolverAudienciaMasivaPort`/`ConsultarMiembrosCelulaPort`/`ResolverAudienciaCursoPort`.
- `resolveRecipients` (`reminderService.ts:135-159`): primero audiencia (barata), después elegibilidad (cara, por persona, secuencial a propósito — mismo criterio que el repo viejo, connection pool acotado). Portado en `RecordatorioService.resolveRecipients`.
- `generar()`/`anunciar()`/`despachar()` (`reminderService.ts:230-410`): la ventana de generación (`VENTANA_DIAS = 3`), la clave FIJA del anuncio (`sendAt = occurrenceStart = createdAt`, para que dos pasadas del cron no lo dupliquen), y la regla de `despachar()` de marcar `sentAt` DESPUÉS de enviar (si falla, se reintenta) están portadas 1:1 en `RecordatorioService.generar`/`anunciar`/`despachar`.
- **Cambio deliberado de mecanismo de entrega:** el repo viejo manda push Expo directo desde `despachar()` (`reminderService.ts:377-407`). Este módulo NO manda push — publica `RecordatorioEventoDebidoEvent` (Spring Modulith event) por cada aviso vencido, y `notifications` decide el canal (push/email) en un listener que construye OTRO agente, fuera de este módulo (encargo explícito: "No toques el módulo `notifications`"). `calendar` decide QUÉ y CUÁNDO, nunca CÓMO se entrega.
- `cancelarPorAsistencia`/`cancelarPorOcurrencia`/`borrarPendientes` (`reminderService.ts:415-462`): portados literal en `SaveRecordatorioPort`.

### 1.7 Lo que NO se portó (fuera de alcance, con motivo)

- **`digests.ts`** (aviso semanal/mensual genérico, "revisa el calendario") — no depende de ningún evento concreto, es un nudge de engagement general, no una regla de negocio de "un evento del calendario". Fuera de alcance de este encargo; se puede construir después sin tocar lo ya hecho.
- **Portada por multipart directo** (`repository.ts:245-262`, `uploadCover`/bucket público `calendar-events`) — ver CL-3 en §5: se usa `AlmacenamientoPort` (URL prefirmada en dos pasos) en vez del multipart directo, por instrucción explícita del encargo ("Storage").
- **Catálogo de niveles/cursos para el panel admin** (`listAudienceOptions`, `service.ts:186-193`) — la app móvil v1 no usa `audienceType=MIN_LEVEL`/`COURSE` (comentario propio de `CreateEventPayload` en `types.ts`, repo de la app: "those stay web-only for now"). No hay panel admin web en este encargo. No se construyó el endpoint.
- **`scope=manage`** (`calendar.ts` de la app, comentario: "el backend entonces resuelve visibilidad con el rol REAL del caller en vez del efectivo (posiblemente degradado a TRAINEE)") — no se tuvo acceso a `route.ts` del backend viejo para ver la degradación exacta que aplica sin ese parámetro. El controller acepta el query param pero no cambia de comportamiento todavía — ver §6, pregunta abierta #2.

---

## 2. Progreso del participante y resolución de audiencia

`calendar` NO importa nada de `users`, `academy` (salvo `academy.api`, ver más abajo) ni `community` en código — mismo motivo documentado en `docs/MODULO_PHASECONTRACTS.md`/`docs/MODULO_ROCKS.md` §2: los tipos internos de otro módulo (`UserRole`, `UserStatus`) rompen `ArchitectureTest.modulesDoNotLeakInternals` si se importan fuera de su `@NamedInterface`.

- **`ConsultarProgresoParticipanteCalendarPort`** (query nativa propia sobre `participantes_programa`+`usuarios`): trae `diaPrograma`, `timezone`, `rol` (enum local `RolUsuario`, mismo vocabulario en inglés que `users.api.UserRole` — D-21), `suspendido`, y `celulaId` — la propia (TRAINEE) o la LIDERADA (MENTOR, `celulas.mentor_id = usuario_id`), resuelta en una sola query con `CASE WHEN`.
- **`ResolverAudienciaMasivaPort`**: las 3 consultas en lote que el generador de recordatorios necesita (`traineesActivos`, `activosConRoles`, `traineesActivosConDiaPrograma`) — mismo criterio "una consulta por evento, no una por aprendiz" del repo viejo.
- **`ConsultarMiembrosCelulaPort`**: audiencia `CELULA`, query nativa PROPIA (JOIN `participantes_programa`+`celulas`, aprendices de la célula + el MENTOR que la lidera) — sin importar nada de `community` (otro agente construye ese módulo en paralelo, encargo explícito).
- **`ResolverAudienciaCursoPort`**: puerto OUT propio con un adaptador que delega en `academy.api.AccesoCursoFinder` (interfaz ya publicada por `academy` para este uso exacto). El servicio de `calendar` nunca depende de `academy.api` directo — solo el adaptador de infraestructura, para poder mockear el puerto en tests unitarios sin levantar `academy`.
- **`NIVEL_MINIMO`** (nivel mínimo de membresía): el rango se resuelve con `ProgresoNivel` sobre el catálogo `niveles_membresia`. Con la tabla vacía (sin seed, CL-2), la audiencia `NIVEL_MINIMO` no tiene destinatarios — comportamiento correcto, no un bug.

---

## 3. Qué se construyó

```
calendar/
├── package-info.java                          @ApplicationModule("Calendar")
├── api/
│   ├── package-info.java                       @NamedInterface("api")
│   └── RecordatorioEventoDebidoEvent.java       publicado cuando un aviso de la cola vence
├── domain/model/
│   ├── evento/                                 EventoId, Evento (agregado), TipoEvento, EstadoEvento,
│   │                                            TipoUbicacion, TipoAudiencia, RolUsuario, Recurrencia,
│   │                                            FrecuenciaRecurrencia, ReglaRecordatorio, TipoReglaRecordatorio,
│   │                                            Excepcion, Ocurrencia, ExpansorOcurrencias (puro),
│   │                                            ReglasPorTipoEvento (puro), ResolverAudiencia (puro)
│   ├── confirmacion/                            Confirmacion (RSVP), EstadoConfirmacion
│   ├── recordatorio/                            RecordatorioEvento (cola), InstanteRecordatorio,
│   │                                            CalculadoraRecordatorios (puro)
│   └── nivelmembresia/                          NivelMembresia, ProgresoNivel (puro)
├── application/
│   ├── ports/in/evento/                         Listar/Obtener/Crear/Actualizar/Eliminar/CancelarOcurrencia
│   │                                            /SolicitarUrlPortada/ConfirmarPortada UseCase + EventoVista
│   ├── ports/in/confirmacion/                   ConfirmarAsistenciaUseCase
│   ├── ports/in/recordatorio/                   Generar/DespacharRecordatoriosUseCase
│   ├── ports/out/{evento,confirmacion,recordatorio,nivelmembresia,participante,celula,curso,elegibilidad}/
│   └── services/                                EventoService, ConfirmacionService, RecordatorioService,
│                                                AccesoEventoService (logica de acceso compartida, package-private)
└── infrastructure/adapter/
    ├── in/rest/evento/                          EventoController + DTOs + EventoWireMapper (traduccion D-36)
    ├── in/scheduler/                            GenerarRecordatoriosScheduler (cada 5 min),
    │                                            DespacharRecordatoriosScheduler (cada 1 min)
    └── out/persistence/
        ├── evento/                              EventoJpaEntity + hijos (Recurrencia/DiaSemana/RolDestino/
        │                                        ReglaRecordatorio) + Excepcion, mappers, adapters,
        │                                        RolesCatalogoCache (rol_id <-> RolUsuario, ver CL-1)
        ├── confirmacion/, recordatorio/, nivelmembresia/
        ├── participante/                        query nativa + RolUsuarioSqlMapper
        ├── celula/, curso/ (puente a academy.api), elegibilidad/ (NoOp, ver §6)
```

### 3.1 Casos de uso y endpoints

Rutas iguales a `/api/v1/calendar/events*` del repo viejo (misma app móvil ya publicada la consume), salvo la portada (CL-3):

| Método | Ruta | Caso de uso | Notas |
|---|---|---|---|
| GET | `/api/v1/calendar/events?from=&to=&scope=` | `ListarEventosParaVisorUseCase` | rango máx. 90 días (no validado todavía, ver §6) |
| GET | `/api/v1/calendar/events/{id}` | `ObtenerEventoUseCase` | |
| POST | `/api/v1/calendar/events` | `CrearEventoUseCase` | ADMIN/ALCHEMIST (cualquier audiencia) o MENTOR (forzado a CELULA propia) |
| PUT | `/api/v1/calendar/events/{id}` | `ActualizarEventoUseCase` | reenvío completo del formulario, igual que el repo viejo |
| DELETE | `/api/v1/calendar/events/{id}` | `EliminarEventoUseCase` | borra portada (best-effort) + fila (cascada BD) |
| PUT | `/api/v1/calendar/events/{id}/rsvp` | `ConfirmarAsistenciaUseCase` | `{occurrenceStart, status}` |
| POST | `/api/v1/calendar/events/{id}/cancel-occurrence` | `CancelarOcurrenciaUseCase` | solo eventos recurrentes |
| POST | `/api/v1/calendar/events/{id}/portada/upload-url` | `SolicitarUrlPortadaUseCase` | CL-3 |
| POST | `/api/v1/calendar/events/{id}/portada/confirm` | `ConfirmarPortadaUseCase` | CL-3 |

Autenticación: header `X-Actor-Id` (sin JWT todavía, per encargo). Autorización como guard clause en el servicio — `NotAuthorizedException` → 403 vía `GlobalExceptionHandler`.

### 3.2 Vocabulario wire (D-36)

Los enums de dominio/BD están en ESPAÑOL; los DTOs REST hablan el vocabulario INGLÉS que la app instalada ya consume. La traducción vive SOLO en `infrastructure/adapter/in/rest/evento/EventoWireMapper` (nunca en dominio ni persistencia):

| Dominio (español) | Wire (inglés, app instalada) |
|---|---|
| `TipoAudiencia`: TODOS/NIVEL_MINIMO/CURSO/ROLES/CELULA | ALL_MEMBERS/MIN_LEVEL/COURSE/ROLES/CELL |
| `TipoUbicacion`: LLAMADA_INTERNA/WEBINAR/ZOOM/MEET/DIRECCION/ENLACE | INTERNAL_CALL/WEBINAR/ZOOM/MEET/ADDRESS/LINK |
| `FrecuenciaRecurrencia`: DIARIA/SEMANAL/MENSUAL | DAILY/WEEKLY/MONTHLY |
| `EstadoConfirmacion`: ASISTE/NO_ASISTE/QUIZAS | GOING/NOT_GOING/MAYBE |
| `TipoReglaRecordatorio` (kind del JSON) | minutesBefore/daysBefore/timeOfDay |
| `TipoEvento` | **igual en los dos lados** — MENTORIA_ALQUIMISTA/ESPONTANEO/SEMANA_MANIFESTACION/SESION_ESPECIAL, sin traducción |
| `RolUsuario` (target_roles) | **igual en los dos lados** — ya está en inglés en el dominio (D-21) |
| `recurrenceByWeekday` (1=lunes..7=domingo, ISO) | igual — `java.time.DayOfWeek` usa la misma convención |

---

## 4. Integraciones y eventos publicados

- **`calendar.api.RecordatorioEventoDebidoEvent`** (implementa `DomainEvent`, molde exacto de `HabitoCompletadoEvent`/`RocaCompletadaEvent`): publicado por `RecordatorioService.despachar()` para cada aviso vencido. Campos: `recordatorioId`, `eventoId` (UUID plano), `destinatarioId`, `inicioOcurrencia`, `tituloEvento`, `esAnuncio` (distingue "hay un evento nuevo" de un recordatorio real de ocurrencia), `occurredAt`. `notifications` escribe su propio listener (otro agente, fuera de este módulo).
- **`academy.api.AccesoCursoFinder`**: usado por `ResolverAudienciaCursoAdapter` para la audiencia `CURSO` — `academy` ya publicó esta interfaz para este uso exacto.
- **`shared.application.ports.out.AlmacenamientoPort`**: portada de eventos (CL-3). Hoy el adapter real de storage tiene credenciales AWS pendientes (D-34, mismo bloqueo que otros módulos) — se usa el puerto igual, sin bloquear la construcción.
- **Outbox de Modulith**: NO se tocó `V2__spring_modulith_event_publication.sql` (ya existe, D-37) — publicar `RecordatorioEventoDebidoEvent` no requirió ninguna migración nueva.

---

## 5. Decisiones propias (`CL-`)

- **CL-1 — la base de datos es inmutable en esta fase.** Decisión del dueño del proyecto, con prioridad sobre el encargo original (que pedía migrar `roles_destino_evento.rol_id smallint` a un enum nativo, D-40 — ANULADA). `roles_destino_evento` se usa TAL CUAL: `evento_id uuid` + `rol_id smallint REFERENCES roles(id)`. El dominio sigue usando el enum `RolUsuario` (inglés, D-21) — la traducción `rol_id ↔ RolUsuario` vive en `RolesCatalogoCache` (`infrastructure/adapter/out/persistence/evento/`), que carga las 5 filas de `renaser.roles` UNA vez al arranque (`@PostConstruct`) en dos `Map` inmutables. Se eligió cache-al-arranque en vez de JOIN-en-cada-query por ser más simple y porque `roles` es un catálogo de sistema que no cambia en caliente (`es_sistema=true`, `ON DELETE RESTRICT`). Mismo criterio, independientemente, que `academy` aplicó para `roles_permitidos_curso` (su propia decisión AC-01) — confirma que es el patrón correcto para este tipo de tabla puente heredada.
- **CL-2 — `niveles_membresia` sin seed.** El baseline no trae seed para esta tabla. Los niveles reales del negocio (rango, nombre, % de progreso mínimo) llegan en una migración de datos posterior, fuera de este módulo — no se encontraron en el código viejo (`membership_levels` en `schema.prisma` tampoco trae seed, se administra desde un panel admin no incluido en este repo). Con la tabla vacía, la audiencia `NIVEL_MINIMO` no tiene destinatarios — comportamiento correcto y documentado, no un bug.
- **CL-3 — portada por URL prefirmada en dos pasos, no multipart directo.** El repo viejo sube la portada con multipart directo a un bucket público de Supabase (`repository.ts:uploadCover`). Este módulo usa `AlmacenamientoPort` (mismo patrón que `SolicitarUrlAdjuntoRocaUseCase` de `rocks`): `POST .../portada/upload-url` devuelve una URL PUT prefirmada; el cliente sube directo a storage; `POST .../portada/confirm` fija la ruta en el evento. Es una adaptación de infraestructura pedida explícitamente por el encargo ("Storage: portada_ruta va por AlmacenamientoPort"), no una divergencia de regla de negocio — mismo criterio D-34. **Efecto en el cliente:** la app móvil publicada usa el flujo multipart viejo contra el backend Next.js; el flujo de portada de `calendar` en Java necesita un cambio en el cliente para usarlo. El resto de los endpoints (listar/crear/editar/eliminar/RSVP) preserva el contrato exacto y no necesita cambios en el cliente.
- **CL-4 — el controller nunca resuelve la URL de lectura de la portada.** `ArchitectureTest.controllersDoNotTouchPersistence` prohíbe que cualquier clase de `adapter.in.rest` dependa de un puerto `out` (incluido `AlmacenamientoPort`). Se introdujo `EventoVista` (`ports.in.evento`, un `record(Evento, coverUrl)`) para que `EventoService` resuelva la URL prefirmada de lectura ANTES de devolver el resultado — el controller solo invoca casos de uso, nunca toca storage.
- **CL-5 — `tipoEvento` es inmutable después de crear el evento.** El repo viejo permite reenviar `eventType` en el `UPDATE` (mismo `EventInputObject` para create/update, `schema.ts`), pero no hay ningún test/comentario que documente una razón de negocio real para cambiar el tipo de un evento ya publicado (con RSVPs, recordatorios y reglas de elegibilidad atadas a ese tipo). Se modeló como inmutable en `Evento` (`tipoEvento` es `final`, no participa de `actualizar()`). Sin pérdida de comportamiento para la app móvil: su `UpdateEventPayload` siempre reenvía el `eventType` existente, nunca ofrece cambiarlo. Si en el futuro se confirma que hace falta, es un cambio pequeño y aislado en el agregado.
- **CL-6 — `resolveRecipients` consulta el progreso UNO POR UNO cuando el tipo exige elegibilidad**, igual que el repo viejo (`reminderService.ts:150-159`, comentario explícito sobre el pool acotado) — no es una regresión de rendimiento nueva, es el mismo trade-off ya aceptado, ahora con el añadido de que hoy siempre devuelve `false` para TRAINEE (CL de facto: ver pregunta abierta #1, §6) así que el bucle nunca crece sin límite en la práctica.
- **CL-7 — `MAX_REGLAS_POR_EVENTO` se mantuvo en 5** (regla de negocio confirmada del repo viejo), NO en 10 (el techo más permisivo del `CHECK` de la tabla, que es solo margen de crecimiento futuro según el propio comentario del baseline).

---

## 6. Qué NO se construyó y preguntas abiertas

1. **Elegibilidad de `MENTORIA_ALQUIMISTA` (`ConsultarElegibilidadEventoPort`).** El repo viejo calcula esto con el % de cumplimiento SEMANAL de hábitos+rocas (Ley VI), datos que hoy viven en `habits`/`rocks`, no en el contrato público que este encargo me dio. **Necesito de `habits`/`rocks`:** un método tipo `porcentajeCumplimientoSemanal(UserId, LocalDate hasta, int dias)` (o equivalente) expuesto en sus respectivos `.api`, o un módulo/servicio central de "coherencia" que agregue ambos (`points` ya calcula algo relacionado — ver `docs/MODULO_POINTS.md`). Mientras tanto, el adaptador NoOp devuelve `false` para todo TRAINEE — nadie sin rol privilegiado ve la Mentoría con el Alquimista hasta que se conecte el dato real. Reportar al supervisor para agregarlo en la integración final.
2. **`scope=manage` no cambia el comportamiento todavía.** No tuve acceso a `route.ts` del backend viejo (fuera del repo clonado que se me dio) para portar con fidelidad la degradación de rol que aplica sin ese parámetro. El query param se acepta y se ignora. Si hace falta, dejarlo para cuando se defina el rol de "vista de administración" vs. "vista de miembro" en el calendario.
3. **`EventRangeQuery` (rango máximo 90 días, `schema.ts`) no está validado.** El endpoint `GET /events` acepta cualquier `[from,to]` sin tope — el repo viejo lo limitaba a 90 días para no generar consultas gigantes. Fácil de agregar (una validación en el controller o el servicio) pero no se hizo por foco de tiempo; anotado para una pasada de dureza.
4. **Catálogo de niveles/cursos para un panel admin (`GET /audience-options`)** — no construido, la app móvil v1 no lo necesita (`CreateEventPayload` en la app: "mobile v1 no expone gating por nivel/curso"). Si se construye un panel admin web, hace falta este endpoint y probablemente un método de catálogo en `academy.api` (hoy `AccesoCursoFinder` solo resuelve acceso, no lista cursos).
5. **`MENTOR_LEAD` no tiene permisos definidos en `calendar`.** El repo viejo (service.ts) solo distingue ADMIN/ALCHEMIST (todo) y MENTOR (acotado a su célula) para crear/editar eventos — no hay ningún camino documentado para `LIDER_MENTORES`. Se lo dejó FUERA de `requireRolCreador` (recibe 403 igual que TRAINEE) en vez de inventarle un alcance. Si el negocio confirma qué puede hacer un líder de mentores en el calendario, es un cambio de una línea.
6. **`digests.ts` (avisos semanal/mensual genéricos)** — no es una regla de "un evento del calendario", es un nudge de engagement general. Fuera de alcance de este encargo (ver §1.7).

---

## 7. Pruebas

**Unitarias de dominio** (sin Spring, sin Postgres) — cubren la parte de mayor valor del módulo:

- `ExpansorOcurrenciasTest`: evento suelto, `DAILY` con intervalo, `WEEKLY` con varios días, clampeo de fin de mes (`MONTHLY`), `hasta` vs. `repeticiones`, excepciones canceladas y reprogramadas.
- `CalculadoraRecordatoriosTest`: las 3 clases de regla (incluida la asimetría documentada de `daysBefore`), descarte de instantes ya pasados, orden, `diasDeVentana`.
- `EventoTest`: las reglas de `refineEventInput`/`audiencia_coherente`/`fin_no_contradictorio` de schema.ts, `reglasRecordatorioEfectivas()` (la semántica null/[] portada como flag).
- `ResolverAudienciaTest`, `ProgresoNivelTest`: `canViewEvent`/`resolveLevelRank` puros.

**Unitarias de `application/services`** (Mockito, sin Spring): `EventoServiceTest` (autorización por rol, célula forzada para MENTOR, borrado de portada, cancelación de ocurrencia), `ConfirmacionServiceTest` (RSVP, cancelación de recordatorios al confirmar ASISTE, validación de ocurrencia real), `RecordatorioServiceTest` (despacho publica el evento de dominio correcto, evento cancelado cancela en vez de despachar).

**Pendiente (Testcontainers, otro agente):**

- `EventoPersistenceAdapterTest`: el ensamblado/desensamblado del agregado multi-tabla (eventos + recurrencias_evento + dias_semana_recurrencia + roles_destino_evento + reglas_recordatorio_evento), especialmente el orden de borrado/reinserción respetando la FK `dias_semana_recurrencia → recurrencias_evento`.
- `RecordatorioPersistenceAdapterTest`: **`FOR UPDATE SKIP LOCKED` contra Postgres real, con dos instancias simuladas del scheduler corriendo en paralelo** — verificar que nunca despachan la misma fila dos veces (la garantía central de la cola). También el `INSERT ... ON CONFLICT DO NOTHING` en batch (que el conteo de filas creadas sea correcto).
- `RolesCatalogoCacheTest`: que los tipos que llegan de Postgres (uuid, enums nativos) se lean como se asume (String vs. objeto tipado, según la ruta real de Hibernate/pgjdbc — el mismo punto que otros módulos ya marcaron como "verificado en integración, no supuesto"). `ConsultarProgresoParticipanteCalendarPersistenceAdapterTest` ya está hecho — ver §7.2.
- Tests de seguridad (403 por rol, 403 por SUSPENDIDO, test de reflexión de `@RequiresPermission`) — CLAUDE.MD §0.3, a cargo del agente de integración final.

### 7.1 Primeros dos fallos de `./mvnw clean test` (corridos por el supervisor) — diagnóstico

- **`EventoTest.actualizarReemplazaLosCamposMutables` rechazaba `WEBINAR` con `valorUbicacion`.** Se confirmó contra el código viejo, no se asumió: `schema.ts:168` (`refineEventInput`) define `urlLocationTypes = ['ZOOM', 'MEET', 'LINK']` — **`WEBINAR` NO está en esa lista**. Cae al `else` de `schema.ts:179-181`, que exige `locationValue` nulo para cualquier tipo que no sea ZOOM/MEET/LINK/ADDRESS. Es decir: `WEBINAR` se comporta como `LLAMADA_INTERNA` (sin valor), nunca como `ZOOM`/`MEET`/`ENLACE` (con URL). **El dominio (`Evento.requireUbicacionCoherente`) estaba bien; el test estaba mal** — usaba `TipoUbicacion.WEBINAR` con una URL. Corregido: el test ahora usa `TipoUbicacion.ENLACE` (mapea a `LINK`, que sí acepta valor) para probar el reemplazo de ubicación en `actualizar()`.
- **`RecordatorioServiceTest.despacharPublicaUnEventoPorRecordatorioVencido` publicaba un `eventoId` distinto al esperado.** No era un bug de `RecordatorioService` — el servicio arma el evento de dominio con `evento.id().value()`, que en producción SIEMPRE coincide con el id por el que se consultó (`loadEventoPort.byId(recordatorio.eventoId())` devuelve la fila con ESE id). El bug era del escenario del test: el helper `evento(TipoEvento tipo)` usaba `Evento.crear(...)`, que SIEMPRE asigna un `EventoId.newId()` al azar — el mock `loadEventoPort.byId(eventoId)` quedaba devolviendo un `Evento` con un id distinto al `eventoId` que el test declaraba y esperaba de vuelta. Corregido: el helper ahora usa `Evento.rehydrate(eventoId, ...)`, fijando el id exacto — igual que devolvería el adaptador de persistencia real. Sin cambios en `RecordatorioService`.

### 7.2 Tres defectos de la capa REST encontrados sondeando la app real contra Postgres — ninguno lo detectaban los tests con mocks

Otro agente levantó la app contra Postgres real y probó los endpoints en vivo (no `EventoServiceTest`, que mockea todos los puertos `out` — por diseño nunca iba a pisar ni la deserialización JSON real ni la query SQL real). Encontró tres defectos, los tres corregidos en esta pasada:

- **Defecto 1 — `EventoController` no tenía `@Valid` en NINGÚN `@RequestBody`.** Los 6 endpoints con cuerpo (`POST`/`PUT` de evento, `rsvp`, `cancel-occurrence`, `portada/upload-url`, `portada/confirm`) no validaban el DTO de entrada — todos los `@NotBlank` de `EventoRequest`/`RsvpRequest`/`CancelarOcurrenciaRequest`/etc. eran letra muerta. Consecuencia real medida: `PUT .../rsvp` y `POST .../cancel-occurrence` con `occurrenceStart` ausente devolvían **500** (`NullPointerException` en `Instant.parse(null)`) en vez de 400 — exactamente el nivel 1 de validación que CLAUDE.MD §5.4.3 exige y que nunca corría. Corregido: `@Valid` en los 6 `@RequestBody` de `EventoController`. Cubierto por `EventoControllerValidationTest` (`@WebMvcTest`, nuevo — primer test de capa web del módulo, ver nota abajo).
- **Defecto 2 — `EventoRequest.notifyOnCreate`/`remindByEmail` eran `boolean` primitivo, así que en la práctica eran obligatorios.** Si el cliente los omitía (el propio javadoc del record ya documentaba que `withEventDefaults()` del cliente puede omitir varios campos), Jackson respondía 400 con un error interno (`Cannot map \`null\` into type \`boolean\``) en vez de aplicar un default. Corregido: pasaron a `Boolean`, con default `false` en el constructor compacto — mismo patrón que `audienceType`/`timezone`/`targetRoles`/`recurrenceByWeekday` en el mismo record. El default `false` se confirmó contra el código viejo, no se asumió: `schema.ts` (`CreateEventInput`/`UpdateEventInput`) — `notifyOnCreate: z.boolean().default(false)`, `remindByEmail: z.boolean().default(false)`.
- **Defecto 3 — el calendario exigía fila en `participantes_programa` incluso para ADMIN/ALCHEMIST.** `ConsultarProgresoParticipanteCalendarPersistenceAdapter` hacía `FROM participantes_programa JOIN usuarios` (INNER): un actor SIN fila en `participantes_programa` desaparecía de `deParticipante` entero, y `AccesoEventoService.requireProgreso` tiraba 404 "Participante no encontrado" — para CUALQUIER operación, incluidas las administrativas (`GET /events`, `DELETE /events/{id}`, `POST .../portada/upload-url`). El baseline (`V1__baseline_renaser.sql`, comentario sobre `participantes_programa`) es explícito: el programa de 90 días es obligatorio solo para APRENDIZ, opcional para el resto — un ADMIN que nunca se inscribió puede legítimamente no tener esa fila, y aun así tiene que poder administrar el calendario. **Confirmado contra el código viejo** (`repository.ts`): `findViewerProgressPercent`/`findViewerCellId` solo consultan `traineeProfile`/`mentorProfile` cuando el rol es TRAINEE/MENTOR y devuelven `0`/`null` sin tocar la base para el resto de roles — el rol del actor viaja siempre desde `usuarios` (vía el JWT/sesión), nunca depende de tener perfil de programa. Corregido: la query pasó a `FROM usuarios LEFT JOIN participantes_programa` — `rol`/`estado` (suspendido) salen siempre de `usuarios`; `diaPrograma` cae a `0` y `celulaId` a `null` cuando no hay fila de programa (mismos defaults que el código viejo); `zona` cae a `'America/Lima'` (el default de la propia columna en el baseline) — hoy ningún caso de uso de `calendar` consume `zona` todavía, el fallback existe solo para que el adaptador no tenga que inventar un `ZoneId` nulo. El puerto ahora solo devuelve `Optional.empty()` cuando el `UserId` no existe en `usuarios` — nunca por falta de perfil de programa. Cubierto por `ConsultarProgresoParticipanteCalendarPersistenceAdapterTest` (nuevo, Testcontainers): admin sin fila de participante aparece con los defaults en vez de desaparecer.

**Nota sobre `EventoControllerValidationTest`:** es el primer test `@WebMvcTest`/`MockMvc` de todo el repo (no solo de `calendar`) — no había convención previa para probar la capa web en aislamiento. Se usó `@WebMvcTest(EventoController.class)` + `@AutoConfigureMockMvc(addFilters = false)` (sin tocar el filtro de seguridad, que hoy permite todo bajo `/api/v1/**`) con los 9 casos de uso mockeados vía `@MockitoBean`. Es exactamente el nivel donde vivían los Defectos 1 y 2 — ninguna prueba con mocks del servicio (`EventoServiceTest`) podía haberlos visto, porque el servicio nunca ve un `@RequestBody` sin validar ni una deserialización JSON real.

**Lo que NO cambió y no había que tocar:** `Evento.rehydrate()` ya tenía la guarda contra `EnumSet.copyOf` con colección vacía (arreglada en una pasada anterior) — se le sumó cobertura explícita (`EventoTest.rehydrateConRolesDestinoVacioNoRevienta` y dos variantes más) porque no tenía ningún test que ejercitara ese camino, que es el más común en producción (solo la audiencia `ROLES` llena `roles_destino_evento`).
