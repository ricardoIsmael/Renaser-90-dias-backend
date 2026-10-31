# Feature Specification: Ficha inicial con diagnóstico, administrable; el Mapa de Renacimiento se retira

**Feature Branch**: `026-ficha-con-diagnostico`

**Created**: 2026-10-31

**Status**: Draft — con 3 preguntas abiertas que cambian el alcance (ver «Dudas para el dueño»)

**Input**: Pedido del dueño del 2026-10-31, textual: «El sistema debe poder gestionar su ficha inicial: CRUD, importar preguntas, quitar preguntas»
(con el enlace a su formulario de diagnóstico de Google); «Debe activarse con diagnóstico o sin diagnóstico: con diagnóstico sale el formulario de arriba,
adaptado; sin diagnóstico sale el formulario que se viene desarrollando»; y «El Mapa de Renacimiento "ya no va": será reemplazado con la nueva ficha». Decisiones
añadidas por el dueño: **los objetivos de 90 días de Plan nacen de esa ficha**; sin diagnóstico, la ficha actual y los objetivos se escriben directo en Plan.

**Repos**: backend (`Renaser-90-dias-backend`, donde vive esta spec) y app (`Renaser-90-dias-frontend-`: onboarding, Plan, Hoy, Yo y Administración).

**Se apoya en**: `022-formaciones` y `025-solicitud-con-formacion` (cuando se acepta a alguien se decide si entra con o sin diagnóstico). **Anexos en esta carpeta**:
[`diagnostico-inicial.md`](./diagnostico-inicial.md) (qué trae el formulario del dueño y qué hay que adaptar) y
[`diagnostico-inicial.csv`](./diagnostico-inicial.csv) (sus 27 preguntas en la plantilla de importación que propone esta spec).

## Contexto

**Lo que existe hoy**

- **La Ficha inicial**: la primera vez que entra, toda persona completa una ficha en 3 capítulos y 12 pasos cortos (identidad y familia, cuerpo y descanso, consentimiento), luego los Términos y luego «Elegir tu Día 1».
  Sus preguntas viven en el catálogo del servidor, pero **el orden, los pasos y varios campos especiales (WhatsApp con código de país, ubicación, documento, fecha de nacimiento, firma) están escritos dentro de la app**: cambiar
  una pregunta exige cambiar código. Algunas respuestas las usan otras funciones (por ejemplo, el dato con el que SER y los avisos tratan a la persona, y los datos de envío de la Caja Renaser).
- **El Mapa de Renacimiento**: un recorrido de 10 pasos (15–20 minutos) que se abre en el Día 7. De él salen la **prioridad principal**, los **tres objetivos de 90 días** (Cuerpo, Negocio, Relaciones), los hitos de 30, 60 y 90 días,
  las acciones para agendar y los protocolos de reemplazo. Al activarlo escribe los tres objetivos en Plan. Hoy, en producción, **un interruptor del servidor exige el Mapa activo para escribir objetivos en Plan**: quien no lo hizo ve
  «Primero tu Mapa de Renacimiento». También lo leen SER (para conocer a la persona) y la cifra del mes de Plan.

**Lo que pide el dueño**: una ficha **administrable** desde la app, con una variante **«con diagnóstico»** (su formulario de Google, adaptado a la app) y otra **«sin diagnóstico»** (la ficha de hoy); y que el **Mapa se retire**, porque
el diagnóstico lo reemplaza y de él nacen los objetivos.

## Clarifications

### Session 2026-10-31 (decisiones del dueño, ya tomadas)

- Q: ¿Qué reemplaza al Mapa de Renacimiento? → A: **La nueva ficha con diagnóstico.** El Mapa «ya no va».
- Q: ¿De dónde nacen los objetivos de 90 días de Plan? → A: **De esa ficha.** Sin diagnóstico: la ficha actual y los objetivos se **escriben directo en Plan**.
- Q: ¿Qué pasa con el interruptor de producción que exige el Mapa para escribir objetivos? → A: **La spec debe decirlo** (ver «Retiro del Mapa»): deja de tener efecto, porque ya no existe un Mapa que activar.
- Q: ¿Qué debe poder hacer Administración con la ficha? → A: **Crear, ver, editar y quitar preguntas, e importarlas** (el dueño apunta a su formulario de Google como ejemplo de lo que quiere cargar).
- Q: ¿Quién decide si una persona recibe el diagnóstico? → A: Administración, al aceptar su solicitud (spec 025).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Completo mi diagnóstico en la app, como una conversación corta (Priority: P1)

Una persona aceptada «con diagnóstico» entra por primera vez y, en vez de la ficha estándar, ve el diagnóstico adaptado: una portada amable, pasos de una a tres preguntas, barra de avance, el botón siempre a la vista y un cierre
que le dice qué sigue. Si cierra la app a la mitad, retoma donde estaba.

**Why this priority**: Es la mitad del pedido del dueño: el diagnóstico es la entrada de la persona al Diplomado y de él salen sus objetivos.

**Independent Test**: Aceptar a una persona con diagnóstico, iniciar sesión y completar las 27 preguntas del anexo; verificar que cada tipo de pregunta (texto, párrafo, opción única, casillas con máximo, cuadrícula, condicional, consentimiento) funciona y que se puede retomar.

**Acceptance Scenarios**:

1. **Given** una persona aceptada con diagnóstico, **When** inicia sesión por primera vez, **Then** ve la portada del diagnóstico (su título, «Tiempo estimado: 15 minutos» y el mensaje «No buscamos respuestas perfectas. Buscamos respuestas verdaderas.») con un botón **Empezar**, y **no** ve la ficha estándar.
2. **Given** el diagnóstico en curso, **When** avanza, **Then** ve pasos cortos con barra de avance, el botón fijo abajo y un solo scroll; **atrás** (la flecha o el gesto del sistema) vuelve un paso, nunca sale de la app.
3. **Given** que su nombre y ciudad ya constan en su solicitud, **When** llega a esas preguntas, **Then** las ve precargadas para confirmarlas, sin tener que escribirlas de nuevo. *(Propuesta del anexo, a confirmar.)*
4. **Given** la pregunta «¿Tienes un negocio que ya genera ventas?», **When** responde **Sí**, **Then** aparecen las preguntas del negocio; con **No**, se saltan sin dejar huecos ni pasos vacíos.
5. **Given** una pregunta de casillas con máximo 2, **When** intenta marcar una tercera, **Then** no se marca y un texto breve le recuerda «Elige como máximo 2».
6. **Given** la cuadrícula de satisfacción (siete áreas, escala del 1 al 5), **When** responde, **Then** ve cada área como una fila con su escala, y no avanza hasta responderlas todas.
7. **Given** una pregunta obligatoria sin responder, **When** toca **Siguiente**, **Then** se marca *esa* pregunta con un mensaje claro y la vista baja hasta ella; no sale un diálogo genérico.
8. **Given** que cierra la app a mitad del diagnóstico, **When** la abre de nuevo, **Then** retoma en el mismo paso con todo lo respondido (0 respuestas perdidas), aunque haya estado sin conexión un momento.
9. **Given** la declaración de responsabilidad y consentimiento, **When** no marca la casilla, **Then** no puede terminar; al marcarla, termina.
10. **Given** una pregunta cuyo texto nombra el plazo («en {duración} quiero haber conseguido»), **When** la persona es de una formación de 1 año, **Then** el texto dice «en 1 año»; si es de 90 días, «en 90 días».
11. **Given** que termina, **When** envía, **Then** ve un cierre breve que dice que sus objetivos ya están preparados y que sigue **Términos** y **Elegir tu Día 1**.

---

### User Story 2 - Mis objetivos de 90 días nacen de mi diagnóstico (Priority: P1)

Al terminar el diagnóstico, la persona ya tiene en Plan sus tres objetivos —uno por eje: Cuerpo (salud y bienestar), Negocio (riqueza y negocios) y Relaciones— con lo que ella escribió, el eje que eligió como prioridad
principal va primero, y su primera acción aparece como sugerencia al agendar. No se le pide hacer un segundo recorrido (el Mapa).

**Why this priority**: Es la razón de reemplazar el Mapa: los objetivos nacen en el primer día y no en el Día 7.

**Independent Test**: Terminar un diagnóstico con las tres áreas respondidas y abrir Plan: tres objetivos con el texto escrito, prioridad principal primero y la primera acción sugerida.

**Acceptance Scenarios**:

1. **Given** una persona que terminó su diagnóstico, **When** abre **Plan**, **Then** ve tres objetivos de 90 días (Cuerpo, Negocio, Relaciones), cada uno con lo que ella escribió para esa área, y el eje de su prioridad principal **primero**.
2. **Given** esos objetivos, **When** la regla de la pregunta Q2 es la recomendada (A), **Then** aparecen «Por confirmar»: la persona los lee, puede reescribirlos **una vez** y los confirma; desde ahí quedan fijos (regla de hoy: un objetivo definido ya no se cambia).
3. **Given** que respondió «Primera acción», **When** agenda acciones del eje de su prioridad principal, **Then** esa acción aparece como sugerencia para elegir, no agendada sola.
4. **Given** una persona a la que Administración le quitó del diagnóstico la pregunta de un área, **When** abre Plan, **Then** ese eje no trae objetivo y Plan le pide escribirlo directo, como a quien no tiene diagnóstico.
5. **Given** una respuesta más larga de lo que admite un objetivo (500 caracteres), **When** la escribe en el diagnóstico, **Then** el campo le muestra un contador y no le deja pasarse; nunca se recorta en silencio.
6. **Given** que ya tiene sus objetivos, **When** abre Hoy, **Then** no ve ninguna tarjeta que le pida «hacer su Mapa»; si faltan objetivos por confirmar, una línea de Hoy lo dice.
7. **Given** que Administración edita el diagnóstico después, **When** la persona ya terminó el suyo, **Then** sus objetivos no cambian.

---

### User Story 3 - Sin diagnóstico: todo sigue como hoy y los objetivos se escriben en Plan (Priority: P1)

Quien entra «sin diagnóstico» —y toda persona que existe hoy— completa la ficha estándar tal como está y escribe sus objetivos de 90 días directamente en Plan, sin pasar por ningún Mapa.

**Why this priority**: Es la mitad del pedido y protege a toda la gente que ya está dentro; la regla de hoy deja a quien no hizo el Mapa sin poder escribir objetivos, y eso debe terminar.

**Independent Test**: Con una cuenta sin diagnóstico y sin Mapa, completar la ficha estándar y definir los tres objetivos desde Plan.

**Acceptance Scenarios**:

1. **Given** una persona sin diagnóstico, **When** inicia sesión por primera vez, **Then** ve la ficha estándar de hoy: mismas preguntas, mismo orden en 12 pasos, mismos textos y mismos avisos de validación.
2. **Given** una persona que hoy ve «Primero tu Mapa de Renacimiento» en Plan, **When** se publique este cambio, **Then** ve el formulario para escribir su objetivo de cada eje, sin Mapa.
3. **Given** que define un objetivo en Plan, **When** lo guarda, **Then** queda fijo (la regla de siempre: lo definido ya no se cambia; solo se registra el avance).
4. **Given** una persona sin objetivos, **When** abre Hoy, **Then** la línea dice «Define tu objetivo en Plan» (como antes de que existiera la exigencia del Mapa), sin mencionar ningún Mapa.

---

