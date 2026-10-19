# Tasks: El líder de mentores ve el cumplimiento de cada persona por grupo

**Input**: `specs/005-lider-cumplimiento-por-persona/` (spec, plan, research, data-model, contracts, quickstart)

**Tests**: obligatorios por la constitución (III). Se escriben antes y se ven fallar.

**Rutas**:
- Backend: `B/` = `src/main/java/com/renaser/os/`, `BT/` = `src/test/java/com/renaser/os/`, `BR/` = `src/main/resources/`.
  Worktree: `Renaser-90-dias-backend/.claude/worktrees/lider-cumplimiento`.
- App: `A/` = `src/`, en el worktree `Renaser-90-dias-frontend-/.claude/worktrees/lider-cumplimiento`.

**Máquina compartida**: mientras otra sesión tenga la máquina, solo se corren pruebas sueltas (`./mvnw -Dtest=…`
sin Testcontainers, `jest <archivo>`). `clean verify`, la suite completa de jest y el E2E van al final, avisando
antes a las otras sesiones.

## Phase 1: Setup

- [x] T001 Crear el worktree de la app `Renaser-90-dias-frontend-/.claude/worktrees/lider-cumplimiento` en la rama `005-lider-cumplimiento-por-persona` desde `master`, y referenciar la spec en `A/../specs/README.md`
- [x] T002 Aplicar `Renaser-90-dias-backend/.claude/worktrees/post-diario-ligar-publicacion.patch` en el worktree del backend y revisar que compile (`./mvnw -q -DskipTests compile`)

## Phase 2: Foundational (bloquea todas las historias)

- [x] T003 Correr en verde las pruebas del parche (`./mvnw -Dtest=SeguimientoServiceTest test`) y ajustar: el post diario completado devuelve `publicacionId` de la primera publicación del autor en su día local y la entrega pasa a `ENTREGADA` a la hora de esa publicación, en `B/mentoring/application/services/SeguimientoService.java` y `B/community/api/PublicacionMuroFinder.java`
- [x] T004 Prueba que falle y cambio: `armarSemana` recibe la ventana (`desde`, `hasta`) en vez de calcular el lunes; las fichas del mentor y de admin siguen pidiendo lunes–domingo, en `B/mentoring/application/services/SeguimientoService.java` y `BT/mentoring/application/services/SeguimientoServiceTest.java`
- [x] T005 [P] Exponer `SemanaDeSeguimientoFinder.semanaDe(personaId, desde, hasta)` en `B/mentoring/api/SemanaDeSeguimientoFinder.java`, implementado por `SeguimientoService`
- [x] T006 [P] Exponer `TablaDelSemaforoFinder.tablaDe(grupoId, semanaHasta)` en `B/mentoring/api/TablaDelSemaforoFinder.java`, implementado por `SemaforoDelGrupoService.armarTabla`
- [x] T007 Sumar a `ObligacionDelDia` los campos opcionales `respuestaTexto`, `evidencias[]` (`id`, `tipo`, `subidaEn`) y `publicacionId`, con su prueba, en `B/mentoring/application/ports/in/ConsultarSeguimientoSemanalUseCase.java`, `SeguimientoService.java` y las respuestas REST de `…/progress` y `…/weekly-progress`
- [x] T008 Escribir `BR/db/migration/V109__consultas_del_lider_y_observacion_sobre_persona.sql` con cabecera: tabla `consultas_de_ficha` (`id uuid PK`, `consultor_id uuid NOT NULL` sin FK, `rol_consultor varchar(32) NOT NULL`, `persona_id uuid NOT NULL` sin FK, `grupo_id uuid NOT NULL`, `mentor_id uuid NULL`, `semana_desde date NOT NULL`, `consultada_en timestamptz NOT NULL`), índice `(mentor_id, consultada_en)`, trigger `BEFORE UPDATE OR DELETE` que lanza excepción; `observaciones_mentor` + `persona_id uuid NULL`, `semana_desde date NULL`; `ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'REVISION_DEL_LIDER'`
- [x] T009 [P] Puerto `CuentasDePruebaFinder.esDePrueba(UserId)` en `B/users/api/CuentasDePruebaFinder.java` con una implementación que en master devuelve siempre `false` (se reemplaza en T046)

**Checkpoint**: compila, las pruebas sueltas de mentoring están en verde y las fichas de mentor y admin no cambian.

## Phase 3: User Story 1 — Del grupo a la persona (P1) 🎯 MVP

**Goal**: el líder abre un grupo, ve a las personas ordenadas y abre la semana de una persona.

