# Pruebas endpoint por endpoint — módulo `rag`

**Fecha:** 2026-08-26
**Alcance:** los 5 endpoints REST del módulo `rag` (el último construido), probados uno por uno contra la app real (`./mvnw spring-boot:run`, Postgres + Redis reales en Docker, sin mocks), después del gate completo en verde (1112 tests) y de la auditoría adversarial + E2E de la sesión anterior. No es un smoke test: cada endpoint se probó con su matriz completa (happy path, sin header de actor, actor inexistente, actor suspendido, sin permiso/rol equivocado, validación de campos, recurso inexistente, y — donde aplica — el alcance de mentor asignado de D-47).

**Actores usados** (ya existentes en la BD de pruebas):

| Actor | UUID | Rol / estado |
|---|---|---|
| ADMIN | `00000000-0000-0000-0000-000000000001` | ADMIN, activo |
| Aprendiz (TRAINEE) | `11111111-1111-1111-1111-111111111111` | APRENDIZ, activo |
| Otro aprendiz sin relación | `aaaaaaaa-1111-1111-1111-111111111111` | APRENDIZ, activo |
| Mentor asignado al aprendiz de arriba | `bbbbbbbb-1111-1111-1111-111111111111` | MENTOR, activo |
| Mentor NO asignado | `33333333-3333-3333-3333-333333333333` | MENTOR, activo |
| Suspendido | `9c675442-6abb-46f3-8971-fe3bf0208127` | APRENDIZ, SUSPENDIDO |
| Inexistente (fantasma) | `ffffffff-0000-0000-0000-000000000000` | no existe en `usuarios` |

**Nota sobre datos de prueba:** para el endpoint 5 (`GET /espejo-sombra/{id}`) no existe ningún endpoint que genere un informe a demanda (nace solo del scheduler semanal, por diseño — D-47/D-48). Se insertó un informe de prueba directo por SQL (`632fed92-6b43-425f-b88a-fe0753e0acbd`, aprendiz `11111111...`, semana `2026-08-17`) con sus 2 preguntas de confrontación, únicamente para poder ejercitar el camino feliz y el control de visibilidad — no es un dato que haya generado el código, es un fixture de esta prueba. No se tocó ningún esquema, solo se insertaron filas respetando las constraints existentes (`pcts_suman_100`, `orden` 1-10, etc.).

**Verificación previa:** extensión `vector` de Postgres instalada (v0.8.6), columna `base_conocimiento.embedding vector(768)` con índice HNSW coseno ya creados por el baseline — el flujo de indexado corre contra pgvector real, no un mock.

---

## 1. `POST /api/v1/admin/conocimiento` — indexar un chunk en la base de conocimiento

Solo ADMIN/ALCHEMIST (D-46). El `EmbeddingPort` es `NoOpEmbeddingAdapter` (sin credenciales de Gemini, D-39): genera un vector de 768 ceros, así que el chunk se guarda igual, sin similaridad semántica real todavía.

| # | Caso | Request | HTTP | Response |
|---|---|---|---|---|
| 1.1 | Happy path (ADMIN) | `{"tipoFuente":"LECCION","clase":"habitos","documentoId":"doc-test-1","contenido":"El Santuario es el bloqueo de telefono nocturno...","metadatos":{"origen":"prueba-endpoint"}}` | **200** | `{"id":"06935445-2504-4952-a390-a1acee93bcc9"}` |
| 1.2 | Sin `X-Actor-Id` | (mismo body, sin header) | **400** | `{"message":"Falta el header obligatorio 'X-Actor-Id'"}` |
| 1.3 | Actor inexistente | header = fantasma | **404** | `{"message":"Actor no encontrado: ffffffff-0000-0000-0000-000000000000"}` |
| 1.4 | Actor suspendido | header = suspendido | **403** | `{"message":"Cuenta suspendida"}` |
| 1.5 | Actor sin permiso (TRAINEE) | header = aprendiz | **403** | `{"message":"Solo ADMIN/ALCHEMIST indexan conocimiento"}` |
| 1.6 | Validación: `contenido` vacío | `"contenido":""` | **400** | `{"message":"contenido: no debe estar vacío"}` |
| 1.7 | Validación: `tipoFuente` vacío | `"tipoFuente":""` | **400** | `{"message":"tipoFuente: no debe estar vacío"}` |

