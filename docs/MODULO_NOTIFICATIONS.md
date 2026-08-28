# Módulo `notifications` — bandeja, preferencias, tokens push

**Fecha:** 2026-08-24
**Ola:** 3 (adelantada — primer consumidor real de eventos de dominio Modulith, cierra el Lote 2 junto a `habits`/`rocks`)
**Documentos hermanos:** `CLAUDE.MD` (arquitectura y convenciones, §4.4 eventos de dominio) · [`MODULOS_A_AVANZAR.md`](MODULOS_A_AVANZAR.md) · [`PLAN_DE_MODULOS.md`](PLAN_DE_MODULOS.md) §"5. `notifications`" (semilla) · [`MODULO_SUPPORT.md`](MODULO_SUPPORT.md)/[`MODULO_PHASECONTRACTS.md`](MODULO_PHASECONTRACTS.md) (patrón de eventos en `api/` y nombres en español) · [`MODULO_HABITS.md`](MODULO_HABITS.md)/[`MODULO_ROCKS.md`](MODULO_ROCKS.md) (los 4 eventos que este módulo consume, ya construidos y documentados ahí)

**Alcance:** bandeja de notificaciones persistida en servidor (`notificacion/`), preferencias por tipo (`preferencia/`) y tokens push de Expo (`tokenpush/`). Es la primera vez que la bandeja vive en el servidor en vez de ser local-por-dispositivo — señalado explícitamente en el encargo.

---

## 0. Paso 0 — reglas extraídas del código viejo (D-33)

Repo viejo clonado en `C:\Users\Usuario\Documents\Backend90dias\RenaserBack`. App RN clonada en `C:\Users\Usuario\Documents/renaser90 dias/RenaserPlayStoreCopy`. Citas archivo:línea contra esos clones.

### 0.1 `notifications/service.ts` — la bandeja y `emit()`

| Regla | Fuente |
|---|---|
| `emit()` **nunca lanza ni rechaza** — encola en un solo `try/catch` la consulta de preferencia Y el insert; si algo falla, se loguea y se traga. Es el contrato del que dependen sus llamantes (responder un ticket / mandar un mensaje no debe fallar porque falló la campana) | `service.ts:34-66` |
| `emit()` respeta `NotificationPreference`: sin fila para `(usuario, tipo)`, el default es **habilitada** — nunca se asume apagado por ausencia | `service.ts:57-60`, reutiliza `chat/repository.ts:findNotificationEnabled` |
| `emit()` se **awaitea** en los llamantes (a diferencia de los push de Expo, que van con `void`) — es un INSERT local, no una llamada HTTP lenta | `service.ts:48-55` |
| `listNotifications`: la bandeja del que llama, resuelta desde el JWT — sin parámetro que permita leer la de otro | `service.ts:70-82` |
| `markAsRead`: filtra por `(id, userId)` **dentro del propio UPDATE**, nunca "cargar y comparar después" — una notificación ajena y una inexistente devuelven **ambas 404**, a propósito (distinguirlas revelaría qué ids existen) | `service.ts:84-113` |
| `markAllAsRead`: idempotente, sin no leídas devuelve `{updated: 0}` y sigue siendo 200 | `service.ts:115-127` |

### 0.2 `notifications/repository.ts` — queries Prisma

| Regla | Fuente |
|---|---|
| Bandeja: últimos 90 días, tope 100, más nueva primero — **tope, no página**, no hay paginación acá | `repository.ts:22-31`, constantes en `schema.ts:13-14` |
| `markRead`: `updateMany({where: {id, userId, readAt: null}})` — el `userId` va en el WHERE del propio UPDATE (atomicidad ante carreras), `count` 0 puede significar "ya leída" o "no es tuya/no existe" | `repository.ts:44-50` |
| `existsForUser`: solo para distinguir esos dos casos de arriba (200 idempotente vs 404) sin exponer cuál | `repository.ts:52-60` |
| `markAllRead`: mismo patrón `updateMany` filtrado por `userId` | `repository.ts:62-69` |

### 0.3 `notifications/schema.ts` — el contrato JSON

```ts
// NOTIFICATIONS_MAX_AGE_DAYS = 90, NOTIFICATIONS_LIMIT = 100
type NotificationItem = { id, type, title, body, createdAt, readAt: string|null, route: string|null }
type NotificationsListResponse = { items: NotificationItem[] }
```

"Renombrar un campo rompe el badge de no leídos" (comentario literal del archivo) — se preservaron los 7 nombres de campo tal cual en `NotificacionResponse` (§2.3).

### 0.4 Preferencias — `profile/schema.ts` + `profile/service.ts` (P-02/P-03)

| Regla | Fuente |
|---|---|
| `ALL_NOTIFICATION_TYPES` fija el orden canónico y sirve para **completar con default `enabled:true`** los tipos sin fila propia | `profile/schema.ts:72-81`, `profile/service.ts:102-105` |
| `PATCH`: por cada item, si `enabled:true` **borra** la fila (vuelve al default), si `enabled:false` hace **upsert** de una fila con `enabled:false` — la tabla solo guarda desviaciones del default | `profile/service.ts:115-134` |
| Después de aplicar los cambios, `PATCH` devuelve el mismo shape que `GET` (recalculado) | `profile/service.ts:130` |

