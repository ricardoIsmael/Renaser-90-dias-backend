# Tasks: Formaciones — Escuela, Diplomado y Certificación Renaser

**Input**: `specs/022-formaciones/` (spec, plan, research, data-model, contracts/formaciones-api.md, quickstart)
**Tests**: obligatorios (constitución III): unitarias de dominio y servicios, controller, IT de aislamiento y de migración, `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`.
**Rutas**: `B:` = backend `src/main/java/com/renaser/os/`, `BT:` = backend `src/test/java/com/renaser/os/`, `BR:` = `src/main/resources/db/migration/`, `A:` = app `src/`.
Regla de las rutas: `[P]` = se puede hacer en paralelo (archivos distintos).

## Phase 1: Setup (modelo de datos y migración)

- [x] T001 `BR:V125__formaciones_catalogo_y_pertenencia.sql`: tabla `formaciones` (`codigo` PK `CHECK codigo = upper(codigo)`, `nombre`, `descripcion`, `activa`, `orden`, `duraciones_dias smallint[]` con `CHECK` 1 a 4 valores entre 1 y 365, `version`, `actualizado_en`, `actualizado_por`); 3 filas (ESCUELA {90}, DIPLOMADO {90,365}, CERTIFICACION {90}); `participantes_programa.formacion text NOT NULL DEFAULT 'ESCUELA'` FK, `duracion_dias smallint NOT NULL DEFAULT 90 CHECK (BETWEEN 1 AND 365)`, `CHECK dia_programa BETWEEN 0 AND duracion_dias`; `celulas.formacion text NOT NULL DEFAULT 'ESCUELA'` FK + índice. Cabecera con el porqué.
- [x] T002 `BR:V126__formaciones_del_personal.sql`: `formaciones_personal(usuario_id, formacion, activa, asignada_en, asignada_por)` PK compuesta, índice único parcial `(usuario_id) WHERE activa`; el personal existente (MENTOR, GUIA_APRENDIZ, LIDER_MENTORES) recibe ESCUELA activa.
- [x] T003 `BR:V127__participaciones_anteriores.sql`: historial append-only (`resultado` `TERMINADA|DADA_POR_TERMINADA`, `motivo` 3–500 obligatorio si dada por terminada) + trigger que impide UPDATE/DELETE.
- [x] T004 `BR:V128__dia_del_programa_hasta_365.sql`: topes `0..365` en `ajustes_dia_programa`, `registros_habito.dia_programa`, `solicitudes_emergencia.dia_al_pedir`, `publicaciones_muro.dia_programa`.
- [x] T005 [P] `BT:users/MigracionAFormacionesIT`: IT con Testcontainers — todo lo sembrado antes queda en ESCUELA/90, el personal con ESCUELA activa, los CHECK rechazan lo inválido.

## Phase 2: Foundational (punto único de formación, filtro y dominio de duración)

- [x] T006 [P] `B:shared/domain/Formacion` (enum ESCUELA/DIPLOMADO/CERTIFICACION, `codigo()`, `desde(String)`), `AlcanceDeFormacion` (valor), `ObjetoDeOtraFormacionException` (extiende `NoSuchElementException` → 404). Pruebas `BT:shared/domain/AlcanceDeFormacionTest`.
- [x] T007 [P] `B:shared/infrastructure/persistence/FiltroDeFormacionSql` (predicado `col = ANY(?)`, alcance vacío → `FALSE`). Prueba `BT:shared/.../FiltroDeFormacionSqlTest`.
- [x] T008 [P] `B:users/domain/model/formacion/DuracionDelPrograma`, `FasesDelPrograma` (cortes estirados `(corte−1)·duracion/90+1`) y `users/api/FasePrograma.paraDiaPrograma(dia, duracion)`. Pruebas: con 90 reproduce los cortes 1/8/35/65; con 365 → 1/29/138/260.
- [x] T009 `B:users/domain/model/participante/ParticipacionPrograma`: campos `formacion` y `duracionDias`, tope del día y graduación por la duración propia, `fechaGraduacionEsperada`/`ultimaFechaDelPrograma` con la duración, `fijarDia` hasta `duracion − 1`; firma `rehydrate` nueva (las anteriores asumen ESCUELA/90). Prueba `BT:users/domain/.../ParticipacionProgramaFormacionTest` (365 días: día 365 gradúa, el 90 no; reloj en madrugada UTC).
- [x] T010 `B:users/infrastructure/.../ParticipacionProgramaJpaEntity` + mapper + `ConsultarResumenParticipacionPersistenceAdapter`: leer/escribir `formacion` y `duracion_dias`; `users.api.ParticipacionPrograma` suma `formacion` y `duracionDias` (aditivo).
- [x] T011 `B:users/api/AlcanceDeFormacionFinder` + `users/application/services/AlcanceDeFormacionService` + puerto out `formaciones_personal` (JDBC). Pruebas: admin/alquimista/soporte → todas; aprendiz → la suya; personal → solo la activa; sin asignación → vacío.
- [x] T012 `B:users/api/FormacionesFinder` (catálogo: nombre, duraciones, etiquetas) + `AprendizTrasladadoDeFormacionEvent`.

