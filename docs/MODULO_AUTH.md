# Autenticación propia — sesión opaca en Redis + cookie `HttpOnly`

**Fecha:** 2026-08-26
**Estado:** diseño aprobado, construcción en curso
**Alcance:** reemplazar el header temporal `X-Actor-Id` por autenticación real gestionada por nosotros con Spring Security, con login por contraseña y login social (Google, Apple, Facebook).

> Este documento **supersede parcialmente** a `docs/MODULO_USERS.md` §5. Lo que cambia y lo que se conserva está explícito en §1.2. Nada de lo que se conserva se re-discute acá.

---

## 1. La decisión

### 1.1 Qué se decidió (D-49)

**Renaser OS emite y valida su propia identidad.** No hay proveedor externo de identidad: ni Supabase Auth, ni Keycloak, ni Cognito. La sesión es un **token opaco guardado en Redis**, transportado en una **cookie `HttpOnly`**.

Decisión del dueño del proyecto (2026-08-26), tomada sobre la recomendación contraria — que era conservar Supabase Auth (D-9/D-18) — y sostenida después de exponerle el costo. Queda registrada como tal: es una decisión informada, no un descuido.

### 1.2 Qué supersede y qué se conserva de `MODULO_USERS.md` §5

| Decisión previa | Estado |
|---|---|
| **D-9** — Supabase Auth, no Keycloak | **Superada.** La conclusión operativa (no meter Keycloak) se conserva; la premisa (usar Supabase) no |
| **D-18** — login social directo contra Supabase, el backend Java no implementa OAuth | **Superada.** Ahora el backend **sí** implementa el intercambio de OAuth. Ver §6 |
| **D-19/D-31** — cookies también para la app móvil | **Se conserva y se implementa acá.** Este documento resuelve lo que D-31 dejó pendiente (esquema CSRF, cookie jar) |
| **D-10** — el rol vive en nuestra tabla, nunca en el token | **Se conserva y se refuerza.** Con sesión opaca el token no tiene claims en absoluto: no hay dónde poner un rol aunque se quisiera |
| **D-11** — defensa en profundidad de 6 capas | **Se conserva.** Cambia solo la capa 1-2 (ver §7.4) |
| **D-12** — `@RequiresPermission` en el método, no en patrones de URL | **Se conserva.** Sigue pendiente (bloqueado por R-2) |
| **B-2** — confirmar RS256/JWKS de Supabase | **Cerrado por irrelevancia.** Ya no dependemos de tokens de Supabase. *(Dato verificado igual el 2026-08-26: el proyecto `apvnaigldsjqeloiolcu` sí tiene claves asimétricas, algoritmo **ES256**. Se anota por si alguna vez se revierte esta decisión.)* |
| **B-4** — auditar RLS `INSERT` en `public.users` | **Sigue vivo** mientras la base de producción vieja siga en pie |

### 1.3 Por qué sesión opaca y no JWT

Un JWT sirve para **verificar sin preguntarle al emisor**. Ese es su único valor real, y se cobra caro. Tenemos un emisor y un consumidor, ambos nuestros: no hay frontera de confianza que cruzar, así que pagaríamos el costo sin comprar el beneficio.

| | JWT de sesión | **Sesión opaca (elegido)** |
|---|---|---|
| Revocar al instante | **Imposible por diseño.** Vale hasta que expira | `DEL` en Redis; muere en esa misma request |
| Rol/estado frescos | Viajan congelados dentro del token | Se leen al momento |
| Contenido legible | Sí — base64url, siempre | **No hay contenido.** Es un aleatorio de 256 bits |
| Tamaño por request | 500–1000+ bytes | ~43 bytes |
| Superficie criptográfica | `alg:none`, confusión de algoritmo, `aud`/`iss`, rotación de claves | Ninguna. Se busca una clave en Redis |

**La trampa que evitamos:** el que arranca con JWT y después necesita revocar termina agregando una lista negra en Redis — pagó el costo de tener estado **y** se quedó con las desventajas del token. Nosotros vamos a Redis a propósito, y por eso el único argumento a favor del JWT (*"me ahorro la lectura"*) no aplica: esa lectura **es** lo que compramos.

**Efecto colateral bueno:** `MODULO_USERS.md` §5.3.5 tenía un caché con TTL de 30 s como red de seguridad porque *"un SUSPENDIDO no puede tardar 30 s en quedar afuera"*. Con sesión opaca eso deja de ser un parche: suspender a alguien le revoca las sesiones **en el acto** (§7.4).

**Dónde SÍ usamos JWT, y bien:** el *ID token* que mandan Google y Apple es un JWT. Lo verificamos **una sola vez** al iniciar sesión, extraemos `sub` y `email`, y lo descartamos. Eso es un JWT en su lugar correcto: una afirmación que cruza una frontera de confianza y se verifica una vez. El `client_secret` de Apple también es un JWT, firmado por nosotros (§6.3).

> **Regla que queda:** JWT para cruzar fronteras. Sesión opaca para nuestra propia casa.

---

## 2. Cambios de esquema — excepción acotada a D-40

D-40 congeló la base en 90 tablas: *"ni tablas nuevas, ni ALTER, ni seeds"*. Autenticar en casa es imposible sin guardar credenciales, así que se abre una **excepción explícita y acotada**, negociada a la baja con el dueño del proyecto (la propuesta inicial eran 4 tablas):

### 2.1 Lo que se agrega

**Dos columnas en `usuarios`** (migración `V2__auth_credenciales.sql`):

```sql
ALTER TABLE usuarios ADD COLUMN hash_contrasena text;
ALTER TABLE usuarios ADD COLUMN contrasena_actualizada_en timestamptz;
```

`hash_contrasena` **admite NULL a propósito**: un usuario que solo entra por Google/Apple no tiene contraseña, y eso es un estado válido, no un dato faltante.

**Una tabla nueva** (migración `V3__auth_identidades_externas.sql`):

```sql
CREATE TABLE identidades_externas (
    id                 uuid PRIMARY KEY,
    usuario_id         uuid NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    proveedor          text NOT NULL,      -- GOOGLE | APPLE | FACEBOOK
    sujeto_proveedor   text NOT NULL,      -- el `sub` del proveedor. Estable, opaco, NO el email
    email_proveedor    text,               -- informativo. NUNCA se usa para resolver identidad
    vinculada_en       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (proveedor, sujeto_proveedor)
);
```

### 2.2 Por qué cada una está donde está

**El hash va en `usuarios`, no en tabla propia.** Es 1:1 estricto: una credencial no tiene identidad propia, ni ciclo de vida propio, ni sentido sin su usuario. Una tabla aparte sería sobre-normalizar y además metería un JOIN en el camino más caliente del sistema. En dominio, `Credencial` es un **value object dentro del agregado `User`**, no un agregado.

> **Condición no negociable:** esas dos columnas **nunca se mapean en `UserJpaEntity`**. Una entidad JPA aparte (`CredencialJpaEntity`, misma tabla, solo `id` + las dos columnas) las lee, y solo la usa el caso de uso de login. Si el hash entra al `UserJpaEntity` general, tarde o temprano sale por un `UserResponse`, por un log o por un export de admin — es la fuga clásica y §5.4.10 ya la prohíbe.

**Las sesiones NO van a Postgres: van a Redis.** Son efímeras por naturaleza, tienen vencimiento propio y se consultan una vez por request. Redis las expira solo (TTL nativo) — sin cron de purga, sin tabla que crezca, sin tabla nueva. Detrás de `SesionRepositoryPort`, así que darles durabilidad después es cambiar un adaptador.

**`identidades_externas` sí necesita tabla** porque es 1:N: un usuario puede vincular Google **y** Apple **y** Facebook a la vez. Y la restricción `UNIQUE (proveedor, sujeto_proveedor)` **es la frontera de seguridad** — es lo que impide que quien controle una cuenta social se apodere de un usuario nuestro ya existente (§6.4). Eso no cabe en una columna.

**No hay tabla de tokens de verificación**, y esto se evaluó dos veces:
- *Verificación de correo:* **no hace falta.** El alta pasa por `AccountRequest` y **la aprueba un ADMIN** — esa aprobación es la verificación, y es más fuerte que un mail. En login social el email llega pre-verificado por el proveedor (ya estaba dicho en D-18).
- *Reset de contraseña:* hace falta, pero es un token de un solo uso que vive 30 minutos → **Redis con TTL**. El "un solo uso" sale gratis: se borra al leerlo.

### 2.3 Lo que NO cambia

Ninguna de las 90 tablas existentes cambia de forma, salvo las dos columnas agregadas a `usuarios`. Ningún módulo fuera de `users` toca esquema. La regla general de D-40 (*"ningún módulo crea migraciones Flyway"*) sigue en pie para los otros 13.

---

## 3. Dónde vive: dentro de `users`

No se crea un módulo `auth`. Razones:

1. **`Credencial` es literalmente un campo del usuario**, y `Sesion`/`IdentidadExterna` no significan nada sin un `UserId`.
2. Un módulo `auth` separado tendría que preguntarle a `users` el rol y el estado **en cada request** — el acoplamiento que Modulith existe para señalar.
3. `MODULO_USERS.md` §5 y CLAUDE.md §5.3.5 ya ubican el filtro de autenticación en `users`.

`users` pasa de 4 a 6 agregados. Eso está dentro de lo que §5.1.2 admite: la señal de alarma es *"¿tengo más de un agregado sin haberlo notado?"*, y acá están separados y nombrados a propósito.

