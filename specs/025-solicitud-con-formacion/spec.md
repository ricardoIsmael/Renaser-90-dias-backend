# Feature Specification: Aceptar una solicitud eligiendo formación, rol y ficha

**Feature Branch**: `025-solicitud-con-formacion`

**Created**: 2026-10-31

**Status**: Draft — con 2 preguntas abiertas que cambian el alcance (ver «Dudas para el dueño»)

**Input**: Pedido del dueño del 2026-10-31, textual: «El sistema, como se viene manejando, debe poder aceptar una solicitud. Al aceptar a la persona,
primero se elige a qué formación entra; si es el Diplomado, si es de 3 meses o de 1 año, y debe modificarse correctamente. Luego, qué rol ocupará dentro
de la app, para que le salgan sus vistas o interfaces. Debe activarse con diagnóstico o sin diagnóstico: con diagnóstico sale el formulario de arriba,
adaptado; sin diagnóstico sale el formulario que se viene desarrollando.»

**Repos**: backend (`Renaser-90-dias-backend`, donde vive esta spec) y app (`Renaser-90-dias-frontend-`, Administración → Solicitudes).

**Se apoya en**: `022-formaciones` (qué es una formación, su duración y el aislamiento). **Se completa con**: `026-ficha-con-diagnostico` (qué es el diagnóstico y
la ficha) y `027-terminos-editables` (los términos que firma la persona al entrar).

## Contexto

Hoy, cuando alguien pide acceso, Administración ve su solicitud en **Administración → Solicitudes** (nombre, correo, teléfono, ciudad, fecha) y puede **aprobar**,
**rechazar** (con motivo) o **borrar**. Al aprobar se elige el **rol** con unos botones (Aprendiz, Guía de aprendiz, Mentor, Líder de mentores, Administrador,
Alquimista, más los roles personalizados de Soporte TI); Aprendiz es el que viene marcado y aprobar como aprendiz es un solo toque. Al aprobarse, la cuenta se
activa, el aprendiz queda inscrito en el programa con su reloj en pausa, entra al chat global, a su chat de soporte y al grupo de recepción, y la próxima vez que abra la
app pasa por la Ficha inicial, los Términos y «Elegir tu Día 1».

Con las formaciones (spec 022), aceptar a alguien exige **tres decisiones, en este orden**: **a qué formación entra** (y, si es el Diplomado, **de cuánto tiempo**),
**qué rol tendrá**, y **cómo se activa: con diagnóstico o sin diagnóstico**.

## Clarifications

### Session 2026-10-31 (decisiones del dueño, ya tomadas)

- Q: ¿En qué orden se decide al aceptar? → A: **Primero la formación (y la duración si es el Diplomado), luego el rol, luego la ficha.**
- Q: ¿Quién elige la formación? → A: **Administración, al aceptar.** La persona no la elige al pedir acceso.
- Q: ¿Qué duraciones hay? → A: Diplomado 3 meses o 1 año; Escuela y Certificación 90 días (spec 022). La duración se configura en Administración.
- Q: ¿Qué es «con diagnóstico»? → A: La persona completa el **formulario de diagnóstico** (el de Google que el dueño compartió, adaptado). «Sin diagnóstico»: completa el **formulario que se viene desarrollando** (la Ficha inicial de hoy).
- Q: ¿Para qué se elige el rol? → A: «Para que le salgan sus vistas o interfaces.»

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aceptar a un aprendiz eligiendo formación, duración, rol y ficha (Priority: P1)

Administración abre una solicitud, toca **Aceptar** y, en una hoja corta, decide en orden: formación → (duración, si el Diplomado) → rol → ficha. Ve una frase que resume lo
elegido («María entrará al Diplomado de 1 año como Aprendiz, con diagnóstico»), la confirma y la cuenta queda activa con todo asignado.

**Why this priority**: Es el flujo del pedido. Sin él no se puede incorporar a nadie a las nuevas formaciones.

**Independent Test**: Con una solicitud pendiente, aceptarla como aprendiz del Diplomado de 1 año con diagnóstico; iniciar sesión con esa cuenta y comprobar formación, duración, ficha y términos.

**Acceptance Scenarios**:

1. **Given** una solicitud pendiente, **When** Administración toca **Aceptar**, **Then** se abre una hoja con cuatro bloques en orden —**1 Formación**, **2 Rol**, **3 Ficha**, **Resumen**— y solo el
   primero está disponible al empezar.
