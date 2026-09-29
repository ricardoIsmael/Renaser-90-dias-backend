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
- `participantes_conversacion`: PK compuesta `(conversacion_id, usuario_id)`, con `ultimo_leido_en` nullable — es la base del conteo de no-leídos y, desde el 2026-09-27, de la doble marca de leído (✓✓, §14, D-208).
- `mensajes`: `tipo` (`TEXTO`/`IMAGEN`/`AUDIO`/`VIDEO`/`SISTEMA`), dos CHECK (`mensaje_con_contenido`, `media_completa`), `respuesta_a_id` auto-referencial (hilos), `oculto`/`eliminado_en` para moderación (sin caso de uso que los mute en esta pasada — ver §6).
- `mensajes_bienvenida`: **no se tocó** — no hay caso de uso que la use (ver §6). *(Actualizado 2026-09-26: desde G-2 la usa la bienvenida automática como marca de idempotencia, §10.)*
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

> **Actualizado 2026-09-27 (D-208, §14).** `POST .../{id}/read` además avisa en vivo hasta dónde leyeron todos (evento `READ`,
> nunca en la comunidad) y la marca solo avanza; `GET .../messages` trae en cada mensaje propio `status` (`SENT` / `READ`).

Todos reciben el actor por `X-Actor-Id` (mismo patrón temporal que el resto de los módulos ya construidos, sin JWT — bloqueante del usuario documentado en `docs/MODULOS_A_AVANZAR.md`).

**D-36 aplicado:** `TipoConversacion`/`TipoMensaje` viven en español en dominio y base; el wire habla inglés (`CELL`/`DIRECT`/`GLOBAL`/`SUPPORT`, `TEXT`/`IMAGE`/`AUDIO`/`VIDEO`/`SYSTEM`) — la traducción vive solo en `ConversacionResponse.toWireTipo`/`MensajeResponse.toWireTipo` (salida) y `MensajeController.parseTipoMensaje` (entrada), nunca en dominio ni persistencia.
> **Corregido 2026-09-27 (E-332).** La entrada sigue traduciendo `SYSTEM`, pero `Mensaje.escribir` lo rechaza con 400: un mensaje de
> sistema lo escribe el programa, no una persona (D-199). Antes cualquier participante podía mandar un `SYSTEM`, incluso vacío.
> **Corregido 2026-09-16 (D-136).** Esta línea listaba solo `CELL`/`DIRECT`/`GLOBAL`. `SOPORTE` -> `SUPPORT` se suma en §8, y la lista de endpoints de arriba no incluye los dos de soporte (`POST .../{id}/leave` y `POST /api/v1/admin/chat/support-conversations/backfill`): están en §8.3.

### 3.4 WebSocket + Redis Pub/Sub

- `infrastructure/adapter/in/websocket/WebSocketConfig`: endpoint STOMP `/ws`, broker simple `/topic`, prefijo de aplicación `/app`. El cliente se suscribe a `/topic/conversaciones/{conversacionId}`.
  **Latidos de 10 s en los dos sentidos (D-202, 2026-09-27):** el `CONNECTED` dice `heart-beat:10000,10000` y el broker cierra la
  sesión del cliente que no escribe nada en 30 a 40 s. Ver §11.
- `infrastructure/adapter/out/redis/RedisChatPublisher` (implementa `PublicarMensajeFanoutPort`): publica a Redis (canal `chat:conversacion:{id}`) **después** del commit de la transacción que guardó el mensaje — `MensajeService.publicarDespuesDelCommit` usa `TransactionSynchronizationManager.registerSynchronization(...).afterCommit(...)`, el mismo mecanismo que ya usa `AccountRequestService` de `users` para su compensación de Supabase. Fire-and-forget: si Redis falla, se loguea y se sigue (el mensaje ya está durable en Postgres).
- `infrastructure/adapter/out/redis/RedisChatSubscriberConfig`: cada instancia se suscribe al patrón `chat:conversacion:*` y reenvía el payload (JSON crudo, sin re-serializar) a `/topic/conversaciones/{id}` vía `SimpMessagingTemplate` — así una instancia distinta a la que recibió el POST también entrega el mensaje en vivo (CLAUDE.MD §5.2.1).
- **Honestidad sobre lo que esto prueba:** la arquitectura compila y el mecanismo (persistir → publicar tras commit → re-suscribir → STOMP) sigue el patrón documentado en CLAUDE.MD §5.2.1 al pie de la letra, pero **no hay verificación E2E con un cliente STOMP/WebSocket real** en este encargo (no hay herramienta de este agente para abrir un socket real contra la app corriendo) — queda pendiente para una fase de pruebas manuales o un test de integración con un cliente STOMP de prueba (`spring-websocket` trae uno).

---

## 4. Reglas de dominio implementadas

- **`Conversacion`**: invariante `tipo_coherente` replicada en dominio (`requireTipoCoherente`) — falla con `IllegalArgumentException` (400) antes de llegar al CHECK de Postgres (500), tanto en las fábricas (`crearCelula`/`crearDirecta`/`crearGlobal`) como en `rehydrate` (defensivo contra datos corruptos).
- **GLOBAL única e idempotente**: `ConversacionService.unirse()` hace busca-o-crea (`loadConversacionPort.global().orElseGet(...)`), protegido además por el índice único parcial de la base. Ventana de carrera teórica entre dos altas simultáneas (dos instancias sin GLOBAL creando cada una la suya) documentada como aceptada — el mismo criterio que ya explica `GlobalExceptionHandler.handleIntegridad` para el "doble tap" del cliente: la segunda pierde la carrera y su `INSERT` viola el índice único, se traduce a 409. Como esto corre en un listener de evento (no en un request HTTP), el 409 no llega a ningún cliente — Modulith reintenta el evento según su política de outbox.
- **`claveDirectaDe`**: orden lexicográfico `menor_mayor` de los dos UUID como string — determinístico sin importar quién inicia la conversación.
- **`Mensaje`**: invariantes `mensaje_con_contenido` (todo mensaje que escribe una persona lleva texto o media) y `media_completa` (`mediaBucket`/`mediaRuta` viajan juntos o ninguno) replicadas en `Mensaje.escribir`, que además rechaza `SISTEMA`: es la voz del programa, no de una persona (E-332).
  > **Corregido 2026-09-27 (E-332).** Decía «SISTEMA no necesita texto/media, cualquier otro tipo sí»: `escribir` aceptaba un `SISTEMA`
  > de cualquier emisor, incluso vacío. La base sigue eximiendo a `SISTEMA` del CHECK; `rehydrate` lee los que ya estén guardados.
- **`EnviarMensaje`**: el emisor debe ser participante (`NotAuthorizedException` si no) — chequeado ANTES de escribir el mensaje. Si `respuestaAId` viene, se verifica que el mensaje original pertenezca a la MISMA conversación (`requireRespuestaEnMismaConversacion`) — evita citar un mensaje de otra conversación por error de cliente o ataque de enumeración de IDs. Al enviar, se actualiza `ultimo_leido_en` del EMISOR (ya "leyó" lo que acaba de escribir).
- **`ListarConversaciones` sin N+1**: `ultimosPorConversacion`/`contarNoLeidos` reciben la lista completa de `ConversacionId` y devuelven un `Map` en una sola consulta cada uno — nunca una consulta por conversación. Verificado con `verify(..., times(1))` en `ConversacionServiceTest` y contra Postgres real en `ChatPersistenceAdapterTest`.
- **`MarcarLeido`**: exige participante (`NotAuthorizedException` si no).
  > **Actualizado 2026-09-27 (D-208).** La marca solo avanza (un UPDATE condicionado: antes leer, pisar y guardar podía hacerla
  > retroceder con dos lecturas a la vez), y después de guardarla se avisa en vivo `READ` si corresponde (§14). Por eso
  > `ConversacionService.marcarLeido` dejó de ser `@Transactional`: el aviso tiene que salir con la lectura ya guardada y
  > calcularse con las de los demás ya guardadas.
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
| CH-6 | **`mensajes_bienvenida` NO se tocó** — ningún caso de uso de este encargo escribe ni lee esa tabla; no hay evidencia de una regla de negocio confirmada sobre cuándo/cómo se genera un mensaje de bienvenida (¿automático al unirse a GLOBAL? ¿manual de un mentor?). Se documenta en vez de inventarla. **Actualizado 2026-09-26:** desde G-2 es la marca de idempotencia de la bienvenida automática (§10, D-174). |
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
| CH-12 | **El `nombre` es una foto del momento de creación** (`"<primer nombre> – Formación Renaser"` desde D-173; ver la nota al pie de la tabla). Lleva el nombre porque quien más ve estas conversaciones es el staff, y sin nombre tendría 25 filas idénticas — el mismo problema que ya arregló el listado de mensajes directos. **Limitación conocida:** si la persona se cambia el nombre después, el título no se entera. Derivarlo en cada lectura obligaría a resolver el aprendiz de cada soporte al listar; no se hizo porque nadie lo pidió, y queda escrito acá en vez de quedar como olvido. |
| CH-13 | **Salir es solo de un SOPORTE.** Irse de una CÉLULA, de un DM o de la GLOBAL son tres preguntas distintas que nadie contestó; el caso de uso rechaza cualquier otro tipo en vez de inventarles un significado. Salir borra la fila de participación y **nunca** los mensajes. |
| CH-14 | **`Conversacion` pasó de 7 a 11 métodos públicos** (`crearSoporte`, `claveSoporteDe`, `esAprendizDeSoporte` y, desde CH-16, `seGanaPorRolDeStaff`), por encima del techo de 7 de `.claude/rules/01`. Es el costo de una raíz de agregado con una fábrica por tipo: la alternativa —un `crear(tipo, ...)` genérico con parámetros que sobran en tres de cada cuatro llamadas— es peor. Se deja anotado en vez de disimulado. |
| CH-15 | **`deSoporte()` no pagina.** Hay una por aprendiz del padrón (25 al 2026-09-16) y quien llama necesita el conjunto entero para compararlo contra el padrón entero. Si el padrón creciera a miles, **este es el método que hay que paginar**. |
| CH-16 | **La baja de rol SÍ revoca** (auditoría de seguridad). Única excepción a CH-11, y acotada a eso: cuando alguien deja de ser `ADMIN`/`ALCHEMIST`, `RetirarDelSoporteUseCase` le borra la fila de toda conversación de soporte donde no sea el aprendiz dueño. **No** repone a quien se fue solo, **no** recompone conversaciones a las que les falte staff y **no** mueve a nadie más — que es lo que CH-11 prohíbe; solo revoca a quien dejó de cumplir la **regla 1**, que estaba sin cumplir en el camino de bajada. Sin esto, un ex administrador conservaba el chat privado de **cada** aprendiz: leyéndolo, escribiendo en él y recibiéndolo en vivo, sin ninguna forma de sacarlo desde el producto. Decide contra el rol **vigente** (no contra el del evento) porque el outbox entrega al-menos-una-vez y sin orden: una reentrega tardía no puede borrarle las filas a un administrador legítimo. Además, las CUATRO copias del guard (`MensajeService`, `ConversacionService`, `PresenciaService`, `AutorizacionDeConversacionService`) exigen ahora rol de staff vigente para un `SOPORTE` ajeno, así la puerta queda cerrada aunque la revocación no haya corrido. |

