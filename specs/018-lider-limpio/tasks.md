# Tasks: Vista del líder, limpia

Estado al 2026-10-29: **[x]** hecho y probado con unitarias · **[ ]** pendiente.

## Backend (`018-lider-limpio`)

- [x] T001 `rocks.api.AccionDelDia` + `horaInicio` y `agendada()`; `AccionesDelDiaFinderService` la llena
- [x] T002 Prueba de `AccionesDelDiaFinderServiceTest`: trae la hora o nada
- [x] T003 `ProgramacionDelDia` (dominio): cuentas, invariantes, `programo()` y `de(...)` vacío sin hábitos
- [x] T004 `ProgramacionDelDiaTest`: la mitad justa, menos de la mitad, una acción sin hora, sin acciones, un solo hábito,
      sin hábitos, cuentas imposibles (7 casos)
- [x] T005 `ProgramacionDeLaPersona`: lee `HorarioDelDiaFinder` y `AccionesDelDiaFinder`; no lee con el programa sin activar
- [x] T006 Cablear `LecturaDeLaFicha` → `ArmadoDeLaFicha` → `SemanaDeLaPersona.programacion` → `SemanaDeLaPersonaResponse`
- [x] T007 `BancoDeCumplimiento`: doble de `HorarioDelDiaFinder` y `habitoDeHoy(...)`
- [x] T008 `SemanaDePersonaServiceTest`: Sí con horas y acciones, una acción sin hora es No, apagados y en pausa no cuentan,
      sin hábitos es nulo, programa sin activar no pregunta, «hoy» en la madrugada UTC, no depende de la ventana, el mentor
      con programa propio, y sin permiso no se lee nada
- [x] T009 `CumplimientoDelLiderControllerTest`: el JSON trae `programacion` (o no lo trae); los 403 y 404 no leen hábitos
- [x] T010 `ArchitectureTest`, `EndpointAuthorizationDeclarationTest`, `FuncionalidadDeclaradaTest`
- [x] T011 Documentar: D-291 (`docs/MODULOS_A_AVANZAR.md`), E-654 (`docs/BITACORA_ERRORES.md`), contrato de la 005 ampliado
- [ ] T012 `clean verify` y las pruebas de integración (`CumplimientoDelLiderIT`): **no corridas** en esta tanda (prohibidas
      por la máquina compartida); quedan para el CI

## App (`018-lider-limpio`)

- [x] T020 `papelesDeHoy`, `papelRecordado` y `usePapelDeHoy` (puros y con AsyncStorage) y sus pruebas
- [x] T021 `ControlSegmentado.sinAnimacion`; `ControlDePapelDeHoy`
- [x] T022 `FilaDeLista`, `MiGrupoEnHoy`, `MisMentoresEnHoy` y sus pruebas montadas
- [x] T023 `HoyScreen`: control, papel a la vista, programa oculto y montado, tarjetas del líder fuera, ficha que vuelve a Hoy,
      deslizar relee
- [x] T024 `CumplimientoDelLiderScreen.grupoInicial` y su prueba
- [x] T025 Esquema y tipo de `programacion` (tolerante) y sus pruebas
- [x] T026 `respuestasDeLaPersona` + `RespuestasDeLaPersona` en `PersonaDelLiderScreen` y sus pruebas
- [x] T027 Menos texto: las 11 filas de la tabla de la US3, con sus pruebas actualizadas
- [x] T028 `AGENTS.md`: la entrada del 2026-10-29 después de la spec 016
- [ ] T029 E2E en el emulador (Maestro y mobile-mcp) y APK: **no corridos**; los hace quien tenga el emulador
