# Feature Specification: Recordatorio por día al planificar un hábito

**Feature Branch**: `008-recordatorio-por-dia` (backend y app)
**Created**: 2026-10-20
**Status**: Draft
**Input**: Sobre la captura del editor de Training → Planificar → GIMNASIO, el dueño pidió: «los recordatorios también deben de ser por día, no en general, arregla eso para continuar». Agregó que después, debajo del recordatorio, irán los distintos tipos de alarma.

## Clarifications

### Session 2026-10-20

- **El recordatorio se elige por día, igual que la hora** (pedido del dueño). Cada día de la semana puede tener su propio aviso: sin aviso, 30 min antes, 10 min antes, a la hora u otra antelación, y varios a la vez como hoy. Un día sin aviso propio usa el general.
- **Los «tipos de alarma» van después**, en otra spec. Esta solo deja el espacio debajo del recordatorio y no agrega nada visible para eso.
- El dueño pidió avanzar sin más preguntas. Lo que no dijo se resuelve con los supuestos de abajo.
- **Respuestas del dueño en consola (2026-10-20):**
  - Q: ¿Cómo se ve en el chip un día con aviso propio? → A: **campanita con minutos** debajo de la hora: «30′», «a la hora», o la campanita tachada si es sin aviso. Solo en los días cuyo aviso difiere del general. Es el ícono de línea `Icon` de la app, no un emoji.
  - Q: ¿Qué muestra Recordatorio con varios días de avisos distintos marcados? → A: **ningún aviso elegido y la línea «Estos días tienen avisos distintos»**. Lo que se elija se pone a todos los marcados.
  - Q: Con días marcados, si solo cambio el aviso, ¿qué se guarda? → A: **solo lo que se tocó**. Sin mover la rueda, cada día conserva su hora y solo cambia el aviso; el botón dice «Guardar aviso · S, D». Si se movió la hora, se guardan hora y aviso.
  - Q: ¿Cambiar solo el aviso de un día cuenta para el límite de «3 hábitos reacomodados por semana»? → A: **no cuenta**: es libre, como apagar un día. La hora sigue contando como hoy.
  - Q: ¿El aviso de hoy se puede cambiar? → A: **no**. Hoy y los días pasados siguen con candado para hora y aviso; el cambio rige desde el próximo día de ese tipo.
- **Reglas de prueba del dueño:** no se compila APK si solo cambia JavaScript. Se prueba con el build debug instalado y Metro, y se publica recién después de probar.

### Lo que hay hoy (revisado en master el 2026-10-20)

| Pieza | Hoy |
|---|---|
| Hora por día | Ya existe. Los chips L–D guardan una hora propia por **día de la semana**, que se repite todas las semanas. |
| Recordatorio | Solo general. Con días marcados la app dice «El recordatorio se elige sin días marcados» (E-408). |
| Aviso del servidor | Para un día de la semana con hora propia usa esa hora, pero el recordatorio **general**. Para una fecha puntual ya respeta hora y recordatorio propios. |
| Alarma del teléfono | **Bug**: una sola alarma diaria a la hora general. **Ignora la hora propia de cada día**: si el jueves va a las 09:00, el teléfono suena igual a las 07:00. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir el recordatorio de cada día (Priority: P1)

Como participante, quiero marcar uno o varios días al planificar un hábito y elegirles su recordatorio, igual que les elijo la hora. Por ejemplo: gimnasio a las 07:00 con aviso 10 min antes de lunes a viernes, y el sábado a las 09:00 con aviso 30 min antes.

**Why this priority**: es el pedido del dueño. Sin esto, la hora por día queda a medias, porque el aviso no acompaña al día.

**Independent Test**: en el editor, marcar el sábado, elegir hora y «30 min antes» y guardar. Al reabrir, el sábado muestra su hora y su aviso, y los demás días siguen con el general.

**Acceptance Scenarios**:

1. **Given** un hábito con aviso general «10 min antes», **When** marco el sábado, elijo «30 min antes» y guardo, **Then** el sábado queda con «30 min antes» y los demás días siguen con «10 min antes».
2. **Given** días marcados, **When** miro la sección Recordatorio, **Then** puedo elegir el aviso (ya no aparece «El recordatorio se elige sin días marcados») y el botón de guardar dice a qué días se aplica.
3. **Given** el sábado con aviso propio, **When** reabro el editor, **Then** el chip del sábado indica que tiene aviso propio y, al marcarlo, la sección Recordatorio muestra el suyo.
4. **Given** un día con hora y aviso propios, **When** lo devuelvo a «como los demás días», **Then** vuelve a usar la hora y el aviso generales.
5. **Given** ningún día marcado, **When** cambio el recordatorio, **Then** cambia el general, que usan todos los días sin aviso propio. Los días con aviso propio no cambian.
6. **Given** un día con aviso propio «Sin aviso», **When** llega ese día, **Then** no suena ninguna alarma ni llega aviso del servidor de inicio para ese hábito; los demás días sí.

