# Auditoría de requisitos no funcionales — seguridad, disponibilidad, concurrencia, resiliencia y mantenibilidad

**Fecha:** 2026-09-06
**Rama:** `auditoria` (backend y frontend)
**Alcance:** el backend desplegado (`src/main/java`, `application.yaml`, el CD) **y la infraestructura real en AWS** (CloudFront, EC2, RDS, security groups, CloudWatch), más el camino completo del chat con herramientas. El frontend tiene su propio informe: `Renaser-90-dias-frontend-/docs/AUDITORIA_ESTILOS_2026-09-06.md`.
**Pedido del dueño, textual:** *"revisa si no se rompió lógica o si rompe algo que cumpla con requisitos no funcionales, más que todo en temas de seguridad, disponibilidad, concurrencia, resiliencia, y el tema de mantenibilidad a futuro… en tema del chat… la parte de tool calling que sea más rápido; luego del diagnóstico lo arreglarás y verificarás que funcione."*
**Método:** todo hallazgo tiene evidencia reproducible — la salida de un `curl`, de la CLI de AWS, de `javap` sobre el bytecode de las librerías, o archivo y línea del código. Donde algo se creyó y al verificarlo resultó distinto, está dicho (§4.1 es el caso principal). **Lo que no se pudo verificar en vivo está marcado como tal**, con el motivo.

---

## 1. Resumen ejecutivo

| # | Eje | Hallazgos | Severidad máxima | Corregidos hoy |
|---|---|---|---|---|
| 1 | Seguridad | 6 | **Alta** (dos) | 2 |
| 2 | Disponibilidad | 5 | Alta | 2 |
| 3 | Concurrencia y resiliencia | 4 | **Alta** | 2 |
| 4 | Mantenibilidad | 3 | Media | 1 |
| 5 | Chat y tool calling (rendimiento) | 4 | Media | 1 |

**Los tres que más pesan, en orden:**

1. **§2.1 — Tres rutas de escritura quedaron fuera de la sesión obligatoria**, entre ellas *invitar usuarios* y *cambiar roles*. Con el UUID de un admin en un header (y los UUID no son secretos: catorce DTOs los devuelven) cualquiera podía hacer las dos cosas. Es el mismo agujero que se cerró esta mañana en `/admin/**`, en dos rutas que un matcher exacto dejó pasar. **Corregido y con prueba.**
2. **§4.1 — El cliente de Google no tenía timeout, reintentaba solo cinco veces ante una cuota agotada, y el fallo salía como 500.** La hipótesis inicial (el retry de Spring AI) era incorrecta; se verificó en el bytecode. **Corregido, con doce pruebas nuevas.**
3. **§2.2 — Producción no devolvía ninguna cabecera de seguridad** (ni HSTS, ni `nosniff`, ni `X-Frame-Options`). **Corregido en CloudFront y verificado con `curl`.**

**Lo que se verificó que está bien** y no hace falta tocar: CSRF deshabilitado es correcto (la sesión viaja por header, no por cookie, así que CSRF no aplica); `open-in-view=false`; virtual threads activos con tope de concurrencia para `@Async`; `ShedLock` en los seis schedulers; Hikari acotado (20) con timeout de conexión; actuator expone **solo** `/health`; Boot no incluye mensaje ni stacktrace en los 500 por defecto; RDS cifrada, con backups de 7 días y protección de borrado; C-1 y C-4 (IA dentro de transacciones) **cerrados** — aunque `CLAUDE.md` decía lo contrario (§5.1).

---

## 2. Seguridad

### 2.1 Rutas de escritura fuera de `authenticated()` — **Alta — CORREGIDO**

**Evidencia.** `SecurityConfig.java` protegía `"/api/v1/habits"` (coincidencia **exacta**) y `"/api/v1/users/me/**"`. Enumerando los `@RequestMapping` de los 74 controllers quedaron fuera:

