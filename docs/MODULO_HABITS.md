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

---

## 10. "Espíritu" (audioterapia diaria) — completado 2026-08-26

**Encargo:** D-H5 tenía `espiritu/` como solo-dominio (`RegistroEspiritu`, `EstadoRegistroEspiritu`, `RegistroEspirituId`, y ya existían los puertos `Load/SaveRegistroEspirituPort`). Esta pasada cierra el agregado de punta a punta: casos de uso, persistencia, controller y el catálogo de audios detrás de un puerto (sin integrar Drive — pedido explícito del encargo).

### 10.1 Paso 0 — reglas verificadas contra el repo viejo (`C:\Users\Usuario\Documents\Backend90dias\RenaserBack`, `src/features/spirit-audio/service.ts`)

- **Hora de desbloqueo 7:00, hora límite 12:00**, hora local del participante (`UNLOCK_HOUR`/`DEADLINE_HOUR`, service.ts:55-56).
- **`AUDIO_UNLOCK_START_DAY = 7`**: `audioDay = diaPrograma - 7` → diaPrograma 8 da audioDay 1 (confirmado con el cliente 2026-08-10 según el comentario del repo viejo; verificado, no asumido).
- **State machine lazy (`ensureAdvanced`)**: se evalúa en cada lectura de estado y en cada entrega, nunca por un cron dedicado — auto-sanador. Sin track → crea el primero si `audioDay >= 1` y el catálogo tiene ese día. Con track: si el último es de hoy, no-op; si es de un día anterior, resuelve PENDIENTE→PERDIDO (si corresponde) y aplica el bloqueo de exactamente un día tras un PERDIDO antes de desbloquear el siguiente.
- **`RegistroEspiritu.entregar()` — CORREGIDO Y CONFIRMADO** (ver §10.2): una entrega fuera de plazo queda PENDIENTE, no lanza excepción.
- **Fuera de alcance, decisión explícita (CLAUDE.MD §0.6):** el espejo hacia "Pastilla Renacer" (repo viejo: una entrega a tiempo completa además ese hábito JOURNALING, `completePastillaRenacerTrack`, best-effort/no-fatal) **no se replicó**. No estaba en el encargo explícito de esta pasada, y hacerlo bien en Spring exige aislar esa escritura en su propia transacción (`REQUIRES_NEW`) para no arriesgar marcar la transacción principal como rollback-only ante un fallo en un hábito ajeno — se prefirió no improvisarlo. Pregunta abierta para una pasada futura.
- **Catálogo de audios (`audios_espiritu`) detrás de `AudioCatalogPort`**, con `NoOpAudioCatalogAdapter` (mismo patrón que `NoOpAlmacenamientoAdapter`/`NoOpEvidenciaValidacionIAAdapter`) — sin credenciales de Drive, el catálogo devuelve vacío y el aprendiz queda "al día", nunca en error.

### 10.2 Corrección confirmada por el dueño del producto (2026-08-26)

**La regla "una entrega fuera de plazo queda PENDIENTE, no se rechaza" fue confirmada explícitamente por el dueño del producto (Luis/Ricardo) el 2026-08-26** — no es una inferencia de este agente ni un simple "portado del repo viejo": es la corrección de un bug real que tenía `RegistroEspiritu.entregar()` (tiraba `IllegalStateException` fuera de plazo). Con esta confirmación, la regla queda **cerrada** — no hay que volver a preguntarla. `RegistroEspirituTest` la fija con casos explícitos (a tiempo, justo en el límite, tarde con sobreescritura, ya entregado).

### 10.3 Qué se construyó

```
habits/
├── domain/model/espiritu/
│   └── RegistroEspiritu.java                    entregar() corregido (§10.2), devuelve boolean aTiempo
├── application/
│   ├── ports/in/espiritu/
│   │   ├── ConsultarEstadoEspirituUseCase.java   vista día-por-día (LOCKED/CURRENT/SUBMITTED/MISSED)
│   │   └── EntregarResumenEspirituUseCase.java
│   ├── ports/out/espiritu/
│   │   ├── LoadRegistroEspirituPort.java         + ultimoDe/todosDe (nuevos, para el state machine y la vista)
│   │   ├── SaveRegistroEspirituPort.java         (ya existía)
│   │   └── AudioCatalogPort.java                 nuevo — puerto al catálogo (Drive en producción)
│   └── services/EspirituService.java             state machine lazy + vista + entrega, exclusivo TRAINEE (mismo criterio que radar, RD-3)
└── infrastructure/adapter/
    ├── in/rest/espiritu/EspirituController.java  GET /api/v1/spirit-audio/status, POST /api/v1/spirit-audio/submit (rutas literales del contrato viejo, D-36)
    ├── out/espiritu/NoOpAudioCatalogAdapter.java  placeholder — sin Drive en esta pasada
    └── out/persistence/espiritu/                 JPA entity + mapper + Spring Data repo + adapter para `registros_espiritu`
```

Tests: `RegistroEspirituTest` (dominio, sin Spring) y `EspirituServiceTest` (Mockito) — seguridad (suspendido, rol≠TRAINEE), desbloqueo del primer audio, no-desbloqueo si `diaPrograma - 7 < 1`, entrega a tiempo, entrega a un día no desbloqueado (404), vista LOCKED para un día del catálogo sin track.

### 10.4 Preguntas abiertas de este agregado

