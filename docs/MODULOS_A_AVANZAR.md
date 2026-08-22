# Módulos a avanzar — plan de construcción de RenaserBack (Java)

**Fecha:** 2026-08-22
**Documentos hermanos:** `CLAUDE.MD` (reglas de trabajo §0, arquitectura y convenciones) · [`BITACORA_ERRORES.md`](BITACORA_ERRORES.md) (base de conocimiento de errores — **buscar ahí antes de investigar un error**)
**Alcance:** en qué orden se construyen los 14 módulos, por qué ese orden, y cuándo cada uno está terminado.

> Este documento responde **qué construir y en qué orden**.
> `CLAUDE.MD` responde **cómo construirlo** (§5.1.2 carpetas, §5.4 DTOs/validación/logs, §5.4.10 checklist de PR).
> Leer §5.1.2 de `CLAUDE.MD` antes de tocar nada.

---

## 0. Decisión de autenticación: Supabase Auth, no Keycloak

Se investigó explícitamente si Supabase ofrece Keycloak dentro de sus servicios. **No lo ofrece.** El resultado, con las fuentes verificadas:

| Pregunta | Respuesta verificada |
|---|---|
| ¿Supabase ofrece Keycloak como servicio gestionado? | **No.** No existe tal producto |
| ¿Supabase se integra con Keycloak? | **Sí, pero solo como proveedor de social login (OAuth/OIDC).** El servidor Keycloak lo instalás, operás, actualizás y respaldás **vos** |
| ¿Keycloak está en el "Third-Party Auth" de Supabase? | **No.** Esa lista es Clerk, Firebase Auth, Auth0, AWS Cognito y WorkOS |
| ¿Qué usamos entonces? | **Supabase Auth**, que es lo que ya está en producción |

### Por qué NO metemos Keycloak

1. **No reduce trabajo, lo multiplica.** Keycloak es un servidor de identidad completo: hay que desplegarlo, mantenerlo, respaldar su base, rotar sus claves y actualizarlo por CVEs. Hoy eso lo hace Supabase por nosotros, gratis en esfuerzo.
2. **Ya hay usuarios reales en producción.** Meter Keycloak significa migrar identidades existentes — el riesgo más caro posible, a cambio de cero beneficio de negocio.
3. **`CLAUDE.MD` §11 ya lo había decidido**: *"no hay razón de negocio para tocar auth ahora"*. Esta investigación lo confirma con datos en vez de intuición.
4. **La app RN ya habla con Supabase Auth.** Cambiar el emisor del token obliga a tocar el cliente móvil, que §8 y §10 dicen explícitamente que no debe enterarse de la migración.

**Cuándo sí reconsiderarlo (y no antes):** si aparece un requisito de SSO corporativo con un cliente empresa (SAML), federación con un directorio existente (LDAP/AD), o una política que exija que las identidades vivan en infraestructura propia. Ninguna de esas señales existe hoy en el dominio.

### Lo que el equipo de login SÍ tiene que resolver: el tipo de firma del JWT

Este es el hallazgo accionable de la investigación, y **es bloqueante para el módulo `users`**.

Supabase firma sus JWT de dos formas posibles, y **solo una es compatible con lo que `CLAUDE.MD` §5.3.5 y §6 diseñaron**:

| Modo | Algoritmo | ¿Sirve para Spring Security Resource Server? |
|---|---|---|
| **Legacy** | HS256 — **secreto simétrico compartido** | ❌ No con el flujo JWKS. Obliga a repartir el secreto al backend, y ese secreto puede *emitir* tokens, no solo verificarlos |
| **Asimétrico (actual)** | **RS256** (por defecto), ECC o Ed25519 | ✅ Sí. Es el modo correcto |

Con el modo asimétrico, Supabase expone las **claves públicas** en:

```
https://<project-ref>.supabase.co/auth/v1/jwks
```

Spring Security se configura apuntando ahí y **nunca necesita un secreto**: solo puede *verificar* firmas, no emitirlas. Esa es exactamente la propiedad de seguridad que queremos.

**Tarea concreta y bloqueante para el equipo de login:**

- [ ] Verificar en el dashboard de Supabase si el proyecto está en claves **legacy (HS256)** o **asimétricas**
- [ ] Si está en legacy → ejecutar la migración de Supabase a claves asimétricas **antes** de escribir el filtro de autenticación en Java. Supabase soporta convivencia de ambos durante la transición, así que no hay ventana de caída
- [ ] Confirmar la URL del JWKS del proyecto y el `issuer` esperado
- [ ] Confirmar en qué *claim* viaja el rol, si es que viaja (ver §Módulo 1 abajo)

**Ojo con esto último:** el `sub` del JWT nos da la identidad, pero **el rol y el estado son nuestros, no de Supabase**. §5.3.5 es explícito: el filtro carga `{role, status}` desde nuestra tabla `users` (cacheado con Caffeine), **no** los lee del token. Un rol dentro del JWT se queda viejo hasta que el token expira; una suspensión tiene que tomar efecto ya.

**Además, sigue pendiente y no lo resuelve el cambio de framework** (`CLAUDE.MD` §5.3.6): verificar a mano que **no exista ninguna policy de RLS de `INSERT` sobre `public.users` para el rol `authenticated`**. Si existe, un solicitante pendiente puede crear su propia fila y auto-aprobarse, salteándose todo el flujo de `AccountRequest`.

---

## 0.bis Modelo de seguridad y RBAC — decisión definitiva

### La distinción que ordena todo

Son dos preguntas distintas y las responde gente distinta. Mezclarlas es el origen de la mayoría de los agujeros de permisos:

| | Pregunta | Quién la responde |
|---|---|---|
| **Autenticación** | ¿Quién sos? | **Supabase Auth** |
| **Autorización (RBAC)** | ¿Qué podés hacer? | **El módulo `users` en Java. NO Supabase** |

### Autenticación: Supabase Auth nativo