> **Corregido 2026-09-26 (D-173).** CH-12 decía que el nombre era `"Soporte - <nombre del aprendiz>"`.
> Operaciones nombra el grupo personal `"NOMBRE – FORMACIÓN RENASER"` (procedimiento OPE-01-01), y el
> dueño pidió ese formato con el **primer nombre** solo, para no romper con nombres compuestos. Aplica a
> los soportes **nuevos**: los que ya existían conservan `"Soporte - Nombre Completo"` a pedido del dueño
> (no hay migración que los renombre). Sin nombre legible, el título es `"Formación Renaser"` (antes `"Soporte"`).
>
> **Corregido 2026-09-29 (D-221).** CH-12 dice que el nombre es una foto del momento de creación y que los
> soportes viejos conservan `"Soporte - Nombre Completo"`. El dueño definió el esquema de nombres para todos
> los chats: el nombre que se MUESTRA ahora se deriva al leer (`NombresDeLosChatsService`) del nombre actual
> del aprendiz, para todos los soportes. La columna se sigue llenando y queda de respaldo. Ver §16.

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
> **Corregido 2026-09-27 (D-208).** Decía «Los dos eventos»: desde la doble marca de leído hay un tercero, `READ`
> (`{"event":"READ","readUpTo":"…"}`, §14), por el mismo canal y con la misma autorización. La app publicada lo descarta
> por desconocido sin romperse.

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

## 9. El chat de dos con quien acompaña (2026-09-26, D-173)

**Qué pidió el dueño.** Al aprobar la cuenta, el aprendiz entra solo a dos chats: el general y su
soporte (§8). Cuando entra a un grupo con alguien que lo acompaña, se le abre **un chat de dos** con
esa persona, sin staff adentro:

- en la recepción (los primeros 7 días), uno con **cada guía** de la cohorte;
- en su grupo estable, uno con **su mentor**, en cuanto el admin se lo asigna.

**Los chats no se cierran.** Cuando el mentor rota (a mano por ahora: el barrido automático de
rotación sigue apagado, V48), se abre el chat con el nuevo y el del anterior queda como historial,
igual que cualquier DM. El dueño eligió eso sobre "cerrarlo para el exmentor".

**Cómo.** `ComposicionCelulaAcompananteListener` escucha el mismo `ComposicionDeCelulaCambiadaEvent`
que reconcilia el chat del grupo, pero en su propio listener: si uno falla, el otro igual corre.
`ChatsConAcompananteService` arma las parejas aprendiz × acompañante con
`AcompanamientoDelGrupoPort` (→ `community.api.AcompanamientoFinder.acompanantesVigentes`, que
devuelve `MENTOR` y `GUIA` y deja afuera `SOPORTE`), pregunta en **una** consulta cuáles ya existen
(`LoadConversacionPort.clavesDirectasExistentes`, anti-N+1: la recepción no tiene tope y cada ingreso
mira el grupo entero) y crea las que faltan como `DIRECTA` normales, cada una en su transacción
(C-10). El UNIQUE de `clave_directa` decide si dos caminos la abren a la vez.

**Solo cuentas activas (G-4, 2026-09-26).** Antes de armar las parejas se leen las cuentas de todo el
grupo en una consulta (`UserSummaryFinder.findByIds`) y se deja afuera a quien no está ACTIVE: no se abre
un chat de dos con un aprendiz suspendido ni con un mentor o guía suspendido.

**Un fallo se reintenta (G-3, 2026-09-26).** Se intentan todas las parejas; la carrera con el UNIQUE
de `clave_directa` es lo único que se traga. Si alguna falló por otra cosa, el servicio lanza al
final, la publicación del outbox queda incompleta y Modulith la reentrega (al reiniciar o a los 5
minutos, `EventPublicationMaintenanceScheduler`): el reintento solo abre las que faltan.
> **Corregido 2026-09-26.** Antes cualquier excepción se registraba con `log.warn` y la publicación
> quedaba completa: el chat que faltaba no se volvía a intentar nunca (E-300).

**Límites conocidos.**
- Solo corre cuando un grupo cambia. Los aprendices que **ya** tenían mentor antes de este cambio no
  reciben su chat hasta el próximo cambio de su grupo. No hay relleno; si se necesita, es un endpoint
  aparte como el de §8.
- Lo mismo para una cuenta que se reactiva: su chat de dos se abre con el próximo cambio de su grupo,
  no al reactivarla.
- Un chat de dos vacío aparece en la lista de ambos apenas se crea.

| Clase | Qué fija |
|---|---|
| `ChatsConAcompananteServiceTest` (8) | Un chat por pareja con solo esos dos; uno por guía en recepción; no repite los existentes y consulta una sola vez; sin acompañante no hace nada; nadie habla solo; la carrera no es error y no frena a las demás; un fallo de verdad se lanza después de intentar las demás (G-3); sin chat con suspendidos (G-4) |
| `AcompanamientoServiceTest` (+2) | `acompanantesVigentes`: mentor y guías sí; soporte, aprendices y exmentor no; grupo cerrado, vacío |
| `ChatPersistenceAdapterTest` (+1) | `clavesDirectasExistentes` contra Postgres real |

## 10. La bienvenida automática en el chat de soporte y en el del grupo (2026-09-26, D-174, D-191; 2026-09-27, D-199, D-204)

**Qué hace.** Cuando nace el soporte de un aprendiz **nuevo** (no en el relleno de §8), **el programa**
manda tres mensajes, en el orden de OPE-01-01: la tarjeta de bienvenida del Canva de Operaciones con su
primer nombre, el mensaje que acompaña la tarjeta («valor agregado, experiencia premium») y el mensaje
formal de bienvenida (confirma el ingreso, dice que el equipo confirmará el horario de la sesión técnica
y que antes le mandará la información para esa sesión). Es el paso 2 de OPE-01-01 que hoy se hace a mano
por WhatsApp.
> **Corregido 2026-09-27 (D-199).** Decía «se mandan tres mensajes desde la cuenta de staff configurada
> (hoy la de Kelin)». El dueño decidió que salgan del programa: «mejor que salga mensaje automático sin
> una persona, ¿no sería lo correcto?». Lo mismo para la del grupo (D-204, más abajo).

**Dónde están los textos (D-190, D-210).** Los ORIGINALES, en `src/main/resources/bienvenida/mensajes.yaml`,
versionado con el código: claves `soporte.con-la-tarjeta` y `soporte.formal` (`{nombre}` = primer nombre).
Los lee `TextosDeBienvenidaYamlAdapter` (puerto `TextosOriginalesDeBienvenidaPort`) una vez al arrancar; si
el archivo falta, el arranque falla. **ADMIN y ALCHEMIST los cambian desde la app** (§13): lo que guardan
reemplaza al original desde la próxima bienvenida, hasta que alguien vuelva al original. Los que salen los
arma `TextosDeBienvenidaVigentes` (puerto `TextosDeBienvenidaPort`), que lee la bitácora en cada bienvenida.
Nada va por entorno. Un texto vacío en el archivo apaga ese mensaje (desde la app no se guarda vacío). Hoy
el archivo todavía los marca como **borradores** del equipo técnico.
> **Corregido 2026-09-27 (D-210).** Decía «**Se cambian editando ese archivo y redesplegando**, no por
> entorno», y que los leía `TextosDeBienvenidaYamlAdapter` detrás de `TextosDeBienvenidaPort`.

**El interruptor (D-199/D-204, 2026-09-27).** `BIENVENIDA_ACTIVA` (`renaser.chat.bienvenida.activa`),
**apagado por defecto**, prende o apaga las dos bienvenidas, la del soporte y la del grupo. Los textos
son borradores y no pueden llegar a aprendices reales hasta que el dueño los apruebe. Apagada, ninguna
de las dos mira nada: no dibuja, no sube, no manda y **no deja marca** (ni `mensajes_bienvenida` ni
`asignaciones_celula.bienvenida_enviada_en`). Al arrancar apagada queda un `INFO`
(`bienvenidas automáticas apagadas (BIENVENIDA_ACTIVA=false)`). En producción se prende con
`/renaser/prod/BIENVENIDA_ACTIVA=true` en Parameter Store y reiniciando el contenedor.

**Al prenderla no salen bienvenidas atrasadas.** La del soporte se dispara una sola vez por aprendiz
(`SoporteDeAprendizNacioEvent`): con el interruptor apagado ese evento se da por completado y no vuelve.
La del grupo se dispara con cada cambio del grupo y apagada no marca a nadie, así que sin un corte el
primer cambio después de prenderla le daría la bienvenida a quien lleva días ahí: por eso solo la
recibe quien entró al grupo hace menos de 48 h (ver «La bienvenida en el grupo estable»).

**Cómo se prende** (sin cambiar código): `BIENVENIDA_ACTIVA=true`. Nada más: ya no hay remitente.
> **Corregido 2026-09-27 (D-199).** Decía «`BIENVENIDA_REMITENTE_EMAIL` con el correo de la cuenta que
> firma. Sin remitente está apagada. Si la cuenta no existe o está suspendida, no sale nada y queda un
> aviso en el log». `BIENVENIDA_REMITENTE_EMAIL` ya no se lee.

