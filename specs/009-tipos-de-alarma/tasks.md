# Tasks: Tipos de alarma por día

**Rutas**:
- `back:` = `Renaser-90-dias-backend/.claude/worktrees/tipos-de-alarma/`
- `app:` = `Renaser-90-dias-frontend-/.claude/worktrees/tipos-de-alarma/` (se crea desde la rama 008 de la app cuando la 008 esté commiteada)

**Reglas**:
- Pruebas primero.
- Un commit por funcionalidad, en español y sin coautoría.
- Sin APK.
- Turno de máquina avisado antes de verify, Jest completo, emulador o Maestro.

## Phase 1: Setup

- [x] T001 Traer a la rama 009 lo último de la 008, en el backend y en la app. Confirmar V112, D-283 y E-641 libres.
  - Hecho en el backend (2026-10-20): la rama 009 ya contiene la 008 (merge `05723a63`). V112, D-283 y E-641 estan libres (grep en todas las ramas locales y de origin y en los worktrees). La parte de la app la trae su agente.

## Phase 2: Foundational (backend)

- [x] T002 `back:V112__combinacion_de_alarma.sql`: las 4 columnas nulas en las dos tablas, el CHECK de sonido o vibración y el CHECK ampliado de la fila semanal (data-model). Con cabecera.
  - Ademas de lo pedido: CHECK `..._alarma_voz_con_sonido` en las dos tablas (la voz es un sonido; mira solo la fila). El de la mezcla entre capas lo resuelve el dominio.
- [x] T003 [P] Prueba de `CombinacionDeAlarma`: herencia por campo, sonido y vibración no pueden ir los dos apagados, voz sin sonido → apagada, y el valor por defecto como hoy.
- [x] T004 Dominio `CombinacionDeAlarma`. `PreferenciaHorario` y `HorarioSemanal` la llevan opcional (la fila semanal encendida acepta solo combinación).
  - Dos tipos: `CombinacionDeAlarma` (resuelta, siempre valida) y `AjusteDeAlarma` (lo que una capa fija; campo en `null` = hereda). `HorarioSemanal` y `PreferenciaHorario` llevan el ajuste. Valor por defecto = sonido si, vibracion si, voz no, repetir no (el tono de Yo -> Alarmas es del telefono; el servidor no lo conoce).
- [x] T005 [P] Prueba del adaptador: resolución por capas de la combinación, fila de solo combinación, y que hora y aviso de la 008 sigan igual.
- [x] T006 Persistencia: entidades, mapper y adaptador. La combinación igual a la general queda en `NULL`, y la fila vacía se borra.
  - La combinacion se guarda ENTERA y, si es igual a la general, queda en NULL (lo decide el servicio, T008). Una fila nueva solo para la alarma general nace sin hora ni aviso (hora del catalogo intacta) y con `recordatorio_activo = true` como `PreferenciaHorario.crear`: `false` apagaria el push (`EntregaDelAvisoDeHabito`).

## Phase 3: US3 — Guardar y leer la combinación (P2, base de las demás)

- [x] T007 [P] [US3] Prueba del servicio: guardar la combinación general y la del día sin cupo, y las lecturas con `alarm` y `customAlarm`.
  - Servicio `PreferenciaHorarioService` (pruebas en `CombinacionDeAlarmaServiceTest` y `ConsultaPreferenciasHorarioServiceTest`): sin cupo, igual a la general hereda, y las mismas guardas del aviso (hábito inexistente, personal ajeno, cuenta suspendida).
- [x] T008 [US3] Casos de uso: `fijarCombinacion` (general y por día) y la consulta.
  - Los casos de uso son `EditarPreferenciaHorarioUseCase#fijarCombinacion` (general) y `EditarHorarioSemanalUseCase#fijarCombinacion` (día); la consulta suma `alarma` a `HorarioDeHabito` y a `DiaDeLaSemana`. Se agregaron a los casos de uso existentes y no a uno nuevo, para no tocar los tres controller tests de la 008 (un `@MockitoBean` más en cada uno). Desvío menor: `HorarioDeHabito.personalizado` (`customized`) deja de contar la fila que existe solo para la alarma.
- [x] T009 [P] [US3] Prueba del controller: `PUT …/alarm` y `PUT …/weekdays/{wd}/alarm` con 204, 400 y **403**; los GET con los campos nuevos.
  - Cubre 204, 400 (sonido y vibración apagados, voz sin sonido, campo omitido, día inexistente), 403 (cuenta suspendida y hábito ajeno), 404, y la prueba con la cadena de seguridad real (sin sesión → 403) en `HabitPreferenceControllerRecordatorioAutenticacionTest`.
- [x] T010 [US3] REST: `AlarmCombinationRequest`, los endpoints y las respuestas (contracts/habit-preferences-alarm.md).
  - Los cuatro campos del cuerpo son obligatorios (`@NotNull Boolean`): omitir uno es 400 y no un `false` silencioso. Las lecturas siempre traen los cuatro campos de `alarm` (lo que nadie eligió es la combinación de hoy).
