# Módulo `habits` — hábitos, Santuario, Día sin celular, radar, espíritu, diario

**Fecha:** 2026-08-24
**Ola:** 2 (núcleo del producto) — depende de `users` (via query propia, sin import Java) y `points` (via `points.api`)
**Documentos hermanos:** `CLAUDE.MD` (cómo), `docs/PLAN_DE_MODULOS.md` §"3. habits" (semilla), `docs/MODULOS_A_AVANZAR.md` (qué y en qué orden), `docs/MODULO_POINTS.md` / `MODULO_PHASECONTRACTS.md` / `MODULO_SUPPORT.md` (ejemplos vivos de convención)

**Alcance real de esta pasada:** este es, con diferencia, el módulo más grande del backend (9 agregados, 21 tablas). Dado el tamaño, se priorizó explícitamente — ver §0.6 de CLAUDE.MD ("decidir, avanzar y avisar qué se asumió") — en este orden: (1) el esqueleto de los 9 agregados, (2) `registro/` y `santuario/` (SesionBloqueo + RachaSinCelular) **100% implementados con tests exhaustivos**, por ser el mayor riesgo y mayor valor, (3) el resto con CRUD básico o solo dominio. El detalle de qué quedó en cada nivel está en §6.

**Actualización 2026-08-24 (D-41 — dejar de depender de Supabase):** el agregado `radar/` (Código Renaser) pasó de "solo dominio" a **completo** (puertos, `RadarService`, controller, persistencia, tests unitarios + integración + seguridad). Ver §8. El resto de lo dicho arriba (qué quedó en cada nivel para los otros 8 agregados) no cambió.

**Actualización 2026-08-24 (D-43 — % de hábitos EN LOTE para el ranking general):** nuevo `habits.api.PorcentajeHabitosFinder`, que expone el % de cumplimiento de hábitos de Ley VI en una sola consulta para N participantes (evita el N+1 que tumbaba el ranking general viejo). Ver §9.

---

## 0. Paso 0 — reglas exactas extraídas del código viejo (D-33)

Repo viejo clonado en `C:\Users\Usuario\Documents\Backend90dias\RenaserBack`. Todas las citas son archivo:línea contra ese clon.

### 0.1 Puntos por hábito (`src/features/habits/points.ts`)

Ya extraído literal en `docs/MODULO_POINTS.md` §2.1 — se implementó en `habits/domain/model/registro/ResultadoOtorgamiento.java`, no en `points` (esa es la responsabilidad de `habits`, `points` solo aplica el delta):

- `HABIT_FULL_POINTS=10` (points.ts:38), `GRACE_WINDOW_MINUTES=10` (points.ts:41), `GRACE_POINT_STEP_MINUTES=2` (points.ts:44), `GRACE_MIN_POINTS=5` (points.ts:47): `max(5, 10 − floor(minutosTarde/2))` (points.ts:112-117).
- `EXTENSION_WINDOW_HOURS=3` DEFAULT, por-hábito via `evidenceExtensionHours` (points.ts:59), `EXTENSION_POINTS=3` fijos (points.ts:62).
- Pasado gracia+extensión: EXPIRED, 0 puntos, bloqueado.
- `resolveHabitAward(deadline, deliveredAt, extensionWindowMinutes)` (points.ts:100-125) → `ResultadoOtorgamiento.calcular(instanteAncla, entregadoEn, extension)`, 1:1.

### 0.2 Ventana de entrega (`service.ts:280-420`)

- `HabitWindow.anchorInstant` (service.ts:310-322): hora FIN si existe, hora INICIO si no ("sin hora de fin, el ancla es la hora de inicio" — fallback del cliente móvil que el backend viejo no tenía hasta el 2026-08-07, corregido porque "16 de los 21 hábitos activos" no tenían hora fin).
- `limitInstantFor` (service.ts:285-299): ventanas que cruzan medianoche (22:00→02:00) ponen la hora fin al día siguiente si `triggerTime` no es anterior a `limitTime`.
- `effectiveExtensionMs` (service.ts:355-371): la extensión configurada se recorta contra `medianocheSiguiente − GRACIA`, nunca promete más de lo que el cron nocturno (barrido ciego de PENDING) va a respetar.
- Traducido 1:1 a `habits/domain/model/registro/VentanaEntrega.java` — **100% pure domain** (solo `java.time.*`, sin Spring/JPA), con `VentanaEntregaTest` cubriendo cada borde (sin horario, solo inicio, hora fin como ancla, cruce de medianoche, recorte de extensión, extensión nunca negativa, borde exacto de `vencida()`).

### 0.3 Máquina de estados del track (`service.ts:1549-1734`, `completeTrackWithOptions`)

- PENDING/IN_PROGRESS/COMPLETED/FAILED/EXPIRED (comentario cabecera service.ts:1-27).
- "FAILED and EXPIRED tracks cannot be completed" (service.ts:11, guards en 1594-1608).
- La ventana vencida marca EXPIRED de forma **lazy** al intentar completar (`requireHabitWindowOpen`, service.ts:459-470) — traducido a `RegistroHabito.expirar()` llamado desde `RegistroService.completar()` antes de rechazar.
- `applyHabitAward` (service.ts:497-529): **sin ventana (`window == null`), NO se otorgan puntos** — ni siquiera los 10 de "a tiempo". Traducido literal: `RegistroService.completar()` deja `puntos=0` y no llama a `AjustarPuntosPort` cuando `resolverVentana()` devuelve `null`.
- Motivo de puntos: `EXTENDED → HABIT_EXTENDED`, cualquier otro (`ON_TIME`/`GRACE`) `→ HABIT_COMPLETED` (service.ts:521) — `LATE_HABIT` es legado, no se usa.

### 0.4 Santuario / BLOQUEO (`blocking.ts`, completo)

