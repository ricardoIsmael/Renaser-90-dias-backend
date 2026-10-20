# Tasks: Recordatorio por día al planificar un hábito

**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/habit-preferences-weekdays.md](./contracts/habit-preferences-weekdays.md), [quickstart.md](./quickstart.md)

**Rutas**: `back:` = `Renaser-90-dias-backend/.claude/worktrees/recordatorio-por-dia/` · `app:` = `Renaser-90-dias-frontend-/.claude/worktrees/recordatorio-por-dia/`. Rama `008-recordatorio-por-dia` en los dos.

**Tests**: obligatorios por la constitución (III): primero la prueba, en rojo, y después el código.

**Reglas**:
- Un commit por funcionalidad, en español, en imperativo y sin coautoría.
- **Sin APK**: la app solo cambia JavaScript. No tocar `package.json`, `app.json` ni `modules/`.
- Antes de `clean verify`, Jest completo, emulador o Maestro, avisar el turno a las otras sesiones.
- No tocar `alarmas/api/preferenciasDeNotificacion.ts`, `InterruptoresDeAvisos.tsx` ni `TipoNotificacion`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [x] T001 Confirmar que la próxima migración libre en `back:src/main/resources/db/migration/` es V111 y que D-282 y E-640 siguen libres en `back:docs/MODULOS_A_AVANZAR.md` y `back:docs/BITACORA_ERRORES.md` (grep en todas las ramas de origin). Si no, tomar los siguientes y anotarlo en este archivo.
  - Nota (2026-10-20, agente backend): verificado en todas las ramas locales y de `origin`: la última migración es V110, la última decisión D-281 y el último error E-639. V111, D-282 y E-640 siguen libres; no hubo que renumerar.

---

## Phase 2: Foundational (backend: el aviso del día existe y se resuelve)

**Purpose**: la columna, el dominio y la resolución por capas. Bloquean a US1 y a US3.

- [x] T002 Migración `back:src/main/resources/db/migration/V111__recordatorio_por_dia_de_la_semana.sql`, con cabecera al estilo de V39 (qué resuelve, por qué columnas y no tabla, compatibilidad):
  - `ALTER TABLE horario_semanal_habito ADD COLUMN recordatorio_activo boolean NULL, ADD COLUMN antelaciones_recordatorio smallint[] NULL`.
  - Reemplazar el CHECK de V40 por `NOT activo OR hora_disparo IS NOT NULL OR recordatorio_activo IS NOT NULL`.
  - Sumar `recordatorio_activo IS DISTINCT FROM true OR cardinality(antelaciones_recordatorio) >= 1`.
  - Leer V40 para el nombre exacto del CHECK actual.
- [x] T003 [P] Prueba de dominio `back:src/test/java/com/renaser/os/habits/domain/model/preferencia/RecordatorioDelDiaTest.java` y `HorarioSemanalTest.java`:
  - `RecordatorioDelDia(activo, antelaciones)`: activo con 1 o más antelaciones en el rango de `AntelacionesDelRecordatorio`; inactivo ignora la lista; normaliza el orden de la más temprana a la más tardía.
  - `HorarioSemanal` encendido acepta hora propia **o** aviso propio, y rechaza «encendido sin nada propio».
- [x] T004 Dominio, hasta poner T003 en verde:
  - Nuevo `back:src/main/java/com/renaser/os/habits/domain/model/preferencia/RecordatorioDelDia.java`.
  - `HorarioSemanal.java`: aviso propio opcional, por ejemplo `Optional<RecordatorioDelDia>` o un campo nullable documentado. «Hereda» ≠ «sin aviso».
  - Validación «activo ⇒ hora o aviso».
  - Sin Spring ni JPA.
- [x] T005 [P] Prueba del adaptador `back:src/test/java/.../persistence/preferencia/PreferenciaHorarioPersistenceAdapterRecordatorioTest.java` (Testcontainers, como las pruebas de V39 y V40). Para `porParticipanteHabitosYFecha`:
  - (a) fila con hora y sin aviso: hora propia y aviso general, como hoy;
  - (b) fila con hora y aviso: los dos propios;
  - (c) fila de solo aviso (hora `NULL`, activa): hora general y aviso propio;
  - (d) fila apagada: igual que hoy;
  - (e) fecha puntual por encima de todo;
  - (f) `recordatorio_activo=false`: el día sin aviso.
