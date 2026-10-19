# Feature Specification: El líder de mentores ve el cumplimiento de cada persona por grupo

**Feature Branch**: `005-lider-cumplimiento-por-persona` (backend y app, cada repo en su rama con este nombre)

**Created**: 2026-10-19

**Status**: Draft

**Input**: User description: "El Líder de Mentores ve el cumplimiento de cada persona por grupo. Decisiones
del dueño (19/10) en `docs/pendientes/PARA_EL_LUNES_2026-10-19.md` del frontend, sección «Decisiones del
dueño del 19 de octubre». Spec que cruza los dos repos: vive en el backend."

**Repos**: backend (`Renaser-90-dias-backend`, esta spec) y app (`Renaser-90-dias-frontend-`). La spec vive
solo acá; el frontend la referencia desde `specs/README.md` sin copiarla.

## Contexto

Hoy el líder de mentores ve en Hoy el **semáforo por grupos**: una tarjeta por grupo con su mentor, cuántos
aprendices hay en cada color y el promedio, **sin nombres** y sin poder abrir un grupo. Así lo fijó RL-07 del
SDD del líder (`specs/002-lider-de-mentores/` del frontend): «en esta entrega no se construye» el expediente
individual. RL-07 ya aclaraba que era un límite de **alcance**, no una prohibición: la especificación del cliente
(§2.3) sí le concede auditar expedientes.

El 17 de octubre el dueño pidió que el líder pueda bajar del grupo a la persona. El 19 de octubre respondió las
preguntas de diseño (abajo). El resultado amplía RL-07: para esta vista, el líder ve nombres, cumplimiento y
evidencias, y puede actuar sobre lo que ve avisándole al mentor o escribiéndole a la persona.

## Clarifications

### Session 2026-10-19

Respuestas del dueño del producto, en la conversación de diseño:

- Q: ¿Por dónde entra el líder a ver a una persona? → A: Desde el **semáforo por grupos**: tocar un grupo lista
  a sus personas.
- Q: La decisión del 01/10 era «ningún aprendiz por su nombre». ¿Qué ve ahora? → A: Nombre y **todas las
  evidencias** de los hábitos de cada persona.
- Q: ¿Qué período? → A: La **semana del semáforo** (de sábado a viernes), día por día, con selector para ir a
  semanas anteriores.
- Q: ¿Qué grupos alcanza? → A: **Todos** los grupos con mentor, como el padrón que ya ve.
- Q: ¿Cómo se ve cada persona en la lista del grupo? → A: Nombre, **color del semáforo** de la semana y **% de
  hábitos cumplidos**, con los rojos primero.
- Q: ¿Qué puede hacer desde la ficha, además de mirar? → A: **Avisar a su mentor** (una observación sobre la
  persona) y **escribirle a la persona** (chat 1 a 1).
- Q: ¿Qué personas aparecen? → A: Los aprendices **y también** quien lleve su propio programa de 90 días en ese
  grupo (el mentor o la guía de aprendiz).
- Q: ¿La persona o su mentor deben saber que el líder abrió la ficha? → A: **Avisar al mentor**, con un
  **resumen una vez al día** («Hoy tu líder revisó a …»), no un aviso por cada ficha abierta.
- Q: ¿Qué evidencias ve? → A: **Todas**: las de los hábitos (foto, texto, audio, video y «¿Qué sentiste?»), las
  de las acciones de los objetivos, las respuestas de la Pastilla y la Audioterapia, y el post diario del Muro.
- Q: ¿Soporte TI la apaga aparte? → A: Sí, con una **clave de funcionalidad propia**, sin apagar el resto del
  líder.

### Session 2026-10-19 (clarify)

- Q: ¿A qué hora le llega al mentor el resumen diario? → A: A las 20:00 del mismo día, en la zona del mentor.
- Q: ¿Administración y Alquimista también pueden abrir los grupos de su semáforo por grupos? → A: Sí, los tres
  roles. Las consultas de Administración y Alquimista no entran en el resumen al mentor.
- Q: ¿Incluye el grupo de bienvenida (sin mentor)? → A: No, solo los grupos con mentor.
- Q: ¿Cuánto se guarda el registro de consultas? → A: Siempre: no se edita ni se borra (auditoría).
- Q: (plan) Si el mentor no lleva programa propio, ¿en qué zona le llega el resumen? → A: La de su grupo
  (confirmado por el dueño, R-8).
- Q: (plan) ¿«Avisar a su mentor» es una observación nueva o la de siempre? → A: La de siempre, con la persona y la
  semana (confirmado por el dueño, R-7).

