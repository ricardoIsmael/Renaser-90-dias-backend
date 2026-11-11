# Plan técnico — spec 050 (Protección de la API)

Redactado el 2026-11-11 sobre `master` `e45b0595` del backend. **Ningún tramo de este plan está implementado**: esta rama solo trae `spec.md`, `plan.md` y `tasks.md`. Cada tramo sale en su propia rama y PR (regla 00: una funcionalidad por rama y por commit) en el orden de D13.

## 0. Constitution Check

| Principio | Cómo lo cumple este plan | ¿Tensión? |
|---|---|---|
| I. Dependencias al dominio | El clasificador de grupos y el tope de sesión son lógica pura en `shared/domain` (sin Spring); el contador va tras un puerto (`ContadorDeVentanas`) con su adaptador Redis en `shared/infrastructure`; los filtros son adaptadores `in`. Los controllers de exportación siguen tontos: un caso de uso recibe un escritor de filas, el CSV se arma en el adaptador. Ninguna IA dentro de `@Transactional`. | La cuota diaria compartida con SER (FR-018) cruza módulos: va por `rag.api`, no por SQL ni por import interno. |
| II. No se inventan reglas | Los topes son los del dueño; la matriz de MENTOR/GUÍA **no se escribe de memoria**: se deriva del inventario y la aprueba el dueño (FR-040). Q1–Q8 son las dudas que cambian el resultado. | — |
| III. Pruebas | Cada FR lleva su prueba (unitaria sin Spring, IT con Postgres y Redis reales, o prueba estática sobre los archivos de `infra/` y `scripts/`). Todo endpoint o ruta nueva prueba 403 de un rol sin permiso y de una cuenta suspendida. | Los recursos de AWS solo se verifican con la salida del script (FR-005, marcado `[sin prueba automática]`). |
| IV. Historia, no reescritura | Una sola migración (V192, valor nuevo de `rol_usuario`, sola en su archivo como V100/E-187). Los contadores de abuso son efímeros (TTL); el registro de «habría bloqueado» vive en Redis 30 días, no en tablas. | — |
| V. El tiempo es del participante | El tope de sesión usa el puerto `Clock` y su prueba corre con el reloj en 00:00–05:00 UTC. Los topes «por día» del filtro son una **ventana de 24 h desde el primer pedido**, no el día de Lima: es protección contra abuso, no una regla del programa (dicho en Supuestos). | — |
| VI. Los cinco tabs | No se toca ninguna pantalla de los cinco tabs. La app solo entiende el 429 en su cola y en `apiFetch` (FR-020). | — |
| VII. Documentar en el mismo cambio | FR-045: D-nn (hoy el último es D-341), E-nn si hay error, `DESPLIEGUE_Y_CI.md`, `MAPA_ENDPOINTS.md`, nota de «Corregido» en la spec 048 (D1). | — |

**Tamaños.** Método ≤ 20 líneas, clase ≤ 150: el filtro se parte en `ClasificadorDeGrupos` (puro), `PoliticaDeLimites` (decide con el resultado del contador) y `FiltroDeLimitePorCuenta` (solo HTTP).

## 1. Las capas y qué hace cada una

```
Internet
  │  CloudFront  ── cabecera de origen (secreto) ──────────────────────────────  tramo A2
  │  AWS WAF (Web ACL)  2.000/5 min por IP · 300/5 min en /auth/ · 3 grupos AWS   tramo B
  ▼
nginx (contenedor `proxy`, EC2)
  │  rechaza lo que no trae la cabecera de origen (403)                           tramo A2
  │  tope por IP del cliente (D1): 20 r/s, ráfaga 40 (429)                        tramo A3
  ▼
Spring (contenedor `backend`)        ─ usa Redis (contenedor `redis`, con contraseña: tramo A1)
  │  Spring Security (sesión)  → FiltroDeTopeDeSesion  90 días (401)              tramo E
  │  FiltroDeSoloLectura («ver como», orden −90)
  │  FiltroDeLimitePorCuenta (orden −85)  por cuenta y grupo (429)                tramo C
  │  PermissionEnforcementInterceptor  → alcance de la cuenta del CRM (D)  → matriz (H)
  ▼
Casos de uso (con sus guards, que se quedan)
```

Cada capa corta algo distinto: el WAF y nginx frenan **volumen por IP** antes de gastar un hilo de Spring; el filtro frena **volumen por cuenta** (lo que una IP no ve: un teléfono con sesión legítima que se descontrola); el origen cerrado evita **saltarse** las dos primeras; la matriz y la cuenta del CRM limitan **qué puede hacer** una sesión válida.

## 2. Contratos de respuesta (los tres lugares dicen lo mismo)