| Opción que ofrece Supabase | Qué es | Veredicto |
|---|---|---|
| **Supabase Auth nativo** | Email/password, magic link, OTP, teléfono, 19 proveedores sociales, SAML SSO | ✅ **Esto** |
| **Social login** (Google, Apple, GitHub…) | Se monta encima de Supabase Auth | ✅ Google + Apple |
| **Third-Party Auth** (Clerk, Auth0, Firebase, Cognito, WorkOS) | Reemplaza Supabase Auth: el proveedor emite los JWT y Supabase los confía | ❌ Migrar identidades en producción + costo, para resolver un problema que no tenemos |
| **Keycloak** | Solo proveedor OIDC, servidor propio | ❌ Ver §0 |

**Nota de plataforma:** si la app llega a la App Store y ofrece login con Google, **Apple exige también "Sign in with Apple"**. Hoy es Android, así que no aplica — pero se planifica antes de portar a iOS, no después.

### RBAC: el rol vive en nuestra tabla, no en el JWT

Supabase tiene una guía oficial de RBAC con **Custom Access Token Hook**: se inyecta el rol como *claim* del JWT y se lee desde las policies de RLS. **No lo usamos como fuente de verdad.** Tres razones:

1. **Un rol dentro del JWT se queda viejo.** Si se suspende a alguien o se le baja el rol, el token que ya tiene en el celular **sigue diciendo lo viejo hasta que expire**. §5.3.5 exige que un `SUSPENDED` corte el acceso *al instante*. Con el rol en la tabla + caché Caffeine invalidada por evento, eso se cumple.
2. **La matriz de permisos no cabe en un claim.** `requireRole` sí. Pero `requireMentorScope` pregunta *"¿este aprendiz está asignado a este mentor?"* — eso es una relación en la base, no un string en un token.
3. **§5.3.4 ya lo había decidido bien**: portar `requireRole` / `requireSelf` / `requireMentorScope` literal a Java.

**Cuándo sí usar el hook de custom claims:** únicamente si la app RN habla **directo** con Supabase (Storage de audios, Realtime, PostgREST). Ahí RLS necesita el rol en el token porque el backend Java no está en el medio. Es un complemento, nunca el sistema de permisos.

### Defensa en profundidad: dónde chequea cada capa

| # | Capa | Qué verifica | Herramienta |
|---|---|---|---|
| 1 | Supabase Auth | La identidad es real | JWT firmado RS256 |
| 2 | Filtro Spring | Firma válida y no expirado | Resource Server + JWKS |
| 3 | Filtro Spring | `status != SUSPENDED` | Tabla `users` + Caffeine (§5.3.5) |
| 4 | `AccessGuard` | Rol / self / alcance de mentor | Las 3 funciones portadas (§5.3.4) |
| 5 | Dominio | Invariantes: `actor.can(MANAGE_ROLES)` | `User.changeRole()` |
| 6 | **RLS en Postgres** | **Última línea si todo lo anterior falla** | Policies de Supabase |

**RLS se queda encendido aunque el backend Java haga la autorización.** No es redundancia: es la capa que asume que las otras cinco pueden fallar.

### Autorización por endpoint: el equivalente al API Gateway

**Pregunta original:** en microservicios se usaba un API Gateway donde se declaraba qué endpoint puede tocar cada rol. ¿Se maneja igual acá?

**Sí, la misma idea existe — y sale más barata.** Lo que cambia es el mecanismo y, sobre todo, **hasta dónde llega**.

| | Microservicios | Monolito modular |
|---|---|---|
| Dónde vive | API Gateway (Spring Cloud Gateway, Kong, APISIX) | **`SecurityFilterChain` de Spring Security**, en el mismo proceso |
| Por qué existe | Que N servicios no reimplementen auth cada uno | Ya hay un solo proceso: es un `@Bean` de configuración |
| Costo | Un salto de red extra + una pieza más que operar | Cero red, cero infraestructura |
| Qué puede decidir | URL + método + claims del token | Lo mismo |

**El límite, que es lo importante:** un gateway (y un `SecurityFilterChain`) solo ve **la URL, el método y el token**. Puede responder *"¿es MENTOR?"*. **No puede** responder *"¿este aprendiz está asignado a ESTE mentor?"* — eso necesita ir a la base y conocer el recurso. Es exactamente `requireMentorScope` de §5.3.4.

Por eso **el gateway nunca es la autorización real**, ni acá ni en microservicios. El error clásico es asumir que sí, y que después una ruta mal escrita o una llamada interna lo esquive.

**Además, un detalle propio de nuestro diseño:** decidimos que **el rol NO viaja en el JWT**. Entonces `hasRole()` / `hasAuthority()` solo funcionan si el filtro de autenticación carga rol y permisos **desde nuestra tabla** (con la caché Caffeine de §5.3.5) y los pone en el `Authentication`. Sin ese filtro, la cadena de seguridad no tiene con qué decidir. **Depende de B-2.**

#### Diseño recomendado: tres capas, cada una con un trabajo

| Capa | Qué decide | Deliberadamente |
|---|---|---|
| **1. `SecurityFilterChain`** | Qué rutas son públicas y cuáles exigen estar autenticado | **Grueso.** Solo el límite público/privado |
| **2. `@RequiresPermission(...)` en el método del controller** | Qué permiso exige ese endpoint | **La tabla endpoint → permiso** |
| **3. `AccessGuard` + dominio** | `requireSelf`, `requireMentorScope`, invariantes | **Fino.** Lo que depende del recurso |

**Por qué la tabla va en anotación sobre el método y no en patrones de URL:** los patrones (`/api/v1/admin/**`) son frágiles — dependen del orden de las reglas, los comodines se solapan, y renombrar una ruta rompe el permiso **en silencio**. La anotación vive pegada al endpoint: no puede desincronizarse de la ruta porque *es* la ruta.

```java
@PostMapping("/api/v1/account-requests/{id}/approve")
@RequiresPermission(APPROVE_ACCOUNT_REQUEST)
public ApprovalResponse approve(@PathVariable UUID id) { ... }
```

#### La matriz auditable, generada sola

Esto es lo que el gateway **no** te daba: un test que recorre por reflexión todos los `@RestController` y

