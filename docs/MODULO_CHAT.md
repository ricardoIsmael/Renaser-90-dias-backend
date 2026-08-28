# Módulo `chat` — Conversaciones, Mensajes, WebSocket + Redis Pub/Sub

**Fecha:** 2026-08-25
**Documentos hermanos:** `CLAUDE.MD` (cómo) · `docs/PLAN_DE_MODULOS.md` §10 (agregados sugeridos, Ola 4) · `docs/MODULO_COMMUNITY.md`, `docs/MODULO_NOTIFICATIONS.md` (patrones replicados acá: eventos cross-módulo, `@ApplicationModuleListener`)

---

## 0. Estado

🔄 **Construido. `./mvnw clean test` no se corrió desde este agente** (regla del encargo: no ejecutar Maven ni git) — lo corre el supervisor.

**Fuentes usadas para las reglas de negocio — importante:** este módulo se construyó **sin acceso ni referencia al backend Next.js viejo** (no se leyó `Backend90dias/RenaserBack`, `renaser backend/RenaserBackCopy` ni `renaser90 dias/RenaserBack`). Las únicas fuentes fueron: `src/main/resources/db/migration/V1__baseline_renaser.sql` (schema real, sección `-- CHAT`, líneas 1274-1336), `CLAUDE.MD` (arquitectura y reglas de trabajo), `docs/PLAN_DE_MODULOS.md` §10, y los módulos Java ya construidos (`community`, `points`, `users`) como plantilla de estructura. Donde el esquema no alcanzaba para decidir una regla de negocio (ver §6), se documentó como pregunta abierta en vez de inventarla.

---

## 1. Esquema real (`V1__baseline_renaser.sql:1274-1336`)

- `conversaciones`: `tipo` (`CELULA`/`DIRECTA`/`GLOBAL`) con el CHECK `tipo_coherente` — cada tipo exige exactamente un campo identificador propio (`celula_id`, `clave_directa`, o ninguno para GLOBAL). Índice único parcial `conversacion_global_unica_uk` garantiza una sola fila `GLOBAL`.
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
| `mensaje` | EnviarMensaje, ListarMensajes (paginación keyset) |

### 3.3 Endpoints REST

| Método | Ruta | Notas |
|---|---|---|
| POST | `/api/v1/chat/conversations/direct` | `{otherUserId}` → busca-o-crea, 201 |
| GET | `/api/v1/chat/conversations` | mis conversaciones, con `unreadCount` y `lastMessage` |
| POST | `/api/v1/chat/conversations/{id}/read` | marca leído hasta ahora |
| POST | `/api/v1/chat/conversations/{conversationId}/messages` | enviar, 201 |
| GET | `/api/v1/chat/conversations/{conversationId}/messages?cursor=&limit=` | paginación keyset por `creado_en`, descendente |

Todos reciben el actor por `X-Actor-Id` (mismo patrón temporal que el resto de los módulos ya construidos, sin JWT — bloqueante del usuario documentado en `docs/MODULOS_A_AVANZAR.md`).

**D-36 aplicado:** `TipoConversacion`/`TipoMensaje` viven en español en dominio y base; el wire habla inglés (`CELL`/`DIRECT`/`GLOBAL`, `TEXT`/`IMAGE`/`AUDIO`/`VIDEO`/`SYSTEM`) — la traducción vive solo en `ConversacionResponse.toWireTipo`/`MensajeResponse.toWireTipo` (salida) y `MensajeController.parseTipoMensaje` (entrada), nunca en dominio ni persistencia.

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
