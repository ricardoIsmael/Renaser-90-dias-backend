# Módulo `points` — estado, reglas extraídas, decisiones y preguntas abiertas

**Fecha:** 2026-08-24
**Ola:** 1 (dominio puro, sin IA) — depende solo de `users`
**Documentos hermanos:** `CLAUDE.MD` (cómo), `docs/MODULOS_A_AVANZAR.md` (qué y en qué orden), `docs/PLAN_DE_MODULOS.md §1` (semilla de este módulo)

---

## 1. Estado actual

🔄 **Construido, sin ejecutar `./mvnw clean test` todavía** (regla del encargo: el agente constructor no corre Maven — lo corre el supervisor).

- [x] `domain/` plano por agregado (`puntaje/`, `ajuste/`, `ranking/`), sin imports de Spring/JPA/Jackson
- [x] Tests unitarios de dominio (`PuntajeParticipanteTest`, `AjustePuntosTest`)
- [x] Casos de uso con comando self-validating
- [x] Controllers tontos (X-Actor-Id, patrón D-29 de `users`)
- [x] DTOs de salida como proyección explícita
- [x] Testcontainers IT para los 3 adaptadores de persistencia + el de ranking
- [ ] `ArchitectureTest` — no ejecutado por este agente (regla del encargo)
- [ ] `./mvnw clean test` — no ejecutado por este agente (regla del encargo)
- [x] Pruebas de seguridad del único endpoint protegido (`POST /points/adjustments`): rol sin permiso → `NotAuthorizedException`(→403), actor `SUSPENDED` → ídem (a nivel de servicio, con IT de persistencia probando el filtro contra Postgres real)
- [ ] Test de reflexión `@RequiresPermission`/`@PublicEndpoint` — **no aplica todavía**: ese mecanismo no existe en ningún módulo del repo (bloqueado por B-5/R-2 en `users`, ver §4 más abajo)
- [x] Avance documentado acá; sin cambios a `docs/MODULOS_A_AVANZAR.md` (no hay decisión nueva de alcance global, todo lo de abajo es específico de `points`)
- [ ] Contrato verificado contra `docs/API_CONTRACT.md` — **desviación documentada explícitamente en §5**, no es 1:1 con el viejo `GET /api/v1/ranking`

---

## 2. Paso 0 — reglas exactas extraídas del código viejo (D-33)

Repo Next.js clonado en el scratchpad de la sesión. Todas las referencias son archivo:línea contra ese clon.

### 2.1 Puntos por hábito (`src/features/habits/points.ts`)

> **Nota de alcance:** esta escala de puntos por hábito la CONSUME `points` (vía `AjustarPuntosUseCase`/`AjustarPuntosPort`) pero no la CALCULA — calcularla necesita `deadline`/`deliveredAt` de un `HabitTrack`, que es dominio de `habits` (Ola 2, todavía no existe). Se documenta acá para que quien construya `habits` la traduzca literal, sin reinterpretarla.

- `HABIT_FULL_POINTS = 10` (points.ts:38) — hábito completado dentro de su ventana (hora inicio→hora fin): **+10**.
- `GRACE_WINDOW_MINUTES = 10` (points.ts:41): tras la hora fin hay 10 minutos de gracia.
- `GRACE_POINT_STEP_MINUTES = 2` (points.ts:44): cada 2 minutos de retraso dentro de la gracia, **-1** punto.
- `GRACE_MIN_POINTS = 5` (points.ts:47): piso de la gracia, nunca menos de 5 puntos dentro de esa ventana.
- Fórmula exacta de la gracia (points.ts:112-117): `max(5, 10 − floor(minutosTarde / 2))`. A los 10 min exactos: `10 − floor(10/2) = 5`.
- `EXTENSION_WINDOW_HOURS = 3` (points.ts:59): DEFAULT de la extensión post-gracia — cada hábito puede tener su propio `evidenceExtensionHours` (columna de `Habit`, no de `points`).
- `EXTENSION_POINTS = 3` (points.ts:62): puntos FIJOS dentro de la extensión, no escalan con cuánto dura la ventana.
- Pasado gracia+extensión: `EXPIRED`, **0 puntos**, hábito bloqueado (no acepta más evidencia).
- `STREAK_BONUS_EVERY_DAYS = 3`, `STREAK_BONUS_POINTS = 5` (points.ts:64-65): **esta parte SÍ vive en `points`** — ver `PuntajeParticipante.RACHA_BONO_CADA_DIAS`/`RACHA_BONO_PUNTOS` y `correspondeBonoDeRacha()`, con test unitario cubriendo el múltiplo de 3 (no solo el primer trío) y que un día imperfecto nunca dispara bono, aunque la racha caiga en un múltiplo de 3 tras cortarse.