```
users/
├── domain/model/
│   ├── user/
│   │   ├── User.java                      (+ credencial como value object)
│   │   ├── Credencial.java                ← NUEVO. Hash + política de contraseña. Sin Spring
│   │   └── ...
│   ├── identidadexterna/                  ← NUEVO agregado
│   │   ├── IdentidadExterna.java
│   │   └── ProveedorIdentidad.java        (GOOGLE | APPLE | FACEBOOK)
│   ├── accountrequest/  mentorprofile/  participante/     (sin cambios)
├── application/
│   ├── ports/in/autenticacion/
│   │   ├── IniciarSesionUseCase.java
│   │   ├── IniciarSesionConProveedorUseCase.java
│   │   ├── CerrarSesionUseCase.java
│   │   ├── CerrarTodasLasSesionesUseCase.java
│   │   └── ValidarSesionUseCase.java      (lo consume el filtro)
│   ├── ports/out/autenticacion/
│   │   ├── LoadCredencialPort.java / SaveCredencialPort.java
│   │   ├── LoadIdentidadExternaPort.java / SaveIdentidadExternaPort.java
│   │   └── VerificadorIdentidadProveedor.java   ← puerto polimórfico, §6.2
│   └── services/AutenticacionService.java
└── infrastructure/
    ├── config/SeguridadConfig.java        ← SecurityFilterChain (se muda desde shared/web)
    └── adapter/
        ├── in/web/security/ActorAutenticadoArgumentResolver.java   ← §8
        ├── in/rest/autenticacion/AutenticacionController.java
        └── out/
            ├── persistence/user/CredencialJpaEntity.java
            └── oauth/{Google,Apple,Facebook}IdentidadAdapter.java
```

**No hay `HashContrasenaPort` ni adaptador de hasheo.** `ArchitectureTest` prohíbe en `application/` solo `org.springframework.web..`, `..http..` y `..data..` — `security.crypto` está permitido, así que el `PasswordEncoder` se inyecta directo en el servicio. Un puerto ahí sería una indirección que no compra nada.

**Tampoco hay adaptador de sesión ni filtro propio**: los pone Spring Session y Spring Security (§4).

---

## 4. La sesión: Spring Session sobre Redis, sin código propio

**Decisión (D-50, corrección del 2026-08-26):** la sesión la maneja **`spring-session-data-redis`**, no código nuestro.

El primer borrador de este documento diseñaba a mano el token, su huella SHA-256, el agregado `Sesion`, el adaptador de Redis y la lógica de TTL. Eso era reescribir algo que el framework ya trae probado — señalado por el dueño del proyecto y corregido antes de construirlo. Lo que Spring Session da hecho:

| Necesidad | Quién la resuelve |
|---|---|
| Generar un identificador opaco de sesión | Spring Session |
| Guardarlo en Redis y expirarlo por TTL | Spring Session |
| Poner y leer la cookie, con sus atributos | `DefaultCookieSerializer` (configuración) |
| Cerrar todas las sesiones de un usuario | `FindByIndexNameSessionRepository` |
| Funcionar entre varias instancias | Spring Session (es su razón de ser) |

Queda a nuestro cargo **solo lo que es del negocio**: verificar la contraseña, resolver la identidad social, y decidir cuándo se revoca (§7.4).

### 4.1 Configuración

```yaml
spring.session:
  store-type: redis
  timeout: 30d                    # inactividad
  redis.repository-type: indexed  # habilita "cerrar todas las sesiones de este usuario"
```

`indexed` no es el valor por defecto y es el que importa: sin él, Redis no mantiene el índice por usuario y revocar en bloque al suspender a alguien (§7.4) no se puede hacer sin recorrer todo.

Los 30 días de inactividad son deliberadamente largos: es una app de hábitos que se abre a diario, y forzar el login cada semana solo empuja a la gente a poner contraseñas peores.

### 4.2 Lo que se pierde respecto del diseño a mano, dicho explícitamente

El borrador guardaba en Redis el **SHA-256** del token en vez del token, para que un volcado de Redis no entregara sesiones utilizables. **Spring Session guarda el identificador tal cual.** Es una capa de defensa en profundidad que se resigna a cambio de no mantener código de sesión propio.

El intercambio se acepta porque quien puede volcar nuestro Redis ya tiene un nivel de acceso donde hay problemas peores, y porque el código que evitamos escribir es exactamente donde se cometen los errores sutiles de seguridad. Queda anotado por si algún día el modelo de amenaza cambia.

### 4.3 Rotación

Spring Security rota el identificador al autenticarse (`sessionFixation().changeSessionId()`, su comportamiento por defecto). Cierra la fijación de sesión sin que escribamos nada.

---

## 5. La cookie

```
Set-Cookie: __Host-sesion=<token>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=2592000
```

| Atributo | Por qué |
|---|---|
| `__Host-` | Prefijo con significado para el navegador: **obliga** a `Secure`, `Path=/` y sin `Domain`. Impide que un subdominio comprometido escriba la cookie de sesión |
| `HttpOnly` | JavaScript no la puede leer. Neutraliza el robo de sesión por XSS en el futuro panel web |
| `Secure` | Solo por HTTPS. En `local` se relaja por configuración, **nunca por código** |
| `SameSite=Lax` | Base de la defensa CSRF. `Strict` rompería el retorno del navegador en el flujo OAuth (§6) |

**El nombre nunca se loguea junto con el valor.** El token de sesión es una credencial: entra en la misma lista de §5.4.9 que los tokens y la PII.

### 5.1 React Native y las cookies (lo que D-31 dejó pendiente)

D-31 aceptó a conciencia que RN no maneja cookies como un navegador. Concretamente:

- El `fetch` de RN usa la pila HTTP nativa, que **sí tiene** cookie jar (`NSHTTPCookieStorage` en iOS, `CookieManager` de OkHttp en Android), pero su comportamiento difiere entre Expo Go y un build standalone.
- Hace falta un cookie jar explícito en el cliente. Queda como **tarea del lado de la app**, no del backend.

> **Advertencia honesta:** este es el punto de mayor riesgo de integración de todo el diseño. Si el jar de RN da problemas, el plan B es mandar el mismo token opaco en `Authorization: Bearer` **solo para la app nativa** — no cambia nada del servidor (mismo token, misma búsqueda en Redis, misma revocación), solo el transporte. Y para una app nativa el argumento de `HttpOnly` es mucho más débil que en un navegador: no hay DOM, así que no hay XSS que robe la cookie. Se decide con la app en la mano, no antes.

### 5.2 CSRF

Con cookies, CSRF deja de ser "no aplica" (lo dice D-31). Esquema: **double-submit cookie**, el estándar de Spring — `CookieCsrfTokenRepository.withHttpOnlyFalse()`, el cliente lee la cookie `XSRF-TOKEN` y la repite en el header `X-XSRF-TOKEN`. Obligatorio en todo método mutable (`POST`/`PUT`/`PATCH`/`DELETE`), no en `GET`.

Nota: exigir un header propio ya es, por sí solo, una defensa CSRF válida (un navegador no puede mandar headers arbitrarios cross-origin sin pasar por *preflight* de CORS). El double-submit se elige igual porque es lo que Spring Security trae probado y porque el futuro panel web lo va a necesitar sin discusión.

---

## 6. Login social

### 6.1 El flujo (Authorization Code + PKCE)

```
App RN ──(1) abre navegador del sistema──► Google/Apple/Facebook
       ◄─(2) redirect con `code` ─────────┘
       │
       └──(3) POST /api/v1/auth/social {proveedor, code, code_verifier}──► Backend
                                                                              │
                          (4) canjea el code por tokens usando el client_secret│
                              ◄──────────────────────────────────────────────►│ Proveedor
                                                                              │
                          (5) verifica el ID token (firma, iss, aud, exp, nonce)
                          (6) busca IdentidadExterna(proveedor, sub)
                          (7) crea sesión ──► Set-Cookie __Host-sesion
```

**El `client_secret` vive solo en el servidor.** Ese es el motivo técnico por el que el canje lo hace el backend y no la app: una app móvil es un cliente público, cualquier secreto que se le embeba se extrae del binario. Con este flujo, la app nunca ve un secreto.

**PKCE es obligatorio** en los tres proveedores (lo exige OAuth 2.1 y es lo correcto para clientes públicos).

### 6.2 Un solo puerto, tres adaptadores

Mismo patrón de polimorfismo que se acaba de aplicar a las políticas de hábitos — un puerto abierto, un adaptador por proveedor, y un registro que resuelve:

```java
public interface VerificadorIdentidadProveedor {
    ProveedorIdentidad proveedor();
    IdentidadVerificada verificar(CanjeCodigoCommand command);
}

public record IdentidadVerificada(String sujeto, String email, boolean emailVerificado, String nombre) { }
```

Agregar un proveedor nuevo = una clase `@Component` nueva. Ni el caso de uso ni el dominio se enteran (Open/Closed, §5.4.8).

### 6.3 Particularidades por proveedor

| Proveedor | Lo que hay que saber |
|---|---|
| **Google** | El más simple. Hace falta crear el OAuth client en Google Cloud Console — **hoy no existe** (`google-services.json` tiene `oauth_client` vacío) |
| **Apple** | **Obligatorio en iOS** si se ofrece cualquier otro login social (App Store, guía 4.8). Su `client_secret` **no es un string**: es un JWT que firmamos con una clave `.p8` y que **caduca a los 6 meses como máximo** — hay que regenerarlo periódicamente o el login se cae solo. Además Apple manda el nombre del usuario **una única vez**, en el primer login: si no se guarda ahí, se pierde |
| **Facebook** | Meta exige revisión de app y verificación de negocio. El trámite tarda — conviene iniciarlo antes de necesitarlo |

