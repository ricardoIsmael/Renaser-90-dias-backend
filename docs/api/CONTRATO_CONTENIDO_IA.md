# Contrato de API — Academy, Chat, Notifications, Support, RAG

**Fecha:** 2026-08-26
**Alcance:** los 5 módulos de "contenido + IA" del backend Java: `academy`, `chat`, `notifications`, `support`, `rag`. Todo lo que sigue sale de leer el código real (controllers, DTOs, servicios de aplicación, dominio, config) — no del contrato viejo de Next.js, aunque en varios lados se lo cita como referencia histórica. Donde no pude confirmar algo contra el código, lo digo explícitamente en vez de inventarlo.

**Base URL:** `http://localhost:8080` (puerto por defecto de Spring Boot — no hay `server.port` configurado explícitamente en `application.yaml`, así que corre en el 8080 salvo que se sobreescriba con `SERVER_PORT`).

---

## 0. Lo que es igual en los 5 módulos (leelo antes de saltar a un endpoint)

### 0.1 Identidad del actor: header `X-Actor-Id`, no JWT todavía

**No hay autenticación real.** `SecurityConfig` (`src/main/java/com/renaser/os/shared/web/SecurityConfig.java:30-36`) deja **todo** `/api/v1/**` en `permitAll()` — Spring Security no bloquea nada. La identidad del actor viaja en el header `X-Actor-Id` (un UUID de `usuarios`), que **cada controller lee directamente** con `@RequestHeader("X-Actor-Id") String actorId` y convierte con `UserId.of(actorId)`. No existe ningún filtro/interceptor global ni clase `ActorContext` compartida — es el mismo patrón repetido en los ~29 controllers del backend.

- Falta el header → `MissingRequestHeaderException` → **400**, `{"message": "Falta el header obligatorio 'X-Actor-Id'"}`.
- Header con formato no-UUID → `UserId.of` lanza `IllegalArgumentException("UserId no es un UUID valido: " + value)` → **400**.
- `X-Actor-Id` está en la whitelist CORS (`Authorization`, `Content-Type`, `X-Actor-Id` — `SecurityConfig.java:53`), reservando `Authorization` para cuando llegue el JWT real de Supabase (config ya presente en `application.yaml`, `oauth2.resourceserver.jwt.jwk-set-uri`, pero sin filtro conectado todavía).

De ahí en más, **cada módulo verifica por su cuenta** que el actor exista y (salvo la excepción documentada de `support`) esté `ACTIVE`. No hay `@RequiresPermission`/`@PublicEndpoint` en ningún controller del repo (ese mecanismo no existe todavía).

### 0.2 Formato de error uniforme

Todas las respuestas de error (400/403/404/409/429) tienen esta forma exacta (`shared/web/ApiErrorResponse.java`):

```json
{ "message": "texto legible", "timestamp": "2026-08-26T02:12:04.349746Z" }
```

Mapeo de excepción → HTTP, aplicado por `shared/web/GlobalExceptionHandler.java` (un único `@RestControllerAdvice` para todo el backend):

| Excepción | HTTP | Mensaje |
|---|---|---|
| `NotAuthorizedException` | 403 | el de la excepción, literal |
| `NoSuchElementException` | 404 | el de la excepción, literal |
| `IllegalArgumentException` / `ConstraintViolationException` | 400 | el de la excepción, literal |
| `IllegalStateException` | 409 | el de la excepción, literal |
| `RateLimitExceededException` | 429 | el de la excepción, literal |
| `MethodArgumentNotValidException` (falla `@Valid` en el body) | 400 | `"campo: mensaje; campo2: mensaje2"` |
| `MissingRequestHeaderException` | 400 | `"Falta el header obligatorio '<nombre>'"` |
| `MissingServletRequestParameterException` | 400 | `"Falta el parametro obligatorio '<nombre>'"` |
| `MethodArgumentTypeMismatchException` (path/query mal tipado, ej. UUID inválido) | 400 | `"El valor de '<nombre>' no tiene el formato esperado"` |
| `DateTimeParseException` (cursor de paginación mal formado) | 400 | `"Fecha u hora con formato invalido: se espera ISO-8601 (ej. 2026-08-25T10:00:00Z)"` |
| `HttpMessageNotReadableException` (JSON malformado) | 400 | `"El cuerpo de la solicitud es invalido o esta mal formado"` |
| `DataIntegrityViolationException` (choque de UNIQUE/CHECK/FK, ej. doble tap) | 409 | `"La operacion entra en conflicto con datos que ya existen"` |

### 0.3 Cómo se resuelven `403` vs `404`, en general

El patrón que se repite en `academy`, `chat`, `support` y `rag`: primero se confirma que el **actor** existe y está activo (404/403), y según el endpoint, después se confirma que el **recurso** existe (404) y recién al final la **visibilidad/pertenencia** sobre ese recurso (403) — nunca al revés cuando hay riesgo de que un 404 filtre si un recurso ajeno existe. El caso más explícito de esto es Espejo de Sombra (rag, §5.5) y el ticket a mentor de `support`.

---

## 1. Módulo `academy` — Cursos, Secciones, Lecciones, Clase Diaria, Recomendación

Todos los endpoints están en `/api/v1/cursos/**`, `/api/v1/lecciones/**`, `/api/v1/classroom/clase-diaria`, `/api/v1/academia/recomendacion`. Todos leen el actor por `X-Actor-Id`, resuelven el "progreso del participante" (`ConsultarProgresoParticipanteAcademyPort`) y devuelven **404** `"Usuario no encontrado: <id>"` si no hay fila, **403** `"Cuenta suspendida"` si está suspendido — antes de cualquier otra regla.

**El wire de `academy` está en español/`snake_case`** (no inglés/camelCase como el resto): `cursos`/`lecciones` nunca vivieron en Prisma, siempre hablaron directo con Postgres/PostgREST, así que se preservan sus nombres de columna tal cual (`dia_desbloqueo`, `portada_url`, `acceso: "abierto"|"restringido"`). Excepción: los DTOs que **nunca fueron una fila de tabla** (`CursoDetalleResponse.portadaFirmada`, `MotivoBloqueoResponse`, `ClaseDiariaResponse`, `RecomendacionResponse`) van en **camelCase**.

### 1.1 La distinción clave: "candado visible" vs "rechazo directo" vs "motivo oculto"

Tres comportamientos distintos ante un curso/lección bloqueado — no los confundas:

1. **Catálogo con candado, muestra metadata:** `GET /api/v1/cursos/bloqueados` y el árbol interno de `GET /api/v1/cursos/{id}` (`secciones[].bloqueada_por_dia`) — nunca dan error, marcan el contenido como bloqueado con `dia_desbloqueo`/`dias_faltantes`.
2. **Rechazo directo (403):** `GET /api/v1/cursos/{id}` (curso raíz), `GET /api/v1/cursos/{id}/secciones`, `GET /api/v1/lecciones/{id}`, `POST`/`DELETE /api/v1/lecciones/{id}/complete` — si el actor no tiene acceso vigente al curso/sección, **403** antes de devolver nada del contenido.
3. **Motivo bajo demanda, revela poco:** `GET /api/v1/cursos/{id}/preview` y `GET /api/v1/lecciones/{id}/preview` — **nunca dan error**, devuelven `{locked, reason, ...}`; **solo revelan el motivo cuando es por día de programa** (`reason:"dia_desbloqueo"`). Si el bloqueo real es por rol, por no estar publicado o por ser `RESTRINGIDO`, devuelven `{locked:false}` igual — para no revelar de más.

`CompletarLeccionUseCase`/`DescompletarLeccionUseCase` **no publican ningún evento de dominio ni tocan puntos** — confirmado en el código (`CatalogoAcademyService` no importa `ApplicationEventPublisher` ni ningún `*Event`). La integración con `habits` (cerrar el hábito + otorgar puntos al completar la Clase Diaria) está pendiente, documentada como hueco explícito.

### 1.2 `GET /api/v1/cursos` — catálogo accesible del actor, con progreso