**Independent Test**: quickstart, escenarios 1, 2, 4, 5 y 11.

### Tests (primero, deben fallar)

- [x] T010 [P] [US1] Unitaria de `OrdenDeLaListaDelLider`: ROJO, AMARILLO, SIN_DATOS, VERDE, NO_SE_MIDE; % ascendente con nulos al final; después nombre y id, en `BT/leadership/domain/model/cumplimiento/OrdenDeLaListaDelLiderTest.java`
- [x] T011 [P] [US1] Unitaria de `ConsultaDeFicha.registrar` (sin métodos de modificación; reloj en la madrugada UTC) en `BT/leadership/domain/model/consulta/ConsultaDeFichaTest.java`
- [x] T012 [P] [US1] Servicio `GrupoDelLiderService`: grupo de recepción o sin mentor → 404; cuentas de prueba y cerradas fuera; acompañante con programa propio como `NO_SE_MIDE` con su %, en `BT/leadership/application/services/GrupoDelLiderServiceTest.java`
- [x] T013 [P] [US1] Servicio `SemanaDePersonaService`: semana sábado–viernes en la zona de la persona; persona fuera del grupo hoy → 404; semana futura → 400; registra una consulta por llamada con `rol_consultor` y `mentor_id`, en `BT/leadership/application/services/SemanaDePersonaServiceTest.java`
- [x] T014 [P] [US1] `@WebMvcTest` de `CumplimientoDelLiderController`: TRAINEE, MENTOR y `SUSPENDED` → 403; MENTOR_LEAD, ADMIN y ALCHEMIST → 200; `Cache-Control: no-store`, en `BT/leadership/infrastructure/adapter/in/rest/CumplimientoDelLiderControllerTest.java`

### Backend

- [x] T015 [P] [US1] `B/leadership/domain/model/cumplimiento/OrdenDeLaListaDelLider.java`
- [x] T016 [P] [US1] `B/leadership/domain/model/consulta/ConsultaDeFicha.java`
- [x] T017 [US1] Puertos `ConsultarGrupoDelLiderUseCase` y `ConsultarSemanaDePersonaUseCase` en `B/leadership/application/ports/in/`, y `SaveConsultaDeFichaPort` y `LoadConsultasDeFichaPort` en `B/leadership/application/ports/out/`
- [x] T018 [US1] `B/leadership/infrastructure/adapter/out/persistence/ConsultasDeFichaJdbcAdapter.java` (JdbcClient, solo INSERT y SELECT)
- [x] T019 [US1] `B/leadership/application/services/GrupoDelLiderService.java`: `requireLiderazgoActivo` + `TablaDelSemaforoFinder` + acompañantes con programa propio (`AcompanamientoFinder.acompanantesVigentes` + `ParticipacionProgramaFinder`) + `CuentasDePruebaFinder` + orden
- [x] T020 [US1] `B/leadership/application/services/SemanaDePersonaService.java`: ventana del semáforo (sábado–viernes) en la zona de la persona, `SemanaDeSeguimientoFinder`, color y % de la semana, mentor vigente, `SaveConsultaDeFichaPort`
- [x] T021 [US1] `B/leadership/infrastructure/adapter/in/rest/CumplimientoDelLiderController.java`: `GET /api/v1/leadership/groups/{groupId}/semaforo` y `GET /api/v1/leadership/groups/{groupId}/people/{personId}/week`, `@RequiresPermission(VIEW_MENTOR_CORPS)` y respuestas según `contracts/leadership-cumplimiento.md` §1–2
- [x] T022 [US1] `puedeAbrirse` en cada grupo de `GET /semaforo/groups` (verdadero si tiene mentor) en `B/mentoring/infrastructure/adapter/in/rest/semaforo/ResumenPorGruposResponse.java`, con su prueba
- [x] T023 [US1] IT con Testcontainers: lista y semana contra Postgres real, trigger append-only (UPDATE y DELETE fallan), en `BT/leadership/infrastructure/CumplimientoDelLiderIT.java`

### App

