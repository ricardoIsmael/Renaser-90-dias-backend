# Feature Specification: Hábitos y categorías por formación

**Feature Branch**: `023-habitos-por-formacion` (backend y app; solo especificación, sin plan ni código)

**Created**: 2026-10-31

**Status**: Implementada (backend y app) el 2026-10-31, con las decisiones del dueño del mismo día; ver «Decisiones del dueño (2026-10-31)».

**Input**: Pedido del dueño (2026-10-31), textual:
> - El sistema debe configurarse las categorías de hábito por formación.
> - El sistema debe mostrar los hábitos por formación.

Esta spec es hermana de la **024** (comunidad por formación: Muro, Eventos, Cursos, Tribu, Ranking y Testimonios) y se
apoya en la **022** (el modelo de formaciones). No repite lo que ellas definen.

## Contexto

**Lo que decidió el dueño (31/10/2026), y que esta spec da por hecho:**

- La app pasa a tener **tres formaciones**: *Escuela Renaser* (90 días), *Diplomado Renaser* (3 meses o 1 año) y
  *Certificación Renaser* (90 días).
- **Un aprendiz pertenece a UNA formación a la vez.** El personal (guía, mentor, líder de mentores) puede tener una o
  varias, que asigna Administración; con varias, **elige en cuál trabaja**. **Administración y Alquimista ven las tres.**
- **Los roles son los mismos, pero aislados:** nadie ve datos de otra formación.
- **Todo lo que existe hoy pasa a ser de la Escuela Renaser.**
- El Mapa de Renacimiento se retira (spec 026): no se trata aquí.

**Cómo funcionan hoy los hábitos** (lo que esta spec cambia y lo que NO):

- Hay **un solo catálogo global** de hábitos del programa. Administración lo gestiona en *Administración → Más opciones →
  «Hábitos del programa»* (spec 001), y cada hábito puede ser «para todos» o solo para ciertos **roles** (desde el día
  siguiente). Cada persona tiene además sus **hábitos personales**.
- Cada hábito está en una **categoría**, que la app muestra como **dimensión** en Training: Cuerpo, Mente, Emociones y
  Espíritu. Son cuatro y fijas: ni el servidor ni la app permiten crear, renombrar, ordenar ni quitar una. («Vida y negocio»,
  la quinta dimensión que ve la persona, reúne las acciones de sus objetivos: no es una categoría de hábito y esta spec no
  la toca.)
- Todo lo que cuenta hábitos —**Hoy** («Hábitos de hoy», el hábito del momento), **Training** (lista por dimensión,
  «Planificar», la hoja de cada hábito), agenda, alarmas, semáforo, coherencia, racha, puntos, cierre del día, tiempo libre
  y la ficha del mentor y del líder— lee ese único catálogo.
- Hay hábitos con **flujo propio** (Despertar y Dormir, Pastilla Renacer, Audioterapia, Clase diaria, post diario en
  Comunidad, Kilómetros, Gimnasio): su identidad funcional es única en todo el sistema, y el Ranking tiene pestañas de
  Kilómetros y Gimnasio ligadas a dos de ellos.

**Lo que se quiere:** que cada formación tenga **su propio catálogo de hábitos y sus propias categorías**, que Administración
los configure por formación, y que cada persona vea solo los de la suya, en todas las pantallas, sin que nada se cruce.

## Clarifications

### Decisiones del dueño (2026-10-31)

Son las del apartado *Contexto* y el pedido textual de arriba. No hay otras decisiones del dueño sobre esta spec todavía.

### Decisiones del dueño (2026-10-31)

Fuente: `~/.config/renaser-e2e/formaciones/decisiones-del-dueno-2026-10-31.md`. Todo es la opción A (recomendada) de cada pregunta. Mandan sobre lo que
diga el resto del documento.

1. **Cada formación tiene sus hábitos (A).** Copiar un hábito a otra formación crea una **copia independiente** (editar uno no cambia el otro). Vale igual
   para cursos y eventos (spec 024).
2. **Diplomado y Certificación arrancan vacías (A).** Administración arma su catálogo y sus categorías antes de que entre el primer participante.
3. **Personal con varias formaciones (A):** «Mi programa» existe solo en la formación donde lo hace; en otra, un aviso de una línea lleva a cambiar de formación.
4. Solo **Administración y Alquimista** configuran categorías y hábitos. Una categoría **con historial se desactiva** (no se elimina). Los **hábitos propios se
   conservan** al cambiar de formación.
5. **El cambio de formación de un aprendiz rige al instante** (decisión 024-9 B, que manda sobre el «desde su día siguiente» de esta spec): la participación se
   reinicia en la transacción del traslado y, desde ese momento, la persona solo recibe y ve los hábitos de la formación nueva; su historial queda como estaba.

