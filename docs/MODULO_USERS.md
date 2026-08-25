# Módulo `users` — estado, decisiones y preguntas abiertas

**Documentos hermanos:** `CLAUDE.MD` (arquitectura y convenciones, aplica a todos los módulos) · [`MODULOS_A_AVANZAR.md`](MODULOS_A_AVANZAR.md) (qué módulos existen y en qué orden) · [`BITACORA_ERRORES.md`](BITACORA_ERRORES.md) (errores y bugs)

**Por qué este documento existe:** `users` es, por ahora, el único módulo construido — casi todas las decisiones recientes eran específicas de él y estaban inflando `CLAUDE.MD` (reglas globales) y `MODULOS_A_AVANZAR.md` (plan de los 14 módulos) con detalle de uno solo. A partir de acá, todo lo que sea específico de `users` se documenta y actualiza **acá**, no en esos dos. Cuando se arranque el siguiente módulo, sigue el mismo patrón: `docs/MODULO_<NOMBRE>.md`.

Las decisiones de acá usan la misma numeración `D-N` que `MODULOS_A_AVANZAR.md` §8 (es un solo contador global, repartido en dos archivos por prolijidad, no dos numeraciones distintas).

---

## 1. Por qué `users` es el primero

Todo módulo del sistema pregunta *"¿quién es este usuario y qué puede hacer?"*. `users` es la fuente de verdad de identidad, rol y estado. Nada se puede construir encima de algo que todavía no existe.

## 2. Los 5 roles

| Nombre de negocio | Constante | Qué es |
|---|---|---|
| Aprendiz | `TRAINEE` | El usuario del programa de 90 días. Rol por defecto de todo alta pública |
| Mentor | `MENTOR` | Acompaña a un conjunto de aprendices asignados |
| Líder de Mentores | `MENTOR_LEAD` | Coordina mentores |
| Administrador / Operación | `ADMIN` | Panel de gestión: aprueba cuentas, asigna mentores |
| Alquimista | `ALCHEMIST` | Máximo nivel |

`UserRole` es un enum (D-13): conjunto cerrado, chico y estable, cada uno atado a una clase de perfil distinta. La matriz fina de **permisos** (`Permission` enum, `caller.can(APPROVE_ACCOUNT_REQUEST)` en vez de `if (role==ADMIN)`) sigue sin construirse — bloqueada por R-2.

`UserStatus`: `ACTIVE`, `SUSPENDED`. `SUSPENDED` corta el acceso antes de llegar al caso de uso.

**Al persistir: `STRING`, nunca `ORDINAL`** — con `ORDINAL` se guarda la posición, no el valor; insertar un rol en el medio corrompería en silencio todo lo ya guardado.

**Blindaje ya en el código:** `User.registerTrainee(...)` no recibe el rol como parámetro — no existe el lugar donde ponerlo, así que el cliente público no puede mandarlo ni por error.

## 3. Preguntas abiertas (no se inventan valores de dominio)

| # | Pregunta | Estado |
|---|---|---|
| R-1 | ¿`MENTOR_LEAD` tiene perfil propio o reusa `MentorProfile`? | ✅ Resuelto (2026-08-22): perfil propio. Sus campos concretos siguen sin definirse (ver `perfiles_lider_mentores` en el SQL, marcado `[PENDIENTE-CONFIRMAR]`) |
| R-2 | ¿Qué permisos tiene `MENTOR_LEAD` por encima de `MENTOR`? | ⬜ Abierto. Bloquea el enum `Permission` y, con él, `@RequiresPermission` en los controllers |
| R-3 | ¿`UserStatus` necesita `PENDING` / `GRADUATED` / `DROPPED`? | ⬜ Abierto. Hoy solo `ACTIVE`/`SUSPENDED` |
| R-4 | ¿"Administrador" y "Operación" son uno o dos roles? | ✅ Resuelto (2026-08-22): el mismo rol, un único `ADMIN` |
| R-5 | Alguien entra por Google/Apple y no tiene fila en `users`. ¿Qué pasa? | ✅ **Resuelto (2026-08-24)**: mismo flujo de `AccountRequest` que hoy — se manda la solicitud, ADMIN/ALCHEMIST la aprueba o rechaza y asigna el rol. La diferencia con el alta por formulario: el email llega **pre-verificado** por Google/Apple, así que no hace falta el paso de verificación de correo que sí necesita el alta con email/contraseña. Ver §5 |

