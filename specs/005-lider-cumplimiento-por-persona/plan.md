# Implementation Plan: El líder de mentores ve el cumplimiento de cada persona por grupo

**Branch**: `005-lider-cumplimiento-por-persona` (backend y app) | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-lider-cumplimiento-por-persona/spec.md`

## Summary

El líder abre un grupo desde el semáforo por grupos y ve a sus personas con el color y el %, ordenadas de rojo a
verde. Toca a una persona y ve su semana de sábado a viernes con **todas** sus evidencias. Desde ahí avisa a su
mentor o le escribe a la persona.

> **Corregido 2026-10-19.** Decía «su semana de sábado a viernes». Tras el E2E, el resumen, la lista y la ficha leen la misma
> ventana de 7 días (la vigente del resumen, o la semana que se eligió con ‹ ›): ver `contracts/` y E-629 (d).

El backend suma tres lecturas bajo `/leadership/` que **reutilizan** lo que ya existe:

- la tabla del semáforo del grupo (`armarTabla`);
- la semana de seguimiento (`armarSemana` con ventana propia);
- las evidencias, con lectura para el líder.

Además, deriva la publicación del post diario, registra cada consulta en una tabla append-only y manda a las 20:00
un resumen diario al mentor. Las observaciones al mentor ganan la referencia a una persona.

La app hace abribles las tarjetas de `SemaforoGruposScreen` y suma dos pantallas en `lider-mentores`. Las fichas
del mentor y de administración muestran «Ver publicación» en el post diario.

## Technical Context

**Language/Version**: Java 25 (Temurin) / TypeScript estricto (Expo SDK 57, React Native 0.86)

**Primary Dependencies**: Spring Boot, Spring Modulith, Spring Data JPA + `JdbcClient`, Flyway, ShedLock. App: zod y
componentes propios (`VistaSemaforoDelGrupo`, `HojaDesdeAbajo`, `ControlSegmentado`, `Presionable`,
`ConfirmacionEnLinea`). **Sin dependencias nuevas.**

**Storage**: PostgreSQL schema `renaser`; `V109` (tabla `consultas_de_ficha`, dos columnas en
`observaciones_mentor`, un valor de `tipo_notificacion`)

**Testing**: JUnit 5 y Mockito (dominio y servicios), Testcontainers (IT), `@WebMvcTest` con autorización negativa,
`ArchitectureTest`, `EndpointAuthorizationDeclarationTest`; con la fase 2, `FuncionalidadDeclaradaTest`. App: jest,
`tsc --noEmit`, Maestro y mobile-mcp

**Target Platform**: backend en EC2 + RDS; app Android (APK) y web

**Project Type**: mobile-app + API (dos repos hermanos)

**Performance Goals**:
- La lista del grupo responde en menos de 1 s con 30 personas: una lectura del semáforo por lote, sin N+1.
- La semana de una persona responde en menos de 1 s.
- El resumen horario recorre solo los mentores con consultas del día.

**Constraints**:
- Integrar **después** de la fase 2 de Soporte TI (V98..V118; research R-1).
- Campos nuevos opcionales y aditivos para los APK anteriores.
- El día es el de cada persona y el resumen va a la hora del mentor.
- El registro de consultas no se edita ni se borra.

**Scale/Scope**:
- ~300 participantes y ~15 grupos.
- 1 migración, ~20 clases en `leadership`, `mentoring`, `community`, `evidence`, `notifications` y `users.api`.
- App: 2 pantallas, 1 API, 2 hooks, utils y pruebas.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I. Dependencias al dominio | El orden de la lista, la regla del resumen y la consulta viven en `leadership/domain` sin Spring. `leadership` lee por `mentoring.api`, `community.api`, `evidence.api`, `rocks.api` y `users.api`, nunca por SQL ajeno. Los controllers llaman a un solo caso de uso | ✅ |
| II. No inventar reglas | Todo sale de las 10 respuestas del dueño y las 4 de clarify. Las decisiones técnicas nuevas se registran como D-nn y se le avisan al dueño: semana sábado–viernes solo para el líder (R-2), observación ligada a persona (R-7), zona del mentor sin programa (R-8) | ✅ (avisar) |
| III. Pruebas en verde | Unitarias del orden, de la regla del resumen (reloj en la madrugada UTC y a las 20:xx en Lima y Madrid) y de la consulta. IT de las lecturas, del trigger append-only y del scheduler. 403 por rol y por `SUSPENDED`, 404 fuera de alcance, 403 en las escrituras del líder | ✅ |
| IV. Historia | `V109` con cabecera; `consultas_de_ficha` con trigger que impide modificar; la publicación del post diario se deriva, no se copia | ✅ |
| V. Tiempo del participante | La semana sale en la zona de la persona y el resumen en la del mentor; el scheduler horario con `@SchedulerLock`, paginado y con fallos aislados por mentor | ✅ |
| VI. Cinco tabs | Hoy: solo las tarjetas del semáforo por grupos se vuelven abribles (y el estado de Hoy suma dos vistas del líder); Administración y la ficha del mentor ganan «Ver publicación»; Yo → Notificaciones suma un interruptor. Todo se anota en `AGENTS.md` como «Cambio por pedido del dueño — 2026-10-19» | ✅ |
| VII. Documentar | D-nn y E-nn verificando el último número de master **y** de la fase 2 (D-273 y E-627 ya tomados); `docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md`, la spec del líder (RL-07 corregido) y `AGENTS.md` | ✅ |

**Re-check post-diseño (Fase 1)**: sin violaciones. Reutilizar `armarTabla` y `armarSemana` por `mentoring.api`
evita una segunda fórmula de cumplimiento. Sin entradas en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/005-lider-cumplimiento-por-persona/
├── spec.md
├── plan.md                 # este archivo
├── research.md             # R-1..R-11
├── data-model.md           # V109, dominio, puertos
├── quickstart.md           # 13 escenarios
├── contracts/leadership-cumplimiento.md
├── checklists/requirements.md
└── tasks.md                # /speckit-tasks
```