1. **falla si algún endpoint no tiene `@RequiresPermission` ni `@PublicEndpoint`** — es imposible publicar una ruta sin protección por olvido;
2. **imprime la matriz completa** endpoint → permiso, para comparar contra `docs/PERMISSIONS.md` en cada PR.

Se suma a `ArchitectureTest` (§5.4.10). Es la misma propiedad que el equipo valoró al elegir 3 funciones simples en vez de CASL — *la matriz se audita de un vistazo* — pero ahora **el build la verifica**, no la buena voluntad de quien revisa.

#### No confundir con el proxy de transición

`CLAUDE.MD` §10 Fase 1 pone un proxy adelante que rutea `/api/v1/users/**` → Spring y el resto → Next.js. **Ese proxy es solo ruteo, no autorización.** Es temporal y se retira en Fase 3; poner permisos ahí sería escribirlos para tirarlos.

### Login social: Google, Apple, Facebook — directo contra Supabase

**Decidido:** el login social se hace **directo contra Supabase desde el cliente**, con los proveedores nativos de Supabase Auth. El flujo detallado se define más adelante; lo que queda cerrado acá es la arquitectura.

**Lo más importante, y simplifica muchísimo: el backend Java NO implementa OAuth.**

```
App RN  ──signInWithOAuth('google')──►  Supabase Auth  ──►  Google
   ▲                                          │
   └──────────  JWT firmado RS256  ◄──────────┘
   │
   └──Authorization: Bearer <JWT>──►  Backend Java  ──valida contra JWKS──►  listo
```

El backend **nunca ve** al proveedor social, ni maneja `client_secret`, ni redirects, ni el `code` de OAuth. Solo recibe un JWT y lo valida contra el JWKS. **Agregar Facebook o cualquier otra red es configuración en el dashboard de Supabase — cero código en Java.** Esa es la razón técnica de peso para dejar el login social en Supabase y no moverlo nunca al backend.

| Proveedor | Estado | Nota |
|---|---|---|
| **Google** | Planificado | El más usado; arranque natural |
| **Apple** | Planificado | **Obligatorio en iOS** si se ofrece cualquier otro login social. Hoy la app es Android, pero se configura antes de portar |
| **Facebook** | Planificado | Meta exige revisión de la app y verificación de negocio antes de producción — **el trámite tarda, iniciarlo temprano** |
| Otros | A definir | Supabase soporta 19 proveedores + cualquier OAuth2/OIDC |

**Lo que sí toca al backend, y es lo único:** un usuario que entra por Google **no tiene fila en nuestra tabla `users`** la primera vez. Hay que decidir qué pasa en ese primer login:

- **(a)** Se crea automáticamente como `TRAINEE` — contradice el flujo de `AccountRequest` con aprobación y sería una forma de saltear la aprobación
- **(b)** Se rechaza con 403 hasta que exista una `AccountRequest` aprobada — coherente con el flujo actual
- **(c)** El login social solo se habilita para usuarios ya aprobados

**Recomendación: (b) o (c).** Con (a), cualquiera con una cuenta de Google entra al sistema. **Pregunta abierta R-5.**

### Web con cookies vs móvil con Bearer: dos cadenas separadas

El backend va a servir **dos clientes con modelos de sesión distintos**, y mezclarlos es una fuente clásica de agujeros. Las reglas detalladas se definen más adelante; el principio que no se negocia:

| | Móvil (React Native) | Web (panel admin) |
|---|---|---|
| Transporte del token | `Authorization: Bearer <JWT>` | Cookie `httpOnly` + `Secure` + `SameSite` |
| Sesión | **Stateless** | Con cookie de sesión |
| CSRF | **No aplica** — no hay credencial ambiente | **Obligatorio.** El navegador manda la cookie sola |
| CORS | No aplica | Origen del panel, explícito |

**Regla dura: dos `SecurityFilterChain` separadas y ordenadas con `@Order`, nunca una sola configuración compartida.**

- `/api/v1/**` → stateless, solo Bearer, CSRF deshabilitado, **sin aceptar cookies de autenticación jamás**
- resto (panel web) → cookie de sesión, CSRF habilitado, CORS con origen explícito

**Por qué es peligroso mezclarlos:** deshabilitar CSRF globalmente porque "el móvil no lo necesita" deja el panel web abierto a *cross-site request forgery*. Y aceptar cookies en `/api/v1/**` reintroduce ese riesgo en la API móvil, que no lo tenía. **El error se comete una vez, en una línea de configuración, y no da ningún síntoma visible.**

### Checklist para el equipo de login

Ordenado por relación valor/esfuerzo:

- [ ] **1. Claves JWT asimétricas (RS256) + JWKS.** Bloqueante **B-2**. Sin esto Spring necesita un secreto que puede *emitir* tokens, no solo verificarlos
- [ ] **2. Auditar la RLS de `INSERT` en `public.users`.** Bloqueante **B-4**. Si existe una policy para `authenticated`, un solicitante pendiente **se auto-aprueba** — es escalada de privilegios, no un detalle
- [ ] **3. CAPTCHA** (hCaptcha o Cloudflare Turnstile, **modo invisible**) en signup / signin / password reset. Ataca el problema del rate limit de `POST /account-requests` de §5.3.6 **antes** de que la request llegue al backend
- [ ] **4. MFA (TOTP) obligatorio para `ADMIN`, `MENTOR_LEAD` y `ALCHEMIST`.** Supabase emite el claim `aal2`; el filtro Java lo exige para esos roles. **Es la medida que más compra por lo que cuesta**: hoy un ADMIN comprometido aprueba cuentas y reparte roles con solo una contraseña
- [ ] **5. Leaked password protection** (HaveIBeenPwned). Requiere plan Pro
- [ ] **6. Longitud mínima ≥ 12** + requisitos de caracteres. El default de 8 es el piso, no el objetivo
- [ ] **7. Reautenticación para cambio de contraseña** si la sesión tiene más de 24 h
- [ ] **8. Confirmar** la URL del JWKS y el `issuer` esperado del proyecto

---

## 1. Cómo se lee este plan

**Regla de orden:** un módulo se construye después de aquellos de los que depende. No se avanza a la siguiente ola hasta que la anterior esté con sus tests en verde y su `ArchitectureTest` pasando.

