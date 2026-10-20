# Implementation Plan: Recordatorio por día al planificar un hábito

**Branch**: `008-recordatorio-por-dia` (backend y app) | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/008-recordatorio-por-dia/spec.md`

## Summary

El recordatorio de un hábito pasa a elegirse por día de la semana, igual que la hora. En el mismo cambio se corrige un bug: la alarma del teléfono hoy ignora la hora propia de cada día.

**Backend:**
- V111 suma el aviso (`recordatorio_activo`, `antelaciones_recordatorio`) a `horario_semanal_habito`.
- Endpoint nuevo `PUT …/weekdays/{wd}/reminder`, que no cobra cupo.
- El barrido resuelve el aviso por capas, campo por campo, y el push lleva la etiqueta del día cuando el hábito usa el esquema por día (R6).
- La lista de preferencias suma los 7 días ya resueltos.

**App (solo JavaScript, sin APK):**
- El editor deja elegir el aviso con días marcados.
- Cada chip muestra la campana con los minutos.
- Se guarda solo lo que se tocó.
- Si el hábito tiene días distintos, las alarmas pasan a `WEEKLY` por día; si no, quedan como hoy.

Ver [research](./research.md).

## Technical Context

**Language/Version**: Backend Java 25, Spring Boot (Modulith, hexagonal). App TypeScript, React Native, Expo SDK 57.
**Primary Dependencies**: Flyway, JPA; expo-notifications 57.0.17 (con el parche de la 007 y `buildFromSource`).
**Storage**: PostgreSQL (`horario_semanal_habito` + V111).
**Testing**: JUnit 5 y Testcontainers (`clean verify`); Jest y `tsc`; mobile-mcp y Maestro en el emulador.
**Target Platform**: Android (alarmas locales), web (solo push), servidor Linux.
**Project Type**: Mobile + API.
**Performance Goals**: Poner al día las alarmas sin pedidos extra por hábito (un solo `GET /habit-preferences`).
**Constraints**: Sin APK nuevo. Compatible con el APK publicado y con un backend anterior. No tocar los demás tabs.
**Scale/Scope**: Hasta ~18 hábitos × 7 días × 3 antelaciones por persona: ≤ ~380 alarmas en el peor caso, decenas en la práctica.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I. Dependencias al dominio | El aviso del día va en `HorarioSemanal` y en un `RecordatorioDelDia` de dominio, sin Spring. El controller solo deserializa, autoriza y llama a `EditarHorarioSemanalUseCase.fijarRecordatorio`. En la app, la lógica pura va en `utils/`: qué guardar, el esquema de alarmas y el aviso de los días marcados. | ✅ |
| II. No inventar reglas | Las decisiones del dueño quedan en Clarifications: la campana, los avisos distintos, guardar solo lo tocado y hoy con candado. Que el aviso no cobre cupo lo confirmó el dueño en consola (2026-10-20) y va a D-282. | ✅ |
| III. Pruebas en verde | Unitarias de dominio y del adaptador por capas (fila de solo aviso, día apagado, fecha puntual), endpoint con 403 y `clean verify`. Jest del editor y de las alarmas (esquema, medianoche, identificadores, rearmado) y `tsc`. E2E en el emulador y Maestro de lo afectado. | ✅ |
| IV. Historia | V111 suma columnas nulas, con cabecera. No reescribe filas. El CHECK se relaja para la fila de solo aviso. | ✅ |
| V. Tiempo del participante | El día de la semana sale de la fecha en la zona del participante, como ya hace el adaptador. La alarma local usa la hora del teléfono. | ✅ |
| VI. Tabs | Cambia el editor de Training → Planificar, autorizado («arregla eso para continuar», 2026-10-20). `FilaDeDiasDelPlan` suma una prop opcional y Plan no cambia. Va una nota en `AGENTS.md`. | ✅ con nota |
| VII. Documentar | D-282 (aviso por día, sin cupo) y E-640 (la alarma diaria ignora la hora del día) en el mismo cambio, con contrato y quickstart. | ✅ |

**Re-check después del diseño**: sin cambios y sin concesiones. La programación semanal (horas por día y días apagados) se conserva entera, y las dos protecciones de la 007 contra el aviso doble (confirmación y etiqueta) siguen en los dos esquemas (R6).

## Project Structure

### Documentation (this feature)

```text
specs/008-recordatorio-por-dia/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/habit-preferences-weekdays.md
├── checklists/requirements.md
└── tasks.md            # /speckit-tasks
```

### Source Code

```text
backend (worktree .claude/worktrees/recordatorio-por-dia)
src/main/resources/db/migration/V111__recordatorio_por_dia_de_la_semana.sql
src/main/java/com/renaser/os/habits/
├── domain/model/preferencia/HorarioSemanal.java            # activo ⇒ hora o aviso propio
├── domain/model/preferencia/RecordatorioDelDia.java        # nuevo
├── application/ports/in/preferencia/EditarHorarioSemanalUseCase.java   # fijarRecordatorio; DiaDeLaSemana + aviso
├── application/ports/out/preferencia/{Load,Save}PreferenciaHorarioPort.java
├── application/services/ (el que implementa EditarHorarioSemanalUseCase y la consulta de preferencias)
└── infrastructure/
    ├── adapter/in/rest/preferencia/{HabitPreferenceController, WeekdayReminderRequest (nuevo),
    │   WeekdayScheduleResponse, HabitPreferencesResponse}.java
    └── adapter/out/persistence/preferencia/{HorarioSemanalJpaEntity, PreferenciaHorarioPersistenceAdapter}.java
