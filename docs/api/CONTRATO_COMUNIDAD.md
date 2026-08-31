# Contrato REST — `community` y `calendar`

Generado leyendo el código real de:
- `src/main/java/com/renaser/os/community/infrastructure/adapter/in/rest/**` (controllers, DTOs)
- `src/main/java/com/renaser/os/community/application/services/**` (reglas de autorización)
- `src/main/java/com/renaser/os/community/domain/model/**` (invariantes, mensajes de error exactos)
- `src/main/java/com/renaser/os/calendar/infrastructure/adapter/in/rest/**`
- `src/main/java/com/renaser/os/calendar/application/services/**`
- `src/main/java/com/renaser/os/calendar/domain/model/**`
- `src/main/java/com/renaser/os/shared/web/{GlobalExceptionHandler,SecurityConfig,ApiErrorResponse}.java`

Todo lo que sigue está confirmado contra ese código. Donde algo no se pudo confirmar (por ejemplo un caso límite que ningún test cubre), se dice explícitamente.

## 0. Antes de probar nada

**Base URL:** no hay `server.port` ni `context-path` propios en `application.yaml` → `http://localhost:8080`. Todas las rutas de abajo son relativas a eso.

**Autenticación real hoy: NO hay.** `SecurityConfig.apiFilterChain` tiene `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())` sobre `/api/v1/**`. La identidad del actor es **enteramente** el header `X-Actor-Id` — no se valida contra ningún JWT todavía (el comentario del propio `SecurityConfig` lo dice: "mecanismo de identidad temporal"). Esto significa que **cualquiera puede mandar cualquier UUID como `X-Actor-Id` y actuar como ese usuario** — para pruebas está bien, pero no es el modelo de seguridad final.

- `X-Actor-Id` tiene que ser un UUID válido (`UserId.of` hace `UUID.fromString`). Si no lo es: `400 Bad Request`, `"UserId no es un UUID valido: <valor>"`.
- Si el endpoint exige el header (`@RequestHeader("X-Actor-Id")` sin `required=false`) y no lo mandás: `400 Bad Request`, `"Falta el header obligatorio 'X-Actor-Id'"`.
- El UUID tiene que corresponder a un usuario que exista en `users` — si no existe, según el endpoint da `404 Actor no encontrado` o `403 Cuenta inexistente o suspendida` (el criterio exacto varía por endpoint, está documentado en cada uno abajo — es una de las trampas reales de este módulo).

**Formato de error estándar** (`ApiErrorResponse`, todo lo que no es 2xx/204 en este documento sigue esta forma salvo que se diga lo contrario):

```json
{ "message": "texto del error", "timestamp": "2026-08-26T10:00:00Z" }
```

**Mapeo de excepciones → HTTP** (`GlobalExceptionHandler`, aplica a los 48 endpoints):

| Excepción de dominio/aplicación | HTTP |
|---|---|
| `NotAuthorizedException` | 403 |
| `NoSuchElementException` | 404 |
| `IllegalArgumentException` / `ConstraintViolationException` / bean validation (`@Valid`) | 400 |
| `IllegalStateException` | 409 |
| `DataIntegrityViolationException` (choque de UNIQUE/CHECK en Postgres, ej. doble tap) | 409 |
| Falta `@RequestParam`/`@RequestHeader` obligatorio | 400 |
| JSON malformado / tipo incorrecto en el body | 400 |
| Path variable con tipo incorrecto (ej. `id` no UUID) | 400 |
| `DateTimeParseException` (fecha/hora mal formada en un parámetro parseado a mano: `cursor`, `from`/`to`, `occurrenceStart`, `startsAt`) | 400, mensaje pide ISO-8601 |
| Verbo HTTP no soportado en la ruta | 405 |

**Trampa transversal:** `IllegalStateException` (conflictos de negocio: "ya existe", "tiene N publicaciones asociadas", "ese mentor ya lidera otra célula") mapea a **409**, no a 400. Si estás armando asserts automáticos, no asumas que todo error de negocio es 400.

---

## 1. El Muro — publicaciones (`WallController`, `/api/v1/wall`)

Servicio: `PublicacionMuroService`.

### 1.1 `GET /api/v1/wall` — feed

**Headers:** `X-Actor-Id` (obligatorio).
**Query params:** `cursor` (opcional, ISO-8601 instant — es `creadoEn` de la última publicación de la página anterior), `category` (opcional, clave de categoría).
**Quién puede:** cualquier actor con cuenta `ACTIVE` (`requireActorActivo`). Suspendido → 403.
**Respuesta 200:**
```json
{ "posts": [ { "id": "...", "authorId": "...", "authorName": "...", "authorAvatarUrl": "...",
  "type": "MANUAL", "category": "REVELACIONES", "text": "...", "media": [ {"url":"...", "mimeType":"image/jpeg"} ],
  "createdAt": "2026-08-26T10:00:00Z", "reactionCounts": {"LIKE": 3, "DISLIKE": 0}, "myReactions": ["LIKE"], "commentCount": 2 } ],
  "nextCursor": "2026-08-25T09:00:00Z" }
```
`type` puede ser `MANUAL`, `MILESTONE_AUTO` o `GUERRERO_CAIDO`. `media[].url` es una URL **firmada de lectura** (15 min de validez), no la ruta cruda. `reactionCounts` siempre trae las dos claves `LIKE`/`DISLIKE`, aunque sean 0.
**Errores:** `category` desconocida → 400 `"Categoria desconocida: <clave>"`. Actor inexistente → 404 `"Actor no encontrado: <id>"`. Suspendido → 403 `"La cuenta esta suspendida"`.
**curl:**
```bash
curl -s "http://localhost:8080/api/v1/wall?category=REVELACIONES" \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111"
```

### 1.2 `POST /api/v1/wall` — publicar

