# Módulo `rag` / `renasia` — diseño y decisiones

**Fecha:** 2026-08-25
**Estado:** **construido** (verificado el 2026-09-14: 5 endpoints, 7 casos de uso, 184 pruebas).
Renasia y Sparkie tienen adaptadores reales de Google GenAI detrás de `renaser.ia.proveedor=google`;
el Espejo Sombra (`GenerarInsightSemanalPort`) y el clasificador de riesgo
(`EvaluarRiesgoMensajePort`, D-82) siguen en `NoOp`.

> La cabecera decía «diseñado, **no construido todavía**. Es el último de los 14 módulos»
> desde el 2026-08-25. Quedó vieja en las dos mitades: el módulo se construyó, y hoy son 16
> módulos, no 14. Corregido el 2026-09-14.

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

### D-143 — Cuando el malestar se repite: un recurso para la persona y un aviso para quien pueda actuar (2026-09-15)

**Qué se construyó.** Cuando un aprendiz le escribe al asistente expresiones como *"me siento mal"*
o *"ya no doy más"*, y **ese patrón se repite**, pasan dos cosas: se le ofrece a la persona un
recurso de ayuda real (la línea de salud mental del MINSA) y les llega un aviso a ADMIN y
ALQUIMISTA por la bandeja de notificaciones.

**Esto NO es el clasificador de riesgo de D-82, y no lo reemplaza.** No clasifica, no diagnostica y
no llama a ninguna IA: compara texto contra una lista de frases y cuenta repeticiones.
`EvaluarRiesgoMensajePort` sigue sin implementación real y sin criterio clínico firmado, y este
camino no lo conecta ni lo toca. Nada en el código, en los nombres ni en los textos afirma que la
persona esté en crisis — el aviso dice que *se repitió un patrón que conviene mirar*, y nada más.

**El umbral: 3 detecciones en 7 días, en UN solo lugar.** Los dos valores son constantes de
`PatronDeMalestarRepetido` (`DETECCIONES_PARA_AVISAR`, `VENTANA`) y **están a confirmar con el
dueño**: tres y siete son lo que se fijó para arrancar, no salen de ninguna regla clínica. Que sea
mayor que uno es deliberado: una frase mala un martes es la molestia corriente que produce cualquier
programa exigente (`Severidad.BAJA`), y contestarle a eso con un recurso de emergencia hace que la
persona deje de escribir — y entonces el asistente ya no está el día que sí hace falta.

**Sin tabla nueva: la cuenta se deriva de `mensajes_renasia`** (regla 02 §2). El texto, el rol y la
fecha de cada mensaje ya están guardados. Un contador paralelo habría que mantenerlo sincronizado y
se desincroniza en cuanto cambie el umbral o la lista; con el conteo derivado, cambiar cualquiera de
los dos reevalúa también lo ya guardado, correrlo dos veces da lo mismo y las detecciones viejas
salen solas de la ventana. Tampoco hizo falta un índice: `mensajes_renasia_conv_idx` ya cubre la
consulta, y la cuota diaria acota la ventana a unos cientos de filas por persona.

**El costo en el camino normal es cero.** La cuenta de la ventana solo puede SUBIR cuando entra un
mensaje que cuenta; sin uno nuevo, la ventana desliza y la cuenta baja. Así que primero se mira en
memoria el mensaje recién escrito y, si no contiene ninguna expresión, no se consulta nada. El 99%
de los mensajes no toca la base.

**La lista de expresiones vive en UN archivo comentado** (`ExpresionesDeMalestar`), en español de
Perú y normalizada (minúsculas, sin tildes, sin signos). Dos reglas la gobiernan:

- **Frases completas, no pedazos.** La lista dice `"no puedo mas"` y **no** `"no puedo"`, porque
  *"no puedo con este hábito"* y *"no puedo a esa hora"* son mensajes de alguien acomodando su día.
  `ExpresionesDeMalestarTest` tiene esos casos como negativos: si alguien agrega `"no puedo"` a
  secas, el build se pone en rojo.
- **Las señales explícitas de peligro NO están, a propósito.** Un mecanismo que exige tres
  repeticiones y se queda callado las dos primeras veces que alguien dice que se quiere morir sería
  peor que no existir. Ese camino es `NivelRiesgo.CRITICO`, que dispara a la primera, y sigue sin
  construirse: le faltan el criterio clínico firmado y D-80 (edad y país confiables). Hay un test
  que falla si alguien mete una de esas frases en esta lista sin haber construido ese camino.

**El texto del MINSA sale de configuración y viene VACÍO** (`renaser.renasia.apoyo.mensaje`,
`RENASIA_MENSAJE_APOYO`). No está confirmado, y un teléfono inventado no es un placeholder: manda a
alguien que está mal a llamar a la nada. Mientras siga vacío, **el aviso a ADMIN/ALQUIMISTA se emite
igual** —un humano se entera y puede actuar— y a la persona no se le muestra nada. Se completa la
propiedad y empieza a mostrarse, sin desplegar código.