Ver §2 de la investigación previa a este documento. Constantes: `DEFAULT_MIN_DURATION_MIN=30` (blocking.ts:16), `BREAK_PENALTY_POINTS=10` (blocking.ts:17), `COMPLETE_GRACE_MS=5min` (blocking.ts:18). `startBlockSession`/`completeBlockSession`/`breakBlockSession` (blocking.ts:99-232) traducidos 1:1 a `SesionBloqueo` (domain) + `SantuarioService` (application), con las mismas guardas: no antes de `triggerInstant`, mínimo de duración, gracia de 5 min tras `limitInstant`, penalización de −10 puntos SIEMPRE al romper (`penaltyApplied: true` sin excepción), idempotencia (completar ya-completada / romper ya-rota devuelven éxito sin re-ejecutar).

### 0.5 Día sin celular (`phoneFree.ts` + `phoneFreeLadder.ts`, completo)

Ver la investigación previa. `MILESTONES=[3,6,9,12,15,18,21,24]` (phoneFreeLadder.ts:27), `FULL_CYCLE_MINUTES=1440`, `MIN_CLOSABLE_MINUTES=180` (phoneFree.ts:57), `milestoneFor`/`isFullCycle`/`closeDeadlineFor` (phoneFreeLadder.ts:51-152) traducidos 1:1 a `RachaSinCelular`. Honor-based: romper NUNCA penaliza (phoneFree.ts:23-28, "Fallar un hito parcial no castiga"). Solo el ciclo completo (≥1440 min) otorga 10 puntos fijos, **medido por duración, no por puntualidad** — nunca pasa por `ResultadoOtorgamiento` (comentario explícito, phoneFree.ts:328-331). `releaseTrack` (phoneFree.ts:441-450): el track vuelve a PENDIENTE si es el track de HOY, EXPIRADO si no — traducido a `RegistroHabito.liberar(esDeHoy, ahora)`.

### 0.6 Áreas investigadas para las que se optó por una simplificación deliberada

Un agente de investigación en background analizó completos, con citas exactas, `habitStaggering.ts`+`staggerService.ts` (escalonamiento por lotes, ~1470 líneas), `weeklyChoice.ts` (elección de día semanal), `blocking.ts`+`limits.ts` (topes de edición), `renameableKeys.ts` (renombre de claves del catálogo), el cron `daily-reset` completo paso a paso, y `attachmentMedia.ts`/`attachmentStorage.ts`/`attachmentUrl.ts` (adjuntos de guía). El detalle completo con cita archivo:línea de cada regla quedó en el hallazgo del agente (no se transcribe entero acá por espacio) — se resume qué se decidió hacer con cada uno en §6.

---

## 1. Qué se construyó — estructura real

```
habits/
├── package-info.java                          @ApplicationModule("Habits")
├── api/                                        @NamedInterface("api")
│   ├── HabitoCompletadoEvent.java
│   ├── SantuarioRotoEvent.java
│   └── RachaCompletadaEvent.java
├── domain/model/
│   ├── habito/           Habito, HabitoId, AmbitoHabito, TipoHabito, TipoDia, ExigenciaEvidencia, PlantillaHabitoPersonal
│   ├── horario/          HorarioHabito, HorarioHabitoId
│   ├── guia/              GuiaHabito, AdjuntoGuia, SeccionGuia, TipoMedioGuia, *Id           (solo dominio, ver §6)
│   ├── registro/          RegistroHabito, EstadoRegistro, VentanaEntrega, ResultadoOtorgamiento, FaseOtorgamiento, RegistroHabitoId
│   ├── preferencia/       PreferenciaHorario, CambioHorarioPendiente                          (dominio completo; caso de uso propio NO wireado, ver §6)
│   ├── santuario/         SesionBloqueo, EstadoSesionBloqueo, MotivoSalidaBloqueo, RachaSinCelular, EstadoRacha, RachaSinCelularId
│   ├── espiritu/           RegistroEspiritu, EstadoRegistroEspiritu, RegistroEspirituId        (solo dominio, ver §6)
│   ├── radar/              RegistroRadar, RegistroRadarId                                       (COMPLETO — ver §8)
│   └── diario/             EntradaDiario, TipoEntradaDiario, EntradaDiarioId                    (solo dominio, ver §6)
├── application/
│   ├── ports/in/registro/     ConsultarTracksDelDiaUseCase, GenerarTracksDelDiaUseCase, CompletarRegistroUseCase, ExpirarRegistrosVencidosUseCase
│   ├── ports/in/santuario/    Iniciar/Completar/RomperSesionBloqueoUseCase, Iniciar/Cerrar/RomperRachaUseCase, ExpirarRachasVencidasUseCase
│   ├── ports/in/radar/        RegistrarCheckInRadarUseCase, ConsultarUltimoRadarUseCase, ConsultarHistorialRadarUseCase
│   ├── ports/out/{registro,habito,horario,preferencia,santuario,espiritu,radar,diario}/  Load/Save por agregado
│   ├── ports/out/participante/  ConsultarProgresoParticipanteHabitsPort, ListarParticipantesActivosPort
│   └── services/    RegistroService, SantuarioService, RachaService, RadarService
└── infrastructure/adapter/
    ├── in/rest/{registro,santuario,racha,radar}/    HabitTrackController, SantuarioController, RachaController, RadarController
    ├── in/scheduler/    ExpirarRegistrosScheduler (+ HabitsSchedulingConfig)
    └── out/persistence/{registro,santuario,habito,horario,preferencia,participante,radar}/    JPA + mapper a mano + adapter
```

Tests: `domain` (unit puro, sin Spring — `ResultadoOtorgamientoTest`, `VentanaEntregaTest`, `RegistroHabitoTest`, `SesionBloqueoTest`, `RachaSinCelularTest`, `RegistroRadarTest`), `application/services` (unit con Mockito — `RegistroServiceTest`, `SantuarioServiceTest`, `RachaServiceTest`, `RadarServiceTest`), `infrastructure/.../persistence` (IT con Testcontainers — `RegistroHabitoPersistenceAdapterTest`, `SantuarioPersistenceAdapterTest`, `RegistroRadarPersistenceAdapterTest`).

---

## 2. Decisiones propias de este módulo

