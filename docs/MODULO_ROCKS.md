# Módulo `rocks` — Rocas Maestras/Semanales/Diarias, Modo Verdugo

**Fecha:** 2026-08-24
**Ola:** 2 (núcleo del producto) — depende de `users` (vía su propia query nativa, ver §2) y de `points` (síncrono, vía `points.api.AjustarPuntosPort`)
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/PLAN_DE_MODULOS.md` §"4. `rocks`" (semilla) · `docs/MODULO_POINTS.md`, `docs/MODULO_PHASECONTRACTS.md`, `docs/MODULO_SUPPORT.md` (ejemplo vivo, patrones replicados acá)

---

## 0. Estado

🔄 **Construido, sin ejecutar `./mvnw clean test` todavía** (regla del encargo: este agente no corre Maven — lo corre el supervisor).

**2026-08-26 — Huecos #15/#16/#17** (encargo nuevo, agente en paralelo con `habits`/`evidence`/`users`):
- **Hueco #15 (dashboard agregado `GET /rocks`): construido.** Ver §9.
- **Hueco #16 (Diario Nocturno `journal/today`): NO construido — colisión real con `habits.EntradaDiario`.** Ver §10. No se tocó `habits`.
- **Hueco #17 (`esPrincipal`/`publishedToWall` al completar evidencia): `esPrincipal` construido, `publishedToWall` NO construido** (depende de `evidence.api`/`community.api`, ninguno expone lo necesario). Ver §11.

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

- **Bitácora Nocturna / `JournalEntry`** (R-05/R-06 de `rocks/service.ts`): pertenece al agregado `diario/` de `habits` (`docs/PLAN_DE_MODULOS.md`), no a `rocks`. No se tocó. **Ver §10 (2026-08-26): confirmado que es el mismo concepto que `habits.domain.model.diario.EntradaDiario` (`TipoEntradaDiario.BITACORA_NOCTURNA`), ya con dominio/puertos/persistencia construidos ahí.**
- ~~Dashboard agregado W-01~~ **Construido 2026-08-26, ver §9.**
- ~~`planningBlocked` (Ley II)~~ **Construido 2026-08-26, ver §9 — una relectura de `rocks/service.ts` mostró que SÍ es construible dentro de `rocks` (no depende de `points`, a diferencia de `coherenceScore`; ver la nota en §9.2).** `coherenceScore` en la respuesta de "hoy" sigue sin construirse: depende de datos que no son de `rocks` (`computeRockCoherenceScore` ya vive conceptualmente en `points`, ver `docs/MODULO_POINTS.md` Q-2/Q-3). No se inventó.

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
| GET | `/api/v1/rocks` | `ConsultarDashboardRocasUseCase` | `GET /api/v1/rocks` (W-01, construido 2026-08-26 — ver §9) |

**Rutas NO portadas**: `GET/PUT /api/v1/journal/today` (R-05/R-06, es de `habits` — ver §10).

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

- ~~Dashboard agregado (W-01...)~~ **Construido 2026-08-26, ver §9.**
- **`coherenceScore` de rocas en la respuesta "hoy"/dashboard** — depende de una fórmula que vive conceptualmente en `points` (ver `docs/MODULO_POINTS.md` Q-2/Q-3, todavía sin resolver ahí tampoco). No implementado para no inventar una integración que `points` no expone todavía. (`planningBlocked` (Ley II) SÍ se construyó — ver §9.2, corrige la nota anterior de esta sección que lo agrupaba con `coherenceScore`.)
- **Diario Nocturno (`journal/today`, Hueco #16, 2026-08-26)** — no construido, colisión real con `habits.EntradaDiario`. Ver §10, decisión pendiente de quien tenga visión de los dos módulos.
- **`publishedToWall` al completar una Roca con evidencia (Hueco #17, 2026-08-26)** — no construido, faltan puertos públicos en `evidence`/`community`. Ver §11.
- **Creación/actualización de Rocas Maestras** — las crea `onboarding` (Ola 5, no existe). `rocks` solo lee (RK-1... perdón, ver RocaMaestra javadoc). Sin `onboarding`, no hay forma real de sembrar datos end-to-end (mismo bloqueante que `docs/MODULO_POINTS.md` §7 documentó para `participantes_programa`).
- ~~**Pregunta abierta real (RK-2):** ¿es correcto que `rocks` escriba en `evidencias` (tabla de `evidence`), o el negocio prefiere que `rocks` no complete una roca hasta que `evidence` exista, dejando R-02 bloqueado como `points` dejó bloqueado su ledger real?~~ **Resuelta 2026-08-25**: `evidence` ya se construyó (`docs/MODULO_EVIDENCE.md`) y `rocks` migró a su puerto público — ver RK-2 en §5.
- **Pregunta abierta real (RK-6):** ¿quién dispara un Evento Verdugo del lado servidor (para que el barrido de las 23:55 tenga algo que resolver)? No hay respuesta en el repo viejo ni en el encargo — documentado, no inventado.
- **Margen de edición de 30 min para Rocas Diarias** (`plazos.ts:50`, `EDICION_DIARIA_TARDIA_MS`) — el repo viejo define la constante pero **nunca la usa** en ningún caso de uso real de `rocks/service.ts` (las diarias no tienen PATCH, solo se completan vía evidencia). No se inventó un endpoint de edición que el propio repo viejo no tiene.
- **`@RequiresPermission`/`@PublicEndpoint` + test de reflexión** — el mecanismo no existe todavía en `shared/` (bloqueado por B-5/R-2 de `users`, igual que en todos los módulos construidos hasta ahora).

---

## 7. Pruebas

| Tipo | Cobertura |
|---|---|
| Unit dominio | `VentanaPlanificacionSemanalTest`, `VentanaPlanificacionDiariaTest`, `EscalaPuntosRocaTest` (matriz completa de la escala de puntos, bordes exactos), `SemanaProgramaTest` (+ casos nuevos de `limites`/`finDelPrograma`, 2026-08-26), `RocaSemanalTest`, `RocaDiariaTest`, `EventoVerdugoTest`, `EstadoRitmoRocasTest`, `ProgresoSemanalTest`, `BloqueoPlanificacionTest`, `DiaGrillaSemanalTest` (los 4 últimos, Hueco #15, 2026-08-26) |
| Unit servicio (Mockito) | `RocaMaestraServiceTest`, `RocaSemanalServiceTest`, `RocaDiariaServiceTest` (+ caso nuevo `esPrincipalViajaAlComandoDeEvidencia`, Hueco #17), `VerdugoServiceTest`, `DashboardRocasServiceTest` (nuevo, Hueco #15) — incluyen las pruebas de seguridad de CLAUDE.MD §0.3 (rol sin permiso → `NotAuthorizedException`, actor SUSPENDIDO → `NotAuthorizedException`) en cada servicio, más Ley IV, Ley VI, idempotencia de puntos, rechazo de `IGNORADO` desde el cliente, Ley II (`planificacionBloqueada`), ritmo, grilla semanal, guard de programa no iniciado |
| Integración Testcontainers | `ConsultarProgresoParticipanteRocksPersistenceAdapterTest`, `RocaMaestraPersistenceAdapterTest`, `RocaSemanalPersistenceAdapterTest` (cubre el riesgo real del `@ElementCollection` de `acciones_criticas`), `RocaDiariaPersistenceAdapterTest`, `EventoVerdugoPersistenceAdapterTest`, `RegistrarEvidenciaRocaPersistenceAdapterTest` (confirma el INSERT nativo contra `evidencias` real) |

**Lo que quedó explícitamente sin verificar** (CLAUDE.MD §0.2): no se ejecutó `./mvnw clean test` ni `ArchitectureTest` (prohibido en este encargo) — el supervisor los corre. Puntos de mayor riesgo si algo falla:
- El `@ElementCollection` de `AccionCriticaEmbeddable` sobre `acciones_criticas` — es el primer uso de `@ElementCollection` en este repo, sin precedente que copiar.
- El `CAST(?4 AS renaser.tipo_evidencia)`/`renaser.estado_validacion` del INSERT nativo de evidencia — mismo tipo de riesgo que `phasecontracts` señaló para sus lecturas de enum vía `Object[]`, acá en la dirección de escritura.
- La reconciliación de `MotivoPuntos` con el agente paralelo de `habits` (RK-1) — el estado final descrito en este documento es el que quedó en el repo al terminar esta tarea, pero otro agente pudo seguir tocando `points/**` después.
- Todos los `@RestController` de este módulo son nuevos (sin precedente propio) — el patrón X-Actor-Id/DTOs es el mismo de `phasecontracts`/`support`, pero no se verificó en vivo.
- **2026-08-26 (Huecos #15/#17):** tampoco se corrió `./mvnw clean test` para este lote de cambios. Puntos de mayor riesgo: `DashboardRocasService` es el primer caso de uso de `rocks` que inyecta OTROS casos de uso del mismo módulo (ver §9.3); y el nuevo campo `esPrincipal` de `CompletarRocaDiariaCommand` obligó a pasarlo también a `SelfValidating.validateConstructorArgs(...)` (booleano autoboxeado) — mismo patrón ya usado en `points.RegistrarCoherenciaDiariaCommand`, verificado como precedente antes de aplicarlo, pero sin correr el test real todavía.

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

---

## 9. Hueco #15 — dashboard agregado `GET /api/v1/rocks`

**Encargo:** un solo llamado para la pantalla principal de Rocas: compuertas de planificación, `rhythmStatus`, grilla semanal. §1.8/§6 (versión anterior de este documento) daban esto por agregación de presentación pura "sin regla de negocio nueva" y `rhythmStatus` por no encontrado. Una relectura completa de `getDashboard`/`buildWeekData` en `rocks/service.ts` (repo viejo, `C:\Users\Usuario\Documents\Backend90dias\RenaserBack\src\features\rocks\service.ts:746-949`) mostró que **sí está completo ahí**, con reglas y umbrales exactos — se portó literal, no se inventó nada.

### 9.1 Qué se construyó

```
rocks/
├── domain/model/dashboard/                          (paquete nuevo — no es un agregado propio,
│   ├── EstadoRitmoRocas.java                         son cálculos puros de presentación, mismo
│   ├── DiaGrillaSemanal.java                         criterio que domain/model/coherencia/ del D-43)
│   ├── ProgresoSemanal.java
│   └── BloqueoPlanificacion.java
├── domain/model/rocasemanal/SemanaPrograma.java      + limites(fechaInicio, numeroSemana), finDelPrograma(...)
├── application/
│   ├── ports/in/dashboard/ConsultarDashboardRocasUseCase.java
│   └── services/DashboardRocasService.java           compone Consultar{RocasMaestras,RocasSemanales,RocasDeHoy}UseCase
└── infrastructure/adapter/in/rest/dashboard/
    ├── DashboardRocasController.java                 GET /api/v1/rocks
    └── DashboardRocasResponse.java
```

`DashboardRocasService` **no duplica reglas**: llama a `ConsultarRocasMaestrasUseCase`, `ConsultarRocasSemanalesUseCase` (dos veces — semana actual y semana de mañana, para la compuerta diaria) y `ConsultarRocasDeHoyUseCase` ya existentes, y usa `CargarConteoDiarioRocasPort` (el mismo puerto EN LOTE del D-43, §8) para la grilla semanal y el ritmo — dos consultas agregadas (rango de la semana, rango de los últimos 7 días), nunca una consulta por día ni por roca. Evita el incidente de N+1 que motivó D-43.

### 9.2 Campos, uno por uno, con su cita exacta del repo viejo

| Campo | Regla | Cita (repo viejo) |
|---|---|---|
| `rocasDesbloqueadas` | `rocasMaestras.size() >= 3` | `service.ts:920` |
| `tieneRocaSemanal` | hay Roca Semanal para los 3 ejes de la semana en curso | `service.ts:806` |
| `rocasSemanales[].editable` | **NO** es `createdAt+48h` (eso es un bug ya corregido, ver RK-5) — se computa con `VentanaPlanificacionSemanal.puedeEditar`, la regla real de W-03 | — (corrección deliberada sobre `service.ts:801`, que sí tiene el `editableUntil` fijo; ver nota abajo) |
| `grillaSemanal` | un día por fecha de la semana (recortada al fin de programa), `null`/`null` si es futuro, `total=null` si no hubo rocas ese día | `service.ts:774-788` |
| `ritmo` (`EstadoRitmoRocas`) | días con >=1 roca completada de los últimos 7 (terminando AYER): `>=5` OK, `>=3` LENTO, si no CRITICO | `service.ts:885-890` |
| `progresoSemanalPct` | completadas/planificadas × 100 redondeado, solo días YA transcurridos de la semana | `service.ts:892-901` |
| `planificacionBloqueada` (Ley II) | día de programa >= 31 Y hora local >= 20:00 Y < 3 Rocas Diarias para mañana | `service.ts:106-124` (`ROCKS_PHASE_START_DAY=31`, `PLANNING_LOCK_HOUR=20`) |
| `puedeCrearPlanDiario` | `rocasDesbloqueadas && ventanaDiariaAbierta(18:00+) && hayRocaSemanalParaLaSemanaDeManana` | `service.ts:145-154, 935` |
| `puedeCrearPlanSemanal` | `rocasDesbloqueadas` (planificar siempre se admite; la ventana solo decide si cuenta en plazo) | `service.ts:938` |
| `planificacionSemanalTardia` | `!VentanaPlanificacionSemanal.abierta(ahora, zona)` | `service.ts:939` |
| `rocasDeHoy` | reutiliza `ConsultarRocasDeHoyUseCase.hoy()` tal cual (ya resuelve Ley IV) | `service.ts:904-912` (portado ya antes, no se duplicó) |
| `coherenceScore` | **NO incluido** — depende de `points`, sin puerto público (`docs/MODULO_POINTS.md` Q-2/Q-3) | `service.ts:930` |

**Corrección sobre `editableUntil`:** el repo viejo calcula `editableUntil: new Date(wr.createdAt.getTime() + 48*60*60*1000)` (`service.ts:801`) — literal, sería la franja fija de 48h que RK-5 (§5) ya documentó como **incorrecta** desde el 2026-08-07 (reemplazada por `plazos.ts`, el sistema de plazo/a-destiempo). Portar ese cálculo tal cual habría reintroducido el mismo bug que el propio repo viejo dejó vivo en el dashboard mientras lo corregía en el resto de `rocks/service.ts`. Se usa `VentanaPlanificacionSemanal.puedeEditar` (la misma regla que ya usa `EditarDentroDe48hUseCase`) en su lugar — un booleano `editable`, no una fecha límite, para no prometerle a la app un instante que la regla real no respeta.

**Guard "programa no iniciado":** portado de `service.ts:826-873` — si `hoy < fechaInicio`, se responde el mismo contrato con colecciones vacías (`grillaSemanal=[]`, `rocasSemanales=[]`, `rocasDeHoy=[]`), `ritmo=OK` fijo, todas las compuertas en `false`, pero `rocasMaestras` y `rocasDesbloqueadas` sí resueltas (se definen en el onboarding, antes del día 1). Evita un 4xx en una pantalla navegable desde la barra inferior.

**`completedDays` por eje de `weeklyRocks` (repo viejo, `service.ts:794-796`) — NO portado.** Requeriría o bien N+1 (una consulta por eje/día) o un puerto agregado nuevo con desglose por eje que no existe (`CargarConteoDiarioRocasPort` agrega TODOS los ejes juntos por diseño, D-43). Se dejó fuera en vez de violar "evitar N+1" o inventar un puerto nuevo sin pedido explícito — pregunta abierta si se necesita, ver abajo.

### 9.3 Qué quedó abierto

- **`coherenceScore`** — no incluido, depende de `points` (ver tabla arriba).
- **`completedDays` por eje en `rocasSemanales`** — no portado, ver 9.2. Si la app lo necesita, hace falta un puerto agregado nuevo en `application/ports/out/coherencia/` con desglose por `EjeObjetivo` (no reutilizable desde `CargarConteoDiarioRocasPort`).
- **Navegador de semanas admin** (`getDashboardForWeek`/vista de mentor sobre una semana arbitraria, `service.ts:951+`) — no pedido en el encargo (Hueco #15 es la pantalla del propio aprendiz) y **RK-7** ya estableció que `rocks` es TRAINEE-únicamente, sin vista de mentor/admin. No construido.
- **No se verificó `./mvnw clean test`** (regla del encargo) — riesgo concreto si algo falla: es la primera vez en este módulo que un caso de uso inyecta OTROS casos de uso del mismo módulo como dependencias (`DashboardRocasService` depende de `ConsultarRocasMaestrasUseCase`/`ConsultarRocasSemanalesUseCase`/`ConsultarRocasDeHoyUseCase`) — sin precedente exacto en el resto de `rocks` para verificar que Spring resuelve el grafo de beans sin ciclos (no debería haberlos: es un grafo estrictamente hacia afuera desde el nuevo servicio).

---

## 10. Hueco #16 — Diario Nocturno (`journal/today`): colisión confirmada con `habits.EntradaDiario`, NO construido

**Encargo:** construir `journal/today` en `rocks`, salvo que sea el mismo concepto que `EntradaDiario` de `habits` — en ese caso, documentar y no duplicar.

**Confirmado: es el mismo concepto.** Evidencia, no solo el nombre:

- `habits/domain/model/diario/EntradaDiario.java` — javadoc: *"Entrada de diario consolidada (tabla `entradas_diario`)"*. Campos: `id`, `participanteId`, `fecha`, `tipo` (`TipoEntradaDiario`), `contenidoTexto`, `audioBucket`, `audioRuta`, `transcripcion`. `TipoEntradaDiario` incluye el valor **`BITACORA_NOCTURNA`** — la traducción literal de "Diario Nocturno"/"Nightly Journal" (R-05/R-06 del repo viejo, ya documentado como tal en §1.8 de este mismo archivo desde antes de esta tarea).
- La tabla `entradas_diario` (baseline `V1__baseline_renaser.sql:369-383`) es de `habits` — `UNIQUE (participante_id, fecha, tipo)`, exactamente la clave de negocio de un diario de una entrada por día y por tipo.
- `habits/application/ports/out/diario/{Load,Save}EntradaDiarioPort.java` — puertos de aplicación ya existen (`porParticipanteFechaYTipo`, `save`), y `habits/infrastructure/adapter/out/persistence/diario/*` — el adaptador JPA completo también existe. **Lo único que falta en `habits` es el caso de uso y el controller que los use** (confirmado: no hay ningún `EscribirEntradaDiarioUseCase` ni controller en `habits` para este agregado — solo dominio + puertos + persistencia, tal como el encargo advertía).
- `habits/api/EntradaDiarioFinder.java` + `EntradaDiarioSummary.java` — contrato público ya existe, pero es de **lectura** (`entradasEntre`, consumido hoy por el Espejo Sombra de `rag`), no de escritura.

**Por qué no se construyó nada, ni en `rocks` ni en `habits`:**

1. El encargo de este agente es **`rocks` únicamente** — no tocar `habits`, donde trabaja otro agente en paralelo.
2. Construir un "Diario Nocturno" propio en `rocks` (tabla/agregado nuevo) sería **duplicar** un concepto que ya tiene dueño, con el riesgo real de terminar con dos tablas de diario (`entradas_diario` de `habits` más algo nuevo en `rocks`) sirviendo la misma pantalla de la app — exactamente el resultado que el encargo pedía evitar explícitamente.
3. La BD está congelada (D-40, regla dura) — no se puede crear una tabla nueva de todos modos, y `entradas_diario` ya cubre el caso de uso (columna `tipo` con `BITACORA_NOCTURNA` dedicado).

**Decisión que le queda a quien tenga visión de los dos módulos (no a este agente):** `GET/PUT /api/v1/journal/today` (o el nombre de ruta que la app espera) debería exponerse desde **`habits`**, escribiendo/leyendo `EntradaDiario` con `tipo=BITACORA_NOCTURNA`, reutilizando `LoadEntradaDiarioPort`/`SaveEntradaDiarioPort` que ya existen ahí. Falta solo el caso de uso (`EscribirEntradaDiarioUseCase`/`ConsultarEntradaDiarioDeHoyUseCase`) y el controller REST — trabajo acotado, ningún dominio nuevo. Si en cambio el negocio quisiera que "Diario Nocturno" fuera conceptualmente distinto de las demás entradas de diario (con reglas propias, ej. vinculado a Rocas del día), eso sería una decisión de producto que ningún documento confirma hoy — no se inventó.

---

## 11. Hueco #17 — `esPrincipal`/`publishedToWall` al completar una Roca con evidencia

**Encargo:** aceptar `publishedToWall` y `esPrincipal` al completar una Roca con evidencia (`POST /api/v1/rocks/{id}/evidence`). `esPrincipal` construido completo; `publishedToWall` documentado como no construible hoy sin cambios en `evidence`/`community` (fuera del alcance de este agente).

### 11.1 `esPrincipal` — construido

Antes de esto, `RocaDiariaService.completar()` mandaba `esPrincipal=true` HARDCODEADO al llamar a `evidence.api.RegistrarEvidenciaPort` — ya viajaba en el comando público de `evidence` (RK-2, §5), pero como constante, no como decisión del cliente. Cambios:

- `CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand` — nuevo campo `boolean esPrincipal`.
- `RocaDiariaService.completar()` — pasa `command.esPrincipal()` en vez de `true`.
- `CompletarRocaDiariaRequest` (DTO web) — nuevo campo `Boolean esPrincipal` (nullable). `RocaDiariaController.completar()`: `null` → `true`, para que un cliente móvil viejo que todavía no manda el campo vea exactamente el mismo comportamiento de antes (nadie tiene que actualizarse a la fuerza).
- Como el destino de esta evidencia SIEMPRE es `DestinoEvidencia.RocaDiaria`, el CHECK `principal_solo_en_roca` que ya valida `RegistrarEvidenciaComando` nunca puede fallar acá — no hace falta validación extra en `rocks`.
- No hace falta tocar el dominio `RocaDiaria` ni su persistencia: `esPrincipal` es un atributo de la EVIDENCIA (tabla `evidencias`, dueña `evidence`), no de la Roca Diaria.

### 11.2 `publishedToWall` — NO construido, con lo que haría falta exacto

`evidencias.publicada_en_muro` (baseline `V1__baseline_renaser.sql:775`) es la columna real detrás de este campo. **Ya estaba documentado como fuera de alcance de `evidence` mismo** (`docs/MODULO_EVIDENCE.md:127`: *"columna presente, sin ningún caso de uso que la togglee — pertenece conceptualmente a `community` (el "Muro"), fuera del alcance de este módulo"*). Confirmado en código: `evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando` no tiene un parámetro `publicadaEnMuro`, y no existe ningún otro puerto público de `evidence` para togglear ese flag después de registrar.

Tampoco `community.api` (paquete público de `community`) resuelve esto — hoy solo expone `CelulaFinder`, `CelulaCreadaEvent` y `PublicacionCreadaEvent`. No hay ningún puerto para que OTRO módulo cree una publicación en `publicaciones_muro`, ni para vincular una evidencia existente a una publicación nueva.

**Lo que le falta a `rocks` para construir esto, sin adivinar el diseño correcto (dos caminos posibles, no es a este agente a quien le toca elegir):**

1. **Camino A — solo el flag:** `evidence.api` expone un parámetro nuevo (o un puerto nuevo, ej. `MarcarPublicadaEnMuroPort`) para setear `evidencias.publicada_en_muro=true` al registrar o después. `rocks` lo llamaría junto a `RegistrarEvidenciaPort.registrar(...)`, dentro de la misma transacción. Esto NO crea ninguna fila en `publicaciones_muro` — sería un flag informativo nada más, sin que la evidencia aparezca realmente en el feed del Muro (`community` no lee ese flag hoy, verificado: cero referencias a `publicada_en_muro` fuera de `evidence`).
2. **Camino B — publicación real:** `community.api` expone un puerto nuevo (ej. `CrearPublicacionDesdeEvidenciaPort`) que, dado el `evidenceId`/`participanteId`/`rutaStorage`, cree una fila real en `publicaciones_muro` (+ `medias_publicacion` referenciando el archivo) — el `tipo_publicacion` de esa tabla ya distingue `MANUAL` de otros orígenes (`tipo_publicacion NOT NULL DEFAULT 'MANUAL'`, baseline:1083), así que probablemente exista o haga falta un valor de enum para "publicado automáticamente desde una Roca" (no confirmado, no se inventó).

Ninguno de los dos existe hoy. Se dejó **sin construir por completo** — ni el campo de request, ni la lógica — para no aceptar un campo que no hace nada (peor que no aceptarlo: un cliente que lo manda creería que funcionó). Queda como pregunta abierta real para quien tenga alcance sobre `evidence`/`community`: ¿cuál de los dos caminos, o algo distinto?

**Nota de seguridad, por si se retoma:** cuando esto se construya, recordar CLAUDE.md §0.3/E-42 (`docs/BITACORA_ERRORES.md`) — el chequeo de actor va siempre DESPUÉS del de visibilidad del recurso. Publicar en el Muro la evidencia de una Roca de OTRO participante (por ejemplo, si el `rocaDiariaId` del comando no fuera del actor) ya está cubierto por `requireRocaPropia` existente — pero cualquier nuevo puerto de `community` que reciba un `evidenceId` directo desde otro módulo debe volver a verificar propiedad, no asumir que quien lo llama ya lo hizo.