2. **Given** el bloque de Formación, **When** elige **Diplomado**, **Then** aparecen las duraciones configuradas (**3 meses** · **1 año**) y debe elegir una antes de seguir.
3. **Given** que elige **Escuela** o **Certificación** (una sola duración), **When** la elige, **Then** no se le pregunta la duración: se muestra como dato («90 días») y el bloque Rol se habilita.
4. **Given** que eligió la formación, **When** pasa a **Rol**, **Then** ve los mismos roles y en el mismo orden de hoy (con Aprendiz marcado) y puede elegir otro.
5. **Given** que eligió el rol, **When** pasa a **Ficha**, **Then** elige entre **Con diagnóstico** y **Sin diagnóstico**, cada uno con una línea que explica qué verá la persona.
6. **Given** las tres decisiones tomadas, **When** mira el Resumen, **Then** lee una frase completa y el botón **Aceptar** queda habilitado; antes de eso está apagado y dice qué falta.
7. **Given** que confirma, **When** el servidor responde, **Then** la hoja se cierra, la solicitud sale de «Pendientes» y una confirmación en línea dice «María entró al Diplomado de 1 año como Aprendiz, con diagnóstico».
8. **Given** la cuenta aceptada, **When** la persona inicia sesión, **Then** pasa por **la ficha elegida**, luego los **términos de su formación** y luego «Elegir tu Día 1», que le muestra la duración de su formación (spec 022).

---

### User Story 2 - Aceptar personal (mentor, guía, líder) en una o varias formaciones (Priority: P1)

Cuando el rol elegido es mentor, guía de aprendiz o líder de mentores, el bloque de formación deja marcar **una o más** formaciones (en vez de una sola). Cuando el rol es Administrador o
Alquimista, no se pide formación: ven las tres.

**Why this priority**: Es la otra mitad de la pertenencia definida por el dueño; sin ella el personal no puede entrar a trabajar.

**Independent Test**: Aceptar a una persona como Mentor en Escuela y Diplomado; iniciar sesión y comprobar que elige formación al entrar (spec 022, Historia 6).

**Acceptance Scenarios**:

1. **Given** el rol Mentor, Guía de aprendiz o Líder de mentores, **When** elige formación, **Then** puede marcar una o varias y debe marcar al menos una.
2. **Given** el rol Administrador o Alquimista, **When** llega al bloque de formación, **Then** ve «Ve las tres formaciones» y no tiene que elegir nada.
3. **Given** el rol Aprendiz, **When** elige formación, **Then** solo puede marcar una.
4. **Given** que Administración cambió el rol de Aprendiz a otro después de elegir la formación, **When** vuelve atrás, **Then** el bloque se ajusta al nuevo rol y conserva lo ya marcado cuando sigue siendo válido.
5. **Given** un rol personalizado (los que crea Soporte TI), **When** se elige, **Then** la formación se pide según su rol base.

---

### User Story 3 - Revisar y corregir antes de que la persona entre (Priority: P2)

Si Administración se equivocó en una elección, puede corregirla mientras la persona **no haya empezado**: formación, duración, ficha o rol, desde la ficha de la persona. Después de que la persona haya
empezado, cambiar de formación ya no es una corrección sino un traslado (spec 022, Historia 7).

**Why this priority**: Con tres decisiones aumentan los errores posibles; sin esta salida, un clic equivocado obligaría a borrar y repetir el proceso.

**Independent Test**: Aceptar a alguien en la Escuela, corregirlo al Diplomado de 3 meses antes de que inicie sesión y comprobar que su primer ingreso muestra solo lo del Diplomado.

**Acceptance Scenarios**:

1. **Given** una persona aceptada que aún no inició sesión por primera vez, **When** Administración cambia su formación, **Then** el cambio se aplica y la persona entra a los chats y al grupo de recepción de la nueva formación, y no queda rastro de la elección anterior en lo que ve.
2. **Given** esa persona, **When** Administración cambia **Con diagnóstico** a **Sin diagnóstico**, **Then** su primer ingreso muestra la nueva ficha.
3. **Given** una persona que ya respondió preguntas de su ficha, **When** Administración intenta cambiar el tipo de ficha, **Then** el sistema no lo permite y explica por qué (se mezclarían respuestas de dos fichas).
4. **Given** una persona que ya eligió su Día 1, **When** Administración intenta cambiar su formación o duración, **Then** el sistema se comporta según la spec 022 (traslado) y la pregunta Q2 de esta spec (duración).
5. **Given** cualquiera de estas correcciones, **When** se guarda, **Then** queda registrado quién la hizo, cuándo y qué cambió.