### 2.2 Ledger + piso 0 (`src/features/habits/repository.ts:1149-1180`, `adjustLeaguePoints`)

- `UPDATE trainee_profiles SET league_points = GREATEST(league_points + delta, 0) ... RETURNING before, after` (repository.ts:1155-1161): el piso en 0 se aplica **atómicamente en la misma sentencia** que mueve el saldo — traducido a dominio puro en `PuntajeParticipante.registrarAjuste()` (sin SQL, testeable sin Postgres).
- `appliedDelta = after − before`, `balanceAfter = after` (repository.ts:1172-1173): se guardan AMBOS — lo pedido (`delta`) y lo aplicado de verdad (`deltaAplicado`) — porque el piso es destructivo (una vez tocado fondo no se sabe cuánto más se habría restado). Traducido 1:1 a `ResultadoAjuste`/`AjustePuntos`.
- **Gap real que P-06 vino a cerrar** (y que este módulo corrige, regla dura del encargo): en el código viejo el UPDATE del saldo y el INSERT del ajuste (`leaguePointAdjustment.create`) son dos sentencias separadas, con el INSERT en un `try/catch` **best-effort** ("si falla, se loguea y se sigue" — repository.ts:1166-1179). Es decir, en el viejo sistema el saldo podía moverse SIN que quedara asiento en el ledger. En `points` (Java), `PuntajeService.aplicarAjusteSobre()` guarda el agregado (saldo) y el `AjustePuntos` (asiento) dentro del MISMO método `@Transactional` — si el INSERT del asiento falla, la transacción entera hace rollback y el saldo tampoco se mueve. Esto es una mejora intencional sobre el comportamiento viejo, no una reinterpretación de una regla de negocio.
- `awardTrackPoints` (repository.ts:167-184): guarda `points<=0 → no aplica nada` y usa `awardedPoints=0` como guarda de idempotencia (un track solo premia una vez). Esa idempotencia es responsabilidad de **`habits`** (columna `habit_tracks.awarded_points`, tabla que no existe en `points`) — `points` no la re-implementa; solo documenta que quien llame a `AjustarPuntosPort.ajustar(...)` debe garantizar el llamado una sola vez por evento (ver pregunta abierta Q-4).

### 2.3 Motivos de ajuste — `LeaguePointReason` (`prisma/schema.prisma:2884-2943`)

Los 12 valores, verificados 1:1 contra el enum Postgres `motivo_puntos` del baseline (`V1__baseline_renaser.sql:91`) — mapeo completo en `MotivoPuntos` (dominio, inglés) ↔ `MotivoPuntosJpa` (persistencia, español), ver `AjustePuntosPersistenceMapper`:

| Java (`MotivoPuntos`) | Postgres (`motivo_puntos`) | Prisma viejo |
|---|---|---|
| `HABIT_COMPLETED` | `HABITO_COMPLETADO` | `HABIT_COMPLETED` |
| `HABIT_EXTENDED` | `HABITO_EXTENDIDO` | `HABIT_EXTENDED` |
| `MISSED_HABIT` | `HABITO_PERDIDO` | `MISSED_HABIT` |
| `LATE_HABIT` | `HABITO_TARDE` | `LATE_HABIT` |
| `STREAK_BONUS` | `BONO_RACHA` | `STREAK_BONUS` |
| `SANCTUARY_BREAK` | `SANTUARIO_ROTO` | `SANCTUARY_BREAK` |
| `INVALID_EVIDENCE` | `EVIDENCIA_INVALIDA` | `INVALID_EVIDENCE` |
| `INVALID_EVIDENCE_REVOKED` | `EVIDENCIA_INVALIDA_REVERTIDA` | `INVALID_EVIDENCE_REVOKED` |
| `PHONE_FREE_WEEK_MISSED` | `SEMANA_SIN_CELULAR_PERDIDA` | `PHONE_FREE_WEEK_MISSED` |
| `ROCK_COMPLETED` | `ROCA_COMPLETADA` | `ROCK_COMPLETED` |
| `ROCK_EXTENDED` | `ROCA_EXTENDIDA` | `ROCK_EXTENDED` |
| `MANUAL_ADJUSTMENT` | `AJUSTE_MANUAL` | `MANUAL_ADJUSTMENT` |