**Nota:** el enum viejo (`DAILY_HABIT_REMINDER`, `DAILY_ROCK_REMINDER`, `RADAR_CHECKIN_REMINDER`, `MENTOR_MESSAGE`, `SYSTEM_ANNOUNCEMENT`, `WEEKLY_SUMMARY`, `ACHIEVEMENT_UNLOCKED`, `PROGRAM_MILESTONE`, 8 valores) **no** es el mismo que `tipo_notificacion` de la BD nueva (13 valores, español) — la BD nueva ya agregó `MENSAJE_CHAT`, `TICKET_RESPONDIDO`, `TICKET_ABIERTO`, `SANTUARIO_ROTO`, `HABITO_PERSONAL_MODIFICADO`, que no existían en el repo viejo. Se usó el enum de la BD nueva tal cual (`V1__baseline_renaser.sql:84`), sin inventar ni recortar valores.

### 0.5 Tokens push — `chat/schema.ts` + `chat/repository.ts` + `push-tokens/route.ts`

| Regla | Fuente |
|---|---|
| `RegisterPushTokenInput`: `{token: string (1-300), platform?: 'ios'\|'android'\|null}` | `chat/schema.ts:81-85` |
| `upsertPushToken`: UPSERT por `token` (UNIQUE) — un token reinstalado/reasignado a otro usuario **reemplaza el dueño**, nunca duplica fila | `chat/repository.ts:407-413` |
| `POST /api/v1/push-tokens`: cualquier rol autenticado (`TRAINEE`,`MENTOR`,`MENTOR_LEAD`,`ADMIN`,`ALCHEMIST`) | `push-tokens/route.ts:19` |
| `sendExpoPushNotifications`: POST crudo a `https://exp.host/--/api/v2/push/send`, en lotes de 100 (límite de Expo), `sound:'default'`+`priority:'high'` obligatorios para que suene en iOS, **fire-and-forget** (`catch` que solo loguea `warn`, nunca propaga) | `chat/repository.ts:436-463` |

**`findNotificationEnabled`** (misma función que usa `emit()`, §0.1): `pref?.enabled ?? true` — confirmado literal, es la fuente de la regla "sin fila = habilitada" que pedía el encargo verificar.

### 0.6 App RN — `notifications/reconcile.ts`

Es el sistema de **notificaciones locales programadas** (`expo-notifications`, scheduling on-device: recordatorios de hábitos/rocas/radar con cadenas de escalación, límite de 64 pendientes en iOS, etc.) — **no** es un cliente de la bandeja del servidor ni de push server-to-device. No aporta contrato de API para este módulo; se documenta para que quede explícito que se revisó y que la bandeja server-side (`/api/v1/notifications`) es una pieza *nueva y separada* de ese sistema local, no un reemplazo de él.

### 0.7 Regla de negocio confirmada, no inventada

La instrucción del encargo pedía: *"si la preferencia existe y está explícitamente en `false`, no se emite; si no hay fila, se emite siempre"*. Verificado literal en `chat/repository.ts:findNotificationEnabled` (§0.5) y replicado 1:1 en `EmitirNotificacionUseCase`/`NotificacionService.emitir()` — no es una interpretación, es la regla real del repo viejo.

---

## 1. Qué se construyó

```
notifications/
├── package-info.java                          @ApplicationModule("Notifications")
├── domain/model/
│   ├── notificacion/    Notificacion, TipoNotificacion (13 valores, espejo BD)
│   ├── preferencia/     PreferenciaNotificacion (record, PK natural usuario+tipo)
│   └── tokenpush/       TokenPush, TokenPushId, PlataformaPush
├── application/
│   ├── ports/in/notificacion/    EmitirNotificacionUseCase, ListarNotificacionesUseCase,
│   │                              MarcarLeidaUseCase, MarcarTodasLeidasUseCase
│   ├── ports/in/preferencia/     GestionarPreferenciasUseCase
│   ├── ports/in/tokenpush/       RegistrarTokenPushUseCase
│   ├── ports/out/{notificacion,preferencia,tokenpush,push}/  Load/Save por agregado + PushPort
│   └── services/    NotificacionService, PreferenciaNotificacionService, TokenPushService
└── infrastructure/adapter/
    ├── in/rest/{notificacion,preferencia,tokenpush}/   3 controllers tontos, X-Actor-Id
    ├── in/event/    4 listeners @ApplicationModuleListener (uno por evento)
    ├── in/scheduler/   PurgaNotificacionesScheduler (>90 días)
    └── out/{persistence/{notificacion,preferencia,tokenpush}, push/NoOpPushAdapter}
```

Sin `api/` propio: `notifications` es un sumidero terminal — hoy ningún otro módulo necesita consumir nada suyo (a diferencia de `points`/`habits`/`rocks`, que sí publican eventos para que otros los escuchen). Si en el futuro otro módulo necesitara, por ejemplo, saber si una notificación fue leída, se agregaría entonces un `api/` con la proyección mínima — no se creó uno vacío "por si acaso" (mismo criterio anti-sobre-ingeniería de CLAUDE.MD §4.1).

No hizo falta migración Flyway propia: `notificaciones`, `preferencias_notificacion`, `tokens_push` ya están completas en `V1__baseline_renaser.sql:1342-1370`.

