# Módulos a avanzar — plan de construcción de RenaserBack (Java)

**Fecha:** 2026-08-22
**Documentos hermanos:** `CLAUDE.MD` (reglas de trabajo §0, arquitectura y convenciones, aplica a todos los módulos) · `docs/MODULO_<NOMBRE>.md` (estado, decisiones y preguntas abiertas de cada módulo — hoy solo [`MODULO_USERS.md`](MODULO_USERS.md)) · [`BITACORA_ERRORES.md`](BITACORA_ERRORES.md) (base de conocimiento de errores — **buscar ahí antes de investigar un error**)
**Alcance:** en qué orden se construyen los 14 módulos, por qué ese orden, y cuándo cada uno está terminado.

> Este documento responde **qué construir y en qué orden**.
> `CLAUDE.MD` responde **cómo construirlo** (§5.1.2 carpetas, §5.4 DTOs/validación/logs, §5.4.10 checklist de PR).
> Leer §5.1.2 de `CLAUDE.MD` antes de tocar nada.

---

## 0. Autenticación y RBAC

Decisión de fondo (Supabase Auth, no Keycloak), modelo de RBAC, defensa en profundidad, login social y la discusión cookies-vs-Bearer viven ahora en **[`docs/MODULO_USERS.md`](MODULO_USERS.md) §5** — es donde se implementan, y donde estaban generando ediciones constantes a este archivo cada vez que algo cambiaba. Los bloqueantes **B-2** (claves RS256) y **B-4** (RLS de `INSERT`) siguen siendo bloqueantes globales, se listan también en §2 de este documento.

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
| ~~B-3~~ | ~~Valores reales de `TraineePhase` desconocidos~~ | ✅ **Resuelto 2026-08-24.** Los valores reales estaban en el `schema.prisma` de producción (`PHASE_1_REBIRTH`, `PHASE_2_DEVELOPMENT`, `PHASE_3_ALCHEMIST_WARRIOR`, `PHASE_4_ASCENSION`) y ya viven en el baseline como enum `fase_programa` (`FASE_1_RENACER`…`FASE_4_ASCENSION`). **`phasecontracts` queda desbloqueado** | — |
| B-4 | **RLS de `INSERT` en `public.users` sin auditar** (§5.3.6) | Agujero de escalada de privilegios que la migración no arregla sola | Login |
| B-5 | **Permisos de `MENTOR_LEAD` sin definir** (R-2, ver [`MODULO_USERS.md`](MODULO_USERS.md) §3). *R-1 y R-4 ya cerrados* | `MENTOR_LEAD` y su perfil ya existen; falta la matriz de `Permission` | Producto |

---

## 3. Orden de construcción por olas

### Ola 0 — Fundación ✅

Todo lo demás depende de esto. Sin esta ola no hay nada.

| Módulo | Estado | Qué entrega |
|---|---|---|
| `shared` | ✅ | `UserId`, `Clock` (puerto), `NotAuthorizedException`, `DomainEvent`. Declarado `@ApplicationModule(type = OPEN)` |
| **`users`** | ✅ | **Módulo 1. Ver detalle en §4.** Dominio + casos de uso + persistencia + REST construidos y auditados (2026-08-24). Queda pendiente el auth real (Resource Server contra Supabase JWT, B-2) — hoy usa `X-Actor-Id` temporal (D-29 en `MODULO_USERS.md`) |

### Ola 1 — Dominio puro, sin IA y sin infraestructura nueva ✅

Se eligen primero porque son **reglas de negocio casi puras**: máximo valor de tests unitarios, mínima superficie de riesgo, y sirven para calibrar la arquitectura con algo real antes de tocar lo complicado.

| Módulo | Depende de | Qué entrega | Nota |
|---|---|---|---|
| `points` | `users` | Puntos y ranking | ✅ 2026-08-24. **Casi todo `adapter/in/event`**: consume `HabitCompletedEvent`, `RockCompletedEvent`. Primer consumidor real de eventos Modulith. También expone `points.api.AjustarPuntosPort`, llamado síncronamente por `habits`/`rocks` (CLAUDE.MD §9.1) |
| `phasecontracts` | `users` | Pacto de Sangre, día de firma por fase | ✅ 2026-08-24 (B-3 resuelto). La regla *"en qué fase está"* vs *"cuándo le toca"* (`CONTRACT_UNLOCK_DAY`) es dominio puro, se tradujo 1:1 |
| ~~`traineeprofile`~~ | — | — | ❌ **Descartado como módulo propio (2026-08-22).** Ver abajo |