**Resultado: 7/7 correctos.** El actor inexistente da 404 (no 403) porque este endpoint no tiene ningún recurso previo cuya existencia se pueda filtrar — mismo criterio ya usado en `feed()`/`publicar()` de `community` (no aplica el fail-closed a 403 de E-30/E-42, que es solo para cuando ya se confirmó la visibilidad de un recurso antes del chequeo de actor).

---

## 2. `POST /api/v1/renasia/mensajes` — preguntarle a Renasia (streaming SSE)

Devuelve `text/event-stream`. Con el adaptador de IA NoOp, la respuesta es siempre el mismo mensaje fijo, sin 500.

| # | Caso | Request | HTTP | Response |
|---|---|---|---|---|
| 2.1 | Happy path (aprendiz activo) | `{"question":"que es el Pacto de Sangre?"}` | **200** | `data:Renasia todavia no esta disponible: faltan credenciales de IA por configurar (D-39).` |
| 2.2 | Sin `X-Actor-Id` | — | **400** | `{"message":"Falta el header obligatorio 'X-Actor-Id'"}` |
| 2.3 | Actor inexistente | header = fantasma | **404** | `{"message":"Usuario no encontrado: ffffffff-0000-0000-0000-000000000000"}` |
| 2.4 | Actor suspendido | header = suspendido | **403** | `{"message":"La cuenta esta suspendida"}` |
| 2.5 | Validación: `question` vacío | `{"question":""}` | **400** | `{"message":"question: no debe estar vacío"}` |
| 2.6 | Cuota diaria (D-48) — 24 preguntas más hasta llegar a 25 | 23 llamadas de relleno | **200** cada una | contador de Redis (`renasia:cuota:...`) sube de 2 a 25 |
| 2.7 | Cuota agotada (mensaje 26 del día) | `{"question":"esta deberia rebotar"}` | **429** | `{"message":"Se alcanzo el limite diario de mensajes a Renasia"}` |

**Resultado: 7/7 correctos.** Se verificó también contra Redis directo (`GET renasia:cuota:11111111...:2026-08-26`) que el contador sube con cada intento — incluidos los que después rebotan en 429, que es el comportamiento documentado (el `INCR` ya ocurrió antes del chequeo de límite).

---

## 3. `GET /api/v1/renasia/mensajes` — historial de la conversación

Paginado por cursor (`Instant`), orden más reciente primero.

| # | Caso | Request | HTTP | Response (resumen) |
|---|---|---|---|---|
| 3.1 | Happy path, sin params | — | **200** | lista completa de mensajes usuario/asistente intercalados, más reciente primero |
| 3.2 | `limit=3` | `?limit=3` | **200** | `{"messages":[...3 items...],"nextCursor":"2026-08-26T02:12:04.349746Z","hasMore":true}` |
| 3.3 | Sin `X-Actor-Id` | — | **400** | `{"message":"Falta el header obligatorio 'X-Actor-Id'"}` |
| 3.4 | Actor inexistente | header = fantasma | **404** | `{"message":"Usuario no encontrado: ffffffff-0000-0000-0000-000000000000"}` |
| 3.5 | Actor suspendido | header = suspendido | **403** | `{"message":"La cuenta esta suspendida"}` |
| 3.6 | Cursor con formato inválido | `?cursor=esto-no-es-una-fecha` | **400** | `{"message":"Fecha u hora con formato invalido: se espera ISO-8601 (ej. 2026-08-25T10:00:00Z)"}` |

**Resultado: 6/6 correctos.** El caso 3.6 confirma en vivo el handler de `DateTimeParseException` (fix de E-38, bug 1) también en este endpoint nuevo — sin él hubiera sido un 500 con stacktrace completo.

---

## 4. `GET /api/v1/espejo-sombra` — listar informes semanales

Sin `participanteId` en la query, lista los propios; con `participanteId`, los de ese participante (visible solo para el propio, su mentor asignado, o ADMIN/ALCHEMIST — D-47).