- **Headers:** `X-Actor-Id`.
- **Query/body:** ninguno.
- **200 OK** → `List<MiCursoResponse>`. Cada item mezcla (spread, `@JsonUnwrapped`) los campos de `CursoResponse` con `progreso` y `portada_firmada`:
  ```json
  {
    "id": "curso-1", "slug": "...", "titulo": "...", "descripcion": "...", "portada_url": "...",
    "orden": 1, "publicado": true, "acceso": "abierto", "origen": "...",
    "dia_desbloqueo": 5, "roles_permitidos": ["TRAINEE"],
    "creado_en": "2026-01-01T00:00:00Z", "actualizado_en": "2026-01-01T00:00:00Z",
    "progreso": { "curso_id": "curso-1", "total_lecciones": 10, "completadas": 3, "ultima_leccion_id": null },
    "portada_firmada": "https://..."
  }
  ```
  `progreso.ultima_leccion_id` **siempre es `null`** — el código viejo nunca trackeó "última vista", se preserva el campo por fidelidad de wire sin funcionalidad real detrás.
- **Quién puede llamarlo:** cualquier rol con fila de progreso no suspendida. No hay gate "solo TRAINEE" acá — lo que varía por rol es qué cursos ve cada uno (`Curso.visibleEnCatalogoPara(rol, programDay)`); `programDay` es `null` para todo rol que no sea TRAINEE.
- **Errores:** 400 sin header/UUID inválido; 404 `"Usuario no encontrado: <id>"`; 403 `"Cuenta suspendida"`.

```bash
curl -s http://localhost:8080/api/v1/cursos \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.3 `GET /api/v1/cursos/bloqueados` — próximos cursos, con motivo

- **200 OK** → `List<CursoBloqueadoResponse>`: `{id, titulo, portada_url, orden, dia_desbloqueo, program_day_actual}`. `portada_url` viaja **sin firmar** a propósito (la app firma la escalera completa disponibles+bloqueados de una sola vez del lado cliente).
- **Quién puede llamarlo:** cualquier rol, pero si `rol != TRAINEE` → **lista vacía** (nunca error). Para TRAINEE, filtra con `Curso.bloqueadoPorDiaPara`: nunca revela un curso restringido por rol, sin publicar o `RESTRINGIDO` — solo los "por venir" únicamente por día de programa.
- Reemplaza la vieja RPC de Supabase `catalogo_cursos_bloqueados`.

```bash
curl -s http://localhost:8080/api/v1/cursos/bloqueados \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.4 `GET /api/v1/cursos/{id}` — detalle de un curso (árbol completo)

- **Path var:** `id` — es un **string** (clave estilo Skool, no UUID). Vacío/blank → 400.
- **200 OK** → `CursoDetalleResponse` (sin `@JsonNaming`, claves ya en la forma final): `{curso: CursoResponse, contenido: {sueltas: [...], secciones: [...]}, portadaFirmada: string}`. **Ojo:** acá `portadaFirmada` es camelCase (inconsistencia real del wire viejo, preservada a propósito) — distinto de `portada_firmada` (snake_case) en `MiCursoResponse` del §1.2.
  - `LeccionLiteResponse` (dentro de `sueltas`/`secciones[].lecciones`): `{id, curso_id, seccion_id, titulo, orden, video_tipo, video_duracion_ms, tiene_cuerpo, recursos_count, dia_desbloqueo, bloqueada_por_dia, program_day_actual, dias_faltantes}`.
- **Errores:** 404 `"Curso no encontrado: <id>"` si no existe; **403** `"No tienes acceso a este curso"` si `!curso.visibleEnCatalogoPara(rol, programDay)`.

```bash
curl -s http://localhost:8080/api/v1/cursos/curso-1 \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.5 `GET /api/v1/cursos/{id}/secciones` — solo las secciones

Mismo gate que el detalle (404 curso inexistente, 403 sin acceso). **200 OK** → `List<SeccionConLeccionesResponse>`: `{id, curso_id, titulo, orden, dia_desbloqueo, bloqueada_por_dia, program_day_actual, dias_faltantes, lecciones: [...]}`.

### 1.6 `GET /api/v1/cursos/{id}/preview` — motivo de bloqueo (nunca rechaza)

- **200 OK** siempre → `MotivoBloqueoResponse` (camelCase, `@JsonInclude(NON_NULL)`):
  - No bloqueado: `{"locked": false}`.
  - Bloqueado por día: `{"locked": true, "reason": "dia_desbloqueo", "cursoTitulo": "...", "diaDesbloqueo": 10, "programDayActual": 3}`.
  - Curso inexistente, bloqueado por rol, sin publicar o `RESTRINGIDO` → **también** `{"locked": false}` (nunca revela esos motivos).
- El único valor posible de `reason` es el string literal `"dia_desbloqueo"`.

```bash
curl -s http://localhost:8080/api/v1/cursos/curso-1/preview \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.7 `GET /api/v1/lecciones/{id}` — detalle de una lección (rechaza si bloqueada)

- **200 OK** → `LeccionDetalleResponse`: `{leccion: LeccionResponse, recursos: [...]}` (no anida `recursos` dentro de `leccion`).
  - `LeccionResponse` (snake_case): `{id, curso_id, seccion_id, titulo, orden, cuerpo_html, cuerpo_md, video_tipo, video_url, video_miniatura_url, video_duracion_ms, creado_en, actualizado_en}`.
  - `RecursoLeccionResponse` (snake_case): `{id, leccion_id, nombre, url, orden}`.
- **Errores:** 404 `"Leccion no encontrada: <id>"`; 403 `"No tienes acceso a este curso"` (curso padre no accesible); 403 `"Esta sesion todavia no esta disponible"` (sección de la lección bloqueada por día, si la lección pertenece a una sección).

```bash
curl -s http://localhost:8080/api/v1/lecciones/leccion-1 \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.8 `GET /api/v1/lecciones/{id}/preview` — motivo de bloqueo de lección (nunca rechaza)

Mismo shape que §1.6 (`MotivoBloqueoResponse`, reutiliza el DTO de `curso`). Si el bloqueo real es a nivel de curso (rol/no publicado/restringido), delega al mismo criterio de "no revelar" que el preview de curso; si es por día de la sección, expone `{locked:true, reason:"dia_desbloqueo", cursoTitulo: <título de la LECCIÓN, no del curso ni de la sección>, diaDesbloqueo, programDayActual}`.

### 1.9 `POST /api/v1/lecciones/{id}/complete` — marcar completada

- **Body:** ninguno.
- **200 OK** → `CompletarLeccionResponse` (camelCase): `{"leccionId": "...", "completadaEn": "2026-08-26T..."}`.
- Mismos 4 gates que el `GET` de la lección (lección existe, actor activo, curso accesible, sección visible) — **exige acceso vigente antes de dejar completar** (a diferencia del código viejo, que confiaba en RLS). Idempotente: completar dos veces no falla ni duplica.
- **Sin efectos secundarios/eventos** — no suma puntos, no dispara nada hacia `habits`/`points` todavía.

```bash
curl -s -X POST http://localhost:8080/api/v1/lecciones/leccion-1/complete \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.10 `DELETE /api/v1/lecciones/{id}/complete` — descompletar

- **204 NO_CONTENT**, cuerpo vacío. Mismos 4 gates que completar. Idempotente (desmarcar algo no completado no falla). Sin eventos.

```bash
curl -s -X DELETE http://localhost:8080/api/v1/lecciones/leccion-1/complete \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" -w "%{http_code}\n"
```

### 1.11 `GET /api/v1/classroom/clase-diaria` — clase del día real del aprendiz

- **Headers:** `X-Actor-Id`. **Sin query params** — el `programDay` nunca viaja desde el cliente, el servidor lo resuelve internamente.
- **200 OK** → `ClaseDiariaResponse` (camelCase, `@JsonInclude(NON_NULL)`):
  - `{"status": "available", "programDay": 17, "cursoId": "...", "cursoTitulo": "...", "leccionId": "...", "leccionTitulo": "..."}`
  - `{"status": "not_started", "programDay": 0}` (el aprendiz todavía no arrancó el programa, `diaPrograma == 0`).
  - `{"status": "coming_soon", "programDay": 17}` (sin clase para ese día en el rango 1-90).
- **Quién puede llamarlo:** **solo TRAINEE**, con chequeo explícito de rol (no solo de visibilidad de catálogo) — 403 `"Solo un aprendiz tiene clase diaria"` para cualquier otro rol. 404/403 estándar de actor antes de eso.
- Otro 403 posible: `"La clase diaria no esta disponible para tu cuenta"`, si la clase encontrada deja de ser visible en el último chequeo (ej. curso despublicado).
- Selección real: cursos publicados con `dia_desbloqueo <= programDay`; dentro de ellos, la **sección con `dia_desbloqueo` más alto que siga siendo `<= programDay`** (porque desde el día ~15 las secciones representan rangos, no un día puntual); dentro de esa sección, la lección cuyo título matchea `/\bclase\b/i`, o si ninguna matchea, la primera por orden.
- **Solo lectura.** Completarla es el `POST` de abajo.