| Ruta | Qué hace | Guard que tenía |
|---|---|---|
| `PUT /api/v1/habits/{id}/rename` | renombrar un hábito del plan | `@RequiresPermission(USE_APP)` sobre el actor del header |
| `DELETE /api/v1/habits/{id}/rename` | quitar el hábito del plan | ídem |
| `POST /api/v1/users/invite` | **invitar usuarios (staff incluido)** | `MANAGE_ROLES` verificado sobre el UUID del header |
| `PATCH /api/v1/users/{id}/role` | **cambiar el rol de un usuario** | ídem |
| `PATCH /api/v1/users/{mentorId}/mentor-profile` | editar perfil de mentor | `MANAGE_MENTOR_PROFILE`, ídem |

El guard verifica *el rol del UUID que llega*, no *que quien llama sea ese usuario*. Fuera de `authenticated()`, el actor se resuelve de `X-Actor-Id`, que lo escribe el cliente.

**Corrección.** `SecurityConfig`: `.requestMatchers("/api/v1/habits/**", "/api/v1/users/**").authenticated()`, con el porqué escrito al lado. Ninguna ruta pública vive bajo esos prefijos (el alta es `/account-requests`; activación y reset son `/auth/**`).

**Prueba.** `HabitRenameControllerAutenticacionTest` (4 casos): sin sesión, `PUT`/`DELETE …/rename` → 403 y el caso de uso nunca se invoca; `POST /users/invite` y `POST /users/{id}/role` → 403 **desde un slice que no carga `UserController`** — si fuera 404 sería el enrutamiento; 403 prueba que la cadena de seguridad cortó antes.

**A confirmar mañana con el token:** que el panel Lambda de solicitudes (`renaser-admin-panel-lambda`) no llame a `/users/invite` con solo `X-Actor-Id`. Esta sesión no tiene su código; usa sesión para `/admin/**` desde esta mañana, así que lo esperable es que no rompa.

### 2.2 Cero cabeceras de seguridad en producción — **Alta — CORREGIDO**

**Evidencia.** `curl -sI https://djbooeq09skac.cloudfront.net/actuator/health` devolvía **solo** `HTTP/1.1 200`. Dos causas sumadas: CloudFront no tenía *response headers policy* (`ResponseHeadersPolicyId: null`), y el origen recibe HTTP plano (`OriginProtocolPolicy: http-only`, puerto 8080), así que Spring Security nunca emite HSTS (solo lo hace sobre HTTPS).

**Corrección.** `Managed-SecurityHeadersPolicy` (`67f7725c-…`) en el comportamiento por defecto de la distribución `E3O4M4W7JW3TJQ`. Sin tocar código.

**Verificado (propagó en ~30 s):**

```
Strict-Transport-Security: max-age=31536000
X-Content-Type-Options: nosniff
X-Frame-Options: SAMEORIGIN
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
```

y el preflight CORS desde `https://renaser-90-dias-frontend-livid.vercel.app` **sigue intacto** (`Allow-Origin`, `Allow-Methods`, `Allow-Headers: x-auth-token`, `Allow-Credentials: true`).

### 2.3 El origen es alcanzable por cualquier distribución de CloudFront — **Media — PENDIENTE (con receta)**

**Evidencia.** El security group del EC2 permite el 8080 desde el *prefix list* global de CloudFront (`pl-3b927c52`). Eso incluye a **todas** las distribuciones del mundo, no solo la nuestra. Y la distribución no manda ninguna cabecera secreta al origen (`CustomHeaders.Quantity: 0`). Alguien puede crear su propia distribución apuntando a `ec2-52-0-210-237…:8080` y saltarse la nuestra (y con ella cualquier WAF o política que le pongamos).

**Receta, en este orden** (el orden importa: al revés rompe producción):
1. CloudFront: cabecera de origen `X-Origen-Renaser: <secreto>` (secreto en Parameter Store).
2. Backend: filtro que exija esa cabecera en `/api/**` — exceptuando `/actuator/health`, que el CD consulta por `localhost`.
3. Recién entonces, desplegar.

No se hizo hoy porque la mitad del backend viviría en esta rama sin desplegar mientras CloudFront ya mandara la cabecera (inofensivo) — un control a medias confunde más de lo que protege.

### 2.4 RDS `PubliclyAccessible: true` — **Media — DECISIÓN DEL DUEÑO**