**Headers:** `X-Actor-Id`.
**Body** (`CreateWallPostRequest`):
```json
{ "text": "string, @NotBlank, max 5000", "media": [ {"url": "ruta-o-url, @NotBlank", "mimeType": "@NotBlank"} ], "category": "clave opcional" }
```
`media`: `@NotEmpty`, máx 10 ítems — **obligatorio al menos 1** (el dominio `Publicacion.publicar` lo exige: "La publicacion debe llevar al menos una foto o video").
**Trampa del campo `media[].url`:** pese al nombre, este campo **no es una URL pública** — es la `ruta` que devolvió `POST /wall/media/upload-url` (ver §1.11). El backend lo guarda tal cual como `ruta` dentro del bucket `wall` y lo vuelve a firmar para lectura cuando se lista. Si mandás la `uploadUrl` firmada en vez de la `ruta`, la publicación se crea pero la imagen no va a poder leerse correctamente.
**Quién puede:** actor `ACTIVE` con rol `TRAINEE`, `MENTOR`, `MENTOR_LEAD`, `ADMIN` o `ALCHEMIST` (`requireActorPuedePublicar` — hoy cubre todos los roles existentes).
**Respuesta:** `201 Created`, body = `WallPostResponse` (mismo shape que un ítem de §1.1).
**Errores:** `category` desconocida → 400. Actor no encontrado → 404. Suspendido → 403.
**curl:**
```bash
curl -s -X POST "http://localhost:8080/api/v1/wall" \
  -H "X-Actor-Id: 11111111-1111-1111-1111-111111111111" -H "Content-Type: application/json" \
  -d '{"text":"Mi primera publicacion","media":[{"url":"muro/1111.../abc-uuid","mimeType":"image/jpeg"}],"category":"REVELACIONES"}'
```

### 1.3 `PATCH /api/v1/wall/{id}` — editar

**Body** (`UpdateWallPostRequest`): mismo shape que crear, sin `category` (no se puede tocar al editar — `Publicacion.editar` no la toca).
**Quién puede:** **solo el autor**. `requireActorHabilitado` (fail-closed) + `!publicacion.autorId().equals(actorId)` → 403 `"No autorizado"`.
**Errores:** publicación oculta o inexistente → 404 `"Publicacion no encontrada: <id>"` (una publicación oculta "no existe" para este endpoint — ver `requireVisible`). Actor inexistente/suspendido → 403 `"Cuenta inexistente o suspendida"` (**no** 404 — a propósito, para no filtrar si el actor existe).
**Respuesta:** 200, `WallPostResponse`.

### 1.4 `DELETE /api/v1/wall/{id}` — ocultar (moderación blanda, no borra)

**Quién puede:** el autor **o** un moderador (`ADMIN`/`ALCHEMIST` activo). `requireActorHabilitado` + (`autor` o `esModerador`) → si ninguna, 403 `"No autorizado"`.
**Respuesta:** 200, `{"id": "<uuid>"}`.
**Nota:** "ocultar" nunca destruye — pone `oculta=true`. El borrado físico es un endpoint aparte (§1.6) y solo lo hace un moderador.

### 1.5 `POST /api/v1/wall/{id}/restore` — restaurar

**Quién puede:** **solo moderador** (`ADMIN`/`ALCHEMIST`). `requireModerador` → 403 `"Solo ADMIN/ALCHEMIST moderan el Muro"` si no.
**Precondición:** la publicación tiene que estar **oculta**; si está visible, 404 `"Publicacion no encontrada: <id>"` (mismo criterio invertido que §1.3/1.4 — para este endpoint, una publicación visible "no existe").
**Respuesta:** 200, `{"id": "<uuid>"}`.

### 1.6 `DELETE /api/v1/wall/{id}/permanent` — borrado físico

**Quién puede:** solo moderador. Misma precondición que §1.5 (tiene que estar oculta primero — hay que ocultarla con §1.4 antes de poder borrarla para siempre).
**Respuesta:** 200, `{"id": "<uuid>"}`.

### 1.7 `GET /api/v1/wall/hidden` — cola de moderación

**Quién puede:** solo moderador (403 si no).
**Query:** `cursor` opcional.
**Respuesta:** 200, mismo shape que el feed (`WallFeedPageResponse`), pero solo publicaciones ocultas.

### 1.8 `GET /api/v1/wall/mine` — cuántas publiqué

**Quién puede:** actor `ACTIVE` (`requireActorActivo`). **Corregido 2026-08-31 (E-50):** antes no había ningún chequeo de actor acá — un actor suspendido o inexistente recibía `200 {"count": 0}`. Ahora una cuenta suspendida recibe 403 y una inexistente 404, igual que en `GET /api/v1/wall`.
**Respuesta:** `{"count": 3}`.

### 1.9 `GET /api/v1/wall/latest-author` — nombre del último que publicó

**Quién puede:** actor `ACTIVE` (`requireActorActivo`, mismo guard que el feed). **Corregido 2026-08-31 (E-50):** antes el caso de uso ni siquiera recibía el `actorId` que el controller ya tenía, así que cualquiera —incluida una cuenta suspendida— obtenía el nombre completo de la última persona que publicó.
**Respuesta:** `{"authorName": "Juan Perez"}` o `{"authorName": null}` si no hay publicaciones visibles.

### 1.10 `POST /api/v1/wall/{id}/react` — reaccionar

**Body** (`ReactToWallPostRequest`): `{"type": "LIKE"}` o `{"type": "DISLIKE"}` (`@NotBlank`, valores **en inglés**; cualquier otro valor → 400 `"type invalido: <valor>"`).
**Quién puede:** actor `ACTIVE` (`requireActorHabilitado`, fail-closed a 403 "Cuenta inexistente o suspendida").
**Semántica del toggle** (`ReaccionMuro.calcularToggle`, dominio puro): mandar el mismo tipo que ya tenías puesto lo **quita**; mandar el otro lo **reemplaza** (nunca hay más de una reacción por usuario — no hace falta "deshacer" antes de cambiar de LIKE a DISLIKE).
**Respuesta:** 200,
```json
{ "reacted": true, "reactionCounts": {"LIKE": 4, "DISLIKE": 0} }
```
`reacted=false` cuando el toggle fue "quitar".
**Errores:** publicación oculta/inexistente → 404.

### 1.11 `POST /api/v1/wall/media/upload-url` — paso 1 de subir media