### User Story 4 - El Mapa de Renacimiento se retira sin dejar a nadie bloqueado (Priority: P1)

El Mapa desaparece de la app y deja de existir como requisito. Nadie pierde lo que ya hizo y nadie queda sin poder definir sus objetivos, ni con la app nueva ni con la anterior.

**Why this priority**: Es riesgo puro de regresión: hay gente con el Mapa hecho, otra a medias y otra bloqueada hoy por el interruptor; y la app no se actualiza por aire.

**Independent Test**: Con tres cuentas —Mapa activo, Mapa a medias y sin Mapa—, abrir Hoy, Plan y Yo en la app nueva y en la anterior, y comprobar los tres casos del escenario.

**Acceptance Scenarios**:

1. **Given** la app nueva, **When** cualquier persona recorre Hoy, Plan y Yo, **Then** no existe ninguna entrada al Mapa: ni tarjeta en Hoy, ni «Primero tu Mapa» en Plan, ni «Mapa de Renacimiento» en Yo → Mi onboarding, ni aviso que lo mencione.
2. **Given** una persona con el Mapa **activo**, **When** abre Plan, **Then** conserva sus tres objetivos, su prioridad y su avance **sin ningún cambio**; nada se borra.
3. **Given** una persona con el Mapa **a medias**, **When** se publique, **Then** su borrador se conserva en el servidor pero ya no se puede continuar; sus objetivos los escribe en Plan.
4. **Given** una persona bloqueada hoy por «Primero tu Mapa», **When** se publique, **Then** puede definir sus objetivos desde Plan.
5. **Given** la app publicada antes de este cambio (que todavía muestra la puerta del Mapa), **When** una persona sin Mapa abre Plan, **Then** **no queda bloqueada**: ve el formulario de objetivos y nada de lo que aún muestre falla con un error. *(Cómo se logra es del plan; aquí solo importa el resultado.)*
6. **Given** el interruptor de producción que exige el Mapa activo, **When** se publique este cambio, **Then** **deja de tener efecto**: ningún objetivo se rechaza por falta de Mapa, sin importar su valor, y puede retirarse de la configuración.
7. **Given** SER, **When** conversa con una persona, **Then** sabe de ella lo mismo que sabía por el Mapa —sus objetivos, su prioridad y su primera acción— tomado de sus objetivos y no de respuestas sensibles del diagnóstico. *(Alcance exacto: pregunta Q8.)*
8. **Given** los pedidos que antes alimentaba el Mapa en Plan (acciones sugeridas, hitos de 30/60/90, cifra del mes), **When** la persona los busca, **Then** no ve cosas rotas ni vacías: lo que dependía del Mapa se oculta o se reemplaza según lo que decida la spec de planificación (ver «Dependencias»).

---

### User Story 5 - Administración gestiona las fichas desde la app (Priority: P2)

Administrador y Alquimista ven las fichas, crean secciones y preguntas, editan, reordenan, quitan, ven cómo las verá la persona y las publican. Nada llega a las personas hasta que se publica.

**Why this priority**: Es el pedido de «CRUD» del dueño, y es lo que evita volver a tocar código para cambiar una pregunta.

**Independent Test**: Crear una pregunta nueva en el diagnóstico, editarla, moverla, ver la vista previa, publicar y comprobar que una persona nueva la ve y una que ya terminó, no.

**Acceptance Scenarios**:

1. **Given** Administración, **When** abre **Más opciones → «Fichas»** (o la formación → «Ficha y términos»), **Then** ve las fichas disponibles, cada una con su número de preguntas, si está publicada o tiene cambios sin publicar, y quién y cuándo la publicó por última vez.
2. **Given** el diagnóstico abierto, **When** lo recorre, **Then** ve sus secciones y preguntas en orden y puede **crear** una sección o pregunta, **editar** su texto, ayuda y si es obligatoria, **reordenar** (también entre secciones), **duplicar** y **quitar**.
3. **Given** una pregunta de opciones, **When** la edita, **Then** agrega, quita y reordena opciones, y fija cuántas se pueden marcar como máximo.
4. **Given** una pregunta, **When** elige «Se muestra solo si», **Then** escoge una pregunta anterior y la respuesta que la activa.
5. **Given** una pregunta, **When** elige «Alimenta», **Then** puede dejarla en «Nada» o marcarla como objetivo de Cuerpo, objetivo de Negocio, objetivo de Relaciones, prioridad principal o primera acción; cada destino admite una sola pregunta.
6. **Given** que edita, **When** toca **Vista previa**, **Then** ve la ficha como la verá la persona (formato teléfono, paso a paso) con sus propios cambios, sin guardar nada para nadie.
7. **Given** cambios sin publicar, **When** toca **Publicar**, **Then** confirma («Lo verán las personas que empiecen desde ahora») y queda una versión nueva con quién y cuándo; las personas que ya estaban en mitad de la ficha **terminan con la versión con la que empezaron**.
8. **Given** una pregunta protegida (la usa otra función de la app), **When** intenta quitarla o cambiarle el tipo, **Then** el sistema no lo permite y dice cuál función depende de ella; su texto, ayuda y orden sí se editan.
9. **Given** una pregunta que alimenta un objetivo, **When** la quita, **Then** un aviso explica «Las personas ya no tendrán ese objetivo preparado: lo escribirán en Plan» y pide confirmar.
10. **Given** un mentor, un líder o un aprendiz, **When** busca «Fichas» en Administración, **Then** no la ve.
11. **Given** una pregunta, **When** la limita a ciertas formaciones («Se muestra en: Diplomado, Certificación»), **Then** la ven solo las personas de esas formaciones. *(Si las fichas son compartidas o propias de cada formación: pregunta Q3.)*

---

### User Story 6 - Importar preguntas desde una hoja de cálculo (Priority: P2)