**Estados:** ✅ hecho · 🔄 en curso · ⬜ pendiente · 🔒 bloqueado

**"Definición de terminado" (DoD) — común a todos los módulos:**

- [ ] `domain/` plano (sin subcarpetas, §5.1.2), sin imports de Spring/JPA/Jackson
- [ ] Tests unitarios de dominio, sin Spring y sin Postgres
- [ ] Casos de uso con comando *self-validating* (§5.4.3)
- [ ] Controller tonto: sin repositorios, sin `@Transactional`, sin reglas de negocio (§5.4.6)
- [ ] DTO de salida como proyección explícita, nunca la entidad (§8)
- [ ] Migración Flyway propia en `db/migration/<modulo>/`
- [ ] Test de integración con Testcontainers
- [ ] `ArchitectureTest` en verde
- [ ] **`./mvnw clean test` ejecutado y en verde** (`CLAUDE.MD` §0.2 — no se reporta terminado sin esto)
- [ ] **Pruebas de seguridad** (`CLAUDE.MD` §0.3): rol sin permiso → 403 · usuario `SUSPENDED` → 403 · el rol no se puede inyectar por el body · todo endpoint con `@RequiresPermission` o `@PublicEndpoint`
- [ ] **Avance documentado** y decisiones nuevas en §8; errores encontrados en `docs/BITACORA_ERRORES.md`
- [ ] Contrato verificado contra `docs/API_CONTRACT.md` — la app RN no ve ninguna ruptura

---

## 2. Bloqueantes globales abiertos

| # | Bloqueante | Impacto | Dueño |
|---|---|---|---|
| ~~B-1~~ | ~~No hay JDK 25 instalado~~ | ✅ **Resuelto 2026-08-22.** Eclipse Temurin **25.0.4.1 LTS** instalado en `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`; `JAVA_HOME` apunta ahí a nivel usuario y máquina. `./mvnw clean test` compila con `release 25` y pasa 21/21 | Infra |
| B-2 | **Tipo de firma del JWT de Supabase sin confirmar** (§0) | Bloquea el filtro de autenticación de `users` | Login |
| B-3 | **Valores reales de `TraineePhase` desconocidos** | Bloquea `shared/domain/TraineePhase` y todo `phasecontracts` | Producto |
| B-4 | **RLS de `INSERT` en `public.users` sin auditar** (§5.3.6) | Agujero de escalada de privilegios que la migración no arregla sola | Login |
| B-5 | **Permisos de `MENTOR_LEAD` sin definir** (R-2 de §4.1). *Parcialmente resuelto: R-1 y R-4 cerrados el 2026-08-22* | El enum en código tiene 4 de 5 roles. Ya se puede crear `MENTOR_LEAD` y `MentorLeadProfile`; falta la matriz de `Permission` | Producto |

---

## 3. Orden de construcción por olas

### Ola 0 — Fundación 🔄

Todo lo demás depende de esto. Sin esta ola no hay nada.

| Módulo | Estado | Qué entrega |
|---|---|---|
| `shared` | ✅ | `UserId`, `Clock` (puerto), `NotAuthorizedException`, `DomainEvent`. Declarado `@ApplicationModule(type = OPEN)` |
| **`users`** | 🔄 | **Módulo 1. Ver detalle en §4** |

### Ola 1 — Dominio puro, sin IA y sin infraestructura nueva ⬜

Se eligen primero porque son **reglas de negocio casi puras**: máximo valor de tests unitarios, mínima superficie de riesgo, y sirven para calibrar la arquitectura con algo real antes de tocar lo complicado.

| Módulo | Depende de | Qué entrega | Nota |
|---|---|---|---|
| `points` | `users` | Puntos y ranking | **Casi todo `adapter/in/event`**: consume `HabitCompletedEvent`, `RockCompletedEvent`. Primer consumidor real de eventos Modulith |
| `phasecontracts` | `users` | Pacto de Sangre, día de firma por fase | 🔒 por **B-3**. La regla *"en qué fase está"* vs *"cuándo le toca"* (`CONTRACT_UNLOCK_DAY`) es dominio puro, se traduce 1:1 |
| ~~`traineeprofile`~~ | — | — | ❌ **Descartado como módulo propio (2026-08-22).** Ver abajo |

> **Resolución: `traineeprofile` NO es un módulo.** `CLAUDE.MD` §5 lo listaba como módulo aparte mientras §5.3.2 ponía `TraineeProfile` dentro de `users` — se contradecían. Queda **dentro de `users`**, por tres razones concretas:
> 1. **Es 1-a-1 con `User`**, igual que los otros cuatro perfiles. Sacar solo este rompe la simetría de §5.3.1 sin ganar nada.
> 2. **Se crea en la misma transacción** que el usuario (`ApproveAccountRequestUseCase` crea `User` + perfil + marca la solicitud, todo atómico). Partirlo en dos módulos Modulith convierte una `@Transactional` gratis en una coordinación entre módulos — el costo que §9.1 dice explícitamente que no se paga sin necesidad.
> 3. **`users` tendría que depender de `traineeprofile` para poder crear un usuario**, y `traineeprofile` de `users` para saber de quién es el perfil. Acoplamiento circular disfrazado de modularidad.
>
> **Total: 14 módulos, no 15.**

### Ola 2 — Núcleo del producto ⬜

Es lo que el aprendiz usa todos los días. Se construye después de `points` para que los eventos ya tengan quien los escuche.

| Módulo | Depende de | Qué entrega | Riesgo |
|---|---|---|---|
| `habits` | `users`, `points` | Hábito, Santuario (phone-free), check-in diario | Medio. La máquina de estados `HabitStatus` (PENDING/IN_PROGRESS/COMPLETED/FAILED/EXPIRED) es dominio puro y de altísimo valor en tests. `PhoneFreeRun` va **plano en `domain/`**, no en subcarpeta |
| `rocks` | `users`, `points` | Rock, admin de rocks, modo Verdugo | Medio |

