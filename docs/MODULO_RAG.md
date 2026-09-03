# Módulo `rag` / `renasia` — diseño y decisiones

**Fecha:** 2026-08-25
**Estado:** diseñado, **no construido todavía**. Es el último de los 14 módulos.
**Insumo:** análisis del esquema real (BD congelada, D-40) + decisiones de negocio confirmadas por el dueño del proyecto el 2026-08-25.

---

## 1. Alcance

Dos funciones distintas que comparten módulo porque comparten la infraestructura de IA:

1. **Renasia** — chatbot con RAG sobre el contenido del programa. El aprendiz pregunta, el sistema busca contexto en la base de conocimiento vectorial y responde con streaming, citando las lecciones que usó.
2. **Espejo Sombra** — informe semanal generado por IA que analiza las entradas del diario del aprendiz y devuelve un patrón dominante, una distribución temporal (pasado/presente/futuro) y preguntas de confrontación.

---

## 2. Las 6 tablas (BD congelada — se usan tal cual)

| Tabla | Rol |
|---|---|
| `base_conocimiento` | Chunks vectorizados. `embedding vector(768)`, índice **HNSW** con `vector_cosine_ops` |
| `conversaciones_renasia` | 1:1 con usuario (`PK = FK`): **una sola conversación por aprendiz**, no una lista |
| `mensajes_renasia` | Mensajes. Enum `rol_mensaje_renasia` = `USUARIO`/`ASISTENTE` |
| `fuentes_mensaje_renasia` | N:M mensaje↔lección: las fuentes citadas de cada respuesta |
| `informes_espejo_sombra` | Un informe por aprendiz por semana (`UNIQUE`), con `CHECK pcts_suman_100` |
| `preguntas_confrontacion` | Hijas del informe, `orden` 1..10 |

Detalles que condicionan el diseño:
- `leccion_id` es **`text`**, no `uuid` (ids estilo Skool, FK a `lecciones.id`).
- `mensajes_renasia.usuario_id` apunta a `conversaciones_renasia`, **no** a `usuarios` — la conversación debe existir antes del primer mensaje.
- `informes_espejo_sombra` cuelga de `participantes_programa`, no de `usuarios`.

---

## 3. Decisiones tomadas

### D-45 — `VectorStorePort` propio con SQL nativo, NO `PgVectorStore` de Spring AI

**El problema, VERIFICADO contra el bytecode del JAR real (`spring-ai-pgvector-store:2.0.0`):** el nombre de tabla y el esquema **sí** son configurables (`.schemaName()`, `.vectorTableName()`, `.initializeSchema(false)`), pero **los nombres de columna están hardcodeados en el SQL de la clase** y no hay forma de remapearlos:

```sql
INSERT INTO <tabla> (id, content, metadata, embedding) VALUES (?, ?, ?::jsonb, ?)
```

Además, `PgVectorSchemaValidator` consulta `information_schema.columns` y **falla si esas columnas exactas no existen**.

Nuestra tabla se llama `base_conocimiento`, usa `contenido`/`metadatos` (español), y tiene **`tipo_fuente text NOT NULL`** más `clase`, `documento_id` y `leccion_id` que Spring AI no modela y no sabría llenar. Con la BD congelada (D-40) no podemos crear la tabla que Spring AI quiere, ni agregarle columnas a la nuestra.

**La decisión:** `VectorStorePort` es un puerto **nuestro** (no la interfaz de Spring AI), implementado con SQL nativo contra `base_conocimiento` usando el operador `<=>` (distancia coseno — coherente con el índice HNSW `vector_cosine_ops` que ya existe). De Spring AI usamos **solo el `EmbeddingModel`**, para generar el vector de 768 dimensiones.

**Por qué es la opción correcta y no un rodeo:** CLAUDE.MD §7 pide "usar pgvector sobre el mismo Postgres" — eso se cumple. Lo que no se cumpliría es depender de que una clase concreta de una librería calce con un esquema que se diseñó antes y está cerrado. Además es exactamente el patrón hexagonal del proyecto: el puerto expresa la intención de negocio (*"traeme los k fragmentos más parecidos a esta pregunta"*), el adaptador conoce la tecnología.

**Consecuencia operativa:** hay que quitar `PgVectorStoreAutoConfiguration` de la lista de exclusiones de `application.yaml`… **no**, al contrario: se mantiene excluida, porque no vamos a usar esa autoconfiguración. Sí hay que quitar las exclusiones de chat y embeddings de Google GenAI cuando lleguen las credenciales.