### 2.4 Coherencia diaria y racha — cron `coherence-score` (`src/app/api/cron/coherence-score/route.ts`)

- **Fórmula Ley VI** (route.ts:12-20, `src/lib/coherence.ts:45-100`): `score = AVG` sobre los últimos 7 días UTC cerrados de `(hábitos completados + rocas completadas) / (hábitos totales + rocas totales) × 100`; el día se redondea a entero PRIMERO (`Math.round`), y LUEGO se promedian los enteros (doble redondeo intencional). Días con denominador 0 se EXCLUYEN del promedio (no cuentan como 0%). Ventana sin ningún día calificable → **100** ("recién empezó", no se castiga). Un hábito opcional sin completar no entra ni al numerador ni al denominador (coherence.ts:61-68) — completarlo nunca puede bajar el % ni pasarlo de 100.
  - **Esta fórmula NO está implementada en `points`**: necesita `habit_tracks`/`daily_rocks`, tablas de `habits`/`rocks` (Ola 2, no existen). Ver pregunta abierta Q-2.
- **Fase 1b — bono de racha de hábitos** (route.ts:121-133): usa los MISMOS `habitTracks` ya traídos para la Fase 1 (sin query extra) — `isPerfectHabitDay = total>0 && completed===total` (solo hábitos, sin rocas). `newStreak = isPerfectHabitDay ? streak+1 : 0`; `newLongest = max(longest, newStreak)`; si `isPerfectHabitDay && streakBonusDue(newStreak)` → `adjustLeaguePoints(+5, 'STREAK_BONUS')`. Traducido 1:1 a `PuntajeParticipante.actualizarRachaTrasDia()` + el ajuste vía `MotivoPuntos.STREAK_BONUS` en `PuntajeService.registrar()`.
- **`historial_coherencia` es tabla NUEVA** (P-18 de la auditoría de BD): el viejo `coherenceScore` se sobreescribía sin dejar rastro. `points` sí la escribe (`SaveHistorialCoherenciaPort.upsert`), aunque hoy nadie más la llame todavía.

### 2.5 Ranking — 4 pestañas (`src/features/community/service.ts:653-772`, `getRanking`)

`GET /api/v1/ranking` del repo viejo arma, para la célula del que llama:

| Pestaña de la respuesta vieja | Fuente | Alcance |
|---|---|---|
| `celulas` | `repo.findCohortRanking(cohortId)` | Ranking de CÉLULAS dentro del cohort, por `coherenceScoreGroup` (promedio de coherencia de sus miembros) → tabla `ranking_celulas` del baseline nuevo |
| `miCelula` | `repo.findCellMembersRanking(cellId)` | Miembros de MI célula, ordenados por `coherenceScore` individual |
| `miCelulaPorHabitos` | `repo.findCellMembersRankingByPoints(cellId)` | Miembros de MI célula, ordenados por `leaguePoints` |
| `miCelulaGeneral` | `repo.findGeneralRankingScores()` (función SQL `general_ranking_scores()`, ver §2.6) | **TODOS** los aprendices ACTIVE de la plataforma (no filtrado por célula), por el score mixto 50/35/15 |

### 2.6 `general_ranking_scores()` (`prisma/migrations/general_ranking_scores_function.sql`)