**Qué cuenta podía ser remitente (E-330, 2026-09-26): ya no aplica.**
> **Corregido 2026-09-27 (D-199).** Este apartado explicaba que solo una cuenta ACTIVA con rol `ADMIN` o
> `ALCHEMIST` que participara del soporte podía firmar; que con otra la bienvenida de ese evento quedaba
> apagada, sin lanzar ni dejar marca, con un `WARN` por evento; y que `RemitenteDeBienvenidaAlArrancarListener`
> dejaba el mismo aviso al arrancar. Con la firma del programa no hay remitente: esa validación, el aviso
> y el listener se quitaron. La lección general de E-330 sigue en la bitácora.
> **Corregido 2026-09-26 (D-190).** Decía «y `BIENVENIDA_TEXTO` con el texto (`{nombre}` se reemplaza
> por el primer nombre). […] sin texto, sale solo la tarjeta». El dueño pidió que el texto no vaya en
> una variable de entorno y que cada parte del ingreso tenga su mensaje. `BIENVENIDA_TEXTO` ya no se lee.

**La firma del programa (D-199/D-204, 2026-09-27).** Las dos bienvenidas salen como mensajes de
**`SISTEMA`** por `EnviarMensajeDelProgramaUseCase` (`MensajeDelProgramaService`): sin actor (nadie tiene
que estar activo ni ser participante), sin marcar leído a nadie (es nuevo para todos los del chat) y
empujadas en vivo después del commit. Una persona no puede escribir `SISTEMA` (E-332): es la voz del
programa.

- **Sin migración, sin tablas y sin cuenta «sistema».** `mensajes.emisor_id` es `NOT NULL REFERENCES
  usuarios (id) ON DELETE CASCADE` (V1). En un mensaje del programa guarda **a quién se refiere**: el
  aprendiz que recibe la bienvenida, en el soporte y en el grupo. Consecuencias: la cascada lo borra con
  la cuenta de esa persona (y, por `mensajes_bienvenida.mensaje_id`, su marca); la media `chat/...` sigue
  sin purgarse con ninguna cuenta (`ClavesDeCuenta` las deja afuera a propósito), igual que antes; y quien
  cuente «lo que escribió» alguien leyendo `emisor_id` directo tiene que excluir `tipo = 'SISTEMA'` (hoy
  nadie lo hace; `users` lee `emisor_id` solo para la purga, y ahí el resultado es el correcto).
- **Hacia afuera nunca es de esa persona:** `Mensaje.remitentePublico()` es el UUID nulo, y el nombre,
  «Formación Renaser» (el mismo sufijo de cada chat de soporte).
- **Alternativas descartadas.** (a) Una migración (V72: `emisor_id` nullable con `CHECK (emisor_id IS NOT
  NULL OR tipo = 'SISTEMA')`): más fiel sobre la autoría, pero el pedido era detenerse si hacía falta; queda
  como camino si el dueño prefiere que la base no guarde a nadie. (b) Una cuenta de staff (la de Kelin o
  cualquier staff del soporte) como emisor técnico: el mensaje quedaba a nombre de una persona y la cascada
  lo borraba de todos los soportes al dar de baja esa cuenta.
- **Mensajes `SISTEMA` viejos:** si en producción hubiera alguno escrito por un cliente antes de E-332
  (en la base local no hay; no se verificó en producción:
  `SELECT count(*) FROM renaser.mensajes WHERE tipo = 'SISTEMA'`), desde este cambio se vería como del
  programa.

**El contrato**, lo que la app nueva pinta como «Formación Renaser» con el fénix (`MensajeResponseTest`):

| Campo de `MensajeResponse` | Valor en un mensaje del programa |
|---|---|
| `type` | `"SYSTEM"` (los tres del soporte y el del grupo) |
| `senderId` | `"00000000-0000-0000-0000-000000000000"`, **nunca `null`** (ni la persona guardada) |
| `senderName` | `"Formación Renaser"` en `GET .../messages` (en `lastMessage` de `GET /conversations` viaja `null`, como para todos) |
| `senderAvatarUrl` | `null` (el fénix lo pone la app) |
| `text` | el texto; `null` en la tarjeta |
| `mediaBucket`/`mediaPath`/`mediaMime`/`mediaBytes`/`mediaUrl` | solo en la tarjeta: `chat`, `chat/<soporte>/fotos/<uuid>`, `image/jpeg`, el tamaño y la URL firmada |
| `replyTo.senderName` | `"Formación Renaser"` si alguien responde a un mensaje del programa |
| Evento en vivo (`MensajeFanoutPayload`) | `senderId` = el mismo UUID nulo (si fuera el aprendiz, su app lo descartaría como eco propio); `type` = `SYSTEM`, como en el REST (E-333, resuelto el 2026-09-27; antes viajaba `SISTEMA`) |

**Riesgo con el APK publicado (sin actualización por aire):** todas sus versiones validan `senderId:
z.string()` dentro del arreglo de mensajes y del `lastMessage` de cada conversación: un `null` haría fallar
la validación entera y dejaría **sin bandeja y sin historial** al aprendiz y a todo el staff (que está en
cada soporte). Por eso el UUID nulo. `type: "SYSTEM"` lo aceptan todas (antes como `z.enum` con `SYSTEM`,
hoy `z.string()`) y lo pintan como una burbuja de texto de «Formación Renaser» a la izquierda (`isMe` es
falso: nadie tiene ese id). Lo que **no** hacen es mostrar la imagen de un `SYSTEM`: en el APK publicado
la tarjeta se ve como «Mensaje del sistema» (y en la bandeja, si fuera el último). No rompe nada, pero
conviene **publicar el APK nuevo antes de prender `BIENVENIDA_ACTIVA`**: la bienvenida del soporte va a
aprendices recién aprobados, que instalan la app de la tienda.

**La bienvenida en el grupo estable (D-191, V71; del programa desde D-204).** OPE-01-01 pide también un
mensaje en el grupo, reforzando pertenencia y compromiso, cuando el aprendiz se integra a su grupo.
`ComposicionCelulaBienvenidaListener` (`@ApplicationModuleListener`: después del commit, en otro hilo,
separado de los otros dos listeners del mismo evento) llama a `BienvenidaEnGrupoService`, que:

0. Con `BIENVENIDA_ACTIVA` apagado (el default, D-204) no hace nada: ni consulta ni marca.
1. Pide a `community.api.BienvenidaDeGrupo` (vía `BienvenidaEnGrupoPort`) las pertenencias de aprendiz
   **vigentes y sin marca** del grupo y su **mentor vigente**. Vacío si el grupo es la **recepción**, está
   fuera de su periodo o **no tiene mentor**. De esas deja solo las que **empezaron hace menos de 48 h**
   (`asignaciones_celula.inicio`, expuesto como `Pendiente.desde`): sin bienvenidas atrasadas (D-204).
2. Lee las cuentas del grupo en una consulta; mentor o aprendiz no activos quedan pendientes.
3. Por cada aprendiz, en **su** transacción: primero `marcarDada` (`UPDATE asignaciones_celula SET
   bienvenida_enviada_en = … WHERE … IS NULL`; sigue solo si afectó 1 fila) y después el mensaje **del
   programa** (`SISTEMA`, guardado a nombre del aprendiz) en el chat del grupo, con el texto `grupo` de
   `mensajes.yaml` (`{nombre}` y `{mentor}` = primeros nombres). Sin tarjeta. O quedan la marca y el
   mensaje, o ninguno. El mentor sigue haciendo falta porque el texto lo nombra.
   > **Corregido 2026-09-27 (D-204).** Decía «el mensaje `TEXTO` en el chat del grupo, firmado por el
   > mentor».
4. Un fallo que no es «ya estaba marcada» se lanza al final, después de intentar a todos (G-3).

La marca es por pertenencia (`asignaciones_celula`, una fila por aprendiz y grupo), así que un traslado
recibe la bienvenida del grupo nuevo. V71 marcó a todos los que ya estaban en un grupo al desplegar. Un
grupo sin mentor deja las bienvenidas pendientes: salen con el cambio de composición que le pone mentor,
si la pertenencia sigue abierta **y empezó hace menos de 48 h**. Pasada esa ventana la pertenencia queda
sin marca y sin mensaje, a propósito: «Qué alegría que te sumes a este grupo» no se le dice a quien lleva
días ahí. La ventana es una decisión técnica (D-204) a confirmar con el dueño.
> **Corregido 2026-09-27 (D-204).** Decía que las pendientes salían con el cambio que le pone mentor, sin
> límite de tiempo. Con el interruptor apagado por defecto (que no marca), eso habría mandado bienvenidas
> atrasadas a todos los que entraron mientras estuvo apagado.

**El texto del grupo cambió (D-204, 2026-09-27).** Lo dice el programa y es más amigable, sin género para
la persona ni para el mentor, sin horarios ni links (sigue siendo borrador): «¡Hola, {nombre}! 🌿 Qué
alegría que te sumes a este grupo. Aquí vas a compartir el camino con cada integrante y con {mentor}, que
te va a acompañar en estos días. Este es tu espacio para contar tus avances, pedir apoyo y celebrar cada
paso. ¡Te damos la bienvenida!». Nombra al mentor en tercera persona: lo dice el programa.
> **Corregido 2026-09-27 (D-204).** Decía «¡{nombre}, te damos la bienvenida a tu grupo! Soy {mentor} y voy
> a acompañarte en estos días. […] Cuento contigo.», en primera persona del mentor.
> **Corregido 2026-09-26 (D-191).** Este apartado decía «**No está implementado:** […] hace falta una
> marca por aprendiz **y grupo**, y `mensajes_bienvenida` no la admite […]. Eso pide una columna o tabla
> nueva, que queda para decisión del dueño». El dueño eligió una columna en `asignaciones_celula`.

**La tarjeta** la dibuja el servidor (`BienvenidaJava2dAdapter`, Java2D, sin servicios externos) sobre la
**portada vigente** —la exportación de Canva sin nombre, `src/main/resources/bienvenida/fondo.png`, o la que
subió Administración desde la app (§13)— con Cinzel
(`bienvenida/Cinzel.ttf`, fuente de Google bajo licencia SIL OFL 1.1, texto en `bienvenida/OFL.txt`).
Las medidas se tomaron comparando el fondo con la exportación "FLOR DE MARÍA": tamaño 133, letras
7,5 px más juntas, centro x = 600, línea base y = 868, color `#153832`. Un nombre que no entra se
achica. Sale en JPEG (~110–125 KB contra 1,2 MB en PNG). Se verificó que dibuja igual dentro de
`eclipse-temurin:25-jre-noble`, la base de la imagen de producción.
> **Corregido 2026-09-27 (D-210).** Decía que se dibujaba «sobre `src/main/resources/bienvenida/fondo.png`»:
> ese es ahora el original, y la portada se puede cambiar.

