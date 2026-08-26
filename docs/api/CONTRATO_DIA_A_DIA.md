# Contrato REAL: el día a día del aprendiz (`habits`, `rocks`, `evidence`, `points`)

Leído directo del código en `src/main/java/com/renaser/os/{habits,rocks,evidence,points}`. Todo lo que dice
este documento sale de un controller, un DTO (`record`) o el servicio de aplicación que hay detrás. Donde no
pude confirmar algo, lo digo explícito en vez de inventarlo.

## Antes de probar nada

- **No hay autenticación real todavía (B-2).** El actor se resuelve con el header `X-Actor-Id: <UUID>`
  (el UUID de un `User` ya existente). Es el mismo patrón en los 4 módulos — está documentado como
  "temporal, D-29 de `users`" en el javadoc de cada controller.
- **Content-Type**: `application/json` en todo body de `POST`/`PATCH`.
- **Falta el header `X-Actor-Id`** → `400` (`MissingRequestHeaderException`, mensaje `"Falta el header
  obligatorio 'X-Actor-Id'"`). No es un 401 — no hay capa de auth que lo intercepte antes.
- **El almacenamiento de archivos (S3) NO está configurado todavía (D-34).** El único endpoint de URL
  firmada real de este alcance, `POST /api/v1/rocks/{id}/evidence/upload-url`, usa
  `NoOpAlmacenamientoAdapter`, que devuelve literalmente `about:blank#pendiente-s3/<ruta>` en vez de una URL
  de S3 utilizable. Para probar el flujo de evidencia HOY, no subas nada de verdad: llamá igual al
  endpoint de "completar con evidencia" pasando un `bucket`/`rutaStorage` inventados (ej.
  `"test-bucket"` / `"test/foto.jpg"`) — el backend no verifica que el archivo exista en el bucket, solo
  que los campos no estén vacíos.

### Formato de error (`shared.web.ApiErrorResponse`)

Todo error de estos 4 módulos devuelve el mismo shape:

```json
{ "message": "texto explicando el rechazo", "timestamp": "2026-08-26T10:00:00Z" }
```

Tabla real de excepción → HTTP status (`shared/web/GlobalExceptionHandler.java`), válida para los 4 módulos:

| Excepción de dominio/aplicación | HTTP | Cuándo aparece en este alcance |
|---|---|---|
| `NotAuthorizedException` | **403** | No sos el dueño, cuenta `SUSPENDED`, rol equivocado, destino ajeno |
| `NoSuchElementException` | **404** | El id no existe (registro, roca, evidencia, participante) |
| `IllegalArgumentException` / `ConstraintViolationException` | **400** | Regla de negocio violada, enum inválido, EXIF fuera de margen |
| `MethodArgumentNotValidException` (falla `@Valid`) | **400** | Campo con `@NotBlank`/`@NotNull`/`@Size` incumplido |
| `IllegalStateException` | **409** | Estado ya terminal, ya completado, ya existe, plazo vencido |
| `DataIntegrityViolationException` (UNIQUE/CHECK/FK) | **409** | Carrera entre dos requests concurrentes |
| `MethodArgumentTypeMismatchException` | **400** | Path variable con el tipo equivocado (ej. `{tipo}` de ranking que no es un valor del enum, un id que no es UUID) |
| `HttpMessageNotReadableException` | **400** | JSON malformado o tipo incorrecto en el body |

---

## 1. `habits` — hábitos del día, Santuario, racha sin celular, Código Renaser (radar)

