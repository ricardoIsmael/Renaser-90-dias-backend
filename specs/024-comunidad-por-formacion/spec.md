# Feature Specification: Comunidad por formación (Muro, Eventos, Cursos, Tribu, Ranking y Testimonios)

**Feature Branch**: `024-comunidad-por-formacion` (backend y app; solo especificación, sin plan ni código)

**Created**: 2026-10-31

**Status**: Draft (con 3 preguntas abiertas para el dueño, marcadas en el texto)

**Input**: Pedido del dueño (2026-10-31), textual:
> - El sistema debe poder soportar varios tipos de comunidad por formación.
> - El sistema debe permitir al usuario subir contenido al muro por formación y no mezclar la información o publicación con otras formaciones.
> - El sistema debe poder mostrar cursos por formación.
> - El sistema debe poder mostrar la tribu por formación.
> - El sistema debe mostrar el ranking por formación.
> - El sistema debe mostrar testimonios por usuarios.

Esta spec es hermana de la **023** (hábitos y categorías por formación) y se apoya en la **022** (el modelo de formaciones).
No repite lo que ellas definen.

## Contexto

**Lo que decidió el dueño (31/10/2026), y que esta spec da por hecho:**

- La app pasa a tener **tres formaciones**: *Escuela Renaser* (90 días), *Diplomado Renaser* (3 meses o 1 año) y
  *Certificación Renaser* (90 días).
- **Un aprendiz pertenece a UNA formación a la vez.** El personal (guía, mentor, líder de mentores) puede tener una o
  varias, que asigna Administración; con varias, **elige en cuál trabaja**. **Administración y Alquimista ven las tres.**
- **Los roles son los mismos, pero aislados:** nadie ve datos de otra formación.
- **Todo lo que existe hoy pasa a ser de la Escuela Renaser.**
- El Mapa de Renacimiento se retira (spec 026): no se trata aquí.

**Cómo funciona hoy la Comunidad** (la pestaña tiene seis secciones; todas se leen de un único espacio, sin formación):

- **Muro**: cualquier persona activa publica (foto o video obligatorios, categoría opcional), comenta, reacciona y comparte.
  Quien publica puede ocultar lo propio; Administración y Alquimista moderan cualquier publicación (ocultar, restaurar,
  eliminar) y ven la cola de ocultas. El hábito «post diario» publica en el Muro. Hoy muestra la invitación con el último
  autor.
- **Eventos**: Administración y Alquimista los crean; cada evento tiene audiencia (todos, por nivel, por curso, por roles o
  por grupo), «Voy / No voy», recordatorios, «Mi agenda» y «Próximo evento» en Hoy, y asistencia («Quién respondió», «Pasar
  lista»). La *Mentoría del Alquimista* solo le sale al aprendiz con semáforo verde.
- **Cursos**: catálogo con secciones y lecciones, desbloqueo por día del programa, restricción por rol, progreso, la Clase
  diaria y el botón «Pregúntale a SER» dentro del curso.
- **Tribu**: la lista de chats. «Formación Renaser» agrupa la **comunidad general** («Formación Renaser Global», única, a la
  que entra toda persona nueva), el **grupo** con su mentor («<Mentor> y sus aprendices») y el **soporte** de cada aprendiz
  con el equipo («<Nombre> – Formación Renaser»); «Directos» agrupa los 1 a 1. Administración y Alquimista tienen además la
  sección «Soporte · N» con el chat de soporte de cada aprendiz. Los lunes a las 09:00 (Lima) el programa publica el podio
  de la semana en la comunidad general.
- **Ranking**: General, Coherencia, Kilómetros y Gimnasio, entre todos los aprendices activos; «Tu posición»; ranking de
  grupos; imagen descargable del podio para líder, Alquimista y Administración.
- **Testimonios**: una lista de testimonios destacados; los crea quien los escribe (incluso desde un formulario sin cuenta) o
  Administración promoviendo una publicación del Muro.

**Lo que se quiere:** que cada formación tenga **su propia comunidad** —su Muro, sus Eventos, sus Cursos, su Tribu, su
Ranking y sus Testimonios—, con **varios tipos de comunidad** configurables, y que lo de una formación sea invisible para las
otras.

## Clarifications

### Decisiones del dueño (2026-10-31)

Son las del apartado *Contexto* y el pedido textual de arriba. No hay otras decisiones del dueño sobre esta spec todavía.

### Preguntas abiertas (el dueño las contesta antes de implementar)

Cada una tiene un «por defecto» que la spec ya usa; si el dueño elige otra opción, se corrige en el mismo cambio. La lista
completa de dudas, incluidas las que solo llevan supuesto, está en *Assumptions*.

1. **«Testimonios por usuarios»: ¿qué significa?**
   - A (por defecto): son los testimonios **que escriben los usuarios de la formación**, cada uno con su autor (nombre,
     foto), y se ven **solo dentro de esa formación**.
   - B: los testimonios se ven como prueba social **en todas las formaciones** (rompe el aislamiento; el dueño tendría que
     autorizarlo expresamente).
   - C: el testimonio es de **cada persona** y se ve en su ficha, y la sección es la lista de los de la formación.
2. **«Varios tipos de comunidad por formación»: ¿qué tipos?**
   - A (por defecto): un *tipo de comunidad* es una clase de espacio de la Tribu —**comunidad general, grupo con mentor,
     soporte y directos**—. Cada formación los tiene prendidos y Administración puede apagar o prender cada uno por formación.
   - B: además, **varias comunidades del mismo tipo** dentro de una formación (por ejemplo, una por generación del Diplomado,
     cada una con su chat y su Muro).
   - C: tipos distintos según la formación, que el dueño define (entregar la lista: por ejemplo «aula» en el Diplomado o
     «mesa de evaluación» en la Certificación).