> **Resolución: `traineeprofile` NO es un módulo.** `CLAUDE.MD` §5 lo listaba como módulo aparte mientras §5.3.2 ponía `TraineeProfile` dentro de `users` — se contradecían. Queda **dentro de `users`**, por tres razones concretas:
> 1. **Es 1-a-1 con `User`**, igual que los otros cuatro perfiles. Sacar solo este rompe la simetría de §5.3.1 sin ganar nada.
> 2. **Se crea en la misma transacción** que el usuario (`ApproveAccountRequestUseCase` crea `User` + perfil + marca la solicitud, todo atómico). Partirlo en dos módulos Modulith convierte una `@Transactional` gratis en una coordinación entre módulos — el costo que §9.1 dice explícitamente que no se paga sin necesidad.
> 3. **`users` tendría que depender de `traineeprofile` para poder crear un usuario**, y `traineeprofile` de `users` para saber de quién es el perfil. Acoplamiento circular disfrazado de modularidad.
>
> **Total: 14 módulos, no 15.**

### Ola 2 — Núcleo del producto ✅

Es lo que el aprendiz usa todos los días. Se construye después de `points` para que los eventos ya tengan quien los escuche.

| Módulo | Depende de | Qué entrega | Riesgo |
|---|---|---|---|
| `habits` | `users`, `points` | Hábito, Santuario (phone-free), check-in diario | ✅ 2026-08-24. La máquina de estados `HabitStatus` (PENDING/IN_PROGRESS/COMPLETED/FAILED/EXPIRED) es dominio puro y de altísimo valor en tests. `PhoneFreeRun` va **plano en `domain/`**, no en subcarpeta |
| `rocks` | `users`, `points` | Rock, admin de rocks, modo Verdugo | ✅ 2026-08-24 |

Acá aparece el primer `@Scheduled` real (`ExpireTracksScheduler`), que reemplaza el cron actual — y con él la primera prueba de que un caso de uso se invoca desde dos adaptadores distintos (web y scheduler). Si algo de la lógica se hubiera colado en el controller, se rompe acá.

### Ola 3 — Contenido y comunicación 🔄

Módulos de menor acoplamiento entre sí; se pueden paralelizar entre varias personas.

| Módulo | Depende de | Nota |
|---|---|---|
| `notifications` | todos | ✅ 2026-08-24. **Casi todo `adapter/in/event`.** Consumidor de eventos de todo el sistema. Es el que validó que el outbox de Modulith funciona de punta a punta (ver D-37/D-38) |
| `support` | `users` | ✅ 2026-08-24 (construido junto con `users`/`points`/`phasecontracts`, fuera de orden — no bloqueaba nada) |
| `academy` | `users` | ⬜ Incluye Academia Adaptativa y Post Program |
| `community` | `users` | ⬜ Ver **Ola 4** para el feed en vivo |
| `calendar` | `users` | ⬜ |

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
| `evidence` | `users` (`habits`/`rocks` dependen DE `evidence`, no al revés — ver corrección abajo) | Puerto único `evidence.api.RegistrarEvidenciaPort` (registrar) + `ValidacionIAPort` interno (validar, hoy `NoOpValidacionIAAdapter`). El límite de reintentos (`intentos_ia` → `REVISION_MANUAL`) es **lógica de dominio**, vive en `evidence/domain` (`Evidencia.registrarIntentoFallido`), **no** en el adapter de Gemini |
| `onboarding` | `users`, `evidence` | Validación V90/6Ps. El patrón async + polling ya está peleado en producción: **se preserva tal cual**, solo cambia la implementación interna |
| `rag` / `renasia` | `users` | `PgVectorStore` sobre el mismo Postgres. Sin base de datos nueva |

