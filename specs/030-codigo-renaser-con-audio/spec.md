# Feature Specification: Código Renaser con la voz

**Feature Branch**: `030-codigo-renaser-con-audio` (backend y app; el cambio es casi todo de la app)
**Created**: 2026-10-31
**Status**: Aclarada (decisiones del dueño del 2026-10-31); lista para planificar e implementar
**Input**: Pedido del dueño (2026-10-31), textual: «Tarea: Código Renaser. El sistema debe poder ofrecer el servicio de audios
al responder el radar.» Es la primera mitad de la tarea «Código Renaser»; la segunda mitad (el análisis 80/20 con IA) es la
spec 031. La app pasa a tener 3 formaciones (spec 022): todo lo de esta spec queda dentro de la formación de la persona.

## Clarifications

### Session 2026-10-31

Decisiones del dueño (`~/.config/renaser-e2e/formaciones/decisiones-del-dueno-2026-10-31.md`, sección 030):

- **030-1 = A** → «el servicio de audios» es **dictar la respuesta** (voz a texto) y **escuchar la pregunta** con la voz Dora, grabada y sin
  red. La energía se toca, no se dicta. No se guarda audio, no hay nota de voz, no hay SER por voz, no se ofrece un audio del programa.
- **030-2 = B** → **el registro dice «dictado»**, y lo ven el **mentor (o guía), el líder y Administración** (Administración y Alquimista, igual
  que hoy ven el Radar). Cambia el registro: una columna nueva, sin tablas nuevas. Se respeta el interruptor de la **spec 034**: el
  «dictado» viaja dentro de las mismas filas del Código Renaser, así que si el interruptor oculta el Código Renaser a mentor/líder/Admin,
  tampoco ven «dictado». Esta spec no agrega ninguna ruta de lectura ni ningún permiso.
- Cómo se decide el «dictado» (decisión de implementación, anotada para que el dueño la corrija si no es lo que quiso): es **un solo dato
  por registro**, `true` si **al menos una** de las cuatro respuestas llevó algo dictado con el micrófono y esa parte sigue en el texto que
  se registra. Lo declara la app al registrar (el servidor no oye audio ni puede comprobarlo); si la persona borra por completo un campo
  dictado, ese campo deja de contar. Un registro hecho por otro camino (p. ej. por SER) es `false`.

## Qué hay hoy (verificado en el código y en `AGENTS.md` el 2026-10-31)

- **El formulario** (Código Renaser, también llamado «el Radar» cuando se responde desde Mi Radar) tiene cuatro preguntas de
  texto libre —¿Qué hago?, ¿Qué pienso?, ¿Qué siento?, ¿Qué evito?—, de hasta 2 000 caracteres cada una, y la energía de 1 a 10.
  Las cuatro de texto son obligatorias.
- **Dónde se responde**: en los días 1 a 7, cada hora de 08:00 a 19:00 y de forma obligatoria para el aprendiz (pantalla
  completa, sin cerrar); desde el día 8, en Mi Radar y en la mitad del Radar de Hoy, de forma opcional y a las horas que salen
  del Despertar y el Dormir de la persona (spec 019); y al tocar el aviso del teléfono. Es el mismo formulario en todos lados.
- **Un registro por hora**: el segundo en la misma hora se rechaza y la app lo da por registrado. El Radar no da puntos ni
  cuenta en el semáforo. Lo ven la persona y su acompañamiento (spec 012).
- **La voz ya existe en la app, pero no en el Radar**: el chat de SER tiene un micrófono de dictado (la persona habla, el texto
  se suma al campo, ella lo revisa y lo envía; nunca se envía solo) con el reconocimiento de voz del teléfono, y **el audio no
  se guarda ni se sube**. SER tiene además su voz en vivo, y la app tiene las voces grabadas de las alarmas (voz «Dora»). El
  formulario del Radar no tiene micrófono ni lee sus preguntas.

## Alcance

