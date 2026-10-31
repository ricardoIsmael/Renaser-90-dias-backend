# Feature Specification: Formaciones — Escuela, Diplomado y Certificación Renaser

**Feature Branch**: `022-formaciones`

**Created**: 2026-10-31

**Status**: Draft — con 3 preguntas abiertas que cambian el alcance (ver «Dudas para el dueño»)

**Input**: Pedido del dueño del 2026-10-31, textual: «El sistema debe estar compuesto por 3 instituciones: Escuela RENASER;
Diplomado Renaser, que tendrá una duración de 3 meses o 1 año; y Certificación, que durará 3 meses o 90 días, igual que la
Escuela Renaser, que dura 90 días.» Requerimientos del dueño: soportar 3 tipos de formaciones; que la autenticación detecte a
cuál pertenece cada usuario; que se muestren las vistas y se carguen los días según la formación; que se pueda establecer el
tiempo de duración de cada una; que los mismos roles existan en cada formación **sin cruzarse** («un aprendiz de la Escuela con un
aprendiz del Diplomado»); y, para Administrador / Alquimista, un lugar donde gestionar las tres, con toda la información importante
dentro de cada una.

**Repos**: backend (`Renaser-90-dias-backend`, donde vive esta spec) y app (`Renaser-90-dias-frontend-`). La app se programa en
paralelo con este contrato; ninguna copia de la spec vive en el frontend.

**Specs hermanas de la misma tanda** (cada una cubre una parte del pedido del 31/10 y se apoya en esta): `025-solicitud-con-formacion`
(aceptar a una persona eligiendo formación, rol y ficha), `026-ficha-con-diagnostico` (ficha administrable, diagnóstico y retiro del
Mapa de Renacimiento) y `027-terminos-editables`. Otras specs de la tanda, escritas por otros agentes y todavía en redacción, cubren los
hábitos por formación y la planificación de la semana y el mes; esta spec **no** las repite.

## Contexto

Hoy Renaser es **un solo programa de 90 días**: todas las personas hacen el mismo recorrido, comparten el mismo Muro, los mismos
cursos, el mismo ranking y el mismo catálogo de hábitos, y los grupos (células) viven todos bajo el mismo programa. Cada cuenta tiene
**un solo rol** (aprendiz, guía de aprendiz, mentor, líder de mentores, administrador, alquimista; Soporte TI aparte). Ese programa es,
en la práctica, lo que el dueño llama **Escuela Renaser**.

El dueño quiere que Renaser pase a ser tres formaciones (él las llama «instituciones») con la misma estructura de roles pero
**totalmente separadas entre sí**:

| Formación | Duración | Quién la elige |
|---|---|---|
| **Escuela Renaser** | 90 días | — |
| **Diplomado Renaser** | 3 meses o 1 año | se elige al aceptar a la persona (spec 025) |
| **Certificación Renaser** | 90 días | — |

La duración de cada formación **la configura Administración**.

## Clarifications

### Session 2026-10-31 (decisiones del dueño, ya tomadas)

- Q: ¿A cuántas formaciones pertenece un aprendiz? → A: **A una sola a la vez.** Administración puede pasarlo a otra cuando termine.
- Q: ¿A cuántas pertenece el personal (mentor, guía de aprendiz y líder de mentores)? → A: **A una o a varias**, las asigna Administración. Con
  varias, la persona elige en cuál trabaja y **nunca ve nada mezclado**.
- Q: ¿Qué ven Administrador y Alquimista? → A: **Las tres formaciones**, y las gestionan.
- Q: ¿Los roles cambian por formación? → A: No: **son los mismos en las tres, pero aislados.** Nadie ve personas ni datos de otra formación.
- Q: ¿Qué pasa con todo lo que existe hoy (usuarios, hábitos, Muro, cursos, grupos, ranking, testimonios)? → A: **Pasa a ser de la Escuela Renaser.**
- Q: ¿Quién configura la duración? → A: Administración.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entro y veo mi formación, sin elegir nada (Priority: P1)

Al iniciar sesión, el sistema sabe a qué formación pertenece la persona. Un aprendiz entra directo a su día de hoy y ve solo lo de su
formación: sus días («Día 40 de 365»), sus fases, su contenido y los nombres de su formación. No se le pregunta nada. El personal con
**una** formación tampoco ve ninguna elección.