Administración carga muchas preguntas de una vez: descarga una plantilla, la llena (o la copia desde su formulario de Google) y la sube. Antes de guardar, ve una vista previa con los errores por fila. El diagnóstico del dueño se carga así, la primera vez.

**Why this priority**: El dueño pidió «importar preguntas» y ya tiene el formulario hecho; escribir 27 preguntas a mano en el celular sería lento y propenso a errores.

**Independent Test**: Importar `diagnostico-inicial.csv` y verificar 6 secciones y 27 preguntas con sus tipos, opciones, condición y destino; luego importar un archivo con errores y comprobar que no se guarda nada.

**Acceptance Scenarios**:

1. **Given** Administración en una ficha, **When** toca **Importar preguntas**, **Then** puede **descargar la plantilla** (una hoja con las columnas: sección, clave, pregunta, ayuda, tipo, obligatoria, opciones o filas, máximo de opciones, se muestra solo si, alimenta) y **subir** su archivo (CSV o Excel).
2. **Given** un archivo subido, **When** se lee, **Then** ve una **vista previa** con cuántas secciones y preguntas trae, cada pregunta con su tipo, y una lista de **errores por fila** («Fila 14: el tipo “Casilla” no existe; usa “Casillas”»). **No se guarda nada** hasta confirmar.
3. **Given** un archivo con algún error, **When** intenta confirmar, **Then** no se importa **nada** (todo o nada) y se le indica qué filas corregir; vuelve a subir el archivo corregido.
4. **Given** un archivo correcto, **When** confirma, **Then** elige **Añadir al final** o **Reemplazar la ficha**; con *Reemplazar*, el sistema dice cuántas preguntas se retirarán y cuántas tienen respuestas y pide confirmación.
5. **Given** que importó, **When** termina, **Then** las preguntas quedan en **borrador**: se revisan, se previsualizan y se publican como cualquier cambio.
6. **Given** un archivo con tildes, comas, saltos de línea o punto y coma como separador (como lo guarda Excel en español), **When** lo sube, **Then** se lee bien, con la ñ y los acentos intactos.
7. **Given** el archivo `diagnostico-inicial.csv` de esta spec, **When** se importa, **Then** reproduce las 6 secciones y las 27 preguntas del formulario del dueño, con la condición de la pregunta 13, el máximo de la 14, la cuadrícula de la 5 y los destinos de las preguntas 16, 17, 18, 19 y 21.
8. **Given** preguntas repetidas (mismo texto en la misma sección), **When** se previsualiza, **Then** se avisan como posibles duplicados, sin bloquear.
9. *(P3)* **Given** una ficha, **When** toca **Descargar la ficha**, **Then** obtiene el mismo archivo de la plantilla con sus preguntas, para editarlo fuera y volver a importarlo.

---

### User Story 7 - Quitar preguntas sin perder lo que la gente respondió (Priority: P2)

Administración quita una o varias preguntas. Si nadie las respondió, desaparecen; si alguien ya las respondió, se **retiran**: dejan de mostrarse a las personas nuevas, pero las respuestas anteriores se conservan.

**Why this priority**: Es el «quitar preguntas» del dueño. Sin esta regla, quitar una pregunta borraría respuestas reales de personas reales.

**Independent Test**: Quitar una pregunta sin respuestas y otra con respuestas; verificar que la primera desaparece y la segunda deja de mostrarse pero sus respuestas siguen visibles en la ficha de quien las dio.

**Acceptance Scenarios**:

1. **Given** una pregunta que nadie respondió, **When** Administración la quita, **Then** desaparece por completo.
2. **Given** una pregunta que ya tiene respuestas, **When** la quita, **Then** el sistema avisa «N personas ya la respondieron: se retirará, no se borrarán sus respuestas», y al confirmar deja de mostrarse a quien no la haya respondido.
3. **Given** una pregunta retirada, **When** Administración o quien acompaña abre la ficha de alguien que sí la respondió, **Then** ve la respuesta marcada «Pregunta retirada», con el texto tal como se le hizo a esa persona.
4. **Given** el modo **Quitar preguntas**, **When** marca varias, **Then** ve cuántas hay marcadas y cuántas tienen respuestas, y las quita en una sola confirmación.
5. **Given** una pregunta de la que depende otra («se muestra solo si»), **When** intenta quitarla sola, **Then** el sistema ofrece quitar también las que dependen de ella, o cancelar.
6. **Given** una pregunta retirada, **When** Administración la **restaura** (mientras no sea reemplazada por otra con la misma función), **Then** vuelve a mostrarse y recupera sus respuestas anteriores.

---

### User Story 8 - Quien acompaña lee el diagnóstico de la persona (Priority: P3)

Administración y quienes ya ven hoy la ficha de una persona (con las reglas de hoy) leen su diagnóstico, por secciones y con las preguntas tal como se le hicieron, sin poder editarlo.

**Why this priority**: Es el sentido de pedirlo; pero depende de un permiso que hoy ya existe para la ficha, y por eso va después.

**Independent Test**: Abrir la ficha de una persona con diagnóstico completado con el rol que hoy ve su ficha estándar y con otro que no; comprobar quién lo ve.

**Acceptance Scenarios**:

1. **Given** una persona con diagnóstico, **When** Administración abre su ficha, **Then** ve el diagnóstico por secciones, con cada pregunta y su respuesta, y el tipo de ficha con el que entró.
2. **Given** un rol que hoy **no** puede ver la ficha de esa persona, **When** intenta abrirla, **Then** tampoco ve el diagnóstico (no se amplía ningún permiso). *(Qué roles lo ven: pregunta Q11.)*
3. **Given** una persona con ficha estándar, **When** se abre su ficha, **Then** se ve como hoy.

---

### Edge Cases