**Por dónde le llega cada cosa.**

| A quién | Por dónde | Por qué así |
|---|---|---|
| La persona | Un evento `texto` más del stream SSE, al final de la respuesta | El contrato SSE es de la app móvil: un `tipo` nuevo que el cliente no conoce lo ignoraría en silencio, o sea que la persona no vería nada. Va por el canal que el cliente ya dibuja, y queda también en el mensaje persistido para que al volver al chat lo vuelva a encontrar |
| ADMIN y ALQUIMISTA | `rag.api.PatronDeMalestarRepetidoEvent` → `notifications` | `rag` no tiene por qué saber que existe una bandeja. Es la **primera vez que `rag` publica algo hacia afuera**: su `api/` estuvo vacío desde el día uno esperando exactamente esto |

La deduplicación es la que ya existe: la clave del **episodio** (persona + instante de la primera
detección de la ventana) viaja como `origenEventoId`, que tiene índice único (V16). Revisar el patrón
en cada mensaje entrega UN aviso a cada administrador, y la reentrega del outbox de Modulith tampoco
lo duplica. `PATRON_DE_MALESTAR_REPETIDO` es un valor más de `tipo_notificacion` (V59) y no una tabla
de alertas propia — misma decisión que V46 y V49.

**C-1 respetado:** la revisión abre su propia transacción corta (la necesita el outbox) y corre
**antes** de hablar con el modelo, nunca durante. Es best-effort: si falla, se registra y la persona
igual recibe su respuesta.

### D-123 — Qué va en el prompt y qué se convierte en herramienta (2026-09-14)

Regla para no seguir discutiéndolo cada vez que el agente necesita un dato nuevo.

> **Las herramientas son para lo que el modelo *decide* buscar y para lo que *escribe*. El prompt
> es para lo que el modelo *siempre* necesita saber.**

**Lo que decide NO es si el dato es por usuario.** Los dos lo son. El prompt de sistema es un
`PromptTemplate` de Spring AI que se renderiza **en cada petición**, y ya inyecta datos por
usuario: `prompts/renasia-sistema.st` tiene `{contexto}`, que son los fragmentos recuperados para
esa persona y filtrados por lo que puede ver hoy (D-81). Agregar un segundo marcador es agregar
una línea, no una función.

Lo que decide es la frecuencia y el coste:

| | Prompt | Herramienta |
|---|---|---|
| Se necesita | Casi siempre | A veces |
| Tamaño | Pequeño y acotado | Puede ser grande |
| ¿Escribe? | Nunca | Sí |
| Coste por uso | Ninguno | **Un turno completo con el modelo** |

El coste de una herramienta no es teórico: el modelo contesta "necesito llamar a X", vuelve al
servidor, el servidor ejecuta, vuelve al modelo, y **recién entonces** responde. Son dos llamadas
al modelo en vez de una — uno o dos segundos más de espera para la persona, por un dato que el
servidor ya tenía antes de empezar.

Este módulo ya aplicó el criterio una vez, y está comentado en `HerramientasAgenteService`:
`consultar_habitos_del_dia` devuelve el total en la MISMA respuesta para que el modelo no encadene
`consultar_puntos_en_juego` — se ahorró un viaje de ida y vuelta a Gemini.

**Cómo cae cada caso pendiente hoy:**

| Dato | Dónde va | Por qué |
|---|---|---|
| Día de programa, fase, racha | **Prompt**, como `{diaPrograma}` | Un dato por persona, chico, que hace falta casi siempre. Hoy `renasia-sistema.st:47` promete que el agente lo sabe y **nada se lo suministra**: `ChatIAPort.Consulta` no lo lleva |
| Si un hábito exige evidencia | **Herramienta existente** | Es un dato POR HÁBITO, y `consultar_habitos_del_dia` ya devuelve una línea por hábito. Se amplía `HabitoDelDia`; no se crea una herramienta nueva |
| Marcar un hábito | **Herramienta** (ya está) | Escribe |
| Conducta ante crisis | **Prompt** | Es una regla de conducta, no un dato |

**Corolario:** no se agregan herramientas "por si acaso". Cada definición viaja en cada petición
—ocupa contexto— y le da al modelo una opción más entre las que dudar. Hoy son tres y alcanzan.

> **Actualizado 2026-09-23 (D-152).** "Hoy son tres y alcanzan" dejó de ser cierto: el dueño pidió
> un acompañante que **planifique** con la persona (tiempo, horarios, rocas, eventos). Pasaron a ser
> ocho, y ninguna es "por si acaso": cada una responde una pregunta concreta que el acompañante no
> podía contestar sin inventar. El criterio de arriba sigue en pie: la herramienta nueva de
> resumen **no** duplica el día y la fase que ya van en el prompt, sino que los lee del mismo
> puerto (`ConsultarSituacionDelAprendizPort`) para que nunca digan cosas distintas.

### D-152 — El acompañante como planificador: cinco herramientas de lectura (2026-09-23)

