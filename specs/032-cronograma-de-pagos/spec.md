# Feature Specification: Cronograma de pagos

**Feature Branch**: `032-cronograma-de-pagos`

**Created**: 2026-11-01

**Status**: BORRADOR. Falta la maqueta del dueño y sus respuestas (las preguntas están en [`preguntas-para-el-duenio.md`](./preguntas-para-el-duenio.md)).
No se planifica ni se programa nada hasta tenerlas. Todo lo que no dijo el dueño con sus palabras está marcado como **[NEEDS CLARIFICATION]** o como supuesto provisional.

**Input**: Pedido del dueño del 2026-10-31, textual:

- «El sistema debe de tener cronograma de pagos junto con el diagnóstico como así.»
- «Prioritario: Pagos.»
- «El de pagos te mandaré imágenes de cómo será.»

**Repos**: backend (`Renaser-90-dias-backend`, donde vive esta spec) y app (`Renaser-90-dias-frontend-`: Administración, Yo y la hoja de aceptar solicitudes, según diga la maqueta).

**Se apoya en**: `022-formaciones` (qué es una formación y su duración), `025-solicitud-con-formacion` (la hoja donde se acepta a la persona) y `026-ficha-con-diagnostico` (el diagnóstico).
**Patrón que reutiliza**: la Caja Renaser (D-219): quién la ve, quién la opera, los avisos y la planilla descargable.

## Contexto

**Lo que hay hoy (verificado en el código el 2026-11-01)**

- **No existe nada de pagos**: ni tablas, ni pantallas, ni avisos. Las palabras «cuota» o «cobro» que aparecen en el código son otra cosa (la cuota semanal de cambios de horario de un hábito, el doble cobro de un puntaje).
- Las specs 022 y 025 dejaron los pagos **fuera de alcance** a propósito: «Pagos, facturación, certificados o diplomas: no se piden» y «Aceptar con otro criterio (por pago, por cupo…): no se pide». Esta spec es la que los pide.
- Hoy, quien lleva los cobros lo hace fuera de la app (a mano, por WhatsApp o en una hoja de cálculo). La app no sabe quién pagó, quién debe ni cuándo vence nada.

**Lo que pide el dueño, leído sin inventar**

1. Que el sistema tenga un **cronograma de pagos**: la lista de lo que cada persona tiene que pagar y cuándo.
2. Que vaya **«junto con el diagnóstico»**. La frase admite varias lecturas (se decide a la vez que el diagnóstico al aceptar a la persona, se muestra junto a él en el primer ingreso, o usa sus respuestas). **No se elige una por cuenta propia**: es la pregunta 1 del dueño.
3. Que sea **prioritario**: es lo primero que el dueño quiere ver funcionando después del cambio de las tres formaciones, aunque su maqueta llegue al final.
4. Que **el diseño lo da él**, con imágenes. Por eso todo lo visual de esta spec es provisional.

**Lo que ya existe y se reutiliza**

| Pieza | Para qué sirve aquí |
|---|---|
| Hoja de aceptar (spec 025): formación → duración → rol → ficha | Es el lugar más natural para decidir el cronograma de una persona nueva, a la par de «con o sin diagnóstico». |
| Formaciones y duraciones (spec 022): Escuela 90 días, Diplomado 3 meses o 1 año, Certificación 90 días | Lo que se cobra y en cuántas cuotas depende de la formación y de la duración elegida. |
| Alcance por formación (spec 022) | Administración y Alquimista ven las tres; nadie más ve datos de una formación que no es la suya. |
| Caja Renaser (D-219) | Modelo de «Administración opera, la persona ve en qué va, hay avisos y se descarga una planilla». El estado se **deriva** al leer y solo se guardan las acciones de una persona. |
| Avisos (bandeja, aviso del teléfono y mensaje del programa en el chat de soporte) | Recordatorios de vencimiento, como los de la Caja. |
| Reloj del participante (constitución, principio V) | «Vence el 5» quiere decir el 5 en la zona de la persona. |
| Historial que no se reescribe (constitución, principio IV) | Un pago mal anotado no se borra: se anula con motivo y se vuelve a anotar. |

## Clarifications

### Session 2026-11-01 (borrador, sin respuestas del dueño todavía)

- Lo único dicho por el dueño son las tres frases del «Input». Todo lo demás se infirió y se dejó a la vista.
- **Reglas generales del dueño para todo este lote de specs**: reutilizar lo que ya existe, no crear tablas innecesarias, normalizar, respetar la lógica actual, SOLID y buenas prácticas; aislamiento total entre formaciones; y que las preguntas se le hagan a él antes de implementar. Ver «Restricciones del dueño».
- **Esta spec va al final** de la tanda (decisión del dueño) y depende de que existan las formaciones (022) y la hoja de aceptar (025).
- Hasta que llegue la maqueta, no se define ninguna pantalla; solo lo que la persona y Administración necesitan poder hacer.

### Lo que se asume mientras tanto (provisional, se cae si el dueño dice otra cosa)