### 6.4 Primer login social de alguien sin fila en `usuarios`

Se conserva lo ya resuelto en D-18/R-5: **no se crea el usuario en silencio.** Se abre un `AccountRequest` y un ADMIN aprueba y asigna el rol. El email llega pre-verificado por el proveedor, así que no se pide verificación aparte.

**La regla de seguridad que no se negocia:** la identidad se resuelve **siempre por `(proveedor, sujeto)`, nunca por email.** Vincular por email permitiría que quien registre una cuenta social con el correo de un aprendiz existente se apodere de su cuenta. Un email que coincide con un usuario ya existente **no vincula automáticamente**: requiere que el dueño de la cuenta lo confirme estando ya autenticado.

> Esa confirmación autenticada **ya existe** desde el 2026-09-01: `POST /api/v1/auth/social/link`, ver §6.9.

### 6.5 Apple y Facebook — hecho 2026-08-26

Construidos como `@Component` que implementan `VerificadorIdentidadProveedor` (el puerto y el enum `ProveedorIdentidad` **no existían todavía** cuando se arrancó esta tarea, así que se crearon con la forma exacta encargada — ver §6.2 — para que el trabajo en paralelo de Google los reuse sin tocarlos). También se creó `CanjeCodigoCommand` (no estaba especificado en el puerto, hacía falta como entrada de `verificar`): self-validating, con `code`/`codeVerifier`/`redirectUri`, ninguno se loguea.

**`AppleIdentidadAdapter`** (`users/infrastructure/adapter/out/oauth/`):
- El `client_secret` (JWT ES256, firmado con la clave `.p8` vía `nimbus-jose-jwt` — ya venía transitivamente por `spring-boot-starter-security-oauth2-resource-server`, no hizo falta agregar dependencia nueva) se **regenera en cada intercambio de código**, con 5 minutos de vigencia. Decisión de diseño no cerrada por el documento: la alternativa era cachear el JWT y rotarlo cerca del vencimiento de 6 meses, pero regenerarlo siempre hace que "vencido" sea imposible por construcción, a costa de una firma criptográfica de más por login — un costo despreciable frente a la llamada de red que sigue.
- El ID token se decodifica con `NimbusJwtDecoder.withJwkSetUri` (bean separado en `AppleJwtDecoderConfig` para poder inyectar un decoder falso en los tests sin pegarle a la red). Además de firma/vigencia, el adaptador verifica a mano `iss=https://appleid.apple.com` y que `aud` contenga el `client_id` — no se valida `nonce` porque `CanjeCodigoCommand` no lo lleva (no estaba en el alcance de esta tarea; si hace falta, es un campo más en el comando y una comparación más acá).
- `nombre` queda siempre `null`: confirmado el límite documentado en §6.3 (Apple no lo manda en el ID token). Si la app captura el nombre en el primer login, tiene que mandarlo aparte — este adaptador no tiene de dónde sacarlo.

**`FacebookIdentidadAdapter`** (mismo paquete): dos llamadas REST con `RestClient` (canje de código → `access_token`, y `GET /me?fields=id,email,name`), sin ID token que verificar (Facebook es OAuth 2.0 plano, no OIDC). `emailVerificado` se deriva de "¿la Graph API devolvió un email?" — **supuesto documentado, no confirmado contra la API real** (sin credenciales de Meta para probarlo). Versión de Graph API fijada en `v21.0` como constante; Meta las deprecha cada ~2 años.

**Lo que NO se construyó, a propósito:** el caso de uso compositor (`IniciarSesionConProveedorUseCase`), el controller (`POST /api/v1/auth/social`), y el agregado `IdentidadExterna` con su persistencia. La instrucción de esta tarea permitía construirlos si hacía falta para que los adaptadores quedaran usables, pero eso es exactamente lo que "el flujo de Google" (§6, en construcción en paralelo) más probablemente ya cubre o va a cubrir — construirlo acá corría el riesgo real de pisar archivos que el otro agente estaba escribiendo al mismo tiempo (confirmado: `GlobalExceptionHandler.java` fue tocado por un tercer agente, el de reset de contraseña, mientras esta tarea corría, sin conflicto porque cada uno agregó su handler en un punto distinto). Los dos adaptadores quedan como `@Component` listos: cualquier caso de uso que inyecte `List<VerificadorIdentidadProveedor>` los va a encontrar automáticamente vía Spring, sin registro manual.

### 6.6 Pendiente antes de que Apple/Facebook funcionen en producción

- Dar de alta el Services ID + la App ID de Apple en Apple Developer, descargar el `.p8`, y cargar `APPLE_AUTH_KEY_PATH`/`APPLE_KEY_ID`/`APPLE_TEAM_ID`/`APPLE_CLIENT_ID` (placeholders ya en `application.yaml`, vacíos).
- Dar de alta la app de Facebook en Meta for Developers y cargar `FACEBOOK_APP_ID`/`FACEBOOK_APP_SECRET` (placeholders ya en `application.yaml`, vacíos).
- **Facebook, además, no arranca solo con las credenciales**: Meta exige revisión de app y verificación de negocio antes de aceptar llamadas de producción contra la Graph API — el código puede estar listo y compilando y el proveedor real igual rechazar las llamadas hasta que ese trámite esté aprobado. Conviene iniciarlo ya, no cuando haga falta.
- El caso de uso compositor + controller de §6.5 (verificar qué hizo el flujo de Google antes de construirlo, para no duplicar).
- Sin credenciales reales de ninguno de los dos proveedores, ningún test contra la API real fue posible — toda la cobertura es contra `MockRestServiceServer`/un `JwtDecoder` mockeado, ver §0.2 de CLAUDE.MD.

### 6.7 Google, `IdentidadExterna` y el caso de uso compositor — hecho 2026-08-26

Construido sobre lo que ya había dejado el trabajo en paralelo de §6.5 (`VerificadorIdentidadProveedor`, `CanjeCodigoCommand`, `IdentidadVerificada`, `ProveedorIdentidad`, `IdentidadProveedorInvalidaException` con su handler en `GlobalExceptionHandler` ya agregado) — se reutilizó tal cual, sin duplicar nada.

**`GoogleIdentidadAdapter`** (`users/infrastructure/adapter/out/oauth/`): mismo patrón que `AppleIdentidadAdapter`/`FacebookIdentidadAdapter`. Canjea `code` + `code_verifier` contra `https://oauth2.googleapis.com/token` con `RestClient`, decodifica el ID token con `NimbusJwtDecoder.withJwkSetUri` (bean separado en `GoogleJwtDecoderConfig`, mismo motivo que en Apple: poder inyectar un decoder falso en los tests), y verifica a mano `iss=https://accounts.google.com` y que `aud` contenga el `client_id` — Google es el más simple de los tres: `client_secret` fijo (no firmado por nosotros como Apple) y sí es OIDC (a diferencia de Facebook, que no tiene ID token). Config nueva en `application.yaml`: `renaser.auth.google.client-id`/`client-secret` (`GOOGLE_OAUTH_CLIENT_ID`/`GOOGLE_OAUTH_CLIENT_SECRET`, vacíos). El `redirect_uri` **no** es config del servidor: viaja en `CanjeCodigoCommand` (ya definido así por §6.5), lo manda el cliente en cada llamada porque tiene que coincidir con el que se usó al pedir el `code`.

**`IdentidadExterna`** (`users/domain/model/identidadexterna/`): agregado nuevo, `record` inmutable (mismo criterio que `Credencial`: sin comportamiento más allá de construirse). `LoadIdentidadExternaPort`/`SaveIdentidadExternaPort` + `IdentidadExternaPersistenceAdapter` contra `identidades_externas` (queries nativas con clave compuesta `(proveedor, sujeto_proveedor)`, mismo truco de `Repository<UserJpaEntity, UUID>` como marcador que ya usa `SpringDataCredencialRepository`). Probado con Testcontainers contra Postgres real: alta/lectura, que el mismo `sub` en proveedores distintos no colisiona, PK duplicada → `DataIntegrityViolationException` (409, ya mapeado), y FK contra un usuario inexistente → mismo 409.

**`IniciarSesionConProveedorUseCase`** + `AutenticacionSocialService` (`application/services`): resuelve el adaptador por proveedor vía `RegistroVerificadoresIdentidad` (mismo patrón que `RegistroPoliticasHabito` de `habits`, pero en `application/services` y no en `domain/` — referencia un puerto `out`, y `domain/` no puede conocer puertos). Si `(proveedor, sujeto)` ya está vinculado → `ResultadoLoginSocial.SesionIniciada`; si no, reusa **el mismo** `SubmitAccountRequestUseCase` que el autoregistro por formulario para abrir una `AccountRequest` (§6.4: nunca se crea el usuario en silencio) → `ResultadoLoginSocial.SolicitudCreada`. `ResultadoLoginSocial` es sellado, mismo criterio que `DecisionPolitica`/`AccessDecision`.

