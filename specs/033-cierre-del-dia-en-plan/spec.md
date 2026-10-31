# Feature Specification: «Cierra tu día» pasa a Plan

**Feature Branch**: `033-cierre-del-dia-en-plan` (app; el servidor no cambia)
**Created**: 2026-10-31
**Status**: Borrador, a la espera de las respuestas del dueño (3 preguntas marcadas; ver «Preguntas abiertas»). No se implementa hasta resolverlas.
**Input**: Pedido del dueño (2026-10-31), textual: «Cerrar el día se mueve a Plan, todo de acuerdo a Plan.»

## Clarifications

### Session 2026-10-31 (pedido y reglas del dueño)

- **Se mueve, no se rehace.** «Cierra tu día» (spec 017) ya existe, ya se guarda y ya se ve en las fichas. Esta spec cambia **dónde vive** y **cómo se presenta**; lo que
  pregunta, lo que guarda y sus reglas se quedan.
- **«Todo de acuerdo a Plan»** se lee así: el cierre habla el idioma de Plan (las acciones del día por eje y con el ícono de su eje, «mañana» es lo que Plan muestra de
  mañana) y usa las mismas piezas que Plan (tarjeta, hoja desde abajo, confirmación en línea), sin inventar patrones. *(a confirmar con la pregunta 2)*
- **Reglas generales del dueño para todas las specs de este lote**: respetar las reglas y no romper la lógica, reutilizar, no crear tablas innecesarias, normalizar siempre
  (o la desnormalización correcta), buenas prácticas y SOLID, en backend y frontend. Ver «Restricciones del dueño».
- **Todo queda dentro de la formación de la persona** (spec 022). El cierre es personal, pero lo ve su acompañamiento, y eso ya está limitado por quién acompaña a quién.
- **El dueño hará todas las preguntas antes de implementar.** Esta spec deja sus dudas a la vista.

### Lo que hay hoy (verificado en el código y en `AGENTS.md` el 2026-10-31)

| Pieza | Hoy |
|---|---|
| «Cierra tu día» en Training | Es **la última fila de la lista de hoy** de Training («Cierra tu día · 2 minutos»), siempre, todos los días y a cualquier hora, sin hora y sin depender de Despertar ni de Dormir (spec 017, corregida el 2026-10-30). Ya cerrada, queda en verde con «Día cerrado» y lo dicho para mañana. |
| La hoja | Pantalla completa: lo cumplido llega marcado; por cada acción pendiente cuya hora ya pasó (o sin hora), «¿Qué pasó?», «¿Qué harás mañana?» y una nota; «Tus hábitos: N de M hechos hoy» y «Tu semana: N de M acciones cumplidas» (solo lectura); «Guardar mi cierre». Un día sin acciones dice «Hoy no tenías acciones». Es opcional y no da puntos. Al guardar: «Día cerrado», lo de mañana y «Ver mañana en Training» (el domingo no se ofrece). |
| Días anteriores | En Training, al tocar un día anterior de la semana, aparece una fila «Cerrado» o «Sin cerrar», solo de lectura. Un día que terminó no se cierra tarde. |
| Fichas | El mentor o la guía, el líder y Administración ven «Cierres del día» de cada persona (semana de lunes a domingo, solo lectura). |
| Plan | Una sola pantalla: cabecera «Plan · Tu dirección en 90 días», selector de eje (Cuerpo, Negocio, Relaciones), tarjeta del objetivo de 90 días, «Esta semana», «Acciones que mueven la meta» (hoy y las que vienen, cada una con el ícono de su eje), «Tu 80/20» y «Organizar mi semana». Sin el Mapa de Renacimiento activo, en lugar de todo lo del eje sale «Primero tu Mapa de Renacimiento». |
| El servidor | Lee y guarda el cierre del día de hoy (solo hoy, en la zona de la persona) y sirve las lecturas de las fichas. Esta spec **no lo toca**. |

**Lo que esto significa**: el cierre del día es un asunto de las acciones de los objetivos, que viven en Plan, pero hoy se cierra desde Training. Esta spec lo pone donde corresponde.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cierro mi día desde Plan (Priority: P1)

Como participante, quiero encontrar «Cierra tu día» en Plan, junto a mis acciones, para cerrar el día donde veo y planifico mis acciones.

**Why this priority**: es el pedido. Todo lo demás depende de que el cierre ya viva en Plan.

