# Tasks: Hábitos vigentes al cambiar de día

## Phase 1: Setup

- [x] T001 Crear spec, clarificaciones, investigación, plan, modelo y contratos en `specs/002-habitos-cambio-de-dia/`.
- [x] T002 Registrar autorización y referencias en app `AGENTS.md`, `specs/README.md` y backend `docs/MODULOS_A_AVANZAR.md`, `docs/BITACORA_ERRORES.md` (D-266/E-608).

## Phase 2: Fundamentos — FR-001, FR-007

- [x] T003 [US1] Añadir pruebas de contexto, países y DST en backend `DiaDeHabitosTest.java` y `DiaDeHabitosIT.java` y contrato/autorización en `DiaDeHabitosControllerTest.java`.
- [x] T004 [US1] Implementar lectura aditiva en backend `ConsultarTracksDelDiaConCatalogoUseCase.java`, `TracksDelDiaProyeccionService.java`, `HabitTrackController.java`, `DiaDeHabitosResponse.java`.
- [x] T005 [US1] Implementar adaptador tolerante y fallback en app `habits/api/diaDeHabitosApi.ts`.

## Phase 3: US1 — FR-002, FR-008

- [x] T006 [US1] Añadir regresiones con reloj/actividad/foco y cargas vencidas en app `training/hooks/__tests__/cambioDeDia.test.ts`.
- [x] T007 [US1] Conectar vigencia y actualización en `training/hooks/useTraining.ts`, `useActualizacionDelDia.ts`, `training/api/cargarEntrenamiento.ts`, `screens/TrainingScreen.tsx`.

## Phase 4: US2 — FR-003, FR-004, FR-006

- [x] T008 [US2] Añadir pruebas de preflight, carrera 409, evidencia y preservación de ID en `habits/api/__tests__/registroVigente.test.ts`.
- [x] T009 [US2] Implementar guardas y aviso compartido en `habits/api/registroVigente.ts`, `evidenciaHabitoApi.ts`, `habits/eventos/diaDeHabitosCambiado.ts`; recuperación de cámara en `habits/hooks/useRegistroConFoto.ts`.

## Phase 5: US3 — FR-005, FR-007

- [x] T010 [US3] Probar falta de conexión, reintento y backend anterior en `training/hooks/__tests__/cambioDeDia.test.ts` y `habits/api/__tests__/diaDeHabitosApi.test.ts`.

## Phase 6: Validación

- [x] T011 Ejecutar tsc, Jest completo, exportación web, backend clean verify; registrar resultados en `validation.md` y actualizar documento del incidente.
- [x] T012 Ejecutar `graphify update .` en los dos repos y revisar diff final sin incluir cambios ajenos.

Dependencias: T001 → T003/T006/T008 (pruebas primero); T003 → T004 → T005 → T007; T008 → T009; T007/T009 → T010 → T011 → T012. T002 acompaña el cambio. La investigación del plan se delegó como solo lectura; implementación secuencial para evitar conflictos en archivos.
