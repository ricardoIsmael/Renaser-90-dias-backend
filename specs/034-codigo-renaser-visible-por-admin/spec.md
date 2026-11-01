# Feature Specification: Administración decide si el mentor y el líder ven el Código Renaser

**Feature Branch**: `034-codigo-renaser-visible-por-admin` (backend y app)
**Created**: 2026-10-31
**Status**: Implementada (2026-10-31): backend en esta rama y app en `033-034-cierre-en-plan-e-interruptor` del frontend. Falta el E2E en el emulador. El dueño respondió el 2026-10-31 (ver «Decisiones del dueño (31/10)»): las tres preguntas son la opción A, y quien lo decide incluye también a Soporte TI.
**Input**: Pedido del dueño (2026-10-31), textual: «El líder de mentores y el mentor no pueden ver el Código Renaser: un interruptor para activar y desactivar.»
Decisión del dueño sobre quién lo decide: **Administración** (con un interruptor). No lo decide el aprendiz ni el mentor.
*(Ampliado el 2026-10-31, 034-4 B: lo deciden Administración, Alquimista **y también Soporte TI**.)*

## Clarifications

### Session 2026-10-31 (pedido y reglas del dueño)

- **Qué se controla**: si el **mentor** (incluida la guía de aprendiz, que actúa como mentor) y el **líder de mentores** ven, en las fichas de los aprendices, el Código Renaser.
- **Quién decide**: **Administración**, con un interruptor. En la app, «Administración» es ADMIN y ALQUIMISTA (quienes ya administran). No decide el aprendiz ni el mentor.
- **Reglas generales del dueño para todas las specs de este lote**: respetar las reglas y no romper la lógica, reutilizar, no crear tablas innecesarias, normalizar siempre
  (o la desnormalización correcta), buenas prácticas y SOLID, en backend y frontend. Ver «Restricciones del dueño».
- **Todo queda dentro de la formación de la persona** (spec 022). El interruptor no amplía lo que nadie ve: el líder sigue viendo solo a los alumnos de sus formaciones.
- **El dueño hará todas las preguntas antes de implementar.** Esta spec deja sus dudas a la vista, entre ellas **cuál es el estado inicial** del interruptor.

### Decisiones del dueño (31/10)

Fuente: `decisiones-del-dueno-2026-10-31.md`, sección 034. Lo no listado = la opción A (recomendada) de cada pregunta.

| # | Pregunta | Decisión |
|---|---|---|
| 1 | Estado inicial (FR-005) | **A, apagado.** Al publicar, el mentor, la guía y el líder dejan de ver el Código Renaser hasta que alguien lo encienda. |
| 2 | Un interruptor o más (FR-006) | **A, uno solo** para el mentor, la guía y el líder juntos. |
| 3 | Qué ven con el interruptor apagado (FR-013) | **A, nada.** Ni respuestas, ni horas, ni el estado de Mi Radar, ni un indicador de «registró / no registró». |
| 4 | Quién lo decide (dudas menores) | **B, Administración, Alquimista y también Soporte TI.** |
| 5 | Motivo escrito | **No.** Cambia con una confirmación, sin motivo. |
| 6 | Avisar a la persona | **No.** No se avisa al aprendiz ni al mentor ni al líder de que cambió. |
| 7 | ADMIN y ALQUIMISTA | El mismo poder. |

**Cómo se resolvió, sin tabla nueva** (plan en `plan.md`): el interruptor es **una funcionalidad más del catálogo de Soporte TI**, `radar.fichas`
(«Código Renaser en las fichas», hija de `radar`), que nace apagada **para MENTOR, TRAINEE_GUIDE y MENTOR_LEAD** y prendida para el resto. Sus filas
(`funcionalidades_estado`, `funcionalidades_estado_rol`), la copia en memoria, el canal Redis y la auditoría append-only (`cambios_auditados`) son
las que ya existen. Apagada **se esconde** (404 `FUNCIONALIDAD_NO_DISPONIBLE`, como «no disponible» de la fase 2) en vez de mostrar el aviso de mantenimiento.
Administración y Alquimista lo cambian con un endpoint propio y angosto; Soporte TI, desde su consola (rol por rol) o por ese mismo endpoint.

### Lo que hay hoy (verificado en el código y en `AGENTS.md` el 2026-10-31)