1. **Espejo hacia "Pastilla Renacer"** (ver §10.1) — no implementado, decisión explícita.
2. **Sin scheduler dedicado de avance** — fiel al repo viejo (`ensureAdvanced` se llama en cada lectura/escritura, "self-healing", sin cron de por medio). Si el negocio pide notificar el desbloqueo antes de que el aprendiz abra la app, hace falta un scheduler nuevo.

---

## 11. Hueco #10 — catálogo resuelto en `GET /habit-tracks/today` — completado 2026-08-26

**Problema:** el endpoint devolvía solo el registro crudo (`RegistroHabitoResponse`) — la pantalla principal de la app necesita, por cada registro, el título del hábito, su tipo, la guía vigente y el horario resuelto (preferencia del participante si existe, si no el del catálogo).

**Solución — una proyección de lectura, nunca N+1** (`TracksDelDiaProyeccionService`, `ConsultarTracksDelDiaConCatalogoUseCase`): delega la autorización en `ConsultarTracksDelDiaUseCase` (ya probada, sin repetir esa lógica) y agrega UN batch por tabla — `LoadHabitoPort.porIds`, `LoadHorarioHabitoPort.porHabitos`, `LoadPreferenciaHorarioPort.porParticipanteYHabitos` (los tres nuevos, agregados a los puertos existentes) y `LoadGuiaHabitoPort.porHabitos` (agregado nuevo — `guia/` era solo-dominio, D-H5; esta pasada le suma persistencia completa: JPA entity, mapper, Spring Data repo, adapter, todo de solo lectura). La resolución de "horario vigente" y "guía vigente" reutiliza los mismos criterios que `RegistroService.resolverVentana` (`HorarioHabito.aplicaEnDia`) y agrega `GuiaHabito.aplicaEnDia` (nuevo método de dominio, mismo criterio) — la guía vigente es la de mayor `diaInicio` que todavía aplica.

**Contrato preservado:** `RegistroHabitoConCatalogoResponse` tiene los mismos campos que `RegistroHabitoResponse` (mismo nombre, misma forma) más `tituloHabito`, `tipoHabito`, `guia` (objeto anidado o `null`), `horaDisparo`, `horaLimite`. `POST /{id}/complete` sigue devolviendo el DTO viejo sin cambios — no se tocó.

Tests: `TracksDelDiaProyeccionServiceTest` — sin registros no consulta ningún puerto de catálogo, resuelve título/tipo/guía/horario con una sola consulta por tabla (verificado con `verify(..., times(1))`), la guía más específica (mayor `diaInicio` que aplica) gana sobre una más genérica, la preferencia del participante gana al horario del catálogo cuando está seteada.

**Qué quedó fuera:** el `GuiaResumen` expuesto es un subconjunto (mantra + qué hacer/cómo hacerlo) — no incluye ciencia/renaser/alquimia/resultados ni los adjuntos de `adjuntos_guia` (sin persistencia propia todavía). Ampliar es agregar campos al record, no un cambio de arquitectura.

---

## 12. Hueco #12 — personalización de hábitos — completado 2026-08-26

Cuatro piezas nuevas, todas construidas sobre lo que ya existía en `preferencia/` (dominio completo, sin caso de uso — D-H7) o sobre tablas del baseline sin ningún dominio todavía. **Todas simplificadas deliberadamente respecto al repo viejo** — el detalle de qué se dejó afuera está en cada subsección; nada está escondido.

### 12.1 `habit-preferences` — editar el horario personal de un hábito

`PATCH /api/v1/habit-preferences/{habitId}` (ruta literal del contrato viejo, D-36), `EditarPreferenciaHorarioUseCase`/`PreferenciaHorarioService`. Traducción de `updateHabitPreference` (repo viejo, `service.ts:2021`, `limits.ts`):

- **`FREE_SCHEDULE_EDITS_UNTIL_DAY = 7`**: hasta el día 7 de programa (o el propio `Habito.diaLimiteEdicionLibre` si es mayor — columna `dia_limite_edicion_libre` del baseline, **ahora mapeada** en `Habito`/`HabitoJpaEntity`/`HabitoPersistenceMapper`, cerrando parte de D-H5), los cambios inmediatos son ilimitados.
- **`WEEKLY_SCHEDULE_EDIT_LIMIT = 3`**: pasada la semana libre, hasta 3 hábitos DISTINTOS reacomodables por semana de programa. Se cuenta con una bitácora append-only nueva (`historial_cambios_horario`, tabla ya existente en el baseline sin dueño hasta ahora — puerto `HistorialCambioHorarioPort`) — reeditar un hábito ya tocado esa semana no gasta cupo nuevo.
- **"No se improvisa el día"**: si la ventana de HOY de ese hábito ya arrancó (la hora de disparo vigente ya pasó), el cambio nunca se rechaza — queda PROGRAMADO para mañana vía `CambioHorarioPendiente` (dominio ya existía, **ahora con persistencia**: `LoadCambioHorarioPendientePort`/`SaveCambioHorarioPendientePort`, tabla `cambios_horario_pendientes`). Un cambio programado NO gasta cupo (igual que el repo viejo).

**Simplificación deliberada:** la cuota reportada en la respuesta no excluye a OTROS hábitos que hoy tengan su propia ventana extendida (el repo viejo lo hace vía `readExtendedFreeWindows`, una lista de todos los hábitos con ventana propia) — solo se resuelve la ventana extendida DEL hábito que se está editando. El campo `horaDisparo`/`horaLimite` de la respuesta es lo que se acaba de guardar (inmediato o programado), no un "vigente hoy" recalculado aparte — simplificación respecto al repo viejo, que separaba ambas cosas. `isProgramCompleted` (post-programa, cupo libre para siempre) no existe todavía en `ConsultarProgresoParticipanteHabitsPort` — no implementado.