Controller: se agregó `POST /api/v1/auth/social` a `AutenticacionController` (ya existente, con los endpoints de password y de reset de §7.5) — no se creó un controller nuevo porque el endpoint compuesto (un solo `code` puede resolver a cualquiera de los tres proveedores) es, por diseño de §6.1, uno solo para los tres. Recibe `{proveedor, code, codeVerifier, redirectUri, phone?, city?}`; según la variante de `ResultadoLoginSocial`, establece sesión + 200 con el perfil, o 202 con el id de la `AccountRequest` (reusa `AccountRequestIdResponse`, ya existente).

**Tres decisiones de diseño que no estaban 100% especificadas, documentadas para revisión:**

1. **`email_verified=false` rechaza el login** (`IdentidadProveedorInvalidaException`), no pedido explícitamente pero consistente con la premisa de §6.4 ("el email llega pre-verificado, no se pide verificación aparte") — si el proveedor no lo confirma, esa premisa no se sostiene.
2. **Un email que ya tiene cuenta activa rechaza el login social con 409**, en vez de crear una segunda `AccountRequest` para el mismo email. Es la aplicación literal de §6.4 ("no vincula automáticamente, requiere confirmación autenticada"). ~~Esa confirmación todavía no existe como funcionalidad~~ — **existe desde el 2026-09-01** (§6.9): el 409 sigue siendo el camino correcto, pero ahora tiene salida (entrar con la contraseña y vincular desde el perfil), y el mensaje del 409 lo dice.
3. ~~**`SubmitAccountRequestCommand.phone` es `@NotBlank`**~~ — **corregido el 2026-09-01 (D-61): el teléfono dejó de ser obligatorio en el alta.** Este punto describía como "limitación de diseño" algo que en la práctica **dejaba muerto el registro por Google**: `phone`/`city` eran opcionales en `IniciarSesionConProveedorCommand`, pero si la identidad era nueva y faltaba el teléfono el caso de uso rechazaba con 400 — y Google/Apple/Facebook no devuelven teléfono, así que *ninguna cuenta nueva por login social podía registrarse*. Peor: como el `code` de OAuth es de un solo uso, ese intento fallido lo consumía igual y reintentar exigía reiniciar el flujo del navegador. La decisión del dueño del proyecto fue mover el teléfono a la **Ficha Inicial del onboarding** (donde se piden los datos completos) y dejar el alta con lo mínimo — correo, nombre y contraseña. Se bajó el `@NotBlank` en `SubmitAccountRequestRequest` y en `SubmitAccountRequestCommand`, se quitó el `requireNotBlank` del agregado `AccountRequest` (un teléfono en blanco se normaliza a `NULL`), se eliminó `AutenticacionSocialService.requirePhoneParaAlta` y la migración **V14** hizo `solicitudes_cuenta.telefono` nullable. **El teléfono se sigue guardando si viene**: solo dejó de ser obligatorio.

**Lo que quedó pendiente de esta tanda — resuelto después (A-7 cerrado, A-8 sigue abierto):**

- ~~**`SaveIdentidadExternaPort.guardar` nunca se invoca todavía.**~~ **Cerrado el 2026-08-31 (A-7).** El hueco era real y no cosmético: la FK de `identidades_externas.usuario_id` exige que la fila de `usuarios` exista, así que el vínculo solo puede escribirse al aprobar — y en el medio, entre el alta y la aprobación, el `sub` del proveedor no tenía dónde sobrevivir y se perdía. Quien se registraba por Google no podía volver a entrar nunca: su segundo intento no encontraba `(proveedor, sujeto)`, intentaba abrir otra `AccountRequest`, chocaba con el `User` ya existente y recibía "iniciá sesión con tu método actual" — un método que no tenía, porque el alta social deja `usuarios.hash_contrasena` en NULL a propósito. Ver §6.8 para cómo se cerró.
- ~~El puerto y el adaptador de `IdentidadExterna` esperan a que se resuelva el punto anterior.~~ Ya están enchufados: `AccountRequestService.approve()` llama a `guardar()` en la misma transacción que activa al usuario.
- ~~**A-8 sigue abierto**~~ — **cerrado el 2026-09-01 (D-61)**: la respuesta a "cuándo se le pide el teléfono a quien se da de alta por login social" es **en la Ficha Inicial del onboarding**, no en el alta. El backend ya no lo exige en ningún punto del flujo social. Ver §10.

### 6.8 Cierre de A-7: el `(proveedor, sujeto)` sobrevive hasta la aprobación — hecho 2026-08-31

Tres piezas, ninguna opcional:

1. **Migración `V12`** — `solicitudes_cuenta` gana `proveedor` y `sujeto_proveedor` (nullable: el alta por formulario no tiene origen social, y eso es un estado válido). Con `CHECK` de que los dos viajan juntos o ninguno, y un índice `UNIQUE` parcial sobre `(proveedor, sujeto_proveedor)` — la misma frontera de seguridad que la PK de `identidades_externas`: dos solicitudes no pueden reclamar la misma identidad social. La solicitud es el lugar natural para ese dato porque es el único registro que existe durante la espera entre el alta y la aprobación.
2. **`AccountRequestService.approve()`** escribe la `IdentidadExterna` (`vincularIdentidadSocialSiCorresponde`) en la **misma transacción** que activa al usuario. Si el vínculo falla, la aprobación entera se deshace: nunca queda un usuario aprobado que no pueda volver a entrar por donde entró.
3. **`ResultadoLoginSocial` pasó de dos variantes a cuatro** — `SesionIniciada`, `SolicitudCreada`, `SolicitudEnRevision`, `CuentaExistenteSinVinculo`. Las tres que no son sesión son **estados normales del flujo, no errores**, y por eso son valores de retorno y no excepciones: antes las tres colapsaban en el mismo 409 genérico, así que "tu solicitud sigue en revisión" y "ya existe una cuenta con ese correo" le llegaban a la app indistinguibles.

El `(proveedor, sujeto)` viaja **dentro de `SubmitAccountRequestCommand`, servidor a servidor** — nunca por HTTP. Los dos campos no existen en `SubmitAccountRequestRequest`, el DTO del alta pública, y no pueden existir: si el cliente pudiera mandar un `sujetoProveedor`, cualquiera reclamaría la identidad social de otro con solo conocer su `sub`. Mismo blindaje por compilador que el `role` ausente de CLAUDE.MD §5.3.3.

**Prueba de que el defecto murió:** `LoginSocialCicloCompletoIntegrationTest`, de integración contra Postgres real (Testcontainers) porque las tres piezas viven en la base: alta social → segundo toque mientras está pendiente devuelve `SolicitudEnRevision` → aprobación por un ADMIN → **el segundo toque del mismo proveedor con el mismo `sub` devuelve `SesionIniciada`**. La prueba unitaria que ya existía (`identidadYaVinculadaDevuelveSesionIniciadaConElUsuarioCorrespondiente`) no cubre esto: parte de un vínculo ya existente en un mock, o sea da por cierto exactamente lo que el defecto impedía que ocurriera. Lo único simulado es el verificador del proveedor — canjear el `code` contra Google exige red y un OAuth client (A-9).

### 6.9 Vincular una identidad social a una cuenta que ya existe — hecho 2026-09-01

**El hueco que cierra.** §6.4 prohíbe vincular por coincidencia de correo, y §6.8 convirtió ese caso en la variante `ResultadoLoginSocial.CuentaExistenteSinVinculo` → **409**. El mensaje decía "iniciá sesión con tu contraseña"… y ahí terminaba todo: **no existía ninguna forma de conectar Google después**. Quien ya tenía cuenta quedaba condenado a la contraseña para siempre, y quien había entrado por Google antes de tener contraseña ni siquiera tenía esa salida.

**La decisión (dueño del proyecto, 2026-09-01): vínculo explícito, no auto-vínculo por correo verificado.** La alternativa considerada era vincular automáticamente cuando el correo del proveedor viene verificado y coincide con el de una cuenta existente. Se descartó: el costo del camino explícito es **una pantalla**, y el riesgo que cubre es **apropiación de cuenta**. Es lo que recomiendan Auth0 y Clerk — autenticar **las dos** identidades antes de unirlas: la nuestra con la sesión, la del proveedor con el `code`.

#### El contrato

```
POST /api/v1/auth/social/link
{ "proveedor": "GOOGLE", "code": "...", "codeVerifier": "...", "redirectUri": "..." }
```

Mismos campos que `POST /auth/social` **menos** `phone`/`city` — acá la cuenta ya existe, no hay alta que completar. Y ningún campo que identifique al usuario: **quién vincula sale de la sesión, nunca del cuerpo**, mismo blindaje por compilador que el `role` ausente del alta pública (CLAUDE.MD §5.3.3).

| Código | Cuándo |
|---|---|
| **204** | Vinculada. También si ya estaba vinculada a **esta misma** cuenta — el caso de uso es idempotente, el doble tap del cliente móvil no es un error y no reescribe la fila |
| **409** | Esa identidad `(proveedor, sujeto)` ya pertenece a **otro** usuario (`IdentidadYaVinculadaException`) |
| **401** | Sin sesión (`SesionNoIniciadaException`), o el proveedor rechazó el `code` (`IdentidadProveedorInvalidaException`) |
| **403** | La cuenta está suspendida |
| **400** | Falta `code`/`codeVerifier`/`redirectUri`/`proveedor` |

#### Las cuatro reglas de seguridad, y por qué cada una