Score = `round((0.5·habitosPct + 0.35·rocasPct + 0.15·cursosPct) × 10) / 10`, con:
- `habitosPct`/`rocasPct`: mismo criterio de ventana de 7 días y doble redondeo que Ley VI (§2.4), pero calculado SOBRE hábitos y rocas por separado (no combinados como en Ley VI) — sin días calificables → 100.
- `cursosPct`: progreso de lecciones sobre cursos accesibles (gate por publicado/acceso/rol/día de desbloqueo) — sin cursos accesibles → 100 (no castiga a quien no tiene nada que cursar).
- Necesita `habit_tracks`, `daily_rocks`, `cursos`, `lecciones`, `leccion_progreso` — **ninguna existe en `points`** (Ola 2 `habits`/`rocks`, Ola 3 `academy`). No implementable hoy.

---

## 3. Qué se construyó

```
points/
├── package-info.java                          @ApplicationModule("Points")
├── api/                                        único paquete público
│   ├── package-info.java                       @NamedInterface("api")
│   ├── AjustarPuntosPort.java                  lo llamarán habits/rocks (Ola 2)
│   └── ResumenAjustePuntos.java
├── domain/model/
│   ├── puntaje/PuntajeParticipante.java        agregado raíz: saldo+coherencia+racha, piso 0
│   ├── ajuste/AjustePuntos.java (record)        línea de ledger, inmutable
│   ├── ajuste/MotivoPuntos.java                 12 valores (§2.3)
│   ├── ajuste/ResultadoAjuste.java (record)
│   ├── ranking/PosicionRanking.java (record)
│   └── ranking/TipoRanking.java                 GENERAL/COHORT/CELL/LEAGUE
├── application/
│   ├── ports/in/puntaje/                        Ajustar(Manualmente)/Consultar/RegistrarCoherenciaDiaria
│   ├── ports/in/ranking/                        Consultar/GenerarSnapshot
│   ├── ports/out/{puntaje,ajuste,ranking}/       Load/Save por agregado + VerificarActorAdministrativoPort
│   └── services/{PuntajeService, RankingService}.java
└── infrastructure/adapter/
    ├── in/rest/puntaje/PuntajeController.java    GET /api/v1/points/{id}, POST /api/v1/points/adjustments
    ├── in/rest/ranking/RankingController.java    GET /api/v1/ranking/{tipo}
    ├── in/scheduler/SnapshotRankingScheduler.java @Scheduled 05:05 UTC, LEAGUE+CELL
    ├── in/scheduler/PointsSchedulingConfig.java   @EnableScheduling (ver §6, decisión sobre alcance)
    └── out/persistence/{puntaje,ajuste,ranking}/  JpaEntity + mapper a mano + adapter, patrón `users`
```

Tests: `domain` (unit puro, sin Spring), `application/services` (unit con Mockito, sin Postgres), `infrastructure/.../persistence` (IT con Testcontainers, patrón `AccountRequestPersistenceAdapterTest`).

---

## 4. Decisiones propias de este módulo