Tests: `PreferenciaHorarioServiceTest` — orden de horas inválido, semana libre sin consultar historial, cupo agotado lanza, reeditar un hábito ya tocado no gasta cupo nuevo, ventana de hoy ya arrancada difiere el cambio a mañana.

### 12.2 `weekly-habit-days` — elegir el día de la semana

`PUT /api/v1/weekly-habit-days/{habitId}` (ruta literal, D-36), `ElegirDiaSemanalUseCase`/`EleccionDiaSemanalService`. Nuevo agregado `domain/model/eleccion/EleccionDiaSemanal` + persistencia completa sobre `dias_semanales_habito`. Traducción de `chooseWeeklyHabitDay` (`weeklyChoice.ts`): ancla de semana **MONDAY** (lunes de calendario, no semana de programa — el repo viejo migró de PROGRAM a MONDAY el 2026-08-07 por un bug de desalineamiento, documentado en `weeklyChoice.ts`; se portó directamente la versión vigente, MONDAY), elegir un día reemplaza (borra+guarda) cualquier elección previa de esa semana, solo se puede elegir hoy o un día futuro de la semana vigente, día 0 de programa rechazado (vista previa).

**Fuera de alcance, ambas decisiones explícitas y documentadas ya en D-H2/D-H3 antes de esta pasada:**
- No valida el desbloqueo escalonado (`isHabitUnlocked`) — D-H2, el algoritmo de staggering no está portado.
- No genera el track de HOY cuando se elige hoy (`seedTrackForChosenDay` del repo viejo) — y, más de fondo, **`GenerarTracksDelDiaUseCase` sigue sin filtrar por elección semanal (D-H3 sigue abierto)**: un hábito de elección semanal hoy genera track TODOS los días que su horario aplique, no solo el elegido. Esta pasada agrega la persistencia de la elección pero no cierra D-H3 — la plumbing (`LoadEleccionDiaSemanalPort`) ya existe para cuando se aborde.

Tests: `EleccionDiaSemanalTest` (dominio) y `EleccionDiaSemanalServiceTest` — día 0 rechazado, hábito sin elección semanal rechazado, fecha fuera de la semana rechazada, elección reemplaza la anterior.

### 12.3 Renombre de hábitos (`GREEN_JUICE`/`WARM_LEMON_WATER`)

`PUT`/`DELETE /api/v1/habits/{habitId}/rename` (ruta literal, D-36), `RenombrarHabitoUseCase`/`QuitarRenombreHabitoUseCase`/`RenombreHabitoService`. Nuevo agregado `domain/model/renombre/RenombreHabito` + persistencia sobre `renombres_habito`. Traducción literal de `renameableKeys.ts`: **solo** `GREEN_JUICE`/`WARM_LEMON_WATER` (las dos bebidas), emparejado por `claveSistema` — nunca por título (el título es editable desde el panel y emparejar por texto haría desaparecer la función en silencio, mismo motivo documentado ya para Pastilla Renacer/Día sin celular). Solo hasta el **día 0** de programa (`RENAME_ALLOWED_UNTIL_PROGRAM_DAY = 0`) — después el hábito ya generó tracks con su nombre. Topes de longitud: título 60, motivo 200 (`RENAME_TITLE_MAX_LENGTH`/`RENAME_REASON_MAX_LENGTH`, validados en el dominio, no solo en el DTO — CLAUDE.MD §5.4.3 nivel 3).

**Simplificación:** la respuesta no incluye `originalTitle` (el título del catálogo) — el repo viejo lo agregaba para que la app ofrezca "volver al original"; acá el cliente ya lo tiene del catálogo. El nombre resuelto (custom si existe, si no el del catálogo) **no se aplica todavía** en `RegistroHabitoConCatalogoResponse` (§11) — `resolveHabitTitle` del repo viejo no se portó a la proyección del hueco #10; sigue mostrando el título del catálogo sin importar el renombre. Pendiente de una pasada futura que cruce ambos huecos.

Tests: `RenombreHabitoTest` (dominio — título/motivo vacíos o demasiado largos, actualizar) y `RenombreHabitoServiceTest` — hábito no renombrable rechazado, fuera de la ventana del día 0 rechazado, renombra en el día 0, quitar borra el renombre, suspendido rechazado.

### 12.4 `habit-unlocks` — plan de desbloqueo escalonado (SOLO LECTURA)

`GET /api/v1/habit-unlocks` (ruta literal, D-36), `ConsultarDesbloqueosHabitoUseCase`/`DesbloqueoHabitoService`. Nuevo agregado `domain/model/desbloqueo/DesbloqueoHabito` (solo `rehydrate`, sin caso de uso de escritura) + persistencia de solo lectura sobre `desbloqueos_habito`.

**Alcance deliberadamente reducido:** el repo viejo (`habitStaggering.ts`/`staggerService.ts`, ~1470 líneas, D-H2) reparte el catálogo completo en lotes (días 1/3/5/7) y deja que el aprendiz reacomode lotes — ese ALGORITMO sigue sin portarse, es la deuda más grande documentada del módulo. Esta pasada solo expone, en lectura, lo que ya esté guardado en `desbloqueos_habito` — sin escribir ni reorganizar nada. `enabled` es una aproximación: `true` si hay al menos un desbloqueo guardado, en vez del campo propio del perfil (`staggeredHabitsAt`) que este backend no tiene todavía.