**Why this priority**: Es la base de todo el pedido: sin saber a qué formación pertenece cada persona, nada se puede separar. Y es lo
primero que ve cada usuario.

**Independent Test**: Con tres aprendices (uno por formación), iniciar sesión con cada uno y comprobar su formación, su «Día N de M» y que el
contenido es el suyo; con un mentor de una sola formación, comprobar que no ve ninguna elección.

**Acceptance Scenarios**:

1. **Given** un aprendiz del Diplomado de 1 año en su día 40, **When** inicia sesión, **Then** entra directo a **Hoy**, sin pantallas extra, y
   ve «Día 40 de 365», el nombre de su formación y solo contenido del Diplomado.
2. **Given** un aprendiz de la Escuela, **When** abre cualquiera de las cinco pestañas, **Then** ve «Día N de 90» y exactamente lo mismo que ve hoy.
3. **Given** un mentor asignado solo a la Certificación, **When** inicia sesión, **Then** ve únicamente sus grupos y aprendices de la Certificación
   y no aparece ningún selector de formación.
4. **Given** la app publicada antes de esta función (versión anterior), **When** un aprendiz de la Escuela inicia sesión, **Then** todo funciona
   igual que antes de esta función, sin errores ni pantallas nuevas.
5. **Given** una app más nueva que el servidor (servidor que todavía no informa la formación), **When** la persona inicia sesión, **Then** la app
   se comporta como Escuela, sin romperse.

---

### User Story 2 - Nada se cruza entre formaciones (Priority: P1)

Ninguna persona ve ni toca personas, grupos, chats, publicaciones, comentarios, testimonios, cursos, eventos, hábitos, ranking, semáforo, consultas
de mentoría, avisos ni búsquedas de una formación que no es la suya. Si alguien intenta llegar a algo ajeno por un enlace, un aviso viejo o un
identificador, el sistema responde **como si no existiera**.

**Why this priority**: Es la regla central del dueño («no deben cruzarse»). Un solo cruce rompe la confianza en las tres formaciones.

**Independent Test**: Con un aprendiz de la Escuela y otro del Diplomado, recorrer las cinco pestañas con cada uno y verificar, área por área, que
el otro no aparece. Repetir las seis combinaciones posibles de pares de formaciones.

**Acceptance Scenarios**:

1. **Given** un aprendiz A de la Escuela y un aprendiz B del Diplomado, **When** A recorre Hoy, Plan, Training, Comunidad (Muro, Eventos, Cursos,
   Tribu, Ranking, Testimonios) y Yo, **Then** no aparece **ninguna** persona, publicación, hábito, evento, curso, chat ni posición de ranking del Diplomado.
2. **Given** una publicación del Diplomado, **When** un aprendiz de la Escuela abre su enlace o un aviso que lo apunta, **Then** el sistema responde como si no
   existiera (el mismo mensaje que para algo borrado), sin revelar que existe en otra formación.
3. **Given** un mentor de la Escuela, **When** busca personas, abre «Mi grupo» o la ficha de un aprendiz, **Then** solo puede llegar a personas de la Escuela.
4. **Given** Administración que avisa «a todos» (evento, publicación del programa, aviso), **When** lo envía desde una formación, **Then** llega solo a las
   personas de esa formación.
5. **Given** un líder de mentores con dos formaciones que eligió la Escuela, **When** abre sus listas (mentores, grupos, semáforo, consultas), **Then** ve solo la
   Escuela; al cambiar al Diplomado ve solo el Diplomado; **nunca** una lista con ambas.
6. **Given** un grupo de la Escuela, **When** Administración intenta sumarle un aprendiz o un mentor que no es de la Escuela, **Then** el sistema lo impide con un
   mensaje claro que nombra las dos formaciones.
7. **Given** dos personas de formaciones distintas, **When** una intenta escribirle a la otra en un chat 1 a 1, **Then** no puede (no aparece en su directorio).

---

### User Story 3 - Todo lo de hoy sigue funcionando, ahora como Escuela Renaser (Priority: P1)

Al publicar esta función, cada persona, grupo y contenido que existe hoy queda en la Escuela Renaser y nada cambia para ellos: mismo día del programa, mismos
puntos, mismas rachas, mismo ranking, mismos grupos, mismos hábitos, mismas publicaciones. Las otras dos formaciones nacen vacías.

**Why this priority**: Hay gente en mitad de su programa de 90 días y la app no se actualiza por aire. Romper lo de hoy sería peor que no tener las formaciones.