3. **Cuando un aprendiz cambia de formación, ¿qué pasa con sus puntos en el ranking?**
   - A (por defecto): el ranking de cada formación cuenta **solo lo hecho en esa formación**. Al entrar a la nueva empieza de
     cero; lo anterior queda en su historial y en el ranking de la formación anterior.
   - B: los puntos de liga **viajan** con la persona (saldo único) y la coherencia, los kilómetros y los días de gimnasio se
     cuentan por formación.
   - C: todo lo acumulado viaja con ella y entra al ranking de la nueva formación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El Muro de mi formación (Priority: P1)

Una persona publica en el Muro de **su** formación y ve solo lo que publicaron los de la suya: publicaciones, comentarios,
reacciones, quién reaccionó, lo que puede compartir y la invitación de Hoy con el último autor. Nada de otra formación se
mezcla.

**Why this priority**: Es la pieza que más se ve y la que más expone a una persona (nombre, foto, lo que escribe). Una
filtración aquí es la peor.

**Independent Test**: dos aprendices en formaciones distintas; uno publica con foto, otro comenta y reacciona dentro de la
suya. El de la otra formación revisa el Muro, «Mis publicaciones», la invitación de Hoy, la cola de ocultas (si modera) y
abre el enlace de la publicación: no aparece nada.

**Acceptance Scenarios**:

1. **Given** un aprendiz de la Escuela, **When** publica en el Muro, **Then** la publicación aparece en el Muro de la Escuela
   y en ningún otro, ni en el feed, ni en búsquedas, ni en contadores, ni en la invitación de Hoy.
2. **Given** una publicación de la Escuela, **When** un aprendiz del Diplomado abre su enlace o su id (de un aviso viejo o de
   un enlace guardado), **Then** recibe lo mismo que si no existiera («Esta publicación ya no está disponible»).
3. **Given** una publicación, **When** alguien la comenta, reacciona o ve «quién reaccionó», **Then** solo participan y se
   listan personas de su formación.
4. **Given** una publicación, **When** la persona toca «Compartir», **Then** solo ofrece destinos (grupos, comunidad general,
   directos) de esa misma formación.
5. **Given** el hábito «post diario», **When** la persona lo cumple, **Then** se publica en el Muro de su formación.
6. **Given** Administración o Alquimista, **When** abre la moderación del Muro, **Then** elige la formación (o «Todas», cada
   fila rotulada) y puede ocultar, restaurar o eliminar; el autor sigue pudiendo ocultar lo propio.
7. **Given** un mentor, guía, líder o aprendiz, **When** intenta moderar (ocultar lo ajeno, restaurar, eliminar), **Then** se
   rechaza, como hoy.

---

### User Story 2 - Los Eventos de mi formación (Priority: P1)

Los eventos —lista, tarjetas, calendario, «Mi agenda», «Próximo evento» de Hoy, detalle, «Voy», recordatorios y asistencia— son
solo los de la formación de la persona. Al crear uno, Administración elige a qué formación pertenece.

**Why this priority**: Un evento mal dirigido manda avisos y alarmas a quien no debe y expone la lista de asistentes.

**Independent Test**: crear un evento en el Diplomado para «todos»; un aprendiz de la Escuela no lo ve en lista, calendario,
agenda ni Hoy, no le suena ninguna alarma y no recibe aviso; uno del Diplomado sí.

**Acceptance Scenarios**:

1. **Given** un evento del Diplomado con audiencia «todos», **When** lo miran los aprendices, **Then** «todos» significa todos
   los **de la formación**: lo ven los del Diplomado y no los de las otras.
2. **Given** un evento con audiencia por grupo, por nivel, por curso o por roles, **When** se resuelve, **Then** se resuelve
   dentro de su formación.
3. **Given** el detalle de un evento de otra formación (por id o aviso viejo), **When** alguien lo abre, **Then** recibe lo
   mismo que un evento inexistente.
4. **Given** los recordatorios, avisos, alarmas locales y «Voy», **When** se generan o se disparan, **Then** alcanzan solo a
   personas de la formación del evento.
5. **Given** la asistencia («Quién respondió», «Pasar lista»), **When** la ven quienes la ven hoy, **Then** lista solo a
   personas de la formación del evento.
6. **Given** la Mentoría del Alquimista, **When** un aprendiz la busca, **Then** la ve solo si su semáforo (el de su
   formación) está en verde, como hoy.
7. **Given** Administración o Alquimista, **When** crea o edita un evento, **Then** debe elegir la formación; no existe un
   evento sin formación.

---

### User Story 3 - Los Cursos de mi formación (Priority: P1)

El catálogo de cursos, el curso, la lección, el progreso, los cursos bloqueados, la Clase diaria y «Pregúntale a SER» dentro
del curso son los de la formación de la persona. El desbloqueo por día usa el **día de su programa en su formación**.

**Why this priority**: Los cursos son contenido de pago de cada formación; verlos de otra es un daño comercial además de una
mezcla.

**Independent Test**: un curso exclusivo del Diplomado: el aprendiz del Diplomado lo ve (bloqueado o abierto según su día); el
de la Escuela no lo ve en el catálogo, en «bloqueados», por id ni como contexto de SER.

**Acceptance Scenarios**:

1. **Given** un curso de una formación, **When** un participante de otra abre el catálogo, **Then** no aparece, ni como
   «bloqueado».
2. **Given** el id o enlace de un curso o lección de otra formación, **When** se abre, **Then** responde lo mismo que un
   inexistente.