**Body** (`SolicitarUrlSubidaMediaRequest`): `{"tipoContenido": "image/jpeg"}` — **sin `@Valid` ni `@NotBlank` en el DTO ni en el controller**; si mandás `null` o lo omitís, viaja como `null` hasta el firmante de storage (no hay validación explícita de este campo en este endpoint — comportamiento exacto ante `null` no confirmado por no tener acceso al adaptador real de storage en este alcance).
**Quién puede:** mismo criterio que publicar (`requireActorPuedePublicar`).
**Respuesta:** 200,
```json
{ "uploadUrl": "https://.../signed...", "bucket": "wall", "ruta": "muro/<actorId>/<uuid>" }
```
**Paso 2 (fuera de esta API):** `PUT` del archivo binario directo a `uploadUrl`, con el `Content-Type` que declaraste en `tipoContenido` — no hay endpoint propio para esto, es una URL prefirmada (`AlmacenamientoPort.firmarSubida`, válida 10 minutos).
**Paso 3:** usar el campo `ruta` de la respuesta como el `url` de cada ítem de `media` en `POST /api/v1/wall` (§1.2) — **no** la `uploadUrl`.
**curl (paso 1):**
```bash
curl -s -X POST "http://localhost:8080/api/v1/wall/media/upload-url" \
  -H "X-Actor-Id: 1111..." -H "Content-Type: application/json" \
  -d '{"tipoContenido":"image/jpeg"}'
```
**curl (paso 2, con la uploadUrl de la respuesta anterior):**
```bash
curl -s -X PUT "<uploadUrl>" -H "Content-Type: image/jpeg" --data-binary "@foto.jpg"
```

---

## 2. Comentarios (`WallCommentController`, `/api/v1/wall/{postId}/comments`)

Servicio: `ComentarioMuroService`.

### 2.1 `GET /api/v1/wall/{postId}/comments` — listar

**Headers:** ninguno obligatorio (no pide `X-Actor-Id` — es el único de este grupo que no lo exige).
**Query:** `cursor` opcional (ISO-8601 instant).
**Precondición:** la publicación tiene que estar visible, si no 404.
**Respuesta:** 200,
```json
{ "comments": [ {"id":"...","postId":"...","authorId":"...","authorName":"...","authorAvatarUrl":"...","text":"...","createdAt":"..."} ], "nextCursor": null, "total": 2 }
```
Página de 30.

### 2.2 `POST /api/v1/wall/{postId}/comments` — crear

**Headers:** `X-Actor-Id` obligatorio.
**Body** (`CreateWallCommentRequest`): `{"text": "string, @NotBlank, max 500"}`.
**Quién puede:** actor `ACTIVE` (fail-closed 403). **Corregido recientemente:** un `SUSPENDED` ya NO puede comentar (era un hallazgo de la auditoría — `requireActorHabilitado` se agregó a `escribir`).
**Respuesta:** `201 Created`,
```json
{ "comment": {"id":"...","postId":"...","authorId":"...","authorName":"...","authorAvatarUrl":"...","text":"...","createdAt":"..."}, "commentCount": 3 }
```

### 2.3 `PATCH /api/v1/wall/{postId}/comments/{commentId}` — editar

**Body:** `{"text": "..."}` (mismas reglas de tamaño).
**Quién puede:** **solo el autor** del comentario. Actor suspendido/inexistente → 403. Autor distinto → 403 `"No autorizado"`.
**Errores:** comentario oculto/inexistente → 404 `"Comentario no encontrado: <id>"`.
**Respuesta:** 200, `WallCommentResponse`.

### 2.4 `DELETE /api/v1/wall/{postId}/comments/{commentId}` — ocultar

**Quién puede:** el autor **o** un moderador (`ADMIN`/`ALCHEMIST`). Igual que ocultar una publicación.
**Corregido recientemente:** antes ni siquiera se chequeaba el estado del actor al ocultar/editar un comentario — ahora `requireActorHabilitado` corre siempre antes de la comparación de autoría.
**Respuesta:** 200, `{"commentCount": 2}`.

---

## 3. Categorías del Muro

### 3.1 `GET /api/v1/wall/categories` — catálogo público (`WallCategoryController`)

**Headers:** **ninguno**. Pese a que el javadoc del controller dice *"catalogo publico, cualquiera con sesion"*, el código **no pide `X-Actor-Id` ni chequea actor** — es 100% público, incluso sin cuenta. Trampa a tener en cuenta si estás verificando permisos: este endpoint no distingue actores en absoluto.
**Respuesta:** 200, `{"categories": [{"key":"REVELACIONES","label":"Revelaciones","emoji":"✨","order":1}]}`. Solo trae las **activas**; sin `isActive`/`isSystem`.

### 3.2–3.6 `/api/v1/admin/wall-categories/**` (`WallCategoryAdminController`)

Servicio: `CategoriaMuroService`. **Todo este grupo es solo `ADMIN`/`ALCHEMIST` activos** (`requireAdmin` en cada método) — cualquier otro rol, 403 `"Solo ADMIN/ALCHEMIST administran categorias del Muro"`.

#### `GET /api/v1/admin/wall-categories`
Lista **todas** (activas e inactivas), con `postCount` por categoría.
```json
[{"key":"REVELACIONES","label":"Revelaciones","emoji":"✨","order":1,"isActive":true,"isSystem":true,"postCount":12}]
```

#### `POST /api/v1/admin/wall-categories`
**Body** (`CrearWallCategoryRequest`): `{"key":"@NotBlank","label":"@NotBlank","emoji":"@NotBlank"}`.
`key` tiene que matchear `^[A-Z][A-Z0-9_]{1,39}$` (mayúsculas ASCII, sin espacios ni acentos) — si no, 400 `"La clave debe ir en MAYUSCULAS ASCII, sin espacios ni acentos (ej. REVELACIONES)"`. `label` máx 40 chars. `emoji` máx 8 chars.
**Errores:** clave repetida → **409** `"Ya existe una categoria con la clave \"<clave>\""`.
**Respuesta:** `201`, `WallCategoryAdminResponse`.

#### `PATCH /api/v1/admin/wall-categories/{key}` — **PATCH, no POST**
**Body** (`ActualizarWallCategoryRequest`, sin `@Valid` en el controller): `{"label": "...", "emoji": "...", "isActive": true}` — todos opcionales, `null` conserva el valor actual.
**Trampa:** una categoría de **sistema** (`isSystem=true`, ej. la que usa la secuencia de bienvenida) no se puede desactivar — `isActive:false` sobre una de sistema → 400 `"\"<etiqueta>\" es una categoria del sistema: la secuencia de bienvenida la necesita para la primera publicacion. Puedes cambiarle el nombre y el emoji, pero no retirarla."` Sí se le puede cambiar `label`/`emoji`.
**Errores:** clave inexistente → 404.