Tests: `DesbloqueoHabitoServiceTest` — suspendido rechazado, sin desbloqueos → `enabled=false`, con desbloqueos → `enabled=true` y los items.

---

## 13. Hueco #13 — evidencia al cerrar la racha "Día sin celular" — completado 2026-08-26

**Problema:** `POST /habit-tracks/phone-free/complete` no aceptaba evidencia — el repo viejo (`phoneFree.ts`, `completePhoneFreeRun`) exige SIEMPRE evidencia (foto/audio/nota) para cerrar, precisamente porque "marcar tareas a mano no existe" y el catálogo pide "declaración + foto del cuaderno".

**Solución:** `CerrarRachaCommand` ahora exige `tipoEvidencia` (FOTO/AUDIO/TEXTO — espejo de `TipoEvidencia` de `evidence.api`, mismo enum que usa el resto del módulo) + `bucket`/`rutaStorage` (no-TEXTO) o `contenidoTexto` (TEXTO) + `timestampExif` opcional. `RachaService.cerrar()` registra la evidencia vía `evidence.api.RegistrarEvidenciaPort`, colgada del **registro en que ARRANCÓ la racha** (no del de hoy — es el que se completa y el que lleva los puntos, mismo criterio que el repo viejo). Se agrega también `SolicitarUrlAdjuntoRachaUseCase` (`POST /habit-tracks/phone-free/evidence/upload-url`) siguiendo el mismo patrón upload-url→PUT→confirm que ya usan `rocks`/`onboarding`/`habit-tracks/{id}/evidence` — bucket propio `dia-sin-celular/`, mismo bucket físico `renaser-files` que el resto del backend.

**No se tocó** `sesiones_bloqueo.evidencia_salida_bucket/ruta` — esas columnas son de un concepto distinto (evidencia de SALIDA TEMPRANA del Santuario/`SesionBloqueo`, no del cierre de la racha "Día sin celular"/`RachaSinCelular`) y no correspondían a este hueco.

Tests: `RachaServiceTest` extendido — `cerrarConEvidencia` ahora exige evidencia en todos los casos, nuevo test que verifica el `RegistrarEvidenciaComando` exacto (destino = el registro que arrancó la racha, no el de hoy), nuevos tests de `solicitarUrl` (URL firmada para la racha activa, sin racha activa lanza).

---

## 14. Bonus — "Bitácora Nocturna" (Diario Nocturno) — completado 2026-08-26

Fuera del encargo A-D, agregado a pedido explícito del coordinador tras confirmar con el agente de `rocks` que el "Diario Nocturno" del hueco #16 **es el mismo concepto que `habits.domain.model.diario.EntradaDiario`** (dominio, puertos y persistencia ya completos, incluido `TipoEntradaDiario.BITACORA_NOCTURNA`) — solo faltaba el caso de uso de escritura y el controller.

`GET`/`PUT /api/v1/journal/today` (ruta literal del contrato viejo, R-05/R-06 — vivía en la feature `rocks` de Next.js, pero `entradas_diario` es tabla de `habits` en este backend, así que el endpoint se construyó acá). `EscribirBitacoraNocturnaUseCase`/`ConsultarBitacoraNocturnaUseCase`/`BitacoraNocturnaService`: upsert por (participante, fecha de HOY en su timezone, `BITACORA_NOCTURNA`) — escribir dos veces el mismo día pisa el contenido anterior, no acumula. Al menos uno de `textContent` o `audioBucket`+`audioPath` es obligatorio (mismo `refine` que `UpsertJournalEntryInput` del repo viejo). El audio ya debe estar subido vía `AlmacenamientoPort` (patrón upload-url del backend, no una URL directa como en el repo viejo — P-03, nunca persistir una URL).

**Fuera de alcance:** `transcripcion` (el dominio tiene el campo pero ningún mutador público — se llena por un proceso de transcripción async que no existe en este backend todavía). Sin endpoint de upload-url dedicado para el audio de esta bitácora — reutiliza el patrón, pero no se construyó un endpoint propio (`/api/v1/journal/today/evidence/upload-url` o similar) en esta pasada; el cliente tendría que usar otro camino existente para conseguir bucket/ruta antes de llamar al PUT.

Tests: `BitacoraNocturnaServiceTest` — suspendido rechazado, comando sin texto ni audio rechazado en el constructor, primera escritura crea la entrada, escribir de nuevo el mismo día pisa el contenido.

Esto desbloquea, del lado de `habits`, el Espejo Sombra de `rag` (`habits.api.EntradaDiarioFinder`/`EntradaDiarioSummary`, D-50) — ese consumidor ya podía leer entradas existentes, pero hasta ahora nada las escribía.

---

## 15. Estado / checklist DoD — actualizado 2026-08-26