3. **Given** un curso con desbloqueo por día, **When** se calcula, **Then** usa el día del programa de la persona en su
   formación.
4. **Given** un curso restringido por rol, **When** se resuelve, **Then** la regla de rol sigue igual, dentro de la formación.
5. **Given** el porcentaje de cursos de una persona (el que alimenta el ranking), **When** se calcula, **Then** cuenta solo
   los cursos de su formación.
6. **Given** el botón «Pregúntale a SER» dentro de un curso, **When** SER recibe el contexto, **Then** es el curso y la
   lección de la formación de la persona.
7. **Given** Administración, **When** crea, edita o publica un curso, **Then** lo hace dentro de una formación elegida.

---

### User Story 4 - La Tribu de mi formación (Priority: P1)

La Tribu muestra, de la formación de la persona: su **comunidad general**, su **grupo** con el mentor y los compañeros, su
**soporte** y sus **directos**. Cada formación tiene su propia comunidad general, sus grupos y sus soportes.

**Why this priority**: Es donde se conversa y se conocen las personas; el chat mezclado expondría conversaciones privadas.

**Independent Test**: un aprendiz de la Escuela y uno del Diplomado entran a Tribu: cada uno ve una sola comunidad general
(la suya), su grupo, su soporte, y en Directos solo personas de su formación. Escribir en una no aparece en la otra.

**Acceptance Scenarios**:

1. **Given** una persona de la Escuela, **When** abre Tribu, **Then** ve la comunidad general de la Escuela, su grupo y su
   soporte; y ninguna conversación de otra formación.
2. **Given** una persona nueva en el Diplomado, **When** entra, **Then** se une a la comunidad general y al grupo inicial del
   Diplomado, y la bienvenida automática sale en esos espacios.
3. **Given** el buscador de personas para un directo, **When** la persona busca, **Then** solo encuentra personas de su
   formación (para el personal con varias, de la formación activa).
4. **Given** un directo entre dos personas, **When** una de ellas deja de compartir formación con la otra, **Then** ya no se
   ve en ninguna de las dos (se conserva, no se borra).
5. **Given** un grupo, **When** se mira su información, **Then** el mentor y los integrantes son de la formación del grupo.
6. **Given** el podio semanal, **When** llega el lunes 09:00 (Lima), **Then** se publica **uno por formación**, en su
   comunidad general, con el podio de esa formación.
7. **Given** Administración o Alquimista, **When** abre «Soporte · N», **Then** ve los soportes de la formación elegida (o
   de «Todas», cada fila con su formación).
8. **Given** el nombre de un chat, **When** hoy dice «Formación Renaser», **Then** lleva el nombre de su formación (por
   defecto, pregunta en *Assumptions*).

---

### User Story 5 - El Ranking de mi formación (Priority: P1)

El ranking compara solo a los aprendices de la misma formación: sus pestañas, «Tu posición», el ranking de grupos y el podio
descargable.

**Why this priority**: Es lo más competitivo y público; compararse con otra formación sería injusto y mezclaría datos.

**Independent Test**: en dos formaciones con aprendices y puntajes distintos, cada ranking lista solo a los suyos, con las
posiciones calculadas entre ellos; la imagen del podio lleva el nombre de la formación.

**Acceptance Scenarios**:

1. **Given** dos formaciones, **When** un aprendiz abre General, Coherencia, Kilómetros o Gimnasio, **Then** ve solo a los de
   la suya y su posición dentro de ellos.
2. **Given** una formación que no ofrece el hábito de Kilómetros o el de Gimnasio (spec 023), **When** se abre su ranking,
   **Then** esa pestaña no aparece.
3. **Given** el ranking de grupos, **When** se abre, **Then** compara solo los grupos de la formación.
4. **Given** «Descargar» el podio, **When** lo usa un líder, Alquimista o Administración, **Then** la imagen es la del ranking
   de la formación que están mirando y lleva su nombre; el líder solo la descarga de sus formaciones.
5. **Given** un aprendiz que cambia de formación, **When** se ve el ranking, **Then** *(según la pregunta abierta 3)* solo
   cuenta lo que hizo en la formación que se mira.
6. **Given** el personal (que no es aprendiz), **When** se arma el ranking, **Then** no entra, como hoy.

---

### User Story 6 - Los Testimonios de mi formación (Priority: P2)

La sección Testimonios muestra los testimonios **de las personas de la formación**, cada uno con su autor, y solo dentro de
esa formación. *(Interpretación de «testimonios por usuarios»; ver pregunta abierta 1.)*

**Why this priority**: Es la sección menos crítica de la Comunidad, pero la única cuyo pedido es ambiguo.

**Independent Test**: un testimonio de un aprendiz del Diplomado se ve en Testimonios del Diplomado con su nombre y foto, y no
en el de la Escuela.

**Acceptance Scenarios**:

1. **Given** un testimonio escrito por una persona de una formación, **When** alguien de otra formación abre Testimonios,
   **Then** no aparece.
2. **Given** un testimonio, **When** se muestra, **Then** lleva su autor (nombre, foto y etiqueta de rol), como hoy.
3. **Given** una publicación del Muro, **When** Administración o Alquimista la promueve a testimonio, **Then** el testimonio
   queda en la formación de la publicación y no se puede promover a otra.
4. **Given** el formulario de testimonio sin cuenta, **When** alguien lo envía, **Then** el testimonio queda sin formación,
   lo ve solo Administración y no se muestra a nadie hasta que le asigna una.
5. **Given** un autor que cambia de formación, **When** se muestran los testimonios, **Then** los suyos siguen en la formación
   donde los escribió.

---

### User Story 7 - Varios tipos de comunidad por formación (Priority: P2)