### 1.1 Dominio

- **`Notificacion`** (`domain/model/notificacion/`): agregado con `emitir()`/`rehydrate()`, `marcarLeida()` idempotente, `perteneceA()`. Constantes `RETENCION_DIAS=90`/`LIMITE_BANDEJA=100` (§0.3).
- **`TipoNotificacion`**: enum de 13 valores, espejo exacto de `tipo_notificacion` (Postgres). Nombres en español, mismo criterio que `support`/`phasecontracts`/`rocks` (`users` es la excepción histórica en inglés).
- **`PreferenciaNotificacion`** (`domain/model/preferencia/`): `record` (sin id propio, PK natural `usuario+tipo`), constante `DEFAULT_HABILITADA=true`.
- **`TokenPush`** (`domain/model/tokenpush/`): agregado con `registrar()`/`reasignar()`/`rehydrate()`.

### 1.2 Aplicación

- **`EmitirNotificacionUseCase`**: el único punto de entrada para crear una notificación — lo llaman los 4 listeners de `in/event/`. Comando self-validating (`EmitirNotificacionCommand`). Devuelve `Optional<Notificacion>` (vacío si la preferencia está explícitamente apagada).
- **`ListarNotificacionesUseCase`/`MarcarLeidaUseCase`/`MarcarTodasLeidasUseCase`**: autoservicio estricto — solo reciben `actorId`, sin parámetro para apuntar a otro usuario.
- **`GestionarPreferenciasUseCase`**: autoservicio por **firma** (§3, blindaje CLAUDE.MD §0.3) — `consultar`/`actualizar` solo reciben `actorId`, no hay forma de pasar un id de usuario objetivo distinto.
- **`RegistrarTokenPushUseCase`**: `usuarioId` sale siempre del actor resuelto (X-Actor-Id), nunca del body.
- **`NotificacionService`**: además de emitir/listar/marcar leída, intenta un push best-effort (§4) tras guardar la notificación — envuelto en `try/catch` para que un fallo de Expo (hoy: `NoOpPushAdapter`, no puede fallar de verdad) nunca tumbe la escritura en la bandeja, mismo criterio fire-and-forget que `chat/repository.ts:sendExpoPushNotifications` (§0.5).

### 1.3 Infraestructura

- **REST**: ver tabla de endpoints §2.
- **Eventos** (`in/event/`): 4 listeners, uno por evento, cada uno `@Component` package-private con un único método `@ApplicationModuleListener void on(XxxEvent event)`. Se optó por **uno por evento** (no un solo listener que dispatchee por tipo) para que cada uno declare su propio mapeo a `TipoNotificacion`/copy de forma explícita y testeable en aislamiento — encargo lo dejaba a criterio de diseño.
- **Persistencia**: JPA + mappers a mano (D-28, traducción explícita caso a caso, nunca `valueOf` "mágico" por nombre — mismo criterio que `support`/`points` aunque los valores coincidan textualmente). `PreferenciaNotificacionJpaEntity` usa `@IdClass` (mismo patrón que `HistorialCoherenciaJpaEntity` de `points`) para su PK compuesta `(usuario_id, tipo)`.
- **Push** (`out/push/NoOpPushAdapter`): placeholder sin credenciales Expo reales — mismo patrón que `NoOpAlmacenamientoAdapter` de `shared` (D-34, S3). Solo loguea un `INFO` con la cantidad de tokens, **nunca** el título/cuerpo (pueden llevar PII, ej. `MENSAJE_MENTOR` — CLAUDE.MD §5.4.9).
- **Scheduler** (`PurgaNotificacionesScheduler`): cron diario 04:30 UTC, purga filas de más de 90 días. Reutiliza `@EnableScheduling` global ya declarado por `points.PointsSchedulingConfig` — no lo repite (mismo criterio documentado en `rocks.VerdugoIgnoradoScheduler`).

---

## 2. Endpoints construidos

Actor resuelto por header `X-Actor-Id` (temporal, D-29 de `users`, sin autenticación real por B-2 — mismo patrón que `points`/`phasecontracts`/`support`/`habits`/`rocks`). Autoservicio estricto en los tres controllers: nunca la bandeja/preferencias/token de otro.

### 2.1 Bandeja (`NotificacionController` — `/api/v1/notifications`)

| Método | Ruta | Repo viejo | Devuelve |
|---|---|---|---|
| GET | `/api/v1/notifications` | `GET /notifications` (preservado) | 200, `{items: [...]}` |
| PUT | `/api/v1/notifications/{id}/read` | `PUT /notifications/:id/read` (preservado) | 200, `{id, readAt}`; 404 si ajena/inexistente |
| PUT | `/api/v1/notifications/read-all` | `PUT /notifications/read-all` (preservado) | 200, `{updated}` |

### 2.2 Preferencias (`PreferenciaNotificacionController` — `/api/v1/notification-preferences`)

| Método | Ruta | Repo viejo | Devuelve |
|---|---|---|---|
| GET | `/api/v1/notification-preferences` | `GET .../notification-preferences` (P-02, preservado) | 200, `{preferences: [{type, enabled}, ...]}` (13 tipos completos) |
| PATCH | `/api/v1/notification-preferences` | `PATCH .../notification-preferences` (P-03, preservado) | 200, mismo shape que GET, recalculado |