- [x] T006 Persistencia:
  - `HorarioSemanalJpaEntity.java` (+2 columnas, `smallint[]` como en V81).
  - El mapper.
  - `PreferenciaHorarioPersistenceAdapter.java`, por campo (research R3): la fila de solo aviso ya no se salta y aplica `recordatorioActivo`, las antelaciones y `minutosRecordatorio` = la más temprana (D-217).
  - Guardar la hora (`fijar`) hace upsert **conservando** las columnas del aviso.
  - Nuevo método de guardado del aviso, que conserva la hora: si el aviso es igual al general, lo pone en `NULL`; si la fila queda encendida sin nada propio, la borra.
  - Puertos `SavePreferenciaHorarioPort` y `LoadPreferenciaHorarioPort`.
  - T005 en verde.
  - Nota (2026-10-20, agente backend): el guardado de la hora (`saveParaDiaSemana`) **no toca** el aviso del día cuando el agregado no trae `recordatorio` (FR-013, lo hace el adaptador y así cubre a todo llamador: Mis hábitos, el acompañante y la API). El aviso se escribe solo por `fijarRecordatorioParaDiaSemana`, que borra la fila si queda encendida sin hora ni aviso. El adaptador recibe el `Clock` para las marcas de tiempo de la fila de solo aviso.
  - Nota: se agregaron al puerto de lectura tres métodos que el plan no nombraba pero que T012, T013 y T015c necesitan: `horariosSemanalesDe` (filas de varios hábitos en una consulta), `habitosConEsquemaPorDia` (R6) y `porParticipanteHabitosYFechaSinAvisoDelDia`. Esta última existe para que el aviso **general** del listado (`GET /habit-preferences`, que el APK publicado reescribe como general por el PATCH y que el editor nuevo necesita para saber qué heredan los días) no cambie según el día que se consulta: con el aviso del día mezclado, el general de un sábado con aviso propio saldría como el del sábado. Sin avisos por día es idéntica a `porParticipanteHabitosYFecha`.
- [x] T007 Commit backend: «Guardar el aviso propio de cada día de la semana y resolverlo por capas (spec 008)».

---

## Phase 3: User Story 3 - El aviso del servidor usa el recordatorio del día (Priority: P2, va antes porque la app lo necesita)

**Goal**: el endpoint del aviso, los días en la lista y el barrido con el aviso del día.
**Independent Test**: con la base clonada, `PUT …/SATURDAY/reminder {true,[30]}`: el `GET …/weekdays` lo devuelve con `customReminder: true`, y el barrido de un sábado genera el aviso de inicio 30 min antes (quickstart S1 y S2).

- [x] T008 [P] [US3] Prueba del servicio `back:src/test/java/.../application/services/HorarioSemanalRecordatorioServiceTest.java`:
  - `fijarRecordatorio` no consume cupo: con el cupo semanal lleno, igual guarda.
  - Hábito ajeno: excepción de permiso. Hábito inexistente: 404.
  - Aviso igual al general: hereda.
  - `consultar` devuelve los 7 días con `recordatorioActivo`, `antelaciones` y `recordatorioPropio` resueltos.
- [x] T009 [US3] Caso de uso:
  - `EditarHorarioSemanalUseCase.java`: `fijarRecordatorio(UserId, HabitoId, DayOfWeek, boolean activo, List<Integer> antelaciones)`; `DiaDeLaSemana` suma `recordatorioActivo`, `antelaciones` y `recordatorioPropio`.
  - Su implementación en `back:src/main/java/com/renaser/os/habits/application/services/` (buscar `implements EditarHorarioSemanalUseCase`).
  - Javadoc: no cobra cupo, por decisión del dueño del 2026-10-20 (D-282).
  - T008 en verde.
- [x] T010 [P] [US3] Prueba del controller `back:src/test/java/.../rest/preferencia/HabitPreferenceControllerRecordatorioTest.java`:
  - `PUT /api/v1/habit-preferences/{id}/weekdays/{wd}/reminder`: 204; 400 fuera de rango; **403 sin `USE_APP`** (prueba negativa obligatoria).
  - `GET …/weekdays` con los campos nuevos.
  - `PUT …/weekdays/{wd}` (hora) no borra el aviso (FR-013).