- [x] `domain/` de los agregados nuevos (`eleccion/`, `renombre/`, `desbloqueo/`) plano, sin imports de Spring/JPA/Jackson
- [x] Tests unitarios de dominio para las reglas nuevas (`RegistroEspirituTest`, `EleccionDiaSemanalTest`, `RenombreHabitoTest`)
- [x] Comandos self-validating para los casos de uso nuevos
- [x] Controllers tontos: sin repositorios, sin `@Transactional`, sin `if` de negocio
- [x] DTOs de salida como proyección explícita (`RegistroHabitoConCatalogoResponse` agrega campos sin romper el contrato viejo)
- [x] Pruebas de seguridad §0.3 en los servicios nuevos: suspendido → `NotAuthorizedException` en los 6 servicios nuevos/tocados (`EspirituService`, `TracksDelDiaProyeccionService` vía `ConsultarTracksDelDiaUseCase`, `PreferenciaHorarioService`, `EleccionDiaSemanalService`, `RenombreHabitoService`, `DesbloqueoHabitoService`, `BitacoraNocturnaService`); rol≠TRAINEE → `NotAuthorizedException` en `EspirituService` (mismo criterio que `radar`, RD-3)
- [ ] `ArchitectureTest`/`./mvnw clean test` — no ejecutados por este agente (regla del encargo: nunca correr Maven), el supervisor los corre
- [x] Avance documentado en este archivo (§10-14), con honestidad explícita de lo que quedó afuera en cada sección
- [x] Bitácora de errores (`docs/BITACORA_ERRORES.md`) — revisada; no se encontró ningún error/bug de configuración real durante esta pasada (correcciones de diseño ya documentadas en §10.2, con fuente y fecha)

**Preguntas abiertas nuevas de esta pasada (CLAUDE.MD §0.6), consolidadas:**

1. Espejo Espíritu→Pastilla Renacer, no implementado (§10.4).
2. `isProgramCompleted` (post-programa, cupo de edición libre para siempre) no existe en `ConsultarProgresoParticipanteHabitsPort` (§12.1).
3. Ventanas de edición extendida de OTROS hábitos no se excluyen de la cuota reportada — solo la del hábito que se edita (§12.1).
4. D-H3 sigue abierto: `GenerarTracksDelDiaUseCase` no filtra por elección semanal (§12.2, ya documentado antes de esta pasada, sigue sin cerrar).
5. Renombre de hábito no se refleja todavía en la proyección del hueco #10 (§12.3).
6. Staggering (D-H2) sigue sin portarse — `habit-unlocks` es de solo lectura (§12.4).
7. Sin endpoint de upload-url dedicado para el audio de la Bitácora Nocturna (§14).

---

## 16. `CompletarClaseDiariaHabitoUseCase` — cierre del gap #23 con `academy` (DAILY_CLASS) — completado 2026-08-26

**Encargo:** el gap #23 de `docs/PLAN_INTEGRACION_FRONTEND.md` (`POST /classroom/clase-diaria`
completar) estaba diferido a coordinar con `habits` — `academy` no sabía si el hábito
`DAILY_CLASS` ya existía en este backend ni cómo se relacionaba con "ver la clase de hoy".

**Investigación contra el repo viejo, con cita exacta** (`RenaserBack`,
`clase-diaria/service.ts:55-90` + `habits/service.ts:1542-1811`): completar la Clase Diaria son
**dos escrituras relacionadas, no un solo concepto**.

```ts
// Se guarda antes de completar el hábito. Ambos pasos son idempotentes: si una red se corta
// entre ellos, repetir la acción termina el segundo sin duplicar progreso ni puntos.
const completed = await habitService.completeTodayDailyClassWithSummary(userId, resumen)
if (!completed.success) return completed
await repo.markLeccionCompleted(userId, clase.leccionId)
```

`DAILY_CLASS` **sí es** un hábito de catálogo real (`claveSistema`), verificado contra
`DAILY_CLASS_SYSTEM_KEY`/`isDailyClassHabit` (`habits/service.ts:1542-1546`) — y ya estaba citado
como ejemplo en el javadoc de `SelectorHabito`/`PoliticaHabito` de este mismo backend, aunque
todavía no tenía ningún caso de uso que lo resolviera por su clave.

**Qué se construyó, sin tocar BD (D-40):**

- `LoadHabitoPort.porClaveSistema(String)` — nuevo lookup por `clave_sistema` (columna ya existía,
  `UNIQUE`, sin usar todavía desde ningún puerto). Implementado en `HabitoPersistenceAdapter` +
  `SpringDataHabitoRepository.findByClaveSistema`.
- `habits.api.CompletarClaseDiariaHabitoUseCase` (nuevo, `@NamedInterface`) — localiza el registro
  de HOY del hábito `DAILY_CLASS` sin exponer su `RegistroHabitoId` al llamador, y delega el
  cálculo de puntos/ventana/evento de dominio en el `CompletarRegistroUseCase` ya existente (no se
  duplica esa lógica — sigue viviendo en un solo lugar, `RegistroService`, tal como exige el
  javadoc de `PoliticaHabito`). Idempotente: si el registro de hoy ya está `COMPLETADO`, devuelve
  el resultado ya otorgado sin volver a completar. Implementado en `ClaseDiariaHabitoService`.
- **Deliberadamente NO es un "completar cualquier hábito por clave" genérico.** El repo viejo
  cierra el bypass de evidencia de `completeDailyClassTrack` a `DAILY_CLASS` únicamente — la ruta
  pública de completar hábitos (H-02) no puede activarlo para ningún otro. Generalizar este puerto
  abriría, para cualquier módulo futuro que importe `habits.api`, un atajo para completar hábitos
  con evidencia obligatoria sin subirla.
- Defensa propia dentro de `ClaseDiariaHabitoService`: aunque hoy el único llamador (`academy`) ya
  valida cuenta suspendida antes de llegar acá (resolviendo la Clase Diaria del día), el puerto es
  público y no confía en que todo futuro llamador repita ese chequeo.