1. **Sesión real obligatoria.** `@RequiresPermission(USE_APP)` + `sesionWeb.actorActual()`, igual que `GET /auth/me` — y es el **segundo** endpoint del sistema que **no acepta el respaldo de `X-Actor-Id`** de la fase 4 (§8). Con un header que cualquiera escribe, cualquiera colgaría su cuenta de Google del usuario de otro: sería exactamente el agujero que este endpoint viene a cerrar. Es la prueba más importante del cambio (`headerXActorIdSoloNoAlcanzaParaVincular`).
2. **Si `(proveedor, sujeto)` ya pertenece a otro usuario → 409.** Es el vector de apropiación **inverso** al de §6.4: quien consiga un `code` de la cuenta social ajena podría colgarla de su propio usuario y entrar como esa persona. El mensaje del 409 es genérico a propósito — nunca dice de quién es la identidad, o el endpoint sería un oráculo.
3. **`email_verified` obligatorio**, igual que el login social. La lógica **se reusó, no se duplicó**: pasó de un `private static` de `AutenticacionSocialService` a `IdentidadVerificada.exigirEmailVerificado(proveedor)`, en el propio puerto — así un flujo social nuevo no puede olvidarse de hacerlo.
4. **NO se exige que el correo del proveedor coincida** con el de la cuenta. Vincular un Google personal a una cuenta con correo de trabajo es legítimo y Auth0/Clerk lo permiten. Lo que impide que una misma cuenta del proveedor sirva a dos usuarios no es una comparación de correos: es la `UNIQUE (proveedor, sujeto_proveedor)` de `identidades_externas`, con el chequeo del punto 2 anticipándola con un 409 entendible.

**El orden de los pasos del caso de uso tampoco es casual:** el actor se carga y se rechaza si está suspendido **antes** de tocar al proveedor. El `code` de OAuth es de un solo uso — quemarlo en una petición que igual iba a terminar en 403 obligaría a la persona a reiniciar el flujo del navegador para nada.

#### Dónde vive

| Pieza | Archivo |
|---|---|
| Caso de uso (puerto `in`) | `users/application/ports/in/autenticacion/VincularIdentidadSocialUseCase.java` |
| Servicio | `users/application/services/VinculacionIdentidadSocialService.java` |
| DTO web | `users/infrastructure/adapter/in/rest/autenticacion/VincularIdentidadSocialRequest.java` |
| Endpoint | `AutenticacionController#vincularIdentidadSocial` |
| Excepción + handler | `shared/domain/IdentidadYaVinculadaException.java` → 409 en `GlobalExceptionHandler` |

**Clase propia y no un método más de `AutenticacionSocialService`** (CLAUDE.MD §5.4.8, una clase por caso de uso): aquel **establece** identidad para quien todavía no la tiene; éste **agrega una forma de entrar** a quien ya probó quién es. Lo que sí comparten —resolver el verificador (`RegistroVerificadoresIdentidad`) y exigir el correo verificado— se reusa, no se copia. No hizo falta migración: la tabla `identidades_externas` (V3) ya soporta 1:N y su PK ya es la frontera correcta.

#### Lo que quedó sin cubrir, dicho explícitamente

- **Desvincular** (`DELETE /auth/social/link`) no existe. Hace falta antes de exponer esto en una pantalla de "cuentas conectadas", y tiene una regla de negocio propia que **no está confirmada**: si el usuario entró por Google y no tiene `hash_contrasena`, desvincular lo dejaría sin ninguna forma de entrar. Eso es una decisión de producto, no se inventó acá.
- **Sigue sin probarse contra un proveedor real** (A-9): el verificador es lo único simulado en las pruebas, canjear el `code` contra Google exige red y un OAuth client.

### 6.10 Registro social en dos pasos: identidad pendiente antes de crear la solicitud — hecho 2026-09-01

**El hueco que cierra (D-65).** Hasta ahora, cuando `POST /api/v1/auth/social` encontraba una identidad nueva, abría la `AccountRequest` **en la misma llamada** que verificaba esa identidad contra el proveedor (§6.7). La app nunca llegaba a mostrarle a la persona un formulario de confirmación con su correo y su nombre ya prellenados — como manejan las redes sociales — porque para cuando el backend conocía esos datos, ya había decidido qué hacer con ellos.

**El obstáculo técnico que obliga al diseño en dos pasos:** el `code` de OAuth es **de un solo uso**. La app no conoce el correo ni el nombre hasta que el backend canjea ese `code` contra el proveedor, así que no hay forma de prellenar un formulario sin retener la identidad ya verificada en algún lado **antes** de gastar el `code` en crear la solicitud. La única salida correcta es que el backend retenga la identidad ya verificada y le devuelva a la app un token de continuación.

#### El flujo nuevo

```
POST /api/v1/auth/social  (identidad nueva)
  → verifica contra el proveedor (§6.1)
  → guarda la identidad YA VERIFICADA en Redis, TTL 10 min (igual al OTP de alta)
  → 202 { registroPendienteToken, email, fullName }   ← la app prellena el formulario con esto

POST /api/v1/auth/social/complete
  { registroPendienteToken, fullName, phone?, city? }
  → consume el token (GETDEL: un solo uso)
  → arma la AccountRequest con el email/proveedor/sujeto DEL REGISTRO, nunca del body
  → 202 { accountRequestId }   ← igual que el alta por formulario
```

Las otras tres salidas de `POST /auth/social` (200 sesión, 202 `EN_REVISION`, 409 correo ya registrado) **no cambian** — sólo cambió el camino de identidad nueva.

#### Dónde vive la identidad retenida

Mismo patrón que `TokenResetContrasenaPort`/`CodigoVerificacionEmailPort` (§2.2): token opaco de 256 bits, TTL nativo de Redis (sin cron de purga), `GETDEL` atómico para que "un solo uso" sea real y no un `GET` seguido de un `DEL` con ventana de carrera.

- **Puerto:** `TokenRegistroPendienteSocialPort` (`users/application/ports/out/autenticacion/`) — `generar(RegistroPendienteSocial, Duration)` / `consumir(String) → Optional<RegistroPendienteSocial>`.
- **Valor retenido:** `RegistroPendienteSocial(proveedor, sujetoProveedor, email, fullName)` — un `record` inmutable, sin comportamiento.
- **Adaptador:** `TokenRegistroPendienteSocialRedisAdapter`, clave `registro-pendiente-social:{token}`. Los 4 campos se serializan en un único string con un separador de control (`U+0001`) en vez de sumar Jackson: ningún otro adaptador de este paquete serializa un objeto compuesto todavía, y el separador no puede aparecer por accidente en un enum de proveedor, en un `sub` opaco de OAuth, ni en texto que una persona tipea en un formulario (verificado con nombres con tildes y apóstrofes en el test de integración).
- **TTL: 10 minutos**, igual al `VIGENCIA_CODIGO` del OTP de alta (`VerificacionEmailService`) — el mismo orden de magnitud que le toma a una persona mirar un formulario ya prellenado y confirmar.

#### La regla de seguridad innegociable

**El correo y el `sujeto` del proveedor se leen SIEMPRE del registro que devuelve `consumir()`, NUNCA del cuerpo de `POST /auth/social/complete`.** `CompletarRegistroSocialRequest`/`CompletarRegistroSocialCommand` ni siquiera tienen un campo para mandarlos — no es un `if` que los ignore, es que el compilador no deja construir el comando con ellos. Si el correo viajara por HTTP en este paso, cualquiera podría completar un registro con el correo de otra persona: es exactamente el agujero de apropiación de cuenta que D-60 y §6.4 vienen evitando, aplicado ahora al paso de confirmación en vez de al de vinculación.

`fullName` **sí** viaja del cliente (la persona puede corregir cómo se escribe su nombre respecto de lo que devolvió el proveedor). `phone`/`city` siguen opcionales (D-61) — si no vienen, la solicitud queda con esos campos `NULL` y se piden después, en la Ficha Inicial del onboarding.

#### Qué cambió en el código existente

- `IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand` perdió `phone`/`city`: ese paso ya no arma ninguna solicitud, esos datos ahora sólo tienen sentido en el comando del paso 2.
- `ResultadoLoginSocial` sigue teniendo cuatro variantes, pero la de identidad nueva cambió de forma: `SolicitudCreada(AccountRequestId)` → `RegistroPendiente(String token, String email, String fullName)`. El sellado (`sealed interface`) obliga a que el compilador marque cada lugar que todavía manejaba la variante vieja.
- `AutenticacionSocialService` ya NO depende de `SubmitAccountRequestUseCase` ni de `TokenVerificacionEmailPort` — esas dos dependencias se mudaron a la clase nueva, `CompletarRegistroSocialService`, que es quien ahora arma el `SubmitAccountRequestCommand.porProveedorSocial(...)` con el mismo camino que ya usaba el paso único anterior (genera el `verificationToken` directo, sin pedirle a la persona el código de 6 dígitos, porque el proveedor ya confirmó el correo — la misma razón que documentaba §6.7 antes de este cambio).
- `SolicitudSocialResponse.EstadoSolicitudSocial` perdió la variante `CREADA` — ya no se produce desde `/auth/social`. Sólo queda `EN_REVISION`. La identidad nueva ahora responde con un cuerpo distinto, `RegistroPendienteSocialResponse{registroPendienteToken, email, fullName}`.
- Clase nueva por caso de uso (CLAUDE.MD §5.4.8): `CompletarRegistroSocialUseCase`/`CompletarRegistroSocialService` no es un método más de `AutenticacionSocialService` — aquel verifica una identidad y decide qué camino sigue; éste sólo sabe convertir un registro pendiente ya verificado en una `AccountRequest`.