**Cómo llega al teléfono.** El servidor la sube a S3 bajo `chat/<soporte>/fotos/<uuid>` con el nuevo
`AlmacenamientoPort.subir` (única excepción a "el backend no toca los bytes": los archivos del
teléfono siguen por URL prefirmada), y la manda como un mensaje `IMAGEN` normal. La app instalada la
muestra como cualquier foto del chat: no hace falta APK.

**Con almacenamiento de marcador no hay tarjeta (G-5, 2026-09-26).** Con `STORAGE_PROVEEDOR=noop` (el
default local) `subir` no guarda nada, y antes igual se mandaba el mensaje `IMAGEN`: una foto rota en
el chat. Ahora `AlmacenamientoPort.guardaObjetos()` (false solo en el adaptador de marcador) decide: sin
almacenamiento real se manda solo el mensaje formal (el que acompaña la tarjeta no tiene sentido sin
ella, D-190) y queda un `WARN` en el log. **Ojo:** el default de
`AWS_S3_BUCKET` es el bucket de producción; en local con `STORAGE_PROVEEDOR=s3` hay que poner uno propio
(`docs/DESPLIEGUE_Y_CI.md` §6.4).

**Dos mensajes, no uno:** la app muestra el texto de una foto solo cuando la foto no carga.

**Se reintenta, y es idempotente (G-2, 2026-09-26).** `SoporteNacioBienvenidaListener` es `@Async` +
`@TransactionalEventListener` (después del commit, en otro hilo). Aunque no sea
`@ApplicationModuleListener`, Spring Modulith guarda su publicación en el outbox (`event_publication`)
igual: si el proceso muere a mitad de camino o el caso de uso lanza, se reentrega al reiniciar o a los
5 minutos. Para que eso no duplique, la marca es la tabla **`mensajes_bienvenida`** del baseline (V1,
que existía sin uso: una fila por destinatario apuntando al primer mensaje, CH-6):

1. Si la marca ya existe, no se hace nada (ni dibujar ni subir).
2. Se dibuja y se sube la tarjeta **fuera** de toda transacción.
3. En **una** transacción: los mensajes y la marca. O queda todo o nada. Si dos entregas se cruzan,
   la PK de la marca deshace la que perdió (sin error).
4. Cualquier otro fallo **se lanza**, para que el outbox reintente.

**Riesgo R2, cerrado.** `SoporteDeAprendizNacioEvent` se publicaba en la transacción del listener que
crea el soporte, *después* de que el soporte commiteaba en su transacción propia (REQUIRES_NEW). Si la
del listener se deshacía, el soporte quedaba y el evento se perdía; el reintento encontraba el soporte
y no volvía a publicar: la bienvenida no salía nunca. Ahora se publica **dentro** de la transacción que
crea el soporte, así que los dos commitean juntos (E-299).

> **Corregido 2026-09-26.** Este apartado decía «**Sin reintento a propósito.** […] **no**
> `@ApplicationModuleListener`, que reintenta: un reintento después de la primera foto mandaría la
> bienvenida dos veces. Si falla, queda en el log y Operaciones la manda a mano». Era falso: el outbox
> también guarda y reentrega las publicaciones de un `@TransactionalEventListener`, así que una
> reentrega (reinicio a mitad de camino) sí podía duplicar, y un fallo que se tragaba perdía la
> bienvenida (E-298).

| Clase | Qué fija |
|---|---|
| `BienvenidaJava2dAdapterTest` (4) | Coincide con la exportación de Canva (la referencia está en `src/test/resources/bienvenida/`); el test distingue una tarjeta sin nombre; un nombre largo no se sale; JPEG liviano |
| `BienvenidaEnSoporteServiceTest` (9) | La firma el programa: los tres en orden (tarjeta con el primer nombre, el que la acompaña y el formal, con los textos del puerto) como `SISTEMA` a nombre de la aprendiz (D-199); apagada no mira nada, no dibuja, no manda, no marca; sin ninguna cuenta de staff igual sale (ya no hay remitente); con los dos textos vacíos solo la tarjeta; un fallo de S3 no manda una foto inexistente y lanza (G-2); deja la marca con la tarjeta; una reentrega con marca no hace nada; la carrera con otra entrega no es error; con `noop` solo el formal (G-5). *Corregido 2026-09-27: las pruebas del remitente (suspendido, `MENTOR`, staff no participante, aviso al arrancar, E-330) se quitaron con el remitente.* |
| `BienvenidaEnSoporteIT` (1) | Postgres real, interruptor prendido: la base acepta el `SISTEMA` a nombre de la aprendiz sin tocar el esquema, la marca queda con él, la reentrega no lo repite y el listado lo devuelve como `SYSTEM` del UUID nulo, «Formación Renaser», sin avatar (D-199) |
| `BienvenidaEnGrupoServiceTest` (11) | Marca y después manda el texto del recurso en el chat del grupo, firmado por el programa (a nombre de la aprendiz, nada a nombre del mentor, D-204), con los dos primeros nombres; el texto nuevo del repo con los reemplazos (D-204); apagada no consulta ni marca (D-204); quien entró hace 3 días no la recibe ni se marca y quien entró hace 1 h sí; borde 47 h sí / 49 h no; todos fuera de la ventana: no busca chat ni cuentas; marca ya puesta no manda; sin pendientes (recepción, sin mentor) no manda; sin texto no marca; mentor suspendido no marca; un fallo no frena a los demás y se lanza al final |
| `BienvenidaEnGrupoIT` (6) | Postgres real, con el interruptor prendido: un mensaje y la reentrega no duplica; quien entró hace 3 días no recibe una bienvenida atrasada ni se marca (D-204); dos entregas cruzadas en dos hilos dan un mensaje; recepción no; sin mentor queda pendiente y sale al ponerle mentor; V71 marca las pertenencias de aprendiz existentes y no las de mentor (base aparte migrada a V70, semilla, V71) |
| `TextosDeBienvenidaYamlAdapterTest` (6) | El `mensajes.yaml` del repo trae los dos textos del soporte y el del grupo con sus marcadores; el del grupo lo dice el programa, sin «Soy {mentor}», sin horarios ni links (D-204); `renaser.chat.bienvenida.activa` existe y viene apagado (D-199); una clave vacía apaga ese mensaje; sin archivo falla al arrancar; `application.yaml` ya no tiene `renaser.bienvenida.texto` (D-190) ni `renaser.bienvenida.remitente-email` (D-199; antes exigía que el remitente siguiera por entorno) |
| `MarcaDeBienvenidaJdbcAdapterTest` (1) | La marca contra Postgres real: una por destinatario, la segunda choca con la PK |
| `ConversacionSoporteServiceTest` (+2 y aserciones) | Avisa solo al crear de verdad: no si ya existía, no si perdió la carrera, no en el relleno; el aviso se publica dentro de la transacción que crea el soporte (R2) |
| `PrimerNombreTest` (2) | Primera palabra con inicial en mayúscula; vacío sin nombre |
| `MensajeTest` (+5) | Una persona no escribe `SISTEMA`, ni vacío ni con texto (E-332); un `SISTEMA` ya guardado se sigue leyendo; el programa escribe `SISTEMA` con texto o con imagen, a nombre de la persona a quien se refiere; sin contenido o sin persona es inválido; hacia afuera lo firma el UUID nulo, nunca la persona guardada (D-199) |
| `MensajeDelProgramaServiceTest` (2) | Guarda un `SISTEMA` a nombre de la persona y lo empuja en vivo, sin actor ni participante; sin la conversación no guarda nada |
| `MensajeServiceTest` (+2) | El listado firma como «Formación Renaser», sin avatar, los mensajes del programa sin buscar a la persona guardada; y el preview de una respuesta a uno de ellos |
| `MensajeResponseTest` (3) | El contrato del cable: `SYSTEM`, `senderId` UUID nulo (nunca `null` ni la persona), «Formación Renaser», avatar `null`; la tarjeta con su media; en la bandeja también firma el programa; el mensaje de una persona no cambia |
| `MensajeFanoutPayloadTest` (2) | El aviso en vivo de un mensaje del programa lleva el UUID nulo (si no, la app de la aprendiz lo descartaría como eco propio) |

## 11. Latidos del canal en vivo (2026-09-27, D-202)

**Qué había.** `WebSocketConfig` hacía `enableSimpleBroker("/topic")` sin latidos: el `CONNECTED` decía
`heart-beat:0,0`. Una conexión muerta (el teléfono que pasa de wifi a datos o se queda sin señal) seguía
«abierta» del lado del servidor, con sus suscripciones y su «en línea», hasta que la cortara el sistema
operativo. Lo dejó propuesto E-331.

**Qué hay.** `setHeartbeatValue({10000, 10000})` con `setTaskScheduler(messageBrokerTaskScheduler)`: el
`CONNECTED` dice `heart-beat:10000,10000`, el broker late cada 10 s y cierra la sesión STOMP del cliente que
no escribe nada en 3 × 10 s (la revisa cada 10 s, así que en 30 a 40 s): `ERROR` con `Session closed.` y
cierre 1002. El `SessionDisconnectEvent` apaga la presencia (§9). Un cliente que ofrece `heart-beat:0,0` no
promete latir y no se corta.

**Por qué 10 s.** Es lo que ofrecen los dos clientes que existen (`conexionStomp.ts`,
`heart-beat:10000,10000`). La app nueva manda su latido cada 10 s y, cuando el servidor late, da la conexión
por muerta tras 32 s de silencio (`latidosNegociados`): holgado contra los 10 s del servidor.

**A quién podía cortar, verificado antes de activarlo** (frontend `origin/master` y `evidencia-foto`):