Acá aparece el primer `@Scheduled` real (`ExpireTracksScheduler`), que reemplaza el cron actual — y con él la primera prueba de que un caso de uso se invoca desde dos adaptadores distintos (web y scheduler). Si algo de la lógica se hubiera colado en el controller, se rompe acá.

### Ola 3 — Contenido y comunicación ⬜

Módulos de menor acoplamiento entre sí; se pueden paralelizar entre varias personas.

| Módulo | Depende de | Nota |
|---|---|---|
| `notifications` | todos | **Casi todo `adapter/in/event`.** Consumidor de eventos de todo el sistema. Conviene construirlo temprano dentro de esta ola: es el que valida que el outbox de Modulith funciona de punta a punta |
| `academy` | `users` | Incluye Academia Adaptativa y Post Program |
| `community` | `users` | Ver **Ola 4** para el feed en vivo |
| `calendar` | `users` | |
| `support` | `users` | Tickets |

### Ola 4 — Tiempo real ⬜

Se separa de la Ola 3 a propósito: **es el único punto donde el monolito no escala solo** (`CLAUDE.MD` §5.2.1).

| Módulo | Qué entrega | Requisito de infraestructura |
|---|---|---|
| `chat` | Chat global por WebSocket/SSE en vez de polling | **Redis Pub/Sub.** Con más de una instancia detrás del balanceador, un evento in-process nunca sale de su instancia: dos usuarios conectados a réplicas distintas no se ven |
| `community` (feed en vivo) | Empuje en vivo del feed | Mismo canal Redis |

**Regla que no se puede saltear:** el mensaje siempre se escribe **primero en Postgres**; el pub/sub es solo el empujón en vivo a quien ya está mirando la pantalla. Redis Pub/Sub es *fire-and-forget* — si una instancia está caída un instante, pierde ese mensaje del canal.

Efecto colateral que hay que resolver en esta ola: la invalidación de la caché de rol/estado de §5.3.5 también es in-process. Con N instancias, debe viajar por el mismo canal Redis, o un `SUSPENDED` tarda hasta 30s (el TTL) en tomar efecto en las otras réplicas.

### Ola 5 — IA ⬜

**Última a propósito.** Son los módulos que más ganan con Spring AI 2.0, pero también los de mayor superficie de riesgo. Se llega acá con la arquitectura ya probada por 10 módulos.

| Módulo | Depende de | Nota |
|---|---|---|
| `evidence` | `habits`, `rocks`, `onboarding` | Puerto único `EvidenceValidationPort`, compartido. El límite de reintentos (`ia_attempts` → `pending_review`) es **lógica de dominio**, vive en `evidence/domain`, **no** en el adapter de Gemini |
| `onboarding` | `users`, `evidence` | Validación V90/6Ps. El patrón async + polling ya está peleado en producción: **se preserva tal cual**, solo cambia la implementación interna |
| `rag` / `renasia` | `users` | `PgVectorStore` sobre el mismo Postgres. Sin base de datos nueva |

**Contrato que no cambia:** `POST /validate` responde `202 {status:"processing"}` de inmediato; `GET /validate?recordingId=X` consulta estado. Es el patrón correcto también en Spring — nunca bloquear el hilo de request esperando a Gemini.

**Regla de logging crítica para esta ola:** `traceId` en MDC propagado a los `@Async`. Sin eso, un flujo async + polling es imposible de correlacionar en los logs (§5.4.9).

---

## 4. Módulo 1 — `users` (la raíz)

**Por qué es el primero:** todo módulo del sistema pregunta *"¿quién es este usuario y qué puede hacer?"*. `users` es la fuente de verdad de identidad, rol y estado. Nada se puede construir encima de algo que todavía no existe.

### 4.1 Los enums son la raíz de la raíz

`UserRole` no es "un enum más": **es el eje del sistema de permisos entero**. Los roles y sus perfiles determinan qué puede hacer cada persona en los 13 módulos restantes. Por eso se construyen primero, antes que cualquier otra clase.

#### Los 5 roles

> **Corrección 2026-08-22.** `CLAUDE.MD` §5.3.1 decía "los 4 roles" y omitía el Líder de Mentores, contradiciendo la tabla de §5 que sí lo listaba. Son **cinco**. La línea de §5.3.1 ya fue corregida.

| Nombre de negocio | Constante | Qué es |
|---|---|---|
| Aprendiz | `TRAINEE` | El usuario del programa de 90 días. Rol por defecto de todo alta pública |
| Mentor | `MENTOR` | Acompaña a un conjunto de aprendices asignados |
| Líder de Mentores | `MENTOR_LEAD` | Coordina mentores. **Nuevo respecto al código actual** |
| Administrador / Operación | `ADMIN` | Panel de gestión: aprueba cuentas, asigna mentores |
| Alquimista | `ALCHEMIST` | Máximo nivel |

| Enum | Valores | Regla que lleva adentro |
|---|---|---|
| `UserRole` | los 5 de arriba | `canManageRoles()` · `defaultForSelfRegistration()` → siempre `TRAINEE` |
| `UserStatus` | `ACTIVE`, `SUSPENDED` | `allowsAccess()` → `SUSPENDED` devuelve 403 antes de llegar al caso de uso |

#### ¿Enum es lo más óptimo? Sí para el rol, no para los permisos

**Para el rol: sí, enum es la elección correcta.** Es un conjunto **cerrado, chico y estable** (5 valores), cada uno atado a una clase de perfil distinta. El enum te da chequeo en tiempo de compilación, `switch` exhaustivo (el compilador te avisa si agregás un rol y te olvidaste de cubrirlo en algún lado) y cero costo en runtime.

**Enum sería la elección equivocada solo si** los roles tuvieran que crearse o modificarse **en caliente por un administrador, sin desplegar**. Ahí serían filas en una tabla. No es el caso: cinco roles fijos, atados a clases de perfil que existen en el código.

**Pero el enum solo no alcanza para los permisos.** El riesgo concreto es este:

```java
// ANTIPATRON: esto se va a repetir en 30 lugares
if (user.role() == ADMIN || user.role() == ALCHEMIST) { ... }
```

