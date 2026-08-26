# Módulo `evidence` — validación de evidencia (SIN IA en este alcance)

**Fecha:** 2026-08-25
**Ola:** 5 en el plan original (`docs/PLAN_DE_MODULOS.md`), pero esta pasada construye **solo la parte sin IA** — la integración real con Gemini/Vertex vía Spring AI queda pospuesta, por decisión explícita del encargo.
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/MODULOS_A_AVANZAR.md` §evidence · `docs/MODULO_ROCKS.md` (RK-2, cerrado acá) · `docs/MODULO_HABITS.md` (D-H6, cerrado acá) · `docs/MODULO_POINTS.md`/`MODULO_PHASECONTRACTS.md`/`MODULO_SUPPORT.md` (patrones replicados)

**Fuente de verdad usada para esta tarea**: `CLAUDE.MD`, `docs/MODULOS_A_AVANZAR.md`, `docs/PLAN_DE_MODULOS.md`, `docs/MODULO_ROCKS.md`, `docs/MODULO_HABITS.md`, el esquema real en `src/main/resources/db/migration/V1__baseline_renaser.sql`, y los módulos Java ya construidos (`points`, `rocks`, `habits`, `phasecontracts`, `support`) como plantilla estructural. **No se leyó ni se usó como referencia el código Next.js viejo** (ninguna de las copias en `Backend90dias`/`renaser backend`/`renaser90 dias`) — este es un backend nuevo, reconstruido desde el esquema y CLAUDE.MD, no una migración literal. Donde el esquema no deja una regla de negocio clara, se documenta como pregunta abierta en vez de inventarla.

---

## 0. Estado

🔄 **Construido, sin ejecutar `./mvnw clean test` todavía** (regla del encargo: este agente no corre Maven — lo corre el supervisor).

**Actualización 2026-08-26:** se cerraron los huecos #19 (`GET /api/v1/evidence`) y #20 (`GET /api/v1/admin/evidence`) — ver §12. Todo lo demás de este documento (§1–§11) sigue vigente tal como se construyó el 2026-08-25; §12 es la única sección nueva.

**Actualización 2026-08-26 (2):** el "override" (pregunta abierta #5) tenía respuesta — el supervisor localizó el backend viejo en `C:\Users\Usuario\Documents\Backend90dias\RenaserBack` (esta sesión lo había buscado y no lo encontró; el agente de `rocks` sí lo tenía disponible). Era un hueco real, no un remapeo del cliente: `anular` no revertía puntos. Ya está cerrado — ver §12.4.

---

## 1. Qué se construyó

```
evidence/
├── package-info.java                          @ApplicationModule("Evidence")
├── api/
│   ├── package-info.java                      @NamedInterface("api")
│   ├── TipoEvidencia.java                      enum, espejo de tipo_evidencia
│   ├── EstadoValidacion.java                   enum, espejo de estado_validacion (maquina de estados)
│   ├── DestinoEvidencia.java                   sealed interface: RegistroHabito|RocaDiaria|RegistroEspiritu
│   └── RegistrarEvidenciaPort.java             puerto publico — unica puerta de entrada para otros modulos
├── domain/model/evidencia/
│   ├── Evidencia.java                          aggregate root — la maquina de estados vive aca
│   └── EvidenciaId.java
├── application/
│   ├── ports/in/evidencia/    ConsultarEvidenciaUseCase, RevisarManualmenteUseCase,
│   │                          AnularVeredictoUseCase, ProcesarColaValidacionUseCase
│   ├── ports/out/evidencia/   LoadEvidenciaPort, SaveEvidenciaPort
│   ├── ports/out/ia/          ValidacionIAPort, ResultadoValidacionIA (APROBADA/RECHAZADA/NO_DISPONIBLE)
│   └── services/              EvidenciaService (implementa RegistrarEvidenciaPort + los 4 in-ports)
└── infrastructure/adapter/
    ├── in/rest/                EvidenciaController (consulta propia/admin), EvidenciaAdminController (revision/anulacion)
    ├── in/scheduler/           ProcesarColaValidacionScheduler (cada minuto, lote 25)
    └── out/
        ├── persistence/        EvidenciaJpaEntity (dueña real de `evidencias`) + mapper + adapter + repo
        └── ia/                 NoOpValidacionIAAdapter (siempre NO_DISPONIBLE — ver §3)