Diseño completo, inventario de las ~90 operaciones del aprendiz y decisiones del dueño:
`docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md`. Esta es la **fase 1**: solo lectura y
cálculo. Las escrituras (cambiar horario, pausar, plan de rocas) llegan en la fase 4, como
**propuestas con botones de confirmación** (fase 2); el modelo nunca las ejecuta solo.

**Punto de extensión.** Cada herramienta nueva es un `@Component` que implementa
`application/services/herramientas/HerramientaAgente`; `HerramientasAgenteService` las recibe
todas, las ofrece solo a `COMPANION`, valida sus obligatorios, traduce cualquier excepción a un
`Fallo` legible y **corta el arranque si dos herramientas se llaman igual**. Las tres originales no
se migraron: funcionan y están probadas.

| Herramienta | Responde | Lee de (reusa, no reimplementa) |
|---|---|---|
| `consultar_tiempo_para_puntos` | "¿llego a tiempo?", "¿cuánto pierdo si lo hago a las 9?", cuál vence primero | `habits.api.AgendaDelDiaFinder`: se agregaron `HabitoEnJuegoResumen.tramos` (la escala D-97, derivada de `ResultadoOtorgamiento`, el mismo cálculo que otorga los puntos) y `zonaDe` |
| `consultar_resumen_del_programa` | día N de 90, fase, fecha y hora local, coherencia, próximo evento | `ConsultarSituacionDelAprendizPort` (día/fase, el mismo del prompt) + `points.api.PorcentajeRocasFinder` y `ProximoEventoFinder`, los mismos de `GET /home` |
| `consultar_horarios` | horario resuelto por día, apagado/pausado/obligatorio, **cuota de cambios** restante | nuevo `habits.api.HorarioDelDiaFinder` → `ConsultarPreferenciasHorarioUseCase` (el de `GET /habit-preferences?date=`) |
| `consultar_rocas` | rocas de hoy/mañana/semana/mes, si el plan de mañana existe, ventana de las 18:00 | nuevo `rocks.api.RocasDelAprendizFinder` → dashboard, rocas de mañana, objetivo del mes, `VentanaPlanificacionDiaria` |
| `consultar_eventos` | eventos de hoy o de los próximos 7 días, con el RSVP | nuevo `calendar.api.EventosDelParticipanteFinder` → `ListarEventosParaVisorUseCase` (audiencia y RSVP intactos) |

**Horas y fechas, siempre en código** (regla 02): "hoy", "mañana", "faltan 32 min" y la hora local
salen de `Clock` en la zona del participante; el modelo solo los repite. Cada herramienta tiene
una prueba con el reloj entre 00:00 y 05:00 UTC, que en Lima cae en el día anterior.

**Dependencias nuevas de `rag`:** `points.api`, `rocks.api` y `calendar.api`. Ninguno de esos
módulos importa `rag`, así que no hay ciclo.

**Huecos conocidos (no se inventaron):**

- ~~La racha y los puntos de liga no se exponen: la derivación vive en `points.application`
  (`HomeAgregadoService.rachaDe`) y no hay contrato público. Falta un finder en `points.api` que
  `HomeAgregadoService` también reuse, para que la regla quede en un solo lugar. La herramienta de
  resumen lo dice explícitamente para que el modelo no invente esos números.~~
  > **Resuelto 2026-09-23.** Entra `points.api.ResumenPuntajeFinder` (`puntosLiga`, `rachaActual`,
  > `rachaMaxima`, misma semántica que `GET /home`: racha **derivada**, no la guardada que devuelve
  > `GET /points/{id}`). La ventana y la derivación viven en `points.application.services.RachaMostrada`,
  > que usan tanto `HomeAgregadoService` como `ResumenPuntajeService`: la regla está escrita una vez.
  > `consultar_resumen_del_programa` ahora muestra "Racha actual: N dias (record: M)" y
  > "Puntos de liga: P"; si no se pueden leer (cuenta suspendida, sin fila), lo dice en vez de inventar.
- En `consultar_eventos`, "semana" son los próximos 7 días; en `consultar_rocas` es la semana del
  programa. Queda así hasta que el dueño diga lo contrario.

**Preguntas abiertas al dueño**, detectadas al implementar (no se tocó nada):

1. Con la truncación a minutos, el mínimo de 5 puntos de D-97 solo se paga en el instante exacto
   del vencimiento: en la práctica la escala termina en 6.
2. `RocaDiariaService.requireFechaPlanificable` deja crear el plan de mañana antes de las 18:00,
   pero `puedeCrearPlanDiario` del dashboard exige la ventana abierta. La herramienta reporta los
   dos datos sin decidir cuál manda.


### D-153 — Las escrituras del acompañante son propuestas con botones (2026-09-23)

