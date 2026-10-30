# Feature Specification: Mi Radar en Yo, desde el día 8

**Feature Branch**: `012-mi-radar-en-yo` (backend y app)
**Created**: 2026-10-29
**Status**: Draft
**Input**: Pedido del dueño (2026-10-29), sobre el punto 9 del encargo de gerencia («Radar dentro de Yo») y la vista
previa https://claude.ai/artifact/VCFE5EYjVa6BCWv3iz7ef7: «el radar son los primeros 7 días y se activa… ahora la
persona puede activarlo individualmente y que le aparezca». Es la primera pieza del encargo de gerencia que pasa a
spec. El resto (Hoy breve, Preparar mañana, bloques, cierre del día…) va en specs aparte.

> **Corregido 2026-10-30 (spec 019, decisión del dueño).** Todo lo de **elegir las horas a mano** de esta spec (las horas en punto de 03:00 a
> 23:00, «hasta 12 por día», `ChipDeOpcion`, horas propias por día, «Puedes elegir hasta 12», «Elige al menos una hora») y el tope de 12
> **dejó de ser cierto y nunca llegó a producción**. Mi Radar desde el día 8 es **Automático** (cada hora entre el Despertar y el Dormir que
> la persona programó, sin tope) o **Personalizado** (elige cuántas veces por día; las horas se reparten parejo), por día de la semana, y
> un día sin Despertar o sin Dormir no tiene Radar. Hoy y lo pasado no se editan. Lo demás (interruptor opcional, la invitación del día 8,
> los días 1 a 7, quién lo ve, un registro por hora) sigue igual. Ver `specs/019-radar-automatico/`.

## Clarifications

### Session 2026-10-29 (respuestas del dueño)

- **Días 1 a 7: igual que hoy, sin cambios.** Se abre solo cada hora, de 08:00 a 19:00. El aprendiz no lo puede
  cerrar y el personal sí. Pedido: «confirma que la primera parte ya funciona correctamente» (ver «Lo que hay hoy»).
- **Desde el día 8 queda apagado y la persona lo enciende** en Yo → Mi Radar.
- **Elige sus horarios**: horas en punto «desde las 3:00 hasta las 23:00 del mismo día, nada después de la
  medianoche». Hasta 12 por día, las mismas todos los días. «No nos compliquemos» con la hora de despertar o de
  dormir: no se piden.
  > **Corregido 2026-10-30.** Ya no se eligen horas ni hay tope de 12: salen del Despertar y el Dormir (spec 019).
- **Cuando llega una hora elegida**: aviso del teléfono y entrada en Yo, «debajo de soporte, ayuda o emergencia».
  «No sobrecargar el inicio»: desde el día 8, Hoy no muestra nada del Radar.
- **Las preguntas son las de hoy**: ¿Qué hago?, ¿Qué pienso?, ¿Qué siento?, ¿Qué evito? y la energía de 1 a 10.
- **Yo → Mi Radar tiene lo que pide gerencia** y lo que muestra la vista previa: registrar, historial, patrones y
  horarios.
- **Lo pueden encender todos los roles**, porque todos pueden hacer el programa de 90 días.
- **Quién lo ve**: la persona, su mentor (que ya lo ve hoy, ahora también desde el día 8), el líder de mentores, el
  administrador y el alquimista, en la ficha de la persona. El dueño se apoya en la Política de Privacidad, que dice
  «tus datos los ven solo las personas que los necesitan: tu mentor, quienes coordinan tu grupo y el equipo de
  administración». Esto **cambia lo que pedía gerencia** («los registros son privados»): hay que avisarle.
- **Invitación al terminar la semana 1**: un aviso único el día 8 que lleva a Yo → Mi Radar.
- **Queda para otra spec** (pedido del dueño: «con otro spec la misma ficha pero para los mentores»): la ficha de un
  mentor o de otra persona del equipo que hace su propio programa, para que la vean el líder de mentores, el
  administrador y el alquimista.

### Session 2026-10-29, segunda ronda (respuestas del dueño)

- **Plazo para responder una hora elegida: hasta el final de esa hora** (la de las 14:00, hasta las 14:59), como en
  la semana 1. Además: «la validación tanto backend y frontend» y «autocalcular los recordatorios». Las reglas de las
  horas se validan en la app y en el servidor, y los avisos salen solos de lo guardado.
- **Es opcional**: «si no llena el día 8 puede llenar al día siguiente, no es algo que sí o sí debe hacer». No
  encenderlo nunca cuenta como falta, y se puede encender cualquier día desde el día 8.
