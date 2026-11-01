# Tasks: Análisis 80/20 con IA

**Input**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/api.md`. Estado: **[x]** hecho · **[~]** hecho y escrito, pendiente de correr en la máquina compartida · **[ ]** pendiente.
Reglas: números V153 y V154 (V155 no se usa) y D-306; commits por paso, en castellano y sin coautoría; comandos pesados solo con `pesado.sh`; sin emulador.

## Phase 1: Setup
- [x] T001 Spec con las decisiones del dueño (031-1 A y B, 031-4 B, 031-8 B, el resto A) y los marcadores resueltos
- [x] T002 Fusionar 022 (formaciones), 030 (dictado) y 034 (`radar.fichas`) en la rama

## Phase 2: Cifras (módulo `rocks`) — US1
- [x] T003 `CalendarioDelAnalisis` (puro): semanas 1–13 como `SemanaPrograma`, desde la 14 sin tope; meses de 4 semanas con la 13 en el mes 3 + `CalendarioDelAnalisisTest`
- [x] T004 `ArmadoDel8020` (puro): regla de las verdes, regla del 20 % (`ceil(n/5)` desde 5 cumplidas), desempates, «hasta hoy», lo que se fue, resumen del cierre + `ArmadoDel8020Test`
- [x] T005 `Pareto8020Finder` / `PeriodosDelProgramaFinder` (api) y sus servicios; `LoadRocaDiariaPort.deParticipanteEntreFechas` y marcas de `EvaluacionDelDiaPort`
- [x] T006 `RespuestasDelRadarFinder` en `habits` (+ `LoadRegistroRadarPort.entre`)

## Phase 3: Generación (módulo `rag`) — US2, US3, US4
- [x] T007 V153 + V154 (`analisis_80_20`, tipo de notificación)
- [x] T008 Dominio: `TextoDelAnalisis`, `ReglasDelAnalisis`, `AnalisisDelPeriodo` (estados), `ValidadorDelTexto` + pruebas
- [x] T009 Puertos y adaptadores: `GenerarAnalisis8020Port` (Gemini sobre el `ChatModel` de SER; noop), `AnalisisDelPeriodoJdbcAdapter`
- [x] T010 `ReunirInsumosDelAnalisis`, `ArmadoDelPedido` (qué sale al proveedor), prompt `analisis-80-20.txt`
- [x] T011 `GeneracionDeAnalisis8020Service` (mínimos, circuito, tope, validación, reintentos) + `BarridoDeAnalisis8020Service` + `GenerarAnalisis8020Scheduler` (`@SchedulerLock`)
- [x] T012 `RegistroDelAnalisis` (`@Transactional` corto + `AnalisisListoEvent`)
- [x] T013 `ia.analisis` en el catálogo y en `InterruptoresDeSer`

## Phase 4: Lectura y fichas — US1, US6
- [x] T014 `ConsultaDelAnalisis8020Service` + `Analisis8020Controller` (la propia persona)
- [x] T015 `AnalisisDelAlumnoService` + `AnalisisDelAlumnoController` (mentor/guía, `radar.fichas`)
- [x] T016 `AnalisisDePersonaService` + `AnalisisDeLaPersonaController` (liderazgo, constancia, `radar.fichas`)
- [x] T017 `AnalisisSoloSaleConLosPermisosDeLaFichaTest` (ArchUnit: ninguna puerta nueva sin guardia)

## Phase 5: SER, aviso, borrado — US5
- [x] T018 `ConsultarAnalisis8020Herramienta` (`consultar_analisis_80_20`)
- [x] T019 `AnalisisListoNotificationListener` + `TipoNotificacion.ANALISIS_80_20`
- [x] T020 `BorradoDeCuentaEnRagAdapter` borra `analisis_80_20`

## Phase 6: Pruebas de punta a punta y cierre
- [x] T021 `Analisis8020IT` (barrido → fila → lectura → aviso; diferir; sin datos; agotado; formaciones aisladas) y `AnalisisDelLiderIT`
- [x] T022 `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`, `RutasCubiertasPorElFiltroTest` en verde (esta última atrapó que `/api/v1/analisis-80-20` no tenía matcher `.authenticated()` en `SecurityConfig`: se agregó). El `verify` completo lo corre la coordinación con todas las ramas juntas.
- [x] T023 D-306 en `docs/MODULOS_A_AVANZAR.md` y la herramienta en `docs/MODULO_RAG.md`

## Phase 7: App (`Renaser-90-dias-frontend-`)
- [x] T024 `features/ochenta-veinte/` (esquemas tolerantes, API de las tres puertas, hook con almacén, componentes, textos)
- [x] T025 La hoja «Tu 80/20» de Plan con Hoy · Semana · Mes; las tres fichas; el aviso `/plan/80-20`; el interruptor de avisos
- [x] T026 Pruebas de Jest, entrada en `AGENTS.md`, Política de Privacidad, flujos de Maestro `.maestro/analisis-80-20/` (escritos)
- [x] T027 `tsc --noEmit` en 0 y Jest de las carpetas tocadas en verde (el Jest completo lo corre la coordinación)
