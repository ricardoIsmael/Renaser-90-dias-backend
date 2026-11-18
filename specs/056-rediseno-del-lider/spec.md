# Spec 056 — Rediseño del rol de líder de mentores

**Rama**: `056-lider-de-mentores` (backend y app)

**Creada**: 2026-11-18

**Estado**: Borrador

**Input**: pedido del dueño (2026-11-18): «hacer la parte del líder de mentores como lo hicimos del mentor», más estricto y con más datos, con poco texto, accesible y reutilizando la 055. Propuesta aprobada el 18/11 sobre https://claude.ai/artifact/VuRujB92TKLSG84Csqwo1B (v12).

## Por qué

Visto en el emulador con la app de master y en la propuesta del 18/11:

- El inicio del líder abre con una caja de párrafos («Para resolver hoy») y una lista de mentores que solo dice un porcentaje; para saber quién necesita ayuda hay que entrar mentor por mentor.
- No ve el cupo de cada mentor, así que no sabe a quién darle un grupo.
- No puede crear grupos ni asignar aprendices: tiene que pedírselo a Administración.
- La ficha del aprendiz que ve el líder es distinta de la que ya ve el mentor desde la 055; la del mentor solo muestra grupos y consultas, y sus hábitos están en otra pantalla.
- La observación que el líder guarda sobre un mentor no le llega al mentor.
- No hay forma de llevarse el seguimiento de una semana en un documento.

Esta spec ordena el recorrido del líder (Mis mentores → mentor → grupo → aprendiz) para que en una pantalla vea qué hay que resolver, actúe en un toque y vea a mentores y aprendices con la misma ficha.

## Aclaraciones

### Sesión 2026-11-18 (propuesta aprobada)

- P: ¿El líder puede crear grupos y asignar aprendices a un mentor? → R: Sí; se le da el permiso de gestionar grupos (revierte la decisión CL-04 del backend).
- P: ¿La nota privada del mentor sale en el documento semanal? → R: No; solo la evaluación y lo compartido.
- P: ¿Cuánto tiempo se guardan los documentos semanales? → R: Para siempre.
- P: ¿En qué formato se guarda el documento semanal? → R: Una página HTML guardada en el almacenamiento privado que ya usan las evidencias, que se abre en el navegador y desde ahí se guarda como PDF; sin librerías nuevas.
- P: ¿Cómo es la ficha del mentor? → R: La misma ficha del aprendiz de la 055 con sus propios datos, y debajo lo suyo como mentor con el mismo estilo.
- P: ¿Las pantallas llevan textos de ayuda? → R: No: nada de guías ni párrafos de relleno; cifras, nombres y botones.

### Sesión 2026-11-18 (aclarar)

- P: ¿Qué se construye ahora? → R: Las 6 historias, en una entrega.
- P: Cuando el líder responde una consulta, ¿qué ve el aprendiz? → R: Que respondió el líder, con su nombre; el mentor también la ve.
- P: ¿El mentor puede descargar el documento semanal de sus aprendices? → R: Sí, de los que acompaña hoy, desde su ficha del aprendiz.
- P: Si el chat falla al entregar una observación, ¿se guarda? → R: No; el líder ve el motivo y la reintenta (nunca queda una observación que el mentor no recibió).
- P: ¿Cuántos avisos le llegan al mentor por una observación o un «Avisar a su mentor»? → R: Uno solo, el del mensaje del chat.
- P: ¿Cómo se calcula el cupo de un mentor? → R: Aprendices que acompaña hoy sobre la suma de los cupos de sus grupos (el servidor guarda el cupo por grupo, no por mentor).

## Escenarios de uso y pruebas *(obligatorio)*

### Historia 1 — Ver qué hay que resolver hoy (Prioridad: P1)

El líder abre «Mis mentores» y ve, sin desplazarse, lo que necesita su atención (grupos sin mentor, consultas sin respuesta, el mentor que peor va) con un botón para cada cosa, y a cada mentor con su cupo y sus aprendices por color.

**Por qué esta prioridad**: es la entrada del rol y la razón de la queja: hoy hay que leer y entrar uno por uno.

**Prueba independiente**: con un líder de prueba con 3 grupos sin mentor y 3 consultas viejas, abrir «Mis mentores» y comprobar las tres filas de «Por resolver», sus botones y la lista de mentores con su cupo.

