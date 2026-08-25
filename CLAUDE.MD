# Migración de RenaserBack a Java 25 / Spring Boot 4.1 / Spring AI 2.0

**Fecha:** 2026-08-21
**Alcance:** recomendación de arquitectura para migrar el backend de *Renaser OS* (hoy Next.js 15 + Prisma 6 + Supabase) a Java 25 + Spring Boot 4.1 + Spring AI 2.0, consumido por una app **React Native** (RenaserPlayStore), con requisito de latencia muy baja.

> **Documento hermano:** [`docs/MODULOS_A_AVANZAR.md`](docs/MODULOS_A_AVANZAR.md) — **qué** construir y en qué orden (olas, estado de cada módulo, bloqueantes, decisión de auth Supabase vs Keycloak).
> Este documento responde **cómo** construirlo. Si sos nuevo en el repo: leé **§0** (reglas de trabajo) y **§5.1.2** (qué es cada carpeta) antes que nada.

---

## 0. Reglas de trabajo (obligatorias, para Claude Code y para el equipo)

Estas reglas **tienen prioridad sobre cualquier comportamiento por defecto**. Se aplican en cada tarea, sin excepción y sin que haga falta recordarlas.

### 0.1 Commits

- **Nunca agregar `Co-Authored-By: Claude`, `Generated with Claude Code`, ni ninguna atribución a la IA** en el mensaje de commit, en el cuerpo del PR ni en el código. Los commits van a nombre de la persona que los hace, y punto.
- Mensajes en español, imperativo, describiendo **qué cambia y por qué**, no qué archivos se tocaron.
- No commitear ni pushear salvo que se pida explícitamente. Si se pide y estamos en la rama principal, crear rama primero.

### 0.2 Pruebas: siempre, no cuando conviene

- **Toda tarea que toque código termina con `./mvnw clean test` ejecutado y en verde.** No se reporta algo como terminado sin haber corrido las pruebas.
- **Pruebas unitarias** de dominio: obligatorias para toda regla de negocio nueva. Sin Spring, sin base de datos.
- **Pruebas de integración** con Testcontainers: obligatorias para todo adaptador nuevo (persistencia, web, IA).
- **`ArchitectureTest` debe pasar siempre.** Si una regla de arquitectura estorba, se discute la regla — no se borra el test ni se le agrega una excepción sin dejarlo documentado.
- **Si una prueba falla, se reporta el fallo con su salida.** Nunca se declara terminado algo que no pasó las pruebas, ni se omite mencionar un test roto.
- Si algo quedó sin probar (por falta de credenciales, de Docker, de un dato), **se dice explícitamente qué quedó sin verificar y por qué**.

### 0.3 Pruebas de seguridad

Además de las funcionales, para todo endpoint nuevo:

- **Test de autorización negativa**: cada endpoint protegido debe tener una prueba que verifique que un rol *sin* permiso recibe **403**, y que un usuario `SUSPENDED` recibe 403 aunque su token sea válido.
- **Test de que el rol no se puede inyectar**: verificar que mandar `role` en el body de un alta pública no lo cambia (§5.3.3).
- **Test de reflexión** que falle si un endpoint no declara `@RequiresPermission` ni `@PublicEndpoint`.
- Antes de cada release: revisar dependencias con vulnerabilidades conocidas.

### 0.4 Documentar lo que se avanza

- **Todo avance se documenta en el mismo cambio**, no después. Si se cierra un bloqueante, se marca resuelto; si se toma una decisión, se agrega al registro de decisiones de `docs/MODULOS_A_AVANZAR.md` §8.
- **Los documentos son fuente de verdad y no pueden contradecirse.** Si un cambio deja una sección vieja, se corrige en el momento.
- **No romper lo que ya funciona.** Los documentos y el código estable se editan quirúrgicamente; nada de reescrituras que descarten trabajo previo.

### 0.5 Bitácora de errores y bugs — nuestra base de conocimiento

**Todo error, bug o comportamiento inesperado se registra en [`docs/BITACORA_ERRORES.md`](docs/BITACORA_ERRORES.md), incluso si se resolvió en dos minutos.**

El objetivo es concreto: **si vuelve a pasar, se resuelve en un minuto en vez de media hora.** Se registra:

- El síntoma exacto (mensaje de error literal, no parafraseado)
- La causa real
- La solución aplicada
- Cómo evitar que vuelva a pasar

Se registra **aunque el error haya sido de configuración, del entorno o "una tontería"** — esos son justamente los que se repiten.

### 0.6 Alcance

- Hacer lo que se pide, completo. Si algo del alcance queda afuera, decirlo explícitamente y por qué.
- Ante una duda que cambia el resultado, preguntar. Ante una duda menor, decidir, avanzar y avisar qué se asumió.
- **No inventar reglas de negocio.** Valores de enums, fases del programa, permisos por rol: si no están confirmados, se preguntan. Nunca se rellenan con supuestos.

---

## 1. Resumen ejecutivo

- **Arquitectura recomendada: Monolito Modular con Hexagonal por módulo**, usando **Spring Modulith**, desplegado como un único proceso JVM. No microservicios, salvo para el subsistema de IA si su perfil de carga diverge (ver §9).
- Es la opción que mejor sirve al objetivo de latencia: dentro de un mismo proceso una llamada entre módulos cuesta nanosegundos; entre microservicios cuesta milisegundos de red aunque estén en el mismo clúster. Partir el sistema en servicios *añade* latencia, no la reduce.
- El objetivo literal de **"<1 ms"** no es alcanzable de punta a punta para un cliente móvil sobre HTTP (la vuelta de red sola ya son varios ms). Es alcanzable, y es donde hay que apuntar, para el **tiempo de procesamiento dentro del servidor** en operaciones cacheadas o de cómputo puro. Ver el desglose realista en §3.
- Java 25 (LTS) + Virtual Threads/Structured Concurrency maduros son el motivo técnico real para migrar desde Node/Next si el driver es rendimiento bajo alta concurrencia I/O-bound (llamadas a Postgres, Gemini, Resend, Drive) — no la sintaxis ni el tipado.
- El dominio actual (Users, Habits, Rocks, Community, Academy, Chat, Calendar, Notifications, Onboarding+IA, RAG, Renasia, Support/Tickets, Phase Contracts, Points/Ranking) mapea de forma natural a **14 módulos** hexagonales. Ver §5.

---

## 2. Diagnóstico del estado actual

Repo `renaserlab/RenaserBack` (revisado en vivo):

| Capa | Hoy |
|---|---|
| Framework | Next.js 15 (App Router, API routes) |
| Lenguaje | TypeScript, React 19 |
| ORM / datos | Prisma 6 sobre PostgreSQL (Supabase) |
| Auth | Supabase Auth |
| IA | Gemini directo (validación de evidencia, onboarding V90, RAG) |
| Integraciones | Google Drive API (catálogo de audios), Resend (email), xlsx (exportes) |
| Tests | Vitest |
| Cliente | App móvil React Native ("RenaserPlayStore") consumiendo `/api/v1/...` |

Puntos relevantes que ya resolvieron bien y que la migración **debe preservar**, no rehacer desde cero:
- El patrón async con polling para validación por IA (`{status:'processing'}` + `GET .../validate?recordingId=X`) — es exactamente el patrón correcto en Spring también (no bloquear el hilo de request esperando a Gemini).
- Reintentos acotados con fallback a revisión manual (`ia_attempts`, `pending_review`).
- Separación de "en qué fase está" vs "cuándo le toca" (`CONTRACT_UNLOCK_DAY`) — es lógica de dominio pura, se traduce 1:1 a un *domain service* Java sin tocar el modelo de datos.

**Implicación clave para la migración:** esto no es un rewrite ciego. El contrato de API (`docs/API_CONTRACT.md`, `docs/MOBILE_HABITS_API.md`) ya es consumido por una app en producción. La migración debe ser *strangler fig* preservando el contrato, no un big-bang.

---

## 3. El objetivo de latencia: qué es realista

Antes de diseñar para "<1 ms" hay que separar dónde se gasta el tiempo, porque la arquitectura correcta depende de esto:

| Tramo | Orden de magnitud | ¿Lo controla el backend? |
|---|---|---|
| Red móvil (4G/5G) ida y vuelta | 20–120 ms | No |
| TLS handshake / HTTP overhead | 1–5 ms (con keep-alive, ~0) | Parcialmente |
| Balanceador / gateway | 0.1–2 ms | Sí |
| **Procesamiento en el servidor** | **objetivo real** | **Sí** |
| Query a Postgres (índice, sin N+1) | 0.3–3 ms | Sí |
| Lectura desde caché en proceso (Caffeine) | **~0.001–0.01 ms** | Sí |
| Lectura desde Redis (otro host) | 0.5–2 ms | Sí |
| Llamada a Gemini / IA generativa | 500–3000 ms | No (por diseño, va async) |

**Conclusión práctica:** "<1 ms" es un objetivo válido y alcanzable para el **tiempo de CPU del servidor en el hot path** (resolver un habit del día, calcular puntos, chequear permisos, servir un feed cacheado) si esos datos viven en memoria (Caffeine) o se resuelven con una sola query indexada. No es alcanzable, ni con Java ni con nada, como tiempo total percibido por el celular del aprendiz. Los SLO que recomiendo fijar y sí son defendibles:

- **p50 de procesamiento en servidor:** < 5 ms
- **p99 de procesamiento en servidor:** < 30–50 ms (excluye red del cliente)
- **Operaciones servidas 100% desde caché en proceso:** < 1 ms de CPU — este es el único lugar donde "<1 ms" es literal y correcto usarlo como meta.
- **Llamadas a IA (Gemini/Vertex):** nunca en el hilo de request; siempre async + polling o webhook, como ya hacen hoy.

Diseñar toda la arquitectura alrededor de estos números (y no del "<1 ms" a secas) evita over-engineering costoso (ej. reescribir todo en Rust, o partir en microservicios "por si acaso") que no movería la aguja porque el cuello de botella real es la red del celular, no el servidor.

---

## 4. Arquitectura recomendada: Monolito Modular + Hexagonal

### 4.1 Por qué no microservicios

Con "son varios módulos" la tentación es pensar en microservicios uno-a-uno con cada feature. Para este caso concreto es la elección equivocada:

- **Latencia:** una llamada in-process (mismo JVM) es órdenes de magnitud más rápida que una llamada de red entre servicios, incluso dentro del mismo clúster (service mesh, DNS, (de)serialización JSON, TLS interno). Si el objetivo es latencia baja, cada frontera de red que se agrega entre módulos que hoy se llaman entre sí (ej. Habits necesita Users y Points en la misma request) es presupuesto de latencia que se tira.
- **Equipo y tamaño real:** el dominio es rico pero es un solo producto con (a juzgar por el repo) un equipo chico. Microservicios reales cobran su costo en versionado de contratos, despliegues coordinados, trazabilidad distribuida y consistencia eventual — costo que no se paga solo, y que en Node ya evitaban corriendo todo en un Next.js monolítico.
- **Transacciones:** varias operaciones de dominio (completar un hábito → sumar puntos → verificar avance de fase → disparar notificación) son más simples y consistentes con una transacción de base de datos local que con saga/compensación entre servicios.

### 4.2 Por qué sí Hexagonal (Ports & Adapters) dentro de cada módulo

Hexagonal no compite con "monolito vs microservicios": es ortogonal. Da la separación domain/aplicación vs infraestructura que:
- Permite testear la lógica de dominio (reglas de fases, puntos, expiración de hábitos) sin base de datos ni HTTP.
- Aísla el reemplazo de infraestructura (Prisma→JPA, Gemini directo→Spring AI `ChatClient`, Google Drive→lo que sea) detrás de puertos, que es exactamente lo que se necesita **durante** la migración.
- Facilita que, si el día de mañana un módulo sí necesita salir como servicio propio (ver §9), sea *extraer un hexágono ya aislado*, no una cirugía a ciegas sobre código entrelazado.