### 1.11-bis `POST /api/v1/classroom/clase-diaria` — completar la clase del día

- **Body:** `{"leccionId": "...", "resumen": "..."}`. `resumen`: **mínimo 15 y máximo 2000 caracteres** (decisión del dueño del producto, 2026-09-04 — antes el mínimo era 20, espejo del backend viejo, y no había máximo). Los dos límites viven en una sola constante compartida por los dos módulos: `habits.api.CompletarClaseDiariaHabitoUseCase.RESUMEN_MIN_LENGTH`/`RESUMEN_MAX_LENGTH`, que `academy` referencia en vez de repetir el número.
- **200 OK** → `CompletarClaseDiariaResponse` (camelCase): `{"leccionId": "...", "registroHabitoId": "...", "puntosOtorgados": 10}`.
- **Hace DOS escrituras, en este orden** (mismo que el repo viejo, envueltas en una `@Transactional` local): (1) cierra el registro de HOY del hábito de catálogo `DAILY_CLASS` — puntos, racha y ventana de entrega, dominio de `habits`, vía `habits.api.CompletarClaseDiariaHabitoUseCase`; (2) marca la lección como vista en `leccion_progreso` (`CompletarLeccionUseCase`). **Ambos pasos son idempotentes**: repetir el POST devuelve el resultado ya otorgado sin volver a sumar puntos (`EstadoRegistro.COMPLETADO` es terminal).
- **El servidor revalida cuál es la clase de hoy**: si `leccionId` no es la que resuelve el `GET`, responde **403** `"Esta no es la clase diaria de hoy"` — el cliente nunca decide qué lección se completa.
- **409** `"No hay una clase diaria disponible para completar hoy"` si el `GET` no está en `available`. **404** si no hay registro de `DAILY_CLASS` generado para hoy.
- **Sin este POST el hábito NO queda completado**: no hay estado intermedio "completado sin resumen". Si el aprendiz abandona el flujo a mitad de camino, su Clase Diaria sigue `PENDIENTE` y puede reintentarla desde Training.

```bash
curl -s -X POST http://localhost:8080/api/v1/classroom/clase-diaria   -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" -H 'Content-Type: application/json'   -d '{"leccionId":"leccion-1","resumen":"Entendi que la disciplina se construye a diario"}'
```

```bash
curl -s http://localhost:8080/api/v1/classroom/clase-diaria \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.12 `GET /api/v1/academia/recomendacion` — Academia Adaptativa

- **200 OK** → `RecomendacionResponse` (camelCase, `@JsonInclude(NON_NULL)`): `{"available": true, "leccionId": "...", "leccionTitulo": "...", "cursoId": "...", "cursoTitulo": "...", "reason": "..."}` o `{"available": false, "reason": "sin_recomendacion_disponible"}`. `reason` se usa en las dos ramas con sentido distinto: motivo de la IA si `available:true`, código de motivo si `available:false`.
- **Quién puede llamarlo:** **solo TRAINEE** — 403 `"Solo un aprendiz recibe recomendaciones de Academia Adaptativa"` para otro rol.
- **Estado real:** cache-first por día calendario (zona horaria del participante, default `America/Lima`). Sin cache previo, el adaptador de IA es `NoOpRecomendarClaseAdapter`, que **siempre** devuelve vacío — hoy, sin un informe cacheado de antes, este endpoint **siempre** responde `{"available": false, "reason": "sin_recomendacion_disponible"}` (literal, hardcodeado). La generación real vía Gemini es trabajo futuro (Ola 5).

```bash
curl -s http://localhost:8080/api/v1/academia/recomendacion \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

---

## 2. Módulo `chat` — Conversaciones, Mensajes, WebSocket

Prefijo real: **`/api/v1/chat/...`** (no lo confundas con la ruta sin prefijo `chat` que pudiera aparecer en otro documento). Todos los endpoints REST resuelven el actor por `X-Actor-Id`; los servicios (`ConversacionService`/`MensajeService`) exigen `requireActivo` en cada operación: 404 `"Usuario no encontrado: <id>"` si no existe, 403 `"La cuenta esta suspendida"` si `status != ACTIVE`.

El wire habla **inglés** (`type`: `CELL`/`DIRECT`/`GLOBAL` para conversaciones, `TEXT`/`IMAGE`/`AUDIO`/`VIDEO`/`SYSTEM` para mensajes) — la traducción desde el dominio en español vive solo en los DTOs (`ConversacionResponse.toWireTipo`, `MensajeResponse.toWireTipo`, `MensajeController.parseTipoMensaje`), nunca en dominio ni persistencia.

### 2.1 `POST /api/v1/chat/conversations/direct` — crear/obtener conversación directa

- **Headers:** `X-Actor-Id`.
- **Body** (`CrearConversacionDirectaRequest`): **`{"otherUserId": "<uuid>"}`** — el campo se llama literalmente **`otherUserId`**, `@NotBlank`.
- **201 CREATED** → `ConversacionResponse`: `{"id": "...", "type": "DIRECT", "celulaId": null, "nombre": null, "createdAt": "..."}`.
- Es **busca-o-crea**, idempotente: si ya existe una conversación directa entre esos dos usuarios (clave determinística por orden lexicográfico de los dos UUID), la devuelve tal cual en vez de duplicar.
- **Errores:** `IllegalArgumentException("No se puede iniciar una conversacion directa con uno mismo")` → 400, si `otherUserId == actorId`; 404/403 de actor para **ambos** usuarios (el propio y el destino) — si `otherUserId` no existe o está suspendido, también falla.
- **Quién puede llamarlo:** cualquier actor activo, con cualquier otro actor activo — no hay restricción de rol ni relación mentor-aprendiz para abrir una conversación directa.

```bash
curl -s -X POST http://localhost:8080/api/v1/chat/conversations/direct \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"otherUserId": "22222222-2222-2222-2222-222222222222"}'
```

### 2.2 `GET /api/v1/chat/conversations` — mis conversaciones

- **200 OK** → `List<ConversacionResumenResponse>`: `{"conversation": ConversacionResponse, "lastMessage": MensajeResponse|null, "unreadCount": <long>}`, ordenadas por actividad más reciente (último mensaje, o fecha de creación si no tiene mensajes). Resuelto **en lote** (nunca N+1: una consulta para "últimos mensajes" y una para "no leídos", sin importar cuántas conversaciones tenga el actor).

```bash
curl -s http://localhost:8080/api/v1/chat/conversations \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 2.3 `POST /api/v1/chat/conversations/{id}/read` — marcar leído

**Confirmado: es POST, no PUT.** (`@PostMapping("/{id}/read")`, `ConversacionController.java:57`.)

- **Path var:** `id` (UUID de la conversación).
- **Body:** ninguno.
- **200 OK** → `{"id": "<uuid>"}`.
- **Errores:** 404 `"Conversacion no encontrada: <id>"`; **403** `"No sos participante de esta conversacion"` si el actor no es miembro de esa conversación.

```bash
curl -s -X POST http://localhost:8080/api/v1/chat/conversations/33333333-3333-3333-3333-333333333333/read \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 2.4 `POST /api/v1/chat/conversations/{conversationId}/messages` — enviar mensaje

- **Path var:** `conversationId` (UUID).
- **Body** (`EnviarMensajeRequest`):
  ```json
  {
    "type": "TEXT",
    "text": "hola",
    "mediaBucket": null, "mediaPath": null, "mediaMime": null,
    "mediaBytes": null, "mediaDurationSeconds": null,
    "replyToId": null
  }
  ```
  - `type`: `@NotBlank`, valores válidos `TEXT|IMAGE|AUDIO|VIDEO|SYSTEM` — cualquier otro string lanza `IllegalArgumentException("Tipo de mensaje invalido: " + type)` → 400 (parseo a mano en el controller, no un enum de Jackson).
  - Resto de campos: sin `@NotBlank`/`@NotNull` a nivel DTO — la validación real (ej. `SISTEMA` no necesita texto/media, cualquier otro tipo sí; `mediaBucket`/`mediaPath` viajan juntos o ninguno) vive en el dominio (`Mensaje.escribir`), y si se viola lanza `IllegalArgumentException` → 400.
  - `replyToId`: si viene, debe ser un mensaje **de la misma conversación** — si no, `IllegalArgumentException("No se puede responder a un mensaje de otra conversacion")` → 400; si el mensaje no existe, `NoSuchElementException("Mensaje no encontrado: <id>")` → 404.