- [x] T011 [US3] REST:
  - Nuevo `WeekdayReminderRequest.java` con `@NotNull Boolean reminderEnabled` y `List<Integer> reminderMinutesList`.
  - Endpoint en `HabitPreferenceController.java` con `@RequiresPermission(Permission.USE_APP)`.
  - `WeekdayScheduleResponse.java` suma `reminderEnabled`, `reminderMinutesList` y `customReminder`.
  - Todo según `contracts/habit-preferences-weekdays.md`. T010 en verde.
- [x] T012 [P] [US3] Prueba de la lista: `GET /api/v1/habit-preferences` trae por hábito `weekdays` (7 entradas resueltas: `weekday`, `triggerTime`, `active`, `reminderEnabled`, `reminderMinutesList`, `customTime`, `customReminder`), armadas con **una** consulta por participante.
- [x] T013 [US3] Implementar `weekdays` en `HabitPreferencesResponse.java` y en el caso de uso de consulta de preferencias, con una consulta por participante a `horario_semanal_habito`. T012 en verde.
  - Nota (2026-10-20, agente backend): `weekdays` sale de `ConsultaPreferenciasHorarioService` con tres lecturas por participante (`porParticipanteYHabitos`, `horariosSemanalesDe` y el cambio pendiente que ya se leía), nunca una por hábito. La resolución vive en el dominio (`SemanaDelHabito.resolver`) y la comparten este listado y `GET …/weekdays`. `customTime` y `custom` significan lo mismo: el día tiene hora propia **o está apagado** (V40 ya lo contaba como propio); una fila de solo aviso no cuenta como hora propia.
  - Nota (desvío menor, conservador): los campos `reminder…` de cada ítem del listado son el aviso **general** (llama a `porParticipanteHabitosYFechaSinAvisoDelDia`), no el del día consultado. Con el aviso del día mezclado, el APK anterior habría reescrito el general con el del sábado (y `AjusteDeHorarioHabitoService` igual), y el editor nuevo no tendría de dónde leer lo que heredan los días sin aviso propio. Sin avisos por día el resultado es idéntico al de antes. Se actualizaron los stubs de `ConsultaPreferenciasHorarioServiceTest` al método nuevo.
- [x] T014 [P] [US3] Prueba del barrido en `back:src/test/java/.../application/services/AvisoHabitoServiceTest.java`: un sábado con aviso propio de 30 min genera INICIO a 30 min; con «sin aviso» propio, no genera INICIO ese día, pero sí el resto de la semana. Usar `FixedClock` en madrugada UTC.
  - Nota (2026-10-20, agente backend): el plan decía que un día con «sin aviso» propio «no genera INICIO». El comportamiento vigente (D-184, `EntregaDelAvisoDeHabito`) es que un hábito con el recordatorio apagado **sí publica el evento** (queda la fila en la bandeja) y `notifications` no empuja. Para no cambiar D-184 en silencio, el día con «sin aviso» propio sale con `recordatorioActivo = false` y **no hay push ni alarma** (lo que pide US1-6); la prueba fija eso. Si el dueño quiere que tampoco quede la fila de la bandeja, es una decisión aparte para el aviso general también.
- [x] T015 [US3] Ajustar `AvisoHabitoService` solo si T014 falla. Por R3 debería pasar sin cambios, porque usa lo resuelto.
  - Nota: por R3 el barrido no necesitó cambios para la antelación del día: `calculadoraPara` ya usa la preferencia resuelta por el adaptador.
- [x] T015b [P] [US3] Prueba de la etiqueta del push (research R6):
  - Sin filas semanales: `tag = habito-{id}-inicio`, como hoy (no romper `AvisoDeHabitoTest`, `ExpoPushTransporteTest` ni `WebPushAdapterTest`).
  - Con alguna fila semanal: `tag = habito-{id}-d{iso}-0`, con `iso` = día de la semana de la fecha del registro en la zona de la persona.
  - Probar con reloj en madrugada UTC: un sábado de Lima a las 21:00 es domingo en UTC y tiene que dar `d6`.
  - El aviso «por vencer» sigue con su etiqueta de hoy.
