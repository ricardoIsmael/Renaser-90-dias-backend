# Tasks: Cierra tu día

**Input**: `spec.md`, `plan.md`, `data-model.md`, `contracts/api.md`. Tests incluidos (la constitución los exige).
**Leyenda**: `[x]` hecho y probado · `[~]` escrito sin correr (necesita Docker, prohibido en esta corrida) · `[ ]` pendiente (fuera del backend).

## Fase 1 — Base de datos y dominio (US1, US2)
- [x] T001 `V121__cierre_del_dia.sql`: cuatro columnas nulas en `rocas_diarias` + `CHECK` (valores, completo, nota con respuesta), cabecera que justifica.
- [x] T002 `rocks.domain.model.cierre`: `MotivoDeAccionPendiente`, `PlanDeManana`, `NotaDeCierre`, `RespuestaDeCierre`, `CierreDeAccion`, `EstadoDelCierre`, `SemanaDeCierres`, `CierreDeOtroDiaException`.
- [x] T003 `RocaDiaria.cierre()`, `cerrarConRespuesta`, `quitarCierre`, `rehydrate` con cierre (el anterior se conserva); `CierreDelDia` (todo o nada, una respuesta por pendiente, solo hoy).
- [x] T004 Pruebas de dominio: `NotaDeCierreTest`, `EstadoDelCierreTest`, `SemanaDeCierresTest`, `CierreDelDiaTest` (SC-001: no toca completada ni puntos), `RocaDiariaCierreTest`.

## Fase 2 — Persistencia (FR-003, FR-004, FR-009)
- [x] T005 `RocaDiariaJpaEntity` + mapper (ida y vuelta, un valor roto no tumba la lectura) + `SaveRocaDiariaPort.guardarSoloElCierre` con `UPDATE` dirigido (`WHERE completada = false`).
- [x] T006 `habits.api.EvaluacionDelDiaPort` + `EvaluacionDelDiaService` + `LoadEntradaDiarioPort.entreFechasDeTipo`; `EntradaDiarioFinder.entradasEntre` excluye `EVALUACION_DIA`.
- [x] T007 `RocaDiariaCierrePersistenciaTest` (sin base), `EvaluacionDelDiaServiceTest`.
- [~] T008 `CierreDelDiaIT` (Postgres real: CHECK de V121, cerrar no toca completada/puntos ni con un cumplimiento a la vez, la marca, el Espejo no la lee, la lectura de la semana).

## Fase 3 — Casos de uso y rutas de la persona (US1, US2, US3)
- [x] T009 `CerrarElDiaUseCase`, `ConsultarCierreDelDiaUseCase`, `CierreDelDiaService`, `ArmadoDelCierre`.
- [x] T010 `CierreDelDiaController` (`GET/PUT /rocks/day-closure`, `FOLLOW_OWN_PROGRAM`, `@Funcionalidad("plan")`, `no-store`) + DTOs.
- [x] T011 `CierreDelDiaServiceTest` (reloj en la madrugada UTC, 409/400/403/404, personal con programa), `ArmadoDelCierreTest`, `CierreDelDiaControllerTest`.

## Fase 4 — Acompañamiento (US4)
- [x] T012 `rocks.api.CierresDelDiaFinder` + `CierresDeLaSemanaService`; ruta del mentor/guía (`CierresDelAlumnoService`, `CierreDelAlumnoController`).
- [x] T013 Ruta del liderazgo: `ConsultarCierresDePersonaUseCase`, `CierresDePersonaService` (constancia en `consultas_de_ficha`), `CierresDeLaPersonaController`.
- [x] T014 `CierresDeLaSemanaServiceTest`, `CierresDelAlumnoServiceTest`, `CierresDePersonaServiceTest`, `CierreDelAlumnoControllerTest`, `CierresDeLaPersonaControllerTest` (autorización negativa).

## Fase 5 — Compuertas y documentación (SC-003, constitución VII)
- [x] T015 `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`, `RutasCubiertasPorElFiltroTest` en verde.
- [x] T016 D-290 en `docs/MODULOS_A_AVANZAR.md` (+ nota del grafo de módulos), `MODULO_ROCKS`, `MODULO_HABITS`, `MAPA_ENDPOINTS`.
- [x] T017 `graphify update .`.

## Fuera del backend / pendiente
- [ ] La app (rama `017-cierre-del-dia` del frontend): fila, hoja, días anteriores, fichas, `AGENTS.md`.
- [ ] Sumar el texto del cierre a la Política de Privacidad antes de publicar.
- [ ] Correr `./mvnw verify` completo (Docker) antes de fusionar, y fusionar 015 antes (V120) o renumerar.

## Análisis de consistencia (speckit-analyze, a mano)
Cada FR tiene tarea y prueba: FR-001/002 → T003/T004/T011; FR-003 → T005/T008/T011 (+ `RocaDiariaCierrePersistenciaTest`); FR-004 → T006/T007; FR-005 → T002/T009;
FR-006 → T012–T014; FR-007 → T010/T012/T013/T015; FR-008 → T001; FR-009 → T006/T008; FR-010–014 → app. Sin contradicciones entre spec, plan y contrato tras
corregir `horaInicio` (`HH:mm:ss`) y `semana` (`null` también sin acciones planificadas).