- **D-P1 — `participanteId` reusa `shared.domain.UserId`, no un tipo nuevo.** `puntajes_participante.participante_id` es, transitivamente, el mismo UUID que `usuarios.id` (`participante_id → participantes_programa.usuario_id → usuarios.id`, ambas FK 1:1). Mismo criterio que `AccountRequest.supabaseUserId` en `users`.
- **D-P2 — `AjustePuntos`/`PosicionRanking` son `record`, no clases Lombok fluent.** A diferencia de `User`/`AccountRequest`/`MentorProfile` (entidades con identidad y ciclo de vida), un asiento del ledger o una fila de ranking ya generada es un HECHO inmutable de un instante — inmutabilidad total vía `record` es más honesta que exponer setters que nunca se usan (CLAUDE.MD §5.4.7).
- **D-P3 — filtro de rol del ranking (solo APRENDIZ) y verificación del actor administrativo, ambos vía SQL nativo contra `renaser.usuarios`, NO vía `users.api.UserSummaryFinder`.** Motivo técnico, no de estilo: `UserSummary.role()`/`.status()` devuelven `com.renaser.os.users.domain.model.user.UserRole`/`UserStatus` — tipos INTERNOS de `users`, fuera de su `@NamedInterface("api")`. Referenciarlos desde `points` (aunque sea solo para leer `.name()`) crea una dependencia de bytecode hacia un paquete no autorizado por Spring Modulith, que `ArchitectureTest.modulesDoNotLeakInternals` puede rechazar — riesgo que este agente no podía verificar compilando (regla del encargo: no correr Maven). El propio encargo autoriza explícitamente el join a `usuarios` para el filtro de rol del ranking; se extendió el mismo criterio a `VerificarActorAdministrativoPort` (ajuste manual) y a la resolución de `fullName` en las lecturas de ranking, para no depender de `UserSummaryFinder` en ningún punto y evitar el riesgo por completo. **Costo:** cero acoplamiento Java a `users`, pero un cambio de nombre de columna/tabla en `usuarios` no lo detecta el compilador — solo Testcontainers.
- **D-P4 — `@EnableScheduling` vive en `points` (`PointsSchedulingConfig`), no en `RenaserOsApplication`.** `RenaserOsApplication.java` está fuera del alcance de este agente (regla del encargo: solo `points/**` y este doc). Es un flag global de Spring (habilita `@Scheduled` en TODO el contexto, no solo en `points`), así que funciona igual declarado acá — pero el lugar natural es la clase principal. Anotado para que se mueva ahí cuando alguien con permiso sobre ese archivo lo haga.
- **D-P5 — lazy-init de `PuntajeParticipante`.** Ni `AjustarPuntosUseCase` ni `ConsultarPuntajeUseCase` exigen que exista una fila previa en `puntajes_participante`: si no existe, se usa `PuntajeParticipante.inicial()` (100/100/0/0, los DEFAULT de la columna) como punto de partida. `ConsultarPuntajeUseCase` NO la persiste (una consulta no tiene efectos secundarios); `AjustarPuntosUseCase`/`RegistrarCoherenciaDiariaUseCase` sí, porque de todos modos van a guardar el resultado del ajuste.
- **D-P6 — `AjustarPuntosUseCase` (uso inter-módulo) no lleva actor; `AjustarPuntosManualmenteUseCase` (REST, humano) sí, y exige `VerificarActorAdministrativoPort`.** El motivo `MANUAL_ADJUSTMENT` se fuerza server-side en el segundo caso — el cliente no puede pedir otro motivo (mismo blindaje que `SubmitAccountRequestCommand` sin campo `role`, CLAUDE.MD §5.3.3).
- **D-P7 — `GenerarSnapshotRankingUseCase`/`RankingService` implementan solo LEAGUE y CELL.** GENERAL y COHORT lanzan `UnsupportedOperationException` con mensaje explícito. No se inventó una fórmula parcial para no violar CLAUDE.MD §0.6 ("no inventar reglas de negocio"). Ver preguntas Q-1/Q-3.
- **D-P8 — sin `CoherenciaDiariaScheduler`.** `RegistrarCoherenciaDiariaUseCase` necesita `valor`/`diaHabitosPerfecto` que solo `habits`/`rocks` pueden calcular (no existen). Un scheduler sin datos que iterar sería una clase vacía o, peor, lógica inventada — se documenta como pendiente en vez de crear una clase de relleno. Ver Q-2.
- **D-P9 — `GET /api/v1/ranking/{tipo}` NO replica el contrato viejo `GET /api/v1/ranking`** (una respuesta con las 4 pestañas agrupadas por la célula del caller). Ese armado necesita datos de célula/cohorte (tabla `celulas`, dueño futuro `community`, Ola 3) que `points` no tiene. Se expone en cambio el snapshot plano por tipo. Ver Q-1.

---

## 5. Desviación de contrato (CLAUDE.MD DoD: "contrato verificado contra API_CONTRACT.md")

**No se cumple 1:1 a propósito, documentado acá en vez de forzar algo a medias:**