**Independent Test**: Tomar una foto de datos antes de publicar (día, puntos, racha, grupo, posición en el ranking de varias personas) y compararla con lo que se
ve después de publicar: debe ser idéntica.

**Acceptance Scenarios**:

1. **Given** una persona en el día 63 de su programa con 480 puntos, **When** se publica esta función, **Then** sigue en el día 63 con 480 puntos, en el mismo grupo y con
   la misma posición de ranking.
2. **Given** el personal existente (mentores, guías, líderes), **When** se publica, **Then** queda asignado a la Escuela; Administradores y Alquimistas ven las tres.
3. **Given** los contenidos existentes (publicaciones, comentarios, testimonios, cursos, eventos, grupos, cohortes, conversaciones), **When** se publica, **Then**
   quedan en la Escuela.
4. **Given** el Diplomado y la Certificación recién creados, **When** Administración los abre, **Then** los ve vacíos (sin personas, grupos ni publicaciones) con
   estados vacíos claros que dicen qué falta para empezar.

---

### User Story 4 - Administración gestiona las tres formaciones desde un solo lugar (Priority: P2)

Administrador y Alquimista encuentran en Administración una entrada **«Formaciones»** con las tres, y dentro de cada una toda la información importante en un
mismo sitio: qué es, cuánto dura, quiénes están, cómo van los grupos, qué ficha y qué términos usa y qué hay que atender.

**Why this priority**: Sin este lugar, el dueño no puede operar tres formaciones; pero depende de que las formaciones ya existan (P1).

**Independent Test**: Entrar como Administrador, abrir «Formaciones» y verificar que cada cifra coincide con la lista a la que lleva.

**Acceptance Scenarios**:

1. **Given** un Administrador en Administración, **When** abre «Formaciones», **Then** ve tres tarjetas (Escuela, Diplomado, Certificación) con nombre, duración(es) y
   cifras clave (aprendices activos, personal, grupos, cosas por atender); con un toque abre la formación.
2. **Given** una formación abierta, **When** la recorre, **Then** encuentra, en este orden: **Resumen** (nombre, descripción, duración(es), estado),
   **Personas** (aprendices por estado —por empezar, en curso, terminaron— y personal asignado), **Grupos** (con mentor, sin mentor, recepción), **Ficha y términos**
   (qué ficha y qué versión de términos usa, con acceso para editarlos), **Hábitos y contenido** (cuántos activos y acceso a su gestión) y **Por atender** (grupos sin mentor,
   personas sin grupo, solicitudes pendientes).
3. **Given** cualquier cifra de la formación, **When** la toca, **Then** abre la lista que la respalda, ya filtrada por esa formación.
4. **Given** las listas de Personas, Grupos, Semáforo, Ranking y Solicitudes de Administración, **When** las abre, **Then** cada una tiene un filtro «Formación»
   (Todas · Escuela · Diplomado · Certificación), en «Todas» por defecto, y cada fila dice a cuál pertenece.
5. **Given** un Alquimista, **When** abre «Formaciones», **Then** tiene las mismas capacidades que el Administrador.
6. **Given** un mentor, un líder o un aprendiz, **When** busca «Formaciones» en Administración, **Then** no la ve (esa pantalla es solo de Administración y Alquimista).

---

### User Story 5 - Cada formación tiene su duración y los días se cargan según ella (Priority: P2)

Administración define cuánto dura cada formación (en la Escuela y la Certificación, 90 días; en el Diplomado, 3 meses o 1 año). Lo que la persona ve como
«Día N de M», sus fases, sus semanas y sus meses sale de **la duración de su formación**, no de un 90 escrito de antemano.

**Why this priority**: Es el segundo pedido de fondo («cargar los días de acuerdo a la formación que lleva» y «establecer el tiempo de duración»).

**Independent Test**: Aceptar a una persona del Diplomado de 1 año y a otra de 3 meses y comprobar su último día, su cierre y los textos «Día N de M» en cada una.

**Acceptance Scenarios**:

1. **Given** un aprendiz del Diplomado de 1 año, **When** abre Hoy, **Then** ve «Día N de 365» y su programa termina al cumplirse el día 365 (misma regla de cierre que hoy
   en el día 90).