- **201 CREATED** → `MensajeResponse`: `{"id","conversationId","senderId","type","text","mediaBucket","mediaPath","mediaMime","mediaBytes","mediaDurationSeconds","hidden","replyToId","createdAt"}`.
- **Errores:** 404 conversación inexistente; **403** `"No sos participante de esta conversacion"` si el actor no es miembro.
- Efecto secundario: tras el `COMMIT` de la transacción (no antes), publica el mensaje a Redis Pub/Sub (canal `chat:conversacion:{id}`) para el fan-out en vivo por WebSocket — fire-and-forget, si Redis falla solo se loguea (el mensaje ya quedó durable en Postgres). También marca `ultimo_leido_en` del propio emisor (ya "leyó" lo que acaba de escribir).

```bash
curl -s -X POST http://localhost:8080/api/v1/chat/conversations/33333333-3333-3333-3333-333333333333/messages \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"type": "TEXT", "text": "hola"}'
```

### 2.5 `GET /api/v1/chat/conversations/{conversationId}/messages` — listar mensajes (paginado)

- **Query params:** `cursor` (opcional, `Instant` ISO-8601 como string, parseado a mano — formato inválido → 400 vía `DateTimeParseException`), `limit` (opcional, int, **default 30**, techo real **100** — pedir más se recorta en silencio, `limit<=0` cae al default 30).
- **200 OK** → `MensajesPageResponse`: `{"messages": [MensajeResponse...], "nextCursor": "<instant>|null", "hasMore": bool}`. Paginación **keyset** por `createdAt` descendente (nunca `OFFSET`).
- **Errores:** mismos 404/403 de conversación/pertenencia que el envío.

```bash
curl -s "http://localhost:8080/api/v1/chat/conversations/33333333-3333-3333-3333-333333333333/messages?limit=20" \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 2.6 WebSocket — STOMP en vivo

- **Endpoint de conexión:** `ws://localhost:8080/ws` (STOMP puro sobre WebSocket nativo — `setAllowedOriginPatterns("*")`, **sin SockJS**: `registry.addEndpoint("/ws")` sin `.withSockJS()`).
- **Autorización del handshake** (`ActorHandshakeInterceptor`): igual que REST, exige el header **`X-Actor-Id`** en la request HTTP inicial del handshake (no un query param). Sin header, o con un valor que no es UUID → el handshake se rechaza con **403** antes de completar el upgrade a WebSocket. El UUID capturado queda en los atributos de la sesión (`actorId`), para que las suscripciones posteriores lo usen.
- **Broker:** simple, en memoria, prefijo `/topic` (`registry.enableSimpleBroker("/topic")`), prefijo de aplicación `/app` (`setApplicationDestinationPrefixes("/app")`) — pero **no hay ningún `@MessageMapping`** en el módulo `chat` (confirmado, no hay handler de mensajes entrantes por `/app/...`). **El envío de mensajes es SOLO por REST** (§2.4); el WebSocket es exclusivamente para **recibir** push en vivo.
- **Suscripción:** el cliente STOMP se suscribe a `/topic/conversaciones/{conversacionId}` (UUID). `SubscripcionAutorizadaInterceptor` intercepta cada `SUBSCRIBE` y exige:
  1. El destino debe empezar con `/topic/conversaciones/` — cualquier otro destino → `MessagingException("Destino de suscripcion no permitido")`.
  2. El actor de la sesión debe existir y estar `ACTIVE` — si no, `MessagingException("Cuenta suspendida")` (o `"Actor no encontrado"`).
  3. El actor debe ser **participante** de esa conversación — si no, `MessagingException("No sos participante de esta conversacion")`.
- **Fan-out entre instancias:** el mensaje se persiste primero en Postgres (REST), y **después del commit** se publica a Redis Pub/Sub (`chat:conversacion:{id}`); cada instancia de la app está suscrita a ese canal y reenvía el payload crudo a `/topic/conversaciones/{id}` vía `SimpMessagingTemplate` — así una instancia distinta a la que recibió el `POST` también entrega el mensaje en vivo. El payload del push es un DTO liviano (`MensajeFanoutPayload`, "hay un mensaje nuevo, refrescá"), no el mensaje completo — el cliente sigue usando `GET .../messages` para el contenido paginado.

No hay forma de probar el WebSocket con `curl`; con un cliente STOMP (ej. `wscat` no alcanza porque STOMP tiene su propio framing — usar una librería STOMP real, p. ej. `@stomp/stompjs` en JS o el cliente de `spring-websocket` en un test):

```
CONNECT ws://localhost:8080/ws
  Header HTTP del handshake: X-Actor-Id: 11111111-1111-1111-1111-111111111111

SUBSCRIBE
destination:/topic/conversaciones/33333333-3333-3333-3333-333333333333
id:sub-0
```

---

## 3. Módulo `notifications` — Bandeja, Preferencias, Tokens Push

Tres controllers, todos autoservicio estricto (nunca la bandeja/preferencias/token de otro usuario — no hay ningún parámetro para apuntar a un usuario distinto del actor). Guard compartido `ActorNotificacionesGuard`: actor inexistente → 404 `"Usuario no encontrado: <id>"`; suspendido → 403 `"Cuenta suspendida"`. La emisión interna (`EmitirNotificacionUseCase`, usada solo por los listeners de eventos, nunca por HTTP) **no** pasa por este guard — un suspendido igual acumula notificaciones en su bandeja aunque no pueda leerla.

### 3.1 `GET /api/v1/notifications` — bandeja

- **200 OK** → `{"items": [NotificacionResponse...]}`. Sin paginación real — tope fijo: últimos **90 días**, máximo **100** filas, más nueva primero.
- `NotificacionResponse`: `{"id": <number>, "type": "<TipoNotificacion>", "title": "...", "body": "...", "createdAt": "...", "readAt": "...|null", "route": "...|null"}`. **`id` es numérico** (bigint), no string.

```bash
curl -s http://localhost:8080/api/v1/notifications \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 3.2 `PUT /api/v1/notifications/{id}/read` — marcar leída

**Confirmado: es PUT.** (`@PutMapping("/{id}/read")`.)

- **Path var:** `id` (número, bigint — no UUID).
- **200 OK** → `{"id": <number>, "readAt": "<instant>"}`.
- **404**, mensaje genérico, tanto si la notificación no existe **como** si es de otro usuario — a propósito, para no revelar cuáles ids existen. Marcar una ya leída es **idempotente** (sigue devolviendo 200).

```bash
curl -s -X PUT http://localhost:8080/api/v1/notifications/42/read \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 3.3 `PUT /api/v1/notifications/read-all` — marcar todas leídas

**Confirmado: PUT.**

- **200 OK** → `{"updated": <int>}`. Idempotente: sin no-leídas, `updated:0`, sigue siendo 200.

```bash
curl -s -X PUT http://localhost:8080/api/v1/notifications/read-all \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 3.4 `GET /api/v1/notification-preferences` — consultar preferencias

- **200 OK** → `{"preferences": [{"type": "...", "enabled": true}, ...]}` — **es una LISTA de objetos, no un mapa**, siempre con los **13** valores del enum completos (default `enabled:true` para los que no tienen fila propia en BD).

```bash
curl -s http://localhost:8080/api/v1/notification-preferences \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 3.5 `PATCH /api/v1/notification-preferences` — actualizar preferencias

- **Body** (`ActualizarPreferenciasRequest`):
  ```json
  { "preferences": [ { "type": "RECORDATORIO_HABITO", "enabled": false } ] }
  ```
  - `preferences`: `@NotEmpty`, lista no vacía obligatoria.
  - `type`: `@NotNull`, debe matchear literal un valor de `TipoNotificacion` (§3.7) — si no, `Enum.valueOf` lanza `IllegalArgumentException` → 400 (mensaje default de Java, no personalizado).
  - `enabled`: `boolean` primitivo.