Las preguntas abiertas de la primera versión (hábito compartido o copia, con qué arrancan las formaciones nuevas, «Mi programa» del personal) quedan
resueltas así; los marcadores `[NEEDS CLARIFICATION]` de FR-023, FR-025 y FR-030a ya no aplican.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cada persona ve solo los hábitos de su formación (Priority: P1)

Una persona que hace su programa ve, en todas las pantallas donde aparecen hábitos —Hoy, Training (la lista por categoría,
«Planificar» y la hoja de cada hábito), agenda, alarmas y avisos—, únicamente los hábitos de su formación (más sus hábitos
personales). Quien la acompaña ve lo mismo que ella, de esa formación.

**Why this priority**: Es el núcleo del pedido («mostrar los hábitos por formación») y la mitad del aislamiento: un hábito
de otra formación que se cuela cuenta puntos, rachas y semáforo que no corresponden.

**Independent Test**: dos aprendices, uno en la Escuela y otro en el Diplomado, cada uno con un hábito que el otro no tiene.
Recorrer Hoy, Training (incluido «Planificar»), agenda, alarmas, avisos y la ficha de su mentor: cada uno ve solo el suyo.

**Acceptance Scenarios**:

1. **Given** un hábito que solo existe en el Diplomado, **When** un aprendiz de la Escuela abre Training, Hoy y «Planificar»,
   **Then** no aparece en ninguna pantalla, ni cuenta en «N de M hechos hoy», en el semáforo, en la coherencia ni en la racha.
2. **Given** un aprendiz del Diplomado, **When** abre Training, **Then** ve las categorías del Diplomado, en el orden del
   Diplomado, cada una con sus hábitos, y nada de la Escuela.
3. **Given** el mentor de ese aprendiz (solo del Diplomado), **When** abre su ficha, **Then** ve sus hábitos del Diplomado,
   los mismos que ve el aprendiz.
4. **Given** el id o el enlace de un hábito de otra formación (de un aviso viejo, de un enlace guardado), **When** la persona
   lo abre, **Then** recibe lo mismo que si no existiera («Este hábito ya no está disponible»), sin pista de que existe.
5. **Given** una persona de la Escuela que no cambia de formación, **When** abre la app después del despliegue, **Then** ve
   exactamente lo mismo que el día anterior.

---

### User Story 2 - Administración configura las categorías de cada formación (Priority: P1)

Un ADMIN o ALQUIMISTA elige una formación y, en *Hábitos del programa → Categorías*, ve las categorías de esa formación. Puede
**crear** una, **renombrarla**, **ordenarlas**, elegir su **ícono** entre los de la app, **activarla o desactivarla** y
**eliminarla** si está vacía. Las categorías de una formación son las dimensiones que sus participantes ven en Training.

**Why this priority**: Es la primera frase del pedido («configurar las categorías de hábito por formación»). Hoy son cuatro
fijas; sin esto, el Diplomado y la Certificación quedarían atados a las dimensiones de la Escuela.

**Independent Test**: con una cuenta ADMIN, crear la categoría «Estudio» en el Diplomado, ponerle un ícono y un hábito; un
aprendiz del Diplomado la ve en Training como una dimensión más, sin APK nuevo; uno de la Escuela no la ve.

**Acceptance Scenarios**:

1. **Given** la formación elegida, **When** Administración crea una categoría con nombre e ícono, **Then** aparece en la lista
   de esa formación y no en las otras, y los participantes de esa formación la ven como dimensión en cuanto tiene un hábito
   activo que les toca.
2. **Given** una categoría con hábitos activos, **When** Administración intenta desactivarla o eliminarla, **Then** el sistema
   no lo hace y dice «Esta categoría tiene hábitos activos. Muévelos o desactívalos primero.».
3. **Given** dos categorías de la misma formación, **When** Administración cambia su orden, **Then** Training las muestra en
   el nuevo orden a todos los participantes de esa formación (y a nadie más).
4. **Given** un nombre que ya existe en la misma formación (sin distinguir mayúsculas ni tildes), **When** se intenta crear
   o renombrar, **Then** se rechaza con un motivo claro; el mismo nombre en **otra** formación es válido.
5. **Given** un usuario que no es ADMIN ni ALQUIMISTA (aprendiz, guía, mentor, líder) o una cuenta suspendida, **When**
   intenta crear, cambiar o borrar una categoría, **Then** el servidor lo rechaza.
6. **Given** una app ya publicada que no conoce una categoría nueva, **When** la recibe, **Then** la muestra con un ícono
   genérico y sigue funcionando.

---

### User Story 3 - Administración configura el catálogo de hábitos de cada formación (Priority: P1)

