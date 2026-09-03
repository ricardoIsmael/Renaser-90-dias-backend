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
| R-7 | ~~La fila de `participantes_programa` para un TRAINEE real se sigue sin crear en `ApproveAccountRequestUseCase`~~ | ✅ **Resuelto — el documento estaba desactualizado.** Verificado en el código real (2026-08-26): `AccountRequestService.approve()` ya llama a `saveParticipacionProgramaPort.save(ParticipacionPrograma.inscribirTraineeAprobado(...))` en la MISMA transacción que crea el `User`, con comentario explícito citando la invariante del baseline. No quedaba pendiente nada que hacer aquí |

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
| D-52 | Hueco #1 no era "falta `TraineeProfile`": era falta de 3 columnas sin mapear en `ParticipacionPrograma` (`tipo_meta`/`nombre_reto_personal`/`programa_completado_en`) + 2 endpoints. `GetMyFullProfileUseCase` compone `User`+`ParticipacionPrograma` en la capa de aplicación (nunca en el controller) para no tocar `GetMyProfileUseCase`, que ya usa el flujo de login | 2026-08-26 |
| ~~D-53~~ | ~~Avatar genérico (gap #4): la confirmación resuelve y persiste una URL de LECTURA firmada (7 días) en vez de guardar solo la ruta~~ ❌ **Era un defecto, no una limitación — revertido 2026-08-31 por D-55 (`docs/MODULOS_A_AVANZAR.md`), ver E-57.** Una URL prefirmada vence: a los 7 días del último cambio de foto el avatar quedaba roto para siempre en todas las pantallas. Ahora el objeto es de lectura pública y la columna guarda su URL permanente | 2026-08-26, superado 2026-08-31 |
| D-54 | Baja de cuenta (gap #5): 14 días de gracia CONFIRMADOS (comentario de `usuarios.baja_solicitada_en` en el baseline + `DIAS_DE_GRACIA` del backend viejo coinciden), configurable vía `renaser.users.account-deletion.grace-period-days`. Acceso se conserva durante la gracia a propósito (permite cancelar). Purga = un solo `DeleteUserPort.deleteById` — las FK del baseline hacen el resto (CASCADE/SET NULL) | 2026-08-26 |

## 9. Paneles admin — staff, aprendices, solicitudes de cuenta (gaps #6/#7/#9)

**Fecha:** 2026-08-26. Construidos sobre un checkout compartido con otros agentes en paralelo
(`UserAccountService`, `ParticipacionProgramaService`, `LoadUserPort`, `User`,
`ParticipacionPrograma` fueron tocados por más de una tarea a la vez) — cada archivo se
releyó antes de editarlo para no pisar trabajo concurrente; no se encontró conflicto real,
solo adiciones en puntos distintos de las mismas clases.

- **`RequireAdminGuard`** (`application/services`, nuevo, package-private): gate ADMIN/ALCHEMIST
  FAIL-CLOSED — nunca lanza para un actor inexistente/suspendido (a diferencia de
  `RequireActiveUserGuard`, que sí lanza y por eso solo se usa donde el actor mismo es el
  recurso). Reusado por los tres gaps de esta sección y por el dashboard de onboarding (#8).
  Regla aplicada en los tres: si la operación apunta a un recurso por id (un staff, un
  aprendiz, una solicitud), ese recurso se carga PRIMERO (404 si no existe) y el gate de
  admin va DESPUÉS (docs/BITACORA_ERRORES.md E-42) — así un actor inválido siempre cae a
  403, nunca a un 404 con mensaje distinto que delataría si el recurso existía.
- **Gap #6 — staff**: `GET/POST /api/v1/admin/staff`, `PATCH /{id}/status`, `PUT /{id}`.
  `InviteAndCreateUserUseCase` ganó `inviteStaff(...)` (id generado por el propio backend
  vía `UUID.randomUUID()` — D-49/MODULO_AUTH.md: ya no hay Supabase de donde tomar un id
  externo para un alta admin-iniciada) que, además de crear el `User`, genera una
  contraseña temporal (`SecureRandom`, 20 bytes, Base64 URL-safe), la hashea con el mismo
  `PasswordEncoder` ya declarado en `SecurityConfig` y la envía con
  `EnviarEmailPort.enviarInvitacionStaff` — **método nuevo en el mismo puerto** que ya
  usaba el reset de contraseña (se reutilizó tal como pedía el encargo, no se creó un
  segundo puerto de email). Suspender a un staff invoca `CerrarTodasLasSesionesUseCase`
  (MODULO_AUTH.md §7.4: revocación en el acto, no en 30s).
- **Gap #7 — aprendices**: `GET /api/v1/admin/trainees` (paginado), `GET /{id}` (detalle:
  `User` + `ParticipacionPrograma` vía `ConsultarResumenParticipacionPort`), `PUT
  /{id}/program-day`. Nuevo método de dominio `ParticipacionPrograma.fijarDia(int, Clock)`:
  misma invariante [0, 90] que ya imponía `avanzarDia` (no es una regla de negocio nueva,
  es la misma cota aplicada también al piso) — a diferencia de `avanzarDia` (incrementa de
  a 1, el paso normal del reloj), permite fijar el día exacto que pide un operador humano.
- **Gap #9 — solicitudes de cuenta**: `GET/DELETE /api/v1/account-requests` (admin,
  paginado) + `GET /api/v1/account-requests/{id}/status` **PUBLIC_ENDPOINT**. Decisión de
  diseño (el encargo pedía elegir entre email o id y documentarlo): se resuelve por el
  `AccountRequestId` que el cliente ya recibió en el 202 de `submit`, NO por email — un
  UUID v4 no adivinable evita abrir una enumeración de qué emails tienen solicitud (probar
  a fuerza bruta "¿existe una solicitud para X@Y.com?"). `DeleteAccountRequestUseCase`
  permite borrar en cualquier estado (PENDING/APPROVED/REJECTED): es limpieza del registro
  de la solicitud, no afecta al `User` ya creado (la FK es de la solicitud hacia el
  usuario, nunca al revés) — **supuesto documentado, no confirmado con producto** si
  conviene restringir el borrado a estados ya decididos.
- **Paginación**: los tres listados (`ListStaffUseCase`, `ListTraineesUseCase`,
  `ListAccountRequestsUseCase`) usan `page`/`size` primitivos en el puerto — nunca
  `org.springframework.data.Pageable` cruzando la frontera `application` (aunque
  `ArchitectureTest` no lo prohíbe explícitamente, es el mismo criterio ya establecido por
  `calendar.LoadRecordatorioPort`: el puerto no conoce Spring Data, solo el adaptador
  arma el `PageRequest`).
- **Lo que NO se construyó**: tests `@WebMvcTest` para los 4 controllers nuevos
  (`StaffAdminController`, `TraineeAdminController`, `AccountRequestController` ampliado,
  `OnboardingDashboardController` en `onboarding`) — la cobertura de autorización negativa
  (CLAUDE.MD §0.3) se hizo a nivel de servicio (guard clauses, orden E-42, 403 vs 404),
  que es donde vive la lógica; falta la vuelta de integración HTTP completa si se quiere
  cerrar del todo.

## 10. Perfil de aprendiz enriquecido, avatar y baja de cuenta (gaps #1/#4/#5)

**Fecha:** 2026-08-26. Igual que la sección anterior, sobre un checkout compartido con los
agentes de los gaps #6/#7/#9 (`UserAccountService`, `ParticipacionProgramaService`,
`LoadUserPort`, `SpringDataUserRepository`, `UserPersistenceAdapter`, `UserPersistenceMapper`,
`UserController`, `User`, `ParticipacionPrograma` fueron tocados por más de una tarea a la
vez) — cada archivo se releyó antes de editarlo; los cambios de esta sección son adiciones
en puntos distintos, sin conflicto real con el trabajo paralelo.