El día que `MENTOR_LEAD` necesite aprobar algo, hay que **cazar todos esos `if` a mano** por el código. Uno que se escape es un agujero de permisos silencioso. Y es exactamente lo que acaba de pasar con el quinto rol: apareció tarde.

**Recomendación: enum de rol + enum de permiso, con la matriz declarada en el constructor.**

```java
public enum Permission {
    APPROVE_ACCOUNT_REQUEST, MANAGE_ROLES, ASSIGN_MENTOR, VIEW_ALL_TRAINEES, /* ... */
}

public enum UserRole {
    TRAINEE      (Set.of()),
    MENTOR       (Set.of(VIEW_ASSIGNED_TRAINEES)),
    MENTOR_LEAD  (Set.of(VIEW_ASSIGNED_TRAINEES, VIEW_ALL_TRAINEES, ASSIGN_MENTOR)),
    ADMIN        (Set.of(/* ... */, APPROVE_ACCOUNT_REQUEST, MANAGE_ROLES)),
    ALCHEMIST    (Set.of(/* todos */));

    private final Set<Permission> permissions;

    public boolean can(Permission permission) { return permissions.contains(permission); }
}
```

Qué se gana, y por qué encaja con lo ya decidido:

- **La matriz de permisos completa vive en un solo archivo**, legible de un vistazo. Es justo lo que el equipo valoró en `docs/PERMISSIONS.md` al elegir 3 funciones simples en vez de CASL.
- **Agregar un rol es tocar un archivo**, no cazar `if` por todo el repo.
- **No es un motor de reglas.** Sigue siendo un enum: cero dependencias, cero configuración, cero costo en runtime. No contradice §5.3.4 — lo hace mantenible.
- `AccessGuard.requireRole(...)` pasa a preguntar por **permiso**, no por rol: `caller.can(APPROVE_ACCOUNT_REQUEST)`. El código dice *qué* se necesita, no *quién* lo tiene.

`requireSelf` y `requireMentorScope` **no cambian**: no son preguntas de rol, son preguntas de relación (¿sos vos? ¿este aprendiz es tuyo?), y siguen siendo funciones aparte.

#### Al persistir: `STRING`, nunca `ORDINAL`

En el `UserJpaEntity` (Ola de persistencia): `@Enumerated(EnumType.STRING)`. Con `ORDINAL` se guarda la **posición** del valor en el enum — el día que alguien inserte `MENTOR_LEAD` en el medio de la lista, **todos los roles ya guardados en la base cambian de significado en silencio**. Es corrupción de datos sin error visible.

#### Blindaje que ya está en el código

`User.registerTrainee(...)` **no recibe el rol como parámetro**. El rol no puede viajar desde el cliente público porque *no existe el lugar donde ponerlo*. El compilador impide el mass-assignment; no hace falta un `if` en runtime que alguien pueda borrar (§5.3.3).

#### Preguntas abiertas (no se inventan valores de dominio)

| # | Pregunta | Estado |
|---|---|---|
| R-1 | ¿`MENTOR_LEAD` tiene perfil propio o reusa `MentorProfile`? | ✅ **Resuelto (2026-08-22): perfil propio, `MentorLeadProfile`.** Son perfiles diferentes. Sus atributos concretos se definen más adelante |
| R-2 | ¿Qué permisos tiene `MENTOR_LEAD` por encima de `MENTOR`? | ⬜ Abierto. Bloquea la matriz de `Permission` |
| R-3 | ¿`UserStatus` necesita `PENDING` / `GRADUATED` / `DROPPED`? | ⬜ Abierto. Hoy solo los dos que el documento original respalda |
| R-4 | ¿"Administrador" y "Operación" son uno o dos roles? | ✅ **Resuelto (2026-08-22): son el mismo rol.** Queda un único `ADMIN` — "Administrador / Operación" son dos nombres de negocio para la misma constante |
| R-5 | **Alguien entra por Google y no tiene fila en `users`. ¿Qué pasa?** ¿Se crea como `TRAINEE` automáticamente, se rechaza hasta tener una `AccountRequest` aprobada, o el login social solo se habilita para aprobados? | ⬜ Abierto. Con la opción automática, **cualquiera con cuenta de Google entra al sistema** saltándose la aprobación. Ver §0.bis |

#### Deuda inmediata en el código

`domain/UserRole.java` hoy tiene **4 constantes: falta `MENTOR_LEAD`**. Es lo primero que se corrige al retomar código — junto con decidir R-1 y R-2, que definen si el cambio es de una línea o incluye una clase de perfil nueva.

### 4.2 Estado actual del módulo

| Pieza | Estado |
|---|---|
| `users/package-info.java` (`@ApplicationModule`) | ✅ |
| `domain/UserRole`, `UserStatus` | 🔄 **falta `MENTOR_LEAD`** (4 de 5 roles) y el enum `Permission` — ver §4.1 |
| `domain/Email` (value object, normaliza a minúsculas) | ✅ |
| `domain/User` (raíz de agregado, sin setters públicos) | ✅ |
| Tests de dominio (`UserTest` 7, `EmailTest` 8) | ✅ |
| `ArchitectureTest` (6 reglas: Modulith + ArchUnit) | ✅ |
| `domain/AccountRequest` | ⬜ |
| `domain/TraineeProfile`, `MentorProfile`, **`MentorLeadProfile`**, `AdminProfile`, `AlchemistProfile` | ⬜ **5 clases separadas**, **no** jerarquía JPA con discriminador. Atributos de `MentorLeadProfile` a definir |
| `port/out/` (`LoadUserPort`, `SaveUserPort`, `SupabaseAdminAuthPort`) | ⬜ |
| `application/` (casos de uso, §4.3) | ⬜ |
| `port/in/` | ⬜ |
| `adapter/out/persistence` (JPA + MapStruct) | ⬜ |
| `adapter/in/web` (controllers + DTOs + `AccessGuard`) | ⬜ |
| Filtro JWT + caché Caffeine (§5.3.5) | 🔒 por **B-2** |
| `api/UserSummary` (lo único que ven otros módulos) | ⬜ |
| Flyway `db/migration/users/` en modo baseline | ⬜ |