### 2.3 Tokens push (`TokenPushController` — `/api/v1/push-tokens`)

| Método | Ruta | Repo viejo | Devuelve |
|---|---|---|---|
| POST | `/api/v1/push-tokens` | `POST /push-tokens` (CHAT-07, preservado) | 200, `{id}` |

### 2.4 Rupturas de contrato conocidas y heredadas (documentadas, no inventadas — mismo criterio que `docs/MODULO_PHASECONTRACTS.md` §4)

- **`NotificacionResponse.id`** pasa de `string` (cuid de Prisma) a `number` (bigint IDENTITY de Postgres) — el nombre del campo se preserva, el tipo JSON cambia.
- **`NotificacionResponse.type`** viaja en **español** (`TipoNotificacion` de la BD nueva: `SANTUARIO_ROTO`, `LOGRO_DESBLOQUEADO`...) — el repo viejo lo devolvía en inglés (`ACHIEVEMENT_UNLOCKED`...). Además la BD nueva tiene 13 valores contra los 8 del repo viejo (§0.4).
- **`RegistrarTokenPushRequest.platform`** ahora espera el literal Postgres en **MAYÚSCULAS** (`"IOS"`/`"ANDROID"`) — el repo viejo aceptaba minúsculas (`"ios"`/`"android"`). El controller normaliza con `toUpperCase()` antes de resolver el enum, pero un valor ya en mayúsculas es lo que realmente se espera; documentado en el javadoc de `RegistrarTokenPushRequest`.

Ninguna de las tres se puede evitar sin reintroducir el problema que la migración a la BD nueva ya resolvió (vocabulario en español, PK bigint) — coordinar con la app antes de liberar, igual que `phasecontracts` documentó para su propio caso de `fase`.

---

## 3. Autoservicio de preferencias — cómo se blindó (CLAUDE.MD §0.3)

`GestionarPreferenciasUseCase.consultar(UserId actorId)` y `.actualizar(ActualizarPreferenciasCommand)` **solo reciben el actor**. `ActualizarPreferenciasCommand` tiene exactamente dos componentes (`actorId`, `preferencias`) — no existe un segundo id de usuario que un cliente malicioso pudiera setear para tocar las preferencias de otro. El blindaje es de **firma**, no un `if` en runtime: es imposible construir un comando que apunte a otro usuario, mismo principio que el `role` ausente en `SubmitAccountRequestCommand` de `users` (CLAUDE.MD §5.3.3).

`PreferenciaNotificacionServiceTest` tiene un test de reflexión (`elComandoNoTieneCampoDeUsuarioObjetivoAparteDelActor`) que falla si algún día se agrega un campo que rompa esta garantía, más un test funcional (`esAutoservicioPorConstruccion`) que confirma que las preferencias de dos actores nunca se mezclan.

---

## 4. La prueba de punta a punta del outbox de Spring Modulith

Es el objetivo central de este módulo (encargo, primer consumidor real de eventos de dominio). `NotificationsEventOutboxIT` (`@SpringBootTest` + Testcontainers):

1. Inserta un usuario prerrequisito con `JdbcTemplate` (mismo patrón que `points.AjustePuntosPersistenceAdapterTest`).
2. Publica el evento **real** (importado directamente de `habits.api.SantuarioRotoEvent`/`rocks.api.RocaCompletadaEvent`, sin duplicar el tipo) a través de `TransactionalEventPublisherTestHelper` — un `@Component` de test con un método `@Transactional` que solo llama a `ApplicationEventPublisher.publishEvent(event)`. Esto es necesario porque `@ApplicationModuleListener` corre en fase `AFTER_COMMIT`: si el test publicara el evento desde un método marcado `@Transactional` de Spring Test (que se revierte al final), el commit nunca ocurriría y el listener jamás se dispararía.
3. Hace **poll manual** (sin Awaitility, no está en el `pom.xml`) contra `renaser.notificaciones` cada 200ms hasta 8s, esperando la fila.
4. Verifica que el `tipo` guardado sea el esperado (`SANTUARIO_ROTO`/`HITO_PROGRAMA`).

**Qué prueba esto y qué NO prueba** (documentado en el javadoc de la clase, para que quede explícito): prueba que un evento publicado con `ApplicationEventPublisher` llega al `@ApplicationModuleListener` de este módulo, pasa por `EmitirNotificacionUseCase` y queda persistido — el circuito completo del outbox (tabla `event_publication` que aporta `spring-modulith-events-jpa` vía su propia migración Flyway en el classpath, reintento, entrega async post-commit). **NO** reverifica que `habits`/`rocks` disparen estos eventos en sus propios casos de uso reales — eso es responsabilidad de sus propios tests, ya documentada en `docs/MODULO_HABITS.md`/`docs/MODULO_ROCKS.md`. Importar el evento desde otro módulo para publicarlo a mano en un test es arquitectónicamente seguro porque `habits.api`/`rocks.api` son paquetes `@NamedInterface`, no fugan tipos internos.