- **H-1 — `MotivoPuntos` ya vivía en `points.api` al construir `habits`.** El encargo me autorizaba a mover `points.domain.model.ajuste.MotivoPuntos` a `points.api` si hacía falta para no romper `ArchitectureTest` (mismo problema de fuga que documentaron `phasecontracts`/`support` con `users.api.UserSummary`). Al llegar a esa parte, el módulo `rocks` (construido en paralelo, primer consumidor real según su propio javadoc, decisión RK-1) ya lo había hecho — de forma más limpia que mi plan (un solo enum en `points.api`, sin copia paralela en `domain`). `habits` simplemente consume `com.renaser.os.points.api.MotivoPuntos` directamente. No hizo falta ningún cambio en `points/**` de mi parte.
- **H-2 — Completar Santuario SÍ otorga puntos, con la misma escala que un hábito común.** El repo viejo (`blocking.ts`, `completeBlockSession`) **no llama a `resolveHabitAward`/`awardTrackPoints`** al completar una sesión — solo `breakBlockSession` toca puntos (penalización). Esto puede ser un vacío real del sistema viejo o un award que ocurre en otro punto no localizado en el paso 0. Se decidió (menor, CLAUDE.MD §0.6) que `SantuarioService.completar()` SÍ otorgue puntos con `ResultadoOtorgamiento` sobre la hora límite del horario, por consistencia con el resto del sistema — **no confirmado por negocio**, ver pregunta abierta §5.
- **H-3 — `PHONE_FREE_DAY` como clave de sistema, verificada literal** contra `phoneFreeKeys.ts:12` (no asumida).
- **H-4 — `ConsultarProgresoParticipanteHabitsPort` es una copia PROPIA** del patrón de `phasecontracts` (query nativa sobre `participantes_programa`+`usuarios`, enum `RolParticipante` local), no una reutilización de la de `phasecontracts` ni de `users.api` — exactamente la instrucción del encargo, misma deuda documentada en 3 módulos ahora.
- **H-5 — Generación de tracks (`GenerarTracksDelDiaUseCase`) por participante, no por lote global.** El cron viejo (`dailyReset`, service.ts:2636-2820) procesa TODOS los aprendices activos en una sola pasada, agrupados por timezone, con Sunday/Intoxication resolution, filtro de elección semanal y filtro de escalonamiento. Implementar eso completo era el 60% del riesgo de todo el módulo. Se implementó en cambio un caso de uso por participante (`RegistroService.generar(participanteId, fecha)`) que resuelve catálogo+horario+idempotencia, y se documenta como **incompleto para uso en producción a escala** — el scheduler de expiración (`ExpirarRegistrosScheduler`) NO llama a este caso de uso todavía (solo expira, no genera). Ver deuda D-H1 en §6.
- **H-6 — `TipoDia` resuelto solo como DOMINGO/DISCIPLINA por día de calendario.** `INTOXICACION` (ciclos fijos del programa) no se implementó — el paso 0 no alcanzó a extraer `INTOXICATION_CYCLES` con precisión suficiente para no inventarlo. `TipoDia.INTOXICACION` existe en el enum (fiel al baseline) pero ningún caso de uso lo asigna todavía.
- **H-7 — Sin escalonamiento (`habitStaggering.ts`) ni elección de día semanal (`weeklyChoice.ts`) en `GenerarTracksDelDiaUseCase`.** Un hábito con `eleccionDiaSemanal=true` genera track TODOS los días que su horario aplique, no solo el día elegido — documentado explícitamente, no oculto (ver §6, D-H2/D-H3).

---

## 3. Endpoints construidos