En *Administración → Más opciones → «Hábitos del programa»* (que ya existe, spec 001), Administración elige la formación y
ve **el catálogo de esa formación**: puede crear, editar, activar, desactivar y eliminar hábitos, y elegir a qué **roles**
aplica cada uno (spec 001, sin cambios). Cada hábito se crea dentro de una categoría **de esa formación**. «Copiar a otra
formación» crea el mismo hábito en la formación elegida, ya independiente.

**Why this priority**: Sin esto el catálogo sigue siendo uno solo y «hábitos por formación» no se puede operar.

**Independent Test**: con una cuenta ADMIN, en el Diplomado crear un hábito en la categoría «Estudio», restringirlo a MENTOR
y copiarlo a la Certificación; verificar que cada copia se edita sin afectar a la otra y que el aprendiz de la Escuela no ve
ninguna.

**Acceptance Scenarios**:

1. **Given** el selector de formación, **When** Administración cambia de formación, **Then** la lista, los filtros por rol, los
   activos e inactivos y los contadores son los de esa formación, y la pantalla dice cuál es en su cabecera.
2. **Given** el formulario de un hábito, **When** Administración elige la categoría, **Then** solo ofrece las de la formación
   que está editando.
3. **Given** un hábito de la Escuela, **When** Administración elige «Copiar a otra formación» y la Certificación, **Then**
   se crea en la Certificación (en la categoría que elija de ella) con el mismo contenido, y desde ese momento editar,
   desactivar o eliminar uno no cambia al otro.
4. **Given** un hábito con registros en el historial, **When** Administración intenta eliminarlo, **Then** el sistema lo
   rechaza y ofrece desactivarlo (regla de hoy, sin cambio), dentro de la misma formación.
5. **Given** un hábito con regla propia (Pastilla Renacer, Clase diaria, Audioterapia, post diario, Despertar/Dormir,
   Kilómetros, Gimnasio) copiado a otra formación, **When** un participante de esa formación lo usa, **Then** su flujo
   funciona igual que en la Escuela, con los datos de su formación.
6. **Given** un cambio de roles de un hábito, **When** se guarda, **Then** rige desde el día siguiente como en la spec 001,
   dentro de la formación.
7. **Given** un usuario sin permiso o una cuenta suspendida, **When** intenta cualquier cambio del catálogo, **Then** se
   rechaza.

---

### User Story 4 - Training, Hoy y Plan muestran el mundo de mi formación (Priority: P2)

Lo que la persona ve en las tres pestañas sale de **su formación**: Training dibuja las categorías de su formación como
dimensiones; Hoy cuenta solo sus hábitos; Plan no muestra hábitos de otra formación.

**Why this priority**: Es la cara visible de las historias 1 a 3, pero sin ella las otras no se notan.

**Independent Test**: con el Diplomado configurado con 3 categorías propias, un aprendiz suyo ve en Training exactamente 3
dimensiones (más «Vida y negocio» si tiene objetivos) con sus nombres e íconos, y «Hábitos de hoy» en Hoy cuenta solo los suyos.

**Acceptance Scenarios**:

1. **Given** una formación con tres categorías activas, **When** un participante abre Training, **Then** ve tres dimensiones,
   con el nombre e ícono que Administración les puso, cada una con su avance («0 de 4 hoy»).
2. **Given** una categoría sin ningún hábito que le toque hoy a esa persona, **When** abre Training, **Then** esa categoría no
   se dibuja (no queda una dimensión vacía).
3. **Given** una formación que no ofrece Despertar ni Dormir, **When** un participante abre Mi Radar, **Then** se comporta
   como un día sin Despertar (spec 019): «Falta tu hora de Despertar», sin inventar un horario.
4. **Given** una formación que no ofrece los hábitos de Kilómetros o Gimnasio, **When** se abre el Ranking, **Then** esas
   pestañas no aparecen en esa formación (ver spec 024).
5. **Given** una formación sin ningún hábito activo para la persona, **When** abre Training, **Then** ve «Tu formación
   todavía no tiene hábitos.» en vez de una pantalla vacía, y Hoy no dibuja «Hábitos de hoy».
6. **Given** la pestaña Plan, **When** la persona la abre, **Then** nada de lo que muestra sobre hábitos, fase o día es de
   otra formación.

---

### User Story 5 - Personal con varias formaciones y Administración con las tres (Priority: P2)

Un guía, mentor o líder asignado a dos formaciones **elige en cuál trabaja** con el selector de formación (spec 022). Todo lo
que ve —aprendices, fichas, hábitos, categorías— es de la formación elegida. Administración y Alquimista usan el mismo selector
con las tres y, en las listas de administración, un filtro «Todas» donde **cada fila lleva el nombre de su formación**.

