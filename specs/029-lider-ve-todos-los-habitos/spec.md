# Feature Specification: El líder de mentores ve los hábitos de todos los alumnos

**Feature Branch**: `029-lider-ve-todos-los-habitos` (backend y app)
**Created**: 2026-10-31
**Status**: Borrador, a la espera de las respuestas del dueño (2 preguntas marcadas; ver «Preguntas abiertas»). No se implementa hasta resolverlas.
**Input**: Pedido del dueño (2026-10-31), textual: «El sistema, con el rol de líder de mentores, debe poder ver todos los hábitos de todos los alumnos.»

## Clarifications

### Session 2026-10-31 (pedido y reglas del dueño)

- **Quién**: el líder de mentores. Administración y Alquimista ya ven a todos por su ficha; esta spec no les quita nada.
- **Qué pide**: ver **todos los hábitos de todos los alumnos**, no persona por persona.
- **Sin duplicar lo que ya hay**: la spec 021 ya le da al líder «Todos los aprendices» (lista con búsqueda) y la ficha de cada uno (sus 7 días, evidencias,
  Código Renaser, tiempo libre y cierres). Esta spec agrega solo lo que esa lista no da: **mirar de través**, un hábito a la vez, en todos los alumnos.
- **Reglas generales del dueño para todas las specs de este lote**: respetar las reglas y no romper la lógica, reutilizar, no crear tablas innecesarias,
  normalizar siempre (o la desnormalización correcta), buenas prácticas y SOLID, en backend y frontend. Ver «Restricciones del dueño».
- **Todo queda dentro de la formación**: el líder solo ve a los alumnos de **sus formaciones** (Escuela 90 días, Diplomado, Certificación; spec 022). Depende
  de la 022 (formaciones) y de la 023 (hábitos por formación).
- **El dueño hará todas las preguntas antes de implementar.** Esta spec deja sus dudas a la vista.

### Lo que hay hoy (verificado en el código y en `AGENTS.md` el 2026-10-31)

| Pieza | Hoy |
|---|---|
| Entrada del líder | Yo → «Mis mentores» (los paneles de rol viven en Yo): resultado por grupo, consultas de mentores, mentores y reporte del mes, y «Todos los aprendices». |
| «Todos los aprendices» (spec 021) | Lista de todos los aprendices que se muestran, con búsqueda por nombre, de a 30; por fila: foto, nombre, grupo (o «Sin grupo») y día del programa. No muestra cumplimiento ni color. Tocar a uno abre su ficha. |
| La ficha de una persona (specs 005 y 021) | Los 7 días de la ventana, cada hábito con su estado en palabras y con forma, sus evidencias (foto, texto, audio, video, «¿Qué sentiste?», Pastilla, post diario), «¿Programó?» (spec 018), Código Renaser, tiempo libre y cierres. Abrirla deja constancia de consulta. |
| Resultado por grupo (spec 005) | Por grupo con mentor: color y % de la semana de cada persona. |
| Lo que **no** se puede | Saber «¿quién no hizo el Despertar hoy?» o «¿qué hábito están dejando más los alumnos?» sin abrir la ficha de cada alumno, una por una. |

**Lo que esto significa**: el líder ve a cada persona con todos sus hábitos, pero no ve **un hábito con todos sus alumnos**. Esta spec agrega esa segunda
mirada, solo de lectura, y reutiliza la ficha para llegar al detalle.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Veo cómo va cada hábito en todos los alumnos (Priority: P1)

Como líder de mentores, quiero abrir «Hábitos de todos» y ver, hábito por hábito, cuántos alumnos lo cumplieron hoy, para saber en un vistazo qué se está
cumpliendo y qué no.

**Why this priority**: es el pedido. Sin esta vista el líder tiene que abrir cientos de fichas para contestar una pregunta simple.

**Independent Test**: con 20 alumnos y 5 hábitos, abrir la vista en el día de hoy. Cada hábito dice «N de M cumplieron» con los mismos números que salen de sumar las
fichas de los 20 alumnos.

**Acceptance Scenarios**:

1. **Given** el líder en Yo → «Mis mentores», **When** toca «Hábitos de todos», **Then** se abre una pantalla completa con el día de hoy, un resumen del día y la lista de
   hábitos de sus formaciones.