Actor resuelto por header `X-Actor-Id` (temporal, D-29 de `users`, sin autenticación real por B-2 — mismo patrón que `points`/`phasecontracts`/`support`). Autoservicio estricto: el participante solo opera sobre sus propios tracks/sesiones/rachas.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/habit-tracks/today` | Tracks del día del actor |
| POST | `/api/v1/habit-tracks/{id}/complete` | Completa un registro (no-BLOQUEO) |
| POST | `/api/v1/habit-tracks/{id}/santuario/start` | Inicia sesión de Santuario |
| POST | `/api/v1/habit-tracks/{id}/santuario/complete` | Completa la sesión (idempotente si ya COMPLETADA) |
| POST | `/api/v1/habit-tracks/{id}/santuario/break` | Rompe la sesión (−10 puntos, siempre) |
| POST | `/api/v1/habit-tracks/{id}/phone-free/start` | Inicia racha sin celular |
| POST | `/api/v1/habit-tracks/phone-free/complete` | Cierra la racha activa del actor |
| POST | `/api/v1/habit-tracks/phone-free/break` | Rompe la racha activa (sin penalización) |
| POST | `/api/v1/radar` | Registra un check-in de Código Renaser (§8) |
| GET | `/api/v1/radar/latest` | Timestamp del último check-in del actor (gating de horario, ver §8) |
| GET | `/api/v1/radar/history?cursor=` | Historial paginado por cursor, 20 por página (§8) |

**Contrato viejo no verificado 1:1** contra `docs/API_CONTRACT.md`/`docs/MOBILE_HABITS_API.md` del repo viejo — no se abrieron esos archivos en esta pasada por límite de tiempo frente al tamaño del módulo. Las rutas se diseñaron por analogía con `blocking.ts`/`phoneFree.ts` (`POST .../phone-free/start|complete|break`, coincide con los comentarios de esos archivos) pero **esto es una desviación potencial sin verificar**, documentada explícitamente en vez de asumida como correcta.

---

## 4. Integración con `points` (CLAUDE.MD §9.1)

`RegistroService.completar()`, `SantuarioService.completar()`/`.romper()` y `RachaService.cerrar()` llaman a `points.api.AjustarPuntosPort.ajustar(...)` **sincrónicamente, dentro del mismo método `@Transactional`** que completa/rompe el registro — nunca por evento. Si la transacción hace rollback (p. ej. el `save` del ajuste de puntos falla), el cambio de estado del registro también se revierte (mismo `PlatformTransactionManager`, mismo datasource — igual garantía que documenta `MODULO_POINTS.md` Q-5).

Los eventos `HabitoCompletadoEvent`/`SantuarioRotoEvent`/`RachaCompletadaEvent` SÍ se publican (para `notifications`, Ola 3, que no existe todavía) pero **no** son el mecanismo de actualización de puntos.

**`radar` es la excepción: no toca `points` en absoluto.** Ni en el backend viejo (`daily-checkin/service.ts`, revisado completo en el paso 0 de §8) ni en `radar.ts` (cliente actual) hay ningún cálculo de puntos asociado al Código Renaser — es un registro de auto-observación, no una tarea puntuable. `RadarService` no inyecta `AjustarPuntosPort` ni publica eventos.

---

## 5. Preguntas abiertas (CLAUDE.MD §0.6)

1. **¿Completar el Santuario debe otorgar puntos?** (H-2). El repo viejo no lo hace explícitamente en `completeBlockSession`. Se asumió que sí, por consistencia — a confirmar.
2. **¿Cuáles son los ciclos exactos de `INTOXICACION`?** No se encontraron en el paso 0 con la precisión necesaria para implementarlos sin inventar. `TipoDia.INTOXICACION` queda sin ningún caso de uso que lo asigne.
3. **¿El contrato REST viejo (`docs/API_CONTRACT.md`/`MOBILE_HABITS_API.md`) coincide con las rutas construidas en §3?** No verificado — ver la nota de §3.
4. **Roles permitidos por endpoint:** ningún endpoint de este módulo valida rol todavía más allá de "es el propio participante" (`requireSelf`) — no se replicó `requireActiveTrainee`/chequeo de rol TRAINEE explícito del repo viejo porque `ConsultarProgresoParticipanteHabitsPort.RolParticipante` está disponible pero no se usó para gatear (cualquier rol autoservicio puede operar sus propios tracks). A confirmar si corresponde restringir a TRAINEE. **Excepción: `radar` (§8) sí lo hace** — ahí el repo viejo era explícito (`requireRole(['TRAINEE'])` en ambas rutas) así que se replicó; queda como precedente para cuando se resuelva esta pregunta para el resto del módulo.
5. **(§8) ¿Un mentor/admin puede consultar el Código Renaser de un aprendiz?** El backend viejo tenía una ruta admin (`src/app/api/v1/admin/trainees/[id]/radar/route.ts`) fuera del alcance de este encargo (autoservicio estricto). No construida — ver D-H12.

---

## 6. Qué quedó explícitamente sin cubrir (honestidad de alcance, CLAUDE.MD §0.2/§0.6)

| # | Deuda | Detalle |
|---|---|---|
| D-H1 | `GenerarTracksDelDiaUseCase` no está wireado a ningún scheduler | Existe el caso de uso (por participante), pero `ExpirarRegistrosScheduler` solo expira — no genera los tracks del día siguiente. Falta un `GenerarTracksScheduler` que itere participantes activos, igual patrón que `ExpirarRegistrosScheduler` |
| D-H2 | Sin escalonamiento por lotes (`habitStaggering.ts`, ~1470 líneas) | `desbloqueos_habito` existe en el baseline sin entidad JPA en este módulo. Cualquier hábito de catálogo se ofrece completo desde el día 1 de su horario, sin escalera |
| D-H3 | Sin elección de día semanal (`weeklyChoice.ts`) | Un hábito `eleccionDiaSemanal=true` no filtra por el día elegido — genera track todos los días que su horario aplique |
| D-H4 | Sin renombre de hábitos del catálogo (`renameableKeys.ts`, tabla `renombres_habito`) | Sin dominio ni caso de uso — sería trivial de agregar (ver hallazgos: `RENAME_ALLOWED_UNTIL_PROGRAM_DAY=0`, claves `GREEN_JUICE`/`WARM_LEMON_WATER`) pero no se priorizó |
| D-H5 | `guia/`, `espiritu/`, `diario/` — solo dominio | Sin `ports/out`, sin `application/services`, sin persistencia, sin REST. `HabitoJpaEntity` tampoco mapea `grupo`/`tipo_entrada_diario`/`dia_limite_edicion_libre`/`orden` (columnas nullable/con DEFAULT — un `save()` de un `Habito` existente pisaría esas columnas con NULL/default si alguna vez tuvieran datos, riesgo aceptado y documentado en el javadoc de `HabitoJpaEntity`). **`radar/` salió de esta lista 2026-08-24 — ver §8, ahora completo** |
| D-H6 | **RESUELTO 2026-08-25** — ~~`SubirEvidenciaUseCase` no se construyó~~. El encargo permitía simplificar la validación IA (no bloquear finalización), pero **no se llegó a construir el flujo de subida de evidencia en sí** (vía `AlmacenamientoPort`, bucket `evidencias/`). Ningún endpoint de este módulo integra evidencia todavía. **Cerrado**: `evidence` ya existe (`docs/MODULO_EVIDENCE.md`). Nuevo `SubirEvidenciaRegistroUseCase` (`application/ports/in/registro/`), implementado por `EvidenciaRegistroService` (servicio separado de `RegistroService` por tamaño de clase, CLAUDE.MD §5.4.8), delega en `evidence.api.RegistrarEvidenciaPort`. Nuevo endpoint `POST /api/v1/habit-tracks/{id}/evidence`. No otorga puntos ni cambia el estado del registro — eso lo sigue haciendo `CompletarRegistroUseCase`, son dos pasos independientes (a diferencia de `rocks`, donde completar y subir evidencia son el mismo paso). Ver `docs/MODULO_EVIDENCE.md` §8. |
| D-H7 | `EditarHorarioUseCase`/`ElegirDiaSemanalUseCase`/`DesbloquearUseCase` no construidos | `PreferenciaHorario`/`CambioHorarioPendiente` tienen dominio completo y persistencia (`preferencia/`), usados INTERNAMENTE por `RegistroService`/`SantuarioService` para resolver la ventana — pero no hay caso de uso ni endpoint para que el aprendiz edite su propio horario |
| D-H8 | Sin `@RequiresPermission`/`@PublicEndpoint` ni test de reflexión | Mecanismo no existe todavía en el repo (bloqueado por B-5/R-2 de `users`, igual que todos los módulos anteriores) |
| D-H9 | `RevisionSemanalSinCelular` (penalización semanal −10 si ningún ciclo completo) no implementada | `phoneFreeWeekly.ts` completo (catch-up de 8 semanas, `MIN_PROGRAM_DAYS_IN_WEEK=4`) analizado en el paso 0, pero no traducido — no hay tabla ni caso de uso en este módulo. `MotivoPuntos.PHONE_FREE_WEEK_MISSED` existe en `points.api` sin ningún llamador desde `habits` |
| D-H10 | Migración Flyway propia: **no hizo falta** | Las 21 tablas de `habits` ya están completas en `V1__baseline_renaser.sql`, sin columnas faltantes detectadas |
| D-H11 | Seeds del catálogo (hábitos+horarios+guías+90 audios+13 audioterapias) | Fuera de alcance de esta tarea — es trabajo de migración de datos, no de código |
| D-H12 | `radar`: sin vista para mentor/admin del Código Renaser de un aprendiz | El backend viejo tenía `GET /api/v1/admin/trainees/[id]/radar` — fuera de alcance del encargo (autoservicio estricto, D-41). `RadarController` no tiene ningún endpoint que acepte un `participanteId` distinto del actor |
| D-H13 | `radar`: sin endpoint equivalente a `GET .../today` del contrato viejo (RC-02) | No construido porque el cliente actual (`radar.ts`) no lo usa — usa `/latest` + `/history` en su lugar (§8). Si algo del lado admin/mentor necesitara "los check-ins de hoy de este aprendiz", falta ese endpoint |

---

## 7. Estado / checklist DoD

- [x] `domain/` plano por agregado, sin imports de Spring/JPA/Jackson (verificado por lectura; `ArchitectureTest.domainIsFrameworkFree` debería confirmarlo)
- [x] Tests unitarios de dominio exhaustivos para `registro/` y `santuario/` (bordes de ventana, gracia, extensión, hitos de racha, transiciones de estado)
- [x] Casos de uso con comando self-validating (`CompletarRegistroCommand`, `Iniciar/Completar/RomperSesionBloqueoCommand`, `Iniciar/Cerrar/RomperRachaCommand`)
- [x] Controller tonto: sin repositorios, sin `@Transactional`, sin reglas de negocio
- [x] DTO de salida como proyección explícita (`RegistroHabitoResponse`, `SesionBloqueoResponse`, `RachaSinCelularResponse`)
- [x] Integración sincrónica con `points.api.AjustarPuntosPort` dentro de la misma transacción (§4)
- [x] Tests de integración con Testcontainers para `registro` y `santuario` (ambos agregados: SesionBloqueo y RachaSinCelular)
- [x] Pruebas de seguridad §0.3: actor ≠ participante → `NotAuthorizedException` (`RegistroServiceTest`, `SantuarioServiceTest`), cuenta suspendida → `NotAuthorizedException` (`RachaServiceTest`)
- [ ] Test de reflexión `@RequiresPermission`/`@PublicEndpoint` — no aplica, mecanismo no existe (D-H8)
- [ ] `ArchitectureTest` — no ejecutado por este agente (regla del encargo: no correr Maven)
- [ ] `./mvnw clean test` — no ejecutado por este agente, mismo motivo. El supervisor lo corre
- [x] Avance documentado en este archivo, con honestidad explícita de lo que quedó afuera (§6)
- [ ] Bitácora de errores (`docs/BITACORA_ERRORES.md`) — no se encontró ningún error/bug real de configuración durante la construcción (solo decisiones de diseño, documentadas en §2); no se agregó entrada artificial

**`radar` (§8), agregado añadido 2026-08-24 — checklist propio:**

- [x] `RegistroRadar` (dominio) valida rango de energía (1-10) y longitud máxima de texto (2000) — antes solo validaba blank/rango de energía, se agregó el máximo de longitud en esta pasada
- [x] Comando self-validating (`RegistrarCheckInRadarCommand`) sin campo `participanteId` alcanzable desde el cliente (solo el actor del header, D-36/§5.3.3)
- [x] Controller tonto, DTOs de salida como proyección explícita (`RegistroRadarResponse`, `UltimoRadarResponse`, `RadarHistoryPageResponse`)
- [x] `RadarService` NO llama a `points.api` (§4) — confirmado que el check-in nunca fue puntuable, ni en el repo viejo ni en el cliente actual
- [x] Pruebas de seguridad §0.3: actor ≠ participante → `NotAuthorizedException`, cuenta suspendida → `NotAuthorizedException`, rol ≠ TRAINEE → `NotAuthorizedException` (`RadarServiceTest`, las tres)
- [x] Tests unitarios de dominio (`RegistroRadarTest`): rango de energía, blank, longitud máxima, trim, equals por id
- [x] Tests de integración con Testcontainers (`RegistroRadarPersistenceAdapterTest`): guardar/recuperar, orden descendente, paginación por cursor, aislamiento entre participantes
- [ ] `ArchitectureTest`/`./mvnw clean test` — no ejecutados por este agente (regla del encargo), el supervisor los corre

**Nota final de honestidad de alcance:** este módulo es, por tamaño, imposible de cerrar al 100% en una sola pasada con la misma profundidad que `points`/`phasecontracts`/`support`. Se priorizó explícitamente el corazón de valor y riesgo (`registro/` + `santuario/`, con sus dos agregados SesionBloqueo y RachaSinCelular) con el mismo nivel de rigor que esos tres módulos — tests exhaustivos, paso 0 verificado línea por línea, integración real con `points`. El resto del módulo (6 de 9 agregados) tiene el esqueleto de dominio construido y, en algunos casos (`horario`, `preferencia`), persistencia funcional usada internamente — pero sin casos de uso ni endpoints propios. La lista de §6 es exhaustiva a propósito: nada de lo que falta está escondido.

---

## 8. Agregado `radar` — Código Renaser (D-41, 2026-08-24)

**Encargo:** D-41 — "no quiero depender de Supabase, todo debe ser con nuestra API de Spring Boot". Hoy la app móvil escribe el check-in del Código Renaser **directo a Postgres via Supabase**, saltándose el backend por completo (`RenaserPlayStoreCopy/src/services/radar.ts:111,145,366`). Este agregado construye la API de Spring que reemplaza esa escritura directa.

### 8.0 Paso 0 — qué se encontró

**Repo viejo (`C:\Users\Usuario\Documents\Backend90dias\RenaserBack`) — la regla SÍ existía en servidor:**

- `src/app/api/v1/radar/route.ts` (RC-01, `POST /api/v1/radar`) y `src/app/api/v1/radar/today/route.ts` (RC-02, `GET /api/v1/radar/today`) — ambos con `requireRole(auth.data, ['TRAINEE'])` explícito. **Esto contradice la premisa inicial del encargo** ("puede que la lógica esté solo en la app") — no es así: el backend Next.js viejo sí validaba en servidor. Lo que pasó es que el cliente móvil actual (`radar.ts`) quedó escribiendo directo a Supabase en algún punto posterior y dejó de pasar por esa API — probablemente para no depender de que el backend Next.js estuviera desplegado, o por apuro. No se encontró comentario ni commit que explique el porqué del salto; se documenta el hecho, no se inventa el motivo.
- `src/features/daily-checkin/schema.ts` (`CreateRadarEntryInput`, zod): `whatAmIDoing`/`whatAmIThinking`/`whatAmIFeeling`/`whatAmIAvoiding` — cada uno `min(1).max(RADAR_TEXT_MAX_LENGTH)` con `RADAR_TEXT_MAX_LENGTH = 2_000`; `energyLevel: z.number().int().min(1).max(10)`. **Idéntico** a las constantes que ya vivían en el dominio Java (`RegistroRadar.registrar`, ya validaba 1-10) y en `radar.ts:65-91` del lado cliente (`RADAR_TEXT_MAX_LENGTH = 2_000`, mismo `validate()`). Tres capas, mismos números — se tomaron como confirmados, no inventados.
- `src/features/daily-checkin/service.ts` — `submitRadarEntry` es un passthrough directo al repositorio (sin cálculo de puntos, sin efectos secundarios). `getTodayRadarEntries`/`getRadarEntriesForDate` calculan `avgEnergy`/`minEnergy`/`maxEnergy` por día (uso admin/mentor, ver D-H12/D-H13) — no usado por el cliente actual.
- `src/features/daily-checkin/repository.ts` — Prisma `radarEntry.create`/`findMany` planos, sin `unique` compuesto por día. **Confirma: no hay "uno por día"** — es un log append-only, tantos check-ins como el usuario quiera enviar (el gating de horario en `radar.ts:169-338` es UX del cliente, nunca una restricción de servidor ni de base).
- **Tabla `registros_radar`** (baseline `V1__baseline_renaser.sql:623-634`): `id`, `participante_id` (FK a `participantes_programa.usuario_id` ON DELETE CASCADE), `que_hago`/`que_pienso`/`que_siento`/`que_evito` (`text NOT NULL`), `nivel_energia` (`smallint NOT NULL CHECK BETWEEN 1 AND 10`), `creado_en` (`timestamptz DEFAULT now()`). Sin `actualizado_en` — confirma append-only, sin UPDATE de negocio. Índice `radar_perfil_fecha_idx (participante_id, creado_en DESC)` — a medida para "último" y "historial paginado desc".
- **`RegistroRadar`/`RegistroRadarId`** (dominio) ya existían de una pasada anterior del módulo (esqueleto, D-H5 original) — solo le faltaba la validación de longitud máxima (2000), que no estaba y se agregó en esta pasada (antes solo validaba blank + rango de energía).
- **Cliente actual** (`RenaserPlayStoreCopy/src/services/radar.ts`, leído completo):
  - `saveRadarEntry` (línea 97-129): valida cliente-side (idéntico a `schema.ts` viejo) y hace `supabase.from("radar_entries").insert(...)`.
  - `getLatestRadarEntryTime` (línea 136-158): `select("created_at").order(desc).limit(1).maybeSingle()` — solo usado para calcular el próximo horario habilitado (`getNextRadarAvailability`/`getNextRadarAvailabilityForSlots`, lógica pura de UI que no toca el backend).
  - `getRadarHistory` (línea 357-390): `select("*").order(desc).limit(20)`, con cursor `.lt("created_at", cursor)` si se pasa uno — página completa (=20) implica "puede haber más", sin conteo extra.
  - No hay ningún tercer `select`/`insert` contra `radar_entries` en el archivo.

### 8.1 Decisiones propias de este agregado

- **RD-1 — Nombres de campo REST en inglés, literales del contrato viejo (`CreateRadarEntryInput`/`RadarEntryItem`), no en español ni el `snake_case` de la tabla Supabase.** D-36 pide "el idioma que la app ya consume, sacado literal, no inventado". La app hoy habla `snake_case` con Supabase (`what_am_i_doing`) porque así expone PostgREST las columnas — pero eso es un detalle de Supabase, no un contrato HTTP real. El contrato HTTP real que sí existió (`RC-01`/`RC-02`, backend viejo) usaba `camelCase` (`whatAmIDoing`, `energyLevel`) — igual a como Jackson serializa por convención en el resto de este backend (ningún otro DTO del repo usa `snake_case`). Se tomó ese contrato HTTP viejo como la fuente de verdad de nombres, no la tabla Supabase — es la interpretación más literal de "la app ya consume" que sigue siendo coherente con el resto del código Java.
- **RD-2 — Rutas `/api/v1/radar` (POST) igual al contrato viejo; `/latest` y `/history` son nuevas.** El contrato viejo exponía `/today` (RC-02); el cliente actual no usa nada parecido a "hoy" — usa "último timestamp" + "historial paginado". Construir `/today` habría sido inventar un endpoint que nadie consume; se construyó lo que los dos `select()` reales piden. `/today` queda como deuda documentada (D-H13) si algún día hace falta para uso admin/mentor.
- **RD-3 — Restricción a rol TRAINEE, tomada literal del `requireRole(['TRAINEE'])` de ambas rutas viejas.** Es el primer endpoint de todo el módulo `habits` que sí gatea por rol explícitamente (ver pregunta abierta #4 en §5, que seguía sin resolverse para el resto del módulo). `RadarService.requireParticipanteHabilitado` reutiliza `ConsultarProgresoParticipanteHabitsPort` (mismo puerto que `SantuarioService`) para chequear `suspendido` y ahora también `rol == TRAINEE`.
- **RD-4 — `RegistroRadar.registrar` gana validación de longitud máxima de texto (2000).** Ya validaba blank y rango de energía; la cota de longitud faltaba en el dominio y solo existía como `@Size` a nivel DTO/REST en el plan original. Corregido para que la regla real de negocio (§5.4.3 nivel 3 de CLAUDE.MD, "en el dominio, nunca solo anotaciones") viva donde corresponde — el `@Size(max=...)` en `RegistrarCheckInRadarRequest`/`RegistrarCheckInRadarCommand` queda como fail-fast de nivel 1/2, no como la única barrera.
- **RD-5 — `actorId`/`participanteId` separados en los tres casos de uso, aunque el controller siempre pase el mismo valor para ambos.** Mismo patrón que `ConsultarTracksDelDiaUseCase` (§1): no hay forma de pedir el Código Renaser de otro desde el cliente hoy (D-H12), pero la separación deja el guard `requireSelf` testeable por unidad y lista para el día que exista una vista mentor/admin.
- **RD-6 — Historial pagina con la misma heurística "página llena ⇒ puede haber más" del cliente viejo**, no un `COUNT` ni un fetch de `limite+1`. Es deliberadamente la misma imprecisión que ya tenía `getRadarHistory` (radar.ts:384-387): si el total de registros es un múltiplo exacto del tamaño de página, el cliente pedirá una página más de la cuenta que volverá vacía. Se replicó por fidelidad al contrato, no por ser la solución "mejor" — está documentado, no oculto.

### 8.2 Endpoints construidos

Actor por header `X-Actor-Id` (D-29, temporal). Autoservicio estricto — sin parámetro de URL para pedir el Código Renaser de otra persona.

| Método | Ruta | Descripción | Restricción de rol |
|---|---|---|---|
| POST | `/api/v1/radar` | Registra un check-in (`RegistrarCheckInRadarRequest`: `whatAmIDoing`, `whatAmIThinking`, `whatAmIFeeling`, `energyLevel`, `whatAmIAvoiding`) | TRAINEE únicamente (RD-3) |
| GET | `/api/v1/radar/latest` | `{ "createdAt": <instant> \| null }` — timestamp del check-in más reciente del actor | TRAINEE únicamente |
| GET | `/api/v1/radar/history?cursor=<instant opcional>` | `{ "entries": [...], "nextCursor": <instant> \| null }`, 20 por página, orden descendente | TRAINEE únicamente |

### 8.3 Preguntas abiertas de este agregado

Ver también la pregunta #5 agregada en §5. Resumen:

1. **¿Vista de mentor/admin sobre el Código Renaser de un aprendiz?** Existía en el backend viejo (`/admin/trainees/[id]/radar`), fuera de alcance de este encargo (D-H12).
2. **¿Hace falta el equivalente a `/today` (RC-02)?** El cliente actual no lo usa; si un futuro flujo admin/mentor lo necesita, falta construirlo (D-H13).
3. **¿Por qué el cliente móvil dejó de pasar por la API vieja y empezó a escribir directo a Supabase?** No se encontró el motivo en el repo (sin commit ni comentario) — se documenta el hecho, no se especula la causa.

---

## 9. `PorcentajeHabitosFinder` — % de hábitos EN LOTE para el ranking general (D-43, 2026-08-24)

**Encargo:** D-43 (`docs/MODULOS_A_AVANZAR.md` §8) — el ranking general viejo (50% hábitos + 35% rocas + 15% cursos) hacía **una consulta por aprendiz** para cursos y con ~30 cuentas activas devolvía *"Too many database connections opened"* (verificado en vivo el 2026-08-12). Decisión del dueño: cada módulo expone su % **EN LOTE** por su `api/`; `points` combina los pesos en su dominio. Esta sección documenta la parte de `habits`.

### 9.0 Paso 0 — la fórmula, verificada contra el código, no contra el comentario

Repo viejo clonado en `C:\Users\Usuario\Documents\Backend90dias\RenaserBack`. Fuente de verdad: `src/lib/coherence.ts` (no la cabecera de la función SQL, aunque coincidan — ver abajo).

- **Ventana — 7 días UTC cerrados incluyendo hoy**: `src/app/api/cron/coherence-score/route.ts:56-63` (`getLast7UtcDates`, `from = today - 6`, 7 fechas hasta `today` inclusive). El propio `coherence.ts` no fija la ventana — la recibe del llamador (`averageCompletionForDates(habitTracks, dailyRocks, dates)`, `coherence.ts:114-131`); el cron es quien arma esas 7 fechas.
- **Un hábito opcional no completado no entra ni al numerador ni al denominador**: `coherence.ts:61-68` (`if (h.isOptional && !done) continue`) dentro de `computeDailyCompletionHistory` (`coherence.ts:45-100`).
- **Cada día se redondea a entero PRIMERO y LUEGO se promedia** (doble redondeo deliberado): redondeo por día en `coherence.ts:97` (`score: total > 0 ? Math.round((completed/total)*100) : 0`); promedio con **1 decimal** en `coherence.ts:130` (`Math.round(avg * 100 * 10) / 10`) — el promedio **no** se redondea a entero.
- **Ventana sin días calificables → 100**: `coherence.ts:127` (`if (dayScores.length === 0) return 100`), "recién empezó", no se castiga.
- **Hábitos personales cuentan igual que los globales, nunca opcionales**: `coherence-score/route.ts:106-113` (`withPersonalTracks`) mezcla ambas fuentes en el mismo array antes de llamar a `computeDailyCompletionHistory` — en el repo viejo esto exigía unir dos tablas (`habit_tracks` + `personal_habit_tracks`). **En este backend Java no hace falta replicarlo**: el baseline SQL (`V1__baseline_renaser.sql:385-386,554-555`) ya fusionó `habitos`/`registros_habito` en una sola tabla cada uno (comentario literal: *"UNIFICA habits + personal_habits (P-12)"*, *"Fusiona habit_tracks + personal_habit_tracks (P-12)"*), y `es_opcional` vive directo en `registros_habito` (denormalizado al crear el track) — la consulta en lote no necesita distinguir `ambito` para saber si un registro es opcional.

**Comentario de la función SQL vieja (`prisma/migrations/general_ranking_scores_function.sql:18-24`) vs. el código TypeScript — SÍ coinciden para hábitos.** Se verificó cada CTE de `habit_day_totals`/`habit_day_scores`/`habitos_pct` (líneas 79-113 de ese archivo) contra `coherence.ts` línea por línea: mismo filtro de opcionales, mismo doble redondeo, misma ventana de 7 días, mismo default de 100. No se encontró ninguna divergencia — a diferencia de lo que el encargo advertía como posible, acá el comentario SQL sí es fiel al código de producción. (No se auditó la parte de `cursos_pct` de esa función — fuera del alcance de `habits`.)

### 9.1 Corrección del coordinador sobre la firma (2026-08-24)

El encargo original sugería `Map<UserId, Integer>`. Se cambió a **`Map<UserId, BigDecimal>` con 1 decimal** — el redondeo real es DOBLE (por día a entero, **luego el promedio con 1 decimal**, `coherence.ts:130` y `general_ranking_scores_function.sql:110` `round(avg(day_score) * 10) / 10`), no un solo redondeo a entero. Devolver `Integer` habría agregado una TERCERA vuelta de redondeo que el sistema viejo nunca tuvo, violando la instrucción explícita de D-43 de portar la fórmula literal sin recalibrar ningún criterio. `rocks` y `academy` exponen el mismo contrato (`BigDecimal` escala 1) para que `points` los combine de forma consistente.

### 9.2 Qué se construyó

```
habits/
├── api/
│   └── PorcentajeHabitosFinder.java                     porcentajePorParticipante(Collection<UserId>, LocalDate hasta) -> Map<UserId, BigDecimal>
├── domain/model/registro/
│   ├── ConteoDiarioHabitos.java                          tally cruda de UN dia (totalRegistros, completados, opcionalesNoCompletados) — value object, sin logica de negocio propia mas alla de validar invariantes
│   └── PorcentajeHabitos.java                            PorcentajeHabitos.calcular(List<ConteoDiarioHabitos>) — la regla de negocio completa (doble redondeo, exclusion de opcionales via calificables(), ventana vacia -> 100), 100% testeable sin Postgres
├── application/
│   ├── ports/out/registro/ContarRegistrosDiariosHabitsPort.java    puerto EN LOTE — una sola consulta para N participantes
│   └── services/PorcentajeHabitosService.java            implementa el Finder: arma la ventana [hasta-6, hasta], llama al puerto UNA vez, aplica PorcentajeHabitos.calcular por participante
└── infrastructure/adapter/out/persistence/registro/
    ├── ConteoDiarioHabitosProjection.java                 proyeccion Spring Data de la fila agregada (participante, dia)
    └── ContarRegistrosDiariosHabitsPersistenceAdapter.java  + query JPQL agregada en SpringDataRegistroHabitoRepository (GROUP BY participante+dia, enum bindeado como parametro — mismo motivo que documenta SpringDataRankingAprendizRepository sobre el cast de enums nativos de Postgres)