Administración decide, por formación, qué **tipos de comunidad** ofrece. Hoy son cuatro: comunidad general, grupo con mentor,
soporte y directos. Apagar uno lo esconde de toda la formación sin borrar sus mensajes; prenderlo los devuelve.

**Why this priority**: Es la primera frase del pedido y hace que las formaciones puedan diferenciarse sin rehacer la Tribu.

**Independent Test**: apagar «Directos» en el Diplomado: sus participantes dejan de verlos y de abrir uno nuevo; en la Escuela
siguen igual. Volver a prenderlo devuelve las conversaciones.

**Acceptance Scenarios**:

1. **Given** una formación nueva, **When** se crea, **Then** ofrece por defecto los cuatro tipos de hoy.
2. **Given** un tipo apagado, **When** un participante abre Tribu, **Then** no aparece ni se puede crear una conversación de
   ese tipo; sus mensajes se conservan.
3. **Given** un tipo vuelto a prender, **When** el participante abre Tribu, **Then** recupera sus conversaciones.
4. **Given** un tipo de comunidad, **When** funciona dentro de una formación, **Then** se rige por sus reglas de hoy (quién
   escribe a quién, quién modera, qué avisa).
5. **Given** un rol sin permiso (aprendiz, guía, mentor, líder) o una cuenta suspendida, **When** intenta apagar o prender un
   tipo, **Then** se rechaza.

---

### User Story 8 - Personal con varias formaciones y Administración con las tres (Priority: P2)

Un guía, mentor o líder asignado a dos formaciones **elige en cuál trabaja** (selector de la spec 022) y todo lo de la
Comunidad —Muro, Eventos, Cursos, Tribu, Ranking, Testimonios— es el de la formación elegida. Administración y Alquimista
usan el mismo selector con las tres y, en las listas de administración, el filtro «Todas» con cada fila rotulada.

**Why this priority**: Es el caso por el que el aislamiento puede romperse más fácil: la misma persona con dos mundos.

**Independent Test**: un mentor de la Escuela y el Diplomado cambia la formación activa y el Muro, los eventos, los cursos, la
Tribu, el ranking y los testimonios se recargan por completo, sin rastro de la anterior.

**Acceptance Scenarios**:

1. **Given** una persona con una sola formación, **When** usa la Comunidad, **Then** no ve ningún selector ni la palabra
   «formación» (la app es la de siempre).
2. **Given** un mentor con dos formaciones, **When** cambia la formación activa, **Then** las seis secciones se recargan con
   lo de la nueva y se cierra cualquier hoja o detalle de la anterior.
3. **Given** un mentor con grupos en las dos formaciones, **When** mira «Mi grupo» y Tribu, **Then** ve solo los de la
   formación activa.
4. **Given** una notificación (mensaje, evento, publicación) de una formación que no es la activa del personal, **When** la
   toca, **Then** la app cambia la formación activa a la del contenido, lo dice en una línea y abre el contenido.
5. **Given** Administración en una lista con «Todas», **When** crea o edita algo, **Then** debe elegir una formación concreta;
   nada se crea «para todas» ni «sin formación».

---

### User Story 9 - El aprendiz que cambia de formación y lo que ya existe (Priority: P3)

Cuando un aprendiz cambia de formación, deja de ver lo de la anterior y pasa a ver lo de la nueva; lo que escribió se queda
donde lo escribió. Al desplegar, todo lo que existe hoy es de la Escuela.

**Why this priority**: Es el caso de borde que más puede dañar datos; no agrega capacidad nueva.

**Independent Test**: pasar a un aprendiz de la Escuela al Diplomado: sus publicaciones y mensajes siguen en la Escuela con su
nombre; él ve el Muro, Tribu y Ranking del Diplomado; no ve nada de la Escuela.

**Acceptance Scenarios**:

1. **Given** un aprendiz con publicaciones, comentarios, mensajes y testimonios en la Escuela, **When** cambia de formación,
   **Then** todo eso se conserva en la Escuela, atribuido a él, y ya no lo ve.
2. **Given** su grupo, su soporte y sus directos de la formación anterior, **When** cambia, **Then** dejan de verse para él y
   para ellos, y en la nueva se une a la comunidad general y al grupo inicial.
3. **Given** un «Voy» a un evento de la formación anterior, **When** cambia, **Then** se conserva en el historial del evento y
   ya no genera avisos para él.
4. **Given** su progreso en cursos de la formación anterior, **When** cambia, **Then** se conserva y no se muestra en la nueva.
5. **Given** el despliegue, **When** termina, **Then** todas las publicaciones, comentarios, reacciones, eventos, cursos,
   grupos, conversaciones, rankings y testimonios existentes pertenecen a la Escuela Renaser, sin cambiar de valor.
6. **Given** una app ya publicada (que no envía nada de formación), **When** una cuenta de la Escuela la usa, **Then**
   funciona completa.

### Edge Cases

- **Dos personas que comparten dos formaciones** (personal con las mismas dos): un directo suyo pertenece a la formación en
  la que se abrió y solo se ve con ella activa.
- **Mentor que lidera un grupo en cada formación**: cada grupo es de una formación; ninguno se mezcla en «Mi grupo».
- **Una formación sin ninguna publicación, evento, curso o testimonio**: cada sección dice su estado vacío («Aún no hay
  publicaciones.»), nunca un error ni el contenido de otra formación.
- **Un aviso (push) viejo de otra formación** abre «Este contenido ya no está disponible» para quien no la ve, o cambia la
  formación activa para el personal que sí la tiene (historia 8).