**Independent Test**: abrir Plan un día cualquiera, a cualquier hora. Está la tarjeta «Cierra tu día · 2 minutos». Tocarla, responder lo pendiente y guardar. La tarjeta queda en verde con «Día cerrado» y
lo dicho para mañana; en la ficha del mentor el día aparece «Cerrado».

**Acceptance Scenarios**:

1. **Given** Plan, **When** lo abro, **Then** veo la tarjeta «Cierra tu día · 2 minutos» como una tarjeta **del día completo**, que no cambia al cambiar el selector de eje.
2. **Given** cualquier día y cualquier hora, **When** abro Plan, **Then** la tarjeta está; no depende de Despertar, de Dormir ni de ninguna hora, y no lleva hora.
3. **Given** la tarjeta, **When** la toco, **Then** se abre la hoja completa de siempre, con las mismas preguntas y reglas de la spec 017.
4. **Given** una acción pendiente en la hoja, **When** la leo, **Then** la veo con el ícono de su eje y su nombre, como en «Acciones que mueven la meta».
5. **Given** que guardo, **When** termina, **Then** la hoja da paso a «Día cerrado» con lo que dije para mañana, la tarjeta de Plan queda en verde y puedo volver a abrirla para cambiar mis respuestas mientras el día no termine.
6. **Given** que guardo, **Then** ninguna acción se completa, no hay puntos ni «+N pts», y no cambian la coherencia, la racha ni el semáforo (igual que hoy).
7. **Given** un día sin acciones, **When** abro la hoja, **Then** dice «Hoy no tenías acciones» y ofrece una nota del día, como hoy.

---

### User Story 2 - Training deja de tener «Cierra tu día» (Priority: P1)

Como participante, quiero que Training muestre solo mis hábitos, acciones y eventos del día, sin una fila que ya vive en otro lado.

**Why this priority**: «se mueve» significa que no queda duplicado; dos entradas al mismo cierre confunden.

**Independent Test**: abrir Training hoy y en un día anterior. No hay fila «Cierra tu día», ni «Cerrado», ni «Sin cerrar», ni espacio vacío donde estaba.

**Acceptance Scenarios**:

1. **Given** la lista de hoy de Training, **When** la miro, **Then** termina donde termina sin el cierre (después de «N hechos hoy», plegado o abierto) y sin un hueco.
2. **Given** un día anterior de la semana en Training, **When** lo elijo, **Then** no hay fila de cierre; el resto del día anterior es igual que hoy.
3. **Given** una persona que ya había cerrado días desde Training antes de este cambio, **When** abre Plan, **Then** su cierre de hoy figura como «Día cerrado» y sus cierres anteriores siguen en las fichas; no se pierde ni se migra nada.

---

### User Story 3 - Veo mañana desde Plan (Priority: P2)

Como participante, después de cerrar mi día quiero ir directo a lo que tengo para mañana, en Plan.

**Why this priority**: «¿Qué harás mañana?» es la mitad del cierre; llevarla a Plan completa el movimiento. Depende de la pregunta 2.

**Independent Test**: guardar el cierre y tocar «Ver mañana en Plan». Plan muestra las acciones de mañana.

**Acceptance Scenarios**:

1. **Given** que guardé mi cierre, **When** toco «Ver mañana en Plan», **Then** llego a las acciones que Plan muestra de mañana, cualquier día de la semana (también el domingo, porque Plan muestra lo que viene).
2. **Given** que mañana no tengo acciones, **When** llego, **Then** leo que no tengo acciones agendadas y puedo agendarlas con el flujo de Plan de siempre.

---

### User Story 4 - Veo mis cierres de días anteriores (Priority: P2)

Como participante, quiero seguir pudiendo ver si cerré un día anterior, sin poder cerrarlo tarde.

**Why this priority**: Training dejó de mostrar «Cerrado» y «Sin cerrar»; esa lectura tiene que quedar en algún sitio. Depende de la pregunta 1.

**Independent Test**: en Plan, ver los días de la semana con «Cerrado» o «Sin cerrar»; tocar un día sin cerrar no ofrece cerrarlo.

**Acceptance Scenarios**:

1. **Given** la semana en curso, **When** miro mis cierres, **Then** cada día pasado dice «Cerrado» (con la hora si hubo) o «Sin cerrar», sin palabras de falta ni de culpa.
2. **Given** un día que terminó sin cerrar, **When** lo toco, **Then** no puedo cerrarlo: un día que terminó no se cierra tarde.
3. **Given** que abro un día cerrado, **When** lo toco, **Then** leo lo que respondí, solo de lectura.

---

### User Story 5 - Mi acompañamiento ve lo mismo (Priority: P3)

Como mentor, guía, líder o administrador, quiero ver los cierres de las personas igual que hoy, vengan de donde vengan.

**Why this priority**: es una garantía de no romper, no una funcionalidad nueva.

**Independent Test**: cerrar un día desde Plan y comprobar que la ficha del mentor, la del líder y la de Administración muestran lo mismo que mostrarían si se hubiera cerrado desde Training.

**Acceptance Scenarios**:

1. **Given** un cierre hecho en Plan, **When** el mentor, el líder o Administración abren «Cierres del día» de esa persona, **Then** ven el día «Cerrado» con lo que respondió, igual que antes.
2. **Given** un líder de otra formación, **When** pide los cierres de esa persona, **Then** la respuesta es la de una persona inexistente.

---

### Edge Cases

- **Sin el Mapa de Renacimiento activo**: hoy Plan reemplaza lo del eje por «Primero tu Mapa de Renacimiento». El cierre no depende de los objetivos (puede cerrarse un día sin acciones). [NEEDS CLARIFICATION: ¿la persona sin Mapa activo también ve «Cierra tu día» en Plan? Opciones: A (recomendada) sí, siempre, como pidió el dueño el 2026-10-30 («es todos los días»); B no, solo con el Mapa activo y acciones agendadas; C sí, pero solo para registrar la nota del día.]
- **Cambio de día con la hoja abierta**: igual que hoy; no se envía nada, se cierra la hoja, se relee y se avisa («Ese día ya cerró»). No se pasa el cierre al día nuevo.
- **Día = día calendario de la persona** en su zona. Se prueba con el reloj en la madrugada UTC (todavía es el día anterior en Lima).
- **Cerrar temprano o después de la medianoche**: se queda como lo decidió el dueño el 2026-10-30 (se pregunta solo por las acciones cuya hora ya pasó; el día anterior queda «Sin cerrar»).
- **Soporte TI apaga Plan**: Plan muestra su aviso de mantenimiento y la tarjeta no aparece; Training tampoco la muestra.
- **Sin red al guardar**: diálogo con «Reintentar» que vuelve a enviar lo mismo; nada a medias.
- **Dos teléfonos**: cerrar desde uno lo muestra cerrado en el otro al volver a leer.
- **APK anterior** (cierre en Training) **y servidor igual**: sigue funcionando; un cierre hecho en cualquiera de las dos versiones se ve en la otra y en las fichas. **APK nuevo contra el servidor anterior** (sin la spec 017): ni la tarjeta ni nada, como hoy.
- **Letra grande y teléfono angosto**: nada se corta; un solo scroll; el botón de SER no tapa la tarjeta ni su chevron.
- **Web**: igual que en el teléfono.
- **Una formación sin objetivos ni acciones**: si la spec 022 define formaciones sin objetivos, el cierre sigue disponible con su nota del día. *(a confirmar)*

## Requirements *(mandatory)*

### Functional Requirements

**El cierre en Plan**

- **FR-001**: Plan MUST mostrar una tarjeta «Cierra tu día · 2 minutos» del día completo, independiente del selector de eje, todos los días, a cualquier hora, sin hora y sin depender de Despertar, de Dormir ni de ninguna hora.
- **FR-002**: Tocarla MUST abrir la hoja completa con las mismas preguntas, reglas y textos de la spec 017 (corregida el 2026-10-30): lo cumplido llega marcado, se pregunta solo por las acciones pendientes cuya hora ya pasó
  o que no tienen hora, nota de hasta 500 caracteres, «Tus hábitos» y «Tu semana» de solo lectura, y «Guardar mi cierre» apagado hasta responder lo que falta.