src/test/java/...  (dominio, adaptador, controller con 403, servicio)
docs/MODULOS_A_AVANZAR.md (D-282), docs/BITACORA_ERRORES.md (E-640)

app (worktree .claude/worktrees/recordatorio-por-dia)
src/features/habits/api/habitsApi.ts                         # fijarAvisoDelDia, tipos de weekdays
src/features/habits/components/FilaDeDiasDelPlan.tsx         # prop opcional: campana con minutos
src/features/habits/notificaciones/recordatoriosDeHabito.ts  # esquema diario o por día, WEEKLY, ids
src/features/habits/notificaciones/utils/alarmasPorDia.ts    # nuevo, puro
src/features/training/components/PlanificarDimensionModal.tsx  # aviso con días marcados, qué guardar
src/features/training/utils/avisoDeLosDias.ts                # nuevo, puro (aviso común o distintos, qué guardar)
src/features/alarmas/avisosJuntos.ts                         # agrupar WEEKLY del mismo día
src/components/Icon.tsx                                      # bell / bellOff si faltan
AGENTS.md                                                    # nota del cambio en Training
```

**Structure Decision**: los dos repos, cada uno en su worktree y en la rama `008-recordatorio-por-dia`. Un commit por funcionalidad.

## Orden de entrega

1. **Backend (US3, base de US1)**: V111, dominio, adaptador por capas, endpoint del aviso y los días en la lista. `clean verify` en verde.
2. **App, alarmas por día (US2)**: corrige el bug de la hora general con lo que ya da la lista de preferencias, más el aviso de cada día cuando el backend lo informe.
3. **App, editor (US1)**: aviso con días marcados, campana en el chip y guardar solo lo tocado.
4. **Validación**: `clean verify`, Jest y `tsc`, quickstart en el emulador **sin compilar APK** y Maestro de los flujos afectados.

## Agentes y coordinación

- Implementan agentes Sonnet 5.5 con esfuerzo xhigh, backend y app en paralelo, con el contrato y el data-model ya fijos. La sesión general supervisa.
- Se avisa el turno de máquina antes de `clean verify`, Jest completo, emulador y Maestro. Nunca verify y Maestro a la vez.
- Un commit por funcionalidad, en español y sin coautoría.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Dos esquemas de alarma (diario y por día), con la misma regla en la app y el servidor | Quien no usa días distintos no ve ningún cambio, ni en las alarmas ni en los identificadores. Quien sí los usa pasa a sonar a la hora correcta. Las etiquetas coinciden en los dos esquemas | Usar siempre `WEEKLY` reprogramaría las alarmas de todos al actualizar y obligaría a cambiar la etiqueta del push para todos |