---

### User Story 4 - La solicitud queda con su historia (Priority: P2)

Las solicitudes ya decididas muestran lo que se eligió y quién lo hizo: «Aceptada por Ana el 31 oct · Diplomado 1 año · Aprendiz · con diagnóstico». La ficha de la persona en Administración
muestra la misma información.

**Why this priority**: Con tres decisiones por aceptación, Administración necesita poder revisar qué se decidió sin preguntarle a nadie.

**Independent Test**: Aceptar, abrir la lista de «Aceptadas» y la ficha de la persona y comprobar que ambas dicen lo mismo.

**Acceptance Scenarios**:

1. **Given** una solicitud aceptada, **When** Administración la abre en Solicitudes, **Then** ve formación, duración, rol, tipo de ficha, quién aceptó y cuándo.
2. **Given** una solicitud rechazada, **When** la abre, **Then** ve el motivo como hoy; no muestra formación ni ficha.
3. **Given** la lista de Solicitudes, **When** usa el filtro **Formación**, **Then** ve solo las aceptadas a esa formación (las pendientes no tienen formación todavía y siempre se ven).

---

### User Story 5 - Aceptar a varias personas con la misma elección (Priority: P3)

Al incorporar un grupo entero al Diplomado, Administración puede usar **«La misma que la última»** para precargar formación, duración, rol y ficha con la última elección, ver el resumen y
aceptar con un toque.

**Why this priority**: Comodidad para cuando se incorporan muchas personas seguidas; no es obligatoria para el pedido.

**Independent Test**: Aceptar a dos personas seguidas, la segunda con «La misma que la última», y comprobar que quedan con la misma formación, duración, rol y ficha.

**Acceptance Scenarios**:

1. **Given** que Administración aceptó antes a alguien en esta sesión, **When** abre otra solicitud, **Then** ve la opción «La misma que la última: Diplomado · 1 año · Aprendiz · con diagnóstico».
2. **Given** que la usa, **When** toca la opción, **Then** los tres bloques quedan completos, se muestra el Resumen y todavía debe tocar **Aceptar** (no acepta sola).
3. **Given** que no aceptó a nadie en esta sesión, **When** abre una solicitud, **Then** la opción no aparece y no hay nada preseleccionado.

---

### Edge Cases

- **Solicitud que dos administradores aceptan a la vez**: gana la primera; la segunda ve «Esta solicitud ya la aceptó Ana» y se recarga la lista. Nunca quedan dos cuentas ni dos inscripciones.
- **Falla a mitad de la aceptación** (red, servidor): la hoja conserva lo elegido y ofrece **Reintentar**; el servidor deja la cuenta **entera o sin tocar**, nunca aceptada sin formación o sin ficha. Reintentar no la acepta dos veces.
- **Formación sin recepción configurada**: la aceptación se completa igual; la persona entra al chat global y al de soporte y queda «sin grupo», visible en «Por atender» de su formación (spec 022), como hoy cuando no existe grupo de recepción.
- **Formación o duración que cambió mientras la hoja estaba abierta** (Administración las editó en otro dispositivo): el servidor rechaza con un mensaje claro y la hoja recarga las opciones sin perder lo ya elegido que siga siendo válido.
- **Formación desactivada**: no se ofrece al aceptar; quienes ya están en ella no cambian.
- **Aceptación desde una app anterior** (la publicada antes de esta función, que solo manda el rol): el servidor la acepta como aprendiz de la **Escuela**, con su duración única y **sin diagnóstico** —lo que pasa hoy—; el rol que mande se respeta. Nunca falla por «falta la formación».
- **App nueva contra servidor anterior**: la app sigue aceptando como hoy (solo rol) y no muestra los bloques nuevos.
- **Cuenta de login social** (Google/Apple): igual que una de correo; la formación y la ficha se eligen igual.
- **Rol cambiado después en «Equipo y roles»**: de Aprendiz a personal, la formación del aprendiz pasa a ser su primera formación asignada; de personal con varias formaciones a Aprendiz, Administración elige cuál conserva; de y hacia Administrador/Alquimista, no se pide formación al cambiar (la conservan o dejan de necesitarla).
- **Rechazar o borrar una solicitud**: sigue exactamente igual que hoy (libera el correo, borra la cuenta que nunca se aprobó). No tiene formación ni ficha.
- **Cuenta de personal que no hace programa**: sigue sin inscribirse en el programa; solo recibe sus formaciones.
- **Solicitudes anteriores ya aceptadas** (antes de esta función): se muestran como «Escuela · sin diagnóstico» sin que nadie haya tocado nada.
- **Pantallas pequeñas y letra grande**: la hoja se desliza por dentro (un solo scroll), cada bloque es legible y los botones miden 48 px o más.

