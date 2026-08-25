# Módulo `rocks` — Rocas Maestras/Semanales/Diarias, Modo Verdugo

**Fecha:** 2026-08-24
**Ola:** 2 (núcleo del producto) — depende de `users` (vía su propia query nativa, ver §2) y de `points` (síncrono, vía `points.api.AjustarPuntosPort`)
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/PLAN_DE_MODULOS.md` §"4. `rocks`" (semilla) · `docs/MODULO_POINTS.md`, `docs/MODULO_PHASECONTRACTS.md`, `docs/MODULO_SUPPORT.md` (ejemplo vivo, patrones replicados acá)

---

## 0. Estado

🔄 **Construido, sin ejecutar `./mvnw clean test` todavía** (regla del encargo: este agente no corre Maven — lo corre el supervisor).

---

## 1. Paso 0 — reglas extraídas del código viejo (D-33)

Repo Next.js clonado en `C:\Users\Usuario\Documents\Backend90dias\RenaserBack`. Archivos leídos completos, citas archivo:línea.

### 1.1 Dos features viejas se fusionan en `rocks`

El encargo mencionaba "Verdugo `enforcer/*`" como si fuera parte de `rocks/service.ts`. **No lo es.** Son dos features Next.js separadas que la BD nueva unifica en un solo módulo Java:

- `src/features/rocks/{service,repository,schema,week,plazos}.ts` (R-01..R-06, W-01..W-04): planificación y ejecución de rocas.
- `src/features/enforcer/{service,repository,schema}.ts` + `src/app/api/v1/enforcer-events/route.ts`: el Modo Verdugo real (registro de reacción del aprendiz cuando se vence un plazo). El comentario "Modo Verdugo (Ley VI)" dentro de `rocks/service.ts:12-13` es un FALSO AMIGO — ahí "Verdugo" nombra la validación EXIF ±15min de fotos, no el `EnforcerEvent`. Son dos cosas distintas con el mismo apodo.

La tabla nueva `eventos_verdugo` (baseline `V1__baseline_renaser.sql:735-751`) es, sin ambigüedad, el equivalente de `EnforcerEvent` (mismos 4 valores en `resultado_verdugo`: `COMPLETADO/POSTERGADO/POSPUESTO_30/IGNORADO` = `COMPLETED/POSTPONED/SNOOZED/IGNORED` del viejo `EnforcerOutcome`).

### 1.2 Plazos de planificación (`plazos.ts`, completo)

- **Semanal:** en plazo domingo 12:00 → lunes 09:00 (hora local del aprendiz); a destiempo cualquier otro momento, con 2h de margen para rectificar. `plazos.ts:34-83`.
- **Diaria:** en plazo 18:00 → 23:59; a destiempo cualquier otro momento, 30 min de margen. `plazos.ts:43-51`. **No implementado el margen de 30 min para diarias** — ver §6 (no hay `EditarRocaDiaria` en el encargo ni en el repo viejo: las diarias no se editan, solo se completan una vez).
- **2026-08-07: planificar fuera de ventana YA NO se rechaza** (`plazos.ts:4-11`) — se admite a destiempo, cuenta como fallado en el semáforo (no implementado en `rocks`, es cálculo de `points`) y el margen de edición se acorta. Portado literal en `VentanaPlanificacionSemanal`/`VentanaPlanificacionDiaria`.
- **El margen tardío nunca cruza la medianoche local** (`plazos.ts:111-127`, `limiteTardioMs`) — portado en `VentanaPlanificacionSemanal.limiteTardio`.

### 1.3 Semanas de programa (`week.ts`, completo)

Semanas lunes-domingo, semana 1 flexible (corta si `fechaInicio` no cae lunes). Portado 1:1 en `SemanaPrograma` (`primerDomingoDesde`, `numeroSemanaParaFecha`). El `+1` al planificar semanal SOLO aplica el domingo (`service.ts:1070-1085`, con el bug histórico documentado ahí: un `+1` incondicional rompía a quien planificaba tarde) — portado en `RocaSemanalService.numeroSemanaAPlanificar`.

### 1.4 Escala de puntos (`rocks/service.ts:458-503`, `aplicarPremioDeRoca`)

Espejo DELIBERADO de la escala de hábitos (`docs/MODULO_POINTS.md` §2.1, `resolveHabitAward`): 10 pts a tiempo · gracia de 10 min con `max(5, 10-floor(minutosTarde/2))` · extensión de 3h con 3 pts fijos · después, 0 (EXPIRADO). Sin `horaFin`, siempre 10 (sin plazo no hay tardanza). Portado en `EscalaPuntosRoca`, ver RK-4 sobre la ventana de extensión fija.

### 1.5 Ley IV (bloqueo Pareto) y Ley VI (EXIF), `rocks/service.ts:227-456`

- **Ley IV:** AMARILLA/ROJA bloqueadas mientras la VERDE del mismo eje/día no tenga evidencia (`service.ts:278-284`, `363-373`). Portado en `RocaDiaria.bloqueadaPorPareto` (pura) + `RocaDiariaService.requireNoBloqueadaPorPareto`/`estaBloqueada`.
- **Ley VI (EXIF):** diferencia entre `timestampExif` y el instante de subida > 15 min → rechazo (`service.ts:375-388`). Portado en `RocaDiariaService.requireExifDentroDeMargen`. **Desviación de código de error:** el repo viejo devolvía 422; este backend no tiene un handler de 422 en `shared/web/GlobalExceptionHandler` (ningún módulo lo tiene todavía) — se usa `IllegalArgumentException` → 400, consistente con el resto del backend nuevo.

### 1.6 R-04 (planificación diaria) y W-02 (planificación semanal)

Reglas de posición/color/ejes portadas literal desde `schema.ts:69-124` (semanal, 3 ejes obligatorios, 1 roca por eje) y `schema.ts:41-52` (diaria: color↔posición 1→VERDE/2→AMARILLA/3→ROJA, posiciones contiguas desde 1 por eje, 1-3 rocas por eje). `NO_WEEKLY_ROCK` cuando falta el plan semanal del eje/semana (`service.ts:621-627`) — portado igual.

### 1.7 Verdugo (`enforcer/service.ts`, completo)

`IGNORADO` nunca lo manda el cliente — lo asigna un barrido server-side (`service.ts:25-27`, comentario explícito). El repo viejo **no tiene** el cron que hace ese barrido en ningún archivo de `src/app/api/cron/` (verificado, no existe `enforcer` ahí) — es una promesa de diseño no implementada en el viejo backend. La tabla nueva SÍ tiene el índice pensado para eso (`verdugo_pendiente_idx ... WHERE resultado IS NULL`, baseline:749). Se construyó el barrido (`VerdugoIgnoradoScheduler`, 23:55 UTC) para cumplir esa promesa, aunque bajo el flujo de creación portado (cliente siempre crea el evento YA resuelto) no debería encontrar nada pendiente — ver RK-6.

### 1.8 Lo que NO se portó (fuera de alcance, con motivo)

- **Bitácora Nocturna / `JournalEntry`** (R-05/R-06 de `rocks/service.ts`): pertenece al agregado `diario/` de `habits` (`docs/PLAN_DE_MODULOS.md`), no a `rocks`. No se tocó.
- **Dashboard agregado W-01** (`getDashboard`: `rhythmStatus`, `weekProgress`, `weekGrid`, navegador de semanas admin): es agregación de presentación, no regla de negocio nueva — se puede construir después sin tocar dominio. Ver §6.
- **`planningBlocked` (Ley II)** y **`coherenceScore`** en la respuesta de "hoy": dependen de datos que no son de `rocks` (`computeRockCoherenceScore` ya vive conceptualmente en `points`, ver `docs/MODULO_POINTS.md` Q-2/Q-3). No se inventó.

---

## 2. Progreso del participante — copia propia del patrón de `phasecontracts`

`rocks` NO importa nada de `users` en código — mismo motivo documentado en `docs/MODULO_PHASECONTRACTS.md` §2.1 (`users.api.UserSummary` expone `UserRole`/`UserStatus`, tipos internos fuera de `@NamedInterface`, y referenciarlos rompe `ArchitectureTest.modulesDoNotLeakInternals`).

`ConsultarProgresoParticipanteRocksPort` + `ConsultarProgresoParticipanteRocksPersistenceAdapter` (`rocks/infrastructure/adapter/out/persistence/participante/`) son una copia PROPIA — más rica que la de `phasecontracts`: además de `diaPrograma`/`rol`/`suspendido`, trae `fechaInicio` y `timezone` (ambas columnas ya existen en `participantes_programa`, baseline:264-278), imprescindibles para las ventanas de planificación con zona horaria real del participante (`java.time.ZoneId`, sin los cálculos manuales que el repo viejo necesitaba en JS).

---

## 3. Qué se construyó

```
rocks/
├── package-info.java                          @ApplicationModule("Rocks")
├── api/
│   ├── package-info.java                       @NamedInterface("api")
│   └── RocaCompletadaEvent.java                 publicado al completar una roca diaria
├── domain/model/
│   ├── rocamaestra/    RocaMaestra (record), RocaMaestraId, EjeObjetivo
│   ├── rocasemanal/    RocaSemanal, RocaSemanalId, AccionCritica, EstadoPlazo,
│   │                   VentanaPlanificacionSemanal, SemanaPrograma
│   ├── rocadiaria/     RocaDiaria, RocaDiariaId, ColorPareto, VentanaPlanificacionDiaria,
│   │                   EscalaPuntosRoca, FasePremio, ResultadoPremio
│   └── verdugo/        EventoVerdugo, EventoVerdugoId, DestinoVerdugo, ResultadoVerdugo
├── application/
│   ├── ports/in/{rocamaestra,rocasemanal,rocadiaria,verdugo}/   9 casos de uso
│   ├── ports/out/{rocamaestra,rocasemanal,rocadiaria,verdugo,participante}/
│   │                   Load/Save por agregado + RegistrarEvidenciaRocaPort (RK-2) +
│   │                   ConsultarProgresoParticipanteRocksPort (§2)
│   └── services/       RocaMaestraService, RocaSemanalService, RocaDiariaService, VerdugoService
└── infrastructure/adapter/
    ├── in/rest/{rocamaestra,rocasemanal,rocadiaria,verdugo}/   controllers tontos, X-Actor-Id
    ├── in/scheduler/    VerdugoIgnoradoScheduler (23:55 UTC, ver §1.7/RK-6)
    └── out/persistence/{rocamaestra,rocasemanal,rocadiaria,verdugo,participante,evidencia}/
                         JPA + mapper a mano, patron `users`/`points`
```

### 3.1 Casos de uso construidos

| Puerto in | Agregado | Nota |
|---|---|---|
| `ConsultarRocasMaestrasUseCase` | rocamaestra | Solo lectura — ver RK-1 |
| `CrearPlanSemanalUseCase` | rocasemanal | W-02 |
| `EditarDentroDe48hUseCase` | rocasemanal | W-03 — ver RK-5 (el nombre no refleja la ventana real) |
| `CerrarSemanaUseCase` | rocasemanal | W-04, idempotente |
| `ConsultarRocasSemanalesUseCase` | rocasemanal | lectura, agregado al encargo ("lo que falte") |
| `CrearPlanDiarioUseCase` | rocadiaria | R-04, agregado al encargo |
| `CompletarRocaDiariaUseCase` | rocadiaria | R-02 |
| `SolicitarUrlAdjuntoRocaUseCase` | rocadiaria | D-34, agregado al encargo |
| `ConsultarRocasDeHoyUseCase` / `ConsultarRocasDeMananaUseCase` | rocadiaria | R-01/R-03 simplificados (sin dashboard, ver §1.8) |
| `RegistrarEventoVerdugoUseCase` | verdugo | |
| `ConsultarEventosVerdugoUseCase` | verdugo | agregado al encargo |
| `ResolverEventosIgnoradosUseCase` | verdugo | interno, lo llama el scheduler, no expuesto por REST |

### 3.2 Endpoints

| Método | Ruta | Caso de uso | Repo viejo |
|---|---|---|---|
| GET | `/api/v1/rocks/master` | `ConsultarRocasMaestrasUseCase` | nuevo (antes embebido en W-01) |
| POST | `/api/v1/rocks/weekly` | `CrearPlanSemanalUseCase` | `POST /api/v1/rocks/weekly` (W-02) |
| GET | `/api/v1/rocks/weekly?semana=` | `ConsultarRocasSemanalesUseCase` | nuevo |
| PATCH | `/api/v1/rocks/weekly/{id}` | `EditarDentroDe48hUseCase` | `PATCH /api/v1/rocks/weekly/:id` (W-03) |
| PATCH | `/api/v1/rocks/weekly/{id}/review` | `CerrarSemanaUseCase` | `PATCH /api/v1/rocks/weekly/:id/review` (W-04) |
| POST | `/api/v1/rocks/plan` | `CrearPlanDiarioUseCase` | `POST /api/v1/rocks/plan` (R-04) |
| GET | `/api/v1/rocks/today` | `ConsultarRocasDeHoyUseCase` | `GET /api/v1/rocks/today` (R-01, simplificado) |
| GET | `/api/v1/rocks/tomorrow` | `ConsultarRocasDeMananaUseCase` | `GET /api/v1/rocks/tomorrow` (R-03) |
| POST | `/api/v1/rocks/{id}/evidence/upload-url` | `SolicitarUrlAdjuntoRocaUseCase` | nuevo (D-34) |
| POST | `/api/v1/rocks/{id}/evidence` | `CompletarRocaDiariaUseCase` | `POST /api/v1/rocks/:id/evidence` (R-02, body cambia — ver RK-2) |
| GET/POST | `/api/v1/enforcer-events` | `ConsultarEventosVerdugoUseCase`/`RegistrarEventoVerdugoUseCase` | preservado literal |

**Rutas NO portadas** (§1.8): `GET /api/v1/rocks` (W-01 dashboard), `GET/PUT /api/v1/journal/today` (R-05/R-06, es de `habits`).

---

## 4. Integración con `points` — síncrona, dentro de la misma transacción

`RocaDiariaService.completar()` llama a `points.api.AjustarPuntosPort.ajustar(...)` DENTRO del mismo `@Transactional` que marca la roca completada — si el ajuste de puntos falla, la roca tampoco queda completada (rollback completo). Es una mejora deliberada sobre el repo viejo (que hacía el pago de puntos fire-and-forget, "no fatal" — `rocks/service.ts:475-478`), mismo criterio que `points` ya aplicó a su propio ledger (P-06, `docs/MODULO_POINTS.md` §2.2) y que CLAUDE.MD §9.1 pide explícitamente para este módulo.

Idempotencia: `RocaDiaria.puedeOtorgarPuntos()` (`puntosOtorgados <= 0`) evita pagar dos veces — mismo criterio que `habits.habit_tracks.awarded_points` (`docs/MODULO_POINTS.md` Q-4).

---

## 5. Decisiones propias de este módulo (prefijo `RK-`, no pisa el contador `D-N` global)

| # | Decisión | Razonamiento |
|---|---|---|
| RK-1 | `MotivoPuntos` se movió de `points.domain.model.ajuste` a `points.api`. `AjustarPuntosPort.ajustar(...)` lo recibe como parámetro — dejarlo en un paquete interno de `points` obligaba a `rocks` a importar un tipo fuera de `@NamedInterface("api")`, la misma fuga documentada para `users.api.UserSummary` en `docs/MODULO_PHASECONTRACTS.md`/`MODULO_SUPPORT.md`. Cambio de una línea de paquete + imports, autorizado explícitamente por el encargo. **Nota de concurrencia:** un agente paralelo (`habits`) tocó los mismos archivos al mismo tiempo con un diseño distinto (dos copias del enum, domain+api, traducidas por nombre); se reconcilió dejando un único enum en `points.api`, sin copia en `domain` — ver el estado final de `points/api/MotivoPuntos.java` y `points/application/services/PuntajeService.java`. |
| RK-2 | **RESUELTO 2026-08-25** — `CompletarRocaDiariaUseCase` inserta la evidencia por SQL nativo en `renaser.evidencias` (`RegistrarEvidenciaRocaPort`/`RegistrarEvidenciaRocaPersistenceAdapter`), sin `@Entity` propio — esa tabla es del futuro módulo `evidence` (Ola 5, no construido), con su propia máquina de validación IA que `rocks` no implementa (solo inserta el estado inicial `PENDIENTE`/0 intentos). Se decidió escribir (no solo leer, a diferencia del patrón de `phasecontracts`) porque sin esto Ley IV (Pareto) y R-02 quedan sin poder implementarse de verdad — el encargo pedía explícitamente usar `AlmacenamientoPort` "para evidencia de rocas, mismo patrón que phasecontracts/support", y esos dos módulos SÍ persisten la referencia de su adjunto (en su propia tabla). `rocas_diarias` no tiene columnas `bucket`/`ruta` propias (a diferencia de `contratos_fase`/`tickets_soporte`) — el diseño del baseline deliberadamente enruta evidencia de rocas a la tabla compartida `evidencias`, así que escribir ahí (no inventar columnas nuevas) es lo fiel al esquema ya decidido. ~~**Riesgo dejado explícito:** cuando `evidence` exista, este adaptador debe migrar a llamar su puerto público, igual que `docs/MODULO_PHASECONTRACTS.md` §2.3 documentó para su propio caso de lectura.~~ **Cerrado**: `evidence` ya existe (`docs/MODULO_EVIDENCE.md`). Se borraron `RegistrarEvidenciaRocaPort`/`RegistrarEvidenciaRocaPersistenceAdapter` (y su test de integración) y `RocaDiariaService.completar()` ahora llama a `evidence.api.RegistrarEvidenciaPort.registrar(...)`, dentro de la misma transacción, misma semántica exacta (`PENDIENTE`, 0 intentos, `esPrincipal=true`). Ver `docs/MODULO_EVIDENCE.md` §7. |
| RK-3 | Zonas horarias reales (`java.time.ZoneId`, columna `participantes_programa.timezone`) en vez de UTC fijo — mejora sobre lo mínimo necesario, posible porque Java maneja zonas nativamente (el repo viejo necesitaba funciones manuales en `timezone.ts` para lo mismo). |
| RK-4 | `EscalaPuntosRoca` usa la ventana de extensión FIJA de hábitos (3h) — una Roca Diaria no tiene columna de extensión configurable en el baseline (a diferencia de `Habit.evidenceExtensionHours` en el repo viejo). No inventado: es el único valor confirmado (el default), documentado como tal en el javadoc de la clase. |
| RK-5 | `EditarDentroDe48hUseCase` conserva el nombre pedido por el encargo, pero la ventana real NO es una franja fija de 48h — el repo viejo la reemplazó el 2026-08-07 por el sistema de plazo/a-destiempo (`plazos.ts`). Se documenta para que nadie asuma "48 horas" literal leyendo el nombre de la clase. |
| RK-6 | `VerdugoIgnoradoScheduler` (23:55 UTC) se construyó siguiendo el índice/comentario del baseline (`verdugo_pendiente_idx ... WHERE resultado IS NULL`), aunque el repo viejo nunca tuvo ese cron y el flujo de creación portado (`RegistrarEventoVerdugoUseCase`, cliente siempre manda el resultado ya resuelto) no debería dejar eventos pendientes. El barrido queda listo para cuando exista un disparador server-side de Verdugo (fuera de alcance de esta tarea) — ver pregunta abierta §7. |
| RK-7 | Todos los casos de uso de `rocks` son TRAINEE-únicamente (`RolParticipante.TRAINEE`), sin vista de mentor/admin — mismo criterio que el repo viejo (`findTraineeProfileByUserId` en cada endpoint de `rocks/service.ts` y `enforcer/service.ts`, sin excepción). No se inventó un rol adicional. |
| RK-8 | `eventos_verdugo.registro_habito_id` se referencia como `UUID` crudo en el dominio (`EventoVerdugo.destinoId()`), nunca como un tipo de `habits` — la tabla es compartida entre `rocks` y `habits` (arco exclusivo), y `rocks` no puede depender del dominio interno de `habits` sin romper `ArchitectureTest`. |

---

## 6. Qué NO se construyó / preguntas abiertas

- **Dashboard agregado (W-01: `rhythmStatus`, `weekProgress`, `weekGrid`, navegador de semanas admin)** — agregación de presentación pura, sin regla de negocio nueva. Se puede construir sobre los puertos ya existentes sin tocar dominio. No se hizo por presupuesto de esta tarea.
- **`planningBlocked` (Ley II) y `coherenceScore` de rocas en la respuesta "hoy"** — dependen de fórmulas que viven conceptualmente en `points` (ver `docs/MODULO_POINTS.md` Q-2/Q-3, todavía sin resolver ahí tampoco). No implementado para no inventar una integración que `points` no expone todavía.
- **Creación/actualización de Rocas Maestras** — las crea `onboarding` (Ola 5, no existe). `rocks` solo lee (RK-1... perdón, ver RocaMaestra javadoc). Sin `onboarding`, no hay forma real de sembrar datos end-to-end (mismo bloqueante que `docs/MODULO_POINTS.md` §7 documentó para `participantes_programa`).
- ~~**Pregunta abierta real (RK-2):** ¿es correcto que `rocks` escriba en `evidencias` (tabla de `evidence`), o el negocio prefiere que `rocks` no complete una roca hasta que `evidence` exista, dejando R-02 bloqueado como `points` dejó bloqueado su ledger real?~~ **Resuelta 2026-08-25**: `evidence` ya se construyó (`docs/MODULO_EVIDENCE.md`) y `rocks` migró a su puerto público — ver RK-2 en §5.
- **Pregunta abierta real (RK-6):** ¿quién dispara un Evento Verdugo del lado servidor (para que el barrido de las 23:55 tenga algo que resolver)? No hay respuesta en el repo viejo ni en el encargo — documentado, no inventado.
- **Margen de edición de 30 min para Rocas Diarias** (`plazos.ts:50`, `EDICION_DIARIA_TARDIA_MS`) — el repo viejo define la constante pero **nunca la usa** en ningún caso de uso real de `rocks/service.ts` (las diarias no tienen PATCH, solo se completan vía evidencia). No se inventó un endpoint de edición que el propio repo viejo no tiene.
- **`@RequiresPermission`/`@PublicEndpoint` + test de reflexión** — el mecanismo no existe todavía en `shared/` (bloqueado por B-5/R-2 de `users`, igual que en todos los módulos construidos hasta ahora).

---

## 7. Pruebas

| Tipo | Cobertura |
|---|---|
| Unit dominio | `VentanaPlanificacionSemanalTest`, `VentanaPlanificacionDiariaTest`, `EscalaPuntosRocaTest` (matriz completa de la escala de puntos, bordes exactos), `SemanaProgramaTest`, `RocaSemanalTest`, `RocaDiariaTest`, `EventoVerdugoTest` |
| Unit servicio (Mockito) | `RocaMaestraServiceTest`, `RocaSemanalServiceTest`, `RocaDiariaServiceTest`, `VerdugoServiceTest` — incluyen las pruebas de seguridad de CLAUDE.MD §0.3 (rol sin permiso → `NotAuthorizedException`, actor SUSPENDIDO → `NotAuthorizedException`) en cada servicio, más Ley IV, Ley VI, idempotencia de puntos, rechazo de `IGNORADO` desde el cliente |
| Integración Testcontainers | `ConsultarProgresoParticipanteRocksPersistenceAdapterTest`, `RocaMaestraPersistenceAdapterTest`, `RocaSemanalPersistenceAdapterTest` (cubre el riesgo real del `@ElementCollection` de `acciones_criticas`), `RocaDiariaPersistenceAdapterTest`, `EventoVerdugoPersistenceAdapterTest`, `RegistrarEvidenciaRocaPersistenceAdapterTest` (confirma el INSERT nativo contra `evidencias` real) |

**Lo que quedó explícitamente sin verificar** (CLAUDE.MD §0.2): no se ejecutó `./mvnw clean test` ni `ArchitectureTest` (prohibido en este encargo) — el supervisor los corre. Puntos de mayor riesgo si algo falla:
- El `@ElementCollection` de `AccionCriticaEmbeddable` sobre `acciones_criticas` — es el primer uso de `@ElementCollection` en este repo, sin precedente que copiar.
- El `CAST(?4 AS renaser.tipo_evidencia)`/`renaser.estado_validacion` del INSERT nativo de evidencia — mismo tipo de riesgo que `phasecontracts` señaló para sus lecturas de enum vía `Object[]`, acá en la dirección de escritura.
- La reconciliación de `MotivoPuntos` con el agente paralelo de `habits` (RK-1) — el estado final descrito en este documento es el que quedó en el repo al terminar esta tarea, pero otro agente pudo seguir tocando `points/**` después.
- Todos los `@RestController` de este módulo son nuevos (sin precedente propio) — el patrón X-Actor-Id/DTOs es el mismo de `phasecontracts`/`support`, pero no se verificó en vivo.

**Deuda de bitácora:** no se encontró ningún error/bug real de entorno durante la construcción (solo decisiones de diseño, documentadas como RK-N arriba) — no se agregó una entrada artificial a `docs/BITACORA_ERRORES.md`.

---

## 8. D-43 — `PorcentajeRocasFinder`, el % de rocas EN LOTE para el ranking general

**Encargo:** el ranking general (`docs/MODULOS_A_AVANZAR.md` §8, D-43) es 50% hábitos + 35% rocas + 15% cursos. `points` combina los tres; cada módulo dueño de su porcentaje lo expone EN LOTE, sin procedimientos almacenados — la fórmula vive en Java, no baja a SQL.

### 8.1 Paso 0 — la fórmula, con citas

Dos fuentes, verificadas ambas, no solo la cabecera del SQL:

- **`src/lib/coherence.ts::averageCompletionForDates`** (`C:\Users\Usuario\Documents\Backend90dias\RenaserBack\src\lib\coherence.ts:114-131`), que a su vez delega el agrupado por día en `computeDailyCompletionHistory` (`coherence.ts:45-100`).
- **`prisma/migrations/general_ranking_scores_function.sql`** (`C:\Users\Usuario\Documents\Backend90dias\RenaserBack\prisma\migrations\general_ranking_scores_function.sql`), la función SQL `general_ranking_scores()` — y esta es la pieza importante: **no es un comentario describiendo código viejo, es la implementación de producción vigente**. `src/features/community/repository.ts:409-412` (`findGeneralRankingScores`) llama `SELECT * FROM general_ranking_scores()` directamente; no existe ningún `generalRankingService.ts` en el repo (búsqueda confirmada, cero resultados) — la función SQL reemplazó por completo a un servicio TypeScript anterior que ya no está. **El comentario NO contradice al código: son la misma fuente.** No hubo que elegir uno sobre otro.

**La fórmula para rocas (`rocas_pct`, `general_ranking_scores_function.sql:115-134`), literal:**

1. Ventana de **7 días UTC cerrados incluyendo hoy**: `r.date BETWEEN bounds.today - 6 AND bounds.today` donde `bounds.today = (now() AT TIME ZONE 'utc')::date` (líneas 67-68, 122). Portado en `PorcentajeRocasService.porcentajePorParticipante`: `desde = hasta.minusDays(6)`.
2. Por cada (participante, día) con al menos una Roca Diaria: `total = count(*)`, `completadas = count(*) FILTER (WHERE r.completed)` (líneas 116-124). **Cada día se redondea a entero PRIMERO**: `day_score = round(completed/total*100)` (línea 126) — portado en `DiaRocas.puntajeDelDia()`.
3. **LUEGO se promedian esos enteros** ya redondeados, con 1 decimal final: `round(avg(day_score)*10)/10` (línea 131) — portado en `PorcentajeRocas.calcular(List<DiaRocas>)`, con `BigDecimal` (no `double`) en toda la cadena para no introducir imprecisión de punto flotante que el SQL (tipo `numeric`) no tiene.
4. **Ventana sin ningún día calificable → 100** (`COALESCE(rp.pct, 100)`, línea 181; mismo criterio en `coherence.ts:127`, `if (dayScores.length === 0) return 100`) — portado en `PorcentajeRocas.calcular`, lista vacía → `100.0`.

### 8.2 Qué cuenta como "roca calificable" — la diferencia real con hábitos

La cabecera de `general_ranking_scores_function.sql:19-24` dice que `rocasPct` usa "el mismo criterio que `habitosPct`" — cierto para el *agregado/promedio* (pasos 1, 3, 4 arriba), **no para qué entra al total de cada día**. Ahí hay una asimetría real, confirmada en tres fuentes:

- **`coherence.ts:77-83`** (`computeDailyCompletionHistory`, el bloque que procesa `dailyRocks`): a diferencia de los hábitos (que sí filtran opcionales sin completar, líneas 61-68), las rocas se suman TODAS sin ninguna condición: `e.total++; e.totalAll++; if (r.completed) e.completed++` — sin ningún `if (isOptional && !done) continue` equivalente. El comentario de la línea 81 lo dice explícito: *"una roca nunca es opcional: cuenta en los dos"*.
- **`prisma/schema.prisma:1574-1605`** (`model DailyRock`): no existe una columna `isOptional` en absoluto — a diferencia de `HabitTrack` que sí la tiene. No es que el campo exista y se ignore: el concepto "roca opcional" no existe en el dominio.
- **La función SQL (`general_ranking_scores_function.sql:116-124`)** confirma lo mismo: agrupa `daily_rocks` sin ningún filtro de opcionalidad, a diferencia del bloque de hábitos (línea 85, `AND (h.is_optional = false OR h.status = 'COMPLETED')`).

**Conclusión — roca calificable = toda fila de `rocas_diarias` (`DailyRock`/`daily_rocks` en el repo viejo) de ese participante en esa fecha, sin importar color Pareto (VERDE/AMARILLA/ROJA) ni si está bloqueada por Ley IV.** Una roca AMARILLA bloqueada (sin evidencia porque la VERDE del eje no se completó) sigue contando en el `total` del día — bloqueada no es lo mismo que "no calificable". Portado en `DiaRocas`: el registro no distingue color ni bloqueo, solo `total`/`completadas`.

**Las Rocas Semanales (`rocas_semanales`/`WeeklyRock`) NO participan en absoluto** — ni la función SQL ni `coherence.ts` las tocan para este cálculo: `WeeklyRock` (`prisma/schema.prisma:677-705`) no tiene columna `date` ni `completed` propia (es un objetivo por semana, no un ítem diario ejecutable), así que estructuralmente no puede entrar a un cálculo día-por-día. `PorcentajeRocasFinder` solo consulta `rocas_diarias` (ver `SpringDataConteoDiarioRocasRepository`, `FROM RocaDiariaJpaEntity`) — no toca `rocas_semanales` en absoluto, fiel al alcance real de la fórmula.

### 8.3 Qué se construyó

```
rocks/
├── api/
│   └── PorcentajeRocasFinder.java                 puerto público D-43, EN LOTE
├── domain/model/coherencia/                        (paquete nuevo — no es un agregado propio,
│   ├── DiaRocas.java                                es un modelo de lectura derivado de rocadiaria)
│   └── PorcentajeRocas.java                         cálculo puro: doble redondeo, ventana vacía → 100
├── application/
│   ├── ports/out/coherencia/
│   │   └── CargarConteoDiarioRocasPort.java         una consulta agrupada por (participante, día)
│   └── services/
│       └── PorcentajeRocasService.java              implementa PorcentajeRocasFinder
└── infrastructure/adapter/out/persistence/coherencia/
    ├── SpringDataConteoDiarioRocasRepository.java   JPQL, GROUP BY participanteId, fecha — repositorio
    │                                                 propio (no reutiliza el de rocadiaria, que es
    │                                                 package-private de ese paquete)
    └── CargarConteoDiarioRocasPersistenceAdapter.java  traduce Object[] → DiaRocas
```

`PorcentajeRocasService` NO itera participantes: recibe la lista completa, hace UNA llamada a `CargarConteoDiarioRocasPort.conteoDiarioPorParticipante(participantes, desde, hasta)`, y luego solo itera el `Map` ya resuelto para rellenar con `100.0` a quien el puerto no haya devuelto (participante sin ninguna Roca Diaria calificable en la ventana). Es exactamente la forma que D-43 pide evitar el incidente real ("Too many database connections opened", ~30 cuentas activas, `general_ranking_scores_function.sql:10-16`).

### 8.4 Qué quedó abierto

- **`points` todavía no consume este puerto** — no se construyó `points` en esta tarea (fuera de alcance: solo se tocó `rocks/**`). Cuando `points` combine los tres pesos, va a necesitar el mismo puerto de `habits` (`PorcentajeHabitosFinder` o como se llame — no existe todavía, verificado) y uno de `academy`/`cursos` para `cursosPct`.
- **No se verificó `./mvnw clean test`** (regla del encargo — lo corre el supervisor). Riesgo concreto de esta pieza si algo falla: el JPQL con `SUM(CASE WHEN r.completada = true THEN 1L ELSE 0L END)` sobre un campo `boolean` de un `@Entity` con Lombok `@Data` — sin precedente exacto en el resto del módulo (la única consulta agregada previa, `contarPorLecciones` en `academy`, no tiene `CASE WHEN`, solo `COUNT`).
- **No se corrigió ni se le preguntó al negocio** si una roca AMARILLA/ROJA bloqueada por Ley IV "debería" contar distinto en este ranking — se portó tal cual el criterio del repo viejo (bloqueada sí cuenta en el total, ver §8.2), sin inventar una excepción que ni `coherence.ts` ni la función SQL contemplan.