### 4.3 El patrón concreto: Spring Modulith

[Spring Modulith](https://spring.io/projects/spring-modulith) es el proyecto oficial de Spring para monolitos modulares: hace cumplir en tiempo de compilación/test que los módulos no se acoplan por accidente (sin `import` cruzado entre paquetes internos), da un mecanismo de eventos de dominio in-process (para desacoplar módulos sin ir a un message broker) y genera documentación de arquitectura (diagramas C4) desde el propio código.

```
com.renaser.os
├── users/            (hexágono: domain, application, adapter-in-web, adapter-out-jpa)
├── habits/           (incluye Santuario / phone-free)
├── rocks/            (incluye modo Verdugo, admin)
├── community/
├── academy/          (incluye Academia Adaptativa)
├── calendar/
├── chat/
├── notifications/
├── onboarding/       (incluye validación IA V90)
├── evidence/         (validación de evidencia por IA, compartida por habits/rocks)
├── rag/               (Renasia + FEATURE_RAG)
├── support/          (tickets)
├── points/           (ranking, puntos)
├── phasecontracts/   (Pacto de Sangre)
└── shared-kernel/    (tipos de dominio compartidos: UserId, permisos, reloj, eventos base)
```

Cada paquete de nivel superior es un **módulo Modulith** = un hexágono:

```
habits/
├── domain/            → entidades y reglas puras (HabitTrack, expiración, DayType…)
├── application/        → casos de uso (CompleteHabitUseCase, ExpirePendingTracksUseCase)
├── port/
│   ├── in/             → interfaces que exponen los casos de uso
│   └── out/            → interfaces que la app necesita de infraestructura (HabitRepository, ClockPort)
├── adapter/
│   ├── web/            → @RestController, DTOs, mapeo a/desde HTTP
│   ├── persistence/     → @Repository JPA, entidades de persistencia (separadas del dominio si hace falta)
│   └── event/          → listeners/publishers de eventos Modulith
└── HabitsModuleConfig
```

Regla dura: **un módulo solo puede llamar a la API pública de otro módulo** (paquete `habits.api` o similar, marcado `@NamedInterface` en Modulith); todo lo demás es `package-private`. Spring Modulith rompe el build de CI si esto se viola. Esto es lo que evita que el monolito degenere en "big ball of mud" con el tiempo — el riesgo real de los monolitos, no la latencia.

### 4.4 Comunicación entre módulos

- **Síncrona, in-process, vía puerto de aplicación** para lecturas que necesita otro módulo en la misma request (ej. `Habits` pregunta a `Users` la fase del aprendiz para filtrar el catálogo del día). Es una llamada a método Java normal: < 1 microsegundo.
- **Eventos de dominio (Spring Modulith Events)** para todo lo que hoy dispararía efectos secundarios: `HabitCompletedEvent` → `points` suma puntos, `notifications` encola push, `phasecontracts` revisa si desbloquea firma. Los eventos se persisten en una tabla de outbox (Modulith lo da out-of-the-box) → **at-least-once** incluso si el proceso se cae entre el commit y el efecto secundario. Esto es un upgrade real respecto a lo que es fácil de lograr en Next.js API routes.
- **Nunca HTTP entre módulos** dentro del mismo deploy — si dos módulos sienten la necesidad de hablarse por HTTP, es una señal de que en realidad quieren ser dos servicios (ver §9), no un atajo dentro del monolito.

---

## 5. Mapeo de dominio → módulos

Basado en `docs/FEATURE_*.md` del repo actual:

| Módulo Java | Features Next.js de origen | Notas de diseño |
|---|---|---|
| `users` | Users, Account Requests, Permissions | Fuente de verdad de roles (Alchemist/Admin/Mentor/MentorLead/Trainee) |
| `habits` | Habit, Santuario (phone-free), Daily Check-in | Máquina de estados `HabitStatus` es dominio puro, alto valor en tests unitarios |
| `rocks` | Rock, Rocks Admin, Modo Verdugo | |
| `evidence` | Evidence AI Validation | Compartido por `habits`, `rocks`, `onboarding` — puerto único `EvidenceValidationPort` |
| `onboarding` | Onboarding, Onboarding AI Validation (V90/6Ps) | Ya usa patrón async+polling; se preserva |
| `academy` | Academy, Academia Adaptativa, Post Program | |
| `community` | Community | |
| `calendar` | Calendar | |
| `chat` | Chat | Candidato a WebSocket/SSE nativo de Spring, no polling |
| `notifications` | Notifications | Consumidor de eventos de todos los demás módulos |
| `rag` / `renasia` | RAG, Renasia, Shadow Mirror | Ver §7, usa Spring AI VectorStore |
| `support` | Support, Tickets | |
| `points` | Points/Ranking | Consumidor de eventos (`HabitCompletedEvent`, `RockCompletedEvent`…) |
| `phasecontracts` | Phase Contracts (Pacto de Sangre) | Reglas de "día de firma por fase" → dominio puro |
| ~~`trainee-profile`~~ | Trainee Profile | ❌ **No es módulo propio.** `TraineeProfile` vive dentro de `users`, igual que los otros 4 perfiles — ver `docs/MODULOS_A_AVANZAR.md` §3 (D-16) |

`shared-kernel` contiene solo lo que de verdad es transversal: `UserId`, `TraineePhase`, reloj (`Clock` inyectable — crítico para testear reglas de "día N del programa" sin esperar al calendario real), y el contrato de eventos base.

---

## 5.1 Estructura de carpetas del proyecto

Un solo módulo de build — **no** un módulo de build por bounded context. Spring Modulith hace cumplir los límites por *paquete*, no por artefacto separado; partir en artefactos es exactamente el tipo de sobre-ingeniería que en §4.1 dijimos evitar.

> **Decidido: Maven** (`pom.xml`, con el wrapper `mvnw`). El proyecto ya está creado así. Paquete base: **`com.renaser.os`**.
>
> **¿La arquitectura hexagonal exige Gradle? No.** Es una confusión frecuente y vale dejarla cerrada:
> - Hexagonal es una regla sobre **hacia dónde apuntan las dependencias entre paquetes**. El build tool no participa: Maven y Gradle producen el mismo JAR, con las mismas clases, en los mismos paquetes.
> - Quien hace cumplir los límites es **Spring Modulith + ArchUnit, en un test** (`ArchitectureTest`), no el build tool.
> - **De dónde viene la idea:** el repo de referencia del libro de Hombergs (`thombergs/buckpal`) usa Gradle — verificado. Es una elección incidental del autor, no un requisito del patrón. Y el libro sí muestra **artefactos de build separados** (un módulo Gradle por capa) como *una* forma de imponer límites — pero eso es justo lo que §4.1 y esta sección rechazan por sobre-ingeniería para nuestro caso.
> - Lo único que Maven te pide a cambio: la configuración de `annotationProcessorPaths` para Lombok + MapStruct (§5.4.5), que ya está resuelta en el `pom.xml`.

```
renaser-backend/
├── pom.xml
├── mvnw / mvnw.cmd
├── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── java/com/renaser/os/
│   │   │   │
│   │   │   ├── RenaserOsApplication.java         ← @SpringBootApplication (único main)
│   │   │   │
│   │   │   ├── shared/                            ← shared-kernel (visible a todos, sin lógica de negocio propia)
│   │   │   │   ├── domain/
│   │   │   │   │   ├── UserId.java                (value object)
│   │   │   │   │   ├── TraineePhase.java
│   │   │   │   │   └── Clock.java                 (puerto — reloj inyectable, clave para tests)
│   │   │   │   ├── event/
│   │   │   │   │   └── DomainEvent.java           (marker interface para eventos Modulith)
│   │   │   │   └── web/
│   │   │   │       ├── ApiErrorResponse.java
│   │   │   │       └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   ├── users/                             ← un módulo = un hexágono. Se repite esta forma en cada uno.
│   │   │   │   ├── package-info.java              (@ApplicationModule — declara el módulo ante Modulith)
│   │   │   │   ├── UsersModuleConfig.java
│   │   │   │   ├── domain/                        ← reglas puras, sin Spring, sin JPA, sin HTTP
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── UserRole.java
│   │   │   │   │   └── UserStatus.java
│   │   │   │   ├── application/                   ← casos de uso, orquestan domain + puertos
│   │   │   │   │   ├── RegisterUserUseCase.java
│   │   │   │   │   ├── ApproveAccountRequestUseCase.java
│   │   │   │   │   └── CheckPermissionUseCase.java
│   │   │   │   ├── port/
│   │   │   │   │   ├── in/
│   │   │   │   │   │   └── UserQueryPort.java      ← API pública que otros módulos pueden llamar
│   │   │   │   │   └── out/
│   │   │   │   │       └── UserRepositoryPort.java ← lo que application necesita de infraestructura
│   │   │   │   ├── adapter/
│   │   │   │   │   ├── in/web/
│   │   │   │   │   │   ├── UserController.java
│   │   │   │   │   │   └── dto/
│   │   │   │   │   │       ├── UserResponse.java
│   │   │   │   │   │       └── RegisterUserRequest.java
│   │   │   │   │   ├── in/event/
│   │   │   │   │   │   └── UserEventListener.java  (escucha eventos de OTROS módulos, ej. reacciona a algo de onboarding)
│   │   │   │   │   └── out/persistence/
│   │   │   │   │       ├── UserJpaEntity.java
│   │   │   │   │       ├── UserJpaRepository.java  (Spring Data)
│   │   │   │   │       └── UserRepositoryAdapter.java (implementa UserRepositoryPort)
│   │   │   │   └── api/                            ← @NamedInterface: ÚNICO paquete visible desde otros módulos
│   │   │   │       └── UserSummary.java            (DTO liviano expuesto entre módulos, no la entidad completa)
│   │   │   │
│   │   │   ├── habits/                             (misma forma: domain / application / port / adapter / api)
│   │   │   │   ├── package-info.java
│   │   │   │   ├── domain/
│   │   │   │   │   ├── HabitTrack.java
│   │   │   │   │   ├── HabitStatus.java            (máquina de estados: PENDING/IN_PROGRESS/COMPLETED/FAILED/EXPIRED)
│   │   │   │   │   ├── DayType.java
│   │   │   │   │   └── PhoneFreeRun.java             (Santuario — plano, NO en subcarpeta: ver §5.1.2)
│   │   │   │   ├── application/
│   │   │   │   │   ├── CompleteHabitUseCase.java
│   │   │   │   │   ├── ExpirePendingTracksUseCase.java   (llamado por el scheduler nocturno)
│   │   │   │   │   └── StartPhoneFreeRunUseCase.java
│   │   │   │   ├── port/{in,out}/
│   │   │   │   ├── adapter/
│   │   │   │   │   ├── in/web/HabitController.java
│   │   │   │   │   ├── in/scheduler/ExpireTracksScheduler.java   (@Scheduled, reemplaza el cron actual)
│   │   │   │   │   └── out/persistence/...
│   │   │   │   └── api/
│   │   │   │       └── HabitCompletedEvent.java     (evento de dominio publicado — otros módulos lo escuchan)
│   │   │   │
│   │   │   ├── evidence/          (mismo patrón — puerto EvidenceValidationPort, adapter Spring AI ChatClient)
│   │   │   ├── onboarding/        (mismo patrón — incluye adapter/in/web con endpoint async 202 + polling)
│   │   │   ├── rocks/
│   │   │   ├── academy/
│   │   │   ├── community/
│   │   │   ├── calendar/
│   │   │   ├── chat/              (adapter/in/websocket además de web)
│   │   │   ├── notifications/     (mayormente adapter/in/event — consume eventos de todos)
│   │   │   ├── rag/               (adapter/out/vectorstore con PgVectorStore de Spring AI)
│   │   │   ├── support/
│   │   │   ├── points/            (mayormente adapter/in/event)
│   │   │   ├── phasecontracts/
│   │   │
│   │   └── resources/
│   │       ├── application.yml                     (config común: virtual threads, OTel, etc.)
│   │       ├── application-local.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/                        ← Flyway, un subdirectorio por módulo para que el dueño sea obvio
│   │           ├── users/V1__baseline_users.sql
│   │           ├── habits/V1__baseline_habits.sql
│   │           ├── habits/V2__add_santuario_tables.sql
│   │           └── ... (uno por módulo, numerados independientemente vía `flyway.locations` múltiples)
│   │
│   └── test/
│       └── java/com/renaser/os/
│           ├── ArchitectureTest.java                 ← `ApplicationModules.of(RenaserOsApplication.class).verify()`
│           │                                            (falla el build si un módulo importa el interno de otro)
│           ├── ModuleDocumentationTest.java           ← genera diagramas C4/PlantUML en cada build (spring-modulith-docs)
│           ├── users/
│           │   ├── domain/UserTest.java                (unit, sin Spring)
│           │   ├── application/RegisterUserUseCaseTest.java  (unit, mockea los puertos out)
│           │   └── adapter/UserControllerIT.java        (@SpringBootTest + Testcontainers Postgres)
│           └── habits/
│               ├── domain/HabitStatusTest.java          (la máquina de estados, 100% unit, sin infra)
│               └── ...
│
└── docs/
    ├── MODULOS_A_AVANZAR.md                          (qué construir y en qué orden)
    └── API_CONTRACT.md                                (contrato que la app RN ya consume)
```

**Reglas que hacen que esto no degenere con el tiempo:**

- `package-info.java` en la raíz de cada módulo lleva `@org.springframework.modulith.ApplicationModule`. Sin eso, Modulith no lo reconoce como módulo y `ArchitectureTest` no lo protege.
- Solo el paquete `<modulo>/api/` es público. Todo lo demás (`domain`, `application`, `port`, `adapter`) es *package-private* o queda marcado como interno — si `rocks` necesita algo de `habits`, solo puede importar `habits.api.*`, nunca `habits.domain.HabitTrack` directamente. `ArchitectureTest` rompe el build en CI si esto se viola — es el mecanismo real que evita el "big ball of mud", no la buena voluntad del equipo.
- `domain/` nunca importa `org.springframework.*` ni `jakarta.persistence.*` — si una clase de dominio necesita un `@Entity`, esa anotación vive en un `adapter/out/persistence/*JpaEntity.java` separado, y un mapper la traduce. Esto es lo que permite testear `HabitStatusTest` sin levantar Spring ni Postgres.
- Migraciones Flyway con carpeta por módulo (`db/migration/<modulo>/`) para que quede trazable qué módulo es dueño de qué tabla, aunque todas vivan en el mismo Postgres — importante porque hoy Prisma tiene un `schema.prisma` único y monolítico; esta convención es el equivalente ordenado en Flyway.
- `ArchitectureTest` y `ModuleDocumentationTest` corren en cada PR — son las pruebas que reemplazan la disciplina manual que hoy exige revisar a mano que nadie mezcle features en Next.js.

### 5.1.1 Esto contra la fuente original: Cockburn y el libro de Hombergs

Verificado contra la documentación primaria, no solo por convención:

- **Alistair Cockburn**, creador del patrón (artículo original *"Hexagonal Architecture"*, y el libro que coescribió con Juan Manuel Garrido de Paz en 2024): la arquitectura hexagonal **no es una prescripción de carpetas** — es un principio de dependencia. El núcleo ("la aplicación") define **puertos** (interfaces) para todo lo que necesita hablar hacia afuera; los **adaptadores** son implementaciones tecnológicas concretas de esos puertos. Un puerto puede tener múltiples adaptadores intercambiables (HTTP, CLI, un test harness, un batch driver). La palabra clave de Cockburn es *"conversación con propósito"*: el puerto expone intención de negocio, no detalles de transporte.
- **Tom Hombergs**, *"Get Your Hands Dirty on Clean Architecture"* — la referencia práctica más citada para llevar esto a Java/Spring. Estructura real de su repo de referencia (`buckpal`, verificado en vivo contra GitHub):

  ```
  io/reflectoring/buckpal/
  ├── adapter/
  │   ├── in/web/SendMoneyController.java
  │   └── out/persistence/
  │       ├── AccountJpaEntity.java
  │       ├── AccountMapper.java
  │       ├── AccountPersistenceAdapter.java
  │       └── SpringDataAccountRepository.java
  ├── application/
  │   ├── domain/
  │   │   ├── model/           (Account, Activity, Money — sin una sola anotación de Spring)
  │   │   └── service/          (SendMoneyService — implementa el caso de uso)
  │   └── port/
  │       ├── in/               (SendMoneyUseCase, GetAccountBalanceUseCase — interfaces)
  │       └── out/               (LoadAccountPort, UpdateAccountStatePort — interfaces)
  └── common/
      ├── UseCase.java           (anotación propia, marcador)
      ├── WebAdapter.java        (anotación propia, marcador)
      └── PersistenceAdapter.java (anotación propia, marcador)
  ```

  Dos diferencias con lo que propuse en §5.1, ambas intencionales de mi parte y vale explicarlas:

    1. **Hombergs anida `domain` y `port` dentro de `application`**; yo los puse como hermanos (`domain/`, `application/`, `port/`, `adapter/` al mismo nivel) **dentro de cada módulo**. La diferencia es de granularidad, no de principio: el libro modela **una sola aplicación** con un hexágono. Nosotros tenemos **14 hexágonos** conviviendo en un mismo deploy (Spring Modulith), así que cada `modulo/` del árbol de §5.1 *es* un `buckpal` completo — el anidado de Hombergs sigue siendo válido y lo pueden usar tal cual dentro de cada módulo si el equipo lo prefiere; ninguna de las dos variantes rompe el principio de dependencia.
    2. **`common/` con anotaciones propias (`@UseCase`, `@WebAdapter`, `@PersistenceAdapter`) + ArchUnit** es el mecanismo de Hombergs para volver la regla *ejecutable*: una regla de ArchUnit dice literalmente "solo clases anotadas `@PersistenceAdapter` pueden implementar interfaces de `port.out`". Es el mismo problema que Spring Modulith resuelve en §5.1 con `@ApplicationModule` + `ArchitectureTest`, solo que Modulith además entiende de **módulos** (bounded contexts), no solo de capas dentro de un único hexágono — por eso Modulith es la pieza correcta para "son varios módulos" y ArchUnit/anotaciones propias es la pieza correcta si en algún punto un módulo individual quiere una arquitectura hexagonal *interna* más estricta que la que da Modulith por defecto. Se pueden usar **las dos a la vez** (Modulith entre módulos, ArchUnit dentro del módulo más crítico, ej. `habits` o `evidence`).

- **El porqué, en una frase:** en ambas fuentes la regla de oro es la misma — *las dependencias del código siempre apuntan hacia adentro, hacia el dominio*. `domain/` no importa nada de Spring, JPA, HTTP ni de ningún adaptador; son los adaptadores los que dependen del dominio (a través de los puertos), nunca al revés. Eso es lo que permite testear `HabitStatusTest` con un `new HabitTrack(...)` plano, sin Spring context, sin Postgres — y es lo que permite cambiar Prisma por JPA, o Gemini directo por Spring AI, sin tocar una sola regla de negocio.

---

### 5.1.2 Qué es cada carpeta, explicado simple (leer esto primero)

Si sos la próxima persona que toca este código, **esta subsección es la que tenés que leer antes que ninguna otra**. Todo el resto del documento explica el *por qué*; esto explica el *dónde*.

**La idea en una frase:** el `domain/` es el cerebro y no sabe que existe internet ni la base de datos; todo lo demás está para conectarlo con el mundo.

Analogía: un módulo es un **restaurante**.

| Carpeta | En el restaurante | Qué es en el código | ¿Puede importar Spring/JPA? |
|---|---|---|---|
| `domain/` | La **receta** y las reglas de la cocina | Las reglas de negocio puras. Clases con datos **y** comportamiento | **NO. Nunca.** |
| `application/` | El **chef** que ejecuta la receta paso a paso | Un caso de uso = una operación completa del sistema | Sí, mínimo (`@Transactional`) |
| `port/in/` | El **menú** — lo que se puede pedir | Interfaces que exponen los casos de uso | No |
| `port/out/` | El **pedido al proveedor** — "necesito tomates", sin decir a qué proveedor | Interfaces de lo que el caso de uso necesita del exterior | No |
| `adapter/in/` | El **mozo** que toma tu pedido | Traduce HTTP / WebSocket / scheduler → caso de uso | Sí |
| `adapter/out/` | El **proveedor concreto** que trae los tomates | Implementa `port/out/` con Postgres, Gemini, Drive… | Sí |
| `api/` | La **ventana** hacia otros módulos | Lo único que otros módulos pueden importar | Sí |
| `shared/` | Lo que **todo el restaurante** comparte | `UserId`, `Clock`, tipos transversales | Sí, mínimo |

**La distinción que más cuesta — `port/out/` vs `adapter/out/`:** el puerto dice *qué necesito*, el adaptador dice *cómo se consigue*. `LoadUserPort` (puerto) no sabe que existe Postgres; `UserPersistenceAdapter` (adaptador) sí. Por eso el puerto se llama por intención de negocio y nunca por tecnología (§5.4.8).

**Cómo saber si algo va en `domain/` o en `application/`:** si la regla es verdad **siempre**, sin importar quién la invoque ni desde dónde (*"un aprendiz se gradúa 90 días después de empezar"*), va en `domain/`. Si describe **una secuencia de pasos** que incluye hablar con el exterior (*"buscá el usuario, validá que no exista, guardalo, mandá el mail"*), va en `application/`.

#### La regla de `domain/`: subcarpeta por agregado, NUNCA por capa ni "para ordenar"

**Corregido 2026-08-24.** La versión anterior de esta regla ("domain/ siempre plano, nunca subcarpetas") estaba mal calibrada — se basó en una sola referencia (`buckpal`) que da la casualidad de que **tiene un solo agregado**. Verificado ahora contra una segunda referencia con **varios agregados**, y la regla real es otra.

- **`thombergs/buckpal`** — `application/domain/model/`: `Account.java`, `Activity.java`, `ActivityWindow.java`, `Money.java`. Plano. Pero las 4 clases son **una sola historia**: una cuenta, sus movimientos, su dinero. Un solo agregado.
- **`xsreality/spring-modulith-with-ddd`** — `borrow/domain/`: `Book.java`, `Hold.java`, `Patron.java`, `BookRepository.java`, `HoldRepository.java`. Plano. Mismo motivo: las 3 entidades sirven **un solo caso de uso** ("pedir prestado un libro"), ninguna tiene vida propia sin las otras.
- **`citerus/dddsample-core`** (la referencia clásica de DDD, citada en el libro de Eric Evans) — acá **sí** hay subcarpetas, y es la prueba que faltaba: `domain/model/cargo/` (11 archivos: `Cargo`, `Delivery`, `Itinerary`, `Leg`, `RouteSpecification`, `CargoRepository`...), `domain/model/handling/` (7 archivos), `domain/model/location/` (3), `domain/model/voyage/` (5). Cuatro carpetas, una por **agregado independiente** — porque un `Cargo` y un `Voyage` son cosas distintas, cada una con su propia identidad, su propio ciclo de vida y su propio repositorio. Uno no es "parte de" el otro.

**La regla real: la subcarpeta no es por capa (eso nunca) ni "para que se vea ordenado" — es por agregado, y solo cuando hay más de uno de verdad.**

- ¿Las clases del `domain/` de este módulo cuentan **una sola historia** (una raíz de agregado + sus value objects + sus enums, todos sin sentido unos sin otros)? → **plano**, como `buckpal`.
- ¿Hay **dos o más raíces de agregado independientes** (cada una con su propia identidad, su propio ciclo de vida, su propio repositorio — capaz de existir y tener sentido sin la otra)? → **una subcarpeta por agregado**, como `dddsample-core`. Ejemplo real de este repo: `users/domain/` tiene `User` (identidad), `AccountRequest` (una solicitud, con su propio `id`, su propio estado, su propio repositorio futuro) y `MentorProfile` (perfil 1:1, con su propio repositorio futuro) — son tres agregados, no uno, así que van en `user/`, `accountrequest/`, `mentorprofile/`.

**Por qué esto no contradice el espíritu de la regla vieja (evitar el "por si acaso"):**

1. **Sigue prohibido subdividir por capa** dentro de `domain/` (nada de `domain/entities/`, `domain/valueobjects/`) — eso seguía siendo una mala idea y la razón 3-4 de la regla vieja (Modulith razona por paquete; Java no da encapsulamiento real ahí) sigue aplicando tal cual.
2. **La subcarpeta sigue siendo la señal de una pregunta**, solo que ahora la pregunta correcta es "¿esto es un agregado nuevo?" en vez de "¿me armo una carpeta?". Si la respuesta es sí, la subcarpeta documenta esa decisión — no la esconde.
3. **El límite de ~10 clases sigue siendo la señal de alarma**, pero ahora la primera reacción no es "¿parto el módulo?" sino "¿ya tengo más de un agregado ahí adentro sin haberlo notado?" — que es más barato de corregir y muchas veces es la causa real de por qué la carpeta creció.

Esta regla aplica **solo a `domain/`**. En `adapter/` se sigue agrupando por tecnología (`adapter/in/web`, `adapter/in/scheduler`, `adapter/out/persistence`), y en `adapter/in/web/dto/` los DTOs van agrupados — eso no cambió.

---

## 5.2 Mensajería asíncrona: ¿RabbitMQ o Kafka?

Para que quede resuelto explícitamente, ya que preguntás por velocidad: **ninguno de los dos, todavía** — y cuando haga falta uno, **RabbitMQ**, no Kafka. Con el porqué:

### Por qué "ninguno, todavía"

En §4.4 ya está la pieza más rápida posible para este problema: **eventos de dominio in-process de Spring Modulith**. Publicar y consumir un evento dentro del mismo JVM (mismo hilo o un virtual thread hermano) cuesta **nanosegundos a bajos microsegundos** — no hay red, no hay serialización a bytes, no hay ida y vuelta a un broker. Agregar RabbitMQ o Kafka *antes* de separar módulos en servicios distintos no hace nada más rápido: le suma una parada de red (mínimo ~0.5–2 ms por mensaje, sin contar cola) a algo que hoy cuesta prácticamente cero. La única razón real para meter un broker en un monolito modular es **durabilidad cross-restart** (que Modulith ya cubre con su tabla de outbox JPA) o **desacoplar el ciclo de vida de un consumidor lento** — no la velocidad.

### La diferencia real entre RabbitMQ y Kafka (por si se separa `evidence`/`rag` como servicio aparte — §9)

| | RabbitMQ | Kafka |
|---|---|---|
| Modelo | *Push*: el broker empuja el mensaje al consumidor por un socket persistente apenas está disponible | *Pull*: el consumidor hace polling del log; Kafka agrupa (*batching*) para maximizar throughput |
| Latencia típica | **~1–10 ms**, sub-milisegundo de p99 a carga moderada | **~5–100 ms** — optimizado para throughput, no para el mensaje individual |
| Throughput techo | Menor (decenas de miles msg/s) | Mucho mayor (cientos de miles a millones msg/s) |
| Patrón que resuelve bien | Colas de trabajo / comandos: "encolar esta validación, que un worker la procese, con ack/retry/DLQ" — RPC, task scheduling | Log de eventos durable: streaming, replay, múltiples consumidores independientes leyendo la misma historia a su propio ritmo, analítica |
| Encaja con | El patrón que **ya usan hoy** para validación IA: encolar → procesar async → notificar resultado | Un futuro "bus de eventos de producto" para BI/analítica (ej. reconstruir métricas históricas de hábitos completados) |

**Recomendación concreta:** si el día de mañana `evidence`/`onboarding`/`rag` se extraen como servicio aparte (§9) porque su perfil de carga con Gemini diverge del resto, la comunicación `habits`/`rocks` → `evidence-service` es exactamente el caso de uso de RabbitMQ (cola de trabajo con ack, reintentos y dead-letter queue para los casos que hoy caen a `pending_review`), no de Kafka. Kafka entraría en el stack solo si en el futuro aparece una necesidad real de **event sourcing o analítica de eventos de producto a gran escala** — un problema distinto a "que la validación responda rápido", y no hay señal de esa necesidad en el dominio actual.

---

## 5.2.1 La excepción real: tiempo real entre instancias (chat global, comunidad)

§5.2 dijo "eventos in-process, sin broker" — eso es correcto **solo para comunicación módulo→módulo dentro de una misma instancia de la JVM**. Hay un caso del dominio actual donde eso no alcanza y no lo separé a tiempo: **`chat` (global) y el feed en vivo de `community`**.

**El problema concreto:** en cuanto el backend corre en **más de una instancia** detrás de un balanceador — que es justamente cómo se escala para atender muchos usuarios —, un usuario conectado por WebSocket a la instancia 1 y otro conectado a la instancia 2 **no se ven** si la única vía de propagación es un evento in-process: ese evento nunca sale de la instancia donde ocurrió. No es una posibilidad, es el comportamiento garantizado de Spring Modulith (in-process, por diseño) apenas hay más de una réplica corriendo.

**Solución — dos caminos, ambos estándar en Spring, ninguno es Kafka:**

1. **Redis Pub/Sub** *(recomendado para este stack)*: cada instancia se suscribe a un canal Redis; al publicar un mensaje de chat/post de comunidad, todas las instancias lo reciben y lo empujan a sus sockets/SSE locales. No suma infraestructura nueva — Redis ya está en el stack (§6) como caché L2; se reutiliza como bus de fan-out.
2. **STOMP Broker Relay a RabbitMQ**: Spring WebSocket tiene soporte de primera clase para esto (`enableStompBrokerRelay`) — cada instancia mantiene una conexión STOMP al broker, que reparte el mensaje a todas. Es el camino "oficial" de Spring, a costa de operar RabbitMQ además de Redis.

Con Redis ya en el stack por caché, **Redis Pub/Sub es el punto de partida por defecto** — agregar RabbitMQ solo si más adelante hace falta *at-least-once* real hacia el broker (Redis Pub/Sub es *fire-and-forget*: si una instancia está caída un instante, pierde ese mensaje del canal — aceptable para "live typing"/presencia, y no es el canal que persiste el historial: el mensaje de chat/post siempre se escribe primero en Postgres, el pub/sub es solo el empujón en vivo a quien ya está mirando la pantalla).

**Efecto colateral sobre §5.3.5 (caché de rol/estado):** la invalidación por evento que propuse ahí también es in-process — con varias instancias, un cambio de rol en la instancia 1 no invalida al instante el caché de la 2. El TTL de 30s sigue siendo la red de seguridad real; para que la invalidación sea inmediata en todas las instancias, debe viajar por el mismo canal Redis Pub/Sub, no solo como evento de Modulith.

**Tabla resumen, actualizada:**

| Necesidad | Mecanismo |
|---|---|
| Módulo → módulo, misma instancia (`HabitCompletedEvent` → `points`, etc.) | Spring Modulith events in-process |
| **Chat global / feed en vivo de comunidad — entre instancias** | **Redis Pub/Sub** (o STOMP + RabbitMQ) |
| Invalidación de caché de rol/estado — entre instancias | Redis Pub/Sub (mismo canal) |
| Cola de trabajo async si `evidence`/`rag` se extrae como servicio (§9) | RabbitMQ |
| Analítica/replay histórico a gran escala (si aparece la necesidad) | Kafka |

**Sobre escalabilidad en general, para que quede explícito:** el monolito modular escala horizontalmente igual que un set de microservicios para todo lo que es *stateless* — se corren N instancias detrás de un balanceador, y Virtual Threads le da a cada instancia una concurrencia I/O-bound muy alta (miles de conexiones por instancia sin agotar hilos). Los únicos puntos que requieren diseño explícito para no romperse con N instancias son los que dependen de estado compartido: WebSocket (resuelto arriba), rate limiting (§5.3.6 — mover a Redis si hay más de una instancia), y la caché L1 en memoria (Caffeine es por instancia; el TTL acota la inconsistencia, la invalidación por Redis Pub/Sub la elimina).

---

## 5.3 Módulo `users`: diseño detallado

Bajado a un caso real, contra lo que el repo ya implementa hoy (`docs/FEATURE_USERS.md`, `docs/PERMISSIONS.md`, `docs/PROPOSAL_ACCOUNT_REQUESTS.md`, verificados en vivo). El objetivo de esta sección es que la migración de `users` sea *fiel a las reglas de negocio ya validadas en producción*, no una reinterpretación.

### 5.3.1 Qué NO cambia

- **Los 5 roles y sus perfiles**: `ALCHEMIST`/`AlchemistProfile`, `ADMIN`/`AdminProfile`, `MENTOR_LEAD`/(perfil por definir), `MENTOR`/`MentorProfile`, `TRAINEE`/`TraineeProfile`.
  > **Corregido 2026-08-22.** Esta línea decía "los 4 roles" y omitía `MENTOR_LEAD`, contradiciendo la tabla de §5 que sí lo lista. Son **cinco**: Aprendiz, Mentor, Líder de Mentores, Administrador/Operación y Alquimista. Ver `docs/MODULOS_A_AVANZAR.md` §4.1 para el modelo de roles y la pregunta abierta sobre el perfil de `MENTOR_LEAD`.

  `User.id` sigue siendo el UUID de Supabase Auth (nunca autogenerado por el repositorio) — Java respeta esto: el `id` viaja desde el adapter de auth hasta el `UserRepositoryPort.save`, el dominio nunca genera identidad propia.
- **Auth se queda en Supabase.** No hay razón de negocio para tocarlo (ya lo dijimos en §11). Spring Security actúa como *Resource Server* validando el JWT que Supabase ya emite.
- **El flujo de alta con dos caminos** (autoregistro vía `AccountRequest` + aprobación, o invitación directa por admin) se preserva tal cual — es lógica de dominio ya peleada contra un problema real (el correo no funciona), no un detalle de framework.

### 5.3.2 Modelo de dominio (paquete `users/domain`)

```java
// users/domain/User.java — sin una sola anotación de Spring/JPA
public final class User {
    private final UserId id;                 // = Supabase Auth UUID, nunca generado acá
    private final Email email;                // value object: valida formato, unicidad la garantiza el repo
    private UserRole role;                    // TRAINEE por defecto; solo ADMIN/ALCHEMIST lo cambian
    private UserStatus status;                // ACTIVE por defecto
    private String fullName;
    private String avatarUrl;
    private Instant lastActiveAt;

    public void suspend()  { this.status = UserStatus.SUSPENDED; }
    public void changeRole(UserRole newRole, User actor) {
        if (!actor.can(Permission.MANAGE_ROLES))           // regla de negocio, no @PreAuthorize
            throw new NotAuthorizedException("No autorizado a cambiar roles");
        this.role = newRole;
    }
    public boolean can(Permission permission) {
        return role.can(permission);                        // la matriz vive en el enum UserRole
    }
}
```

> **Corregido 2026-08-22.** Antes esto decía `return role == ADMIN || role == ALCHEMIST`. Es el antipatrón descrito en `docs/MODULOS_A_AVANZAR.md` §4.1: ese `if` se replica por todo el código y, cuando aparece un rol nuevo, **hay que cazarlo a mano en cada lugar**. Pasó de verdad — `MENTOR_LEAD` apareció tarde. La matriz de permisos vive en el enum `UserRole`, en un solo archivo.

`AlchemistProfile`, `AdminProfile`, `MentorLeadProfile`, `MentorProfile`, `TraineeProfile` son cinco clases de dominio separadas (no una jerarquía de herencia JPA con discriminador — el propio documento del feature prohíbe mezclar roles/perfiles). `TraineeProfile` lleva las reglas ya conocidas como invariantes de constructor: `expectedGraduationDate = startDate.plusDays(90)`, `programDay`/`coherenceScore`/`leaguePoints` sin setters públicos (solo mutables desde dentro del módulo, vía métodos con nombre de intención: `advanceProgramDay()`, no `setProgramDay()`).

### 5.3.3 Casos de uso (paquete `users/application`)

| Caso de uso | Reemplaza (Next.js) | Nota de migración |
|---|---|---|
| `SubmitAccountRequestUseCase` | `POST /api/v1/account-requests` | DTO de entrada **sin campo `role`** — el mismo blindaje que ya identificaron como "el punto más importante": el rol nunca viaja desde el cliente público, se fuerza `TRAINEE` en el use case, ni siquiera llega como opción al mapper |
| `ApproveAccountRequestUseCase` | Aprobar en panel admin (`adminCreateUser` + `createTraineeDirect`) | Transacción única: crea `User` + perfil correspondiente + marca `AccountRequest.APPROVED` — si algo falla, rollback incluye `SupabaseAdminAuthPort.deleteUser` (el mismo patrón de compensación que ya usan en `inviteAndCreateUser`) |
| `RejectAccountRequestUseCase` | Rechazar en panel | Incluye el `deleteUser` en Supabase para liberar el correo (punto 3 de `PROPOSAL_ACCOUNT_REQUESTS.md`) |
| `InviteAndCreateUserUseCase` | `inviteAndCreateUser` | Sigue siendo la vía para MENTOR/ADMIN/ALCHEMIST y cohortes a mitad de programa — no se retira |
| `GetMyProfileUseCase` / `UpdateMyProfileUseCase` | U-02, U-03 | |
| `UpdateTraineeProfileUseCase` | U-05 | Rechaza explícitamente `programDay`, `coherenceScore`, `leaguePoints`, `currentPhase` si vienen en el request — estos campos ni siquiera existen en el DTO de entrada, así el compilador los excluye en vez de un `if` en runtime |
| `AssignMentorToTraineeUseCase` | `assignMentorToTrainee` | Actualiza `totalTraineesManaged` atómicamente |
| `UpdateUserRoleUseCase` | `updateUserRole` | El propio doc original lo marca "pending si la cascada es compleja" — mantener esa cautela: implementar solo el camino simple (crear el nuevo perfil vacío), dejar migración de datos entre perfiles fuera de alcance explícitamente |

### 5.3.4 Autorización: portar `requireRole` / `requireSelf` / `requireMentorScope` tal cual

El sistema de permisos actual (`docs/PERMISSIONS.md`) es deliberadamente simple — 3 funciones, sin motor de reglas. **Recomiendo portarlo literal a Java en vez de saltar directo a `@PreAuthorize`/ABAC de Spring Security**, por la misma razón que en toda la migración: minimizar drift de comportamiento contra algo ya probado en producción.

```java
// users/adapter/in/web/security/AccessGuard.java
public sealed interface AccessDecision permits Allowed, Forbidden { }

public final class AccessGuard {
    // equivalente a requireRole — pregunta por PERMISO, no por rol. Sin ir a la base:
    // usa el Authentication ya resuelto por el filtro (rol + permisos cacheados, §5.3.5)
    public AccessDecision requirePermission(AuthenticatedUser caller, Permission needed) { ... }

    // equivalente a requireSelf
    public AccessDecision requireSelf(AuthenticatedUser caller, UserId targetUserId) { ... }

    // equivalente a requireMentorScope — sí consulta: mentorProfile del caller + mentorId del trainee
    public AccessDecision requireMentorScope(AuthenticatedUser caller, TraineeProfileId target) { ... }
}
```

> **Ajustado 2026-08-22.** `requireRole(caller, Set<UserRole>)` pasó a `requirePermission(caller, Permission)`. La intención es la misma y sigue siendo una de las 3 funciones originales; el cambio es que el código dice **qué hace falta** (`APPROVE_ACCOUNT_REQUEST`) en vez de **quién lo tiene** (`Set.of(ADMIN, ALCHEMIST)`). Con la lista de roles esparcida por los endpoints, agregar `MENTOR_LEAD` obliga a revisar los 29 a mano. `requireSelf` y `requireMentorScope` **no cambian**: no son preguntas de rol, son preguntas de relación.

Se usan como *guard clauses* al principio de cada `@RestController`, exactamente en el mismo orden que hoy: `1) autenticación (JWT) → 2) AccessGuard → 3) caso de uso`. Nunca como `@PreAuthorize` con SpEL disperso por 29 endpoints — la matriz de permisos de `PERMISSIONS.md` sigue siendo la fuente de verdad y sigue siendo auditable de un vistazo, que es exactamente lo que valoraron al elegir 3 funciones en vez de CASL/CanCanCan.

**Qué endpoint puede tocar cada rol** (el equivalente al API Gateway de microservicios) se declara con `@RequiresPermission(...)` sobre el método del controller, y un test de reflexión falla si algún endpoint queda sin anotar. El diseño completo de las 3 capas está en `docs/MODULOS_A_AVANZAR.md` §0.bis.

### 5.3.5 El filtro de autenticación — dónde vive el `<1 ms` real de §3

`verifyJwt` corre en **cada una de las requests autenticadas del sistema** (29 endpoints hoy, todos los que existan mañana). Es, con diferencia, el hot path más transitado de todo el backend — el lugar exacto donde el objetivo de "<1 ms de procesamiento" de §3 importa de verdad:

```java
// users/adapter/in/web/security/SupabaseJwtAuthenticationFilter.java
// 1. Verifica firma/expiración del JWT (Spring Security Resource Server, JWKS de Supabase cacheado)
// 2. Extrae el UUID (sub claim)
// 3. Carga { role, status } del User — ACÁ es donde importa el caché
```

El paso 3 es una lectura por clave primaria idéntica en cada request — candidato perfecto para Caffeine (§3, §6): `Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(Duration.ofSeconds(30))`, invalidado además por evento cuando `UpdateUserRoleUseCase`/`UpdateUserStatusUseCase` cambian algo (así un `SUSPENDED` no tarda 30s en tomar efecto — se invalida al toque, el TTL es solo la red de seguridad). Con esto, verificar rol+estado pasa de una query Postgres (~0.3–3 ms) a una lectura de mapa en memoria (~microsegundos) en el >99% de las requests. `SUSPENDED` sigue devolviendo 403 antes de tocar el caso de uso, igual que hoy.

### 5.3.6 Lo que hay que re-verificar al migrar (no asumir que "ya está resuelto")

Estos son hallazgos de seguridad que el propio equipo dejó documentados como abiertos — no se resuelven solos por cambiar de framework, hay que llevarlos explícitamente al checklist de la migración de este módulo:

- **RLS de `public.users` en Supabase**: no debe existir ninguna policy de `INSERT` para `authenticated` — si existe, un solicitante pendiente podría crear su propia fila y auto-aprobarse saltándose todo `AccountRequest`. Esto es infraestructura de Supabase, no cambia con la migración a Java — verificarlo sigue siendo un paso manual obligatorio.
- **Rate limit de `POST /api/v1/account-requests`** (60/hora por IP, hoy vía `COUNT` en Postgres porque Vercel no sostiene memoria entre invocaciones): en Java, con un proceso *siempre-arriba* (no serverless), esto se puede simplificar a un limitador en memoria (Bucket4j o Resilience4j `RateLimiter`) — más rápido que la query a Postgres — pero solo si el despliegue deja de ser serverless. Si se mantiene detrás de un autoscaler con múltiples instancias, seguir centralizando el contador (Postgres o Redis) para que el límite sea real entre instancias.
- **Squatting de correos**: rechazar una solicitud debe seguir disparando `deleteUser` en Supabase para liberar el email — es lógica de aplicación (`RejectAccountRequestUseCase`), se porta 1:1.
- **Rollback si `admin.createUser` de Supabase funciona pero el INSERT de `AccountRequest` falla**: en Java esto se resuelve mejor que en Next.js — una `@Transactional` de Spring alrededor del caso de uso, con un `TransactionSynchronization.afterCompletion` que dispara `deleteUser` si el commit no llegó a pasar, es más robusto que el manejo manual actual.

---

## 5.4 DTOs, mapeo, validación, excepciones, estilo y logging

Esta sección cierra los huecos que §5.1.1 dejó abiertos. §5.1.1 estableció el *principio* (las dependencias apuntan hacia adentro); esto define las *reglas ejecutables* que hacen que ese principio sobreviva al día a día. Todo lo de acá es material de revisión de PR, no teoría.

### 5.4.1 Las cuatro estrategias de mapeo, y cuál usamos en cada frontera

Hombergs (*Get Your Hands Dirty on Clean Architecture*, cap. "A Word on Mapping Strategies") no prescribe "usá mappers siempre". Da cuatro opciones con su costo, y explicita que **la estrategia se elige por frontera y puede cambiar con el tiempo**:

| Estrategia | Qué es | Costo |
|---|---|---|
| **No Mapping** | La misma clase cruza web → aplicación → persistencia | Cero boilerplate, pero JPA y JSON contaminan el dominio. **Prohibido acá**: rompe la regla de §5.1 (`domain/` no importa `jakarta.persistence.*`) |
| **Two-Way Mapping** | Cada adaptador traduce de/hacia el modelo de dominio | Dominio limpio, código repetitivo |
| **Full Mapping** | Cada caso de uso tiene su propio modelo de entrada (`XxxCommand`) | Máxima claridad de intención, muchas clases |
| **One-Way Mapping** | Solo entrada mapeada, salida vía interfaces de estado | Elegante pero poco convencional; no lo usamos |

**Decisión para este proyecto:**

| Frontera | Estrategia | Por qué |
|---|---|---|
| `adapter/in/web` → `application` | **Full Mapping** | Es lo que §5.3.3 ya exige sin nombrarlo: *"DTO de entrada sin campo `role`"*. Es una decisión de **seguridad** (mass-assignment), no de estilo |
| `application` ↔ `adapter/out/persistence` | **Two-Way Mapping** | `UserJpaEntity` ↔ `User`. Mapeo plano y aburrido — único lugar donde una herramienta se justifica (§5.4.5) |
| `application` → `adapter/in/web` (salida) | **Two-Way, a mano** | Proyecciones por caso de uso (§8). El mapeo automático filtra campos nuevos sin querer |

### 5.4.2 Los cuatro modelos de un mismo concepto

Para `users`, un mismo "usuario" existe como cuatro clases distintas, y eso es correcto, no duplicación:

```
RegisterUserRequest      adapter/in/web/dto/      ← forma del JSON que manda React Native
SubmitAccountRequestCommand  application/          ← contrato del caso de uso (self-validating, SIN role)
User                     domain/                   ← reglas e invariantes de negocio
UserJpaEntity            adapter/out/persistence/  ← forma de la tabla en Postgres
```

Regla: **ninguna clase con anotaciones de dos mundos a la vez.** Nada de una clase con `@Entity` y `@JsonProperty` juntas. Si aparece, es señal de que alguien colapsó dos modelos.

Entre módulos viaja un quinto, deliberadamente pobre: `users/api/UserSummary` (§5.1) — nunca `User` completo.

### 5.4.3 Validación: tres niveles, no uno

| Nivel | Dónde | Qué valida | Herramienta |
|---|---|---|---|
| **Sintáctica** | `adapter/in/web/dto` | Formato: `@NotBlank`, `@Email`, `@Size` | Bean Validation + `@Valid` en el controller |
| **Contrato del caso de uso** | `application/*Command` | El comando es estructuralmente imposible de construir mal | **Self-validating**: el constructor del `record` valida y explota |
| **Semántica / invariantes** | `domain/` | Reglas de negocio: `expectedGraduationDate = startDate.plusDays(90)`, `actor.canManageRoles()` | Código Java plano. **Nunca anotaciones** |

El nivel 2 es el que casi todos se saltan, y es el importante: si el `@Valid` vive **solo** en el `@RestController`, el día que un `@Scheduled` o un listener de evento invoque el mismo caso de uso, entra sin validar. El comando self-validating hace que eso sea imposible venga de donde venga.

```java
// application/SubmitAccountRequestCommand.java
public record SubmitAccountRequestCommand(
        @NotBlank String fullName,
        @NotBlank @Email String email) {
    public SubmitAccountRequestCommand {
        SelfValidating.validate(this);   // explota en el constructor, no despues
    }
    // NO existe un campo 'role': el compilador impide el mass-assignment de §5.3.3
}
```

### 5.4.4 Excepciones

- **`domain/` lanza excepciones de negocio, sin nada de HTTP.** `NotAuthorizedException` (§5.3.2), `TraineeAlreadyGraduatedException`. Sin códigos de estado, sin `ResponseStatusException`, sin `@ResponseStatus`.
- **`shared/web/GlobalExceptionHandler` (`@RestControllerAdvice`) es el único lugar del sistema que sabe que el 403 existe.** Traduce excepción de dominio → HTTP.
- **No adoptar `ProblemDetail`/RFC 9457 por defecto.** Spring Boot 4 lo trae como estándar, pero §8 y §10 exigen preservar el contrato que la app RN ya consume. **Replicar el formato de error actual de Next.js** en `ApiErrorResponse`; cambiarlo sería exactamente el drift de comportamiento que §5.3.4 manda evitar.
- Nunca `catch (Exception e) { }` vacío ni `e.printStackTrace()`. Si se captura y no se relanza, se loguea con contexto y se decide algo.

Regla de una línea: **la excepción de dominio no conoce su status code; el handler sí.**

### 5.4.5 MapStruct: dónde sí y dónde no

MapStruct **no sale del libro** — en `buckpal` los mappers están escritos a mano. Entró por el esqueleto de Spring Initializr (`pom.xml`). Se mantiene, pero **acotado**:

| Frontera | Herramienta | Por qué |
|---|---|---|
| `UserJpaEntity` ↔ `User` | **MapStruct** | Mapeo plano campo-a-campo, repetido en los 14 módulos. Su caso de uso legítimo |
| `User` → `UserResponse` (salida HTTP) | **A mano** (`static UserResponse.from(user)`) | MapStruct mapea **por nombre, automáticamente**: agregar un campo al dominio lo filtra solo a la respuesta HTTP. Viola §8 (*"evita fugas de campos internos"*) |
| `RegisterUserRequest` → `Command` (entrada HTTP) | **A mano** | Es donde vive el blindaje de `role`. Un mapeo automático reintroduce el agujero |

**Asimetría deliberada: MapStruct hacia adentro de la base de datos, a mano hacia afuera del sistema.** La frontera con el cliente es superficie de ataque; la frontera con nuestro propio Postgres no.

Tres cosas que el `pom.xml` **ya tiene resueltas** y no hay que romper:
- `lombok-mapstruct-binding` en `annotationProcessorPaths`, en el orden `lombok → binding → mapstruct-processor`. Obligatorio desde Lombok 1.18.16; sin él MapStruct puede correr antes que Lombok y **generar mappers vacíos sin fallar el build** (compila verde, campos `null` en runtime).
- El mismo set de procesadores en `default-testCompile`, no solo en `default-compile` — si no, todo `@Mapper` declarado en `src/test` no se genera.
- `<maven.compiler.proc>full</maven.compiler.proc>`: desde **JDK 23** el annotation processing implícito está deshabilitado por seguridad. Sin esto, en Java 25 no corre ni Lombok ni MapStruct.

**Riesgo abierto:** MapStruct estable es **1.6.3 (nov-2024)**; el soporte de JDK 25/26 se agregó en CI recién en abr-2026, sobre la línea **1.7, todavía en beta** (1.7.0.Beta2, jun-2026). Con el uso acotado de arriba el riesgo es bajo, pero **si aparece un mapper que genera código raro en Java 25, la primera hipótesis es la versión**, no el código.

**Lombok en `domain/`, corregido 2026-08-24 contra evidencia real:** la prohibición no es "nada de Lombok", es **"nada que genere setters públicos"**. Verificado en vivo contra `thombergs/buckpal` (la referencia que este documento ya citaba en §5.1.1) — su `Account.java` (dominio, cero JPA) usa:

```java
@AllArgsConstructor(access = AccessLevel.PRIVATE)   // constructor privado: solo lo llaman los factory methods
public class Account {
    private final AccountId id;
    @Getter private final Money baselineBalance;      // getter, NUNCA setter
    @Getter private final ActivityWindow activityWindow;
    public static Account withoutId(...) { return new Account(null, ...); }  // factory methods, como los nuestros
    public static Account withId(AccountId id, ...) { return new Account(id, ...); }
}
```

Y su `Money.java` (value object) usa `@Value` (todo `final`, sin setters, `equals`/`hashCode` generados) — el equivalente pre-`record` de lo que acá ya se hace con `record Email(...)`, `record UserId(...)`.

**Lo que SÍ se usa en `domain/`:** `@Getter`, `@AllArgsConstructor(access = AccessLevel.PRIVATE)`, `@EqualsAndHashCode(of = "id")`, y `@Accessors(fluent = true)` para que el getter generado sea `id()` en vez de `getId()` (mantiene el estilo fluent ya establecido en `User`/`UserId`/`Email`). La validación de invariantes NO va en el constructor (Lombok no soporta lógica ahí) — va en los factory methods estáticos (`registerTrainee`, `submit`, etc.), igual que en `buckpal` (`withId`/`withoutId`).

**Lo que sigue prohibido, sin excepción:** `@Data`, `@Setter`, `@NoArgsConstructor` público — cualquier cosa que abra una forma de mutar el estado sin pasar por un método con nombre de intención. Y `@ToString`/`@EqualsAndHashCode` sin acotar (`of = "..."`) porque puede filtrar campos con PII (email, etc.) a un log — el `toString()` de una entidad con datos personales se escribe a mano, corto, sin PII (ver `User.toString()`).

**Dónde SÍ van `@Entity`/`@NoArgsConstructor`:** nunca en `domain/`. Van en la clase JPA separada de `adapter/out/persistence/` (ej. `UserJpaEntity`), que ni siquiera se ve desde el dominio. `buckpal` lo prueba en el mismo repo: `AccountJpaEntity` (con `@Entity @Table @Data @AllArgsConstructor @NoArgsConstructor`) vive en `adapter/out/persistence/`, es una clase totalmente distinta de `Account`, y un `AccountMapper` a mano traduce entre las dos. Ese paso (adaptador de persistencia) es el paso 6 del orden de construcción de `docs/MODULOS_A_AVANZAR.md` §4.4 — **todavía no se llegó ahí** en `users`, por eso hoy no hay ningún `@Entity` en el repo.

### 5.4.6 El controller es tonto: sin lógica de negocio, sin repositorio

Regla dura, verificable en review:

**Un `@RestController` solo puede hacer cinco cosas:** deserializar el request, validar formato (`@Valid`), invocar `AccessGuard` (§5.3.4), invocar **un** caso de uso, y mapear el resultado a DTO de salida. Nada más.

**Prohibido en un controller:**
- Inyectar un `Repository`, un `JpaRepository`, un `EntityManager` o un puerto `out`. Solo se inyectan puertos `in` (casos de uso).
- `if` que decida reglas de negocio (`if (user.getPhase() == 3 && ...)`) — eso es dominio.
- `@Transactional`. La transacción es del caso de uso, no del transporte.
- Orquestar varios casos de uso para "armar" una operación. Si hacen falta dos, falta un caso de uso que los componga.
- Cálculos, formateos de negocio, acceso a `Clock` real (usar el puerto `Clock` de `shared/`).

**El porqué, no el dogma:** el controller es un *adaptador de transporte*. Si mañana el mismo caso de uso se expone por WebSocket (§5.2.1, `chat`), por un `@Scheduled` (§5.1, `ExpireTracksScheduler`) o por un listener de evento, toda lógica que viva en el controller hay que duplicarla o rescatarla. Un controller de más de ~15 líneas por endpoint es la señal de alarma.

Simétricamente: **el caso de uso no conoce HTTP.** Nada de `HttpServletRequest`, `ResponseEntity` ni códigos de estado en `application/`.

### 5.4.7 Programación funcional: qué sí, y dónde no

Se prioriza el estilo funcional **en el dominio**, que es donde paga:

**Sí:**
- **Inmutabilidad por defecto.** `record` para value objects (`UserId`, `Email`, comandos, DTOs). Campos `final`. Métodos que "cambian" devuelven una instancia nueva.
- **Funciones puras en `domain/`.** Sin I/O, sin reloj del sistema, sin aleatoriedad — el reloj entra por el puerto `Clock` (§5). Esto es lo que hace posible `HabitStatusTest` con un `new HabitTrack(...)` plano.
- **`Optional` como tipo de retorno** de puertos de lectura (`Optional<User> findById(...)`), en vez de devolver `null`.
- **Streams para transformar colecciones**, con lambdas cortas y con nombre cuando crecen.
- **`sealed interface` + pattern matching** para resultados con variantes cerradas — ya usado en `AccessDecision` (§5.3.4). El compilador obliga a cubrir todos los casos.

**No:**
- `Optional` como **campo de clase o parámetro de método**. Es un tipo de retorno, no un modelo de datos.
- Cadenas de `.stream().map().filter().flatMap()` de más de ~3 pasos: extraer a un método privado con nombre.
- Efectos secundarios dentro de un `forEach`/`map` (escribir en base, publicar eventos). Los efectos van explícitos en el caso de uso.
- Excepciones checked dentro de lambdas — obligan a `try/catch` anidados ilegibles.
- **Streams en el hot path de §3.** El filtro de autenticación de §5.3.5 corre en **cada request**: ahí un `for` plano sobre un array evita la asignación de objetos intermedios. El estilo funcional es para claridad del dominio, no para el camino de microsegundos. Medir con el dashboard de §12 antes de optimizar, y solo ahí.

### 5.4.8 SOLID y tamaño de código, aterrizado a esta arquitectura

SOLID no como recitado, sino como se ve en este repo:

| Principio | Cómo se ve acá |
|---|---|
| **SRP** | **Una clase por caso de uso** (`CompleteHabitUseCase`), no un `UserService` con 30 métodos. La tabla de §5.3.3 ya está escrita así |
| **OCP** | Agregar un adaptador (otro proveedor de IA, otro storage) no toca `domain/` ni `application/` |
| **LSP** | Por eso §5.3.2 usa **4 clases de perfil separadas**, no una jerarquía JPA con discriminador |
| **ISP** | Puertos chicos y por intención: `UserQueryPort` con lo que el consumidor necesita, no un repositorio genérico de 20 métodos |
| **DIP** | Es literalmente la regla de §5.1.1: los adaptadores dependen del dominio vía puertos, nunca al revés |

**Límites duros de tamaño (se revisan en PR):**

| Elemento | Objetivo | Techo |
|---|---|---|
| Método | ≤ 20 líneas | **40** |
| Clase | ≤ 150 líneas | **300** |
| Parámetros de un método | ≤ 3 | 4 → si no, un `record` de comando |
| Niveles de anidamiento | ≤ 2 | 3 → guard clauses / early return |
| Métodos públicos por clase | ≤ 7 | 10 |

**Cómo se rompe una función larga:** extraer **métodos privados con nombre de intención**, no `parte1()`/`step2()`. El método público queda como un índice legible de arriba a abajo:

```java
public AccountRequestId submit(SubmitAccountRequestCommand cmd) {
    rejectIfEmailAlreadyRegistered(cmd.email());
    rejectIfRateLimitExceeded(cmd.email());
    var request = AccountRequest.pendingFor(cmd.fullName(), cmd.email(), clock.now());
    return repository.save(request).id();
}
```

Cada `private` de esos nombra un concepto que antes estaba implícito. **Ese es el objetivo: el método privado existe para ponerle nombre a una idea, no para acortar líneas.**

**Nombres:**
- Casos de uso: verbo + sustantivo + `UseCase` (`ApproveAccountRequestUseCase`).
- Puertos: por **intención de negocio**, no por tecnología — la *"conversación con propósito"* de Cockburn (§5.1.1). `LoadUserPort`, no `JpaUserFinder`. El puerto no debe delatar quién lo implementa.
- Adaptadores: sí nombran la tecnología (`UserPersistenceAdapter`, `GeminiEvidenceValidationAdapter`).
- Booleanos: `isX` / `hasX` / `canX` (`canManageRoles()` en §5.3.2).
- **Prohibidos**: `Util`, `Helper`, `Manager`, `Processor`, `Data`, `Info` sueltos. Son nombres que no dicen nada y se vuelven basureros.
- Sin abreviaturas inventadas (`usrRepo`, `calcPts`). El nombre completo lo escribe el IDE.

### 5.4.9 Logging

`domain/` **no loguea**. Es cómputo puro y testeable sin infraestructura; comunica resultados con valores de retorno y excepciones. Si se siente la necesidad de loguear en el dominio, lo que falta es un evento de dominio (§4.4).

| Capa | ¿Loguea? | Qué |
|---|---|---|
| `domain/` | **No** | — |
| `application/` (casos de uso) | **Sí** | Decisiones de negocio: *"solicitud X aprobada por admin Y"*, *"validación IA falló, intento 3/3 → pending_review"*. Es el rastro auditable de §5.3.6 |
| `adapter/in/web` | Poco | Ya lo cubre Micrometer/OTel (§6); loguear cada request a mano es ruido duplicado |
| `adapter/out/*` | **Sí** | Latencia y fallos de lo externo: Gemini, Supabase, Drive, Resend. Es donde se diagnostican los p99 de §3 |

**Reglas:**
- **Niveles:** `ERROR` = requiere acción humana. `WARN` = degradación recuperada (reintento de Gemini). `INFO` = hito de negocio. `DEBUG` = diagnóstico. Nunca `INFO` dentro de un bucle.
- **`traceId` en MDC** desde el filtro de autenticación (§5.3.5), **propagado a los `@Async`** de validación IA (§7). Sin esto, un flujo async + polling es imposible de correlacionar. Es la regla más importante de esta subsección.
- **Nunca loguear**: el JWT o cualquier token, contraseñas, el `sub` de Supabase, emails completos, ni contenido de evidencia (audio, respuestas de onboarding V90). Son datos personales de aprendices. El punto de fuga clásico es el `GlobalExceptionHandler` volcando el request entero en un 500 — auditarlo explícitamente.
- **Logs estructurados (JSON)** en `prod`, texto plano en `local`.
- `spring-modulith-observability` (ya en el `pom.xml`) genera **spans por invocación de módulo** automáticamente: se ve *"esta request pasó por habits → points → notifications"* sin instrumentar a mano. Es la observabilidad del §4.4 que el documento describía pero no explicaba cómo mirar.

### 5.4.10 Checklist de revisión de PR

- [ ] `domain/` no importa `org.springframework.*`, `jakarta.persistence.*`, ni nada de `adapter/`
- [ ] Ninguna clase tiene anotaciones de dos mundos (`@Entity` + `@JsonProperty`)
- [ ] El controller no inyecta repositorios ni puertos `out`, no tiene `@Transactional` ni `if` de negocio
- [ ] El comando del caso de uso es self-validating y **no** tiene campos que el cliente no deba poder setear (`role`, `programDay`, `coherenceScore`, `leaguePoints`, `currentPhase`)
- [ ] El DTO de salida es una proyección explícita, no la entidad serializada
- [ ] Las excepciones de dominio no conocen códigos HTTP
- [ ] Ningún método supera 40 líneas ni 3 niveles de anidamiento
- [ ] Ningún nombre `Util`/`Helper`/`Manager`
- [ ] No hay logs de PII, tokens ni contenido de evidencia
- [ ] `ArchitectureTest` (§5.1) pasa

---

## 6. Stack tecnológico objetivo

Confirmado contra los releases reales a la fecha (agosto 2026):

| Pieza | Versión | Nota |
|---|---|---|
| Java | **25 (LTS)** | Virtual Threads sin *pinning* en secciones sincronizadas, Structured Concurrency (JEP 505, 5ª preview), Scoped Values finalizados (JEP 506) |
| Spring Boot | **4.1** (GA jun-2026) | Sobre Spring Framework 7.0.8; grpc auto-config, `spring.datasource.connection-fetch=lazy`, OTel toggle nativo |
| Spring AI | **2.0** (GA jun-2026) | Baseline Boot 4.x, tool-calling unificado entre providers, MCP con transporte movido al propio Spring AI (Streamable HTTP por defecto) |
| Persistencia | Spring Data JPA + **Flyway** | Reemplaza `prisma migrate`; mantener el mismo Postgres (Supabase-hosted o migrar a RDS/Cloud SQL más adelante, decisión independiente) |
| Caché local | **Caffeine** | Para el hot path de <1 ms real (§3) |
| Caché distribuida / rate limit | Redis | Solo para lo que debe ser compartido entre instancias |
| Auth | Spring Security **OAuth2 Resource Server** validando JWT de **Supabase Auth** | No reescribir auth: Spring valida el JWT que Supabase ya emite. Reduce drásticamente el riesgo de la migración |
| Vector store (RAG) | `pgvector` sobre el mismo Postgres, vía Spring AI `VectorStore` | Evita sumar una base de datos nueva |
| IA generativa | Spring AI `ChatClient` sobre **Vertex AI Gemini** (o Google GenAI starter) | Mismo modelo (Gemini) que ya usan, ahora con retry/observabilidad de Spring AI |
| Mensajería interna | Spring Modulith Events (outbox JPA) | No hace falta Kafka/RabbitMQ para el volumen de este dominio; si aparece, se agrega sin tocar el dominio (está detrás de un puerto) |
| Concurrencia | **Virtual Threads** (`spring.threads.virtual.enabled=true`) | Tomcat/Netty con un hilo virtual por request; ideal para I/O-bound (Postgres, Gemini, Resend, Drive) |
| Observabilidad | Micrometer + OpenTelemetry (nativo en Boot 4.1) | Medir p50/p99 reales en vez de asumir |
| Testing | JUnit 5 + Testcontainers (Postgres real) + `spring-modulith-test` (verifica que los módulos no se acoplen) + ArchUnit opcional | |
| Mapeo objeto-objeto | **MapStruct 1.6.3** | **Solo** en `adapter/out/persistence` (JpaEntity ↔ dominio). A mano en la frontera web (§5.4.5). Riesgo abierto: 1.6.3 no fue testeado contra JDK 25 |
| Boilerplate | **Lombok** | **Prohibido en `domain/`** — sus setters destruyen los invariantes (§5.4.5). Nunca `@Data` en entidades JPA |
| Reglas de arquitectura | **ArchUnit 1.4.2** | Llega transitivo de Spring Modulith, no hay que declararlo. Usado en `ArchitectureTest` |
| Build | **Maven** (`mvnw`), con **GraalVM Native Image** (Spring AOT) opcional | La arquitectura no depende del build tool (§5.1). Native Image solo si hay cold-start/escala-a-cero; en un monolito siempre-arriba no es prioridad día 1 |

---

## 7. IA con Spring AI 2.0 (RAG, validación de evidencia, Renasia)

El repo actual ya resolvió el patrón difícil (async + polling + reintentos acotados + fallback a revisión humana). En Spring AI 2.0 se traduce así:

- **`ChatClient`** encapsula la llamada a Gemini; se configura con `retry`, `timeout` (los 45 s que ya calibraron para audio) y `ToolExecutionEligibilityChecker` si en el futuro Renasia necesita tool-calling (MCP ya viene integrado en 2.0).
- El endpoint de validación sigue el mismo contrato que hoy: `POST /validate` responde `202 {status:"processing"}` de inmediato (encola en un `@Async` sobre virtual thread), y `GET /validate?recordingId=X` consulta estado — esto **no cambia**, solo cambia la implementación interna.
- **RAG (`FEATURE_RAG.md`)**: usar `PgVectorStore` de Spring AI sobre el mismo Postgres (extensión `pgvector`), evitando sumar Pinecone/Weaviate como dependencia nueva. `VectorStore.similaritySearch()` + `ChatClient` con contexto recuperado es el patrón estándar de Spring AI 2.0.
- El límite de reintentos (`ia_attempts`, fallback a `pending_review`) es lógica de dominio, no de infraestructura — vive en `evidence/domain`, no en el adapter de Gemini. Así, si mañana cambian de Gemini a otro modelo, la regla de negocio no se toca.

---

## 8. Contrato de API para React Native

- Mantener el prefijo y las rutas de `docs/API_CONTRACT.md` / `docs/MOBILE_HABITS_API.md` durante toda la migración — el cliente móvil no debería enterarse de qué hay detrás.
- Documentar con **springdoc-openapi** (OpenAPI 3.1) para regenerar tipos TypeScript del lado del cliente RN automáticamente y detectar drift de contrato en CI.
- DTOs de salida siempre como proyecciones específicas del caso de uso (nunca serializar la entidad JPA completa) — controla payload y evita fugas de campos internos, además de evitar el costo de serialización de grafos completos.
- Para `chat`, considerar upgrade real a WebSocket/SSE (Spring soporta ambos nativamente) en vez de polling — mejora percibida de latencia más grande que casi cualquier optimización de servidor.

---

## 9. Cuándo SÍ separar en servicios aparte

El monolito modular es el punto de partida correcto, no el final del camino. Separar un módulo en un servicio propio se justifica solo cuando aparece una de estas señales concretas — no antes:

1. **Perfil de carga muy distinto**: si `rag`/`renasia` (llamadas a Gemini, potencialmente costosas en CPU/memoria por embeddings) necesita escalar independiente del resto y el resto no, sacarlo permite escalar solo esa pieza.
2. **Aislamiento de fallos**: si un pico de uso de IA no debe poder tumbar el registro de hábitos, separarlo da un blast radius menor.
3. **Ciclo de release distinto**: si el equipo de IA necesita deployar 5 veces al día y el resto del dominio deploya semanalmente.

Como cada módulo ya es un hexágono con puertos definidos, extraerlo el día que haga falta es: mover el paquete, exponer sus casos de uso por HTTP/gRPC (Boot 4.1 ya trae auto-config de gRPC) en vez de llamada Java directa, y sustituir el listener de eventos in-process por un consumer de un broker. Es trabajo mecánico, no una reescritura — ese es el beneficio real de haber invertido en Hexagonal desde el día uno.

### 9.1 El costo que no se ve a simple vista: el rollback deja de ser gratis

Esto es el motivo técnico de fondo, no solo de estilo, para no separar en microservicios sin necesidad — y responde directo a por qué §5.3.3 puede confiar en una `@Transactional` simple para `ApproveAccountRequestUseCase`, mientras que la misma operación repartida en servicios necesitaría infraestructura adicional.

**En el monolito, el rollback es gratis porque hay una sola base de datos y una sola transacción ACID.** `ApproveAccountRequestUseCase` crea el `User`, crea el perfil (`TraineeProfile`/`MentorProfile`/etc.) y marca el `AccountRequest` como `APPROVED` dentro de la **misma transacción de Postgres**. Si el paso 2 falla, Postgres deshace el paso 1 solo — es una propiedad del motor de base de datos (atomicidad), no algo que el código tenga que programar. Por eso en §5.3.3 el rollback ante fallo de Supabase se resuelve con un simple `TransactionSynchronization.afterCompletion`.

**En microservicios, esa garantía desaparece por diseño.** La razón de ser de un microservicio es tener *su propia base de datos*, que nadie más toca directamente — si `users-service` y `notifications-service` son servicios separados, no existe una transacción que abarque las dos bases a la vez. No es una limitación técnica que "todavía no se resolvió": es la consecuencia directa de la independencia que se buscó al separarlos. Intentar forzar una transacción distribuida clásica (2PC/XA) es posible en papel, pero en la práctica casi nadie lo hace — ni Kafka ni la mayoría de brokers modernos lo soportan, y bloquea recursos en todos los servicios a la vez mientras dura, matando la disponibilidad que se estaba buscando al separar servicios.

**La solución estándar de la industria es el patrón Saga — y ahí es donde entran RabbitMQ/Kafka, no como reemplazo del rollback, sino como el mecanismo para *deshacerlo a mano*:**

1. Cada paso del flujo de negocio se convierte en una **transacción local**, cada una en su propio servicio/base de datos, seguida de la publicación de un evento (`UserApproved`, `ProfileCreated`, `NotificationFailed`...).
2. Si un paso falla **después de que un paso anterior ya hizo commit**, no hay "deshacer" automático — el código tiene que ejecutar explícitamente una **transacción compensatoria**: una acción que revierte el efecto a mano (ej. `DeleteUserCompensation`, el equivalente al `deleteUser` de Supabase que ya usan hoy, pero ahora disparado por un evento de fallo en vez de por una línea de código en el mismo método).
3. El broker (RabbitMQ o Kafka) es lo que transporta esos eventos entre servicios de forma confiable — ahí es donde realmente se usan, para que un servicio se entere de que otro falló y dispare su compensación, incluso si en ese momento no está prendido (el broker retiene el mensaje hasta que lo procese).
4. Para que "guardar el cambio" y "avisar que hay que compensar si algo falla más adelante" sean atómicos dentro de un mismo servicio, se usa el mismo **patrón Outbox** que ya mencioné en §4.4 para Modulith — solo que ahí era una tabla local con relé in-process, y en microservicios es una tabla local con un relé que publica a Kafka/RabbitMQ.

Dos formas de organizar la Saga: **coreografía** (cada servicio escucha eventos y reacciona, sin coordinador central — más simple, más difícil de rastrear "qué pasó" cuando algo sale mal) u **orquestación** (un componente dedicado manda la secuencia paso a paso y decide cuándo compensar — más fácil de auditar, un punto más de infraestructura).

**El costo real, para que quede explícito y no a medias:** una Saga da **consistencia eventual**, no atomicidad — hay una ventana de tiempo real donde el sistema está en un estado intermedio (ej. usuario creado en Supabase, pero todavía sin fila en `users-service`), y cada compensación hay que diseñarla, programarla y **testear el camino de falla**, no solo el camino feliz. Es trabajo de ingeniería real, no una casilla de configuración.

**Por qué esto refuerza la recomendación de §4.1:** operaciones como "aprobar solicitud → crear usuario → crear perfil" o "completar hábito → sumar puntos → revisar fase → notificar" son hoy, en el monolito, una sola `@Transactional`. Separarlas en servicios distintos sin necesidad real cambia ese código gratis por una Saga con compensaciones a mano — pagás ese costo aunque el negocio no lo haya pedido. El único lugar de este dominio donde vale la pena pagarlo es exactamente el que ya identifiqué en §9 y §5.2: si `evidence`/`onboarding`/`rag` se extrae como servicio aparte, "encolar validación → procesar async → si falla 3 veces, caer a `pending_review`" **ya es una mini-Saga** — y el fallback a revisión manual que el equipo ya construyó es, ni más ni menos, la transacción compensatoria de ese flujo. Ahí sí se justifica RabbitMQ; en el resto del dominio, todavía no.

---

## 10. Estrategia de migración (Strangler Fig, no big-bang)

1. **Fase 0 — Base:** proyecto Spring Boot 4.1 nuevo, Flyway apuntando al mismo Postgres (schema ya existe, no hay migración de datos todavía), Spring Security validando JWT de Supabase, `users` como primer módulo (bajo riesgo, alto apalancamiento — todo lo demás depende de él).
2. **Fase 1 — Gateway de transición:** poner un proxy inverso (o el propio Next.js) delante, ruteando por prefijo: `/api/v1/users/**` → Spring, el resto sigue en Next.js. El cliente RN no ve el cambio.
3. **Fase 2 — Migrar módulo por módulo** en orden de menor a mayor acoplamiento con IA/async: `points` → `phasecontracts` → `habits` → `rocks` → `academy`/`community`/`calendar`/`support` → `onboarding`/`evidence`/`rag` al final (son los que más se benefician de Spring AI, pero también los de mayor superficie de riesgo).
4. **Fase 3 — Retirar Next.js del rol de backend**, dejándolo (si aplica) solo como frontend admin web, o migrarlo también a un cliente puro sobre la API de Spring.
5. **Contrato como guardrail:** en cada fase, tests de contrato (Pact o snapshot de OpenAPI) contra `docs/API_CONTRACT.md` para garantizar que RN nunca ve una ruptura.

---

## 11. Riesgos y trade-offs a decidir explícitamente

| Decisión | Opción A | Opción B | Recomendación |
|---|---|---|---|
| Auth | Reescribir en Spring Security propio | Validar JWT de Supabase (Resource Server) | **B** — no hay razón de negocio para tocar auth ahora |
| Storage de audios | Migrar de Google Drive a S3/Supabase Storage | Mantener Drive vía puerto | **B** por ahora, aislado detrás de `AudioCatalogPort` para migrar después sin tocar dominio |
| ORM | JPA/Hibernate | jOOQ / JDBC Client puro | JPA para CRUD de dominio; **JDBC Client** (nuevo en Spring 6.1+, ideal con virtual threads) para las queries de reporting/ranking que son lectura pura y sensibles a latencia |
| Base de datos | Mantener Supabase Postgres | Migrar a instancia propia | Mantener — no es un problema de arquitectura, es infraestructura ya resuelta |
| Empaquetado | JVM tradicional | GraalVM Native Image | JVM tradicional al inicio (mejor tooling, throughput mejor en cargas sostenidas); Native Image si más adelante hay requisito de cold-start (serverless/escala-a-cero) |

---

## 12. Checklist técnico de arranque

Estado real al **2026-08-22**. El plan detallado por módulo está en [`docs/MODULOS_A_AVANZAR.md`](docs/MODULOS_A_AVANZAR.md).

**Hecho**

- [x] Proyecto Maven con Spring Boot 4.1.1, Java 25, `spring-modulith-starter-core` + `-jpa` + `-test`, Spring AI 2.0.0, pgvector.
- [x] Paquete base `com.renaser.os`. Módulos `shared` (OPEN) y `users` declarados con `@ApplicationModule`.
- [x] `pom.xml`: `lombok-mapstruct-binding` en el orden correcto, procesadores también en `test-compile`, y `maven.compiler.proc=full` (obligatorio desde JDK 23).
- [x] `users/domain` — `User`, `Email`, `UserRole`, `UserStatus`. Sin setters públicos, sin anotaciones de framework.
- [x] `ArchitectureTest` — `ApplicationModules.verify()` + reglas ArchUnit: `domain/` sin Spring/JPA/Jackson, `domain/` sin adaptadores, controllers sin persistencia, sin clases `Util`/`Helper`/`Manager`.
- [x] **Eclipse Temurin JDK 25.0.4.1 LTS instalado.** `./mvnw clean test` compila con `release 25` — el target real del `pom.xml`, ya no un workaround.
- [x] 21 tests en verde (7 `UserTest`, 8 `EmailTest`, 6 `ArchitectureTest`), sobre Java 25.

**Bloqueado**

- [ ] **Confirmar que Supabase use claves JWT asimétricas (RS256) + JWKS**, no el secreto legacy HS256. Bloquea el Resource Server y el filtro de autenticación.
- [ ] **Auditar la RLS de `INSERT` en `public.users`** — si existe policy para `authenticated`, hay escalada de privilegios.
- [ ] **Definir los permisos de `MENTOR_LEAD`** y los valores reales de `TraineePhase`.

**Pendiente**

- [ ] `spring.threads.virtual.enabled=true` en `application.yaml`.
- [ ] Completar `UserRole` con `MENTOR_LEAD` (hoy tiene 4 de 5) + enum `Permission` con la matriz.
- [ ] Los 5 perfiles de dominio + `AccountRequest`.
- [ ] Puertos, casos de uso, adaptadores de `users` (orden en `MODULOS_A_AVANZAR.md` §4.4).
- [ ] Flyway apuntando al Postgres existente en modo *baseline* (no recrear schema).
- [ ] `spring-modulith-docs` generando el diagrama de módulos en CI, para que el mapa de §5 no se desactualice.
- [ ] Test de reflexión que falle si un endpoint no declara `@RequiresPermission` ni `@PublicEndpoint`.
- [ ] Dashboard de Micrometer/OTel con p50/p99 por endpoint desde el primer módulo desplegado — para validar los SLO de §3 con datos reales, no supuestos.
- [ ] Gateway/proxy de transición ruteando `/api/v1/users/**` a Spring, resto a Next.js.

---

### Fuentes consultadas
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [Spring Boot 4.1.0 available now](https://spring.io/blog/2026/03/26/spring-boot-4-1-0-M4-available-now/) / [InfoQ: Spring Boot 4.1](https://www.infoq.com/news/2026/06/spring-boot-4-1/)
- [Spring AI 2.0.0 GA Available Now](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/)
- [Spring AI 2.0: Java Agents, MCP & Migration Guide](https://essamamdani.com/blog/spring-ai-2-0-java-agent-mcp-migration-guide-2026)
- [Java 25 Virtual Threads / Structured Concurrency (JEP 505) / Scoped Values (JEP 506)](https://javapro.io/2025/12/23/java-25-getting-the-most-out-of-virtual-threads-with-structured-task-scopes-and-scoped-values/)
- Repositorio `renaserlab/RenaserBack` (código, `package.json`, `prisma/schema.prisma`, `docs/*.md`, `INFORME_2026-08-05.md`, `docs/FEATURE_USERS.md`, `docs/PERMISSIONS.md`, `docs/PROPOSAL_ACCOUNT_REQUESTS.md`)
- [Alistair Cockburn — hexagonal-architecture (fuente original)](https://alistair.cockburn.us/hexagonal-architecture) / [Hexagonal Architecture Explained (2023/2025)](https://alistaircockburn.com/Hexagonal%20Budapest%2023-05-18.pdf)
- [Tom Hombergs — Get Your Hands Dirty on Clean Architecture](https://reflectoring.io/book/) y repo de referencia [`thombergs/buckpal`](https://github.com/thombergs/buckpal) (estructura verificada en vivo contra GitHub)
- [RabbitMQ vs Kafka: Latency Comparison for AI Systems](https://latitude.so/blog/rabbitmq-vs-kafka-latency-comparison-for-ai-systems) / [Confluent: RabbitMQ vs Apache Kafka](https://www.confluent.io/compare/rabbitmq-vs-apache-kafka/) / [Kafka vs RabbitMQ Benchmark 2026](https://www.bladepipe.com/blog/data_insights/kafka_vs_rabbitmq_vs_rocketmq_pulsar/)
- [Spring Boot WebSocket: STOMP, Raw Handlers, Scaling](https://websocket.org/guides/frameworks/spring-boot/) / [Spring WebSocket and Redis Pub/Sub](https://dgempiuc.medium.com/spring-websocket-and-redis-pub-sub-a02af0dabddb) — patrón de broker relay para WebSocket/STOMP entre múltiples instancias
- [microservices.io — Pattern: Saga](https://microservices.io/patterns/data/saga.html) (Chris Richardson, referencia canónica del patrón) / [Saga Design Pattern — Azure Architecture Center](https://learn.microsoft.com/en-us/azure/architecture/patterns/saga) — Saga, transacciones compensatorias y Transactional Outbox