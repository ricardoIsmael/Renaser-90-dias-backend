# Módulo `phasecontracts` — Pacto de Sangre por fase (90 días)

**Fecha:** 2026-08-24
**Depende de:** `shared` (Clock, SelfValidating, AlmacenamientoPort, NotAuthorizedException). **NO depende de `users` en código** — ver §4 (deuda técnica) para el porqué, que es una decisión de diseño real, no un descuido.
**Tabla propia:** `renaser.contratos_fase` (ya existe en `V1__baseline_renaser.sql:938-947`, sin cambios).

---

## 0. Paso 0 — qué dice el código viejo (obligatorio antes de codificar, D-33)

Repo viejo clonado en el scratchpad de esta sesión. Archivos leídos completos:

- `src/lib/phase.ts` (32-53): `phaseForDay`, `PHASE_2_STARTS_DAY`/`PHASE_3_STARTS_DAY`/`PHASE_4_STARTS_DAY`, `PHASE_START_DAYS`.
- `src/features/phase-contracts/service.ts` (1-108): `CONTRACT_UNLOCK_DAY`, `PHASE_LABEL`, `getPendingContract`, `signContract`, `requireActiveTrainee`.
- `src/features/phase-contracts/repository.ts` (1-33): queries Prisma (`findTraineeProfileByUserId`, `findPhaseContract`, `createPhaseContract`).
- `src/features/phase-contracts/schema.ts` (1-17): `SignPhaseContractInput` (`signatureBucket`, `signatureUrl`), `PendingPhaseContractResponse`.
- `src/features/phase-contracts/__tests__/service.test.ts` (1-108): los casos de test ya escritos en el repo viejo — se usaron para verificar que mi entendimiento de las reglas coincide con lo que el propio equipo validó.
- `src/app/api/v1/phase-contracts/route.ts` (POST) y `.../pending/route.ts` (GET): rutas Next.js, con los checks de rol (`requireRole`).
- `docs/API_CONTRACT.md` del repo viejo: **no menciona phase-contracts en absoluto** — el endpoint existía en código y funcionaba, pero nunca se documentó ahí. No hay `docs/FEATURE_PHASE_CONTRACTS.md` en el repo (los comentarios del código lo citan como referencia, pero el archivo no está en el clon).
- `RenaserPlayStoreCopy/src/services/phaseContracts.ts` (1-167): cómo lo consume la app — incluye un comentario propio muy relevante (líneas 1-9): la pantalla que llamaba a este servicio **se borró en un PR sin querer** (PR #89) y "`/api/v1/phase-contracts` lleva funcionando desde entonces sin nadie que lo llamara" — es decir, el backend viejo es correcto pero **hoy no tiene ningún cliente real activo**. Relevante para priorizar: no hay urgencia de compatibilidad con una pantalla en producción ahora mismo, pero el contrato igual se preserva por disciplina (mañana puede reaparecer la pantalla).

### 0.1 Los días de firma — VERIFICADOS, no son 1/23/46/68

El bloque de la tarea decía "el blueprint dice 1/23/46/68 pero el día 1 lo cubre el onboarding: verificalo en el código". Verificado — **son otros números, no una variación menor del blueprint**:

```ts
// service.ts:35-39
const CONTRACT_UNLOCK_DAY: Partial<Record<TraineePhase, number>> = {
  PHASE_2_DEVELOPMENT: 17,
  PHASE_3_ALCHEMIST_WARRIOR: 35,
  PHASE_4_ASCENSION: 65,
}
```

- **Fase I: no tiene entrada.** Se firma en el Pacto del onboarding (flujo separado, fuera de este módulo) — `service.ts:6-7` lo dice explícito: *"El Día 1 se firma en el Pacto del onboarding (flujo separado) — esta feature cubre solo las Fases 2/3/4"*.
- **Fase II: día 17.** Y esto es lo importante que el blueprint también pasaba por alto: **17 no es el día en que arranca la Fase II** (arranca el día 8, `phase.ts:39`). El comentario original (`service.ts:9-13`) lo explica: *"el aprendiz ya está en Fase II varios días antes de que le toque firmar"*.
- **Fase III: día 35.** Coincide con `PHASE_3_STARTS_DAY = 35` (`phase.ts:30`) — pero se declara aparte a propósito: *"no depender de esa coincidencia si algún día cambian por separado"* (`service.ts:12-13`).
- **Fase IV: día 65.** Coincide con `PHASE_4_STARTS_DAY = 65` (`phase.ts:40`), mismo razonamiento.

Blueprint (`1/23/46/68`) **descartado**: no aparece en ningún lado del código, ni en tests, ni en comentarios. Puede ser un documento de fases vencido (el propio `phase.ts:11-14` menciona que "el blueprint v3 quedó desactualizado" en otro punto — los cortes de días de las guías). No se usó ese número para nada.

### 0.2 La fase NUNCA se lee de una columna guardada — bug real que este módulo evita

`phase.ts:1-9` (comentario completo, cabecera del archivo) es una advertencia explícita del equipo anterior:

> *"This is the single source of truth for 'what phase is this trainee in' — always compute it from programDay at read time, never trust a stored currentPhase column (see TraineeProfile.currentPhase: it's written once at profile creation and never recomputed as programDay advances daily via the cron, so it silently drifts for any trainee who progresses past their starting phase)."*

La base nueva (`V1__baseline_renaser.sql:266`) **sí tiene** una columna `participantes_programa.fase` (`fase_programa NOT NULL DEFAULT 'FASE_1_RENACER'`).

> **Corregido 2026-09-01 (D-67).** Esta sección decía que "ese cron todavía no existe" — ya no es cierto: `AvanzarDiaProgramaScheduler` (`users/infrastructure/adapter/in/scheduler`) corre cada madrugada, avanza `dia_programa` de los participantes con el reloj activado y **recalcula `fase` en el mismo movimiento** vía `com.renaser.os.users.api.FasePrograma.paraDiaPrograma(int)` — la copia en inglés de este mismo enum, con los mismos cortes 1-7/8-34/35-64/65-90, duplicada a propósito por el límite de módulo (D-21; ver javadoc de `ParticipacionPrograma.avanzarDiaDelPrograma`). El bug que describe el párrafo de abajo (fase vieja conviviendo con un día nuevo) ya tenía una instancia real en los datos antes de este cambio — dos filas en día 17 con fases distintas — causada también por `fijarDia` (ajuste manual del panel admin), que D-66 corrigió para que también recalcule la fase. Este módulo (`phasecontracts`) sigue sin depender de esa columna ni del cron de `users` — la regla de abajo ("nunca lee `participantes_programa.fase`") sigue vigente tal cual, es una garantía adicional, no una que dependa de que el cron de `users` funcione bien.

Para no repetir el bug que el propio repo viejo señala:

**Este módulo NUNCA lee `participantes_programa.fase`.** Deriva la fase siempre de `dia_programa`, vía `FasePrograma.paraDiaPrograma(int)` (dominio puro, ver §2). Documentado también en el javadoc de `FasePrograma`.

### 0.3 El flujo de firma cambia: antes subía directo, ahora pide URL prefirmada (D-34)

Repo viejo (`phaseContracts.ts` en la app, líneas 111-127): la app subía el SVG **directo** a Supabase Storage con la sesión del usuario (bucket `onboarding-signatures`, ruta `{userId}/pacto_fase/{phase}.svg`), y solo mandaba al backend `{signatureBucket, signatureUrl}` (metadata) por `POST /api/v1/phase-contracts`.

Eso ya no es posible: D-34 dice que la app no tiene credenciales AWS y debe pedir una URL prefirmada a la API. Ver §3 para el diseño nuevo.

---

## 1. Regla de dominio (el corazón del módulo)

`FasePrograma` (enum, `domain/model/contrato/FasePrograma.java`) encapsula dos conceptos que el repo viejo mantenía deliberadamente separados:

| Fase | `diaInicio` (fuente: `phase.ts`) | `diaDesbloqueoFirma` (fuente: `service.ts`) | ¿Coinciden? |
|---|---|---|---|
| FASE_1_RENACER | 1 | *(no aplica — se firma en el onboarding)* | — |
| FASE_2_DESARROLLO | 8 | 17 | **No** |
| FASE_3_GUERRERO_ALQUIMISTA | 35 | 35 | Sí |
| FASE_4_ASCENSION | 65 | 65 | Sí |

Métodos puros, 100% testeados sin Spring/Postgres (`FaseProgramaTest`, matriz completa día×fase):

- `paraDiaPrograma(int)`: fase actual (equivalente a `phaseForDay`).
- `firmaDesbloqueadaEnDia(int)`: ¿ya corresponde firmar ESTA fase a ese día? (equivalente a la comparación `programDay < unlockDay` de `service.ts`).
- `faseAFirmarEnDia(int)`: la fase que corresponde firmar hoy, o `null` — combina las dos anteriores, usado tanto por `firmar` como por `consultarPendiente` para no duplicar la regla.
- `numero()` / `porNumero(int)`: 1..4, usado para nombrar la ruta de S3 y para el puerto público `api.ContratoFaseFinder` (ver §5).

`ContratoFase` (agregado, `domain/model/contrato/ContratoFase.java`): construcción vía `firmar(participanteId, diaProgramaActual, clock)`, que deriva la fase del día — **nunca la recibe como parámetro de confianza** (mismo principio que el `role` ausente en `SubmitAccountRequestCommand` de `users`, CLAUDE.MD §5.3.3). Rechaza con `IllegalArgumentException` (→ 400) si la fase es Fase I o si el día de desbloqueo no llegó.

---

## 2. Deuda técnica: lectura de `participantes_programa` / `usuarios`

La tarea ya anticipaba que este módulo necesitaría leer `dia_programa` de una tabla que no es suya, y pedía documentarlo como deuda. Al construirlo apareció una segunda necesidad del mismo tipo, **no anticipada**, que vale explicar en detalle porque es un hallazgo de arquitectura real:

### 2.1 Por qué no alcanza con `users.api.UserSummary`

`users/api/UserSummary.java` expone `role: UserRole` y `status: UserStatus` — pero esos dos tipos viven en `users.domain.model.user`, un paquete **interno** de `users` (no `@NamedInterface`). Si una clase de `phasecontracts` llama a `userSummary.role()` y usa el resultado (incluso solo comparándolo, incluso con `var` sin escribir el nombre del tipo), el `.class` compilado de esa clase queda con una referencia de bytecode a `com.renaser.os.users.domain.model.user.UserRole`. `ArchitectureTest.modulesDoNotLeakInternals` (que corre `ApplicationModules.verify()`) detecta esa referencia como una fuga de un paquete no expuesto — **sin importar que el método que la devuelve sea público**. Es una limitación real de `users/api` tal como está hoy: nadie la había consumido todavía desde otro módulo, así que nadie había tropezado con esto.

No se puede arreglar acá: tocar `users/**` está fuera de alcance de esta tarea.

### 2.2 Solución adoptada

`phasecontracts` no importa nada de `users` en código. En vez de eso:

- **Puerto** `application/ports/out/contrato/ConsultarProgresoParticipantePort` — devuelve `ProgresoParticipante(diaPrograma, rol, suspendido)`, donde `rol` es un enum **local** a este módulo (`RolParticipante`, con los mismos 5 valores que `UserRole` pero declarado acá, sin importar el tipo de `users`).
- **Adaptador** `infrastructure/adapter/out/persistence/participante/ConsultarProgresoParticipantePersistenceAdapter` — una única query SQL nativa (vía `EntityManager.createNativeQuery`, sin `@Entity` ni `JpaRepository` de por medio) que hace `JOIN` entre `renaser.participantes_programa` y `renaser.usuarios` y devuelve `dia_programa`, `rol`, `estado` crudos. Deliberadamente NO es un `JpaRepository` sobre una entidad propia: este paquete entero está pensado para desaparecer limpio el día que `users/api` exponga esta lectura.
- Los valores de `rol_usuario` (`APRENDIZ`, `MENTOR`, `LIDER_MENTORES`, `ADMIN`, `ALQUIMISTA`) se traducen a mano a `RolParticipante` — mismo patrón de "nunca JPA mágico por nombre" que ya usa `users` (`RolUsuarioJpa`).

**Riesgo dejado explícito, no verificado por mí:** la query nativa lee columnas `enum` de Postgres (`rol`, `estado`) vía `Object[]`. El driver puede devolverlas como `String` o como `org.postgresql.util.PGobject` según la ruta interna que tome Hibernate/pgjdbc — el código usa `String.valueOf(...)` (no un cast directo) precisamente para cubrir ambos casos, pero **esto no lo verifiqué corriendo el test** (no debía correr Maven). `ConsultarProgresoParticipantePersistenceAdapterTest` (Testcontainers) es exactamente la prueba diseñada para confirmar esto contra Postgres real — el supervisor la corre y si falla, la primera hipótesis es este punto, no el resto del adaptador.

### 2.3 A dónde migrar esto

`docs/PLAN_DE_MODULOS.md` (sección "0. Cierre de `users`") ya prevé agregar un 4º agregado `participante` a `users` (`ParticipantePrograma`). Cuando eso exista, la migración natural es: `users/api` expone una proyección primitiva (día de programa + rol/estado como tipos seguros para consumo externo, no los enums de dominio), y este puerto + adaptador se borran enteros, reemplazados por una llamada a esa API.

---

## 3. Firma a S3 — endpoints nuevos (D-34)

El repo viejo no tenía "pedir URL prefirmada" porque la app subía directo a Supabase Storage. Con D-34 (AWS S3 real, sin credenciales en el cliente) hace falta un paso más. Diseño adoptado:

1. **`POST /api/v1/phase-contracts/upload-url`** (nuevo) — el servidor deriva la fase EN CURSO del participante (mismo cálculo que firmar), calcula la ruta **determinística** `firmas/{participanteId}/fase_{N}.svg` (`ContratoFase.rutaFirma`) y devuelve una URL PUT prefirmada (`AlmacenamientoPort.firmarSubida`, 10 min de validez). La ruta nunca la elige el cliente — mismo blindaje anti mass-assignment que el `role` ausente en `users`.
2. La app sube el SVG directo a esa URL (fuera de esta API).
3. **`POST /api/v1/phase-contracts`** (preservado) — ya no recibe body: el servidor ya sabe la ruta (es determinística), solo falta registrar el pacto. Persiste `bucket` (siempre `onboarding-signatures`, el mismo default que ya tenía la tabla) + la ruta calculada + `firmado_en`. Idéntico bloqueo de negocio que el repo viejo (Fase I rechazada, día no desbloqueado rechazado, ya firmada → idempotente).

Esto es una **decisión de este módulo**, no confirmada por nadie fuera de la tarea — documentada acá como corresponde a una duda menor (§0.6 de CLAUDE.MD: "ante una duda menor, decidir, avanzar y avisar qué se asumió"). Alternativas descartadas: (a) que el POST siga recibiendo `{bucket, ruta}` en el body — reintroduce la superficie de mass-assignment que D-34 buscaba cerrar; (b) que el POST reciba solo `contentType` — no hay necesidad, es siempre SVG.

---

## 4. Endpoints construidos

Base: `/api/v1/phase-contracts`. Actor resuelto por header `X-Actor-Id` (temporal, D-29 — igual que en `users`, sin autenticación real todavía por B-2). **Autoservicio**: el participante solo opera sobre su propio pacto, igual que el repo viejo (`auth.data.userId` siempre, nunca un id ajeno) — no hay endpoint "firmar por otro".

| Método | Ruta | Repo viejo | Rol permitido | Notas |
|---|---|---|---|---|
| GET | `/pending` | `GET .../pending` (preservado) | TRAINEE, MENTOR | `{pending:false}` o `{pending:true, phase, phaseLabel}` — misma forma exacta |
| POST | `/` | `POST /` (preservado, body cambia — ver §3) | TRAINEE | Sin body. Idempotente |
| GET | `/` | **Nuevo** | TRAINEE, MENTOR | Lista los pactos ya firmados, con URL de lectura prefirmada c/u |
| POST | `/upload-url` | **Nuevo** (D-34) | TRAINEE | URL PUT prefirmada para la firma |

**Ruptura de contrato conocida y heredada, no de este módulo:** el valor de `phase` en las respuestas ahora es español (`FASE_2_DESARROLLO`...) porque la base nueva migró el vocabulario del enum (D-33, decisión de esquema anterior a esta tarea) — el repo viejo devolvía inglés (`PHASE_2_DEVELOPMENT`...). Cualquier cliente que compare el string literal necesita coordinar release. No se intentó ocultar esto traduciendo en la capa web: sería inventar un mapeo que nadie pidió y that diverge del resto del backend nuevo (todos los enums de dominio en este backend están en español, ver `UserRole`... no, `UserRole` es inglés — pero los enums que reflejan tipos Postgres, como este, van en español igual que `EstadoSolicitudJpa`).

**Roles permitidos — asunción explícita:** el repo viejo solo definía roles para `/pending` (TRAINEE+MENTOR) y `POST /` (solo TRAINEE). Los dos endpoints nuevos (`GET /` y `POST /upload-url`) no tienen precedente — se les asignó el mismo set que su endpoint análogo más cercano (`GET /` como lectura → mismo set que `/pending`; `POST /upload-url` como paso previo a firmar → mismo set que `POST /`). Es una decisión razonable, no una regla de negocio confirmada por el cliente — si el negocio quiere otra cosa, es una pregunta abierta (§7).

---

## 5. API pública del módulo (`phasecontracts.api`)

`ContratoFaseFinder.estaFirmado(UserId participanteId, int numeroFase)` — para que otro módulo (candidatos: `academy`, `habits`) pueda gatear contenido por fase firmada, sin ver el `ContratoFase` completo. Nadie lo consume todavía (no hay otro módulo construido que lo necesite hoy) — se agregó por paridad con el patrón de `users.api.UserSummaryFinder` y porque no cuesta nada mantenerlo simple.

**Ojo con el `int` en vez del enum `FasePrograma`:** es a propósito, por la misma razón de §2.1 — `FasePrograma` vive en `domain.model.contrato`, no expuesto. Usar el enum acá habría reproducido, en este módulo, exactamente el mismo problema que documento como deuda de `users.api`. `numero()`/`porNumero()` son el puente seguro.

---

## 6. Qué NO se construyó / preguntas abiertas

- **No hay Flyway nuevo.** `contratos_fase` ya existe completa en el baseline (columnas, `UNIQUE`, default de `bucket`) — no hizo falta agregar nada.
- **No hay `@RequiresPermission`/`@PublicEndpoint`.** Esa infraestructura no existe todavía en `shared/` (bloqueada por R-2/B-5, igual que en `users` — ver D-29 de `MODULO_USERS.md`). La autorización vive dentro de `ContratoService` (mismo patrón que `AccountRequestService.requireManager`), lanzando `NotAuthorizedException` → 403 vía el `GlobalExceptionHandler` ya existente. El test de reflexión que pide el DoD no se puede escribir de forma significativa porque la anotación no existe — anotado como bloqueado, no inventado.
- **No hay tests HTTP/MockMvc de 403 end-to-end.** Ningún otro módulo del repo los tiene todavía tampoco (ni `users`, que es el canon) — los tests de seguridad de este módulo verifican la regla donde vive de verdad (`ContratoServiceTest`: rol sin permiso y cuenta suspendida → `NotAuthorizedException`, que es exactamente lo que el handler traduce a 403). Si se quiere el nivel HTTP explícito, es trabajo adicional simétrico al que le falta a `users`.
- **`programa_activado_en` no se verifica.** El repo viejo tampoco lo chequeaba en esta feature (solo perfil existente + no suspendido) — no se inventó una regla nueva.
- **Pregunta abierta real:** ¿los roles permitidos para `GET /` (listado) y `POST /upload-url` (asumidos como TRAINEE+MENTOR y TRAINEE respectivamente, ver §4) son correctos, o el negocio quiere algo distinto ahora que existen? No confirmado — decisión menor, avanzada y documentada, pero es la primera candidata a revisar.
- **Pregunta abierta real:** ¿`ADMIN`/`ALCHEMIST`/`MENTOR_LEAD` deberían poder ver o firmar el pacto de OTRO participante (p. ej. un mentor supervisando)? El repo viejo nunca lo permitió (siempre autoservicio) y este módulo preserva eso — pero no hay confirmación de que sea la intención definitiva.

---

## 7. Registro de decisiones de este módulo

(Numeración local, sin pisar el contador compartido `D-N` de `MODULOS_A_AVANZAR.md` — el supervisor decide si promoverlas al registro global.)

| # | Decisión | Razonamiento |
|---|---|---|
| PC-1 | Días de firma exactos: Fase I *(onboarding)*, Fase II día 17, Fase III día 35, Fase IV día 65 — **no** 1/23/46/68 | Verificado literal contra `service.ts:35-39` del repo viejo, ver §0.1 |
| PC-2 | La fase se deriva SIEMPRE de `dia_programa`, nunca se lee `participantes_programa.fase` | Repite una advertencia explícita del repo viejo (`phase.ts:1-9`) sobre un bug real ya vivido, ver §0.2 |
| PC-3 | `phasecontracts` no importa nada de `users` en código; lee `participantes_programa`+`usuarios` con su propia query nativa | `users.api.UserSummary` filtra tipos internos (`UserRole`/`UserStatus`) que romperían `ArchitectureTest` si se consumen desde otro módulo — ver §2.1 |
| PC-4 | Flujo de firma nuevo: `POST /upload-url` (URL prefirmada) + `POST /` sin body (ruta determinística, ya no viaja del cliente) | D-34 (S3 real, sin credenciales en la app) — ver §3 |
| PC-5 | `GET /` (listado, nuevo) y `POST /upload-url` (nuevo) heredan el set de roles de su endpoint analogo mas cercano | Sin precedente en el repo viejo — decisión menor documentada, no confirmada por negocio, ver §4 y §6 |

---

## 8. Estado / checklist DoD

- [x] `domain/` plano por agregado (`contrato/`), sin imports de Spring/JPA/Jackson
- [x] Tests unitarios de dominio sin Spring/Postgres — matriz completa día×fase (`FaseProgramaTest`, `ContratoFaseTest`)
- [x] Casos de uso con comando self-validating (`FirmarContratoCommand`, `ObtenerUrlFirmaContratoCommand`) — sin campo `fase` inyectable, con test de regresión que lo verifica por reflexión
- [x] Controller tonto: sin repositorios, sin `@Transactional`, sin reglas de negocio
- [x] DTO de salida como proyección explícita (`ContratoFaseResponse`, `ContratoPendienteResponse`, `UrlFirmaResponse`)
- [x] Sin migración Flyway nueva (nada que agregar al baseline)
- [x] Tests de integración con Testcontainers (`ContratoPersistenceAdapterTest`, `ConsultarProgresoParticipantePersistenceAdapterTest`)
- [ ] `ArchitectureTest` en verde — **no corrido por mí** (instrucción explícita de no ejecutar Maven). Diseñado activamente para pasar (sin imports cruzados a `users`, `domain/` sin frameworks) pero sin confirmación empírica
- [ ] `./mvnw clean test` — **no corrido por mí**, mismo motivo. El supervisor lo corre
- [x] Pruebas de seguridad §0.3: rol sin permiso → `NotAuthorizedException` (`ContratoServiceTest`), cuenta suspendida → `NotAuthorizedException` (`ContratoServiceTest`), rol no inyectable por body (no hay campo para inyectar; test de regresión por reflexión). Test de reflexión de `@RequiresPermission`/`@PublicEndpoint`: **no aplicable**, esa infraestructura no existe todavía (bloqueado, igual que en `users`)
- [x] Avance documentado en este archivo
- [ ] Bitácora de errores (`docs/BITACORA_ERRORES.md`) — **no se encontró ningún error/bug real durante la construcción** (solo la decisión de diseño de §2, que no es un bug sino un hallazgo de arquitectura); no se agregó una entrada artificial
- [x] Contrato verificado contra el comportamiento real del repo viejo (no había `docs/API_CONTRACT.md` de esta feature — se verificó contra el código de rutas + el cliente de la app)

**Honestidad de alcance:** todo lo pedido en la tarea está construido. Lo que falta es exclusivamente lo que dependía de correr Maven (que tenía prohibido) o de infraestructura que no existe todavía en `shared/`/`users/` (bloqueada, no inventada). El hallazgo de arquitectura de §2.1 no estaba anticipado en el encargo original y cambió el diseño del puerto de progreso del participante — se prefirió resolverlo de raíz (query propia, tipos locales) antes que forzar un import que rompería `ArchitectureTest`.

---

## Auditoría de arquitectura (2026-08-28) — agente automático

Auditoría de solo lectura de `src/main/java/com/renaser/os/phasecontracts/` (22 archivos `.java` de producción, 7 de test) contra CLAUDE.md §5.1/§5.1.2/§5.3.4-5.3.5/§5.4.1-5.4.10. No se corrió `./mvnw`, no se modificó ningún `.java`.

### 1. Autenticación de actor — sin violaciones, único controller del módulo ya migrado

`ContratoController.java` es el único `@RestController` de `phasecontracts` (4 endpoints: `GET /`, `GET /pending`, `POST /upload-url`, `POST /`). Los cuatro reciben el actor con `@ActorAutenticado UserId actor` (líneas 39, 46, 51, 57; import línea 10, `com.renaser.os.shared.web.security.ActorAutenticado`). `grep` de `X-Actor-Id`/`@RequestHeader` sobre todo el módulo: **vacío**. No hay ningún caso del patrón inseguro descrito en el encargo (el que sí apareció en `community/TestimonioController`). Este es un resultado limpio, no una ausencia de búsqueda: se confirmó línea por línea contra el único controller del módulo.

Nota de contexto: `docs/MODULO_PHASECONTRACTS.md` §4 todavía describe el actor como resuelto por "header `X-Actor-Id` (temporal, D-29)" — esa frase quedó desactualizada por el commit `b824c4b` (migración de los 64 controllers a `@ActorAutenticado`) y ya no refleja el código. Es un caso menor de la regla CLAUDE.md §0.4 ("los documentos no pueden contradecirse") que vale corregir la próxima vez que se toque este archivo.

### 2. `domain/` — limpio, correctamente plano por agregado único

`domain/model/contrato/` tiene 3 clases (`ContratoFase`, `ContratoFaseId`, `FasePrograma`), todas plano en una sola carpeta — correcto por §5.1.2: es un único agregado real (`ContratoFase`, su identidad `ContratoFaseId` y el enum `FasePrograma` que usa como value object) sin sentido unos sin otros, exactamente el caso `buckpal` (no el caso `dddsample-core` de agregados múltiples), así que no corresponde subdividir.

- `grep` de `org.springframework.*`/`jakarta.persistence.*` sobre `domain/`: **vacío**.
- `grep` de imports a `phasecontracts.application`/`phasecontracts.infrastructure` desde `domain/`: **vacío**. La única dependencia hacia afuera es `com.renaser.os.shared.domain.{Clock,UserId}`, que es shared-kernel, permitido.
- `ContratoFase.firmar(participanteId, diaProgramaActual, clock)` recibe el reloj inyectado y nunca llama `Instant.now()`/`LocalDate.now()` crudo (`grep` sobre `domain/` y `application/`: vacío en ambos). El día de programa tampoco se recalcula del reloj — se recibe como parámetro ya resuelto por `ConsultarProgresoParticipantePort`, consistente con la regla documentada en §0.2 del propio doc del módulo ("la fase nunca se lee de una columna guardada, siempre se deriva de `dia_programa`").
- `domain/` no loguea: sin `Logger`/`log.` en ningún archivo.

### 3. Lombok — uso correcto, igual al patrón ya validado en `users`/`rocks`

`ContratoFase.java:5-9,14-17`: `@Getter` + `@Accessors(fluent = true)` + `@AllArgsConstructor(access = AccessLevel.PRIVATE)` + `@EqualsAndHashCode(of = "id")`. Sin `@Data`/`@Setter`/`@NoArgsConstructor` público en `domain/`. `toString()` acotado a mano (línea 68-70: solo `id`, `participanteId`, `fase` — sin PII, sin `firmadoEn`/`bucket`/`rutaFirma`).

`@Entity`/`@Data`/`@NoArgsConstructor` solo aparecen donde corresponde: `infrastructure/adapter/out/persistence/contrato/ContratoFaseJpaEntity.java:17-21`. Ningún otro archivo del módulo usa esas anotaciones.

### 4. Controller — tonto, cumple la regla al pie de la letra

`ContratoController.java` (61 líneas totales, 4 endpoints, ninguno pasa de 5 líneas de cuerpo): no inyecta ningún puerto `out` ni repositorio (los 4 campos son casos de uso `port/in`), sin `@Transactional`, sin `if` de negocio — cada método deserializa (nada que deserializar salvo el actor), invoca un único caso de uso y mapea la salida con un factory estático del DTO (`ContratoFaseResponse.deListado/deFirma`, `ContratoPendienteResponse.from`, `UrlFirmaResponse.from`). Constructor con 4 parámetros, dentro del techo de §5.4.8.

### 5. Excepciones — el dominio no conoce HTTP, el `GlobalExceptionHandler` cubre las 3 que el módulo lanza

`ContratoFase.requireFirmable` lanza `IllegalArgumentException` (sin código de estado). `ContratoService.requireProgreso` lanza `NotAuthorizedException` (rol sin permiso o cuenta suspendida) y `NoSuchElementException` (participante inexistente); `obtenerUrlSubida` lanza `IllegalStateException` cuando el pacto ya existe. Las tres están mapeadas en `shared/web/GlobalExceptionHandler.java`: `NotAuthorizedException`→403, `NoSuchElementException`→404, `IllegalArgumentException`→400, `IllegalStateException`→409. Ninguna excepción de `phasecontracts` referencia `HttpStatus`/`ResponseEntity` — confirmado por lectura completa de `ContratoFase.java` y `ContratoService.java`.

### 6. Nombres, tamaños — sin violaciones

- Sin `Util`/`Helper`/`Manager`/`Processor`/`Data`/`Info` sueltos en ningún nombre de clase del módulo.
- `ContratoService.java` (130 líneas): implementa 5 interfaces (`FirmarContratoUseCase`, `ConsultarContratosPendientesUseCase`, `ConsultarContratosUseCase`, `ObtenerUrlFirmaContratoUseCase`, `ContratoFaseFinder`), 5 métodos públicos — dentro del techo de 300 líneas / 10 métodos públicos de §5.4.8, muy lejos del problema de tamaño que sí se encontró en `community.CelulaService` (417 líneas) o `rocks.RocaDiariaService` (350 líneas).
- Método más largo del módulo: `firmar()` (líneas 58-72, ~15 líneas). Ningún método del módulo se acerca al techo de 40 líneas ni a 3 niveles de anidamiento.
- Constructor de `ContratoService` con 5 parámetros — supera el "≤4" literal de §5.4.8, pero por el mismo motivo ya aceptado en la auditoría de `community` (agrupa los puertos que sus 5 casos de uso necesitan, no datos de negocio que debieran ir en un record); no se considera hallazgo grave.

### 7. Frontera con otros módulos — respeta D-41, con una discrepancia real entre código y doc

`ConsultarProgresoParticipantePersistenceAdapter` (único adaptador de este módulo que cruza a otro) delega en `com.renaser.os.users.api.ParticipacionProgramaFinder` — no hace ninguna query propia contra `participantes_programa`/`usuarios`. Es exactamente el patrón correcto de CLAUDE.md §4.3/§5.1 (un módulo solo llama a la API pública de otro).

**Esto contradice lo que dice `docs/MODULO_PHASECONTRACTS.md` §2.2/§2.3/PC-3**, que describe (y da por vigente) un adaptador con **query SQL nativa propia vía `EntityManager.createNativeQuery`** contra `participantes_programa`+`usuarios`, presentando el consumo de `users.api` como un trabajo *futuro* pendiente de que `users` exponga esa proyección. El código ya no coincide con esa descripción: la migración a `users.api.ParticipacionProgramaFinder` (con el mismo comentario en el propio adaptador citando "D-41: ningún módulo lee la tabla de otro de frente") ya está hecha. El único commit del archivo en `git log` (`49a5a98`) ya contiene la versión que delega en `users.api`, así que el doc no describe ninguna versión real del código en ningún punto de su historia — quedó desalineado desde el principio, probablemente porque el diseño cambió después de redactar §2 pero antes de terminar la implementación, sin volver a corregir el texto. Es una violación concreta de CLAUDE.md §0.4 ("los documentos son fuente de verdad y no pueden contradecirse... si un cambio deja una sección vieja, se corrige en el momento") — en este caso el código terminó siendo *mejor* que lo documentado, pero el efecto práctico es el mismo: alguien que lea solo el doc diseñaría sobre una premisa falsa (que hay una query nativa frágil pendiente de migrar) cuando esa migración ya ocurrió y el `ConsultarProgresoParticipantePersistenceAdapterTest.java` actual (que sí usa `EntityManager` — pero solo para insertar fixtures de prueba, no en el adaptador) tampoco es evidencia de lo contrario si se lee rápido.

`ContratoFaseFinder` (`api/ContratoFaseFinder.java`, único tipo del `@NamedInterface("api")` del módulo): `grep` de `phasecontracts.api` fuera del propio módulo devuelve **vacío** — ningún otro módulo lo consume todavía. Coincide con lo que ya declara el propio doc (§5: "nadie lo consume todavía"), no es un hallazgo nuevo.

### 8. Resumen

Módulo pequeño (22 archivos de producción) y el más limpio de los auditados hasta ahora contra las reglas de arquitectura: cero violaciones de dependencia, cero controllers con el patrón `X-Actor-Id` inseguro, cero excepciones HTTP-aware en dominio, cero infracciones de tamaño duras. El único hallazgo real es documental (punto 7): `docs/MODULO_PHASECONTRACTS.md` describe una deuda técnica (query nativa contra tablas ajenas) que el código ya resolvió, y el §4 del mismo doc describe una autenticación por header que el código también ya reemplazó por `@ActorAutenticado`. Ninguno de los dos es un riesgo de seguridad — ambos son el código *adelantándose* a su propia documentación — pero corresponde actualizar el doc para que vuelva a ser fuente de verdad.