- **200 OK** → mismo shape que el GET, recalculado con los 13 tipos.

```bash
curl -s -X PATCH http://localhost:8080/api/v1/notification-preferences \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"preferences": [{"type": "MENSAJE_CHAT", "enabled": false}]}'
```

### 3.6 `POST /api/v1/push-tokens` — registrar token push

- **Body** (`RegistrarTokenPushRequest`): `{"token": "<string, obligatorio, max 300>", "platform": "IOS"|"ANDROID"|null}`. Solo esos dos campos — **no hay `deviceId`**.
  - `platform` es opcional; el controller normaliza con `.toUpperCase()` antes de convertir al enum, así que `"ios"`/`"android"` en minúscula también funcionan en la práctica (aunque un comentario del código sugiere que debería mandarse ya en mayúsculas — tratalo como aceptado igual, verificado en el propio controller).
  - Un valor que no matchea ninguna de las dos (ej. `"web"`) → `IllegalArgumentException` → 400.
- **200 OK** (no 201, pese a ser una creación/upsert — sin `@ResponseStatus`) → `{"id": "<uuid>"}`.
- `usuarioId` nunca sale del body, siempre del actor (`X-Actor-Id`). `token` es **globalmente único**: registrar un token ya existente **reasigna el dueño** (útil para dispositivo reinstalado/reasignado), nunca duplica fila.

```bash
curl -s -X POST http://localhost:8080/api/v1/push-tokens \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"token": "ExponentPushToken[xxxxxxx]", "platform": "ANDROID"}'
```

### 3.7 Enum `TipoNotificacion` — los 13 valores exactos (español)

```
RECORDATORIO_HABITO, RECORDATORIO_ROCA, RECORDATORIO_RADAR, MENSAJE_MENTOR,
ANUNCIO_SISTEMA, RESUMEN_SEMANAL, LOGRO_DESBLOQUEADO, HITO_PROGRAMA,
MENSAJE_CHAT, TICKET_RESPONDIDO, TICKET_ABIERTO, SANTUARIO_ROTO,
HABITO_PERSONAL_MODIFICADO
```

`PlataformaPush`: `IOS`, `ANDROID`.

**Quién dispara cada tipo hoy** (vía eventos de dominio, `@ApplicationModuleListener`, asíncrono post-commit — no hay endpoint que emita a mano):
- `HabitoCompletadoEvent` (de `habits`) → `LOGRO_DESBLOQUEADO`.
- `RachaCompletadaEvent` (de `habits`) → `LOGRO_DESBLOQUEADO`.
- `RocaCompletadaEvent` (de `rocks`) → `HITO_PROGRAMA`.
- `SantuarioRotoEvent` (de `habits`) → `SANTUARIO_ROTO`.
- Los 9 tipos restantes no tienen listener dentro de `notifications` — quedan sin confirmar si algún otro módulo los emite directamente.

---

## 4. Módulo `support` — Tickets a Mentor y Tickets de Soporte Técnico

**Son dos familias completamente distintas, con su propio controller normal + admin cada una.** No comparten dominio ni reglas.

### 4.1 Familia 1 — Tickets a mentor (`/api/v1/tickets`, admin en `/api/v1/admin/tickets`)

#### 4.1.1 `POST /api/v1/tickets` — abrir ticket a mentor

- **Body** (`AbrirTicketMentorRequest`, los 3 campos `@NotBlank @Size(max=2000)`):
  ```json
  { "blockDescription": "...", "attemptedSolutions": "...", "smartGoalImpact": "..." }
  ```
- **201 CREATED** → `TicketMentorResponse`: `{"id","traineeProfileId","blockDescription","attemptedSolutions","smartGoalImpact","status":"OPEN","mentorAnswer":null,"answeredAt":null,"savedToLibrary":false,"createdAt"}`. **`traineeProfileId` conserva el nombre viejo pero ahora es literalmente el `userId` del aprendiz**, no un id de perfil propio — riesgo de migración si algún cliente lo usa para algo más que mostrarlo.
- **Quién puede llamarlo:** **solo TRAINEE**, activo. 403 `"Solo un aprendiz puede abrir un ticket"` para otro rol; 403 `"La cuenta esta suspendida"` para un TRAINEE suspendido (a diferencia de soporte técnico, ver §4.2).
- Efecto secundario: publica `TicketMentorAbiertoEvent` (evento de dominio).

```bash
curl -s -X POST http://localhost:8080/api/v1/tickets \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"blockDescription":"No entiendo la roca de esta semana","attemptedSolutions":"Relei el material dos veces","smartGoalImpact":"Me atrasa el objetivo de octubre"}'
```

#### 4.1.2 `GET /api/v1/tickets` — listar tickets (propios o de la plataforma según rol)

- **Query:** `cursor` (opcional, ISO-8601; inválido → `IllegalArgumentException("cursor invalido, se espera ISO-8601: <valor>")` → 400).
- **200 OK** → `{"tickets": [TicketMentorResponse...], "nextCursor": "..."|null}`. Página fija de **30**.
- **Quién puede llamarlo:** **TRAINEE** ve solo los suyos; **MENTOR** ve **todos los tickets de la plataforma** (no solo los de su célula/asignados — deuda documentada, sin scope de célula todavía). Cualquier otro rol → 403 `"Solo un aprendiz o un mentor pueden listar estos tickets"`.

```bash
curl -s http://localhost:8080/api/v1/tickets \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

#### 4.1.3 `POST /api/v1/tickets/{id}/answer` — responder (solo el mentor asignado)

- **Body** (`ResponderTicketMentorRequest`): `{"mentorAnswer": "<string, @NotBlank, max 4000>"}`.
- **200 OK** → `TicketMentorResponse` actualizado (`status:"ANSWERED"`, `answeredAt` poblado).
- **Autorización real — la trampa del enunciado:** tener rol `MENTOR` **no alcanza**. `TicketMentorService.responder()` primero exige rol MENTOR (403 `"Solo el mentor asignado puede responder un ticket"` si no), y **después** resuelve la asignación real vía **`ParticipacionProgramaFinder`** (puerto de `users/api`, no propio de `support`): compara `actorId` contra `ParticipacionPrograma.mentorId()` del aprendiz dueño del ticket. Si no coincide → **403** `"Solo el mentor asignado a ese aprendiz puede operar su ticket"`. Este es el fix del bug histórico E-38 (antes solo se chequeaba el rol, cualquier MENTOR podía responder tickets ajenos).
- Responder un ticket ya `ANSWERED` → `IllegalStateException("El ticket ya fue respondido: RESPONDIDO")` → **409**.
- Efecto secundario: publica `TicketMentorRespondidoEvent`.

```bash
curl -s -X POST http://localhost:8080/api/v1/tickets/33333333-3333-3333-3333-333333333333/answer \
  -H "X-Actor-Id: bbbbbbbb-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"mentorAnswer": "Probá dividir la roca en 3 pasos chicos, avisame cómo te fue"}'
```

#### 4.1.4 `POST /api/v1/tickets/{id}/save-to-library` — guardar en biblioteca

- **Sin body.** **200 OK** → `TicketMentorResponse` (`savedToLibrary:true`).
- Misma autorización que responder: rol MENTOR **+** mentor asignado (mismo mensaje de error, `"Solo el mentor asignado a ese aprendiz puede operar su ticket"`).
- Exige que el ticket ya esté `RESPONDIDO` — si no, `IllegalStateException("El ticket todavia no tiene respuesta")` → **409**.

```bash
curl -s -X POST http://localhost:8080/api/v1/tickets/33333333-3333-3333-3333-333333333333/save-to-library \
  -H "X-Actor-Id: bbbbbbbb-1111-1111-1111-111111111111"
```

#### 4.1.5 `GET /api/v1/tickets/library?q=` — buscar en la biblioteca

- **Query obligatorio:** `q` (sin `required=false` → falta → `MissingServletRequestParameterException` → 400 `"Falta el parametro obligatorio 'q'"`).
- **200 OK** → `{"results": ["Pregunta: ...\nRespuesta: ...", ...]}` — strings ya formateados, **hasta 5 resultados** (límite fijo, no configurable por el cliente).
- **Es full-text search nativo de PostgreSQL** (`to_tsvector('spanish', ...) @@ plainto_tsquery('spanish', :q)`, ordenado por `ts_rank`), **no** una búsqueda por categoría ni por palabra clave literal, y **no** usa RAG/embeddings. Filtra solo tickets con `guardado_en_biblioteca = true`.
- **Quién puede llamarlo:** TRAINEE o MENTOR. 403 `"Solo un aprendiz o un mentor pueden buscar en la biblioteca"` para otro rol (ej. ADMIN).

```bash
curl -s "http://localhost:8080/api/v1/tickets/library?q=roca%20semanal" \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