#### `DELETE /api/v1/admin/wall-categories/{key}`
**Trampa doble:**
- Una categoría **de sistema** nunca se puede eliminar → 400 `"\"<etiqueta>\" es una categoria del sistema y no se puede eliminar..."`.
- Una categoría **con publicaciones asociadas** (`postCount > 0`) tampoco → **409** `"\"<etiqueta>\" tiene N publicacion(es). Retirala en vez de eliminarla."` (hay que primero `PATCH {isActive:false}`).
**Respuesta:** `204 No Content` si se pudo.

#### `POST /api/v1/admin/wall-categories/reorder`
**Body** (`ReordenarWallCategoriesRequest`): `{"keys": ["A","B","C"], "@NotEmpty"}` — el **orden de la lista** es el nuevo orden.
**Errores:** alguna clave no existe → 400 `"Categorias desconocidas: <claves>"`.
**Respuesta:** `204 No Content`.

---

## 4. Cohortes (`CohorteAdminController`, `/api/v1/admin/cohorts`)

Servicio: `CohorteService`. **Distinción de roles real (verificada en el código, no asumida):**
- **`ADMIN`/`ALCHEMIST`**: listar/ver/crear/editar/cambiar estado/eliminar — todo.
- **`MENTOR`**: puede **listar y ver** (`GET`), pero **solo la cohorte de la célula que lidera** — si no lidera ninguna, `listar` devuelve `[]` (no error) y `obtener` sobre cualquier cohorte que no sea la suya da 403. **No puede crear/editar/eliminar/cambiar estado** (esos 4 métodos llaman `requireAdmin` directo, sin excepción para MENTOR).
- Cualquier otro rol (`TRAINEE`, `MENTOR_LEAD`): 403 en todo.

### 4.1 `GET /api/v1/admin/cohorts`
**Query:** `status` opcional, en inglés (`PLANNED`/`ACTIVE`/`COMPLETED`) — cualquier otro valor → 400 `"status invalido: <valor>"`.
**Respuesta:** `[{"id":"...","name":"...","startDate":"2026-01-01","endDate":null,"status":"PLANNED","cellCount":3}]`.

### 4.2 `GET /api/v1/admin/cohorts/{id}`
**Errores:** MENTOR viendo una cohorte ajena → 403 `"No tienes acceso a esta cohorte"`; MENTOR sin célula liderada → 403 `"No lideras ninguna celula"`; inexistente → 404.

### 4.3 `POST /api/v1/admin/cohorts`
**Body** (`CrearCohorteRequest`): `{"name":"@NotBlank","startDate":"2026-01-01, @NotNull (LocalDate)","endDate":"2026-04-01 (opcional)"}`.
`name` máx 200 chars. `endDate` (si viene) no puede ser anterior a `startDate` → 400.
**Respuesta:** `201`, `CohorteResponse`.

### 4.4 `PATCH /api/v1/admin/cohorts/{id}` — **PATCH, no POST**
**Body** (`ActualizarCohorteRequest`, sin `@Valid`): `{"name":"...","startDate":"...","endDate":"..."}`. **Trampa (CM-14, documentada en el propio código):** `endDate` **siempre se aplica**, incluso si lo mandás `null` explícito — el controller no distingue "omitido" de "null explícito" en este campo puntual, a diferencia de `name`/`startDate` que si son `null` conservan el valor anterior.

### 4.5 `PATCH /api/v1/admin/cohorts/{id}/status`
**Body** (`CambiarEstadoCohorteRequest`): `{"status":"ACTIVE"}`.
**Trampa — máquina de estados de un solo sentido:** `PLANIFICADA → ACTIVA → COMPLETADA`, de a un paso, **sin vuelta atrás**. Saltar un paso (`PLANNED → COMPLETED` directo) o retroceder → 400 `"Transicion de estado invalida: <actual> -> <destino>"`.

### 4.6 `DELETE /api/v1/admin/cohorts/{id}`
**Trampa:** si tiene células asociadas → **409** `"No se puede eliminar: tiene N celula(s) asociada(s). Elimina las celulas primero."`
**Respuesta:** `204`.

---

## 5. Células (`CelulaAdminController`, `/api/v1/admin/cells`)

Servicio: `CelulaService`. Mismo patrón de roles que cohortes: `ADMIN`/`ALCHEMIST` todo; `MENTOR` solo lectura y **solo de la/las célula(s) que lidera**; mutaciones (crear/editar/asignar/quitar/programar/eliminar) son **siempre `requireAdmin`**, ni siquiera el mentor puede editar su propia célula.

### 5.1 `GET /api/v1/admin/cells?cohortId=<uuid>` — **`cohortId` es OBLIGATORIO**
**Trampa confirmada:** el parámetro es `@RequestParam UUID cohortId` sin `required=false` — si lo omitís, **400** `"Falta el parametro obligatorio 'cohortId'"` (no 200 con lista vacía, no 500).
**Quién puede:** `ADMIN`/`ALCHEMIST` ven todas las de esa cohorte; `MENTOR` solo ve la suya si coincide con `cohortId` (si no lidera ninguna o lidera una de otra cohorte, lista vacía `[]`, no error).
**Respuesta:** `[{"id":"...","name":"...","cohortId":"...","videoCallUrl":"...","nextSessionAt":null,"memberCount":5,"mentor":{"id":"...","fullName":"...","avatarUrl":"..."}}]` (`mentor` puede ser `null` si no tiene mentor asignado).

### 5.2 `GET /api/v1/admin/cells/{id}`
**Quién puede:** `ADMIN`/`ALCHEMIST` cualquiera; `MENTOR` solo si `celula.mentorId == actorId` → si no, 403 `"No lideras esta celula"`.
**Respuesta:** `CelulaDetalleResponse` — incluye `members` (lista de `PerfilBasicoResponse`).

### 5.3 `POST /api/v1/admin/cells`
**Body** (`CrearCelulaRequest`): `{"name":"@NotBlank","cohortId":"uuid @NotNull","videoCallUrl":"opcional"}`.
**Quién puede:** solo `ADMIN`/`ALCHEMIST`.
**Errores:** cohorte inexistente → 404.
**Respuesta:** `201`, `CelulaDetalleResponse`.