### D-46 — Ingesta de conocimiento: endpoint admin (respuesta del dueño, 2026-08-25)

`base_conocimiento` la puebla un **administrador** desde la aplicación, no un proceso batch externo. Implica que el módulo necesita, además del puerto de chat:
- `IndexarConocimientoUseCase` (admin) — recibe el contenido, lo trocea si hace falta, genera el embedding vía `EmbeddingPort` y persiste el chunk.
- `EmbeddingPort` + su adaptador sobre el `EmbeddingModel` de Spring AI.
- Endpoint admin protegido (ADMIN/ALCHEMIST).

### D-47 — Quién ve los informes del Espejo Sombra (respuesta del dueño)

**El propio aprendiz, su mentor, ADMIN y ALCHEMIST.** No es self-service puro: el mentor necesita verlo para acompañar.

Implicación de seguridad: el acceso del mentor debe verificar que sea **el mentor asignado a ese aprendiz**, no cualquier mentor — mismo criterio que se corrigió en `support` (bug E-38 §5: tener rol MENTOR no es lo mismo que ser *el* mentor de ese aprendiz). Se resuelve con `users.api.ParticipacionProgramaFinder`, que ya expone `mentorId`.

Además: el contenido es sensible (análisis psicológico). Aplica CLAUDE.MD §5.4.9 — **nunca loguear el contenido de un informe ni las entradas de diario que lo alimentan**.

### D-48 — Límite de uso de Renasia: en Redis, no en la BD (respuesta del dueño + decisión técnica mía)

El pedido fue: *"límites que no gasten tanto pero que también ayuden a los demás"*.

**Propuesta concreta, ajustable:** **25 mensajes por aprendiz por día**, con reinicio a medianoche en la zona horaria del participante. Es holgado para un uso real de acompañamiento (una conversación de trabajo rara vez pasa de 10-15 turnos) y corta el abuso o un bucle accidental del cliente.

**Dónde se cuenta:** en **Redis**, no en Postgres. La BD está congelada y no tiene columna de contador; agregar una está prohibido por D-40. Redis ya está en el stack (lo usa `chat` para el fanout), y un contador con TTL hasta medianoche es exactamente su caso de uso. Clave: `renasia:cuota:{usuarioId}:{fecha}`.

Al superar el límite: **429** (`RateLimitExceededException` ya existe en `shared/domain` y el `GlobalExceptionHandler` ya la traduce).

> **Asunción explícita:** el número 25 lo elegí yo, no vino del negocio. Es un parámetro de configuración (`renaser.renasia.limite-diario`), así que cambiarlo es editar el yaml, no recompilar. Si el uso real muestra que estorba o que se queda corto, se ajusta sin tocar código.

### D-49 — Marcar mensajes: NO se construye (respuesta del dueño)

El dueño decidió quitar la función de "marcar" un mensaje del chat de la IA.

**Nota sobre la BD:** las columnas `marcado_por_usuario`, `nota_marca` y `anulado_por_admin` **siguen existiendo** en `mensajes_renasia` — la BD está congelada, no se borran. Simplemente no se expone ningún caso de uso ni endpoint que las use; quedan con sus valores por defecto (`false`/`null`). Esto queda documentado para que nadie las vea vacías más adelante y crea que es un bug de persistencia.

### D-50 — `habits` debe exponer las entradas de diario

El Espejo Sombra analiza `entradas_diario`, que es **tabla del módulo `habits`**. Por las reglas de Modulith, `rag` no puede leerla directamente.

Hay que agregar a `habits/api/`:
```java
public record EntradaDiarioSummary(UUID id, UserId participanteId, LocalDate fecha,
                                    String contenidoTexto, String transcripcion) { }

public interface EntradaDiarioFinder {
    List<EntradaDiarioSummary> deLaSemana(UserId participanteId, LocalDate inicio, LocalDate fin);
}
```
Mismo patrón que `users.api.UserSummaryFinder` y `academy.api.AccesoCursoFinder`: un finder de solo lectura con DTO propio del `api`, nunca la entidad interna.

### D-81 — La búsqueda vectorial ignoraba el bloqueo de contenido de `academy` (bug real, cerrado 2026-09-03)

**El problema:** `PgVectorNativoAdapter.buscarSimilares` hacía `SELECT` sobre toda `base_conocimiento` ordenada por distancia coseno, sin `WHERE`. `academy` sí bloquea contenido por día de programa (`Curso.visibleEnCatalogoPara`/`bloqueadoPorDiaPara`, y a nivel de sección `SeccionCurso.visibleEnCatalogoPara`), pero Renasia podía citarle a un aprendiz en el día 3 el contenido de una lección del día 60 que su propio módulo de academia todavía tiene bloqueada — fuga de contenido, no solo un problema de UX.