#### 4.1.6 `GET /api/v1/admin/tickets` — vista de plataforma (solo lectura)

- **Query:** `cursor` opcional, mismo formato/página de 30.
- **200 OK** → mismo shape que §4.1.2.
- **Quién puede llamarlo:** **MENTOR_LEAD, ADMIN o ALCHEMIST únicamente** — 403 `"Solo MENTOR_LEAD/ADMIN/ALCHEMIST ven todos los tickets"` para cualquier otro (incluido MENTOR simple).

```bash
curl -s http://localhost:8080/api/v1/admin/tickets \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001"
```

### 4.2 Familia 2 — Tickets de soporte técnico (`/api/v1/support-tickets`, admin en `/api/v1/admin/support-tickets`)

#### 4.2.1 `POST /api/v1/support-tickets` — abrir ticket de soporte

- **Body** (`AbrirTicketSoporteRequest`):
  ```json
  {
    "category": "TECHNICAL", "subject": "No puedo subir audio", "message": "Se traba al grabar...",
    "clientLog": null, "attachmentBucket": null, "attachmentPath": null, "attachmentUrl": null
  }
  ```
  - `category`: **opcional**; valores válidos en inglés `TECHNICAL|ACCOUNT|PROGRAM|BILLING|OTHER`; ausente/blank → default `OTHER` **en el servicio**; valor no reconocido → 400 `"category invalida: <valor>"`.
  - `subject`: `@NotBlank @Size(max=200)`.
  - `message`: `@NotBlank @Size(min=10, max=4000)` — además el dominio revalida el mínimo tras `trim()`.
  - `clientLog`: opcional, `@Size(max=4000)`.
  - Adjunto: acepta **dos formas** — cliente nuevo manda `attachmentBucket`+`attachmentPath` (obtenidos vía §4.2.3); cliente viejo/publicado manda solo `attachmentUrl` (se extrae bucket/ruta parseando esa URL, asumiendo el bucket heredado `Evidence`).
- **201 CREATED** → `TicketSoporteResponse`.
- **Quién puede llamarlo — la trampa central del módulo: CUALQUIER actor que exista, INCLUIDO uno SUSPENDIDO.** `TicketSoporteService.requireActorExiste()` solo verifica existencia, **nunca** el estado. Es una excepción consciente y documentada en el propio código (contradice la regla general de "un SUSPENDED recibe 403 en todo"): **soporte es el único canal que le queda a una cuenta suspendida para reclamar su propia suspensión** — bloquearlo la dejaría sin forma de pedir ayuda. Hay tests dedicados a fijar esta regla exactamente para que nadie la "corrija" por simetría con el resto del sistema.

```bash
curl -s -X POST http://localhost:8080/api/v1/support-tickets \
  -H "X-Actor-Id: 9c675442-6abb-46f3-8971-fe3bf0208127" \
  -H "Content-Type: application/json" \
  -d '{"subject":"Mi cuenta esta suspendida","message":"No entiendo por que me suspendieron, necesito ayuda"}'
```
*(el ejemplo usa un actor suspendido a propósito: este endpoint debe darle 201, no 403).*

#### 4.2.2 `GET /api/v1/support-tickets` — mis tickets

- **Sin paginación** — devuelve la lista completa, ordenada por `creadoEn desc`.
- **200 OK** → `List<TicketSoporteResponse>`.
- **Mismo criterio que abrir: cualquier actor que exista, incluido SUSPENDIDO.**

```bash
curl -s http://localhost:8080/api/v1/support-tickets \
  -H "X-Actor-Id: 9c675442-6abb-46f3-8971-fe3bf0208127"
```

#### 4.2.3 `POST /api/v1/support-tickets/attachments/upload-url` — pedir URL prefirmada

- **Body** (`SolicitarUrlAdjuntoRequest`): `{"fileName": "<string, @NotBlank>", "contentType": "<string, @NotBlank>"}`.
- **200 OK** → `UrlAdjuntoResponse`: `{"bucket": "renaser-files", "path": "soporte/<usuarioId>/<epochMillis><ext>", "uploadUrl": "<URL firmada, válida 10 min>"}`.
- **Es un modelo de URL prefirmada (subida PUT directa del cliente), NO una subida al backend.** Con el estado actual de infraestructura, el adaptador real (`AlmacenamientoPort`) es `NoOpAlmacenamientoAdapter` — sin credenciales AWS S3 (D-34): la URL devuelta es un placeholder (`about:blank#pendiente-s3/...`), **no funcional contra un bucket real todavía**.
- **Quién puede llamarlo:** cualquier actor que exista, incluido suspendido (mismo criterio que abrir/listar).

```bash
curl -s -X POST http://localhost:8080/api/v1/support-tickets/attachments/upload-url \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"fileName": "log.txt", "contentType": "text/plain"}'
```

#### 4.2.4 `GET /api/v1/admin/support-tickets?status=` — inbox de soporte (admin)

- **Query:** `status` opcional, inglés `OPEN|RESOLVED`; valor no reconocido → 400 `"status invalido: <valor>"`; ausente = sin filtrar.
- **200 OK** → `List<TicketSoporteResponse>`.
- **Quién puede llamarlo:** **ADMIN o ALCHEMIST, y activo** — acá **sí** se exige `requireActorActivo` (a diferencia del lado aprendiz). Un ADMIN suspendido **no** puede administrar. 403 `"Solo ADMIN/ALCHEMIST administran tickets de soporte"` para MENTOR/TRAINEE (un MENTOR **no tiene ningún alcance** en soporte técnico, a propósito — es un dominio completamente distinto del de tickets a mentor).

```bash
curl -s "http://localhost:8080/api/v1/admin/support-tickets?status=OPEN" \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001"
```

#### 4.2.5 `POST /api/v1/admin/support-tickets/{id}/resolve` — resolver

- **Body opcional** (`ResolverTicketSoporteRequest`): `{"adminNotes": "<string, opcional, max 4000>"}` — puede omitirse el body por completo.
- **200 OK** → `TicketSoporteResponse` (`status:"RESOLVED"`).
- **Idempotente:** resolver un ticket ya `RESOLVED` no falla ni cambia nada, simplemente devuelve el ticket tal cual.
- Mismo gate que §4.2.4 (ADMIN/ALCHEMIST activo).
- 404 `"Ticket de soporte no encontrado: <id>"` si no existe.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/support-tickets/44444444-4444-4444-4444-444444444444/resolve \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{"adminNotes": "Reseteamos el token de la app, resuelto"}'
```

### 4.3 Wire de enums de `support` (dominio español → HTTP inglés)

- `EstadoTicketMentor`: `ABIERTO→"OPEN"`, `RESPONDIDO→"ANSWERED"`.
- `EstadoTicketSoporte`: `ABIERTO→"OPEN"`, `RESUELTO→"RESOLVED"`.
- `CategoriaSoporte`: `TECNICO→"TECHNICAL"`, `CUENTA→"ACCOUNT"`, `PROGRAMA→"PROGRAM"`, `FACTURACION→"BILLING"`, `OTRO→"OTHER"`.

---

## 5. Módulo `rag` — RenasIA y Espejo de Sombra

Prefijos confirmados: `/api/v1/admin/conocimiento`, `/api/v1/renasia/mensajes`, `/api/v1/espejo-sombra`.

### 5.1 `POST /api/v1/admin/conocimiento` — indexar chunk de conocimiento

- **Body** (`IndexarConocimientoRequest`):
  ```json
  {
    "tipoFuente": "LECCION", "clase": "habitos", "documentoId": "doc-1",
    "leccionId": null, "contenido": "texto del chunk...", "metadatos": {"origen": "manual"}
  }
  ```
  - `tipoFuente`: `@NotBlank`. `contenido`: `@NotBlank`. Resto (`clase`, `documentoId`, `leccionId`, `metadatos`) opcionales; `metadatos` ausente se normaliza a `{}`.
- **200 OK** → `{"id": "<uuid>"}`.
- **Quién puede llamarlo:** **solo ADMIN o ALCHEMIST**. 403 `"Solo ADMIN/ALCHEMIST indexan conocimiento"` para cualquier otro rol; 403 `"Cuenta suspendida"`; 404 `"Actor no encontrado: <id>"`.
- El `EmbeddingPort` real (Gemini `text-embedding-004`, 768 dimensiones) todavía no tiene credenciales — hoy usa `NoOpEmbeddingAdapter` (vector de 768 ceros), así que el chunk se guarda pero sin similaridad semántica real todavía.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/conocimiento \
  -H "X-Actor-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{"tipoFuente":"LECCION","clase":"habitos","documentoId":"doc-test-1","contenido":"El Santuario es el bloqueo de telefono nocturno."}'
```