Todos los controllers de `habits` en este alcance son **autoservicio estricto**: `actorId` (header) tiene
que ser igual al `participanteId`/dueño del recurso. Si no, `403` ("Solo el propio participante puede
operar..."). Todos, además, validan que la cuenta no esté `SUSPENDED` (`403 "Cuenta suspendida"`) — la capa
de verificación vive en `requireProgreso`/`requireSelf` de cada servicio, no en el controller.

### 1.1 `GET /api/v1/habit-tracks/today`

Hábitos (tracks) del día del propio actor.

- **Headers**: `X-Actor-Id`.
- **Request body**: ninguno.
- **Response 200** — `List<RegistroHabitoResponse>`:

```json
[{
  "id": "uuid", "habitoId": "uuid", "fechaEjecucion": "2026-08-26", "diaPrograma": 12,
  "tipoDia": "DISCIPLINA", "esOpcional": false, "estado": "PENDIENTE", "puntosOtorgados": 0,
  "respuestaTexto": null, "calificacionProductividad": null, "completadoEn": null
}]
```

  `tipoDia`: `DISCIPLINA` cualquier día salvo domingo (`DOMINGO`). El tercer tipo del enum viejo,
  `INTOXICACION` (ciclos fijos), **no está implementado** en esta versión — confirmado en el comentario de
  `RegistroService.resolverTipoDia`.
  `estado`: `PENDIENTE | EN_CURSO | COMPLETADO | FALLIDO | EXPIRADO` (máquina de estados de
  `EstadoRegistro`). `COMPLETADO`/`FALLIDO`/`EXPIRADO` son terminales.

- **Quién puede llamarlo**: cualquier actor, sobre sí mismo (`actorId == participanteId`, siempre, porque el
  endpoint no toma un id de otro en la URL — ambos son el mismo header).
- **Errores**: `403` si la cuenta está suspendida.

```bash
curl -s http://localhost:8080/api/v1/habit-tracks/today \
  -H "X-Actor-Id: <UUID_ACTOR>"
```

**Trampa**: si nunca corrió el generador nocturno de tracks para hoy (`GenerarTracksDelDiaUseCase`, un
scheduler), esta lista puede venir vacía aunque el catálogo tenga hábitos — no hay un endpoint síncrono en
este alcance que dispare la generación bajo demanda.

### 1.2 `POST /api/v1/habit-tracks/{id}/complete`

Completa un hábito directo (`CHECKBOX`/`JOURNALING`/`CALIFICACION`). **NO sirve para hábitos `BLOQUEO`**
(ver trampa abajo).

- **Headers**: `X-Actor-Id`.
- **Path**: `{id}` = UUID del `RegistroHabito` (el track, no el hábito del catálogo).
- **Request body** (`CompletarRegistroRequest`):

| Campo | Tipo | Validación |
|---|---|---|
| `respuestaTexto` | `String` | opcional, `@Size(max = 4000)` |
| `calificacionProductividad` | `Integer` | opcional, sin rango validado en el DTO |

  Sin campo `puntos`: **el otorgamiento SIEMPRE lo calcula el servidor** (comentario explícito del DTO).

- **Response 200** — `RegistroHabitoResponse` (mismo shape que 1.1), con `estado: "COMPLETADO"` y
  `puntosOtorgados` ya calculado.
- **Quién puede llamarlo**: solo el dueño del track (`actorId == registro.participanteId`).
- **Errores**:
  - `404` `"Registro no encontrado: <id>"` — id inexistente.
  - `403` `"Solo el propio participante puede operar sobre sus habitos"` — no sos el dueño.
  - `403` `"Cuenta suspendida"`.
  - `400` `"Los habitos BLOQUEO (Santuario) se completan via /habit-tracks/{id}/santuario, no aca"` — **este
    es el mensaje exacto** que devuelve intentar completar un hábito de Santuario por acá.
  - `409` `"El habito expiro — no se puede completar"` — la ventana de entrega venció (el registro pasa a
    `EXPIRADO` en el mismo request, no es un simple rechazo).
  - `409` `"Este registro no puede completarse: <estado>"` — ya estaba en un estado terminal
    (`COMPLETADO`/`FALLIDO`/`EXPIRADO`).

```bash
curl -s -X POST http://localhost:8080/api/v1/habit-tracks/<REGISTRO_ID>/complete \
  -H "X-Actor-Id: <UUID_ACTOR>" -H "Content-Type: application/json" \
  -d '{"respuestaTexto": "Lo hice a las 7am", "calificacionProductividad": 8}'
```

**Cómo se calculan los puntos (para interpretar `puntosOtorgados`)** — `ResultadoOtorgamiento.calcular`,
solo si el hábito tiene horario configurado (si no, siempre 0 puntos):

| Cuándo entregás | Fase | Puntos |
|---|---|---|
| Antes o en el instante ancla (hora fin, o inicio si no hay fin) | `A_TIEMPO` | 10 |
| Hasta 10 min después (gracia) | `GRACIA` | `max(5, 10 - floor(minutosTarde/2))` |
| Después de la gracia, dentro de la extensión del hábito (default 3h) | `EXTENDIDO` | 3 |
| Pasada la extensión | — | el registro expira, no se puede completar (ver 409 arriba) |

### 1.3 `POST /api/v1/habit-tracks/{id}/evidence`

Sube evidencia de un registro diario (D-H6). Delega en `evidence.api.RegistrarEvidenciaPort` — **es un paso
independiente de completar**: un hábito con evidencia `OPCIONAL` puede completarse sin pasar por acá.

- **Headers**: `X-Actor-Id`.
- **Path**: `{id}` = UUID del `RegistroHabito`.
- **Request body** (`SubirEvidenciaRegistroRequest`):

| Campo | Tipo | Validación |
|---|---|---|
| `tipo` | `String` | `@NotBlank`. Valores válidos: `FOTO`, `VIDEO`, `AUDIO`, `TEXTO`, `CAPTURA` |
| `bucket` | `String` | obligatorio si `tipo != TEXTO` (validado en el dominio, no en el DTO) |
| `rutaStorage` | `String` | obligatorio si `tipo != TEXTO` |
| `contenidoTexto` | `String` | obligatorio si `tipo == TEXTO` |
| `timestampExif` | `Instant` | opcional |
| `gpsLat` | `Double` | opcional, viaja junto con `gpsLng` (los dos o ninguno), rango -90..90 |
| `gpsLng` | `Double` | opcional, rango -180..180 |

  **Trampa**: a diferencia de `rocks`, `habits` **no tiene un endpoint propio de URL firmada**
  (`upload-url`). El cliente tiene que conseguir `bucket`/`rutaStorage` por su cuenta (o, para probar,
  inventarlos — ver nota general sobre S3 arriba).

- **Response 200** — `EvidenciaRegistroResponse`:

```json
{ "id": "uuid", "estadoValidacion": "PENDIENTE" }
```

  `estadoValidacion` arranca siempre en `PENDIENTE` (la validación IA corre después, async, vía el
  scheduler de `evidence`).

- **Quién puede llamarlo**: solo el dueño del registro.
- **Errores**: `404` registro inexistente; `403` no sos el dueño o cuenta suspendida; `400` si falta
  `bucket`/`rutaStorage` (tipo no-texto) o `contenidoTexto` (tipo texto), o si `tipo` no es un valor válido
  del enum (`IllegalArgumentException` de `TipoEvidencia.valueOf`, mensaje genérico de Java tipo
  `"No enum constant ...TipoEvidencia.FOO"`, no un mensaje de negocio); `400` si mandás solo una de
  `gpsLat`/`gpsLng`, o fuera de rango.

```bash
curl -s -X POST http://localhost:8080/api/v1/habit-tracks/<REGISTRO_ID>/evidence \
  -H "X-Actor-Id: <UUID_ACTOR>" -H "Content-Type: application/json" \
  -d '{"tipo": "FOTO", "bucket": "test-bucket", "rutaStorage": "habits/test/foto.jpg"}'
```

### 1.4 Santuario — hábitos `BLOQUEO` (`/api/v1/habit-tracks/{id}/santuario/...`)

**La regla real** (`PoliticaSantuario` + `RegistroPoliticasHabito`): cualquier hábito de tipo `BLOQUEO` no
se completa con el gesto genérico de 1.2 — tiene su propio ciclo de vida (`iniciar → completar` o
`iniciar → romper`), con estado propio en la tabla `sesiones_bloqueo`. `RegistroService.completar` consulta
una tabla de políticas indexada por tipo/clave de hábito; para `BLOQUEO` esa política siempre devuelve
"no procede, andá a `/santuario`" — es la razón exacta del 400 documentado en 1.2.

#### `POST /api/v1/habit-tracks/{id}/santuario/start`

- **Request body**: ninguno.
- **Response 200** — `SesionBloqueoResponse`:

```json
{ "registroHabitoId": "uuid", "estado": "ACTIVA", "iniciadaEn": "...", "terminadaEn": null,
  "duracionMinimaMin": 0, "motivoSalida": null }
```

- **Errores**: `404` registro/hábito inexistente; `400` `"Este habito no es de tipo Santuario (BLOQUEO)"` si
  intentás iniciar Santuario sobre un hábito que no es `BLOQUEO`; `409` `"Ya existe una sesion de Santuario
  para este registro"`; `409` `"Todavia no es la hora de iniciar tu Santuario"` si el hábito tiene horario y
  todavía no llegó la hora de disparo.

#### `POST /api/v1/habit-tracks/{id}/santuario/complete`

- **Request body**: ninguno.
- **Response 200** — `SesionBloqueoResponse` con `estado: "COMPLETADA"`.
- **Idempotente**: si ya estaba completada, devuelve la sesión tal cual (no error).
- Otorga puntos con la misma escala que un hábito común (ver tabla de 1.2) — **decisión H-2, documentada
  como no confirmada por negocio**: el repo viejo no otorgaba puntos explícitamente al completar Santuario,
  se asumió por consistencia que sí.

#### `POST /api/v1/habit-tracks/{id}/santuario/break`

- **Request body** (`RomperSantuarioRequest`):

| Campo | Tipo | Validación |
|---|---|---|
| `motivo` | enum `MotivoSalidaBloqueo` | `@NotNull`. Valores: `SALIDA_TEMPRANA`, `VIOLACION_APP_USADA`, `MANUAL` |
| `evidenciaBucket` | `String` | opcional |
| `evidenciaRuta` | `String` | opcional |

- **Response 200** — `SesionBloqueoResponse` con `estado: "ROTA"`, `motivoSalida` reflejando lo enviado.
- **Idempotente** igual que `complete`.
- **Penaliza puntos** (`SesionBloqueo.PENALIZACION_ROTURA_PUNTOS`, `MotivoPuntos.SANCTUARY_BREAK`) y marca el
  registro `FALLIDO` — a diferencia de la racha sin celular (1.5), que nunca penaliza.

```bash
curl -s -X POST http://localhost:8080/api/v1/habit-tracks/<REGISTRO_ID>/santuario/start -H "X-Actor-Id: <UUID>"
curl -s -X POST http://localhost:8080/api/v1/habit-tracks/<REGISTRO_ID>/santuario/complete -H "X-Actor-Id: <UUID>"
curl -s -X POST http://localhost:8080/api/v1/habit-tracks/<REGISTRO_ID>/santuario/break \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" -d '{"motivo": "SALIDA_TEMPRANA"}'
```

### 1.5 Racha sin celular / "Día sin celular" (`phone-free`)

**Trampa central, tal como se pidió confirmar**: `start` SÍ lleva `{id}` en la ruta (necesita saber sobre
qué `RegistroHabito` arranca la racha); `complete` y `break` **NO llevan `{id}`** — el backend resuelve la
racha activa del actor directamente (`RachaService.requireRachaActiva(actorId)`, "solo puede haber una racha
activa por participante a la vez").

Honor-based: a diferencia de Santuario, romper la racha **nunca penaliza puntos** (comentario explícito en
`RachaService`).

#### `POST /api/v1/habit-tracks/{id}/phone-free/start`

- **Path**: `{id}` = UUID del `RegistroHabito` — **tiene que ser el track de un hábito cuyo
  `claveSistema == "PHONE_FREE_DAY"`**, si no: `400 "Este habito no lleva rachas sin celular"`.
- **Request body** (`IniciarRachaRequest`):

| Campo | Tipo | Validación |
|---|---|---|
| `horasObjetivo` | `Integer` | `@NotNull`. Solo se acepta uno de los 8 hitos: **3, 6, 9, 12, 15, 18, 21, 24** (validado en el dominio, no en el DTO — si mandás otro valor, `400 "La meta debe ser un multiplo de 3 hasta 24: <valor>"`) |

- **Response 200** — `RachaSinCelularResponse`:

```json
{ "id": "uuid", "iniciadaEn": "...", "horasObjetivo": 24, "estado": "ACTIVA",
  "minutosTranscurridos": 0, "hitoAlcanzado": 0, "plazoCierre": "..." }
```

- **Errores**: `404` registro/hábito inexistente; `400` hábito no es de racha sin celular o `horasObjetivo`
  inválido; `403` cuenta suspendida o no sos el dueño; `409` `"Ya tienes una racha en curso — cierrala o
  rompela antes de empezar otra"`.

#### `POST /api/v1/habit-tracks/phone-free/complete`

- **Sin `{id}` en la ruta.** Cierra la racha ACTIVA del actor.
- **Request body**: ninguno.
- **Response 200** — `RachaSinCelularResponse`. `estado` queda `"COMPLETADA"` si llegaste al ciclo de 24h
  completo, o `"ROTA"` si cerraste antes (pero después del mínimo).
- **Errores**:
  - `404` `"No tienes ninguna racha en curso"` — no hay racha activa para este actor.
  - `409` `"Todavia no llegas al primer hito de 3 horas"` — mínimo de 180 minutos para poder cerrar.
  - `409` `"El plazo para cerrar esta racha ya vencio"` — pasaste el plazo de cierre (inicio + 24h + 3h de
    extensión default); la racha se marca `EXPIRADA` en el mismo request.
- Solo el ciclo completo (24h reales, medido por duración, no puntualidad) otorga puntos: **10 puntos
  fijos**, motivo `HABIT_COMPLETED` — nunca pasa por la tabla de fases de 1.2.

#### `POST /api/v1/habit-tracks/phone-free/break`

- **Sin `{id}` en la ruta.**
- **Request body** (`RomperRachaRequest`) — **opcional, `required = false`** (podés mandar el POST sin body):

| Campo | Tipo | Validación |
|---|---|---|
| `motivo` | `String` | libre, sin `@NotBlank`, sin validar contra ningún enum |

- **Response 200** — `RachaSinCelularResponse` con `estado: "ROTA"`.
- **Nunca penaliza puntos.**
- **Errores**: `404` `"No tienes ninguna racha en curso"`.

```bash
curl -s -X POST http://localhost:8080/api/v1/habit-tracks/<REGISTRO_ID_PHONE_FREE>/phone-free/start \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" -d '{"horasObjetivo": 3}'

curl -s -X POST http://localhost:8080/api/v1/habit-tracks/phone-free/complete -H "X-Actor-Id: <UUID>"

curl -s -X POST http://localhost:8080/api/v1/habit-tracks/phone-free/break \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" -d '{"motivo": "urgencia laboral"}'
```

### 1.6 Código Renaser / check-in "Radar" (`/api/v1/radar`)

**Restringido a rol `TRAINEE` exclusivamente** — no es autoservicio genérico para cualquier rol, es
literal `"El Codigo Renaser es exclusivo de aprendices"` si un `MENTOR`/`ADMIN`/etc. lo intenta sobre sí
mismo.

#### `POST /api/v1/radar`

- **Request body** (`RegistrarCheckInRadarRequest`) — nombres de campo en inglés, calcados del contrato
  viejo:

| Campo | Tipo | Validación |
|---|---|---|
| `whatAmIDoing` | `String` | `@NotBlank`, `@Size(max = RegistroRadar.TEXTO_MAX_LENGTH)` |
| `whatAmIThinking` | `String` | igual |
| `whatAmIFeeling` | `String` | igual |
| `energyLevel` | `Integer` | `@NotNull`, `@Min/@Max` según `RegistroRadar.NIVEL_ENERGIA_MIN/MAX` (no pude confirmar el rango exacto sin leer `RegistroRadar` — está definido como constantes ahí) |
| `whatAmIAvoiding` | `String` | `@NotBlank`, `@Size(max = ...)` |

- **Response 200** — `RegistroRadarResponse`:

```json
{ "id": "uuid", "whatAmIDoing": "...", "whatAmIThinking": "...", "whatAmIFeeling": "...",
  "energyLevel": 7, "whatAmIAvoiding": "...", "createdAt": "..." }
```

#### `GET /api/v1/radar/latest`

- **Response 200** — `{ "createdAt": null | "instant" }` — solo el timestamp del último check-in, o `null`
  si nunca hizo uno.

#### `GET /api/v1/radar/history?cursor=<instant>`

- **Query param** `cursor` opcional (ISO-8601 Instant) — paginación por cursor, tamaño de página fijo **20**.
- **Response 200** — `{ "entries": [RegistroRadarResponse...], "nextCursor": null | "instant" }`.
  `nextCursor` viene `null` cuando la página no llegó a 20 elementos (heurística sin conteo extra, calcada
  del cliente viejo — puede haber falsos negativos raros en el borde, es la misma heurística que ya usaban).

```bash
curl -s -X POST http://localhost:8080/api/v1/radar \
  -H "X-Actor-Id: <UUID_TRAINEE>" -H "Content-Type: application/json" \
  -d '{"whatAmIDoing":"Trabajando","whatAmIThinking":"En la reunion","whatAmIFeeling":"Enfocado","energyLevel":7,"whatAmIAvoiding":"Revisar el celular"}'

curl -s http://localhost:8080/api/v1/radar/latest -H "X-Actor-Id: <UUID_TRAINEE>"
curl -s "http://localhost:8080/api/v1/radar/history" -H "X-Actor-Id: <UUID_TRAINEE>"
```

---

## 2. `rocks` — Roca Maestra, Roca Semanal, Roca Diaria, Modo Verdugo

Autoservicio estricto en todos los endpoints, con una regla adicional constante en `rocks`: **solo rol
`TRAINEE`** puede operar (`"Solo un aprendiz opera sus propias rocas"` / `"...registra sus propios eventos
Verdugo"`) — a diferencia de `habits`, que no restringe por rol.

### 2.1 `GET /api/v1/rocks/master`

Las 3 Rocas Maestras del actor (una por eje: `CUERPO`, `TRABAJO`, `RELACIONES`). No hay endpoint de creación
en este alcance — se asume que se crean en onboarding.

- **Response 200** — `List<RocaMaestraResponse>`: `{ "id", "eje", "objetivo", "creadoEn" }`.

```bash
curl -s http://localhost:8080/api/v1/rocks/master -H "X-Actor-Id: <UUID>"
```

### 2.2 Roca Semanal (`/api/v1/rocks/weekly`)

#### `GET /api/v1/rocks/weekly?semana=<int>`

- **Query param** `semana` opcional — si no se manda, usa la semana actual del participante
  (`SemanaPrograma.numeroSemanaParaFecha`).
- **Response 200** — `List<RocaSemanalResponse>`.

#### `POST /api/v1/rocks/weekly`

Crea el plan semanal — **exactamente una roca por eje, los 3 ejes, ni más ni menos**.

- **Request body** (`CrearPlanSemanalRequest`):

```json
{ "rocas": [
  { "eje": "CUERPO", "titulo": "...", "accionCritica1": "...", "accionCritica2": "...",
    "accionCritica3": "...", "obstaculo": "...", "contingencia": "...", "autoevaluacionInicio": 5 },
  { "eje": "TRABAJO", "titulo": "..." },
  { "eje": "RELACIONES", "titulo": "..." }
]}
```

| Campo (item) | Tipo | Validación |
|---|---|---|
| `eje` | `String` | `@NotBlank`, tiene que ser `CUERPO`/`TRABAJO`/`RELACIONES` |
| `titulo` | `String` | `@NotBlank` |
| `accionCritica1/2/3` | `String` | sin `@NotBlank` en el DTO (libres) |
| `obstaculo`, `contingencia` | `String` | libres |
| `autoevaluacionInicio` | `Integer` | libre, sin rango validado en el DTO |

- **Response 201** — `List<RocaSemanalResponse>`.
- **Errores**: `403` `"ROCKS_LOCKED: completa tu onboarding antes de planificar rocas"` (no tenés las 3
  Rocas Maestras todavía); `400` `"se requiere exactamente una roca semanal por eje (CUERPO, TRABAJO,
  RELACIONES)"` si mandás ejes repetidos o incompletos; `409` `"ALREADY_PLANNED: la semana <n> ya tiene
  rocas planificadas"`.

#### `PATCH /api/v1/rocks/weekly/{id}`

Edición parcial (`EditarRocaSemanalRequest` — campo ausente/`null` = no se toca):

```json
{ "titulo": "nuevo titulo", "accionesCriticas": ["a1","a2","a3"], "obstaculo": "...",
  "contingencia": "...", "autoevaluacionInicio": 6 }
```

**Trampa del nombre**: el caso de uso se llama `EditarDentroDe48hUseCase`, pero **la ventana real NO es 48h
fijas** (decisión RK-5, documentada en el propio código: el nombre se conservó tal cual lo pidió el encargo
original, la regla de negocio cambió). La ventana real es el **"Domingo Ritual"**:

- Abre el **domingo a las 12:00** hora local del participante y cierra el **lunes a las 09:00**.
- Si la roca se creó DENTRO de esa ventana, después de que cierra ya **no se puede editar más** (hasta que
  vuelva a abrir el domingo siguiente).
- Si se creó A DESTIEMPO (fuera de la ventana), hay **2 horas de margen** para corregirla (tope: el margen
  de 2h, o el fin del día local de creación, lo que ocurra primero).
- Si la ventana normal está abierta AHORA (aunque la roca se haya creado a destiempo antes), siempre se
  puede editar.

- **Errores**: `403` `"La ventana para editar esta roca semanal ya cerro"`; `403` si la roca no es tuya
  (`"Esta roca semanal no pertenece al actor"`); `404` roca inexistente.

#### `PATCH /api/v1/rocks/weekly/{id}/review`

Cierre de la revisión semanal — **sin restricción de ventana** en el servicio (`cerrar` no valida plazo, a
diferencia de `editar`).

- **Request body** (`CerrarSemanaRequest`, **sin validación `@Valid` real de contenido** — todos los campos
  son primitivos/`String` libres, `autoevaluacionFin` es `int` así que si no lo mandás, JSON lo interpreta
  como `0`, no como error):

```json
{ "autoevaluacionFin": 8, "bloqueoPrincipal": "...", "correccion": "..." }
```

- **Response 200** — `RocaSemanalResponse` con `autoevaluacionFin`/`bloqueoPrincipal`/`correccion` seteados.

```bash
curl -s -X POST http://localhost:8080/api/v1/rocks/weekly \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" \
  -d '{"rocas":[{"eje":"CUERPO","titulo":"Entrenar"},{"eje":"TRABAJO","titulo":"Cerrar proyecto"},{"eje":"RELACIONES","titulo":"Llamar a mi hermano"}]}'

curl -s -X PATCH http://localhost:8080/api/v1/rocks/weekly/<ROCA_SEMANAL_ID> \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" -d '{"titulo":"Entrenar 5 dias"}'

curl -s -X PATCH http://localhost:8080/api/v1/rocks/weekly/<ROCA_SEMANAL_ID>/review \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" \
  -d '{"autoevaluacionFin": 8, "bloqueoPrincipal": "Falta de tiempo", "correccion": "Bloquear agenda"}'
```

### 2.3 Roca Diaria (`/api/v1/rocks/...`)

#### `GET /api/v1/rocks/today` / `GET /api/v1/rocks/tomorrow`

- **Response 200** — `List<RocaDiariaResponse>`. `today` incluye `bloqueada` real (ver Ley Pareto abajo);
  `tomorrow` siempre trae `bloqueada: false` (no se recalcula para el futuro).

```json
{ "id":"uuid","fecha":"2026-08-26","posicion":1,"titulo":"...","descripcion":null,"color":"VERDE",
  "puntajeImpacto":8,"esDelegable":false,"eje":"CUERPO","rocaSemanalId":"uuid",
  "horaInicio":"07:00:00","horaFin":"08:00:00","completada":false,"completadaEn":null,
  "puntosOtorgados":0,"bloqueada":false }
```

#### `POST /api/v1/rocks/plan`

Planifica las Rocas Diarias de una fecha — **1 a 3 rocas por eje**, con **posiciones contiguas empezando en
1** (1=VERDE, 2=AMARILLA, 3=ROJA — el color se deriva de la posición, nunca se manda suelto).

- **Request body** (`CrearPlanDiarioRequest`):

```json
{ "fecha": "2026-08-27", "rocas": [
  { "eje": "CUERPO", "posicion": 1, "titulo": "...", "descripcion": null, "puntajeImpacto": 8,
    "esDelegable": false, "horaInicio": "07:00:00", "horaFin": "08:00:00" }
]}
```

| Campo | Tipo | Validación |
|---|---|---|
| `fecha` | `LocalDate` | `@NotNull` |
| `rocas` | `List` | `@NotEmpty`, cada item `@Valid` |
| `eje` | `String` | `@NotBlank` |
| `posicion` | `int` | 1..3, contiguo por eje (validado en el servicio) |
| `titulo` | `String` | `@NotBlank`; en el dominio además: 1..500 chars |
| `descripcion` | `String` | opcional, dominio: máx 2000 chars |
| `puntajeImpacto` | `int` | dominio: 1..10 |
| `esDelegable` | `boolean` | — |
| `horaInicio`/`horaFin` | `LocalTime` | opcionales |

  **Fecha admitida**: si estás DENTRO de la ventana de planificación diaria (`VentanaPlanificacionDiaria`),
  solo se admite planificar **mañana**; si estás A DESTIEMPO, se admite **hoy o mañana**. Fuera de esas
  fechas: `400 "INVALID_DATE: la fecha de planificacion debe ser [fechas]"`.

- **Response 201** — `List<RocaDiariaResponse>`.
- **Errores**: `403` `"ROCKS_LOCKED..."` (sin las 3 Rocas Maestras); `400` `"cada eje debe tener entre 1 y 3
  rocas"` / `"las posiciones de <eje> deben empezar en 1 sin huecos"`; `400`
  `"NO_WEEKLY_ROCK: no hay plan semanal activo para el eje <eje>"` (no planificaste la semana todavía);
  `409` `"ALREADY_PLANNED: ya existen rocas planificadas para <fecha>"`.

#### `POST /api/v1/rocks/{id}/evidence/upload-url` — paso 1 del flujo de evidencia

- **Request body** (`SolicitarUrlAdjuntoRequest`): `{ "tipoContenido": "image/jpeg" }` (sin validación
  `@NotBlank` en el DTO — puede ir vacío/null sin que el DTO lo rechace).
- **Response 200** — `UrlAdjuntoResponse`: `{ "uploadUrl": "...", "bucket": "renaser-files", "ruta":
  "rocas/<actorId>/<rocaDiariaId>" }`. Bucket fijo `renaser-files` para TODA la evidencia de rocas
  (`RocaDiariaService.BUCKET_ROCAS`).
  **HOY `uploadUrl` es `about:blank#pendiente-s3/<ruta>` (ver nota general de S3 arriba) — no subas nada de
  verdad ahí, la URL no es funcional todavía.**
- **Errores**: `404` roca inexistente; `403` no es tuya o no sos TRAINEE activo; `409`
  `"Esta roca ya tiene evidencia registrada"` si ya está completada.

#### `POST /api/v1/rocks/{id}/evidence` — paso 2: completar con evidencia

- **Request body** (`CompletarRocaDiariaRequest`) — mismo shape que `SubirEvidenciaRegistroRequest` de
  `habits` (1.3): `tipo` (`@NotBlank`, valores `FOTO/VIDEO/AUDIO/TEXTO/CAPTURA`), `bucket`, `rutaStorage`
  (usá el `bucket`/`ruta` que te devolvió el paso 1, o inventalos si estás probando sin S3 real),
  `contenidoTexto`, `timestampExif`, `gpsLat`, `gpsLng`.
- **Response 200** — `RocaDiariaResponse` con `completada: true`, `puntosOtorgados` ya calculado.
- **Regla EXIF (Ley VI)**: si `tipo == "FOTO"`, `timestampExif` no puede diferir de "ahora" (instante de
  subida) por más de **15 minutos** — si no: `400 "EXIF_MISMATCH: el timestamp de la foto difiere mas de 15
  minutos del instante de subida"`. Para otros tipos no se valida.
- **Bloqueo Pareto (Ley IV)**: una roca `AMARILLA`/`ROJA` no se puede completar hasta que la `VERDE` del
  mismo eje/día tenga evidencia — `403 "GREEN_NOT_EVIDENCED: primero hay que completar la roca VERDE de
  este eje"`. `VERDE` nunca está bloqueada.
- **Otros errores**: `409 "ALREADY_COMPLETED: esta roca ya tiene evidencia"`; `404` roca inexistente; `403`
  no es tuya.
- **Puntos** (`EscalaPuntosRoca`, misma escala que hábitos): motivo `ROCK_COMPLETED` o `ROCK_EXTENDED` según
  si entregaste antes/después de `horaFin`. Se paga **una sola vez** (`puedeOtorgarPuntos`).

```bash
# Paso 1 (opcional/no funcional hoy — podés saltarlo directo al paso 2 con datos inventados)
curl -s -X POST http://localhost:8080/api/v1/rocks/<ROCA_DIARIA_ID>/evidence/upload-url \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" -d '{"tipoContenido": "image/jpeg"}'

# Paso 2
curl -s -X POST http://localhost:8080/api/v1/rocks/<ROCA_DIARIA_ID>/evidence \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" \
  -d '{"tipo":"FOTO","bucket":"renaser-files","rutaStorage":"rocas/<actor>/<roca>","timestampExif":"2026-08-26T10:00:00Z"}'
```

### 2.4 Modo Verdugo (`/api/v1/enforcer-events`)

Registra la reacción del aprendiz cuando se le vence el plazo de una roca o un hábito.

#### `GET /api/v1/enforcer-events`

- **Response 200** — `List<EventoVerdugoResponse>` del actor.

#### `POST /api/v1/enforcer-events`

- **Request body** (`RegistrarEventoVerdugoRequest`):

| Campo | Tipo | Validación |
|---|---|---|
| `destinoTipo` | `String` | `@NotBlank`. **Únicos valores válidos**: `ROCA_DIARIA`, `REGISTRO_HABITO` |
| `destinoId` | `UUID` | `@NotNull` — el id de la roca diaria o del registro de hábito |
| `disparadoEn` | `Instant` | `@NotNull` |
| `resultado` | `String` | `@NotBlank`. Valores aceptados del CLIENTE: `COMPLETADO`, `POSTERGADO`,
  `POSPUESTO_30`. **`IGNORADO` existe en el enum pero está prohibido para el cliente** — lo asigna
  exclusivamente el barrido nocturno de las 23:55; si lo mandás: `400 "IGNORADO lo asigna el barrido
  nocturno, no el cliente"` |

- **Response 201** — `EventoVerdugoResponse`: `{ "id", "destinoTipo", "destinoId", "disparadoEn",
  "resultado", "resueltoEn" }`.
- **Regla real, la que se pidió confirmar**: **el destino tiene que pertenecerte.** El servicio verifica
  ownership según el tipo:
  - `ROCA_DIARIA` → carga la `RocaDiaria` por `destinoId` y compara `participanteId`. Si el id no existe:
    `404 "Roca diaria no encontrada: <id>"`.
  - `REGISTRO_HABITO` → `verificarDestinoPort.registroHabitoPerteneceA(destinoId, actorId)` (consulta
    cross-módulo hacia `habits`).
  - Si el destino existe pero es de OTRO participante: `403 "Ese destino no te pertenece"` (cierra el
    hallazgo E-38: antes se podía ensuciar el historial de otro).
- **Otros errores**: `403` cuenta suspendida o rol distinto de `TRAINEE`.

```bash
curl -s http://localhost:8080/api/v1/enforcer-events -H "X-Actor-Id: <UUID>"

curl -s -X POST http://localhost:8080/api/v1/enforcer-events \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" \
  -d '{"destinoTipo":"ROCA_DIARIA","destinoId":"<ROCA_PROPIA_ID>","disparadoEn":"2026-08-26T20:00:00Z","resultado":"POSTERGADO"}'

# Contra un destino ajeno -> 403 "Ese destino no te pertenece"
curl -s -X POST http://localhost:8080/api/v1/enforcer-events \
  -H "X-Actor-Id: <UUID>" -H "Content-Type: application/json" \
  -d '{"destinoTipo":"ROCA_DIARIA","destinoId":"<ROCA_DE_OTRO_ID>","disparadoEn":"2026-08-26T20:00:00Z","resultado":"POSTERGADO"}'
```

---

## 3. `evidence` — consulta y administración de evidencia

Este módulo no expone un endpoint propio de "subir evidencia" — eso pasa siempre por `habits`/`rocks`
(sección 1.3 / 2.3), que llaman internamente a `evidence.api.RegistrarEvidenciaPort`.

### 3.1 `GET /api/v1/evidence/{id}`

- **Headers**: `X-Actor-Id`.
- **Quién puede llamarlo**: el dueño de la evidencia, o un actor `ADMIN`/`ALCHEMIST` activo (verificado
  dentro de `EvidenciaService`, no en el controller).
- **Response 200** — `EvidenciaResponse`:

```json
{ "id":"uuid","participanteId":"uuid","registroHabitoId":"uuid|null","rocaDiariaId":"uuid|null",
  "registroEspirituId":"uuid|null","tipo":"FOTO","contenidoTexto":null,"timestampExif":"...",
  "subidaEn":"...","gpsLat":null,"gpsLng":null,"esPrincipal":true,"estadoValidacion":"PENDIENTE",
  "notasValidacion":null,"intentosIa":0,"penalizacionAplicada":false,"publicadaEnMuro":false }
```

  Exactamente uno de `registroHabitoId`/`rocaDiariaId`/`registroEspirituId` viene no-nulo (arco exclusivo).

- **Máquina de estados de `estadoValidacion`** (comentario de `EstadoValidacion`, no confirmado que la IA
  esté conectada de verdad — ver trampa abajo):

```
PENDIENTE --IA aprueba--> VALIDA
PENDIENTE --IA rechaza--> RECHAZADA
PENDIENTE --IA falla/no disponible, intentosIa < 3--> PENDIENTE (incrementa intentosIa)
PENDIENTE --IA falla/no disponible, intentosIa == 3--> REVISION_MANUAL
REVISION_MANUAL --admin aprueba--> VALIDA
REVISION_MANUAL --admin rechaza--> RECHAZADA
VALIDA/RECHAZADA --admin anula--> ANULADA_ADMIN
```

  **Trampa**: `NoOpEvidenciaValidacionIAAdapter` existe en el código — la IA de validación **no está
  conectada en este alcance**, siempre responde `NO_DISPONIBLE`. En la práctica, toda evidencia que subís
  se queda en `PENDIENTE` y, tras 3 corridas del scheduler (`ProcesarColaValidacionScheduler`), cae sola a
  `REVISION_MANUAL` — nunca vas a ver `VALIDA`/`RECHAZADA` por la vía automática hoy, solo por el endpoint
  admin de 3.2.

- **Errores**: `404` evidencia inexistente; `403` `"Solo ADMIN/ALCHEMIST administran evidencia ajena"` si no
  sos el dueño ni admin.

```bash
curl -s http://localhost:8080/api/v1/evidence/<EVIDENCIA_ID> -H "X-Actor-Id: <UUID>"
```

### 3.2 Admin (`/api/v1/admin/evidence`) — solo `ADMIN`/`ALCHEMIST`

Gateado **dentro del servicio**, no en el controller (comentario explícito: CLAUDE.MD §5.4.6).

#### `POST /api/v1/admin/evidence/{id}/review`

- **Request body** (`RevisarManualmenteRequest`): `{ "aprobar": true|false, "notas": "texto opcional" }`
  (sin `@NotBlank` en `notas`). `aprobar=true` → `VALIDA`; `aprobar=false` → `RECHAZADA`. Aplica solo a
  evidencia en `REVISION_MANUAL` (la validación de estado vive en el dominio `Evidencia`, no confirmé el
  mensaje exacto de rechazo si se llama fuera de ese estado — no lo pude leer en este alcance).
- **Response 200** — `EvidenciaResponse` actualizado.
- **Errores**: `403` actor no admin o suspendido; `404` evidencia inexistente.

#### `POST /api/v1/admin/evidence/{id}/void`

- **Request body** (`AnularVeredictoRequest`): `{ "notas": "motivo de la anulacion" }` — `notas` es
  `@NotBlank`, obligatorio acá (a diferencia de `review`).
- **Response 200** — `EvidenciaResponse` con `estadoValidacion: "ANULADA_ADMIN"`.
- **Errores**: `403` no admin; `404` evidencia inexistente.

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/evidence/<EVIDENCIA_ID>/review \
  -H "X-Actor-Id: <UUID_ADMIN>" -H "Content-Type: application/json" -d '{"aprobar": true, "notas": "OK"}'
```

---

## 4. `points` — puntaje y ranking

### 4.1 `GET /api/v1/points/{participanteId}`

- **Headers**: `X-Actor-Id`.
- **Quién puede llamarlo**: el propio participante, o un `ADMIN`/`ALCHEMIST` activo. Si sos el propio
  participante, también se re-verifica que estés `ACTIVE` (capa de defensa adicional, comentario explícito
  en `PuntajeService.consultar`).
- **Response 200** — `PuntajeResponse`:

```json
{ "participanteId": "uuid", "coherencia": 0.00, "puntosLiga": 120, "rachaActual": 3, "rachaMaxima": 7 }
```

  Si el participante nunca tuvo un ajuste, devuelve un `PuntajeParticipante.inicial(...)` calculado en
  memoria (no lanza 404) — **no lo confundas con que el endpoint no falla nunca**: si el `participanteId`
  no corresponde a ningún actor real, el error va a salir recién al intentar autorizar (`actor no
  encontrado`), no de este endpoint puntual.
- **Errores**: `403` `"Solo el propio participante o un administrativo pueden ver este puntaje"`; `403`
  `"Cuenta suspendida"` (viendo tu propio puntaje estando suspendido).

```bash
curl -s http://localhost:8080/api/v1/points/<PARTICIPANTE_ID> -H "X-Actor-Id: <UUID_MISMO_O_ADMIN>"
```

### 4.2 `POST /api/v1/points/adjustments` — ajuste manual (solo ADMIN/ALCHEMIST)

- **Request body** (`AjustarPuntosManualRequest`):

| Campo | Tipo | Validación |
|---|---|---|
| `participanteId` | `String` | `@NotBlank` |
| `delta` | `Integer` | `@NotNull` (puede ser negativo) |
| `nota` | `String` | opcional |

  Sin campo `motivo`: el motivo **siempre** se fuerza a `MANUAL_ADJUSTMENT` server-side (mismo blindaje de
  mass-assignment que en `users`).

- **Response 201** — `AjustePuntosResponse`: `{ "id": 123, "deltaAplicado": -10, "saldoPosterior": 110 }`.
- **Errores**: `403` `"Solo ADMIN/ALCHEMIST activos hacen ajustes manuales de puntos"`; `404`
  `"Participante no encontrado: <id>"` si el participante no está inscripto en el programa
  (`participacionProgramaFinder`, no simplemente si el `User` existe).

```bash
curl -s -X POST http://localhost:8080/api/v1/points/adjustments \
  -H "X-Actor-Id: <UUID_ADMIN>" -H "Content-Type: application/json" \
  -d '{"participanteId": "<UUID_PARTICIPANTE>", "delta": -10, "nota": "Penalizacion manual por revision"}'
```

### 4.3 `GET /api/v1/ranking/{tipo}?fecha=<YYYY-MM-DD>`

- **Headers**: `X-Actor-Id` — requiere una cuenta `ACTIVE` (no importa el rol, cualquier activo puede
  consultar el ranking de cualquier tipo).
- **Path** `{tipo}`: bindeado directo a `TipoRanking` (`@PathVariable TipoRanking tipo`) — Spring lo
  convierte con `Enum.valueOf` ANTES de llegar al servicio.
- **Query param** `fecha` opcional, ISO-8601 (`YYYY-MM-DD`) — si no se manda, usa la fecha de hoy
  (`clock.today()`).
- **Response 200** — `List<EntradaRankingResponse>`: `{ "participanteId", "fullName", "posicion",
  "puntaje" }`.

**La trampa pedida — qué valores de `tipo` funcionan de verdad:**

| `tipo` | ¿Funciona `GET`? | Detalle |
|---|---|---|
| `LEAGUE` | Sí | ordena por `puntosLiga` |
| `CELL` | Sí | ordena por `coherencia` |
| `GENERAL` | Sí | fórmula compuesta de `PuntajeGeneral` (hábitos + rocas + cursos) |
| `COHORT` | **200, pero SIEMPRE vacío** — no lanza error en el `GET`. El snapshot de este tipo nunca se genera: `GenerarSnapshotRankingUseCase.generar` lo rechaza explícitamente con `UnsupportedOperationException` ("requiere agrupar por cohorte, dato que todavia no expone ningun contrato publico"). Como el snapshot nunca se guarda, el `GET` consulta una tabla sin filas para `COHORT` y devuelve `[]` — no confundir "sin error" con "andando" |
| cualquier otro string | **400** `MethodArgumentTypeMismatchException` — Spring rechaza el path variable antes de que el controller/servicio lo vea, mensaje genérico `"El valor de 'tipo' no tiene el formato esperado"` |

```bash
curl -s "http://localhost:8080/api/v1/ranking/LEAGUE" -H "X-Actor-Id: <UUID>"
curl -s "http://localhost:8080/api/v1/ranking/COHORT" -H "X-Actor-Id: <UUID>"   # 200, []
curl -s "http://localhost:8080/api/v1/ranking/NOPE" -H "X-Actor-Id: <UUID>"    # 400
```

**Cómo se llenan estos rankings**: el `GET` solo LEE snapshots ya generados (`LoadRankingPort`). No hay
endpoint HTTP en este alcance para disparar `GenerarSnapshotRankingUseCase` — es un scheduler. Si nunca
corrió para la fecha que estás consultando, `GET` devuelve `[]` igual que `COHORT`, sin que sea un error de
`tipo`.

---

## Flujos completos para probar

Todos asumen `BASE=http://localhost:8080`, y que ya tenés un `X-Actor-Id` de un `User` real (rol `TRAINEE`
para todo lo que exige ese rol: rocks, radar, verdugo).

### A. Día del aprendiz: hábitos

1. `GET /api/v1/habit-tracks/today` → tomá el `id` de un registro con `estado: "PENDIENTE"` y
   `tipoDia != no-BLOQUEO` (mirá el hábito en el catálogo si necesitás saber su tipo — este endpoint no lo
   expone directo, solo `habitoId`).
2. Completar uno simple: `POST /api/v1/habit-tracks/{id}/complete` con ese `id` → mirá `puntosOtorgados` en
   la respuesta.
3. Completar uno con evidencia:
   a. `POST /api/v1/habit-tracks/{id}/evidence` (otro `id`, de otro registro `PENDIENTE`) con
      `tipo: "FOTO"` y `bucket`/`rutaStorage` de prueba.
   b. Después llamá igual a `POST /api/v1/habit-tracks/{id}/complete` sobre el mismo `id` — **confirmado
      leyendo `RegistroService.completar` de punta a punta: el gesto de completar NO verifica en ningún
      momento que exista evidencia subida ni consulta `ExigenciaEvidencia`.** Son dos operaciones
      completamente independientes; podés completar un hábito `OBLIGATORIA` sin haber subido nunca su
      evidencia, y el backend no te lo va a impedir en este alcance (la exigencia hoy es informativa para
      el cliente, no se hace cumplir server-side en `complete`).
4. `GET /api/v1/points/{tuActorId}` (usando tu propio id como `participanteId`) → compará `puntosLiga`
   antes/después de los pasos 2 y 3.

### B. Santuario (día bloqueado)

1. `GET /api/v1/habit-tracks/today` → encontrá un registro cuyo hábito sea de tipo `BLOQUEO` (si intentás
   `complete` genérico sobre él, te va a devolver 400 diciéndote que uses `/santuario`).
2. `POST /api/v1/habit-tracks/{id}/santuario/start` → usá el mismo `id` del paso 1.
3. Para completarlo: `POST /api/v1/habit-tracks/{id}/santuario/complete` (mismo `id`).
   Para romperlo en su lugar: `POST /api/v1/habit-tracks/{id}/santuario/break` con
   `{"motivo": "SALIDA_TEMPRANA"}`.
4. `GET /api/v1/points/{tuActorId}` → si completaste, sumó puntos; si rompiste, restó
   `PENALIZACION_ROTURA_PUNTOS`.

### C. Día sin celular (racha honor-based)

1. `GET /api/v1/habit-tracks/today` → buscá el registro del hábito `PHONE_FREE_DAY`.
2. `POST /api/v1/habit-tracks/{id}/phone-free/start` con `{"horasObjetivo": 3}` (usá el `id` del paso 1;
   `3` es el hito más chico para no esperar 24h de verdad al probar).
3. Esperar (o, si tenés control del reloj de test, avanzarlo) al menos 180 minutos, y:
   - `POST /api/v1/habit-tracks/phone-free/complete` (**sin `{id}`**) para cerrar.
   - o `POST /api/v1/habit-tracks/phone-free/break` (**sin `{id}`**) para romperla antes — sin penalización.
4. `GET /api/v1/points/{tuActorId}` → solo sube si cerraste con el ciclo completo de 24h real
   (`horasObjetivo: 3` cierra la racha pero NO paga los 10 puntos si no llegaste a las 24h).

### D. Rocas: plan semanal → plan diario → evidencia → cierre de semana

1. `GET /api/v1/rocks/master` → confirmá que tenés las 3 Rocas Maestras (si no, `ROCKS_LOCKED` en todo lo
   demás — hablá con onboarding).
2. `POST /api/v1/rocks/weekly` con una roca por eje → guardá los `id` de la respuesta (uno por eje).
3. `POST /api/v1/rocks/plan` con `fecha` = mañana (o hoy, si estás fuera de la ventana de planificación) y
   1-3 items por eje, posiciones 1..N contiguas → guardá los `id` de `RocaDiariaResponse`.
4. Completar una roca con evidencia:
   a. (Opcional, no funcional hoy) `POST /api/v1/rocks/{id}/evidence/upload-url`.
   b. `POST /api/v1/rocks/{id}/evidence` con el `id` de una roca `VERDE` (posición 1) primero — si probás
      con `AMARILLA`/`ROJA` antes, te va a rechazar con `GREEN_NOT_EVIDENCED`.
5. `PATCH /api/v1/rocks/weekly/{id}/review` (usando el `id` de la roca semanal del paso 2) para cerrar la
   semana.
6. `GET /api/v1/points/{tuActorId}` → confirmá que sumó `puntosOtorgados` de la roca completada.

### E. Modo Verdugo: destino propio vs. ajeno

1. Conseguí un `destinoId` propio: el `id` de una `RocaDiaria` tuya (`GET /api/v1/rocks/today`) o el `id` de
   un `RegistroHabito` tuyo (`GET /api/v1/habit-tracks/today`).
2. `POST /api/v1/enforcer-events` con `destinoTipo: "ROCA_DIARIA"` (o `"REGISTRO_HABITO"`), ese `destinoId`,
   y `resultado: "POSTERGADO"` → esperás `201`.
3. Repetí el paso 2 con un `destinoId` de OTRO participante (necesitás otro `X-Actor-Id` para generarlo
   primero) → esperás `403 "Ese destino no te pertenece"`.
4. `GET /api/v1/enforcer-events` → confirmá que solo aparece el evento del paso 2, no ningún intento fallido
   del paso 3.

---

## Lo que quedó sin verificar en este alcance

- El rango exacto (`@Min`/`@Max`) de `energyLevel` en el check-in de radar — está definido como constante
  en `RegistroRadar.NIVEL_ENERGIA_MIN/MAX`, fuera del paquete `rest` que cubrí; no lo leí.
- El mensaje exacto que devuelve `evidence.revisar` si se llama sobre una evidencia que NO está en
  `REVISION_MANUAL` (la guarda vive en `Evidencia.revisarManualmente`, dominio que no leí en este alcance).