## Phase 3: US1 — Entro y veo mi formación, sin elegir nada (P1)

**Independent test**: login/`GET /auth/me` de tres aprendices (uno por formación) y de un mentor de una sola: cada uno trae su `formacion`; un cliente sin el campo sigue igual.

- [x] T013 [US1] `B:users/api/FormacionDeLaSesion` + `ConsultarFormacionDeLaSesionUseCase` y servicio (activa, disponibles, `puedeElegir`, `veTodas`, `sinFormacion`, `duracionDias`, `fases`).
- [x] T014 [US1] `B:users/infrastructure/.../UserResponse` + `AutenticacionController` (`login`, `me`, `social`) y `UserController` (`POST /users/me`): campo aditivo `formacion` (un solo caso de uso `ArmarPerfilDeSesion`).
- [x] T015 [US1] `B:users/infrastructure/.../FormacionDeLaSesionController`: `GET /api/v1/me/formacion` (`USE_APP`, `@SinInterruptor`). Pruebas de controller (200, sesión, 403 `SUSPENDED`). _(Cerrado después: `FormacionDeLaSesionControllerTest` y `FormacionesAdminControllerTest`.)_
- [x] T016 [US1] Reconstruir cuando falten datos: la cuenta sin fila de programa se trata como Escuela (aprendiz) — prueba.

## Phase 4: US2 — Nada se cruza entre formaciones (P1)

**Independent test**: IT de aislamiento — 1 aprendiz y 1 mentor por formación; ninguna lectura de personas y grupos de una muestra a la otra (las 6 combinaciones).

- [x] T017 [US2] Admin Personas: `GET /admin/trainees` (+ detalle) con `?formacion=`, filas con `formacion` y `duracionDias` (`ConsultarResumenParticipacionPersistenceAdapter`, `ListTraineesUseCase`, `TraineeSummaryResponse`, `TraineeDetailResponse`).
- [x] T018 [US2] `celulas.formacion`: `Celula` (dominio) + entidad JPA + mapper + `CrearCelulaRequest`/`CelulaResponse`; `?formacion=` en `GET /admin/cells` y `/admin/cells/dashboard`.
- [x] T019 [US2] FR-010: crear/editar grupo y sumar mentor o aprendiz (principal o adicional) valida la formación (`ComposicionDeCelulaService`, `SumarAprendizAGrupoService`, `SumarMentorAGrupoService`, candidatos); error `409` claro con las dos formaciones.
- [x] T020 [US2] Recepción por formación (`ConsultarRecepcionVigentePort`, `IngresoARecepcionService`): el aprendiz entra al grupo de bienvenida de **su** formación.
- [x] T021 [US2] Mentor y líder por alcance: `/mentor/context`, `/me/cells`, `/me/cell`, `/leadership/mentors`, `/leadership/people`, semáforo por grupos y bandeja de consultas filtran por el alcance de quien pide; pedir por id algo de otra formación → 404. _(Cerrado después: `acompanaVigente` exige la formación activa, lo que cubre `/mentor/context` y todo acceso por id de grupo; probado en `GruposPorFormacionIT`.)_
- [x] T022 [US2] Directorio de chat 1 a 1 y búsqueda de personas (`BusquedaDeUsuariosFinder`): solo personas del alcance.
- [x] T023 [P] [US2] `BT:users/AislamientoDeFormacionesIT` (las 6 combinaciones) y `BT:community/GrupoPorFormacionIT`. _(Parcial: `FormacionesIT` (6 pares de aislamiento) y `GruposPorFormacionIT`; no se llaman `AislamientoDeFormacionesIT`/`GrupoPorFormacionIT`.)_

## Phase 5: US3 — Todo lo de hoy sigue funcionando como Escuela (P1)

- [x] T024 [US3] Verificar con `MigracionAFormacionesIT` (T005) día/puntos/grupo/ranking sin cambios; reutilizar las pruebas existentes de `users`, `community` y `points` afectadas y arreglar lo que rompa.
- [x] T025 [US3] Compatibilidad del APK 1.6.0: controllers que reciben requests sin `formacion` caen a ESCUELA; documentarlo en `contracts/`.

## Phase 6: US4 — Administración gestiona las tres formaciones (P2)

- [x] T026 [US4] `B:leadership/…/FormacionesAdminController`: `GET /admin/formaciones`, `GET /admin/formaciones/{codigo}` (cifras desde `users.api`, `community.api`), `PUT /admin/formaciones/{codigo}` (nombre, descripción, `activa`, duraciones, `version`); permiso `MANAGE_TRAINEES`; funcionalidad `admin.personas`.
- [x] T027 [US4] `ConfigurarFormacionUseCase` en `users` (valida nombre 3–80, descripción ≤ 600, 1–4 duraciones únicas 1–365; `version` optimista).
- [x] T028 [P] [US4] Pruebas de controller y servicio (403 sin permiso, 403 `SUSPENDED`, 409 de versión). _(Cerrado después: además `CentroDeFormacionesControllerTest` y `FormacionesAdminControllerTest`.)_