**Del lado de `academy`:** `CompletarClaseDiariaUseCase` (nuevo puerto), implementado en
`ClaseDiariaService` junto a la resolución de lectura ya existente. Revalida en servidor que la
lección pedida coincide con la Clase Diaria real de hoy (403 si no), delega en el puerto de
`habits` y recién después llama al `CompletarLeccionUseCase` de `academy` ya existente — mismo
orden que el repo viejo. Envuelto en una única `@Transactional` (CLAUDE.MD §9.1): ambos pasos ya
son idempotentes por separado, así que la transacción local no cambia el comportamiento ante un
reintento, solo lo hace atómico (ventaja real del monolito frente al repo viejo, que dependía de
la idempotencia de cada paso porque no tenía una transacción compartida). Nuevo endpoint
`POST /api/v1/classroom/clase-diaria`. Detalle completo en `docs/MODULO_ACADEMY.md` §6 (AC-13).

**Tests (unitarios, Mockito, sin Spring/Postgres):** `ClaseDiariaHabitoServiceTest` — camino feliz
delegando en `CompletarRegistroUseCase`, idempotencia si ya estaba `COMPLETADO`, suspendido →
`NotAuthorizedException`, sin hábito `DAILY_CLASS` en catálogo → `NoSuchElementException`, sin
registro de hoy → `NoSuchElementException`, resumen corto rechazado en el constructor del comando.
Del lado de `academy`, `ClaseDiariaServiceTest` extendido con los mismos casos más "lección que no
es la de hoy" (403).

**Qué quedó fuera:** no se tocó `RegistroService`/la matriz de políticas (`PoliticaHabito`) — no
hizo falta una `PoliticaHabito` propia para `DAILY_CLASS` porque el nuevo puerto no pasa por la
ruta genérica de completar (H-02); si el día de mañana se necesita bloquear el completado directo
de `DAILY_CLASS` vía H-02, ese es un cambio aparte, en `RegistroPoliticasHabito`.

---

## 17. Panel admin de catálogo — hueco #11 — completado 2026-08-26 (adjuntos ENLACE solamente)

**Encargo:** `docs/PLAN_INTEGRACION_FRONTEND.md` #11 — `habitsAdmin.ts` (cliente ya escrito,
`C:\renaserPlayStore\src\services\habitsAdmin.ts`) esperaba un backend admin de catálogo, horarios
y guías/adjuntos que todavía no existía. La capa de LECTURA de estos tres agregados ya existía
(usada por `TracksDelDiaProyeccionService`); lo que faltaba era la capa de ESCRITURA
administrativa.

### 17.1 Invariantes de `Habito` protegidos deliberadamente (y por qué)

Antes de exponer un endpoint de edición se revisaron los invariantes de `Habito` contra el resto
del código que ya lo consume:

- **`claveSistema` — inmutable, ni antes ni ahora tenía setter.** `SelectorHabito.PorClaveSistema`
  (usado hoy por `PoliticaHabito`/`RegistroPoliticasHabito`, ej. `PASTILLA_RENACER`, `DAILY_CLASS`
  del §16) resuelve políticas por esta clave — cambiarla en caliente deja una política ya indexada
  apuntando a un hábito distinto sin que nada lo note. El DTO de creación/edición del panel admin
  (`CreateHabitRequest`/`UpdateHabitRequest`) ni siquiera tiene este campo — el panel admin no crea
  claves funcionales, esas las siembra la migración baseline.
- **`tipo` — se hizo explícitamente inmutable post-creación, aunque el tipo TS del cliente
  (`UpdateHabitInput`) técnicamente permite mandar `habitType` en la edición.** `SelectorHabito.PorTipo`
  usa `tipo` para reglas estructurales (`BLOQUEO` = Santuario, ver §0.4) y el significado de un
  `registro_habito` ya generado depende de qué tipo tenía el hábito al crearse (Santuario vs
  completar directo). Reclasificar un `CHECKBOX` en `BLOQUEO` después de que ya existan tracks es
  un cambio de regla de negocio no confirmado por nadie — se decidió **no implementarlo**:
  `UpdateHabitRequest.habitType` se acepta en el JSON (para no romper si el cliente lo manda) pero
  se ignora al construir el comando, documentado explícitamente en el javadoc del DTO y de
  `Habito.actualizarDetalles`.
- **`ambito`/`participanteId` — identidad del agregado, nunca se reasignan.** No expuesto en ningún
  DTO de escritura.
- **Categoría/exigencia de evidencia/opcionalidad — sí editables**, no tienen ningún consumidor que
  dependa de que permanezcan fijas tras la creación.

Se agregó `Habito.actualizarDetalles(DetallesHabito, Instant)` (value object nuevo,
`domain/model/habito/DetallesHabito.java`) y `Habito.activar(Instant)` (faltaba el inverso de
`desactivar`). Un test de dominio (`HabitoTest.tipoYClaveSistemaNoTienenNingunMetodoMutadorPublico`)
documenta la garantía: ningún método de instancia toca `tipo` ni `claveSistema` fuera del
constructor privado.

### 17.2 Qué se construyó

```
habitoadmin/    (nuevo) — ConsultarCatalogoAdminUseCase, Crear/Actualizar/CambiarActivo/EliminarHabitoUseCase
                HabitoAdminService, HabitoAdminGuard (compartido por los 3 servicios de este hueco)
                HabitoAdminController — /api/v1/admin/habits (GET, POST, POST /{id}, POST /{id}/toggle, DELETE /{id})
horarioadmin/   (nuevo) — Consultar/Crear/Actualizar/EliminarHorarioHabitoUseCase, HorarioHabitoAdminService
                HorarioHabitoAdminController — /api/v1/admin/habits/{id}/schedules, /schedules/{id}
guiaadmin/      (nuevo) — Consultar/Upsert/EliminarGuiaHabitoUseCase, Crear/EliminarAdjuntoGuiaUseCase
                GuiaHabitoAdminService, GuiaHabitoAdminController + GuiaAdjuntoAdminController
```

