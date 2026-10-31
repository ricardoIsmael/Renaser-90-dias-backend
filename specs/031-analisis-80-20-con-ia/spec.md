# Feature Specification: Tu 80/20 con IA, cada semana y cada mes

**Feature Branch**: `031-analisis-80-20-con-ia` (backend y app)
**Created**: 2026-10-31
**Status**: Draft
**Input**: Pedido del dueño (2026-10-31), textual: «Tarea: Código Renaser. El sistema debe tener el 80/20 de acciones diarias,
analizado semanal y mensualmente con IA, más las respuestas del Código Renaser, y brindar una respuesta y análisis.» Es la
segunda mitad de la tarea «Código Renaser»; la primera (responder con la voz) es la spec 030. La app pasa a tener 3
formaciones (spec 022): todo lo de esta spec queda dentro de la formación de la persona.

## Qué hay hoy (verificado en el código y en `AGENTS.md` el 2026-10-31)

- **«Tu 80/20» en Plan** es una tarjeta que abre una hoja con la regla de Pareto del producto: «Pocas acciones mueven casi todo tu
  avance». En cada eje la acción **verde** va primero (la que más mueve la aguja) y mientras no se cumple, las otras dos de ese eje
  (amarilla y roja) esperan con candado. La hoja lista las acciones **de hoy** del eje con su color y su estado. **No hay cálculo
  de qué parte del avance vino de qué acciones, ni mirada de la semana o del mes, ni IA.**
- **Las acciones del día** (hasta 3 por eje: Cuerpo, Negocio, Relaciones) llevan un puntaje de impacto de 1 a 10 que escribe la
  persona al planificar, un color por su posición (verde, amarilla, roja), una hora opcional y su estado. El plan se organiza en
  semanas de lunes a domingo (13 en el programa) y meses del programa (mes 1: semanas 1 a 4; mes 2: 5 a 8; mes 3: 9 a 13).
- **Las respuestas del Código Renaser** son cuatro textos libres (¿Qué hago?, ¿Qué pienso?, ¿Qué siento?, ¿Qué evito?) y la energía de 1
  a 10, con la hora a la que se respondió. En los días 1 a 7 son 12 por día y obligatorias; desde el día 8, Mi Radar es opcional y
  las horas salen del Despertar y el Dormir (spec 019). Las ven la persona y su acompañamiento (spec 012).
- **SER** es el acompañante con IA: chat, voz en vivo, memoria (que **nunca** guarda lo emocional ni la salud), herramientas de
  lectura del plan, desvío de la semana y propuestas con botón de confirmar. Tiene cuota diaria. Existe además un informe semanal
  con IA del diario (Espejo de la Sombra) que hoy no tiene análisis real conectado.
- **Quien acompaña** (mentor o guía vigente, líder de mentores, Administración, Alquimista) ve en la ficha de la persona secciones de
  solo lectura por semana (Radar, tiempo libre, cierres), con constancia de la consulta cuando lo hace el líder o Administración.

## Alcance

Que cada persona tenga, **cada semana y cada mes del programa**, (1) su **80/20 de acciones diarias** calculado con cifras y (2) un
**análisis con IA** que lo lee junto con las respuestas que dio en el Código Renaser en ese periodo, y le entrega **una respuesta**
de SER: qué movió su avance, qué se le fue, cómo estuvo y qué hacer la próxima semana. **Lo que cambia y nada más:** la hoja «Tu
80/20» de Plan gana las vistas Semana y Mes; hay un aviso del teléfono por análisis listo (apagable); la ficha de quien acompaña gana
una sección de solo lectura (según el Punto abierto 2); SER puede leer y explicar el análisis. No cambian los hábitos, las
acciones, la regla de Pareto ni sus candados, los puntos, el semáforo, la coherencia, el Radar, Hoy, Training ni Comunidad.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver mi 80/20 de la semana (Priority: P1)

Como persona que planifica acciones cada día, quiero ver qué pocas acciones mueven la mayor parte de mi avance esta semana, para
dedicar mi energía a lo que sí cuenta.

**Why this priority**: es el «80/20 de acciones diarias» que pidió el dueño y la base de todo lo demás. Funciona sin IA, sin costo y
sin esperar al fin de semana, y deja la hoja «Tu 80/20» útil aunque la IA esté apagada.