Fase 2 de `docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md`. **El modelo nunca ejecuta una
escritura:** una herramienta de escritura llama a `ProponerAccionUseCase`, que guarda una fila en
`propuestas_acompanante` (V63): herramienta, argumentos (jsonb), su SHA-256 sobre una forma
canónica, un resumen legible y `vence_en = ahora + renaser.ia.acompanante.propuesta-vigencia`
(default `PT10M`, ventana técnica, no regla de negocio). El turno del chat la entrega como evento
SSE `propuesta` (ver el contrato en §4.bis) y la persona la resuelve con
`POST /api/v1/renasia/propuestas/{id}/confirmar` o `/cancelar` (sesión + `USE_APP`).

- Estados guardados: `PENDIENTE → CONFIRMADA | CANCELADA | FALLIDA`. El vencimiento **se deriva**
  (`PENDIENTE` y `vence_en <= ahora`): no hay scheduler ni estado `VENCIDA`.
- `confirmar`, en orden: dueño y cuenta activa (403) → ya resuelta (devuelve el mismo resultado sin
  volver a ejecutar) → vencida o cancelada (409) → integridad del hash → que exista un
  `AccionConfirmable` para esa herramienta → guarda `CONFIRMADA` con bloqueo optimista (`version`)
  **antes** de ejecutar, así un doble toque ejecuta una sola vez → ejecuta fuera de toda
  transacción (C-1). Un rechazo del negocio deja la propuesta `FALLIDA` con el motivo a la vista.
- **Primera escritura migrada:** `marcar_habito_completado`, detrás de
  `renaser.ia.acompanante.confirmacion-con-botones` (env `IA_ACOMPANANTE_CONFIRMACION_CON_BOTONES`,
  **default `false`**). Con `false` todo es como antes. Con `true` la herramienta verifica que el
  registro sea de hoy y siga en juego, propone `Marcar '<titulo>' como hecho (+N puntos si lo
  confirmas ahora)` y le dice al modelo que **todavía no** está hecho; la escritura real la hace
  `MarcarHabitoCompletadoConfirmable`. La traducción de errores vive en un solo lugar
  (`CompletacionDeHabito`). **El flag se prende junto con la versión de la app que dibuja los
  botones**: la app no se actualiza por aire.

**Limitaciones conocidas (sin decidir):**

1. Si el proceso muere entre guardar `CONFIRMADA` y guardar el resultado, la propuesta queda "en
   ejecución" para siempre: cada toque posterior responde "ya estoy aplicando este cambio" y nada
   la reintenta.
2. Una propuesta ajena responde 403, no 404: revela que el id existe (los ids son UUID aleatorios).

### D-154 — Escrituras de horario, plan de hábitos y rocas como propuestas (fase 4, 2026-09-23)

Siete herramientas R2, **solo registradas con `renaser.ia.acompanante.confirmacion-con-botones=true`**
(sin botones en la app, proponer sería inútil). Cada una valida antes con datos de lectura y deja
una propuesta (D-153); la escritura real la hace su `AccionConfirmable` (siempre registrado)
delegando en el mismo caso de uso que la app, que vuelve a correr todas sus guardas al confirmar.

| Herramienta | Escritura real (vía `*.api`) | Antes de proponer |
|---|---|---|
| `proponer_cambio_de_horario` | `EditarPreferenciaHorarioUseCase` (`habits.api.AjustarHorarioHabitoUseCase`) | cupo de `CuotaEdicionHorario` de la semana efectiva: **agotado → no propone**; límite después del inicio; fecha futura. Conserva el recordatorio vigente |
| `proponer_apagar_dia` | `CambiarEstadoHabitoEnFechaUseCase` | no pasado, no obligatorio, que el cambio cambie algo |
| `proponer_horario_por_dia_de_semana` | `EditarHorarioSemanalUseCase` | `fijar` gasta cupo (próxima ocurrencia del día), `apagar`/`quitar` no |
| `proponer_pausar_habito` | `CambiarEstadoHabitoDelPlanUseCase` (`habits.api.PlanDeHabitosPort`) | no obligatorio, fecha de fin no pasada |
| `proponer_dia_de_habito_semanal` | `ElegirDiaSemanalUseCase` | días de `SemanaDeEleccion` (regla extraída al dominio y usada también por el caso de uso) |
| `proponer_plan_del_dia` | `CrearPlanDiarioUseCase` (`rocks.api.PlanificacionDeRocasPort`) | JSON estricto en un argumento `plan`; la fecha siempre queda escrita (default: mañana en su zona) |
| `proponer_plan_de_la_semana` | `CrearPlanSemanalUseCase` | solo la forma del JSON (el domingo planifica la semana siguiente) |

**Supuestos a confirmar por el dueño:** en el plan del día, cada acción va con `puntajeImpacto=5`
y `esDelegable=false`, copiados del default del cliente (`posicionarPorEje`), no de una regla del
backend. Elegir el día de un hábito semanal **solo lo anota** mientras siga abierto D-H3 (el
generador no filtra por el día elegido): el resumen dice "Anotar" y el modelo no lo promete.

**Límites conocidos:** el pre-chequeo de cupo no ve qué hábitos ya se reacomodaron en la semana,
así que puede negar uno que `habits` sí permitiría (el lado seguro); con cupo agotado remite a la
app. La ambigüedad de la ventana de las 18:00 (D-152) sigue sin resolver: decide el caso de uso.