| Pieza | Hoy |
|---|---|
| Qué es | El Código Renaser: cinco preguntas («¿Qué hago? ¿Qué pienso? ¿Qué siento? ¿Qué evito?» y la energía de 1 a 10). Los días 1 a 7 se responde cada hora de 08:00 a 19:00; desde el día 8 es opcional, con «Mi Radar» (specs 012 y 019). |
| Quién lo ve hoy | La persona, y su acompañamiento en la ficha: el mentor o la guía vigente del grupo, el líder de mentores (por grupo o por «Todos los aprendices», spec 021), Administración y Alquimista. Decisión del dueño de la spec 012: «se tiene que analizar a los demás roles de cómo va». |
| Qué ven | En la sección «Código Renaser» de la ficha: si Mi Radar está encendido, a qué horas y cuántas veces, y todos los registros (las respuestas, la energía, la hora prevista y la real), paginados. |
| Quién puede cambiarlo | Nadie: no existe ningún ajuste. La regla está escrita en el código y en la spec 012. |
| Lo que dice la Política de Privacidad | Que los datos los ven «solo las personas que los necesitan: tu mentor, quienes coordinan tu grupo y el equipo de administración». |
| Interruptores existentes | Soporte TI ya tiene una consola de interruptores por funcionalidad y por rol, con auditoría y con la regla de que lo que a una cuenta no le toca **se esconde** (sin aviso de mantenimiento). Es de Soporte TI, no de Administración. |

**Lo que esto significa**: hoy el mentor y el líder ven siempre el Código Renaser. El dueño quiere que Administración pueda decidir que no lo vean, y volver a permitirlo, sin tocar código ni reinstalar la app.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administración enciende o apaga la vista del mentor y del líder (Priority: P1)

Como administrador o alquimista, quiero un interruptor en Administración que decida si el mentor y el líder ven el Código Renaser de los aprendices.

**Why this priority**: es el pedido. Sin el interruptor no hay nada que decidir.

**Independent Test**: como administrador, abrir Yo → Administración → Más opciones → «Código Renaser en las fichas», apagar el interruptor y confirmar. Ver la línea «Último cambio: … ». Como mentor, abrir la ficha de un aprendiz:
no hay sección «Código Renaser».

**Acceptance Scenarios**:

1. **Given** un administrador o alquimista, **When** abre Yo → Administración → Más opciones, **Then** ve «Código Renaser en las fichas» con su estado («El mentor y el líder lo ven» / «El mentor y el líder no lo ven»).
2. **Given** que toca el interruptor, **When** cambia el estado, **Then** una confirmación le dice qué va a pasar («El mentor, la guía y el líder dejarán de ver el Código Renaser de los aprendices. Tú y la propia persona lo seguirán viendo.») y, al
   aceptar, el cambio rige en el acto.
3. **Given** que cambió el estado, **Then** aparece una confirmación en línea («Listo: el mentor y el líder ya no ven el Código Renaser») y la pantalla muestra «Último cambio: <nombre>, <fecha y hora>».
4. **Given** una cuenta que no decide esto (mentor, guía, líder, aprendiz), **When** abre su app, **Then** la fila ni la pantalla existen; si pide el cambio por otro camino, el servidor lo rechaza. **Soporte TI** lo decide desde su consola de interruptores (la funcionalidad «Código Renaser en las fichas», rol por rol, con su motivo y su auditoría de siempre) y también puede usar el endpoint de Administración.
5. **Given** que el cambio falla (sin red), **Then** el interruptor vuelve a su estado real y se dice qué pasó, con «Reintentar».

---

### User Story 2 - Apagado, el mentor y el líder no ven nada del Código Renaser (Priority: P1)

Como aprendiz, quiero que cuando Administración lo decida, ni mi mentor ni el líder vean mi Código Renaser.

**Why this priority**: es el efecto del pedido; un interruptor que solo esconde un botón sin impedir la lectura no protege nada.

**Independent Test**: con el interruptor apagado, el mentor de un aprendiz y el líder abren su ficha (por el grupo y por «Todos los aprendices»): no hay sección «Código Renaser». Pedir esas mismas lecturas directamente al
servidor con las sesiones del mentor y del líder: rechazo. Con la sesión de un administrador: 200.

**Acceptance Scenarios**:

1. **Given** el interruptor apagado, **When** el mentor o la guía abre la ficha de su aprendiz, **Then** la ficha carga completa **sin** la sección del Código Renaser: no se ve un hueco, un error ni un aviso de mantenimiento.
2. **Given** el interruptor apagado, **When** el líder abre una ficha (desde un grupo o desde «Todos los aprendices»), **Then** ocurre lo mismo.
3. **Given** el interruptor apagado, **When** el mentor o el líder piden las lecturas del Código Renaser por cualquier camino, **Then** el servidor las rechaza; esconderlas solo en la pantalla no alcanza.
4. **Given** el interruptor apagado, **When** un administrador o alquimista abre una ficha, **Then** sigue viendo el Código Renaser completo.
5. **Given** el interruptor apagado, **When** la propia persona usa Mi Radar o responde su Código Renaser, **Then** nada cambia para ella.
6. **Given** que Soporte TI entra con «Ver como Mentor», **When** el interruptor está apagado, **Then** ve lo que vería ese mentor: sin la sección.
7. **Given** un mentor con una versión anterior de la app, **When** abre la ficha con el interruptor apagado, **Then** la ficha carga sin la sección (la app ya esconde la sección si el servidor la rechaza).

---

### User Story 3 - Encendido, todo sigue como hoy (Priority: P1)

Como mentor o líder, quiero que cuando Administración lo permita, vea el Código Renaser exactamente como lo ve hoy.

**Why this priority**: «no romper la lógica actual». Encender debe devolver todo, sin pérdidas.

**Independent Test**: apagar y volver a encender. La ficha del mentor muestra de nuevo el estado de Mi Radar, las horas y todos los registros, incluidos los que la persona hizo mientras estuvo apagado.

**Acceptance Scenarios**:

1. **Given** el interruptor encendido, **When** el mentor o el líder abren una ficha, **Then** ven la sección como hoy (specs 012 y 019).
2. **Given** que estuvo apagado un tiempo, **When** se enciende, **Then** reaparecen todos los registros, también los hechos durante ese tiempo; nada se borró.
3. **Given** que estaba apagado, **Then** los datos del Código Renaser de las personas siguen guardándose y no se alteran.

---

### User Story 4 - Queda constancia de quién lo cambió (Priority: P2)

Como administrador, quiero saber quién y cuándo cambió el interruptor, para rendir cuentas ante el dueño y las personas.

**Why this priority**: es una decisión de privacidad; sin historial nadie sabe por qué un mentor dejó de verlo.

**Independent Test**: cambiar el interruptor dos veces con dos cuentas distintas. El historial tiene dos entradas con quién, cuándo, el valor anterior y el nuevo.

**Acceptance Scenarios**:

1. **Given** cualquier cambio, **Then** queda registrado quién lo hizo, cuándo, el valor anterior y el nuevo, y ese registro no se edita ni se borra.
2. **Given** la pantalla del interruptor, **Then** muestra el último cambio con quién y cuándo.
3. **Given** dos administradores que cambian a la vez, **Then** gana el último, ambos cambios quedan en el registro y la pantalla muestra el valor vigente.

---

### Edge Cases

- **Cambiar con una ficha abierta**: la sección aparece o desaparece cuando la ficha se vuelve a leer; no queda a medias ni muestra un error.
- **Los días 1 a 7**: el Código Renaser es obligatorio para el aprendiz y el mentor suele mirarlo para acompañar. Con el interruptor apagado el mentor no lo ve esos días tampoco. **Decidido (31/10, opción A): nada.** Con el interruptor apagado el mentor y el líder no ven nada que venga del Código Renaser, tampoco un indicador sin contenido.
- **Los datos no se tocan**: apagar no borra, anonimiza ni mueve nada.
- **Rol personalizado** que hereda de Mentor o de Líder: se trata como su rol base.
- **Cuenta suspendida o sin red**: no cambia nada; el servidor sigue siendo la autoridad.
- **Cuentas de prueba de «Ver como»**: se comportan como el rol que representan.
- **Sin formación**: el interruptor no cambia a quién ve el líder (solo a los alumnos de sus formaciones, spec 022); apagarlo oculta, encenderlo no amplía.
- **APK nuevo contra el servidor anterior**: la pantalla del interruptor no se dibuja (el servidor no la ofrece). **APK anterior contra el servidor nuevo**: si el interruptor está apagado, la ficha carga sin la sección.
- **Web**: igual que en el teléfono.
- **Política de Privacidad**: la página pública dice quién puede ver los datos, no que siempre lo ven; con el interruptor apagado sigue siendo cierta y no se modifica.

## Requirements *(mandatory)*

### Functional Requirements

**El interruptor**

- **FR-001**: Administración (ADMIN y ALQUIMISTA) MUST tener, en Yo → Administración → Más opciones, una pantalla «Código Renaser en las fichas» con un interruptor que decide si el mentor, la guía y el líder de mentores ven el Código Renaser en las fichas de los aprendices. La fila y la pantalla MUST
  existir solo para esos dos roles. **Soporte TI** MUST poder decidir lo mismo desde su consola de interruptores (la funcionalidad `radar.fichas`) y por el mismo endpoint (decisión 034-4 B).