### Gap #1 — investigado primero, no asumido

El encargo pedía verificar si `docs/PLAN_INTEGRACION_FRONTEND.md` ("no existe `TraineeProfile`
como dominio") seguía vigente contra D-33/§6.bis (`ParticipacionPrograma` YA es el 4to
agregado). **D-33 tenía razón; el documento de gaps estaba desactualizado.** Lo que de
verdad faltaba, verificado contra `services/profile.ts`/`useProgramStartDate.ts` del
frontend real:

- Tres columnas del baseline sin mapear en `ParticipacionProgramaJpaEntity`: `tipo_meta`,
  `nombre_reto_personal`, `programa_completado_en` — existían en la tabla desde el
  principio, el comentario del propio archivo decía "quedan fuera a propósito" porque
  ningún caso de uso los necesitaba todavía.
- `POST /api/v1/users/me` no traía ningún dato de `ParticipacionPrograma`.
- No existía forma de editar `personalChallengeName` (U-05 de CLAUDE.MD §5.3.3, ya
  documentado como caso de uso pendiente, nunca construido).

**Qué se construyó:**

- `users.domain.model.participante.TipoMeta` (dominio, vocabulario inglés PHYSICAL/SALES/FEAR)
  + `TipoMetaJpa` (espejo español FISICA/VENTAS/MIEDO) — mismo patrón D-36 que `FasePrograma`.
  Sin setter de dominio todavía: ningún caso de uso de `users` lo escribe (lo hará el
  onboarding de "Meta Maestra", gap #3, ya cerrado en otro módulo pero sin tocar esta
  columna).