## 4. Estado actual del módulo

| Pieza | Estado |
|---|---|
| `users/package-info.java` (`@ApplicationModule`) | ✅ |
| `domain/model/user/` (`User`, `Email`, `UserRole`, `UserStatus`) | ✅ 5/5 roles. Falta `Permission` (R-2) |
| `domain/model/accountrequest/` (`AccountRequest` + `Id`/`Status`) | ✅ `submit`/`approve`/`reject`, campos = `solicitudes_cuenta` del SQL |
| `domain/model/mentorprofile/` (`MentorProfile` + `Level`/`OperationalStatus`) | ✅ único perfil con tabla propia (D-25) |
| `MentorLeadProfile` | ⬜ sin tabla ni clase: `perfiles_lider_mentores` se eliminó del SQL (placeholder sin confirmar, D-25). Se agrega cuando se sepa qué campos lleva |
| `domain/model/participante/` (`ParticipacionPrograma`) | ✅ **Resuelto 2026-08-24 (D-33): es el 4to agregado de `users`.** Ver §6.bis |
| `application/ports/{in,out}/` (por agregado: `user/`, `accountrequest/`, `mentorprofile/`) | ✅ 8 casos de uso, 7 puertos de salida |
| `application/services/` (`AccountRequestService`, `UserAccountService`, `MentorProfileService`) | ✅ una clase por agregado (D-27) |
| `infrastructure/adapter/out/persistence/` (por agregado) | ✅ JPA + mappers a mano (D-28), 7 tests de integración contra Postgres real |
| `infrastructure/adapter/in/rest/` (por agregado) | ✅ controllers + DTOs record — **sin autenticación real**, ver D-29 |
| Filtro JWT + caché Caffeine | 🔒 bloqueado por **B-2** (RS256 sin confirmar). Mientras tanto, header `X-Actor-Id` temporal |
| `api/UserSummary` + `UserSummaryFinder` | ✅ lo único que otros módulos pueden importar de `users` |
| `SupabaseAdminAuthPort` — adaptador real | 🔒 sin credenciales. Hoy `NoOpSupabaseAdminAuthAdapter` (solo loguea) |
| Flyway por módulo (`db/migration/users/`) | ⬜ sigue siendo un único `V1__baseline_renaser.sql` compartido — desviación consciente, no se separó todavía |
| `@RequiresPermission` + test de reflexión que audita la matriz | ⬜ bloqueado por R-2 (necesita el enum `Permission` primero) |

## 5. Modelo de seguridad y autenticación

### 5.1 Por qué Supabase Auth, no Keycloak (D-9)

Supabase **no ofrece Keycloak como servicio gestionado** — solo se integra con él como proveedor OIDC externo que vos operarías. No está en su lista de "Third-Party Auth" (esa es Clerk/Firebase/Auth0/Cognito/WorkOS). Se usa **Supabase Auth nativo**, que es lo que ya está en producción: no reduce trabajo meter Keycloak, lo multiplica (desplegar, mantener, respaldar, rotar claves, migrar usuarios reales existentes).

**Cuándo reconsiderarlo:** SSO corporativo con SAML, federación con LDAP/AD propio, o política de compliance que exija identidades en infraestructura propia. Ninguna señal de eso hoy.

### 5.2 Bloqueante B-2: el JWT tiene que ser RS256, no HS256

Supabase firma JWT de dos formas: **legacy HS256** (secreto simétrico — no sirve para Resource Server, ese secreto podría *emitir* tokens) o **asimétrico RS256/ECC/Ed25519** (claves públicas en `https://<project-ref>.supabase.co/auth/v1/jwks`, Spring Security solo *verifica*, nunca emite). Necesitamos RS256.