1. **Solo se registra, no se cobra dentro de la app**: Administración anota los pagos que recibe por afuera (transferencia, Yape, efectivo…). La app no procesa tarjetas. *(Pregunta 2.)*
2. **Un plan modelo por formación y duración** (por ejemplo «Diplomado · 1 año: 12 cuotas») que se aplica a la persona al aceptarla, y que Administración puede ajustar para una persona concreta. *(Pregunta 3.)*
3. **Nada se bloquea por deber una cuota**: la app solo avisa y muestra «atrasada». *(Pregunta 7.)*
4. **Mentores, guías y líderes no ven nada de dinero**; lo ven la propia persona, Administración y Alquimista. *(Pregunta 6.)*
5. **La moneda es el sol (S/)**. El diagnóstico del dueño ya habla de «S/» y «US$» al preguntar ingresos. *(Dato que el dueño debe confirmar.)*

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Veo mi cronograma de pagos y sé qué me toca pagar (Priority: P1)

Como persona que entró a una formación con pagos, quiero ver en la app cuánto pago, en cuántas cuotas, cuándo vence cada una y cuáles ya figuran pagadas, para no tener que preguntarlo por WhatsApp.

**Why this priority**: Es la mitad del pedido y lo que el dueño más va a querer ver («le sale su cronograma»). Sin esto, el cronograma solo existiría para Administración.

**Independent Test**: Con una cuenta que tenga un cronograma de 4 cuotas y una pagada, abrir la app y comprobar que se ve la próxima cuota, las cuatro con su fecha y monto, y la pagada marcada.

**Acceptance Scenarios**:

1. **Given** una persona con cronograma, **When** busca «Pagos» donde diga la maqueta (probablemente una fila en **Yo**, como «Tu Caja Renaser»), **Then** ve primero **su próxima cuota** (monto y fecha) y debajo **todas** las cuotas en orden, cada una con su estado en palabras («Pagada», «Vence el 5 de diciembre», «Vence hoy», «Atrasada»).
2. **Given** una cuota pagada, **When** la persona la mira, **Then** ve la fecha en que se registró su pago y, si la hay, una forma de ver su comprobante.
3. **Given** el cronograma, **When** la persona lo lee, **Then** ve el total de la formación, lo ya pagado y lo que falta, con la moneda.
4. **Given** una cuota atrasada, **When** la persona la mira, **Then** la ve marcada con **forma y palabra**, no solo con color, y sin un tono de reproche (tuteo, tono de Renaser).
5. **Given** una persona **sin** cronograma (la formación no cobra o no se le asignó uno), **When** recorre la app, **Then** no ve ninguna entrada de pagos: la app queda como hoy.
6. **Given** que Administración registra un pago, **When** la persona vuelve a abrir su cronograma, **Then** ve la cuota como pagada sin tener que cerrar sesión.
7. **Given** un cronograma, **When** la persona intenta llegar a ver el de otra persona (por enlace, aviso viejo o identificador), **Then** el sistema responde como si no existiera.

---

### User Story 2 - Al aceptar a alguien, Administración define su cronograma junto con el resto de su entrada (Priority: P1)

Como Administración, quiero que en el momento de aceptar una solicitud, al lado de la formación, el rol y el diagnóstico, quede definido el cronograma de pagos de esa persona, para no tener que armarlo después en otro lado.

**Why this priority**: Es la lectura más probable de «junto con el diagnóstico» y sigue el mismo patrón de la spec 025. **[NEEDS CLARIFICATION: ¿a esto se refiere el dueño? Ver pregunta 1.]**

**Independent Test**: Aceptar una solicitud eligiendo Diplomado · 1 año · Aprendiz · con diagnóstico · con cronograma, e iniciar sesión con esa cuenta: ve su cronograma de 1 año.

**Acceptance Scenarios**:

1. **Given** la hoja de aceptar (spec 025), **When** Administración llega al final, **Then** ve una decisión más —**con cronograma de pagos** o **sin cronograma**— junto a la de «con diagnóstico» o «sin diagnóstico». *(Forma exacta: la maqueta.)*
2. **Given** que elige «con cronograma», **When** ya eligió formación y duración, **Then** el sistema le propone el **plan modelo** de esa combinación (monto total, cuotas y fechas) y Administración lo confirma o lo ajusta para esa persona.
3. **Given** que cambia la formación o la duración en la misma hoja, **When** vuelve al bloque de pagos, **Then** el plan propuesto se ajusta a la nueva combinación y no queda uno viejo escondido.
4. **Given** el resumen final de la hoja, **When** Administración lo lee, **Then** la frase incluye el cronograma («María entrará al Diplomado de 1 año como Aprendiz, con diagnóstico y con cronograma de 12 cuotas»).
5. **Given** que acepta, **When** se completa, **Then** la persona queda con el cronograma creado en la **misma operación** que su cuenta y su formación: o queda todo o no queda nada.
6. **Given** una formación o una combinación **sin** plan modelo, **When** Administración elige «con cronograma», **Then** puede armarlo a mano para esa persona o dejarlo para después, y la hoja no se rompe.
7. **Given** una app anterior que acepta solo con rol (spec 025, FR-013), **When** acepta una solicitud, **Then** la persona queda **sin** cronograma y nada falla.