**Evidencia.** `describe-db-instances`: `PubliclyAccessible: true`. **Mitigado**: el SG de la RDS solo acepta el 5432 desde `38.250.157.113/32` (una IP residencial) y desde el SG del backend. No está abierta a internet. Pero: (a) esa IP es **dinámica** — la regla va a caducar sola y además mañana desde el trabajo no entra; (b) el flag es una capa menos: si alguien amplía el SG, queda expuesta al instante.

**Propuesta.** `modify-db-instance --no-publicly-accessible --apply-immediately` (el backend la sigue alcanzando por el endpoint privado dentro de la VPC) y, para el acceso del dueño, túnel por SSM en vez de IP fija:

```bash
aws ssm start-session --profile renaser --target i-0ea00f555c5fe8028 \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["renaser-prod.cm7cywwku4wf.us-east-1.rds.amazonaws.com"],"portNumber":["5432"],"localPortNumber":["5433"]}'
```

Cambia el flujo de trabajo del dueño; por eso no se hizo sin preguntar.

### 2.5 `GOOGLE_OAUTH_CLIENT_SECRET` sigue conteniendo el *client ID* — **Media — PENDIENTE (dueño)**

Hallazgo de esta misma sesión, sin cambios: el parámetro tiene el ID, no el secreto `GOCSPX-…`. El login con Google no puede completar hasta que lo cargue el dueño (yo no cargo credenciales).

### 2.6 Sin WAF ni límite de tasa en el borde — **Baja — INFORMATIVA**

No hay Web ACL en la distribución. El login ya tiene su propio límite (10/h por email, 50/h por IP, verificado en producción esta mañana), y el chat tiene cuota diaria por persona. Con una sola instancia, un abuso volumétrico sobre cualquier otra ruta llega al backend. AWS WAF con una *rate-based rule* cuando haya presupuesto; hoy no bloquea salir.

---

## 3. Disponibilidad

### 3.1 Cero alarmas — **Alta — CORREGIDO (falta un paso del dueño)**

**Evidencia.** `describe-alarms` → `0`. Si el backend se caía a las 3 de la mañana, nadie se enteraba hasta que un aprendiz avisara.

**Corrección.** Topic SNS `renaser-alertas` y dos alarmas sobre `i-0ea00f555c5fe8028`: `StatusCheckFailed_System` y `StatusCheckFailed_Instance` (máximo por minuto, 2 de 2 períodos, *missing data = breaching*, con `OK` también notificado). Estado inicial `INSUFFICIENT_DATA` hasta juntar dos minutos de métrica.

**Lo que falta, y es del dueño:** suscribir su correo al topic (no lo hago yo: es su dato y su decisión):

```bash
aws sns subscribe --profile renaser --region us-east-1 \
  --topic-arn arn:aws:sns:us-east-1:302277511407:renaser-alertas \
  --protocol email --notification-endpoint TU_CORREO
```

y confirmar el mail que llega.

### 3.2 Los logs del contenedor viven solo en el disco de la instancia — **Media — PENDIENTE (es un cambio del CD)**

**Evidencia.** `docker inspect backend` → driver `json-file`, 68 KB. El CD hace `docker rm -f backend` en cada despliegue: **cada deploy borra los logs del anterior**, y un reinicio de la instancia también. Cuando algo falle en producción no va a haber nada que leer.

**Receta.** En `cd.yml`, al `docker run`: `--log-driver=awslogs --log-opt awslogs-group=/renaser/backend --log-opt awslogs-region=us-east-1 --log-opt awslogs-stream=backend`, más `logs:CreateLogGroup/CreateLogStream/PutLogEvents` en el rol `renaser-backend-ec2`, y retención de 30 días en el grupo. Es un cambio de `master` (el CD despliega solo desde ahí), por eso no va en esta rama.

### 3.3 Memoria: 196 MB libres y la JVM sin límite de contenedor — **Media — INFORMATIVA**

**Evidencia.** `t3.small` (1,9 GB); `free -m`: 1.197 usados, **196 libres**; el contenedor sin `--memory` (`MEM_LIMIT=0`) y `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` → el heap puede crecer hasta ~1,4 GB del **host**, con Redis y el SO en los ~500 MB restantes. Un pico de carga puede terminar en el OOM-killer; el `restart: unless-stopped` lo levantaría, con un corte de ~45 s.