- [x] T024 [P] [US1] Esquemas zod tolerantes (`passthrough`, campos nuevos opcionales) en `A/features/lider-mentores/api/cumplimientoDelLiderSchemas.ts` y llamadas en `cumplimientoDelLiderApi.ts`, con prueba
- [x] T025 [P] [US1] `puedeAbrirse` opcional en `A/features/semaforo/api/semaforoSchemas.ts`, sin reordenar campos (coordinado con la sesión 006)
- [x] T026 [US1] `useGrupoDelLider` y `useSemanaDePersona` en `A/features/lider-mentores/hooks/`: lectura vigente (una respuesta vieja no pisa a una nueva), estados de carga, error con reintento y vacío; al cambiar de persona o de semana, ningún dato anterior. Prueba en `__tests__/lecturaDelLider.test.ts`
- [x] T027 [US1] `A/features/lider-mentores/screens/GrupoDelLiderScreen.tsx` con `VistaSemaforoDelGrupo`, «No se mide en el semáforo», vacío «Este grupo todavía no tiene personas», `useSystemBackHandler`, un solo scroll
- [x] T028 [US1] `A/features/lider-mentores/screens/PersonaDelLiderScreen.tsx` y `components/DiaDeLaPersona.tsx`: semana sábado–viernes con ‹ › (sin futuro, hasta `primeraSemana`), estados en palabras y con forma, `motivo` en palabras sin ✗, `useSystemBackHandler`
- [x] T029 [US1] `A/features/semaforo/screens/SemaforoGruposScreen.tsx`: tarjeta `Presionable` solo si `puedeAbrirse === true`; si no, como hoy. Vistas en el estado de Hoy (`A/screens/HoyScreen.tsx`) para el grupo y la persona

**Checkpoint**: el MVP se demuestra con los escenarios 1, 2, 4, 5, 11 y 12 del quickstart.

## Phase 4: User Story 2 — Ver las evidencias (P1)

**Goal**: el líder abre cualquier evidencia de la semana.

**Independent Test**: quickstart, escenarios 3 y 13.

- [x] T030 [P] [US2] Prueba que falle: MENTOR_LEAD activo lee `GET /evidence/{id}` y `/url`; revisar (aprobar o rechazar) → 403; MENTOR_LEAD `SUSPENDED` → 403, en `BT/evidence/application/services/EvidenciaServiceLectorLiderTest.java`
- [x] T031 [US2] Regla de lector para el liderazgo activo en `requireDuenoOAdmin` (solo detalle y URL, sin `MANAGE_EVIDENCE`) en `B/evidence/application/services/EvidenciaService.java`
- [x] T032 [P] [US2] Prueba que falle y finder `EvidenciasDeRocasFinder.accionesDelDia(persona, desde, hasta)` (`id`, `titulo`, `estado`, `evidenciaId`) en `B/rocks/api/` con su adaptador, y `acciones[]` en cada día de la semana del líder
- [x] T033 [P] [US2] `GET /api/v1/leadership/publications/{publicationId}` con `PublicacionMuroFinder.paraCompartir`; 404 si no existe; prueba en el controller test
  > **Corregido 2026-10-19 (decisión de la sesión supervisora).** El endpoint no es `GET /leadership/publications/{id}` sino
  > `GET /api/v1/wall/{id}` (`PublicacionDelMuroController`, `@Funcionalidad("muro")`), porque «Ver publicación» también está en la ficha
  > del mentor y en la de administración: lo lee su autor, el liderazgo, o el mentor/guía que acompaña hoy al autor
  > (`ConsultarPublicacionService`, en `community`). No usa `paraCompartir`, que no trae la fecha ni deja ver lo oculto.
- [x] T034 [US2] `A/features/lider-mentores/components/EvidenciaDeObligacion.tsx`: foto (URL al abrir, `expo-image`), texto, audio y video, `respuestaTexto` debajo, «Cumplido · sin evidencia» sin botón, «Ya no está disponible» si da 404
- [x] T035 [US2] `A/features/lider-mentores/components/VisorDePublicacion.tsx` (en `HojaDesdeAbajo`) y la foto de las acciones del día en `DiaDeLaPersona.tsx`
- [x] T036 [US2] «Ver publicación» del post diario en `A/features/mentor/screens/AlumnoScreen.tsx` y `A/features/admin/screens/FichaAprendizScreen.tsx`, usando `publicacionId` (opcional en `A/features/mentor/api/mentorSchemas.ts`)

## Phase 5: User Story 3 — Avisar al mentor o escribirle a la persona (P2)

**Independent Test**: quickstart, escenarios 6 y 7.

- [x] T037 [P] [US3] Prueba que falle: observación con `personId` y `weekStart` que se guarda y se lista; `personId` fuera de los grupos del mentor → 400; sin esos campos, como antes, en `BT/leadership/application/services/ObservacionesDeMentorServiceTest.java`
- [x] T038 [US3] Campos opcionales `personId` y `weekStart` en `RegisterObservationRequest`, en el dominio de la observación, en el adaptador (`persona_id`, `semana_desde`) y en la respuesta del GET (`personId`, `personName`, `weekStart`), en `B/leadership/`
  > **Corregido 2026-10-19.** Las pruebas de T037 viven en `FichaYObservacionesServiceTest` (el servicio ya tenía ahí su prueba, no en un
  > `ObservacionesDeMentorServiceTest`), en `ObservacionDeMentorTest`, `LeadershipControllersTest` y, contra Postgres, en
  > `ObservacionMentorPersistenceAdapterTest` (escrita, no corrida) y `BorradoDeCuentaEnLosModulosIT` (idem). `personId` se valida contra
  > `AcompanamientoFinder` (aprendiz vigente de un grupo de ESE mentor), no contra la tabla de otro módulo.