---

### User Story 3 - Administración registra los pagos y ve quién está al día (Priority: P1)

Como Administración, quiero anotar cada pago que recibo y ver de un vistazo quién está al día, quién vence pronto y quién está atrasado, para dejar de llevar esa cuenta a mano.

**Why this priority**: Un cronograma que nadie puede actualizar no sirve; y la utilidad real para el dueño es saber quién debe.

**Independent Test**: Con tres personas (una al día, una por vencer y una atrasada), abrir la lista, comprobar los contadores y las filas, registrar el pago de la atrasada y ver que pasa a «al día».

**Acceptance Scenarios**:

1. **Given** Administración en su sección de pagos, **When** la abre, **Then** ve contadores —**al día**, **por vencer**, **atrasados**— y la lista de personas con su formación, su próxima cuota y su estado; **tocar un contador abre la lista que lo respalda**.
2. **Given** la lista, **When** filtra por **formación** (Todas · Escuela · Diplomado · Certificación) y por **estado**, **Then** ve solo esas personas, y cada fila dice a qué formación pertenece.
3. **Given** la ficha de una persona, **When** Administración la abre, **Then** ve su cronograma completo y el historial de pagos, con quién registró cada uno y cuándo.
4. **Given** una cuota pendiente, **When** Administración **registra el pago**, **Then** anota fecha, monto y medio [NEEDS CLARIFICATION: ¿qué medios hay?], y opcionalmente adjunta la foto del comprobante; la cuota pasa a «Pagada» en el acto.
5. **Given** un pago registrado por error, **When** Administración lo corrige, **Then** lo **anula con un motivo escrito** y puede registrar el correcto; el pago anulado **no se borra**, queda en el historial.
6. **Given** un doble toque en «Registrar pago», **When** se envía dos veces, **Then** queda **un solo** pago registrado.
7. **Given** dos administradores que registran el mismo pago a la vez, **When** ambos envían, **Then** queda uno y el otro ve «Este pago ya lo registró Ana».
8. **Given** Alquimista, **When** entra a pagos, **Then** tiene las mismas capacidades que Administración. *(A confirmar: en la Caja Renaser opera solo Administración.)*
9. **Given** un mentor, una guía, un líder o un aprendiz, **When** busca pagos en Administración, **Then** no existe para ellos.
10. **Given** un pago parcial de una cuota [NEEDS CLARIFICATION: ¿se aceptan pagos parciales?], **When** se registra, **Then** la cuota queda con lo que falta, no como pagada ni como sin pagar.

---

### User Story 4 - Un plan modelo por formación y duración (Priority: P2)

Como Administración, quiero definir una sola vez cuánto cuesta cada formación según su duración y en cuántas cuotas se paga, para que al aceptar a alguien se arme solo.

**Why this priority**: Evita escribir el cronograma persona por persona, pero se puede empezar sin él (armándolo a mano), por eso es P2.

**Independent Test**: Crear el plan «Diplomado · 1 año: 12 cuotas de S/ X», aceptar a alguien en esa combinación y comprobar que su cronograma sale con 12 cuotas.

**Acceptance Scenarios**:

1. **Given** Administración o Alquimista, **When** abren la configuración de una formación, **Then** ven los planes de cada duración (Diplomado: 3 meses y 1 año; Escuela y Certificación: 90 días) con **monto total, número de cuotas y a cuántos días vence cada una**.
2. **Given** un plan, **When** lo editan, **Then** el cambio rige **solo para las personas que se acepten después**; los cronogramas ya asignados **no cambian** (misma regla que la duración, spec 022, FR-004).
3. **Given** una formación, **When** no tiene plan, **Then** al aceptar a alguien no se propone cronograma y se puede armar a mano.
4. **Given** un plan, **When** los montos de las cuotas no suman el total, **Then** el sistema no deja guardarlo y dice cuánto falta o sobra.
5. **Given** que otro administrador editó el plan mientras se miraba, **When** se intenta guardar, **Then** se rechaza con un mensaje claro y se recarga sin perder lo escrito.
6. **Given** el plan, **When** se pregunta por «pago único» o por descuentos, **Then** [NEEDS CLARIFICATION: ¿hay pago único, becas o descuentos? ¿cómo se expresan?].

---

### User Story 5 - Avisos de vencimiento y de atraso (Priority: P2)

Como persona, quiero que me avisen antes de que venza una cuota y si se me pasó; como Administración, quiero saber quién se atrasó sin tener que revisar la lista cada día.

**Why this priority**: Es lo que más ahorra trabajo de cobranza, pero el cronograma sirve sin avisos.

**Independent Test**: Con una cuota que vence en 3 días, comprobar que llega el recordatorio; dejarla vencer y comprobar el aviso de atraso a la persona y a Administración.

**Acceptance Scenarios**:

1. **Given** una cuota que vence pronto [NEEDS CLARIFICATION: ¿cuántos días antes? se propone 3, como el recordatorio de la Caja], **When** llega ese día, **Then** la persona recibe un aviso (bandeja, aviso del teléfono y mensaje del programa en su chat de soporte) con el monto y la fecha.
2. **Given** el día del vencimiento, **When** llega, **Then** la persona recibe un segundo aviso «vence hoy».
3. **Given** una cuota que se pasó, **When** pasa [NEEDS CLARIFICATION: ¿cuántos días de gracia?], **Then** la persona recibe un aviso y Administración ve a la persona en su contador de **atrasados**.
4. **Given** una cuota que se pagó, **When** se registra el pago, **Then** dejan de llegar avisos de esa cuota y no llega ninguno ya enviado «tarde».
5. **Given** que la persona **ya vio** el aviso o pagó, **When** pasan los días, **Then** no se le repite en bucle; hay un máximo de avisos por cuota [NEEDS CLARIFICATION: ¿cuántos?].
6. **Given** el reloj de la persona, **When** se decide «hoy», **Then** se usa **su** zona horaria, también cuando en UTC ya es otro día.
7. **Given** una persona que apagó los avisos del teléfono, **When** vence una cuota, **Then** igual ve su estado dentro de la app.

---

### User Story 6 - El cronograma sigue a la formación y a la duración (Priority: P2)

Como Administración, quiero saber qué pasa con el cronograma de una persona cuando cambia de duración o de formación, para no cobrar de más ni perder lo pagado.

**Why this priority**: Las specs 022 y 025 ya permiten cambiar la duración de quien empezó (con motivo) y trasladar a un aprendiz de formación. Sin una regla, los pagos quedarían desalineados.

**Independent Test**: Cambiar la duración de una persona con cuotas pagadas y comprobar que lo pagado se conserva y que el cronograma queda marcado «por revisar».

**Acceptance Scenarios**:

1. **Given** una persona con cuotas pagadas, **When** Administración cambia su duración (spec 025, 025-2), **Then** lo ya pagado **no se pierde ni cambia** y el cronograma queda marcado «por revisar» para Administración. [NEEDS CLARIFICATION: ¿se recalcula solo o se ajusta a mano? Pregunta 10.]
2. **Given** una persona que termina la Escuela y pasa al Diplomado (traslado, spec 022), **When** se traslada, **Then** el cronograma de la Escuela queda en su historial y [NEEDS CLARIFICATION: ¿se arma uno nuevo para el Diplomado? ¿se le abona lo pagado?].
3. **Given** el historial de pagos de una formación anterior, **When** la persona o Administración lo miran, **Then** se ve separado y nunca mezclado con el de la formación nueva.
4. **Given** una persona que todavía no empezó (sin Día 1), **When** Administración corrige su formación o duración, **Then** el cronograma se rehace sin dejar historia, porque nunca empezó.

---

### User Story 7 - La persona manda su comprobante (Priority: P3)

Como persona, quiero subir la foto de mi comprobante de pago desde la app para que Administración la vea y confirme mi cuota. **[NEEDS CLARIFICATION: ¿se quiere esto? Pregunta 5.]**

**Why this priority**: Es una comodidad. El mismo resultado se logra si la persona manda su comprobante por afuera y Administración lo registra (Historia 3).

**Independent Test**: Subir un comprobante, ver la cuota «En revisión» y confirmarla desde Administración.

**Acceptance Scenarios**:

1. **Given** una cuota pendiente, **When** la persona toca «Ya pagué» y adjunta su comprobante, **Then** la cuota pasa a «En revisión» y Administración recibe un aviso.
2. **Given** una cuota «En revisión», **When** Administración la confirma, **Then** pasa a «Pagada»; si la rechaza con un motivo, vuelve a «Pendiente» y la persona ve el motivo.
3. **Given** una cuota «En revisión», **When** la persona mira su cronograma, **Then** NO la ve como pagada hasta que Administración la confirme.

---

### User Story 8 - Descargar la planilla de pagos (Priority: P3)

Como Administración, quiero descargar los pagos en una planilla (CSV o Excel) para pasarlos a contabilidad, como ya hago con la Caja Renaser.

**Why this priority**: Es un exportable, no cambia el funcionamiento.

**Independent Test**: Descargar la planilla con un filtro de formación y comprobar que sus filas coinciden con la lista.

**Acceptance Scenarios**:

1. **Given** la lista de pagos con filtros, **When** Administración toca «Descargar», **Then** obtiene un archivo con exactamente las filas del filtro (persona, formación, cuota, monto, vencimiento, estado, fecha y medio de pago).
2. **Given** un rol sin permiso, **When** intenta descargar, **Then** el servidor lo rechaza.

---

### Edge Cases