Que la persona pueda **responder el Código Renaser hablando** y **escuchar cada pregunta**, en el mismo formulario y en todos
los lugares donde hoy se abre. **Lo que cambia y nada más:** se suma un micrófono a cada pregunta de texto y un botón para
escuchar la pregunta. No cambian las preguntas, los horarios, la obligatoriedad, el contrato del registro, quién lo ve, el
semáforo, los puntos, Mi Radar ni el resto de la app.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Responder una pregunta hablando (Priority: P1)

Como persona que tiene el formulario del Código Renaser delante, quiero tocar un micrófono en una pregunta y decir mi respuesta,
para no tener que escribir con el teclado, sobre todo cuando estoy cansada, en movimiento o escribo despacio.

**Why this priority**: es el pedido. Sin esto no hay «servicio de audios» al responder. Además, en los días 1 a 7 el aprendiz
responde 12 veces al día: escribir cuatro textos cada hora es la mayor fricción del programa en su primera semana.

**Independent Test**: abrir el formulario, tocar el micrófono de «¿Qué siento?», decir una frase, ver el texto en el campo,
corregir una palabra y tocar «Registrar». El registro queda igual que si se hubiera escrito.

**Acceptance Scenarios**:

1. **Given** el formulario abierto y un teléfono con reconocimiento de voz, **When** toco el micrófono de una pregunta y hablo,
   **Then** veo aparecer lo que voy diciendo en ese campo y, al terminar, queda como texto que puedo editar.
2. **Given** que ya había escrito algo en el campo, **When** dicto, **Then** lo dictado se suma al final y no borra lo que había.
3. **Given** un micrófono abierto en una pregunta, **When** toco el micrófono de otra, **Then** el primero se cierra y se abre el
   segundo: nunca hay dos abiertos.
4. **Given** que terminé de dictar las cuatro respuestas, **When** no toco nada más, **Then** no se registra nada: solo se
   registra al tocar «Registrar», con las mismas reglas de siempre (las cuatro respuestas y la energía).
5. **Given** que dicto más de 2 000 caracteres en un campo, **When** llega al límite, **Then** se corta ahí y se me avisa igual que
   si estuviera escribiendo.
6. **Given** los días 1 a 7 (formulario obligatorio, sin cerrar), **When** dicto, **Then** nada de la obligatoriedad cambia: sigo
   sin poder cerrarlo salvo registrando.
7. **Given** que empecé a responder y cerré el formulario (el personal puede), **When** lo abro de nuevo, **Then** el borrador
   conserva lo dictado igual que lo escrito.

---

### User Story 2 - Si la voz no se puede usar, escribo y no pierdo nada (Priority: P1)

Como persona cuyo teléfono no tiene reconocimiento de voz, no me dio permiso, no tiene red o está en un lugar con ruido, quiero
que el formulario siga funcionando igual y que me diga con claridad qué pasó.

**Why this priority**: el Código Renaser es obligatorio en la primera semana. Una función de voz que impida completarlo, o que
borre lo escrito al fallar, sería peor que no tenerla.

**Independent Test**: simular cada falla (sin permiso, sin red, sin motor de voz, silencio, ocupado) y comprobar que se puede
terminar y registrar escribiendo, y que lo ya escrito o dictado sigue ahí.

**Acceptance Scenarios**:

1. **Given** un teléfono sin reconocimiento de voz, **When** abro el formulario, **Then** no veo el micrófono y el formulario es
   exactamente el de hoy.
2. **Given** que no di el permiso del micrófono, **When** toco el micrófono, **Then** una línea me dice que lo active en los
   ajustes del teléfono y puedo seguir escribiendo.
3. **Given** que no hay red y el reconocimiento la necesita, **When** dicto, **Then** una línea me dice que no hay conexión para
   reconocer mi voz y puedo escribir.
4. **Given** que toco el micrófono y no digo nada, **When** pasa la espera, **Then** se cierra solo, sin mensaje de error.
5. **Given** cualquier falla, **When** ocurre, **Then** lo escrito y lo dictado antes en todos los campos se conserva.

---

### User Story 3 - Escuchar la pregunta (Priority: P2)