### D-155 — El acompañante escribe primero: avisos de hábito en el chat (fase 5, 2026-09-23)

`AvisoHabitoEnChatListener` (`@ApplicationModuleListener` sobre `habits.api.AvisoHabitoDebidoEvent`,
al lado del push de `notifications`) llama a `DejarAvisoHabitoEnChatUseCase`, que deja un mensaje
ASISTENTE/COMPANION armado con **plantilla y datos reales**: título, hora local (`occurredAt +
minutosQueFaltan` en la zona con que calculó `habits`) y los puntos D-97 que trae el evento. **Sin
IA y sin consumir cuota.** Apagado por defecto (`renaser.ia.acompanante.avisos-en-chat`); los tipos
y los textos son configuración y **provisorios hasta que el dueño apruebe la redacción**.

- **Idempotencia sin migración:** `habits` republica el aviso cada barrido de 5 min dentro de su
  franja. El id del mensaje se deriva (`nameUUIDFromBytes("aviso-habito-en-chat:" + claveEvento)`)
  y se consulta `LoadMensajeRenasiaPort.existe` antes de guardar (un `save` con id existente sería
  un UPDATE silencioso).
- **No se escribe si el momento ya pasó, ni si lo último del chat es un pedido sin respuesta:** un
  mensaje del asistente ahí haría pasar ese pedido por respondido en `soloTurnosRespondidos` y
  reabriría D-132. El push sale igual.
- **Entrega:** la app lo ve al abrir el panel (recarga el historial); no hay tiempo real.
- Sin verificar contra Gemini: una memoria que empieza con un turno del asistente, o con dos
  seguidos.

### D-156 — El acompañante suma rituales, academia, mentor y logros (2026-09-23)

Pedido del dueño: "que sea amigable pero con sus restricciones". Mismo patrón de siempre: lectura
directa (R0), escritura solo como propuesta con botón (R2, detrás de
`renaser.ia.acompanante.confirmacion-con-botones`) y cada herramienta delega en el caso de uso de
la app por un contrato `*.api` nuevo, sin reimplementar reglas.

| Área | Herramientas | Contrato | Notas |
|---|---|---|---|
| Bitácora y Código Renaser | `consultar_bitacora_de_hoy`, `proponer_bitacora_de_hoy`, `consultar_ultimo_radar`, `proponer_check_in_radar` | `habits.api.DiarioYRadarPort` | la bitácora es un upsert: si ya existe, la propuesta dice "Reemplazar" y muestra lo actual y lo nuevo; no escribe si al confirmar ya pasó la medianoche. Radar: uno por hora (`RadarService.mismaHora`, compartido) |
| Academia | `consultar_clase_de_hoy`, `proponer_entregar_clase_de_hoy`, `consultar_mis_cursos`, `consultar_por_que_esta_bloqueado` | `academy.api.ClaseDiariaPort`, `CursosDelAprendizFinder` | entregar la clase da puntos; no entrega si cambió el día de programa entre proponer y confirmar. ~~**No lee la recomendación adaptativa**: generarla llama a la IA (C-1) y no hay lectura solo de caché~~ **Corregido 2026-09-23:** `consultar_clase_de_hoy` muestra la recomendación de hoy si ya está en caché, vía `ClaseDiariaPort.recomendacionDeHoySiExiste` → `ConsultarRecomendacionDiariaUseCase.recomendacionDeHoySiExiste` (mismo "hoy" en la zona del participante y misma fila que el `GET`, nunca llama a `RecomendarClasePort` ni guarda); si no hay, dice que se genera al abrir la Academia en la app |
| Domingo Ritual y contratos | `proponer_cerrar_semana`, `consultar_contratos_de_fase` | `rocks.api.CierreDeSemanaPort`, `phasecontracts.api.ContratosDeFaseDelParticipanteFinder` | cerrar la semana no da puntos (verificado); **desde el chat no se pisa una revisión existente** (supuesto a confirmar por el dueño). Firmar contratos es consentimiento legal: no hay herramienta para eso |
| Espíritu y enfoque | `consultar_espiritu_de_hoy`, `proponer_resumen_espiritu`, `proponer_iniciar_santuario`, `proponer_iniciar_dia_sin_celular` | `habits.api.EnfoqueDiarioPort` | la lectura de Espíritu **no es pura**: usa el mismo caso de uso que abrir Training (idempotente); copiar su avance duplicaría la regla. Solo se INICIA Santuario / día sin celular: completar o romper sigue en la app |
| Notificaciones y Espejo | `consultar_notificaciones`, `proponer_marcar_notificaciones_leidas`, `consultar_espejo_de_la_sombra` | `rag.api.BandejaDeNotificaciones` (la implementa `notifications`, que ya depende de `rag`) | El Espejo por chat solo muestra el informe propio. **Corregido 2026-09-23:** esta fila incluía `consultar_mis_tickets_al_mentor` y `proponer_ticket_al_mentor` ("excepción aprobada a no escribe a terceros"). Se quitaron el mismo día: los tickets al mentor **se retiraron de la app el 2026-09-07** a pedido del dueño, y para hablar con el mentor existe el chat privado. El acompañante sugiere escribirle por ese chat y puede ayudar a ordenar el mensaje, pero no escribe por la persona |