- **Persona sin Día 1 elegido todavía**: sus fechas de vencimiento se calculan desde... [NEEDS CLARIFICATION: ¿desde su Día 1, desde que se la aceptó o con fechas fijas? Pregunta 4]. Mientras no se sepa, el cronograma muestra los montos y «la fecha se confirma al elegir tu Día 1» (o lo que diga la maqueta), nunca una fecha inventada.
- **Pago adelantado de varias cuotas** o **un pago mayor a la cuota**: [NEEDS CLARIFICATION: ¿cómo se reparte?]. Hasta que lo diga el dueño, se registra por cuota, una a una.
- **Devolución o reembolso**: no se pide. Si hace falta, se registra como una anulación con motivo y queda en el historial. [NEEDS CLARIFICATION: ¿existen devoluciones?]
- **Cuenta suspendida o con cierre de cuenta pedido** (`ELIMINACION_DE_CUENTAS`): el registro de pagos es un dato financiero; [NEEDS CLARIFICATION: ¿cuánto tiempo se conserva y qué pasa con él si la persona elimina su cuenta?] (puede haber una obligación legal de conservarlo).
- **Plan modelo editado con gente adentro**: no cambia a nadie que ya tenga cronograma (Historia 4).
- **Cuota con fecha en feriado o fin de semana**: vence ese día; no se corre a otro sin una regla del dueño.
- **Moneda distinta a la del plan**: [NEEDS CLARIFICATION: ¿solo soles o también dólares?]. Un plan tiene **una** moneda; no se mezclan dos en el mismo cronograma.
- **Soporte TI «ver como»** (solo lectura): ve el cronograma de la cuenta que mira, sin poder registrar nada.
- **Formación sin recepción o sin plan**: la persona se acepta igual; el cronograma se arma a mano o se deja para después.
- **App anterior contra servidor nuevo**: no ve ninguna entrada de pagos y todo lo demás funciona. **App nueva contra servidor anterior**: no muestra nada de pagos.
- **Funcionalidad apagada por Soporte TI**: la sección de pagos se esconde o muestra el aviso de mantenimiento, según corresponda (spec 002); lo ya registrado no se pierde.
- **Aislamiento entre formaciones**: un administrador que filtra «Escuela» no ve cifras ni filas del Diplomado, y un aviso de pagos no sale de la formación de la persona.
- **Pantallas pequeñas y letra grande**: un solo scroll, áreas táctiles de 48 px o más y textos de 16–17 px (la gente que usa la app es en parte mayor de 40 años).

## Requirements *(mandatory)*

### Functional Requirements

**El cronograma de una persona**

- **FR-001**: El sistema MUST poder asociar a una persona un **cronograma de pagos**: una lista ordenada de cuotas, cada una con número, monto, fecha de vencimiento y estado.
- **FR-002**: Cada cuota MUST estar en uno de estos estados: **pendiente**, **por vencer**, **vence hoy**, **atrasada** o **pagada**. Los tres intermedios MUST derivarse de la fecha de vencimiento y del día de la persona **al leer**, sin guardarse; solo lo que una persona hace (registrar o anular un pago) se guarda.
- **FR-003**: El cronograma MUST mostrar el total, lo pagado y lo que falta, en **una** moneda. [NEEDS CLARIFICATION: ¿soles solamente?]
- **FR-004**: El sistema MUST distinguir una persona **con cronograma** de una **sin cronograma**; sin él, la app MUST verse exactamente como hoy.
- **FR-005**: Las fechas de vencimiento MUST expresarse en el día local de la persona (su zona), no en UTC.
- **FR-006**: [NEEDS CLARIFICATION: ¿se aceptan pagos parciales de una cuota?] Hasta que el dueño lo diga, una cuota se paga completa.

**Quién lo arma**

- **FR-007**: Administración y Alquimista MUST poder definir el cronograma de una persona al aceptar su solicitud (spec 025) y también después, desde su ficha. [NEEDS CLARIFICATION: ¿al aceptar, junto al diagnóstico, o en otro momento? Pregunta 1.]
- **FR-008**: El sistema MUST ofrecer un **plan modelo por formación y duración** (monto total, número de cuotas y plazo de cada una) que se aplica al aceptar. [NEEDS CLARIFICATION: Pregunta 3.]
- **FR-009**: Administración MUST poder ajustar el cronograma de **una** persona (montos, fechas o cantidad de cuotas) dejando un motivo escrito. [NEEDS CLARIFICATION: ¿qué se puede ajustar? ¿becas y descuentos?]
- **FR-010**: Un cambio al plan modelo MUST regir solo para las personas aceptadas después; los cronogramas ya asignados MUST NOT cambiar.
- **FR-011**: Los montos de las cuotas MUST sumar el total del plan; si no suman, no se guarda.
- **FR-012**: Las fechas de las cuotas MUST contarse desde [NEEDS CLARIFICATION: ¿el Día 1 de la persona, la fecha de aceptación o fechas fijas? Pregunta 4].

**Registrar pagos**