- **Persona a mitad de la ficha estándar al publicar**: la termina como hoy; nada cambia para ella.
- **Persona a mitad del diagnóstico cuando Administración publica una versión nueva**: termina con la versión con la que empezó; la nueva la ven las personas que empiecen después.
- **Sin diagnóstico publicado**: la opción «Con diagnóstico» de la spec 025 no se ofrece («Todavía no hay un diagnóstico publicado»).
- **Diagnóstico sin respuesta para un área**: ese eje no recibe objetivo y la persona lo escribe en Plan; los demás ejes sí.
- **Pregunta que alimenta con respuesta vacía** (opcional sin responder): equivale a «sin respuesta» para ese eje.
- **Pregunta de la que depende otra, con la condición cambiada**: si cambia la respuesta que la activa, las respuestas de las preguntas que dejaron de mostrarse **se conservan** pero no cuentan ni se piden; si vuelven a activarse, reaparecen.
- **Tipo de pregunta que no se puede cambiar**: con respuestas, el tipo no cambia (se retira y se crea otra), para no convertir respuestas de un tipo en otro.
- **Plantilla con una columna desconocida o sin la obligatoria**: se informa la columna y no se importa.
- **Más de una pregunta marcada con el mismo destino** («Alimenta»): se rechaza al guardar, con la lista de las preguntas en conflicto.
- **Opción que se quita de una pregunta ya respondida**: las respuestas con esa opción se conservan y se muestran como respondidas.
- **Persona de login social o con la solicitud incompleta** (sin ciudad o sin teléfono): la precarga solo usa lo que existe; el resto se pregunta.
- **Letra grande del sistema y pantallas pequeñas**: ninguna pregunta ni opción se corta; los botones miden 48 px o más.
- **Sin conexión al responder**: la persona puede seguir contestando el paso en curso; al volver la conexión se guarda; no se pierde nada ni se avanza dando por guardado lo que no lo está.
- **Persona con el Mapa activo que ahora recibe un diagnóstico** (no aplica: el diagnóstico se asigna al aceptar). Las personas que ya están dentro no reciben diagnóstico automáticamente (ver Q9).
- **Administración borra la última pregunta de una sección o la sección entera**: una sección vacía no se muestra a la persona; borrarla sigue las mismas reglas que quitar sus preguntas.
- **Dos administradores editan a la vez**: el último en publicar avisa si el otro publicó antes («Otra persona publicó cambios mientras editabas») y muestra qué cambió, en vez de pisarlo en silencio.
- **Formación sin fichas propias**: usa las compartidas (ver Q3).
- **Respuestas ya enviadas de la ficha estándar**: no cambian jamás por editar fichas.

## Requirements *(mandatory)*

### Functional Requirements

**Dos fichas y a quién llega cada una**

- **FR-001**: El sistema MUST ofrecer dos fichas: la **estándar** (la de hoy) y el **diagnóstico**; cada persona completa **una** de las dos en su primer ingreso, según se decidió al aceptarla (spec 025).
- **FR-002**: Sin diagnóstico, la ficha estándar MUST verse y comportarse como hoy (preguntas, orden, textos, validaciones) para toda persona que ya existe y para toda persona nueva sin diagnóstico.
- **FR-003**: Con diagnóstico, el diagnóstico MUST reemplazar a la ficha estándar; no se piden las dos.
- **FR-004**: «Con diagnóstico» MUST ofrecerse al aceptar solo si existe una versión publicada del diagnóstico.

**Experiencia de la persona**

- **FR-005**: El diagnóstico MUST mostrarse en pasos cortos (una a tres preguntas), con barra de avance, botón principal fijo, un solo scroll, área táctil de 48 px o más, tipo oración y tuteo de Perú; el gesto atrás del sistema MUST volver un paso.
- **FR-006**: El sistema MUST soportar en el diagnóstico: texto corto, párrafo, número, opción única, casillas con máximo de respuestas, escala, cuadrícula de escala, fecha y consentimiento.
- **FR-007**: Una pregunta MUST poder mostrarse solo si otra respuesta es una en concreto; las preguntas ocultas no se piden ni cuentan como pendientes.
- **FR-008**: Una pregunta obligatoria sin responder MUST marcarse en la propia pregunta con un mensaje claro y llevar la vista hasta ella.
- **FR-009**: El sistema MUST guardar cada paso o sección al avanzar y MUST dejar retomar en el mismo paso con lo respondido; una respuesta no guardada MUST NOT darse por guardada.
- **FR-010**: Los datos que ya constan de la solicitud (nombre, ciudad, teléfono) MUST precargarse para confirmarse cuando el diagnóstico los pida. *(Propuesta del anexo, a confirmar.)*
- **FR-011**: Un texto del diagnóstico MUST poder nombrar la duración de la persona y mostrarse con su valor («en 90 días», «en 1 año»).
- **FR-012**: El consentimiento MUST bloquear el envío mientras no se marque y MUST mostrar, encima de la casilla, el texto de la declaración que Administración haya cargado.
- **FR-013**: Al terminar, la persona MUST ver un cierre que le dice qué sigue (Términos y Día 1) y MUST NOT volver a pasar por el diagnóstico.

**Los objetivos nacen de la ficha**