**Recomendación** (no se aplicó: requiere redeploy y una prueba de carga que hoy no hay): `--memory 1200m` al contenedor **y** `MaxRAMPercentage=60`, o subir a `t3.medium`. Con swap de 1 GB como red de seguridad.

### 3.4 Timeout de las respuestas asíncronas — **Media — CORREGIDO**

Sin `spring.mvc.async.request-timeout`, rige el del contenedor: Tomcat corta un request asíncrono a los 30 s. El chat es SSE (`Flux<String>`); una respuesta larga con dos herramientas se partía a mitad **sin ningún error del lado del servidor**. Fijado en **120 s** (`MVC_ASYNC_REQUEST_TIMEOUT`), por encima del timeout del cliente de Google (§4.1) con margen para dos viajes al modelo.

### 3.5 Instancia única y RDS de una sola zona — **Baja — DECISIÓN CONSCIENTE**

`db.t4g.micro`, single-AZ, cifrada, backups 7 días, *deletion protection*. Para el tamaño actual es razonable; Multi-AZ duplica el costo de la base. Se registra para que sea una decisión y no un olvido.

---

## 4. Concurrencia y resiliencia

### 4.1 El cliente de Google: sin timeout, con reintentos ocultos, y 500 en vez de 503 — **Alta — CORREGIDO** (E-147)

**Lo que se creía.** `GoogleGenAiClientesConfig` pasa `RetryUtils.DEFAULT_RETRY_TEMPLATE` al chat y a los embeddings, así que la hipótesis fue *"Spring AI reintenta 10 veces con hasta 3 minutos de backoff"*.

**Lo que se verificó (`javap` sobre `spring-ai-retry-2.0.0.jar` y `spring-ai-google-genai-2.0.0.jar`).** El template es `maxRetries(10)`, `delay 2 s`, `multiplier 5`, `maxDelay 180 s` — pero **`includes(TransientAiException, ResourceAccessException)` solamente**, y el módulo de Google **no traduce** sus excepciones a esos tipos (cero referencias a `TransientAiException` en el jar). Un 429 llega como `com.google.genai.errors.ClientException`. **Ese retry nunca se activaba.** Haber arreglado "el retry de Spring AI" habría sido arreglar algo que no corría.

**La causa real, en dos partes (`javap` sobre `google-genai-1.58.0.jar`):**

1. **El SDK reintenta solo aunque nadie lo configure.** `ApiClient` hace `httpOptions.retryOptions().orElse(HttpRetryOptions.builder().build())` e instala un `RetryInterceptor` **siempre**, con defaults **5 intentos, 1 s → 60 s con base 2, sobre 408/429/500/502/503/504**. Ante un 429 de cuota: cinco golpes a Google con 1+2+4+8 s entre medio, gastando más cuota, para fallar igual ~15 s después.
2. **Sin timeout HTTP** (`Client.builder().apiKey(k).build()` pelado) y sin el de MVC (§3.4).

Arriba de eso, `GlobalExceptionHandler` no conocía ninguna excepción de IA → **500**; y el móvil reintenta los 500 enseguida.

**Corrección** (`D-117`):
- `HttpOptions` con **timeout 60 s** y `HttpRetryOptions` explícito: **2 intentos, 0,5 → 2 s, solo 408/5xx**. El 429 se saca de la lista a propósito.
- `TraduccionErroresGoogleGenAi` (adaptador): 429 → `ProveedorIaNoDisponibleException` (60 s); 408/5xx/timeouts (`GenAiIOException`, `SocketTimeoutException`…) → la misma (10 s); un 400 pasa intacto porque es un bug **nuestro** en la solicitud. Busca la `ApiException` en toda la cadena de causas.
- `ProveedorIaNoDisponibleException` en `shared/domain` (solo `java.time`, sin SDK) → `GlobalExceptionHandler` → **503 + `Retry-After`**. Es 503 y no 429 porque el 429 de esta API ya significa "vos agotaste tu cuota diaria" y el móvil lo muestra así.
- En el stream (ya salió el 200): mensaje propio *"saturado… en unos minutos"* en vez de *"en unos segundos"*.

