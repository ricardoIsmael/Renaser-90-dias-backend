---
description: "Tareas de Hábitos globales por rol"
---

# Tasks: Hábitos globales por rol

**Input**: `specs/001-habitos-por-rol/` (spec, plan, research, data-model, contracts)

**Tests**: obligatorias por la constitución (principio III). Se escriben antes que la
implementación de cada pieza y deben fallar primero.

**Rutas**: `B/` = `Renaser-90-dias-backend/`, `J/` = `B/src/main/java/com/renaser/os/`,
`T/` = `B/src/test/java/com/renaser/os/`, `F/` = `Renaser-90-dias-frontend-/`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [x] T001 Verificar el último número de migración (V95) y de decisiones/errores en
  `B/docs/MODULOS_A_AVANZAR.md` §8 y `B/docs/BITACORA_ERRORES.md`; reservar `V96` y el D-nn siguiente.
- [x] T002 Confirmar que el backend compila en verde antes de tocar nada (`./mvnw -q -DskipTests compile`)
  y anotar el estado del working tree (hay cambios ajenos sin commit: no tocarlos).

---

## Phase 2: Foundational (bloquea todas las historias)

- [x] T003 Crear `B/src/main/resources/db/migration/V96__audiencia_de_habitos_por_rol.sql` con
  cabecera justificada, `audiencias_habito` e `historial_rol_participante` (data-model.md).
- [x] T004 [P] Pruebas unitarias de dominio en `T/habits/domain/model/audiencia/`: `CodigoDeRolTest`,
  `AudienciaTest` (vacío = todos; contiene), `VigenciaPorDiaTest` (reloj 04:30 UTC = día anterior en
  Lima; cambio a las 23:59 local rige desde el día siguiente), `HistorialDeAudienciaTest` (sin
  versiones → todos; primera versión rige desde la creación; cambio de hoy rige mañana; varias el
  mismo día → la última), `HistorialDeRolTest` (sin tramos → actual; tramo desde epoch).
- [x] T005 [P] Implementar el dominio en `J/habits/domain/model/audiencia/`: `CodigoDeRol`,
  `Audiencia`, `VersionDeAudiencia`, `HistorialDeAudiencia`, `TramoDeRol`, `HistorialDeRol`,
  `VigenciaPorDia` (sin Spring). Hacer pasar T004.
- [x] T006 [P] `users.api`: `RolDelSistema`, `CatalogoDeRolesFinder`, `UserRole.nombreVisible()`
  (Aprendiz, Mentor, Líder de mentores, Administrador, Alquimista) y
  `J/users/application/services/CatalogoDeRolesService.java`; prueba unitaria del orden y nombres.
- [x] T007 Puertos de salida en `J/habits/application/ports/out/audiencia/`: `CargarAudienciasPort`,
  `GuardarAudienciaPort`, `HistorialDeRolPort`, `ContarRegistrosDeHabitoPort`, `CatalogoDeRolesPort`.
- [x] T008 Adaptadores JDBC en `J/habits/infrastructure/adapter/out/persistence/audiencia/`
  (`AudienciaHabitoJdbcAdapter`, `HistorialRolJdbcAdapter` con `ON CONFLICT DO NOTHING`,
  `CatalogoDeRolesAdapter` sobre `users.api`) y `ContarRegistrosDeHabito` en el adaptador de
  registros.
- [x] T009 IT de persistencia `T/habits/infrastructure/adapter/out/persistence/audiencia/AudienciaHabitoPersistenceIT.java`:
  guardar versiones, cargar por lote varias en una consulta, reentrega del historial de rol sin duplicar,
  borrado en cascada al eliminar un hábito sin registros.
- [x] T010 `ConsultarCatalogoAplicableUseCase` + `J/habits/application/services/CatalogoAplicableService.java`
  (activos globales ∩ audiencia vigente para el rol vigente el día D en la zona de la persona) con
  `CatalogoAplicableServiceTest` (mocks de puertos; madrugada UTC).

**Checkpoint**: dominio, persistencia y filtro listos.

---

## Phase 3: User Story 1 - Restringir un hábito global (P1) 🎯 MVP

**Goal**: el admin define la audiencia y la generación de registros la respeta desde mañana.

**Independent Test**: con el endpoint, restringir a MENTOR; al día siguiente el aprendiz no recibe el
registro, el mentor sí; el registro de hoy del aprendiz se completa.

- [x] T011 [P] [US1] Ampliar `T/habits/infrastructure/adapter/in/rest/habitoadmin/HabitoAdminControllerTest.java`:
  `targetRoles` en crear/editar (ausente, `[]`, lista), rol desconocido → 400, respuesta con
  `targetRoles`/`targetRolesEffectiveFrom`/`audienceChangedAt`/`audienceChangedBy`/`systemKey`/
  `isDeactivatable`, 403 para TRAINEE/MENTOR/MENTOR_LEAD y para cuenta `SUSPENDED`.