- **FR-003**: Cada acción pendiente de la hoja MUST mostrarse con el ícono de su eje y su nombre, como las acciones de Plan.
- **FR-004**: Guardar el cierre MUST NOT completar una acción, pagar o anunciar puntos, ni tocar la coherencia, la racha ni el semáforo.
- **FR-005**: Cerrada, la tarjeta MUST quedar en verde con «Día cerrado» y lo dicho para mañana; la hoja MUST poder reabrirse y cambiarse mientras el día no termine.
- **FR-006**: El día del cierre MUST ser el día calendario de la persona en su zona. Si el día cambia con la hoja abierta, MUST NOT enviarse nada, MUST cerrarse la hoja, releerse el cierre y avisarse; MUST NOT reasignarse el cierre al día nuevo.
- **FR-007**: Mientras el dueño no responda la pregunta 3 (ver «Edge Cases»), el cierre MUST seguir disponible para todos, también para quien no tiene acciones agendadas ni el Mapa activo (solo con la nota del día), como hoy.

**Training**

- **FR-008**: Training MUST dejar de mostrar la fila «Cierra tu día» y las filas «Cerrado» y «Sin cerrar» de los días anteriores, sin dejar espacio vacío ni cambiar nada más de su lista por hora.
- **FR-009**: Los cierres ya guardados, hechos desde Training, MUST aparecer en Plan y en las fichas sin migrar ni perder nada.

**Mañana y días anteriores**

- **FR-010**: Después de guardar, «Ver mañana» MUST llevar a lo que Plan muestra de mañana, cualquier día de la semana. [NEEDS CLARIFICATION: ¿basta con mover el lugar o también hay que conectar «¿Qué harás mañana?» con planificar mañana? Opciones: A (recomendada) solo mover, y «Ver mañana en Plan» lleva a las acciones de mañana, sin automatismos (como en la 017); B además, al guardar, ofrecer «Agendar mañana» como atajo hacia el flujo de agendar de Plan; C automatizar: «Hacerla a otra hora» abre el selector de hora de esa acción.]
- **FR-011**: Los cierres de días anteriores MUST seguir pudiéndose ver, solo de lectura, como «Cerrado» (con la hora si hubo) o «Sin cerrar»; un día que terminó MUST NOT poder cerrarse. [NEEDS CLARIFICATION: ¿dónde se ven? Opciones: A (recomendada) solo en Plan, en la misma tarjeta (la semana de lunes a domingo); B en Plan y también en el día anterior de Training; C solo en las fichas del acompañamiento.]

**Fichas, interruptores y compatibilidad**

- **FR-012**: «Cierres del día» de las fichas del mentor o la guía, del líder y de Administración MUST seguir iguales en contenido, reglas y permisos.
- **FR-013**: El interruptor `plan` de Soporte TI MUST regir la tarjeta; apagado, Plan muestra su aviso de mantenimiento de siempre. *(el nombre del interruptor es el de la 017)*
- **FR-014**: La tarjeta y la hoja MUST seguir los patrones de la app: un solo scroll, 48 px, tipo oración, tuteo, claro y oscuro, hoja desde abajo completa, el atrás del sistema, y las mismas animaciones y vibraciones que ya
  tiene el cierre (un fundido al guardar, `tacto.logro()` una sola vez, respeto de «reducir movimiento»); sin animaciones nuevas.
- **FR-015**: El servidor MUST NOT cambiar: mismas lecturas, mismo guardado, mismas reglas y mismos permisos. Una app nueva contra un servidor anterior MUST comportarse como hoy; una app anterior contra el servidor actual MUST seguir funcionando.
- **FR-016**: Todo MUST documentarse en el mismo cambio: la spec 017 y `AGENTS.md` llevan notas «Corregido» donde decían «Training», y el pedido del dueño sobre Plan y Training se anota como autorización.

### Key Entities

- **Cierre del día**: el que define la spec 017 (marca de día cerrado, nota del día y respuestas de las acciones pendientes). No cambia.
- **Tarjeta «Cierra tu día» de Plan**: la entrada al cierre del día de hoy, con su estado (sin cerrar, cerrado) y, según la pregunta 1, la mirada a los días anteriores.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Hay una sola entrada a «Cierra tu día»: en Plan sí, en Training no (E2E y Maestro: el cierre no existe en la lista de Training, hoy ni en días anteriores).
- **SC-002**: Cerrar el día desde Plan toma los mismos pasos que desde Training (abrir, responder lo pendiente, guardar) y menos de 2 minutos; sin pasos extra.
- **SC-003**: Lo que se guarda no cambia: las pruebas de la spec 017 (guardar no cambia `completada` ni los puntos de ninguna acción) siguen en verde sin modificarse.
- **SC-004**: El 100 % de los cierres hechos antes del cambio aparecen en Plan y en las fichas, sin migración.
- **SC-005**: Las fichas muestran exactamente lo mismo antes y después del cambio, para un mismo cierre (comparación en E2E): 0 diferencias.
- **SC-006**: Con el reloj en la madrugada UTC, el «hoy» del cierre es el día local; una hoja abierta al pasar la medianoche no envía nada.
- **SC-007**: Un líder, mentor o guía de otra formación recibe la misma respuesta que ante una persona inexistente al pedir los cierres (E2E).

