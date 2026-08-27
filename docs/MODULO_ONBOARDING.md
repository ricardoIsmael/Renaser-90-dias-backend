# Módulo `onboarding` — estado, reglas extraídas, decisiones y preguntas abiertas

**Fecha:** 2026-08-25
**Ola:** 4 (`chat` + `evidence` + `onboarding`, `docs/PLAN_DE_MODULOS.md`)
**Documentos hermanos:** `CLAUDE.MD` (cómo), `docs/MODULOS_A_AVANZAR.md` (qué y en qué orden), `docs/PLAN_DE_MODULOS.md` (semilla de este módulo)

**Fuente de trabajo, para que quede explícito:** este módulo se construyó ÚNICAMENTE a partir de (1) el esquema real en `src/main/resources/db/migration/V1__baseline_renaser.sql`, (2) `CLAUDE.MD` y los docs de `docs/`, y (3) los módulos Java ya construidos (`users`, `points`, `phasecontracts`, `shared`) como plantilla estructural. **No se leyó ni se usó como referencia ningún código del backend viejo en Next.js** (hay varias copias en esta máquina, fuera de este repo — no se tocaron). Esto es intencional: es una reconstrucción desde cero sobre el esquema y sobre clean code/SOLID, no una migración literal — así que ninguna regla de negocio de este documento viene de "así lo hacía el viejo backend".

---

## 1. Estado actual

🔄 **Construido, sin ejecutar `./mvnw clean test` todavía** (regla del encargo: el agente constructor no corre Maven — lo corre quien supervisa).

- [x] `domain/` por agregado (`estado/`, `cuestionario/`, `respuesta/`, `media/`, `grabacionv90/`), sin imports de Spring/JPA/Jackson/jakarta.validation (verificado con `grep`, no con el compilador — ver abajo)
- [x] Tests unitarios de dominio: `EstadoOnboardingTest`, `RespuestaTest` (EAV `un_solo_valor` + upsert de dominio), `GrabacionV90Test` (máquina de estados IA, 3 intentos → REVISION_MANUAL), `MediaOnboardingTest`, `PreguntaTest`
- [x] Casos de uso con comando self-validating (`SelfValidating.validateConstructorArgs`)
- [x] Controllers tontos (`X-Actor-Id`, patrón de `users`/`phasecontracts`)
- [x] DTOs de salida como proyección explícita (wire en inglés, dominio en español — mismo criterio que `phasecontracts`)
- [x] Tests de aplicación con Mockito: `RespuestaServiceTest`, `MediaServiceTest`, `GrabacionV90ServiceTest`, `EstadoOnboardingServiceTest`, `CuestionarioServiceTest`
- [x] Tests de seguridad: actor suspendido → `NotAuthorizedException` (403) en los 5 servicios; una grabación V90 de OTRO usuario → 403, no 404 (evita la fuga de existencia de E-30 en la bitácora); actor inexistente → 404
- [x] IT con Testcontainers: `EstadoOnboardingPersistenceAdapterTest` (roundtrip incluyendo `progreso_flujo` jsonb), `RespuestaPersistenceAdapterTest` (CHECK `un_solo_valor` y UNIQUE `(usuario_id, pregunta_id)` reales contra Postgres, jsonb `valor_json`), `GrabacionV90PersistenceAdapterTest` (UNIQUE `(usuario_id, fase, eje, indice)` real, jsonb `feedback_ia`), `MediaPersistenceAdapterTest` (ownership de `porIdYUsuario`, jsonb `metadatos`), `CuestionarioPersistenceAdapterTest` (orden, los 11 valores de `tipo_pregunta_onboarding`, condicionales), `ConsultarActorPersistenceAdapterTest` (delegación real a `users.api`)
- [ ] `ArchitectureTest` — no ejecutado por este agente (regla del encargo). Se verificó a mano con `grep` que ningún archivo de `onboarding/domain` ni `onboarding/application` importa `org.springframework.*`/`jakarta.persistence.*`/`jakarta.servlet`/`org.springframework.web`/`org.springframework.http`, y que ningún controller importa `ports.out`/`adapter.out`/`org.springframework.data`/`jakarta.persistence` — pero esto NO reemplaza correr el test real
- [ ] `./mvnw clean test` — no ejecutado por este agente (regla del encargo)
- [ ] Test de reflexión `@RequiresPermission`/`@PublicEndpoint` — **no aplica todavía**: ese mecanismo no existe en ningún módulo del repo (bloqueado por B-5/R-2 de `users`, igual que en `points`/`phasecontracts`)
- [x] Avance documentado acá