### Source Code

```text
# Backend (src/main/java/com/renaser/os/)
leadership/
├── domain/model/consulta/{ConsultaDeFicha,ReglaDelResumenDeConsultas}.java
├── domain/model/cumplimiento/OrdenDeLaListaDelLider.java
├── application/ports/in/{ConsultarGrupoDelLider,ConsultarSemanaDePersona,ResumirConsultasDelLider}UseCase.java
├── application/services/{GrupoDelLider,SemanaDePersona,ResumenDeConsultas}Service.java
├── infrastructure/adapter/in/rest/CumplimientoDelLiderController.java
├── infrastructure/adapter/in/scheduler/ResumirConsultasDelLiderScheduler.java
└── infrastructure/adapter/out/persistence/ConsultasDeFichaJdbcAdapter.java
mentoring/api/{SemanaDeSeguimientoFinder,TablaDelSemaforoFinder}.java    # expone lo existente
mentoring/application/services/SeguimientoService.java                   # ventana parametrizable, campos opcionales
community/api/PublicacionMuroFinder.java                                 # primeraDelAutorEnElDia
evidence/application/services/EvidenciaService.java                      # lector: liderazgo activo
notifications/domain/model/TipoNotificacion.java                         # REVISION_DEL_LIDER
src/main/resources/db/migration/V109__consultas_del_lider_y_observacion_sobre_persona.sql

# App (src/)
features/lider-mentores/
├── api/{cumplimientoDelLiderApi,cumplimientoDelLiderSchemas}.ts
├── hooks/{useGrupoDelLider,useSemanaDePersona}.ts
├── screens/{GrupoDelLiderScreen,PersonaDelLiderScreen}.tsx
├── components/{DiaDeLaPersona,EvidenciaDeObligacion,VisorDePublicacion}.tsx
└── utils/{lecturaVigente,textoDeObservacion}.ts
features/semaforo/screens/SemaforoGruposScreen.tsx       # tarjeta abrible si puedeAbrirse
features/semaforo/api/semaforoSchemas.ts                 # puedeAbrirse opcional (coordinado con 006)
features/mentor/screens/AlumnoScreen.tsx                 # «Ver publicación»
features/admin/screens/FichaAprendizScreen.tsx           # «Ver publicación»
screens/YoScreen.tsx (Notificaciones)                    # interruptor «Revisiones de tu líder»
screens/HoyScreen.tsx                                    # estado: grupo/persona del líder
```

**Structure Decision**: dos repos hermanos y una spec en el backend. Lo nuevo del líder vive en `leadership` y en
`features/lider-mentores`. Lo que ya existía se expone por `api/` y no se copia.

## Fases de implementación (orden para `/speckit-tasks`)

1. **Base**: aplicar el parche del post diario (R-6) y ponerlo en verde, abrir `armarSemana` y `armarTabla` por
   `mentoring.api`, `V109`.
2. **US1 (P1)**: lista del grupo y semana de la persona, con registro de la consulta; en la app, las tarjetas
   abribles y las dos pantallas.
3. **US2 (P1)**: lectura de evidencias para el líder, acciones del día, publicación del post diario; visor en la
   app; «Ver publicación» en las fichas del mentor y de administración.
4. **US3 (P2)**: observación con persona y chat 1 a 1.
5. **US4 (P3)**: resumen diario, preferencia y aviso.
6. **Integración con la fase 2**: clave `lider.cumplimiento`, `@Funcionalidad`, cuentas de prueba, rebase.
7. **Cierre**: documentación, `AGENTS.md`, `mvnw verify`, jest, tsc y E2E en el emulador (turnándose la máquina).

## Riesgos

- **Memoria de la máquina**: dos backends con el emulador tumban todo (2026-10-19). El E2E se agenda con las otras
  sesiones.
- **Coordinación con 006** (mensaje del semáforo) en `semaforoSchemas.ts`: cada uno suma solo campos opcionales.
- **Fase 2 sin integrar**: si se demora, 005 queda lista detrás. No se integra antes (R-1).

## Complexity Tracking

Sin violaciones de la constitución.