**Logros en el chat (proactivo, plantilla, sin IA ni cuota):** `LogroEnChatListener` escucha
`habits.api.RachaCompletadaEvent` y `rocks.api.RocaCompletadaEvent`; apagado por defecto
(`renaser.ia.acompanante.logros-en-chat`) y por defecto solo celebra el día sin celular completo
(las rocas son varias por día). Textos provisorios. La lógica común con D-155 (id derivado,
`existe`, guarda de D-132) pasó a una sola clase, `MensajeProactivoDelAcompanante`.

**Tono:** el prompt del acompañante adoptó "cercano y cálido" (celebra lo chico, no regaña tras
un día perdido, una pregunta a la vez, sin voseo) y reglas nuevas: horas, puntos y fechas siempre de
una herramienta; nada se da por hecho hasta que la herramienta lo confirma; a terceros solo el
— **corregido 2026-09-23:** decía "a terceros solo el ticket al mentor como propuesta"; ahora nunca escribe a terceros y sugiere el chat privado con el mentor. Crisis (D-143), riesgo y atribución de fuentes no se tocaron.

**Lo que escribe la persona es suyo:** bitácora, radar y resúmenes. Las descripciones le
prohíben al modelo inventarlo o "mejorarlo". Ese contenido viaja al modelo y queda en
`propuestas_acompanante.argumentos`, pero **no va al log** (E-218).

### D-157 — La voz del orbe: Piper es_MX desde el backend (2026-09-23)

Pedido del dueño: "la voz es paupérrima… una voz más fluida, natural y menos robótica". Se
compararon cuatro voces con la misma frase: el TTS del teléfono (robótico), Kokoro (natural pero
3,3 s por frase en CPU), Gemini TTS (buena, 0,7 s al primer audio, **de pago**) y **Piper
`es_MX-claude-high`** (open source, gratis, **~0,3 s por frase**). Se eligió Piper.

> **Corregido 2026-09-23 (D-159).** El contrato de abajo (el `POST` devuelve el WAV entero) ya no
> rige: el dueño eligió la voz Kore de Gemini con streaming, y el endpoint pasó a dos pasos. Piper
> sigue como proveedor, detrás del mismo contrato nuevo. Se deja el texto original como historia.

`POST /api/v1/renasia/voz` con `{"texto":"..."}` (≤ 400 caracteres, `USE_APP`, sesión obligatoria
por `/api/v1/renasia/**`) devuelve `200 audio/wav`, o `204` sin cuerpo cuando no hay voz del
servidor (proveedor `noop`, o Piper caído, lento o con una respuesta que no es WAV). Con un 204 la
app habla con el TTS del teléfono y no vuelve a preguntar durante un minuto. El caso de uso
`SintetizarVozService` no abre transacción, porque la síntesis es una llamada de red (C-1). Exige
cuenta activa y texto recortado no vacío, y delega en `SintetizarVozPort` (`Optional<byte[]>`, que
nunca lanza).

Adaptadores: `NoOpVozAdapter` (el default, `renaser.ia.voz.proveedor=noop`) y `PiperVozAdapter`
(`piper`). Este llama a `POST {renaser.ia.voz.url}/synthesize` con `text`, `length_scale`,
`noise_scale` y `noise_w_scale`. Los valores por defecto son 1.06 / 0.78 / 0.95: un poco más
pausado y con más variación de entonación que el original, que sonaba plano. Se cambian por
entorno, sin tocar código. El adaptador valida el WAV por la cabecera RIFF/WAVE, porque Piper lo
manda como `text/html` (E-226), y registra los fallos sin el texto.

Piper corre como servicio aparte: `infra/piper/Dockerfile` (la voz se descarga al construir la
imagen) y el servicio `piper` de `docker-compose.yml`, detrás del perfil `voz`
(`docker compose --profile voz up -d piper`). Tiene un interruptor propio, independiente de
`renaser.ia.proveedor`.

**En la app:** el `Locutor` pide el audio de cada oración apenas llega, mientras suena la anterior,
y las reproduce en orden con `expo-audio`, como URI `data:`. Si una falla, la dice la voz del
teléfono.

**Abierto (lo decide el dueño):**
- El endpoint no tiene cuota propia, y cada frase gasta CPU del servicio de voz.
- La voz podría ir dentro de la app (Piper con sherpa-onnx, +60 MB, sin internet). Cambiarla solo
  toca `PARLANTES_DEL_TELEFONO.sintetizar`.

### D-158 — Modo voz: el acompañante contesta como se habla (2026-09-23)