**Why this priority**: Evita que quien trabaja en dos formaciones vea mezclado lo que no debe, y es la única vía por la que
Administración «ve las tres».

**Independent Test**: un mentor asignado a la Escuela y al Diplomado cambia de formación activa: la lista de aprendices, las
fichas y los hábitos cambian por completo, sin cerrar la app y sin rastro de la anterior.

**Acceptance Scenarios**:

1. **Given** un mentor con una sola formación, **When** usa la app, **Then** no ve ningún selector ni la palabra «formación»
   en las pantallas de hábitos (la app es la de siempre).
2. **Given** un mentor con dos formaciones, **When** cambia la formación activa, **Then** Training, Hoy y las fichas se
   recargan con los hábitos y aprendices de la nueva, sin mostrar ni un cuadro de la anterior.
3. **Given** un líder de mentores con dos formaciones, **When** abre «Mis mentores» y una ficha, **Then** solo aparecen las
   personas, los hábitos y las categorías de la formación activa.
4. **Given** Administración en «Hábitos del programa» con el filtro «Todas», **When** mira la lista, **Then** cada hábito
   muestra su formación; crear o editar siempre ocurre dentro de una formación concreta.
5. **Given** un guía que hace su propio programa en una sola de sus formaciones, **When** tiene activa la otra, **Then**
   *(según la pregunta abierta 3)* «Mi programa» muestra el aviso de una línea «Tu programa propio está en <formación>.
   Cámbiate a ella para verlo.».

---

### User Story 6 - El aprendiz que cambia de formación no pierde nada (Priority: P2)

Cuando Administración pasa a un aprendiz de una formación a otra (spec 022), sus hábitos pasan a ser los de la nueva desde su
día siguiente; todo lo que hizo antes se conserva, atribuido a la formación en que lo hizo.

**Why this priority**: Es el caso de borde que más puede dañar datos y confianza (puntos y rachas).

**Independent Test**: pasar a un aprendiz de la Escuela al Diplomado con un registro de hoy pendiente y 12 días de racha:
hoy puede cerrar su registro de la Escuela; mañana ve los hábitos del Diplomado; su historial de la Escuela sigue completo.

**Acceptance Scenarios**:

1. **Given** un registro de hoy de un hábito de la Escuela, **When** el aprendiz cambia de formación durante el día, **Then**
   puede completarlo hoy y recibe sus puntos; desde su día siguiente ya no se le genera nada de la Escuela.
2. **Given** su historial de la Escuela, **When** se consulta después del cambio, **Then** los registros, puntos y rachas
   pasados siguen ahí, ninguno modificado, y no cuentan en lo que se muestra de la nueva formación.
3. **Given** la hora, los días, la pausa y el nombre propio que había puesto en hábitos de la Escuela, **When** vuelve a la
   Escuela, **Then** los recupera tal cual.
4. **Given** sus hábitos personales, **When** cambia de formación, **Then** siguen siendo suyos; si su categoría no existe
   en la nueva formación, se agrupan bajo «Mis hábitos».
5. **Given** un aviso o alarma local de un hábito de la formación anterior, **When** el teléfono la dispara después del
   cambio, **Then** no suena ni se muestra, y la siguiente puesta al día la retira.

---

### User Story 7 - Todo lo de hoy sigue siendo la Escuela (Priority: P3)

Al desplegar, todos los hábitos, categorías, registros, preferencias, rachas y horarios existentes pasan a la **Escuela
Renaser** sin que nadie note diferencia.

**Why this priority**: Es la garantía de «no romper la lógica actual», pero no agrega capacidad nueva.

**Independent Test**: tomar una foto de lo que ven 10 cuentas (hábitos, categorías, horarios, puntos, rachas, semáforo)
antes y después del despliegue: idénticas.

**Acceptance Scenarios**:

1. **Given** el catálogo y las cuatro categorías de hoy, **When** se despliega, **Then** todos pertenecen a la Escuela con el
   mismo nombre, orden, roles, horarios y guías.
2. **Given** una app ya publicada (que no envía nada de formación), **When** una cuenta de la Escuela la usa, **Then**
   funciona completa y sin errores.
3. **Given** una persona nueva, **When** entra, **Then** su formación se resuelve como define la spec 022; nunca queda sin
   catálogo por falta de una asignación pendiente (ve el aviso de formación sin hábitos, no un error).

### Edge Cases

- **Hábito con el mismo título en dos formaciones** (por una copia): válido; el título es único dentro de cada formación, no
  en todo el sistema.
- **Categoría desactivada con hábitos inactivos**: válido; su historial se conserva y reaparece si se reactiva.
- **Mover un hábito a otra categoría de la misma formación**: rige en el acto en pantalla; los registros ya generados conservan
  la categoría con que se generaron.