Se cubrieron 2 de los 4 eventos con esta IT completa (uno por módulo de origen, `habits` y `rocks`, ya que el mecanismo de outbox es el mismo para los cuatro) más los 4 con un test unitario Mockito por listener (`HabitoCompletadoNotificationListenerTest`, `RachaCompletadaNotificationListenerTest`, `SantuarioRotoNotificationListenerTest`, `RocaCompletadaNotificationListenerTest`) que confirma, sin contexto Spring, que cada uno traduce su evento al `EmitirNotificacionCommand` correcto.

---

## 5. Decisiones propias de este módulo (prefijo `DN-`, no pisa el contador `D-N` global)

| # | Decisión | Razonamiento |
|---|---|---|
| DN-1 | Mapeo evento→`TipoNotificacion`: `HabitoCompletadoEvent`→`LOGRO_DESBLOQUEADO`, `RachaCompletadaEvent`→`LOGRO_DESBLOQUEADO`, `SantuarioRotoEvent`→`SANTUARIO_ROTO` (único match 1:1 exacto), `RocaCompletadaEvent`→`HITO_PROGRAMA` | El repo viejo **nunca** notificaba la finalización de un hábito/roca/racha (verificado: solo `tickets`/`chat`/`personal-habits`/`post-program` llamaban a `emit()`, §0.1-0.5) — no hay precedente ni un tipo de `TipoNotificacion` dedicado a "hábito completado" en el baseline. Se siguió la instrucción literal del encargo ("emitir siempre salvo preferencia explícita en false", sin dejar la puerta abierta a "no emitir") y se eligió la aproximación más razonable disponible en el enum de 13 valores — **no confirmada por negocio**, ver pregunta abierta §7.1. Riesgo real explícito: `HabitoCompletadoEvent` puede disparar varias notificaciones por día por participante (ruido) |
| DN-2 | `PreferenciaNotificacionService.actualizar()` siempre hace `upsert`, nunca borra la fila cuando `enabled` vuelve a `true` (a diferencia de `profile/service.ts:patchNotificationPreferences`, que borraba para "volver al default") | Simplificación segura: la tabla nueva (`preferencias_notificacion.habilitada boolean NOT NULL DEFAULT true`) lee exactamente igual con o sin fila cuando el valor es `true` — la única razón del borrado viejo era mantener la tabla chica, no una regla de negocio. Guardar la fila explícita no cambia ningún comportamiento observable |
| DN-3 | `EmitirNotificacionUseCase` también intenta un push best-effort (vía `LoadTokenPushPort`+`PushPort`) inmediatamente después de guardar la notificación | El repo viejo separaba completamente "guardar en bandeja" (`notifications/service.ts:emit`) de "mandar push" (`chat/repository.ts:sendExpoPushNotifications`, llamado aparte solo desde `chat`) — nunca desde el mismo punto de entrada. Se decidió unificarlo acá porque `EmitirNotificacionUseCase` es ahora el único punto de entrada para "avisarle algo a un usuario", y separar el push obligaría a cada listener a resolver tokens por su cuenta. Envuelto en `try/catch` (nunca tumba la escritura), documentado como extensión propia sobre el contrato viejo, no como 1:1 |
| DN-4 | Sin paquete `api/` propio | `notifications` es sumidero terminal — nadie más lo consume hoy. Se sigue el criterio anti-sobre-ingeniería de CLAUDE.MD §4.1 (no crear infraestructura "por si acaso") |
| DN-5 | El E2E del outbox usa un `@Component` de test (`TransactionalEventPublisherTestHelper`) en el mismo paquete que los listeners de producción | Riesgo no verificado: si `ApplicationModules.verify()` (`ArchitectureTest`) escanea también el classpath de test al construir sus módulos, podría tratar este componente como parte de la estructura del módulo `notifications` — no debería romper nada (es package-private, no fuga tipos, no importa nada de otro módulo salvo `shared.event.DomainEvent`), pero no se pudo confirmar corriendo Maven (regla del encargo) |

---

## 6. Integración con `points`/`habits`/`rocks` — asíncrona, a propósito

A diferencia de `habits`→`points` y `rocks`→`points` (síncrono, misma transacción, CLAUDE.MD §9.1), `notifications` escucha los 4 eventos **de forma asíncrona vía outbox** (`@ApplicationModuleListener`). Es el diseño correcto para este caso: si la notificación falla, el hábito/roca/racha/santuario ya está confirmado — no hay razón de negocio para que un fallo de la campana revierta el progreso del aprendiz. Es exactamente el mismo criterio que ya aplicaba `notifications/service.ts:emit()` en el repo viejo (nunca lanza), llevado ahora al nivel de mecanismo (outbox con reintento) en vez de un `try/catch` manual.

---

## 7. Preguntas abiertas para el supervisor