- `ParticipacionPrograma.renombrarRetoPersonal(String, Clock)` — el único de los 3 campos
  nuevos con escritura propia, vía `UpdateTraineeProfileUseCase` (U-05):
  `PATCH /api/v1/users/me/trainee-profile`, self-only, `null` = "no cambiar" (no borra).
  Vive en `ParticipacionProgramaController`, no en `UserController`, porque el campo es del
  4to agregado.
- `GetMyFullProfileUseCase` (nuevo, en `UserAccountService`): compone `User` +
  `ParticipacionPrograma` para `POST /api/v1/users/me`. **Deliberadamente no se tocó
  `GetMyProfileUseCase`** — ese puerto lo usa también el flujo de login
  (`AutenticacionController`/`IniciarSesionUseCase`) y cambiar su forma de retorno hubiera
  arrastrado esos otros llamadores sin necesidad; es la composición que exige CLAUDE.MD
  §5.4.6 resuelta como caso de uso nuevo, no como el controller orquestando dos.
- `traineeProfile` aparece en la respuesta si el actor tiene fila en `participantes_programa`
  (cualquier rol — incluye staff con "seguimiento personal" activado, D-34), no solo TRAINEE.
- **No implementado a propósito:** el enmascaramiento "ALCHEMIST con `traineeProfile` ve
  `role: TRAINEE`" que sí hacía el backend viejo (`route.ts` de `/users/me`) — es una regla
  real pero no estaba en el alcance del encargo (que hablaba de campos faltantes, no de
  esta lógica de mascarado) y tocarla arrastra decisiones sobre cómo el resto de la app
  (navegación, gates) trata el rol. Queda como pregunta abierta, no como bug.

### Gap #4 — avatar genérico

`SolicitarUrlAvatarUseCase` (`POST /api/v1/users/me/avatar/upload-url`) +
`ConfirmarAvatarUseCase` (`PATCH /api/v1/users/me/avatar`), clase propia `AvatarService`
(no `UserAccountService`: es un concepto distinto, sube y confirma un archivo). Mismo bucket
compartido `renaser-files` que `rocks`/`habits`/`calendar` (D-34), ruta `avatares/{userId}`.

#### Corregido 2026-08-31 — el avatar guarda una URL PERMANENTE (D-55, `docs/BITACORA_ERRORES.md` E-57)

La versión original de este gap persistía una URL de lectura **prefirmada** (validez 7 días,
el máximo que permite SigV4) en `usuarios.avatar_url`. Eso era un defecto, no una limitación:
**a los 7 días del último cambio de foto la firma vence y nadie la vuelve a firmar nunca**, y
el mismo string se sirve en el muro, los comentarios, el chat, los miembros de célula, los
testimonios y el panel admin — todos lo reciben vía `users.api.UserSummary`. No se notaba
porque el adaptador por defecto (`NoOpAlmacenamientoAdapter`) devuelve `about:blank`: estaba
escrito para romperse el día que se activara S3, una semana después, sin ningún error en el log.

**Qué hace ahora:**

- El objeto del avatar es de **lectura pública** y la columna guarda su **URL permanente** — el
  nombre `avatar_url` ahora es correcto. `AlmacenamientoPort` ganó `urlPublica(ruta)`, que
  `S3AlmacenamientoAdapter` compone con `S3Utilities` (bucket + región, sin llamar a AWS).
- La **subida no cambió**: sigue siendo una URL prefirmada de 10 minutos. Lo que hace público
  al objeto es la política del bucket, no el código. Escribir en el bucket nunca es público.
- `User.changeAvatar` **rechaza** cualquier valor con marcas de SigV4
  (`X-Amz-Signature`/`X-Amz-Credential`/`X-Amz-Expires`), y `V13` agrega el `CHECK` equivalente
  en `usuarios` y en `testimonios`. Persistir una prefirmada volvió a ser difícil, en las dos capas.
- `confirmar` recalcula la ruta desde el actor y **no confía en la del body**: nadie puede
  publicar como avatar propio un objeto ajeno del bucket.
- **`chat` y `community` no cambiaron**: reciben la URL ya lista dentro de `UserSummary`.

**Por qué URL pública y no "firmar al leer".** Firmar en cada respuesta también arregla el
vencimiento, y es lo correcto para evidencia, contratos, adjuntos y audios — ahí el vencimiento
*es* la medida de seguridad. Para el avatar no: la URL cambiaría en cada respuesta y eso invalida
el caché de imagen del cliente, así que un muro con 20 avatares volvería a descargar las 20 fotos
en cada pantallazo. El avatar es el activo de menor sensibilidad y el que más se repite por
respuesta; es el patrón de GitHub/Slack. Decisión del dueño del proyecto, que aceptó
explícitamente que la ruta sea adivinable.