#### Pruebas

`LoginSocialCicloCompletoIntegrationTest` se reescribió al ciclo de tres toques (primer toque → `RegistroPendiente` → `completar()` → `AccountRequest` creada → segundo toque mientras pendiente → `SolicitudEnRevision` → aprobación de un ADMIN → tercer toque → sesión), contra Postgres y Redis reales (Testcontainers), incluida la variante sin teléfono de D-61. `AutenticacionSocialServiceTest` se reescribió (ya no mockea `SubmitAccountRequestUseCase`/`TokenVerificacionEmailPort`, ahora `TokenRegistroPendienteSocialPort`). Se agregó `CompletarRegistroSocialServiceTest` — la prueba central de seguridad es que mandar un correo distinto en el comando no tiene ningún efecto observable, porque no existe un parámetro por donde mandarlo — y `TokenRegistroPendienteSocialRedisAdapterTest` (Testcontainers: `GETDEL` atómico, TTL real, ida y vuelta con nombres con tildes/apóstrofes). Los controllers tienen su propia cobertura en `CompletarRegistroSocialControllerTest` (nuevo) y `LoginSocialControllerTest` (actualizado).

**Lo que queda sin cubrir, dicho explícitamente:** el token de continuación no tiene límite de reintentos ni rate limiting propio más allá del que ya aplica `AccountRequestService.submit` (60/hora por IP, se ejecuta igual porque `/complete` termina llamando al mismo caso de uso). Tampoco se agregó protección contra que alguien complete el mismo registro pendiente dos veces en paralelo antes de que el primero termine de escribir en Postgres — el `GETDEL` de Redis evita que el TOKEN se use dos veces, pero no hay una segunda capa contra una carrera a nivel de la `AccountRequest` en sí; si aparece, la cubre la misma `UNIQUE (proveedor, sujeto_proveedor)` parcial de la migración V12 que ya protege el camino anterior, devolviendo 409 vía `DataIntegrityViolationException`.

---

## 7. Spring Security

### 7.1 La cadena

```java
http.securityMatcher("/api/v1/**")
    .cors(Customizer.withDefaults())
    .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/**", "/api/v1/account-requests").permitAll()
        .anyRequest().authenticated());
```

Eso es todo: **sin `sessionCreationPolicy`, sin filtro propio.** La sesión la administra Spring Session sobre Redis (§4) y Spring Security la lee sola. Poner `STATELESS` acá sería justamente apagar lo que queremos.

### 7.2 Contraseñas

Un solo bean, declarado una vez en `SecurityConfig` y ya construido:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

BCrypt por defecto, con el prefijo del algoritmo en el hash (`{bcrypt}$2a$...`). **No se escribe lógica de hasheo ni de comparación en ningún servicio**: se inyecta el bean y se usan `encode` y `matches`.

El *delegating* y no BCrypt pelado por una razón concreta: permite **convivir con hashes de otro origen y recodificarlos en el siguiente login**, sin pedirle a nadie que cambie su contraseña. Si algún día hay que importar los usuarios de Supabase, ese es el camino.

> **Pendiente de verificar antes de confiar en esto:** con qué algoritmo guarda Supabase (GoTrue) los hashes. Si no es BCrypt, la importación necesita otro plan. **No dar por sentado.**

Largo mínimo (**≥ 12**, de la checklist de `MODULO_USERS.md` §5.8): va como `@Size(min = 12, max = 200)` en el **comando self-validating** del caso de uso, no en una clase de dominio propia. El comando valida en su constructor (§5.4.3, nivel 2), así que la regla se aplica venga de HTTP, de un scheduler o de un listener — que era el único motivo para no dejarla solo en el DTO.

El tope de 200 no es de seguridad: BCrypt trunca en 72 bytes, así que aceptar entradas enormes no agrega fuerza y sí abre un vector de denegación de servicio.

### 7.3 Rate limiting

`POST /api/v1/auth/login` necesita límite **por IP y por cuenta** (la segunda dimensión es la que frena el *password spraying*, que rota IPs). Bucket4j sobre Redis, para que el límite sea real entre instancias.

### 7.4 Defensa en profundidad: cómo queda D-11

| # | Capa | Antes (D-11) | Ahora |
|---|---|---|---|
| 1 | Identidad real | Supabase Auth, JWT RS256 | **Nuestra sesión.** El token existe en Redis o no existe |
| 2 | Token válido | Firma + expiración | **Existe en Redis** (y no pasó el límite absoluto) |
| 3 | `estado != SUSPENDIDO` | Tabla + Caffeine con TTL 30 s | Tabla + Caffeine, **y suspender revoca las sesiones en el acto** vía `FindByIndexNameSessionRepository` (§4.1) |
| 4 | `AccessGuard` | Rol / self / alcance de mentor | Sin cambios |
| 5 | Dominio | Invariantes | Sin cambios |
| 6 | RLS en Postgres | Última línea | Sin cambios |

La capa 3 mejora de verdad: antes un suspendido podía seguir operando hasta 30 s; ahora su sesión desaparece en la misma transacción que lo suspende.

### 7.5 Reset de contraseña — hecho 2026-08-26

Construido en paralelo con el trabajo de Google/Apple/Facebook de §6.5 sobre el mismo checkout — sin conflicto: los únicos archivos compartidos (`GlobalExceptionHandler.java`, `AutenticacionController.java`, `application.yaml`) se tocaron en puntos distintos cada vez.

**Lo que se construyó**, todo dentro de `users` (§3), sin tabla nueva (§2.2 ya preveía esto):

- `SolicitarResetContrasenaUseCase` / `ConfirmarResetContrasenaUseCase` (`application/ports/in/autenticacion`) + `ResetContrasenaService` (`application/services`). `solicitar` responde igual exista o no la cuenta (misma no-enumeración que el login, §5.3.3 de CLAUDE.MD); `confirmar` valida el token, fija la contraseña nueva (mismo `PasswordEncoder` de §7.2) y cierra todas las sesiones.
- `TokenResetContrasenaPort` + `TokenResetContrasenaRedisAdapter` (`infrastructure/adapter/out/redis`): clave `reset-password:{token}` → `UserId`, TTL de 30 min (§2.2). El token es 256 bits de `SecureRandom` codificados en Base64 URL-safe. El "un solo uso" es atómico de verdad: `consumir` usa `ValueOperations#getAndDelete` (equivalente a `GETDEL`), no un GET seguido de un DEL — así dos requests casi simultáneas con el mismo token nunca pueden las dos tener éxito. Probado contra Redis real (Testcontainers): ronda completa, doble consumo, token nunca emitido, vencimiento por TTL, y dos tokens del mismo usuario son independientes.
- `LimitarSolicitudesResetPort` + `LimitarSolicitudesResetRedisAdapter`: mismo patrón que `ControlCuotaRenasiaPort` (rag, D-48) — `INCR` atómico con TTL fijado solo la primera vez. Se registra un intento por email y, si hay IP, otro por IP; cualquiera de los dos que se agote corta con `RateLimitExceededException` (429), antes incluso de mirar si la cuenta existe.
- **`CerrarTodasLasSesionesUseCase`** (el que `§3` dejaba pendiente de construir) + `GestionSesionesService`: inyecta `FindByIndexNameSessionRepository<? extends Session>` directo, sin puerto de indirección — mismo criterio que `PasswordEncoder` en `AutenticacionService` (§3): no es HTTP ni JPA, y Spring Session ya es la única implementación de "sesión" posible acá.
- `EnviarEmailPort` (`application/ports/out/autenticacion`) + `NoOpEnviarEmailAdapter`: **no existía ningún puerto de email en el proyecto** (se buscó en `notifications`, que solo tiene `PushPort` para push — nada de mail). Se creó nuevo, con el mismo patrón que `NoOpPushAdapter`/`NoOpSupabaseAdminAuthAdapter`: solo loguea (sin el token ni el email, nunca), listo para un adaptador real (Resend/SES) el día que haya credenciales. **Decisión a confirmar:** si mañana otro módulo necesita mandar mail, ¿se generaliza este puerto o se mueve a `shared`? Por ahora vive en `users` porque es el único consumidor.
- Dos endpoints en `AutenticacionController`: `POST /api/v1/auth/password/reset-request` (siempre 202) y `POST /api/v1/auth/password/reset-confirm` (204 si el token es válido, 400 vía `TokenResetInvalidoException` si no).

**Decisiones tomadas sin especificación explícita, documentadas para revisión:**

- **Umbrales del rate limit**: CLAUDE.MD solo pedía "por email y por IP", sin números. Se asumió 5/hora por email y 20/hora por IP, por analogía con el único precedente del repo (60/hora por IP en `AccountRequestService` para altas). **No confirmado con el dueño del producto.**
- **URL del link de reset**: `renaser.web.reset-password-url` en `application.yaml`, con placeholder `https://TODO-frontend-no-definido.renaser.dev/reset-password` — el frontend todavía no tiene definida esa pantalla. Sobreescribir con `RESET_PASSWORD_URL` antes de producción.
- `confirmar` no vuelve a chequear `cuentaHabilitada()`: si el token es válido, se fija la contraseña igual aunque la cuenta esté suspendida (no puede loguear igual, el guard de estado sigue en `AutenticacionService`) — se siguió literal la instrucción de la tarea, que solo pedía chequear `permiteLoginPorContrasena()` al solicitar.