- **Texto que cita contenido de otra formación** (una respuesta citada, un enlace pegado en un chat): el enlace se abre solo
  si la persona ve esa formación.
- **Persona suspendida o cuenta cerrada**: sus publicaciones se tratan como hoy, dentro de su formación.
- **Testimonio con el autor eliminado**: se conserva como hoy, dentro de su formación.
- **Un evento que cambia de formación**: no existe; se copia (*Assumptions*).
- **Podio con empates o con menos de 10 aprendices**: como hoy, calculado dentro de la formación.
- **Zona horaria**: el lunes 09:00 del podio y el «desde mañana» de un cambio de formación son los de Lima y el de la zona de
  cada persona, respectivamente (principio V).
- **Interruptores de Soporte TI**: un interruptor apagado (por ejemplo, `muro` o `ranking`) sigue apagado para todas las
  formaciones.
- **Soporte TI «ver como»**: ve la comunidad de la formación del rol simulado; su detalle lo define la spec 022.
- **Reacciones, comentarios y respuestas de una publicación cuyo autor cambió de formación**: siguen en la formación de la
  publicación, y quien cambió deja de poder verlos.

## Requirements *(mandatory)*

### Functional Requirements

**Reglas comunes**

- **FR-001**: Cada publicación del Muro, comentario, evento, curso, grupo, comunidad general, soporte, posición del ranking y
  testimonio MUST pertenecer a exactamente **una** formación (los directos, según FR-029).
- **FR-002**: Todo lo existente MUST pasar a la **Escuela Renaser** sin cambiar de valor, autor, fecha ni orden.
- **FR-003**: Ninguna lectura ni escritura de la Comunidad MUST cruzar formaciones, para ningún rol, **salvo** Administración y
  Alquimista, que operan siempre sobre una formación explícita o leen con «Todas» (cada fila rotulada con su formación).
- **FR-004**: Pedir por id, enlace o aviso un contenido de una formación que la persona no ve MUST responder exactamente lo
  mismo que un inexistente.
- **FR-005**: Todo push, aviso y alarma de un contenido MUST llegar solo a personas de su formación.
- **FR-006**: El contenido nuevo MUST nacer en la formación activa de quien lo crea (el aprendiz, la suya; el personal, la
  activa; Administración, la que elige).
- **FR-007**: Una app ya publicada MUST seguir funcionando para las cuentas de la Escuela sin enviar nada nuevo.
- **FR-008**: Los permisos MUST seguir siendo los de hoy: la formación solo **reduce** lo visible, nunca da un permiso nuevo.

**Muro**

- **FR-009**: La persona MUST poder publicar en el Muro de su formación, y el feed, «Mis publicaciones», el último autor (la
  invitación de Hoy), los comentarios, las reacciones y «quién reaccionó» MUST ser solo de esa formación.
- **FR-010**: «Compartir» MUST ofrecer solo destinos de la misma formación.
- **FR-011**: El hábito «post diario» y la publicación desde evidencia MUST publicar en el Muro de la formación de la persona.
- **FR-012**: La moderación (ocultar, restaurar, eliminar permanente) MUST seguir siendo del autor (lo propio) y de ADMIN y
  ALQUIMISTA (cualquiera); la cola de ocultas MUST listarse por formación (o «Todas», rotulada).
- **FR-013**: Las categorías del Muro MUST seguir siendo las mismas para todas las formaciones (no llevan datos de personas); la
  publicación pertenece a su formación aunque la categoría sea común.
- **FR-014**: Una publicación MUST NOT poder moverse a otra formación.

**Eventos**

- **FR-015**: Cada evento MUST pertenecer a una formación; la lista, las tarjetas, el calendario, «Mi agenda» y «Próximo
  evento» de Hoy MUST mostrar solo los de la formación de la persona.
- **FR-016**: La audiencia de un evento (todos, nivel, curso, roles, grupo) MUST resolverse dentro de su formación; «todos»
  MUST significar todos los de la formación.
- **FR-017**: Crear, editar y cancelar eventos MUST seguir siendo de ADMIN y ALQUIMISTA y MUST exigir elegir la formación.
- **FR-018**: Recordatorios, avisos, alarmas locales, «Voy / No voy» y asistencia MUST alcanzar y listar solo a personas de la
  formación del evento.
- **FR-019**: La elegibilidad de la Mentoría del Alquimista MUST seguir la regla de hoy (semáforo verde), con el semáforo de la
  persona en su formación.

**Cursos**

- **FR-020**: El catálogo, los cursos bloqueados, el curso, la sección, la lección, el progreso, la Clase diaria y las
  recomendaciones MUST ser solo los de la formación de la persona.
- **FR-021**: El desbloqueo por día MUST usar el día del programa de la persona en su formación; la restricción por rol MUST
  seguir igual dentro de la formación.
- **FR-022**: El porcentaje de cursos que alimenta el ranking MUST contar solo los cursos de la formación.
- **FR-023**: El contexto de «Pregúntale a SER» dentro de un curso MUST ser el curso y la lección de la formación de la persona.
- **FR-024**: Crear, editar y publicar cursos MUST seguir siendo de quien puede hoy y MUST exigir elegir la formación.

**Tribu**

- **FR-025**: La Tribu MUST mostrar, de la formación de la persona: la comunidad general, su grupo, su soporte y sus directos.
- **FR-026**: Cada formación MUST tener una sola comunidad general; toda persona nueva de la formación MUST unirse a ella y al
  grupo inicial de **esa** formación; los mensajes automáticos (bienvenida, podio) MUST salir en los espacios de su formación.
- **FR-027**: Cada grupo MUST pertenecer a una formación, y su mentor y sus integrantes MUST ser de esa formación; un mentor
  con dos formaciones MUST poder liderar grupos en cada una.
