# Feature Specification: Planificar — días solo con íconos, «Otra» en su lugar y pantalla completa

**Feature Branch**: `011-editor-por-pasos` (solo app; el backend no cambia)
**Created**: 2026-10-20
**Status**: Ready
**Input**: Pedido del dueño (2026-10-20), con una captura de un teléfono real del editor Training → Planificar →
DESCANSO PROFUNDO: «que ocupe todo, y cuando le doy "Otra" se sobrepone lo de abajo». Comparó 20 diseños en un
lienzo (https://claude.ai/artifact/ThAjGxrVucj36hcgKasZzQ) y al final decidió:

> «Nos quedamos con el que tenemos […] que solo vaya la campana y el reloj de recordatorio, no lo demás […] y la
> "Otra" que sale en recordatorio, como se maneja aquí [imagen: «− 15 min antes +» bajo los botones] […] y que ocupe
> toda la pantalla, y el "Cómo suena" es igual […] la hora mejor que se quede como está […] solo quita que en cada
> día se vea el "30′ +1" de sobra […] no era tanto cambio al final.»

## Clarifications

### Session 2026-10-20

- **El editor de hoy se queda**: mismo orden, mismos textos, misma hora, mismo «Cómo suena» y mismas reglas
  (008, 009 y 010). Se descartan los rediseños del lienzo («por pasos», tarjetas, agenda…).
- **Solo cambian tres cosas**: (1) la pastilla de cada día deja de escribir el aviso («30′», «30′ +1», «a la hora»)
  y queda solo con sus íconos; (2) «Otra» se elige en su lugar, dentro de lo que se desliza; (3) el editor ocupa
  toda la pantalla.
- No cambian el backend, los datos guardados ni las alarmas: no se repiten las pruebas E2E de alarmas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Días que se leen solo con íconos (Priority: P1)

Como participante, quiero que cada día muestre si avisa con la campana (o la campana tachada) y el reloj si tiene
su alarma propia, sin los minutos escritos, que en un chip chico no se entienden.

**Independent Test**: un hábito con el sábado «Sin aviso» y el domingo «30 min antes y a la hora» con alarma propia.
El sábado muestra la campana tachada; el domingo, la campana y el reloj, sin «30′ +1».

**Acceptance Scenarios**:

1. **Given** un día con aviso propio, **When** miro su pastilla, **Then** veo solo la campana, sin texto debajo.
2. **Given** un día sin aviso, **When** lo miro, **Then** veo la campana tachada, como hoy.
3. **Given** un día con alarma propia, **When** lo miro, **Then** veo el reloj junto a la campana, como hoy.
4. **Given** la fila, **When** la miro, **Then** las siete pastillas miden lo mismo y la letra, el número y la hora
   quedan alineados, como hoy.
5. **Given** el lector de pantalla, **When** recorre los días, **Then** sigue diciendo la lista completa de avisos de
   cada día, como hoy.

### User Story 2 - «Otra» se elige en su lugar, sin taparse (Priority: P1)

Como participante, quiero que al tocar «Otra» los minutos se elijan ahí mismo, debajo de los botones, y que lo de
abajo baje en vez de quedar tapado.

**Independent Test**: tocar «Otra». Debajo aparece la pastilla del aviso nuevo («15 min antes», marcada) y una fila
«− 15 min antes +». «Cómo suena» baja. Nada queda encima de nada.

**Acceptance Scenarios**:

1. **Given** el recordatorio, **When** toco «Otra», **Then** se agrega y marca un aviso «15 min antes» (o el último
   elegido) y debajo aparece la fila «−  15 min antes  +», dentro de lo que se desliza.
2. **Given** la fila abierta, **When** toco «+» o «−», **Then** los minutos cambian de 5 en 5, entre 5 y 60, y la
   pastilla del aviso cambia con ellos.
3. **Given** la fila abierta, **When** vuelvo a tocar «Otra», **Then** la fila se cierra y el aviso elegido queda.
4. **Given** la pastilla del aviso de «Otra», **When** la toco, **Then** se desmarca, como cualquier otro aviso.
5. **Given** «reducir movimiento», **When** se abre la fila, **Then** aparece sin animación; si no, con un fundido
   corto.

### User Story 3 - El editor ocupa toda la pantalla (Priority: P1)

Como participante, quiero que el editor de un hábito ocupe toda la pantalla, sin que asome «TRAINING» detrás ni se
corte lo de abajo.

**Independent Test**: abrir Planificar → Descanso profundo. El editor llega arriba hasta debajo de la barra de
estado, y desplazando hasta el final se lee todo por encima de «Guardar».

**Acceptance Scenarios**:

1. **Given** Planificar, **When** abro un hábito, **Then** el editor ocupa todo el alto disponible.
2. **Given** el editor, **When** me desplazo al final, **Then** «Cómo suena» se ve entero por encima del botón fijo.
3. **Given** el editor, **When** uso volver, la ✕ o el atrás de Android, **Then** se comporta como hoy.

### Edge Cases

- **Teléfono angosto y letra grande**: las pastillas no crecen porque ya no llevan texto del aviso.
- **«Otra» con un valor que no es múltiplo de 5** (guardado antes con la rueda): «+» lo lleva al múltiplo de 5
  siguiente (7 → 10) y «−» al anterior (7 → 5), siempre entre 5 y 60, de 5 en 5.
- **«Otra» con un valor que ya existe** («30 min antes»): no se duplica; la pastilla existente queda marcada.
- **Planificador de acciones (010)**: usa la misma fila de días; también deja de mostrar el texto del aviso.
- **Web**: sin recordatorios, como hoy.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La pastilla de cada día MUST mostrar solo los íconos del aviso (campana o campana tachada) y de la
  alarma propia (reloj). MUST NOT escribir los minutos del aviso.
- **FR-002**: La etiqueta del lector de pantalla de cada día MUST seguir nombrando sus avisos completos.
- **FR-003**: «Otra» MUST abrir, dentro del contenido que se desliza y debajo de las opciones, una fila con «−», los
  minutos y «+», de al menos 48 px de toque. MUST NOT abrir nada por encima de otro contenido.
- **FR-004**: «−» y «+» MUST cambiar el aviso de «Otra» entre 5 y 60, de 5 en 5 (solo múltiplos de 5), y reflejarlo
  al instante en su pastilla.
- **FR-005**: El editor de un hábito en Planificar MUST ocupar toda la pantalla disponible.
- **FR-006**: Lo demás del editor (hora, días, opciones del recordatorio, «Cómo suena», textos, guardado y
  permisos) MUST quedar como hoy. Los datos que se guardan MUST ser los mismos.

## Success Criteria *(mandatory)*

- **SC-001**: En el emulador, con «Otra» abierta y cerrada, ningún elemento queda encima de otro.
- **SC-002**: Ninguna pastilla de día muestra texto del aviso; todas miden lo mismo.
- **SC-003**: El editor ocupa el 100 % del alto disponible.
- **SC-004**: Las pruebas automáticas de la 008, 009 y 010 siguen en verde, y los flujos de Maestro de Planificar
  pasan.

## Assumptions

- **Solo JavaScript**: no hace falta código nativo. Se publica con un AAB nuevo, que pidió el dueño.
- **«Otra» parte de 15 min** o del último valor elegido, como hoy.
- **Autorización**: el dueño la dio el 2026-10-20 para el tab Training (editor de Planificar), con este alcance. Se
  anota en AGENTS.md de la app.