- **FR-014**: Una pregunta del diagnóstico MUST poder marcarse como fuente del objetivo de Cuerpo, del objetivo de Negocio, del objetivo de Relaciones, de la prioridad principal o de la primera acción; cada destino admite una sola pregunta.
- **FR-015**: Al terminar el diagnóstico, el sistema MUST preparar en Plan un objetivo de 90 días por cada eje cuya pregunta fuente tenga respuesta, ordenar los ejes según la prioridad principal y dejar la primera acción como sugerencia al agendar.
- **FR-016**: Un eje sin respuesta MUST quedar sin objetivo y Plan MUST pedir escribirlo, igual que a quien no tiene diagnóstico.
- **FR-017**: Una respuesta que alimenta un objetivo MUST respetar el largo máximo de un objetivo (500 caracteres) desde que se escribe, con un contador, sin recortes silenciosos.
- **FR-018**: Una persona sin diagnóstico MUST poder escribir sus objetivos directo en Plan, sin ningún requisito previo.
- **FR-019**: Un objetivo definido MUST quedar fijo con la regla de hoy (solo se registra el avance), con la salvedad de la pregunta Q2 para los que nacen del diagnóstico.
- **FR-020**: Editar o republicar el diagnóstico MUST NOT cambiar los objetivos de quien ya lo terminó.

**Retiro del Mapa de Renacimiento**

- **FR-021**: La app MUST NOT ofrecer ninguna entrada al Mapa (tarjeta de Hoy, puerta de Plan, Yo → Mi onboarding, avisos).
- **FR-022**: El servidor MUST NOT exigir un Mapa activo para ninguna acción; el interruptor `ROCKS_EXIGIR_MAPA_ACTIVO` MUST dejar de tener efecto, sea cual sea su valor, y MAY retirarse de la configuración de producción una vez publicado este cambio.
- **FR-023**: Los datos del Mapa de toda persona (activo o a medias) MUST conservarse sin borrar nada; los objetivos de quien tenía el Mapa activo MUST seguir intactos.
- **FR-024**: Una persona con el Mapa a medias MUST NOT poder continuarlo, y MUST poder definir sus objetivos en Plan.
- **FR-025**: Una app publicada antes de este cambio MUST NOT dejar bloqueada a ninguna persona ni mostrar errores por la ausencia del Mapa.
- **FR-026**: SER MUST conocer de la persona sus objetivos, su prioridad principal y su primera acción desde su nueva fuente; el uso de respuestas completas del diagnóstico queda sujeto a la pregunta Q8.
- **FR-027**: Lo que Plan tomaba del Mapa (acciones sugeridas, hitos, cifra del mes, escala de Relaciones) MUST ocultarse o reemplazarse sin dejar pantallas vacías ni rotas; el reemplazo lo define la spec de planificación (ver «Dependencias»).

**Administración de las fichas**

- **FR-028**: Solo Administrador y Alquimista MUST poder ver y editar las fichas.
- **FR-029**: Administración MUST poder crear, ver, editar, reordenar, duplicar y quitar secciones y preguntas, con texto, ayuda, obligatoria, tipo, opciones, máximo de opciones, condición («se muestra solo si») y destino («alimenta»).
- **FR-030**: Los cambios MUST quedar en borrador y verse con una vista previa; solo **Publicar** los pone a disposición de las personas, y cada publicación MUST registrar quién, cuándo y qué versión.
- **FR-031**: Una persona que empezó una ficha MUST terminarla con la versión con la que empezó.
- **FR-032**: Las preguntas de las que depende otra función (por ejemplo el trato de SER, los datos de envío de la Caja Renaser) MUST marcarse como protegidas: se puede editar su texto y orden, pero no quitarlas ni cambiarles el tipo.
- **FR-033**: Las preguntas internas de otras funciones (que comparten el catálogo pero no son parte de la ficha de nadie) MUST NOT aparecer en el editor.
- **FR-034**: Cada pregunta MUST poder limitarse a ciertas formaciones; sin limitar, es para todas. *(Si las fichas son compartidas o propias de cada formación: pregunta Q3.)*
- **FR-035**: Dos administradores editando a la vez MUST ser detectados al publicar, sin pisar en silencio lo publicado por el otro.

**Importar y quitar**

- **FR-036**: Administración MUST poder descargar una plantilla de importación y subir un archivo CSV o Excel con las preguntas.
- **FR-037**: Antes de guardar, el sistema MUST mostrar una vista previa y los errores por fila; una importación con errores MUST NOT guardar nada (todo o nada).
- **FR-038**: Importar MUST ofrecer «Añadir al final» y «Reemplazar la ficha», y MUST pedir confirmación indicando cuántas preguntas y cuántas respuestas se ven afectadas.
- **FR-039**: El archivo MUST leerse con UTF-8 y aceptar coma o punto y coma como separador, con campos entre comillas y saltos de línea.
- **FR-040**: Una pregunta sin respuestas MUST eliminarse al quitarla; una con respuestas MUST retirarse (deja de mostrarse, se conservan sus respuestas) y MAY restaurarse.
- **FR-041**: Quitar varias preguntas a la vez MUST ser posible con una sola confirmación que informe cuántas tienen respuestas.
- **FR-042**: Una pregunta de la que dependen otras MUST NOT poder quitarse sola sin ofrecer quitar también las dependientes.

**Privacidad**

- **FR-043**: Las respuestas del diagnóstico MUST ser visibles solo para la persona y para quienes hoy pueden ver su ficha; esta spec no amplía ningún permiso. *(Quiénes: pregunta Q11.)*
- **FR-044**: Una pregunta retirada MUST seguir mostrándose, con su texto original, en la ficha de quien la respondió.

### Key Entities *(include if feature involves data)*