```

**Por qué la agregación es SQL puro y la regla de negocio es Java:** la consulta JPQL solo cuenta filas por condición sobre columnas ya almacenadas (`COUNT`, `SUM(CASE WHEN estado = :completado ...)`, `SUM(CASE WHEN es_opcional AND estado <> :completado ...)`) — nunca decide qué "cuenta". La resta `calificables = totalRegistros - opcionalesNoCompletados` y el doble redondeo viven en `PorcentajeHabitos` (dominio), así el criterio de negocio se testea sin Testcontainers.

**Qué pasa con un participante sin ningún registro en la ventana:** el `Map` que devuelve `PorcentajeHabitosFinder.porcentajePorParticipante` trae **una entrada por cada participante pedido, sin excepción** — nunca lo omite. Un participante sin datos aparece con el valor explícito `100.0` (`PorcentajeHabitos.SIN_DIAS_CALIFICABLES`). Es distinto del `COALESCE(hp.pct, 100)` de la función SQL vieja (ahí el `LEFT JOIN` simplemente no trae fila y el caller debe saber aplicar el default) — acá `points` puede confiar en que `resultado.get(participanteId)` nunca es `null` para ningún id de la colección pedida.

### 9.3 Tests

- **Dominio** (`PorcentajeHabitosTest`, `ConteoDiarioHabitosTest` — sin Spring, sin Postgres): doble redondeo con resultado que diverge de un solo redondeo (67/17 por día → 42.0, distinto de promediar las fracciones crudas → 41.7), redondeo de mitad exacta (37.5% → 38, igual que `Math.round` de JS), opcional no completado sin castigar, día sin nada calificable excluido del promedio, ventana vacía → 100.0, promedio con decimal significativo (`.3`, `.7`) y sin decimal (100,100,67 → 89.0) — el caso que `Integer` hubiera perdido en silencio.
- **Aplicación** (`PorcentajeHabitosServiceTest`, Mockito): varios participantes en una sola llamada al puerto, participante ausente del mapa del puerto → 100.0 explícito, colección vacía no consulta el puerto.
- **Integración** (`ContarRegistrosDiariosHabitsPersistenceAdapterTest`, Testcontainers): agregación correcta por participante+día con datos reales, registro fuera de la ventana excluido, participante sin registros no aparece, y **una sola consulta SQL para N participantes** (aserción vía `Statistics.getQueryExecutionCount() == 1` de Hibernate, con `hibernate.generate_statistics=true` activado solo en esa clase de test vía `@TestPropertySource` — no se tocó `application.yaml` compartido).

### 9.4 Qué quedó abierto

- **No verificado contra `rocks`/`academy` en vivo**: se siguió la corrección del coordinador de que los tres exponen `BigDecimal` escala 1, pero no se leyó el código de esos dos módulos (fuera del alcance de este agente — reglas duras del encargo).
- **`points` (consumidor) no existe todavía** en este repo — el contrato de `PorcentajeHabitosFinder` es especulativo hasta que se construya y se confirme que encaja.