### 5.4 `PATCH /api/v1/admin/cells/{id}` — **PATCH, no POST**
**Body** (`ActualizarCelulaRequest`, sin `@Valid`): `{"name":"...","videoCallUrl":"..."}`. Mismo comportamiento CM-14 que cohortes: `videoCallUrl` siempre se aplica (incluso `null` explícito borra la URL).
**Trampa:** si la cohorte de esa célula ya está `COMPLETADA` → 403 `"No se pueden modificar celulas de una cohorte completada"` (nótese: es `NotAuthorizedException`, mapea a **403**, no a 409 pese a ser un estado de negocio).

### 5.5 `PUT /api/v1/admin/cells/{id}/mentor` — **PUT, no POST** — asignar mentor
**Body** (`AsignarMentorRequest`): `{"leaderUserId": "uuid @NotNull"}`.
**Validaciones en cadena (en este orden):**
1. Usuario no existe → 404 `"Usuario no encontrado: <id>"`.
2. Usuario no `ACTIVE` → 400 `"El usuario seleccionado no esta activo"`.
3. Rol no es `MENTOR`/`ADMIN`/`ALCHEMIST` → 400 `"El usuario seleccionado no puede liderar una celula"`.
4. No tiene fila en `perfiles_mentor` todavía → **409** `"El usuario todavia no tiene un perfil de mentor (perfiles_mentor) — debe crearse desde el modulo de usuarios antes de poder liderar una celula"`.
5. Ya lidera **otra** célula distinta → **409** `"Ese mentor ya lidera otra celula"`.
**Respuesta:** 200, `CelulaDetalleResponse`.

### 5.6 `DELETE /api/v1/admin/cells/{id}/mentor` — quitar mentor
Sin body. `204`... en realidad devuelve `CelulaDetalleResponse` con `mentor:null` (200, no 204 — revisar el controller: `quitarMentor` retorna `CelulaDetalleResponse`, no `ResponseEntity<Void>`).

### 5.7 `POST /api/v1/admin/cells/{id}/session` — programar próxima sesión
**Body** (`ProgramarSesionRequest`): `{"scheduledAt": "2026-09-01T18:00:00Z, @NotNull (Instant)"}`.
**Respuesta:** 200, `CelulaDetalleResponse` con `nextSessionAt` actualizado.

### 5.8 `DELETE /api/v1/admin/cells/{id}`
Solo `ADMIN`/`ALCHEMIST`. `204`.

---

## 6. Mi célula (`MiCelulaController`, `/api/v1/me/cell`)

Servicio: `CelulaService` (métodos `miCelula`/`misCompaneros`).

**Trampa de autorización real vs. javadoc:** el comentario del controller dice *"solo TRAINEE"*, pero el código de `CelulaService.miCelula`/`misCompaneros` **solo llama `requireActorActivo`** — no hay ningún chequeo de `rol == TRAINEE`. En la práctica, un `MENTOR` o `ADMIN` activo que mande su propio `X-Actor-Id` también recibe una respuesta (probablemente 404 `assigned:false` porque no está en `participantes_celula`, pero no un 403). Si vas a escribir un test de autorización negativa para este endpoint, el código real no lo bloquea por rol — solo por estado de cuenta.

### 6.1 `GET /api/v1/me/cell`
**Confirmado — 404 que NO es un error real:** si el actor no tiene célula asignada, la respuesta es `404 Not Found` con body `{"assigned": false}` — **no** el `ApiErrorResponse` estándar (`{message, timestamp}`). Un cliente que solo mira el código HTTP para decidir "hubo un error" va a tratar esto como fallo; hay que mirar el body.
**Si tiene célula, 200:**
```json
{ "cellId":"...", "cellName":"...", "cohortName":"...", "cohortStatus":"ACTIVE", "mentorName":"...", "mentorAvatarUrl":"...",
  "memberCount": 5, "totalCellsInCohort": 4, "videoCallUrl":"...", "nextSessionAt": null }
```

### 6.2 `GET /api/v1/me/cell/members`
Sin célula asignada: `200 {"members": []}` (no 404 acá — a diferencia de §6.1).
Con célula: `{"members": [{"traineeId":"...","fullName":"...","avatarUrl":"...","isSelf":true}]}` — `isSelf` marca tu propia fila en la lista.

---

## 7. Testimonios (`TestimonioController`, `/api/v1/testimonios`)

Servicio: `TestimonioService`.

### 7.1 `GET /api/v1/testimonios` — listar destacados
**Totalmente público** — sin header, sin chequeo de actor. Devuelve hasta 50, los que tienen `destacado=true` (todo testimonio nace destacado, no hay endpoint para des-destacar).
**Respuesta:** `[{"id":"...","userId":"...","wallPostId":null,"nombre":"...","rol":"...","avatarUrl":"...","fotoEventoUrl":null,"texto":"...","estrellas":5,"createdAt":"..."}]`.

### 7.2 `POST /api/v1/testimonios` — dos modos según el body

**Modo A — formulario manual (sin `wallPostId`):**
**Headers:** `X-Actor-Id` **opcional** (`required=false`) — se puede mandar sin sesión.
**Body** (`CreateTestimonioRequest`, **sin ninguna validación Bean**): `{"nombre":"...","rol":"...","texto":"...","estrellas":5,"wallPostId":null}`.
**Validación real (en el dominio, no en el DTO):** `nombre` mínimo 2 caracteres → si no, 400 `"El nombre debe tener al menos 2 caracteres"`. `texto` mínimo 5 caracteres → 400 `"El texto debe tener al menos 5 caracteres"`. `estrellas` entre 1 y 5 (default 5 si se omite) → 400 `"estrellas debe estar entre 1 y 5"`.
**Trampa:** en este modo **no hay ningún chequeo de actor ni de rol** — literalmente cualquiera, autenticado o no, puede crear un testimonio manual. No es una promoción, es un registro directo.

**Modo B — promover una publicación del Muro (con `wallPostId` no vacío):**
**Headers:** `X-Actor-Id` **obligatorio en este modo** — si falta, 403 `"Se requiere sesion para promover"` (chequeo del controller, no del `GlobalExceptionHandler`).
**Quién puede:** solo `ADMIN`/`ALCHEMIST` (`requireAdmin` en `TestimonioService.promover`) → cualquier otro rol, 403 `"Solo administradores pueden promover publicaciones"`.
**Comportamiento:** ignora `nombre`/`texto`/`rol` del body — los toma de la publicación y su autor. La portada del testimonio es la primera foto del carrusel de la publicación (por `orden`).
**Errores:** publicación inexistente → 404.
**Respuesta (ambos modos):** `201`, `TestimonioResponse`.
**curl (modo A, sin sesión):**
```bash
curl -s -X POST "http://localhost:8080/api/v1/testimonios" -H "Content-Type: application/json" \
  -d '{"nombre":"Ana Diaz","rol":"Aprendiz","texto":"Este programa me cambio la vida","estrellas":5}'
```