**La solución, mismo patrón que D-50 (`habits` → `rag`):** `academy/api/` gana `LeccionesVisiblesFinder.leccionesVisiblesPara(UserId actorId)`, que devuelve la unión de ids de lecciones visibles HOY para ese actor (resuelto en 3 consultas en lote — `LoadCursoPort.listarTodos()`, la nueva `LoadSeccionCursoPort.listarTodas()` y la nueva `LoadLeccionPort.listarIdentificadores()` — nunca una consulta por curso ni por lección, mismo criterio anti N+1 que `ContarRegistrosDiariosHabitsPort`). `rag` consume esto detrás de su propio puerto, `ConsultarLeccionesVisiblesPort` (`ports/out/conversacion/`), implementado por `LeerLeccionesVisiblesAdapter` (`adapter/out/academy/`) — nunca importa tipos de `academy` fuera de `academy.api`.

**Dónde vive el filtro:** `VectorStorePort.buscarSimilares` ganó un tercer parámetro, `FiltroLecciones` (`SinFiltro` | `SoloVisibles(Set<String> leccionIds)`). Va en el WHERE de `PgVectorNativoAdapter` (`leccion_id IS NULL OR leccion_id = ANY(...)`), antes del `ORDER BY ... LIMIT` — filtrar en Java después de traer `topK` filas devolvería menos de `topK` fragmentos cuando hay candidatos bloqueados de por medio. `ConversacionRenasiaService` resuelve el conjunto visible (vía `ConsultarLeccionesVisiblesPort`) y arma el filtro; `PgVectorNativoAdapter` no sabe nada de reglas de `academy`, solo aplica el WHERE. Los chunks con `leccion_id IS NULL` (material general) nunca se filtran. `ConocimientoService` (indexación admin) no llama a `buscarSimilares` hoy — si algún día necesita buscar, usa `FiltroLecciones.sinFiltro()`.

**Consecuencia para el contrato compartido:** es el segundo cambio de firma de `VectorStorePort` (antes "firma congelada" salvo para cerrar bugs reales) — `ConversacionRenasiaServiceTest`/`PgVectorNativoAdapterTest` ya están al día.

### D-82 — Puerto y adaptador vacío del clasificador de riesgo (solo estructura, sin conectar)

Ya existían `NivelRiesgo`/`Severidad`/`EvaluacionRiesgo` en `rag/domain/model/seguridad/`. Se agregó `EvaluarRiesgoMensajePort` (`application/ports/out/seguridad/`) y su placeholder `NoOpEvaluacionRiesgoAdapter` (`infrastructure/adapter/out/seguridad/`, mismo molde que `NoOpEmbeddingAdapter`): loguea que es un placeholder y devuelve `EvaluacionRiesgo.sinSenales()`, nunca `null`.

**Deliberadamente NO incluido:** el mapeo de `EvaluacionRiesgo` a modo de respuesta (qué apaga herramientas, qué escala a mentor, qué entra en crisis) y los criterios de detección en sí — ambos son reglas sin confirmar (CLAUDE.MD §0.6) que tiene que firmar el dueño del producto y un profesional con licencia, respectivamente. Este puerto **no está conectado** a `ConversacionRenasiaService` ni a ningún caso de uso todavía.

**Por qué el `NoOp` devuelve `sinSenales()` y no un nivel alto "por las dudas":** `NivelRiesgo.CRITICO` dispara modo crisis siempre, sin importar la severidad — usarlo como default de un adaptador que nunca leyó el mensaje convertiría, el día que alguien lo conecte sin releer el javadoc, TODA conversación con Renasia en una falsa alarma de crisis permanente. `sinSenales()` es el mismo criterio que el resto de los `NoOp` del módulo: un placeholder inerte, no una mentira sobre haber evaluado algo. Queda pendiente, documentado en el javadoc de la clase: si hace falta un tercer estado "indeterminado" en `NivelRiesgo` (pregunta que el propio enum deja abierta) y, después, el mapeo completo a modo de respuesta — ninguna de las dos cosas se resuelve acá.

---

## 4. Estructura del módulo

Tres agregados reales (cada uno con identidad, ciclo de vida y repositorio propios):