**Independent Test**: con una semana de ejemplo (12 acciones planificadas, 9 cumplidas, 3 verdes cumplidas), abrir «Tu 80/20» →
Semana y comprobar que las cifras coinciden con las de una cuenta hecha a mano.

**Acceptance Scenarios**:

1. **Given** una semana en curso con acciones planificadas, **When** abro «Tu 80/20» y elijo Semana, **Then** veo cuántas acciones
   cumplí de las planificadas hasta hoy, cuánto del impacto cumplido vino de las acciones que más mueven (según la regla elegida en
   el Punto abierto 1) y cuáles fueron, por eje.
2. **Given** que cumplí lo que menos mueve y dejé pendiente lo que más mueve, **When** miro Semana, **Then** lo dice sin culpa
   («esta semana lo que más movía quedó pendiente») y nombra esas acciones.
3. **Given** que cumplo una acción hoy, **When** vuelvo a abrir Semana, **Then** las cifras ya la incluyen, sin esperar nada.
4. **Given** una persona sin acciones planificadas esta semana, **When** abro Semana, **Then** veo una línea que dice cómo planificar
   (lleva a agendar) y ninguna cifra inventada.
5. **Given** la IA apagada o caída, **When** abro Semana, **Then** las cifras se ven igual.

---

### User Story 2 - Recibir el análisis de mi semana con la respuesta de SER (Priority: P1)

Como persona que cerró su semana, quiero recibir un análisis que junte lo que hice con lo que sentí, y una respuesta clara de qué
hacer la semana que viene, para mejorar sin tener que interpretar números.

**Why this priority**: es el pedido central («analizado semanal… con IA, más las respuestas del Código Renaser, y brindar una
respuesta y análisis»).

**Independent Test**: con una persona de datos de prueba (acciones de la semana y respuestas del Código Renaser), al cerrar la
semana en su zona aparece el análisis con las cuatro partes y el aviso; abrirlo diez veces no genera nada nuevo.

**Acceptance Scenarios**:

1. **Given** que terminó mi semana (lunes a domingo en mi zona) y tengo datos suficientes, **When** empieza el lunes, **Then** a más
   tardar en 24 horas tengo mi análisis y un aviso del teléfono que solo dice que está listo.
2. **Given** el análisis, **When** lo leo, **Then** tiene cuatro partes cortas y siempre en el mismo orden: **Lo que más movió**,
   **Lo que se te fue**, **Cómo estuviste** (energía y lo que escribí en el Código Renaser, según el Punto abierto 3) y **Para la
   próxima semana** (la respuesta de SER, con uno a tres pasos concretos).
3. **Given** que las cifras del análisis (acciones cumplidas, porcentajes) aparecen en el texto, **When** las comparo con las de
   Semana, **Then** son exactamente las mismas.
4. **Given** que no respondí el Código Renaser esa semana (es opcional desde el día 8), **When** leo el análisis, **Then** habla solo
   de mis acciones y dice, en una línea, que no hay respuestas del Código Renaser para leer; no inventa cómo me sentí.
5. **Given** que ya tengo el análisis, **When** lo abro cuantas veces quiera, **Then** es siempre el mismo y no se vuelve a pagar la IA.
6. **Given** que SER me propone un paso que implica agendar o cambiar algo, **When** lo veo, **Then** llega como una propuesta con
   botón de confirmar, como las demás de SER; el análisis por sí solo no cambia mi plan.

---

### User Story 3 - Cuando no hay suficiente o la IA falla (Priority: P1)

Como persona que a veces no planifica o no responde, o cuando el servicio de IA falla, quiero que me digan la verdad en lugar de
recibir un análisis vacío, repetido o inventado.

**Why this priority**: sin datos suficientes cualquier análisis es relleno, y una falla mal manejada cuesta dinero o confianza.

**Independent Test**: una semana con una sola acción planificada no genera análisis ni llamada a la IA; una semana con datos y la IA
caída muestra las cifras y el análisis llega solo cuando la IA vuelve, una sola vez.

**Acceptance Scenarios**:

1. **Given** una semana con menos datos que el mínimo (Supuestos), **When** cierra, **Then** no se llama a la IA y la hoja dice «Todavía
   no hay suficiente para analizar tu semana» con qué falta, sin culpa.
2. **Given** la IA caída al cerrar la semana, **When** abro la hoja, **Then** veo las cifras y una línea «Tu análisis estará listo en
   cuanto podamos generarlo».
3. **Given** que la IA se recupera, **When** pasa el siguiente intento, **Then** el análisis aparece una sola vez, sin duplicados.
4. **Given** que la IA no responde tras los reintentos, **When** pasa el plazo, **Then** el periodo queda sin análisis, con las
   cifras, y el sistema no sigue intentando para siempre.
5. **Given** que la IA devuelve un texto que no cumple las reglas (cifras que no son las calculadas, diagnóstico, otra persona),
   **When** el sistema lo revisa, **Then** no lo guarda ni lo muestra.

---

### User Story 4 - El análisis del mes (Priority: P2)

Como persona que termina un mes del programa, quiero ver cómo se movió mi 80/20 semana a semana y qué se sostuvo, para saber si voy
mejorando.

**Why this priority**: lo pidió el dueño («semanal y mensualmente»), pero depende de que el semanal exista.

**Independent Test**: con las cuatro semanas del mes 1 con datos, al cerrar el mes aparece el análisis mensual que compara las
semanas y lo que cambió.

**Acceptance Scenarios**:

1. **Given** que cerró el mes del programa (semanas 1 a 4, 5 a 8 o 9 a 13), **When** abro Mes, **Then** veo las cifras del mes y de
   cada semana, y el análisis con las mismas cuatro partes pero mirando la tendencia.
2. **Given** un mes con algunas semanas sin datos, **When** leo el mes, **Then** solo cuenta las semanas con datos y lo dice.
3. **Given** el mes en curso, **When** abro Mes, **Then** veo las cifras al día y la nota de que el análisis llega al cerrar el mes.

---

### User Story 5 - Conversar el análisis con SER (Priority: P2)

Como persona con mi análisis delante, quiero preguntarle a SER «¿qué significa esto?» o «¿qué hago con esto?» y que me conteste con
lo que ya dice mi análisis.

**Why this priority**: reutiliza a SER y cierra el círculo («una respuesta») sin construir un chat nuevo.

**Independent Test**: preguntar a SER «¿cómo me fue la semana pasada?» y comprobar que contesta con el análisis guardado, sin generar
otro y sin repetir lo emocional en su memoria de largo plazo.

**Acceptance Scenarios**:

1. **Given** un análisis listo, **When** le pregunto a SER por mi semana o mi mes, **Then** contesta con ese análisis, en su tono.
2. **Given** que la cuota de SER del día se agotó, **When** pregunto, **Then** pasa lo mismo que con cualquier pregunta; el análisis
   sigue disponible en Plan.
3. **Given** que hablé de mi análisis con SER, **When** SER arma su memoria, **Then** no guarda lo emocional ni la salud, como siempre.

---

### User Story 6 - Quien me acompaña ve mi análisis (Priority: P2)

Como mentor, guía, líder, administrador o alquimista, quiero leer el análisis de la persona que acompaño, para saber cómo va sin
leer todo su Radar ni todas sus acciones.

**Why this priority**: el dueño decidió que el acompañamiento lee el Radar para «analizar cómo va» la persona; el análisis resume
justo eso. Su alcance exacto es el Punto abierto 2, así que hasta que se responda **solo lo ve la persona**.

**Independent Test**: con cuentas de dos formaciones y de cada rol, comprobar quién lee el análisis de una persona (matriz del
criterio de aceptación de las Restricciones).

**Acceptance Scenarios**:

1. **Given** la regla de audiencia que decida el dueño, **When** abro la ficha de una persona que me corresponde, **Then** veo su
   análisis de la semana y del mes en una sección de solo lectura, con la forma de las otras secciones de la ficha.
2. **Given** una persona que no me corresponde (otro grupo, otra formación, sin vínculo), **When** intento leerla, **Then** no la veo
   ni averiguo que existe.