- **FR-028**: Cada aprendiz MUST tener su chat de soporte con el equipo de **su** formación; Administración y Alquimista MUST
  ver los soportes de las tres en «Soporte · N», con filtro por formación y cada fila rotulada.
- **FR-029**: Los directos MUST ser solo entre personas que comparten formación; el buscador MUST listar solo personas de la
  formación activa; un directo pertenece a la formación en que se abrió y solo se ve con ella activa.
- **FR-030**: Los nombres de los chats MUST llevar el nombre de su formación donde hoy dicen «Formación Renaser» (el nombre
  lo manda el servidor).
- **FR-031**: La información del grupo, los integrantes, «Ver ficha» y «Escribirle» MUST ser de la formación del grupo.
- **FR-032**: Un aviso de mensaje MUST llegar solo a participantes de esa conversación; para el personal con varias formaciones
  MUST llegar aunque no sea la activa, y al tocarlo MUST cambiar la formación activa y abrir la conversación.

**Tipos de comunidad**

- **FR-033**: Cada formación MUST tener configurados los tipos de comunidad que ofrece; los de hoy son cuatro (general, grupo
  con mentor, soporte, directos) y MUST estar prendidos por defecto en toda formación nueva. [NEEDS CLARIFICATION: pregunta
  abierta 2: ¿«tipos» son los cuatro de hoy prendibles y apagables (supuesto), varias comunidades del mismo tipo por
  formación, o tipos propios de cada formación que defina el dueño?]
- **FR-034**: Solo ADMIN y ALQUIMISTA MUST poder prender o apagar un tipo en una formación; cualquier otro rol o cuenta
  suspendida MUST ser rechazado (403).
- **FR-035**: Un tipo apagado MUST esconderse a todos los de la formación y MUST NOT permitir crear conversaciones nuevas de
  ese tipo; sus mensajes MUST conservarse y reaparecer al prenderlo. Apagar un tipo MUST pedir confirmación y decir cuántas
  conversaciones activas esconde.
- **FR-036**: Cada tipo MUST regirse por sus reglas de hoy dentro de la formación (quién escribe a quién, quién modera, qué
  avisa). Prender o apagar un tipo MUST NOT pedir una app nueva.

**Ranking**

- **FR-037**: El ranking de una formación (General, Coherencia, Kilómetros, Gimnasio y «Tu posición») MUST incluir solo
  aprendices de esa formación y calcularse solo con lo hecho en ella.
- **FR-038**: Las pestañas de Kilómetros y Gimnasio MUST aparecer solo si la formación ofrece esos hábitos (spec 023).
- **FR-039**: El ranking de grupos y la posición dentro del grupo MUST comparar solo grupos y personas de la formación.
- **FR-040**: El podio semanal automático (lunes 09:00, Lima) MUST publicarse **uno por formación**, en su comunidad general.
- **FR-041**: La imagen descargable del podio MUST ser la del ranking de la formación que se mira y llevar su nombre; el
  permiso MUST seguir siendo el de hoy (líder, Alquimista, Administración), limitado para el líder a sus formaciones.
- **FR-042**: Cuando un aprendiz cambia de formación, el ranking de cada formación MUST contar solo lo que hizo en ella; su
  historial anterior MUST conservarse sin modificarse. [NEEDS CLARIFICATION: pregunta abierta 3: ¿empieza de cero en la nueva
  (supuesto), viaja el saldo de puntos de liga o viaja todo?]

**Testimonios**

- **FR-043**: Cada testimonio MUST pertenecer a la formación de la persona que lo da, mostrar su autor (nombre, foto y etiqueta
  de rol) y verse solo dentro de esa formación; Administración y Alquimista lo ven en la formación que eligen.
  [NEEDS CLARIFICATION: pregunta abierta 1: ¿«testimonios por usuarios» son los de los usuarios de la formación y solo ahí
  (supuesto), prueba social para todas las formaciones, o uno por persona en su ficha?]
- **FR-044**: Promover una publicación del Muro a testimonio MUST seguir siendo de ADMIN y ALQUIMISTA, y el testimonio MUST
  quedar en la formación de la publicación.
- **FR-045**: Un testimonio recibido sin cuenta MUST quedar sin formación y visible solo para Administración hasta que le
  asigne una.
- **FR-046**: Un testimonio MUST seguir en su formación aunque su autor cambie de formación.

**Personal y Administración**

- **FR-047**: El personal con varias formaciones MUST ver las seis secciones de la formación activa; cambiarla MUST recargarlas
  todas y cerrar cualquier hoja o detalle de la anterior.
- **FR-048**: Administración y Alquimista MUST usar el mismo selector con las tres formaciones; las listas de administración
  (moderación, soportes, eventos, cursos, testimonios) MUST ofrecer «Todas» con cada fila rotulada; crear o editar MUST ocurrir
  siempre en una formación concreta.
- **FR-049**: El líder de mentores MUST ver lo suyo (Mis mentores, tickets, semáforo por grupos, fichas) de la formación activa.

**Pantallas**

- **FR-050**: La persona con una sola formación MUST NOT ver ningún selector de formación ni la palabra «formación» en la
  Comunidad; la que tiene varias lo MUST ver en un solo lugar uniforme (spec 022).
- **FR-051**: Las pantallas nuevas o cambiadas MUST cumplir las reglas de la app: un solo scroll, gesto atrás que vuelve un
  nivel, áreas táctiles de 48 px o más, tema claro y oscuro, español de Perú con tuteo y estados vacíos con texto.