- **Mover un hábito a otra formación**: no existe; se copia (pregunta abierta 1).
- **Un rol sin ningún hábito en una formación** (por la audiencia por rol de la spec 001): ve «Tu formación todavía no tiene
  hábitos.» y no un error.
- **Personal con varias formaciones**: cada cambio de formación activa recarga todo; una vista de la formación anterior que
  quedara abierta (una hoja, un detalle) se cierra.
- **Aprendiz cambiado de formación más de una vez en el mismo día**: rige la última, desde su día siguiente.
- **Persona en otra zona horaria**: «mañana» es su día siguiente (principio V de la constitución).
- **Hábito de otra formación en un backup de alarmas locales o un aviso agrupado**: no se arma; el rearmado de alarmas
  (spec 007) solo conoce los hábitos de la formación vigente.
- **Pausa «hasta que yo lo reactive» de un hábito de otra formación**: se conserva; no se ve ni cuenta mientras la persona no
  esté en esa formación.
- **Cuenta de Soporte TI «ver como»**: ve los hábitos de la formación del rol simulado (sin cambios de comportamiento; el
  detalle de su aislamiento lo define la spec 022).
- **Mensaje de error de un id de otra formación**: idéntico al de un id inexistente (no se confirma existencia).

## Requirements *(mandatory)*

### Functional Requirements

**Pertenencia de hábitos y categorías**

- **FR-001**: Cada hábito del catálogo global MUST pertenecer a exactamente **una** formación.
- **FR-002**: Cada categoría de hábito MUST pertenecer a exactamente una formación, y cada hábito MUST estar en una categoría
  **de su misma formación**.
- **FR-003**: Cada categoría MUST tener nombre, orden, ícono (de entre los que la app ya conoce) y estado activa/inactiva.
  El nombre MUST ser único dentro de su formación (sin distinguir mayúsculas ni tildes).
- **FR-004**: Todo lo existente —los hábitos del catálogo, las cuatro categorías de hoy, sus horarios, guías, audiencias por
  rol, preferencias, registros, puntos y rachas— MUST quedar en la **Escuela Renaser** sin cambiar de nombre, orden, regla ni
  valor.
- **FR-005**: Un hábito con flujo propio (Despertar y Dormir, Pastilla Renacer, Audioterapia, Clase diaria, post diario,
  Kilómetros, Gimnasio) MUST funcionar con el hábito de la formación de la persona. Una formación que no lo ofrece MUST NOT
  tener ese flujo ni lo que cuelga de él (por ejemplo, las pestañas de Kilómetros y Gimnasio del Ranking).
- **FR-006**: El título de un hábito del catálogo MUST ser único dentro de su formación (hoy lo es en todo el sistema).
- **FR-007**: Los hábitos personales MUST seguir siendo de la persona. Su categoría MUST ser una de la formación de la persona;
  si no existe en la formación vigente, MUST agruparse bajo «Mis hábitos».

**Qué ve cada persona**

- **FR-008**: La generación diaria de registros MUST considerar solo los hábitos activos de la formación de la persona que
  además le aplican por su rol (spec 001).
- **FR-009**: Training MUST dibujar como dimensiones las categorías **activas de la formación** de la persona, en el orden
  que Administración les dio, con el nombre y el ícono que vienen del servidor; una categoría sin hábitos que le toquen
  a la persona hoy MUST NOT dibujarse. «Vida y negocio» MUST seguir como hoy.
- **FR-010**: Hoy, Training, «Planificar», la hoja de cada hábito, el catálogo para elegir, las preferencias de horario, la
  agenda, las alarmas, los avisos y el cierre del día MUST mostrar y usar solo hábitos de la formación de la persona.
- **FR-011**: Coherencia, semáforo, racha, puntos posibles, «todos los hábitos del día», tiempo libre, celebraciones y todo
  contador de hábitos MUST contar solo los hábitos de la formación (consecuencia de FR-008).
- **FR-012**: Las vistas de acompañamiento —ficha del guía y del mentor, «Mi grupo», «Mis mentores» y la ficha del líder, y la
  ficha de Administración— MUST mostrar los hábitos y categorías de la formación **en que se mira a la persona**, los mismos
  que ve ella.
- **FR-013**: Una app ya publicada MUST seguir funcionando para las cuentas de la Escuela sin enviar nada nuevo. Si recibe una
  categoría que no conoce, MUST mostrarla con un ícono genérico sin romper la pantalla.
- **FR-014**: Una formación sin ningún hábito para la persona MUST decir «Tu formación todavía no tiene hábitos.» en Training y
  MUST NOT dibujar «Hábitos de hoy» en Hoy; nunca un error ni una pantalla vacía.

**Cambio de formación e historial**