| Cliente | ¿Abre el socket? | ¿Late? | Efecto de D-202 |
|---|---|---|---|
| Web de producción | No: `HAY_CHAT_EN_VIVO` es falso en web | — | Ninguno |
| APK publicado | Sí, pero el CONNECT nunca llega entero (E-331, tramas sin NUL) | — | Ninguno: el broker no registra su sesión |
| App nueva (044159f) | Sí, tramas en binario | Cada 10 s | Sigue conectada; detecta el silencio del servidor |

**El programador.** Se usa el `messageBrokerTaskScheduler` que Spring ya crea para el broker, inyectado
`@Lazy` (vive en la misma configuración que consume `WebSocketConfig`). No se declaró uno propio: los
`@Scheduled` de toda la app buscan un `TaskScheduler` único y con dos caerían a uno local de un solo hilo.

**Costo.** Cada latido del servidor pasa por `EntregaAutorizadaInterceptor`, que mira la sesión HTTP con la
memoria de `SesionViva`: como mucho una lectura de Redis por socket cada 10 s. Si la sesión se revocó, el
latido no sale; el cliente, al no oír nada, cierra y reconecta, y el handshake lo rechaza con 403.

| Clase | Qué fija |
|---|---|
| `LatidosDelChatIT` (2) | Tomcat real, cliente STOMP en binario como la app: el `CONNECTED` negocia `10000,10000` (antes `0,0`); la conexión muda se cierra con `Session closed.` y 1002, la que late sigue abierta y recibe latidos, y la que ofreció `0,0` no se corta |

## 12. La foto del chat de soporte: la tarjeta con el nombre de su aprendiz (2026-09-27, D-205)

**Qué pidió el dueño.** La foto del chat de SOPORTE de cada persona es SU tarjeta de Canva con SU primer
nombre, la misma que le manda la bienvenida (`BienvenidaJava2dAdapter`, §10). Los grupos y la comunidad
usan la tarjeta sin nombre, que la app ya trae como asset (frontend 8971acf, `tarjeta-renaser.jpg`).

> **Corregido 2026-09-27 (D-206).** La frase de arriba ya no vale para la comunidad: el dueño pidió que el
> chat global vuelva al fénix («de la plantilla que te pasé los 2 png […] solo afecta esos 2 primeros»: el
> grupo y el soporte). La tarjeta sin nombre queda para los grupos y de respaldo del soporte.

**El endpoint.** `GET /api/v1/chat/conversations/{id}/foto` (`ConversacionSoporteController`,
`@RequiresPermission(USE_APP)`) responde `image/jpeg` con la tarjeta del aprendiz dueño del soporte.

> **Corregido 2026-09-27 (D-206).** El endpoint pasó a `FotosDelChatController`, junto al de los
> integrantes (§12.1); el servicio se llama `FotosDelChatService` (antes `FotoDelSoporteService`) y la
> prueba contra el Tomcat real `FotosDelChatIT` (antes `FotoDelSoporteIT`). La ruta y el contrato no cambiaron.

| Caso | Respuesta |
|---|---|
| La aprendiz o el staff del soporte (ADMIN/ALCHEMIST participante, mismo `puedeVer` que el resto del chat) | 200, la tarjeta; `Cache-Control: max-age=86400, private` y `ETag` |
| El mismo `If-None-Match` | 304 sin cuerpo (lo resuelve Spring al escribir el `ResponseEntity`) |
| Quien no puede ver la conversación, o sin sesión | 403 |
| Una conversación que no existe, o que no es un soporte (grupo, comunidad, 1 a 1) | 404 |

> **Corregido 2026-09-27 (D-212).** El grupo ya no es siempre 404: con foto propia, la sirve por esta misma
> ruta (§12.2). Sin foto propia sigue siendo 404, igual que la comunidad y un 1 a 1.

El orden es existe → puede verla → es un soporte: a quien no participa no se le dice qué tipo de chat es.
Spring Security no pisa el `Cache-Control` de la foto (lo fija `FotoDelSoporteIT` contra el Tomcat real).

**De dónde sale el nombre.** El aprendiz dueño sale de la clave del soporte (`soporte:<uuid>`,
`Conversacion.aprendizDelSoporte()`; una clave con otra forma da vacío, no lanza) y su primer nombre de
`users.api` (`PrimerNombre`). Si su cuenta ya no existe, va la tarjeta sin nombre.

**Sin tablas, sin S3, sin columnas.** La tarjeta se dibuja al pedirla y se guarda en memoria
(`TarjetasConNombreEnMemoria`, Caffeine): **por portada y primer nombre** (el dibujo pasa el nombre a
mayúsculas, así que «Ana» y «ANA» son la misma entrada; la portada es la vigente, §13), **acotada por peso** a 6 MB (unas cincuenta tarjetas de ~120 KB: el
contenedor de producción tiene tope de memoria, V-8) y con el mismo nombre dibujado una sola vez aunque lo
pidan a la vez. El `ETag` es la huella del contenido (SHA-256 del JPEG, 32 caracteres), no del nombre: si
cambia el fondo o la letra, cambia sola y los teléfonos la vuelven a bajar. Dibujar no va dentro de una
transacción.
> **Corregido 2026-09-27 (D-210).** Decía «**por primer nombre**»: con la portada editable desde la app, una
> clave solo por nombre seguía sirviendo la tarjeta de la portada vieja (E-351). Ahora la clave es (portada,
> nombre), se dibuja sobre la portada de la clave, y las tarjetas de una portada vieja salen solas por el
> tope de peso. Cada foto lee la portada vigente de la bitácora (una fila por índice).

**`ConversacionResponse.photoPath`.** Campo nuevo y opcional: en un soporte, la ruta de su foto
(`/api/v1/chat/conversations/{id}/foto`); `null` en lo demás. Las versiones publicadas de la app lo
ignoran: su esquema de conversación es `passthrough` en todas (verificado en el historial de
`chatSchemas.ts` de `origin/master`). Si el primer nombre cambia, la ruta no: la foto vieja puede verse
hasta un día (el `max-age`) y después se revalida por el `ETag`. Lo mismo al cambiar la portada (§13).

**La app** (frontend `eventos-app`) la muestra en la lista, la cabecera y la info del soporte. La pide con
la sesión: en Android/iOS con las cabeceras del `Image`; en web trae el blob y usa un object URL guardado
por conversación. Mientras carga o si falla queda la tarjeta sin nombre.

| Clase | Qué fija |
|---|---|
| `FotosDelChatServiceTest` (antes `FotoDelSoporteServiceTest`, 7) | La aprendiz ve su tarjeta (primer nombre y huella); el staff ve la de la aprendiz; sin acceso 403 sin dibujar; un grupo o la comunidad 404; la que no existe 404 antes de preguntar el acceso; suspendida 403; sin la cuenta de la aprendiz, sin nombre |
| `TarjetasConNombreEnMemoriaTest` (3) | Un dibujo por nombre sin importar mayúsculas ni espacios; huella del contenido; acotada por peso |
| `FotosDelChatControllerTest` (antes `FotoDelSoporteControllerTest`, 5) | 200 con `image/jpeg`, `Cache-Control` y `ETag`; 304 con `If-None-Match`; 403 (también pidiendo `Accept: image/jpeg`); 404; suspendida 403 sin llegar al caso de uso |
| `FotosDelChatIT` (antes `FotoDelSoporteIT`, 2) | Tomcat real con sesión: la aprendiz y el staff reciben el JPEG entero con el mismo `ETag` y el `Cache-Control` sin pisar; 304; 403 al que no participa y sin sesión; 404 en un 1 a 1 y en una que no existe |
| `ConversacionTest` (+3) | `aprendizDelSoporte`: el soporte lo sabe; lo demás no tiene; una clave rara da vacío |
| `ConversacionResponseTest` (+1) | `photoPath` solo en un soporte |
| `MensajeFanoutPayloadTest` (+1 aserción) | El evento en vivo lleva `SYSTEM` y `TEXT`, como el REST (E-333) |

### 12.1 La tarjeta de cada integrante en la info del grupo (2026-09-27, D-206)

**Qué pidió el dueño.** La lista de integrantes de la info del chat de un grupo mostraba iniciales
(captura: «Ricardo Palomino, E2E Libre 01 y E2E Libre 02» con «RP», «EL», «EL»); tiene que mostrar la
tarjeta de Canva con el primer nombre de cada uno, el mentor incluido. En la página de decisiones eligió
«Siempre su tarjeta con nombre» aunque la persona haya subido foto en «Yo», y pidió dejar listo el otro
modo por si cambia de idea.

**El endpoint.** `GET /api/v1/chat/conversations/{id}/miembros/{usuarioId}/foto` (`FotosDelChatController`,
`@RequiresPermission(USE_APP)`) responde `image/jpeg` con la tarjeta del primer nombre de ese integrante,
con el mismo `Cache-Control`, `ETag` y 304 que la del soporte.

| Caso | Respuesta |
|---|---|
| Quien puede ver el grupo (o el soporte) pide la de un integrante | 200, su tarjeta |
| Cuenta suspendida, quien no puede ver la conversación, o sin sesión | 403 |
| La conversación no existe | 404 |
| La comunidad o un 1 a 1 (aunque se los pueda ver) | 404 |
| Alguien que no es integrante de ese grupo o soporte | 404, sin dibujar nada |
| Un `usuarioId` que no es un UUID | 400 |

El orden es existe → puede verla → es un grupo o un soporte y esa persona es integrante: a quien no
participa no se le dice quién está adentro. **Integrante = quien puede ver la conversación**, con el mismo
`puedeVer` del resto del módulo (en un grupo, la pertenencia vigente, mentor incluido; en un soporte, la
aprendiz y el staff que participa). No se escribió otra regla.

**Dónde viaja la ruta.** La lista de la info no sale de chat sino de `community` (`/me/cells` para el
mentor, `/me/cells/{id}/members` para los aprendices), así que la ruta va ahí: `mentorId` y
`mentorPhotoPath` en la primera, `photoPath` en la segunda (`docs/MODULO_COMMUNITY.md` §13). Community se la
pide a chat por `community.api.FotosDeIntegrantesDelGrupo`, que chat implementa
(`FotosDeIntegrantesDelGrupoAdapter`, adaptador de entrada): chat ya depende de `community.api` y al revés
sería un ciclo. Una consulta por grupo (el chat por `celula_id`).

**El modo** (`FotoDeIntegrantes`; `renaser.chat.foto-de-integrantes`, `CHAT_FOTO_DE_INTEGRANTES` en
Parameter Store):