`POST /api/v1/renasia/mensajes` acepta un campo opcional `canal` (`TEXTO` | `VOZ`). El orbe de Hoy
manda `"canal":"VOZ"`. Sin el campo, o con cualquier otro valor (no importan mayúsculas ni
espacios), es `TEXTO`, y el prompt queda idéntico al de antes.

A diferencia de `agent`, un valor desconocido **no** es 400. `canal` solo cambia la forma, y como la
app no se actualiza por aire, un build futuro que mande un valor nuevo tiene que seguir funcionando.

El valor recorre `PreguntarRenasiaRequest.canalConversacion()` → `PreguntarRenasiaCommand.canal` →
`ChatIAPort.Consulta.canal` y no se persiste. Con `VOZ`, `GoogleGenAiRenasiaChatAdapter` agrega al
final del prompt de sistema el bloque `prompts/modo-voz.st`, que pide:
- una a tres frases;
- sin markdown, listas, emojis ni enlaces;
- horas y cantidades dichas como se hablan;
- a lo sumo una pregunta corta al final.

El bloque dice explícitamente que fuentes, herramientas y "Tus limites" siguen iguales, y que en una
crisis los números de ayuda se dicen completos. No se editó `renasia-sistema.st` ni
`sparkie-cursos.st`.

**Limitación conocida:** los textos que el servicio agrega después de la respuesta del modelo no
pasan por el bloque. Son el "Propuesta: …" y el texto de apoyo (D-143). La app no lee en voz alta
el "Propuesta: …": dice "Te dejé la propuesta en el chat: confírmala con el botón".

### D-159 — La voz del orbe pasa a Gemini (Kore) y se transmite mientras se genera (2026-09-23)

El dueño escuchó muestras de Piper y de cuatro voces de Gemini y eligió **Kore**, con el modelo
**`gemini-3.8-flash-lite-tts`** porque es el más rápido. Pidió: "que sea fluida" y "que se demore
menos". Las mediciones del 2026-09-23:
- Esperar el audio entero tarda **4 a 7 s por frase**, demasiado para conversar.
- **Transmitiendo, el primer sonido llega en ~1,5 s**, y el audio se genera más rápido de lo que
  dura, así que no se corta una vez que empieza.

**Contrato (reemplaza al de D-157):**

| Pedido | Respuestas |
|---|---|
| `POST /api/v1/renasia/voz` `{"texto"}` | **201** `{"audio":"/api/v1/renasia/voz/{id}"}`: el audio **ya empieza a generarse**. **204** sin voz del servidor. 400 / 403 como antes |
| `GET /api/v1/renasia/voz/{id}` | **200** `audio/wav` transmitido mientras se genera (`StreamingResponseBody`). **204** si falló sin producir sonido (espera hasta 8 s). **404** si no existe, venció (2 min) o es de otra persona |

**Por qué dos pasos:** el reproductor del teléfono (ExoPlayer vía expo-audio) solo sabe bajar una
URL con headers, pero sí toca un WAV mientras baja. Así sirve con la app ya instalada, sin
módulos nativos nuevos. Como el `POST` arranca la generación, la segunda oración ya está lista
cuando le toca sonar.

**Piezas:**
- `SintetizarVozPort` pasó a entregar el audio por partes: `disponible()` y
  `sintetizar(texto, destino)`. Nunca lanza.
- `GeminiVozAdapter` (`renaser.ia.voz.proveedor=gemini`) pide la Interactions API con
  `stream: true` y reenvía cada `step.delta`. Esos pedazos son PCM crudo `audio/l16` (16 bits,
  mono, 24 kHz); el adaptador les antepone una cabecera WAV con largo `0xFFFFFFFF`, porque todavía
  no se sabe cuánto va a durar. Usa la misma key que el chat y nunca registra el texto ni la key.
- `PiperVozAdapter` entrega su WAV de una vez.
- `VozDelOrbeService` genera en hilos virtuales y guarda cada audio en un `AudioEnCurso`: un búfer
  que se puede leer desde el principio mientras sigue creciendo.
- Tiene un tope de 300 audios en memoria. Pasado el tope, la app usa su propia voz.
- Sin `@Transactional` (C-1).

**Por qué en memoria y no Kafka ni Redis:** el audio vive 2 minutos y lo lee una sola persona.
Kafka sirve para eventos durables entre servicios y acá solo sumaría infraestructura y demora.
Producción es **una sola EC2**, así que la memoria alcanza. **Límite conocido:** con varias
instancias, el `GET` podría caer en otra y dar 404. Ahí se pasa a Redis, cambiando solo dónde se
guarda.

**Configuración** (`renaser.ia.voz.gemini.*`): `modelo`, `voz`, `estilo` (la instrucción de cómo
hablar, que es lo que la hace sonar fluida) y `timeout-ms`, todos cambiables por entorno.

**Costo:** flash-lite TTS cuesta US$0,50 por millón de tokens de texto y US$6 por millón de tokens
de audio (~25 por segundo) hasta el 2026-12-31. Se duplica desde el 2027-01-01. Una respuesta
hablada de 10 s cuesta unos US$0,0015.