| Quién | Estado | Cuerpo (`application/json`) | Cabeceras |
|---|---|---|---|
| WAF (reglas por tasa, en `Block`) | 429 | `{"message":"Espera un momento y vuelve a intentarlo.","codigo":"DEMASIADOS_PEDIDOS"}` | `Retry-After: 60` (respuesta personalizada del WAF) |
| nginx, tope por IP | 429 | el mismo | `Retry-After: 1` |
| Spring, filtro por cuenta | 429 | `{"message":…,"timestamp":"…","codigo":"DEMASIADOS_PEDIDOS"}` | `Retry-After: <segundos que faltan para que venza la ventana pasada, entero ≥ 1>`; sin nombre de grupo ni tope |
| nginx, origen no permitido | 403 | `{"message":"No autorizado","codigo":"ORIGEN_NO_PERMITIDO"}` | — |
| Spring, sesión de más de 90 días | 401 | `{"message":"Tu sesión venció. Vuelve a iniciar sesión.","timestamp":"…","codigo":"SESION_VENCIDA"}` | — |
| Spring, cuenta del CRM fuera de su lista | 403 | `{"message":"No autorizado","timestamp":"…","codigo":"FUERA_DE_ALCANCE_DE_LA_INTEGRACION"}` | — |
| nginx, modo con corte (FR-038) | 503 | `{"message":"Estamos actualizando. Vuelve a intentarlo en un momento.","codigo":"SERVICIO_NO_DISPONIBLE"}` | `Retry-After: 30` |

El mensaje va en tuteo y con tildes (E-373: el motivo viaja). Una app vieja que no entiende el `codigo` muestra el `message` como cualquier error; la que tiene la cola de la spec 048 ya reintenta 429 y 503 (`colaSinRed.ts`).

## 3. Tramo A — Redis con contraseña, cabecera de origen y tope en nginx (infra, scripts del dueño)

Tres ramas, tres PR (A1, A2, A3): son el mismo tipo de trabajo pero tres funcionalidades.

### A1 · Redis con contraseña (FR-010, FR-011)

**Dónde**: `infra/redis/contrasena.sh` (nuevo), `infra/redis/redis.conf.plantilla` (nuevo), `docs/DESPLIEGUE_Y_CI.md` §5.3 (el contenedor `redis` hoy no tiene su definición en el repositorio: pasa a tenerla).

**Orden que evita cualquier hueco** (a **verificar en el ensayo local** de la tarea T005 antes de tocar producción: lo del paso 2 depende del comportamiento de `AUTH <usuario> <contraseña>` de Redis 7 contra un usuario `default` sin contraseña):

1. Se genera la contraseña (`openssl rand`, solo caracteres seguros) y se guarda como `SecureString` en `/renaser/prod/REDIS_PASSWORD`; `REDIS_USERNAME=default` en el mismo prefijo.
2. **Se despliega el backend con esas dos variables mientras Redis todavía no exige contraseña.** Con `AUTH default <clave>` Redis acepta cualquier clave mientras el usuario `default` es `nopass` (la forma de un solo argumento, `AUTH <clave>`, sí fallaría: por eso se pasa el usuario). El backend ya queda con su credencial.
3. Se activa la exigencia **en caliente** con `CONFIG SET requirepass` (las conexiones abiertas siguen valiendo; las nuevas piden la clave, que el backend ya tiene).
4. Se vuelve **persistente** (si no, un reinicio de la instancia levantaría Redis sin contraseña con el backend pidiéndola): el contenedor se recrea con `redis.conf` (`requirepass`, `appendonly yes`, directorio de datos en el host) **conservando los datos**: `SAVE` y copia de `dump.rdb` al directorio nuevo antes de arrancar, de modo que las sesiones vigentes sobreviven (SC-004). Es un corte de segundos de Redis, que Lettuce reconecta solo.
5. Se comprueba: `PING` sin clave desde otro contenedor → `NOAUTH`; `/salud` UP; una sesión vieja sigue valiendo.

**Vuelta atrás** (impresa por el script): `CONFIG SET requirepass ""` y recrear con la conf anterior; el backend no necesita redeploy porque `AUTH default <clave>` contra un `nopass` sigue siendo válido.

**Sin secretos en pantalla**: `redis-cli` con `REDISCLI_AUTH`, nunca `-a`; la conf del contenedor con permiso 600 de root; el log del SSM no los repite.

### A2 · Cabecera de origen (FR-006, FR-007, FR-008, FR-011)

**Dónde**: `scripts/despliegue/desplegar-backend.sh` (la plantilla de nginx está **dentro** del script, que es el cuerpo que manda el CD por SSM, así que la config nueva viaja con cada despliegue), `infra/cloudfront/cabecera-de-origen.sh` (nuevo: agrega o rota la cabecera en la distribución), `infra/cloudfront/README.md`.

**nginx** (bloques de nivel `http`, como el `map` que ya existe en el archivo; los secretos van en un archivo aparte `origen.conf` de permiso 600, no en el que se imprime):