| Valor | A quién se le manda la ruta de la tarjeta | Qué ve la info |
|---|---|---|
| `TARJETA` (default) | A todos | La tarjeta de cada uno, aunque haya subido foto |
| `FOTO_SUBIDA` | Solo a quien no subió foto (una consulta en lote a `users.api`) | Su foto si la subió; si no, la tarjeta |

Se lee al arrancar, como `BIENVENIDA_ACTIVA`: se cambia el parámetro y se reinicia el contenedor; el log de
arranque dice el modo que tomó (`[chat.fotos] foto de los integrantes en grupos y soporte: …`). Un valor que
no es ninguno de los dos no tumba el arranque: queda `TARJETA` y lo avisa un WARN. El endpoint sirve la
tarjeta en los dos modos: el modo decide a quién se le manda la ruta, no qué tarjetas existen.

**Por qué el servidor decide a quién le manda la ruta, y no un campo con el modo.** Porque así la app tiene
una sola regla que sirve para los dos modos: si llega la ruta, muestra la tarjeta e ignora `avatarUrl`
(con las iniciales mientras carga o si falla); si no llega, la foto subida; si tampoco hay, las iniciales.
Cambiar de modo no pide APK. `avatarUrl` sigue viajando igual porque esas respuestas también alimentan
pantallas que no cambian.

**Qué muestra cada chat.** El global, el fénix (vuelve a como estaba antes de 8971acf); los grupos, la
tarjeta sin nombre; el soporte, la tarjeta de su aprendiz (D-205) en los dos modos, porque es la foto de la
conversación y no la de un integrante. El soporte no tiene lista de integrantes en la app y las burbujas
de un grupo muestran solo el nombre, así que la lista de la info del grupo es el único lugar donde el modo
se ve.

| Clase | Qué fija |
|---|---|
| `FotosDelChatServiceTest` (+10; 17 en total) | Grupo: la aprendiz ve la tarjeta del mentor; soporte: la del staff; 403 a quien no puede ver el grupo, antes de mirar al integrante; suspendida 403 en las dos fotos; la comunidad y un 1 a 1, 404; alguien de afuera, 404 sin dibujar; modo `TARJETA`: todos, sin consultar a nadie; `FOTO_SUBIDA`: solo quien no subió (una URL en blanco no es foto); un modo mal escrito queda en `TARJETA`; grupo sin chat, vacío; `application.yaml` trae `TARJETA` por defecto |
| `FotoDeIntegrantesTest` (3) | La regla de cada modo; se lee sin importar mayúsculas ni espacios y no adivina un valor mal escrito |
| `FotosDelChatControllerTest` (7 en total) | 200 y 304 de la tarjeta de un integrante; 403 y 404; `usuarioId` que no es UUID, 400 sin llegar al caso de uso; suspendida 403 en las dos rutas |
| `FotosDeIntegrantesDelGrupoAdapterTest` (4) | La ruta de cada uno en el chat del grupo; quien no lleva tarjeta no figura; sin chat, vacío; sin integrantes no consulta |
| `FotosDelChatIT` (+4; 6 en total) | Postgres y Tomcat reales: `/me/cells` trae `mentorId` y la ruta del mentor, que sirve su tarjeta (200, 304, otra `ETag` que la del soporte); `/me/cells/{id}/members` trae la ruta de cada aprendiz —también de quien subió foto, en el modo por defecto— y la ven el mentor y los compañeros; 403 a quien no está en el grupo, a una cuenta suspendida y sin sesión; 404 a alguien de afuera, en un 1 a 1 y en una que no existe; en el soporte, la del staff sí |

### 12.2 La foto propia de un grupo (2026-09-27, D-212)

La eligen el ADMIN o el mentor de ese grupo (`docs/MODULO_COMMUNITY.md` §14, donde vive: las columnas de
`celulas`, el caso de uso y quién puede). El chat solo la sirve y la anuncia.

**Servirla.** `GET /api/v1/chat/conversations/{id}/foto` (`FotosDelChatController.fotoDeLaConversacion`,
antes `fotoDelSoporte`): en un grupo, su foto propia, que el chat le pide a community
(`community.api.FotoPropiaDelGrupoFinder.fotoDe`), con el mismo `Cache-Control: max-age=86400, private` y
un `ETag` de su contenido (SHA-256, 32 caracteres). Mismo orden de siempre: existe (404) → quien pide
puede ver el chat del grupo (403) → tiene foto propia (404 si no: la app muestra la tarjeta del APK).

**Anunciarla.** `ConversacionResponse.photoPath` de un grupo viene solo con foto propia:
`/api/v1/chat/conversations/{id}/foto?v=<milisegundos de cuándo cambió>`. El `?v=` no lo lee el servidor;
cambia la URL, y con otra foto el teléfono la baja aunque tenga la anterior en su caché de un día. La lista
de chats (`ConversacionService.listar`) pide cuándo cambió la de cada grupo en UNA consulta
(`cambiadasEn`), y ninguna si la lista no tiene grupos. Los APK publicados ignoran el campo (`passthrough`);
los que ya leen el de D-205 lo usan solo en un soporte.

| Clase | Qué fija |
|---|---|
| `FotosDelChatServiceTest` (+3) | Un grupo con foto propia la sirve con la huella de su contenido; sin foto propia, 404; a quien no ve el grupo, 403 sin preguntarle a community; la cuenta suspendida, 403 también en el grupo |
| `ConversacionServiceTest` (+2) | La lista trae cuándo cambió la de cada grupo en una consulta; sin grupos, no pregunta |
| `ConversacionResponseTest` (+1) | La ruta del grupo solo con foto propia y con `?v=` |
| `FotosDelChatControllerTest` (+1) | La ruta con `?v=` sirve la foto con los mismos encabezados |
| `FotoDelGrupoIT` | De punta a punta: ver `docs/MODULO_COMMUNITY.md` §14 |

## 13. La bienvenida editable desde la app: portada y mensajes (2026-09-27, D-210)

**Qué pidió el dueño.** Al aprobar los tres textos: *«Y si es posible poder hacer el cambio en administrador y
alquimista […] de la portada y el mensaje»*. ADMIN y ALCHEMIST cambian desde la app la **portada** (la imagen de
fondo de la tarjeta) y los **tres mensajes** (el que acompaña la tarjeta, el formal y el del grupo), y vuelven a
los originales cuando quieren. En la app: Administración → Más opciones → Bienvenida (frontend `3e4b1ad`).

**Dónde se guarda.** `cambios_bienvenida` (V73), una bitácora que solo crece, como `ajustes_dia_programa`:

| Columna | Qué guarda |
|---|---|
| `id` | Identidad creciente: «el último» es el de `id` más alto, no el de `cambiado_en` (dos cambios en el mismo instante tendrían un orden ambiguo) |
| `pieza` | `SOPORTE_CON_LA_TARJETA`, `SOPORTE_FORMAL`, `GRUPO` o `PORTADA` (`PiezaDeBienvenida`) |
| `texto` | El mensaje nuevo (1 a 1000 caracteres); NULL en la portada |
| `portada_ruta` | La clave del objeto, bajo `bienvenida/portadas/`; NULL en un texto |
| `cambiado_por` | Quién (ON DELETE SET NULL: borrar la cuenta deja el cambio, sin autor) |
| `cambiado_en` | Cuándo (el `Clock` del servidor) |

Lo vigente de cada pieza es su **última fila**; si no tiene, o la última es una vuelta al original (`texto` y
`portada_ruta` en NULL), sale el original: el texto de `bienvenida/mensajes.yaml` o `bienvenida/fondo.png`. Los
originales no se copian a la base. La regla vive en `EstadoDePieza` y la usan los tres lectores: los textos que se
mandan (`TextosDeBienvenidaVigentes`), la portada que se dibuja (`PortadasDeBienvenida`) y la pantalla de
Administración. Guardar lo que ya sale, o volver al original cuando ya sale el original, no escribe nada.

**Qué se acepta** (dominio; los números son técnicos, a confirmar con el dueño):

| Pieza | Regla | Mensaje (la app lo muestra tal cual) |
|---|---|---|
| Texto | No vacío | «El mensaje no puede quedar vacío.» |
| Texto | Hasta 1000 caracteres, contados como `char_length` (un emoji es uno) | «El mensaje tiene 1001 caracteres: el máximo es 1000.» |
| Texto | Con sus marcadores: `{nombre}` en los tres, `{mentor}` en el del grupo | «Al mensaje le falta {nombre}: es donde va el nombre de la persona.» |
| Texto | Sin marcadores que nadie reemplaza (un `{…}` que no es suyo) | «El mensaje tiene {mentor}, que no se reemplaza por nada: en este mensaje solo se puede usar {nombre}.» (con `{Nombre}`, sugiere `{nombre}`) |
| Portada | JPEG o PNG, por el contenido | «Esa imagen no es JPG ni PNG.» |
| Portada | Hasta 5 MB | «La imagen pesa 7,2 MB: el máximo es 5 MB.» |
| Portada | De 600 a 8000 px por lado; si no es cuadrada se usa el centro, llevado a 1200 × 1200 | «La imagen es muy chica (…)» / «demasiado grande (…)» |
| Portada | El nombre se lee: como mucho el 10 % de su franja (abajo al centro, donde lo escribe `BienvenidaJava2dAdapter`) con contraste < 3:1 contra el verde de la letra (WCAG, letra grande) | «El nombre no se leería: la franja donde va (abajo, al centro) es muy oscura. Elige una imagen más clara en esa parte.» |

La portada de Operaciones pasa holgada (menos del 1 % oscuro, `ImagenDePortadaTest.laOriginalPasa`). La imagen se
abre leyendo antes las medidas de la cabecera y salteando píxeles al decodificar: una foto de 8000 px ocupa unos
17 MB y no 190 (tope de memoria de producción, V-8).

**La imagen, en el mismo almacenamiento que las fotos de evidencia.** El teléfono la sube directo con URL
prefirmada, como la portada de un evento (D-186): `upload-url` → `PUT` → `confirm`. El servidor la baja con
`AlmacenamientoPort.leer` —nuevo, acotado al peso máximo; es la segunda excepción a «el backend no toca los bytes»,
tan acotada como `subir`— para revisarla y para dibujar, y la guarda abierta en memoria (la vigente y una
candidata). Una ruta no se reescribe nunca (cada subida lleva un id nuevo). Si la portada vigente deja de abrir
(se borró, o el proceso corre con el almacenamiento de marcador), se dibuja sobre la original y queda un `WARN`: la
bienvenida y la foto del soporte siguen saliendo. Permiso IAM: `s3:GetObject`, que el principal ya tiene (D-54).