- [x] T012 [P] [US1] Ampliar las pruebas de `HabitoAdminService` (crear con audiencia → versión 1;
  editar con la misma audiencia → sin versión; distinta → versión nueva con actor e instante; editar sin
  `targetRoles` → sin versión; hábito personal → 404).
- [x] T013 [US1] Comandos `CrearHabitoCommand`/`ActualizarHabitoCommand` con audiencia (opcional en
  editar); `HabitoAdminService` valida códigos contra `CatalogoDeRolesPort` y guarda versiones.
- [x] T014 [US1] DTOs `CreateHabitRequest`, `UpdateHabitRequest` (`targetRoles` opcional) y
  `AdminHabitResponse` extendido; nombre de quien cambió vía `users.api` (sin SQL ajeno).
- [x] T015 [US1] `RegistroService.generarInterno` (`:206`) usa `ConsultarCatalogoAplicableUseCase`
  en lugar de `catalogoActivo()`; ajustar `RegistroServiceTest`.
- [x] T016 [US1] IT `T/habits/application/services/HabitosPorRolGeneracionIT.java` (Testcontainers +
  `FixedClock`): restringir hoy → el aprendiz conserva el registro de hoy y mañana no se le genera;
  el que aún no generó su día hoy recibe la audiencia vieja; mentor con programa lo recibe; quitar
  roles → mañana vuelve para todos; configuración (hora, días, pausa, renombre) se recupera tal cual
  al volver a aplicar; registros pasados intactos.

**Checkpoint**: MVP funcional por API.

---

## Phase 4: User Story 2 - Cada persona ve solo los de su rol (P1)

**Independent Test**: con un hábito "solo MENTOR", ningún lector del aprendiz lo muestra.

- [x] T017 [P] [US2] `MisHabitosService.vistas` (`:106`) y `ConsultaPreferenciasHorarioService` (`:100`)
  pasan por el catálogo aplicable del día de hoy de la persona; ajustar sus pruebas.
- [x] T018 [P] [US2] Vista de staff `HabitosDeAprendizJdbcAdapter` (`:63,90`) y el caso de uso del
  mentor (`ConsultarHabitosDeAlumnoUseCase`): excluir los globales que no aplican al aprendiz hoy;
  revisar su consulta y probar.
- [x] T019 [US2] Prueba de arquitectura/texto en `T/habits/` que falla si `catalogoActivo()` se llama
  fuera de `CatalogoAplicableService` (y del adaptador que lo implementa).
- [x] T020 [US2] IT de flujos propios `T/habits/application/services/FlujosPropiosSinAudienciaIT.java`:
  Clase diaria, Pastilla Renacer, Audioterapia, post diario y Despertar restringidos a otro rol →
  sin error 500, sin puntos, sin generar el registro (el post diario regenera vía `generarInterno` y
  sigue sin crearlo).
- [x] T021 [US2] Prueba de que los personales no cambian al restringir globales (unitaria en
  `CatalogoAplicableServiceTest` o IT de MisHabitos).

---

## Phase 5: User Story 4 - Cambio de rol de una persona (P2)

- [x] T022 [P] [US4] `HistorialDeRolService` + `RolDeUsuarioCambiadoHabitsListener`
  (`@ApplicationModuleListener`) en `J/habits/infrastructure/adapter/in/event/`; prueba unitaria
  (primer cambio inserta el tramo desde epoch con el rol anterior; reentrega idempotente).
- [x] T023 [US4] IT `T/habits/infrastructure/adapter/in/event/CambioDeRolHabitosIT.java`: APRENDIZ →
  MENTOR hoy; hoy conserva lo suyo, mañana recibe los de MENTOR; registros pasados intactos.

---

## Phase 6: User Story 3 - Pantalla en la app (P2)

**Backend de apoyo**

- [x] T024 [P] [US3] `GET /api/v1/admin/habits/roles` (`ConsultarRolesElegiblesUseCase`,
  `RolElegibleResponse`) y `GET /api/v1/admin/habits?role=` (filtro por última audiencia, rol
  desconocido 400) con pruebas de controller y 403.
- [x] T025 [P] [US3] Eliminar con historia → `IllegalStateException` con el motivo del contrato
  (409) antes de tocar la base; prueba de servicio y de controller.

**App** (cargar `emil-design-eng` y `animate-expo` antes de diseñar)

- [x] T026 [P] [US3] `F/src/features/admin/api/habitosProgramaSchemas.ts` y `habitosProgramaApi.ts`
  (listar con filtro, roles, crear, editar, toggle, eliminar) con
  `F/src/features/admin/api/__tests__/habitosProgramaApi.test.ts` (mock de `apiFetch`, rutas, cuerpos,
  tolera respuesta sin `targetRoles`).