```
rag/
├── package-info.java                    (@ApplicationModule)
├── api/                                  (@NamedInterface — hoy sin consumidores)
├── domain/model/
│   ├── conocimiento/                     ChunkConocimiento, ChunkConocimientoId
│   ├── conversacion/                     ConversacionRenasia (raíz), MensajeRenasia, RolMensaje, FuenteMensaje
│   └── espejosombra/                     InformeEspejoSombra (raíz), PreguntaConfrontacion,
│                                          DistribucionTemporal (VO que encapsula el invariante "suma 100")
├── application/
│   ├── ports/in/
│   │   ├── conversacion/                 PreguntarRenasiaUseCase, ObtenerHistorialUseCase
│   │   ├── espejosombra/                 GenerarInformeUseCase (solo scheduler), ObtenerInformeUseCase, ListarInformesUseCase
│   │   └── conocimiento/                 IndexarConocimientoUseCase (admin, D-46)
│   ├── ports/out/
│   │   ├── conversacion/                 Load/Save de conversación y mensajes, ConsultarLeccionesVisiblesPort (→ academy.api, D-81)
│   │   ├── espejosombra/                 Load/Save informes + LeerEntradasDiarioPort (→ habits.api, D-50)
│   │   ├── conocimiento/                 VectorStorePort (D-45, FiltroLecciones desde D-81), SaveChunkPort
│   │   ├── ia/                           ChatIAPort (streaming), GenerarInsightSemanalPort, EmbeddingPort
│   │   ├── cuota/                        ControlCuotaRenasiaPort (→ Redis, D-48)
│   │   └── seguridad/                    EvaluarRiesgoMensajePort (D-82, estructura sin conectar)
│   └── services/                         ConversacionRenasiaService, EspejoSombraService, ConocimientoService
└── infrastructure/adapter/
    ├── in/rest/                          RenasiaController (streaming), EspejoSombraController, ConocimientoAdminController
    ├── in/scheduler/                     GenerarInformesSemanalesScheduler
    └── out/
        ├── persistence/{conversacion,espejosombra,conocimiento}/
        ├── vectorstore/                  PgVectorNativoAdapter (SQL con `<=>`, D-45; filtro por lección, D-81)
        ├── ia/                           NoOp* mientras no haya credenciales (mismo patrón que evidence/onboarding)
        ├── redis/                        ControlCuotaRedisAdapter (D-48)
        ├── habits/                       LeerEntradasDiarioAdapter (llama a habits.api, D-50)
        ├── academy/                      LeerLeccionesVisiblesAdapter (llama a academy.api, D-81)
        └── seguridad/                    NoOpEvaluacionRiesgoAdapter (placeholder, D-82)
```

---

## 4.bis Hallazgos de la verificación técnica (contra los JARs reales, no documentación)

Se inspeccionaron los JARs de `spring-ai:2.0.0` en el repositorio local de Maven (`jar tf`, `javap`, extracción de strings del bytecode). Cuatro resultados cambian o confirman el diseño:

### El streaming SÍ funciona con Spring MVC — riesgo descartado

Era el riesgo más grande de la propuesta original. **Resuelto a favor de nuestro stack:**
- `ChatClient...stream().content()` devuelve `Flux<String>` (Project Reactor).
- `reactor-core:3.8.7` **ya está en el classpath**, entra como dependencia directa de `spring-ai-client-chat` — no hay que agregar nada.
- `spring-webflux` **NO está** en el árbol de dependencias, **y no hace falta**: `spring-webmvc:7.0.9` trae `ReactiveTypeHandler`, `ResponseBodyEmitter` y `SseEmitter`. Spring MVC detecta un retorno `Flux<T>` y lo adapta a streaming sobre el `HttpServletResponse` en modo async-dispatch, compatible con virtual threads.

**Decisión:** un `@RestController` normal que devuelve `Flux<String>` con `produces = TEXT_EVENT_STREAM_VALUE`. Sin WebFlux, sin WebSocket, sin Redis Pub/Sub — a diferencia de `chat`, acá es 1:1 entre el aprendiz y la IA, sin fan-out entre instancias.

### D-51 — La dimensión del embedding hay que fijarla explícitamente (habría roto en runtime)

`GoogleGenAiTextEmbeddingOptions.DEFAULT_MODEL_NAME` es **`gemini-embedding-001`, que produce vectores de 3072 dimensiones**. Nuestra columna es `vector(768)`. Con la configuración por defecto, **toda inserción fallaría**.

Opciones reales verificadas:
- `text-embedding-004` → 768 nativo, calza exacto con la columna.
- `gemini-embedding-001` con `.dimensions(768)` → trunca vía Matryoshka Representation Learning.

**Decisión original: `text-embedding-004`**, por ser el que coincide de forma nativa con el esquema ya congelado, sin truncado de por medio.