---

## 2. Riesgo técnico principal: mapeo de columnas `jsonb`

Este es el punto de mayor incertidumbre del módulo porque **ningún otro módulo construido hasta ahora había necesitado persistir una columna `jsonb` de verdad** (el resto normalizó sus `jsonb` candidatos en filas relacionales, ej. `ReglaRecordatorioEventoJpaEntity` en `calendar`). El baseline marca varias columnas de `onboarding` como `jsonb` **JUSTIFICADO** a propósito (`progreso_flujo`, `config_escala`, `reglas_validacion`, `valor_json`, `metadatos`, `feedback_ia`) — datos opacos que el backend transporta pero no interpreta.

**Decisión tomada:** mapear cada una como `String` (JSON crudo) tanto en el dominio como en la entidad JPA, con:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private String miColumnaJsonb;
```

Esto usa el soporte nativo de Hibernate 6+ para JSON (`org.hibernate.type.SqlTypes.JSON`, ya en el classpath — se usa `SqlTypes.NAMED_ENUM` en varios módulos existentes, así que el import está probado). El comportamiento documentado de Hibernate para este mapeo es: cuando el tipo Java del atributo es `String`, Hibernate trata el contenido como JSON ya serializado y lo pasa tal cual al driver JDBC — no lo re-serializa con Jackson (que lo envolvería en comillas, corrompiendo el valor). **Esto no se pudo verificar compilando ni corriendo un test contra Postgres real en esta sesión** (regla del encargo: no correr Maven). Se escribió `EstadoOnboardingPersistenceAdapterTest.guardaYRecuperaConJsonbIntacto` y equivalentes en `respuesta`/`grabacionv90`/`media` específicamente para que esto se vea en el primer `./mvnw clean test` — **si algo del mapeo jsonb está mal, estos son los tests que van a fallar primero**, y el diagnóstico más probable (por E-15 en la bitácora, mismo tipo de problema con enums nativos) sería un mensaje de Postgres tipo `column "..." is of type jsonb but expression is of type character varying`.

---

## 3. Qué se construyó

```
onboarding/
├── package-info.java                          @ApplicationModule("Onboarding")
├── api/
│   ├── package-info.java                      @NamedInterface("api")
│   └── OnboardingEstadoFinder.java             completado(usuarioId), pactoFase1Firmado(usuarioId)
├── domain/model/
│   ├── estado/EstadoOnboarding.java            agregado raíz 1:1 con usuarios (PK = usuario_id)
│   ├── estado/HitoOnboarding.java               TERMINOS | PACTO | PACTO_FIRMADO | ROCAS_SYNC
│   ├── cuestionario/{Seccion,Pregunta,OpcionPregunta,TipoPreguntaOnboarding}.java   catálogo, solo lectura
│   ├── respuesta/Respuesta.java                 EAV tipado, un_solo_valor, upsert de dominio
│   ├── media/{MediaOnboarding,ClaseMedia}.java  registro de archivo ya subido
│   └── grabacionv90/{GrabacionV90,EstadoIAv90}.java   9 audios V90, máquina de estados IA propia
├── application/
│   ├── ports/in/{estado,cuestionario,respuesta,media,grabacionv90}/   8 casos de uso
│   ├── ports/out/{estado,cuestionario,respuesta,media,grabacionv90,actor}/   9 puertos
│   └── services/{EstadoOnboardingService, CuestionarioService, RespuestaService, MediaService, GrabacionV90Service}.java
└── infrastructure/adapter/
    ├── in/rest/{estado,cuestionario,respuesta,media,grabacionv90}/    5 controllers, ver §5
    ├── out/persistence/{estado,cuestionario,respuesta,media,actor}/  JpaEntity + mapper a mano + adapter
    ├── out/ia/NoOpValidacionIAAdapter.java       SIN IA real, ver §4
    └── out/async/{OnboardingAsyncConfig, DespacharValidacionV90Adapter}.java   @EnableAsync + @Async, ver §6