2. **Given** un aprendiz del Diplomado de 3 meses y otro de la Escuela, **When** abre Hoy, **Then** ambos ven «Día N de 90».
3. **Given** una formación con una sola opción de duración, **When** se acepta a alguien, **Then** se aplica esa duración sin preguntar (se muestra como dato, no como elección).
4. **Given** Administración que cambia las opciones de duración del Diplomado, **When** guarda, **Then** las personas que ya empezaron **no cambian**; las próximas aceptaciones usan la nueva.
5. **Given** una persona de 1 año, **When** consulta sus fases, semanas y meses, **Then** siguen su duración. *(Cómo se reparten las fases en 1 año: pregunta abierta Q2.)*
6. **Given** un aprendiz de cualquier duración, **When** el programa llega a su último día, **Then** todo lo que hoy ocurre al cerrar el día 90 (cierre, fase final, avisos) ocurre en su último día.

---

### User Story 6 - El personal trabaja en una o varias formaciones sin mezclar (Priority: P2)

Administración asigna a cada mentor, guía o líder a una o más formaciones. Con una sola, nada cambia en su uso. Con varias, al entrar elige en cuál trabaja hoy; esa
elección se recuerda; puede cambiarla desde Yo; y la formación activa se ve siempre.

**Why this priority**: Es la excepción pedida para el personal; sin ella el personal con dos formaciones no puede trabajar, pero es menos frecuente que el caso base.

**Independent Test**: Asignar una mentora a dos formaciones; comprobar la elección al entrar, que se recuerda, que el cambio vacía toda vista de la anterior y que nunca hay listas mixtas.

**Acceptance Scenarios**:

1. **Given** una mentora asignada a Escuela y Diplomado, **When** inicia sesión por primera vez, **Then** ve una pantalla corta «¿En qué formación vas a trabajar hoy?» con una tarjeta por
   formación (nombre y cuántos aprendices y pendientes tiene allí).
2. **Given** que eligió el Diplomado, **When** vuelve a entrar otro día, **Then** entra directo al Diplomado (recuerda la última) y ve el nombre «Diplomado Renaser» en un lugar fijo y discreto.
3. **Given** la mentora trabajando en el Diplomado, **When** abre Hoy, Comunidad, Training o «Mi grupo», **Then** no aparece nada de la Escuela.
4. **Given** la mentora, **When** toca «Cambiar de formación» en Yo, **Then** la app recarga todas sus vistas con la otra formación y no deja a la vista nada de la anterior (ni por un instante).
5. **Given** una mentora que lidera un grupo en el Diplomado, **When** Administración intenta quitarle el Diplomado, **Then** el sistema no lo permite hasta reasignar el grupo y dice cuál grupo.
6. **Given** un integrante del personal sin ninguna formación asignada, **When** inicia sesión, **Then** ve un mensaje claro («Administración todavía no te asignó una formación») y nada más.
7. **Given** Administración, **When** abre la ficha de un integrante del personal, **Then** ve y puede cambiar sus formaciones (marcando una o más).

---

### User Story 7 - Pasar a una persona de una formación a otra (Priority: P3)

Cuando un aprendiz termina su formación, Administración puede pasarlo a otra (por ejemplo, de la Escuela al Diplomado). La persona conserva su historia, empieza limpia en la nueva
formación y deja de ver la anterior.

**Why this priority**: Pedido explícito del dueño, pero ocurre solo cuando alguien termina; el sistema funciona sin él.

**Independent Test**: Terminar el programa de un aprendiz de prueba, pasarlo al Diplomado y comprobar que la historia se conserva y que ya no ve ni es visto por la Escuela.

**Acceptance Scenarios**:

1. **Given** un aprendiz que terminó la Escuela, **When** Administración lo pasa al Diplomado de 1 año, **Then** queda en el Diplomado, elige su nuevo Día 1, sale de los grupos de la Escuela, y la Escuela
   deja de verlo y de contarlo (ranking, semáforo, listas).
2. **Given** ese aprendiz, **When** abre su historial, **Then** ve la formación anterior (cuál, fechas, resultado) y sus registros conservados, sin mezclarse con la nueva.
3. **Given** un aprendiz que **todavía no terminó** su formación, **When** Administración intenta pasarlo, **Then** el sistema se comporta según la respuesta a la pregunta Q1.
4. **Given** una persona que todavía no empezó (sin Día 1 elegido), **When** Administración corrige su formación o duración, **Then** se cambia sin dejar historia (nunca empezó nada).

---

### Edge Cases