---

## 8. Calendario — Eventos (`EventoController`, `/api/v1/calendar/events`)

Servicios: `EventoService`, `ConfirmacionService`, con reglas de acceso compartidas en `AccesoEventoService`.

**Modelo de roles para el calendario** (`RolUsuario`, propio del módulo, espejo del de `users`): `ALCHEMIST`, `ADMIN`, `MENTOR_LEAD`, `MENTOR`, `TRAINEE`.

**Quién puede administrar (crear/editar/eliminar/cancelar ocurrencia/portada):**
- `ADMIN`/`ALCHEMIST`: cualquier evento.
- `MENTOR`: solo eventos que **él mismo creó** (`requirePropioSiMentor`) — editar/eliminar/cancelar/portada de un evento creado por otro mentor u otro admin → 403 `"Solo puedes <editar/eliminar/cancelar una ocurrencia de> los eventos que creaste"`.
- `TRAINEE`/`MENTOR_LEAD`: no pueden administrar nada → 403 `"No tienes permiso para administrar el calendario"`.

**Trampa grande para `MENTOR` al crear/editar:** si el actor es `MENTOR`, el backend **ignora y sobreescribe** lo que mandaste en `audienceType`/`minLevelId`/`courseId`/`targetRoles` — fuerza `audienceType=CELL` con la célula que ese mentor lidera (`requireCelulaLiderada`). Si el mentor no lidera ninguna célula → 403 `"Todavia no lideras una celula — no puedes administrar sesiones"`. No es un error silencioso del cliente: el servidor decide la audiencia por vos.

**Acceso de lectura (ver eventos / listar):** lo consulta cualquier rol, sin restricción de rol — la visibilidad depende de la **audiencia** del evento (ver tabla abajo), no de quién puede administrar.

### Reglas de audiencia (`ResolverAudiencia.puedeVer`, dominio puro)

| `audienceType` | Quién ve el evento |
|---|---|
| `ALL_MEMBERS` | Todos. |
| `MIN_LEVEL` | Solo `TRAINEE` cuyo rango de nivel (calculado del % de progreso del programa) sea ≥ el rango del nivel mínimo del evento. |
| `COURSE` | Solo quien tenga acceso a ese curso (`ResolverAudienciaCursoPort`). |
| `ROLES` | Solo los roles listados en `targetRoles`. |
| `CELL` | Solo miembros de esa célula específica (comparando `celulaId` del visor). |

`ADMIN`/`ALCHEMIST` **siempre ven todo**, sin importar la audiencia (bypass total, primera línea de `puedeVer`).

**Elegibilidad adicional para `MENTORIA_ALQUIMISTA`:** además de pasar la audiencia, si el visor es `TRAINEE` tiene que ser "elegible" (`ConsultarElegibilidadEventoPort.esElegible` — % de cumplimiento semanal de hábitos+rocas, calculado fuera de este módulo). `ADMIN`/`ALCHEMIST`/`MENTOR` son elegibles siempre, sin consulta. Los otros 3 tipos de evento (`ESPONTANEO`, `SEMANA_MANIFESTACION`, `SESION_ESPECIAL`) no exigen elegibilidad.

### 8.1 `GET /api/v1/calendar/events` — listar ocurrencias en un rango

**Headers:** `X-Actor-Id`.
**Query:** `from` y `to` — **ambos obligatorios**, ISO-8601 instant (`@RequestParam("from") String from`, sin `required=false`) → si falta alguno, 400 `"Falta el parametro obligatorio 'from'"` (o `'to'`). Si el valor no parsea como instant, 400 con el mensaje de formato ISO-8601. `scope` opcional — **se acepta pero no hace nada todavía** (el controller lo recibe y lo descarta; documentado así en el propio código, no es que falte implementarlo por descuido).
**Respuesta:** 200, lista de `OcurrenciaResponse` — el evento recurrente expandido en cada ocurrencia real dentro del rango, ordenadas por `startsAt`. `viewerRsvpStatus` es tu propio RSVP para esa ocurrencia puntual (`GOING`/`NOT_GOING`/`MAYBE`/`null` si no confirmaste).
**Errores:** actor suspendido → 403 `"Cuenta suspendida"`. Actor sin progreso registrado → 404.
**curl:**
```bash
curl -s "http://localhost:8080/api/v1/calendar/events?from=2026-08-01T00:00:00Z&to=2026-08-31T23:59:59Z" \
  -H "X-Actor-Id: 1111..."
```

### 8.2 `GET /api/v1/calendar/events/{id}` — un evento puntual
**Errores:** sin acceso por audiencia/elegibilidad → 403 `"No tienes acceso a este evento"`. Inexistente → 404.
**Respuesta:** 200, `EventoResponse` (sin las ocurrencias expandidas — es el evento "maestro").

### 8.3 `POST /api/v1/calendar/events` — crear

**Body** (`EventoRequest`, mismo shape para crear y editar — el form siempre reenvía el evento completo):