```

---

## 4. SIN integración de IA real (decisión explícita del encargo)

`ValidacionIAPort` (puerto propio de este módulo, **no compartido con `evidence`** — conceptualmente distinto: valida audio/transcripciones V90, no evidencia diaria; y `evidence` se construye en paralelo, puede no existir todavía) tiene un único adaptador, `NoOpValidacionIAAdapter`, que **siempre** devuelve `NO_DISPONIBLE` y loguea `WARN` — mismo estilo que `shared.infrastructure.storage.NoOpAlmacenamientoAdapter`.

**Consecuencia real, sin ambigüedad:** el contrato async (`ValidarV90UseCase`: `POST .../validation` → 202 inmediato, `GET .../validation` → polling) está completo y funciona, pero como la IA nunca responde de verdad, **toda grabación V90 termina en `REVISION_MANUAL` después de 3 intentos**. No hay ninguna validación automática funcionando hoy — cuando se conecte Gemini de verdad (Spring AI `ChatClient`, CLAUDE.MD §7), el único archivo que hace falta reemplazar es `NoOpValidacionIAAdapter` por un adaptador real que implemente el mismo `ValidacionIAPort`; el resto del módulo (dominio, casos de uso, controllers) no cambia.

---

## 5. Endpoints construidos

Base `/api/v1/onboarding`. Todos protegidos, todos con `@RequestHeader("X-Actor-Id")` — el `usuarioId` de todo el módulo ES el actor (ver D-O2 abajo, no hay concepto de "actuar en nombre de otro" en este alcance).

| Método | Ruta | Caso de uso |
|---|---|---|
| GET | `/questionnaire?flow=` | `ObtenerCuestionarioUseCase` |
| GET | `/state` | `ObtenerEstadoOnboardingUseCase` (crea la fila si es la primera vez) |
| PUT | `/state` | `AvanzarEstadoUseCase` |
| POST | `/milestones` | `AceptarHitoOnboardingUseCase` |
| POST | `/complete` | `CompletarOnboardingUseCase` |
| POST | `/answers` | `GuardarRespuestaUseCase` (upsert) |
| POST | `/media/upload-url` | `ObtenerUrlSubidaMediaUseCase` |
| POST | `/media` | `RegistrarMediaUseCase` |
| POST | `/v90-recordings` | `RegistrarGrabacionV90UseCase` (upsert por slot) |
| GET | `/v90-recordings` | `ListarGrabacionesV90UseCase` |
| POST | `/v90-recordings/{id}/validation` | `ValidarV90UseCase.solicitarValidacion` (202) |
| GET | `/v90-recordings/{id}/validation` | `ValidarV90UseCase.consultarEstado` (polling) |

**No se verificó contra `docs/API_CONTRACT.md`** — no se encontró una sección de onboarding en ese documento al momento de construir esto; si existe y difiere, es una desviación a resolver, no algo ya reconciliado.

---

## 6. Decisiones propias de este módulo

- **D-O1 — Mapeo `TipoPreguntaOnboarding` → slot de valor EAV.** El baseline no dice explícitamente qué columna de `respuestas_onboarding` usa cada uno de los 11 tipos de pregunta — es una consecuencia forzada de que el esquema EAV solo tiene 5 slots. Se documentó el mapeo completo en el javadoc de `Respuesta` (TEXTO/AREA_TEXTO/FECHA/SELECCION_UNICA → `valorTexto`, NUMERO → `valorNumero`, ESCALA → `valorEscala`, SELECCION_MULTIPLE → `valorJson`, CASILLA → `valorBooleano`, AUDIO/FIRMA/ARCHIVO → solo media). **No es una regla de negocio inventada en el sentido de CLAUDE.MD §0.6** (no hay una alternativa de negocio distinta posible dado el esquema), pero se señala igual porque nadie del equipo la confirmó explícitamente — si la intención real es otra, es un cambio acotado a `Respuesta.slotEsperado()`.
- **D-O2 — Este módulo es estrictamente self-service: `usuarioId` == actor.** No existe un mentor/admin actuando "en nombre de" un aprendiz en ningún caso de uso — todos los endpoints usan el `X-Actor-Id` como el dueño del recurso, sin un segundo parámetro de "usuario objetivo". Es la simplificación explícita que pide el encargo ("restringí a 'solo el propio usuario' y documentalo"). Si en el futuro un mentor necesita ver el progreso de onboarding de su aprendiz (para acompañamiento), hace falta un caso de uso nuevo con chequeo de scope de mentor (mismo patrón que `phasecontracts` con `ROLES_PUEDEN_CONSULTAR`) — no está construido.
- **D-O3 — `ObtenerEstadoOnboardingUseCase.obtener()` crea la fila si no existe (no hay un endpoint separado "iniciar onboarding").** El primer `GET /state` de un usuario nuevo inicializa `estado_onboarding` con `EstadoOnboarding.iniciar()`. Alternativa descartada: exigir un `POST /state/start` explícito — no había ninguna señal en el encargo de que hiciera falta ese paso extra, y el criterio "abrir la pantalla arranca el onboarding" es el más simple que no inventa un concepto de negocio nuevo.
- **D-O4 — `CompletarOnboardingUseCase` es una acción EXPLÍCITA, sin precondición automática.** Ver pregunta abierta Q-O1. `marcarCompletado()` es idempotente (completar dos veces conserva el primer `completadoEn`).
- **D-O5 — `HitoOnboarding.aceptarHito()` es re-aceptable: cada llamada pisa el timestamp con el momento actual.** No hay confirmación de que "aceptar" deba ser de una sola vez o irreversible — se eligió el comportamiento menos sorprendente (repetir la acción simplemente actualiza cuándo fue la última vez), documentado para que se corrija si el negocio quiere lo contrario (ej. rechazar un segundo intento de aceptar `PACTO_FIRMADO`).
- **D-O6 — `MediaOnboarding.rutaNueva()` NO es determinística** (a diferencia de `ContratoFase.rutaFirma()` en `phasecontracts`, que sí lo es). Un aprendiz puede resubir audio para la misma pregunta (reintento de grabación), así que cada subida necesita su propio nombre de archivo — se generó con un UUID aleatorio en el dominio, mismo criterio que `ContratoFaseId.newId()`.
- **D-O7 — `GrabacionV90.marcarGrabada()` (re-grabado) reinicia el veredicto de IA a PENDIENTE con 0 intentos.** Es una grabación distinta; conservar el veredicto de la anterior sería engañoso. No confirmado por nadie del equipo, pero es la única interpretación consistente con "cada grabación se valida a sí misma".
- **D-O8 — `ValidarV90UseCase` usa `@Async` real (hilo separado, virtual thread vía `spring.threads.virtual.enabled=true`), no una simulación síncrona.** Se separó el disparo (`DespacharValidacionV90Port`, puerto `out`) del trabajo real (`ProcesarValidacionV90UseCase`, puerto `in`) en dos beans distintos — necesario porque `@Async` de Spring no funciona con auto-invocación (una llamada `this.metodo()` no pasa por el proxy). Se agregó `OnboardingAsyncConfig` con `@EnableAsync`, que no existía en ningún otro módulo — es la primera vez que se habilita en el repo. **Riesgo:** si algún otro módulo (`evidence`, construido en paralelo) también agrega su propio `@EnableAsync`, no debería haber conflicto (Spring lo tolera bien: infraestructura de rol `ROLE_INFRASTRUCTURE`), pero no se pudo verificar arrancando el contexto completo.
- **D-O9 — Catálogo (`secciones_onboarding`/`preguntas_onboarding`/`opciones_pregunta`) es SOLO LECTURA en este alcance.** No hay casos de uso para crear/editar preguntas — las tablas quedan vacías (sin seeds, fase futura de migración de datos, explícitamente fuera de alcance del encargo). `LoadCuestionarioPort` no tiene contraparte `Save`.
- **D-O10 — `preguntas_onboarding.reglas_validacion`/`config_escala` se exponen como JSON opaco al cliente, sin intérprete en Java.** El motor de formularios que evalúa condicionales/validaciones vive en el cliente móvil — es una decisión explícita del encargo, no una limitación descubierta acá.
- **D-O11 — Todos los servicios exigen `ConsultarActorPort` (existe + no suspendido) antes de cualquier operación**, incluida la lectura del catálogo (que no es específica de un usuario) — por consistencia con "todo endpoint protegido" de CLAUDE.MD, aunque el catálogo en sí no tenga datos personales.

---

## 7. Preguntas abiertas (CLAUDE.MD §0.6: no se inventan, se preguntan)

- **Q-O1 — ¿Cuál es el criterio real de "onboarding completo"?** ¿Todas las preguntas `requerida=true` respondidas? ¿Además el Pacto de Fase I firmado (`HitoOnboarding.PACTO_FIRMADO`)? ¿Además las 9 grabaciones V90 con veredicto (aunque hoy siempre caigan en `REVISION_MANUAL`, ver §4)? Nadie lo confirmó — `CompletarOnboardingUseCase` es una acción explícita sin ninguna de estas validaciones (D-O4). Si la respuesta real tiene precondiciones, hay que agregarlas a `EstadoOnboarding.marcarCompletado()` (o a un caso de uso previo que las verifique).
- **Q-O2 — ¿Un mentor/admin puede ver o intervenir el onboarding de un aprendiz?** Hoy es estrictamente self-service (D-O2). Si hace falta acompañamiento de mentor, hay que definir qué puede ver (solo estado/progreso, o también respuestas con contenido personal) y qué rol.
- **Q-O3 — ¿`aceptada_en` de `respuestas_onboarding` es específicamente para preguntas `CASILLA`, o tiene otro significado?** Se interpretó (D-O1 extendido) como "el momento en que una pregunta tipo checkbox de aceptación se marcó en `true`" — es la única lectura que le encontramos al nombre de la columna en el contexto de una tabla EAV genérica, pero no está confirmado.
- **Q-O4 — ¿El Pacto de Fase I (`HitoOnboarding.PACTO_FIRMADO`) necesita una firma real (imagen SVG, como `contratos_fase` de `phasecontracts`) o solo un checkbox de aceptación?** El esquema de `estado_onboarding` solo tiene un timestamp (`pacto_firmado_en`), sin columna de bucket/ruta como `contratos_fase`. Si necesita firma gráfica, el flujo sería: subir la firma como `MediaOnboarding` (clase `FIRMA`) y DESPUÉS llamar `AceptarHitoOnboardingUseCase(PACTO_FIRMADO)` — los dos casos de uso ya existen por separado, pero no hay nada que los ate entre sí (ej. exigir que exista una `MediaOnboarding` de clase FIRMA antes de aceptar el hito). No se agregó esa validación por no inventarla.
- **Q-O5 — ¿`ValidarV90UseCase` debería exigir que las 9 grabaciones existan antes de permitir completar el onboarding, o cada una se valida independiente sin relación con el resto del flujo?** Ver Q-O1.

---

## 8. Qué quedó explícitamente sin cubrir

- **Integración de IA real** (Gemini/Spring AI `ChatClient`) — decisión explícita del encargo, ver §4.
- **Seeds del catálogo** (secciones/preguntas/opciones reales del cuestionario V90/6Ps) — fase futura de migración de datos, explícitamente fuera de alcance.
- **CRUD admin del catálogo** — no hay caso de uso para crear/editar preguntas (D-O9).
- **`@RequiresPermission`/`@PublicEndpoint` + test de reflexión** — el mecanismo no existe en el repo todavía (B-5/R-2 de `users`), igual que en `points`/`phasecontracts`.
- **Verificación de `./mvnw clean test` y `ArchitectureTest`** — no ejecutados por este agente (regla del encargo). El riesgo más probable de que algo falle es el mapeo `jsonb` (§2).
- **Contrato de mentor/scope ampliado** — ver Q-O2.
- **Atadura entre `MediaOnboarding` (clase FIRMA) y `HitoOnboarding.PACTO_FIRMADO`** — ver Q-O4.

## 9. Dashboard admin (gap #8 de docs/PLAN_INTEGRACION_FRONTEND.md) — 2026-08-26

`GET /api/v1/admin/onboarding/dashboard`: agregado de cuántos aprendices tienen onboarding
iniciado/completado/con Pacto de Fase I firmado, más el desglose de grabaciones V90 por
estado de validación IA (`PENDIENTE`/`PROCESANDO`/`REVISION_MANUAL`/`APROBADA`/`RECHAZADA`).

- **Vive DENTRO de `onboarding`**, no en `users`: toda la agregación es sobre las tablas
  propias del módulo (`estado_onboarding`, `grabaciones_v90`) — no hay razón para que otro
  módulo la posea. Se agregaron dos métodos nuevos, ambos de solo lectura: `LoadEstadoOnboardingPort.contarResumen()`
  y `LoadGrabacionV90Port.contarPorEstado(EstadoIAv90)`.
- **El gate ADMIN/ALCHEMIST se resuelve contra `users.api.UserSummaryFinder` directo**, NO
  contra `onboarding.application.ports.out.actor.ConsultarActorPort` (el puerto local que
  ya usan los 5 servicios existentes de este módulo): ese puerto solo expone
  `(id, suspendido)`, sin rol — agregarle `role` hoy habría tocado los 6 archivos que ya lo
  consumen (5 servicios + su adaptador) sin necesidad, para un caso de uso que no comparte
  la forma "self-service" del resto del módulo (D-O2: acá el actor SÍ actúa sobre datos que
  no son suyos, es un panel admin).
- **Total de aprendices activos**: `users.api.ParticipacionProgramaFinder.usuariosActivosConRol(Set.of(TRAINEE))`
  — mismo puerto público que ya consumen otros módulos, sin query nativa propia.
- **Sin caché**: es un agregado sobre counts (`COUNT(*)` indexado por PK/estado), no un
  join costoso — no se vio necesidad de Caffeine para un endpoint de panel admin de baja
  frecuencia (CLAUDE.MD §3: el objetivo de <1ms es para el hot path del aprendiz, no para
  paneles operativos).
- **No se construyó**: test `@WebMvcTest` del controller (cobertura de autorización a nivel
  de servicio, ver `OnboardingDashboardServiceTest`); breakdown de onboarding "en curso" por
  `flujoActual`/`seccionActual` (son strings libres del cliente, no un enum de dominio —
  agruparlos sería inventar una taxonomía no confirmada, CLAUDE.MD §0.6).