| # | Caso | Request | HTTP | Response |
|---|---|---|---|---|
| 4.1 | Happy path, propio (antes de insertar el fixture) | — | **200** | `[]` |
| 4.2 | Sin `X-Actor-Id` | — | **400** | `{"message":"Falta el header obligatorio 'X-Actor-Id'"}` |
| 4.3 | Actor inexistente | header = fantasma | **404** | `{"message":"Actor no encontrado: ffffffff-0000-0000-0000-000000000000"}` |
| 4.4 | Actor suspendido | header = suspendido | **403** | `{"message":"Cuenta suspendida"}` |
| 4.5 | Mentor **asignado** consultando a su aprendiz | `?participanteId=11111111...`, header = mentor asignado | **200** | `[]` (antes del fixture) |
| 4.6 | Mentor **NO asignado** consultando a ese aprendiz | igual, header = mentor no asignado | **403** | `{"message":"No tenes visibilidad sobre los informes de ese participante"}` |
| 4.7 | Otro aprendiz sin relación consultando | igual, header = otro aprendiz | **403** | `{"message":"No tenes visibilidad sobre los informes de ese participante"}` |
| 4.8 | ADMIN consultando a cualquier aprendiz | igual, header = admin | **200** | `[]` |
| 4.9 | Repetido después de insertar el fixture, propio | — | **200** | `[{"id":"632fed92...","participanteId":"11111111...","semanaInicio":"2026-08-17","cantidadEntradas":5,"patronDominante":"Enfoque en el futuro","pctPasado":20,"pctPresente":30,"pctFuturo":50,"insight":"El aprendiz muestra un patron consistente de planificacion hacia adelante.","preguntasConfrontacion":["Que estas evitando enfrentar hoy?","Que necesitarias soltar del pasado?"],"creadoEn":"2026-08-26T02:12:44.234983Z"}]` |

**Resultado: 9/9 correctos.** El alcance de mentor asignado (D-47) se confirmó de punta a punta contra la app real: el mentor correcto entra, el mentor equivocado no, con el mismo mensaje que un tercero sin relación (no se puede distinguir "sos mentor pero no el correcto" de "no tenés ninguna relación").

---

## 5. `GET /api/v1/espejo-sombra/{id}` — un informe puntual

| # | Caso | Request | HTTP | Response |
|---|---|---|---|---|
| 5.1 | Happy path, propio | id del fixture | **200** | el informe completo (mismo shape que 4.9) |
| 5.2 | Sin `X-Actor-Id` | — | **400** | `{"message":"Falta el header obligatorio 'X-Actor-Id'"}` |
| 5.3 | Actor inexistente | header = fantasma | **404** | `{"message":"Actor no encontrado: ffffffff-0000-0000-0000-000000000000"}` |
| 5.4 | Actor suspendido | header = suspendido | **403** | `{"message":"Cuenta suspendida"}` |
| 5.5 | Mentor asignado viendo el informe de su aprendiz | — | **200** | el informe completo |
| 5.6 | Mentor **NO asignado** | — | **403**, no 404 | `{"message":"No tenes visibilidad sobre los informes de ese participante"}` |
| 5.7 | Otro aprendiz sin relación | — | **403** | `{"message":"No tenes visibilidad sobre los informes de ese participante"}` |
| 5.8 | Informe inexistente | UUID al azar | **404** | `{"message":"Informe no encontrado: 00000000-1111-2222-3333-444444444444"}` |

**Resultado: 8/8 correctos.** El caso 5.6 es el que más importaba verificar: un mentor sin relación con el informe recibe **403, nunca 404** — si diera 404 ahí, el mensaje distinto al de "informe no encontrado" (5.8) delataría que el informe SÍ existe, exactamente el patrón de fuga que E-30/E-42 identificaron y que acá se confirma que NO ocurre.

---

## Resumen

**38 casos probados, 38 correctos, cero HTTP 500.** Los 5 endpoints del módulo `rag` quedan verificados de punta a punta contra Postgres/Redis reales (no mocks, no Testcontainers): autenticación por header, autorización por rol y por relación (mentor asignado), validación de entrada, manejo de errores sin fuga de información ni de stacktrace, cuota diaria en Redis, y persistencia real contra pgvector.

**No verificado en esta pasada (fuera de alcance de este módulo o requiere IA real):**
- Comportamiento de `chatIAPort.responder(...)`/`EmbeddingPort.generar(...)` con Gemini real conectado (D-39) — hoy ambos son NoOp por falta de credenciales.
- Generación real de un informe de Espejo Sombra por el scheduler semanal (`GenerarInformesSemanalesScheduler`) — se probó el endpoint de lectura con un fixture insertado a mano, no el disparo del scheduler en sí.
- Búsqueda por similaridad semántica real en `PgVectorNativoAdapter.buscarSimilares` (el chunk indexado en 1.1 tiene un vector de ceros, así que cualquier búsqueda de similaridad contra él no es representativa de un embedding real).