| Campo | Tipo/regla |
|---|---|
| `title` | `@NotBlank`, máx **30** caracteres (deliberadamente corto — tarjeta del calendario) |
| `eventType` | `@NotBlank`, uno de `MENTORIA_ALQUIMISTA`/`ESPONTANEO`/`SEMANA_MANIFESTACION`/`SESION_ESPECIAL` (sin traducción, mismos literales en dominio/BD/wire) |
| `description` | opcional, máx 300 |
| `startsAt` | `@NotBlank`, ISO-8601 instant |
| `durationMinutes` | opcional, si viene tiene que ser positivo |
| `timezone` | opcional, default `America/Lima`; **atención:** un valor inválido pasa a `ZoneId.of(...)`, que lanza `DateTimeException` — **no** `DateTimeParseException` — así que puede no estar cubierto por el handler de fechas del `GlobalExceptionHandler` y devolver un 500 en vez de 400 (no confirmado con un test real; verificar antes de asumir 400 acá) |
| `locationType` | `@NotBlank`, uno de `INTERNAL_CALL`/`WEBINAR`/`ZOOM`/`MEET`/`ADDRESS`/`LINK` |
| `locationValue` | obligatorio y no vacío si `locationType` es `ZOOM`/`MEET`/`LINK` (mensaje: `"La URL es obligatoria para este tipo de ubicacion"`) o `ADDRESS` (`"La direccion es obligatoria"`); tiene que ir **vacío** si es `INTERNAL_CALL`/`WEBINAR` (`"valorUbicacion debe ser nulo para <tipo>"`). Máx 600 |
| `audienceType` | opcional, default `ALL_MEMBERS`; uno de `ALL_MEMBERS`/`MIN_LEVEL`/`COURSE`/`ROLES`/`CELL` |
| `minLevelId` | obligatorio **solo si** `audienceType=MIN_LEVEL`, y tiene que existir en el catálogo de niveles — si no, 400 `"El nivel minimo indicado no existe: <id>"` |
| `courseId` | obligatorio **solo si** `audienceType=COURSE` |
| `targetCellId` | obligatorio **solo si** `audienceType=CELL` |
| `targetRoles` | obligatorio y no vacío **solo si** `audienceType=ROLES`, vacío en cualquier otro caso — violar esto → 400 `"audiencia_coherente: ..."` o `"targetRoles es obligatorio para ROLES y vacio en cualquier otro caso"` |
| `reminderRules` | opcional; si viene (aunque sea `[]`), el evento usa recordatorios **propios** en vez de los del tipo; máx 5, sin dos reglas iguales. Cada ítem: `{"kind":"minutesBefore"\|"daysBefore"\|"timeOfDay","value": numero o "HH:mm:ss"}` |
| `notifyOnCreate`, `remindByEmail` | `Boolean`, default `false` si se omiten |
| `recurrenceFrequency` | opcional, `DAILY`/`WEEKLY`/`MONTHLY` — si se omite, el evento no es recurrente |
| `recurrenceInterval` | opcional, default 1 |
| `recurrenceByWeekday` | opcional, lista de enteros 1–7 (ISO, 1=lunes) |
| `recurrenceUntil` | opcional, ISO instant; si viene tiene que ser **posterior** a `startsAt` → si no, 400 `"recurrencia.hasta debe ser posterior a iniciaEn"` |
| `recurrenceCount` | opcional |

Recordá el override de audiencia si el actor es `MENTOR` (arriba).
**Respuesta:** `201`, `EventoResponse` con `coverUrl:null` (todavía no tiene portada — ver §8.8/8.9).

**curl (evento simple, admin):**
```bash
curl -s -X POST "http://localhost:8080/api/v1/calendar/events" \
  -H "X-Actor-Id: <admin-uuid>" -H "Content-Type: application/json" \
  -d '{
    "title": "Sesion especial",
    "eventType": "SESION_ESPECIAL",
    "startsAt": "2026-09-01T18:00:00Z",
    "durationMinutes": 60,
    "locationType": "ZOOM",
    "locationValue": "https://zoom.us/j/123",
    "audienceType": "ALL_MEMBERS"
  }'
```

### 8.4 `PUT /api/v1/calendar/events/{id}` — editar (PUT, reenvío completo)
Mismo body y mismas reglas que crear. `MENTOR` solo si es el creador. Al editar, además se intentan borrar los avisos pendientes futuros de la versión anterior (best-effort — si falla, solo queda un warning en el log, no rompe la request).

### 8.5 `DELETE /api/v1/calendar/events/{id}` — eliminar
`204`. `MENTOR` solo si es el creador. Si tenía portada, se intenta borrar del storage (best-effort, no rompe la request si falla).

### 8.6 `PUT /api/v1/calendar/events/{id}/rsvp` — confirmar asistencia
**Body** (`RsvpRequest`): `{"occurrenceStart":"2026-09-01T18:00:00Z, @NotBlank","status":"GOING"}` (`status` en `GOING`/`NOT_GOING`/`MAYBE`).
**Quién puede:** cualquiera con acceso al evento por audiencia (no hace falta ser "administrador" del calendario, este es para el visor común).
**Trampas de `occurrenceStart`:**
- Tiene que corresponder a una ocurrencia **real** de la serie (tolerancia de 3 minutos / 180000ms) — si no, 400 `"inicioOcurrencia no corresponde a una ocurrencia real de este evento"`.
- No podés confirmar asistencia a una ocurrencia de **más de 12 horas en el pasado** → **409** `"No puedes confirmar asistencia a una ocurrencia de dias pasados"`.
**Efecto colateral:** marcar `GOING` cancela los avisos/recordatorios pendientes de esa ocurrencia para vos (no pasa con `NOT_GOING`/`MAYBE`).
**Respuesta:** `200`, sin body.

### 8.7 `POST /api/v1/calendar/events/{id}/cancel-occurrence` — cancelar una ocurrencia puntual
**Body** (`CancelarOcurrenciaRequest`): `{"occurrenceStart":"..."}`.
**Quién puede:** mismo criterio que editar (`ADMIN`/`ALCHEMIST`, o `MENTOR` dueño del evento).
**Errores:** evento no recurrente → 400 `"Este evento no es recurrente — eliminalo en vez de cancelar una ocurrencia"`. `occurrenceStart` no real → 400 (mismo mensaje que RSVP).
**Respuesta:** `200`, sin body. Es un upsert — cancelar dos veces la misma ocurrencia no falla, reemplaza el override existente.

### 8.8 `POST /api/v1/calendar/events/{id}/portada/upload-url` — paso 1 de la portada
**Body** (`SolicitarUrlPortadaRequest`): `{"contentType":"image/jpeg", "@NotBlank"}`.
**Quién puede:** mismo criterio que editar el evento.
**Respuesta:** 200, `{"url":"https://...(firmada PUT, 10 min)...","bucket":"renaser-files","ruta":"calendar/<eventoId>/portada-<epochMillis>"}`.