- **Con Mi Radar apagado no se registra**: «que le salga un interruptor al entrar para activar o desactivar». Lo
  primero de Mi Radar es ese interruptor. Para registrar, hay que encenderlo.
- **La fila de Yo aparece desde el día 8**: «la opción en Yo sale a partir del día 7, los demás días no sale». En los
  días 1 a 7 no hay fila: el Radar se abre solo, como hoy.
- **Después del día 90 sigue como la persona lo dejó.**

### Session 2026-10-29, tercera ronda (respuestas del dueño)

- **Día 8**, confirmado.
- **Por qué no es privado**: «se tiene que analizar a los demás roles de cómo va». El acompañamiento lee el Radar
  para ver cómo va la persona. Es lo que se le explica a gerencia.
- **Cómo construirlo**: «no hacer sobreingeniería… no crear una nueva tabla sino reutilizar, aplicar SOLID y buenas
  prácticas». Por eso se quitó el registro de consulta propio del Radar (ver FR-015) y la invitación del día 8 vive en
  el teléfono.
- **La spec siguiente** (la ficha del personal que hace su programa) sigue la cadena: al mentor lo ve el líder de
  mentores, al líder lo ven el administrador y el alquimista, y estos dos lo ven todo.

### Session 2026-10-29, cuarta ronda (respuestas del dueño, después de ver las capturas)

- **Mi Radar por día, igual que los hábitos en Planificar**: «no harán la acción todos los días, es molestoso… igual
  que los hábitos, pueden ser diferentes». Hay horas para todos los días y, marcando días, horas propias para esos
  días o «Sin Radar ese día».
  > **Corregido 2026-10-29.** Decía «Hasta 12 por día, las mismas todos los días» (primera ronda). Sigue el tope de
  > 12 por día.
- Un día sin Radar se comporta como Mi Radar apagado ese día: no suena ni se registra.
- «Recuerda la experiencia de uso para hacerlo más fácil, en todos los aspectos.» Se usa la misma forma que Planificar
  (la fila de los siete días, «Todos los días» o «Solo sáb, dom», «Guardar horas · sáb», «Dejar sáb como los demás
  días»), que la persona ya conoce.

### Lo que hay hoy (verificado en el código el 2026-10-29)

- **App** (`features/radar/config/configRadar.ts`): días 1 a 7, 12 horas en punto de 08:00 a 19:00, para todas las
  cuentas. Es obligatorio para `TRAINEE`/`APRENDIZ`, el personal lo puede cerrar y se apaga el día 8. Suenan 12
  avisos diarios en el teléfono (`recordatoriosDeRadar.ts`), que se cancelan al salir de la ventana. Se corrigieron
  el parpadeo y el 409 (AGENTS.md, 2026-10-19). Se registra con `POST /api/v1/radar`, se lee la hora con
  `GET /radar/latest` y la app no usa `GET /radar/history`.
- **Servidor** (`RadarService`): un registro por hora (índice `radar_un_registro_por_hora`, V52) y ningún límite de
  día: el corte del día 8 lo pone la app. Puede registrar el aprendiz y el personal con el programa activado (E-169).
  El Radar no da puntos ni cuenta en el semáforo. Los logros cuentan cuántos registros hay.
- **Quién lo lee hoy**: solo la persona y el acompañante vigente de su grupo
  (`GET /mentor/groups/{g}/learners/{u}/radar`, página de 84 = días 1 a 7). Ni el líder ni Administración lo ven.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Encender Mi Radar con mis horarios (Priority: P1)

Como persona que hace los 90 días y ya pasó la primera semana, quiero encender el Radar en Yo → Mi Radar y elegir a
qué horas me pregunta, para seguir viendo cómo vivo mi día sin que me interrumpa cada hora.

**Why this priority**: es el pedido. Sin esto, el Radar se termina el día 7 para siempre.

**Independent Test**: en el día 10, encender Mi Radar con 09:00, 14:00 y 20:00 y guardar. El teléfono avisa a esas
tres horas y a ninguna otra, y la fila de Yo dice «Encendido · 09:00, 14:00 y 20:00».

**Acceptance Scenarios**:

1. **Given** el día 8 o después y Mi Radar apagado, **When** entro, **Then** lo primero es el interruptor «Mi
   Radar», apagado, con una línea que dice qué hace. No hay «Registrar» ni horarios.
2. **Given** el interruptor, **When** lo enciendo, **Then** aparecen los horarios con 09:00, 14:00 y 20:00 ya
   marcadas, que puedo cambiar antes de guardar.