- **FR-015**: El cambio de formación de un aprendiz MUST regir desde su día siguiente, en su zona horaria; el registro de hoy
  ya generado MUST conservarse y poder completarse. Para el personal, cambiar la formación activa MUST regir en el acto
  (solo cambia lo que mira, no sus hábitos).
- **FR-016**: Los registros, puntos, rachas y ajustes pasados MUST conservarse sin modificarse y quedar atribuidos a la
  formación en que se hicieron; MUST NOT contar en lo que se muestra de otra formación.
- **FR-017**: La configuración personal de un hábito (hora, días, pausa, nombre propio) MUST conservarse cuando el hábito deja
  de aplicar a la persona y MUST recuperarse si vuelve a aplicar.
- **FR-018**: Los avisos y alarmas locales de un hábito de otra formación MUST NOT sonar ni mostrarse; la puesta al día de
  alarmas MUST retirarlas.

**Administración**

- **FR-019**: Solo ADMIN y ALQUIMISTA con la cuenta activa MUST poder crear, editar, ordenar, activar, desactivar y eliminar
  categorías y hábitos de **cualquier** formación; cualquier otro rol o cuenta suspendida MUST ser rechazado (403).
- **FR-020**: «Hábitos del programa» MUST ofrecer un selector de formación; la lista, los filtros (por rol, activos e
  inactivos) y los formularios MUST ser los de la formación elegida, y la cabecera MUST decir cuál es.
- **FR-021**: Las listas de administración MUST ofrecer un filtro «Todas»; en él cada fila MUST llevar el nombre de su
  formación, y crear o editar MUST ocurrir siempre dentro de una formación concreta.
- **FR-022**: Administración MUST poder crear, renombrar, reordenar, cambiar el ícono, activar/desactivar y eliminar categorías
  de una formación. Una categoría con hábitos activos MUST NOT poder desactivarse ni eliminarse (mensaje de la historia 2); una
  categoría con registros históricos MUST poder desactivarse pero no eliminarse.
- **FR-023**: «Copiar a otra formación» MUST crear en la formación elegida un hábito con el mismo contenido (título,
  descripción, tipo, evidencia, opcionalidad, obligatorio en intoxicación, audiencia por rol) en la categoría que
  Administración elija de esa formación, **sin ningún vínculo posterior** con el original.  *(Decisión del dueño 1: copia independiente.)*
- **FR-024**: Cada alta, cambio o baja de una categoría o de la formación de un hábito MUST quedar registrado: quién, cuándo,
  qué cambió.
- **FR-025**: Administración MUST poder dejar lista una formación nueva (categorías y hábitos) **antes** de que tenga
  participantes, y la formación MUST aparecer a sus participantes sin publicar una app nueva. *(Decisión del dueño 2: arrancan vacías.)*
- **FR-026**: La regla de la spec 001 (audiencia por rol, «desde mañana», historial y configuración conservados) MUST seguir
  rigiendo **dentro de cada formación**, sin cambios.

**Aislamiento**

- **FR-027**: Ninguna lectura ni escritura de hábitos, categorías, registros, preferencias, avisos o contadores de hábitos
  MUST cruzar formaciones, para ningún rol, **salvo** la de Administración y Alquimista, que opera siempre sobre una formación
  explícita (o con el filtro «Todas» solo para leer, con cada fila rotulada).
- **FR-028**: Pedir por id, enlace o aviso un hábito, un registro o una categoría de una formación que no es la de la persona
  MUST responder exactamente lo mismo que un id inexistente.
- **FR-029**: Un push o una alarma de un hábito MUST llegar solo a personas de su formación.

**Pantallas**

- **FR-030**: La persona con **una sola** formación MUST NOT ver ningún selector de formación ni la palabra «formación» en las
  pantallas de hábitos. La que tiene varias MUST verlo en un solo lugar uniforme (spec 022) y cambiarlo MUST recargar todas
  las pestañas.
- **FR-030a**: El personal con varias formaciones que hace su propio programa en una de ellas MUST ver «Mi programa» (Training
  y Hoy) con los hábitos de esa formación; con otra activa MUST ver un aviso de una línea que lo dice y lleva a cambiar de
  formación. *(Decisión del dueño 3.)*
- **FR-031**: Las pantallas nuevas o cambiadas MUST cumplir las reglas de la app: un solo scroll, gesto atrás que vuelve un
  nivel, áreas táctiles de 48 px o más, tema claro y oscuro, español de Perú con tuteo, validación antes de enviar y
  confirmación en línea al guardar.
- **FR-032**: **Training, Hoy y Plan no cambian de forma** (diseño, textos, tabs): cambia de dónde salen las categorías y los
  hábitos, y aparece el selector solo para quien tiene varias formaciones.