> **D-51 quedó obsoleta (2026-09-03).** Google retiró `text-embedding-004` el 2026-01-14 — ese modelo ya no existe, así que la decisión original habría hecho fallar la primera indexación con credenciales reales. **Decisión vigente:** `gemini-embedding-001` (el default de Spring AI) con `spring.ai.google.genai.embedding.text.dimensions=768` fijado explícitamente en `application.yaml` — el truncado Matryoshka nativo del modelo, no un recorte casero. Ya cableado en `GoogleGenAiClientesConfig`/`GoogleGenAiEmbeddingAdapter`; este último falla con `IllegalStateException` (no trunca en silencio) si el modelo alguna vez devolviera una cantidad de dimensiones distinta a la esperada.

### El "Modular RAG" de la propuesta NO existe en 2.0.0

`RetrievalAugmentationAdvisor` (con query transformers, document joiners, post-processors) **no está en ningún JAR del classpath** — pertenece a versiones posteriores o a otra rama. Lo que sí existe es `QuestionAnswerAdvisor` (en `spring-ai-vector-store-advisor:2.0.0`): un advisor de **un solo paso**, que recibe un `VectorStore` y un `SearchRequest`.

**Consecuencia:** el pipeline modular (query rewriting, re-ranking, fusión de retrievers) no se configura — se programa a mano orquestando la búsqueda antes de llamar al modelo. Como igual vamos a usar nuestro propio `VectorStorePort` (D-45), esto no nos afecta demasiado: el caso de uso orquesta *buscar → armar contexto → preguntar*, que es justamente donde vive esa lógica en arquitectura hexagonal.

**Alineado con la propuesta original:** empezar simple (`búsqueda → top-K → contexto → modelo`) y sumar re-ranking solo si las pruebas muestran que hace falta.

### Control de costo: hay más herramientas de las esperadas

Verificado en `GoogleGenAiChatOptions`:
- **Thinking budget:** `.thinkingBudget(Integer)` y `.thinkingLevel(MINIMAL|LOW|MEDIUM|HIGH)`. Permite exactamente lo que planteaba la propuesta: razonamiento mínimo para preguntas simples, más presupuesto para análisis complejos (el Espejo Sombra es el caso claro de "más presupuesto").
- **Caching implícito:** `.autoCacheTtl(Duration)`, `.autoCacheThreshold(Integer)`, `.useCachedContent(Boolean)`.
- **Caching explícito:** existe un servicio completo, `GoogleGenAiCachedContentService`, con CRUD de contenido cacheado y TTL. Relevante para Renasia: el prompt de sistema y el contexto del programa se cachean del lado de Gemini y dejan de facturarse como tokens de entrada en cada llamada.

Esto se combina con el límite de D-48: **el límite protege del abuso, el caching reduce el costo del uso legítimo.** Son complementarios, no alternativas.

---

## 5. Lo que sigue bloqueado

| Bloqueo | Efecto |
|---|---|
| **Credenciales de Gemini** (D-39) | **Parcialmente resuelto (2026-09-03).** Los adaptadores reales (`GoogleGenAiRenasiaChatAdapter`, `GoogleGenAiEmbeddingAdapter`) y su `@Configuration` (`GoogleGenAiClientesConfig`) ya están escritos, detrás de `renaser.ia.proveedor=google`. Sin `GOOGLE_GENAI_API_KEY` real, el default (`renaser.ia.proveedor=noop`) sigue activando los `NoOp*`. Lo que falta: probar el camino `google` con una API key real (nadie corrió `./mvnw` contra Gemini de verdad) y que Producto defina el prompt de sistema definitivo (hoy es un placeholder explícito en `prompts/renasia-sistema.st`) |
| **Sin datos en `base_conocimiento`** | Es esperable: la ingesta es admin (D-46) y el contenido llega en la fase de migración de datos |
| **Clasificador de riesgo real** (D-82) | Existe la estructura (`EvaluarRiesgoMensajePort` + `NoOpEvaluacionRiesgoAdapter`), sin conectar. Falta: (1) confirmar si `NivelRiesgo` necesita un tercer estado "indeterminado", (2) el mapeo completo de `EvaluacionRiesgo` a modo de respuesta (firmado por el dueño del producto) y (3) los criterios de detección en sí (firmados por un profesional con licencia). Depende además de D-80 (edad/país confiables) para el camino de crisis |

---

## 6. Preguntas que quedaron abiertas (no inventadas)