- **Cuenta sin formación**: una cuenta aceptada nunca debe quedar sin formación (aprendiz) ni, salvo Administración, Alquimista y Soporte TI, sin al menos una (personal). Si por un error de datos ocurre, la persona ve un mensaje claro y Administración la ve marcada «Sin formación» en «Por atender».
- **Aceptación desde una app anterior** (que no sabe elegir formación): la persona entra a la Escuela con su duración única, como hoy (el detalle está en la spec 025).
- **Formación activa que desaparece mientras el personal trabaja** (Administración se la quita con la sesión abierta): al siguiente movimiento de la app, la persona pasa a otra formación suya o, si no tiene otra, ve el mensaje de «sin formación»; nunca sigue viendo datos de la que perdió.
- **Persona cuyo traslado ocurre con la sesión abierta**: al volver la app al primer plano, o a más tardar al siguiente pedido, se rehace entera con la nueva formación.
- **Duración editada con gente adentro**: quitar una opción de duración que alguien ya está cursando no la saca a esa persona; la opción deja de ofrecerse a quienes se acepten después.
- **Diplomado con dos duraciones en la misma formación**: una persona de 3 meses y otra de 1 año **comparten** la formación (grupos, Muro, ranking): el aislamiento es entre formaciones, no entre duraciones. *(Supuesto; ver dudas.)*
- **Aviso o enlace viejo que apunta a otra formación**: abre la app sin mostrar el contenido ajeno y sin decir que existe; la persona queda en su pantalla de siempre.
- **Búsquedas, menciones y sugerencias**: solo ofrecen personas y contenido de la formación activa.
- **Cuentas de prueba («ver como» de Soporte TI)**: nacen en la Escuela; ver como otro rol respeta el aislamiento de la formación de esa cuenta.
- **Administración filtrando «Todas»**: es la única vista mixta permitida, y solo para Administración y Alquimista; cada fila dice su formación.
- **Persona suspendida o con cuenta eliminada**: sigue perteneciendo a su formación para fines de historial y conteos; no aparece en listas activas, igual que hoy.
- **Alta de una cuarta formación en el futuro**: no está en el alcance (ver «Fuera de alcance»); el sistema no debe obligar a tocar las tres existentes si algún día se agrega.
- **Reloj y zona horaria**: el día de cada persona sigue siendo el de **su** zona; la duración se cuenta desde **su** Día 1.
- **Una persona del personal que además hace su propio programa de 90 días** (hoy es opcional): ese programa pertenece a una de sus formaciones (ver dudas).

## Requirements *(mandatory)*

### Functional Requirements

**Las formaciones**

- **FR-001**: El sistema MUST ofrecer exactamente tres formaciones: **Escuela Renaser**, **Diplomado Renaser** y **Certificación Renaser**, cada una con nombre, descripción corta y estado (activa o no).
- **FR-002**: Cada formación MUST tener una o más **opciones de duración** configurables por Administración. Valores iniciales: Escuela 90 días; Certificación 90 días; Diplomado 3 meses y 1 año.
- **FR-003**: Administración y Alquimista MUST poder editar el nombre, la descripción y las opciones de duración de cada formación.
- **FR-004**: Un cambio de duración u opciones MUST regir solo para las personas que se acepten después; quienes ya empezaron conservan la suya.
- **FR-005**: Todo lo que existe antes de publicar esta función (personas, grupos, cohortes, publicaciones, comentarios, testimonios, cursos, eventos, conversaciones, hábitos, ranking) MUST quedar en la Escuela Renaser, sin cambios visibles.

**Pertenencia**

- **FR-006**: Cada aprendiz MUST pertenecer a **una sola** formación a la vez y tener **una** duración.
- **FR-007**: Cada integrante del personal con rol de mentor, guía de aprendiz o líder de mentores MUST tener asignada **al menos una** formación para poder trabajar, y MAY tener varias.
- **FR-008**: Administrador y Alquimista MUST ver y gestionar las tres formaciones sin tener asignación. *(Soporte TI: ver supuestos.)*
- **FR-009**: Administración MUST poder ver, desde la ficha de cada persona, a qué formación pertenece (y su duración, si es aprendiz) y cambiar las formaciones del personal.
- **FR-010**: Un grupo MUST pertenecer a **una** formación; su mentor y todos sus integrantes MUST ser de esa formación.
- **FR-011**: El sistema MUST impedir quitarle una formación a un integrante del personal mientras lidere allí un grupo o tenga allí aprendices asignados, indicando qué falta reasignar.