- **FR-002**: Cualquier otro rol (mentor, guía, líder, aprendiz) MUST recibir un rechazo si intenta leer o cambiar este ajuste, y una cuenta suspendida también, aunque su sesión siga viva.
- **FR-003**: Cambiar el interruptor MUST pedir una confirmación que diga qué va a pasar, MUST regir en el acto para todas las personas y MUST NOT exigir reinstalar la app.
- **FR-004**: Todo cambio MUST quedar registrado: quién, cuándo, valor anterior y valor nuevo, sin posibilidad de editarlo ni borrarlo. La pantalla MUST mostrar el último cambio.
- **FR-005**: El estado inicial del interruptor al publicarse MUST ser **apagado** (decisión 034-1 A): sin ninguna fila guardada, el mentor, la guía y el líder no ven el Código Renaser.
- **FR-006**: MUST haber **un solo interruptor** para el mentor, la guía y el líder juntos (decisión 034-2 A). Encender lo enciende para los tres; apagar lo apaga para los tres.

**Qué se esconde**

- **FR-007**: Con el interruptor apagado, el mentor, la guía y el líder MUST NOT recibir ni ver nada del Código Renaser de otras personas: ni las respuestas, ni las horas, ni el estado de Mi Radar, ni cuántos registros hay. La sección MUST esconderse de la ficha sin dejar un hueco, un error ni un aviso de mantenimiento.
- **FR-008**: El servidor MUST rechazar cualquier lectura del Código Renaser de otra persona hecha por esos roles, venga de la pantalla que venga. Esconder en la pantalla MUST NOT ser la única protección. Los rechazos MUST ser tan limpios que la ficha siga cargando.
- **FR-009**: Con el interruptor encendido, todo MUST comportarse exactamente como hoy (specs 012 y 019).
- **FR-010**: Administración y Alquimista MUST seguir viendo el Código Renaser de todas las personas, y cada persona MUST seguir viendo el suyo, sin importar el interruptor.
- **FR-011**: Apagar y encender MUST NOT borrar, alterar ni mover ningún dato; al encender MUST reaparecer todo, incluido lo registrado mientras estuvo apagado.
- **FR-012**: Cualquier otra forma de ver las respuestas del Código Renaser que agreguen otras specs (por ejemplo la 030, con audio) MUST respetar este mismo interruptor.
- **FR-013**: Con el interruptor apagado, el mentor y el líder MUST NOT ver nada que venga del Código Renaser, tampoco un indicador sin contenido (decisión 034-3 A).

**Formación, experiencia y compatibilidad**

- **FR-014**: El interruptor MUST NOT ampliar nunca lo que alguien ve: el líder sigue viendo solo a los alumnos de sus formaciones y el mentor solo a los de su grupo.
- **FR-015**: La pantalla MUST seguir los patrones de Administración: el `Interruptor` de la app, confirmación antes de cambiar, `ConfirmacionEnLinea` y `tacto.logro()` al guardar, un solo scroll, 48 px, tipo oración, tuteo, claro y oscuro, el atrás del sistema subiendo un nivel por vez, y las mismas animaciones que ya usa el
  `Interruptor` (sin nuevas; respetando «reducir movimiento»).
- **FR-016**: Una app anterior contra el servidor nuevo MUST seguir funcionando (la ficha carga sin la sección si el interruptor está apagado); una app nueva contra un servidor anterior MUST NOT dibujar la pantalla.
- **FR-017**: Todo MUST documentarse en el mismo cambio: la spec 012 (quién ve el Código Renaser) y `AGENTS.md` llevan notas «Corregido», y el pedido del dueño sobre Yo se anota como autorización.

### Key Entities