```

### 1.1 Endpoints

| Método | Ruta | Caso de uso | Restricción |
|---|---|---|---|
| GET | `/api/v1/evidence/{id}` | `ConsultarEvidenciaUseCase` | dueño, o ADMIN/ALCHEMIST |
| GET | `/api/v1/evidence` | `ListarEvidenciaUseCase` | dueño (propia), MENTOR asignado (con `participanteId`), o ADMIN/ALCHEMIST — hueco #19, ver §12 |
| GET | `/api/v1/admin/evidence` | `ListarEvidenciaAdminUseCase` | ADMIN/ALCHEMIST, sin scoping — hueco #20, ver §12 |
| POST | `/api/v1/admin/evidence/{id}/review` | `RevisarManualmenteUseCase` | ADMIN/ALCHEMIST, evidencia en `REVISION_MANUAL` |
| POST | `/api/v1/admin/evidence/{id}/void` | `AnularVeredictoUseCase` | ADMIN/ALCHEMIST, evidencia `VALIDA`/`RECHAZADA` |

No hay endpoint público de "registrar evidencia" — eso solo pasa por `RegistrarEvidenciaPort` (llamado por `rocks`/`habits`), ver decisión E-1.

---

## 2. Decisión E-1 — `RegistrarEvidenciaPort` es a la vez el puerto público y el "in-port"

No se construyó un `RegistrarEvidenciaUseCase` interno separado del `RegistrarEvidenciaPort` público, aunque el encargo original los listaba por separado. Motivo: nada dentro de `evidence` expone "registrar" por su propio REST — los únicos llamadores son `rocks` y `habits`, vía el puerto público. Un `UseCase` intermedio que solo reenviara al mismo `EvidenciaService` habría sido una capa sin propósito. Mismo criterio que `points.api.AjustarPuntosPort`, implementado directamente por `PuntajeService` sin un `AjustarPuntosUseCase` duplicado para esa firma exacta (ahí sí hay un `AjustarPuntosUseCase` distinto, pero porque `points` además expone un ajuste manual por su propio REST con una forma de comando distinta — no es el mismo caso).

## 3. Decisión E-2 — `TipoEvidencia`/`EstadoValidacion` viven en `evidence.api`, no en `domain`

El encargo original listaba `TipoEvidencia`/`EstadoValidacion` dentro de `domain/model/evidencia/`. Se movieron a `evidence.api` en su lugar, replicando la decisión RK-1 ya tomada en `rocks` para `points.api.MotivoPuntos` (ver `docs/MODULO_ROCKS.md` RK-1) y H-1 en `habits`: son parámetros de `RegistrarEvidenciaPort`, la única puerta pública del módulo. Dejarlos en `domain/` habría obligado a `rocks`/`habits` a importar un tipo fuera de `@NamedInterface("api")`, rompiendo `ArchitectureTest.modulesDoNotLeakInternals`. Un solo enum cada uno, sin copia paralela en `domain` — `Evidencia` los usa directo desde `evidence.api`.

## 4. Decisión E-3 — el arco exclusivo se modela con un `sealed interface`, no con 3 columnas nullable validadas a mano

`DestinoEvidencia` (`evidence.api`) es un `sealed interface` con tres implementaciones (`RegistroHabito`, `RocaDiaria`, `RegistroEspiritu`), cada una envolviendo el único UUID que le corresponde. En vez de replicar el CHECK `evidencia_un_destino` (tres campos nullable + validación en runtime, como hacía el INSERT nativo que `rocks` usaba antes de que este módulo existiera), un estado inválido — cero o dos destinos a la vez — es **irrepresentable en el tipo**, no solo rechazado (CLAUDE.MD §5.4.7: "sealed interface + pattern matching para resultados con variantes cerradas"). La entidad JPA (`EvidenciaJpaEntity`) sigue teniendo las tres columnas nullable porque esa es la forma física real de la tabla; el mapper traduce entre el sealed type y las tres columnas.

## 5. Decisión E-4 — la máquina de estados de validación vive en `Evidencia` (dominio), no en el adapter de IA

`Evidencia.aprobarPorIa()`/`rechazarPorIa()`/`registrarIntentoFallido()`/`revisarManualmente()`/`anularVeredicto()` son los únicos lugares donde `estadoValidacion` cambia. El límite de reintentos (`MAX_INTENTOS_IA = 3`, CHECK `intentos_ia BETWEEN 0 AND 3`) y el fallback a `REVISION_MANUAL` son lógica de dominio pura (CLAUDE.MD §7: "es lógica de dominio, no de infraestructura, vive en `evidence/domain`, no en el adapter de Gemini"), 100% testeable sin Spring ni Postgres. Esto significa que **el camino de fallback a revisión humana ya está completo y probado** aunque la IA real no exista todavía — ver §6.

---

## 6. SIN IA — alcance explícito de esta pasada

Por decisión explícita del encargo, esta construcción **no integra Gemini/Spring AI**. `spring.ai.*` sigue excluido en `application.yaml` (D-39, sin tocar — fuera del alcance de este agente).

- `ValidacionIAPort` (`application/ports/out/ia/`) define la forma que necesita el dominio: `ResultadoValidacionIA validar(Evidencia evidencia)`, con el resultado cerrado a `APROBADA`/`RECHAZADA`/`NO_DISPONIBLE`.
- `NoOpValidacionIAAdapter` (`infrastructure/adapter/out/ia/`) es la única implementación: **siempre devuelve `NO_DISPONIBLE`** y loguea un `WARN` — mismo patrón exacto que `shared/infrastructure/storage/NoOpAlmacenamientoAdapter` para S3 (D-34).
- `ProcesarColaValidacionScheduler` (cada minuto, lote de 25, `FOR UPDATE SKIP LOCKED` sobre `evidencias_cola_ia_idx`) SÍ está completo y funcional. Con `NoOpValidacionIAAdapter` siempre respondiendo `NO_DISPONIBLE`, cada corrida simplemente incrementa `intentosIa` de las evidencias `PENDIENTE` hasta que, al tercer intento, caen a `REVISION_MANUAL`. **Esto es correcto e intencional, no un bug**: el camino de fallback a revisión humana queda completamente funcional y probado (ver `EvidenciaTest.tresIntentosFallidosCaeARevisionManual` y `EvidenciaServiceTest.procesarLoteSinIaIncrementaIntentosHastaRevisionManual`); solo la IA real está pendiente para una Ola futura.
- Cuando se integre IA real, el trabajo es: implementar un `GeminiValidacionIAAdapter` (o el nombre que corresponda) que reemplace a `NoOpValidacionIAAdapter`, sin tocar `Evidencia` ni `EvidenciaService` — exactamente el beneficio de tener esto detrás de un puerto (CLAUDE.MD §9).

---

## 7. RK-2 (rocks) — cerrado

`rocks` ya no escribe evidencia por SQL nativo. Se borraron `rocks/application/ports/out/rocadiaria/RegistrarEvidenciaRocaPort.java` y `rocks/infrastructure/adapter/out/persistence/evidencia/RegistrarEvidenciaRocaPersistenceAdapter.java` (y su test de integración). `RocaDiariaService.completar()` ahora llama a `evidence.api.RegistrarEvidenciaPort.registrar(...)`, dentro de la misma transacción que completa la roca (misma garantía atómica que antes). La semántica es idéntica: `estadoValidacion=PENDIENTE`, `intentosIa=0`, `esPrincipal=true` siempre (una Roca Diaria se completa una única vez, con una evidencia que es por definición la principal). `TipoEvidenciaRoca` (espejo local de `rocks`) se traduce a `evidence.api.TipoEvidencia` con un mapper de una línea (`aEvidenceTipo`) — `rocks` conserva su propio enum local porque sigue siendo su vocabulario de dominio interno (misma razón por la que `ColorPareto`/`EjeObjetivo` no se movieron a ningún lado).

Ver `docs/MODULO_ROCKS.md` §5 (tabla RK-N), marcado como RESUELTO.

## 8. D-H6 (habits) — cerrado

`habits` gana `SubirEvidenciaRegistroUseCase` (`application/ports/in/registro/`), implementado por un servicio nuevo `EvidenciaRegistroService` (separado de `RegistroService` a propósito: `RegistroService` ya estaba cerca del techo de tamaño de clase de CLAUDE.MD §5.4.8, y "subir evidencia" es una operación independiente de "completar" — un hábito con `ExigenciaEvidencia.OPCIONAL` puede completarse sin evidencia). Nuevo endpoint `POST /api/v1/habit-tracks/{id}/evidence`, mismo estilo de rutas que el resto de `HabitTrackController`. `EvidenciaRegistroService` reutiliza el mismo guard `requireSelf` (pertenencia + cuenta no suspendida) que ya usa `RegistroService`, contra `ConsultarProgresoParticipanteHabitsPort` — sin inventar un mecanismo nuevo.

A diferencia de `rocks` (donde completar una Roca Diaria y subir su evidencia son el mismo paso, `esPrincipal=true` siempre), en `habits` subir evidencia **no** otorga puntos ni cambia el estado del registro — `CompletarRegistroUseCase` sigue siendo el único que hace eso. `esPrincipal=false` siempre (el CHECK `principal_solo_en_roca` de la tabla ni lo permitiría para un destino `RegistroHabito`).

Ver `docs/MODULO_HABITS.md` §6 (tabla de deudas), marcado como RESUELTO.

---

## 9. Pruebas

| Tipo | Cobertura |
|---|---|
| Unit dominio | `EvidenciaTest` — arco exclusivo (vía `DestinoEvidencia` sealed), media-o-texto, GPS coherente, `esPrincipal` solo en Roca, máquina de estados completa (PENDIENTE→VALIDA, PENDIENTE→RECHAZADA, 3 fallos consecutivos→REVISION_MANUAL, revisión manual aprueba/rechaza, anulación desde VALIDA y desde RECHAZADA, transiciones inválidas rechazadas), **override (§12.4): `anularVeredicto` idempotente sobre `ANULADA_ADMIN` (no pisa notas, devuelve `false`), devuelve `true` y apaga `penalizacionAplicada` cuando había una (vía `Evidencia.rehydrate`, la única forma de fijar esa bandera hoy), devuelve `false` sin penalización** |
| Unit aplicación | `EvidenciaServiceTest` (Mockito) — actor suspendido no registra evidencia propia (defensa en profundidad), dueño ve su evidencia, actor ajeno sin admin rechazado, admin sí puede, TRAINEE no puede revisar/anular, MENTOR no puede anular, admin suspendido rechazado, cola de validación SIN IA incrementa intentos (con `NoOpValidacionIAAdapter` real vía el mock del puerto, determinístico), **override (§12.4): `anular` idempotente de punta a punta (segunda llamada no lanza, no reescribe, no vuelve a tocar `points.api`), revierte puntos vía `AjustarPuntosPort.ajustar(..., INVALID_EVIDENCE_REVOKED, PENALIZACION_EVIDENCIA_INVALIDA_PUNTOS, ...)` cuando había penalización, nunca llama a `points.api` cuando no la había** |
| Unit aplicación (habits) | `EvidenciaRegistroServiceTest` — actor distinto del dueño rechazado, actor suspendido rechazado, registro inexistente, delega correctamente en `RegistrarEvidenciaPort` con destino `RegistroHabito` y `esPrincipal=false` |
| Unit adapter | `NoOpValidacionIAAdapterTest` — siempre `NO_DISPONIBLE` |
| Integración Testcontainers | `EvidenciaPersistenceAdapterTest` — round-trip JPA completo, `pendientesLote` respeta orden y filtro por estado, índice único `evidencias_principal_uk` (dos evidencias `esPrincipal=true` para la misma roca → `DataIntegrityViolationException`), CHECK `evidencia_un_destino` (cero destinos, dos destinos) y CHECK `evidencia_media_o_texto` — estos dos últimos vía INSERT nativo porque el dominio hace esos estados irrepresentables en Java, así que la única forma de probar que la *base* los rechaza es rodeando el dominio |
| Integración (rocks, actualizada) | `RocaDiariaServiceTest` actualizado para mockear `evidence.api.RegistrarEvidenciaPort` en vez del puerto borrado — sigue pasando con la misma semántica |
| Unit aplicación (listados, huecos #19/#20) | `EvidenciaServiceTest` — dueño lista la propia sin indicar `participanteId`, TRAINEE no puede pedir la de otro, ADMIN lista cualquiera, MENTOR sin `participanteId` rechazado, MENTOR asignado autorizado, MENTOR NO asignado rechazado (mismo bug que E-38), actor suspendido rechazado en ambos listados, TRAINEE/MENTOR rechazados en el listado admin, ADMIN suspendido rechazado en el listado admin, truncado de página a `limite` con `siguienteCursor` cuando el puerto devuelve `limite+1`, comando rechaza `desde > hasta` |
| Integración Testcontainers (listados) | `EvidenciaPersistenceAdapterTest.buscar*` — orden por `creadoEn` descendente + cursor de keyset, filtro por estado, filtro por tipo de destino (`REGISTRO_HABITO` vacío porque no hay fixture de ese destino), filtro por rango de fechas, filtro por `participanteId` (no filtra evidencia de otro participante) |

**Lo que quedó explícitamente sin verificar** (CLAUDE.MD §0.2): no se ejecutó `./mvnw clean test` ni `ArchitectureTest` — el supervisor los corre. Puntos de mayor riesgo si algo falla:

- El naming de los enums Postgres nativos (`TipoEvidenciaJpa`→`tipo_evidencia`, `EstadoValidacionJpa`→`estado_validacion`) sigue el mismo patrón (`@Enumerated(STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`, nombre de clase Java menos el sufijo `Jpa` en snake_case) ya usado en `users`/`points`/`rocks` — no verificado en vivo contra Postgres real por este agente, mismo riesgo que ya cargan esos módulos.
- El `@Lock(PESSIMISTIC_WRITE)` + hint de lock timeout -2 sobre `pendientesLote` (mismo idiom que `calendar.SpringDataRecordatorioRepository`) — no se verificó con múltiples instancias concurrentes reales, solo con Testcontainers de una sola conexión.
- La reconciliación de `RocaDiariaServiceTest`: se actualizó a mano contra el archivo tal como quedó al momento de esta tarea; si otro agente lo tocó en paralelo después, puede haber conflicto de merge.
- **La reversión de puntos del override (§12.4) está probada solo con Mockito** (`AjustarPuntosPort` mockeado en `EvidenciaServiceTest`) — no hay un test de integración cruzado `evidence`+`points` contra Postgres real que confirme que `PuntajeService.ajustar(..., INVALID_EVIDENCE_REVOKED, ...)` efectivamente mueve el saldo. No se encontró ningún test de integración cruzado entre dos módulos en el resto del repo (cada módulo prueba su persistencia por separado), así que esto sigue el mismo patrón existente, no es un hueco nuevo introducido acá — pero es el punto de mayor riesgo si algo falla en el camino real.

**Deuda de bitácora:** no se encontró ningún error/bug real de entorno durante la construcción (solo decisiones de diseño, documentadas como E-N arriba) — no se agregó una entrada artificial a `docs/BITACORA_ERRORES.md`.

---

## 10. Qué queda explícitamente fuera de alcance

- **Integración real de IA** (Gemini/Vertex vía Spring AI `ChatClient`) — Ola futura, ver §6. `spring.ai.*` sigue excluido en `application.yaml` (D-39, no tocado).
- **`penalizacionAplicada` — solo la mitad quedó resuelta.** La REVERSIÓN (`anular` → `points.api.MotivoPuntos.INVALID_EVIDENCE_REVOKED` cuando la bandera está en `true`) **ya está construida**, ver §12.4. Lo que sigue sin construir es la APLICACIÓN (quién pone la bandera en `true` la primera vez, con `MotivoPuntos.INVALID_EVIDENCE`) — sigue dependiendo de la integración real con IA (`rechazarPorIa` no ocurre de forma realista con el `NoOp`) y de una decisión de negocio no confirmada (pregunta abierta #2).
- **`publicadaEnMuro`**: columna presente, sin ningún caso de uso que la togglee — pertenece conceptualmente a `community` (el "Muro"), fuera del alcance de este módulo.
- ~~**Listado de evidencia por participante/por estado**~~ — **RESUELTO**, ver §12 (huecos #19/#20).

## 11. Preguntas abiertas (CLAUDE.MD §0.6)

1. **¿Quién puede revisar manualmente / anular un veredicto — solo ADMIN/ALCHEMIST, o también MENTOR/MENTOR_LEAD?** Se restringió a ADMIN/ALCHEMIST por el mismo criterio ya usado en `support.TicketSoporteService.requireAdmin` (el único precedente de "acción administrativa sobre contenido de otro usuario" en el repo) — no confirmado por negocio específicamente para evidencia. **Misma pregunta aplica ahora a `ListarEvidenciaAdminUseCase` (§12)**: se restringió al mismo par ADMIN/ALCHEMIST por consistencia con `revisar`/`anular`, no porque el negocio lo haya confirmado para el listado.
2. **¿Debe `evidence` disparar `MotivoPuntos.INVALID_EVIDENCE` hacia `points` cuando una evidencia se rechaza — y con qué disparador exacto?** `INVALID_EVIDENCE_REVOKED` (la reversión) **ya está resuelto y construido**, ver §12.4 — esta pregunta ahora es solo sobre el otro lado, aplicar la penalización por primera vez. El backend viejo (`Backend90dias/RenaserBack/src/features/evidence-ai/service.ts:34-46,96-100`) confirma que **era automático, no una acción de admin**: `processOneEvidence` aplicaba la penalización (`-10` puntos, `INVALID_EVIDENCE`) apenas la IA marcaba una evidencia inválida, **y solo para evidencia de HÁBITO** (`relatedEntityType === 'HABIT'` — nunca ROCA ni ESPÍRITU, "league points is a habits-only mechanic"). No implementado en este alcance porque sigue dependiendo de la integración real de IA (§6): con el `NoOp`, `rechazarPorIa` no ocurre de forma realista, así que no hay un punto real donde enganchar el disparador todavía. Cuando se construya, el criterio ya está confirmado: automático, en `rechazarPorIa` (o donde se traduzca `ResultadoValidacionIA.RECHAZADA`), acotado a `DestinoEvidencia.RegistroHabito`.
3. **¿El scheduler de la cola debe correr cada minuto, o un intervalo distinto?** Se usó el mismo intervalo que `calendar.DespacharRecordatoriosScheduler` (cada minuto) por consistencia, sin que el esquema o CLAUDE.MD especifiquen un valor — cuando haya IA real con costo por invocación, este intervalo probablemente deba revisarse (llamar a Gemini cada minuto para un lote vacío es gasto innecesario, aunque `procesarLote()` no hace ninguna llamada de red si `pendientesLote` devuelve vacío).
4. **¿Un MENTOR debería poder listar la evidencia de TODOS sus aprendices sin indicar `participanteId` uno por uno?** No implementado — ver §12.2: no existe en `users.api.ParticipacionProgramaFinder` un método "aprendices asignados a este mentor" (búsqueda inversa), y agregarlo está fuera del alcance de este agente (`users` es de otro agente). Con lo que hay hoy, un MENTOR debe pedir explícitamente `participanteId` por cada aprendiz; queda acotado con `requireMentorAsignado` (mismo criterio que `support.TicketMentorService`, E-38).
5. ~~**El "override" de un paso del frontend viejo — ¿mapea a `review`, a `void`, o requiere un caso de uso nuevo?**~~ — **RESUELTO 2026-08-26**, ver §12.4. El backend viejo SÍ existía en el disco (`C:\Users\Usuario\Documents\Backend90dias\RenaserBack`, no encontrado por la búsqueda de esta sesión — ver §12.3 para el detalle del intento fallido); el supervisor lo localizó y confirmó la semántica exacta contra `evidence-ai/service.ts:174` (`overrideEvidence`). Era un hueco real (`anular` no revertía puntos), no un remapeo de cliente — ya está construido.

---

## 12. Huecos #19 y #20 — listados de evidencia

**Fecha:** 2026-08-26.

### 12.1 Qué se construyó

Dos casos de uso de lectura, ambos implementados por `EvidenciaService`, ambos apoyados en un único puerto de salida nuevo:

```
application/ports/out/evidencia/LoadEvidenciaPort.java
    + buscar(FiltroEvidencia filtro, Instant cursor, int limite): List<Evidencia>
    + record FiltroEvidencia(participanteId, estado, tipoDestino, desde, hasta)   -- todos opcionales

application/ports/in/evidencia/
    ListarEvidenciaUseCase.java         -- hueco #19, GET /api/v1/evidence
    ListarEvidenciaAdminUseCase.java    -- hueco #20, GET /api/v1/admin/evidence
    (TipoDestino vive anidado en ListarEvidenciaUseCase: REGISTRO_HABITO/ROCA_DIARIA/REGISTRO_ESPIRITU,
     el filtro "tipo de entidad relacionada" que pide el encargo, sin exponer el id puntual)

infrastructure/adapter/out/persistence/
    EvidenciaSpecifications.java      -- nueva, ver decisión E-5
    SpringDataEvidenciaRepository.java -- + JpaSpecificationExecutor<EvidenciaJpaEntity>
    EvidenciaPersistenceAdapter.java   -- + buscar(...)

infrastructure/adapter/in/rest/
    EvidenciaPageResponse.java   -- nueva, {evidencias, nextCursor}, compartida por los dos controllers
    EvidenciaController.java     -- + GET /api/v1/evidence
    EvidenciaAdminController.java -- + GET /api/v1/admin/evidence
```

Filtros expuestos por query param en ambos endpoints: `participanteId`, `estado` (`EstadoValidacion`), `tipoDestino` (`REGISTRO_HABITO`/`ROCA_DIARIA`/`REGISTRO_ESPIRITU`), `desde`/`hasta` (ISO-8601, sobre `creadoEn`), `cursor` (keyset). Paginación por keyset, más nueva primero (`creadoEn` descendente), mismo contrato que `community` (`GET /api/v1/wall`): el servidor trae `limite+1` filas, si sobra una se trunca a `TAMANO_PAGINA=20` y se expone el `creadoEn` de la última fila como `nextCursor`; sin sobrante, `nextCursor=null`. No hay offset en ningún punto del camino.

**Decisión E-5 — `Specification`/`JpaSpecificationExecutor` en vez de un método JPQL por combinación de filtros.** `buscar` tiene hasta 5 filtros opcionales más el cursor. El patrón que ya usa `community.SpringDataPublicacionRepository` (un método JPQL explícito por combinación de filtro presente/ausente, para esquivar **E-31** — Postgres no puede inferir el tipo de un parámetro que solo aparece en `:param IS NULL`) escala mal acá: con 2 filtros opcionales son 4 métodos, con hasta 6 (5 filtros + cursor) serían hasta 2⁶. En cambio, `Specification` compone un predicado por filtro **solo cuando el filtro tiene valor** (`EvidenciaSpecifications.filtro`) — nunca se genera un `:param IS NULL`, así que E-31 no puede repetirse por construcción, sin necesidad de partir en métodos. Es la primera vez que este patrón se usa en el repo (`JpaSpecificationExecutor` no aparecía en ningún otro módulo antes de esta tarea); si otro módulo llega a necesitar 3+ filtros opcionales combinables, este es el precedente a seguir en vez de repetir el patrón de métodos de `community`.

### 12.2 Autorización (lo más importante de la tarea A)

`ListarEvidenciaUseCase` (`GET /api/v1/evidence`) resuelve el filtro según el rol del actor, dentro de `EvidenciaService.resolverFiltroSegunRol`:

- **ADMIN/ALCHEMIST**: el filtro pasa tal cual, `participanteId` opcional — puede ver cualquier evidencia.
- **MENTOR**: `participanteId` es **obligatorio** (403 si viene vacío) y tiene que ser el mentor asignado a ese aprendiz — verificado contra `users.api.ParticipacionProgramaFinder.deParticipante(participanteId).mentorId()`, exactamente el mismo puerto y el mismo criterio que ya usa `support.TicketMentorService.requireMentorAsignado` para el mismo problema (evitar la reincidencia de **E-38**: "rol MENTOR correcto" no es lo mismo que "asignado a este aprendiz"). **No existe una forma de que un MENTOR pida "todos mis aprendices" en una sola llamada** — ver pregunta abierta #4: eso requeriría un método de búsqueda inversa en `ParticipacionProgramaFinder` (p. ej. `aprendicesDeMentor(UserId)`) que hoy no existe y que no se agregó porque `users` es responsabilidad de otro agente en esta tarea.
- **Cualquier otro rol** (TRAINEE, MENTOR_LEAD): solo puede listar la propia — si no manda `participanteId` se fuerza a `actorId`; si manda uno distinto, 403.
- Actor inexistente o `SUSPENDIDO`: 403 en cualquier caso, antes de resolver el filtro (mismo patrón fail-closed que el resto del módulo).

`ListarEvidenciaAdminUseCase` (`GET /api/v1/admin/evidence`) es deliberadamente más simple: **sin ningún scoping de dueño ni de mentor**, un único gate de rol (`requireAdmin`, el mismo helper que ya usan `revisar`/`anular`) — ADMIN/ALCHEMIST, o 403. Es la vista de plataforma para el panel de revisión, no un listado de autoservicio.

### 12.3 El análisis de "override" pedido por el encargo

El encargo pidió explícitamente **no inventar** un tercer caso de uso ni cambiar la semántica de `review`/`void`, y en cambio documentar en qué se diferencian del "override de un paso" del frontend viejo. **No se pudo contrastar contra el código real del frontend viejo**: se buscó en todo `C:\` un clon Next.js/TypeScript del backend (los nombres que cita este documento en el encabezado — `Backend90dias`/`renaser backend`/`renaser90 dias`) y no se encontró ninguno accesible desde esta máquina; lo único presente son copias de la app **React Native** (`C:\renaserPlayStore`, `C:\RenaserPlayStoreCopy`, `C:\renaser`, `C:\Renaser 90 dias rediseno\renaser90-app`), que consumen la API pero no son el backend. El análisis que sigue es, entonces, sobre la semántica real de este backend Java — comparada contra lo que un "override de un paso" *implicaría* por su nombre — no contra el código viejo.

**Qué hace cada uno hoy, exactamente** (`Evidencia.java`, dominio):

| Caso de uso | Precondición de estado | Resultado | Naturaleza |
|---|---|---|---|
| `revisar` (`review`) | `estadoValidacion == REVISION_MANUAL` (es decir: **todavía no hay ningún veredicto**, la IA no decidió o no está disponible) | `VALIDA` o `RECHAZADA`, según `aprobar` | **Emitir el veredicto que falta** |
| `anular` (`void`) | `estadoValidacion == VALIDA` o `RECHAZADA` (es decir: **ya hay un veredicto**, de IA o de una revisión manual previa) | `ANULADA_ADMIN` | **Invalidar un veredicto existente**, sin emitir uno nuevo |

Punto clave, verificado en el código: **`ANULADA_ADMIN` es un estado terminal.** Ni `revisarManualmente` (exige `REVISION_MANUAL`) ni `anularVeredicto` (exige `VALIDA`/`RECHAZADA`) aceptan una evidencia `ANULADA_ADMIN` — no hay, en este módulo, ningún camino para volver a decidir sobre una evidencia una vez anulada.

**Comparación contra lo que "override de un paso" sugiere por su nombre**, en los tres sentidos posibles:

1. *"Resolver en un solo click una evidencia que quedó sin decisión"* (p. ej. cayó a `REVISION_MANUAL` porque la IA no respondió) → esto **ya es exactamente `review`**, un solo `POST` con `aprobar=true/false`. Alcanza con que el cliente llame a `review` cuando la evidencia está en `REVISION_MANUAL`.
2. *"Anular un veredicto sin reemplazarlo por otro"* (p. ej. la IA aprobó pero el admin quiere invalidarlo sin decir qué era lo correcto) → esto **ya es exactamente `void`**, un solo `POST`. Alcanza con remapear.
3. *"Tomar una evidencia que YA tiene un veredicto (de IA o de una revisión manual previa) y cambiarlo directamente al veredicto contrario, en una sola acción de usuario"* (p. ej. la IA aprobó, el admin quiere marcarla RECHAZADA directamente, sin dos pasos) → **esto NO existe hoy y no es un simple remapeo**: la única forma de tocar una evidencia `VALIDA`/`RECHAZADA` es `void`, que la manda a `ANULADA_ADMIN` (terminal) — no hay ningún caso de uso que la lleve de vuelta a `VALIDA` o `RECHAZADA`. Si el frontend viejo permitía esto en un solo paso, **hace falta un caso de uso nuevo** (algo como "re-decidir", que probablemente debería reabrir a `REVISION_MANUAL` y de ahí usar `review`, o directamente fijar el nuevo veredicto) — decisión de negocio que no está tomada y que **no se implementó acá** por instrucción explícita del encargo ("no inventes un tercer caso de uso").

**Recomendación (al momento de escribir esto):** sin ver el frontend viejo, no se puede saber con certeza cuál de los tres sentidos era. Si el equipo confirma que es el sentido 1 o 2, alcanza con que el cliente llame `review`/`void` según el estado actual de la evidencia (el 404/409 que ya devuelven por precondición de estado — ver `Evidencia.revisarManualmente`/`anularVeredicto`, mapeados a 409 `CONFLICT` por `GlobalExceptionHandler.handleConflict` — son señal suficiente para que el cliente sepa cuál de los dos llamar). Si es el sentido 3, es un hueco real (un cuarto caso de uso, "re-decidir sobre una evidencia ya anulada o con veredicto") que queda **fuera de esta tarea** y debería preguntarse antes de construirlo, siguiendo CLAUDE.MD §0.6.

**Esta recomendación quedó incompleta — la búsqueda del frontend viejo falló, no porque no existiera.** El supervisor tenía acceso al repo real (`C:\Users\Usuario\Documents\Backend90dias\RenaserBack` — un directorio que esta sesión no llegó a listar; el agente de `rocks`, trabajando en paralelo, sí lo tenía) y confirmó el sentido exacto. Era el **sentido 2 con un efecto adicional que este análisis no había anticipado en ninguno de los tres casos**: "anular sin reemplazar" (`void`) **más** revertir una penalización de puntos si la había. Ver §12.4 — resuelto, no es un cuarto caso de uso, es un comportamiento que le faltaba a `anular`.

### 12.4 Resolución — el "override" viejo, confirmado y cerrado (2026-08-26)

**Evidencia exacta del backend viejo** (`Backend90dias/RenaserBack`):

- `src/features/evidence-ai/service.ts:174-204`, función `overrideEvidence(callerRole, evidenceId)`, ruta `POST /api/v1/admin/evidence-review/:id/override` (`src/app/api/v1/admin/evidence-review/[id]/override/route.ts`). Comentario del propio archivo: *"Admin disagrees with the AI's invalid verdict — reverses the penalty and marks the row reviewed."*
- Semántica exacta: (1) solo ADMIN/ALCHEMIST; (2) evidencia inexistente → 404; (3) **idempotente** — si `evidence.aiOverriddenByAdmin` ya era `true`, devuelve éxito sin escribir nada; (4) si `evidence.aiPenaltyApplied` era `true`, revierte con `adjustLeaguePoints(traineeProfileId, +INVALID_EVIDENCE_PENALTY_POINTS, 'INVALID_EVIDENCE_REVOKED')`; (5) marca `aiOverriddenByAdmin=true`.
- `INVALID_EVIDENCE_PENALTY_POINTS = 10` (`evidence-ai/service.ts:21`), con el comentario "Same magnitude as Santuario's break penalty" — coincide con `habits.SesionBloqueo.PENALIZACION_ROTURA_PUNTOS` en este backend, confirmando el valor.

**Por qué NO hacía falta una columna ni un estado nuevo (D-40 respetado).** `docs/db/AUDITORIA_REDISENO_BD.md` fila P-14 documenta, con fecha anterior a este hallazgo, que el enum `estado_validacion` **reemplaza 4 booleanos**: `validated_by_ai`, `ai_valid`, `ai_penalty_applied`, `ai_overridden_by_admin`. Es decir: `ANULADA_ADMIN` **ya es**, por diseño, "overrideada por admin" — el propio rediseño de base de datos ya había absorbido `aiOverriddenByAdmin` antes de que esta pregunta se hiciera. No había nada que agregar al esquema.

**Qué le faltaba a `anular` para ser el override real — dos cosas, ambas ahora construidas en `Evidencia.anularVeredicto`/`EvidenciaService.anular`:**

1. **Idempotencia real.** Antes, una segunda llamada a `void` sobre una evidencia ya `ANULADA_ADMIN` lanzaba `IllegalStateException` (409 `CONFLICT`). Ahora: `EvidenciaService.anular` detecta el estado ya-anulado y devuelve la evidencia sin escribir nada (ni `save`, ni ajuste de puntos) — dos admins resolviendo la misma fila ya no es un error, mismo comportamiento que el viejo `overrideEvidence`.
2. **Reversión de puntos.** `Evidencia.anularVeredicto(String)` cambió de `void` a `boolean`: sigue moviendo el estado a `ANULADA_ADMIN`, pero además apaga `penalizacionAplicada` si estaba prendida y devuelve `true` en ese caso — la señal para que `EvidenciaService` (el único lugar del módulo que conoce `points.api`, nunca el dominio) llame a `AjustarPuntosPort.ajustar(participanteId, MotivoPuntos.INVALID_EVIDENCE_REVOKED, Evidencia.PENALIZACION_EVIDENCIA_INVALIDA_PUNTOS, nota)`.

**No se tocó ningún endpoint ni se agregó un tercero.** Sigue siendo `POST /api/v1/admin/evidence/{id}/void` — el mismo que ya existía, con el comportamiento que le faltaba. No se creó `POST .../override`: el nombre del backend viejo era literal a su implementación (booleanos separados), no una operación conceptualmente distinta una vez que `estado_validacion` unificó todo en un enum.

**Lo que NO se construyó, deliberadamente, y sigue abierto:** quién aplica la penalización la PRIMERA vez (`MotivoPuntos.INVALID_EVIDENCE`, `-10`, disparado por `rechazarPorIa` según el backend viejo, solo para evidencia de HÁBITO) — eso depende de la integración real de IA, ver pregunta abierta #2 actualizada. Sin eso, `penalizacionAplicada` nunca se pone en `true` en producción hoy, así que la reversión que se acaba de construir queda correcta pero dormida — mismo patrón exacto que ya toleraba este módulo con el fallback a `REVISION_MANUAL` (§6): la lógica está completa y probada, el disparador real todavía no existe.

**Pruebas agregadas:** `EvidenciaTest.anularVeredictoEsIdempotente`, `anularVeredictoSenalaReversionDePenalizacionYLaApaga` (usa `Evidencia.rehydrate` para construir una evidencia con `penalizacionAplicada=true`, ya que ningún caso de uso la pone en `true` todavía), `anularVeredictoSinPenalizacionDevuelveFalse`; `EvidenciaServiceTest.traineeNoPuedeAnular`, `mentorNoPuedeAnular`, `anularEsIdempotente`, `anularRevierteLaPenalizacionCuandoEstabaAplicada`, `anularSinPenalizacionNoAjustaPuntos`.