- [ ] Verificar en el dashboard si el proyecto está en legacy o asimétrico
- [ ] Si es legacy, migrar (Supabase soporta convivencia de ambos, sin downtime)
- [ ] Confirmar URL del JWKS y el `issuer` esperado
- [ ] B-4 (relacionado): auditar que no exista policy de RLS `INSERT` sobre `public.users` para `authenticated` — si existe, un solicitante pendiente se auto-aprueba

### 5.3 RBAC: el rol vive en nuestra tabla, no en el JWT (D-10)

Autenticación (¿quién sos?) la responde Supabase. Autorización (¿qué podés hacer?) la responde **el módulo `users` en Java**, nunca Supabase. Tres razones para no usar el Custom Access Token Hook de Supabase como fuente de verdad:

1. Un rol dentro del JWT queda viejo hasta que expira — un `SUSPENDED` tiene que cortar el acceso *al instante*, y eso solo se logra con la tabla + caché Caffeine invalidada por evento.
2. La matriz de permisos no cabe en un claim (`requireMentorScope` pregunta por una relación en la base, no un string).
3. Ya estaba decidido portar `requireRole`/`requireSelf`/`requireMentorScope` literal a Java.

### 5.4 Defensa en profundidad (D-11)

| # | Capa | Qué verifica |
|---|---|---|
| 1 | Supabase Auth | La identidad es real (JWT RS256) |
| 2 | Filtro Spring | Firma válida, no expirado |
| 3 | Filtro Spring | `status != SUSPENDED` (tabla + Caffeine) |
| 4 | `AccessGuard` | Rol / self / alcance de mentor |
| 5 | Dominio | Invariantes (`actor.can(MANAGE_ROLES)`) |
| 6 | RLS en Postgres | Última línea si todo lo anterior falla |

RLS se queda encendido aunque el backend autorice — no es redundante, asume que las otras cinco capas pueden fallar.

### 5.5 Autorización por endpoint (D-12)

El equivalente al API Gateway de microservicios es el `SecurityFilterChain` (grueso: público/privado) + `@RequiresPermission(...)` en el método del controller (qué permiso exige) + `AccessGuard` (fino: depende del recurso — `requireSelf`, `requireMentorScope`). La anotación va sobre el método, nunca en patrones de URL (`/api/v1/admin/**` es frágil y se desincroniza en silencio).

Un test de reflexión, todavía sin construir (bloqueado por R-2), recorre todos los `@RestController` y falla si algún endpoint no tiene `@RequiresPermission` ni `@PublicEndpoint` — imposible publicar una ruta sin protección por olvido.

### 5.6 Login social: Google, Apple, directo contra Supabase (D-18)

**El backend Java NO implementa OAuth.**

```
App RN  ──signInWithOAuth('google')──►  Supabase Auth  ──►  Google
   ▲                                          │
   └──────────  JWT firmado RS256  ◄──────────┘
   │
   └──Authorization: Bearer <JWT>──►  Backend Java  ──valida contra JWKS──►  listo
```

Agregar un proveedor nuevo (Facebook, etc.) es configuración en el dashboard de Supabase — cero código en Java. El backend nunca ve al proveedor social, ni `client_secret`, ni el `code` de OAuth.

| Proveedor | Estado |
|---|---|
| Google | Planificado |
| Apple | Planificado — **obligatorio en iOS** si se ofrece cualquier otro login social (hoy la app es Android, pero se configura antes de portar) |
| Facebook | Planificado — Meta exige revisión de app + verificación de negocio, iniciar el trámite temprano |

**Primer login social sin fila en `users` (R-5, resuelto 2026-08-24):** mismo flujo de `AccountRequest` que ya existe — se manda la solicitud, ADMIN/ALCHEMIST aprueba o rechaza y asigna el rol. Lo único que cambia frente al alta con email/contraseña: el email llega pre-verificado por Google/Apple (ellos ya lo validaron), así que el cliente no pide verificación de correo aparte — el campo `email` de `SubmitAccountRequestCommand` se llena con el que devuelve la sesión de Supabase, no con uno tipeado a mano. No hace falta ningún campo nuevo en `AccountRequest`: es una diferencia de qué hace el cliente antes de llamar a `submit()`, no del modelo de dominio.

### 5.7 Cookies vs Bearer — decisión revisada (D-19, actualizada por D-31)