- **Ajuste «el mentor y el líder ven el Código Renaser»**: un valor (sí o no) que deciden Administración, Alquimista y Soporte TI. Uno solo, global. Vive como la funcionalidad `radar.fichas` del catálogo de Soporte TI: «sí» = prendida para los tres roles, «no» = apagada para los tres.
- **Cambio del ajuste**: quién lo hizo, cuándo, el valor anterior y el nuevo. Solo se agrega; no se edita ni se borra.
- **Código Renaser de una persona**: sus registros y su configuración de Mi Radar (specs 012 y 019). No cambia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con el interruptor apagado, el 100 % de las lecturas del Código Renaser de otra persona hechas por mentor, guía y líder (por el grupo, por «Todos los aprendices» y directamente) son rechazadas, y el 100 % de las hechas por Administración y Alquimista funcionan (E2E).
- **SC-002**: El cambio rige en el acto: la lectura siguiente, de cualquier persona, ya respeta el nuevo estado (E2E).
- **SC-003**: Con el interruptor encendido, las fichas del mentor, del líder y de Administración son idénticas a las de hoy (comparación en E2E): 0 diferencias.
- **SC-004**: El 100 % de los cambios queda registrado con quién, cuándo, valor anterior y nuevo, y ningún registro se puede editar ni borrar.
- **SC-005**: Después de apagar y volver a encender, la cantidad de registros del Código Renaser de cada persona es la misma que antes, más los hechos mientras estuvo apagado: 0 pérdidas.
- **SC-006**: Un administrador cambia el interruptor en 3 toques o menos desde Yo.
- **SC-007**: Con una sesión de «Ver como Mentor» y el interruptor apagado, la ficha no tiene la sección; con una sesión de aprendiz, el aprendiz sigue viendo el suyo.
- **SC-008**: Un mentor con una versión anterior de la app abre la ficha con el interruptor apagado y la ficha carga completa, sin errores visibles.

## Restricciones del dueño

Reglas del dueño (2026-10-31) que valen para esta spec:

1. **Reutilizar lo que ya existe**: las lecturas actuales del Código Renaser (por mentor, por líder y por Administración), el permiso y las comprobaciones de rol que ya existen, el mecanismo de interruptores y de auditoría que Soporte TI ya tiene (el plan debe evaluar reutilizarlo antes de crear algo), la
   pantalla de Administración «Más opciones» y los componentes de la app (`Interruptor`, confirmación, `ConfirmacionEnLinea`). **Ninguna tabla nueva si una existente sirve**; un solo ajuste y su historial. Datos normalizados; si se desnormaliza, se justifica en el plan.
2. **No romper la lógica actual**: con el interruptor encendido todo es como hoy; Mi Radar, los días 1 a 7, lo que ve la propia persona, lo que ve Administración y la constancia de consulta de las fichas no cambian.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID, pruebas de autorización negativa para cada endpoint nuevo) y las reglas del `AGENTS.md` del frontend. Yo → Administración y las fichas de Yo se tocan solo para esto, autorizado por este pedido, y se anota en `AGENTS.md`.
4. **Todo queda dentro de la formación de la persona.** Criterio verificable por E2E: el interruptor no hace que nadie vea más de lo que ya ve. Un líder de la formación A, con el interruptor encendido, ve el Código Renaser de los alumnos de la A y de ninguno de la B; con el interruptor apagado, de ninguno. Si el alcance
   resulta ser por formación, apagarlo en A no cambia nada en B.
5. **El dueño hace las preguntas antes de implementar**: esta spec queda en borrador; se implementa solo con sus respuestas. Las dudas están en «Preguntas abiertas».

## Assumptions

- «Administración» es ADMIN y ALQUIMISTA, quienes ya administran en la app; **Soporte TI también lo decide** (034-4 B), desde su consola. *(confirmado el 2026-10-31)*
- «Mentor» incluye a la guía de aprendiz, que tiene los mismos permisos de ficha.
- La propia persona siempre ve su Código Renaser; Administración y Alquimista lo ven siempre. *(confirmado el 2026-10-31)*
- El cambio pide una confirmación pero **no** un motivo escrito (a diferencia de la consola de Soporte TI, que conserva el suyo). *(confirmado el 2026-10-31)* La auditoría guarda un motivo fijo («Cambio desde Administración») para que el registro quede completo.
- Apagado, lo que se esconde es todo el Código Renaser de la ficha, no solo las respuestas: también el estado y las horas de Mi Radar.
- Los datos se siguen guardando mientras el interruptor está apagado; el interruptor solo decide quién los ve.
- Cualquier cambio posterior (por ejemplo el audio de la spec 030) depende de este mismo interruptor; no hay uno por función.
- La Política de Privacidad no se modifica y **no se avisa a la persona** del cambio. *(confirmado el 2026-10-31)*
- Hace falta publicar el backend antes que el APK nuevo.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.

## Preguntas abiertas

Ninguna. Respondidas por el dueño el 2026-10-31 (ver «Decisiones del dueño (31/10)»): estado inicial **apagado**, **un solo** interruptor, apagado **nada** (ni un indicador),
lo deciden **Administración, Alquimista y también Soporte TI**, sin motivo escrito y sin avisar a la persona.