**Corrección 2026-08-25 (dependencia real, no la de este plan original):** este plan decía que `evidence` "depende de" `habits`/`rocks`/`onboarding`. Es al revés — `evidence` es quien expone el puerto (`RegistrarEvidenciaPort`) que `habits`/`rocks` LLAMAN, así que la dependencia real es `habits`→`evidence` y `rocks`→`evidence`, no `evidence`→`habits`/`rocks`. Ya construido (2026-08-25, ver `docs/MODULO_EVIDENCE.md`): el esqueleto completo de `evidence` **sin integración de IA real** (dominio, máquina de estados de validación con fallback a revisión manual, persistencia, scheduler de cola, y el cierre de las deudas RK-2/D-H6 en `rocks`/`habits`). Lo que queda de Ola 5 sin construir: `onboarding`, `rag`/`renasia`, y la integración real de IA dentro de `evidence` (`ValidacionIAPort` con Gemini/Vertex en vez de `NoOpValidacionIAAdapter`) — por eso el marcador de la ola sigue en ⬜, no se marca completa.

**Contrato que no cambia:** `POST /validate` responde `202 {status:"processing"}` de inmediato; `GET /validate?recordingId=X` consulta estado. Es el patrón correcto también en Spring — nunca bloquear el hilo de request esperando a Gemini.

**Regla de logging crítica para esta ola:** `traceId` en MDC propagado a los `@Async`. Sin eso, un flujo async + polling es imposible de correlacionar en los logs (§5.4.9).

---

## 4. Módulo 1 — `users` (la raíz)

**Por qué es el primero:** todo módulo del sistema pregunta *"¿quién es este usuario y qué puede hacer?"*. `users` es la fuente de verdad de identidad, rol y estado. Nada se puede construir encima de algo que todavía no existe.

**Estado, roles, casos de uso, preguntas abiertas (R-1 a R-5) y el registro de decisiones específicas de este módulo viven en [`docs/MODULO_USERS.md`](MODULO_USERS.md).** Ese documento se actualiza en cada avance de `users`; este archivo ya no — así se evita reescribir el plan de los 14 módulos cada vez que algo cambia en uno solo.

Resumen de una línea para quien solo necesita el mapa general: `users` ya tiene dominio, casos de uso, persistencia (verificada contra Postgres real) y una API REST construidos; falta la autenticación real (bloqueada por B-2) y la matriz fina de permisos (bloqueada por R-2).

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

## 7. Repositorio: stack y cómo compilar

```
renaser-backend/
├── pom.xml                    Boot 4.1.1 · Java 25 · Spring AI 2.0.0 · Modulith 2.1.0 · MapStruct 1.6.3
├── mvnw / mvnw.cmd
├── docker-compose.yml         Postgres local (pgvector) para desarrollo y Testcontainers
├── CLAUDE.MD                  arquitectura y convenciones (CÓMO), aplica a todos los módulos
└── docs/
    ├── MODULOS_A_AVANZAR.md   este documento (QUÉ y en qué orden)
    ├── MODULO_USERS.md        estado y decisiones del módulo users
    └── BITACORA_ERRORES.md    errores y bugs encontrados
```

El detalle de qué hay dentro de `src/` por módulo vive en el doc de cada módulo (`docs/MODULO_<NOMBRE>.md`), no acá — evita que este archivo quede desactualizado apenas alguien agrega una clase.

```bash
./mvnw clean test
```

**Requisito:** Eclipse Temurin JDK **25.0.4.1 LTS**. `JAVA_HOME` a nivel usuario y máquina apuntando a `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`. Reinstalar en otra máquina Windows: `winget install --id EclipseAdoptium.Temurin.25.JDK`.

> **Nota para quien también trabaje en Android:** si `./gradlew` de un proyecto Android toma Java 25 desde la terminal y el Android Gradle Plugin lo rechaza, fijá el JDK en el `gradle.properties` de ese proyecto con `org.gradle.java.home` en vez de tocar el `JAVA_HOME` global.

---

## 8. Registro de decisiones

Una línea por decisión cerrada, para no rediscutirlas. **Solo decisiones globales, que aplican a más de un módulo.** Las decisiones específicas de un módulo (por ejemplo, todo lo de RBAC/auth y las D-9 a D-32 de `users`) van al doc de ese módulo — ver [`docs/MODULO_USERS.md`](MODULO_USERS.md) §7. La numeración `D-N` es un solo contador compartido entre este archivo y los docs de módulo, por eso hay saltos acá (D-9 a D-15, D-18, D-19, D-21, D-22, D-24, D-25, D-27 a D-32 están en `MODULO_USERS.md`).