- **FR-013**: Administración (y Alquimista, si se confirma) MUST poder registrar el pago de una cuota con fecha, monto, medio de pago y, opcionalmente, la foto del comprobante; el sistema MUST guardar quién lo registró y cuándo.
- **FR-014**: Los medios de pago MUST ser [NEEDS CLARIFICATION: ¿cuáles? ¿transferencia, Yape, Plin, efectivo, tarjeta, otros? ¿los edita Administración?].
- **FR-015**: Un pago registrado MUST NOT editarse ni borrarse. Un error se corrige **anulándolo con un motivo escrito** y registrando el correcto; el pago anulado MUST quedar en el historial (constitución, principio IV).
- **FR-016**: Registrar un pago MUST ser **idempotente** ante el doble toque y ante dos administradores a la vez: queda uno solo.
- **FR-017**: [NEEDS CLARIFICATION: ¿Se cobra dentro de la app (tarjeta, billetera) o solo se registra lo que se paga afuera? Pregunta 2.] El borrador asume **solo registrar**. Cobrar dentro de la app sería otra spec: sumaría una pasarela de pagos y, según la tienda, reglas de la tienda de aplicaciones sobre cobros dentro de la app que habría que revisar antes.
- **FR-018** *(P3)*: Si el dueño lo pide, la persona MUST poder subir su comprobante y la cuota quedar «en revisión» hasta que Administración la confirme.

**Quién lo ve**

- **FR-019**: La persona MUST ver solo su propio cronograma.
- **FR-020**: Administración y Alquimista MUST ver los cronogramas de todas las formaciones, con el filtro «Formación» y cada fila diciendo la suya.
- **FR-021**: Mentor, guía de aprendiz y líder de mentores MUST NOT ver montos ni estados de pago de nadie. [NEEDS CLARIFICATION: ¿o se les muestra solo «al día / atrasado» sin montos? ¿y Soporte TI? Pregunta 6.]
- **FR-022**: Pedir por identificador, enlace o aviso el cronograma de otra persona MUST responder como si no existiera, sin revelar nada (misma regla de la spec 022, FR-020).
- **FR-023**: Los datos de pagos MUST respetar el aislamiento entre formaciones (spec 022, FR-019): nada de una formación aparece en cifras, listas, avisos ni descargas de otra para quien no la ve.

**Administración**

- **FR-024**: Administración MUST tener una sección de pagos con contadores (al día, por vencer, atrasados), la lista de personas y el detalle de cada cronograma, cuyas cifras coincidan **exactamente** con las listas a las que llevan.
- **FR-025**: La lista MUST ofrecer los filtros **Formación** y **Estado**.
- **FR-026** *(P3)*: Administración MUST poder descargar los pagos en una planilla con las filas del filtro vigente.
- **FR-027**: La ficha de la persona en Administración MUST mostrar el estado de su cronograma y llevar a él, como hoy muestra el estado de su Caja.

**Avisos**

- **FR-028**: La persona MUST recibir un aviso antes de que venza una cuota, el día que vence y cuando se atrasa, con tope de avisos por cuota. [NEEDS CLARIFICATION: ¿cuántos días antes, cuántos de gracia y cuántos avisos como máximo? Pregunta 7.]
- **FR-029**: Un aviso MUST salir por los mismos caminos que los de la Caja (bandeja, aviso del teléfono y mensaje del programa en el chat de soporte) [NEEDS CLARIFICATION: ¿todos? ¿también por correo o WhatsApp?].
- **FR-030**: Administración MUST ver quién está atrasado sin tener que abrir cada ficha; [NEEDS CLARIFICATION: ¿recibe además un aviso o un resumen diario o semanal?].
- **FR-031**: Registrar un pago MUST detener los avisos de esa cuota.

**Relación con la formación, la duración y el diagnóstico**

- **FR-032**: El plan de una persona MUST salir de su **formación y su duración** (Diplomado de 3 meses ≠ Diplomado de 1 año).
- **FR-033**: **«Junto con el diagnóstico»**: el cronograma MUST definirse [NEEDS CLARIFICATION: al aceptar, en la misma hoja que el diagnóstico / al primer ingreso, a continuación del diagnóstico / otra]. El borrador asume lo primero (Historia 2), por ser lo que más se parece a lo ya decidido para la spec 025.
- **FR-034**: Las respuestas de la persona en el diagnóstico (ingresos, ahorros, deudas, utilidad del negocio) MUST NOT usarse para decidir el plan ni mostrarse junto a los pagos. [NEEDS CLARIFICATION: ¿está de acuerdo el dueño? Pregunta 1, opción C.] Es un supuesto defensivo: mezclarlas sería inventar una regla de negocio y expone datos delicados.
- **FR-035**: Cambiar la duración o la formación de una persona con cronograma MUST conservar todo lo ya pagado y dejar el cronograma «por revisar» para Administración. [NEEDS CLARIFICATION: Pregunta 10.]
- **FR-036**: Al trasladar a un aprendiz de formación (spec 022), su cronograma anterior MUST quedar en su historial, separado del nuevo.
- **FR-037**: Las personas que **ya están dentro** cuando se publique esta función MUST seguir funcionando sin cronograma; Administración puede asignárselo después. [NEEDS CLARIFICATION: ¿o todas deben tener uno desde el inicio? Pregunta 8.]

**Compatibilidad y operación**