### Key Entities

- **Formación**: Escuela, Diplomado o Certificación (definida en la spec 022). Aquí es el dueño de un catálogo y de sus
  categorías.
- **Categoría de hábito (dimensión)**: nombre, orden, ícono, activa o no, y su formación. Hoy son cuatro fijas.
- **Hábito del catálogo**: lo que ya es (título, descripción, tipo, evidencia, opcional, audiencia por rol, horarios, guías),
  más su formación y su categoría.
- **Hábito personal**: de una persona; sin formación propia; se agrupa bajo una categoría de su formación vigente.
- **Registro del hábito**: historia; conserva el hábito, su categoría y la formación del día en que se generó.
- **Configuración personal del hábito**: hora, días, pausa y nombre propio de una persona para un hábito; se conserva aunque el
  hábito deje de aplicarle.
- **Pertenencia y formación activa** (spec 022): a qué formación(es) pertenece cada persona y cuál tiene activa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un ADMIN crea una categoría con su ícono y un hábito en ella para una formación, y un aprendiz de esa formación
  los ve en Training en menos de 5 minutos, sin publicar una app nueva.
- **SC-002** *(prueba E2E de aislamiento de hábitos)*: con dos aprendices en formaciones distintas y un hábito y una categoría
  exclusivos de cada una, en **cada** una de estas pantallas el 100 % de los elementos de la otra formación está ausente: Hoy,
  Training (lista, «Planificar», hoja del hábito), agenda de Eventos, alarmas del teléfono, avisos, cierre del día, tiempo
  libre, la ficha de su mentor y la de su líder. 0 excepciones.
- **SC-003**: Tras el despliegue, el 100 % de las personas existentes ve los mismos hábitos, categorías, horarios, puntos y
  rachas que antes (0 diferencias en una muestra de 10 cuentas de todos los roles).
- **SC-004**: Ninguna persona pierde registros, puntos, rachas ni configuración por un cambio de formación o de catálogo
  (0 registros pasados modificados; se comprueba con una cuenta que cambia de ida y vuelta).
- **SC-005**: El 100 % de los intentos de un rol sin permiso (aprendiz, guía, mentor, líder) o de una cuenta suspendida de
  cambiar categorías o hábitos del catálogo es rechazado.
- **SC-006**: El 100 % de los accesos directos por id, enlace o aviso a un hábito, registro o categoría de otra formación
  recibe la misma respuesta que un inexistente.
- **SC-007**: Una app ya publicada recorre sin errores Hoy y Training con una cuenta de la Escuela (flujos de regresión en
  verde) y una categoría nueva se ve sin romperla.
- **SC-008**: Un mentor con dos formaciones cambia la activa en 2 toques o menos y ve la pantalla entera de la nueva
  formación, sin rastro de la anterior, sin cerrar la app.
- **SC-009**: El 100 % de los intentos de desactivar o eliminar una categoría con hábitos activos es rechazado con el motivo.
- **SC-010**: Pasar un aprendiz de formación el día D deja intactos sus registros y puntos hasta el cierre de D y le da los
  hábitos de la nueva el día D+1 (verificado con el reloj en la madrugada UTC, día local anterior en Lima).

## Restricciones del dueño

Dichas por el dueño el 31/10/2026 para todas las specs; aquí aplicadas a los hábitos, sin entrar en el diseño técnico.

1. **Reutilizar lo que ya existe.** El catálogo de hábitos, las categorías, los registros, las preferencias, la pantalla
   «Hábitos del programa» y sus flujos se **extienden**, no se duplican. Nada de tablas nuevas si una existente sirve con una
   columna o una relación: la formación es un dato más de lo que ya hay, no un catálogo paralelo por formación. Los datos
   quedan normalizados; si algo se desnormaliza (por ejemplo, la formación guardada en un registro histórico), debe estar
   justificado por la regla de que el historial no se reescribe.
2. **No romper la lógica actual.** Todo lo de hoy sigue funcionando como Escuela Renaser: horarios, guías, audiencia por rol
   (spec 001), cambio de día (spec 002), alarmas por día (008–010), tiempo libre, cierre del día, Mi Radar y puntos.
3. **Respetar la constitución del repo y las reglas de la app.** Hexagonal, Spring Modulith y SOLID en el backend (la
   constitución, principios I a VII); `AGENTS.md` del frontend en la app (los cinco tabs no se tocan salvo lo autorizado, un
   solo scroll, 48 px, tipo oración, español de Perú con tuteo). Esta spec toca Hoy, Training y Plan: la **autorización
   explícita** es el pedido del dueño del 31/10/2026 y alcanza solo a «de dónde salen las categorías y los hábitos» y al
   selector; al implementarse se anota en `AGENTS.md` con fecha y «lo que cambia y nada más» (principio VI). Las migraciones
   llevan su cabecera (principio IV), el tiempo sigue siendo el del participante (principio V) y se documenta en el mismo
   cambio (principio VII).