- **FR-052**: **La Comunidad no cambia de forma** (sus seis secciones, sus diseños y textos): cambia qué contenido sale de cada
  una y aparece el selector solo para quien tiene varias formaciones.

### Key Entities

- **Formación**: Escuela, Diplomado o Certificación (spec 022). Aquí es el dueño de cada comunidad.
- **Publicación del Muro**: lo que ya es (autor, texto, fotos, categoría, comentarios, reacciones), más su formación.
- **Evento**: lo que ya es (fecha, audiencia, recordatorios, asistencia), más su formación.
- **Curso**: lo que ya es (secciones, lecciones, desbloqueo, roles permitidos), más su formación.
- **Grupo (célula) y cohorte**: lo que ya son (mentor, integrantes), más su formación.
- **Conversación**: tipo (general, grupo, soporte, directo), participantes y su formación.
- **Tipo de comunidad de una formación**: qué tipos ofrece cada formación y si están prendidos.
- **Posición del ranking**: aprendiz, formación, puntaje, posición, fecha.
- **Testimonio**: lo que ya es, más su formación; puede estar sin formación si vino sin cuenta.
- **Pertenencia y formación activa** (spec 022): a qué formación(es) pertenece cada persona y cuál tiene activa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

Cada parte tiene su prueba E2E de aislamiento: **dos cuentas en formaciones distintas (A y B), con contenido propio en cada
una; todo lo de A es invisible para B en cada superficie listada, y viceversa, con 0 excepciones.**

- **SC-001** *(Muro)*: superficies: feed, «Mis publicaciones», invitación de Hoy con el último autor, comentarios, reacciones y
  «quién reaccionó», destinos de «Compartir», cola de ocultas, aviso de publicación nueva y enlace por id.
- **SC-002** *(Eventos)*: lista, tarjetas, calendario, «Mi agenda», «Próximo evento» de Hoy, detalle por id, asistencia,
  recordatorios, alarmas locales y avisos.
- **SC-003** *(Cursos)*: catálogo, «bloqueados», curso, lección, progreso, Clase diaria, contexto de SER y porcentaje de
  cursos del ranking.
- **SC-004** *(Tribu)*: lista de chats, comunidad general, grupo e información del grupo, soporte, «Soporte · N» de
  Administración, directos, buscador de personas, bienvenida y podio semanal.
- **SC-005** *(Ranking)*: cada pestaña, «Tu posición», ranking de grupos, podio semanal y la imagen descargable.
- **SC-006** *(Testimonios)*: lista, detalle, promoción desde el Muro y formulario sin cuenta.
- **SC-007** *(Tipos de comunidad)*: apagar un tipo en la formación A no cambia nada en B; volver a prenderlo restituye el 100 %
  de las conversaciones y mensajes.
- **SC-008**: Un acceso por id, enlace o aviso a un contenido de otra formación recibe la misma respuesta que un inexistente
  en el 100 % de los casos de las seis secciones.
- **SC-009**: Tras el despliegue, el 100 % del contenido existente pertenece a la Escuela y el 100 % de las personas existentes
  ve lo mismo que antes (0 diferencias en una muestra de 10 cuentas de todos los roles, por sección).
- **SC-010**: Un aprendiz que cambia de formación conserva el 100 % de sus publicaciones, comentarios, mensajes, testimonios,
  «Voy» y progreso en la formación de origen, y ve 0 elementos de ella desde su día siguiente (reloj en la madrugada UTC).
- **SC-011**: Un mentor con dos formaciones cambia la activa en 2 toques o menos y ve las seis secciones de la nueva sin rastro
  de la anterior y sin cerrar la app.
- **SC-012**: El 100 % de los intentos de un rol sin permiso (o de una cuenta suspendida) de moderar el Muro, crear eventos o
  cursos, o prender y apagar tipos de comunidad es rechazado.
- **SC-013**: Una app ya publicada recorre sin errores las seis secciones con una cuenta de la Escuela (flujos de regresión en
  verde).
- **SC-014**: Administración crea contenido (una publicación, un evento, un curso) en cada una de las tres formaciones y lo ve
  rotulado con «Todas» sin que ninguna persona de las otras lo vea.

## Restricciones del dueño

Dichas por el dueño el 31/10/2026 para todas las specs; aquí aplicadas a la Comunidad, sin entrar en el diseño técnico.

1. **Reutilizar lo que ya existe.** El Muro, los eventos, los cursos, los grupos, los chats, el ranking y los testimonios se
   **extienden**, no se duplican. Nada de tablas nuevas si una existente sirve con una columna o una relación: la formación es
   un dato más de lo que ya hay, no una comunidad paralela por formación. Los datos quedan normalizados; si algo se
   desnormaliza (por ejemplo, la formación guardada en un puntaje histórico para no recalcularlo), debe estar justificado por
   la regla de que el historial no se reescribe.
2. **No romper la lógica actual.** Todo lo de hoy sigue funcionando como Escuela Renaser: la moderación, las audiencias de los
   eventos, el desbloqueo de cursos, los nombres y las reglas de cada chat, el ranking, el podio de los lunes y los
   testimonios.
3. **Respetar la constitución del repo y las reglas de la app.** Hexagonal, Spring Modulith (un módulo solo importa la API
   pública de otro) y SOLID en el backend (la constitución, principios I a VII); `AGENTS.md` del frontend en la app. Esta
   spec toca la pestaña **Comunidad** y, por los avisos y las invitaciones, **Hoy**: la **autorización explícita** es el
   pedido del dueño del 31/10/2026 y alcanza solo a «qué contenido sale de cada sección» y al selector; al implementarse se
   anota en `AGENTS.md` con fecha y «lo que cambia y nada más» (principio VI). Las migraciones llevan su cabecera
   (principio IV), el tiempo sigue siendo el del participante (principio V) y se documenta en el mismo cambio (principio VII).