## Requirements *(mandatory)*

### Functional Requirements

**Las tres decisiones**

- **FR-001**: Al aceptar una solicitud, el sistema MUST pedir, en este orden: formación (y duración si hace falta), rol y tipo de ficha.
- **FR-002**: El bloque de formación MUST ofrecer solo las formaciones activas, y la duración MUST pedirse solo si la formación tiene más de una opción; con una sola, se aplica y se muestra como dato.
- **FR-003**: Para el rol Aprendiz el sistema MUST exigir **una** formación; para Mentor, Guía de aprendiz y Líder de mentores, **una o más** (al menos una); para Administrador y Alquimista MUST NOT pedirla.
- **FR-004**: Los roles ofrecidos MUST ser los de hoy (los seis y los personalizados activos) en el mismo orden, con Aprendiz marcado por defecto.
- **FR-005**: El bloque de ficha MUST ofrecer **Con diagnóstico** y **Sin diagnóstico**, cada uno con una línea que explica lo que verá la persona. *(Para qué roles: pregunta Q1.)*
- **FR-006**: La aceptación MUST NOT quedar habilitada hasta tener las decisiones completas, y el botón MUST decir qué falta mientras tanto.
- **FR-007**: La aceptación MUST mostrar, antes de confirmar, una frase de resumen con todo lo elegido.

**Lo que ocurre al aceptar**

- **FR-008**: La aceptación MUST ser atómica: o queda la cuenta activa con formación, duración, rol y ficha asignados, o no queda nada cambiado.
- **FR-009**: Una vez aceptada, el aprendiz MUST quedar inscrito en su formación con la duración elegida y su reloj en pausa hasta que elija su Día 1 (como hoy); el personal MUST quedar con sus formaciones.
- **FR-010**: La persona MUST entrar a los espacios de **su** formación: chat global, grupo de recepción y su chat de soporte; nunca a los de otra.
- **FR-011**: En su primer ingreso, la persona MUST pasar por la ficha elegida, luego los términos de su formación y luego «Elegir tu Día 1», que muestra la duración de su formación.
- **FR-012**: Los roles MUST dar a la persona las vistas de siempre de ese rol, dentro de su formación (spec 022).
- **FR-013**: Una aceptación hecha por una app anterior MUST completarse como Escuela, duración única y sin diagnóstico; MUST NOT fallar por falta de la formación.

**Correcciones, historial y concurrencia**

- **FR-014**: Administración MUST poder corregir formación, duración, tipo de ficha y rol de una persona **que todavía no empezó**, y el cambio MUST aplicarse completo (espacios, ficha, términos, duración) sin rastro de la elección anterior en lo que ve la persona.
- **FR-015**: El tipo de ficha MUST NOT poder cambiarse una vez que la persona respondió alguna pregunta; el sistema MUST explicar por qué.
- **FR-016**: La duración de una persona que ya empezó MUST cambiarse solo según la respuesta a la pregunta Q2.
- **FR-017**: El sistema MUST registrar de cada aceptación y corrección quién, cuándo y qué se eligió, y mostrarlo en la solicitud y en la ficha de la persona.
- **FR-018**: Dos aceptaciones simultáneas de la misma solicitud MUST resultar en una sola cuenta aceptada; la otra recibe un aviso claro.
- **FR-019**: Rechazar y borrar solicitudes MUST comportarse exactamente como hoy.
- **FR-020**: Una app nueva contra un servidor anterior MUST seguir aceptando como hoy, sin mostrar los bloques nuevos.

**Experiencia**