- `map $http_x_origen_renaser $origen_valido { default 0; include origen-secretos.inc; }` con una línea por secreto aceptado (vigente y anterior).
- `geo $ip_de_operacion { default 0; 127.0.0.1 1; <IP de operación desde Parameter Store> 1; }` (el CD comprueba la salud por `127.0.0.1:8080`: **tiene** que seguir pasando).

> **Corregido 2026-11-11 (implementación del tramo A2, PR #131).** Esta línea decía que bastaba eximir `127.0.0.1`. No alcanza: Docker entrega lo que viene del propio host al contenedor con la IP de la **pasarela de la red** (`172.x.0.1`), no con `127.0.0.1` (comprobado con nginx 1.28). El `geo` exime `127.0.0.1/32`, la subred de la red `renaser` (sale de `docker network inspect`) y las IP de `origen-ips-directas`. Los secretos van en el mismo `renaser.conf` con permiso 600 (no en un `origen.conf` aparte: el proxy monta un solo archivo), y el modo (`ORIGEN_MODO`) sale de Parameter Store (`origen-modo`), no del CD.
- Modo `registrar`: `access_log /dev/stdout` solo para lo que no valida (visible con `docker logs proxy`); modo `exigir`: `return 403` con el cuerpo del §2.

**Los secretos** salen de Parameter Store (`origen-secreto`, `origen-secreto-anterior`, `origen-ips-directas`) con la función `parametro` que el script ya trae; **ninguno se escribe en el repositorio**.

**Orden de salida** (cada paso lo corre el dueño y se verifica antes del siguiente):

1. Despliegue de la rama: nginx con la comprobación en `registrar` (no rechaza nada).
2. `cabecera-de-origen.sh --aplicar`: CloudFront agrega la cabecera (`aws cloudfront wait distribution-deployed`).
3. Se mira `docker logs proxy` 24 h: **cero** pedidos sin cabecera salvo loopback e IP de operación.
4. Se pasa nginx a `exigir` (variable del despliegue `ORIGEN_MODO=exigir`).

**Rotación**: nginx acepta vigente y anterior → CloudFront cambia → se espera la propagación → se retira el anterior. **Vuelta atrás**: volver a `registrar` (un despliegue) y/o quitar la cabecera con el archivo de respaldo que el script guardó antes de cambiar nada.

### A3 · Tope por IP en nginx (FR-009) — **espera a D1 en producción**

**Dónde**: la misma plantilla de nginx del script: `limit_req_zone $ip_del_cliente zone=cliente:10m rate=20r/s;`, `limit_req zone=cliente burst=40 nodelay;`, `limit_req_status 429;`, `limit_req_dry_run on;` en la primera etapa, `error_page 429` con el cuerpo del §2. **`$ip_del_cliente` es un solo `map`** que arma la dependencia D1 («la IP del cliente se resuelve en un solo lugar a partir de lo que pone el borde»); esta rama **no** define cómo se saca esa IP, solo la usa. El loopback queda exento.

Se mide en `dry_run` junto con la medición del tramo C (dos semanas) mirando cuántas veces habría rechazado y a quién; solo entonces se quita `limit_req_dry_run`.

## 4. Tramo B — WAF en CloudFront (FR-001 a FR-005, FR-011)

**Dónde**: `infra/waf/web-acl.plantilla.json` (nuevo), `infra/waf/crear-web-acl.sh`, `infra/waf/asociar-web-acl.sh`, `infra/waf/pasar-regla-a-bloqueo.sh`, `infra/waf/README.md`.

- **Plantilla** (alcance `CLOUDFRONT`, región `us-east-1`): `AWSManagedRulesCommonRuleSet`, `AWSManagedRulesKnownBadInputsRuleSet`, `AWSManagedRulesAmazonIpReputationList` (acción `Count` de partida), y dos reglas por tasa con ventana de 5 minutos: `limite-global` (2.000 por IP) y `limite-auth` (300 por IP con la condición «la ruta empieza por `/api/v1/auth/`»), ambas con bloqueo de respuesta personalizada 429 (§2) cuando pasen a `Block`.
- **Lo que hay que mirar antes de bloquear los grupos administrados**: el cuerpo de un pedido de la app puede ser un texto largo (publicaciones, diario, transcripciones) y algunas reglas de «Common» miran el tamaño del cuerpo y patrones que un texto libre puede parecer. Se revisan en `Count` y, si una regla acierta mal con tráfico legítimo, se **excluye esa regla por nombre** en la plantilla con su motivo (FR-003). Las subidas de archivos van directo a S3 con URL firmada y no pasan por el WAF.
- **Registro** (Q8): destino CloudWatch Logs `aws-waf-logs-renaser`, con `RedactedFields` para las cabeceras `x-auth-token`, `authorization` y `cookie` y sin cuerpo (FR-004). 14 días, luego muestreo.
- **Costo**: la ACL (~US$ 5) más ~US$ 1 por regla (5 reglas) más ~US$ 0,60 por millón de pedidos: del orden de **US$ 10 al mes** con el tráfico de hoy (el dueño estimó 5–10). Los tres grupos administrados de AWS que se usan no tienen cargo aparte.
- **Los scripts**: `crear-web-acl.sh` (simulacro: imprime la plantilla resuelta y el resultado de `aws wafv2 check-capacity`; `--aplicar` la crea **sin asociarla**); `asociar-web-acl.sh` (obtiene la configuración de la distribución con su `ETag`, guarda el respaldo, agrega el `WebACLId`, `update-distribution`; la vuelta atrás es la misma llamada con el respaldo); `pasar-regla-a-bloqueo.sh <regla>` (cambia **una** regla de `Count` a `Block`).
- **Cómo se ensaya sin tocar la distribución real** (tarea T022, opcional pero recomendada): la misma ACL contra una **distribución de CloudFront de ensayo** con un origen de mentira, para ver el 429 con su cuerpo y su `Retry-After` y la censura del registro. En `Count` el WAF no cambia el tráfico, así que si el dueño prefiere saltarse el ensayo, asociar la ACL real en `Count` es reversible y no bloquea nada.
- **Orden**: crear → asociar en `Count` → 7 días de métricas y muestras → bloquear **primero** las dos reglas por tasa y IP reputation → los otros dos grupos solo si no hay falsos positivos.
- **IP compartida**: una red móvil o una oficina puede poner a muchas personas detrás de la misma IP; 2.000 por 5 minutos es holgado para una persona (≈ 6,7 por segundo) pero no para un salón entero. Por eso se mira primero en `Count` cuántas IP superan el 50 % y el 100 % del umbral antes de bloquear.

## 5. Tramo C — Filtro de límite por cuenta, en modo «solo registrar» (FR-012 a FR-021)

### Dónde (hexagonal)

| Pieza | Ruta | Qué hace |
|---|---|---|
| Grupos y clasificación | `shared/domain/GrupoDeLimite.java`, `ClasificadorDeGrupos.java` | Puro, sin Spring: `(método, ruta) → grupo` o «exento». Lista corta y probada de **lecturas con POST** (hoy `POST /users/me`). |
| Puerto del contador | `shared/domain/ContadorDeVentanas.java` | `Resultado intentar(String cuenta, GrupoDeLimite grupo, List<Ventana> ventanas)`; el `Resultado` trae «pasó», la ventana que se pasó y los segundos que faltan. |
| Adaptador Redis | `shared/infrastructure/cache/ContadorDeVentanasRedis.java` | Script Lua **multi-ventana**: mira todas las ventanas **antes** de incrementar ninguna (un rechazo no consume las otras: FR-012), con el mismo patrón de TTL del contador actual (`INCR` y `PEXPIRE` solo si no tiene TTL, en el mismo script: C-8). Devuelve el `PTTL` de la ventana pasada para el `Retry-After`. Claves `prot:lim:<grupo>:<ventana>:<cuenta>`. |
| Política | `shared/application/PoliticaDeLimites.java` | Aplica el modo del grupo, registra la métrica y decide `PASAR` / `PASAR_Y_ANOTAR` / `RECHAZAR`. Si el contador falla: `PASAR` + `error_de_redis` (FR-017). |
| Filtro | `shared/web/security/FiltroDeLimitePorCuenta.java` + `FiltroDeLimitePorCuentaConfig.java` | `OncePerRequestFilter` **registrado como instancia** con `FilterRegistrationBean` en orden **−85** (E-307: como bean del módulo lo envuelve el proxy de Modulith y revienta en `init()`), después de la cadena de Spring Security (−100) y de `FiltroDeSoloLectura` (−90), antes de Spring MVC. Toma la cuenta de `SecurityContextHolder`. Un pedido anónimo no se cuenta aquí (lo cubren el WAF, nginx y los límites propios de D14), salvo `webhook-whatsapp`, que cuenta por origen. |
| Propiedades | `shared/web/security/LimitesDeProteccion.java` (`@ConfigurationProperties("renaser.proteccion.limites")`) | Topes y modo por grupo. |
| Métricas | `shared/infrastructure/metricas/MicrometerLimitesAdapter.java` | Detrás de un puerto `RegistrarMetricaDeLimitesPort`, como hacen `chat` y `rag`. |

### Claves de configuración

Todas con valor por defecto en `application.yaml` (los de la tabla de FR-013) y sobrescribibles por variable de entorno desde Parameter Store:

| Clave | Por defecto | Qué es |
|---|---|---|
| `renaser.proteccion.limites.habilitado` | `true` | Apaga todo el filtro (vuelta atrás de emergencia). |
| `renaser.proteccion.limites.cache-del-modo` | `30s` | Cada cuánto lee el filtro el modo de Redis (Q6). |
| `renaser.proteccion.limites.grupos.<grupo>.modo` | `REGISTRAR` | `DESACTIVADO` \| `REGISTRAR` \| `APLICAR`. |
| `renaser.proteccion.limites.grupos.<grupo>.por-minuto` | ver FR-013 | Tope por minuto (si aplica). |
| `renaser.proteccion.limites.grupos.<grupo>.por-hora` | ver FR-013 | Tope por hora (solo `muro`). |
| `renaser.proteccion.limites.grupos.<grupo>.por-dia` | ver FR-013 | Tope por 24 h (solo `subidas`). |

`<grupo>` ∈ `lectura`, `escritura`, `muro`, `chat`, `subidas`, `ia-fuera-de-ser`, `ser`, `crm`, `exportaciones`, `admin`, `webhook-whatsapp`. Orden de decisión del clasificador (gana el primero que coincide): `webhook-whatsapp` → `exportaciones` (`…/export.csv`, `…/exportar.csv`) → `subidas` (`…/upload-url`, `…/presign`) → `chat` (`POST /chat/**/messages`) → `muro` (`POST /wall`, `POST /wall/{id}/comments`) → `ia-fuera-de-ser` (`POST` bajo `/analisis-80-20`, `/espejo-sombra`, `/mapa-renacimiento`) → `ser` (`POST /renasia/**`) → `admin` (`/admin/**`) → `escritura` (cualquier verbo que escribe) → `lectura` (el resto). El grupo `crm` lo aplica el alcance de la cuenta (tramo D), no el filtro.

### Cómo funciona «solo registrar»

- En `REGISTRAR` el contador **cuenta igual** (si no, no habría qué medir), la política anota `habria_bloqueado` y **el pedido pasa**. Nunca sale un 429 (SC-002).
- Lo que se guarda para decidir los topes:
  - Métrica `renaser_limite_pedidos_total{grupo,resultado}` y `renaser_limite_uso{grupo,ventana}` (distribución de **uso/tope**: 1,0 = justo en el tope; > 1 habría bloqueado) con histograma de percentiles. El panel nuevo va en `infra/observabilidad/grafana/dashboards/renaser-proteccion.json` y hay que comprobar que `config.alloy` deja pasar `renaser_limite_*`.
  - **Quién habría bloqueado**, de forma durable (el log de Docker solo vive desde el último despliegue): `HINCRBY proteccion:habria:<grupo>:<AAAA-MM-DD> <idCuenta> 1`, TTL de 30 días, sin datos personales (solo el UUID de la cuenta). `scripts/proteccion/cuentas-que-habrian-bloqueado.sh <grupo> [día]` lo lista (lo corre el dueño; usa `REDISCLI_AUTH`).
- **Interruptor por grupo sin reiniciar** (Q6): el modo efectivo es el de Redis `proteccion:modo:<grupo>` si existe y, si no, el de la propiedad; el filtro lo cachea 30 s. `scripts/despliegue/limites-modo.sh <grupo> <modo>` lo cambia (simulacro por omisión, como todo script de D12).

### Contrato y app

- 429 según §2. El filtro responde él mismo (no lanza una excepción hacia `GlobalExceptionHandler`: corre antes de MVC) con el mismo formato de `FiltroDeSoloLectura`: JSON con `message`, `timestamp` y `codigo`.
- **App** (repositorio de la app, rama aparte, FR-020): `apiFetch` lee `Retry-After`; la cola sin red espera ese tiempo antes de reintentar en vez de su espera creciente cuando es mayor; una pantalla que muestra un error de 429 usa el `message`. Sin cambios en los cinco tabs.

### SER y la IA fuera de SER

- `ser`: el filtro suma el tope de 10 por minuto; los 25 por día siguen en `ControlCuotaRedisAdapter` **sin tocarse**.
- `ia-fuera-de-ser`: 5 por minuto en el filtro; la **cuota diaria compartida** (Q3) se descuenta en el caso de uso de cada herramienta a través de un método nuevo de `rag.api` (`CuotaDiariaDeIa`), **antes** de llamar al proveedor y **fuera** de cualquier `@Transactional` (regla de arquitectura C-1). El peso por generación sale de Q3.

### Cómo se mide durante dos semanas

1. Día 0: el tramo C llega a producción con **todos los grupos en `REGISTRAR`** (y A3 en `dry_run`, si ya está).
2. Cada día: el panel «Protección» (distribución de uso por grupo, `habria_bloqueado` por grupo) y, si algo sube, el script de «quién habría bloqueado».
3. **Mirar de manera especial** los picos tras reconexión: la cola de la spec 048 envía en ráfaga cuando vuelve la señal (hábitos, radar, subidas de fotos), y el grupo `escritura` (60 / min) y `subidas` (20 / min) podrían quedar cortos para esa ráfaga. Si pasa, o se sube el tope, o se hace que la cola espacie sus envíos; se decide con el dato.
4. Día 14: tabla de valores propuestos por grupo = **el mayor entre el valor de partida y 2 × p99 observado**, y la lista de cuentas que habrían bloqueado, revisadas una por una (abuso vs. tope mal puesto) → SC-003.
5. Se pasan a `APLICAR` **de a uno**, empezando por los de menor riesgo (`exportaciones`, `admin`, `ia-fuera-de-ser`, `subidas`) y terminando por `muro`, `chat`, `escritura` y `lectura`, con la métrica de `bloqueado` abierta y la vuelta atrás a un comando.

## 6. Tramo D — Cuenta de servicio del CRM (FR-022 a FR-027)

**Migración V192** (verificar con `git fetch` el último número usado al abrir la rama; hoy el último es V191): `V192__rol_integracion_crm.sql`, **sola** en su archivo (`ADD VALUE` no puede usarse en la misma transacción, E-187), con la cabecera que exige la regla 04: *por qué se toca la base* (el dueño pidió una cuenta propia de solo lectura para el CRM, en vez de la de Alquimista que puede todo), *por qué no se reusa ADMIN con una marca* (la matriz tendría que mirar la marca en cada guard; con un rol propio la matriz niega por defecto, como Soporte TI en V100) y *por qué ese nombre* (`INTEGRACION_CRM` en español como los demás valores del tipo, D-21; la API lo llama `CRM_READER`). `ALTER TYPE renaser.rol_usuario ADD VALUE IF NOT EXISTS 'INTEGRACION_CRM';`

**Código**: `UserRole.CRM_READER` + `RolUsuarioJpa.INTEGRACION_CRM` + traducción en `UserPersistenceMapper`; `nombreVisible()` «Integración CRM»; `can(...)` con su conjunto cerrado.

**Alcance cerrado**: `users/infrastructure/adapter/in/web/security/AlcanceDeCuentaDeIntegracion.java`, invocado por `PermissionEnforcementInterceptor` justo después de la comprobación de cuenta suspendida (ya resuelve el actor y su rol; no se duplica la lectura de la base). Es una **lista de permitidos**: método y patrón de ruta; todo lo demás, 403 `FUERA_DE_ALCANCE_DE_LA_INTEGRACION`. También cuenta el tope de **60 / min** con el puerto `ContadorDeVentanas` del tramo C (grupo `crm`).

**Los servicios**: `CuotasDeTodosService` y el listado de `TraineeAdminService` aceptan el rol nuevo **solo en la lectura**; `DELETE /admin/pagos/cuotas/{id}` y `PUT /admin/trainees/{id}/program-day` comparten permiso con esas lecturas y por eso quedan protegidos por la lista de permitidos **y** por el guard del servicio, que no lo acepta.

**Lo que hay que revisar a propósito** (tiene más alcance de lo que parece): hay al menos siete usos, en seis archivos, que recorren `UserRole.values()` o `EnumSet.allOf(UserRole.class)` (audiencias de hábitos, catálogo y consola de interruptores, «ver como», roles personalizados). Un `UserRole.deLasPersonas()` los excluye y una prueba por lugar lo comprueba; además, ninguna consulta que arme una lista de personas (`AND NOT es_cuenta_de_prueba`, rol en lista) debe traer a la cuenta del CRM. Alternativa descartada: una marca sobre un rol existente (el rol seguiría siendo ADMIN y la seguridad dependería de que la lista de permitidos nunca falle).

**Cómo nace la cuenta**: `scripts/crm/asignar.sql` y `scripts/crm/asignar-en-produccion.sh`, copiando el patrón de `scripts/soporte-ti/` (el rol se asigna a **una cuenta ya existente y activa**, el correo entra por variable de `psql`, nunca en el repositorio; verifica antes de cambiar nada y aborta sin tocar la fila). La contraseña la fija el dueño con el flujo de restablecer. RenaserOs entra con ella (Q1) y vuelve a entrar si recibe 401.

**Registro**: una línea INFO por pedido de la cuenta con ruta, estado y cantidad de filas, sin datos de personas.

## 7. Tramo E — Tope absoluto de sesión (FR-028 a FR-031)

- `shared/domain/TopeDeSesion.java` (puro: `vencida(creadaEn, ahora, tope)`), `shared/web/security/FiltroDeTopeDeSesion.java` y su `…Config` (instancia, orden **−92**: después de Spring Security, que ya cargó la sesión, y antes del filtro de solo lectura). Lee `HttpSession.getCreationTime()` (Spring Session lo guarda como `creationTime`) y el `Clock` del puerto.
- Pasado `renaser.sesion.tope-absoluto` (90 días; **vacío o `0` = sin tope**, que es la vuelta atrás): `sesion.invalidate()` y 401 `SESION_VENCIDA` (§2). Las rutas públicas (login y demás) **pasan** aunque la sesión que traían venza, para que la app nunca quede sin poder volver a entrar; la sesión se invalida igual.
- `spring.session.timeout: 30d` **no se toca**; «ver como» (30 min) tampoco.
- **Aviso**: nadie queda fuera el día del despliegue (las sesiones más viejas aún no llegan a los 90 días); la primera tanda llegará al tope unas semanas después. Antes de activarlo, un conteo de solo lectura de sesiones por `creationTime` dice cuántas personas tendrán que volver a entrar y cuándo, para avisarles.
- La app ya lleva al login en un 401 con sesión mandada conservando la cola (spec 048, FR-001/FR-003); se comprueba con un E2E.

## 8. Tramo F — Exportaciones y listas acotadas (FR-032 a FR-034)

- **CSV en streaming**: `CajaAdminController.exportar` y `DiagnosticoAdminController.exportar` devuelven `ResponseEntity<StreamingResponseBody>`. El caso de uso (`exportar(actor, EscritorDeFilas)`) **comprueba el guard antes de escribir** (un error a mitad de la respuesta ya no puede cambiar el estado HTTP) y entrega las filas **por lotes** (Caja: lotes del padrón; Diagnóstico: páginas de la consulta); el CSV lo da el adaptador de entrada. Prueba de caracterización **antes** de tocar: el CSV nuevo es idéntico byte a byte al de hoy para el mismo dato (SC-008).
- **Caja** (Q4, recomendada A): se mantiene el cálculo del estado derivado en Java, pero `resumenes()` deja de rearmarse por cada página del listado (cache corto o cálculo por lotes) y se documenta el techo; la medición de su costo queda en el PR. La alternativa B (columna materializada) pide migración y rompe «derivar, no guardar».
- **Topes**: `PaginaAcotada` (valor de `shared/domain`: `acotar(valor, mínimo, máximo)`) usado en `PersonasDelPrograma`, `ConsolaSoporteTiController` (`auditoria`) y en todo controller con `size`/`limit`/`tamano`; **regla de código** nueva en `scripts/reglas-de-codigo.test.mjs`: un `@RequestParam … int size|limit|tamano` en un controller que no pase por `PaginaAcotada` falla, con el mensaje de qué está mal y cómo se arregla.
- Los feeds propios (Muro, notificaciones, ranking, tickets) que el inventario no pudo comprobar uno a uno se revisan con esa misma regla.

## 9. Tramo G — Despliegue sin corte (FR-035 a FR-039)

**Lo que ya existe y no se reescribe**: `desplegar-backend.sh` ya hace blue/green con nginx (levanta `backend-nuevo`, espera `UP`, intercambia nombres, nginx lo resuelve en ≤ 5 s, se apaga el viejo). El 2026-11-11 no lo usó porque la condición de memoria no alcanzó (**1.890 MB disponibles vs 1.912 pedidos**, aun parando Alloy).

1. **G0 — medir (solo lectura)**: `scripts/despliegue/memoria-de-la-instancia.sh` (por SSM): `MemAvailable`, RSS real de cada contenedor (`docker stats --no-stream`), memoria de la JVM y lo que se come el sistema; y la tabla «necesario» frente a «medido» del script actual. Se corre **antes y durante** tres despliegues.
2. **G1 — decidir con el dato (Q5)**: si lo medido dice que el margen de 512 MB es mayor que lo necesario, se baja con la medición a la vista y escrita en la bitácora; si no, se sube la instancia (`t3.large`) y el script no cambia. **La condición no se baja a ojo** (E-155: la última vez cayó el agente de SSM y la producción quedó siete horas caída).
3. **G2 — 503 en vez de 502**: en el modo con corte, `error_page 502 503 504` de nginx devuelve el cuerpo del §2 con `Retry-After`, de modo que la app y la cola lo traten como «reintenta».
4. **G3 — la sonda**: `scripts/despliegue/sonda-de-salud.sh <url>` pide `/salud` cada segundo por el borde durante un despliegue y cuenta lo que no sea 200 (FR-035, SC-009); se corre en tres despliegues seguidos.
5. **G4 — la vuelta atrás** (FR-039): se prueba en local con Docker el comando que el script imprime.

**Recomendación de orden**: **G0 va primero de todo** (es de solo lectura y gratis): cada tramo siguiente se despliega, y cada despliegue sin corte son 1–2 minutos de 502 para la gente que usa la app.

## 10. Tramo H — Matriz estricta de MENTOR y TRAINEE_GUIDE (FR-040 a FR-044)

Es el tramo más grande y el único que toca lo que la gente **hace** a diario; por eso va **último** y en cuatro pasos, cada uno fusionable:

1. **H1 — inventario y propuesta (solo documentos)**: la lista de los permisos que declaran los handlers (`@RequiresPermission`, con y sin `value =`) y, por cada uno, qué rol lo usa hoy en la app (se saca de las pantallas de Mentoría y de los flujos de Mentor/Guía y del registro de sombra de MENTOR_LEAD). **El conjunto de permisos de MENTOR/GUÍA lo aprueba el dueño** (regla 00): ahí van los candidatos (USE_APP, TRACK_PROGRAM_AS_STAFF, FOLLOW_OWN_PROGRAM, PUBLISH_ON_WALL, OPEN_SUPPORT_TICKET, ANSWER_MENTOR_TICKET, MANAGE_EVIDENCE, MANAGE_CALENDAR…), **sin darlos por buenos**.
2. **H2 — sombra**: `UserRole.can` pasa a negar por defecto con el conjunto aprobado y `PermissionEnforcementInterceptor` aplica la matriz a MENTOR y TRAINEE_GUIDE con `renaser.security.mentor-enforcement=false` (registra el permiso, el método y la ruta, **no** la cuenta, igual que MENTOR_LEAD). Se deja en producción hasta que el registro esté limpio.
3. **H3 — aplicar**: la propiedad pasa a `true` (Q7: lo mismo para `mentor-lead-enforcement` si su registro está limpio). Los guards de servicio **no se tocan** (FR-042).
4. **H4 — pruebas y E2E**: la prueba que recorre todos los handlers por rol (misma técnica que `CuentaSuspendidaEnTodoEndpointTest`) y **un E2E por rol** (Mentor y Guía) contra el backend local, con mobile-mcp y Maestro: Mentoría, grupos, aprendices, tickets, evidencias, cursos, eventos, su propio programa. Un 403 nuevo en un flujo legítimo es bloqueante.

Cuidado conocido: varios guards comparan el rol contra `TRAINEE` literal (E-169); la matriz no los arregla, solo se asegura de no empeorarlos.

## 11. Orden de salida

```
G0 (medir)  ─────────────────────────────────────────────►  (en paralelo, solo lectura)
A1 Redis ► A2 Origen ► B WAF ► C Filtro (+ app 429) ► D CRM ► E Sesión ► F Exportaciones ► G1–G4 ► H1 ► H2 ► H3/H4
                         └─ A3 (nginx por IP) entra cuando D1 esté en producción; se mide junto con C
```

Dependencias duras: **A3 después de D1**; **D después de C** (usa el puerto del contador y el grupo `crm`); **H después de D** (los dos tocan `UserRole`); **C-app antes de pasar a `APLICAR`** cualquier grupo de escritura. El resto es independiente, y todos pueden fusionarse por separado.

## 12. Riesgos

| Riesgo | Qué lo amortigua |
|---|---|
| Un tope por IP o por cuenta mal puesto deja sin servicio a gente legítima (IP compartida, cola sin red que envía en ráfaga). | Todo nace en `Count`/`REGISTRAR`/`dry_run`; dos semanas de medición; se bloquea de a un grupo con la vuelta atrás a un comando; los valores finales salen de datos (SC-003). |
| Las reglas administradas del WAF acusan a texto libre legítimo (Muro, diario, transcripciones). | `Count` primero, registro con revisión y exclusión de la regla por nombre. |
| El cambio de contraseña de Redis puede dejar a la API sin sesiones. | Orden en cuatro pasos (credencial primero, exigencia en caliente, persistencia después), ensayo local previo y datos conservados con `SAVE`. |
| La cabecera de origen mal aplicada rechaza al propio CRM, al CD o a CloudFront. | Modo `registrar` antes de `exigir`, loopback exento, rotación con dos secretos vigentes. |
| El rol nuevo del CRM aparece en listas de personas. | `UserRole.deLasPersonas()`, una prueba por cada recorrido de `UserRole.values()` y una consulta de revisión. |
| El tope de 90 días saca a mucha gente a la vez. | Conteo previo de sesiones por antigüedad, aviso, y la propiedad vacía como vuelta atrás. |
| La matriz estricta deja sin una función a un mentor. | Modo sombra, aprobación de la tabla por el dueño, E2E por rol y `mentor-enforcement=false` como vuelta atrás. |
| Un despliegue necesita memoria para dos JVM. | G0 mide, Q5 decide; el comportamiento seguro de hoy (reemplazo con corte) no cambia mientras tanto. |
| Redis caído deja pasar el filtro. | Intencional (FR-017): con Redis caído la sesión tampoco se lee y la API no atiende. |
| Un recurso de AWS creado a mano por error. | Todo por script del repositorio, con simulacro por omisión y respaldo de la configuración previa. |