**Pruebas nuevas (12):** traducción 7, adaptador de embeddings 3, adaptador de chat 1 (`ChatModel` simulado emitiendo `Flux.error(ClientException 429)`), handler web 1 (`503` + `Retry-After: 60`), servicio 1.

**Sin verificar en vivo:** provocar un 429 real implicaría agotar la cuota de producción a propósito.

### 4.2 Pregunta del chat sin tope de tamaño — **Baja — CORREGIDO**

`PreguntarRenasiaRequest.question` tenía `@NotBlank` y nada más: un cliente podía mandar un prompt de megabytes que se embebía, se guardaba y se enviaba entero al modelo, a cargo de la cuota compartida. `@Size(max = 4000)` (unas 600 palabras) con dos pruebas (4.001 → 400 sin tocar el caso de uso; 4.000 → 200).

### 4.3 `EvaluarRiesgoMensajePort` existe y nadie lo llama — **Baja — PENDIENTE (producto)**

El puerto, su modelo (`EvaluacionRiesgo`, `NivelRiesgo`, `Severidad`) y el adaptador `NoOp` están; **ningún servicio lo invoca** (`grep` en `rag/application/services`: cero). La evaluación de riesgo de mensajes que el diseño promete no corre. Qué hacer con un mensaje de riesgo es decisión de producto; hasta entonces es una abstracción muerta que hay que borrar o conectar, no dejar a medias.

### 4.4 C-1 / C-4 — **VERIFICADO CERRADO** (y la doc decía lo contrario)

`PgVectorNativoAdapter.buscarSimilares` no es `@Transactional` (su javadoc lo explica citando C-1/C-4) y `EvidenciaService.procesarLote` tampoco. `CLAUDE.md` §7 afirmaba que *"siguen abiertos"*. Corregido en §5.1.

---

## 5. Mantenibilidad

### 5.1 `CLAUDE.md` contradecía el código — **Media — CORREGIDO**

El párrafo de §7 se lee en cada sesión; decía que dos problemas seguían abiertos cuando ambos estaban cerrados. Cualquiera habría "arreglado" de nuevo algo correcto. Corregido con la nota de fecha, como manda `.claude/rules/05`.

### 5.2 El grafo de código no está disponible en esta máquina — **Baja — INFORMATIVA**

`CLAUDE.md` exige `graphify update .` tras cada cambio y el hook post-commit; `graphify` no está instalado ni como módulo de Python. El grafo en `graphify-out/` está desactualizado. O se instala, o se quita la sección, pero no puede quedar como regla que nadie puede cumplir.

### 5.3 La pasada en vivo de endpoints autenticados queda para mañana — **PENDIENTE (token)**

`docs/informes/pruebas-endpoints-en-vivo.md` (2026-08-31) recorrió 221 endpoints con `curl`. Hoy se verificaron en vivo los públicos y el borde (§2.2, §3.1, login 401/429 esta mañana); los autenticados necesitan una sesión, que esta máquina no tiene. Con el token del dueño se repite el barrido contra producción.

---

## 6. Chat y tool calling — rendimiento

**El camino real de una pregunta** (`ConversacionRenasiaService.preguntar`, verificado en el código): `requireActivo` (BD) → `requireCuotaDisponible` (Redis) → `buscarOCrearConversacion` (BD) → `ultimosTurnos` (BD, 10 turnos) → guardar pregunta (BD) → **`buscarSimilares`: una llamada de embedding a Google + consulta pgvector** → recién ahí arranca el stream del modelo. Con herramientas, cada llamada a una herramienta es **un viaje más de ida y vuelta a Gemini** (~1-2 s en `flash-lite`), y el stream se bufferiza hasta que terminan.

### 6.1 El total de puntos viaja con los hábitos del día — **Media — CORREGIDO**

`consultar_puntos_en_juego` es derivable de `consultar_habitos_del_dia`. Cuando la pregunta mezcla "qué me falta" y "cuánto vale", el modelo encadenaba las dos: **un viaje más a Gemini**. Ahora la primera devuelve además `Total en juego: N puntos en M hábito(s)`. La herramienta de puntos **se conserva** (D-112): para la pregunta directa es más exacta que hacer sumar al modelo.