### 4.3 Casos de uso a construir

| Caso de uso | Reemplaza | Nota |
|---|---|---|
| `SubmitAccountRequestUseCase` | `POST /api/v1/account-requests` | Comando **sin campo `role`**. Rate limit: 60/hora por IP |
| `ApproveAccountRequestUseCase` | Aprobar en panel admin | Transacción única: `User` + perfil + `AccountRequest.APPROVED`. Rollback compensatorio con `deleteUser` en Supabase vía `TransactionSynchronization.afterCompletion` |
| `RejectAccountRequestUseCase` | Rechazar en panel | **Debe** disparar `deleteUser` en Supabase para liberar el correo (anti-squatting) |
| `InviteAndCreateUserUseCase` | `inviteAndCreateUser` | Vía para MENTOR/ADMIN/ALCHEMIST y cohortes a mitad de programa |
| `GetMyProfileUseCase` / `UpdateMyProfileUseCase` | U-02, U-03 | |
| `UpdateTraineeProfileUseCase` | U-05 | El comando **no tiene** `programDay`, `coherenceScore`, `leaguePoints`, `currentPhase`. El compilador los excluye |
| `AssignMentorToTraineeUseCase` | `assignMentorToTrainee` | Actualiza `totalTraineesManaged` atómicamente |
| `UpdateUserRoleUseCase` | `updateUserRole` | **Solo el camino simple** (crear el perfil nuevo vacío). Migración de datos entre perfiles queda fuera de alcance, explícitamente |

### 4.4 Orden de construcción dentro del módulo

De adentro hacia afuera — la misma dirección de las flechas de dependencia:

1. ✅ `domain/` — enums, value objects, `User`
2. ⬜ `domain/AccountRequest` + los 4 perfiles
3. ⬜ `port/out/` — qué necesita el dominio del mundo
4. ⬜ `application/` — casos de uso, testeados **mockeando los puertos out**
5. ⬜ `port/in/`
6. ⬜ `adapter/out/persistence` — `JpaEntity` + mapper + adapter (**acá y solo acá entra MapStruct**, §5.4.5)
7. ⬜ `adapter/in/web` — controllers, DTOs, `AccessGuard`
8. ⬜ `db/migration/users/V1__baseline_users.sql`
9. ⬜ `api/UserSummary`

Los pasos 1 a 5 **no necesitan base de datos ni servidor web**. Que eso sea posible es la prueba de que la arquitectura está bien puesta.

---

## 5. Mapa de dependencias entre módulos

```
                          shared
                             │
                          users ◄──────────── todos preguntan "quien es y que puede"
                             │
        ┌────────────┬───────┴────────┬──────────────┐
        ▼            ▼                ▼              ▼
     points   phasecontracts     academy        support
        ▲            ▲           community      calendar
        │            │                │
        │            │                ▼
        │            │             chat (Redis Pub/Sub)
        │            │
     habits ─────► eventos ─────► notifications
     rocks                            ▲
        │                             │
        ▼                             │
    evidence ──► onboarding ──────────┘
        │
      rag / renasia
```

Las flechas son **dependencias de compilación** (llamada directa a la API pública de otro módulo). Los eventos de dominio van al revés y no crean acoplamiento: `habits` publica `HabitCompletedEvent` sin saber que `points` y `notifications` lo escuchan.

---

## 6. Lo que NO se construye todavía

Registrado para que nadie lo empiece por iniciativa propia:

| Pieza | Cuándo |
|---|---|
| RabbitMQ / Kafka | Solo si `evidence`/`rag` se extrae como servicio aparte (§9). Hoy los eventos in-process de Modulith cuestan nanosegundos y ya tienen outbox |
| Microservicios | Solo ante las 3 señales de §9: perfil de carga divergente, aislamiento de fallos, o ciclo de release distinto. **Separar sin necesidad cambia una `@Transactional` gratis por una Saga con compensaciones a mano** |
| GraalVM Native Image | Solo si aparece requisito de cold-start. En un monolito siempre-arriba no es prioridad |
| Migrar el storage de audios fuera de Google Drive | Queda detrás de `AudioCatalogPort`; se migra después sin tocar dominio |
| Migrar Postgres fuera de Supabase | No es problema de arquitectura, es infraestructura ya resuelta |
| Reescribir auth | Ver §0 |

---

## 7. Estado del repositorio hoy (2026-08-22)

Lo que existe realmente en disco, para que nadie tenga que adivinar:

```
renaser-backend/
├── pom.xml                    Boot 4.1.1 · Java 25 · Spring AI 2.0.0 · Modulith 2.1.0 · MapStruct 1.6.3
├── mvnw / mvnw.cmd
├── docker-compose.yml
├── CLAUDE.MD                  arquitectura y convenciones (CÓMO)
├── docs/
│   └── MODULOS_A_AVANZAR.md   este documento (QUÉ y en qué orden)
└── src/
    ├── main/java/com/renaser/os/
    │   ├── RenaserOsApplication.java
    │   ├── shared/
    │   │   ├── package-info.java          @ApplicationModule(type = OPEN)
    │   │   ├── domain/  Clock · UserId · NotAuthorizedException
    │   │   └── event/   DomainEvent
    │   └── users/
    │       ├── package-info.java          @ApplicationModule("Users")
    │       └── domain/  User · Email · UserRole · UserStatus
    └── test/java/com/renaser/os/
        ├── ArchitectureTest.java          Modulith verify + 5 reglas ArchUnit
        ├── TestcontainersConfiguration.java
        ├── TestRenaserOsApplication.java
        └── users/domain/  UserTest · EmailTest
```

**Tests: 21 en verde** (7 `UserTest`, 8 `EmailTest`, 6 `ArchitectureTest`).

### Cómo compilar

```bash
./mvnw clean test        # 21/21 en verde, compilando con release 25
```

**Requisito:** Eclipse Temurin JDK **25.0.4.1 LTS** (instalado 2026-08-22).
`JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`, definido a nivel usuario y máquina.

Reinstalar en otra máquina Windows: `winget install --id EclipseAdoptium.Temurin.25.JDK`