1. **¿El mapeo de tipos de DN-1 es el correcto?** En particular, ¿debería `HabitoCompletadoEvent` generar una notificación por cada hábito completado (varias por día), o el negocio prefiere no notificar eso en absoluto (como hacía el repo viejo, que nunca lo notificaba)? Si la respuesta es "no notificar", el cambio es eliminar el llamado a `emitir()` de `HabitoCompletadoNotificationListener` (o dejarlo vacío documentando por qué), sin tocar el resto del módulo.
2. **¿Los roles permitidos en `/api/v1/notifications`, `/notification-preferences` y `/push-tokens` son correctos?** Hoy los tres son "cualquier autoservicio autenticado" (mismo criterio que `support` para tickets de soporte) — no hay gate de rol porque ninguno de los tres endpoints tiene una versión "para otro usuario". A confirmar si corresponde alguna restricción (ej. ¿un `SUSPENDED` debería seguir viendo su propia bandeja? Se asumió que sí, mismo criterio que `support` §0.2 "a suspended account can still reach support" — no confirmado para este módulo específicamente).
3. **DN-3 (push best-effort desde `EmitirNotificacionUseCase`):** ¿es la ubicación correcta, o el negocio prefiere mantener push y bandeja completamente separados como en el repo viejo? Hoy no importa (el adapter es `NoOpPushAdapter`), pero es una decisión de diseño que conviene confirmar antes de conectar Expo real.
4. **Ruptura de contrato §2.4 (`type` en español, `id` numérico, `platform` en mayúsculas):** ¿coordinar con el equipo de la app RN antes de liberar este módulo, mismo protocolo que `phasecontracts`?

---

## 8. Estado de las pruebas (escritas, no corridas — CLAUDE.MD prohíbe ejecutar Maven en este encargo)

| Tipo | Archivo | Cubre |
|---|---|---|
| Unit dominio | `NotificacionTest` | `emitir()` rechaza título/cuerpo vacíos, `marcarLeida()` idempotente, `perteneceA()` |
| Unit dominio | `PreferenciaNotificacionTest` | construcción, default `true`, validación de nulos |
| Unit dominio | `TokenPushTest` | `registrar()` rechaza token vacío, acepta plataforma nula, `reasignar()` cambia dueño sin tocar el token |
| Unit servicio (Mockito) | `NotificacionServiceTest` | preferencia default/explícita true/false, fallo de push no tumba la emisión, ventana de `listar()` (90 días/100), `marcarLeida()` idempotente y 404 ajena/inexistente, `marcarTodas()` |
| Unit servicio (Mockito) | `PreferenciaNotificacionServiceTest` | completa con default, persiste cada item, **CLAUDE.MD §0.3**: autoservicio por construcción + test de reflexión del comando |
| Unit servicio (Mockito) | `TokenPushServiceTest` | construye el agregado y delega en el puerto de upsert |
| Unit listener (Mockito, sin Spring) | `HabitoCompletadoNotificationListenerTest`, `RachaCompletadaNotificationListenerTest`, `SantuarioRotoNotificationListenerTest`, `RocaCompletadaNotificationListenerTest` | cada uno traduce su evento al `EmitirNotificacionCommand` correcto (tipo, usuario, contenido) |
| Integración Testcontainers | `NotificacionPersistenceAdapterTest` | round-trip, traducción de los 13 tipos, ventana+límite de bandeja, `marcarLeida` atómico y aislado por usuario, `marcarTodasLeidas` idempotente, `purgarAnterioresA` |
| Integración Testcontainers | `PreferenciaNotificacionPersistenceAdapterTest` | sin fila → vacío, upsert inserta y luego actualiza la MISMA fila (no duplica), traducción de los 13 tipos |
| Integración Testcontainers | `TokenPushPersistenceAdapterTest` | upsert inserta nuevo, upsert con token ya existente reasigna sin duplicar fila, `tokensDe` trae todos los dispositivos de un usuario |
| Integración E2E (outbox) | `NotificationsEventOutboxIT` (+ `TransactionalEventPublisherTestHelper`) | §4 — publica `SantuarioRotoEvent`/`RocaCompletadaEvent` reales y confirma que llegan a la bandeja |

**Lo que quedó explícitamente sin verificar** (CLAUDE.MD §0.2): no se ejecutó `./mvnw clean test` (prohibido en este encargo) — el supervisor debe correrlo. Puntos de mayor riesgo si algo falla:
- **DN-5**: si el `@Component` de test `TransactionalEventPublisherTestHelper` interfiere con `ArchitectureTest.modulesDoNotLeakInternals()` (`ApplicationModules.verify()`).
- Los `@Modifying @Query` JPQL de `SpringDataNotificacionRepository` (`marcarLeida`, `marcarTodasLeidas`, `deleteByCreadoEnBefore`) — primer uso de `@Modifying` sin `clearAutomatically` en este repo (el único precedente, `points.SpringDataRankingAprendizRepository`, sí lo usa); se analizó a mano que ninguno de los tests de este módulo depende de que el contexto de persistencia se limpie después del bulk update (nunca se re-carga como entidad managed el mismo id tras un `@Modifying`), pero no se confirmó en vivo.
- El `@IdClass` de `PreferenciaNotificacionJpaEntity` — mismo patrón que `HistorialCoherenciaJpaEntity` de `points`, pero con un `enum` como parte de la PK compuesta (el precedente usa `UUID`+`LocalDate`) — combinación no probada antes en este repo.
- Si `@ApplicationModuleListener` efectivamente se dispara dentro de la ventana de 8s del poll manual de `NotificationsEventOutboxIT` — depende de la configuración de `TaskExecutor` async que Spring Boot/Modulith resuelven automáticamente; no se pudo confirmar en vivo.

**Bitácora de errores:** no se encontró ningún error/bug real de entorno durante la construcción (solo decisiones de diseño, documentadas como DN-N arriba) — no se agregó una entrada artificial a `docs/BITACORA_ERRORES.md`.

