# Módulo `evidence` — validación de evidencia (SIN IA en este alcance)

**Fecha:** 2026-08-25
**Ola:** 5 en el plan original (`docs/PLAN_DE_MODULOS.md`), pero esta pasada construye **solo la parte sin IA** — la integración real con Gemini/Vertex vía Spring AI queda pospuesta, por decisión explícita del encargo.
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/MODULOS_A_AVANZAR.md` §evidence · `docs/MODULO_ROCKS.md` (RK-2, cerrado acá) · `docs/MODULO_HABITS.md` (D-H6, cerrado acá) · `docs/MODULO_POINTS.md`/`MODULO_PHASECONTRACTS.md`/`MODULO_SUPPORT.md` (patrones replicados)

**Fuente de verdad usada para esta tarea**: `CLAUDE.MD`, `docs/MODULOS_A_AVANZAR.md`, `docs/PLAN_DE_MODULOS.md`, `docs/MODULO_ROCKS.md`, `docs/MODULO_HABITS.md`, el esquema real en `src/main/resources/db/migration/V1__baseline_renaser.sql`, y los módulos Java ya construidos (`points`, `rocks`, `habits`, `phasecontracts`, `support`) como plantilla estructural. **No se leyó ni se usó como referencia el código Next.js viejo** (ninguna de las copias en `Backend90dias`/`renaser backend`/`renaser90 dias`) — este es un backend nuevo, reconstruido desde el esquema y CLAUDE.MD, no una migración literal. Donde el esquema no deja una regla de negocio clara, se documenta como pregunta abierta en vez de inventarla.

---

## 0. Estado

🔄 **Construido, sin ejecutar `./mvnw clean test` todavía** (regla del encargo: este agente no corre Maven — lo corre el supervisor).

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
| Unit dominio | `EvidenciaTest` — arco exclusivo (vía `DestinoEvidencia` sealed), media-o-texto, GPS coherente, `esPrincipal` solo en Roca, máquina de estados completa (PENDIENTE→VALIDA, PENDIENTE→RECHAZADA, 3 fallos consecutivos→REVISION_MANUAL, revisión manual aprueba/rechaza, anulación desde VALIDA y desde RECHAZADA, transiciones inválidas rechazadas) |
| Unit aplicación | `EvidenciaServiceTest` (Mockito) — actor suspendido no registra evidencia propia (defensa en profundidad), dueño ve su evidencia, actor ajeno sin admin rechazado, admin sí puede, TRAINEE no puede revisar/anular, admin suspendido rechazado, cola de validación SIN IA incrementa intentos (con `NoOpValidacionIAAdapter` real vía el mock del puerto, determinístico) |
| Unit aplicación (habits) | `EvidenciaRegistroServiceTest` — actor distinto del dueño rechazado, actor suspendido rechazado, registro inexistente, delega correctamente en `RegistrarEvidenciaPort` con destino `RegistroHabito` y `esPrincipal=false` |
| Unit adapter | `NoOpValidacionIAAdapterTest` — siempre `NO_DISPONIBLE` |
| Integración Testcontainers | `EvidenciaPersistenceAdapterTest` — round-trip JPA completo, `pendientesLote` respeta orden y filtro por estado, índice único `evidencias_principal_uk` (dos evidencias `esPrincipal=true` para la misma roca → `DataIntegrityViolationException`), CHECK `evidencia_un_destino` (cero destinos, dos destinos) y CHECK `evidencia_media_o_texto` — estos dos últimos vía INSERT nativo porque el dominio hace esos estados irrepresentables en Java, así que la única forma de probar que la *base* los rechaza es rodeando el dominio |
| Integración (rocks, actualizada) | `RocaDiariaServiceTest` actualizado para mockear `evidence.api.RegistrarEvidenciaPort` en vez del puerto borrado — sigue pasando con la misma semántica |

**Lo que quedó explícitamente sin verificar** (CLAUDE.MD §0.2): no se ejecutó `./mvnw clean test` ni `ArchitectureTest` — el supervisor los corre. Puntos de mayor riesgo si algo falla:

- El naming de los enums Postgres nativos (`TipoEvidenciaJpa`→`tipo_evidencia`, `EstadoValidacionJpa`→`estado_validacion`) sigue el mismo patrón (`@Enumerated(STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`, nombre de clase Java menos el sufijo `Jpa` en snake_case) ya usado en `users`/`points`/`rocks` — no verificado en vivo contra Postgres real por este agente, mismo riesgo que ya cargan esos módulos.
- El `@Lock(PESSIMISTIC_WRITE)` + hint de lock timeout -2 sobre `pendientesLote` (mismo idiom que `calendar.SpringDataRecordatorioRepository`) — no se verificó con múltiples instancias concurrentes reales, solo con Testcontainers de una sola conexión.
- La reconciliación de `RocaDiariaServiceTest`: se actualizó a mano contra el archivo tal como quedó al momento de esta tarea; si otro agente lo tocó en paralelo después, puede haber conflicto de merge.

**Deuda de bitácora:** no se encontró ningún error/bug real de entorno durante la construcción (solo decisiones de diseño, documentadas como E-N arriba) — no se agregó una entrada artificial a `docs/BITACORA_ERRORES.md`.

---

## 10. Qué queda explícitamente fuera de alcance

- **Integración real de IA** (Gemini/Vertex vía Spring AI `ChatClient`) — Ola futura, ver §6. `spring.ai.*` sigue excluido en `application.yaml` (D-39, no tocado).
- **`penalizacionAplicada`**: la columna existe en la tabla y en `Evidencia`/`EvidenciaJpaEntity`, pero ningún caso de uso la setea todavía — es un efecto que depende de la integración real con `points` para evidencia rechazada (`MotivoPuntos.INVALID_EVIDENCE`/`INVALID_EVIDENCE_REVOKED`, ya existen en `points.api` pero nadie los dispara desde `evidence` en este alcance). Se deja para cuando la IA real exista, porque hoy `rechazarPorIa`/`revisarManualmente(aprobar=false)` nunca ocurren de forma realista (el `NoOp` nunca rechaza, solo devuelve `NO_DISPONIBLE`).
- **`publicadaEnMuro`**: columna presente, sin ningún caso de uso que la togglee — pertenece conceptualmente a `community` (el "Muro"), fuera del alcance de este módulo.
- **Listado de evidencia por participante/por estado** (para un futuro panel admin de revisión manual) — solo se construyó consulta por id. Si hace falta un `GET /api/v1/admin/evidence?estado=REVISION_MANUAL`, es un puerto/endpoint nuevo, no construido acá.

## 11. Preguntas abiertas (CLAUDE.MD §0.6)

1. **¿Quién puede revisar manualmente / anular un veredicto — solo ADMIN/ALCHEMIST, o también MENTOR/MENTOR_LEAD?** Se restringió a ADMIN/ALCHEMIST por el mismo criterio ya usado en `support.TicketSoporteService.requireAdmin` (el único precedente de "acción administrativa sobre contenido de otro usuario" en el repo) — no confirmado por negocio específicamente para evidencia.
2. **¿Debe `evidence` disparar `MotivoPuntos.INVALID_EVIDENCE`/`INVALID_EVIDENCE_REVOKED` hacia `points` cuando una evidencia se rechaza/se revoca el rechazo?** Los motivos ya existen en `points.api` (agregados por `habits`/`rocks` en su momento) pero nada los usa todavía. No implementado en este alcance porque, sin IA real, `rechazarPorIa` nunca ocurre de forma realista y `revisarManualmente(aprobar=false)` sí podría — pero conectar eso con `points` es una decisión de negocio (¿la penalización es automática o la aplica el admin a mano?) que no está confirmada.
3. **¿El scheduler de la cola debe correr cada minuto, o un intervalo distinto?** Se usó el mismo intervalo que `calendar.DespacharRecordatoriosScheduler` (cada minuto) por consistencia, sin que el esquema o CLAUDE.MD especifiquen un valor — cuando haya IA real con costo por invocación, este intervalo probablemente deba revisarse (llamar a Gemini cada minuto para un lote vacío es gasto innecesario, aunque `procesarLote()` no hace ninguna llamada de red si `pendientesLote` devuelve vacío).