4. **Aislamiento total entre formaciones: nunca se cruza información.** Los hábitos y las categorías de una formación son
   **invisibles** para las otras. El criterio verificable por E2E es SC-002 y SC-006: dos cuentas en formaciones distintas,
   cada una con un hábito y una categoría exclusivos, y cero elementos de la otra en cualquier pantalla, aviso, contador,
   ficha o enlace.
5. **Todas las dudas, antes de implementar.** El dueño quiere hacer él mismo las preguntas: están las tres de *Clarifications*
   y todas las que solo llevan supuesto, marcadas abajo con «(duda)».

## Assumptions (Supuestos)

Cada «(duda)» es una pregunta real para el dueño aunque ya tenga un supuesto; las marcadas «(cambia el alcance)» pueden
agregar o quitar trabajo.

- La **spec 022** define las formaciones, la pertenencia, la formación activa y el selector. Aquí se usa su vocabulario sin
  redefinirlo.
- Los roles de spec 001 siguen igual; un hábito sin roles marcados es «para todos» **dentro de su formación**.
- **(duda; cambia el alcance)** Hábito compartido o copia independiente: ver pregunta abierta 1.
- **(duda; cambia el alcance)** Con qué arrancan Diplomado y Certificación: ver pregunta abierta 2. Por defecto, vacías.
- **(duda; cambia el alcance)** «Mi programa» del personal con varias formaciones: ver pregunta abierta 3.
- **(duda; cambia el alcance)** Las categorías iniciales del Diplomado y la Certificación: por defecto, las que
  Administración cree; si el dueño quiere las mismas cuatro de la Escuela, se copian al configurarlas.
- **(duda)** El cambio de formación de un aprendiz rige **desde su día siguiente** (mismo criterio de la spec 001) tanto para
  hábitos como para lo demás; si la spec 022 fija otro instante, rige el de la 022. Para el personal, cambiar la formación
  activa rige en el acto.
- **(duda)** Quién configura el catálogo y las categorías: solo ADMIN y ALQUIMISTA, como hoy; el líder de mentores **no**,
  ni siquiera en su formación.
- **(duda)** Eliminar una categoría: solo si está vacía y sin historial; si tiene historial, solo se desactiva.
- **(duda)** Los hábitos personales de una persona que cambia de formación se conservan y, si su categoría no existe en la
  nueva, van bajo «Mis hábitos».
- **(duda)** Los íconos de una categoría salen de la lista que la app ya conoce; ampliarla pide una app nueva, y una categoría
  nueva sin ícono usa uno genérico.
- «Vida y negocio» (las acciones de los objetivos) **no** es una categoría de hábito y no se gestiona aquí; se ve como hoy
  para quien tiene objetivos. Su relación con las formaciones nuevas la definen las specs de objetivos.
- Las reglas del programa que dependen del día (fases, ciclos de intoxicación consciente, semáforo, día del programa) son
  del programa de cada formación y se definen en la spec 022 y las que siguen; aquí solo se exige que los hábitos que usan
  sean los de la formación.
- Los horarios, guías y audioterapias de cada hábito siguen administrándose como hoy (fuera de la app) y pertenecen al hábito;
  al copiar un hábito se copian junto con él.
- Mi Radar, el cierre del día y el tiempo libre no cambian de regla: solo leen los hábitos de la formación.
- Las pruebas del horario diario se corren con el reloj en la madrugada UTC.

## Dependencias

- **Spec 022 (formaciones)**: el modelo, la pertenencia, la formación activa y el selector. Esta spec no se puede planificar
  hasta que la 022 fije esos términos.
- **Spec 001 (hábitos por rol)**: la audiencia por rol se mantiene dentro de cada formación.
- **Spec 024 (comunidad por formación)**: el Ranking y la publicación del post diario dependen de qué hábitos ofrece cada
  formación.

## Fuera de alcance

- El modelo de formaciones y de pertenencia (spec 022) y su selector.
- La comunidad: Muro, Eventos, Cursos, Tribu, Ranking y Testimonios (spec 024).
- Las reglas del programa de cada formación (fases, ciclos, semáforo, día del programa) y qué hábitos concretos lleva cada una.
- Los objetivos y «Vida y negocio», el Mapa de Renacimiento y la ficha con diagnóstico (spec 026).
- La fórmula de puntos, coherencia y semáforo: no cambia, solo qué hábitos entran.
- Hábitos personales (cómo se crean) y editar los horarios y guías de un hábito.
- Avisar a las personas cuando cambia su formación o su catálogo.