Como persona que prefiere oír antes que leer, o que tiene la vista cansada, quiero tocar un botón junto a cada pregunta y
escucharla, para responder sin leer.

**Why this priority**: completa el «servicio de audios» (oír y hablar) y ayuda a las personas de 40 años o más, que son el
público principal. Es P2 porque el pedido se cumple con dictar; escuchar lo vuelve cómodo.

**Independent Test**: tocar «Escuchar» en «¿Qué pienso?» sin conexión y oír la pregunta con la voz de la app; empezar a dictar
y comprobar que la lectura se detiene.

**Acceptance Scenarios**:

1. **Given** el formulario abierto, **When** toco «Escuchar» en una pregunta, **Then** oigo esa pregunta, con la voz de la app y
   sin necesitar conexión.
2. **Given** una pregunta leyéndose, **When** toco el micrófono, **Then** la lectura se detiene antes de que el micrófono abra,
   para que el micrófono no capte la voz de la app.
3. **Given** una pregunta leyéndose, **When** toco «Escuchar» de nuevo o salgo del formulario, **Then** se detiene.
4. **Given** que el teléfono tiene el volumen en cero o en silencio, **When** toco «Escuchar», **Then** la app me lo dice en una
   línea en vez de quedarse muda sin explicación.

---

### User Story 4 - Lo mismo en todos los lugares donde se responde (Priority: P2)

Como persona que responde el Código Renaser desde distintos lugares (aviso del teléfono, Hoy, Mi Radar, pantalla obligatoria de
la primera semana), quiero que la voz funcione y se vea igual en todos, para no aprender dos formas.

**Why this priority**: la experiencia debe ser uniforme; un micrófono que existe en un lugar y no en otro es un defecto, no una
característica.

**Independent Test**: abrir el formulario por cada una de las cuatro entradas y comprobar que el micrófono y «Escuchar» están en
el mismo lugar, con la misma forma y el mismo comportamiento.

**Acceptance Scenarios**:

1. **Given** cualquiera de las entradas al formulario, **When** se abre, **Then** trae el mismo micrófono y el mismo «Escuchar» en
   cada pregunta.
2. **Given** el modo claro o el oscuro, **When** miro los botones, **Then** se leen con el contraste del tema y el estado
   (escuchando o detenido) no depende solo del color.

---

### Edge Cases

- **Cambia la hora mientras dicto**: se comporta igual que hoy cuando se escribe a las 14:58 y se registra a las 15:01 (la hora que
  rige es la de la regla actual; la voz no la cambia).
- **Llamada, otra app o pantalla apagada mientras dicto**: el micrófono se cierra, lo dictado hasta ahí queda en el campo.
- **Android antiguo que corta en la primera pausa**: la persona vuelve a tocar el micrófono y el texto se suma; no se pierde.
- **Texto reconocido con errores**: nada se registra solo; la persona revisa y corrige antes de tocar «Registrar».
- **Ruido o varias voces**: se reconoce lo que se pueda; el texto es siempre editable.
- **Dictar en voz alta en un lugar público**: lo decide la persona; el micrófono nunca se abre solo.
- **Persona que dicta una frase y la borra**: el borrador y el límite de caracteres se comportan como con el teclado.
- **Teléfono que reconoce otro idioma**: se pide español latinoamericano; si el teléfono no lo tiene, el micrófono no se ofrece.
- **Letra grande del sistema**: los botones nuevos no cortan los textos ni se superponen al campo.
- **Mismo registro dos veces en la hora (409)**: se resuelve como hoy; la voz no cambia esa regla.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Cada pregunta de texto del Código Renaser MUST ofrecer un micrófono para responderla hablando, en **todos** los
  lugares donde se abre el formulario (pantalla obligatoria de los días 1 a 7, Hoy, Mi Radar y el aviso del teléfono).
- **FR-002**: Lo dictado MUST sumarse al texto que ya hay en el campo, mostrarse mientras se habla y quedar editable antes de
  registrar. MUST NOT reemplazar lo escrito.