- [x] T039 [US3] «Avisar a su mentor» en `PersonaDelLiderScreen.tsx`: reutiliza `ObservarScreen` con la persona y la semana; el texto del chat empieza con el nombre (`utils/textoDeObservacion.ts`, con prueba); se esconde si `mentor` es `null`
- [x] T040 [US3] «Escribirle» en `PersonaDelLiderScreen.tsx` con `abrirConversacionDirecta` + `irAPestana('Comunidad', …)`, como `AlumnoScreen`, sin enviar nada

## Phase 6: User Story 4 — Resumen diario al mentor (P3)

**Independent Test**: quickstart, escenarios 9 y 10.

- [x] T041 [P] [US4] Unitaria de `ReglaDelResumenDeConsultas`: `toca` de 20:00 a 20:59 en Lima y en Madrid, y nunca en la madrugada UTC; cuerpo con 1, 2, 3 y 5 nombres («y 2 más»); excluye a ADMIN y ALCHEMIST y al propio mentor; clave `consultas-lider:<mentorId>:<fecha>`, en `BT/leadership/domain/model/consulta/ReglaDelResumenDeConsultasTest.java`
- [x] T042 [US4] `B/leadership/domain/model/consulta/ReglaDelResumenDeConsultas.java`
- [x] T043 [US4] `ResumirConsultasDelLiderUseCase` + `ResumenDeConsultasService` (zona: `participantes_programa.timezone` o la del primer grupo, decisión R-8 confirmada; paginado; fallo aislado por mentor; evento `RevisionDelLiderEvent` en `leadership/api`) y `ResumirConsultasDelLiderScheduler` (`cron 0 10 * * * *` en el plan; ver la nota, zona UTC, `@SchedulerLock(name="leadership-resumir-consultas")`), con IT del scheduler sin repetir el aviso
- [x] T044 [US4] `REVISION_DEL_LIDER` en `B/notifications/domain/model/TipoNotificacion.java` y un listener que emite «Tu líder revisó a tu grupo» con la ruta `/mentor/groups/{g}/semaforo` y la deduplicación por clave, en `B/notifications/infrastructure/adapter/in/event/`
  > **Corregido 2026-10-19 (cron).** El scheduler corre `0 10,40 * * * *`, no `0 10 * * * *`: dos intentos por ventana local de 20:00–20:59, para
  > que un fallo de la primera lo recupere la segunda (misma clave, sin aviso doble). Ver research R-8.
  > **Corregido 2026-10-19.** T043: el servicio es `ResumenDeConsultasService` (puerto de lectura `LeerConsultasDeFichaPort`, adaptador
  > `LecturaDeConsultasDeFichaJdbcAdapter`) y el evento lleva el cuerpo ya redactado (`RevisionDelLiderEvent`); el título «Tu líder revisó a tu
  > grupo» lo pone el listener de `notifications` (`RevisionDelLiderNotificationListener`, T044). La regla recibe el día del mentor
  > (`DiaDelMentor`), no lo adivina de las consultas. El scheduler se apaga por `renaser.scheduling.resumen-consultas-lider.activo` (la suite
  > lo apaga) y respeta el interruptor `lider.cumplimiento` (`saltaEsteTurno`). Las cuentas que ya no existen no se nombran. La IT del
  > scheduler (`ResumirConsultasDelLiderSchedulerIT`) está escrita y no corrida (sin Testcontainers en esta máquina).
- [x] T045 [US4] Interruptor «Revisiones de tu líder» (tema `REVISION_DEL_LIDER`) en Yo → Notificaciones, solo para quien acompaña un grupo, en `A/screens/YoScreen.tsx` o en su sub-vista de notificaciones, y la apertura de la ruta del aviso en `A/navigation/rutaDeAviso.ts`

## Phase 7: Integración con la fase 2 de Soporte TI (cuando esté en master)