**Lo que NO se construyó, a propósito:** el envío real de correo (Resend/SES) — sin credenciales, prohibido explícitamente integrar un proveedor real en esta fase. `NoOpEnviarEmailAdapter` deja el flujo completo y probado detrás del puerto.

---

## 8. La migración de `X-Actor-Id` — el trabajo real

**162 usos en 54 controllers, más 19 en tests.** Es la parte más grande de este trabajo, y no se hace de un saque.

El puente es un `HandlerMethodArgumentResolver`:

```java
public UserId actor(@ActorAutenticado UserId actorId) { ... }
```

El resolver lo saca del `SecurityContext`. Durante la transición, **y solo bajo el perfil `local`**, cae al header `X-Actor-Id` si no hay sesión — así los 14 módulos se migran de a uno con sus tests, sin romper todo el mismo día. Ese fallback **no existe en `prod`**: se compila, pero la condición que lo habilita nunca se cumple fuera de `local`.

Orden: `users` primero (es donde vive el login), después el resto por tamaño ascendente.

---

## 8.1 Inventario de autorización por endpoint — hecho 2026-08-31

**Antes de migrar `X-Actor-Id` hacía falta saber qué exige cada endpoint, y no estaba escrito en ningún lado.** La autorización real vivía (y sigue viviendo) dentro de los servicios, en guards como `requireAdminActivo`, `requireActorPuedePublicar`, `HabitoAdminGuard` o `RequireActiveUserGuard`. Desde el controller no se veía nada: los 218 handlers de entonces eran indistinguibles entre sí.

Ahora **cada endpoint declara qué permiso va a exigir**, y el build lo verifica.

### Qué se agregó

| Pieza | Dónde | Qué hace |
|---|---|---|
| `Permission` | `shared/domain/` | 30 permisos, cada uno derivado de un guard que ya existía. El javadoc de cada valor cita el mensaje literal del `NotAuthorizedException` que hoy lo hace cumplir y los roles que hoy lo satisfacen |
| `@RequiresPermission(value, scope)` | `shared/web/security/` | Declara el permiso. `scope` documenta la restricción de **relación** que el permiso no puede expresar (dueño del recurso, mentor asignado, líder de célula, participante de la conversación) |
| `@PublicEndpoint(razón)` | `shared/web/security/` | Declara que el endpoint se sirve sin cuenta, **con la justificación obligatoria** |
| `EndpointAuthorizationDeclarationTest` | `src/test/java/com/renaser/os/` | El test de reflexión que exige CLAUDE.MD §0.3. Rompe el build si un handler no declara nada, si declara las dos anotaciones a la vez, o si un `@PublicEndpoint` no explica por qué |

**Declaran, no ejecutan — actualizado 2026-09-01 (D-64, cierra A-1 parcialmente).** Esto describe el estado hasta el 2026-08-31. Desde D-64, `@RequiresPermission` **sí se ejecuta**, pero solo para el rol TRAINEE: `PermissionEnforcementInterceptor` (`users/infrastructure/adapter/in/web/security`) corta con 403 antes del controller si un TRAINEE no tiene el permiso o si la cuenta está `SUSPENDED`. Para MENTOR, MENTOR_LEAD, ADMIN y ALCHEMIST el interceptor sigue sin hacer nada — la matriz de esos 4 roles no está definida (regla de negocio pendiente del dueño del proyecto) y `SecurityConfig` sigue en `permitAll()`: nada de esto depende de la sesión todavía, sigue funcionando sobre el respaldo `X-Actor-Id`. Detalle completo en `docs/MODULOS_A_AVANZAR.md` D-64 y `docs/ENDPOINTS_FALTANTES.md` A-1. Lo que sigue de esta sección (§8.1) describe el **inventario** tal como se armó el 2026-08-31; sigue siendo el insumo correcto, la matriz real de TRAINEE ya lo usó tal cual.

### El inventario: 219 endpoints

> **Actualizado 2026-09-01.** Eran 218; entró `POST /api/v1/auth/social/link` (§6.9), que declara `USE_APP`.

| | Cantidad |
|---|---|
| Con permiso declarado | **202** |
| Públicos (`@PublicEndpoint`) | **12** |
| Sin clasificar (TODO explícito) | **5** |

**Los 12 públicos están todos en `users`**, y todos por la misma razón: ocurren *antes* de que exista la cuenta o la sesión.

- `POST /api/v1/account-requests` (el alta), `.../check-email`, `.../exists`, `.../verify-email`, `GET /api/v1/account-requests/{id}/status`
- `POST /api/v1/auth/login`, `/logout`, `/social`, `/password/reset-request`, `/password/reset-confirm`
- `POST /api/v1/auth/email-verification/send`, `/confirm`

`AccountRequestController` es **mixto** y ahora lo dice endpoint por endpoint: el alta y sus pasos previos son públicos, la bandeja (`listar`, `approve`, `reject`, `eliminar`) exige `APPROVE_ACCOUNT_REQUEST`.

Reparto por permiso, de mayor a menor:

| Permiso | Endpoints | Quién lo satisface hoy |
|---|---|---|
| `USE_APP` | 88 | Cualquier rol con cuenta activa |
| `FOLLOW_OWN_PROGRAM` | 22 | TRAINEE |
| `MANAGE_HABIT_CATALOG` | 17 | ADMIN, ALCHEMIST |
| `MANAGE_CELLS` | 14 | ADMIN, ALCHEMIST (un MENTOR pasa en 2 de ellos, acotado a su célula) |
| `MANAGE_CALENDAR` | 6 | ADMIN, ALCHEMIST, MENTOR — **no MENTOR_LEAD** |
| `MANAGE_COHORTS` | 6 | ADMIN, ALCHEMIST (ídem MENTOR en 2) |
| `MANAGE_WALL_CATEGORIES` | 5 | ADMIN, ALCHEMIST |
| `APPROVE_ACCOUNT_REQUEST` | 4 | ADMIN, ALCHEMIST |
| `MANAGE_EVIDENCE`, `MANAGE_ROLES`, `MANAGE_STAFF`, `MANAGE_TRAINEES`, `MODERATE_WALL`, `OPEN_SUPPORT_TICKET` | 3 c/u | Ver el javadoc de cada valor |
| `ANSWER_MENTOR_TICKET`, `MANAGE_SUPPORT_TICKETS`, `PUBLISH_ON_WALL`, `SIGN_PHASE_CONTRACT`, `TRACK_PROGRAM_AS_STAFF`, `USE_MENTOR_TICKETS`, `VIEW_OWN_PHASE_CONTRACTS` | 2 c/u | Ídem |
| `ADJUST_POINTS`, `ASSIGN_MENTOR`, `MANAGE_KNOWLEDGE_BASE`, `MANAGE_MENTOR_PROFILE`, `OPEN_MENTOR_TICKET`, `RENAME_GLOBAL_CHAT`, `VIEW_ALL_MENTOR_TICKETS`, `VIEW_ONBOARDING_DASHBOARD` | 1 c/u | Ídem |

Por módulo: `community` 45, `habits` 40, `users` 40, `onboarding` 15, `rocks` 13, `academy` 12, `support` 11, `calendar` 9, `chat` 8, `notifications` 6, `evidence` 5, `points` 5, `rag` 5, `phasecontracts` 4.

### Los 5 que NO se pudieron clasificar

**Ninguno se marcó público.** Un `@PublicEndpoint` puesto por comodidad es un agujero permanente: cuando `SecurityConfig` pase a `authenticated()`, esa anotación *es* la lista de excepciones. Los 5 llevan un TODO en su controller y entran en `HANDLERS_SIN_CLASIFICAR` del test, que los aísla sin dejarlos pasar por default.

| Endpoint | Por qué no se puede decidir desde el código |
|---|---|
| `GET /api/v1/wall/{postId}/comments` (`WallCommentController#listar`) | No recibe actor ni ejecuta guard, mientras el feed del Muro sí exige cuenta activa. No se sabe si es deliberado o una omisión |
| `GET /api/v1/wall/mine` (`WallController#mine`) | Recibe actor y `contarMisPublicaciones` no lo valida. Con el respaldo de `X-Actor-Id`, devuelve el conteo de cualquier `userId` que el cliente declare |
| `GET /api/v1/wall/latest-author` (`WallController#latestAuthor`) | Declara actor y `ultimoAutor()` ni lo recibe: el parámetro se ignora. Hoy es público de hecho, sin que nadie lo haya decidido |
| `GET /api/v1/testimonios` (`TestimonioController#listar`) | Sin actor y sin guard. Podría ser contenido público de marketing, pero el código no lo dice en ningún lado |
| `POST /api/v1/testimonios` (`TestimonioController#crear`) | Un solo handler con **dos** autorizaciones según el body: sin `wallPostId` acepta actor `null` y no valida nada; con `wallPostId` exige `PROMOTE_TESTIMONIAL`. No es declarable hasta partirlo en dos endpoints |

### Hallazgos del recorrido, para la fase 4

Ninguno se corrigió acá — cambiarlos altera comportamiento y son decisiones de producto, no de refactor:

- **`MANAGE_CALENDAR` no incluye `MENTOR_LEAD`.** `EventoService.requireRolCreador` acepta ADMIN, ALCHEMIST y MENTOR. Es exactamente la omisión que el vocabulario de permisos existe para no repetir.
- **`PUBLISH_ON_WALL` tiene un guard que hoy no puede fallar.** `requireActorPuedePublicar` enumera *en negativo* los 5 roles, así que cualquier cuenta activa pasa. Recién morderá cuando exista un rol nuevo, y lo hará en silencio: nadie habrá decidido que ese rol no publica. Se le dio un permiso propio en vez de colapsarlo en `USE_APP` justamente para que la decisión sea explícita en la matriz.
- **`GET /api/v1/evidence/{id}` deja leer a una cuenta suspendida** si es la suya: `requireDuenoOAdmin` retorna sin chequear estado cuando el actor es el dueño. Es el único endpoint del sistema con esa forma.
- **`GET /api/v1/chat/members` no exige ser participante del grupo GLOBAL**, mientras `/chat/conversations/global/members` sí — y leen el mismo roster.
- **`OPEN_SUPPORT_TICKET` no exige cuenta activa, a propósito.** `TicketSoporteService.requireActorExiste` solo verifica que el actor exista. Tiene sentido (alguien suspendido tiene que poder reclamar su suspensión) y por eso es un permiso aparte: colapsarlo en `USE_APP` le cerraría la puerta sin que nadie lo note.
- **`MiCelulaController` dice TRAINEE en el javadoc pero no tiene guard de rol.** El efecto "solo aprendiz" es incidental: quien no es participante recibe lista vacía, no 403.
- **`requireSelf` inerte en `RadarController` y `HabitTrackController#hoy`**: el controller pasa el mismo actor como actor y como participante, así que la comparación nunca puede fallar. La restricción real la aporta el otro guard de la cadena.

### Qué falta para pasar de `permitAll()` a `authenticated()`

1. ~~**Definir la matriz rol → permiso**~~ — **hecho parcialmente 2026-09-01 (D-64).** `UserRole.can(Permission)` existe, en un solo archivo (CLAUDE.MD §5.3.2), pero **solo para TRAINEE** (8 permisos, ver D-64). MENTOR, MENTOR_LEAD, ADMIN y ALCHEMIST siguen sin matriz (`can()` devuelve `true` para cualquier permiso) — sigue abierto qué permisos tiene cada uno, `MENTOR_LEAD` incluido (R-2), y es una decisión del dueño del proyecto, no algo que se infiera del código.
2. **Resolver los 5 sin clasificar**, y partir `POST /api/v1/testimonios` en dos endpoints. Sin cambios: `PermissionEnforcementInterceptor` tampoco decide por ellos (no tienen `@RequiresPermission` ni `@PublicEndpoint`, así que el interceptor los deja pasar tal cual pasaban antes).
3. **Terminar la migración de `X-Actor-Id`** (§8): sigue abierto. El interceptor de D-64 verifica el permiso de quien el header/la sesión dice ser, pero mientras el respaldo por header exista, esa identidad sigue siendo auto-declarable — es la misma limitación que tenía la app antes de D-64, no algo nuevo que D-64 introduzca ni resuelva.
4. ~~**Conectar la anotación**~~ — **hecho parcialmente 2026-09-01 (D-64).** `PermissionEnforcementInterceptor` (`HandlerInterceptor`, registrado via `WebMvcConfigurer` en `users`) lee `@RequiresPermission`/`@PublicEndpoint` y consulta `UserRole.can(Permission)`, pero solo actúa cuando el actor resuelto es TRAINEE — para los otros 4 roles no hace nada (ver punto 1). **No hay todavía caché de rol/estado** (CLAUDE.MD §5.3.5, TTL 30s): la consulta es la misma que ya hacía `RequireActiveUserGuard` por request (una lectura indexada por PK), no una nueva; construir la caché queda pendiente y declarado, no hecho por decisión unilateral. `SecurityConfig` sigue en `permitAll()`: falta cerrar los puntos 1 (los 4 roles) y 3 (retirar el respaldo de header) antes de poder pasar a `.anyRequest().authenticated()`.
5. ~~**Tests de autorización negativa**~~ — **hechos para TRAINEE, 2026-09-01 (D-64).** `PermissionEnforcementInterceptorTest` y `WallControllerAuthorizationTest` cubren TRAINEE sin permiso → 403, `SUSPENDED` → 403 (con la excepción de `OPEN_SUPPORT_TICKET`), `@PublicEndpoint` → pasa, y el rol sin matriz (ADMIN) → pasa, marcado explícitamente como temporal. Faltan los mismos tests para MENTOR/MENTOR_LEAD/ALCHEMIST el día que tengan matriz real.

---

## 9. Fases

| Fase | Qué entra | Estado |
|---|---|---|
| **1** | `Credencial` + bean `PasswordEncoder` + Spring Session en el `pom` | ✅ Hecho |
| **2** | Migraciones (`V2` columnas, `V3` tabla) + `CredencialJpaEntity` + puertos | En curso |
| **3** | Cadena de Spring Security + login/logout por contraseña + CSRF | |
| **4** | Migración de `X-Actor-Id`, módulo por módulo (§8) | Inventario de autorización por endpoint ✅ hecho (§8.1): los 219 endpoints declaran qué permiso exigen y el build lo verifica. **Matriz rol → permiso y el filtro que la aplica ✅ hechos PARCIALMENTE 2026-09-01 (D-64): solo TRAINEE se verifica de verdad.** MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST siguen sin matriz (falla-abierto deliberado) y falta migrar el header `X-Actor-Id` |
| **5** | Google | ✅ Hecho, ver §6.7. Falta crear el OAuth client en Google Cloud Console (A-9) |
| **5.bis** | Vincular una identidad social a una cuenta ya existente (`POST /auth/social/link`) | ✅ Hecho 2026-09-01, ver §6.9. Falta **desvincular**, que depende de una regla de producto sin confirmar |
| **6** | Apple + Facebook | Adaptadores hechos (§6.5); caso de uso compositor, `IdentidadExterna` y controller ahora también hechos (§6.7, compartidos por los tres proveedores). Falta lo de §6.6 (altas en Apple Developer/Meta, revisión de negocio de Meta) |
| **7** | Reset de contraseña (Redis + envío de correo) | ✅ Hecho, ver §7.5. Envío de correo es `NoOpEnviarEmailAdapter` (sin proveedor real todavía) |

Fuera de alcance por ahora, y anotado para no perderlo: MFA/TOTP para roles administrativos, CAPTCHA en el alta, y protección contra contraseñas filtradas (HaveIBeenPwned) — los tres estaban en la checklist de `MODULO_USERS.md` §5.8 y siguen pendientes.

---

## 10. Preguntas abiertas

| # | Pregunta | Bloquea |
|---|---|---|
| A-1 | ¿Con qué algoritmo hashea Supabase? Define si los usuarios existentes se importan o tienen que resetear su contraseña | Nada hoy; define la fase de migración de datos |
| A-2 | ¿Cuántos usuarios reales hay hoy en el Supabase de producción? Si son de prueba, la migración es gratis | Ídem |
| A-3 | Cookie jar de RN vs `Authorization: Bearer` para la app nativa (§5.1) | Fase 3 del lado de la app |
| A-4 | ¿El futuro panel web comparte esta `SecurityFilterChain` o va separada? | Nada hoy |
| A-5 | Umbrales de rate limit de reset de contraseña (§7.5): se asumieron 5/hora por email y 20/hora por IP sin confirmar con producto | Nada hoy, son ajustables sin recompilar el patron (constantes en `ResetContrasenaService`) |
| A-6 | `EnviarEmailPort` (§7.5): ¿se generaliza a `shared` cuando otro módulo necesite mandar mail, o se queda en `users`? | Nada hoy |
| ~~A-7~~ | ✅ **RESUELTO 2026-08-31.** `IdentidadExterna` ya se vincula al aprobar la `AccountRequest`. Lo cerraron tres cosas: la migración `V12` (`solicitudes_cuenta.proveedor`/`.sujeto_proveedor`, que es donde el `sub` sobrevive la espera entre el alta y la aprobación), la llamada a `SaveIdentidadExternaPort.guardar` desde `AccountRequestService.approve()` en la misma transacción que activa al usuario, y las cuatro variantes de `ResultadoLoginSocial` que reemplazaron al 409 genérico. Probado de punta a punta contra Postgres real en `LoginSocialCicloCompletoIntegrationTest`. Ver §6.8 | Nada — ya no bloquea |
| ~~A-8~~ | ✅ **RESUELTO 2026-09-01 (D-61).** El teléfono **se pide en la Ficha Inicial del onboarding**, no en el alta — decisión del dueño del proyecto. El backend dejó de exigirlo en los cinco puntos donde lo hacía: el DTO web, el comando de aplicación, el invariante del agregado `AccountRequest`, `AutenticacionSocialService.requirePhoneParaAlta` y el `NOT NULL` de `solicitudes_cuenta.telefono` (migración **V14**). Efecto directo: **el alta por Google quedó desbloqueada** — antes ninguna cuenta nueva por login social podía registrarse. Probado en `LoginSocialCicloCompletoIntegrationTest#altaSocialPorGoogleSinTelefonoSeCompletaYSeAprueba` | Nada — ya no bloquea |
| A-9 | Crear el OAuth client de Google en Google Cloud Console y cargar `GOOGLE_OAUTH_CLIENT_ID`/`GOOGLE_OAUTH_CLIENT_SECRET` (hoy vacíos en `application.yaml`) — mismo estado que Apple/Facebook en A-6.6 | Probar el login con Google contra el proveedor real |
