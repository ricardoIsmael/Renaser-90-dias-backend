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
│   │   ├── conversacion/                 Load/Save de conversación y mensajes
│   │   ├── espejosombra/                 Load/Save informes + LeerEntradasDiarioPort (→ habits.api, D-50)
│   │   ├── conocimiento/                 VectorStorePort (D-45), SaveChunkPort
│   │   ├── ia/                           ChatIAPort (streaming), GenerarInsightSemanalPort, EmbeddingPort
│   │   └── cuota/                        ControlCuotaRenasiaPort (→ Redis, D-48)
│   └── services/                         ConversacionRenasiaService, EspejoSombraService, ConocimientoService
└── infrastructure/adapter/
    ├── in/rest/                          RenasiaController (streaming), EspejoSombraController, ConocimientoAdminController
    ├── in/scheduler/                     GenerarInformesSemanalesScheduler
    └── out/
        ├── persistence/{conversacion,espejosombra,conocimiento}/
        ├── vectorstore/                  PgVectorNativoAdapter (SQL con `<=>`, D-45)
        ├── ia/                           NoOp* mientras no haya credenciales (mismo patrón que evidence/onboarding)
        ├── redis/                        ControlCuotaRedisAdapter (D-48)
        └── habits/                       LeerEntradasDiarioAdapter (llama a habits.api, D-50)
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

**Decisión: `text-embedding-004`**, por ser el que coincide de forma nativa con el esquema ya congelado, sin truncado de por medio. Queda como configuración explícita, nunca implícita.

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
| **Credenciales de Gemini** (D-39) | Los adaptadores de IA quedan `NoOp` como en `evidence`/`onboarding`. Todo el resto (persistencia, cuota, permisos, búsqueda vectorial) se construye y se prueba igual |
| **Sin datos en `base_conocimiento`** | Es esperable: la ingesta es admin (D-46) y el contenido llega en la fase de migración de datos |

---

## 6. Preguntas que quedaron abiertas (no inventadas)

1. **¿Qué tipos de `entradas_diario` alimentan el Espejo Sombra?** El enum tiene un valor `ESPEJO_SOMBRA` dedicado, pero nada obliga a filtrar por él — podrían usarse todas las entradas de la semana. No se puede derivar del esquema.
2. **Retención de conversaciones de Renasia.** El chat normal sí tiene política documentada (12 meses en GLOBAL); para Renasia no hay ninguna.
3. **¿Notificar al aprendiz cuando su informe semanal está listo?** El enum `tipo_notificacion` ya tiene `RESUMEN_SEMANAL` sin dueño — encajaría, pero no está confirmado que deba dispararse.
4. **Cadencia del scheduler:** ¿barrido semanal para todos los participantes activos, o por aniversario individual de cada aprendiz (día N de su programa)?
