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

## 9. Fases

| Fase | Qué entra | Estado |
|---|---|---|
| **1** | `Credencial` + bean `PasswordEncoder` + Spring Session en el `pom` | ✅ Hecho |
| **2** | Migraciones (`V2` columnas, `V3` tabla) + `CredencialJpaEntity` + puertos | En curso |
| **3** | Cadena de Spring Security + login/logout por contraseña + CSRF | |
| **4** | Migración de `X-Actor-Id`, módulo por módulo (§8) | |
| **5** | Google | |
| **6** | Apple + Facebook | |
| **7** | Reset de contraseña (Redis + envío de correo) | |

Fuera de alcance por ahora, y anotado para no perderlo: MFA/TOTP para roles administrativos, CAPTCHA en el alta, y protección contra contraseñas filtradas (HaveIBeenPwned) — los tres estaban en la checklist de `MODULO_USERS.md` §5.8 y siguen pendientes.

---

## 10. Preguntas abiertas

| # | Pregunta | Bloquea |
|---|---|---|
| A-1 | ¿Con qué algoritmo hashea Supabase? Define si los usuarios existentes se importan o tienen que resetear su contraseña | Nada hoy; define la fase de migración de datos |
| A-2 | ¿Cuántos usuarios reales hay hoy en el Supabase de producción? Si son de prueba, la migración es gratis | Ídem |
| A-3 | Cookie jar de RN vs `Authorization: Bearer` para la app nativa (§5.1) | Fase 3 del lado de la app |
| A-4 | ¿El futuro panel web comparte esta `SecurityFilterChain` o va separada? | Nada hoy |