- Viejo: `GET /api/v1/ranking` (sin parámetros, resuelve la célula del caller del JWT) → `{cohortName, celulas, miCelula, miCelulaPorHabitos, miCelulaGeneral, actualizadoEn}`.
- Nuevo (hoy): `GET /api/v1/ranking/{tipo}?fecha=YYYY-MM-DD` (tipo = GENERAL|COHORT|CELL|LEAGUE) → lista plana `[{participanteId, fullName, posicion, puntaje}]`. LEAGUE y CELL devuelven datos reales (si el scheduler ya corrió ese día); GENERAL y COHORT devuelven `[]` siempre (nunca se genera su snapshot).
- La app móvil (`RenaserPlayStoreCopy/src/features/community/services/community.ts:113-136`) consume el contrato viejo — **este endpoint nuevo todavía no le sirve tal cual**. Cuando `community` (dueño de célula/cohorte) exista, hay que decidir si el contrato final agrupa por célula en el backend (server-side, como el viejo) o si la app arma la agrupación cliente-side a partir de varias llamadas a este endpoint. Es una decisión de producto, no técnica — no se resuelve acá.

---

## 6. Preguntas abiertas (CLAUDE.MD §0.6: no se inventan, se preguntan)

- **Q-1 — ¿Qué es `tipo_ranking.COHORTE` exactamente?** El baseline (`V1__baseline_renaser.sql:92`) lo declara como uno de los 4 valores de `ranking_aprendices` (ranking POR PARTICIPANTE). El código viejo no tiene un ranking de participantes scoped-por-cohort — lo que sí tiene es un ranking de CÉLULAS dentro de un cohort (`ranking_celulas`, tabla aparte, no implementada en este módulo — ver Q-1b). ¿`COHORTE` es (a) participantes de un mismo cohort rankeados entre sí por algún score, (b) un alias pensado para lo que hoy es `ranking_celulas`, o (c) todavía sin definir? Sin esa respuesta, `points` no puede generar ese snapshot sin inventar la fórmula.
  - **Q-1b:** `ranking_celulas` (fecha, celula_id, posición, puntaje_grupo — celdas dentro de un cohort) no tiene persistencia en este módulo: sus datos de agrupación (`celulas`, `cohortes`) están planificados como dueño `community` (Ola 3, `docs/PLAN_DE_MODULOS.md` §"community"). Si se prefiere que `points` sea igual el dueño de esa tabla (varias tablas de ranking pueden convivir en el mismo módulo aunque agrupen por conceptos ajenos), avisar explícitamente — hoy no se tocó por prudencia (regla de alcance: no tocar lo que otro módulo va a necesitar decidir).
- **Q-2 — ¿Quién calcula la fórmula Ley VI (coherencia) y el `isPerfectHabitDay`, y cómo se lo pasa a `points`?** `RegistrarCoherenciaDiariaUseCase` está listo para recibirlo (`valor`, `diaHabitosPerfecto`), pero nadie lo llama todavía. Cuando `habits`/`rocks` existan (Ola 2), ¿el llamado es síncrono (un caso de uso orquestador en un módulo nuevo, o en el propio scheduler de `habits`) o vía evento de dominio (`HabitoCompletadoEvent`/similar, con un listener en `points`)? Es una decisión de integración entre módulos, no de `points` solo.
- **Q-3 — ¿La fórmula GENERAL (50% hábitos + 35% rocas + 15% cursos) se mantiene igual cuando `habits`/`rocks`/`academy` existan, o se revisa?** Se documentó tal cual estaba (§2.6) para no perder el criterio, pero nadie confirmó que siga vigente para la migración.
- **Q-4 — Idempotencia de `AjustarPuntosPort.ajustar(...)` cuando lo llamen `habits`/`rocks`.** El viejo `awardTrackPoints` garantizaba "una vez por track" con la columna `habit_tracks.awarded_points=0` como guarda (repository.ts:167-184) — esa guarda vive en la tabla de `habits`, no en `points`. `points` aplica el delta que le pidan, sin preguntar si ya se lo pidieron antes. ¿Cada módulo llamador es responsable de no llamar dos veces (igual que hoy), o `points` debería exponer algo tipo "aplicar como máximo una vez por (participante, motivo, referencia-externa)"? No se agregó una solución no pedida — se deja para cuando `habits` exista y se vea el caso real.
- **Q-5 — Compensación cuando `AjustarPuntosPort` se llama desde OTRO módulo y esa transacción hace rollback después.** Hoy la transacción de `points` es local a su propio método `@Transactional`; si `habits` llama a `points.api.AjustarPuntosPort.ajustar(...)` y DESPUÉS algo en `habits` falla, ¿el ajuste de puntos debe revertirse? Con Spring, si ambos módulos comparten el mismo `PlatformTransactionManager` (mismo datasource, que es el caso en este monolito) y la llamada ocurre dentro de la transacción del llamador (propagación `REQUIRED`, default), un rollback del llamador SÍ revierte también el ajuste de puntos — pero esto no está verificado con un test de integración cross-módulo (no existe todavía quien lo llame). Anotado para cuando `habits` se construya.
- **Q-6 — ¿El mentor asignado debería poder ver el puntaje de su aprendiz?** `PuntajeService.consultar()` (`GET /api/v1/points/{participanteId}`) solo permite al propio participante o a un `administrativo activo` (`VerificarActorAdministrativoPort`, ADMIN/ALCHEMIST) — un MENTOR, incluso el asignado al aprendiz, recibe 403 hoy. Detectado en la auditoría E2E de la ola de módulos (curl real contra la app), no en la construcción de este módulo. A diferencia de `rocks` (RK-7, documentado explícitamente: "sin vista de mentor, mismo criterio que el repo viejo"), acá no hay ninguna decisión registrada — puede ser el mismo criterio del repo viejo (autoservicio puro) o un vacío real. No se agrega `requireMentorScope` sin confirmar: si la respuesta es "sí debería verlo", además hay que decidir si `points` resuelve el mentor asignado vía `users.api.ParticipacionProgramaFinder` (mismo patrón que `EspejoSombraService`/D-47) directamente, o si `PuntajeService` necesita un puerto nuevo para no acoplarse a un tipo de `users` fuera de lo que ya usa (ver D-P3, que evita `UserSummaryFinder` a propósito).