- **FR-021**: La aceptación MUST ser una hoja desde abajo con un solo scroll, bloques numerados que se habilitan en orden, áreas táctiles de 48 px o más, textos en tipo oración y tuteo de Perú, y el gesto atrás del sistema cierra la hoja (nunca la app).
- **FR-022**: Al elegir en un bloque, éste MUST colapsar a una línea con lo elegido y poder reabrirse; cambiar un bloque anterior MUST conservar los siguientes mientras sigan siendo válidos.
- **FR-023**: Al confirmar, la app MUST dar una confirmación en línea y vibración de logro; los errores MUST ir en un diálogo con **Reintentar**.
- **FR-024** *(P3)*: La opción «La misma que la última» MUST precargar las tres decisiones sin aceptar por sí sola.

### Key Entities *(include if feature involves data)*

- **Solicitud de cuenta**: la que ya existe (nombre, correo, teléfono, ciudad, estado, motivo de rechazo); suma, una vez aceptada, la elección que se hizo.
- **Elección de aceptación**: formación(es), duración, rol, tipo de ficha, quién y cuándo. Para un aprendiz se materializa en su pertenencia a una formación (spec 022); para el personal, en su asignación.
- **Tipo de ficha**: «con diagnóstico» o «sin diagnóstico»; decide qué formulario ve la persona en su primer ingreso (spec 026).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Aceptar un aprendiz del Diplomado toma **5 toques o menos** desde la lista (Aceptar, formación, duración, ficha y confirmar; el rol ya viene en Aprendiz) y **menos de 30 segundos** para una persona acostumbrada.
- **SC-002**: **0** cuentas aceptadas sin formación (aprendices) ni sin al menos una (personal salvo Administración y Alquimista), ni sin tipo de ficha asignado.
- **SC-003**: El **100 %** de las personas aceptadas ve en su primer ingreso la ficha elegida, los términos de su formación y su Día 1 con la duración correcta.
- **SC-004**: El **100 %** de las aceptaciones hechas con la app publicada antes de esta función entra a la Escuela sin errores.
- **SC-005**: En dos aceptaciones simultáneas de la misma solicitud, queda **exactamente 1** cuenta aceptada.
- **SC-006**: Tras corregir la elección de alguien que no empezó, **0** rastros de la elección anterior aparecen en lo que ve esa persona (chats, grupo, ficha, términos, duración).
- **SC-007**: Una persona aceptada en una formación aparece en **0** listas, grupos ni chats de otra formación (aislamiento de la spec 022, comprobado por E2E).
- **SC-008**: Ante una falla de red a mitad de la aceptación, la cuenta queda **entera o sin tocar** en el **100 %** de las pruebas.

## Fuera de alcance

- **Qué contiene cada ficha y cómo se administra**: spec 026. **Qué dicen los términos y cómo se editan**: spec 027.
- **Que la persona elija su formación al pedir acceso**: el pedido de acceso no cambia.
- **Notificar a la persona de su formación o su ficha por correo o aviso**: no se pide; la persona lo ve al entrar.
- **Aceptar con otro criterio** (por pago, por cupo, por lista de espera): no se pide.
- **Crear formaciones o roles nuevos**: spec 022 y Soporte TI.

## Restricciones del dueño

Reglas del dueño del 2026-10-31 para todas las specs de esta tanda:

1. **Reutilizar lo que ya existe**: la pantalla de Solicitudes, la acción de aprobar, los botones de rol, la hoja desde abajo y el aviso en línea de la app; la inscripción al programa, los espacios de recepción y soporte, y los eventos
   de alta de hoy. Nada de tablas nuevas si una existente sirve con una columna o una relación; lo elegido al aceptar se guarda donde ya se guarda la persona y su inscripción. Datos normalizados y, si algo se desnormaliza, justificado.
2. **No romper la lógica actual**: aceptar como aprendiz de la Escuela sin diagnóstico sigue dando exactamente lo de hoy; la app publicada antes de esta función sigue aceptando (FR-013, SC-004); rechazar y borrar no cambian.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID; el módulo de cuentas no escribe en tablas ajenas, usa las `api/` de los otros) **y el `AGENTS.md` del frontend** (hoja desde abajo, `ChipDeOpcion`, `ConfirmacionEnLinea`,
   `tacto`, un solo scroll, 48 px, tipo oración, tuteo de Perú). Anotar en `AGENTS.md` la autorización de este cambio con fecha, alcance y «lo que cambia y nada más».