**En local** el almacenamiento es de marcador y **no se prende S3**: el default de `AWS_S3_BUCKET` es el bucket de
producción (E-305). La URL de subida sale `about:blank#pendiente-s3/…`, la app lo detecta y lo dice, y `confirm`
responde 409. `sePuedeCambiar` viene en `false` y la app deja el botón deshabilitado con la explicación. Los textos
sí se cambian en local.

**Quién puede.** `Permission.MANAGE_WELCOME`, solo ADMIN y ALCHEMIST activos, con el patrón de D-186:
`BienvenidaParaAdministrar.exigirQuePuedaCambiarla` pide cuenta ACTIVA y `canManageRoles()` antes de mirar nada
(también antes de validar la clave o el texto: 403 antes que 400). El 403 de TRAINEE lo da el interceptor; el de
MENTOR, el de MENTOR_LEAD (en modo sombra) y el de un ADMIN o ALCHEMIST suspendido, el servicio.

**Los endpoints** (todos con sesión y `@RequiresPermission(MANAGE_WELCOME)`; los errores, JSON `{message, timestamp}`):

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /api/v1/admin/bienvenida` | — | 200 la bienvenida (abajo) |
| `PUT /api/v1/admin/bienvenida/textos/{clave}` | `{"texto": "…"}` | 200 la bienvenida; 400 con el motivo, o si la clave no es SOPORTE_CON_LA_TARJETA, SOPORTE_FORMAL ni GRUPO |
| `DELETE /api/v1/admin/bienvenida/textos/{clave}` | — | 200 la bienvenida (vuelve al original) |
| `POST /api/v1/admin/bienvenida/portada/upload-url` | `{"contentType": "image/jpeg"}` (o `image/png`) | 200 `{"url", "ruta"}`; 400 otro tipo |
| `POST /api/v1/admin/bienvenida/portada/confirm` | `{"ruta": "bienvenida/portadas/<uuid>"}` | 200 la bienvenida; 400 con el motivo; 404 sin imagen en esa ruta; 409 sin almacenamiento de verdad |
| `DELETE /api/v1/admin/bienvenida/portada` | — | 200 la bienvenida (vuelve a la original) |
| `GET /api/v1/admin/bienvenida/tarjeta?nombre=María[&portada=<ruta>]` | — | 200 `image/jpeg`, `Cache-Control: no-store`: la tarjeta con el primer nombre de ejemplo sobre la vigente o sobre la candidata (revisada: 400/404/409 como `confirm`) |

La bienvenida: `{"activa", "largoMaximo", "textos": [{"clave", "texto", "original", "cambiado", "marcadores",
"ultimoCambio": {"por", "en", "volvioAlOriginal"} | null}], "portada": {"cambiada", "sePuedeCambiar",
"ultimoCambio"}}`. `activa` es `BIENVENIDA_ACTIVA`: apagada, lo que se cambie vale recién cuando se prenda.
**Los endpoints que ya existían no cambian de forma**: `GET /api/v1/chat/conversations/{id}/foto` sigue igual y su
`ETag` sale del contenido, así que cambia solo con la portada.

**Afuera, a propósito.** Borrar del almacenamiento las imágenes rechazadas o reemplazadas (quedan sin uso, como la
portada de un evento que no se confirma); la foto de grupos y comunidad, que la app trae como asset y no cambia con
la portada; el historial completo en la app (se ve el último cambio de cada pieza; la historia está en la tabla).

| Clase | Qué fija |
|---|---|
| `TextoDeBienvenidaTest` (13) | Vacío, sin `{nombre}` (los tres), el del grupo sin `{mentor}`, `{mentor}` en el soporte, `{Nombre}` con sugerencia, 1000 caracteres con emojis, sin espacios en los bordes |
| `PortadaDeBienvenidaTest` (12) | Tipos que se suben; solo rutas de portadas (ni firmas ni `..`); formato, peso y medidas con su mensaje; el 10 % |
| `EstadoDePiezaTest` (6) | Sin cambios el original; el guardado; la vuelta al original; la portada; otra pieza no se mezcla; las claves de la API |
| `ImagenDePortadaTest` (8) | La portada de Operaciones pasa; una negra no; importa solo la franja del nombre; el límite es el contraste 3:1 (gris #808080 sí, #707070 no); el centro de una apaisada; una de 4000 × 3000; PNG transparente queda blanco; GIF, basura, chica y dañada |
| `PortadasDeBienvenidaTest` (5) | La vigente; revisada una vez no se vuelve a bajar; la oscura se rechaza; ruta ajena y sin subir; sin la subida, la original |
| `TarjetasConNombreYPortadaTest` (3) | Con la portada nueva no se sirve la vieja y cambia la huella; al volver, la original sin redibujar; se dibuja sobre la portada de la clave aunque la vigente cambie mientras tanto. Falla contra la clase de D-205 (E-351) |
| `BienvenidaJava2dAdapterTest` (+2) | Con la portada nueva la tarjeta sale sobre ella y el nombre queda donde estaba; se puede dibujar sobre una portada dada |
| `TextosDeBienvenidaVigentesTest` (2) · `TextosDeBienvenidaYamlAdapterTest` (+2) | Sin cambios salen los del repo; el guardado reemplaza solo a su mensaje y la vuelta lo devuelve; los originales cumplen las reglas de un texto guardado |
| `BienvenidaConTextosCambiadosTest` (2) | El formal guardado sale en la próxima bienvenida del soporte y, al volver, el del repo; el del grupo guardado sale con el nombre y el mentor |
| `TextosDeBienvenidaAdminServiceTest` (12) · `PortadaDeBienvenidaAdminServiceTest` (11) | ADMIN y ALCHEMIST sí; TRAINEE, MENTOR, MENTOR_LEAD y cuentas suspendidas, 403 sin escribir nada (403 antes que 400); bitácora con quién y cuándo; sin `{nombre}` se rechaza; sin cambios repetidos; URL de subida; confirmar revisa; 409 sin almacenamiento; vista previa con el primer nombre |
| `BienvenidaAdminControllerTest` (9) | El JSON de la bienvenida; PUT/DELETE; 400/404/409 con el motivo en JSON (también pidiendo la imagen); la tarjeta `image/jpeg` con `no-store`; TRAINEE y TRAINEE suspendido, 403 del interceptor sin llamar al caso de uso; el 403 del servicio sale como 403 |
| `S3AlmacenamientoAdapterTest` (+3) | `leer` baja del bucket configurado; no baja un objeto más pesado que el tope (por lo que declara S3 o al leer); lo que no existe da vacío |
| `BienvenidaEditableIT` (5) | Tomcat real con sesión, Postgres con V73 y la bienvenida prendida: el texto guardado sale en la próxima bienvenida y la vuelta al original saca el del repo, con las filas de la bitácora; ALCHEMIST puede y sin `{nombre}` es 400; MENTOR, MENTOR_LEAD, TRAINEE, ADMIN suspendido y sin sesión, 403; sin almacenamiento la URL es de marcador, `confirm` 409 y la tarjeta de muestra sale; los CHECK de V73 y el SET NULL al borrar la cuenta |
| `PortadaDeBienvenidaIT` (2) | Con un almacenamiento en memoria: subir, ver la candidata antes de usarla (la foto del soporte todavía da 304), confirmar, y la foto sale sobre la portada nueva con otro `ETag` (con el viejo ya no es 304); al volver a la original, el `ETag` de antes. Una portada oscura: 400 con el motivo en la vista previa y al confirmar. Contra la caché de D-205 falla: `expected: 200 but was: 304` (E-351) |

## 14. La doble marca de leído: ✓ guardado, ✓✓ leído (2026-09-27, D-208)

**Qué pidió el dueño.** En la página de decisiones eligió «Quiero ✓✓ de leído (trabajo extra en el servidor)». Hasta ahora
los mensajes propios llevaban un solo ✓ (el servidor lo guardó): el backend no informaba lectura por mensaje.

**La regla.**

| Conversación | ✓✓ cuando… |
| 1 a 1 (DIRECTA) | el otro lo leyó |
| Grupo (CELULA) y soporte (SOPORTE) | lo leyeron **todos** los demás participantes, como WhatsApp |
| Comunidad (GLOBAL) | nunca: queda un solo ✓ |

La comunidad no tiene ✓✓ porque es el chat de toda la generación, con cientos de personas que entran cuando quieren: «leído
por todos» no llegaría nunca o no diría nada, y avisar cada lectura les llegaría en vivo a todos los conectados. Ahí el
servidor ni calcula ni avisa (`TipoConversacion.confirmaLectura`).

**Cómo se calcula (sin tablas ni índices nuevos).** Con `participantes_conversacion.ultimo_leido_en`, que ya existía y que
mueven abrir el chat (`POST .../read`) y escribir (`MensajeService.enviar`). La marca de agua es el mínimo entre los
participantes que pueden leer (`ConfirmacionDeLectura`, dominio puro): un mensaje escrito en ese instante o antes está
leído.

- **La misma marca sirve para los mensajes de cualquiera.** La pregunta es «¿lo leyeron los DEMÁS?», que depende de quién
  escribió; pero quien escribe queda marcado en el mismo instante de su mensaje y su marca solo avanza, así que para un
  mensaje suyo el mínimo entre todos y el mínimo entre los demás coinciden. Por eso el aviso en vivo es uno solo para todos
  los que miran y no uno por destinatario.
- **Quiénes cuentan:** los participantes con la cuenta activa. Suspender no saca a nadie de un chat, y una cuenta
  suspendida congelaría el ✓✓ de su grupo o, si es del staff, de todos los soportes (E-345). En un soporte coincide con la
  regla 1 de D-136 (el aprendiz y el staff *activo*).
- **Sin ✓✓** si alguno nunca abrió la conversación (marca nula) o si del otro lado no queda nadie activo.
- **Quien se suma después** arranca con la marca en el momento en que entró (CH-1): no le quita el ✓✓ a lo que ya estaba leído.
- **Una lectura por conversación, no por mensaje:** los participantes de la conversación sobre la clave primaria
  `(conversacion_id, usuario_id)` (el `EXPLAIN` local muestra `Bitmap Index Scan on participantes_conversacion_pkey`; por eso no
  hizo falta V74), más una consulta en lote a `users.api` por el estado de esas cuentas. En el listado es una por página, y
  ninguna si en la página no hay mensajes propios; en la comunidad, ninguna.

**El contrato.**

- `MensajeResponse.status`: `SENT` (✓) o `READ` (✓✓) en los mensajes propios de quien mira, en `GET .../messages`. En la
  comunidad siempre `SENT`. `null` en los de otras personas y en los del programa (la bienvenida guardada a nombre del
  aprendiz no es suya). Como `senderName`, solo se resuelve en el listado: en la respuesta de enviar y en el último mensaje
  de la bandeja va `null`, que la app toma como ✓.
- Evento nuevo por `/topic/conversaciones/{id}`: `{"event":"READ","readUpTo":"2026-09-27T17:00:26.869554Z"}` («todos
  leyeron hasta»). Sale después de cada `POST .../read` que deja una marca (`ConversacionService.marcarLeido` →
  `LecturaService.anunciar` → `RedisChatPublisher.publicarLectura`), nunca en la comunidad, y no dice quién leyó ni cuándo leyó
  cada uno. Nunca falla hacia arriba: la lectura ya quedó guardada.

**En vivo, de punta a punta.** Ana escribe. Luis, con el chat abierto, recibe el `MESSAGE`, recarga el historial y marca
leído; el servidor publica `READ` con la marca nueva; la app de Ana pasa a ✓✓ sus mensajes escritos hasta ahí, sin recargar.
Un aviso de lectura nunca dispara otra lectura (la app no marca leído por un `READ`): así dos teléfonos con el chat abierto
no se avisan sin fin.

**Cambios de comportamiento que vienen con esto.**

- La marca de lectura solo avanza, en un UPDATE condicionado (`marcarLeidoSiAvanza`, con `flushAutomatically` y sin
  `clearAutomatically`, ver su javadoc). Antes era leer la fila, pisarla y guardarla: dos lecturas a la vez (el teléfono y
  la web) podían dejar la más vieja, y un ✓✓ habría vuelto a ✓.
- `ConversacionService.marcarLeido` ya no es `@Transactional`: el aviso sale con la lectura guardada, y calcula la marca
  con las de los demás ya guardadas. Dentro de una sola transacción, dos lecturas simultáneas calcularían cada una sin ver
  la otra y la última en avisar podría anunciar una marca vieja.
- `MensajeService.enviar` guarda y devuelve el instante en microsegundos (E-344): la app compara ese `createdAt` con la marca.

**Compatibilidad con la app publicada** (sin actualización por aire), verificada corriendo el código de `origin/master` del
frontend con los cuerpos nuevos: su esquema del mensaje es `passthrough` y su mapeador arma la burbuja campo por campo, así
que `status` se ignora (con `READ`, `SENT`, `null` o un valor desconocido la página valida igual); su `leerEventoDelChat`
devuelve `null` para un `READ` —también si trae campos de mensaje— y el manejador sale sin tocar nada. Además, ese APK nunca
completa el CONNECT (E-331).

**Autorización.** No hay endpoint nuevo. `status` solo sale en `GET .../messages`, que exige cuenta activa y participación
(la pertenencia vigente en un grupo, el rol vigente en un soporte); el `READ` viaja por el mismo destino que ya autoriza
`SubscripcionAutorizadaInterceptor` al suscribirse y `EntregaAutorizadaInterceptor` en cada entrega.

**Pregunta abierta (para el dueño).** En el soporte, el aprendiz ve ✓✓ cuando leyó TODO el staff activo, como pidió. Si
algún administrador no abre nunca los soportes, el ✓✓ no llega. Si se prefiere que baste con que lea uno del staff, es otra
regla y no se implementó.

**No incluye:** «entregado» (el servidor no sabe cuándo un mensaje llegó a un teléfono), marcas en la fila de la lista de
chats, y recuperar un `READ` perdido con el socket caído (la marca se pone al día al volver a abrir el chat o con el próximo
mensaje, que recarga el historial).

| `ConfirmacionDeLecturaTest` (9) | 1 a 1: leído si el otro leyó después, también en el mismo instante; nada si nunca leyó o no queda nadie; grupo y soporte: hacen falta todos; la misma marca vale para los mensajes de cada uno; la comunidad nunca; solo los propios, y no los del programa |
| `LecturaServiceTest` (6) | El aviso lleva el mínimo; la comunidad no consulta ni avisa; sin aviso si alguien nunca leyó o no queda nadie activo; una cuenta suspendida no frena; nunca falla hacia arriba |
| `MarcaDeLeidoEnElListadoTest` (7) | El listado con el servicio real: 1 a 1, grupo, soporte con una suspendida, comunidad siempre `SENT`, una lectura por página y ninguna sin propios, el mensaje del programa sin marca, 403 a quien no participa y a una cuenta suspendida |
| `ConversacionServiceTest` (+2) | Marcar leído guarda y DESPUÉS avisa; una cuenta suspendida no marca ni avisa |
| `MensajeServiceTest` (+1) | E-344: el mensaje y la marca del emisor van en microsegundos |
| `MensajeResponseTest` (+1) | `status` sale `SENT` o `READ`, y `null` donde no se resuelve |
| `LecturaFanoutPayloadTest` (2) | El cuerpo exacto `{"event":"READ","readUpTo":"…"}` por el canal de la conversación; Redis caído no lanza |
| `ChatPersistenceAdapterTest` (+2) | Contra Postgres: la marca no retrocede y se lee con la nula incluida; el UPDATE ve lo pendiente de la misma transacción |
| `LecturaEnVivoIT` (5) | Tomcat, Postgres, Redis y un cliente STOMP en binario: a Ana le llega `READ` cuando Luis abre el chat; el listado pasa de `SENT` a `READ`; el soporte llega a `READ` con una administradora suspendida adentro; la comunidad queda `SENT` y no avisa; 403 al que no participa y a la suspendida, y el que no participa no se suscribe |

Contra el código anterior, `LecturaEnVivoIT` falla en 4 de sus 5 pruebas (la de autorización negativa ya pasaba: es la
guarda); la del aviso en vivo, con `No llegó lo esperado tras 20 intentos; llegó: []`. Las demás usan clases nuevas y no
compilan contra él.

---

## 15. El largo máximo de un mensaje (2026-09-27, D-215; E-374)

**Qué pasaba.** CHT-06 del e2e: `POST /api/v1/chat/conversations/{id}/messages` con 1.048.576 caracteres → 201, y
se guardaba entero. Ni el request ni el dominio tenían tope, y `mensajes.texto` es `text`.

**El tope.** `Mensaje.LARGO_MAXIMO_DEL_TEXTO = 6.000`, en `Mensaje.escribir`: lo que escribe una persona, sea un
texto, el epígrafe de una foto o una publicación del Muro compartida (que usa el mismo camino). Más es un 400
«El mensaje puede tener hasta 6000 caracteres». Los mensajes del programa (`Mensaje.delPrograma`: bienvenidas,
semáforo) no llevan tope, porque los escribe el servidor.

**Por qué 6.000** (propuesta, D-215): alcanza de sobra para escribir y deja entrar una publicación del Muro
compartida, que puede tener 5.000 más su encabezado. Se cuenta con `String.length()`, lo mismo que el
`maxLength` del campo de la app, así los dos cuentan igual (un emoji son dos). La app limita el campo al mismo
número (`LARGO_MAXIMO_DEL_MENSAJE`).

**Sin `CHECK` en la base.** La fila de 1 MB del e2e ya existe y el tope del dominio alcanza para lo nuevo.

| Clase | Qué fija |
|---|---|
| `MensajeTest` (+2) | 6.000 entra y 6.001 es 400 con su texto (también el epígrafe de una foto); se cuentan caracteres y no bytes. Fallan contra el código anterior |


---

## 16. Nombres de los chats y aviso de mensaje nuevo (2026-09-29, D-221)

**Nombres** (derivados al leer, salvo la comunidad): comunidad «Formación Renaser Global» (columna, V83);
grupo «<primer nombre del mentor vigente> y sus aprendices» o, sin mentor, el nombre del grupo
(`community.api.MentorVigenteFinder`); soporte «<primer nombre> – Formación Renaser»; 1 a 1, `null` (la app lo
nombra con el otro). Regla en `NombreDelChat`; lote en `NombresDeLosChatsService` (una consulta a `community`,
una a `users`). Los grupos siguen naciendo solo cuando el Admin crea la célula (`CelulaCreadaChatListener`).

**Aviso:** `MensajeService` y `MensajeDelProgramaService` publican `chat.api.MensajeDeChatGuardadoEvent`
(outbox). `notifications` lo escucha y pregunta a `chat.api.AvisosDeMensajesFinder`
(`AvisosDeMensajesService`) destinatarios, nombre del chat por destinatario y no leídos. Quién: participantes
con la regla de acceso del tipo, menos el autor, menos quien tiene el chat abierto (`ConversacionesAbiertasDeSockets`
anota en Redis `chat:abierta:<conversación>:<usuario>`, 3 min renovados cada 45 s, al SUBSCRIBE autorizado; se
borra al UNSUBSCRIBE o al cerrarse el socket). La app nueva se desuscribe en segundo plano.

| Clase | Qué fija |
|---|---|
| `NombreDelChatTest` (4) | Los tres nombres y sus respaldos |
| `AvisosDeMensajesServiceTest` (6) | Autor fuera; grupo con pertenencia vigente; soporte con staff de hoy; chat abierto fuera; mensaje del programa a todos; 1 a 1 |
| `ConversacionesAbiertasDeSocketsTest` (3) | Suscribir abre, desuscribir cierra, dos teléfonos, socket caído |
| `MensajeDeChatAvisoIT` (6) | Postgres + Redis + outbox: quién recibe fila y push (suspendida con fila sin push, preferencia apagada sin nada, ex mentor y ajenos sin nada), título con conteo, chat abierto sin aviso, nombre que cambia al rotar el mentor, soporte viejo renombrado, purga sin transacción (E-425) y V83 |