- [x] T011 [US3] D-283 en `back:docs/MODULOS_A_AVANZAR.md`. Pruebas puntuales en verde. Commit: «Guardar cómo suena la alarma de cada hábito y de cada día (spec 009, D-283)».
  - D-283 anotado después de D-282 (misma tabla). Pruebas puntuales: 53 clases, 539 pruebas, 0 fallos (habits, 008, rag que usa `HorarioSemanal`, `ArchitectureTest`). No se corrió `verify` completo (lo corre la sesión supervisora).

## Phase 4: US1 + US2 — La alarma suena según la combinación del día (P1)

- [x] T012 [P] [US1] Prueba pura `app:combinacionDeAlarma.test.ts`: resolver la combinación del día y elegir el canal (tabla R1, con `-sinvib`, solo vibrar y voz del hábito o genérica).
- [x] T013 [US1] `app:src/features/alarmas/sonidoDeAlarma.ts`: canales `-sinvib` (`enableVibrate: false`), `ARCHIVO_POR_CANAL` y `archivoDelCanal` al día. Prueba de `sonidosElegibles` en verde (ningún archivo nuevo).
- [x] T014 [P] [US1] Prueba de `app:repeticiones.test.ts`:
  - fechas de los próximos 7 días con repetir;
  - ids `habito-{id}-rep-{AAAAMMDD}-{5|10}`;
  - cancelar hoy deja mañana;
  - cruce de medianoche;
  - sin repetir no hay ninguna;
  - cerrar sesión las borra.
- [x] T015 [US1] `app:src/features/habits/notificaciones/repeticiones.ts` y su conexión:
  - programar desde `programar` y desde la puesta al día (ventana de 7 días);
  - cancelar hoy al tocar una alarma del hábito (listener de respuesta) y al recibir `habitoCumplido`.
- [x] T016 [US1] `recordatoriosDeHabito.ts`: cada alarma del día usa el canal de su combinación, en el esquema diario y en el por día de la 008. La 007 se conserva (tipo despertador, rearmado, etiqueta).
- [x] T017 [US1] Pruebas puntuales y `tsc`. Commit: «Hacer sonar, vibrar, hablar y repetir la alarma según la combinación de cada día (spec 009)».

## Phase 5: US1 + US2 — La pantalla «Cómo suena» (P1)

- [x] T018 [P] [US1] Prueba de `app:src/components/__tests__/InterruptorGrande.test.tsx`: rol `switch`, `checked`, estado «Varía», deshabilitado, nombre accesible, y sin animación con «reducir movimiento».
- [x] T019 [US1] `app:src/components/InterruptorGrande.tsx` (research R6). Cargar las skills `emil-design-eng` y `animate-expo` antes de escribirlo.
- [x] T020 [P] [US2] Prueba pura de la sección:
  - estado inicial con varios días (común o «Varía»);
  - reglas de sonido y vibración y de la voz;
  - qué guardar (solo combinación → «Guardar alarma · S»).
- [x] T021 [US1] `app:src/features/training/components/SeccionComoSuena.tsx`: 4 filas con rótulos de 17 px y explicación de 15 px (textos de research R7), filas de 56 px o más, mensajes de regla en línea y la sección atenuada con «Este día no tiene aviso».
- [x] T022 [US2] `PlanificarDimensionModal.tsx`:
  - poner la sección en el espacio que dejó la 008;
  - guardar general o por día con `fijarCombinacion…`;
  - releer y reprogramar después de guardar;
  - marcar la alarma propia en el chip, coherente con la campana de la 008;
  - backend anterior → no se muestra.
- [x] T023 [US1] Nota en `AGENTS.md` (Training → Planificar: «Cómo suena»; repeticiones con ventana de 7 días; sin APK). Pruebas puntuales y `tsc`. Commit: «Elegir cómo suena la alarma de cada día con interruptores grandes (spec 009)».

## Phase 6: Validación conjunta (008 + 009)

- [ ] T024 Con turno avisado: `TZ=UTC ./mvnw clean verify` (backend), y Jest completo y `tsc` (app).
- [ ] T025 Con turno avisado: backend con V111 y V112 en `renaser_alarmas` y Metro del worktree. Correr el quickstart de la 008 (E1–E10, S1–S3) y el de la 009 (T1–T12, B1) con mobile-mcp, con captura por caso en `~/Imágenes/e2e-2026-10-20/`. Cada fallo va a E-641 en adelante, con su commit.
- [ ] T026 Maestro de los flujos afectados. Liberar la máquina y avisar a las otras sesiones.

## Dependencias

T001 → backend (T002–T011) ∥ alarmas de la app (T012–T017) → pantalla (T018–T023) → validación (T024–T026). La pantalla necesita T015 y T016 para reprogramar.