1. **¿Qué tipos de `entradas_diario` alimentan el Espejo Sombra?** El enum tiene un valor `ESPEJO_SOMBRA` dedicado, pero nada obliga a filtrar por él — podrían usarse todas las entradas de la semana. No se puede derivar del esquema.
2. **Retención de conversaciones de Renasia.** El chat normal sí tiene política documentada (12 meses en GLOBAL); para Renasia no hay ninguna.
3. **¿Notificar al aprendiz cuando su informe semanal está listo?** El enum `tipo_notificacion` ya tiene `RESUMEN_SEMANAL` sin dueño — encajaría, pero no está confirmado que deba dispararse.
4. **Cadencia del scheduler:** ¿barrido semanal para todos los participantes activos, o por aniversario individual de cada aprendiz (día N de su programa)?

---

## Auditoría de arquitectura (2026-08-28) — agente automático

Auditoría de solo lectura de `src/main/java/com/renaser/os/rag/`. No se corrió `./mvnw` (fuera de alcance del encargo). 3 controllers REST confirmados (`ConocimientoAdminController`, `RenasiaController`, `EspejoSombraController`) cubriendo 6 endpoints, más 1 `@Scheduled` (`GenerarInformesSemanalesScheduler`). No se reportan como hallazgo: D-45 (SQL nativo propio sobre `pgvector` en vez de `PgVectorStore`) ni que `base_conocimiento` arranque vacía — ambas son decisiones ya tomadas y documentadas en este archivo.

**1. Seguridad — `@ActorAutenticado`: sin violaciones, grep vacío**

Los 3 controllers usan `@ActorAutenticado UserId actorId` (`shared/web/security/ActorAutenticado.java`), nunca `@RequestHeader("X-Actor-Id", ...)` suelto:

- `infrastructure/adapter/in/rest/ConocimientoAdminController.java:25`
- `infrastructure/adapter/in/rest/conversacion/RenasiaController.java:39,45`
- `infrastructure/adapter/in/rest/espejosombra/EspejoSombraController.java:43,53`

`grep -rn "X-Actor-Id\|RequestHeader" src/main/java/com/renaser/os/rag` solo encuentra una mención en un comentario de `EspejoSombraController.java:19` que **documenta** el mecanismo de `ActorAutenticadoArgumentResolver` (sesión primero, header como respaldo interno de esa anotación) — no es un uso directo del header en el propio módulo. `rag` migró completo en el commit `b824c4b`.

**2. Control de cuota diaria (`ControlCuotaRedisAdapter`, D-48) — sin forma de bypass por spoofing de actor**

`infrastructure/adapter/out/redis/ControlCuotaRedisAdapter.java:40-52` incrementa una clave Redis `renasia:cuota:{actorId}:{fecha}` (TTL a medianoche UTC) recibiendo el `UserId` que `ConversacionRenasiaService.preguntar` (`application/services/ConversacionRenasiaService.java:88-90`) ya resolvió desde `@ActorAutenticado` en `RenasiaController.java:39` — nunca desde un header leído dentro del propio flujo de cuota. Como el actor viene de la sesión real (o del respaldo interno ya validado de `ActorAutenticadoArgumentResolver`, no de un header propio del módulo), no existe una ruta donde un cliente pueda escribir en la clave de cuota de otro usuario. El único camino "generoso" es `liberar()` (línea 55-57): decrementa la cuenta cuando el intercambio falla después de haberla consumido (búsqueda de contexto o streaming de IA fallan, `ConversacionRenasiaService.java:98-101,109-112`) — es una devolución legítima del propio flujo, no una vía de terceros.

**3. Inconsistencia de logging — `participanteId` (= UserId = `sub` de Supabase) se loguea en `EspejoSombraService` y en el scheduler, pese a que el propio módulo documenta lo contrario**

`ConversacionRenasiaService.java:162-163` deja explícito en un comentario: *"Tampoco el id del actor (es el `sub` de Supabase)"* — coherente con CLAUDE.md §5.4.9 ("Nunca loguear: ... ni el `sub` de Supabase"). Sin embargo, en el mismo módulo:

- `application/services/EspejoSombraService.java:97-98, 104-106, 110-111, 116-117` — cuatro `log.debug/warn/info` que incluyen `participante={}` con el `UserId` completo.
- `infrastructure/adapter/in/scheduler/GenerarInformesSemanalesScheduler.java:63-64` — `log.error(...)` con `participante={}`.