2. **Given** la lista, **When** la leo, **Then** cada hábito muestra su nombre en tipo oración, su dimensión, «cumplieron N de M» (M son los alumnos a quienes ese día les toca el hábito)
   y cuántos están pendientes (hoy) o sin cumplir (días cerrados).
3. **Given** la lista, **When** la miro por primera vez, **Then** los hábitos con menos cumplimiento van primero, para encontrar rápido lo que más cuesta.
4. **Given** un hábito que a nadie le toca ese día, **When** miro la lista, **Then** no aparece.
5. **Given** un día en curso, **When** miro un hábito, **Then** lo no hecho se llama «Pendiente» y no «Sin cumplir»: el día todavía no cierra.

---

### User Story 2 - Abro un hábito y veo a los alumnos (Priority: P1)

Como líder, quiero tocar un hábito y ver a cada alumno agrupado por cómo le fue, para saber a quién escribirle o a quién abrir.

**Why this priority**: sin el detalle, la vista solo da números. El valor está en llegar a la persona.

**Independent Test**: tocar «Despertar» en el día de hoy. Se ven los alumnos en secciones: «Sin cumplir» o «Pendientes», «Cumplieron» y «No les tocaba o en pausa». Tocar a uno abre su
ficha de siempre en ese mismo día.

**Acceptance Scenarios**:

1. **Given** un hábito, **When** lo toco, **Then** veo a los alumnos en secciones por estado, con foto o iniciales, nombre, grupo (o «Sin grupo») y, si hay, la hora planificada y la
   hora en que cumplió.
2. **Given** la lista de alumnos, **When** escribo en el buscador, **Then** filtra por nombre, sin tildes ni mayúsculas.
3. **Given** un alumno, **When** lo toco, **Then** se abre su ficha de siempre (la de la spec 021) en el mismo día, y al volver conservo el hábito, el filtro y la posición.
4. **Given** un alumno que cumplió con evidencia, **When** toco «Ver evidencia» en su fila, **Then** se abre la misma hoja de evidencia de la ficha.
5. **Given** la lista, **When** abro una sección, **Then** cada fila dice su estado con palabras y con forma (ícono), nunca solo con color.

---

### User Story 3 - El líder solo ve a los alumnos de sus formaciones (Priority: P1)

Como dueño del producto, quiero que cada líder vea únicamente a los alumnos de las formaciones que le corresponden, para que ningún dato salga de su formación.

**Why this priority**: es una regla del dueño para todo este lote y una condición de privacidad; sin ella la vista no se puede publicar.

**Independent Test**: con dos formaciones (A y B), tres alumnos en cada una, y un líder que solo tiene la A: la vista cuenta 3 alumnos, el buscador no encuentra a nadie de la B y pedir directamente
la ficha de uno de la B responde como si no existiera.

**Acceptance Scenarios**:

1. **Given** un líder de la formación A, **When** abre la vista, **Then** los totales, las listas y la búsqueda incluyen solo a los alumnos de la A.
2. **Given** un líder con dos formaciones, **When** abre la vista, **Then** ve a los alumnos de ambas y puede filtrar por formación; cada hábito se identifica con su formación.
3. **Given** un líder, **When** intenta abrir la ficha o la evidencia de un alumno de otra formación (por ejemplo con un enlace guardado), **Then** recibe «no está disponible» sin
   distinguir si existe o no.
4. **Given** un líder sin ninguna formación a su cargo, **When** abre la vista, **Then** ve un vacío explicado, no un error.
5. **Given** Administración o Alquimista, **When** usan la vista, **Then** ven todas las formaciones.

---

### User Story 4 - Cambio de día y filtros (Priority: P2)

Como líder, quiero mirar ayer o un día de esta semana, y filtrar por grupo, formación o dimensión, para analizar sin recorrer todo.

**Why this priority**: amplía la utilidad, pero la vista ya sirve sin ella con el día de hoy.

**Independent Test**: pasar al día de ayer con ‹ y filtrar por «Sin grupo». Los números cambian y siguen cuadrando con las fichas.

**Acceptance Scenarios**:

1. **Given** la vista, **When** toco ‹ o ›, **Then** cambio de día dentro de la ventana permitida (hasta 7 días hacia atrás, nunca el futuro ni antes del Día 1) y nunca veo, ni por un instante, los datos del día anterior.
2. **Given** los filtros, **When** elijo un grupo (incluido «Sin grupo»), una dimensión o una formación, **Then** la lista, el resumen y el detalle se ajustan a eso.
3. **Given** un filtro sin resultados, **When** lo aplico, **Then** leo «Ningún alumno coincide con estos filtros» con un botón para quitarlos.

---

### Edge Cases

- **Falta de datos no es incumplimiento**: un alumno en pausa, antes de su Día 1, sin nada programado ese día, con la cuenta suspendida o sin haber elegido su Día 1 **no entra** en lo esperado y se
  cuenta aparte con su motivo. Nunca baja un porcentaje.
- **Hoy**: lo no hecho es «Pendiente» hasta que el día de **ese alumno** cierra en **su zona horaria**. Se prueba con el reloj en la madrugada UTC (todavía es el día anterior en Lima).
- **Alumno que cambió de grupo o de formación a mitad de semana**: se cuenta en el grupo y la formación donde está hoy, como en la ficha.
- **Hábito desactivado o renombrado**: sigue mostrando lo que tuvo ese día con su nombre del catálogo; el nombre propio que una persona le puso a una bebida no se usa en la vista de todos.
- **Cuentas de prueba, suspendidas o cerradas**: nunca aparecen (la misma regla de «Todos los aprendices»).
- **Mentores y guías con programa propio**: no son «alumnos»; no entran, igual que en «Todos los aprendices».
- **Muchos alumnos** (cientos) y muchos hábitos: la vista carga sin recorrer persona por persona y no se cuelga.
- **Un hábito compartido entre formaciones**: aparece un renglón por formación; cómo se agrupan lo fija la spec 023.
- **Error de red o respuesta lenta**: estado de carga, y si falla «No pudimos cargar los hábitos» con «Reintentar» de 48.
- **Soporte TI apaga la funcionalidad**: la fila no se dibuja o, si estaba abierta, aparece el aviso de mantenimiento de siempre.
- **APK anterior contra el servidor nuevo**: no cambia nada. **APK nuevo contra el servidor anterior**: la fila no se dibuja (como «Todos los aprendices»).
- **Web**: igual que en el teléfono.

## Requirements *(mandatory)*

### Functional Requirements

**Quién y dónde**

- **FR-001**: El líder de mentores MUST tener, en Yo → «Mis mentores», una entrada «Hábitos de todos» que abre una pantalla completa de solo lectura. La entrada MUST existir solo si el servidor la
  ofrece, y MUST NOT cambiar nada de Hoy, Plan, Training ni Comunidad.
- **FR-002**: Administración y Alquimista MUST poder usar la misma lectura; la entrada en la app es del líder en esta spec. *(a confirmar)*
- **FR-003**: El mentor, la guía de aprendiz, el aprendiz, Soporte TI y cualquier rol que no herede del liderazgo MUST recibir un rechazo; una cuenta suspendida también, aunque su sesión siga viva. El rechazo MUST ir antes que «no existe».
- **FR-004**: La funcionalidad MUST poder apagarse desde Soporte TI, como el resto del líder.

**Qué se ve**

- **FR-005**: La vista MUST contar solo alumnos: aprendices con cuenta activa y programa, sin cuentas de prueba, suspendidas ni cerradas (la regla de «Todos los aprendices»).
- **FR-006**: La vista MUST mostrar, para el día elegido, un resumen y la lista de hábitos de las formaciones del líder, y para cada hábito: nombre, dimensión, cuántos cumplieron sobre cuántos les toca
  y cuántos están pendientes (hoy) o sin cumplir (días cerrados). [NEEDS CLARIFICATION: ¿qué necesita ver el líder con «todos los hábitos de todos los alumnos»? Opciones: A (recomendada) quién cumplió y quién no, hábito por hábito, de todos los alumnos, con
  la hora planificada; B además una tabla alumno × hábito de la semana (difícil de leer en un teléfono); C solo lo que cada alumno tiene planificado (qué hábitos y a qué hora), sin cumplimiento.]
- **FR-007**: Los estados MUST ser los mismos de la ficha de la persona (cumplido, pendiente, sin cumplir, en pausa, antes del Día 1, sin nada planificado, cuenta suspendida) para que las cifras coincidan. La
  falta de datos MUST NOT contarse como incumplimiento.