3. **Given** el selector de horas, **When** lo miro, **Then** solo ofrece horas en punto de 03:00 a 23:00 del mismo
   día. No ofrece medianoche ni madrugada después de las 00:00.
4. **Given** 12 horas marcadas, **When** quiero marcar otra, **Then** no se puede y se lee «Puedes elegir hasta 12».
5. **Given** ninguna hora marcada, **When** intento guardar encendido, **Then** no se guarda y se lee «Elige al menos
   una hora».
6. **Given** horas que no cumplen las reglas (fuera de 03:00–23:00, sin ser en punto, repetidas o más de 12),
   **When** llegan al servidor por cualquier camino, **Then** el servidor las rechaza: la app valida antes y el
   servidor vuelve a validar.
7. **Given** Mi Radar encendido, **When** lo apago, **Then** dejan de sonar los avisos y el historial sigue ahí.
8. **Given** los días 1 a 7, **When** abro Yo, **Then** no hay fila «Mi Radar»: el Código Renaser se abre solo, como
   hoy.
9. **Given** que no lo enciendo el día 8, **When** pasan los días, **Then** lo puedo encender cualquier día, y no
   encenderlo nunca cuenta como falta.
10. **Given** que cambio de teléfono o entro desde la web, **When** abro Mi Radar, **Then** veo los mismos horarios:
   la elección es de la cuenta, no del teléfono.

---

### User Story 2 - Responder cuando toca, sin cargar Hoy (Priority: P1)

Como persona con Mi Radar encendido, quiero que el teléfono me avise a mis horas y que el aviso me lleve directo a
las preguntas, sin que Hoy se llene de tarjetas.

**Why this priority**: sin el aviso no se responde. El dueño pidió no sobrecargar el inicio.

**Independent Test**: a las 14:00 suena el aviso. Tocarlo abre el formulario de siempre. Al registrar, Mi Radar
muestra «Prevista 14:00 · respondida 14:06» y Hoy no cambió.

**Acceptance Scenarios**:

1. **Given** una hora elegida, **When** llega, **Then** suena un aviso «Código Renaser · ¿Dónde estás ahora mismo?»
   como los de la semana 1, que al tocarlo abre el formulario con las cinco preguntas de hoy.
2. **Given** el día 8 o después, **When** abro la app a una hora elegida, **Then** el formulario no se abre solo y
   Hoy no muestra la tarjeta del Código Renaser.
3. **Given** el aviso de las 14:00, **When** respondo entre las 14:00 y las 14:59, **Then** el registro guarda la hora
   prevista (14:00) y la hora real. Desde las 15:00, esa hora queda «Sin registro», como en la semana 1. La hora
   prevista la calcula el servidor con los horarios guardados, no la manda la app.
4. **Given** Mi Radar encendido, **When** toco «Registrar ahora» en una hora que no elegí (entre 03:00 y 23:59),
   **Then** se guarda sin hora prevista y el historial dice solo la hora en que respondí.
5. **Given** Mi Radar apagado, desde el día 8, **When** intento registrar por cualquier camino, **Then** no se puede:
   la app no ofrece «Registrar» y el servidor lo rechaza.
6. **Given** que ya registré esta hora, **When** intento registrar otra vez en la misma hora, **Then** se lee «Ya
   registraste esta hora», como hoy.
7. **Given** una hora elegida sin respuesta, **When** miro Mi Radar, **Then** dice «Sin registro», en gris, sin
   palabras de falta ni de culpa.

---

### User Story 3 - Ver mi historial y mis patrones (Priority: P2)

Como persona que usa el Radar, quiero ver lo que respondí y los patrones que salen de mis respuestas, con cuántos
registros los sostienen, para entender mi energía y lo que evito.

**Why this priority**: es lo que gerencia pide además de registrar. Sin historial, el registro no le sirve a la
persona.

**Independent Test**: con 6 registros, Mi Radar lista los de hoy y los días anteriores, y en Patrones dice la hora de
más energía «en N registros».

**Acceptance Scenarios**:

1. **Given** Mi Radar, **When** lo abro, **Then** veo «Hoy»: cada hora elegida que ya pasó, con su registro o «Sin
   registro», y los registros libres de hoy.
2. **Given** días anteriores con registros, **When** bajo, **Then** los veo agrupados por día, del más nuevo al más
   viejo, y puedo cargar más. Cada registro muestra la energía y lo que sentí, y al tocarlo, las cuatro respuestas.