4. **Aislamiento total entre formaciones: nunca se cruza información.** El Muro, los Cursos, la Tribu, el Ranking, los
   Testimonios y los Eventos de una formación son **invisibles** para las otras. Los criterios verificables por E2E son
   SC-001 a SC-008: dos cuentas en formaciones distintas y cero elementos de la otra en cada superficie, aviso, contador,
   enlace y ficha.
5. **Todas las dudas, antes de implementar.** El dueño quiere hacer él mismo las preguntas: están las tres de *Clarifications*
   y todas las que solo llevan supuesto, marcadas abajo con «(duda)».

## Assumptions (Supuestos)

Cada «(duda)» es una pregunta real para el dueño aunque ya tenga un supuesto; las marcadas «(cambia el alcance)» pueden
agregar o quitar trabajo.

- La **spec 022** define las formaciones, la pertenencia, la formación activa y el selector; esta spec usa su vocabulario.
- **(duda; cambia el alcance)** «Testimonios por usuarios»: se interpretó como los testimonios que escriben las personas de la
  formación, cada uno con su autor, visibles solo en ella (pregunta abierta 1).
- **(duda; cambia el alcance)** «Varios tipos de comunidad»: se interpretó como los cuatro tipos de hoy, prendibles y
  apagables por formación (pregunta abierta 2).
- **(duda; cambia el alcance)** Puntos del ranking al cambiar de formación: empiezan de cero en la nueva (pregunta abierta 3).
- **(duda; cambia el alcance)** Contenido compartido entre formaciones (un curso, un evento o un aviso del programa para
  varias): no existe. Cada pieza pertenece a una formación y, si Administración quiere repetirla en otra, la **copia** y
  queda independiente. Es la misma decisión de la pregunta abierta 1 de la spec 023: se responde una vez para hábitos,
  cursos y eventos.
- **(duda; cambia el alcance)** Una formación nueva (Diplomado, Certificación) arranca con su comunidad **vacía** —sin
  publicaciones, eventos ni cursos— pero con los cuatro tipos de comunidad prendidos y su comunidad general creada. Quién
  carga los cursos y eventos iniciales es de Administración.
- **(duda)** El cambio de formación de un aprendiz rige **desde su día siguiente** (mismo criterio de la spec 001 y de la 023)
  para todo; si la spec 022 fija otro instante, rige el de la 022. Para el personal, cambiar la formación activa rige en el
  acto.
- **(duda)** Los nombres de los chats que hoy dicen «Formación Renaser» pasan a decir el nombre de su formación («Escuela
  Renaser Global», «Pedro – Escuela Renaser»). Si el dueño prefiere que la Escuela conserve el nombre de hoy, la regla se
  limita a las otras dos.
- **(duda)** Las categorías del Muro (Revelaciones, Agradecimiento, Logros, Ayuda, Presentación) son las mismas en las tres
  formaciones; la de sistema «Presentación» sigue siendo la del primer paso de la bienvenida.
- **(duda)** Moderan el Muro solo ADMIN y ALQUIMISTA (como hoy); el líder, el mentor y el guía **no**, ni siquiera en su
  formación.
- **(duda)** Un directo pertenece a la formación en que se abrió; si dos personas comparten dos formaciones, tienen un directo
  por formación.
- **(duda)** El aviso de un mensaje, un evento o una publicación llega al personal aunque la formación de ese contenido no sea
  su activa, y al tocarlo cambia la formación activa (historia 8). La alternativa es no avisar de la no activa.
- **(duda)** Al cambiar de formación, un aprendiz sale del grupo y de la comunidad general de la anterior; en la nueva se une a
  la comunidad general y al grupo inicial, y Administración le asigna su grupo con mentor como hoy.
- **(duda)** Un testimonio recibido sin cuenta se asigna a una formación solo por Administración; el formulario no pregunta
  la formación.
- El personal (guía, mentor, líder) no entra al ranking, como hoy; solo los aprendices.
- Los permisos, las reglas de chat (quién escribe a quién) y la moderación de hoy no cambian; la formación solo reduce lo visible.
- Los interruptores de Soporte TI siguen siendo globales por funcionalidad (no por formación).
- Las pruebas del podio de los lunes y del «desde mañana» se corren con el reloj en la madrugada UTC.

## Dependencias

- **Spec 022 (formaciones)**: el modelo, la pertenencia, la formación activa y el selector. Esta spec no se puede planificar
  hasta que la 022 fije esos términos.
- **Spec 023 (hábitos por formación)**: el post diario, el Ranking de Kilómetros y Gimnasio y el progreso de hábitos dependen
  de los hábitos que ofrece cada formación.
- **Specs 005, 006, 012, 018 y 021**: las fichas del líder, el podio y las demás lecturas del liderazgo se leen dentro de la
  formación activa.

## Fuera de alcance

- El modelo de formaciones y de pertenencia (spec 022) y su selector.
- Los hábitos y las categorías de hábito (spec 023).
- Las reglas del programa de cada formación (fases, ciclos, semáforo, día del programa) y qué cursos, eventos o testimonios
  concretos lleva cada una.
- Los objetivos, el Mapa de Renacimiento y la ficha con diagnóstico (spec 026).
- La fórmula del ranking, del semáforo y de los puntos: no cambia, solo qué se cuenta.
- Nuevas funciones de chat, nuevas reacciones, moderación por parte del mentor o del líder, o un tipo de comunidad realmente
  nuevo (necesitaría su propia spec).
- Avisar a las personas cuando cambia su formación o sus comunidades.