3. **Given** que el líder, Administración o el Alquimista leen un análisis, **When** lo hacen, **Then** queda constancia de la
   consulta, como en las demás lecturas de la ficha.

---

### Edge Cases

- **Primera semana**: las acciones del día aparecen después del Mapa (día 7). Una semana 1 casi sin acciones y con 84 respuestas del
  Código Renaser no genera el 80/20; el sistema no inventa acciones.
- **Persona que cambia su plan a mitad de semana**: cuentan las acciones que existían al cerrar la semana; las de días pasados no se
  pueden editar, así que no cambian después.
- **Acción sin puntaje de impacto o con puntaje igual a otra**: la regla de cuenta no se rompe; los empates se resuelven siempre igual.
- **Una sola acción cumplida**: «el 100 % vino de una acción» no es un hallazgo; el análisis no lo presenta como tal.
- **Semana con Radar y sin acciones** (por ejemplo la primera): no hay análisis 80/20; el Radar sigue visible para quien lo ve hoy.
- **Cuenta suspendida, cerrada o de prueba de «ver como»**: no genera análisis (no hay quien lo lea y cuesta una llamada).
- **Persona que cambia de formación** o se une tarde al programa: ver Supuestos; nunca se mezclan datos de dos formaciones en un análisis.
- **Zona horaria**: la semana y el mes son los de la persona; un cierre cerca de la medianoche UTC no cae en el día equivocado.
- **IA que repite frases entre semanas**: el análisis mensual no copia los semanales; resume la tendencia.
- **Texto de la persona con contenido muy delicado**: el análisis no lo diagnostica ni lo repite; el aviso de malestar repetido que ya
  existe sigue su propio camino y no cambia.
- **Letra grande o lector de pantalla**: las cuatro partes se leen en orden y las cifras no se cortan.
- **Persona sin ningún dato del Código Renaser ni acciones durante un mes**: no hay análisis y la hoja lo dice con amabilidad.

## Requirements *(mandatory)*

### Functional Requirements

**El 80/20 (cifras, sin IA)**

- **FR-001**: El sistema MUST calcular, para cada persona y cada periodo (semana de lunes a domingo y mes del programa), su 80/20 de
  acciones diarias: cuántas acciones planificó y cumplió, y cuánto del impacto cumplido vino de las que más mueven, por eje y en
  total. [NEEDS CLARIFICATION: ¿qué cuenta como «el 20 %»? A = las acciones verdes (la regla Pareto actual del producto); B = el 20 %
  de las acciones con más impacto cumplido (cuenta estadística clásica); C = el eje que concentra la mayor parte. Ver «Puntos
  abiertos».]
- **FR-002**: Las cifras MUST salir del sistema, no de la IA. La IA las interpreta; MUST NOT calcularlas ni cambiarlas.
- **FR-003**: La semana y el mes en curso MUST mostrar las cifras al día, sin IA y sin esperar al cierre.
- **FR-004**: La regla de Pareto del producto (la verde primero, el candado de las otras) MUST NOT cambiar; el cálculo la lee, no la
  modifica. El 80/20 MUST NOT dar ni quitar puntos ni contar en el semáforo, la coherencia o la racha.

**El análisis con IA**

- **FR-005**: Al cerrar cada semana y cada mes del programa, el sistema MUST generar **un** análisis por persona y periodo, sin que
  nadie lo pida, con las cifras del 80/20 y las respuestas del Código Renaser de ese periodo.
- **FR-006**: El análisis MUST guardarse la primera vez que se genera. Abrirlo, mostrarlo en la ficha o contárselo a SER MUST NOT
  volver a llamar a la IA. Lo que el texto cita de las cifras MUST guardarse junto al texto, para que lo que se leyó hoy sea lo mismo
  que se lea mañana.
- **FR-007**: El análisis MUST tener cuatro partes, siempre en este orden: **Lo que más movió**, **Lo que se te fue**, **Cómo estuviste**
  y **Para la próxima semana** (o «el próximo mes»), esta última con uno a tres pasos concretos. Se debe leer en menos de un minuto.
- **FR-008**: El texto MUST estar en español de Perú, con tuteo y el tono cálido y directo de SER; MUST NOT diagnosticar, juzgar,
  compararse con otras personas ni nombrarlas, ni afirmar nada que no esté en los datos del periodo.