3. **Given** menos de 3 registros, **When** miro Patrones, **Then** dice «Con N registros todavía no hay patrones.
   Aparecen desde 3».
4. **Given** 3 registros o más, **When** miro Patrones, **Then** veo la hora con más energía promedio y la de menos,
   cada una con «en N registros», y en cuántos de mis registros dije que evitaba algo, con el último ejemplo.
5. **Given** un registro de los días 1 a 7, **When** miro el historial, **Then** también está: es el mismo Radar.

---

### User Story 4 - Que mi acompañamiento lo vea (Priority: P2)

Como mentor, líder de mentores, administrador o alquimista, quiero ver en la ficha de la persona si tiene Mi Radar
encendido, a qué horas y lo que registró, también desde el día 8.

**Why this priority**: el dueño lo pidió y lo respalda la Política de Privacidad. Va después de US1 a US3 porque
primero tiene que haber registros.

**Independent Test**: el mentor abre la ficha de su aprendiz y lee «Mi Radar: encendido · 09:00, 14:00 y 20:00»
con los registros del día 10. Un mentor de otro grupo recibe un rechazo.

**Acceptance Scenarios**:

1. **Given** el mentor o la guía vigente del grupo, **When** abre la ficha de su aprendiz, **Then** la sección del
   Código Renaser muestra el estado de Mi Radar, sus horas y todos los registros (de los días 1 a 7 y los de
   después), paginados.
2. **Given** el líder de mentores, **When** abre a una persona desde «Semáforo por grupos» (spec 005), **Then** ve la
   misma sección.
3. **Given** un administrador o un alquimista, **When** abre Administración → ficha del aprendiz, **Then** ve la
   misma sección.
4. **Given** cualquiera de ellos, **When** mira, **Then** solo lee: nadie más que la persona registra, cambia,
   enciende, apaga ni borra.
5. **Given** otro aprendiz, un mentor de otro grupo o Soporte TI, **When** intenta leerlo, **Then** el servidor lo
   rechaza.
6. **Given** la persona, **When** abre Mi Radar, **Then** se lee quién puede verlo: «Lo ves tú, tu mentor y el equipo
   que coordina tu grupo». Nunca dice «solo tú».

---

### User Story 5 - La invitación al terminar la semana 1 (Priority: P3)

Como persona que terminó el día 7, quiero un aviso que me cuente que puedo seguir con el Radar, para no tener que
descubrirlo sola.

**Why this priority**: sin el aviso, la persona igual lo encuentra en Yo.

**Independent Test**: una cuenta que llega al día 8 recibe un solo aviso. Tocarlo abre Yo → Mi Radar. El día 9 no
llega otro.

**Acceptance Scenarios**:

1. **Given** el día 8 de una persona con Mi Radar apagado, **When** llega la mañana, **Then** recibe una vez
   «Terminaste tu primera semana del Código Renaser. Si quieres seguir, enciende Mi Radar».
2. **Given** que toca el aviso, **When** se abre la app, **Then** está en Yo → Mi Radar.
3. **Given** que ya lo encendió, o que ya recibió la invitación, **When** pasan los días, **Then** no recibe otra.
4. **Given** que no le hizo caso a la invitación, **When** entra a Yo otro día, **Then** la fila «Mi Radar» sigue ahí
   y lo puede encender. No se insiste con más avisos.

### Edge Cases

- **La hora de un registro**: siempre es la del día de la persona, en su zona. Como no hay horas después de las
  23:00, ningún registro cae en el día siguiente.
- **Cambiar las horas** rige en el acto. Si quito la hora que está corriendo, esa hora deja de esperar respuesta. Lo
  que ya registré no cambia.
- **Cambio de zona horaria** (viaje): las horas siguen siendo las mismas en la hora local del teléfono, como los
  hábitos.
- **Cuenta suspendida**: no suenan avisos y el servidor rechaza registrar, como hoy.
- **Pausa del programa**: Mi Radar sigue como la persona lo dejó, porque lo encendió por su cuenta. Los registros no
  cuentan para nada que la pausa congele.
- **Después del día 90**: Mi Radar sigue como la persona lo dejó. Si estaba encendido, sigue sonando hasta que lo
  apague. El historial queda.
- **Persona que ya pasó el día 8 cuando sale esta versión**: no recibe la invitación por aviso, porque llegaría a
  todos a la vez. Ve la fila «Mi Radar · Apagado» en Yo.