- **FR-003**: El sistema MUST NOT registrar nada solo al terminar de dictar. Registrar ocurre únicamente al tocar el botón de
  siempre, con las mismas validaciones (cuatro respuestas, energía de 1 a 10, 2 000 caracteres por campo).
- **FR-004**: MUST haber como máximo un micrófono abierto a la vez en el formulario.
- **FR-005**: El audio MUST NOT guardarse ni enviarse a los servidores de Renaser; lo único que se guarda es el texto que la
  persona confirma al registrar (y la marca «dictado», FR-013). Decisión 030-1 = A: sin nota de voz, sin SER por voz, sin audio del
  programa al terminar.
- **FR-006**: Sin reconocimiento de voz en el teléfono, el micrófono MUST NOT mostrarse y el formulario MUST ser idéntico al de hoy.
- **FR-007**: Con falta de permiso, de red, de motor en español o con el reconocimiento ocupado, el sistema MUST decirlo en una
  línea clara y MUST permitir seguir escribiendo; MUST NOT perder lo escrito ni lo dictado antes.
- **FR-008**: Los textos de la voz MUST estar en español de Perú, con tuteo, en tipo oración y sin jerga técnica.
- **FR-009**: La energía de 1 a 10 MUST seguir respondiéndose con el control de hoy; la voz no la llena.
- **FR-010**: Cada pregunta MUST poder escucharse con la voz de la app (la misma de las alarmas), sin conexión, y MUST NOT usar la
  voz del teléfono.
- **FR-011**: Escuchar una pregunta y dictar MUST NOT ocurrir a la vez: abrir el micrófono detiene la lectura, y la lectura se
  detiene al salir del formulario.
- **FR-012**: Las reglas del formulario MUST NOT cambiar: obligatorio y sin cerrar para el aprendiz en los días 1 a 7, opcional y
  cerrable para el resto, un registro por hora, borrador conservado, horas y avisos.
- **FR-013**: El registro MUST decir si se dictó (decisión 030-2 = B): un dato `dictado` (verdadero/falso) en el registro, **sin
  tabla nueva**, que viaja al registrar y que **ven el mentor o guía, el líder y Administración (y Alquimista)** en las mismas lecturas del
  Código Renaser que ya usan. Lo demás del registro MUST ser indistinguible de uno escrito: mismo contenido, mismos límites, mismo destino,
  mismo acceso y mismo 409. Los registros anteriores y los hechos por otro camino MUST valer `false`. Si la spec 034 oculta el Código
  Renaser a quien acompaña, MUST ocultar también el «dictado» (porque viaja en las mismas filas); la voz no agrega ningún camino de lectura.
- **FR-013b**: Donde mentor, líder o Administración leen el Código Renaser de una persona, cada registro dictado MUST decir «Dictado» con
  una pista de que puede haber errores de transcripción; MUST NOT cambiar nada del texto ni de la hora. El texto del aviso está en tuteo
  de Perú y no culpa a la persona.
- **FR-014**: Los botones nuevos MUST tener área táctil de 48 a 52 px, nombre para el lector de pantalla, estado anunciado
  («Escuchando», «Detenido») y contraste del tema en claro y oscuro; MUST verse completos con la letra grande del sistema.
- **FR-015**: Dictar en el Código Renaser MUST NOT depender de que SER o su IA estén encendidos (el reconocimiento es del
  teléfono). Si Soporte TI apaga el Código Renaser, la voz se apaga con él, sin interruptor nuevo.
- **FR-016**: La Política de Privacidad MUST decir, antes de publicar la app con esta función, que el Código Renaser también se
  puede dictar con el reconocimiento de voz del teléfono y que el audio no se guarda.
- **FR-017**: La voz MUST NOT abrir nuevos accesos: los registros siguen siendo de la persona, dentro de su formación, y los ve
  exactamente quien los ve hoy. El único dato nuevo es el «dictado» de FR-013.