### Session 2026-10-19 (post-E2E)

- Q: En el E2E el resumen decía «Últimos 7 días · del lunes 12 al domingo 18» y, al tocar un grupo, la lista decía «Semana
  cerrada · del sábado 10 al viernes 16»; los porcentajes cambiaban (Rocío: 50 % y 47,6 %). ¿Qué ventana manda? → A: **La
  ventana del resumen manda en todo.** Al tocar un grupo, la lista y después la ficha de la persona abren en los MISMOS 7 días
  que mostraba el resumen. Las flechas ‹ › mueven la ventana igual en las tres pantallas, de 7 en 7 días, y ninguna llega al
  futuro. Es lo que ya hace Administración (`SemaforoGrupoAdminScreen`: «abre en la misma semana que se estaba mirando en el
  resumen»). Las fichas del mentor y de Administración (lunes a domingo) no cambian.

> **Corregido 2026-10-19.** Las respuestas de «Session 2026-10-19» decían «La **semana del semáforo** (de sábado a viernes)»
> como período de la ficha y R-2 decía que solo el líder iba de sábado a viernes. Esa semana ya no es el período por defecto:
> lo es la ventana vigente del resumen (los 7 días que terminan ayer); la semana de sábado a viernes sigue existiendo, pero como
> una de las ventanas a las que se llega con ‹ desde ahí.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Del grupo a la persona (Priority: P1)

El líder abre «Semáforo por grupos» en Hoy, toca un grupo y ve la lista de sus personas con el color de la
semana y el porcentaje cumplido, primero las que están en rojo. Toca a una persona y ve su semana día por día:
qué hábitos cumplió, cuáles quedaron pendientes o vencidos, y cuáles no le tocaban. Puede ir a semanas
anteriores.

**Why this priority**: Es el pedido del dueño. Sin esto el líder sabe que un grupo va mal pero no sabe quién
ni en qué, y no puede ayudar al mentor.

**Independent Test**: Con una cuenta de líder y un grupo con tres aprendices en distintos colores, abrir el
grupo, comprobar el orden y los porcentajes, abrir a una persona y comparar día por día con lo que el
administrador ve en la ficha de esa misma persona.

**Acceptance Scenarios**:

1. **Given** un líder y un grupo con aprendices, **When** toca la tarjeta del grupo, **Then** ve a cada persona
   con su nombre, el color del semáforo de esa semana y el % cumplido, ordenadas de rojo a verde y, dentro de
   cada color, de menor a mayor %.
2. **Given** la lista del grupo, **When** toca a una persona, **Then** ve la semana seleccionada día por día con
   el estado de cada hábito, en palabras y en forma (no solo en color), igual que la ficha de administración.
3. **Given** la ficha de una persona, **When** pasa a la ventana anterior, **Then** ve esos días tal como fueron
   (los hábitos que tenía ese día, no los de hoy), y no puede pasar de la ventana del resumen hacia el futuro.
4. **Given** el resumen «Semáforo por grupos» abierto en una ventana, **When** el líder toca un grupo y luego una persona,
   **Then** el resumen, la lista y la ficha muestran los mismos 7 días con el mismo rótulo, y la misma persona tiene el mismo
   color y el mismo % en la lista y en la ficha (y cuenta igual en el resumen).
5. **Given** un mentor o una guía con su propio programa en ese grupo, **When** el líder abre el grupo, **Then**
   aparece en la lista con su % y con «No se mide en el semáforo» en lugar de un color.
6. **Given** un grupo sin nadie que mostrar, **When** el líder lo abre, **Then** ve «Este grupo todavía no tiene
   personas» y no una lista vacía muda.

---

### User Story 2 - Ver las evidencias (Priority: P1)

En la ficha de la persona, el líder abre cualquier evidencia de la semana: la foto, el texto, el audio o el
video de un hábito con su «¿Qué sentiste?», la foto de una acción de objetivos, lo que respondió en la Pastilla
o la Audioterapia, o la publicación del Muro con la que cumplió el post diario.

**Why this priority**: El dueño lo pidió explícitamente («deben de ver todas las evidencias»). Un porcentaje sin
evidencia no le dice al líder si el cumplimiento es real.

**Independent Test**: Con una persona que tenga en la misma semana una evidencia de cada tipo, abrir cada una
desde la ficha del líder y comprobar que es la misma que ve el administrador.

**Acceptance Scenarios**:

1. **Given** un hábito cumplido con foto, **When** el líder toca «Ver evidencia», **Then** se abre la foto y
   debajo se lee lo que la persona escribió.
