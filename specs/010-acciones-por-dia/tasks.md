# Tasks: Acciones por día (spec 010)

**Ruta**: `app:` = `Renaser-90-dias-frontend-/.claude/worktrees/acciones-por-dia/` (rama `010-acciones-por-dia`, sale de la 009).

**Reglas**:
- Pruebas primero.
- Un commit por funcionalidad, en español y sin coautoría.
- Sin APK.
- Sin servidor.

- [ ] T001 Crear el worktree de la app desde `009-tipos-de-alarma`, cuando la 009 esté cerrada (voces incluidas).
- [x] T002 [P] [US1] Prueba pura del modelo de preferencias por día:
  - leer el formato viejo y migrarlo a «general»;
  - resolver hora, encendido, antelaciones y combinación de cada día con la misma herencia de la 008 y la 009.
- [x] T003 [US1] `objetivos/notificaciones/preferenciasPorDia.ts`, puro, y el guardado en `recordatoriosDeAcciones.ts`.
- [x] T004 [P] [US1] Prueba del plan de alarmas:
  - el diario como `WEEKLY` por día cuando hay días distintos, y `DAILY` como hoy si no;
  - el aviso de cada acción con las antelaciones de su día;
  - ids estables;
  - cerrar sesión borra todo.
- [x] T005 [US1] `programarRecordatorioDiario` y `planDeAlarmasDeAcciones` con días.
- [x] T006 [P] [US2] Prueba de la combinación en acciones:
  - canal de objetivos `-vib` / `-sinvib`, voz de objetivos y solo vibrar;
  - repeticiones de fecha a +5 y +10, que se cancelan al tocar la alarma o al cumplir la acción;
  - E-641: los objetivos vibran por defecto.
- [x] T007 [US2] Canales de objetivos en `sonidoDeAlarma.ts`, sin tocar los de hábitos; repeticiones de acciones; cancelación al tocar la alarma (ruta del aviso de acción) y al sellar la roca.
- [x] T008 [P] [US1] Prueba de `RecordatorioDeAcciones`:
  - días con candado hoy;
  - «Estos días tienen avisos distintos»;
  - guardar solo lo tocado;
  - «Cómo suena» con «Varía».
- [x] T009 [US1] `RecordatorioDeAcciones.tsx` con `FilaDeDiasDelPlan` (prop opcional), las opciones de aviso de la 008, `SeccionComoSuena` y `InterruptorGrande`. Mismo componente en Plan y en Yo → Alarmas. UX para mayores de 40.
- [x] T010 `AGENTS.md` (tabs Plan y Yo → Alarmas: lo que cambia). Jest de lo tocado y `tsc`. Commits:
  - «Elegir por día el recordatorio y el aviso de las acciones de los objetivos (spec 010)»;
  - «Elegir cómo suenan las alarmas de las acciones, por día (spec 010)».
- [ ] T011 Validación: Jest completo y `tsc`. E2E en el emulador con la base clonada y acciones agendadas a pocos minutos, con días distintos. Que suenen, con su combinación, repetir y el force-stop.