---

## 7. Bloqueante real de integración (no de compilación/tests)

**`puntajes_participante.participante_id` es FK a `participantes_programa.usuario_id`.** La tabla `participantes_programa` existe en el baseline SQL pero **todavía no tiene entidad JPA en ningún módulo** — el agregado `participante` de `users` está pendiente (`docs/PLAN_DE_MODULOS.md §0`: *"agregado participante/ (recomendación: dentro de users — ParticipantePrograma como 4º agregado...)"*, no construido a la fecha de este documento).

**Consecuencia concreta:** en producción, `PuntajeService.ajustar(...)`/`.registrar(...)` van a fallar con una violación de FK de Postgres para cualquier participante que no tenga ya una fila en `participantes_programa` — es decir, **`points` no puede operar de punta a punta hasta que ese agregado exista en `users`**. Los tests de este módulo (unit e IT) no lo notan porque:
- los tests unitarios (dominio, `application/services`) mockean los puertos, nunca tocan Postgres;
- los tests IT (Testcontainers) insertan la fila prerrequisito a mano vía `JdbcTemplate` (ver comentario en `PuntajeParticipantePersistenceAdapterTest`), exactamente para dejar esta brecha visible y documentada en vez de escondida.

No se creó el agregado `participante` en `users` — está fuera del alcance de archivos permitido a este agente (`points/**` y este doc únicamente).

---

## 8. Qué quedó explícitamente sin cubrir

- `ranking_celulas` (ranking de células dentro de un cohort) — sin persistencia, ver Q-1b.
- Snapshots GENERAL/COHORT — sin implementación real, lanzan `UnsupportedOperationException` documentada.
- `CoherenciaDiariaScheduler` — no existe, ver D-P8/Q-2.
- Endpoint `GET /api/v1/ranking` con la forma vieja (4 pestañas agrupadas por célula) — ver §5.
- `@RequiresPermission`/`@PublicEndpoint` + test de reflexión — el mecanismo no existe en el repo todavía (B-5/R-2 de `users`).
- Migración Flyway propia — no hizo falta: las 5 tablas de `points` (`puntajes_participante`, `ajustes_puntos_liga`, `historial_coherencia`, `ranking_aprendices`, `ranking_celulas`) ya están en `V1__baseline_renaser.sql`, que este módulo tiene prohibido tocar.
- Batch lookup de nombres en `RankingPersistenceAdapter.porTipoYFecha` — hoy es un `SELECT` por fila (documentado en el código como optimización futura si el volumen crece; el repo viejo documentaba ~30 aprendices activos).