**Diseño original (D-19, 2026-08-22):** dos `SecurityFilterChain` separadas — `/api/v1/**` (consumida por la app móvil) 100% stateless con Bearer, sin cookies, sin CSRF (no aplica: no hay credencial ambiente); un futuro panel web con cookie `httpOnly`+`Secure`+`SameSite` y CSRF obligatorio. Nunca mezclados, porque aceptar cookies en `/api/v1/**` reintroduce en la API móvil un riesgo (CSRF) que hoy no tiene.

> **D-31 (2026-08-24).** Decisión del equipo: usar cookies **también** para la API que consume la app móvil, no solo para un futuro panel web. Esto es un cambio consciente respecto a D-19, con dos costos que quedan aceptados a propósito, no ignorados:
> - **CSRF deja de ser "no aplica"**: con cookies, la API pasa a necesitar protección CSRF en todos los endpoints mutables, incluidos los que hoy solo llama el celular.
> - **React Native no maneja cookies como un navegador**: hace falta agregar un cookie jar explícito en el cliente (ej. `@react-native-cookies/cookies` o el manejo de cookies del cliente HTTP elegido) — no es automático como en un `<form>` de navegador.
>
> **Pendiente de definir cuando se retome (no bloquea lo demás):** qué librería de cookie jar usa el cliente RN, el esquema de CSRF token para móvil (double-submit cookie vs header sincronizado), y si el panel web futuro comparte la misma `SecurityFilterChain` o sigue separada. Sigue bloqueado en la práctica por B-2 (sin JWT/sesión real todavía no hay nada que envolver en una cookie).

### 5.8 Checklist del equipo de login, sin resolver todavía

- [ ] **B-2.** Claves JWT asimétricas (RS256) + JWKS
- [ ] **B-4.** Auditar RLS `INSERT` en `public.users`
- [ ] CAPTCHA (hCaptcha/Turnstile invisible) en signup/signin/reset — ataca el rate limit de `POST /account-requests` antes de llegar al backend
- [ ] MFA (TOTP) obligatorio para `ADMIN`, `MENTOR_LEAD`, `ALCHEMIST` (claim `aal2` de Supabase)
- [ ] Leaked password protection (HaveIBeenPwned) — requiere plan Pro
- [ ] Longitud mínima ≥ 12 + requisitos de caracteres
- [ ] Reautenticación para cambio de contraseña si la sesión tiene más de 24h
- [ ] Confirmar URL del JWKS y `issuer` del proyecto
- [ ] Esquema de CSRF + cookie jar para el cliente RN (nuevo, por D-31)

## 6. Casos de uso construidos

| Caso de uso | Estado | Nota |
|---|---|---|
| `SubmitAccountRequestUseCase` | ✅ | Comando sin campo `role`. Rate limit 60/hora por IP |
| `ApproveAccountRequestUseCase` | ✅ | Transacción única: `User` + `AccountRequest.APPROVED`. Compensación via `TransactionSynchronization` |
| `RejectAccountRequestUseCase` | ✅ | Libera el email en Supabase (anti-squatting) |
| `InviteAndCreateUserUseCase` | ✅ | Para MENTOR/ADMIN/ALCHEMIST y cohortes a mitad de programa |
| `GetMyProfileUseCase` / `UpdateMyProfileUseCase` | ✅ | |
| `UpdateUserRoleUseCase` | ✅ | Solo el camino simple: crea perfil de mentor vacío si corresponde. Migración de datos entre perfiles, fuera de alcance |
| `UpdateMentorProfileUseCase` | ✅ | Nivel/estado operativo requieren ADMIN/ALCHEMIST; bio la puede tocar el propio mentor |
| `AssignMentorToTraineeUseCase` | ✅ | Solo ADMIN/ALCHEMIST. **NO** actualiza ningún contador en `perfiles_mentor` — ver D-35 |
| `ActivateSelfTrackingUseCase` / `DeactivateSelfTrackingUseCase` | ✅ | Ver §6.bis |

## 6.bis Agregado `participante` — programa de 90 días (`participantes_programa`)

