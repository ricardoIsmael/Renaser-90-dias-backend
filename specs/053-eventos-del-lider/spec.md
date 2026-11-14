# Spec 053 — Eventos del líder: «Sesión personalizada», semáforo de ayer y «Repetir por días»

**Input**: pedido del dueño (2026-11-14), tres cambios a los eventos. Backend y app (rama `eventos-lider-personalizada`).
Se apoya en la spec 044 (eventos por audiencia, FR-007 y FR-009 a FR-012) y en la 040 (Meet por ocurrencia).

## Decisiones del dueño (2026-11-14)

| # | Decisión | Efecto |
|---|---|---|
| P1 | Al crear una sesión, el **líder de mentores** hoy ve dos tipos. Debe ver **solo uno: «Sesión personalizada»**, ya elegido (sin tocar). | FR-001 a FR-003 (D-355) |
| P2 | El filtro «Por semáforo» de «¿Para quién es?» usa el semáforo de **ayer** (el día ya cerrado), no el de hoy: temprano todos se ven incompletos. | FR-004 a FR-006 (D-356) |
| P3 | «Programar por días», simple: repetir el evento en los días que se elijan, con un resumen en palabras. «Que no sea complicada.» | FR-007 a FR-012 (D-357) |

## Lo que había (leído en el código el 2026-11-14)

- `QuienConvoca.tiposDe`: líder, mentor y guía recibían `SESION_ESPECIAL` y `ESPONTANEO` (spec 044, P9). Administración y Alquimista, los cuatro.
- El color del semáforo de una audiencia sale de `ColoresDelSemaforoAdapter` → `SemaforoFinder.vigenteDe`: **los siete días cerrados que terminan ayer**, cada
  persona en su zona (`LecturaDeMediciones.vigenteDe`, hasta = hoy − 1). Hoy a medio hacer **nunca** entra. La regla de P2 ya estaba en el servidor; faltaba
  dejarla por escrito, bajo prueba y a la vista del usuario (la pantalla no decía de qué día era el color).
- El servidor ya acepta repetición (`recurrenceFrequency`, `recurrenceInterval`, `recurrenceByWeekday` ISO 1=lunes…7=domingo, `recurrenceUntil` o
  `recurrenceCount`): una fila de regla y las ocurrencias se expanden al leer (`ExpansorOcurrencias`); Meet crea una sala por ocurrencia (spec 040). La
  app no lo ofrecía: un evento repetido se podía leer, no crear.

## Requisitos

### Tipo del líder

- **FR-001**: El servidor ofrece al líder de mentores un solo tipo, `SESION_PERSONALIZADA` («Sesión personalizada»), en `eventTypes` de `GET /calendar/audience/options`,
  y solo ese tipo se acepta al crear (otro: 403 «No puedes crear un evento de ese tipo»).
- **FR-002**: `SESION_PERSONALIZADA` es un tipo nuevo en dominio, base (V193) y wire, con los avisos de la sesión especial (6:00 del día y 10 minutos antes) y sin
  elegibilidad extra. Los eventos ya guardados no cambian de tipo y se siguen viendo.
- **FR-003**: Mentor y guía conservan «Sesión especial» y «Espontáneo»; Administración y Alquimista, sus cuatro tipos de siempre (no se les ofrece la nueva). En la
  app, si el servidor ofrece un solo tipo, el formulario lo deja elegido de entrada y lo muestra como un dato fijo («Tipo: Sesión personalizada»), no como una
  lista para elegir. Un tipo que la app no conoce se rotula «EVENTO».

### Semáforo de ayer

- **FR-004**: El color de una persona para la audiencia es el del semáforo **al cierre de ayer** en su zona: la ventana vigente de siete días cerrados que termina
  ayer. Nunca el día en curso ni la semana a medias; «Sin datos», «En pausa» y sin programa no entran por ningún color (spec 044, P8).
- **FR-005**: La misma lectura sirve a la vista previa («Llega a N»), a las cuentas por color de las opciones, al guardar y a «Volver a calcular» (spec 044,
  FR-010 y FR-011): la lista guardada es una foto del cierre de ayer y no cambia sola; los avisos y recordatorios usan esa lista y no vuelven a mirar el color.
  (La Mentoría del Alquimista, que sí mira el color en cada momento, usa la misma ventana: D-293.)
- **FR-006**: La app lo dice en «Por semáforo»: «Según el semáforo de ayer.» (y «Volver a calcular» al editar toma el de ese día).

### Repetir por días (solo app: el servidor ya acepta la repetición)

- **FR-007**: Al crear un evento, un bloque «Repetir» con un interruptor (apagado de entrada). Al prenderlo aparecen siete botones de día (L M M J V S D) con el día
  de la fecha elegida ya marcado, y un fin: «Por 4 semanas» (sugerido) / «Hasta una fecha» / «N veces».
- **FR-008**: Una línea en palabras resume la repetición: «Se repite lunes y jueves, 4 semanas (hasta el 12 de diciembre).» / «…hasta el 12 de diciembre.» /
  «…, 8 veces.». Sin ningún día marcado no se guarda («Elige al menos un día para repetir.»).
- **FR-009**: Cuerpo: `recurrenceFrequency: 'WEEKLY'`, `recurrenceInterval: 1`, `recurrenceByWeekday` (ISO), y `recurrenceCount` o `recurrenceUntil` (fin de ese
  día en la zona del evento). Si la fecha elegida no cae en un día marcado, la primera ocurrencia es el primer día marcado desde esa fecha, y el evento
  empieza ahí (para que la fecha de inicio sea una ocurrencia real).
- **FR-010**: «Hasta» no puede ser anterior a la fecha de inicio. El tope de «N veces» es 52; el de «semanas», 26.
- **FR-011**: El formulario de edición no muestra «Repetir» (un evento repetido sigue sin editarse en la app, como hasta hoy: se cancela); un evento repetido se
  lista con su serie como siempre.
- **FR-012**: La audiencia de la serie es una (la foto de FR-005), la misma para todas las ocurrencias.

## Fuera de alcance

Editar una serie; repetir cada N semanas, por mes o por día de mes; excepciones por fecha desde la app; cambiar los tabs.