> ⚠️ **Requisito de infraestructura — sin esto la foto no se ve.** El bucket tiene que permitir
> `s3:GetObject` **anónimo** sobre el prefijo `avatares/*`. S3 bloquea el acceso público por
> defecto (*Block Public Access*), así que hay que desactivar esa opción para el bucket y agregar
> la bucket policy correspondiente. Sin ese cambio la URL que devuelve el backend es correcta
> pero responde **403**. Los permisos IAM del backend **no cambian**: siguen siendo `GetObject`,
> `PutObject` y `DeleteObject` sobre el bucket (D-54). Ver D-55 en `docs/MODULOS_A_AVANZAR.md`.

**Migración de datos (`V13__avatar_url_permanente.sql`).** No cambia la estructura, solo repara
valores: corta la query string de las filas prefirmadas (`split_part(avatar_url, '?', 1)` — exacto
y no heurístico, porque en SigV4 todo lo que caduca vive después del `?`) y pone `NULL` en las que
tenían el marcador `about:blank#pendiente-s3/...` del `NoOp`, que no son reparables ni tienen un
objeto detrás (cuando el `NoOp` estaba activo, la URL de subida también era un marcador, así que
el cliente nunca llegó a subir nada). Las `NULL` no se tocan: siguen significando "no tiene avatar".
Lo mismo para `testimonios.avatar_url`, que copia el avatar del autor al promover y heredaba el
mismo vencimiento.

**Verificado:** `./mvnw clean test` → **1747/1747 en verde** (2026-08-31). `V13` la aplica Flyway
contra el Postgres real de Testcontainers en cada build, así que los `CHECK` nuevos y los `UPDATE`
de reparación se ejecutan de verdad, no solo en el despliegue. **Lo que estas pruebas NO cubren, a
propósito:** que el bucket esté efectivamente abierto para lectura anónima en `avatares/*` — eso
exige la política de S3 del recuadro de arriba y se verifica en el despliegue, no acá.

### Gap #5 — baja de cuenta autogestionada

Portado 1:1 de `features/account-deletion` del backend viejo (Next.js) — código real
consultado, no reconstruido de memoria:

- **`EstadoBajaCuenta`** (`users/domain/model/user`): lógica pura del plazo de gracia,
  puerto de `plazo.ts`. `diasRestantes` redondea HACIA ARRIBA (30 minutos antes de purgar
  sigue siendo "1 día", no "0"), `null` si no hay solicitud o si ya venció (el cron la
  purgará en su próxima pasada).
- **`User.solicitarBaja`/`cancelarBaja`/`bajaPendiente`**: idempotente (repetir la solicitud
  NO reinicia el contador — si reiniciara, pulsar dos veces regalaría días de gracia de más
  sin que el usuario lo entienda). `bajaSolicitadaEn` es una columna aparte de
  `UserStatus`, a propósito: NO corta `hasAccess()` — sin acceso durante la gracia no habría
  forma de arrepentirse y cancelar.