- **FR-009**: Toda cifra que mencione el texto MUST coincidir con la calculada. Un texto con una cifra distinta, un diagnóstico o
  datos de otra persona MUST descartarse y no mostrarse.
- **FR-010**: Cuánto del Código Renaser entra al análisis MUST seguir el Punto abierto 3. [NEEDS CLARIFICATION: ¿puede el análisis
  citar frases de lo que la persona escribió, o solo describir patrones (energía, lo que más se repite en «qué evito») sin
  citarla? Ver «Puntos abiertos».]
- **FR-011**: Ante expresiones repetidas de malestar, el análisis MUST mantenerse neutro y sin clasificar; el aviso a Administración y
  Alquimista que ya existe MUST seguir funcionando igual e independiente.
- **FR-012**: El mes MUST mirar la tendencia semana a semana (qué se sostuvo y qué cambió) y MUST contar solo las semanas con datos,
  diciéndolo.
- **FR-013**: Las respuestas del Código Renaser MUST ser opcionales para el análisis: sin ellas, el análisis habla solo de las
  acciones y lo dice en una línea. Sin acciones suficientes, MUST NOT haber análisis.
- **FR-014**: La respuesta de SER MUST NOT cambiar el plan por sí sola. Si sugiere agregar, mover o quitar algo, MUST hacerlo como una
  propuesta con botón de confirmar, con las reglas de siempre (hoy no se reacomoda, la verde primero, ventana de planificación).

**Datos mínimos, fallas y costo**

- **FR-015**: Si el periodo no alcanza el mínimo de datos (Supuestos), el sistema MUST NOT llamar a la IA y MUST decirlo sin culpa.
- **FR-016**: Si la IA falla, el sistema MUST mostrar las cifras, reintentar un número acotado de veces y generar el análisis una sola
  vez cuando responda; MUST NOT guardar ni mostrar un texto incompleto, y MUST dejar de intentar tras el plazo.
- **FR-017**: El costo MUST estar acotado: como máximo una generación por persona y periodo (más los reintentos acotados), un tope
  diario global configurable que, si se alcanza, difiere la generación sin perderla, y ninguna generación para cuentas suspendidas,
  cerradas o de prueba de «ver como».
- **FR-018**: El periodo MUST ser el de la persona (su zona horaria) y la generación MUST decidirse por hora y por persona; se prueba
  con el reloj en la madrugada UTC.
- **FR-019**: Soporte TI MUST poder apagar el análisis con IA sin publicar una versión nueva, independiente del resto de SER. Apagado:
  las cifras se siguen viendo, el texto muestra el aviso de mantenimiento, y al volver se generan los pendientes sin duplicar.

**Quién lo ve y privacidad**

- **FR-020**: La persona MUST poder leer su propio análisis semanal y mensual, y nadie más hasta que el dueño responda el Punto
  abierto 2. [NEEDS CLARIFICATION: ¿quién más lo ve? A = la misma cadena que hoy ve su Radar (mentor o guía vigente, líder,
  Administración, Alquimista); B = solo su mentor o guía vigente; C = solo la persona. Ver «Puntos abiertos».]
- **FR-021**: Quien acompañe MUST leer solo a las personas que le corresponden, en solo lectura, y MUST NOT averiguar si existe el
  análisis de quien no le corresponde. El líder, Administración y el Alquimista dejan constancia de la consulta, reutilizando la que
  ya existe para la ficha.
- **FR-022**: Todo MUST quedar dentro de la formación de la persona: el análisis usa solo sus datos y los de su formación, ninguna
  cifra ni texto cruza formaciones, y quien acompaña otra formación no lo ve.
- **FR-023**: Al proveedor de IA MUST viajar solo lo necesario (acciones, cifras y, según el Punto abierto 3, lo que escribió en el
  Código Renaser), sin correo, teléfono, documento ni dirección. La Política de Privacidad MUST decir, antes de activar la función,
  que las respuestas del Código Renaser se usan para este análisis.
- **FR-024**: El contenido del análisis MUST NOT aparecer en registros técnicos ni en el texto del aviso del teléfono. El aviso dice
  solo que el análisis está listo y MUST poder apagarse desde las preferencias de avisos.