- **Ficha**: un formulario con nombre y versión publicada (hoy: estándar y diagnóstico). Reutiliza el catálogo de secciones y preguntas de la ficha actual.
- **Sección**: agrupa preguntas; tiene título, descripción y orden; puede tener condición.
- **Pregunta**: texto, ayuda, tipo, obligatoria, opciones, máximo de opciones, condición, destino («alimenta»), si es protegida, formaciones a las que aplica, estado (activa, retirada).
- **Versión publicada**: la foto de una ficha en el momento de publicarla (quién, cuándo); la usan las personas que empiezan después.
- **Respuesta**: lo que una persona contestó a una pregunta (las de hoy, más las del diagnóstico); se conserva aunque la pregunta se retire.
- **Objetivo de 90 días**: el que ya existe en Plan (por eje); aquí gana un origen nuevo: el diagnóstico.
- **Plantilla de importación**: la hoja con las columnas de la ficha; el anexo `diagnostico-inicial.csv` es un ejemplo completo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El **90 %** de las personas completa el diagnóstico en **20 minutos o menos** en el celular (el formulario original promete 15).
- **SC-002**: **0** respuestas perdidas: el **100 %** de quienes cierran la app a mitad retoman en el mismo paso con todo lo respondido.
- **SC-003**: El **100 %** de las personas que terminan el diagnóstico con las tres áreas respondidas ven en Plan **tres objetivos** con el texto que escribieron, el eje de su prioridad principal primero y su primera acción como sugerencia, sin hacer otro recorrido.
- **SC-004**: La ficha estándar tiene **0 cambios visibles** para quien entra sin diagnóstico (se compara paso a paso con la actual) y para las personas que ya existen.
- **SC-005**: Administración **agrega una pregunta y la publica en menos de 2 minutos**, y **importa las 27 preguntas del anexo en menos de 5 minutos**.
- **SC-006**: El **100 %** de las filas inválidas de un archivo se señalan por número de fila antes de guardar, y **0** importaciones quedan a medias.
- **SC-007**: Quitar preguntas con respuestas conserva el **100 %** de esas respuestas, visibles en la ficha de cada persona.
- **SC-008**: Después de publicar, **0** pantallas de la app llevan al Mapa y **0** personas quedan bloqueadas para escribir sus objetivos (con la app nueva y con la anterior).
- **SC-009**: El **100 %** de las personas con el Mapa activo conserva exactamente los mismos objetivos, prioridad y avance.
- **SC-010**: Una pregunta limitada al Diplomado la ve **0** personas de las otras formaciones (aislamiento de la spec 022, por E2E).

## Fuera de alcance

- **Planificación de la semana y el mes** (qué reemplaza a los hitos de 30/60/90, a la cifra del mes y a las acciones sugeridas del Mapa): otra spec de la tanda; aquí solo se exige que nada quede roto.
- **Analítica o tableros de las respuestas** del diagnóstico, y exportar respuestas de personas.
- **Importar leyendo directamente el enlace de Google Forms** o con credenciales de Google: no se hace (ver «Supuestos»).
- **La ficha de envío de la Caja Renaser, el Cuestionario Profundo de Yo → Mi onboarding y el Pacto**: no cambian.
- **Términos y condiciones**: spec 027. **Aceptar solicitudes**: spec 025. **Formaciones**: spec 022.
- **Reemplazar los textos de la ficha estándar existente**: esta spec no los toca (salvo lo que dice la pregunta Q1).

## Restricciones del dueño

Reglas del dueño del 2026-10-31 para todas las specs de esta tanda:

1. **Reutilizar lo que ya existe**: el catálogo de secciones y preguntas de la ficha actual y las respuestas guardadas por pregunta; los objetivos de Plan; las pantallas de Administración, la hoja desde abajo, el aviso en línea y los componentes de formulario de la app. Nada de tablas nuevas si una existente sirve con una columna o una relación
   (el catálogo actual ya guarda secciones, preguntas y opciones: se amplía con lo que falte —máximo de opciones, condición, destino, estado, versión— en vez de crear un catálogo paralelo). Datos normalizados; lo que se desnormalice, justificado.
2. **No romper la lógica actual**: la ficha estándar, sus respuestas, los Términos, el Día 1 y los objetivos existentes siguen igual para la Escuela (SC-004, SC-009). El retiro del Mapa no borra nada.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID; el módulo de onboarding publica en su `api/` lo que Plan, SER y Caja necesitan, sin que otros lean sus tablas) **y el `AGENTS.md` del frontend** (estructura por feature, esquemas tolerantes para que un APK publicado no se rompa, tipo oración, tuteo, gesto atrás). Las
   cinco pestañas se tocan solo para retirar el Mapa y mostrar los objetivos nacidos del diagnóstico; anotar la autorización en `AGENTS.md` con «lo que cambia y nada más».
4. **Aislamiento total entre formaciones**: una pregunta limitada a una formación no la ve nadie de otra, ni en la ficha ni en lo que quien acompaña lee de otra formación (SC-010). Criterio E2E: «un aprendiz de la Escuela no ve ninguna pregunta ni respuesta del diagnóstico del Diplomado».
5. **El dueño hace él mismo todas las preguntas antes de implementar**: la sección «Dudas para el dueño» las lista todas, aunque tengan supuesto.

## Assumptions

- **Importar desde Google Forms, el supuesto más simple y sin credenciales**: Google Forms **no permite exportar las preguntas** (solo las respuestas) y su API pide la cuenta dueña. Por eso la importación es desde una **hoja de cálculo (CSV o Excel) con una plantilla**: el dueño copia las preguntas de su formulario a la plantilla (o usa el
  `diagnostico-inicial.csv` ya hecho) y la sube. Se descartan leer el enlace público del formulario (depende de un formato interno de Google que puede cambiar) y conectar la cuenta de Google.