**Sesión y vistas**

- **FR-012**: Al iniciar sesión, el sistema MUST informar a la app la formación de la persona (o sus formaciones y la última elegida), sin que la persona tenga que elegirla si solo tiene una.
- **FR-013**: Quien trabaja en varias formaciones MUST elegir una como «formación activa» al entrar (se recuerda la última), poder cambiarla desde Yo, y ver siempre cuál está activa.
- **FR-014**: Toda pantalla de la app MUST mostrar únicamente contenido de la formación de la persona (o de la activa), con el nombre de la formación visible en un lugar fijo y discreto cuando la persona tiene más de una o es de una formación distinta de la Escuela.
- **FR-015**: La estructura de la app (las cinco pestañas y sus secciones) MUST ser la misma en las tres formaciones; lo que cambia es el contenido, los nombres y el calendario. *(Confirmación pendiente: Q3.)*
- **FR-016**: Los textos «Día N de M», fases, semanas y meses del programa MUST salir de la duración de la persona, no de un valor fijo de 90 días.
- **FR-017**: El último día del programa (cierre, fase final, avisos, graduación) MUST ocurrir en el último día de la duración de cada persona. *(Reparto de fases en 1 año: Q2.)*
- **FR-018**: Una app publicada antes de esta función MUST seguir funcionando sin cambios para las personas de la Escuela; una app nueva MUST tolerar un servidor que todavía no informa la formación, tratándola como Escuela.

**Aislamiento total**

- **FR-019**: Ninguna lectura ni acción MUST cruzar formaciones: personas, grupos, chats grupales, global y 1 a 1, publicaciones, comentarios, reacciones, testimonios, cursos, eventos, hábitos, puntos y ranking, semáforo, consultas de mentoría y de soporte de aprendices, reportes, avisos y búsquedas.
- **FR-020**: Pedir por identificador, enlace o aviso algo de otra formación MUST responder como si no existiera, sin revelar nada.
- **FR-021**: Quien tiene varias formaciones MUST ver solo la activa en cada momento; ninguna lista, cifra o aviso combina dos formaciones.
- **FR-022**: Los avisos y envíos «a todos» MUST limitarse a la formación desde la que se envían.
- **FR-023**: Una acción de Administración sobre una formación (editar duración, ficha, términos, hábitos…) MUST NOT alterar a otra.
- **FR-024**: La única vista que mezcla formaciones es la de Administración y Alquimista con el filtro «Todas»; cada fila debe indicar su formación.

**Administración**

- **FR-025**: Administración MUST tener una entrada «Formaciones» con las tres y, dentro de cada una, resumen, personas, grupos, ficha y términos vigentes, hábitos y contenido, y lo que hay por atender (ver Historia 4).
- **FR-026**: Las cifras de «Formaciones» MUST coincidir exactamente con las listas a las que llevan.
- **FR-027**: Las listas de Personas, Grupos, Semáforo, Ranking y Solicitudes de Administración MUST ofrecer el filtro «Formación».

**Pasar de formación**

- **FR-028**: Administración MUST poder pasar a un aprendiz a otra formación (con su duración), en las condiciones de la pregunta Q1.
- **FR-029**: Al pasar de formación, el sistema MUST conservar la historia de la formación anterior (cuál, fechas, resultado, registros) y MUST NOT mezclarla con la nueva; la persona elige un nuevo Día 1 y sale de los grupos de la formación anterior.
- **FR-030**: Corregir la formación o la duración de alguien que todavía no empezó (sin Día 1 elegido) MUST ser posible sin dejar historia.

**Calidad**

- **FR-031**: Toda decisión sobre formaciones MUST ser verificable por pruebas de aislamiento de punta a punta (E2E) en las seis combinaciones de pares de formaciones.

### Key Entities *(include if feature involves data)*