- **FR-025**: Eliminar la cuenta MUST borrar los análisis de la persona, con el mismo plazo que el resto de sus datos.
- **FR-026**: SER MUST poder leer el análisis guardado y explicarlo, en su cuota normal; MUST NOT copiar lo emocional ni la salud a su
  memoria de largo plazo.

**Experiencia**

- **FR-027**: La persona MUST leer el análisis en la hoja «Tu 80/20» de Plan, que gana el control Hoy · Semana · Mes, con el patrón
  de hojas de la app. MUST NOT haber tarjetas nuevas en Hoy, ni pestaña nueva, ni cambios en Plan fuera de esa hoja.
- **FR-028**: La hoja MUST usar tipo oración, tuteo de Perú, letra de 16 a 17 px, áreas táctiles de 48 a 52 px, un solo scroll, el
  contraste del tema claro y oscuro, y MUST NOT usar rojo ni otro color de alarma para «lo que se te fue» (se dice con palabras).
- **FR-029**: La ficha de quien acompaña MUST sumar una sección de solo lectura con la forma de las demás (semana con ‹ ›, mes,
  «Reintentar», y un 403 o 404 la esconde).
- **FR-030**: Todas las personas que hacen los 90 días MUST tenerlo, incluido el personal con programa propio; ninguna pieza filtra
  por rol.
- **FR-031**: Una app nueva contra un servidor anterior MUST dejar «Tu 80/20» como hoy; una app anterior contra el servidor nuevo
  MUST seguir funcionando (el campo nuevo se ignora).
- **FR-032**: Los cambios en Plan, Yo → Notificaciones y las fichas MUST anotarse en `AGENTS.md` del frontend con fecha, alcance y «lo
  que cambia y nada más».

### Key Entities *(include if feature involves data)*

- **Acción del día**: la que la persona planifica (eje, color de Pareto, puntaje de impacto, hora opcional, cumplida o no). Ya
  existe; esta spec solo la lee.
- **Respuesta del Código Renaser**: el registro de siempre (cuatro textos, energía, hora). Ya existe; esta spec solo lo lee.
- **Cifras del 80/20 de un periodo**: cumplidas de planificadas, parte del impacto que vino de las acciones que más mueven, por eje
  y en total. Se calculan al pedirlas y, cuando se cita un periodo cerrado, se guardan con su análisis.
- **Análisis de un periodo**: de una persona, de una semana o un mes del programa, de su formación; con las cuatro partes del texto,
  las cifras que cita, su estado (pendiente, listo, sin datos suficientes) y cuándo se generó. Uno por persona y periodo.
- **Constancia de consulta**: la que ya existe para las lecturas del líder y Administración sobre una persona.

## Puntos abiertos (los decide el dueño)

Cada uno cambia el resultado. Va primero la opción que recomienda el equipo.

**Pregunta 1 — ¿Qué es «el 20 %» del 80/20 de acciones diarias?**

- **A (recomendada): las acciones verdes.** Es la regla que el producto ya enseña en «Tu 80/20» (la verde de cada eje es la que más
  mueve). El análisis dice cuánto del impacto cumplido vino de las verdes y cuándo se hizo lo que menos mueve. Coherente con el
  candado actual; no es la cuenta estadística exacta (en un eje con tres acciones la verde es un tercio).
- **B: el 20 % de las acciones con más impacto cumplido**, ordenadas por el puntaje de impacto que escribió la persona, y cuánto
  suman. Es el 80/20 clásico, pero es una regla nueva que puede no coincidir con las verdes.
- **C: por eje**: qué eje (Cuerpo, Negocio o Relaciones) concentra la mayor parte del impacto cumplido. Más simple de leer, menos
  accionable.

**Pregunta 2 — ¿Quién más ve el análisis, además de la persona?**

- **A (recomendada): la misma cadena que hoy ve su Radar** —mentor o guía vigente, líder de mentores, Administración y
  Alquimista—, en solo lectura y con constancia de consulta. Es lo que el dueño decidió para el Radar («se tiene que analizar a los
  demás roles de cómo va»); el análisis solo lo resume. Costo: es un dato nuevo (una lectura de IA sobre lo emocional) que hay que
  poner en la Política de Privacidad.