`UserId` es, por diseño documentado en CLAUDE.md §5.3.1, el mismo UUID de Supabase Auth (`User.id`) — es decir, el mismo dato que `ConversacionRenasiaService` identifica como el `sub` a no loguear. No es un hallazgo catastrófico (es un UUID, no contenido de conversación ni el JWT en sí, y son logs de nivel `INFO`/`WARN`/`ERROR`/`DEBUG` sobre hitos de negocio del propio caso de uso, exactamente lo que CLAUDE.md §5.4.9 pide loguear en `application/`), pero es una inconsistencia real dentro del mismo módulo contra su propio criterio ya declarado, y técnicamente contradice la lista explícita de "nunca loguear" de §5.4.9. Comparado contra otros módulos ya auditados (`rocks.VerdugoService` solo loguea el id del *evento*, no de usuario; `habits/application` no tiene logging en absoluto), el patrón de loguear `UserId` no aparece en otro lado — es específico de `rag`.

**4. `domain/` — cumple, subcarpetas correctas por agregado real**

11 clases en `domain/model/`, repartidas en 3 subcarpetas, cada una un agregado independiente con identidad y ciclo de vida propios: `conocimiento/` (2: `ChunkConocimiento`, `ChunkConocimientoId`), `conversacion/` (5: `ConversacionRenasia`, `MensajeRenasia`, `MensajeRenasiaId`, `FuenteMensaje`, `RolMensaje` — `MensajeRenasia` no vive sin `ConversacionRenasia`, pero es su propia raíz con FK, no un value object suelto de ella), `espejosombra/` (4: `InformeEspejoSombra`, `InformeEspejoSombraId`, `DistribucionTemporal`, `PreguntaConfrontacion`). Ninguna subcarpeta se acerca al techo de ~10 clases. `grep` de `org.springframework.*`/`jakarta.persistence.*`/paquetes `application`/`infrastructure` sobre `domain/` no devolvió coincidencias — dominio limpio.

Lombok en `domain/`: los 4 agregados usan exactamente el patrón permitido por CLAUDE.md §5.4.5 (`@Getter`, `@Accessors(fluent = true)`, `@AllArgsConstructor(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id"/"usuarioId")`) — nunca `@Data`/`@Setter`/`@NoArgsConstructor` público. Construcción vía factory methods estáticos (`indexar`, `escribirDeUsuario`/`escribirDeAsistente`, `generar`, `iniciar`) + `rehydrate` separado para persistencia, con validación de invariantes en los factory methods, no en constructores (`MensajeRenasia.java:37-81`, `InformeEspejoSombra.java:56-112`, `ChunkConocimiento.java:59-98`). `toString()` acotado sin PII en las tres clases con datos sensibles (`MensajeRenasia.java:83-87`, `ConversacionRenasia.java:47-50`).

**5. Controllers "tontos" — cumplido en los 3**

`RenasiaController` (52 líneas, 2 casos de uso), `EspejoSombraController` (58 líneas, 2 casos de uso), `ConocimientoAdminController` (32 líneas, 1 caso de uso): cada endpoint deserializa, valida (`@Valid` donde aplica), invoca un único caso de uso y mapea a DTO de salida. Ninguno inyecta un puerto `out`, tiene `@Transactional` (`grep` de `@Transactional` sobre `infrastructure/adapter/in` no devolvió coincidencias) ni contiene un `if` de negocio — la única rama visible es `EspejoSombraController.java:46` (`participanteIdParam != null ? ... : actorId`), que es resolución de un parámetro opcional de query, no una regla de negocio (la regla real, D-47, vive en `EspejoSombraService.requireVisibilidad`, línea 167-179). El comentario de `ConocimientoAdminController.java:13` deja explícito que el gateo de rol vive en el servicio, no en el controller — correcto contra CLAUDE.md §5.4.6.

**6. Autorización D-47/D-46/D-48 — resuelta en `application/`, nunca en el controller ni con anotaciones declarativas**

`EspejoSombraService.requireVisibilidad` (línea 167-179) resuelve visibilidad de informes (propio participante, mentor **asignado** — no cualquier mentor, `esMentorAsignado` línea 181-186 consulta `ParticipacionProgramaFinder` — o ADMIN/ALCHEMIST) **antes** de tocar el informe puntual, de forma que un tercero sin relación recibe 403 y nunca 404 (evita filtrar existencia). `ConocimientoService.requireAdmin` (línea 47-56) exige ADMIN/ALCHEMIST y cuenta activa antes de indexar. `ConversacionRenasiaService.requireActivo`/`requireCuotaDisponible` (línea 148-160) verifican estado de cuenta y cuota antes de cualquier operación. Las tres verificaciones lanzan `NotAuthorizedException`/`RateLimitExceededException` (dominio compartido, `shared/domain/`) sin conocimiento de HTTP — el único traductor a status code es `shared/web/GlobalExceptionHandler.java` (maneja ambas excepciones, líneas 35-36 y 105-106). Ninguna excepción de `rag` construye un `ResponseEntity` ni conoce un código de estado.