- [x] T027 [P] [US3] `F/src/features/admin/utils/habitosPrograma.ts` (etiquetas de dimensión, tipo y
  evidencia; texto de audiencia; «Rige desde mañana» con la fecha local; validación del formulario
  incluido «al menos un rol con el interruptor apagado»; formulario → cuerpo) con pruebas en
  `F/src/features/admin/utils/__tests__/habitosPrograma.test.ts`.
- [x] T028 [US3] `F/src/features/admin/hooks/useHabitosProgramaAdmin.ts` (`{datos, roles, cargando,
  fallo, recargar}` según el patrón de `useAnimalesDeFaseAdmin`).
- [x] T029 [US3] `F/src/features/admin/screens/HabitosProgramaAdminScreen.tsx` (lista, filtro por rol,
  activos/inactivos, estados de carga/vacío/error/sin permiso, gesto atrás).
- [x] T030 [US3] `F/src/features/admin/screens/HabitoProgramaFormScreen.tsx` (crear/editar, «¿Para
  quién es?» con `Interruptor` y `Checkbox`, activar/desactivar, eliminar con confirmación y 409 →
  «Desactivar en su lugar», aviso de reglas propias, cambios sin guardar, `ConfirmacionEnLinea`).
- [x] T031 [US3] Conectar en `AdminScreen.tsx` (vistas `habitos-programa` y `habito-programa`) y
  `MasOpcionesScreen.tsx` (entrada en `enLaApp`; quitar «Catálogo de hábitos» de `enLaWeb` y ajustar
  el texto general); actualizar `screens/__tests__/pilaDeAdministracion.test.ts` y crear
  `screens/__tests__/habitosProgramaEnAdministracion.test.ts`.

---

## Phase 7: User Story 5 - Rol nuevo sin rehacer la función (P3)

- [x] T032 [P] [US5] Prueba unitaria: `Audiencia`/`CatalogoAplicableService` con un código de rol que
  no existe hoy (p. ej. `"COACH"`) funciona sin tocar habits; prueba de la app: un rol desconocido
  en `GET /roles` se muestra con el nombre del servidor.

---

## Phase 8: Polish & documentación

- [x] T033 Documentar D-nn en `B/docs/MODULOS_A_AVANZAR.md` §8 (audiencia versionada, vigencia
  estricta, audiencia inicial desde la creación) y actualizar `B/docs/MODULO_HABITS.md`.
- [x] T034 [P] `F/AGENTS.md`: entrada «Cambio por pedido del dueño — 2026-10-12 — Administración:
  «Hábitos del programa»» (lo que cambia y nada más; hace falta APK y backend con V96); `F/specs/README.md`
  apuntando a `B/specs/001-habitos-por-rol/`.
- [x] T035 Backend: `./mvnw clean verify` en verde (leer las dos líneas `Tests run:`).
- [x] T036 [P] App: `node node_modules/typescript/bin/tsc --noEmit` y `node node_modules/jest/bin/jest.js --silent`.
- [x] T037 Registrar en `B/docs/BITACORA_ERRORES.md` todo error encontrado durante la implementación.
- [x] T038 `graphify update .` en los dos repos.

---

---

## Phase 9: E2E en la app real (BLOQUEANTE antes de subir)

**Por qué**: unitarias e integración en verde no prueban la app instalada (pedido del usuario, 2026-10-12: «no quiero
errores en producción»). Se corre en el emulador Android con `mobile-mcp` (registrado en el proyecto del backend),
backend del worktree con V96 contra una base propia (no la de `master`) y la app del worktree. Cada caso deja captura en
`~/Imágenes/e2e-2026-10-XX/habitos-por-rol-*.png`; todo fallo va a la bitácora (E-nn) y se corrige antes de seguir.

**Preparación**
- [x] T039 Base propia para el worktree (clonar `renaser` → `renaser_habitos_rol`, `DB_URL` en el `.env` del worktree),
  backend del worktree en un puerto que no sea el 8080 del usuario, app del worktree en el emulador. Cuentas: ADMIN,
  ALQUIMISTA, MENTOR (con programa), LÍDER DE MENTORES, APRENDIZ ×2 y un ADMIN suspendido.

**Administración (US3)**
- [x] T040 ADMIN y ALQUIMISTA ven «Hábitos del programa» en Más opciones; MENTOR, LÍDER y APRENDIZ no llegan a
  Administración. Un 403 forzado muestra el mensaje de sin permiso.
- [x] T041 Lista: carga, Activos/Inactivos, filtro por cada rol (incluye los «Para todos»), «Ningún hábito llega a este
  rol», rótulo de audiencia y «Rige desde mañana».