- **Formación**: una de las tres instituciones. Tiene nombre, descripción, estado y sus opciones de duración. Es el límite de aislamiento.
- **Duración de formación**: una opción de duración de una formación (por ejemplo «90 días», «3 meses», «1 año»), expresada en días para el cálculo del calendario.
- **Pertenencia del aprendiz**: la formación y la duración con las que una persona hace su programa; una a la vez.
- **Asignación del personal**: la relación entre un integrante del personal y las formaciones en las que trabaja (una o más); incluye la última elegida.
- **Participación anterior (historial)**: lo que quedó de una formación que la persona ya cursó (cuál, desde cuándo, hasta cuándo, cómo terminó).
- **Grupo / célula**: pertenece a una formación; su mentor e integrantes son de ella.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En las **seis** combinaciones de pares de formaciones (A ve a B), una persona de A ve **0** publicaciones, hábitos, personas, eventos, cursos, chats y posiciones de ranking de B, comprobado por una prueba de punta a punta en cada área del FR-019.
  Ejemplo: «un aprendiz de la Escuela no ve ninguna publicación, hábito, persona ni ranking del Diplomado».
- **SC-002**: El **100 %** de las personas que existen antes de publicar conservan su día de programa, puntos, rachas, grupo y posición de ranking después de publicar (comparación antes/después, 0 diferencias).
- **SC-003**: Un aprendiz llega a Hoy con **los mismos pasos que hoy** (0 pantallas extra); el personal con varias formaciones llega a su trabajo con **1** elección.
- **SC-004**: En un Diplomado de 1 año, la persona ve «Día N de 365» desde su primer día y el programa cierra en su día 365; en 90 días, en el 90. Comprobado en el primer día, el penúltimo y el último.
- **SC-005**: Administración encuentra cualquier dato clave de una formación en **2 toques o menos** desde la raíz de Administración, y las cifras de «Formaciones» coinciden con sus listas en el **100 %** de los casos.
- **SC-006**: Cambiar la duración de una formación modifica a **0** personas que ya empezaron.
- **SC-007**: La suite de regresión de la app publicada (flujos de Maestro de Escuela) pasa **sin cambios** contra el servidor nuevo.
- **SC-008**: Un aviso «a todos» enviado desde una formación llega a **0** personas de otra.

## Fuera de alcance

- **Qué hay dentro de cada formación** (qué hábitos, cursos, eventos, planificación): lo definen las specs de contenido; esta spec garantiza solo el aislamiento y que lo existente quede en la Escuela.
- **Aceptar solicitudes con formación, rol y ficha**: spec 025. **Ficha, diagnóstico y retiro del Mapa**: spec 026. **Términos y condiciones editables**: spec 027.
- **Crear o borrar formaciones desde la app**: son tres, fijas. Se edita nombre, descripción y duraciones.
- **Pagos, facturación, certificados o diplomas**: no se piden.
- **Cambios de diseño** de las cinco pestañas más allá de mostrar el nombre de la formación y el selector del personal.

## Restricciones del dueño

Reglas del dueño del 2026-10-31 para todas las specs de esta tanda:

1. **Reutilizar lo que ya existe**: tablas, módulos, pantallas y flujos de hoy. Nada de tablas nuevas si una existente sirve con una columna o una relación. Datos normalizados y,
   si algo se desnormaliza, que esté justificado. (Aquí: las cohortes, los grupos, la participación en el programa, los roles y las listas de Administración se reutilizan; lo único realmente
   nuevo es el concepto de formación y su relación con el personal.)
2. **No romper la lógica actual**: todo lo de hoy sigue funcionando como Escuela Renaser (SC-002, SC-007).
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID; módulos que solo importan la `api/` de otros) **y las reglas del `AGENTS.md` del frontend** (estructura por feature, componentes
   compartidos, un solo scroll, áreas táctiles de 48 px, tipo oración, tuteo de Perú, gesto atrás en toda vista hija). Tocar las cinco pestañas exige dejar anotada la autorización en el `AGENTS.md`
   del frontend con fecha, alcance y «lo que cambia y nada más»; este pedido del dueño es esa autorización.
4. **Aislamiento total entre formaciones**: **nunca** se cruza información. Criterio de éxito verificable por E2E: SC-001 y SC-008.
5. **El dueño hace él mismo todas las preguntas antes de implementar**: por eso la sección «Dudas para el dueño» lista todas, aunque ya tengan un supuesto razonable.

## Assumptions