### 6.2 Cada mensaje al acompañante gasta un embedding y ~3.000 tokens de contexto — **Media — DECISIÓN DEL DUEÑO**

La búsqueda RAG corre **para los dos agentes**, con `TOP_K = 5` fragmentos de ~450 palabras. Para "¿cuántos puntos me quedan?" eso es una llamada a Google (~200-400 ms) y unos 3.000 tokens de prefill que no aportan — y **es la misma cuota de 1.000 embeddings/día que hoy tiene frenada la indexación de Sparkie**: cada charla con el acompañante la consume.

**No se cambió, a propósito**: el prompt del acompañante (`renasia-sistema.st`, línea 132) usa `{contexto}` y le dice al modelo que responda con "el contenido del programa". Quitárselo es un cambio de producto, no una optimización. Opciones, de menor a mayor impacto: bajar `TOP_K` a 3 para el acompañante; omitir la búsqueda cuando la pregunta no menciona el programa (heurística, frágil); o asumir que el acompañante trabaja solo por herramientas, como el dueño lo describió hoy (*"el otro es de soporte con tool calling"*). Lo decide el dueño con estos números.

### 6.3 Lo que no se puede acelerar desde acá — **INFORMATIVA**

- Spring AI ejecuta las herramientas de un mismo turno **en secuencia**; las nuestras tardan milisegundos (una consulta local cada una), así que paralelizarlas no se notaría. El costo dominante es el viaje al modelo.
- El prompt de sistema (~1.700 tokens) + herramientas + 10 turnos: Gemini aplica **caché implícita** a prefijos repetidos ≥ 1.024 tokens sin hacer nada; la caché explícita (`GoogleGenAiCachedContentService`) solo vale la pena si el prefijo crece mucho.
- Las lecturas previas al stream son locales y suman decenas de ms; la única cara es el embedding (§6.2).

### 6.4 Lo que sí acota la cola de latencia — **CORREGIDO (§3.4, §4.1)**

Antes, el peor caso de una pregunta era **ilimitado** (sin timeout) o **30 s cortados sin aviso**. Ahora: 60 s de timeout hacia Google, 1 reintento rápido, 120 s de tope del SSE, y un fallo de cuota que vuelve en milisegundos con un mensaje que dice cuánto esperar.

---

## 7. Verificación

| Qué | Cómo | Resultado |
|---|---|---|
| Cabeceras de seguridad | `curl -sI` vía CloudFront, antes y después | antes: ninguna → después: 5 cabeceras (§2.2) |
| CORS del frontend tras el cambio | preflight `OPTIONS` con `Origin` del `-livid` | intacto |
| Alarmas | `describe-alarms` | 2 creadas, `INSUFFICIENT_DATA` inicial |
| Suite del backend | `./mvnw clean verify` (JDK 25, Testcontainers); cifras leídas de los XML de surefire/failsafe, no del exit code (E-111) | **2.457 unitarias + 25 de integración: 0 fallos, 0 errores, 0 omitidas** (337 + 11 clases; incluye las 18 pruebas nuevas de hoy) |
| Frontend | `npx tsc --noEmit` tras la conversión de 16 filas | exit 0 |

**Sin verificar hoy, con motivo:** endpoints autenticados en vivo (sin token — mañana); un 429 real de Google (implicaría agotar la cuota de producción); render visual de `PlanScreen` (detrás del login); que el panel Lambda no use `/users/invite` sin sesión (§2.1).

## 8. Lo que queda, por prioridad

1. Suscribir el correo a `renaser-alertas` (§3.1) — un comando.
2. Cargar el `GOOGLE_OAUTH_CLIENT_SECRET` real (§2.5).
3. Decidir RDS privada + túnel SSM (§2.4) — la IP de casa va a caducar sola.
4. `awslogs` en el CD (§3.2) — sin eso, el próximo incidente no deja rastro.
5. Cabecera secreta de origen (§2.3), en el orden indicado.
6. Decidir el RAG del acompañante (§6.2) — también alivia la cuota de Sparkie.
7. Memoria del contenedor (§3.3) antes de la primera cohorte grande.
8. Conectar o borrar `EvaluarRiesgoMensajePort` (§4.3).


---

## 9. Segunda pasada — la respuesta a "¿no hay nada más?"