- **FR-018**: El cambio MUST anotarse en `AGENTS.md` del frontend con fecha, alcance y «lo que cambia y nada más», porque toca la
  pantalla obligatoria de Hoy y Mi Radar de Yo.

### Key Entities *(include if feature involves data)*

- **Respuesta del Código Renaser**: el registro de siempre (cuatro textos, energía, hora prevista) más **`dictado`** (verdadero si alguna
  respuesta se dictó; falso por defecto). Es una columna en `registros_radar`, no una entidad nueva.
- **Dictado**: lo que la persona dice mientras el micrófono está abierto. Es transitorio: se convierte en texto en el campo y no se
  guarda como audio.
- **Pregunta hablada**: la versión en voz de cada una de las cuatro preguntas. Es contenido fijo de la app, no de la persona.

## Puntos abiertos (resueltos el 2026-10-31: pregunta 1 = A, pregunta 2 = B)

Cada uno cambia el resultado. Va primero la opción que recomienda el equipo.

**Pregunta 1 — ¿Qué es «el servicio de audios» al responder el radar?** → **Elegida por el dueño: A.**

- **A (recomendada): dictar la respuesta y escuchar la pregunta.** Es lo que esta spec describe. La app ya sabe dictar (chat de
  SER), no guarda audio, no crea tablas ni cambia el registro, y funciona con la voz de la app sin conexión.
- **B: A, y además guardar la respuesta como nota de voz** que el acompañamiento pueda escuchar. Cambia mucho: almacenamiento
  nuevo de audio, quién lo escucha, cuánto se conserva, transcripción para el análisis de la spec 031, y la Política de
  Privacidad (hoy dice que el audio de la voz en vivo no se guarda). Es una spec aparte.
- **C: A, y responder conversando con SER por voz**, que hace las preguntas y llena el formulario. Reutiliza la voz en vivo, pero
  gasta la cuota de SER (10 min por día) y la IA en cada hora; no conviene en la primera semana (12 veces al día).
- **D: ofrecer un audio del programa al terminar** (por ejemplo la Pastilla o una clase según la energía que se respondió). Es otra
  función (una recomendación), no «responder con la voz».

**Pregunta 2 — ¿Se marca el registro como «dictado»?** → **Elegida por el dueño: B** (la recomendada del equipo era A).

- **A (recomendada): no.** El registro no cambia y no hay migración. El uso se mide con una prueba con personas, no con un dato.
- **B: sí, con una marca en el registro** para saber cuántas respuestas se dictan y mostrar a quien acompaña que pudo haber
  errores de transcripción. Es un campo nuevo en el registro y en lo que ven mentor, líder y Administración.

## Restricciones del dueño

Textual del dueño: «respetar las reglas y no romper la lógica, reutilizar, no crear tablas innecesarias, siempre normalizar (o la
desnormalización correcta), buenas prácticas y SOLID, en backend y frontend.» Para esta spec significa:

1. **Reutilizar lo que existe**: el micrófono de dictado y el reconocimiento de voz del teléfono que ya usa SER; las voces
   grabadas de la app (Dora); el formulario, el borrador y el registro del Código Renaser tal como están; Mi Radar. **No se crean
   tablas**; la única columna nueva es `dictado` (030-2 = B). Datos normalizados: el texto dictado es el mismo dato que el
   texto escrito, no se duplica.
2. **No romper la lógica actual**: obligatoriedad de la primera semana, un registro por hora, el 409, horas de Mi Radar,
   semáforo y puntos quedan exactamente como están. Las pruebas existentes del Código Renaser siguen en verde.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID; si algo llega al backend, sin lógica en el
   controlador) **y las reglas del `AGENTS.md` del frontend** (un solo scroll, áreas de 48 a 52 px, tipo oración, tuteo de Perú,
   `useSystemBackHandler`, textos del tema en claro y oscuro, código nuevo por feature y componentes compartidos).