### 8.9 `POST /api/v1/calendar/events/{id}/portada/confirm` — paso 2, confirmar
**Body** (`ConfirmarPortadaRequest`): `{"ruta":"calendar/<eventoId>/portada-...", "@NotBlank"}` — acá el campo **sí** se llama `ruta` y **sí** es literalmente la `ruta` que devolvió el paso 1 (a diferencia del Muro, no hay confusión de nombre en este flujo).
**Respuesta:** 200, `EventoResponse` con `coverUrl` ahora seteado (URL firmada de **lectura**, válida 1 hora — se re-firma en cada `GET`, no es la misma URL para siempre).
**curl (los 3 pasos):**
```bash
# 1) pedir URL de subida
curl -s -X POST "http://localhost:8080/api/v1/calendar/events/<id>/portada/upload-url" \
  -H "X-Actor-Id: <admin-uuid>" -H "Content-Type: application/json" -d '{"contentType":"image/jpeg"}'
# 2) subir el binario a la uploadUrl devuelta
curl -s -X PUT "<url del paso 1>" -H "Content-Type: image/jpeg" --data-binary "@portada.jpg"
# 3) confirmar con la ruta del paso 1
curl -s -X POST "http://localhost:8080/api/v1/calendar/events/<id>/portada/confirm" \
  -H "X-Actor-Id: <admin-uuid>" -H "Content-Type: application/json" -d '{"ruta":"calendar/<id>/portada-..."}'
```

---

## Flujos completos para probar

### A. Muro: publicar con media → reaccionar → comentar → listar → moderar

1. **Subir la foto (paso 1 de 2):** `POST /api/v1/wall/media/upload-url` con `{"tipoContenido":"image/jpeg"}` → guardá `ruta` de la respuesta (NO `uploadUrl` para el paso 3).
2. **Subir el binario:** `PUT <uploadUrl del paso 1>` con el archivo.
3. **Publicar:** `POST /api/v1/wall` con `X-Actor-Id: <autor>`, `media: [{"url": "<ruta del paso 1>", "mimeType":"image/jpeg"}]` → guardá `id` de la respuesta (es el `postId`).
4. **Otro usuario reacciona:** `POST /api/v1/wall/{id}/react` con `X-Actor-Id: <otro-usuario>`, `{"type":"LIKE"}` → confirmá `reactionCounts.LIKE == 1`.
5. **Un tercero comenta:** `POST /api/v1/wall/{id}/comments` con `X-Actor-Id: <tercero>`, `{"text":"..."}` → guardá `comment.id` como `commentId` y confirmá `commentCount == 1`.
6. **Listar y ver contadores:** `GET /api/v1/wall` — buscá el post por `id`, confirmá `commentCount` y `reactionCounts` coinciden con los pasos 4–5.
7. **Moderar (ocultar) como admin:** `DELETE /api/v1/wall/{id}` con `X-Actor-Id: <admin>` → `GET /api/v1/wall/hidden` (mismo admin) debe listarlo.
8. **Restaurar:** `POST /api/v1/wall/{id}/restore` con `X-Actor-Id: <admin>` → vuelve a aparecer en `GET /api/v1/wall`.

### B. Administración: cohorte → célula → mentor → "mi célula"

1. **Crear cohorte:** `POST /api/v1/admin/cohorts` con `X-Actor-Id: <admin>` → guardá `id` como `cohortId`.
2. **Crear célula en esa cohorte:** `POST /api/v1/admin/cells` con `{"name":"...","cohortId":"<cohortId>"}` → guardá `id` como `cellId`.
3. **Asignar mentor:** `PUT /api/v1/admin/cells/{cellId}/mentor` con `{"leaderUserId":"<uuid de un usuario con rol MENTOR y perfil de mentor ya creado>"}`. Si el usuario no tiene fila en `perfiles_mentor`, este paso falla con 409 — hay que crear ese perfil desde `users` antes.
4. **Poner a un aprendiz en esa célula:** (fuera del alcance de `community` — se hace desde `users`/la asignación de participante a célula; no hay endpoint en este módulo para eso).
5. **Ver "mi célula" desde ese aprendiz:** `GET /api/v1/me/cell` con `X-Actor-Id: <ese-aprendiz>` → confirmá que `cellId == cellId del paso 2` y `mentorName` coincide con el mentor asignado.

### C. Calendario: crear evento con audiencia → portada → ver en agenda → RSVP

1. **Crear evento** (admin): `POST /api/v1/calendar/events` con `audienceType:"ALL_MEMBERS"` (o `ROLES`/`CELL`/etc. según lo que quieras probar) → guardá `id` como `eventId` y `startsAt` (lo vas a necesitar como `occurrenceStart` si el evento no es recurrente).
2. **Portada, paso 1:** `POST /api/v1/calendar/events/{eventId}/portada/upload-url` → guardá `ruta` y `url`.
3. **Portada, paso 2 (subida real):** `PUT <url>` con el binario.
4. **Portada, paso 3 (confirmar):** `POST /api/v1/calendar/events/{eventId}/portada/confirm` con `{"ruta":"<ruta del paso 2>"}` → confirmá que la respuesta trae `coverUrl` no nulo.
5. **Un aprendiz lo ve en su agenda:** `GET /api/v1/calendar/events?from=<antes de startsAt>&to=<despues>` con `X-Actor-Id: <aprendiz-con-audiencia-correcta>` → el evento tiene que aparecer en la lista, con `occurrenceStart == startsAt` del paso 1 (si no es recurrente).
6. **Confirma asistencia:** `PUT /api/v1/calendar/events/{eventId}/rsvp` con `X-Actor-Id: <ese-aprendiz>`, `{"occurrenceStart":"<el mismo valor del paso 5>","status":"GOING"}` → repetí el `GET` del paso 5 y confirmá `viewerRsvpStatus == "GOING"`.

### D. Testimonios: listar públicos y crear uno

1. **Listar (sin sesión):** `GET /api/v1/testimonios` → debería responder aunque no mandes ningún header.
2. **Crear uno manual (sin sesión):** `POST /api/v1/testimonios` con `{"nombre":"Ana","texto":"Cambio mi vida, en serio","estrellas":5}` (sin `wallPostId`) → `201`.
3. **Volver a listar:** el testimonio del paso 2 tiene que aparecer (nace `destacado=true` siempre).
4. **(Opcional) Promover una publicación existente:** `POST /api/v1/testimonios` con `X-Actor-Id: <admin>` y `{"wallPostId":"<id de una publicacion del flujo A>"}` → `201`, con `nombre`/`texto`/`avatarUrl` tomados automáticamente del autor y la publicación, ignorando cualquier otro campo del body.