### 5.2 `POST /api/v1/renasia/mensajes` — preguntarle a RenasIA (streaming SSE real)

**Headers:** `X-Actor-Id` obligatorio. No hace falta mandar `Accept: text/event-stream` — el streaming lo fuerza el propio endpoint (`produces = MediaType.TEXT_EVENT_STREAM_VALUE`).

**Body** (`PreguntarRenasiaRequest`): `{"question": "<string, @NotBlank>"}`.

**200 OK**, `Content-Type: text/event-stream`. Implementación real: un `@RestController` de **Spring MVC clásico** (no WebFlux) que devuelve `Flux<String>` (Reactor) — Spring MVC lo adapta a streaming sobre `HttpServletResponse` vía su `ReactiveTypeHandler`. Cada elemento del `Flux` se emite como una línea `data:<contenido>` plana (sin `event:`/`id:`/`retry:`, no son `ServerSentEvent` tipados), y el stream simplemente termina cuando el `Flux` completa (sin evento de cierre tipo `data:[DONE]`).

**Con el adaptador NoOp actual (sin credenciales Gemini, D-39): un único evento `data:`**, con el mensaje fijo `Renasia todavia no esta disponible: faltan credenciales de IA por configurar (D-39).` — **esto no es representativo del comportamiento final**: con Gemini real conectado, `ChatClient...stream().content()` emitiría **tokens sueltos en múltiples eventos `data:`**, no un mensaje completo de una vez.

- **Cuota diaria — valor real confirmado en `application.yaml`:** `renaser.renasia.limite-diario`, **default 25**, sobreescribible por la variable de entorno `RENASIA_LIMITE_DIARIO`. Se cuenta en **Redis** (clave `renasia:cuota:{usuarioId}:{fecha}`, `INCR` atómico, TTL hasta medianoche UTC) — no en Postgres. El mensaje número 25 pasa, el 26 rebota. Si Redis falla, el adaptador **no bloquea** (asume permitido — es protección de abuso, no fuente de verdad de negocio).
- **Al superar la cuota:** **429**, `{"message": "Se alcanzo el limite diario de mensajes a Renasia"}`.
- **Quién puede llamarlo:** cualquier actor activo, **sin chequeo de rol** (a diferencia de indexar conocimiento). 404 `"Usuario no encontrado: <id>"`; 403 `"La cuenta esta suspendida"`.
- **Nunca se loguea la pregunta ni la respuesta** (dato personal).

```bash
curl -N -s -X POST http://localhost:8080/api/v1/renasia/mensajes \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"question": "que es el Pacto de Sangre?"}'
```

### 5.3 `GET /api/v1/renasia/mensajes` — historial de la conversación

- **Query:** `cursor` (opcional, `Instant` ISO-8601, string parseado a mano — inválido → 400 formato fecha estándar); `limit` (opcional, int, **default 30**, techo real 100 — pedir más se recorta en silencio).
- **200 OK** → `{"messages": [MensajeRenasiaResponse...], "nextCursor": "...", "hasMore": bool}`.
  - `MensajeRenasiaResponse`: `{"id","role":"USER"|"ASSISTANT","content","sourceLessonIds":[...],"createdAt"}` — `role` traducido del dominio español (`USUARIO`/`ASISTENTE`) al wire en inglés.
- Paginación keyset por `createdAt`, nunca `OFFSET`.
- Mismos 404/403 de actor que preguntar. Sin chequeo de rol.

```bash
curl -s "http://localhost:8080/api/v1/renasia/mensajes?limit=10" \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 5.4 `GET /api/v1/espejo-sombra?participanteId=` — listar informes semanales

- **Query:** `participanteId` (opcional, tipo `UUID` nativo en la firma — formato inválido → 400 `"El valor de 'participanteId' no tiene el formato esperado"`). Sin el param, se listan los informes **propios**.
- **200 OK** → `List<InformeEspejoSombraResponse>` (array, sin envolver, sin paginación): `{"id","participanteId","semanaInicio":"2026-08-17","cantidadEntradas","patronDominante","pctPasado","pctPresente","pctFuturo","insight","preguntasConfrontacion":["..."],"creadoEn"}`. Lista vacía `[]` si no hay informes — **nunca 404 por ausencia de datos**.
- **Visibilidad real** (`EspejoSombraService.requireVisibilidad`, en este orden):
  1. Actor debe existir y estar activo (404/403 estándar).
  2. Si `rol ∈ {ADMIN, ALCHEMIST}` → permitido.
  3. Si `participanteId == actorId` (es el propio) → permitido.
  4. Si `rol == MENTOR` **y** es el **mentor asignado** a ese participante (verificado contra `ParticipacionProgramaFinder.mentorId`, no contra ningún campo suelto) → permitido.
  5. Si no → **403** `"No tenes visibilidad sobre los informes de ese participante"` — mismo mensaje tanto para un mentor no asignado como para un tercero sin relación (intencional, no se puede distinguir un caso del otro desde afuera).
  - **Nota de gap:** el chequeo usa literalmente `rol == MENTOR`, no incluye `MENTOR_LEAD` — un `MENTOR_LEAD` que fuera mentor asignado de alguien caería también al 403.

```bash
# Propios
curl -s http://localhost:8080/api/v1/espejo-sombra \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"

# Mentor consultando a su aprendiz asignado
curl -s "http://localhost:8080/api/v1/espejo-sombra?participanteId=11111111-1111-1111-1111-111111111111" \
  -H "X-Actor-Id: bbbbbbbb-1111-1111-1111-111111111111"
```

### 5.5 `GET /api/v1/espejo-sombra/{id}` — un informe puntual

- **Path var:** `id` (UUID; formato inválido → 400).
- **200 OK** → mismo shape que un elemento del listado.
- **Por qué 403 y no 404 para un mentor sin relación (la trampa central de este endpoint):** el código (`EspejoSombraService.porId`) resuelve **primero la existencia** del informe (`loadInformePort.byId` → si no existe, **404** `"Informe no encontrado: <id>"`) y **recién después** la visibilidad (mismo método de §5.4). El efecto observable es: para un informe que **sí existe**, cualquier tercero sin relación recibe siempre **403** con el mismo mensaje genérico — nunca un 404 que delataría que el informe existe. Solo un UUID que no corresponde a ningún informe real da 404 (con ese id en el mensaje). Es el mismo criterio anti-enumeración que corrigió el bug histórico E-30/E-42.
- **No existe ningún endpoint para generar un informe a demanda** — nace exclusivamente del scheduler (§5.6).

```bash
curl -s http://localhost:8080/api/v1/espejo-sombra/632fed92-6b43-425f-b88a-fe0753e0acbd \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 5.6 Scheduler — generación semanal del Espejo de Sombra (no es HTTP)

- `@Scheduled(cron = "0 0 3 * * MON", zone = "UTC")` → **todos los lunes a las 03:00 UTC**, después de que ya cerró por completo la semana anterior.
- Genera el informe de la **semana pasada completa** (lunes a domingo previo), para todos los participantes inscritos activos.
- Idempotente por semana (`UNIQUE` en la tabla): si el informe de esa semana ya existe, no se regenera. Si la semana no tiene entradas de diario, no genera nada. Con el adaptador de IA NoOp actual (sin credenciales Gemini), tampoco genera nada — solo loguea.
- Resiliente por participante: si uno falla, se loguea y el barrido sigue con el resto.

### 5.7 Otros detalles transversales de `rag`