## Phase 7: US5 — Duración y días según la formación (P2)

- [x] T029 [US5] El barrido (`AvanzarDiaProgramaScheduler`/`RelojProgramaService`) y el resumen de lectura usan la duración propia; `phasecontracts` toma la duración para sus fases; textos «de 90» fijos del programa del servidor (`Publicacion`, `RegistroHabito`, `PublicacionMuroService`) aceptan hasta 365.
- [x] T030 [US5] `ElegirDiaUno`/activación: la participación nueva nace con la duración elegida al aceptar (por defecto la única de su formación); aceptar «Elegir mi Día 1» del personal toma la formación activa (Q9-A). _(Parcial: el personal elige su Día 1 en su formación activa (`ProgramaPropioDelPersonal`); la duración que se elige al ACEPTAR a un aprendiz llega con la 025.)_
- [x] T031 [P] [US5] Pruebas: día 365 gradúa; día 90 de un Diplomado de 365 no gradúa; fases estiradas.

## Phase 8: US6 — El personal con varias formaciones (P2)

- [x] T032 [US6] `PUT /me/formacion/activa` (`ElegirFormacionActivaUseCase`); `PUT /admin/users/{id}/formaciones` (`AsignarFormacionesDelPersonalUseCase`, FR-011: no quitar una formación donde lidera un grupo o tiene aprendices); listener de `RolDeUsuarioCambiadoEvent` (personal nuevo → ESCUELA; deja de ser personal → sin filas).
- [x] T033 [P] [US6] Pruebas de servicio y controller. _(Cerrado después: además los `WebMvcTest` de los tres controllers.)_

## Phase 9: US7 — Pasar a un aprendiz de formación (P3)

- [x] T034 [US7] `B:users/domain/model/formacion/ReglasDeTraslado`, `ParticipacionAnterior`; `ParticipacionPrograma.trasladarA(...)`; `TrasladarAprendizDeFormacionService`; `POST /admin/trainees/{id}/formacion`; historial en el detalle.
- [x] T035 [US7] Listeners: `community` (sale del grupo + entra a la recepción de la formación nueva) y `points` (ajuste del ledger a 100, coherencia 100, racha 0).
- [x] T036 [P] [US7] Pruebas: reglas del traslado, servicio, IT del traslado.

## Phase 10: App

- [x] T037 [P] `A:features/formaciones/` tipos, esquemas tolerantes (`formacion` opcional), `formacionesApi`, `FormacionContext`/`useFormacionDeLaSesion` (relee al volver al primer plano), `fasesDeLaDuracion`, `etiquetaDeDuracion` + pruebas.
- [x] T038 `A:features/auth` lee `formacion` de `/auth/me` y `/auth/login` y la entrega al contexto; sin el campo = Escuela.
- [x] T039 `A:features/home/hooks/useResumenHome`, `faseEnCurso` y `AGENTS`: «Día N de M» y fases según la duración de la persona (las 4 fases estiradas), sin tocar el diseño.
- [x] T040 `A:features/formaciones/components/SelectorDeFormacion` (hoja desde abajo en Yo → «Cambiar de formación», solo con varias; también al entrar sin activa elegida) y `NombreDeLaFormacion` (línea fija y discreta en Hoy cuando es de más de una o no es la Escuela). Al cambiar: recarga de todas las vistas.
- [x] T041 `A:features/admin/screens` «Formaciones» (lista de 3 tarjetas + detalle con Resumen, Personas, Grupos, Por atender; edición de nombre/descripción/duraciones) en «Más opciones»; filtro «Formación» en Personas y Grupos; ficha de la persona con formación, duración, historial y traslado/asignaciones.
- [x] T042 [P] Pruebas Jest de lo nuevo y de lo tocado; `tsc --noEmit` en 0 errores.
- [x] T043 [P] `.maestro/formaciones/*.yaml` (aislamiento Escuela vs Diplomado, personal con dos formaciones, Administración).

## Phase 11: Polish

- [x] T044 `docs/MODULOS_A_AVANZAR.md` D-295, `docs/MODULO_USERS.md`, `docs/BITACORA_ERRORES.md` si aplica; `AGENTS.md` de la app (entrada del 2026-10-31).
- [x] T045 `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest` en verde; `graphify update .` en cada worktree.

## Dependencias
Setup → Foundational → US1 (la sesión) → US2 (aislamiento) → US3 → {US4, US5, US6} → US7 → App → Polish. US4, US5 y US6 son independientes entre sí.

## Estrategia
MVP = Setup + Foundational + US1 + US2 + US3 (la base que desbloquea 023 y 024). Después US5/US6/US4 y por último el traslado y la app.