- **3 meses = 90 días y 1 año = 365 días** para el cálculo del calendario (el dueño equipara «3 meses o 90 días»). Se confirma en las dudas.
- **Soporte TI** (rol interno) ve las tres formaciones, igual que Administración, porque sus interruptores y su consola son globales; no hace programa. No aparece en el selector.
- **Los roles personalizados** (hoy se heredan de un rol base) se comportan como su rol base respecto de las formaciones.
- **La app pública de alta (solicitud) no cambia**: la persona no elige su formación al pedir acceso; la elige Administración al aceptar (spec 025).
- **«Programa»** se mantiene en los textos donde hoy ya aparece; la app muestra el nombre de la formación donde corresponda (cabecera de Hoy, selector del personal, fichas de Administración).
- **Una persona de 3 meses y otra de 1 año del Diplomado comparten grupos, Muro y ranking** (el límite es la formación).
- **Al pasar de formación los puntos empiezan en cero** en la nueva; los de la anterior se conservan en su historial y su ranking histórico.
- **El personal que hace su propio programa** lo hace en una de sus formaciones, la que elija al escoger su Día 1.
- **Las cohortes y los grupos existentes** pasan a la Escuela tal cual; cada formación nueva tiene su propio grupo de recepción, que se crea al configurarla (como hoy, sin grupo de recepción nadie entra a ningún grupo).
- **La formación activa del personal es una preferencia de uso**, no un permiso: el servidor decide qué ve cada persona según sus asignaciones, no según lo que diga la app.
- **Dependencias**: la spec 025 (aceptar con formación) y la 026 (ficha) dependen de que existan las formaciones; las specs de hábitos y planificación por formación dependen del aislamiento definido aquí.

## Dudas para el dueño

Todas las dudas reales, aunque ya tengan supuesto. Las marcadas **(cambia el alcance)** requieren respuesta antes de planificar.

| # | Duda | Opciones (la recomendada primero) | ¿Cambia el alcance? |
|---|---|---|---|
| Q1 | [NEEDS CLARIFICATION: ¿se puede pasar a un aprendiz de formación **antes** de que termine la suya, o solo cuando termina?] | A) Solo cuando terminó; antes, Administración debe dar por terminada su participación con un motivo escrito (queda en el historial). B) Solo cuando terminó, sin excepciones. C) En cualquier momento. | **Sí** |
| Q2 | [NEEDS CLARIFICATION: ¿cómo se reparten las 4 fases del programa (1–7, 8–34, 35–64, 65–90) en un Diplomado de 1 año?] | A) Mismas cuatro fases estiradas en proporción (automático, sin inventar contenido). B) Administración define los cortes de cada duración. C) Una sola etapa para el Diplomado de 1 año. | **Sí** |
| Q3 | [NEEDS CLARIFICATION: «mostrar las vistas de acuerdo a la formación»: ¿la forma de la app (cinco pestañas y secciones) es igual en las tres y solo cambia el contenido, o cada formación tiene pantallas distintas?] | A) Misma forma, contenido propio por formación. B) Pantallas distintas por formación (requiere definir cuáles, una spec por formación). | **Sí** |
| Q4 | ¿3 meses son 90 días y 1 año son 365? | A) Sí (365). B) 1 año = 360. C) Otros valores. | No (cambia números) |
| Q5 | ¿Soporte TI ve las tres formaciones? | A) Sí, como Administración. B) Solo las que se le asignen. | No |
| Q6 | ¿Se necesitará crear formaciones nuevas desde la app más adelante? | A) No: tres fijas (recomendada para esta versión). B) Sí: administrar formaciones. | No (afecta el diseño futuro) |
| Q7 | En el Diplomado, ¿el grupo, el Muro y el ranking mezclan a los de 3 meses con los de 1 año? | A) Sí, comparten (puntos por día comparables). B) Separar por duración. | No, salvo B |
| Q8 | Al pasar a una persona de formación, ¿sus puntos empiezan en cero en la nueva? | A) Sí; los anteriores quedan en su historial. B) Se arrastran. | No |
| Q9 | Si el personal hace su propio programa de 90 días, ¿en cuál de sus formaciones lo hace? | A) En la que elija al escoger su Día 1. B) Solo en la Escuela. | No |
| Q10 | Nombre oficial del Diplomado en la app: ¿«Diplomado Renaser» o «Diplomado Riqueza con Propósito: Maestría de Vida» (el título que usa el formulario de diagnóstico)? | A) «Diplomado Renaser» con la descripción larga editable. B) El nombre largo. | No |
| Q11 | Si Administración cambia las formaciones de un integrante del personal con la sesión abierta, ¿se aplica al instante o en su siguiente apertura? | A) Al instante (en su siguiente pedido). B) Al volver a abrir la app. | No |