2. **Given** un hábito cumplido con texto, audio o video, **When** lo abre, **Then** lee el texto, o escucha o ve
   el archivo, sin descargarlo a mano.
3. **Given** la Pastilla o la Audioterapia cumplida, **When** la abre, **Then** lee las respuestas a las
   preguntas fijas.
4. **Given** el post diario cumplido al publicar en el Muro, **When** lo abre, **Then** ve esa publicación.
5. **Given** una acción de objetivos cumplida con foto, **When** la abre, **Then** ve esa foto.
6. **Given** un hábito cumplido sin evidencia (porque no la exige), **When** lo mira, **Then** dice «Cumplido ·
   sin evidencia» y no ofrece un botón que no abre nada.

---

### User Story 3 - Avisar al mentor o escribirle a la persona (Priority: P2)

Desde la ficha, el líder puede dejarle una observación al mentor del grupo sobre esa persona («Ana lleva tres
días sin el ritual de la mañana») o abrir el chat 1 a 1 con la persona.

**Why this priority**: Convierte la consulta en ayuda. Se apoya en dos cosas que ya existen (observaciones al
mentor y chat directo), así que es más barata que P1, pero no tiene sentido sin P1.

**Independent Test**: Desde la ficha de una persona, dejar una observación y comprobar que el mentor la recibe
nombrando a la persona; abrir el chat y comprobar que es el 1 a 1 con ella, sin ningún mensaje enviado
automáticamente.

**Acceptance Scenarios**:

1. **Given** la ficha de una persona con mentor, **When** el líder elige «Avisar a su mentor», escribe y
   confirma, **Then** queda una observación al mentor con el tipo (reconocimiento, sugerencia o alerta), que
   nombra a la persona y la semana, y con la opción de enviarla también por el chat, como las observaciones de
   hoy.
2. **Given** la ficha, **When** el líder elige «Escribirle», **Then** se abre (o se reutiliza) el chat 1 a 1 con
   la persona, vacío y listo para escribir. No se envía nada en su nombre.
3. **Given** una persona cuyo grupo no tiene mentor, **When** el líder abre la ficha, **Then** «Avisar a su
   mentor» no aparece.

---

### User Story 4 - El mentor sabe que su líder revisó a su gente (Priority: P3)

Una vez al día, si el líder abrió la ficha de alguien de su grupo, el mentor recibe un solo aviso: «Hoy tu líder
revisó a Ana y Luis».

**Why this priority**: Es la transparencia que eligió el dueño. No bloquea nada de lo anterior.

**Independent Test**: Con un líder que abre tres fichas de dos grupos en un día, comprobar que cada mentor recibe
un solo aviso con solo sus personas, y que un día sin consultas no genera aviso.

**Acceptance Scenarios**:

1. **Given** que el líder abrió la ficha de dos personas del grupo de un mentor, **When** llegan las 20:00 en la
   zona del mentor, **Then** el mentor recibe un solo aviso que nombra a las dos.
2. **Given** que el líder abrió la misma ficha cinco veces, **When** llega el resumen, **Then** la persona
   aparece una vez.
3. **Given** un día sin consultas a su grupo, **Then** el mentor no recibe nada.
4. **Given** que el mentor apagó ese tipo de aviso en sus preferencias, **Then** no lo recibe.
5. **Given** que solo Administración o Alquimista abrieron fichas de su grupo ese día, **Then** el mentor no recibe
   aviso.

---

### Edge Cases

- **Falta de datos no es incumplimiento**: un día sin registros (antes del Día 1, en pausa, cuenta suspendida,
  día sin hábitos programados) se muestra con su motivo en palabras, nunca como ✗ ni bajando el %.
- **Persona que cambió de grupo a mitad de semana**: aparece en el grupo donde está hoy. Su semana muestra todos
  sus hábitos, sin importar en qué grupo estaba cada día.
- **Respuesta lenta, error o sin conexión**: la lista y la ficha muestran su estado de carga y, si fallan,
  «No pudimos cargar…» con «Reintentar». Al cambiar de persona o de semana nunca se ve, ni por un instante, el
  dato de la persona o la semana anterior.
- **Cuentas de prueba**: nunca aparecen en las listas.
- **El propio líder en un grupo** (el líder también hace su programa): si está en un grupo, aparece en la lista
  como cualquier otra persona con programa. No se avisa a nadie por consultarse a sí mismo.
- **Evidencia borrada o archivo que ya no existe**: la evidencia dice «Ya no está disponible» y el resto de la
  ficha sigue.
- **Funcionalidad apagada por Soporte TI**: la tarjeta del grupo vuelve a no abrirse, y una ficha abierta deja de
  cargar con el aviso de mantenimiento de siempre.