- **B: solo su mentor o guía vigente.** Menos exposición; el líder y Administración siguen viendo el Radar crudo, no el análisis.
- **C: solo la persona.** La opción más privada; el acompañamiento no gana nada nuevo. Abrirlo después es un cambio de permiso, no de
  cálculo.

Hasta que se responda, la spec se construye para la opción C.

**Pregunta 3 — ¿Cuánto de lo emocional del Código Renaser entra al análisis?**

- **A (recomendada): patrones y cifras, con lenguaje cuidado.** Energía por día y momento, lo que más se repite en «qué evito», y
  cómo se relaciona con lo cumplido; sin citar frases de la persona y sin ponerle nombre a una emoción ni a un cuadro. El texto que
  escribió igual viaja al proveedor de IA para leerse.
- **B: además, frases cortas de lo que la persona escribió.** Es más personal; el análisis (que otros pueden leer, según la Pregunta
  2) expone texto íntimo. No recomendada.
- **C: solo la energía de 1 a 10.** Lo más seguro; el texto de las cuatro preguntas no viaja a la IA y se pierde casi todo el valor
  de «cómo estuviste».

## Restricciones del dueño

Textual del dueño: «respetar las reglas y no romper la lógica, reutilizar, no crear tablas innecesarias, siempre normalizar (o la
desnormalización correcta), buenas prácticas y SOLID, en backend y frontend.» Para esta spec significa:

1. **Reutilizar lo que existe**: SER (su IA, su tono, su cuota, sus herramientas de lectura del plan y sus propuestas con botón); el
   informe semanal con IA que ya existe y su recorrido por hora y zona de cada persona; las acciones del día y la regla de Pareto;
   Mi Radar y sus registros; la hoja «Tu 80/20» de Plan; la ficha de quien acompaña y su constancia de consulta; los avisos y sus
   preferencias; los interruptores de Soporte TI. **Nada de tablas nuevas si una existente sirve.** Datos normalizados: las
   acciones y las respuestas del Código Renaser se leen donde viven, no se copian. La única desnormalización justificada es guardar
   el análisis generado con las cifras que cita, para no pagar la IA dos veces y para que lo leído hoy sea lo leído mañana.
2. **No romper la lógica actual**: la regla de Pareto y sus candados, los puntos, el semáforo, la coherencia, el Radar, la memoria
   de SER (que no guarda lo emocional) y el aviso de malestar repetido siguen exactamente igual. Las pruebas existentes siguen en
   verde.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith con módulos que se hablan por su `api`, ninguna llamada a la IA
   dentro de una transacción, controlador sin lógica, el tiempo por el reloj de la persona, SOLID) **y las reglas del `AGENTS.md` del
   frontend** (los cinco tabs no se tocan sin autorización: aquí solo la hoja «Tu 80/20» de Plan, un interruptor en Yo →
   Notificaciones y las fichas; un solo scroll, áreas de 48 a 52 px, tipo oración, tuteo, tema claro y oscuro).
4. **Todo queda dentro de la formación de la persona, y lo sensible del Radar solo lo ve quien debe.** Criterio verificable por E2E
   (SC-005): con una cuenta de cada rol en dos formaciones, y con personas con y sin vínculo, la matriz de quién lee el análisis de
   quién da el resultado esperado (200, o 403 o 404 sin pista de que existe), tanto por la app como por el servidor, y ningún
   análisis, cifra o texto de una formación aparece en otra.
5. **Preguntas antes de implementar**: el dueño quiere hacer él mismo todas las preguntas antes de implementar. Las tres de «Puntos
   abiertos» cambian el alcance; las demás dudas están en el informe y en «Supuestos» marcadas «a confirmar».

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las semanas y meses cerrados con datos suficientes tienen análisis disponible en menos de 24 horas desde el
  cierre en la zona de la persona (o, si hubo falla de IA, desde que ésta se recupera dentro del plazo de reintentos).