**7. Mapeo de persistencia — manual en los 3 agregados, no MapStruct (desviación menor de CLAUDE.md §5.4.5)**

CLAUDE.md §5.4.5 recomienda MapStruct específicamente para la frontera `JpaEntity ↔ dominio` ("mapeo plano campo-a-campo, repetido en los 14 módulos... su caso de uso legítimo"). `rag` no usa `@Mapper`/`org.mapstruct` en ningún punto (`grep` vacío) — los 3 mappers (`MensajeRenasiaPersistenceMapper`, `InformeEspejoSombraPersistenceMapper`, `ConversacionRenasiaPersistenceMapper`) son clases `@Component` con métodos `toDomain`/`toEntity` escritos a mano. No es una violación de una regla dura (Two-Way Mapping en esa frontera es la estrategia correcta según §5.4.1, y el mapeo a mano es explícitamente válido — `buckpal` también mapea a mano), y en este caso concreto hay lógica que un mapper generado por convención no cubriría bien sin configuración adicional (conversión `int`↔`short`, `List<PreguntaConfrontacionEmbeddable>`↔`List<PreguntaConfrontacion>`, reconstrucción de `FuenteMensaje` a partir de una consulta separada). Se documenta como desviación del patrón preferido, no como defecto funcional.

**8. Excepción a la regla de no-mapeo automático hacia el cliente — `IndexarConocimientoRequest`/`PreguntarRenasiaRequest` sin campos sensibles que blindar**

A diferencia de `users` (blindaje de `role`), los DTOs de entrada de `rag` no tienen campos que el cliente no deba poder setear: `IndexarConocimientoRequest` (`infrastructure/adapter/in/rest/IndexarConocimientoRequest.java`) es de uso exclusivo admin y no incluye ningún campo de identidad; `PreguntarRenasiaRequest` es un único campo `question`. Full Mapping a mano igual, consistente con §5.4.1, pero no había superficie de mass-assignment real que corregir acá.

**9. Tamaños — todo bajo los techos duros de §5.4.8**

Archivo más grande del módulo: `application/services/EspejoSombraService.java` con 196 líneas (techo 300) y 3 interfaces `implements`/3 métodos públicos de caso de uso. Ningún método individual observado supera ~25 líneas. Sin nombres prohibidos (`Util`/`Helper`/`Manager`/`Processor`/`Data`/`Info` sueltos — `grep` vacío sobre el módulo completo).

**10. Módulo `api/` deliberadamente vacío — documentado, no un olvido**

`api/package-info.java` declara el `@NamedInterface("api")` sin contenido: `rag` es consumidor final de la cadena (lee de `habits.api.EntradaDiarioFinder` y `users.api.*` vía las fronteras públicas correctas, `infrastructure/adapter/out/habits/LeerEntradasDiarioAdapter.java:3-4`) y hoy nadie consume nada de `rag`. `grep` de imports cruzados confirma que todos los accesos a otros módulos pasan por sus paquetes `.api.*` — sin ningún import a `domain`/`application`/`infrastructure` interno de `users`/`habits`.

**11. Los 5 archivos más grandes del módulo**

1. `application/services/EspejoSombraService.java` — 196 líneas (hallazgo 3, logging)
2. `application/services/ConversacionRenasiaService.java` — 167 líneas
3. `domain/model/espejosombra/InformeEspejoSombra.java` — 113 líneas
4. `infrastructure/adapter/out/vectorstore/PgVectorNativoAdapter.java` — 111 líneas
5. `domain/model/conocimiento/ChunkConocimiento.java` — 99 líneas

**Resumen:** `rag` es, de los módulos auditados hasta ahora, uno de los más limpios contra CLAUDE.md — cero violaciones de autenticación (hallazgo 1), cuota sin bypass (hallazgo 2), dominio puro con subcarpetas correctas por agregado (hallazgo 4), controllers tontos (hallazgo 5) y autorización resuelta enteramente en `application/` (hallazgo 6). Los únicos hallazgos son menores: una inconsistencia de logging de `UserId`/`sub` de Supabase contra el propio criterio que el módulo se fijó (hallazgo 3, el más accionable de la lista) y el uso de mapeo manual en vez de MapStruct en persistencia (hallazgo 7, estilo).