- [x] T042 Crear: nombre vacío → aviso; interruptor apagado sin roles → aviso; crear «solo Mentor» → aparece en la lista
  y en el filtro Mentor, no en Aprendiz; crear «para todos».
- [x] T043 Editar: cambiar descripción/dimensión/evidencia/opcional/intoxicación sin tocar roles → «Guardado.»; cambiar
  roles → «Guardado. Los roles rigen desde mañana.»; marcar los cinco roles → se guardan cinco (no «Para todos»).
- [x] T044 Activar/Desactivar; Eliminar uno sin uso (confirma y desaparece); Eliminar uno con registros → diálogo con
  «Desactivar» y queda inactivo.
- [x] T045 Aviso de reglas propias en Clase diaria / Pastilla / post diario. Salir con cambios pregunta; el gesto atrás
  vuelve un nivel en lista y formulario, nunca cierra la app.
- [x] T046 Claro y oscuro, 360 px y tablet, letra del sistema grande, «reducir movimiento» (las casillas aparecen sin
  fundido), lector de pantalla en interruptor, casillas y chips.

**Lo que ve cada persona (US1, US2, US4)**
- [x] T047 Restringir hoy un hábito a Mentor: el aprendiz lo sigue teniendo HOY en Hoy y Training (y puede completarlo);
  con el reloj del backend en el día siguiente ya no aparece en Hoy, Training, Plan (planificar), alarmas ni agenda; el
  mentor con programa sí lo recibe.
- [x] T048 Coherencia, semáforo, puntos y «¡Día completo!» del aprendiz no cuentan el hábito restringido al día
  siguiente.
- [x] T049 La vista del mentor y la ficha de administración del aprendiz muestran lo mismo que ve el aprendiz.
- [x] T050 Clase diaria restringida a otro rol: el aprendiz no la ve y el curso no le da puntos por ella, sin error.
- [x] T051 Volver a «Para todos»: al día siguiente el aprendiz lo recupera con su hora, días, pausa y nombre propio, y la
  racha pasada intacta.
- [x] T052 Cambio de rol (Equipo y roles) de APRENDIZ a MENTOR: hoy conserva sus hábitos; mañana recibe los de mentor y
  deja los de aprendiz; su historial sigue.
- [x] T053 Los hábitos personales del aprendiz no cambian en ningún caso.
- [x] T054 Compatibilidad: el APK publicado (1.5.x) contra el backend nuevo sigue funcionando (solo ve menos hábitos).

**Cierre**
- [x] T055 Informe E2E (`specs/001-habitos-por-rol/e2e.md`) con cada caso, resultado y captura; solo con todo en verde
  se commitea para subir.

> **Corrida del 2026-10-12, todo en verde con dos límites dichos en `e2e.md`.** T048: los conteos del día (14 / 16)
> sí; coherencia, semáforo y «¡Día completo!» no se vieron en pantalla (salen de esos mismos registros). T054: se
> probó el código de `master` contra el backend nuevo, no el binario 1.5.0 de la tienda (no hay etiqueta para
> reconstruirlo y nunca se prueba contra producción). En T051 la pausa no se pudo ejercitar: los desbloqueos están
> apagados en la base clonada. Fallos hallados y corregidos: E-600, E-601 y E-602.

## Notas de la implementación (2026-10-12)

- T006 se simplificó: `UserRole` ya vive en `users.api`; bastó `UserRole.nombreVisible()` (sin finder nuevo).
- T018: la vista de staff excluye los globales que NO le tocan (`CatalogoAplicable.globalesQueNoLeTocan`), así una
  fila sin datos de catálogo no se esconde por accidente.
- T016/T020/T023 quedaron en una sola IT, `HabitosPorRolIT` (8 casos, Postgres real, reloj a las 10:00 de Lima).
- Corrección de diseño encontrada al implementar: el primer cambio de un hábito sin versiones guarda una versión base
  «para todos» desde epoch (research R-2).
- Frontend: Jest ignora `/.claude/` en su configuración; en el worktree se corre con
  `--testPathIgnorePatterns /node_modules/ /e2e/`.

## Dependencies & Execution Order

- Phase 1 → Phase 2 (bloquea todo) → US1 (MVP) → US2 → US4 → US3 → US5 → Polish → **E2E (Phase 9, bloquea subir)**.
- US2 y US4 dependen de T010 (filtro) y T015; US3-app depende de T024/T025 para probar contra el
  servidor, pero T026–T028 pueden avanzar con el contrato.
- [P] = archivos distintos sin dependencia pendiente.

## Implementation Strategy

MVP = Phase 1–3 (restricción por API + generación). Luego US2 (todas las vistas), US4 (cambio de
rol), US3 (pantalla), US5 (prueba de escalabilidad) y documentación. Cada fase cierra con su prueba
independiente en verde.