- [x] T015c [US3] Implementar:
  - `AvisoHabitoService` marca en `AvisoHabitoDebidoEvent` un campo nuevo al final, `Integer diaDeLaSemanaDelAviso` (ISO 1–7). Va solo si el hábito tiene alguna fila en `horario_semanal_habito`; si no, `null`. Usar una sola consulta por barrido y participante.
  - `notifications/domain/model/habito/AvisoDeHabito.etiqueta()` usa `habito-{id}-d{iso}-0` cuando el campo viene.
  - T015b en verde.
- [x] T016 [US3] Docs backend: D-282 en `back:docs/MODULOS_A_AVANZAR.md`, en la tabla de decisiones (aviso por día de la semana, sin cupo, endpoint nuevo, compatibilidad). `TZ=UTC ./mvnw clean verify` en verde, con turno avisado. Commit: «Elegir el aviso de cada día de la semana sin gastar cupo y usarlo en el aviso de inicio (spec 008, D-282)».

---

## Phase 4: User Story 2 - La alarma del teléfono por día (Priority: P1)

**Goal**: la alarma suena a la hora y con el aviso de cada día. Corrige el bug de la hora general (E-640).
**Independent Test**: un hábito con el jueves a las 09:00 «a la hora» y el resto a las 07:00 «10 min antes». `dumpsys alarm` muestra el jueves a las 09:00 y los demás días a las 06:50, todas tipo despertador (quickstart E4, E5, E6).

  - Nota: D-282 queda en `MODULOS_A_AVANZAR.md` (tabla de decisiones, después de D-280). El `clean verify` completo no lo corrió el agente backend: lo corre la sesión supervisora al final. Pruebas corridas por clase: las de este trabajo y las de los paquetes afectados (1085 pruebas de surefire, 0 fallos).
- [x] T017 [P] [US2] Tipos y API en `app:src/features/habits/api/habitsApi.ts`:
  - Tipos de `weekdays` en la preferencia (opcionales: un backend anterior no los manda).
  - Nueva `fijarAvisoDelDia(habitId, weekday, activo, antelaciones)` → `PUT …/weekdays/{wd}/reminder`.
  - `obtenerHorarioSemanal` lee los campos nuevos, todos opcionales.
- [x] T018 [P] [US2] Prueba pura `app:src/features/habits/notificaciones/__tests__/alarmasPorDia.test.ts` de `planDeAlarmas(habito, diasResueltos, general)`:
  - Sin días distintos: `{esquema:'diario', ...}`, igual que hoy.
  - Con días distintos: un pedido `WEEKLY` por día encendido × antelación, con hora = hora del día − antelación.
  - Cruce de medianoche: un aviso de 30 min de las 00:10 del jueves va al miércoles a las 23:40.
  - Días apagados y «sin aviso»: sin pedidos.
  - Identificadores `habito-{id}-d{iso}-{n}` estables entre corridas; el más temprano de cada día es `-0`, que es la etiqueta que manda el servidor (R6).
  - La regla de esquema es la misma que la del servidor: el esquema por día va si algún día de `weekdays` tiene `customTime`, `customReminder` o `active = false`.
  - Sin `weekdays` (backend anterior): diario.
- [x] T019 [US2] Implementar `app:src/features/habits/notificaciones/utils/alarmasPorDia.ts`, puro. T018 en verde.
- [x] T020 [P] [US2] Prueba de `programar`, `cancelar` y `ajustarRecordatoriosAlServidor` en `app:src/features/habits/notificaciones/__tests__/recordatoriosPorDia.test.ts`, con expo-notifications falso:
  - Programa `WEEKLY` con el canal del hábito.
  - Al cambiar un día, cancela los del hábito y no deja duplicados.
  - `recordatoriosPorHabito` guarda los ids nuevos.
  - El esquema diario sigue poniendo `habito-{id}-inicio`.
  - `cancelar` y cerrar sesión borran también los `WEEKLY`.
