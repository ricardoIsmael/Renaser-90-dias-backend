# Implementation Plan: Planificación de semana y mes

**Branch**: `028-planificacion-semana-y-mes` (backend y app) | **Date**: 2026-10-31 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/028-planificacion-semana-y-mes/spec.md` (clarificada con las decisiones del dueño del 2026-10-31)

## Summary

Tres piezas, todas encima de lo que ya existe:

1. **«Solo esta semana»** en el editor de Planificar: un cambio vale para los días que faltan de **una** semana y no toca el plan de siempre.
   Se guarda en `horarios_habito_por_fecha` (la tabla que ya guarda «ese día»), ampliada con los mismos campos que el editor guarda por día de la
   semana: **varios avisos** y **«Cómo suena»** (V130 y V131). Cero tablas nuevas.
2. **Repetir la semana** (hábitos): dentro de Planificar («Tus prácticas») sale una oferta «Repetir tu semana del 26 oct al 1 nov en la del 2 al 8 nov».
   El servidor calcula la oferta y la aplica con una función pura, todo o nada e idempotente. «Ahora no» se recuerda en el teléfono. «Deshacer» devuelve la
   semana al plan de siempre.
3. **Planificar el mes** = el plan de **objetivos** de Plan (decisión del dueño): una hoja «Planificar mi mes» con los tres meses del programa, el objetivo del
   mes del eje abierto (editable en cualquier mes, con el servicio que ya existe) y las semanas del mes. **Solo app**, sin backend nuevo.

Además, las **alarmas del teléfono** respetan lo «solo esta semana» (las que se repiten no saltan un día: se reemplazan por alarmas de fecha del día afectado
y se revierten solas), con las reglas de las specs 007 a 010 y del #33.

**Backend:** V130 + V131; dominio puro (`SemanaCalendaria`, `HorarioPorFecha` ampliado, `PlanDeLaSemana`, `RepeticionDeSemana`); puertos y adaptador por capas;
servicios `PlanDeLaSemanaService` y `RepeticionDeSemanaService`; endpoints nuevos bajo `/api/v1/habit-preferences`; `dateOverrides` en `GET /habit-preferences`.

**App (solo JavaScript, APK nuevo porque la app no se actualiza por aire):** editor con alcance, oferta y deshacer, alarmas con fechas propias, hoja del mes en Plan.

Ver [research](./research.md), [data-model](./data-model.md), [contratos](./contracts/).

## Technical Context

**Language/Version**: Backend Java 25, Spring Boot (Modulith, hexagonal). App TypeScript, React Native, Expo SDK 57.
**Primary Dependencies**: Flyway, JPA; expo-notifications 57 (con los parches de la 007); AsyncStorage.
**Storage**: PostgreSQL (`horarios_habito_por_fecha` + V130/V131). AsyncStorage para «Ahora no» y la nota de las alarmas.
**Testing**: JUnit 5 y Testcontainers (módulos tocados + `ArchitectureTest`, **sin `verify` completo**: la máquina es compartida); Jest y `tsc`; flujos de Maestro escritos (no se corren: sin emulador).
**Target Platform**: Android (alarmas locales), web (solo push), servidor Linux.
**Project Type**: Mobile + API.
**Performance Goals**: sin pedidos por hábito: `dateOverrides` sale de UNA consulta por rango para todos los hábitos (como `weekdays`); la oferta se calcula con una lectura por semana.
**Constraints**: con el alcance por defecto todo es igual que hoy (SC-007: las pruebas de las specs 008 a 011 siguen verdes sin tocarse). Compatible con el APK publicado y con un backend anterior. No se tocan Hoy, Comunidad ni Yo.
**Scale/Scope**: hasta ~18 hábitos × 7 fechas × 3 avisos por semana editable; ≤ ~250 alarmas de fecha en el peor caso (ver R5).

## Constitution Check

| Principio | Cómo se cumple | Estado |
|---|---|---|
| I. Dependencias al dominio | Las reglas (semana, resolución por fecha, qué copia una repetición) son dominio puro sin Spring; el controller solo deserializa, autoriza y llama a **un** caso de uso; en la app, la lógica va en `utils/` puros. | ✅ |
| II. No inventar reglas | Las tres preguntas las respondió el dueño. Lo decidido por quien implementa va en research y se avisa (R3 oferta con o sin cambios en la semana siguiente; R5 horizonte de las alarmas; R7 cuota tras el día 90). | ✅ con avisos |
| III. Pruebas en verde | Dominio sin Spring, adaptador con Testcontainers (capas, legados, V130/V131), servicios, controllers con 403 y `SUSPENDED`, reloj en la madrugada UTC; Jest de editor, oferta, alarmas y mes; `tsc`. | ✅ |
| IV. Historia | V130/V131 ampliadas con cabecera, aditivas; no reescriben filas; ninguna fila existente cambia de significado. | ✅ |
| V. Tiempo del participante | «Hoy» y la semana salen del `Clock` en la zona del participante; en la app, del reloj del teléfono solo para las alarmas, como siempre. | ✅ |
| VI. Tabs | Training → Planificar (editor y «Tus prácticas») y Plan cambian **por pedido del dueño** (2026-10-31); va una entrada en `AGENTS.md`. Hoy, Comunidad y Yo no se tocan. | ✅ con nota |
| VII. Documentar | D-296 en `MODULOS_A_AVANZAR.md`, E-nn si aparecen errores, contrato y quickstart en esta carpeta. | ✅ |

**Re-check después del diseño**: sin cambios y sin concesiones. El único punto delicado es la relación con la cuota semanal después del día 90 (R7); se deja a la vista.

## Project Structure

### Documentation (this feature)

```text
specs/028-planificacion-semana-y-mes/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── habit-preferences-week.md      # alcance «solo esta semana» + dateOverrides
│   └── habit-preferences-week-repeat.md   # oferta, repetir, deshacer
├── checklists/requirements.md
└── tasks.md
```

### Source Code

```text
backend (worktree .claude/worktrees/impl-028)
src/main/resources/db/migration/V130__fecha_con_avisos_y_alarma.sql, V131__fecha_con_aviso_o_alarma_propios.sql
src/main/java/com/renaser/os/habits/
├── domain/model/preferencia/
│   ├── SemanaCalendaria.java            # nueva: lunes a domingo, aritmética de semanas
│   ├── HorarioPorFecha.java             # +recordatorio, +alarma (hereda = null)
│   ├── CambioDeFecha.java               # nueva: «solo lo tocado» de UNA fecha
│   ├── PlanDeLaSemana.java              # nueva: base por día de la semana + capa por fecha
│   └── RepeticionDeSemana.java          # nueva: qué se copia, qué se reemplaza, qué se deja afuera
├── application/ports/in/preferencia/{PlanDeLaSemanaUseCase, RepetirSemanaUseCase}.java
├── application/ports/out/preferencia/{Load,Save}PreferenciaHorarioPort.java      # fechas propias en rango; borrado en rango
├── application/services/{PlanDeLaSemanaService, RepeticionDeSemanaService}.java, ConsultaPreferenciasHorarioService.java (dateOverrides)
└── infrastructure/
    ├── adapter/in/rest/preferencia/{HabitPreferenceController (+week), RepetirSemanaController (nuevo), *Request/*Response}.java
    └── adapter/out/persistence/preferencia/{HorarioPorFechaJpaEntity, PreferenciaHorarioPersistenceAdapter, SpringDataHorarioPorFechaRepository}.java
src/test/java/... (dominio, adaptador, servicios, controllers, IT de la repetición)
docs/MODULOS_A_AVANZAR.md (D-296), docs/BITACORA_ERRORES.md (si hay E-nn)

app (worktree .claude/worktrees/impl-028)
src/features/habits/types/habits.types.ts, api/{habitsSchemas,habitsApi}.ts       # dateOverrides, semana, oferta
src/features/habits/notificaciones/utils/{alarmasPorDia,puenteDeAlarmas,repeticionesDeAlarma}.ts  # fechas propias
src/features/habits/notificaciones/recordatoriosDeHabito.ts                         # programar / ajustar con fechas
src/features/training/components/PlanificarDimensionModal.tsx                       # alcance «Solo esta semana»
src/features/training/components/{OfertaDeRepetirSemana,HojaDeRepetirSemana}.tsx    # oferta, resumen, deshacer
src/features/training/components/PracticasDeTraining.tsx                            # monta la oferta
src/features/training/hooks/useOfertaDeRepetirSemana.ts, utils/{alcanceDeLaSemana,ofertaDeRepetir}.ts
src/features/objetivos/components/PlanDelMesModal.tsx, utils/mesDelPrograma.ts      # «Planificar mi mes»
src/screens/PlanScreen.tsx                                                          # botón «Planificar mi mes»
.maestro/planificacion/{repetir-la-semana,solo-esta-semana,planificar-el-mes}.yaml
AGENTS.md                                                                           # entrada fechada 2026-10-31
```

**Structure Decision**: los dos repos, cada uno en su worktree `.claude/worktrees/impl-028` y en la rama `028-planificacion-semana-y-mes`. Un commit por funcionalidad.

## Orden de entrega

1. **Backend (base de todo)**: V130/V131, dominio, adaptador por capas (la fecha propia con aviso y alarma), `dateOverrides`, endpoints de la semana, repetición y deshacer. Pruebas de los módulos tocados + `ArchitectureTest`.
2. **App, datos y contrato**: tipos y esquemas (`dateOverrides`, semana, oferta) en un solo commit, antes de repartir el trabajo.
3. **App, en paralelo** (tres frentes que no se pisan): (a) editor «Solo esta semana» + oferta + deshacer; (b) alarmas con fechas propias; (c) «Planificar mi mes» en Plan.
4. **Validación**: pruebas de los módulos tocados, `tsc`, Jest de lo tocado, `graphify update .`, flujos de Maestro escritos.

## Agentes y coordinación

- Implementa la sesión con tres agentes de la app en paralelo una vez fijado el contrato. Todo comando pesado (Maven, `tsc`, Jest) corre bajo `flock /tmp/renaser-pesado.lock` para no tumbar la máquina compartida; Maven con `-Xmx1500m` y sin `verify` completo; **no se usa el emulador**.
- Un commit por funcionalidad, en español, en imperativo y sin coautoría.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Las alarmas de un día afectado pasan a ser de fecha mientras exista el cambio «solo esta semana» | Una alarma `WEEKLY` no puede saltar una fecha (mismo motivo que el puente del #33) | Dejar la semanal y sumar otra de fecha duplicaría el aviso; cancelar la semanal sin reemplazo dejaría sin alarma a las demás semanas |
| `HorarioPorFecha` suma `recordatorio` y `alarma` opcionales | La fecha propia debe poder decir lo mismo que un día de la semana (spec 008/009) | Una tabla nueva de «plan de semana» abriría una segunda respuesta a «¿cómo va este día?» que V31, V40, V111 y V112 ya rechazaron |