- **FR-038**: Una app publicada antes de esta función MUST seguir funcionando sin cambios; una app nueva MUST tolerar un servidor que todavía no informa de pagos, sin mostrar nada.
- **FR-039**: La función MUST poder apagarse desde Soporte TI como las demás (interruptor propio, spec 002). [Supuesto provisional: nace apagada hasta que el dueño la prenda.]
- **FR-040**: Los pagos MUST conservarse para el historial aunque la cuenta se suspenda; qué pasa al eliminar la cuenta lo decide el dueño [NEEDS CLARIFICATION: Pregunta aparte, a tratar con la política de privacidad].
- **FR-041**: Las pruebas MUST verificar el aislamiento entre formaciones, la autorización negativa (rol sin permiso → 403, cuenta suspendida → 403) y el reloj en la madrugada UTC (día local anterior en Lima).

### Key Entities *(include if feature involves data)*

- **Plan de pago**: lo que cuesta una formación para una duración y cómo se divide (moneda, total, número de cuotas, plazo de cada una). Lo edita Administración; un cambio solo rige para las personas que vienen después.
- **Cronograma de la persona**: la copia concreta de un plan para una persona (con los ajustes que Administración le hizo). Pertenece a una formación.
- **Cuota**: una línea del cronograma: número, monto, fecha de vencimiento. Su estado se **deriva** de la fecha y de los pagos.
- **Pago**: lo que se recibió por una cuota: fecha, monto, medio, comprobante opcional, quién lo registró y cuándo. Es **historial**: no se edita ni se borra; se anula con motivo.
- **Ajuste del cronograma**: cambio hecho por Administración a un cronograma ya asignado, con motivo, quién y cuándo.
- **Decisión de aceptación «con / sin cronograma»**: la marca que Administración pone al aceptar a una persona, como la de «con / sin diagnóstico» (spec 025).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona con cronograma llega a ver su próxima cuota en **2 toques o menos** desde la pantalla de inicio de la app.
- **SC-002**: Administración sabe quién está atrasado en **2 toques o menos** desde la raíz de Administración, y las cifras coinciden con las listas en el **100 %** de los casos.
- **SC-003**: Registrar un pago toma **4 toques o menos** y **menos de 30 segundos** para una persona acostumbrada.
- **SC-004**: Aceptar a una persona **con** cronograma suma **2 toques o menos** a lo que hoy cuesta aceptarla (spec 025, SC-001).
- **SC-005**: **0** cuentas aceptadas «con cronograma» quedan sin él, y **0** quedan con uno a medias ante una falla a mitad de la operación.
- **SC-006**: Cambiar un plan modelo modifica a **0** personas que ya tienen cronograma.
- **SC-007**: **0** personas ven el cronograma de otra, y **0** administraciones filtradas por una formación ven filas o cifras de otra (comprobado por E2E, en las seis combinaciones de pares de formaciones).
- **SC-008**: Para las personas **sin** cronograma, la regresión de la app publicada pasa **sin cambios** (misma suite de Maestro).
- **SC-009**: Un pago registrado por error se corrige sin perder el historial: **100 %** de los pagos anulados siguen visibles con su motivo.
- **SC-010**: Un aviso de cuota **nunca** llega después de que la cuota se pagó (0 avisos tardíos).
- **SC-011** *(a fijar con el dueño)*: [NEEDS CLARIFICATION: ¿qué porcentaje de pagos al día espera el dueño ver en el primer mes? Es un objetivo de negocio, no de la app; se deja como pregunta abierta.]

## Fuera de alcance *(en este borrador)*

- **Cobrar dentro de la app** con tarjeta o billetera, y cualquier conexión a una pasarela de pagos [NEEDS CLARIFICATION: Pregunta 2].
- **Emitir boletas o facturas electrónicas** [NEEDS CLARIFICATION: Pregunta 9]. El borrador asume que se siguen emitiendo fuera de la app.
- **Bloquear o pausar a quien no paga**: solo se avisa [NEEDS CLARIFICATION: Pregunta 7].
- **Intereses, moras y recargos por atraso**, **cupones** y **becas automáticas**: no se piden.
- **Contabilidad** (asientos, impuestos, conciliación bancaria): la planilla descargable solo ayuda a quien la lleva.
- **Qué contiene cada formación**, la ficha, el diagnóstico y los términos: specs 022 a 027.
- **Cambios de diseño** de las cinco pestañas más allá de lo que muestre la maqueta; mientras no llegue, no se toca ninguna.

## Restricciones del dueño

Reglas del dueño del 2026-10-31 para todas las specs de esta tanda:

1. **Reutilizar lo que ya existe**: tablas, módulos, pantallas y flujos de hoy. Nada de tablas nuevas si una existente sirve con una columna o una relación. Datos normalizados y, si algo se desnormaliza, que esté justificado. (Aquí: el plan modelo y las cuotas de cada persona son datos genuinamente nuevos; la aceptación, los avisos, los estados derivados, el historial y la planilla se reutilizan del patrón de la Caja y de la spec 025.)
2. **No romper la lógica actual**: sin cronograma, la app queda exactamente como hoy (SC-008).
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID; los módulos solo importan la `api/` de otros) **y el `AGENTS.md` del frontend** (una hoja desde abajo, un solo scroll, 48 px, tipo oración, tuteo de Perú, gesto atrás en toda vista hija). Tocar los cinco tabs exige anotar en `AGENTS.md` la autorización con fecha, alcance y «lo que cambia y nada más»; el pedido del dueño sería esa autorización, pero el alcance exacto lo fija **su maqueta**.
4. **Aislamiento total entre formaciones**: nunca se cruza información (FR-023, SC-007).
5. **El dueño hace él mismo todas las preguntas antes de implementar**: por eso este borrador las deja todas a la vista y no decide por él.