- **`HorarioHabito` ganó `actualizarRango` y `diaInicio` pasó a mutable** (antes `final`) — el panel
  admin puede correr el rango de días de un horario existente; no hay ningún consumidor que
  dependiera de que `diaInicio` fuera inmutable (a diferencia de `tipo`/`claveSistema` en `Habito`).
- **`GuiaHabito` ganó `actualizarContenidoCompleto`, `cerrarEn` y `establecerDiaFin`** (value object
  nuevo `ContenidoGuia`). El endpoint de guías (`UpsertGuiaHabitoUseCase`) es un upsert real por
  `(habitoId, diaInicio)` — mismo `UNIQUE` de `guias_habito` — con `closePrevious`: si viene en
  `true`, cierra en `diaInicio-1` la guía abierta (`diaFin IS NULL`) más reciente del hábito, salvo
  que sea la misma que se está editando (test `upsertConClosePreviousNoSeCierraASiMisma`).
- **`AdjuntoGuia` NO tenía NINGUNA persistencia** (solo dominio, documentado en §1 de este archivo
  como pendiente) — se construyó el stack completo (`AdjuntoGuiaJpaEntity`, mapper con los dos
  enums espejo `SeccionGuiaJpa`/`TipoMedioGuiaJpa`, `SpringDataAdjuntoGuiaRepository`,
  `AdjuntoGuiaPersistenceAdapter`) más los puertos `Load/SaveAdjuntoGuiaPort`.