4. **Todo queda dentro de la formación de la persona, y lo sensible del Radar lo ve solo quien debe.** La voz no cambia quién ve
   el Radar. Criterio verificable por E2E: con cuentas de dos formaciones, un registro dictado por la persona A lo lee A, su
   mentor o guía vigente, el líder, Administración y Alquimista (la cadena de hoy), y no lo lee ninguna cuenta de la otra
   formación ni un mentor que no la acompaña; el resultado es idéntico al de un registro escrito.
5. **Preguntas antes de implementar**: respondidas por el dueño el 2026-10-31 (ver *Clarifications*).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En una prueba con al menos 5 personas de 40 años o más, 4 de 5 completan las cuatro respuestas de texto del
  formulario hablando en menos de 2 minutos, sin ayuda.
- **SC-002**: El 100 % de las fallas simuladas del reconocimiento (sin permiso, sin red, sin motor, silencio, ocupado) permiten
  terminar y registrar escribiendo, y conservan lo escrito y lo dictado antes.
- **SC-003**: Cero audios guardados o enviados a los servidores de Renaser: la inspección de los pedidos de un registro dictado
  muestra el mismo contenido que el de uno escrito, más el dato `dictado`, y ningún archivo de audio.
- **SC-004**: Un registro dictado y uno escrito se distinguen **solo** por `dictado` (mismo contrato, mismo acceso, mismo 409),
  verificado con la matriz de acceso por rol y formación de la restricción 4; y `dictado` lo leen mentor/guía, líder, Administración y
  Alquimista donde ya leen el Código Renaser, y nadie más.
- **SC-005**: «Escuchar» empieza a sonar en menos de 1 segundo y sin conexión en el 100 % de las pruebas, y se detiene antes de
  que el micrófono abra en el 100 % de los casos.
- **SC-006**: Las cuatro entradas al formulario muestran los mismos botones, en la misma posición y con el mismo comportamiento
  (comprobado en una prueba de extremo a extremo por entrada).
- **SC-007**: Todas las pruebas existentes del Código Renaser y de Mi Radar siguen en verde y el flujo obligatorio de la primera
  semana se completa igual que antes.

## Assumptions

- **Cómo se lee «servicio de audios»** (decisión del equipo, a confirmar con el dueño; ver Pregunta 1):

  | Lectura | Qué sería | Decisión |
  |---|---|---|
  | Dictar | Micrófono por pregunta; voz a texto; el texto se suma, se revisa y se registra | **Se hace** (P1) |
  | Escuchar la pregunta | La app dice la pregunta con su voz | **Se hace** (P2) |
  | Guardar la respuesta en audio | Nota de voz como respuesta | No: spec aparte |
  | Conversar con SER por voz | SER pregunta y llena el registro | No: gasta cuota de IA cada hora |
  | Ofrecer un audio del programa | Recomendar Pastilla, audioterapia o clase al terminar | No: es otra función |

- La voz de «Escuchar» es la **voz grabada de la app (Dora)**, no la del teléfono ni una voz generada en el servidor. Son cuatro
  frases fijas, así que no cuesta IA ni red. Si la spec 022 hace que las preguntas cambien por formación, la voz sigue a la pregunta.
- El reconocimiento es el del teléfono, en español latinoamericano. Sin red puede no funcionar en algunos teléfonos; se avisa.
- La energía de 1 a 10 no se dicta: es un control de un toque y decirla por voz sería más lento y ambiguo.
- La voz está disponible para todos los roles que ven el formulario, como hoy; ninguna pieza filtra por rol.
- Hoy la app es solo Android; se aplica donde la app ya ofrece dictado.
- **Cambio de servidor mínimo** (decisión 030-2 = B): una columna `dictado` en `registros_radar` (migración V132, decisión D-297) y un
  campo opcional `dictado` en el pedido y en las respuestas del Código Renaser. Es aditivo: un APK anterior no lo manda (vale `false`) y
  lo ignora al leer. La spec vive en el repo del backend porque ahí viven `specs/` y `.specify/`.
- La spec 022 define cómo una persona pertenece a una formación. Esta spec solo exige que la voz no cruce datos entre formaciones.
- No hay datos que migrar.