---

## 9. Estado / checklist DoD

- [x] `domain/` plano por agregado (`notificacion/`, `preferencia/`, `tokenpush/`), sin imports de Spring/JPA/Jackson
- [x] Tests unitarios de dominio sin Spring/Postgres
- [x] Casos de uso con comando self-validating (`EmitirNotificacionCommand`, `ActualizarPreferenciasCommand`, `RegistrarTokenPushCommand`)
- [x] Controller tonto: sin repositorios, sin `@Transactional`, sin reglas de negocio (3 controllers, todos verificados)
- [x] DTO de salida como proyección explícita (`NotificacionResponse`, `PreferenciasResponse`, `TokenPushResponse`)
- [x] Sin migración Flyway nueva (las 3 tablas ya están completas en el baseline)
- [x] Tests de integración con Testcontainers para los 3 agregados
- [x] **Test de integración E2E del outbox de Modulith** — el objetivo central del encargo (§4)
- [x] Pruebas de seguridad §0.3: `GestionarPreferenciasUseCase` autoservicio por construcción + test de reflexión (§3)
- [ ] Test de reflexión `@RequiresPermission`/`@PublicEndpoint` — no aplica, mecanismo no existe todavía (bloqueado por B-5/R-2 de `users`, igual que en todos los módulos construidos hasta ahora)
- [ ] `ArchitectureTest` — no ejecutado por este agente (regla del encargo: no correr Maven). Riesgo real explícito en DN-5
- [ ] `./mvnw clean test` — no ejecutado, mismo motivo. El supervisor lo corre
- [x] Avance documentado en este archivo, con honestidad explícita de lo que quedó sin verificar (§8)
- [ ] Bitácora de errores — no se encontró ningún error/bug real de entorno (ver cierre de §8)

**Honestidad de alcance:** todo lo pedido en el encargo está construido — los 3 agregados completos (dominio/aplicación/persistencia/REST), los 4 listeners de eventos con el mecanismo de outbox probado de punta a punta contra 2 de los 4 eventos reales (más los 4 cubiertos a nivel unitario), el scheduler de retención, y el adapter de push placeholder. Lo que falta es exclusivamente lo que dependía de correr Maven (prohibido en este encargo) o de infraestructura que no existe todavía en `shared/`/`users/` (bloqueada, no inventada, igual que en el resto de módulos de este lote). La decisión de mayor impacto de negocio no confirmada es DN-1 (qué tipo de notificación le corresponde a cada uno de los 4 eventos, y si `HabitoCompletadoEvent` debería notificar en absoluto) — documentada como pregunta abierta §7.1, no asumida en silencio.

## Auditoría de arquitectura (2026-08-28) — agente automático

Auditoría de solo lectura del código real bajo `src/main/java/com/renaser/os/notifications/`, contra las reglas de CLAUDE.md §5.1/§5.1.2/§5.4. No se ejecutó `./mvnw` (fuera de alcance del encargo). Alcance: 63 archivos `.java`, 1776 líneas totales.

**Resultado general: sin violaciones de arquitectura detectadas.** Es uno de los módulos más limpios auditados hasta ahora.

### 1. `domain/` (3 agregados: `notificacion/`, `preferencia/`, `tokenpush/`)

Correctamente organizado por agregado (regla §5.1.2 "subcarpeta por agregado, nunca por capa") — 3 agregados independientes, cada uno con su propia identidad y ciclo de vida:
- `domain/model/notificacion/`: `Notificacion.java` (83 líneas), `TipoNotificacion.java` (enum, 23 líneas)
- `domain/model/preferencia/`: `PreferenciaNotificacion.java` (record, 34 líneas)
- `domain/model/tokenpush/`: `TokenPush.java` (64 líneas), `PlataformaPush.java` (enum, 7 líneas), `TokenPushId.java` (record, 25 líneas)

Verificado: **cero imports** de `org.springframework.*` o `jakarta.persistence.*` en todo `domain/` (grep vacío). Lombok usado correctamente — solo `@Getter`/`@AllArgsConstructor(PRIVATE)`/`@EqualsAndHashCode(of="id")`/`@Accessors(fluent=true)` en `Notificacion.java` y `TokenPush.java`; nada de `@Data`/`@Setter`/`@NoArgsConstructor` público. `toString()` acotado sin PII (`Notificacion.java:80-82`, `TokenPush.java:61-63`) — cumple §5.4.5/§5.4.9. `domain/` no loguea (cero imports de `org.slf4j` ahí) — cumple §5.4.9.

Factory methods con nombre de intención (`emitir`, `rehydrate`, `registrar`, `reasignar`, `marcarLeida`) en vez de setters públicos; validación de invariantes vía `requireNotBlank`/`Objects.requireNonNull` en los factory methods, no en constructores Lombok — patrón correcto descrito en CLAUDE.md §5.4.5.

### 2. Listeners de evento (`infrastructure/adapter/in/event/`) — el foco del encargo