**Sí había más.** La primera pasada fue dirigida (rutas, borde, proveedor de IA, chat, estilos) y
no exhaustiva. Esta segunda cubre lo que quedó fuera: los puntos de entrada que no son HTTP, la IP
real detrás de CloudFront, permisos IAM, exposición del bucket, dependencias, secretos en git, y —
sobre todo — **qué quedó abierto de la auditoría del 2026-09-01**, cuyos 22 hallazgos de seguridad
(S-1…S-22) no tenían ningún rastro de cierre en la bitácora.

### 9.1 Estado real de la auditoría del 2026-09-01 (verificado punto por punto, no por el nombre)

| Hallazgo | Estado al 2026-09-06 | Evidencia |
|---|---|---|
| S-1 suplantación por header con toda la API en `permitAll` | **Cerrado en lo esencial** hoy | `SecurityConfig`: sesión obligatoria en todas las rutas con datos de personas (§2.1 y anteriores); queda `account-requests` público por diseño |
| S-2 handshake WebSocket por `X-Actor-Id` | **Abierto → CERRADO hoy** (E-148) | `ActorHandshakeInterceptor` ahora resuelve desde Spring Session |
| S-3 matriz de permisos falla abierta para 4 de 5 roles | **ABIERTO — decisión del dueño** | `UserRole.can`: `case MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST -> true` |
| S-4 publicar directo en `/topic` | **Abierto → CERRADO hoy** (E-148) | guarda de `SEND` en `SubscripcionAutorizadaInterceptor` |
| S-5 login sin rate limit | **Cerrado** hoy temprano | 10/h por email, 50/h por IP, verificado en producción |
| S-6 WebSocket acepta cualquier origen | **Abierto → CERRADO hoy** (E-148) | `setAllowedOrigins(cors.origenes)` |
| S-7 sesión no rotada al autenticar | **Cerrado** | `SesionWebAdapter.cerrar` invalida; el login crea sesión nueva |
| S-8 sesiones de 30 días deslizantes | Abierto, **decisión de producto** | `spring.session.timeout: 30d` es deliberado para móvil |
| S-10 mensajes internos verbatim, sin catch-all | **Parcial** | `GlobalExceptionHandler` devuelve `getMessage()` de `IllegalArgument/IllegalState`; el catch-all sigue sin existir, pero Boot no incluye mensaje ni stacktrace en el 500 por defecto |
| S-12 Redis sin contraseña | **Mitigado** | sin puertos publicados en el host (`docker port redis` vacío); solo la red interna de Docker |
| S-18/S-19 devtools en el pom, sin análisis de dependencias | **Aceptable / abierto** | `spring-boot-devtools` es `optional` + `runtime` (el plugin de Boot lo excluye del jar); sigue sin `dependency-check` |
| S-20 Facebook `client_secret` en query | Abierto, **bajo** | así lo pide la Graph API de Facebook; viaja por TLS |
| S-22 `InviteUserRequest.usuarioId` lo elige el cliente | Abierto, **bajo** | requiere `MANAGE_ROLES`; con S-3 abierto, un mentor podría |
| Fuga de PII en `Email.java` (auditoría de código) | **Cerrado** | los mensajes ya no incluyen el correo |

Los C-n de concurrencia tienen rastro (bitácora o `auditoria-fixes/`); ver §4.4.

### 9.2 Hallazgos nuevos de la segunda pasada