**Escenarios de aceptación**:

1. **Dado** 3 grupos sin mentor, **cuando** el líder abre «Mis mentores», **entonces** ve la fila «3 grupos sin mentor» con el botón «Asignar», que abre Grupos filtrado en «Sin mentor».
2. **Dado** 3 consultas sin respuesta, la más vieja de hace 5 días, **cuando** abre «Mis mentores», **entonces** ve «3 consultas · 5 días» con «Recordar», que abre Consultas.
3. **Dado** un mentor con 9 de 10 cupos, 5 aprendices en rojo y 4 sin datos, **cuando** mira su fila, **entonces** ve el anillo con «9/10», los contadores con su forma (× 5, ! 0, ✓ 0, · 4) y la barra de colores.
4. **Dado** que no hay nada que resolver, **cuando** abre «Mis mentores», **entonces** «Por resolver» no aparece y la lista de mentores sube.

---

### Historia 2 — Ver la ficha de un mentor: cómo acompaña y cómo va él mismo (Prioridad: P1)

El líder toca un mentor y ve la misma ficha que el mentor ve de sus aprendices (semana, día por pilares, objetivos, 80/20), con los datos del mentor, y debajo lo suyo como mentor: grupos, evaluación, consultas y lo que el líder le dijo.

**Por qué esta prioridad**: el líder supervisa a mentores; hoy sus hábitos están en otra pantalla y lo que le dice no le llega.

**Prueba independiente**: abrir la ficha de un mentor que sigue su programa y la de uno que no, y comprobar la ficha, la sección «Como mentor» y que una observación le llega al mentor.

**Escenarios de aceptación**:

1. **Dado** un mentor que sigue su programa, **cuando** el líder abre su ficha, **entonces** ve su semana en círculos de color, el día elegido con sus hábitos por pilar, sus objetivos y su 80/20, igual que la ficha de un aprendiz.
2. **Dado** un mentor que no sigue su programa, **cuando** el líder abre su ficha, **entonces** la parte de hábitos dice en una línea que no sigue su programa, y «Como mentor» se ve completa.
3. **Dado** la ficha de un mentor, **cuando** el líder guarda «Alertar» con un texto, **entonces** el mentor recibe un aviso en su teléfono y el texto en su chat con el líder, y la fila «Lo que le dijiste» lo muestra.
4. **Dado** la ficha de un mentor, **cuando** el líder toca un grupo de «Como mentor», **entonces** se abre ese grupo.

---

### Historia 3 — Ver un grupo y la ficha de cualquier aprendiz (Prioridad: P1)

El líder abre un grupo, ve su estado por colores y a los aprendices ordenados por riesgo, y abre la ficha de cualquiera: la misma de la 055, con su evidencia, sus objetivos y su 80/20, y puede avisarle a su mentor.

**Por qué esta prioridad**: el líder ve todo lo que hace un aprendiz; hoy lo ve en una ficha distinta y más larga.

**Prueba independiente**: abrir un grupo con aprendices en los cuatro estados y la ficha de uno en rojo; ver una evidencia y usar «Avisar a su mentor».

**Escenarios de aceptación**:

1. **Dado** un grupo con 5 en rojo y 4 sin datos, **cuando** el líder lo abre, **entonces** ve la cabecera en rojo con una frase corta, los 4 contadores con forma y a los aprendices en el orden de la 055.
2. **Dado** la ficha de un aprendiz, **cuando** el líder elige un día y toca el ojo de un hábito cumplido, **entonces** se abre la hoja de la evidencia de la 055.
3. **Dado** la ficha de un aprendiz en rojo, **cuando** el líder toca «Avisar a su mentor» y envía, **entonces** el mentor recibe un aviso y un mensaje en su chat con el enlace a la ficha.
4. **Dado** un aprendiz que no está en ningún grupo, **cuando** el líder lo abre desde «Todos los aprendices», **entonces** ve la misma ficha, sin «Avisar a su mentor».

---

### Historia 4 — Armar grupos y repartir aprendices (Prioridad: P2)

El líder crea un grupo, elige su mentor viendo el cupo de cada uno, le suma aprendices sin grupo y puede mover aprendices o cambiar el mentor de un grupo.

**Por qué esta prioridad**: hoy depende de Administración; con el permiso nuevo lo resuelve él.