- **FR-008**: Los hábitos MUST ordenarse por menor cumplimiento primero (desempate por nombre). Un hábito que a nadie le toca ese día MUST NOT aparecer.
- **FR-009**: Al tocar un hábito, la vista MUST mostrar a los alumnos agrupados por estado, con foto o iniciales, nombre, grupo o «Sin grupo», hora planificada y hora en que cumplió, con búsqueda por nombre sin tildes ni mayúsculas.
- **FR-010**: Tocar a un alumno MUST abrir su ficha de siempre en el mismo día; tocar «Ver evidencia» MUST abrir la hoja de evidencia de siempre. Al volver MUST conservarse hábito, filtros y posición.
- **FR-011**: La vista MUST permitir cambiar de día (hasta 7 días hacia atrás, nunca el futuro ni antes del Día 1) y filtrar por formación (si el líder tiene más de una), grupo (con «Sin grupo») y dimensión.
  [NEEDS CLARIFICATION: ¿qué período mira la vista? Opciones: A (recomendada) un día a la vez, hoy por defecto y hasta 7 días atrás; B la semana entera por hábito (lunes a domingo) con el detalle de un día al tocar; C las dos cosas.]
- **FR-012**: La vista MUST NOT permitir registrar, editar, marcar ni borrar nada: es solo lectura.

**Formación, privacidad y datos**

- **FR-013**: Todo MUST quedar dentro de las formaciones del líder: los totales, las listas, la búsqueda, los filtros, el detalle y el acceso directo a una ficha o evidencia. Un alumno de otra formación MUST tratarse como si no existiera.
- **FR-014**: Las cifras MUST salir de los mismos registros diarios que usa la ficha; no se guarda una copia aparte (un dato derivable se deriva en un solo lugar).
- **FR-015**: Mirar las listas MUST NOT dejar constancia de consulta (igual que «Todos los aprendices»); abrir la ficha o una evidencia MUST dejarla como hoy.
- **FR-016**: El estado de cada alumno MUST ser el de su propio día en su zona horaria.
- **FR-017**: La vista MUST cargar sin pedir persona por persona y MUST seguir usable con cientos de alumnos.

**Experiencia y compatibilidad**

- **FR-018**: La pantalla MUST seguir los patrones de la app: un solo scroll, áreas de toque de 48 px, tipo oración, tuteo, claro y oscuro, estados de cargando, vacío, sin coincidencias y error con «Reintentar», el atrás del sistema
  subiendo un nivel por vez (detalle → lista → «Mis mentores» → Yo) y sin animaciones nuevas más allá de `Presionable`.
- **FR-019**: Una app nueva contra un servidor anterior MUST NOT dibujar la entrada; una app anterior contra el servidor nuevo MUST seguir como hoy.
- **FR-020**: Todo MUST documentarse en el mismo cambio y la autorización del dueño sobre Yo MUST anotarse en `AGENTS.md`.

### Key Entities

- **Alumno**: un aprendiz que se muestra (spec 021) y pertenece a una formación.
- **Hábito de una formación**: un hábito del catálogo que aplica a esa formación (spec 023).
- **Estado de un hábito para un alumno en un día**: cumplido (a tiempo o tarde), pendiente, sin cumplir, o sin datos con su motivo. Se deriva de los registros diarios, no se guarda aparte.
- **Formaciones del líder**: las formaciones cuyos alumnos puede ver (spec 022).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El líder contesta «¿quién no hizo este hábito hoy?» en 3 toques como máximo y en menos de 15 segundos, sin abrir ninguna ficha.
- **SC-002**: Para una muestra de 10 hábitos en 3 días distintos, las cifras de la vista coinciden con la suma de los estados en las fichas individuales: 0 discrepancias (E2E).
- **SC-003**: Con dos formaciones y un líder de una sola, 0 alumnos de la otra aparecen en totales, listas, búsqueda o acceso directo (E2E); el acceso directo responde igual que a un alumno inexistente.
- **SC-004**: La lista de hábitos se ve en menos de 3 segundos con 300 alumnos en una conexión móvil normal.
- **SC-005**: En el 100 % de las pruebas, un alumno sin datos (pausa, antes del Día 1, suspendida) nunca cuenta como incumplimiento ni baja un porcentaje.
- **SC-006**: Con el reloj en la madrugada UTC, el «hoy» de cada alumno es su día local (Lima); un hábito aún por cumplir ese día se ve «Pendiente», no «Sin cumplir».
- **SC-007**: Mentor, guía, aprendiz y Soporte TI reciben rechazo; una cuenta suspendida, también (E2E de autorización negativa).