**Resuelto 2026-08-24 (D-33):** la fila de "❌ replanteado" en §4 quedó cerrada. `ParticipacionPrograma`
es el **4to agregado** de `users` — la tabla `participantes_programa` (baseline V1, ~línea 255) ya
existía, no se creó ninguna migración nueva. Igual que `MentorProfile`, no hereda de nada: es el
único agregado que modela esta inscripción.

### Por qué entra a `users` y no a otro módulo

`participantes_programa` es, ante todo, un dato de **identidad extendida** (¿en qué día del
programa está este usuario, con qué mentor, en qué célula?) — la misma familia de pregunta que
`users` ya responde para rol/estado. Los 6 módulos que hoy la leen (`points`, `phasecontracts`,
`habits`, `rocks`, `calendar`, `community`) la consultan siempre en función de "quién es este
usuario", nunca como parte de su propio dominio (`academy` también la lee, de forma opcional —
ver AC-04 en su propio puerto). Dejarla huérfana forzaba a cada uno a mantener su propia copia de
la traducción rol↔enum contra una tabla que no es suya.

### Qué se construyó

- `domain/model/participante/ParticipacionPrograma.java` — agregado puro. Invariantes: `diaPrograma`
  acotado a [0, 90] (`avanzarDia` nunca supera 90), `fechaGraduacionEsperada()` se **calcula al
  vuelo** (`fechaInicio.plusDays(90)`) y nunca se persiste — en Postgres es columna generada
  (`GENERATED ALWAYS AS (fecha_inicio + 90) STORED`), escribirla sería un error de Postgres, no
  solo una redundancia.
- `application/ports/in/participante/` — `ActivateSelfTrackingUseCase`, `DeactivateSelfTrackingUseCase`,
  `AssignMentorToTraineeUseCase`. Los tres comandos son self-validating y **ninguno acepta
  `diaPrograma`/`fase`/`coherenceScore` desde el cliente** — el servidor los fija siempre (§5.3.3).
- `application/ports/out/participante/` — `Load`/`Save`/`DeleteParticipacionProgramaPort` (el
  agregado propio) + `ConsultarResumenParticipacionPort` (la query compuesta `usuarios` LEFT JOIN
  `participantes_programa` que implementa el Finder público).
- `application/services/ParticipacionProgramaService` — implementa los 3 casos de uso **y**
  `users.api.ParticipacionProgramaFinder`.
- `infrastructure/adapter/{in/rest,out/persistence}/participante/` — controller + JPA + mapper a
  mano, mismo patrón que `mentorprofile`.
- **`users/api/FasePrograma`** (enum público, vocabulario inglés — `PHASE_1_REBIRTH`... — el mismo
  que ya consume la app móvil, D-36) y **`users/api/ParticipacionPrograma`** + **`ParticipacionProgramaFinder`**
  — el contrato público que `points`/`phasecontracts`/`habits`/`rocks`/`calendar`/`community`
  deberían consumir en vez de su propia query nativa. **Ninguno de los 6 fue refactorizado en esta
  sesión** — tenían agentes trabajando en paralelo; el refactor queda para una sesión donde el
  dueño del repo lo coordine explícitamente.

### D-34: el endpoint que ya existía — `/api/v1/mentor/activate-tracking`

La app móvil (`RenaserPlayStoreCopy/src/features/mentor/services/mentorService.ts`) ya llama a un
endpoint del backend para el opt-in/opt-out de "seguimiento personal" (Mis Hábitos/Mis Rocas
opcional, para staff — MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST, nunca TRAINEE) **antes** de caer a un
fallback que escribe directo contra Supabase (`trainee_profiles.upsert`/`.delete`) — el agujero de
seguridad descrito en el encargo (mass-assignment de `program_day`/`coherence_score`/`current_phase`
desde el cliente). El backend viejo (`Backend90dias/RenaserBack/src/app/api/v1/mentor/activate-tracking/route.ts`)
ya lo servía:

| Verbo | Contrato ya consumido por la app | Implementación Java |
|---|---|---|
| `GET /api/v1/mentor/activate-tracking` | `{ active: boolean }` | `ParticipacionProgramaFinder.deParticipante(actor).inscrito` |
| `POST /api/v1/mentor/activate-tracking` | 200 `{ traineeProfileId, programDay }` / 409 si ya existía | `ActivateSelfTrackingUseCase` — arranca en día 1, fase inicial, zona `America/Lima` (mismos defaults que `datosDeActivacion()` del backend viejo) |
| `DELETE /api/v1/mentor/activate-tracking` | 200 `{ deactivated: boolean }`, idempotente | `DeactivateSelfTrackingUseCase` — hard delete de la fila (cascada limpia hábitos/rocas), replica `deleteTraineeProfileForMentor` |

Este endpoint **construye el Java equivalente 1:1**, incluidos los roles permitidos
(`requireRole(['MENTOR','MENTOR_LEAD','ADMIN','ALCHEMIST'])` del backend viejo → guard clause en
`ParticipacionProgramaService`, no en el controller) y el código de estado 409 para una segunda
activación. La app publicada sigue funcionando sin cambios — deja de necesitar el fallback directo
a Supabase (que ya no debería dispararse, aunque no se retiró del cliente móvil: fuera de alcance
de este encargo, que era solo backend).

`PUT /api/v1/participants/{traineeId}/mentor` (`AssignMentorToTraineeUseCase`) es un endpoint
**nuevo**, sin equivalente literal en el backend viejo que se haya encontrado — se diseñó siguiendo
el mismo patrón (self-validating command, guard clause en el servicio, ADMIN/ALCHEMIST únicamente).

### D-35: `AssignMentorToTraineeUseCase` NO actualiza ningún contador de mentor

El encargo original pedía "actualizar `perfiles_mentor.total_aprendices` de forma atómica". Esa
columna **no existe** en el baseline actual: se llamaba `total_trainees_managed` y se eliminó a
propósito (P-17, comentario en `V1__baseline_renaser.sql` sobre `perfiles_mentor`) por ser derivable
con `COUNT(*) FROM participantes_programa WHERE mentor_id = ?` — exactamente lo que dice `D-25`.
Escribir una columna que el propio baseline decidió no tener sería inventar una regla de negocio
(CLAUDE.MD §0.6), así que `assignMentor` no la toca. Si en el futuro se necesita ese conteo
frecuentemente, la vía correcta es un índice sobre `participantes_programa.mentor_id` (ya existe:
`participantes_programa_mentor_idx`) + un `COUNT`, no una columna redundante.

### D-36: vocabulario público en inglés, columna en español

`users.api.FasePrograma` usa los mismos literales que la app móvil y el backend viejo
(`PHASE_1_REBIRTH`, `PHASE_2_DEVELOPMENT`, `PHASE_3_ALCHEMIST_WARRIOR`, `PHASE_4_ASCENSION` —
`TraineePhase` de Prisma) — no se inventaron. La columna Postgres `fase_programa` está en español
(`FASE_1_RENACER`...); la traducción explícita vive en `ParticipacionProgramaPersistenceMapper`
(dominio↔Postgres) y en `ConsultarResumenParticipacionPersistenceAdapter` (query compuesta↔api),
mismo patrón que `RolUsuarioJpa`/`UserPersistenceMapper`.

### Preguntas abiertas nuevas

| # | Pregunta | Estado |
|---|---|---|
| R-6 | Los 6 módulos consumidores (`points`/`phasecontracts`/`habits`/`rocks`/`calendar`/`community`) siguen con su propia query nativa contra `participantes_programa`/`usuarios` — `ParticipacionProgramaFinder` existe pero nadie lo consume todavía. ¿Quién y cuándo hace ese refactor? | ⬜ Abierto — explícitamente fuera de alcance de esta sesión (agentes en paralelo en `academy`/`habits`) |
| R-7 | La fila de `participantes_programa` para un TRAINEE real se sigue sin crear en `ApproveAccountRequestUseCase` (mencionado como pendiente ya en versiones anteriores de este documento). ¿Se crea ahí, o via un caso de uso separado del propio agregado `participante`? | ⬜ Abierto |

## 7. Decisiones específicas de `users` (registro, numeración compartida con `MODULOS_A_AVANZAR.md` §8)