**Prueba independiente**: crear un grupo de 2 aprendices con un mentor con cupo, confirmar el resumen y ver el grupo en la lista; cambiar su mentor.

**Escenarios de aceptación**:

1. **Dado** Grupos, **cuando** el líder toca «Crear grupo», **entonces** recorre 3 pasos (nombre, mentor, aprendices) con una decisión por pantalla.
2. **Dado** el paso del mentor, **cuando** un mentor tiene el cupo lleno, **entonces** se ve apagado y no se puede elegir.
3. **Dado** el paso de aprendices, **cuando** el líder marca 2 y confirma, **entonces** ve un resumen (nombre, mentor, aprendices) y, al aceptar, el grupo queda creado y aparece en «Activos».
4. **Dado** un grupo sin mentor, **cuando** el líder toca «Asignar» y elige un mentor, **entonces** el grupo pasa a «Activos» con ese mentor.

---

### Historia 5 — Llevarse la semana en un documento (Prioridad: P2)

Desde la ficha de un aprendiz o de un mentor, el líder descarga el documento de una semana para guardarlo o compartirlo.

**Por qué esta prioridad**: el dueño lo pidió para el seguimiento y las reuniones con cada mentor.

**Prueba independiente**: descargar la semana de un aprendiz, abrir el enlace y comprobar las secciones; volver a descargarla sin cambios y comprobar que es el mismo documento.

**Escenarios de aceptación**:

1. **Dado** la ficha de un aprendiz, **cuando** el líder toca «Descargar semana», **entonces** recibe un enlace que abre el documento de esa semana con las secciones en el orden de FR-035.
2. **Dado** un documento ya generado y sin cambios en los datos, **cuando** se pide otra vez, **entonces** se entrega el mismo, sin volver a generarlo.
3. **Dado** un enlace entregado, **cuando** pasan 10 minutos, **entonces** el enlace ya no abre el documento, y pedirlo de nuevo da otro enlace.
4. **Dado** un mentor que pide el documento de un aprendiz que no acompaña, **cuando** lo pide, **entonces** recibe una negativa con su motivo.

---

### Historia 6 — Destrabar consultas, ver el mes y programar una sesión (Prioridad: P3)

El líder ve las consultas sin responder y le recuerda al mentor o responde él; ve el reporte del mes de sus mentores; y programa una sesión con un mentor, su grupo o todos los mentores desde la ficha.

**Prueba independiente**: recordar una consulta y ver el mensaje en el chat del mentor; responder otra; programar una sesión con Meet desde la ficha de un mentor.

**Escenarios de aceptación**:

1. **Dado** una consulta sin respuesta, **cuando** el líder toca «Recordar al mentor», **entonces** el mentor recibe en su chat con el líder un mensaje con la consulta.
2. **Dado** una consulta sin respuesta, **cuando** el líder la responde, **entonces** pasa a «Respondidas» y el aprendiz recibe la respuesta.
3. **Dado** la ficha de un mentor, **cuando** el líder toca «Programar sesión», **entonces** se abre la hoja con el mentor ya elegido como audiencia, y al programarla queda en el calendario con su enlace de Meet.

---

### Casos límite