- **Fuera de 03:00–23:59** (por ejemplo, a la 01:00): no se registra ni suena nada, aunque Mi Radar esté encendido.
- **Web**: se pueden elegir horarios, registrar y ver el historial, pero no suenan avisos. Mi Radar lo dice en la web.
- **Teléfono que borró las alarmas** (spec 007): al abrir la app, los avisos de Mi Radar vuelven, como los de los
  hábitos.
- **SER**: si la persona registra el Radar hablando con SER, cuenta igual que el formulario y sigue las mismas reglas
  (un registro por hora y, desde el día 8, solo con Mi Radar encendido). SER no enciende ni apaga Mi Radar.
- **Sin red al registrar**: no se pierde lo escrito (borrador de siempre) y se puede reintentar.
- **Puntos, racha, semáforo, coherencia**: el Radar no los toca, ni antes ni después del día 8.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: En los días 1 a 7, el Código Renaser MUST seguir exactamente como hoy: 12 horas de 08:00 a 19:00, se
  abre solo, obligatorio para el aprendiz y se puede cerrar para el personal, con los mismos avisos y la misma
  tarjeta en Hoy.
- **FR-002**: Desde el día 8, el Radar MUST quedar apagado hasta que la persona lo encienda en Yo → Mi Radar.
- **FR-003**: Mi Radar MUST permitir elegir de 1 a 12 horas en punto, entre 03:00 y 23:00, para todos los días y, por
  día de la semana, horas propias (de 1 a 12) o «Sin Radar ese día». Un día sin horas propias sigue las de todos los
  días. Al encenderlo por primera vez, MUST proponer 09:00, 14:00 y 20:00 para todos los días.
- **FR-004**: El estado (encendido o apagado) y las horas MUST guardarse en la cuenta: valen en cualquier teléfono,
  en la web, y los lee el acompañamiento.
- **FR-005**: En el teléfono, cada hora elegida MUST sonar con un aviso del Código Renaser que abre el formulario. Con
  Mi Radar apagado, desde el día 8, MUST NOT sonar ninguno. Los avisos MUST calcularse solos con lo guardado en la
  cuenta: al guardar, al abrir la app y al entrar en otro teléfono. La persona no los programa uno por uno.
- **FR-005a**: Las reglas de las horas (de 1 a 12, en punto, entre 03:00 y 23:00, sin repetir) MUST validarse en la
  app antes de guardar y otra vez en el servidor, que rechaza lo que no las cumpla.
- **FR-006**: Desde el día 8, Hoy MUST NOT mostrar la tarjeta ni abrir solo el formulario del Radar.
- **FR-007**: El formulario MUST ser el de hoy: ¿Qué hago?, ¿Qué pienso?, ¿Qué siento?, ¿Qué evito? y la energía de 1
  a 10, con sus validaciones, su borrador y la regla de un registro por hora.
- **FR-008**: Cada registro MUST guardar si respondía a una hora prevista, cuál, y la hora real. Un registro sin hora
  prevista se muestra solo con la hora en que se respondió. La hora prevista MUST calcularla el servidor: es la hora elegida en curso si el registro se
  hace dentro de esa hora.
- **FR-008a**: Desde el día 8 MUST poder registrarse solo con Mi Radar encendido y entre 03:00 y 23:59. La app no lo
  ofrece fuera de eso y el servidor lo rechaza.
- **FR-009**: Desde el día 8, Yo MUST tener la fila «Mi Radar», debajo de «Tuve una emergencia», con su estado en una
  línea («Encendido · 09:00, 14:00 y 20:00» o «Apagado»). En los días 1 a 7 MUST NOT aparecer.
- **FR-010**: Mi Radar MUST empezar con el interruptor para encenderlo o apagarlo. Encendido, MUST mostrar además
  registrar, hoy (horas previstas con su registro o «Sin registro») y los horarios. Siempre MUST mostrar el historial
  por día y paginado, y los patrones.
- **FR-010a**: Encender Mi Radar MUST ser opcional: se puede hacer cualquier día desde el día 8, y no encenderlo
  nunca MUST NOT contar como falta.
- **FR-011**: Los patrones MUST salir solo de registros existentes. Cada uno MUST decir en cuántos registros se apoya,
  y con menos de 3 registros no se muestra ninguno.
- **FR-012**: «Sin registro» MUST NOT contar como falta en ningún lugar: puntos, racha, semáforo, coherencia,
  cumplimiento ni mensajes.