---

### User Story 2 - La alarma del teléfono suena a la hora y con el aviso de cada día (Priority: P1)

Como participante, quiero que la alarma de mi teléfono suene a la hora que puse para ese día y con su antelación, no a la hora general.

**Why this priority**: hoy el teléfono ignora la hora por día. Es un error que ya existe y que deja sin sentido el recordatorio por día.

**Independent Test**: un hábito a las 07:00 todos los días, con el jueves a las 09:00 y «a la hora». Revisar las alarmas programadas en el teléfono: el jueves suena a las 09:00 y no a las 07:00; los otros días, a las 07:00 con su aviso.

**Acceptance Scenarios**:

1. **Given** el jueves a las 09:00 con aviso «a la hora» y el resto a las 07:00 con «10 min antes», **When** se programan las alarmas, **Then** el jueves suena una sola alarma a las 09:00 y los demás días suena a las 06:50 y a ninguna otra hora.
2. **Given** un día apagado para el hábito, **When** llega ese día, **Then** no suena alarma de ese hábito.
3. **Given** que cambio la hora o el aviso de un día, **When** guardo, **Then** la alarma de ese día se reprograma sin tocar las de los demás días y sin duplicarse.
4. **Given** el permiso de alarmas exactas, **When** se programan las alarmas por día, **Then** siguen siendo tipo despertador (spec 007) y aparecen como próxima alarma.
5. **Given** que el teléfono borró las alarmas (detención forzada, spec 007), **When** abro la app, **Then** vuelven todas las de cada día, con su hora y aviso.

---

### User Story 3 - El aviso del servidor usa el recordatorio del día y no se cruza con la alarma (Priority: P2)

Como participante sin alarmas en el teléfono (web, o teléfono que no confirmó sus alarmas), quiero que el aviso del servidor llegue con la antelación de ese día. Si mi teléfono ya tiene la alarma, quiero ver un solo aviso.

**Why this priority**: mantiene coherentes el aviso del servidor y la alarma local, como dejó la 007.

**Independent Test**: en la base clonada, poner el sábado con «30 min antes» y forzar el barrido: el aviso de inicio sale 30 min antes de la hora del sábado. Con la alarma local confirmada, el teléfono muestra uno solo.

**Acceptance Scenarios**:

1. **Given** el sábado con aviso propio «30 min antes», **When** corre el barrido del servidor, **Then** el aviso de inicio del sábado se genera para 30 min antes de su hora.
2. **Given** un día con aviso propio «Sin aviso», **When** corre el barrido, **Then** no sale aviso de inicio ese día.
3. **Given** un teléfono con la alarma del día programada y confirmada, **When** llega el aviso del servidor, **Then** no se ven dos avisos del mismo inicio.

---

### Edge Cases