**A futuro (no hecho):** la experiencia más humana sería **Gemini Live**, voz a voz en tiempo real
por WebSocket y con interrupciones. Pide rehacer herramientas, propuestas, cuota e historial dentro
de una sesión Live, más audio nativo en la app: es otro proyecto, con su propio diseño. El
`Locutor` y el orbe de la app se reusan.

---

## 4. Estructura del módulo

Tres agregados reales (cada uno con identidad, ciclo de vida y repositorio propios):

```
rag/
├── package-info.java                    (@ApplicationModule)
├── api/                                  (@NamedInterface — PatronDeMalestarRepetidoEvent, D-143)
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

### Contrato SSE de `POST /api/v1/renasia/mensajes` (actualizado 2026-09-23, D-153)

Formas de `data:` (fuente de verdad: `EventoRenasiaSseMapper`):

    data: {"tipo":"texto","valor":"..."}
    data: {"tipo":"propuesta","id":"<uuid>","resumen":"Marcar 'Meditar' como hecho (+10 puntos si lo confirmas ahora)","venceEn":"2026-09-23T15:10:00Z"}
    data: {"tipo":"fuentes","lecciones":["leccion-id-1"]}
    data: {"tipo":"error","valor":"mensaje apto para mostrar"}
    data: {"tipo":"fin"}

- `propuesta.id` se usa en `POST /api/v1/renasia/propuestas/{id}/confirmar` y `/cancelar`.
- `venceEn` es `Instant.toString()` (UTC, puede traer fracciones de segundo). Pasado ese instante,
  `confirmar` responde 409; la app puede ocultar los botones.

**Orden garantizado:** textos del modelo → por cada propuesta del turno (de la más vieja a la más
nueva) un `texto` `"\n\nPropuesta: <resumen>"` seguido de su `propuesta` → texto de apoyo (D-143)
→ `fuentes` (a lo sumo una vez) → `fin` (siempre último). Si el modelo falla, el turno es
`error` + `fin` y no trae propuestas.

**Compatibilidad:** un `tipo` desconocido se ignora en silencio y la app no se actualiza por aire,
así que cada propuesta viaja además como `texto`: una app vieja muestra "Propuesta: …" aunque no
pueda confirmarla. Ese texto queda guardado en el mensaje del asistente; los botones no se
redibujan desde el historial. **El texto del chat nunca confirma nada**: solo el endpoint.


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
| **Credenciales de Gemini** (D-39) | **Parcialmente resuelto (2026-09-03).** Los adaptadores reales (`GoogleGenAiRenasiaChatAdapter`, `GoogleGenAiEmbeddingAdapter`) y su `@Configuration` (`GoogleGenAiClientesConfig`) ya están escritos, detrás de `renaser.ia.proveedor=google`. Sin `GOOGLE_GENAI_API_KEY` real, el default (`renaser.ia.proveedor=noop`) sigue activando los `NoOp*`. Lo que falta: probar el camino `google` con una API key real (nadie corrió `./mvnw` contra Gemini de verdad) y que Producto cierre la voz de marca. **Corregido 2026-09-04 (D-89):** esta celda decía que el prompt es "un placeholder explícito" cuya única regla es la abstención. Ya no: `prompts/renasia-sistema.st` se reescribió a pedido del dueño y ahora define un **orden de fuentes con atribución obligatoria** — contenido del programa → búsqueda web → conocimiento general —, más límites clínicos y una cláusula de crisis. La búsqueda web real se enciende con `renaser.ia.busqueda-web` (`IA_BUSQUEDA_WEB`; default `true` al escribirse esto, **bajado a `false` el mismo día por D-100**: `gemini-3.1-flash-lite` no la soporta y el stream moría sin respuesta), que setea `GoogleGenAiChatOptions.googleSearchRetrieval(true)`. Sigue faltando la voz de marca, no la regla de negocio. `PromptSistemaRenasiaTest` renderiza el `.st` real para que un error de sintaxis de StringTemplate falle en la suite y no en producción |
| **Sin datos en `base_conocimiento`** | Es esperable: la ingesta es admin (D-46) y el contenido llega en la fase de migración de datos |
| **Clasificador de riesgo real** (D-82) | Existe la estructura (`EvaluarRiesgoMensajePort` + `NoOpEvaluacionRiesgoAdapter`), sin conectar. **D-143 NO lo desbloquea** (2026-09-15): la repetición de expresiones de malestar es un conteo de frases, no una clasificación, y las señales explícitas de peligro quedan deliberadamente fuera de ese mecanismo — siguen sin cubrir. Falta: (1) confirmar si `NivelRiesgo` necesita un tercer estado "indeterminado", (2) el mapeo completo de `EvaluacionRiesgo` a modo de respuesta (firmado por el dueño del producto) y (3) los criterios de detección en sí (firmados por un profesional con licencia). Depende además de D-80 (edad/país confiables) para el camino de crisis |

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