- **14 días de gracia — CONFIRMADO, no un supuesto.** El comentario de la columna
  `usuarios.baja_solicitada_en` en el baseline SQL ("cron purga a los 14 días, política
  actual") coincide exactamente con `DIAS_DE_GRACIA` del backend viejo
  (`features/account-deletion/plazo.ts`). Configurable sin recompilar
  (`renaser.users.account-deletion.grace-period-days`, default 14) por si el producto
  decide cambiarlo, pero el valor de partida está confirmado por dos fuentes independientes.
- **`GET/POST/DELETE /api/v1/users/me/account-deletion`** — mismo estilo de recurso que
  `/api/v1/mentor/activate-tracking` (D-34). `POST` exige `{"confirmacion":"ELIMINAR"}`
  (backend viejo, `PALABRA_DE_CONFIRMACION`) — no sustituye a la reautenticación (la hace
  el cliente contra su propia sesión antes de llamar), pero cierra la puerta a que un
  reintento automático o un request suelto borren una cuenta. `DELETE` cancela (self-only).
- **Purga real (`AccountDeletionService.purgeExpired` + `PurgarCuentasBajaScheduler`,
  04:15 UTC diario)**: a diferencia del backend viejo (Prisma + Supabase Auth: 26 tablas +
  Storage + Auth Supabase borrados a mano en `borrarCuenta`, porque el ORM no cascadea y
  encima había una tabla — `account_requests` — completamente fuera del grafo de Prisma),
  acá **alcanza con un solo `DeleteUserPort.deleteById`**: las ~30 FK contra `usuarios` en
  el baseline SQL son `ON DELETE CASCADE` (o `SET NULL` en las de auditoría, que
  deliberadamente sobreviven a la baja) y desde D-49 el propio backend es dueño de
  credenciales/identidades (ya no hay una fila de Supabase Auth aparte que borrar). El
  `UNIQUE` de `usuarios.email` libera el correo apenas la fila se va — no hace falta
  borrar nada más a mano.
- Cada cuenta se purga en su propio intento (`try`/`catch` por id): un fallo puntual no
  deja sin purgar a las demás, mismo criterio que `purgarBajasVencidas` del backend viejo.
- **Demostrado con Testcontainers, no solo con mocks** (`AccountDeletionIntegrationTest`):
  crear cuenta → pedir baja → purgar (con el reloj adelantado 15 días) → el email queda
  libre → registrar una cuenta nueva con el mismo email funciona sin violar el `UNIQUE`.
  Es la prueba explícita de que el bug viejo (borrar una cuenta no liberaba el email) no
  se repite acá.
- **No implementado a propósito** (fuera del alcance literal del encargo, que hablaba de
  autogestión): el borrado inmediato sin gracia que el backend viejo ofrecía a un
  admin desde el panel de aprendices/mentores (`eliminarCuentaComoAdmin`/
  `eliminarMentorComoAdmin`), ni el flujo de baja pública sin sesión por enlace de correo
  (`solicitarEnlacePublico`/`confirmarBajaPublica`) que exige Google Play para quien ya
  desinstaló la app. Quedan como trabajo futuro si el producto los pide.

### Preguntas abiertas de esta sección

| # | Pregunta | Estado |
|---|---|---|
| R-8 | ¿Se quiere replicar el enmascaramiento "ALCHEMIST con `traineeProfile` → `role: TRAINEE`" del backend viejo en `POST /api/v1/users/me`? | ⬜ Abierto — no implementado a propósito, ver gap #1 arriba |
| R-9 | ¿Hace falta baja de cuenta SIN gracia para el panel admin, y/o baja pública sin sesión por enlace de correo (exigida por Google Play para quien desinstaló la app)? | ⬜ Abierto — fuera del alcance literal de este encargo (autogestión) |

## 11. Vista vertical del panel: los hábitos de UN aprendiz (2026-08-31)

**Dónde vive el código: en `habits`, no acá.** Se documenta en este archivo porque cierra un
hueco del panel admin de aprendices descrito en §9 (gap #7), y porque es la pieza que le
faltaba a `GET /api/v1/admin/trainees/{id}` para que un operador pueda abrir a una persona y
ver algo más que su perfil.

**El hueco, medido:** el admin de `habits` (`/api/v1/admin/habits`) es de **catálogo** — qué
hábitos existen para todos. El admin de `users` (`/api/v1/admin/trainees`) muestra **perfil,
rol y estado**. Nadie cruzaba las dos cosas: era imposible ver el horario real de un aprendiz,
sus hábitos propios, su cuota de cambios gastada ni su plan de desbloqueo.

**Qué se construyó:** `GET /api/v1/admin/trainees/{traineeId}/habits` — contrato completo en
[`docs/api/CONTRATO_DIA_A_DIA.md`](api/CONTRATO_DIA_A_DIA.md) §1.7. Por cada hábito activo del
aprendiz: identidad (título del catálogo + renombre propio si lo hay), ámbito
(sistema/personal), horario vigente (su preferencia pisando al catálogo), cambio de horario
programado con su fecha efectiva, recordatorio, día de desbloqueo (con si lo eligió la persona
o el relleno automático), día semanal elegido, y la cuota semanal de reacomodos usados/restantes.

**Por qué el código está en `habits` y no en `users`:** el dato es de hábitos (seis tablas de
ese módulo) y la autorización de admin de hábitos ya vive ahí (`HabitoAdminGuard`, reusado tal
cual). Ponerlo en `users` habría obligado a una llamada entre módulos para leer tablas ajenas.
La ruta cuelga de `/admin/trainees/` porque es la pantalla del operador — Spring rutea por
path, no por módulo, así que los dos controllers conviven sin conflicto.

**Rendimiento — primer uso real de `JdbcClient` en el repo (CLAUDE.MD §11).** La grilla se lee
por **proyección**, no por entidad: una sola consulta con dos `LATERAL` cruza `habitos`,
`renombres_habito`, `horarios_habito`, `preferencias_horario`, `cambios_horario_pendientes`,
`desbloqueos_habito` y `dias_semanales_habito`, trayendo exactamente las columnas de la
respuesta. El endpoint hace **4 consultas fijas** en total (actor, participante, proyección,
cuota), ninguna dentro de un bucle — un aprendiz con 40 hábitos cuesta lo mismo que uno con 3.
Sin paginación a propósito: el tope de la colección son los hábitos activos de una persona, no
crece con el uso (justificado en el javadoc de `LeerHabitosPersonalizadosPort`).

**Reglas reusadas, no reinventadas:** la precedencia preferencia-sobre-catálogo es la de
`RegistroService`/`TracksDelDiaProyeccionService`; la cuota se cuenta con el mismo
`HistorialCambioHorarioPort` que la cobra en `PreferenciaHorarioService` (sin modificarlo); el
ancla de semana de calendario es la de `EleccionDiaSemanalService`.

**Autorización:** ADMIN/ALCHEMIST activos. Rol sin permiso → 403; actor `SUSPENDED` → 403
aunque su token sea válido. El aprendiz mirado **sí** puede estar suspendido: un operador tiene
que poder auditar justamente a quien acaba de suspender. Orden E-42 respetado (el aprendiz se
carga primero, el gate de admin va después).

## Auditoría de arquitectura (2026-08-28) — agente automático

Auditoría de solo lectura de `src/main/java/com/renaser/os/users/`. No se corrió `./mvnw` (fuera de alcance del encargo). 9 controllers REST confirmados (`VerificacionEmailController`, `LogrosController`, `UserController`, `ParticipacionProgramaController`, `TraineeAdminController`, `StaffAdminController`, `MentorProfileController`, `AutenticacionController`, `AccountRequestController`), ~209 archivos `.java` en el módulo.

### 1. Patrón de seguridad `@ActorAutenticado` vs `X-Actor-Id` — sin violaciones

A diferencia de `community.TestimonioController` (corregido en esta misma sesión), **los 9 controllers de `users` usan `@ActorAutenticado UserId actor`** en todos los endpoints que necesitan un actor. `grep` de `@RequestHeader.*X-Actor-Id` sobre todo el módulo da 0 resultados de código real — las únicas apariciones de la cadena `X-Actor-Id` son comentarios Javadoc que documentan que `ActorAutenticadoArgumentResolver` (en `shared.web.security`) usa ese header como **respaldo interno** cuando no hay sesión, nunca como lectura directa en el controller. Esto es exactamente el mecanismo correcto (sesión primero, header como puente temporal de migración, docs/MODULO_AUTH.md §8), no el antipatrón.

Los tres endpoints sin `@ActorAutenticado` son legítimamente públicos y están documentados como tales en el propio código:
- `VerificacionEmailController` (`/send`, `/confirm`) — prueba de control de correo ANTES de tener cuenta, no hay actor posible.
- `AccountRequestController.checkEmail/exists/verifyEmail/submit` — formulario de alta público.
- `AccountRequestController.consultarEstado` (`GET /{id}/status`) — el solicitante todavía no tiene `User`; se resuelve por el `AccountRequestId` no adivinable, decisión documentada en D-… (gap #9).

**Conclusión: sin hallazgos de seguridad en este patrón.**

### 2. Pureza de `domain/`

`grep` de `import org.springframework.*` / `import jakarta.persistence.*` sobre `src/main/java/com/renaser/os/users/domain/` da 0 resultados. `grep` de `import com.renaser.os.users.(application|infrastructure)` sobre el mismo árbol también da 0 resultados. Las 14 clases de dominio (`User`, `Email`, `Credencial`, `EstadoBajaCuenta`, `AccountRequest`+`Id`+`Status`, `MentorProfile`+`Level`+`OperationalStatus`, `ParticipacionPrograma`, `TipoMeta`, `IdentidadExterna`, `ProveedorIdentidad`) están limpias: sin Spring, sin JPA, sin setters públicos (Lombok `@Getter`/`@Accessors(fluent=true)`/`@AllArgsConstructor(PRIVATE)`/`@EqualsAndHashCode(of=id)` o `record` para value objects), validación en factory methods, `Clock` inyectado nunca `Instant.now()` directo.

**Desviación real, no un bug pero sí una inconsistencia con lo documentado:** `User.java` (`domain/model/user/User.java:3-4`) y `ParticipacionPrograma.java` (`domain/model/participante/ParticipacionPrograma.java:5`) importan `com.renaser.os.users.api.UserRole`, `com.renaser.os.users.api.UserStatus` y `com.renaser.os.users.api.FasePrograma` — es decir, **`UserRole`/`UserStatus`/`FasePrograma` viven enteros en `users/api/`, no en `domain/model/user/`** como dice §4 de este mismo documento ("`domain/model/user/` (`User`, `Email`, `UserRole`, `UserStatus`)") y como muestra el árbol de ejemplo de CLAUDE.MD §5.1. El dominio termina dependiendo de su propia `api/` en vez de al revés (lo habitual es que `api/` exponga una proyección *derivada* del dominio, como sí hace `UserSummary`). No viola la regla dura de §5.1.2 (domain no importando Spring/JPA/adapters), y `ArchitectureTest` no lo prohíbe porque sigue siendo el mismo módulo — pero es inconsistente con el resto del propio módulo: `ProveedorIdentidad` (identidad externa) y `TipoMeta` (participante) sí están correctamente en `domain/`, mientras que `UserRole`/`UserStatus`/`FasePrograma` no. Vale una decisión explícita (¿se documenta como patrón intencional — "estos 3 enums son vocabulario compartido con otros módulos desde el día uno, por eso viven en `api/`" — o se corrige la tabla de §4 que hoy es fácticamente incorrecta?).

### 3. Regla de subcarpeta por agregado (CLAUDE.MD §5.1.2)

Correctamente aplicada. Carpetas de `domain/model/`: `user/` (agregado `User` + sus value objects `Email`/`Credencial`/`EstadoBajaCuenta`, todos sin sentido sin `User`), `accountrequest/` (agregado propio, su propia identidad `AccountRequestId`), `mentorprofile/` (agregado propio, único con tabla propia por D-25), `participante/` (agregado `ParticipacionPrograma` + su value object `TipoMeta`), `identidadexterna/` (agregado `IdentidadExterna`, 1:N real con `User`, identidad natural `(proveedor, sujeto)` — el propio Javadoc del archivo explica por qué es agregado propio y no parte de `User`). Cinco agregados reales, cinco carpetas — ninguna subdivide por capa dentro de `domain/`, tal como exige la regla.

### 4. Controllers "tontos"

Los 9 controllers son delgados: ningún `@Transactional`, ningún `if` de negocio, ninguna inyección de `Repository`/puerto `out`. Cada método deserializa, invoca **un** caso de uso vía su interfaz `in`, y mapea la salida. El endpoint más largo (`AutenticacionController.loginSocial`, con el `switch` sellado sobre `ResultadoLoginSocial`) tiene 10 líneas de cuerpo — el propio Javadoc explica por qué el `switch` vive ahí (es la única decisión de "¿corresponde sesión?", forzada por el compilador vía `sealed interface`) y no es lógica de negocio sino despacho de transporte.

### 5. Excepciones de dominio sin HTTP

`grep` de `@ResponseStatus`/`ResponseStatusException` sobre todo `users/` da 0 resultados. Las excepciones de negocio (`NotAuthorizedException`, `RateLimitExceededException`, `TokenVerificacionEmailInvalidoException`, `CredencialesInvalidasException`, `IdentidadProveedorInvalidaException`, etc.) viven en `shared.domain`, sin conocimiento de status codes. `shared.web.GlobalExceptionHandler` es el único traductor a HTTP (`FORBIDDEN`, `UNAUTHORIZED`, `BAD_REQUEST`, `TOO_MANY_REQUESTS`, `CONFLICT`, `NOT_FOUND`, según la excepción) — exactamente el diseño de §5.4.4.

### 6. Lombok

`@Data`/`@Setter`/`@NoArgsConstructor` aparecen únicamente en las 4 clases `@Entity` de persistencia (`UserJpaEntity`, `AccountRequestJpaEntity`, `MentorProfileJpaEntity`, `ParticipacionProgramaJpaEntity`) — cada una con javadoc explicando por qué `@Data` es seguro ahí (sin relaciones `@ManyToOne`/`@OneToMany` perezosas que romper). `domain/` usa el patrón fluent con constructor privado descrito en §5.4.5, o `record` para los value objects sin comportamiento posterior a la construcción (`Email`, `Credencial`, `IdentidadExterna`, `AccountRequestId`, `EstadoBajaCuenta`). Sin violaciones.

### 7. Nombres prohibidos

`grep` de clases `*Util`/`*Helper`/`*Manager`/`*Processor`/`*Data`/`*Info` sueltas sobre el módulo: sin resultados.

### 8. Techos duros de tamaño (§5.4.8)

La mayoría de las clases está dentro de rango. Dos excepciones reales:

- **`application/services/ParticipacionProgramaService.java` — 300 líneas (justo en el techo) y ~19 métodos públicos** (contra el techo de 10 de §5.4.8). Implementa 11 interfaces de caso de uso a la vez (`ActivateSelfTrackingUseCase`, `DeactivateSelfTrackingUseCase`, `ConsultarSelfTrackingUseCase`, `AssignMentorToTraineeUseCase`, `ParticipacionProgramaFinder`, `ListTraineesUseCase`, `GetTraineeDetailUseCase`, `SetTraineeProgramDayUseCase`, `UpdateTraineeProfileUseCase`, `AssignTraineeCellUseCase`, `RemoveTraineeCellUseCase`, `AsignacionCelulaPort`). Es el patrón D-27 documentado ("una clase por agregado", no "una clase por caso de uso") y cada método sigue siendo corto y de una sola responsabilidad — pero el conteo de métodos públicos excede el techo explícito de §5.4.8 independientemente del patrón que lo justifique. Vale una decisión explícita: ¿el techo de 10 métodos públicos aplica también a las clases "una por agregado" de D-27, o D-27 es una excepción documentada a ese punto en particular?
- **`infrastructure/adapter/out/persistence/participante/ConsultarResumenParticipacionPersistenceAdapter.java` — 304 líneas**, por encima del techo de 300. La densidad de complejidad real es baja (son 9 constantes de SQL nativo + sus métodos de mapeo fila→objeto), pero excede el límite literal. Candidato a partir en dos clases (ej. separar las queries de "resumen de un participante" de las de "panel admin de aprendices", que son casos de uso distintos) si se quiere cumplir la letra de la regla.

Ningún método individual observado supera las 40 líneas, y no se encontraron niveles de anidamiento mayores a 2.

### 9. Logging

`grep` de `Logger`/`log\.` sobre `domain/` da 0 resultados — el dominio nunca loguea, consistente con §5.4.9. El logging de PII/credenciales está tratado con cuidado explícito, mejor que el mínimo exigido: `NoOpEnviarEmailAdapter` y `SmtpEnviarEmailAdapter` tienen comentarios línea por línea citando CLAUDE.MD §5.4.9 y loguean solo la **longitud** de un link/token/contraseña temporal, nunca su valor ni el email destinatario (ej. `NoOpEnviarEmailAdapter.java:48-50`, `SmtpEnviarEmailAdapter.java:92-94`). Los adaptadores OAuth (`GoogleIdentidadAdapter`, `AppleIdentidadAdapter`, `FacebookIdentidadAdapter`) solo loguean la excepción al fallar el intercambio de código, nunca el `code`/`id_token` en sí. `AccountDeletionService` loguea el `UserId` (UUID, no PII) al fallar una purga individual — aceptable.

### 10. Otras observaciones

- **Mitigación de ataque de temporización en login** (`AutenticacionService.java:23-24,43-52`): cuando el email no existe o la cuenta no tiene contraseña, se compara igual contra un hash BCrypt señuelo fijo, para que `passwordEncoder.matches` haga el mismo trabajo criptográfico siempre — evita que el tiempo de respuesta delate si un email está registrado. No estaba pedido explícitamente en el checklist de auditoría, pero es una buena práctica de seguridad que vale destacar.
- **Orden E-42 (recurso antes que gate de admin) aplicado consistentemente**: verificado en `StaffAdminService`, `ParticipacionProgramaService.obtener/fijarDia/assign/remove` y `AccountRequestService.eliminar` — en todos, el recurso por id se carga primero (404 si no existe) y `RequireAdminGuard`/`RequireActiveUserGuard` se invoca después (403 si el actor no califica), evitando que el código de estado delate si el recurso existía.
- **Frontera de módulo respetada**: `package-info.java` de `users` lleva `@ApplicationModule`; `users/api/package-info.java` lleva `@NamedInterface("api")`. `grep` de `import com.renaser.os.users.(domain|application|infrastructure)` sobre el resto del código (`com/renaser/os/**`, excluyendo el propio `users/`) da 0 resultados — ningún otro módulo importa los paquetes internos de `users`. `ArchitectureTest` cubre esta regla con `ApplicationModules.verify()`.
- **Mapeo en la frontera de persistencia**: Two-Way Mapping a mano (no MapStruct) en los 4 agregados con tabla propia, documentado explícitamente como decisión (D-28) por la traducción de enums español↔inglés — consistente con §5.4.1/§5.4.5.
- El enum `Permission` y `@RequiresPermission` siguen sin construirse (bloqueado por R-2, ya documentado en §3 de este archivo) — la autorización fina de este módulo se resuelve hoy con guard clauses dentro de los servicios (`RequireAdminGuard`, `RequireActiveUserGuard`, `User.canManageRoles()`), no con la anotación declarativa de CLAUDE.MD §5.3.4/§5.3.5. No es un hallazgo nuevo, pero se confirma que el mecanismo de reemplazo (guard clauses) está aplicado de forma consistente en los ~13 casos de uso que lo necesitan.