> **Nota para quien también trabaje en Android:** el `JAVA_HOME` de usuario antes apuntaba al JBR de Android Studio (JDK 21) y se cambió a Temurin 25. Android Studio usa su JBR interno por su propia configuración, así que no se ve afectado — pero si corrés `./gradlew` de un proyecto Android **desde la terminal**, ahora tomará Java 25 y el Android Gradle Plugin puede rechazarlo. En ese caso, fijá el JDK en el `gradle.properties` de ese proyecto con `org.gradle.java.home`, en vez de volver atrás el `JAVA_HOME` global.
>
> Revertir, si hiciera falta: `setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"`

### Deuda conocida en el código actual

| Qué | Dónde | Por qué quedó así |
|---|---|---|
| Falta `MENTOR_LEAD` en el enum (4 de 5 roles) | `users/domain/UserRole.java` | `CLAUDE.MD` §5.3.1 decía "los 4 roles". Ya corregido en el documento; falta en el código |
| No existe el enum `Permission` | `users/domain/` | Decidido en §4.1, pendiente R-2 |
| Dos reglas de ArchUnit con `allowEmptyShould(true)` | `ArchitectureTest.java` | `application/` y `adapter/in/web` todavía no existen. Llevan `TODO`; se quitan al crear el primer caso de uso |
| No hay test `contextLoads` | — | Se retiró del esqueleto: necesita Docker y todavía no hay nada que levantar |
| `spring.threads.virtual.enabled` sin configurar | `application.yaml` | Pendiente |

---

## 8. Registro de decisiones

Una línea por decisión cerrada, para no rediscutirlas.

| # | Decisión | Fecha | Dónde está el razonamiento |
|---|---|---|---|
| D-1 | **Monolito modular + hexagonal por módulo**, no microservicios | 2026-08-21 | `CLAUDE.MD` §4.1, §9 |
| D-2 | **Maven**, no Gradle. El build tool es irrelevante para hexagonal | 2026-08-22 | `CLAUDE.MD` §5.1 |
| D-3 | **Paquete base `com.renaser.os`** (el esqueleto usaba `com.renaser.renaser_backend`) | 2026-08-22 | `CLAUDE.MD` §5.1 |
| D-4 | **`domain/` plano: solo clases, sin subcarpetas** | 2026-08-22 | `CLAUDE.MD` §5.1.2 |
| D-5 | **MapStruct solo en persistencia**; mapeo a mano en la frontera web | 2026-08-22 | `CLAUDE.MD` §5.4.5 |
| D-6 | **Validación en tres niveles**: DTO web → comando self-validating → dominio | 2026-08-22 | `CLAUDE.MD` §5.4.3 |
| D-7 | **Controller tonto**: sin repositorios, sin `@Transactional`, sin reglas | 2026-08-22 | `CLAUDE.MD` §5.4.6 |
| D-8 | **`domain/` no loguea** | 2026-08-22 | `CLAUDE.MD` §5.4.9 |
| D-9 | **Supabase Auth nativo.** No Keycloak, no Third-Party Auth | 2026-08-22 | §0 |
| D-10 | **El rol NO viaja en el JWT.** Vive en nuestra tabla + caché Caffeine | 2026-08-22 | §0.bis |
| D-11 | **RLS encendido** aunque el backend Java autorice — última línea | 2026-08-22 | §0.bis |
| D-12 | **Autorización por endpoint con `@RequiresPermission`**, no con patrones de URL | 2026-08-22 | §0.bis |
| D-13 | **Enum de rol + enum de permiso**, matriz en el constructor. `@Enumerated(STRING)`, nunca `ORDINAL` | 2026-08-22 | §4.1 |
| D-14 | **Son 5 roles.** `MENTOR_LEAD` existe y tiene perfil propio | 2026-08-22 | §4.1 (R-1) |
| D-15 | **"Administrador" y "Operación" son el mismo `ADMIN`** | 2026-08-22 | §4.1 (R-4) |
| D-16 | **`traineeprofile` no es un módulo**: vive dentro de `users`. Total 14 módulos | 2026-08-22 | §3, Ola 1 |
| D-17 | **JDK: Eclipse Temurin 25 LTS.** `JAVA_HOME` de usuario movido del JBR de Android Studio a Temurin | 2026-08-22 | §7 |
| D-18 | **Login social directo contra Supabase** (Google, Apple, Facebook y los que sigan). El backend Java **no implementa OAuth**: solo valida el JWT | 2026-08-22 | §0.bis |
| D-19 | **Dos `SecurityFilterChain` separadas**: `/api/v1/**` stateless con Bearer y sin CSRF; panel web con cookie y CSRF. Nunca una configuración compartida | 2026-08-22 | §0.bis |
| D-20 | **Reglas de trabajo obligatorias** en `CLAUDE.MD` §0: sin atribución de IA en commits, pruebas siempre, todo se documenta, bitácora de errores | 2026-08-22 | `CLAUDE.MD` §0 |

---

## 9. Fuentes verificadas

- [Supabase Docs — Login with Keycloak](https://supabase.com/docs/guides/auth/social-login/auth-keycloak) — Keycloak como proveedor OAuth/OIDC, servidor propio
- [Supabase Docs — Third-party auth overview](https://supabase.com/docs/guides/auth/third-party/overview) — la lista es Clerk, Firebase, Auth0, Cognito, WorkOS; Keycloak **no** está
- [Supabase — JWT Signing Keys](https://supabase.com/features/jwt-signing-keys) y [JSON Web Tokens](https://supabase.com/docs/guides/auth/jwts) — legacy HS256 vs asimétrico RS256/ECC/Ed25519, endpoint JWKS
- [Supabase Docs — Self-hosted auth keys](https://supabase.com/docs/guides/self-hosting/self-hosted-auth-keys)
- [Supabase Blog — Third-party auth](https://supabase.com/blog/third-party-auth-mfa-phone-send-hooks)
- `CLAUDE.MD` §5 (mapeo de dominio), §5.1.2 (carpetas), §5.2.1 (tiempo real), §5.3 (`users`), §5.4 (convenciones), §9 (cuándo separar), §10 (strangler fig)