- **Las dos fichas son comunes a las tres formaciones y cada pregunta puede limitarse a ciertas formaciones** (reutiliza la lógica de audiencia que ya existe para los hábitos por rol). Se confirma en Q3.
- **El diagnóstico se carga la primera vez desde `diagnostico-inicial.csv`** y se revisa antes de publicar; el texto de la declaración de consentimiento lo entrega el dueño.
- **Una versión de ficha publicada es la que ven las personas que empiezan después**; las que ya empezaron terminan con la suya.
- **Una pregunta se retira, no se borra, cuando tiene respuestas**; se elimina del todo solo si nadie la respondió.
- **Importar es todo o nada**: es más seguro que un archivo medio cargado.
- **Las personas existentes no reciben el diagnóstico** (sin diagnóstico, ficha estándar); si el dueño quiere ofrecérselo después, es otra historia (Q9).
- **La visibilidad del diagnóstico es la de la ficha de hoy**; no se amplía ningún permiso.
- **SER usa solo lo derivado** (objetivos, prioridad principal, primera acción), no las respuestas completas del diagnóstico, que incluyen ingresos y emociones.
- **La retirada del Mapa deja los datos y el borrador en el servidor** (sin borrar), por si el dueño decide recuperar algo; solo se quita la entrada.
- **Dependencias**: spec 022 (formaciones) y 025 (con o sin diagnóstico al aceptar); la spec de planificación de la semana y el mes de esta tanda, que debe decidir qué reemplaza a los hitos, la cifra del mes y las acciones sugeridas del Mapa; la spec 027 para los términos que siguen a la ficha.

## Dudas para el dueño

Todas las dudas reales, aunque ya tengan supuesto. Las marcadas **Sí** cambian el alcance.

| # | Duda | Opciones (la recomendada primero) | ¿Cambia el alcance? |
|---|---|---|---|
| Q1 | [NEEDS CLARIFICATION: ¿qué ficha se administra desde la app? Hoy la estándar tiene campos especiales (WhatsApp con código de país, ubicación, documento, fecha de nacimiento, firma) y respuestas que usan SER y la Caja.] | A) Solo el diagnóstico es administrable; la estándar queda como hoy (recomendada para esta versión). B) Ambas; en la estándar, los campos especiales quedan protegidos (se edita su texto y orden, no se quitan). C) Ambas totalmente editables. | **Sí** |
| Q2 | [NEEDS CLARIFICATION: los objetivos que nacen del diagnóstico, ¿quedan fijos al terminar o la persona los revisa una vez?] | A) La persona los revisa y confirma una vez en Plan (puede reescribirlos) y desde ahí quedan fijos (recomendada: lo escribió en 15 minutos y puede haber errores). B) Quedan fijos al terminar el diagnóstico. C) Siempre editables. | **Sí** |
| Q3 | [NEEDS CLARIFICATION: ¿las fichas son las mismas para las tres formaciones o cada formación tiene las suyas?] | A) Las mismas dos fichas para todas, y cada pregunta puede limitarse a ciertas formaciones (recomendada: evita copiar y desfasar). B) Cada formación con sus propias fichas. C) Iguales para todas, sin poder limitar. | **Sí** |
| Q4 | Formato de importación. | A) CSV o Excel con plantilla descargable (recomendada). B) Pegar el texto de las preguntas en una caja. C) Leer el enlace del formulario de Google (frágil: depende de Google). | No (C sí cambiaría el alcance) |
| Q5 | Las preguntas 16, 17 y 18 piden en un solo párrafo «Hoy mi situación es… / En 90 días quiero haber conseguido…». ¿Se parten en dos respuestas? | A) Sí: «Hoy» y «Quiero conseguir» por separado; la segunda alimenta el objetivo (recomendada). B) Se queda un párrafo y se usa entero como objetivo (máx. 500 caracteres). | No |
| Q6 | La pregunta 23 pide aceptar la «declaración de responsabilidad y consentimiento», pero el formulario **no trae el texto** de esa declaración. ¿Cuál es? | Lo entrega el dueño (se carga como texto editable encima de la casilla). | No (pero sin el texto no se puede publicar) |
| Q7 | El diagnóstico no pregunta cómo dirigirse a la persona (lo usa SER). ¿Se suma esa pregunta? | A) Sí, una pregunta corta («¿Cómo prefieres que nos dirijamos a ti?»). B) No: trato neutro. | No |
| Q8 | ¿SER puede leer las respuestas completas del diagnóstico (ingresos, emociones, situaciones personales)? | A) No: solo objetivos, prioridad y primera acción (recomendada: sensibles). B) Sí, todo. C) Solo algunas secciones. | Sí, si B o C |
| Q9 | Las personas que ya están dentro (Escuela, a mitad de camino), ¿pueden recibir el diagnóstico después? | A) No en esta versión; escriben sus objetivos en Plan (recomendada). B) Sí: Administración se lo asigna a quien quiera. | Sí, si B |
| Q10 | El «Cuestionario Profundo» de Yo → Mi onboarding, ¿se queda? | A) Se queda como está (recomendada). B) Se retira con el Mapa. C) Se une al diagnóstico. | Sí, si B o C |
| Q11 | ¿Quién puede leer el diagnóstico de una persona? | A) Los mismos roles que hoy ven su ficha (recomendada). B) Solo Administración y Alquimista. C) También su mentor. | Sí, si cambia respecto de hoy |
| Q12 | El título del formulario dice «Diplomado Riqueza con Propósito: Maestría de Vida». Si la Escuela o la Certificación reciben el diagnóstico, ¿qué título ven? | A) El nombre de su propia formación (recomendada). B) El mismo para todos. | No |
| Q13 | Una persona con el Mapa activo, ¿sigue pudiendo ver su Mapa? | A) No: se retira de la vista; sus objetivos siguen en Plan (recomendada). B) Solo lectura en Yo. | No |
| Q14 | ¿Qué reemplaza a los hitos de 30/60/90, a la cifra del mes y a las acciones sugeridas que daba el Mapa? | Lo decide la spec de planificación; hasta entonces se ocultan. | Sí, para esa spec |
| Q15 | El diagnóstico precarga nombre y ciudad de la solicitud: ¿te parece bien no volver a preguntarlos? | A) Sí (recomendada). B) Preguntarlos igual. | No |