## Assumptions

- **Un solo cronograma vigente por persona y formación.** Una persona que pasa de formación guarda el anterior en su historial.
- **Aplica primero a aprendices.** Si «con cronograma» valdrá también para mentores, guías y líderes (como el diagnóstico vale para cualquier rol, decisión 025-1 B), no está dicho. [NEEDS CLARIFICATION: Pregunta 1.]
- **Moneda**: soles (S/). Un plan, una moneda.
- **La app no maneja dinero**: solo lleva el control de lo que se pagó afuera. Eso evita pasarelas, comisiones y las reglas de la tienda de aplicaciones sobre pagos dentro de la app.
- **Quien opera es Administración** (y Alquimista, a confirmar), como en la Caja Renaser. Soporte TI no opera pagos.
- **Los avisos son los de siempre** (bandeja, aviso del teléfono, mensaje del programa en soporte); no se suma correo ni WhatsApp.
- **Nada se bloquea por deber una cuota**; el dueño puede pedirlo después.
- **El orden del primer ingreso** de una persona con cronograma sigue siendo el de hoy (ficha o diagnóstico → términos → Día 1); el cronograma lo ve **cuando entra a la app**, sin pantalla obligatoria extra. [NEEDS CLARIFICATION: Pregunta 1, opción B.]
- **Dónde se ve en la app**: probablemente una fila **«Tu cronograma de pagos» en Yo**, que solo existe si la persona tiene uno (como «Tu Caja Renaser»), y una sección **«Pagos» en Administración**; sin pestaña nueva. **Provisional hasta la maqueta.**
- **Dependencias**: spec 022 (formaciones y aislamiento), spec 025 (hoja de aceptar) y spec 026 (diagnóstico). Sin ellas, esta spec no se puede probar de punta a punta.
- **Números de migración y de decisión**: ninguno reservado todavía. Se piden al integrador cuando la spec pase a plan (la última migración usada por la tanda es la V157 y la última decisión, la D-306).

## Lo que la maqueta del dueño tiene que responder

Cuando llegue, hay que mirar que diga, al menos:

1. **Dónde vive** para la persona (¿una fila en Yo?, ¿una tarjeta en Hoy?, ¿una pestaña?) y para Administración (¿en Administración, en la ficha, en «Formaciones»?).
2. **Qué se ve de cada cuota**: número, monto, fecha, estado, comprobante, botón «Cómo pago».
3. **Los estados y sus colores o formas** (pagada, por vencer, vence hoy, atrasada, en revisión).
4. **La parte de Administración**: lista, contadores, filtros, detalle, «Registrar pago» y «Anular».
5. **La hoja de aceptar**: ¿es un bloque más, un paso aparte o una pantalla distinta?
6. **Las instrucciones de pago** que verá la persona (cuentas, números, titular, a dónde mandar el comprobante).
7. **Los textos**: cómo habla la app de plata (el tono, la forma de decir «atrasada»).
8. **Si hay algo más que no está en esta spec** (por ejemplo, descuentos, becas, pago único, recibos).

## Dudas para el dueño

Todas están desarrolladas, con opciones y la recomendada primero, en [`preguntas-para-el-duenio.md`](./preguntas-para-el-duenio.md). Resumen:

| # | Duda | Cambia el alcance |
|---|---|---|
| 1 | [NEEDS CLARIFICATION] ¿Qué significa «junto con el diagnóstico»? | **Sí** |
| 2 | [NEEDS CLARIFICATION] ¿Se cobra dentro de la app o solo se registra? | **Sí** |
| 3 | [NEEDS CLARIFICATION] ¿Quién fija montos y cuotas: un plan por formación y duración, o persona por persona? | **Sí** |
| 4 | [NEEDS CLARIFICATION] ¿Desde cuándo corren las fechas de las cuotas? | No |
| 5 | [NEEDS CLARIFICATION] ¿Quién registra un pago: Administración, la persona o las dos? | Sí, si la persona sube comprobante |
| 6 | [NEEDS CLARIFICATION] ¿Quién ve el cronograma y quién no? | No |
| 7 | [NEEDS CLARIFICATION] ¿Cuándo se avisa y qué pasa si no se paga? | **Sí**, si se bloquea algo |
| 8 | [NEEDS CLARIFICATION] ¿Qué pasa con la gente que ya está dentro? | No |
| 9 | [NEEDS CLARIFICATION] ¿La app emite boletas o facturas, o solo lleva el control? | **Sí**, si las emite |
| 10 | [NEEDS CLARIFICATION] ¿Qué pasa con el cronograma si cambia la duración o la formación? | No |