- Todos los errores de `rag` pasan por el mismo `GlobalExceptionHandler` global (§0.2) — no hay manejo propio del módulo.
- `UserId.of` valida formato UUID del `X-Actor-Id` en los tres controllers igual que en el resto del backend.
- Roles reales usados en las comparaciones: `UserRole` tiene 5 valores (`ALCHEMIST, ADMIN, MENTOR_LEAD, MENTOR, TRAINEE`); `UserStatus` tiene 2 (`ACTIVE, SUSPENDED`).

---

## 6. Flujos completos para probar

### 6.1 Academia: catálogo → bloqueado y su motivo → curso accesible → secciones → completar lección → clase diaria

1. `GET /api/v1/cursos` (actor TRAINEE) → tomá un `id` de un curso **accesible** de la respuesta (`curso.id`) para el paso 3, y anotá un `curso_id`/`dia_desbloqueo` de un curso que **no** aparezca ahí (o mirá `/bloqueados`) para el paso 2.
2. `GET /api/v1/cursos/bloqueados` → elegí un `id` de esta lista (curso bloqueado por día) y llamá `GET /api/v1/cursos/{id}/preview` con ese `id` → esperá `{"locked":true,"reason":"dia_desbloqueo",...}`.
3. `GET /api/v1/cursos/{id}` con el `id` accesible del paso 1 → de `contenido.secciones[].lecciones[]` (o `contenido.sueltas[]`) tomá un `leccion_id` de una lección **no** bloqueada (`bloqueada_por_dia:false` en su sección, o suelta).
4. `GET /api/v1/cursos/{id}/secciones` (mismo `id`) → confirmá que la sección de la lección elegida aparece con `bloqueada_por_dia:false`.
5. `POST /api/v1/lecciones/{leccionId}/complete` (con el `leccionId` del paso 3) → esperá 200 con `completadaEn` poblado.
6. `GET /api/v1/cursos` de nuevo → el `progreso.completadas` del curso debería subir en 1 respecto al paso 1.
7. `GET /api/v1/classroom/clase-diaria` → usa el `programDay` real del actor (no pasa nada por query); si `status:"available"`, anotá `leccionId` para inspeccionar con `GET /api/v1/lecciones/{leccionId}`.

### 6.2 Chat: crear conversación directa → enviar mensaje → el otro lo lista y marca leído → suscribirse por WebSocket

1. `POST /api/v1/chat/conversations/direct` (actor A, `{"otherUserId": "<uuid de B>"}`) → tomá `id` de la respuesta (`conversationId`) para todos los pasos siguientes.
2. `POST /api/v1/chat/conversations/{conversationId}/messages` (actor A, `{"type":"TEXT","text":"hola"}`) → tomá el `id` del mensaje devuelto si vas a probar `replyToId` después.
3. `GET /api/v1/chat/conversations` (actor B) → confirmá que la conversación aparece con `unreadCount:1` y `lastMessage.text:"hola"`.
4. `POST /api/v1/chat/conversations/{conversationId}/read` (actor B, mismo `id` del paso 1) → después, repetí el paso 3 y confirmá `unreadCount:0`.
5. (Opcional) Conectate por WebSocket a `/ws` con header `X-Actor-Id: <uuid de B>` en el handshake, suscribite a `/topic/conversaciones/{conversationId}` (mismo `id` del paso 1), y desde otra sesión repetí el paso 2 (actor A envía otro mensaje) — deberías recibir el push en el socket de B.

### 6.3 Soporte: aprendiz abre ticket a su mentor → el mentor asignado responde → lo guarda en biblioteca → buscar en la biblioteca

1. `POST /api/v1/tickets` (actor TRAINEE) → tomá `id` del ticket devuelto.
2. `POST /api/v1/tickets/{id}/answer` (actor = el **mentor asignado** a ese aprendiz — confirmalo primero contra `users`/`academy`; un mentor cualquiera da 403) con el `id` del paso 1 → confirmá `status:"ANSWERED"`.
3. `POST /api/v1/tickets/{id}/save-to-library` (mismo mentor asignado, mismo `id`) → confirmá `savedToLibrary:true`.
4. `GET /api/v1/tickets/library?q=<alguna palabra del blockDescription original>` (actor TRAINEE o el mismo mentor) → debería aparecer un resultado `"Pregunta: ...\nRespuesta: ..."` con el contenido del ticket guardado.

### 6.4 RenasIA: indexar conocimiento como admin → preguntar (streaming) → ver el historial → agotar la cuota y ver el 429

1. `POST /api/v1/admin/conocimiento` (actor ADMIN o ALCHEMIST) → tomá el `id` del chunk devuelto (opcional, solo para confirmar que se creó).
2. `POST /api/v1/renasia/mensajes` (`curl -N`, actor cualquiera activo, `{"question":"..."}`) → consumí el stream `data:`.
3. `GET /api/v1/renasia/mensajes` (mismo actor) → confirmá que aparecen el mensaje de usuario y la respuesta del asistente del paso 2, más reciente primero.
4. Repetí el paso 2 hasta 25 veces en total en el mismo día (para el mismo actor) → el mensaje 25 todavía responde 200; el mensaje 26 responde **429** `{"message":"Se alcanzo el limite diario de mensajes a Renasia"}`.

### 6.5 Espejo de Sombra: listar propios; probar visibilidad mentor asignado vs no asignado

1. `GET /api/v1/espejo-sombra` (actor TRAINEE, sin `participanteId`) → si no hay informes todavía, esperá `[]` (no es un error — el informe nace solo del scheduler semanal de los lunes 03:00 UTC, no hay forma de generarlo a demanda vía API).
2. Con un informe ya existente (`id` conocido, por ejemplo insertado como fixture de prueba o generado por el scheduler): `GET /api/v1/espejo-sombra/{id}` con el actor **mentor asignado** al participante dueño del informe → 200 con el informe completo.
3. Mismo `id`, con un actor **mentor NO asignado** (o un aprendiz sin relación) → **403** `"No tenes visibilidad sobre los informes de ese participante"` (nunca 404, aunque el informe exista).
4. Un `id` al azar que no corresponde a ningún informe real → **404** `"Informe no encontrado: <id>"` — confirmá que el mensaje/código es distinto del paso 3, para verificar que el sistema no revela existencia a terceros sin relación pero sí distingue "no existe en absoluto".

---

## 7. Puntos marcados explícitamente como "sin confirmar"

- **Notifications:** no se confirmó, solo con los archivos de este alcance, si algún módulo fuera de los 4 listeners de `notifications` invoca `EmitirNotificacionUseCase` para los 9 tipos de `TipoNotificacion` sin listener propio (`RECORDATORIO_HABITO`, `RECORDATORIO_ROCA`, `RECORDATORIO_RADAR`, `MENSAJE_MENTOR`, `ANUNCIO_SISTEMA`, `RESUMEN_SEMANAL`, `MENSAJE_CHAT`, `TICKET_RESPONDIDO`, `TICKET_ABIERTO`, `HABITO_PERSONAL_MODIFICADO`).
- **Support:** no hay tests de integración HTTP (`@SpringBootTest`/`MockMvc`) para ninguno de los 4 controllers — la autorización descrita acá está verificada a nivel de servicio (unit tests con fakes), no de mapeo HTTP real end-to-end. Tampoco se confirmó el contenido de `docs/FEATURE_SUPPORT.md` citado en comentarios de test (no se leyó ese documento).
- **RAG:** el chequeo de mentor asignado en Espejo de Sombra usa literalmente `rol == MENTOR`; no se confirmó si existe algún escenario de negocio donde `MENTOR_LEAD` deba actuar como mentor asignado directo (hoy, si lo fuera, caería al 403 igual que un tercero).
- **Academia:** no se encontraron tests de integración HTTP (`MockMvc`/`TestRestTemplate`) para los 4 controllers de `academy` — el 403/404 y el formato exacto del JSON están verificados contra el código y contra tests de servicio (Mockito), no contra Postgres real a nivel HTTP.
- **General:** ninguno de los 5 módulos tiene `@RequiresPermission`/`@PublicEndpoint` ni el test de reflexión que exige `CLAUDE.md` §0.3 — ese mecanismo no existe todavía en el backend, confirmado por ausencia total en el código (no es un hueco de este alcance en particular).
- **Puerto del servidor:** no hay `server.port` explícito en `application.yaml` — se asume el default de Spring Boot (8080). Confirmalo contra tu propio `application-local.yaml`/variable de entorno antes de correr los `curl` de este documento si tu entorno lo cambia.