| # | Hallazgo | Severidad | Estado |
|---|---|---|---|
| N-23 | **WebSocket del chat: identidad por header del cliente, cualquier origen, `SEND` a `/topic`** (S-2/S-4/S-6) | **Crítica** | **Corregido** — E-148, 7 pruebas |
| N-24 | **Detrás de CloudFront todos comparten la misma "IP"**: el límite de login (50/h) era un contador global | **Alta** | **Corregido** — `forward-headers-strategy: framework`, E-149, 1 prueba |
| N-25 | **`UserRole.can` devuelve `true` a MENTOR, MENTOR_LEAD, ADMIN y ALCHEMIST para TODO permiso** (S-3). Un mentor con sesión puede invitar usuarios, cambiar roles y usar `/admin/**` | **Alta** | **Decisión del dueño** — es la matriz de permisos por rol, que `CLAUDE.md` §0.6 prohíbe inventar. Pregunta concreta en §9.3 |
| N-26 | **Los avatares devuelven 403 en producción**: `AvatarService` usa `urlPublica`, el bucket bloquea todo acceso público y no hay política. Verificado con `curl` sobre un objeto real de `avatares/` | Media (funcional) | **Decisión del dueño**: (a) URL prefirmada de lectura al servir el avatar, o (b) CloudFront con OAC delante de `avatares/*`. NO abrir el bucket |
| N-27 | Redis sin timeout: 60 s por comando (default de Lettuce); con la sesión obligatoria, Redis caído = cada request colgado un minuto, incluido `/actuator/health` | Media | **Corregido** — `timeout: 3s`, `connect-timeout: 2s` |
| N-28 | Política IAM `bootstrap-primer-admin` seguía pegada al rol del EC2 (el script decía quitarla tras crear al primer admin) | Baja | **Corregido** — eliminada; queda el parámetro `BOOTSTRAP_ADMIN_PASSWORD`, que borra el dueño cuando haya cambiado su clave |
| N-29 | `npm audit`: 16 moderadas — tooling de Expo (`@expo/config-plugins` → `xcode`, con fix) y `@react-navigation/*` vía `query-string`/`decode-uri-component` (sin fix) | Baja | Pendiente: `npm audit fix` para las de Expo en una rama aparte, con `tsc` y prueba manual |
| N-30 | Token de sesión en `localStorage` en el build web (degradación documentada en `almacenamientoSeguro.ts`). Sin CSP en el frontend de Vercel | Baja | Pendiente: cabecera `Content-Security-Policy` en `vercel.json`; la sesión por header ya evita CSRF |
| — | Historial de git de los dos repos **limpio** de claves (solo `AKIAIOSFODNN7EXAMPLE`, el ejemplo de la documentación de AWS) | — | Verificado |
| — | Bucket `renaser90dias-prod`: acceso público bloqueado, AES256, versionado | — | Verificado |

### 9.3 La pregunta que hay que contestar: la matriz de permisos (N-25)

`UserRole.can` hoy:

```java
case TRAINEE -> PERMISOS_TRAINEE.contains(permission);
case MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST -> true;
```

No lo corregí porque **qué puede hacer un mentor es una regla de negocio**, y la regla del repo es
preguntarla, no rellenarla. Propuesta para confirmar (o corregir):

| Permiso | TRAINEE | MENTOR | MENTOR_LEAD | ADMIN | ALCHEMIST |
|---|---|---|---|---|---|
| `USE_APP` | sí | sí | sí | sí | sí |
| ver progreso de SUS aprendices | — | sí | sí | sí | sí |
| `MANAGE_MENTOR_PROFILE` (bio propia) | — | sí | sí | sí | sí |
| aprobar solicitudes de cuenta | — | — | ¿sí? | sí | sí |
| `MANAGE_ROLES` / invitar staff | — | **no** | ¿no? | sí | sí |
| `/admin/**` (catálogo, cohortes, evidencias, tickets) | — | **no** | ¿parcial? | sí | sí |

Con esa tabla confirmada, el cambio es una línea por rol en `UserRole` y una prueba por celda.

### 9.4 Verificación de la segunda pasada

| Qué | Cómo | Resultado |
|---|---|---|
| Suite del backend con WebSocket, IP real y Redis | `./mvnw clean verify`, cifras de los XML | **2465 unitarias + 25 de integración: 0 fallos, 0 errores, 0 omitidas** (340 + 11 clases; incluye las 8 pruebas nuevas de la segunda pasada: WebSocket 7, IP real 1) |
| IAM | `list-role-policies renaser-backend-ec2` | solo `app` (+ `AmazonSSMManagedInstanceCore`) |
| Avatares | `curl` a la URL pública de un objeto real | `HTTP 403` (confirma N-26) |
| Secretos en git | `git log -p --all -G` en los dos repos | limpio |
| Día 7 (frontend) | `tsc --noEmit` + recorrido visual en el navegador con un arnés temporal | ver `docs/MAPA_RENACIMIENTO_DIA7.md` |