| # | Decisión | Fecha |
|---|---|---|
| D-9 | Supabase Auth nativo. No Keycloak, no Third-Party Auth | 2026-08-22 |
| D-10 | El rol NO viaja en el JWT. Vive en nuestra tabla + caché Caffeine | 2026-08-22 |
| D-11 | RLS encendido aunque el backend Java autorice — última línea | 2026-08-22 |
| D-12 | Autorización por endpoint con `@RequiresPermission`, no con patrones de URL | 2026-08-22 |
| D-13 | Enum de rol + enum de permiso, matriz en el constructor. `@Enumerated(STRING)`, nunca `ORDINAL` | 2026-08-22 |
| D-14 | Son 5 roles. `MENTOR_LEAD` existe y tiene perfil propio | 2026-08-22 |
| D-15 | "Administrador" y "Operación" son el mismo `ADMIN` | 2026-08-22 |
| D-18 | Login social directo contra Supabase. El backend Java no implementa OAuth | 2026-08-22 |
| D-19 | Dos `SecurityFilterChain` separadas (móvil Bearer / web cookie) — **revisado por D-31** | 2026-08-22 |
| D-21 | RBAC sigue siendo enum Java (reafirma D-13), no tablas. El SQL de `roles`/`permisos`/`rol_permiso` quedó `[SUPERADO]` | 2026-08-24 |
| D-22 | `AccountRequest` y los perfiles Alchemist/Admin/Mentor se modelan 1:1 contra `BD_NUEVA_V1.sql` | 2026-08-24 |
| D-24 | ~~`RoleProfile`~~ revertido por D-25 | 2026-08-24 |
| D-25 | Tabla propia por rol SOLO si tiene más de un campo propio. Alchemist/Admin se fusionaron en `usuarios.bio`/`departamento`; `perfiles_lider_mentores` se eliminó sin reemplazo (placeholder sin confirmar); `perfiles_mentor` queda porque otras tablas (`celulas`, `participantes_programa`) lo referencian como FK — no es solo "tiene más campos", es un ancla relacional real | 2026-08-24 |
| D-27 | Estructura de capas adaptada a la convención de la cátedra: `application/ports/{in,out}`, `infrastructure/adapter/{in/rest,out/persistence}`, `application/services/` con una clase por agregado | 2026-08-24 |
| D-28 | Mappers de persistencia a mano, no MapStruct: los enums necesitan traducción español↔inglés, no es mapeo plano | 2026-08-24 |
| D-29 | `X-Actor-Id` como resolución de actor TEMPORAL en los controllers, hasta que B-2 se resuelva | 2026-08-24 |
| D-30 | Subcarpeta por agregado también en `ports/`, `persistence/`, `rest/` (no solo `domain/model/`) | 2026-08-24 |
| D-31 | Cookies también para la API móvil, no solo para un futuro panel web — revisa D-19. CSRF deja de ser "no aplica"; el cliente RN necesita cookie jar explícito | 2026-08-24 |
| D-32 | R-5 resuelto: primer login social sigue el flujo de `AccountRequest` con aprobación; el email llega pre-verificado por el proveedor, sin paso de verificación aparte | 2026-08-24 |
| D-33 | `ParticipacionPrograma` (`participantes_programa`) es el 4to agregado de `users`, no un módulo aparte — ver §6.bis | 2026-08-24 |
| D-34 | `/api/v1/mentor/activate-tracking` (GET/POST/DELETE) preservado tal cual la app móvil ya lo consume — reemplaza el fallback inseguro a Supabase directo (mass-assignment de `program_day`/`coherence_score`/`current_phase`) | 2026-08-24 |
| D-35 | `AssignMentorToTraineeUseCase` NO escribe ningún contador en `perfiles_mentor` — `total_trainees_managed` fue eliminado del baseline (P-17, derivable via `COUNT`). Escribirlo sería inventar una columna | 2026-08-24 |
| D-36 | `users.api.FasePrograma` usa vocabulario inglés (`PHASE_1_REBIRTH`...) igual que la app y el backend viejo; la columna Postgres `fase_programa` sigue en español — traducción explícita en el mapper, nunca en el dominio | 2026-08-24 |