- **FR-013**: El mentor o la guía vigente, el líder de mentores, el administrador y el alquimista MUST poder leer, en
  la ficha de la persona, el estado de Mi Radar, sus horas y todos sus registros. Nadie más que la persona MUST poder
  escribir, cambiar o borrar.
- **FR-014**: Cualquier otro rol o persona MUST recibir un rechazo del servidor al intentar leer el Radar de otra
  persona.
- **FR-015**: *(Quitado el 2026-10-29.)* Decía que cada lectura del Radar de otra persona dejaba su propio registro de
  consulta. Eso pedía una tabla nueva, o mezclar las lecturas del Radar con el resumen «Tu líder revisó a tu grupo»
  de la spec 005, y el dueño pidió no crear tablas ni sobreingeniería. La ficha del líder ya registra la consulta al
  abrirse (spec 005, FR-016).
- **FR-016**: Mi Radar MUST decir quién puede verlo, de acuerdo con FR-013.
- **FR-017**: El día 8 de cada persona, a las 09:00, MUST llegar una sola vez la invitación, solo si Mi Radar está
  apagado. Tocarla abre Yo → Mi Radar. La programa el teléfono durante los días 1 a 7.
- **FR-018**: Todo lo nuevo MUST ser compatible: la app nueva con un backend anterior no muestra la fila «Mi Radar» y
  los días 1 a 7 siguen iguales; el APK publicado con el backend nuevo sigue funcionando como hoy.
- **FR-019**: Lo pueden encender todas las cuentas que hacen su programa: aprendiz y personal con su Día 1 elegido.

### Key Entities

- **Configuración de Mi Radar** (una por persona): encendido o apagado, horas elegidas (de 1 a 12, en punto, de 03 a
  23). Apagado no guarda horas. Sigue después del día 90. Va en la fila del programa de la persona, sin tabla nueva.
- **Registro del Radar** (ya existe): las cuatro respuestas, la energía y la hora real. Suma la hora prevista, que
  puede faltar.
- **Invitación del día 8**: un aviso que el teléfono programa una sola vez por cuenta, sin servidor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En los días 1 a 7, el E2E de hoy del Código Renaser pasa sin cambios: 12 horas, se abre solo,
  obligatorio para el aprendiz, sin parpadeo y con el 409 resuelto.
- **SC-002**: Una persona en el día 8 o después enciende Mi Radar con sus horas en menos de 1 minuto desde Yo.
- **SC-003**: En el emulador, el 100 % de los avisos suena a las horas elegidas, ninguno fuera de 03:00–23:00 y
  ninguno con Mi Radar apagado.
- **SC-004**: Hoy, desde el día 8, se ve igual con Mi Radar encendido o apagado.
- **SC-005**: Puntos, racha, semáforo y coherencia dan lo mismo con y sin registros del Radar.
- **SC-006**: Los cuatro roles con acceso leen el Radar desde la ficha. Los demás intentos (otro aprendiz, otro
  mentor, Soporte TI, cuenta suspendida) se rechazan el 100 % de las veces.
- **SC-007**: Cada patrón visible dice en cuántos registros se apoya, y con menos de 3 registros no se muestra ninguno.
- **SC-008**: El 100 % de los intentos de guardar horas fuera de las reglas, o de registrar con Mi Radar apagado desde
  el día 8, se rechazan en el servidor aunque se salte la app.

## Assumptions

- **Autorización de los tabs** (constitución VI): el pedido del dueño del 2026-10-29 autoriza la fila nueva en Yo, la
  pantalla Mi Radar y la sección del Radar en las fichas del mentor, del líder y de Administración. Hoy no cambia.
  Se anota en `AGENTS.md` con «lo que cambia y nada más».
- Es la misma funcionalidad que la semana 1: mismo formulario, mismos avisos (tipo despertador con permiso, spec
  007) y misma regla de un registro por hora.
- Los patrones (hora con más y con menos energía, y cuántas veces evitas algo) son los de la vista previa de
  gerencia. Los ve solo la persona; el acompañamiento ve el estado y los registros.
- El texto de la invitación y de la fila son propuestas. El dueño los puede cambiar sin tocar el comportamiento.
- Se le avisa a gerencia que el Radar no es privado: la persona lo ve, junto con su acompañamiento.
- La ficha del personal que hace su programa (para que el líder, el administrador y el alquimista lo vean) va en la
  spec siguiente. Hasta entonces, el Radar de un mentor solo lo ve él.
- Hace falta backend nuevo (la configuración, la hora prevista, los permisos de lectura y la invitación) y un APK
  nuevo. El backend se publica primero.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.