## Restricciones del dueño

Reglas del dueño (2026-10-31) que valen para esta spec:

1. **Reutilizar lo que ya existe**: la lista y la búsqueda de «Todos los aprendices», la ficha y la hoja de evidencia (specs 005 y 021), la regla de quién «se muestra», los estados de los
   hábitos y la ventana de días de la ficha, la entrada Yo → «Mis mentores» y el interruptor del líder de Soporte TI. **Ninguna tabla nueva si una existente sirve**: las cifras se derivan de los
   registros diarios que ya existen. Datos normalizados; si se desnormaliza, se justifica en el plan.
2. **No romper la lógica actual**: los permisos de la spec 021 (mentor y guía, rechazados), el resultado por grupo, las rutas por grupo, la constancia de consulta al abrir una ficha, y la regla de que la falta de datos no es
   incumplimiento no cambian.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID, pruebas de autorización negativa, el tiempo es del participante) y las reglas del `AGENTS.md` del frontend. Yo → «Mis mentores» se toca solo
   para esta entrada, autorizada por este pedido, y se anota en `AGENTS.md`.
4. **Todo queda dentro de la formación de la persona.** Criterio verificable por E2E: un líder de la formación A ve 0 alumnos de la B en totales, listas, búsqueda y filtros, y pedir directamente un alumno, una ficha o una evidencia
   de la B responde como a algo inexistente; un líder con A y B ve ambas y puede separarlas con el filtro.
5. **El dueño hace las preguntas antes de implementar**: esta spec queda en borrador; se implementa solo con sus respuestas. Las dudas están en «Preguntas abiertas».

## Assumptions

- «Alumno» es el aprendiz (rol aprendiz), igual que en la spec 021. Los mentores y guías con programa propio no entran. *(a confirmar)*
- «Sus formaciones» del líder es lo que defina la spec 022; si no define nada para el líder, esta spec exige que el líder vea solo las que se le asignen. Un líder puede tener más de una. *(a confirmar en la 022)*
- Administración y Alquimista ven todas las formaciones y pueden leer lo mismo; su entrada en la app no se agrega en esta spec. *(a confirmar)*
- La ventana de días es la misma de la ficha: hoy y hasta 7 días hacia atrás, sin pasar el Día 1 de cada alumno.
- Los hábitos con menos cumplimiento van primero; el líder no pidió otro orden. *(a confirmar)*
- Las listas no dejan constancia de consulta (como la lista de «Todos los aprendices»); la ficha y las evidencias sí, como hoy. *(a confirmar)*
- La vista no muestra el contenido de las evidencias ni el Código Renaser; para eso están la ficha y su hoja. Si el dueño apaga el Código Renaser para el líder (spec 034), esta vista ya no lo muestra de ningún modo.
- Se apaga con el mismo interruptor de Soporte TI del cumplimiento del líder, salvo que el plan encuentre una razón para uno propio.
- Hace falta publicar el backend antes que el APK nuevo.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.

## Preguntas abiertas

**Las dos que cambian el alcance (marcadas arriba):**

| # | Pregunta | Opciones |
|---|---|---|
| 1 | ¿Qué es «ver todos los hábitos de todos los alumnos»? (FR-006) | **A (recomendada)** por hábito: quién cumplió y quién no, con la hora planificada. B: A más una tabla alumno × hábito de la semana. C: solo lo planificado (qué hábitos y a qué hora tiene cada alumno), sin cumplimiento. |
| 2 | ¿Qué período mira? (FR-011) | **A (recomendada)** un día a la vez (hoy y hasta 7 atrás). B: la semana entera por hábito. C: las dos. |

**Dudas menores asumidas (no cambian el alcance; ver «Assumptions»)**: si Administración y Alquimista tienen su propia entrada; el orden de los hábitos; si las listas dejan constancia; si el líder puede tener más de una formación; si el mismo interruptor de Soporte TI basta.