- [ ] T046 Rebasar sobre master con la fase 2; declarar `normal("lider.cumplimiento", …, roles MENTOR_LEAD, ADMIN, ALCHEMIST, IT_SUPPORT)` en `EntradasDelCatalogo.java`; `@Funcionalidad("lider.cumplimiento")` en los endpoints nuevos; `CuentasDePruebaFinder` real sobre `usuarios.es_cuenta_de_prueba`; `saltaEsteTurno` en el scheduler; `puedeAbrirse` falso con la funcionalidad apagada; `FuncionalidadDeclaradaTest` en verde
  > **Corregido 2026-10-19.** Con la fase 1 de Soporte TI ya en `origin/master` (V98–V102), la parte de esta tarea que no
  > depende de la fase 2 **se hizo**: entrada `lider.cumplimiento` en `EntradasDelCatalogo`, `@Funcionalidad` en el
  > controller nuevo (`CumplimientoDelLiderController`; la publicación del Muro lleva `muro`), `puedeAbrirse` falso con la
  > funcionalidad apagada (`SemaforoPorGruposService`) y `saltaEsteTurno` en el resumen diario (T043); `FuncionalidadDeclaradaTest`
  > en verde. **Sigue pendiente**, para cuando entre la fase 2 (V103–V108): `CuentasDePruebaFinder` real sobre
  > `usuarios.es_cuenta_de_prueba` (V107) y renumerar V109 si esa fase ocupa números por encima de los de hoy.

## Phase 8: Polish & Cross-Cutting

- [x] T047 [P] Documentación del backend: D-nn y E-nn (verificar el último número en master y en la fase 2), `docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md` (lista del líder) y la corrección de RL-07 en la spec del líder con `> **Corregido 2026-10-19.**`
  > **Corregido 2026-10-19.** Hecho en el backend: **D-274** (`docs/MODULOS_A_AVANZAR.md`, tras D-268: es el último número de este master, y
  > D-269 a D-273 quedan reservados para la fase 2 de Soporte TI), **E-628** (`docs/BITACORA_ERRORES.md`, tras E-616; E-617 a E-627 igual
  > reservados), `docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md` (§4.7 y las dos notas `Corregido` de la fila del líder y de §4.6) y la fila 21 de
  > `docs/informes/auditoria-fixes/C-5.md`. **Queda pendiente la corrección de RL-07 en la spec del líder**: esa spec vive en el repo del
  > frontend (`specs/002-lider-de-mentores/spec.md`, línea RL-07), que este agente no toca; la hace quien tenga ese repo.
- [x] T048 [P] `AGENTS.md` del frontend: «Cambio por pedido del dueño — 2026-10-19 — Hoy (semáforo por grupos del líder), fichas del mentor y de administración, Yo → Notificaciones», con lo que cambia y nada más
- [x] T049 Verificación completa con la máquina libre: backend `TZ=UTC ./mvnw -B clean verify` (leer `Tests run:` de surefire y failsafe); app `tsc --noEmit` y jest completo
  > Backend 2026-10-19: `TZ=UTC ./mvnw -B clean verify` en verde, surefire 6588 y failsafe 443, 0 fallos, 0 errores y 0 omitidas (11:21 min). App: `tsc` 0 errores y 84 suites relacionadas en verde (788 pruebas); la suite completa de jest corre dentro del E2E (T050).
- [ ] T050 E2E en el emulador con Maestro y mobile-mcp contra el backend local y la base clonada `renaser_lider005`: los 13 escenarios del quickstart, con capturas en `specs/005-lider-cumplimiento-por-persona/e2e.md`. Avisar antes a las otras sesiones
- [ ] T051 Un commit por repo y por bloque coherente en la rama `005-lider-cumplimiento-por-persona`; `graphify update .` en los dos repos

## Dependencies & Execution Order

- Setup → Foundational (T003–T009) → US1 (MVP) → US2 → US3 → US4. US3 y US4 dependen de US1 (la ficha y el registro de consultas). US2 depende de T007 y de la ficha de US1.
- La fase 7 espera a que la fase 2 de Soporte TI esté en master y va antes del merge de 005.
- T049 y T050 esperan a que la máquina esté libre.

## Parallel Examples

- **US1**: T010–T014 (pruebas) juntas; después T015 y T016 en paralelo con T024 y T025 (app).
- **US2**: T030, T032 y T033 en paralelo; T034 y T035 en la app mientras tanto.
- **US4**: T041 en paralelo con T045 (app).

## Implementation Strategy

1. **MVP**: Setup + Foundational + US1. Se demuestra al dueño: grupo → persona → semana.
2. US2: evidencias, lo que pidió explícitamente.
3. US3 y US4.
4. Integración con la fase 2, verificación completa, E2E y merge.