Los 4 listeners (`HabitoCompletadoNotificationListener.java:28-42`, `RachaCompletadaNotificationListener.java:13-27`, `RocaCompletadaNotificationListener.java:13-26`, `SantuarioRotoNotificationListener.java:14-28`) son "adaptador tonto" de manual: cada uno usa `@ApplicationModuleListener`, reacciona a **un** evento con **una** llamada a `EmitirNotificacionUseCase.emitir(...)`, sin lógica de negocio embebida, sin `@Transactional` propio, sin orquestar más de un caso de uso. Ninguno supera 42 líneas.

Nota de diseño (no defecto de código, ya documentada como DN-1 en §7.1 de este mismo doc): 3 de los 4 eventos mapean a `TipoNotificacion.LOGRO_DESBLOQUEADO`/`HITO_PROGRAMA` por aproximación, sin tipo dedicado confirmado por negocio — riesgo de producto, no de arquitectura.

### 3. Adaptadores REST (`infrastructure/adapter/in/rest/`)

Los 3 controllers (`NotificacionController.java`, `PreferenciaNotificacionController.java`, `TokenPushController.java`) cumplen la regla de "controller tonto": sin inyección de repositorios ni puertos `out`, sin `@Transactional`, sin `if` de negocio, cada método invoca un único caso de uso vía puerto `in`. `PreferenciaNotificacionController.actualizar` (líneas 34-43) hace un `.map()` de DTO→`ItemPreferencia`, que es mapeo de frontera web permitido a mano (regla 6), no lógica de negocio.

DTOs de salida (`NotificacionResponse`, `PreferenciasResponse`, `TokenPushResponse`) son proyecciones explícitas escritas a mano, no serialización de entidad — cumple §5.4.1/§8.

### 4. Persistencia (`infrastructure/adapter/out/persistence/`)

Mapeo `JpaEntity ↔ dominio` escrito **a mano** (no MapStruct) en los 3 pares (`NotificacionPersistenceMapper`, `PreferenciaNotificacionPersistenceMapper`, `TokenPushPersistenceMapper`) — no es una violación (la regla dice "MapStruct SOLO ahí", no "MapStruct obligatorio ahí"), pero es una desviación de la guía de CLAUDE.md §5.4.5 que recomienda MapStruct justamente para este mapeo plano y repetitivo; los propios mappers documentan la razón (`PreferenciaNotificacionPersistenceMapper.java:9-10`: "traducción explícita caso a caso, nunca `valueOf` mágico"), así que parece deliberado, no un descuido.

`@Entity` con `@Data`/`@NoArgsConstructor`/`@AllArgsConstructor` correctamente confinado a `*JpaEntity.java` en `adapter/out/persistence/`, nunca en `domain/` — cumple §5.4.5.

`@Modifying @Query` en `SpringDataNotificacionRepository.java:25-37` (`marcarLeida`, `marcarTodasLeidas`, `deleteByCreadoEnBefore`) sin `clearAutomatically` — ya señalado como riesgo abierto por el propio doc (§8, DN-bullet sobre `@Modifying`), no es un hallazgo nuevo de esta auditoría.

### 5. Nombres y tamaños

- Sin nombres prohibidos: `grep -rniE "class .*(Util|Helper|Manager|Processor|Info)\b"` sobre todo el módulo → 0 resultados.
- Puertos nombrados por intención de negocio (`LoadNotificacionPort`, `SaveNotificacionPort`, `PushPort`, `UpsertTokenPushPort`, `LoadTokenPushPort`), nunca por tecnología — cumple §5.4.8.
- Archivo más grande: `NotificacionService.java` con 114 líneas (techo 300) y su método más largo, `emitir` (líneas 60-73), 14 líneas (techo 40). Ningún archivo ni método del módulo se acerca a los techos de CLAUDE.md §5.1.2/tabla de tamaños.
- `ActorNotificacionesGuard` (42 líneas) está correctamente extraído como servicio compartido por los 3 servicios del módulo, evitando duplicar el guard de actor tres veces — buen ejemplo de SRP/DIP.

### 6. Observación estructural (no defecto): convención `infrastructure/adapter/` vs. `adapter/`

El árbol real usa `notifications/infrastructure/adapter/{in,out}/...`, un nivel más anidado que el árbol de ejemplo en CLAUDE.md §5.1 (`habits/adapter/{in,out}/...`, sin `infrastructure/` intermedio). Se verificó que **los 14 módulos del repo** siguen esta misma convención `infrastructure/adapter/` de forma consistente (incluido `users`, el módulo de referencia) — es una convención de proyecto ya asentada, no una desviación introducida por `notifications`. No se marca como hallazgo porque no rompe la regla de dependencia (`domain/` sigue sin saber de `adapter/`), solo difiere del diagrama ilustrativo del documento madre.

### 7. `api/` — módulo sin paquete público propio

`notifications` no tiene paquete `api/` (a diferencia de los otros 13 módulos). Verificado que es correcto para su rol: `notifications` es mayormente consumidor de eventos de otros módulos (no expone casos de uso para que otros los llamen síncronamente) — la única implementación "hacia afuera" es `NotificacionesNoLeidasService implements NotificacionesNoLeidasFinder`, donde la interfaz vive en `points.api` (Dependency Inversion: `points` define el puerto, `notifications` lo implementa, evitando que `points` dependa de `notifications`). Ningún otro módulo importa `com.renaser.os.notifications.*` — confirmado por grep sobre todo `src/main/java`. No hace falta `api/` mientras esto no cambie.