- **SC-002**: En un conjunto de prueba de al menos 20 personas con datos conocidos, el 100 % de las cifras que cita el texto
  coinciden con las calculadas, y el 100 % de los textos con una cifra distinta, un diagnóstico o datos ajenos se descartan.
- **SC-003**: Abrir el análisis 100 veces, desde Plan, la ficha y SER, produce exactamente **una** generación por persona y periodo;
  el costo mensual por persona es como máximo 5 semanales y 1 mensual, más reintentos acotados.
- **SC-004**: Con la IA apagada o caída, el 100 % de las personas con acciones ven sus cifras del 80/20, y los análisis pendientes se
  generan una sola vez cuando la IA vuelve.
- **SC-005**: La matriz de acceso (roles × vínculo × formación) pasa al 100 % en una prueba de extremo a extremo, incluida una
  cuenta suspendida (403) y un mentor de otro grupo o de otra formación.
- **SC-006**: Cero apariciones del contenido del análisis o de las respuestas del Código Renaser en registros técnicos y en el texto
  de los avisos, verificado con una revisión automática.
- **SC-007**: En una prueba con al menos 10 personas de 40 años o más, 8 de 10 dicen en una frase, tras leerlo en menos de un
  minuto, qué fue lo que más movió su semana y qué harán distinto la próxima.
- **SC-008**: Una semana con menos del mínimo de datos produce cero llamadas a la IA y un mensaje sin culpa; una persona de prueba
  de «ver como» produce cero.
- **SC-009**: Todas las pruebas existentes de Plan (Pareto y candados), Mi Radar, SER y el semáforo siguen en verde; los puntos y el
  semáforo de una persona no cambian por tener o no análisis.

## Assumptions

- **Qué es «acción diaria»**: la acción del día (con color de Pareto y puntaje de impacto) que ya existe en Plan, no el hábito. Los
  hábitos no entran al 80/20; el pedido habla de acciones.
- **Periodos**: la semana es de lunes a domingo (13 en el programa, la última hasta el día 90) y el mes es el mes del programa de
  Plan (semanas 1 a 4, 5 a 8 y 9 a 13). Después del día 90 no hay más análisis; la persona conserva los que tiene. (A confirmar con
  el dueño: si debe seguir después del día 90 o usar meses calendario.)
- **Automático**: se genera al cerrar cada periodo para todos los que tienen datos suficientes, sin que lo pidan. Mientras el periodo
  está en curso solo hay cifras. (A confirmar: si se quiere pedir el análisis antes del cierre; costaría una generación más.)
- **Mínimo de datos (valor propuesto, a confirmar)**: para la semana, al menos 5 acciones planificadas repartidas en al menos 3 días;
  para el mes, al menos 15 en total. Para incluir la lectura del Código Renaser, al menos 3 respuestas en el periodo. Es un solo valor
  configurable, no repartido por el código.
- **Reintentos**: hasta 3 intentos separados en las 24 horas siguientes al cierre; pasado el plazo, el periodo queda sin análisis.
- **Aviso del teléfono**: uno por análisis listo, con texto fijo; encendido por defecto, apagable en Yo → Notificaciones. (A confirmar.)
- **Tono**: el de SER (cálido, directo, como Darren sin decir que es Darren), con 1 a 3 emojis como máximo, y sin emojis en las cifras.
- **Retención**: mientras exista la cuenta; se borra con ella. La persona no borra análisis sueltos en esta versión. (A confirmar: si
  debe poder verlos y borrarlos como la memoria de SER.)
- **Fuentes**: solo las acciones del día y las respuestas del Código Renaser. No entran los hábitos, el diario ni lo que la persona
  dijo en «Cierra tu día» (sus razones de lo pendiente serían una fuente valiosa; sumarla es una decisión aparte). No se unifica con el
  informe del diario (Espejo de la Sombra); ese sigue como está.
- **Formación**: la spec 022 define cómo una persona pertenece a una formación. Se asume que pertenece a una a la vez y que mover a
  alguien de formación no arrastra análisis ajenos; qué pasa con sus análisis anteriores lo define la spec 022.
- **Todos los roles que hacen el programa** lo tienen, como Mi Radar; no hay filtro por rol.
- **Sin migración de datos**: no hay historia anterior; empieza con la primera semana que cierre tras publicarse.