- **Persona que eliminó su cuenta** (en plazo de recuperación): no aparece en la lista.

## Requirements *(mandatory)*

### Functional Requirements

**Alcance y permisos**

- **FR-001**: El sistema MUST permitir al líder de mentores consultar, de cualquier grupo con mentor, la lista de
  sus personas y la ficha de cumplimiento de cada una. Administración y Alquimista ya lo hacen en Administración →
  Semáforo → grupo → persona (desde el 2026-09-26). Ese recorrido no cambia y también pueden usar esta vista. El grupo de bienvenida (sin mentor) queda fuera.
- **FR-002**: Las personas listadas MUST ser las del grupo con rol aprendiz y las que lleven su propio programa de
  90 días en él (mentor o guía de aprendiz). MUST quedar fuera las cuentas de prueba y las cuentas cerradas.
- **FR-003**: El acceso nuevo del líder MUST ser solo de lectura sobre la persona: no edita hábitos, no registra
  en nombre de nadie, no aprueba ni rechaza evidencias, no reasigna personas ni cambia roles. El servidor MUST
  rechazar esas escrituras para el líder aunque se intenten directamente.
- **FR-004**: El alcance del mentor (solo los grupos que acompaña) y el de administración MUST NOT cambiar.
- **FR-005**: Toda lectura nueva MUST poder apagarse desde Soporte TI con una clave de funcionalidad propia,
  independiente de la del resto de las pantallas del líder.

**Lista del grupo**

- **FR-006**: Cada persona MUST mostrar su nombre (foto o iniciales), el color del semáforo de la semana
  seleccionada y el % de hábitos cumplidos de esa semana. Si no se mide en el semáforo, «No se mide en el
  semáforo» en lugar del color.
- **FR-007**: La lista MUST ordenarse de rojo a verde, después «sin datos» y «no se mide»; dentro de cada grupo,
  de menor a mayor %.
- **FR-008**: El color y el % MUST salir del mismo cálculo que ya usan el semáforo y la ficha de administración,
  sin una fórmula nueva.

**Ficha de la persona**

- **FR-009**: La ficha MUST mostrar, día por día, la **misma ventana de 7 días que mostraba el resumen «Semáforo por grupos»**
  desde el que se llegó (la vigente, o una semana de sábado a viernes), con el estado de cada hábito que la persona tenía ese
  día, en palabras y no solo en color. La lista del grupo MUST abrir en esa misma ventana y MUST rotularla con el mismo texto.
  Con la misma fecha, el resumen, la lista y la ficha MUST dar el mismo color y el mismo % para la misma persona.
- **FR-010**: Las flechas ‹ › MUST mover la ventana igual en las tres pantallas (de la vigente a la última semana cerrada y de
  ahí de 7 en 7 días hacia atrás), hasta el inicio del programa de la persona. Ninguna llega al futuro: el servidor responde 400
  a un viernes que todavía no llegó.

> **Corregido 2026-10-19.** FR-009 decía «la semana del semáforo (de sábado a viernes)» y FR-010 «volver a la semana en
> curso». El dueño decidió tras el E2E que la ventana del resumen manda en todo (ver «Session 2026-10-19 (post-E2E)»): ya no
> hay «semana en curso» que mostrar, porque la ventana vigente termina ayer.
- **FR-011**: La ficha MUST mostrar, para cada hábito cumplido con evidencia, una forma de abrirla: foto, texto,
  audio, video, «¿Qué sentiste?», respuestas de la Pastilla y la Audioterapia, la publicación del Muro del post
  diario y la foto de las acciones de objetivos del día.
- **FR-012**: El enlace a un archivo de evidencia MUST pedirse al momento de abrirlo y vencer en poco tiempo,
  como en la ficha del mentor y la de administración.
- **FR-013**: La ficha MUST NOT mostrar el Mapa de Renacimiento, los objetivos escritos, los chats ni los datos
  de la ficha inicial de la persona. Solo cumplimiento y evidencias.

**Acciones**

- **FR-014**: Desde la ficha, el líder MUST poder dejar una observación al mentor del grupo sobre esa persona,
  con las mismas reglas de las observaciones de hoy (tipo, texto, registro permanente, envío opcional por chat),
  y la observación MUST nombrar a la persona y la semana.
- **FR-015**: Desde la ficha, el líder MUST poder abrir o reutilizar el chat 1 a 1 con la persona, sin enviar
  ningún mensaje automático.

**Registro y aviso al mentor**