- **Traducción de vocabulario en la frontera REST** (CLAUDE.MD §5.4.1, a mano): el dominio habla
  español (`CALIFICACION`, `BLOQUEO`, `CUERPO`/`MENTE`/`CONSCIENCIA`/`ESPIRITU`, `OPCIONAL`/
  `OBLIGATORIA`, `QUE_HACER`/`COMO_HACERLO`/..., `ENLACE`/`IMAGEN`/`AUDIO`) y `habitsAdmin.ts` habla
  inglés (`RATING`/`BLOCKING`, `BODY`/`MIND`/`CONSCIENCE`/`SPIRIT`, `OPTIONAL`/`REQUIRED`,
  `WHAT_TO_DO`/`HOW_TO_DO`/..., `LINK`/`IMAGE`/`AUDIO`) — el mapeo de categorías está tomado literal
  del comentario de la propia siembra SQL (`V1__baseline_renaser.sql`: "BODY→CUERPO, MIND→MENTE,
  CONSCIENCE→CONSCIENCIA, SPIRIT→ESPIRITU"), no inventado.
- **`PATCH` real para horarios** (`ActualizarHorarioHabitoUseCase`): `UpdateScheduleInput` del
  cliente distingue "clave ausente" (no tocar) de "clave presente en `null`" (limpiar, ej. volver el
  horario abierto quitando `endDay`) — un record normal no puede distinguir eso, así que
  `HorarioHabitoAdminController.actualizar` lee el body como `JsonNode` (única excepción al mapeo a
  mano con DTOs tipados de esta pasada) vía `PartialUpdateScheduleRequest.from(JsonNode)`.
- **`updateHabit` (catálogo) SÍ es reemplazo completo, no merge parcial** — decisión de alcance
  documentada en el javadoc de `UpdateHabitRequest`: aunque el tipo TS del cliente marca todo
  opcional, se asumió que el panel siempre reenvía el formulario completo ya hidratado (patrón
  estándar de una pantalla de edición), evitando construir la misma capa de "ausente vs. null" que
  sí hizo falta para horarios.
- **Borrado de hábito (`DELETE /api/v1/admin/habits/{id}`) es físico, no lógico** — `SaveHabitoPort.eliminar`
  deja que la FK `RESTRICT` de `registros_habito.habito_id` (P-02, "el catálogo no arrastra
  historial") frene el DELETE con una violación de integridad si el hábito ya tiene tracks; el
  `GlobalExceptionHandler` ya traducía `DataIntegrityViolationException` a 409 (nada nuevo que
  escribir ahí). Un hábito sin historial se borra de verdad; uno con historial se da de baja lógica
  con el toggle (`activo=false`).

### 17.3 Autorización

Mismo criterio que `CategoriaMuroService` (`community`)/`EvidenciaService`/etc.: ADMIN/ALCHEMIST,
`SUSPENDED` → 403. Extraído a una clase compartida DENTRO del módulo (`HabitoAdminGuard`, package-
private) en vez de duplicado 3 veces en los 3 servicios nuevos — la duplicación *entre* módulos es
deliberada (CLAUDE.MD §4.3), pero duplicar dentro del mismo módulo no protege ningún límite de
Modulith. El actor viaja por `X-Actor-Id` (patrón temporal de todo este backend mientras B-2 sigue
bloqueante) — el cliente ya escrito (`habitsAdmin.ts`) manda `Authorization: Bearer` (JWT de
Supabase), que este backend todavía no valida; es la misma deuda conocida de siempre, no una nueva.

### 17.4 Qué quedó explícitamente sin cubrir

1. **Subida de archivo para adjuntos IMAGEN/AUDIO** (`POST .../guide-attachments/upload`,
   multipart). Solo se construyó el adjunto tipo ENLACE (`CrearAdjuntoGuiaEnlaceUseCase`) — cubre
   el caso de uso real documentado en el propio cliente ("el vídeo se queda como enlace de YouTube,
   decisión de Luis 2026-08-11"). Subir un archivo de verdad necesita (a) manejo de multipart —
   sin precedente en este backend, ningún controller usa `MultipartFile` todavía — y (b) un puerto
   de almacenamiento que reciba bytes directos: `shared.application.ports.out.AlmacenamientoPort`
   solo firma URLs de subida/lectura (patrón presign-and-PUT), no acepta un archivo. Construir esto
   a medias bajo presión de tiempo, sin poder correr los tests contra un bucket real, parecía peor
   que dejarlo documentado.
2. **CRUD de `categorias_habito`/`iconos_habito`** (las tablas-catálogo, P-23) no se tocó — el
   encargo pedía catálogo de hábitos/horarios/guías, no administrar las categorías en sí. El mapeo
   de categorías (`HabitCategoryDto`) asume las 4 claves ya sembradas; si se agrega una 5ª
   categoría a la tabla sin actualizar este enum, la respuesta de listado explota con
   `IllegalStateException` (409) — riesgo bajo porque el propio cliente (`HabitCategory` en
   `habitsAdmin.ts`) tampoco soporta una 5ª categoría hoy.
3. **`icono_clave` no es editable desde el panel** — `AdminHabit`/`CreateHabitInput` del cliente no
   tienen ningún campo de ícono; se deja `null` en la creación, igual que antes de esta pasada.

### 17.5 Tests

- Dominio (sin Spring): `HabitoTest`, `HorarioHabitoTest`, `GuiaHabitoTest` — invariantes de rango,
  el invariante protegido de `tipo`/`claveSistema`, `cerrarEn`/`establecerDiaFin`.
- Aplicación (Mockito): `HabitoAdminServiceTest`, `HorarioHabitoAdminServiceTest`,
  `GuiaHabitoAdminServiceTest` — mentor rechazado, admin suspendido rechazado (test de autorización
  negativa exigido por CLAUDE.MD §0.3), 404 sobre ids inexistentes, semántica de `closePrevious`
  (incluida "no se cierra a sí misma"), semántica PATCH de horarios (omitido vs. limpiar).
- Integración (Testcontainers): `GuiaHabitoPersistenceAdapterTest`, `AdjuntoGuiaPersistenceAdapterTest`
  (adaptador enteramente nuevo) — guardar/recuperar, `masRecienteAbierta`, cascada de borrado
  `guía → adjuntos`.
- No se agregó el "test de reflexión que falle si un endpoint no declara `@RequiresPermission` ni
  `@PublicEndpoint`" que pide CLAUDE.MD §0.3: ese mecanismo de anotaciones **todavía no existe en
  este backend** (confirmado — ver `docs/BITACORA_ERRORES.md`/comentario en
  `AccountRequestController`, "NO todavía con @RequiresPermission + AccessGuard"); el criterio real
  usado hoy en todo el código (`requireAdmin` a mano en el servicio) es el que se replicó aquí,
  igual que en `community`/`evidence`/`rag`/`support`.

---

## 18. Verdugo y hábitos personales — hueco #18, mitad de `rocks` — investigado 2026-08-26

**Encargo:** confirmar si `rocks.DestinoVerdugo` acepta hábitos personales por FK, y si falta un
valor para "Código Renaser". No se tocó ningún archivo de `rocks` — los dos hallazgos fueron de
solo lectura.

- **Hábitos personales: YA funciona, verificado contra la base real, sin cambio de código.**
  `registros_habito.habito_id` referencia la tabla unificada `habitos` (P-12 — SISTEMA y PERSONAL
  conviven en la misma tabla, discriminados por `ambito`). `rocks.VerificarDestinoVerdugoPersistenceAdapter.registroHabitoPerteneceA`
  hace `SELECT COUNT(*) FROM registros_habito WHERE id = ? AND participante_id = ?` — nunca
  joinea `habitos.ambito`. Un evento Verdugo contra el track de un hábito PERSONAL pasa exactamente
  igual que contra uno SISTEMA. Se agregó `VerificarDestinoVerdugoPersistenceAdapterTest` (nuevo,
  con Testcontainers) en `rocks` para dejarlo probado, no solo leído.
- **Código Renaser (`RADAR`): sigue sin valor en `DestinoVerdugo`, y no se agregó.** Dos bloqueantes
  reales, no uno solo de esquema: (1) `eventos_verdugo` tiene el CHECK `verdugo_un_destino` atado a
  exactamente dos columnas (`registro_habito_id`/`roca_diaria_id`) — agregar un tercer destino
  necesita una columna nueva, y la BD está congelada (D-40) sin una razón de negocio confirmada
  todavía; (2) más de fondo, **Verdugo dispara sobre un plazo vencido y `registros_radar` no tiene
  ningún plazo**: es un log append-only (`RegistroRadar`, sin `diaFin` ni estado pendiente), y el
  propio §8.0 de este documento ya dejó citado que el gating de horario del Código Renaser "es UX
  del cliente, nunca una restricción de servidor ni de base". Sin una regla de negocio confirmada
  de qué cuenta como "Código Renaser vencido", implementar el valor del enum sería inventar la
  regla que CLAUDE.MD prohíbe. Queda como pregunta abierta para quien confirme esa regla, documentado
  también en `docs/PLAN_INTEGRACION_FRONTEND.md` #18.