- [x] T021 [US2] `app:src/features/habits/notificaciones/recordatoriosDeHabito.ts`:
  - `programar` y `ajustarRecordatoriosAlServidor` usan `planDeAlarmas` con los `weekdays` de la lista de preferencias (research R4, R5).
  - Se conservan sonido y canal por hábito (voz Dora), la ruta `/habitos/{id}` y `cambioDiferido`.
  - T020 en verde.
- [x] T022 [P] [US2] Prueba de rearmado y agrupado:
  - `app:src/features/alarmas/__tests__/rearmarAlarmas.test.ts` ya rearma `weekly` (agregar un caso con id por día).
  - `app:src/features/alarmas/__tests__/avisosJuntos.test.ts`: dos `WEEKLY` del mismo día y la misma hora se agrupan; si no es seguro, quedan separados sin perder ninguno.
- [x] T023 [US2] Ajustar `app:src/features/alarmas/avisosJuntos.ts` y, solo si hace falta, `rearmarAlarmas.ts`. T022 en verde.
- [x] T024 [US2] Docs: E-640 en `back:docs/BITACORA_ERRORES.md` («la alarma del teléfono ignoraba la hora propia de cada día»: síntoma, causa `DAILY` a la hora general, corrección, prevención). Jest de `src/features/habits` y `src/features/alarmas`, más `tsc` en verde. Commit en la app: «Programar la alarma del teléfono con la hora y el aviso de cada día (spec 008, E-640)»; en el backend, commit solo de la bitácora.

---

## Phase 5: User Story 1 - Elegir el recordatorio de cada día en el editor (Priority: P1)

**Goal**: con días marcados se elige el aviso; los chips muestran la campana; se guarda solo lo tocado.
**Independent Test**: quickstart E1, E2, E3, E7 y E8 en el emulador.

  - Nota (2026-10-20, agente backend): la parte backend, E-640 en `docs/BITACORA_ERRORES.md`, está escrita (commit aparte). Quedan Jest, `tsc` y el commit de la app, que son del agente de la app. Si el agente de la app cambia algo de lo que E-640 dice de la app, se corrige ese texto en el mismo cambio.
- [x] T025 [P] [US1] Prueba pura `app:src/features/training/utils/__tests__/avisoDeLosDias.test.ts`:
  - `avisoInicialDeLosMarcados(dias)`: el común o `'distintos'`.
  - `queGuardar({movioLaRueda, cambioElAviso})`: `'hora' | 'aviso' | 'ambos'`.
  - `textoDelBoton`: «Guardar aviso · S, D», «Guardar 07:00 · S, D», o «Guardar 07:00 · todos los días» sin días marcados.
  - `resumenDelAvisoDelDia(dia, general)`: `null` si es igual al general; `'30′'`, `'a la hora'` o `'sin aviso'`.
- [x] T026 [US1] Implementar `app:src/features/training/utils/avisoDeLosDias.ts`, puro, en español de Perú con tuteo. T025 en verde.
- [x] T027 [P] [US1] Iconos: confirmar si existen `bell` y `bellOff` en `app:src/components/Icon.tsx`. Si no, dibujarlos con la forma de Lucide en caja de 24 y `s24`, en un bloque `/* Planificar */`. Correr `grosorDeIconos.test.ts`.
- [x] T028 [US1] `app:src/features/habits/components/FilaDeDiasDelPlan.tsx`:
  - Prop **opcional** `avisoDelDia?: (dia) => string | null`.
  - Si devuelve texto, debajo de la hora va `Icon` campana de 16 con el texto, o la campana tachada si es «sin aviso». Tipografía Jost 12–13 px y color del tema.
  - Sin la prop, la fila queda idéntica: Plan no la pasa.
  - Prueba de render sin la prop, igual a hoy, y con la prop.