## Restricciones del dueño

Reglas del dueño (2026-10-31) que valen para esta spec:

1. **Reutilizar lo que ya existe**: la hoja del cierre, sus textos y reglas, su lectura y guardado, las fichas de «Cierres del día» y los componentes de Plan (tarjeta, hoja desde abajo, ícono de eje, confirmación en línea).
   **Ni endpoint ni tabla nuevos**: el servidor no cambia. Si el plan encontrara que algo sí hace falta, lo justifica antes.
2. **No romper la lógica actual**: la spec 017 entera sigue vigente (opcional, sin puntos, no completa acciones, solo el día de hoy, 409 en un día que ya cerró, pregunta solo por las acciones cuya hora pasó, el texto no entra al
   Espejo Sombra, constancia al leer una ficha). Training (su lista por hora) y Plan (selector de eje, Mapa) no pierden nada más que lo que esta spec mueve.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID, el tiempo es del participante) y las reglas del `AGENTS.md` del frontend. Training y Plan son de los cinco tabs: este pedido los autoriza solo para esto y
   se anota en `AGENTS.md`. Una excepción no abre el tab.
4. **Todo queda dentro de la formación de la persona.** El cierre es de cada persona. Criterio verificable por E2E: quien acompaña o lidera solo recibe los cierres de personas de sus formaciones; pedir los de otra formación responde como a algo
   inexistente; un cierre guardado en una formación no aparece en otra.
5. **El dueño hace las preguntas antes de implementar**: esta spec queda en borrador; se implementa solo con sus respuestas. Las dudas están en «Preguntas abiertas».

## Assumptions

- La tarjeta va en la pantalla principal de Plan, bajo las acciones del día, porque cerrar es lo último que se hace; su lugar exacto lo fija el diseño con el dueño. *(a confirmar)*
- Lo que Plan ya muestra de «mañana» son las acciones que vienen; «Ver mañana en Plan» las usa. No se agrega ninguna vista nueva de mañana.
- «Ver mañana en Plan» se ofrece todos los días, también el domingo (en Training el domingo no se ofrecía porque mañana caía fuera de la semana).
- Un día anterior se lee solo desde Plan, y solo la semana en curso (la regla de la spec 017). *(a confirmar con la pregunta 1)*
- La spec no agrega avisos nuevos: el cierre nunca tuvo recordatorio y no lo tiene.
- Se mantiene el interruptor `plan` de Soporte TI de la spec 017; si el dueño prefiere uno propio para el cierre, lo decide el plan.
- Hace falta un APK nuevo (solo cambio de la app). No hace falta publicar el backend.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.

## Preguntas abiertas

**Las tres que cambian el alcance (marcadas arriba):**

| # | Pregunta | Opciones |
|---|---|---|
| 1 | ¿Dónde se ven los cierres de días anteriores? (FR-011) | **A (recomendada)** solo en Plan. B: en Plan y también en el día anterior de Training. C: solo en las fichas del acompañamiento (la persona ya no los ve). |
| 2 | ¿Basta con mover el lugar, o también se conecta con planificar mañana? (FR-010) | **A (recomendada)** solo mover; «Ver mañana en Plan», sin automatismos. B: además un atajo «Agendar mañana». C: automatizar «Hacerla a otra hora». |
| 3 | ¿La persona sin Mapa activo también ve «Cierra tu día» en Plan? (Edge Cases, FR-007) | **A (recomendada)** sí, siempre. B: no, solo con Mapa y acciones agendadas. C: sí, pero solo para la nota del día. |

**Dudas menores asumidas (no cambian el alcance; ver «Assumptions»)**: el lugar exacto de la tarjeta en Plan; si «Ver mañana» se ofrece también el domingo; si se mantiene el interruptor `plan`; qué pasa con una formación sin objetivos.