- **Día de hoy y días pasados con candado**: no se puede cambiar su hora ni su aviso, igual que hoy. El cambio rige desde el próximo día de ese tipo.
- **Fecha puntual** (cambio para una fecha concreta): sigue mandando sobre el día de la semana, para la hora y el aviso.
- **Mismo aviso que el general**: elegir para un día el mismo aviso que el general lo deja igual al general. No se muestra como propio.
- **Varias antelaciones en un día** (por ejemplo 30 y 10 min antes): suenan las dos ese día.
- **Antelación que cruza la medianoche** (hábito 00:10 con aviso 30 min antes): la alarma suena el día anterior a las 23:40, y cuenta como aviso del día del hábito.
- **APK anterior con backend nuevo**: al guardar la hora de un día, no borra el aviso propio que ese día ya tenga. La app vieja sigue usando el general en el teléfono.
- **APK nuevo con backend anterior**: el servidor no informa avisos por día. La app no ofrece aviso por día y se comporta como hoy, sin errores.
- **Cambio diferido de hora** («rige desde mañana»): sigue funcionando; el aviso por día se aplica igual que la hora por día.
- **Hábito sin hora**: no tiene recordatorio, ni general ni por día.
- **Cerrar sesión**: se cancelan todas las alarmas, también las de cada día.
- **Dos hábitos a la misma hora el mismo día**: se agrupan como hoy (E-410), usando la hora y el aviso de ese día.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La persona MUST poder elegir el recordatorio de uno o varios días de la semana de un hábito en el editor, con las mismas opciones que el general: sin aviso, 30 min, 10 min, a la hora y otra antelación, y varias a la vez.
- **FR-002**: Con días marcados, la sección Recordatorio MUST estar disponible y aplicar solo a esos días. Se quita el aviso «El recordatorio se elige sin días marcados».
- **FR-003**: Sin días marcados, el recordatorio MUST cambiar el general, que usan los días sin aviso propio.
- **FR-004**: Un día sin aviso propio MUST usar el general. Un día con aviso propio MUST conservarlo aunque cambie el general.
- **FR-005**: Cada chip de día MUST mostrar si ese día tiene un aviso distinto del general, sin agregar texto largo.
- **FR-006**: Devolver un día a «como los demás días» MUST quitarle hora y aviso propios.
- **FR-007**: El sistema MUST guardar el aviso propio de cada día de la semana junto con su hora, para que valga en cualquier teléfono de la persona.
- **FR-008**: La alarma del teléfono MUST sonar, cada día, a la hora de ese día menos cada antelación de ese día. Los días apagados no suenan. Corrige el error actual de usar siempre la hora general.
- **FR-009**: Cambiar la hora o el aviso de un día MUST reprogramar solo las alarmas de ese día, sin dejar alarmas viejas ni duplicadas.
- **FR-010**: Las alarmas por día MUST conservar lo de la spec 007: tipo despertador con permiso, rearmado al abrir la app y aviso si el teléfono las borró.
- **FR-011**: El aviso de inicio del servidor MUST usar el recordatorio del día: el propio del día de la semana si tiene, si no el general. Una fecha puntual sigue mandando sobre ambos.
- **FR-012**: El aviso del servidor y la alarma local del mismo inicio MUST NOT verse duplicados en un teléfono con alarmas confirmadas (protecciones de la 007).
- **FR-013**: Un APK anterior que guarde la hora de un día MUST NOT borrar el aviso propio de ese día.
- **FR-014**: Un APK nuevo contra un backend anterior MUST funcionar como hoy, sin ofrecer aviso por día y sin errores.
- **FR-015**: El editor MUST dejar libre el espacio debajo del recordatorio para los futuros tipos de alarma, sin mostrar nada nuevo ahí en esta spec.
- **FR-016**: Los textos nuevos MUST ir en español de Perú, con tuteo y en tipo oración, y los cambios de la UI MUST respetar las reglas del editor (áreas de toque ≥ 44 px, `Presionable`, háptico de selección, sin animaciones nuevas salvo las del sistema de la app).

### Key Entities

- **Recordatorio general del hábito**: activado o no y lista de antelaciones en minutos. Lo usan los días sin aviso propio.
- **Día de la semana del hábito**: hora propia y, nuevo, recordatorio propio (activado o no y lista de antelaciones). Sin recordatorio propio, hereda el general.
- **Fecha puntual del hábito**: ya existente. Hora y recordatorio para una fecha concreta; manda sobre el día de la semana.
- **Alarma local del día**: alarma del teléfono para un hábito, un día de la semana y una antelación. Sale de lo que resulta para ese día.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona puede dar a un día una hora y un aviso distintos de los demás en menos de 30 segundos y con un solo guardado.
- **SC-002**: En el emulador, el 100 % de las alarmas programadas de un hábito con horarios por día coinciden con la hora y el aviso de cada día (0 alarmas a la hora general en un día con hora propia).
- **SC-003**: Tras cambiar el aviso de un día, el teléfono no tiene alarmas duplicadas ni viejas de ese hábito (verificado en la lista de alarmas del sistema).
- **SC-004**: El aviso de inicio del servidor de un día con aviso propio sale con su antelación, comprobado en la base clonada.
- **SC-005**: Ningún aviso duplicado del mismo inicio en un teléfono con alarmas confirmadas durante la prueba en el emulador.
- **SC-006**: Con el APK publicado y el backend nuevo, guardar la hora de un día conserva su aviso propio (0 avisos perdidos).

## Assumptions

- «Por día» es por **día de la semana** y se repite todas las semanas, igual que la hora por día que ya existe. Los chips muestran las fechas de esta semana solo como referencia.
- El aviso propio de un día solo se guarda si difiere del general. Elegir el mismo que el general lo deja heredando.
- Hoy y los días pasados siguen con candado; el cambio rige desde el siguiente día de ese tipo.
- El backend se publica antes que el APK nuevo, como en las specs anteriores.
- Las preferencias de una fecha puntual (ya existentes) no cambian y siguen mandando sobre el día de la semana.
- Los tipos de alarma (sonidos, etc.) son otra spec.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.