| # | Decisión | Fecha | Dónde está el razonamiento |
|---|---|---|---|
| D-1 | **Monolito modular + hexagonal por módulo**, no microservicios | 2026-08-21 | `CLAUDE.MD` §4.1, §9 |
| D-2 | **Maven**, no Gradle. El build tool es irrelevante para hexagonal | 2026-08-22 | `CLAUDE.MD` §5.1 |
| D-3 | **Paquete base `com.renaser.os`** (el esqueleto usaba `com.renaser.renaser_backend`) | 2026-08-22 | `CLAUDE.MD` §5.1 |
| D-4 | ~~`domain/` plano: solo clases, sin subcarpetas~~ **matizado por D-26**: plano si es un solo agregado, subcarpeta por agregado si hay varios | 2026-08-22 | `CLAUDE.MD` §5.1.2 |
| D-5 | **MapStruct solo en persistencia**; mapeo a mano en la frontera web | 2026-08-22 | `CLAUDE.MD` §5.4.5 |
| D-6 | **Validación en tres niveles**: DTO web → comando self-validating → dominio | 2026-08-22 | `CLAUDE.MD` §5.4.3 |
| D-7 | **Controller tonto**: sin repositorios, sin `@Transactional`, sin reglas | 2026-08-22 | `CLAUDE.MD` §5.4.6 |
| D-8 | **`domain/` no loguea** | 2026-08-22 | `CLAUDE.MD` §5.4.9 |
| D-16 | **`traineeprofile` no es un módulo**: vive dentro de `users`. Total 14 módulos | 2026-08-22 | §3, Ola 1 |
| D-17 | **JDK: Eclipse Temurin 25 LTS.** `JAVA_HOME` de usuario movido del JBR de Android Studio a Temurin | 2026-08-22 | §7 |
| D-20 | **Reglas de trabajo obligatorias** en `CLAUDE.MD` §0: sin atribución de IA en commits, pruebas siempre, todo se documenta, bitácora de errores | 2026-08-22 | `CLAUDE.MD` §0 |
| D-23 | **`CLAUDE.MD` §5.4.5 corregido: Lombok SÍ se usa en `domain/`** (`@Getter`, `@AllArgsConstructor(PRIVATE)`, `@EqualsAndHashCode(of=...)`, `@Accessors(fluent=true)`), solo `@Data`/`@Setter`/`@ToString` sin acotar siguen prohibidos. Verificado en vivo contra `thombergs/buckpal` (`Account.java` usa exactamente ese patrón). La regla anterior ("prohibido en domain/") era más estricta que su propia fuente citada | 2026-08-24 | `CLAUDE.MD` §5.4.5 |
| D-33 | **La BD nueva es el baseline único y el análisis va primero.** `src/main/resources/db/migration/V1__baseline_renaser.sql` (90 tablas, esquema `renaser`, español) es la **fuente operativa** de la BD; el diseño de referencia y su auditoría viven en `docs/db/` (93 tablas — las 3 de diferencia son los perfiles fusionados/diferidos por D-25, y las tablas RBAC retenidas [SUPERADO] por D-21 hasta ajustar `roles_permitidos_curso`/`roles_destino_evento` en academy/calendar). Además, **cada módulo arranca con un "paso 0" de análisis del código viejo** de su feature (patrón `reminders.ts`: extraer reglas exactas antes de codificar, pedido de Luis/Ricardo) | 2026-08-24 | `docs/db/AUDITORIA_REDISENO_BD.md`, este §3 |
| D-36 | **Vocabulario del wire: el contrato de la app habla el idioma VIEJO (inglés)**. La BD nueva usa enums en español (`FASE_2_DESARROLLO`, `ABIERTO`…) pero los DTOs REST traducen a los literales que la app instalada ya consume (`PHASE_2_DEVELOPMENT`, `OPEN`…) — la traducción vive en la frontera web de cada módulo, nunca en dominio ni persistencia. `support` ya lo hace; es regla para todos | 2026-08-24 | Reportes Lote 1; `CLAUDE.MD` §8/§10 (no romper el contrato) |
| D-34 | **Storage de archivos: AWS S3 real** (elegido sobre Supabase-S3 y MinIO). Puerto transversal `AlmacenamientoPort` en `shared` + `S3StorageAdapter` (SDK v2); la app deja de subir directo (URLs prefirmadas vía API); columnas `bucket`+`ruta_storage` ya agnósticas; migración de objetos por módulo. Audios Espíritu siguen en Drive detrás de su puerto | 2026-08-24 | `docs/PLAN_DE_MODULOS.md` |
| D-35 | **Plan de desarrollo por módulo** en [`docs/PLAN_DE_MODULOS.md`](PLAN_DE_MODULOS.md): 13 módulos restantes + cierre de `users`, en lotes de 3 con agentes supervisados; al arrancar cada módulo se crea su `docs/MODULO_<NOMBRE>.md` con su sección como semilla | 2026-08-24 | `docs/PLAN_DE_MODULOS.md` |
| D-26 | **`domain/` corregido: subcarpeta por agregado, no siempre plano.** La regla anterior ("domain/ nunca subcarpetas") se basaba en una sola referencia (`buckpal`) que tiene un solo agregado. Verificado contra `citerus/dddsample-core` (múltiples agregados: cargo/handling/location/voyage, cada uno en su carpeta): cuando un módulo tiene **más de un agregado independiente** (identidad propia, ciclo de vida propio, repositorio propio), cada uno va en su subcarpeta. `users/domain/` tenía 3: `User`, `AccountRequest`, `MentorProfile` — pasaron a `user/`, `accountrequest/`, `mentorprofile/`. Sigue prohibido subdividir por capa (`domain/entities/`, etc.) | 2026-08-24 | `CLAUDE.MD` §5.1.2 |
| D-37 | **La tabla `event_publication` del outbox de Spring Modulith se crea a mano, vía Flyway.** `spring-modulith-starter-jpa` no trae ningún script de schema propio (verificado: el jar no contiene un solo `.sql`) — es responsabilidad del proyecto. Se agregó `V2__spring_modulith_event_publication.sql` con el DDL derivado del mapeo JPA real de `JpaEventPublication` (`ddl-auto=update` contra Testcontainers, volcado de `information_schema`, no inventado). Regla para cualquier módulo que publique eventos: la migración ya existe, no crearla de nuevo | 2026-08-24 | E-28 en `docs/BITACORA_ERRORES.md` |
| D-38 | **Los tests de integración en este repo terminan en `*Test.java`, nunca `*IT.java`.** El proyecto usa solo Surefire (fase `test`), sin Failsafe — Surefire ignora en silencio cualquier archivo que no matchee `**/*Test.java`/`**/Test*.java`/`**/*Tests.java`/`**/*TestCase.java`, y `mvn test` reporta éxito sin haber corrido nada. Pasó con el E2E del outbox de `notifications` (`NotificationsEventOutboxIT` → renombrado a `NotificationsEventOutboxTest`) | 2026-08-24 | E-27 en `docs/BITACORA_ERRORES.md` |
| D-39 | **`application.yaml` (main) excluye las auto-configuraciones de Spring AI Google GenAI** (chat/embedding/pgvector) hasta que `evidence`/`onboarding`/`rag` (Ola 5) se construyan con credenciales reales de Gemini/Vertex — sin esto la app no arranca fuera de los tests (que ya tenían el mismo exclude desde antes, ver E-15). Quitar el exclude es parte del trabajo de esos módulos, no antes | 2026-08-24 | E-15/E-28 en `docs/BITACORA_ERRORES.md`, `src/main/resources/application.yaml` |
| D-40 | ~~Convertir `roles_permitidos_curso.rol_id`/`roles_destino_evento.rol_id` a columna enum nativa~~ **ANULADA el mismo día, antes de escribir una sola línea de SQL.** Decisión del dueño del proyecto: **la BD queda cerrada en el baseline de 90 tablas y no se toca** — ni tablas nuevas, ni ALTER, ni seeds. Las dos junction se usan tal cual (`rol_id smallint REFERENCES roles(id)`); la traducción a `users.api.UserRole` la hace el adaptador de persistencia leyendo las 5 filas de `roles` (cacheadas). El pendiente que D-21 dejó abierto se resuelve así, en código, no en esquema. **Regla general que queda: ningún módulo crea migraciones Flyway**; los catálogos (`categorias_muro`, `niveles_membresia`, cursos…) llegan en la fase posterior de migración de datos desde producción, y todo módulo debe funcionar con su tabla vacía | 2026-08-24 | Este §8; instrucción directa de Luis/Ricardo |
| D-41 | **La app NO habla con Postgres: todo pasa por la API de Spring.** Decision de Luis/Ricardo (2026-08-24): depender de Supabase para datos es mala practica. Hoy la app hace **37 accesos directos** a tablas (+2 RPC), de los cuales **9 son ESCRITURAS** — eso pone la regla de negocio en el cliente y deja la unica defensa en manos de RLS (el agujero de B-4). Cada acceso directo se reemplaza por un endpoint. **Alcance acotado a acceso a datos**: Supabase sigue siendo el proveedor de identidad (emite el JWT que Spring valida como Resource Server, D-1 de `MODULO_USERS.md`) y el hosting del Postgres — ambas cosas se pueden cambiar despues sin rehacer este trabajo. El storage ya salio de Supabase por D-34 (AWS S3). **Regla para todo modulo nuevo:** si la app hace hoy un `.from(tabla)` o un `.rpc()` contra algo de tu modulo, tu modulo debe exponer el endpoint equivalente — se releva en el paso 0 (D-33) | 2026-08-24 | Este §8; auditoria de la app RN en `docs/MODULOS_A_AVANZAR.md` §9 |
| D-42 | **El panel admin en Next.js no frena nada.** Se sigue construyendo la API completa; el cambio del panel se hace al final, cuando todo este listo. No se monta proxy de transicion ni se coordina release por modulo mientras tanto | 2026-08-24 | Instruccion directa de Luis/Ricardo |
| D-43 | **El ranking general se calcula con consultas EN LOTE por modulo, no con un procedimiento almacenado.** El backend viejo resolvio este calculo con la funcion `general_ranking_scores()` (`prisma/migrations/general_ranking_scores_function.sql`) por un incidente real y medido: el calculo hacia **una consulta por aprendiz** para el progreso de cursos y con ~30 cuentas activas devolvia *"Too many database connections opened"* (verificado en vivo el 2026-08-12). El problema era el **N+1**, no el volumen — y se resuelve igual con consultas en lote, sin mover regla de negocio a SQL. Decision (Luis/Ricardo, 2026-08-24): cada modulo expone su porcentaje EN LOTE por su `api/` (`habits` → %habitos, `rocks` → %rocas, `academy` → %cursos) y `points` combina con los pesos **50% habitos + 35% rocas + 15% cursos** en su DOMINIO, testeable sin Postgres. Tres consultas totales, y el snapshot corre de noche. **Descartado a proposito:** portar la funcion a Flyway (la formula quedaria en SQL y una sola funcion leeria tablas de 4 modulos, saltandose todos sus limites de una vez) y meter el mismo SQL como query nativa en `points` (mismo problema de limites, sin la ventaja de estar en la base). La formula se porta LITERAL de `src/lib/coherence.ts::averageCompletionForDates` y `cursos/repository.ts::sumarProgresoCursos` — no se recalibra ningun criterio | 2026-08-24 | Este §8; cabecera de `general_ranking_scores_function.sql` en el repo viejo |
| D-44 | **Un modulo sin eventos previos SI puede sumar un DomainEvent nuevo cuando otro modulo lo necesita para reaccionar** (patron confirmado con `chat`, Ola 4): se agrego `users.api.UsuarioRegistradoEvent` (publicado desde `AccountRequestService.approve` y `UserAccountService.invite`) y `community.api.CelulaCreadaEvent` (publicado desde `CelulaService.crear`) para que `chat` pudiera auto-unir usuarios a la conversacion GLOBAL y auto-crear la conversacion de una celula, sin tocar ninguna otra logica de `users`/`community`. Regla para el proximo modulo que necesite lo mismo: agregar el evento al `api/` del modulo dueno del dato, publicarlo con `ApplicationEventPublisher` DENTRO de la misma transaccion que ya escribe el agregado, y actualizar solo el test unitario del servicio dueno para mockear el publisher nuevo — nunca reescribir la logica de negocio existente para "hacer lugar" al evento | 2026-08-25 | `docs/MODULO_CHAT.md` §2, §4 |
| D-45 | **Envio de correo real por SMTP, y el switch real/NoOp por propiedad explicita.** El flujo de alta quedo sin salida cuando se solto Supabase: solo existia `NoOpEnviarEmailAdapter`, asi que ni el codigo de verificacion ni el link de activacion llegaban a nadie fuera de la maquina del desarrollador. Se agrego `spring-boot-starter-mail` + `SmtpEnviarEmailAdapter` sobre `JavaMailSender` (autoconfigurado por `spring.mail.*`, Boot 4.1). **Cual adaptador se usa lo decide `renaser.email.proveedor` (`smtp` \| `noop`, por defecto `noop`)** y NO `@ConditionalOnBean(JavaMailSender.class)`: `@ConditionalOnBean` depende del orden de registro de beans y Spring solo lo garantiza dentro de autoconfiguracion, no en clases escaneadas. Con dos `@ConditionalOnProperty` simetricas sobre la misma propiedad siempre hay exactamente un `EnviarEmailPort`. La redaccion vive en `PlantillasEmail` (SRP): el adaptador solo transporta. Un fallo del proveedor lanza `EnvioEmailFallidoException` → **503**, nunca se traga — tragarlo dejaria cuentas aprobadas que nadie puede usar ni descubrir. **Pendiente del usuario:** credenciales SMTP reales (`SMTP_HOST`/`SMTP_USERNAME`/`SMTP_PASSWORD`) y dominio remitente | 2026-08-27 | [Spring Boot 4.1 — Sending Email](https://docs.spring.io/spring-boot/reference/io/email.html), [Spring Framework 7.0 — Email](https://docs.spring.io/spring-framework/reference/integration/email.html) |
| D-46 | **Los tres endpoints publicos de consulta de correo se portan del repo viejo, y el limite de tasa se mueve del borde a la aplicacion.** `POST /account-requests/check-email`, `/exists` y `/verify-email` existian en el contrato que la app ya consume pero no se habian migrado (devolvian 405). Se portan con sus reglas literales (AR-04/05/06): las dos primeras comparten un unico lookup (`ConsultarEmailRegistradoUseCase`) con dos respuestas de nombre invertido, y la tercera consulta MX por DNS (`ResolverMxPort` + `DnsResolverMxAdapter` sobre JNDI del JDK, sin dependencia nueva), con tres estados — nunca convierte un DNS caido en un "no". **Sobre enumeracion de correos:** se mantiene la decision de producto del 2026-08-01 (el dato ya se filtraba por el 409 de `POST /account-requests`; se acepta el coste a cambio de no hacer llenar seis campos para descubrir el duplicado al final). **Lo que si cambia:** el repo viejo concluyo que el limite por IP tenia que vivir en el borde (WAF) *porque en serverless no habia donde contar*; corriendo siempre-arriba y con Redis ya en el stack, vive en `ConsultaEmailService` reutilizando `LimitarSolicitudesResetPort` — que es lo que CLAUDE.MD §5.3.6 anticipaba. Umbral 120/hora por IP: mas alto que el de envio de codigos (alli cada intento cuesta un correo real; aca una lectura por indice UNIQUE) — **asuncion, no confirmada por producto**, mismo criterio que A-5 | 2026-08-27 | [OWASP — Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html), `service.ts::checkEmailAvailability` del repo viejo |

---

## 9. Fuentes verificadas

- [Supabase Docs — Login with Keycloak](https://supabase.com/docs/guides/auth/social-login/auth-keycloak) — Keycloak como proveedor OAuth/OIDC, servidor propio
- [Supabase Docs — Third-party auth overview](https://supabase.com/docs/guides/auth/third-party/overview) — la lista es Clerk, Firebase, Auth0, Cognito, WorkOS; Keycloak **no** está
- [Supabase — JWT Signing Keys](https://supabase.com/features/jwt-signing-keys) y [JSON Web Tokens](https://supabase.com/docs/guides/auth/jwts) — legacy HS256 vs asimétrico RS256/ECC/Ed25519, endpoint JWKS
- [Supabase Docs — Self-hosted auth keys](https://supabase.com/docs/guides/self-hosting/self-hosted-auth-keys)
- [Supabase Blog — Third-party auth](https://supabase.com/blog/third-party-auth-mfa-phone-send-hooks)
- `CLAUDE.MD` §5 (mapeo de dominio), §5.1.2 (carpetas), §5.2.1 (tiempo real), §5.3 (`users`), §5.4 (convenciones), §9 (cuándo separar), §10 (strangler fig)

---

## 9. Accesos directos de la app a Supabase — inventario a eliminar (D-41)

Relevado el 2026-08-24 sobre el repo de la app (`RenaserPlayStoreCopy`) con:

```bash
grep -rhoE '\.from\("[a-z_]+"' --include=*.ts --include=*.tsx .
grep -rhoE '\.rpc\("[a-z_]+"'  --include=*.ts --include=*.tsx .
```

**37 accesos directos a tablas + 2 RPC.** Mientras exista uno solo, la regla de negocio de esa operación vive en el cliente y la única defensa es RLS.

### 9.1 Escrituras — prioridad, son el riesgo real

| Tabla | Op | Archivo de la app | Módulo dueño | Endpoint |
|---|---|---|---|---|
| `leccion_progreso` | upsert | `src/services/cursos.ts:456` | `academy` | ✅ `POST /api/v1/lecciones/{id}/complete` |
| `leccion_progreso` | delete | `src/services/cursos.ts:465` | `academy` | ❌ falta el "descompletar" |
| `onboarding_answers` | upsert | `src/services/onboarding.ts:132` | `onboarding` | ❌ Ola 5 |
| `onboarding_media` | upsert | `src/services/onboarding.ts:503` | `onboarding` | ❌ Ola 5 |
| `onboarding_state` | upsert | `src/services/onboarding.ts:252` | `onboarding` | ❌ Ola 5 |
| `radar_entries` | insert | `src/services/radar.ts:111` | `habits` | ❌ falta |
| `testimonios` | insert | `TestimoniosPanel.tsx:776` | `community` | ✅ `POST /api/v1/testimonios` |
| `trainee_profiles` | upsert | `mentorService.ts:162` | `users` | ❌ falta |
| `trainee_profiles` | delete | `mentorService.ts:209` | `users` | ❌ falta |
| `users` | update | `src/services/users.ts:55` | `users` | ✅ `PATCH /api/v1/users/me` |

### 9.2 Lecturas y RPC

| Origen | Módulo | Endpoint |
|---|---|---|
| `.rpc("catalogo_cursos_bloqueados")` `src/services/cursos.ts:180` | `academy` | ❌ nunca existió en el backend viejo — la app lo llama directo |
| `.rpc("progreso_cursos")` `src/services/cursos.ts:152` | `academy` | ❌ ídem |
| `cursos` ×3, `curso_secciones` ×3, `lecciones` ×4, `leccion_recursos` ×1 | `academy` | ✅ cubiertos por `/cursos`, `/cursos/{id}/secciones`, `/lecciones/{id}` |
| `leccion_progreso` select ×2 | `academy` | ✅ vía el detalle de curso/lección |
| `radar_entries` select ×2 | `habits` | ❌ falta |
| `onboarding_answers` ×2, `onboarding_state` ×1 | `onboarding` | ❌ Ola 5 |
| `mentor_tickets` select | `support` | ✅ `GET /api/v1/tickets` |
| `users` select ×4, `trainee_profiles` select ×2 | `users` | ⚠️ revisar cobertura |
| `testimonios` select | `community` | ✅ `GET /api/v1/testimonios` |
| `routines` select `src/services/routines.ts:8` | — | ⚠️ **código muerto** (ver Anexo A de la auditoría de BD): la tabla no se usa |

### 9.3 Regla permanente

En el **paso 0** de cada módulo (D-33) hay que relevar, además del backend viejo, **qué le pega la app directo a Supabase de las tablas de ese módulo**, y exponer el endpoint equivalente. Un módulo no está terminado mientras su tabla siga siendo escrita desde el cliente.