- Líder sin mentores a cargo o sin formaciones activas: «Mis mentores» lo dice en una línea, sin huecos.
- Mentor suspendido: su fila se ve apagada y sin acciones; su ficha solo dice que la cuenta está suspendida.
- Mentor que no sigue su programa: la parte de hábitos lo dice (regla #202) y «Descargar semana» entrega solo el resumen como mentor.
- Grupo sin aprendices: lo dice en una línea; no hay lista vacía.
- Aprendiz en Día 0 o suspendido: la ficha lo dice arriba y la semana va sin color.
- Crear un grupo con un nombre repetido o vacío: el campo lo dice antes de avanzar.
- Dos líderes asignan el mismo aprendiz o el mismo cupo a la vez: el segundo recibe la negativa del servidor con su motivo y la lista se relee.
- Toques repetidos en «Asignar», «Crear», «Alertar», «Avisar», «Descargar semana» o «Recordar»: un toque, un pedido.
- Semana sin datos: el documento dice «Sin actividad esta semana» en lugar de tablas vacías.
- Sin internet: lo ya cargado se sigue viendo; las acciones dicen que hace falta conexión y no pierden lo escrito.
- Personas en otra zona horaria: días y horas son los de cada persona.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Mis mentores**

- **FR-001**: «Mis mentores» no muestra la insignia del rol en la cabecera.
- **FR-002**: Arriba, «Por resolver» muestra hasta tres filas, cada una con un símbolo de estado, una cifra corta y un botón: grupos sin mentor → «Asignar»; consultas sin respuesta con los días de la más vieja → «Recordar»; el mentor con la peor evaluación del mes por debajo del 60 % → «Ver». Una fila sin nada que resolver no se muestra; sin ninguna, la sección no aparece.
- **FR-003**: Cada mentor se muestra con un anillo de cupo con el número escrito («n/máx», donde n son los aprendices que acompaña hoy y máx la suma de los cupos de sus grupos), su nombre, la cantidad de sus aprendices en cada estado con su forma (× rojo, ! ámbar, ✓ verde, · sin datos) y una barra de colores proporcional; ordenados por urgencia (más aprendices en rojo primero).
- **FR-004**: Un mentor sin grupo dice «Sin grupo» con su cupo libre; un mentor suspendido se ve apagado, sin anillo ni acciones.
- **FR-005**: Al pie, accesos a Grupos, Consultas (con su cantidad), Reporte del mes y Todos los aprendices.

**Grupos**

- **FR-006**: Grupos lista todos los grupos de las formaciones del líder con filtros «Sin mentor», «Activos» y «Llenos»; abre en «Sin mentor» si hay alguno, si no en «Activos».
- **FR-007**: Cada grupo muestra el anillo de cupo, su nombre y, si tiene mentor, sus aprendices por color; si no tiene mentor, el botón «Asignar».
- **FR-008**: «Crear grupo» abre un asistente de 3 pasos: nombre; mentor (cada uno con su cupo; los que tienen el cupo lleno, apagados y sin poder elegirse); aprendices sin grupo, con buscador y casillas. Antes de crear muestra un resumen para confirmar.
- **FR-009**: El líder puede asignar o cambiar el mentor de un grupo, sumar aprendices sin grupo y quitar aprendices de un grupo; cada cambio se confirma antes de guardarse.
- **FR-010**: El líder tiene el permiso de gestionar grupos en el servidor, limitado a las formaciones que tiene activas.

**Grupo**

- **FR-011**: La cabecera del grupo muestra su nombre, su mentor, su estado con su forma y una frase corta (por ejemplo «Grupo en rojo · 0 % en 7 días»).
- **FR-012**: Debajo, cuatro contadores con forma (rojo, ámbar, verde, sin datos) y los aprendices ordenados como en la 055 (FR-004 de la 055), cada uno con el borde de su color, su estado con su forma y una línea con su motivo.
- **FR-013**: El grupo tiene los botones «+ Aprendices» y «Cambiar mentor».

**Ficha del aprendiz (vista del líder)**

- **FR-014**: La ficha del aprendiz que abre el líder es la de la 055 (FR-010 a FR-022 de la 055): cabecera con el borde de su color, nota y chat; semana en círculos; mes; día por pilares; evidencia con el ojo y su hoja.
- **FR-015**: El ícono de nota abre las notas privadas del mentor solo para leer (FR-024 de la 055).
- **FR-016**: Debajo de los pilares, en el mismo estilo plegable: objetivos con su avance, caja, Código Renaser, 80/20 de SER, tiempo libre y cierres, y la evaluación de su mentor.
- **FR-017**: El líder ve los días del aprendiz desde su día 1, esté o no en un grupo, siempre que sea de una formación que el líder tiene activa.
- **FR-018**: El color de cada día que ve el líder es el mismo estado diario que ve el mentor.
- **FR-019**: «Avisar a su mentor» abre una hoja con un mensaje sugerido editable; al enviar, el mentor recibe el mensaje en su chat con el líder, con el enlace a la ficha, y el aviso de ese mensaje en su teléfono (un solo aviso). Sin mentor, el botón no se muestra.
- **FR-020**: La ficha tiene «Descargar semana» (FR-039).

**Ficha del mentor**

- **FR-021**: La ficha de un mentor que sigue su programa es la misma ficha del aprendiz (FR-014 a FR-018) con los datos del mentor: su semana, sus días por pilar, sus evidencias, sus objetivos, su 80/20, su tiempo libre y sus cierres. El mentor no cuenta en el semáforo de ningún grupo.
- **FR-022**: Si el mentor no sigue su programa, en lugar de la semana y los pilares va una línea que lo dice (regla #202); si su cuenta está suspendida, la ficha solo dice eso.
- **FR-023**: En la cabecera, en lugar de la nota, una estrella que abre «Reconocer, sugerir o alertar», y el chat.
- **FR-024**: Debajo, la sección «Como mentor» con el mismo estilo plegable: sus grupos (cada uno con su barra de colores), su evaluación del mes, sus consultas sin responder con los días, y «Lo que le dijiste». (Las «evaluaciones que debe» se quitaron en el E2E del 18/11: el servidor no tiene ese dato; lo que había eran las evidencias esperadas de sus aprendices.)
- **FR-025**: Al guardar un reconocimiento, una sugerencia o una alerta, el mentor recibe el texto en su chat con el líder y el aviso de ese mensaje (un solo aviso); si el mensaje no se puede entregar, la observación no se guarda y el líder ve el motivo. La observación queda en «Lo que le dijiste».
- **FR-026**: La ficha del mentor tiene «Descargar semana» (FR-039), «Alertar» y «Programar sesión» (FR-033).

**Objetivos**

- **FR-027**: El líder puede leer los objetivos y su avance de cualquier aprendiz o mentor de sus formaciones; solo leer.

**Consultas**

- **FR-028**: Consultas muestra «Sin responder» y «Respondidas»; cada consulta dice quién pregunta, a qué mentor le toca y hace cuántos días.
- **FR-029**: «Recordar al mentor» le manda al mentor, en su chat con el líder, un mensaje con la consulta.
- **FR-030**: «Responder yo» deja al líder responder la consulta; el aprendiz recibe la respuesta con el nombre del líder, el mentor la ve en su bandeja y la consulta pasa a «Respondidas».

**Reporte del mes**

- **FR-031**: El reporte del mes muestra el promedio de los mentores en un anillo, la cantidad debajo del 60 % y el ranking de mentores con el borde de su color y sus consultas respondidas.
- **FR-032**: «Exportar el reporte» entrega el documento del mes con el mismo mecanismo de FR-039.

**Programar sesión**

- **FR-033**: «Programar sesión» abre una hoja desde la ficha del mentor con la audiencia ya elegida (ese mentor), que se puede cambiar a su grupo o a todos los mentores; con fecha, hora, duración y Meet automático.
- **FR-034**: La sesión se crea igual que una sesión personalizada de Eventos y aparece en el calendario de sus invitados con su enlace.

**Documento semanal**

- **FR-035**: El documento semanal del aprendiz trae, en este orden: encabezado (nombre, su formación y su día —nunca un plazo fijo escrito—, mentor, grupo, semana y fecha de generación); titular con el estado de la semana y una frase; cuatro cifras comparadas con la semana anterior (hábitos cumplidos, días activos, evidencias, días programados); tabla pilar × día; evidencias de la semana (tipo, fecha y estado); «En sus palabras»; la evaluación del mes de su mentor; un foco para la próxima semana; y una segunda página con el detalle día por día.
- **FR-036**: El documento semanal del mentor trae: encabezado; titular de su grupo; conteo por color; aprendices ordenados por riesgo con su tendencia; sus pendientes (evaluaciones, consultas, mensajes a aprendices en rojo); alertas (aprendices con 3 o más días sin actividad); lo que le dijo el líder; y el foco de la semana. Si el mentor sigue su programa, suma su propia semana como en FR-035.
- **FR-037**: La nota privada del mentor nunca sale en un documento.
- **FR-038**: Una semana sin datos dice «Sin actividad esta semana» en lugar de tablas vacías.
- **FR-039**: «Descargar semana» genera el documento en el servidor, lo guarda en el almacenamiento privado y entrega un enlace que vence a los 10 minutos; si el documento de esa persona y semana ya existe y sus datos no cambiaron, se reutiliza.
- **FR-040**: Los documentos se guardan para siempre y quedan registrados quién los generó y quién los descargó, y cuándo.
- **FR-041**: Pueden pedir el documento de un aprendiz el líder de su formación, Administración y el mentor que lo acompaña hoy; el de un mentor, el líder y Administración. Cualquier otro recibe una negativa con su motivo.

**Comunes**

- **FR-042**: Todo lo que el líder lee o cambia lo autoriza el servidor por sus formaciones activas; quien no corresponde recibe una negativa con su motivo y la app lo muestra.
- **FR-043**: El estado del semáforo nunca se muestra solo con color: siempre con su forma y su símbolo, y con una etiqueta para el lector de pantalla.
- **FR-044**: Toda acción tiene un botón visible de 44 a 48 de alto; deslizar, si existe, es solo un atajo.
- **FR-045**: Las pantallas del líder no tienen textos de ayuda ni párrafos de relleno: cifras, nombres, estados y botones.
- **FR-046**: Toda subpantalla, hoja o paso del asistente responde al gesto de volver del sistema yendo un nivel atrás; nunca cierra la app.
- **FR-047**: La ficha del aprendiz que ve el mentor (055) suma «Descargar semana» para los aprendices que acompaña hoy.

### Entidades clave

- **Grupo** (ya existe): nombre, formación, mentor, aprendices y cupo.
- **Cupo del mentor** (derivado): aprendices que acompaña hoy sobre la suma de los cupos de sus grupos; no se guarda.
- **Observación del líder** (ya existe): reconocimiento, sugerencia o alerta sobre un mentor, con autor y fecha; ahora además se le entrega al mentor.
- **Consulta** (ya existe): pregunta de un aprendiz a su mentor, con estado y respuesta.
- **Documento semanal** (nuevo): persona, semana, tipo (aprendiz o mentor), dónde está guardado, una huella de los datos con que se generó y la fecha de generación.
- **Registro de descargas** (nuevo): documento, quién lo pidió, cuándo y si lo generó o lo reutilizó.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: Al abrir «Mis mentores», el líder ve «Por resolver» y al menos 3 mentores en la primera pantalla, sin desplazarse (teléfono de 1080 × 2400).
- **SC-002**: Desde «Mis mentores», resolver un grupo sin mentor (elegir y confirmar el mentor) toma como mucho 3 toques.
- **SC-003**: Crear un grupo con su mentor y 2 aprendices toma menos de 1 minuto y como mucho 3 pantallas más el resumen.
- **SC-004**: La ficha del aprendiz que ve el líder y la que ve su mentor muestran los mismos días, colores, hábitos y evidencias para la misma persona y fecha.
- **SC-005**: El 100 % de las observaciones del líder y de los «Avisar a su mentor» llegan al mentor como aviso y como mensaje en su chat.
- **SC-006**: Descargar la semana de una persona entrega el enlace en menos de 10 segundos, y la segunda vez sin cambios, en menos de 3.
- **SC-007**: Ningún documento contiene la nota privada del mentor, y un rol sin permiso no obtiene ningún documento (verificado por rol).
- **SC-008**: Ningún estado del semáforo en las pantallas del líder depende solo del color (revisión de cada pantalla).

## Lo que no cubre

- La fase 2 de la 055 (objetivos en la ficha del mentor, «Mi evaluación» con «Qué sigue», historial de grupos y aviso de rojo del mentor) sigue aparte; esta spec solo hace que el líder lea los objetivos.
- No cambia cómo se calcula el semáforo ni sus colores.
- Grabaciones y notas de Meet no se traen a la app (decisión del 18/11).
- El tope de tamaño de letra de la app (1,2) no cambia en esta spec.

## Supuestos

- Se reutiliza lo que ya existe: las lecturas del líder sobre una persona (semana, cierres, tiempo libre, Código Renaser, 80/20, diagnóstico), la ficha y la hoja de evidencia de la 055, la gestión de grupos de Administración, las consultas, el reporte del mes, la observación, la conversación 1 a 1, los avisos al teléfono, la creación de sesiones de Eventos y el almacenamiento privado con enlaces que vencen.
- Lo nuevo de servidor: el permiso de gestionar grupos para el líder, leer objetivos de otra persona, los colores de cada día para el líder, que la observación y el aviso lleguen al mentor, responder consultas, y generar, guardar y entregar el documento semanal.
- El documento es una página que el navegador guarda como PDF; si más adelante hace falta el PDF directo, se agrega sin cambiar la spec.
- «Peor evaluación» en «Por resolver» es la más baja del mes por debajo del 60 %, el mismo umbral del reporte del mes.
- Cambian las pantallas del líder dentro del tab Yo («Mis mentores»): cambio pedido y aprobado por el dueño el 18/11 (se anota en `docs/decisiones/` de la app).