4. **Aislamiento total entre formaciones**: al aceptar, la persona entra solo a los espacios de su formación (FR-010, SC-007). Criterio E2E: «una persona aceptada en el Diplomado no aparece en ninguna lista, grupo ni chat de la Escuela, ni ve nada de ella».
5. **El dueño hace él mismo todas las preguntas antes de implementar**: la sección «Dudas para el dueño» las lista todas.

## Assumptions

- **Nada preseleccionado** en formación ni en ficha: obligar a elegir evita aceptar a alguien en la formación equivocada por un toque de costumbre. Solo el rol viene marcado (Aprendiz), como hoy.
- **El tipo de ficha se pide a todos los roles que hoy pasan por la ficha** salvo que el dueño responda Q1 (donde se recomienda limitar el diagnóstico a los aprendices).
- **Administrador, Alquimista y Soporte TI** no se asignan a formaciones: ven las tres (spec 022). Soporte TI no se acepta por solicitud.
- **Un aprendiz sin Día 1 elegido** puede corregirse libremente; con Día 1, es traslado (spec 022).
- **Cambiar el tipo de ficha** se permite hasta que la persona responda la primera pregunta; antes de eso no hay nada que mezclar.
- **La frase de resumen y los textos de ayuda** son de la app (no los escribe el servidor); los nombres de formaciones y duraciones sí vienen del servidor (spec 022).
- **El orden de los roles** en la hoja es el de hoy, para que Administración no tenga que reaprenderlo.
- **El chat global, el grupo de recepción y el chat de soporte de cada formación** existen o se crean al configurar la formación (spec 022).
- **Dependencias**: spec 022 (formaciones y aislamiento), spec 026 (las dos fichas) y spec 027 (términos por formación) para que el primer ingreso sea completo; sin ellas, esta spec se prueba con las fichas y términos actuales.

## Dudas para el dueño

Todas las dudas reales, aunque ya tengan supuesto. Las marcadas **Sí** cambian el alcance.

| # | Duda | Opciones (la recomendada primero) | ¿Cambia el alcance? |
|---|---|---|---|
| Q1 | [NEEDS CLARIFICATION: ¿«con diagnóstico» se puede asignar a cualquier rol o solo a aprendices?] | A) Solo aprendices: el personal siempre completa la ficha estándar. B) Cualquier rol que pase por la ficha. | **Sí** (quién ve el diagnóstico y si el personal recibe objetivos desde él) |
| Q2 | [NEEDS CLARIFICATION: «debe modificarse correctamente»: ¿se puede cambiar la duración de una persona **que ya empezó** (por ejemplo de 3 meses a 1 año)?] | A) Sí, Administración la cambia con motivo escrito: el día actual se conserva, el último día se mueve, y queda en el historial. B) No: solo antes de empezar; después, traslado. | **Sí** |
| Q3 | ¿Se quiere aceptar a varias personas a la vez (por ejemplo, 30 del Diplomado)? Hoy se propone solo «La misma que la última» (P3). | A) Solo «La misma que la última». B) Selección múltiple de solicitudes y una sola elección para todas. C) Ninguna de las dos. | Sí, si B (suma una historia) |
| Q4 | ¿Se preselecciona algo (formación, ficha) para ahorrar toques? | A) Nada, solo el rol Aprendiz (recomendada: evita errores). B) Escuela y «sin diagnóstico». | No |
| Q5 | ¿Hasta cuándo se puede cambiar el tipo de ficha de una persona? | A) Hasta que responda su primera pregunta. B) Hasta que termine la ficha. C) Nunca. | No |
| Q6 | Si el personal acepta varias formaciones, ¿alguna debe quedar como «principal» (la que se abre primero)? | A) Ninguna: la primera vez elige y después se recuerda la última (spec 022). B) Administración marca una principal. | No |
| Q7 | La persona aceptada, ¿recibe algún aviso (correo o notificación) con su formación y ficha? | A) No; lo ve al entrar (como hoy). B) Un aviso de bienvenida con el nombre de su formación. | No (sumaría un aviso) |
| Q8 | Si una formación no tiene grupo de recepción, ¿se acepta igual? | A) Sí; la persona queda sin grupo y Administración lo ve en «Por atender» (como hoy). B) No se deja aceptar hasta configurarlo. | No |
