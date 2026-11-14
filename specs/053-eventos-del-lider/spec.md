# Spec 053 — Eventos del líder: «Sesión personalizada», semáforo de ayer y «Repetir por días»

**Input**: pedido del dueño (2026-11-14), tres cambios a los eventos. Backend y app (rama `eventos-lider-personalizada`).
Se apoya en la spec 044 (eventos por audiencia, FR-007 y FR-009 a FR-012) y en la 040 (Meet por ocurrencia).

## Decisiones del dueño (2026-11-14)

| # | Decisión | Efecto |
|---|---|---|
| P1 | Al crear una sesión, el **líder de mentores** hoy ve dos tipos. Debe ver **solo uno: «Sesión personalizada»**, ya elegido (sin tocar). | FR-001 a FR-003 (D-355) |
| P2 | «Por semáforo» usa el semáforo de **ayer** (el día ya cerrado); si ayer no tuvo datos, el último día con datos y la pantalla dice cuál. Se resuelve una vez al crear. | FR-004 a FR-006 (D-356) |
| P3 | «Programar por días», simple, para todos los que crean, con resumen en palabras; cancelar una serie pregunta «solo esta» o «esta y las siguientes». Título automático y avisos propios de la personalizada. | FR-007 a FR-015 (D-357, D-358) |

## Lo que había (leído en el código el 2026-11-14)

- `QuienConvoca.tiposDe`: líder, mentor y guía recibían `SESION_ESPECIAL` y `ESPONTANEO` (spec 044, P9). Administración y Alquimista, los cuatro.
- El color del semáforo de una audiencia salía de `SemaforoFinder.vigenteDe` (promedio de los siete días cerrados que terminan ayer; hoy nunca entraba). Ahora se usa
  el color del último día cerrado con datos de esa misma ventana (FR-004) y la pantalla lo dice (antes rotulaba la cifra «hoy»).
- El servidor ya acepta repetición (`recurrenceFrequency`, `recurrenceInterval`, `recurrenceByWeekday` ISO 1=lunes…7=domingo, `recurrenceUntil` o
  `recurrenceCount`): una fila de regla y las ocurrencias se expanden al leer (`ExpansorOcurrencias`); Meet crea una sala por ocurrencia (spec 040). La
  app no lo ofrecía: un evento repetido se podía leer, no crear.

## Requisitos

### Tipo del líder

- **FR-001**: El servidor ofrece al líder de mentores un solo tipo, `SESION_PERSONALIZADA` («Sesión personalizada»), en `eventTypes` de `GET /calendar/audience/options`,
  y solo ese tipo se acepta al crear (otro: 403 «No puedes crear un evento de ese tipo»).
- **FR-002**: `SESION_PERSONALIZADA` es un tipo nuevo en dominio, base (V193) y wire, sin elegibilidad extra (sus avisos: FR-015). Los eventos ya guardados no cambian de tipo y se siguen viendo.
- **FR-003**: Mentor y guía conservan «Sesión especial» y «Espontáneo»; Administración y Alquimista, sus cuatro tipos de siempre (no se les ofrece la nueva). En la
  app, si el servidor ofrece un solo tipo, el formulario lo deja elegido de entrada y lo muestra como un dato fijo («Tipo: Sesión personalizada»), no como una
  lista para elegir. Un tipo que la app no conoce se rotula «EVENTO».

### Semáforo de ayer

- **FR-004** (D4, D5 del dueño): El color de una persona para la audiencia es el de su **último día cerrado con datos** en su zona: ayer o, si ayer no tuvo nada medido
  (domingo, feriado), el último día anterior que sí (dentro de la ventana vigente de siete días cerrados que termina ayer). Nunca el día en curso ni el
  promedio de la semana; «Sin datos», «En pausa» y sin programa no entran por ningún color (spec 044, P8).
- **FR-005**: La misma lectura sirve a la vista previa («Llega a N»), a las cuentas por color de las opciones, al guardar y a «Volver a calcular». Se resuelve **una vez al
  crear** (la foto de spec 044, FR-010): los invitados quedan fijos para toda la serie y no se recalculan por ocurrencia; los avisos usan esa lista.
- **FR-006**: `GET /calendar/audience/options` trae `trafficLightDate` (el día más reciente del que salen los colores). La app dice «Según el semáforo de ayer.» si es
  ayer y, si no, «Según el semáforo del viernes 13.» (el día de la semana y la fecha).

### Repetir por días (app; el servidor ya acepta la repetición)

- **FR-007** (D6, D7): Al crear un evento, **cualquier cuenta que crea** (líder, mentor, guía, Administración, Alquimista) ve el mismo bloque «Repetir», con un interruptor
  apagado. Al prenderlo: botones L M M J V S D con el día de la fecha elegida ya marcado, y el fin: «Semanas» (4 sugeridas, de 1 a 12) o «Hasta» una fecha.
- **FR-008**: Una línea lo dice en palabras: «Se repite lunes y jueves, 4 semanas, hasta el 13 de diciembre.» / «Se repite lunes y jueves, hasta el 12 de diciembre.»
  Sin ningún día marcado no se guarda («Elige al menos un día para repetir.»).
- **FR-009**: Cuerpo: `recurrenceFrequency: 'WEEKLY'`, `recurrenceInterval: 1`, `recurrenceByWeekday` (ISO) y `recurrenceUntil` (fin de ese día en la zona del evento). Si la
  fecha elegida no cae en un día marcado, el evento empieza el primer día marcado desde esa fecha (y la línea lo dice).
- **FR-010**: «Hasta» no puede ser anterior a la fecha de inicio. Tope de semanas: 12.
- **FR-011**: El formulario de edición no muestra «Repetir» (un evento repetido sigue sin editarse en la app).
- **FR-012**: La audiencia de la serie es una sola (FR-005).

### Título automático, avisos y cancelar la serie

- **FR-013** (D2): El líder no escribe el título de «Sesión personalizada»: la app lo arma. Una persona: «Sesión personalizada · <Nombre>»; un grupo: «… · <Grupo>»; varias
  personas: «… · 5 personas»; un color: «… · Semáforo verde» (amarillo, rojo); mezclas: «… · 12 personas» (el total de «Llega a N»). Si no hay total todavía, «Sesión personalizada».
  El servidor admite hasta 60 letras para este tipo (los demás, 30) y sigue exigiendo un título no vacío.
- **FR-014** (D8): Cancelar un evento repetido pregunta «¿Solo esta sesión o esta y las siguientes?» (además de «todas»). `POST /calendar/events/{id}/cancel-from`
  `{occurrenceStart}` termina la serie un instante antes de esa fecha (las pasadas no se tocan, los avisos pendientes se regeneran); si es la primera fecha, se cancela el evento
  entero. «Solo esta sesión» usa `cancel-occurrence` (`excepciones_evento`) como hasta hoy.
- **FR-015** (D3): Sin avisos propios, «Sesión personalizada» avisa **1 día, 1 hora, 30 y 10 minutos antes** (app y WhatsApp, por los mismos canales de la spec 041). *(Nota: los avisos por
  defecto de «Sesión especial» son otros: 6:00 del día y 10 minutos antes; el dueño pidió estos cuatro para la personalizada.)*

## Fuera de alcance

Editar una serie; repetir cada N semanas, por mes o por día de mes; excepciones por fecha desde la app; cambiar los tabs.