- **FR-016**: El sistema MUST registrar cada vez que el líder, Administración o Alquimista abre la ficha de una
  persona desde esta vista (no desde el recorrido de Administración, que no cambia): quién, con qué rol, a quién, de qué grupo y cuándo. El registro se guarda siempre: no
  se edita ni se borra.
- **FR-017**: A las 20:00 de cada día, en la zona del mentor, el sistema MUST enviar a cada mentor un solo aviso
  con las personas de sus grupos cuya ficha abrió **el líder de mentores** ese día, sin repetir a nadie. Las
  consultas de Administración y Alquimista no entran en el aviso. Sin consultas del líder, no hay aviso.
- **FR-018**: El aviso MUST respetar las preferencias de notificación del mentor, y tocarlo MUST abrir «Mi
  grupo».

**Estados y app**

- **FR-019**: La lista y la ficha MUST tener estados de carga, vacío, sin datos (con su motivo) y error con
  «Reintentar». La falta de datos MUST NOT presentarse como incumplimiento.
- **FR-020**: Las cinco pestañas MUST NOT cambiar. El cambio en Hoy se limita a que la tarjeta de cada grupo del
  semáforo por grupos (el del líder y el de Administración) se abra; se anota como excepción en `AGENTS.md` del
  frontend.
- **FR-021**: Contra un backend anterior (sin la lectura nueva), la app MUST seguir mostrando el semáforo por
  grupos como hoy, con las tarjetas sin abrirse.

### Key Entities

- **Persona del grupo**: alguien que pertenece a un grupo y hace el programa (aprendiz, mentor o guía con
  programa propio). Lleva nombre, foto, color de la semana (o «no se mide») y % cumplido.
- **Ventana de cumplimiento**: los siete días del resumen «Semáforo por grupos» (los que terminan ayer, o una semana de
  sábado a viernes) con los hábitos que la persona tenía cada día, su estado y su evidencia. Cada día coincide con el que ven
  el mentor y el administrador; sus fichas siguen mostrando la semana de lunes a domingo.
- **Evidencia**: lo que respalda un cumplimiento: archivo (foto, audio, video), texto, respuestas guiadas, o la
  publicación del Muro.
- **Consulta del líder**: quién consultó a quién, de qué grupo y cuándo. Alimenta el resumen diario.
- **Observación sobre una persona**: una observación al mentor (la que ya existe) que además referencia a la
  persona y la semana.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde Hoy, el líder llega a la semana de una persona concreta en tres toques o menos (semáforo
  por grupos → grupo → persona).
- **SC-002**: Para la misma persona y el mismo día, el estado de cada hábito y cada evidencia que ve el líder
  coinciden al 100 % con lo que ve el administrador.
- **SC-003**: El 100 % de los intentos de escritura del líder sobre una persona (completar, aprobar, editar,
  reasignar, cambiar rol) son rechazados por el servidor.
- **SC-004**: Un mentor recibe como máximo un aviso de consultas por día, y nunca uno de un día sin consultas.
- **SC-005**: Con conexión lenta o cortada, la lista y la ficha nunca quedan en blanco ni muestran datos de otra
  persona o de otra semana: siempre hay carga, dato o error con «Reintentar».
- **SC-006**: Ninguna cuenta de prueba aparece en ninguna lista del líder.

## Assumptions

- **El % de la lista** es el de la ventana que se está mirando (la del resumen al entrar) y el color es el del semáforo de
  esa misma ventana; la ficha de la persona abre en la misma, con la misma cifra.
- **Mentor y guía con programa propio** no tienen color porque el semáforo solo mide aprendices; su % sí se
  muestra.
- **El chat 1 a 1** usa la regla vigente de quién puede escribirle a quién (cuentas activas). Si el servidor no
  lo abre, la app avisa con su respuesta, como en la info del chat.
- **La observación sobre una persona** reutiliza las observaciones del líder al mentor (RL-14..RL-17); no se crea
  un buzón nuevo.
- **El post diario** hoy se cierra sin evidencia: la publicación no queda ligada al cumplimiento, y la ficha de
  administración dice «sin evidencia» (`evidenciaId` vacío). **Esta spec la liga** para cumplir FR-011. El ✓ del
  post diario funciona en local (verificado el 2026-10-19); lo que pasa en producción lo revisa el dueño aparte.
- **Los hábitos de otro rol** que la persona no tiene (D-264) no aparecen: la semana muestra solo lo que tenía.
- Se reutilizan la lectura semanal y la de evidencias de administración. No se crea una fórmula de cumplimiento
  nueva.
- Hace falta publicar el backend antes que la app y un APK nuevo para verlo en el teléfono.