- [x] T029 [US1] `app:src/features/training/components/PlanificarDimensionModal.tsx`:
  - Con días marcados, mostrar la sección Recordatorio y quitar la línea «El recordatorio se elige sin días marcados» (E-408).
  - Estado inicial con `avisoInicialDeLosMarcados`; con «distintos», ningún chip marcado y la línea «Estos días tienen avisos distintos».
  - Registrar si se movió la rueda y si se tocó el aviso.
  - Guardar según `queGuardar`: `fijarHorarioDelDia` y/o `fijarAvisoDelDia` por día marcado. Hoy y los días pasados siguen con candado.
  - Tras guardar, releer la semana y reprogramar las alarmas del hábito (`programar`).
  - Pasar `avisoDelDia` a `FilaDeDiasDelPlan`.
  - Sin días marcados, el recordatorio general funciona como hoy.
  - Backend anterior (sin `customReminder` en la respuesta): no se ofrece el aviso con días marcados y se mantiene la línea E-408.
  - `Presionable` y `tacto.seleccion()` como en el resto del editor; áreas de toque de 44 px o más; sin animaciones nuevas.
  - Dejar un contenedor vacío, sin nada visible, debajo del recordatorio para los futuros tipos de alarma (FR-015).
- [x] T030 [US1] Prueba de integración del editor `app:src/features/training/__tests__/planificarAvisoPorDia.test.tsx`, con la API falsa:
  - Marcar S y D, elegir 30 min sin mover la rueda: solo se llama a `fijarAvisoDelDia` dos veces.
  - Mover la rueda y cambiar el aviso: hora y aviso.
  - Avisos distintos: la línea correspondiente.
  - Backend anterior: comportamiento de hoy.
- [x] T031 [US1] `app:AGENTS.md`: entrada «Cambio por pedido del dueño — 2026-10-20 — tab `Training` → Planificar: el recordatorio por día» (lo que cambia y nada más, sin APK nuevo, backend con V111 antes). Jest de `src/features/training` y `src/features/habits`, más `tsc` en verde. Commit: «Elegir el recordatorio de cada día al planificar un hábito (spec 008, D-282)».

---

## Phase 6: Polish & validación

- [ ] T032 Con turno avisado:
  - `TZ=UTC ./mvnw clean verify` en `back:`, leyendo los `Tests run:` de surefire y failsafe.
  - Jest completo y `tsc` en `app:`, con `--testPathIgnorePatterns '/node_modules/' '/e2e/'`.
  - Liberar el turno.
- [ ] T033 Con turno avisado:
  - Backend `back:` en 8097 con `renaser_alarmas` (V111 aplicada) y Metro del worktree `app:` en 8081. **Build debug ya instalado, sin compilar APK.**
  - Correr el quickstart E1–E10 y S1–S3 con mobile-mcp, con una captura por caso en `~/Imágenes/e2e-2026-10-20/008-*`.
  - Lo que falle va a E-641 en adelante, con un commit por arreglo.
- [ ] T034 Maestro de los flujos afectados (`.maestro/regresion/08-planificar-despertar.yaml`, `07-yo-y-ajustes.yaml`). Si `08` cambió por la UI nueva, actualizarlo en el mismo trabajo. Nunca a la vez que el verify.
- [ ] T035 Liberar la máquina y avisar a las sesiones. Commits por funcionalidad listos y PR solo cuando el dueño lo pida.

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational (T002–T007)** → **US3 (T008–T016)**: el backend completo.
- **US2 (T017–T024)**: T017–T020 pueden empezar en paralelo con el backend, contra el contrato. La prueba real necesita US3.
- **US1 (T025–T031)**: depende de T017 (API) y de T021 (reprogramar tras guardar).
- **Polish (T032–T035)**: al final.

### Parallel Opportunities

- Backend: T003 ∥ T005 (pruebas); T008 ∥ T010 ∥ T012 ∥ T014.
- App: T017 ∥ T018 ∥ T020 ∥ T025 ∥ T027.
- Un agente para el backend (T002–T016) y otro para la app (T017–T031) en paralelo, con el contrato fijo.

### Parallel Example

```text
Agente backend: T002 → T003+T005 → T004 → T006 → T008+T010+T012+T014 → T009/T011/T013/T015 → T016
Agente app:     T017+T018+T020+T025+T027 → T019 → T021 → T022 → T023 → T026 → T028 → T029 → T030 → T031
```

## Implementation Strategy

- **MVP**: Foundational + US3 + US2. Con esto el teléfono ya suena a la hora de cada día (el bug) y el servidor ya entiende el aviso por día.
- **Después**: US1, el editor, que es lo que pidió el dueño en pantalla.
- Validación integral (T032–T034) antes de proponer el PR.
