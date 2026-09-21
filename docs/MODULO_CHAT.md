# Módulo `chat` — Conversaciones, Mensajes, WebSocket + Redis Pub/Sub

**Fecha:** 2026-08-25
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/PLAN_DE_MODULOS.md` §10 (agregados sugeridos, Ola 4) · `docs/MODULO_COMMUNITY.md`, `docs/MODULO_NOTIFICATIONS.md` (patrones replicados acá: eventos cross-módulo, `@ApplicationModuleListener`)

---

## 0. Estado

🔄 **Construido. `./mvnw clean test` no se corrió desde este agente** (regla del encargo: no ejecutar Maven ni git) — lo corre el supervisor.

**Fuentes usadas para las reglas de negocio — importante:** este módulo se construyó **sin acceso ni referencia al backend Next.js viejo** (no se leyó `Backend90dias/RenaserBack`, `renaser backend/RenaserBackCopy` ni `renaser90 dias/RenaserBack`). Las únicas fuentes fueron: `src/main/resources/db/migration/V1__baseline_renaser.sql` (schema real, sección `-- CHAT`, líneas 1274-1336), `CLAUDE.MD` (arquitectura y reglas de trabajo), `docs/PLAN_DE_MODULOS.md` §10, y los módulos Java ya construidos (`community`, `points`, `users`) como plantilla de estructura. Donde el esquema no alcanzaba para decidir una regla de negocio (ver §6), se documentó como pregunta abierta en vez de inventarla.

---

## 1. Esquema real (`V1__baseline_renaser.sql:1274-1336`)

- `conversaciones`: `tipo` (`CELULA`/`DIRECTA`/`GLOBAL`/`SOPORTE`) con el CHECK `tipo_coherente` — cada tipo exige exactamente un campo identificador propio (`celula_id`, `clave_directa` para DIRECTA **y SOPORTE**, o ninguno para GLOBAL). Índice único parcial `conversacion_global_unica_uk` garantiza una sola fila `GLOBAL`.
  > **Corregido 2026-09-16 (D-136).** Esta línea decía `tipo` (`CELULA`/`DIRECTA`/`GLOBAL`) y que el CHECK cubría esos tres. `V53` agregó el valor `SOPORTE` y `V54` amplió el CHECK con su rama — ver §8.
- `participantes_conversacion`: PK compuesta `(conversacion_id, usuario_id)`, con `ultimo_leido_en` nullable — es la base del conteo de no-leídos.
- `mensajes`: `tipo` (`TEXTO`/`IMAGEN`/`AUDIO`/`VIDEO`/`SISTEMA`), dos CHECK (`mensaje_con_contenido`, `media_completa`), `respuesta_a_id` auto-referencial (hilos), `oculto`/`eliminado_en` para moderación (sin caso de uso que los mute en esta pasada — ver §6).
- `mensajes_bienvenida`: **no se tocó** — no hay caso de uso que la use (ver §6).
- Comentario del baseline (línea 1293-1295): *"todo usuario nuevo se agrega AUTOMÁTICAMENTE a la conversación GLOBAL"* — es la única regla de negocio que el propio schema deja explícita en un comentario; se implementó tal cual (§4).

---

## 2. Integración con `users` y `community`

`chat` no posee `usuarios` ni `celulas`. Lee/reacciona a ellos de dos formas:

1. **`users.api.UserSummaryFinder`** — rol/estado del actor en cada caso de uso (`requireActivo`), mismo patrón que `community`/`points`.
2. **Eventos de dominio nuevos, publicados por otros módulos** (ninguno existía antes de este encargo):
   - `users.api.UsuarioRegistradoEvent` — se agregó el `publishEvent` en `AccountRequestService.approve()` (tras `saveAccountRequestPort.save(request)`) y en `UserAccountService.invite()` (tras `ensureMentorProfileIfNeeded(saved)`). Se inyectó `ApplicationEventPublisher events` en ambos servicios. **No se tocó ninguna otra lógica de `users`.**
   - `community.api.CelulaCreadaEvent` — se agregó el `publishEvent` en `CelulaService.crear()` (tras `saveCelulaPort.save(celula)`). Se inyectó `ApplicationEventPublisher events`. **No se tocó ninguna otra lógica de `community`.**

Los tres tests unitarios existentes (`AccountRequestServiceTest`, `UserAccountServiceTest`, `CelulaServiceTest`) se actualizaron para mockear `ApplicationEventPublisher` en el constructor — sin cambiar ningún assert de negocio preexistente.

---

## 3. Qué se construyó

### 3.1 Agregados (`domain/model/`)

`conversacion/` (`Conversacion`, `ConversacionId`, `TipoConversacion`, `Participante` — un solo agregado, PLAN_DE_MODULOS.md linea 132: "conversacion/ (con Participante)"), `mensaje/` (`Mensaje`, `MensajeId`, `TipoMensaje`).

`Participante` vive dentro de `conversacion/` sin ser un agregado propio: no tiene sentido ni identidad sin su conversación (regla de subcarpeta de CLAUDE.MD §5.1.2 — un agregado, no una carpeta por capa).

### 3.2 Casos de uso

| Agregado | Casos de uso |
|---|---|
| `conversacion` | CrearConversacionDirecta (busca-o-crea), ListarConversaciones (con último mensaje + no-leídos, en lote), MarcarLeido, UnirseAConversacionGlobal (interno, disparado por evento), CrearConversacionCelula (interno, disparado por evento) |
| `mensaje` | EnviarMensaje, ListarMensajes (paginación keyset), CompartirPublicacion (del Muro, delega en EnviarMensaje) |

### 3.3 Endpoints REST

| Método | Ruta | Notas |
|---|---|---|
| POST | `/api/v1/chat/conversations/direct` | `{otherUserId}` → busca-o-crea, 201 |
| GET | `/api/v1/chat/conversations` | mis conversaciones, con `unreadCount` y `lastMessage` |
| POST | `/api/v1/chat/conversations/{id}/read` | marca leído hasta ahora |
| POST | `/api/v1/chat/conversations/{conversationId}/messages` | enviar, 201 |
| POST | `/api/v1/chat/conversations/{conversationId}/messages/share-wall-post` | `{postId}` → comparte una publicación del Muro, 201 con el mismo `MensajeResponse` que enviar |
| GET | `/api/v1/chat/conversations/{conversationId}/messages?cursor=&limit=` | paginación keyset por `creado_en`, descendente |

Todos reciben el actor por `X-Actor-Id` (mismo patrón temporal que el resto de los módulos ya construidos, sin JWT — bloqueante del usuario documentado en `docs/MODULOS_A_AVANZAR.md`).

**D-36 aplicado:** `TipoConversacion`/`TipoMensaje` viven en español en dominio y base; el wire habla inglés (`CELL`/`DIRECT`/`GLOBAL`/`SUPPORT`, `TEXT`/`IMAGE`/`AUDIO`/`VIDEO`/`SYSTEM`) — la traducción vive solo en `ConversacionResponse.toWireTipo`/`MensajeResponse.toWireTipo` (salida) y `MensajeController.parseTipoMensaje` (entrada), nunca en dominio ni persistencia.
> **Corregido 2026-09-16 (D-136).** Esta línea listaba solo `CELL`/`DIRECT`/`GLOBAL`. `SOPORTE` -> `SUPPORT` se suma en §8, y la lista de endpoints de arriba no incluye los dos de soporte (`POST .../{id}/leave` y `POST /api/v1/admin/chat/support-conversations/backfill`): están en §8.3.

### 3.4 WebSocket + Redis Pub/Sub

- `infrastructure/adapter/in/websocket/WebSocketConfig`: endpoint STOMP `/ws`, broker simple `/topic`, prefijo de aplicación `/app`. El cliente se suscribe a `/topic/conversaciones/{conversacionId}`.
- `infrastructure/adapter/out/redis/RedisChatPublisher` (implementa `PublicarMensajeFanoutPort`): publica a Redis (canal `chat:conversacion:{id}`) **después** del commit de la transacción que guardó el mensaje — `MensajeService.publicarDespuesDelCommit` usa `TransactionSynchronizationManager.registerSynchronization(...).afterCommit(...)`, el mismo mecanismo que ya usa `AccountRequestService` de `users` para su compensación de Supabase. Fire-and-forget: si Redis falla, se loguea y se sigue (el mensaje ya está durable en Postgres).
- `infrastructure/adapter/out/redis/RedisChatSubscriberConfig`: cada instancia se suscribe al patrón `chat:conversacion:*` y reenvía el payload (JSON crudo, sin re-serializar) a `/topic/conversaciones/{id}` vía `SimpMessagingTemplate` — así una instancia distinta a la que recibió el POST también entrega el mensaje en vivo (CLAUDE.MD §5.2.1).
- **Honestidad sobre lo que esto prueba:** la arquitectura compila y el mecanismo (persistir → publicar tras commit → re-suscribir → STOMP) sigue el patrón documentado en CLAUDE.MD §5.2.1 al pie de la letra, pero **no hay verificación E2E con un cliente STOMP/WebSocket real** en este encargo (no hay herramienta de este agente para abrir un socket real contra la app corriendo) — queda pendiente para una fase de pruebas manuales o un test de integración con un cliente STOMP de prueba (`spring-websocket` trae uno).

---

## 4. Reglas de dominio implementadas

- **`Conversacion`**: invariante `tipo_coherente` replicada en dominio (`requireTipoCoherente`) — falla con `IllegalArgumentException` (400) antes de llegar al CHECK de Postgres (500), tanto en las fábricas (`crearCelula`/`crearDirecta`/`crearGlobal`) como en `rehydrate` (defensivo contra datos corruptos).
- **GLOBAL única e idempotente**: `ConversacionService.unirse()` hace busca-o-crea (`loadConversacionPort.global().orElseGet(...)`), protegido además por el índice único parcial de la base. Ventana de carrera teórica entre dos altas simultáneas (dos instancias sin GLOBAL creando cada una la suya) documentada como aceptada — el mismo criterio que ya explica `GlobalExceptionHandler.handleIntegridad` para el "doble tap" del cliente: la segunda pierde la carrera y su `INSERT` viola el índice único, se traduce a 409. Como esto corre en un listener de evento (no en un request HTTP), el 409 no llega a ningún cliente — Modulith reintenta el evento según su política de outbox.
- **`claveDirectaDe`**: orden lexicográfico `menor_mayor` de los dos UUID como string — determinístico sin importar quién inicia la conversación.
- **`Mensaje`**: invariantes `mensaje_con_contenido` (SISTEMA no necesita texto/media, cualquier otro tipo sí) y `media_completa` (`mediaBucket`/`mediaRuta` viajan juntos o ninguno) replicadas en `Mensaje.escribir`.
- **`EnviarMensaje`**: el emisor debe ser participante (`NotAuthorizedException` si no) — chequeado ANTES de escribir el mensaje. Si `respuestaAId` viene, se verifica que el mensaje original pertenezca a la MISMA conversación (`requireRespuestaEnMismaConversacion`) — evita citar un mensaje de otra conversación por error de cliente o ataque de enumeración de IDs. Al enviar, se actualiza `ultimo_leido_en` del EMISOR (ya "leyó" lo que acaba de escribir).
- **`ListarConversaciones` sin N+1**: `ultimosPorConversacion`/`contarNoLeidos` reciben la lista completa de `ConversacionId` y devuelven un `Map` en una sola consulta cada uno — nunca una consulta por conversación. Verificado con `verify(..., times(1))` en `ConversacionServiceTest` y contra Postgres real en `ChatPersistenceAdapterTest`.
- **`MarcarLeido`**: exige participante (`NotAuthorizedException` si no).
- **Paginación de mensajes**: keyset por `creado_en` (`WHERE creado_en < :cursor ORDER BY creado_en DESC`), nunca `OFFSET` — mismo criterio que el feed de `community`, y mismo cuidado con el bug E-31 (ver §5.1 más abajo: acá se evitó de entrada partiendo en dos métodos, `paginaSinCursor`/`paginaConCursor`).

---

## 5. Decisiones propias de este módulo (prefijo `CH-`)

| # | Decisión |
|---|---|
| CH-1 | **`ultimoLeidoEn` arranca en el momento de unirse, no `null`** (`Participante.unirse`). Sin confirmar con negocio: la alternativa (arrancar `null` = "nunca leyó nada") haría que un usuario nuevo viera como no-leído TODO el historial previo de GLOBAL, potencialmente miles de mensajes — decisión de producto razonable pero no confirmada, documentada en el propio `Participante.java`. |
| CH-2 | **`agregar()` es idempotente y NO pisa `ultimo_leido_en` si el participante ya existe** (`ParticipanteConversacionPersistenceAdapter.agregar`: `existsByConversacionIdAndUsuarioId` corta antes del `save`). Importante porque el listener de `UsuarioRegistradoEvent` puede reintentarse (outbox de Modulith) — un reintento no debe "resetear" cuánto leyó ya el usuario. |
| CH-3 | **`CrearConversacionCelulaUseCase` solo crea la fila de la conversación**, no agrega participantes. `community` no publica hoy un evento de "miembro agregado/quitado de célula" (solo `CelulaCreadaEvent`, agregado en este mismo encargo) — sin esa señal, no hay forma de saber quién debería ser participante del chat de una célula. Ver §6. |
| CH-4 | **`DELETE`/edición/moderación de mensajes NO se construyó** — `Mensaje.oculto`/`eliminadoEn` existen en el dominio (reflejan lo que ya persiste la base) pero sin mutadores ni caso de uso: el encargo original solo pidió enviar/listar/marcar-leído/crear-o-obtener-directa. Igual que `oculto`/`eliminado_en`, quedan listos para cuando se pida moderación. |
| CH-5 | **Retención de 12 meses de GLOBAL (cron) NO se construyó** — explícitamente fuera de alcance por instrucción del encargo original. |
| CH-6 | **`mensajes_bienvenida` NO se tocó** — ningún caso de uso de este encargo escribe ni lee esa tabla; no hay evidencia de una regla de negocio confirmada sobre cuándo/cómo se genera un mensaje de bienvenida (¿automático al unirse a GLOBAL? ¿manual de un mentor?). Se documenta en vez de inventarla. |
| CH-7 | **Redis Pub/Sub, no STOMP broker relay a RabbitMQ** — CLAUDE.MD §5.2.1 ya deja esto resuelto ("con Redis ya en el stack por caché, es el punto de partida por defecto"); se siguió tal cual. |
| CH-8 | **El fanout de Redis (`MensajeFanoutPayload`) es un DTO liviano, no el `Mensaje` completo ni el `MensajeResponse` del contrato REST** — el cliente que recibe el push solo necesita saber "hay un mensaje nuevo, refrescá"; el contenido completo con paginación keyset sigue viniendo de `GET .../messages`. Evita acoplar el adaptador de Redis al contrato REST. |
| CH-9 | **`EnviarMensaje` no valida `mediaBucket`/`mediaRuta` contra un `AlmacenamientoPort`** — a diferencia de `community` (`SolicitarUrlSubidaMediaUseCase`), este encargo no pidió el flujo de subida prefirmada para chat; el request acepta los campos de media ya resueltos (asumiendo que el cliente los obtuvo por otra vía, hoy inexistente). Documentado como hueco, no como decisión definitiva — ver §6. |

---

## 6. Qué NO se construyó / preguntas abiertas

- **Participantes de una conversación CELULA** (CH-3) — bloqueado hasta que `community` publique un evento de membresía de célula. Pregunta abierta real: ¿ese evento debería vivir en `community.api` (`MiembroCelulaAgregadoEvent`) el día que se construya la asignación de aprendices a células (bloqueada hoy, ver `docs/MODULO_COMMUNITY.md` CM-2)?
- **Flujo de subida de media para chat** (CH-9) — no hay `AlmacenamientoPort` con prefijo `chat/` conectado a ningún endpoint todavía (PLAN_DE_MODULOS.md linea 133 lo sugiere: `AlmacenamientoPort` S3 `chat/`). `EnviarMensaje` acepta los campos de media pero no hay forma real de que el cliente los obtenga hoy.
- **Moderación de mensajes** (ocultar/eliminar) — CH-4.
- **Retención de 12 meses / cron de purga de GLOBAL** — CH-5.
- **`mensajes_bienvenida`** — CH-6.
- **Verificación E2E de WebSocket con un cliente real** — no se hizo en este encargo (ver §3.4).
- **`@RequiresPermission`/`@PublicEndpoint` + test de reflexión** — el mecanismo sigue sin existir en `shared/` (mismo bloqueante que el resto de los módulos ya construidos, `docs/MODULOS_A_AVANZAR.md`).
- **Filtro JWT real** — todos los controllers usan `X-Actor-Id` (bloqueante general del proyecto).

---

## 7. Pruebas

| Tipo | Cobertura |
|---|---|
| Unitarias de dominio | `ConversacionTest` (invariante `tipo_coherente` en las 3 fábricas y en `rehydrate`, simetría de `claveDirectaDe`), `MensajeTest` (`mensaje_con_contenido`, `media_completa`, positivos de `mediaBytes`/`mediaDuracionS`) — sin Spring, sin Postgres. |
| Unitarias de `application/services` (Mockito) | `ConversacionServiceTest` (actor suspendido rechazado en las 4 operaciones, idempotencia de `obtenerOCrear`/`unirse`/`crearParaCelula`, no-participante rechazado en `marcarLeido`, conteo de no-leídos/último-mensaje resuelto en **una sola llamada** cada uno — verificado con `times(1)`), `MensajeServiceTest` (no-participante y actor suspendido rechazados en `enviar`/`listar`, el fanout se publica después de guardar, paginación con `hayMas`/`siguienteCursor`). |
| Seguridad | Cubierta dentro de los tests de servicio de arriba: todo caso de uso rechaza a un actor `SUSPENDED` vía `UserSummaryFinder` (`NotAuthorizedException`, CLAUDE.MD §0.3). No hay tests de integración HTTP (`@SpringBootTest` con `MockMvc`) en este encargo — mismo criterio que el resto de los módulos ya construidos, que prueban autorización a nivel de servicio. |
| Integración con Testcontainers (Postgres real) | `ChatPersistenceAdapterTest`: persistencia de conversación directa + participantes, **unicidad de GLOBAL contra el índice real** (`DataIntegrityViolationException`), idempotencia de `agregar()` sin pisar `ultimo_leido_en`, conteo de no-leídos en lote, último-mensaje-por-conversación en lote (`DISTINCT ON`), paginación keyset de mensajes. |
| Listeners de eventos | `UsuarioRegistradoChatListenerTest`, `CelulaCreadaChatListenerTest` — unit puro (sin Spring), mismo criterio que `HabitoCompletadoNotificationListenerTest` de `notifications`: confirman que el listener traduce el evento al caso de uso correcto; la entrega real vía el outbox de Modulith es infraestructura de Spring, no se re-prueba acá. |

---

## Auditoría de arquitectura (2026-08-28) — agente automático

Alcance: solo lectura, `src/main/java/com/renaser/os/chat/`, contra las reglas de CLAUDE.md §5.1/§5.1.2/§5.4. No se corrió `./mvnw`.

**Estructura del módulo** (75 archivos `.java`): `domain/model/{conversacion,mensaje}` (7 clases), `application/{ports/in,ports/out,services}`, `infrastructure/adapter/{in/rest, in/websocket, in/event, out/persistence, out/redis}`, `api/` (solo `package-info.java` con `@NamedInterface`, sin clases — ningún otro módulo importa `chat.*` hoy, confirmado por grep). `package-info.java` de la raíz lleva `@ApplicationModule(displayName = "Chat")`.

1. **`domain/` limpio — sin violaciones.** Grep de imports en `domain/model/{conversacion,mensaje}/*.java` no devuelve nada fuera de `com.renaser.os.chat.*`, `com.renaser.os.shared.domain.UserId`, `java.*` y `lombok.*`. Cero `org.springframework.*` / `jakarta.persistence.*`. Lombok usado según lo permitido (`@Getter`, `@AllArgsConstructor(access = PRIVATE)`, `@EqualsAndHashCode(of = "id")`, `@Accessors(fluent = true)`); sin `@Data`/`@Setter`/`@NoArgsConstructor` público. `toString()` acotado, sin PII (solo IDs/tipo).

2. **Subcarpetas de `domain/` correctas contra la regla de agregado (§5.1.2).** Dos agregados independientes, cada uno en su carpeta: `conversacion/` (`Conversacion`, `ConversacionId`, `TipoConversacion`, y `Participante` — este último documentado explícitamente en el Javadoc de `Participante.java:14-17` como parte del agregado `Conversacion`, no un agregado propio) y `mensaje/` (`Mensaje`, `MensajeId`, `TipoMensaje`). Ninguna subcarpeta por capa. Cumple.

3. **Controllers REST — "adaptador tonto", cumplen.** `ConversacionController.java`, `MensajeController.java`, `MiembroController.java`: cada endpoint son 1-9 líneas (techo ~15), solo inyectan casos de uso (`*UseCase`), sin `@Transactional`, sin `if` de negocio, sin puertos `out` ni repositorios inyectados. Mapeo de salida a mano (`MensajeResponse.from(...)`, `ConversacionResponse.from(...)`) — proyecciones explícitas, no la entidad serializada.

4. **Adaptadores WebSocket — mayormente cumplen, un hallazgo de fondo real:**
   - `WebSocketConfig.java`: solo configuración, sin lógica — correcto.
   - `ActorHandshakeInterceptor.java` (46 líneas) y `SubscripcionAutorizadaInterceptor.java` (78 líneas): tontos en el sentido de la regla (delegan la decisión de negocio — "¿es participante?", "¿está ACTIVE?" — a `EsParticipantePort`/`UserSummaryFinder`, no la calculan ellos mismos).
   - **Hallazgo (H-1):** `ActorHandshakeInterceptor.java:27-33` identifica al actor leyendo el header `X-Actor-Id` **directo y exclusivo** — nunca consulta `SecurityContextHolder`. En cambio, el lado REST (`ActorAutenticadoArgumentResolver.java:43-47`) intenta primero la sesión y solo cae al header si no hay `Authentication` real. Hoy es inofensivo porque `SecurityConfig` sigue en `permitAll()` en todos los perfiles (ningún actor llega autenticado por sesión todavía), pero el día que se active `authenticated()` en el resto de la API (paso ya previsto, CLAUDE.MD §12), el WebSocket seguirá aceptando cualquier `X-Actor-Id` sin verificar sesión real, mientras el REST sí exigirá una — una asimetría de seguridad que conviene resolver junto con esa activación, no después.
   - **Hallazgo menor (H-2):** doc desactualizado — `docs/MODULO_CHAT.md` §6 (línea ~115, antes de este agregado) dice *"todos los controllers usan `X-Actor-Id`"*, pero el código real usa `@ActorAutenticado` (sesión primero, header como fallback, ver `ActorAutenticadoArgumentResolver.java`). Contradice CLAUDE.MD §0.4 ("los documentos no pueden contradecirse"); no se corrigió en este agregado porque el encargo fue solo auditar y anexar, no editar secciones previas.

5. **Nombres prohibidos: ninguno.** Sin `Util`/`Helper`/`Manager`/`Processor`/`Info` sueltos. Los `SpringData*Repository` son la convención estándar de Spring Data JPA (interfaz, no clase con lógica) — no es el antipatrón que la regla busca prevenir.

6. **Tamaños — todos dentro de los techos de §5.4.8.** Archivo más grande: `MensajeService.java` (199 líneas, bajo el techo de 300). Métodos revisados en los tres servicios de aplicación (`MensajeService`, `ConversacionService`, `MiembroService`) — ninguno supera ~25 líneas ni 2 niveles de anidamiento; guard clauses (`requireActivo`, `requireParticipante`, `requireConversacion`) nombradas por intención, no genéricas.

7. **Mapeo: `application` ↔ `adapter/out/persistence` es a mano, no MapStruct** (`ConversacionPersistenceMapper.java`, `MensajePersistenceMapper.java`) — diverge de CLAUDE.MD §5.4.5 ("MapStruct solo en esta frontera"), aunque no rompe el principio de dependencia ni introduce riesgo: el mapeo es campo-a-campo, explícito, sin lógica. No es una violación arquitectónica, es una elección de herramienta distinta a la documentada como default.

8. **Frontera web → aplicación → dominio: sin fugas.** `MensajeResponse`/`ConversacionResponse`/`MiembroResponse` son proyecciones explícitas a mano (Full/Two-Way Mapping manual, según corresponde); ningún DTO de entrada ni de salida tiene anotaciones de dos mundos (`@Entity` + `@JsonProperty`) — confirmado por lectura de las clases de request/response y de `*JpaEntity`.

9. **Logging: cumple.** `domain/` no loguea. `RedisChatPublisher.java:47-51` (único log de `adapter/out` revisado) usa `WARN` con `mensaje.id()`/`conversacionId` — sin texto del mensaje, sin datos de usuario.

**Conclusión:** el módulo `chat` es hexagonalmente correcto y ya viene con una autocrítica inusualmente completa en su propio Javadoc (invariantes de BD replicadas en dominio, decisiones de mapeo documentadas in situ). El único hallazgo con relevancia de seguridad real es H-1 (asimetría de resolución de actor REST vs. WebSocket), a resolver cuando se active `authenticated()` globalmente — no bloqueante hoy porque `permitAll()` sigue activo en todos los perfiles.

---

## 8. El chat de soporte por aprendiz (2026-09-16, D-136)

**Qué pidió el dueño del proyecto, textual:** *"en comunidad miembros se visualizará un grupo de las
personas, solo él y el staff de un administrador o alquimista, por cada uno que entra, automático
debe de ser"*.

Las cinco reglas, confirmadas por él y **no** ampliadas por cuenta propia:

1. **Una** conversación por aprendiz. Adentro: el aprendiz y **todos** los usuarios `ACTIVO` con rol
   `ADMIN` o `ALCHEMIST`. Nadie más — ni mentor, ni líder de mentores.
2. Nace sola cuando el aprendiz **entra al programa**, no al registrarse sin aprobar.
3. Un `ADMIN`/`ALCHEMIST` nuevo —o alguien que **pasa** a ese rol— se suma a las conversaciones de
   soporte que ya existen.
4. El staff **puede** salirse. El aprendiz **no**.
5. Los aprendices que ya estaban también la reciben, por un endpoint de administración explícito.

### 8.1 Por qué `UsuarioRegistradoEvent` es el evento correcto

Es el único punto del código que marca "entró al programa", y lo es por construcción:

| Camino | ¿Publica `UsuarioRegistradoEvent`? | ¿Hay fila en `participantes_programa`? |
|---|---|---|
| Registrarse (sin aprobar) | **No** — la fila de `usuarios` nace en `INACTIVE` y no se publica nada | No |
| `AccountRequestService.approve` | Sí, **después** de `saveParticipacionProgramaPort.save(...)`, misma transacción | **Sí** |
| `UserAccountService.invite` / `inviteStaff` | Sí | No (es staff) |

Como `@ApplicationModuleListener` corre **después del commit**, cuando el listener se ejecuta la fila
de `participantes_programa` ya existe y se puede consultar. El servicio igual **verifica
`inscrito`** antes de crear nada: así el camino de invitación —que no crea participación— no genera
un soporte para alguien que todavía no entró.

Se descartó crear un evento nuevo (`AprendizIngresoAlProgramaEvent`) habiendo uno que ya marca ese
instante: dos eventos para el mismo hecho se desincronizan en cuanto alguien agrega un tercer camino
de alta y se acuerda de publicar solo uno.

**Lo que sí hubo que agregar a `users`:** `RolDeUsuarioCambiadoEvent`, publicado por
`UserAccountService.updateRole` **solo si el rol cambió de verdad**. Sin él, la regla 3 quedaba a
medias: alguien promovido a ADMIN veía únicamente los soportes de los aprendices que entraran
*después* de su ascenso. Cambiar de rol no publicaba nada hasta hoy.

### 8.2 Por qué no hay tabla ni columna nueva

Instrucción explícita del dueño ("no crear tablas de más") y, además, no hacían falta.

- La identidad *"el soporte de tal aprendiz"* va en `conversaciones.clave_directa` con el valor
  `'soporte:' || <uuid del aprendiz>`. Esa columna ya tiene índice **UNIQUE**
  (`conversaciones_clave_directa_key`, V1:1282), así que **la base misma** impide dos conversaciones
  de soporte para la misma persona. No colisiona con las claves de DM, que son `<uuid>_<uuid>`.
- **Eso cambia dónde vive la garantía:** "buscar y si no existe crear" es un check-then-act que dos
  entregas simultáneas del mismo evento pasan las dos. La lectura previa está solo para no intentar
  el INSERT al pedo; quien realmente corta el duplicado es el UNIQUE, y el servicio atrapa la
  `DataIntegrityViolationException` como "la creó otro primero".
- Una columna `aprendiz_soporte_id uuid UNIQUE` habría quedado `NULL` en el 100% de las filas de los
  otros tres tipos y habría duplicado un índice único que ya existía.

Las **dos** migraciones (`V53` declara el valor `SOPORTE`, `V54` amplía el `CHECK tipo_coherente`)
son dos y no una porque Postgres no deja usar un valor de enum en la transacción que lo crea — el
mensaje literal y el detalle están en [`E-187`](BITACORA_ERRORES.md).

### 8.3 Qué se construyó

| Pieza | Dónde |
|---|---|
| `TipoConversacion.SOPORTE` + `crearSoporte` / `claveSoporteDe` / `esAprendizDeSoporte` | `domain/model/conversacion/` |
| `IncorporarUsuarioAlSoporteUseCase` (una puerta; el dominio decide qué significa según el rol) | `application/ports/in/conversacion/` |
| `RellenarConversacionesDeSoporteUseCase`, `SalirDeConversacionSoporteUseCase` | idem |
| `LoadConversacionPort.deSoporte()` | `application/ports/out/conversacion/` |
| `ConversacionSoporteService` | `application/services/` |
| `UsuarioRegistradoSoporteListener`, `RolDeUsuarioCambiadoSoporteListener` | `adapter/in/event/` |
| `ConversacionSoporteController` (`POST .../{id}/leave`, `POST /admin/chat/support-conversations/backfill`) | `adapter/in/rest/conversacion/` |

**Wire (D-36):** `SOPORTE` viaja como **`SUPPORT`**, junto a `CELL`/`DIRECT`/`GLOBAL`. La traducción
sigue viviendo solo en `ConversacionResponse.toWireTipo`, y ahora la fija un test
(`ConversacionResponseTest`): el `switch` es exhaustivo, así que olvidarse no compila, pero
equivocar la palabra no lo nota ningún compilador — lo nota el teléfono de alguien.

### 8.4 Decisiones de este agregado (prefijo `CH-`)

| # | Decisión |
|---|---|
| CH-10 | **El relleno es un endpoint, no un barrido al arrancar.** Una corrida masiva en el arranque crea N conversaciones en todo entorno que levante —incluido el de un desarrollador— y cuando alguien lo nota ya pasó. `POST /api/v1/admin/chat/support-conversations/backfill` lo dispara una persona, y la respuesta dice `traineesReviewed / created / alreadyExisted / failed`. Idempotente. |
| CH-11 | **Nada reconcilia participantes, nunca** — con **una excepción acotada, CH-16**. Es la consecuencia directa de la regla 4: si el staff se puede ir, una sincronización *"dejalo como debería estar"* le desharía la salida en el próximo evento. Por eso el relleno **no toca** una conversación que ya existe aunque le falte alguien del staff, y `incorporar` solo **suma**. Es la diferencia con `ParticipantesCelulaService`, que sí reconcilia — ahí la composición la manda `community`, acá la manda la persona. |
| CH-12 | **El `nombre` es una foto del momento de creación** (`"Soporte - <nombre del aprendiz>"`). Lleva el nombre porque quien más ve estas conversaciones es el staff, y sin nombre tendría 25 filas idénticas — el mismo problema que ya arregló el listado de mensajes directos. **Limitación conocida:** si la persona se cambia el nombre después, el título no se entera. Derivarlo en cada lectura obligaría a resolver el aprendiz de cada soporte al listar; no se hizo porque nadie lo pidió, y queda escrito acá en vez de quedar como olvido. |
| CH-13 | **Salir es solo de un SOPORTE.** Irse de una CÉLULA, de un DM o de la GLOBAL son tres preguntas distintas que nadie contestó; el caso de uso rechaza cualquier otro tipo en vez de inventarles un significado. Salir borra la fila de participación y **nunca** los mensajes. |
| CH-14 | **`Conversacion` pasó de 7 a 11 métodos públicos** (`crearSoporte`, `claveSoporteDe`, `esAprendizDeSoporte` y, desde CH-16, `seGanaPorRolDeStaff`), por encima del techo de 7 de `.claude/rules/01`. Es el costo de una raíz de agregado con una fábrica por tipo: la alternativa —un `crear(tipo, ...)` genérico con parámetros que sobran en tres de cada cuatro llamadas— es peor. Se deja anotado en vez de disimulado. |
| CH-15 | **`deSoporte()` no pagina.** Hay una por aprendiz del padrón (25 al 2026-09-16) y quien llama necesita el conjunto entero para compararlo contra el padrón entero. Si el padrón creciera a miles, **este es el método que hay que paginar**. |
| CH-16 | **La baja de rol SÍ revoca** (auditoría de seguridad). Única excepción a CH-11, y acotada a eso: cuando alguien deja de ser `ADMIN`/`ALCHEMIST`, `RetirarDelSoporteUseCase` le borra la fila de toda conversación de soporte donde no sea el aprendiz dueño. **No** repone a quien se fue solo, **no** recompone conversaciones a las que les falte staff y **no** mueve a nadie más — que es lo que CH-11 prohíbe; solo revoca a quien dejó de cumplir la **regla 1**, que estaba sin cumplir en el camino de bajada. Sin esto, un ex administrador conservaba el chat privado de **cada** aprendiz: leyéndolo, escribiendo en él y recibiéndolo en vivo, sin ninguna forma de sacarlo desde el producto. Decide contra el rol **vigente** (no contra el del evento) porque el outbox entrega al-menos-una-vez y sin orden: una reentrega tardía no puede borrarle las filas a un administrador legítimo. Además, las CUATRO copias del guard (`MensajeService`, `ConversacionService`, `PresenciaService`, `AutorizacionDeConversacionService`) exigen ahora rol de staff vigente para un `SOPORTE` ajeno, así la puerta queda cerrada aunque la revocación no haya corrido. |

### 8.5 Anti-N+1 (D-43)

El relleno resuelve todo con **cuatro** consultas en lote, no cuatro por aprendiz: `aprendicesActivos()`,
`participantesInscritosActivos()`, `deSoporte()` y `usuariosActivosConRol({ADMIN, ALCHEMIST})`. Lo
único que se repite por persona son los `INSERT` de quien todavía no tenía su conversación. Lo fija
un test que verifica `times(1)` en cada una y `never()` en `porClaveDirecta`, que es justamente la
consulta por aprendiz.

La intersección con `participantesInscritosActivos()` es el mismo recurso que usó D-135, y por el
mismo motivo: `aprendicesActivos()` devuelve aprendices `ACTIVO` **tengan o no** fila de programa, y
un aprendiz sin fila todavía no entró.

### 8.6 Pruebas

| Clase | Qué fija |
|---|---|
| `ConversacionTest` (+9 casos) | Clave canónica y determinista, que no colisione con la de un DM, `tipo_coherente` para SOPORTE en `rehydrate`, y quién es el aprendiz dueño |
| `ConversacionSoporteServiceTest` (24) | Las cinco reglas, el anti-N+1, el barrido que no se detiene ante un fallo, y las autorizaciones negativas (suspendido, aprendiz que intenta salirse, no-administrador que intenta rellenar) |
| `ConversacionResponseTest` (2) | El contrato del wire: `SUPPORT` |
| `SoporteChatListenersTest` (6) | Los dos avisos llegan al caso de uso, y el de cambio de rol llega al caso de uso **correcto** según la dirección (CH-16) |
| `RevocacionDeSoportePorBajaDeRolTest` (5) | La regresión del hallazgo, con la cadena real: ascender a `ADMIN`, ver el soporte de un aprendiz ajeno, degradar, y ya no verlo — por REST, por STOMP, por presencia y en la bandeja. Cubre las CUATRO copias del guard, que la excepción a CH-11 no mueva a nadie más, y que una reentrega del outbox no le quite las filas a quien volvió al staff |
| `ChatPersistenceAdapterTest` (+2) | Contra Postgres real: `V53` + `V54` + el mapeo de Hibernate funcionando juntos, y el UNIQUE rechazando el segundo soporte del mismo aprendiz |
| `UserAccountServiceTest` (+2) | `users` avisa el cambio de rol, y **no** avisa si el rol no cambió |

Todas se verificaron **revirtiendo la regla y viéndolas en rojo**. Ese ejercicio encontró un test
decorativo: `elAprendizNoPuedeSalirseDeSuPropioSoporte` pasaba con y sin la regla, porque el rechazo
le llegaba del guard de participación. Se corrigió declarando al aprendiz participante, y ahí sí
distingue.

Las de CH-16 se verificaron igual: revirtiendo el listener y **cada uno de los cuatro guards por
separado**, y viendo que `RevocacionDeSoportePorBajaDeRolTest` se pone en rojo con cada reversión.
Un guard que se pudiera revertir sin que ningún test lo notara sería justo la superficie que el
hallazgo dejó abierta.

**Sin caso de reloj en el rango 00:00–05:00 UTC** (`.claude/rules/02`): esta función no deriva
ninguna fecha local. El reloj solo sella `creado_en`/`ultimo_leido_en`, que son instantes.

### 8.7 Lo que queda pendiente

- **El aprendiz suspendido y después reactivado** no dispara ningún evento hoy, así que si su soporte
  no existía sigue sin existir hasta el próximo relleno. Se menciona porque es el hueco real que deja
  el diseño por eventos, no porque se haya decidido dejarlo así.
- **Cuándo se archiva el soporte de alguien que termina el programa** — nadie lo definió. Hoy la
  conversación queda viva.

---

## 9. Presencia real: quién está conectado (2026-09-17, D-140)

### 9.1 Qué había antes

La app mostraba **`● En línea` escrito a mano** debajo del nombre de cualquier persona, en verde,
en toda conversación directa. No era un dato incompleto ni desactualizado: era un literal en el
JSX. El sistema **no tenía ninguna noción de presencia** — el campo `isOnline` existía en el tipo
del cliente y no lo escribía ningún mapeador.

Lo reportó el dueño el 2026-09-17, mirando el APK: *"el tema de en línea de los chat no funciona,
también son datos escritos"*.

### 9.2 Por qué la presencia NO vive en Postgres

Es estado **efímero y compartido entre instancias**. Guardarlo en una tabla tiene un modo de fallo
feo: si el backend se cae, la tabla queda afirmando que todos siguen conectados, y no hay quién la
corrija. En Redis, con vencimiento, el mismo accidente se resuelve solo — la instancia muerta deja
de refrescar y sus llaves vencen.

- Una llave **por usuario** (`chat:presencia:{userId}`), no un conjunto: un `SET` no vence por
  elemento, así que una instancia que muere sin limpiar dejaría a su gente dentro para siempre.
- Vigencia **3 minutos**, refresco cada **45 s** desde cada instancia. Hay que perder tres refrescos
  seguidos para que alguien conectado parpadee a "ausente", y una instancia caída se apaga sola en
  ese plazo.
- La lectura es un solo `MGET` para todo el roster (D-43, anti-N+1).

### 9.3 Por qué el aviso viaja por el canal de la conversación

Se reutiliza `chat:conversacion:{id}` → `/topic/conversaciones/{id}`, el mismo camino que los
mensajes, en vez de abrir `/topic/presencia/{userId}`.

**El motivo es de seguridad, no de comodidad.** `SubscripcionAutorizadaInterceptor` ya autoriza ese
destino —participante, activo, y para un grupo revalidando contra la pertenencia vigente (§ E-37)—
y esa guarda está auditada. Un canal nuevo habría exigido escribir una regla de autorización nueva
("¿puedo ver la presencia de este usuario?") y ponerla a la par de la que ya existe. Reusar el canal
deja el problema resuelto por construcción: a la presencia de alguien llega exactamente quien ya
podía leer lo que esa persona escribe.

Los dos eventos se distinguen por el campo **`event`** del payload (`MESSAGE` / `PRESENCE`), que se
agregó a `MensajeFanoutPayload` en este mismo cambio.

### 9.4 Se cuentan sockets, no personas

`PresenciaDeSockets` lleva `sesión STOMP → usuario` y `usuario → cuántos sockets`. Solo el **primero**
enciende y el **último** apaga. Sin esa cuenta, alguien con el teléfono y la web abiertos quedaría
"ausente" al cerrar una pestaña, y una reconexión normal produciría un parpadeo apagado/encendido en
la pantalla del otro.

El mapa es de *sesión* a usuario porque `SessionDisconnectEvent` trae el id de sesión, no los
atributos del handshake: sin haber guardado quién era esa sesión al conectarse, al cerrarse no habría
a quién apagar.

### 9.5 El refresco NO lleva ShedLock

Es la excepción al patrón del proyecto (D-P4). Los demás schedulers se bloquean para correr en **una**
instancia; este tiene que correr en **todas**, porque cada una refresca los sockets que solo ella
tiene. Con lock, las instancias que lo perdieran dejarían caer la presencia de su propia gente.

### 9.6 Qué se construyó

| Pieza | Archivo |
|---|---|
| Puerto de presencia (Redis) | `application/ports/out/presencia/PresenciaPort` |
| Puerto de fanout | `application/ports/out/presencia/PublicarPresenciaFanoutPort` |
| Conversaciones de un usuario | `application/ports/out/participante/ConversacionesDeUsuarioPort` |
| Casos de uso | `application/ports/in/presencia/{Consultar,Registrar}PresenciaUseCase` |
| Servicio | `application/services/PresenciaService` |
| Adaptador Redis | `infrastructure/adapter/out/redis/RedisPresenciaAdapter` |
| Payload del fanout | `infrastructure/adapter/out/redis/PresenciaFanoutPayload` |
| Eventos de socket | `infrastructure/adapter/in/websocket/PresenciaDeSockets` |
| REST | `GET /api/v1/chat/conversations/{id}/presence` |

`ConversacionesDeUsuarioPort` se apoya en `conversacionIdsDeUsuario`, que **ya existía** en
`SpringDataParticipanteConversacionRepository`: la consulta estaba escrita y sin puerto que la
expusiera.

### 9.7 Por qué hace falta el endpoint REST además del socket

Una suscripción entrega **cambios**, no estado. Sin la consulta inicial, quien abre el chat con la
otra persona ya conectada no recibiría nada y la vería apagada hasta que el otro se fuera.

El endpoint devuelve **solo los ids conectados**, nunca un "última vez". La columna
`usuarios.ultima_actividad_en` existe desde `V1__baseline_renaser.sql:154` y **no la escribe nadie**;
inventar ahí un "última vez" sería repetir exactamente el error que este trabajo vino a corregir.

### 9.8 Autorización

`PresenciaService.enLineaEn` repite la guarda de `MensajeService.requireParticipante`, incluida la
revalidación de grupo contra `PertenenciaVigentePort`. Saber quién está conectado es menos que leer
lo que escriben, pero es información del mismo grupo: un exmentor con la proyección vieja tampoco la
ve. Hay prueba dedicada.

### 9.9 Nunca falla hacia arriba

Si Redis no responde, se loguea y se sigue: al consultar se contesta **"nadie en línea"** —la lectura
prudente, que no afirma lo que no se pudo comprobar— y al conectarse el chat funciona igual. Lo peor
que pasa es que el indicador no se encienda, o sea el comportamiento anterior a este cambio.

### 9.10 Pruebas

`PresenciaServiceTest`, 10 casos. Los que importan no son los del camino feliz sino estos tres:

- sin nadie conectado **no devuelve a nadie** (lo contrario del literal que reemplaza);
- un **exmentor** con la proyección vieja recibe `NotAuthorizedException`, no la lista;
- con **Redis caído** se contesta "nadie", nunca una presencia inventada.

### 9.11 Compatibilidad con la app publicada

`MensajeFanoutPayload` ganó el campo `event`. La app **anterior a este cambio nunca abrió el socket**
(conversaba solo por REST), así que no hay cliente viejo que se pueda romper. Y la app nueva tolera
el payload **sin** `event` a propósito, para poder hablar con un backend todavía no desplegado: el
teléfono se actualiza cuando la tienda quiere, no cuando uno despliega.
