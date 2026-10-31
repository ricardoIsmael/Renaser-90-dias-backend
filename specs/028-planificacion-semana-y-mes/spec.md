# Feature Specification: Planificar la semana y el mes — repetir lo planificado

**Feature Branch**: `028-planificacion-semana-y-mes` (backend y app)
**Created**: 2026-10-31
**Status**: Borrador, a la espera de las respuestas del dueño (3 preguntas marcadas; ver «Preguntas abiertas»). No se implementa hasta resolverlas.
**Input**: Pedido del dueño (2026-10-31), textual: «El sistema, en planificación de hábitos, podrá hacer lo siguiente: si una
persona planifica una semana, ofrecerle la misma planificación la siguiente semana; y, a la par, que pueda hacer la
planificación de cada mes, si lo desea.»

## Clarifications

### Session 2026-10-31 (pedido y reglas del dueño)

- **Qué pide**: (1) repetir la semana: si la persona planificó una semana, el sistema le ofrece la misma planificación para la
  siguiente; (2) planificar el mes: además, y si lo desea, poder planificar cada mes.
- **Reutilizar la planificación de hábitos**: «lo único bueno que ya está es la planificación de hábitos». Esta spec **no inventa
  otra planificación**: se apoya en el editor y en las reglas de las specs 008 a 011 y suma encima la idea de «semana» y de «mes».
- **Reglas generales del dueño para todas las specs de este lote**: respetar las reglas y no romper la lógica, reutilizar, no crear
  tablas innecesarias, normalizar siempre (o la desnormalización correcta), buenas prácticas y SOLID, en backend y frontend. Ver
  «Restricciones del dueño».
- **Todo queda dentro de la formación de la persona** (Escuela 90 días, Diplomado o Certificación; spec 022). Esta spec no define las
  formaciones: depende de la 022 (formaciones) y de la 023 (hábitos por formación).
- **El dueño hará todas las preguntas antes de implementar.** Por eso esta spec deja sus dudas a la vista y no las resuelve sola.

### Lo que hay hoy (verificado en el código y en `AGENTS.md` el 2026-10-31)

| Pieza | Hoy |
|---|---|
| Dónde se planifica | Training → engranaje → «Tus prácticas» → una dimensión → hoja «Planificar <dimensión>» → editor de **un** hábito: hora, días, recordatorio y «Cómo suena» (specs 008 a 011). |
| Qué alcance tiene lo que se guarda | **Todas las semanas**. El editor lo dice así: «Solo sáb, dom, todas las semanas. Hoy no cambia.». Existen cambios para una sola fecha (por ejemplo apagar un día), pero la persona no los usa para planificar una semana y no se repiten. |
| Qué días se pueden cambiar | De mañana en adelante. Hoy y los días pasados van con candado (D-91). El domingo la hoja muestra la semana siguiente entera. No hay tope de cambios (D-170). |
| Vista de la semana completa | **No existe.** Cada hábito se edita uno por uno; no hay un lugar donde ver todos los hábitos de una semana ni copiarlos a otra. |
| Quién lee lo planificado | Training (días que vienen y lista por hora), las alarmas del teléfono (specs 007 a 010), los avisos del servidor, Mi Radar (el Despertar y el Dormir de cada día, spec 019), el tiempo libre de las fichas (spec 015) y «¿Programó?» (spec 018). |
| Acciones de los objetivos | Se planifican en **Plan** («Organizar mi semana», «Agendar»), con su propia ventana (domingo 12:00 a lunes 09:00). No son parte de esta spec salvo que el dueño diga lo contrario (pregunta 3). |

**Lo que esto significa**: hoy lo planificado ya «se repite» porque vale para todas las semanas. Lo que **no existe** es poder
planificar **una semana concreta** distinta de lo normal (un viaje, unos exámenes, una semana de descanso), ni **repetir** esa semana
o **arreglar varias semanas de un mes** de una vez. Eso es lo que esta spec agrega.

### Palabras que usa esta spec

- **Plan de siempre**: lo que el editor guarda hoy para todas las semanas.
- **Plan de una semana**: lo que rige en los siete días (lunes a domingo) de una semana concreta: el plan de siempre más los cambios
  que la persona hizo **solo para esa semana**.
- **Repetir una semana**: dar a otra semana el mismo plan de una semana.
- **Planificar el mes**: ver las semanas de un mes y arreglarlas juntas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Me ofrecen repetir mi semana en la siguiente (Priority: P1)

Como participante que planificó una semana distinta de lo normal, quiero que el sistema me ofrezca dejar la semana siguiente igual,
para no volver a abrir hábito por hábito.

**Why this priority**: es el primer punto del pedido del dueño y el que ahorra trabajo real todos los domingos.

**Independent Test**: con el sábado de esta semana cambiado a las 09:00 **solo esta semana**, abrir Planificar. Aparece la oferta de
repetirlo en la semana siguiente. Aceptar. La semana siguiente tiene el sábado a las 09:00; la tercera semana sigue con el plan de
siempre. Todo se ve y se edita en el editor de siempre.

**Acceptance Scenarios**:

1. **Given** una semana con cambios míos solo para esa semana y la siguiente sin cambios propios, **When** abro Planificar, **Then**
   veo una oferta que dice qué semana se repite y en cuál («Tu semana del 26 oct al 1 nov, en la del 2 al 8 nov») con «Repetir» y
   «Ahora no».
2. **Given** la oferta, **When** toco «Ver qué se repite», **Then** leo, hábito por hábito, qué días y qué cambia (hora, aviso o «no
   va»), y qué hábitos se dejan afuera y por qué.
3. **Given** que acepto, **When** termina, **Then** la semana siguiente queda con el mismo plan, se confirma en línea («Tu semana del 2 al
   8 nov quedó como esta») y puedo «Deshacer».
4. **Given** que la semana siguiente ya tiene cambios propios, **When** veo la oferta, **Then** me dice que reemplazaría lo que ya hice y
   pide confirmarlo de forma explícita; nunca pisa en silencio.
5. **Given** que no hice cambios solo para esta semana (todo es el plan de siempre), **When** abro Planificar, **Then** no aparece
   oferta: no hay nada que repetir.
6. **Given** que toco «Ahora no», **When** vuelvo a abrir Planificar, **Then** la oferta de ese par de semanas no reaparece, salvo que yo
   cambie después la semana modelo.

---

### User Story 2 - Planifico solo una semana, sin tocar las demás (Priority: P1)

Como participante, quiero poder decir que un cambio vale **solo para esta semana**, para no alterar mi plan de siempre.

**Why this priority**: sin esto «repetir la semana» no tiene qué repetir. Es la pieza que hace posible la US1 y el mes.

**Independent Test**: en el editor de un hábito, elegir «Solo esta semana», mover el sábado a las 09:00 y guardar. Esta semana el sábado
va a las 09:00; la semana siguiente y todas las demás siguen a la hora de siempre.

**Acceptance Scenarios**:

1. **Given** el editor de un hábito, **When** no toco nada de alcance, **Then** guarda para **todas las semanas**, exactamente como hoy.
2. **Given** que elijo «Solo esta semana» y cambio hora, días, aviso o «Cómo suena», **When** guardo, **Then** el cambio rige solo en los
   días que faltan de esta semana y el plan de siempre no cambia.
3. **Given** una semana con cambios solo para ella, **When** abro Planificar, **Then** se nota cuál es (por ejemplo «Cambiada solo esta
   semana») y tengo «Volver al plan de siempre» para ella.
4. **Given** hoy o un día pasado, **When** intento cambiarlo, **Then** sigue con candado, como hoy.
5. **Given** un hábito en el que no cambié nada solo para la semana, **When** repito la semana, **Then** ese hábito sigue con su plan de
   siempre en la semana destino.

---

### User Story 3 - Planifico el mes, si lo deseo (Priority: P2)

Como participante, quiero ver las semanas de un mes y arreglarlas de una vez (copiar una semana a las que faltan, o dejar una semana
distinta), pero solo si quiero.

**Why this priority**: es el segundo punto del pedido y es opcional por definición («si lo desea»). Depende de la US2.

**Independent Test**: el día 12, abrir «Planificar el mes». Se ven las semanas desde la actual hasta fin de mes. Elegir la semana modelo,
«Aplicar a las semanas que faltan», confirmar el resumen. Cada semana queda con el plan de la modelo; una de ellas se deja como siempre.

**Acceptance Scenarios**:

1. **Given** Planificar, **When** toco «Planificar el mes», **Then** veo las semanas del mes que todavía tienen días por planificar, cada una con
   su estado: «Como siempre», «Cambiada solo esa semana» o «Repetida de la semana del …».
2. **Given** la lista de semanas, **When** elijo una como modelo y toco «Aplicar a las semanas que faltan», **Then** veo un resumen (qué se
   copia, a qué semanas, qué se reemplaza) y confirmo antes de que se guarde.
3. **Given** una semana de la lista, **When** la toco, **Then** puedo copiar otra semana en ella, dejarla como siempre o editarla con el
   editor de siempre (con «Solo esa semana» ya elegido).
4. **Given** que salgo sin aplicar, **Then** no cambia nada.
5. **Given** el mes que viene, **When** lo pido, **Then** veo sus semanas con el mismo comportamiento.
6. **Given** una formación que termina antes de acabar el mes, **Then** las semanas posteriores al último día de la formación no aparecen.
7. **Given** que nunca uso «Planificar el mes», **Then** nada me lo exige ni me lo recuerda con insistencia.

---

### User Story 4 - Veo qué quedó planificado y lo deshago (Priority: P2)

Como participante, quiero ver de un vistazo cómo quedó una semana y poder volver al plan de siempre si me arrepiento.

**Why this priority**: da confianza para usar la US1 y la US3; sin una salida clara, la gente no se atreve a repetir.

**Independent Test**: repetir una semana, tocar «Deshacer» en la confirmación: la semana vuelve al plan de siempre y las alarmas también.
Más tarde, desde «Planificar el mes», «Volver al plan de siempre» en esa semana hace lo mismo.

**Acceptance Scenarios**:

1. **Given** una semana, **When** la miro desde Planificar el mes o desde la oferta, **Then** leo un resumen corto («17 hábitos, 3 con
   cambios solo esta semana») y puedo abrir el detalle por hábito.
2. **Given** una semana repetida, **When** toco «Deshacer» o «Volver al plan de siempre», **Then** queda igual al plan de siempre y las
   alarmas y avisos se ajustan.
3. **Given** que deshago, **Then** no se pierde lo que hice en otras semanas ni lo del plan de siempre.

---

### Edge Cases

- **Hoy y los días pasados** no se cambian nunca. Si la semana modelo incluye días pasados, sirve de modelo pero no se toca. Si la semana
  destino es la actual, solo cambian los días que faltan.
- **Un hábito ya no existe en la formación de la persona, está desactivado o la persona dejó de tenerlo**: se deja afuera y la oferta lo
  dice por su nombre; no falla el resto.
- **Hábitos pausados**: la pausa («Solo hoy» o «Hasta que yo lo reactive») **no se repite**; es otra decisión de la persona.
- **Hábito creado después de la semana modelo**: en la semana destino sigue con el plan de siempre.
- **Cambio de hora ya programado («rige desde mañana», D-91)**: sigue su curso; repetir no lo cancela ni lo anticipa.
- **Cambio de formación**: lo planificado en la formación anterior no se lleva a la nueva.
- **Sin elegir el Día 1 / Día 0**: no se ofrece repetir ni planificar el mes (no hay programa en marcha).
- **Últimos días de la formación**: no se ofrecen semanas ni meses que queden fuera de ella.
- **Semana que cruza dos meses** (por ejemplo del 26 oct al 1 nov): se muestra una sola vez, con sus fechas completas.
- **Sin red al aceptar**: la semana queda como estaba y se dice con «Reintentar»; nunca a medias.
- **Doble toque o reintento**: se aplica una sola vez.
- **Dos teléfonos de la misma persona**: lo repetido vale en ambos; la oferta desaparece en los dos cuando se acepta.
- **Cuenta suspendida**: no se ofrece ni se acepta nada.
- **APK anterior contra el servidor nuevo**: sigue guardando «todas las semanas» y no ve la oferta. Lo planificado solo para una semana
  no lo conocen sus alarmas locales hasta actualizar la app; los avisos del servidor sí lo respetan (límite aceptado, ver Supuestos).
- **Web**: mismo plan y misma oferta, sin alarmas del teléfono.
- **Todo comportamiento diario se prueba con el reloj en la madrugada UTC** (día local anterior en Lima).

## Requirements *(mandatory)*

### Functional Requirements

**Planificar solo una semana**

- **FR-001**: El editor de un hábito MUST ofrecer dos alcances para un cambio: «Todas las semanas» (el que ya existe y el valor por
  defecto) y «Solo esta semana». [NEEDS CLARIFICATION: ¿Es así como quieres que se planifique una semana concreta? Hoy todo vale «todas las
  semanas». Opciones: A (recomendada) dejar «todas las semanas» como base y sumar «Solo esta semana» como alcance opcional; B pasar a que cada semana tenga siempre su propio plan y que la
  siguiente se copie de la anterior; C no agregar alcance nuevo y limitarse a confirmar que el plan de siempre sigue la semana siguiente.]
- **FR-002**: Con «Todas las semanas», lo que se guarda, las alarmas, los avisos y las reglas MUST ser exactamente los de las specs 008 a 011.
- **FR-003**: Un cambio «Solo esta semana» MUST regir únicamente en los días de esa semana que todavía se pueden cambiar y MUST NOT alterar el
  plan de siempre ni otras semanas.
- **FR-004**: Hoy y los días pasados MUST seguir con candado, para la hora, los días, el aviso y «Cómo suena».
- **FR-005**: Una semana con cambios propios MUST poder reconocerse y MUST poder volver al plan de siempre en un solo paso.

**Repetir la semana**

- **FR-006**: El sistema MUST ofrecer repetir una semana en la siguiente cuando se cumplan las tres cosas: la semana modelo tiene cambios
  propios, la siguiente no los tiene, y repetir la dejaría distinta de como está. En cualquier otro caso MUST NOT ofrecerlo.
- **FR-007**: La oferta MUST mostrar antes de confirmar un resumen de qué se repite (hábitos, días y qué cambia) y qué se deja afuera con su
  motivo. Si la semana destino ya tiene cambios propios, MUST pedir una confirmación explícita antes de reemplazarlos.
- **FR-008**: Aceptar MUST dejar la semana destino con el mismo plan que la semana modelo, de modo que sea **indistinguible de haberlo planificado
  a mano**: se ve y se edita en el mismo editor, y lo leen igual Training, las alarmas y avisos, Mi Radar y el tiempo libre.
- **FR-009**: Repetir MUST NOT cambiar hoy ni los días pasados, lo ya registrado, los puntos, la racha ni el semáforo, y MUST NOT gastar ningún cupo.
- **FR-010**: Repetir MUST ser todo o nada, y MUST aplicarse una sola vez aunque se toque dos veces o se reintente.
- **FR-011**: Las pausas MUST NOT repetirse. Un hábito que ya no corresponde a la formación o está desactivado MUST quedar afuera con aviso.
- **FR-012**: «Ahora no» MUST evitar que la misma oferta (mismo par de semanas) vuelva a aparecer, salvo que la semana modelo cambie.
- **FR-013**: Después de repetir, la persona MUST poder deshacerlo en el momento («Deshacer») y más tarde («Volver al plan de siempre»), y ambos
  MUST dejar la semana igual al plan de siempre.

**Planificar el mes**

- **FR-014**: El editor de Planificar MUST tener una entrada «Planificar el mes» que muestre las semanas del mes con días por planificar y el
  estado de cada una. [NEEDS CLARIFICATION: ¿«mes» es el mes de calendario (de noviembre, de diciembre…) o el mes del programa (bloques de cuatro
  semanas desde el Día 1, como en Plan)? Opciones: A (recomendada) mes de calendario, porque sirve igual en la Escuela, el Diplomado y la
  Certificación; B mes del programa, que coincide con el «Mes 1, Mes 2» de Plan; C que la persona elija cuál ver.]
- **FR-015**: Desde esa vista la persona MUST poder, para cada semana: copiar otra semana en ella, dejarla como siempre o editarla. Y MUST poder
  aplicar una semana modelo a todas las que faltan del mes, con resumen y confirmación.
- **FR-016**: Planificar el mes MUST ser opcional: nada lo exige, no bloquea el uso de la app y no genera avisos nuevos más allá del recordatorio
  de los domingos que la persona ya tenga encendido.
- **FR-017**: Las semanas que queden fuera de los días de la formación MUST NOT ofrecerse.

**Alcance, formación y compatibilidad**

- **FR-018**: Todo MUST ocurrir dentro de la formación de la persona: solo sus hábitos, solo su plan. Ninguna lectura ni escritura MUST alcanzar el
  plan de otra persona ni de hábitos de otra formación.
- **FR-019**: Las alarmas del teléfono y los avisos del servidor de una semana repetida MUST ser los que saldrían de planificarla a mano (specs 007 a 010).
- **FR-020**: Los textos MUST ir en español de Perú, con tuteo y en tipo oración, con áreas de toque de 48 px, un solo scroll por pantalla, claro y oscuro, y
  el atrás del sistema subiendo un nivel por vez. Sin animaciones nuevas más allá de las hojas y confirmaciones en línea que la app ya usa.
- **FR-021**: Una app nueva contra un servidor anterior MUST comportarse como hoy (sin oferta ni «Solo esta semana»). Una app anterior contra el
  servidor nuevo MUST seguir funcionando como hoy.
- **FR-022**: Todo MUST documentarse en el mismo cambio (decisión D-nn y, si hay errores, E-nn) y la autorización del dueño sobre Training MUST anotarse en `AGENTS.md`.
- **FR-023**: Lo que se repite y se agrupa por semana y por mes MUST ser la planificación de los **hábitos**. [NEEDS CLARIFICATION: ¿incluye también las acciones de los
  objetivos que se agendan en Plan? Opciones: A (recomendada) solo hábitos, como dice el pedido; B hábitos y acciones; C primero hábitos y las acciones en una spec aparte.]

### Key Entities

- **Plan de siempre de un hábito**: la hora, los días, el recordatorio y «Cómo suena» que rigen todas las semanas. Ya existe.
- **Plan de una semana**: los cambios de un hábito para los días de una semana concreta. Pertenece a una persona, a un hábito y a una semana de su formación.
- **Oferta de repetición**: la propuesta de dar a una semana el plan de otra; tiene estado (ofrecida, aceptada o descartada) para un par de semanas.
- **Semana de la formación**: lunes a domingo, contada en la zona de la persona y limitada por los días de su formación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde abrir Planificar, repetir una semana toma como máximo 2 toques y menos de 10 segundos.
- **SC-002**: Arreglar las semanas que faltan de un mes de 4 semanas con una semana modelo toma menos de 2 minutos.
- **SC-003**: Una semana repetida y la misma semana planificada a mano dan exactamente lo mismo en hora, días, aviso y «Cómo suena» para los 7 días de al
  menos 3 hábitos (comparación automática en E2E): 0 diferencias.
- **SC-004**: Después de repetir, hoy, los días pasados, lo registrado, los puntos, la racha y el semáforo no cambian (E2E): 0 cambios.
- **SC-005**: El 100 % de las alarmas del teléfono de la semana repetida coinciden con las de planificarla a mano (verificado en el emulador).
- **SC-006**: En una prueba con dos formaciones, repetir y planificar el mes en una deja 0 cambios en las personas y hábitos de la otra.
- **SC-007**: Con el alcance por defecto, las pruebas de las specs 008, 009, 010 y 011 siguen en verde sin modificarse.
- **SC-008**: En el 100 % de los casos de prueba sin cambios solo-semana, no aparece ninguna oferta.

## Restricciones del dueño

Reglas del dueño (2026-10-31) que valen para esta spec:

1. **Reutilizar lo que ya existe**: el editor de Planificar, las pantallas y flujos de Training → «Tus prácticas», las reglas de las specs 008 a 011
   (días, hora, recordatorio, «Cómo suena», candado de hoy y pasados), las alarmas (007 a 010) y las lecturas que ya consumen lo planificado. **Ninguna
   tabla nueva si una existente sirve**; si hace falta guardar algo más, se amplía lo que ya guarda cada cosa. Datos normalizados; si se desnormaliza, se
   justifica en el plan.
2. **No romper la lógica actual**: con el alcance por defecto, todo es como hoy; el rige-desde-mañana, el candado de hoy, la ausencia de tope, las
   pausas, las alarmas y los avisos no cambian. No se mueve el recordatorio de los domingos.
3. **Respetar la constitución del repo** (hexagonal, Spring Modulith, SOLID, pruebas, el tiempo es del participante) y las reglas del `AGENTS.md` del
   frontend (§2 a §6: un scroll, 48 px, tipo oración, tuteo, atrás del sistema). Training no se toca más allá de este pedido, que lo autoriza; se anota en `AGENTS.md`.
4. **Todo queda dentro de la formación de la persona.** Criterio verificable por E2E: con dos personas de formaciones distintas, repetir una semana o
   planificar un mes en una no cambia ni una hora, un día ni un aviso de la otra; un hábito que no pertenece a la formación de la persona no se puede
   planificar ni repetir (se rechaza sin afectar nada).
5. **El dueño hace las preguntas antes de implementar**: esta spec queda en borrador; se implementa solo con sus respuestas. Las dudas están en «Preguntas abiertas».

## Assumptions

- «Por semana» es de lunes a domingo, contada en la zona de la persona (la regla de siempre de la app).
- El plan de siempre sigue siendo la base y gana donde la semana no tiene cambios propios.
- La oferta aparece dentro de Planificar (no como pantalla ni aviso nuevos). El recordatorio de los domingos que ya existe («Arma tu semana los domingos») abre Planificar
  como hoy; no se agrega ninguna notificación. *(a confirmar)*
- La oferta se puede aceptar cualquier día, no solo el domingo, mientras queden días por planificar en la semana destino. *(a confirmar)*
- Una semana que cruza dos meses se muestra una sola vez, en el mes donde cae su lunes. *(a confirmar)*
- «Ahora no» se recuerda por cuenta; en otro teléfono puede volver a aparecer una vez. Es un límite aceptado.
- Lo que hoy se puede cambiar para una sola fecha no alcanza para todo lo que el editor guarda por día de la semana (varios avisos, «Cómo suena»). El plan
  debe resolver cómo cubrir ese hueco ampliando lo existente, sin tabla nueva y sin cambiar lo que ya se guarda. Es el riesgo técnico principal de esta spec.
- El Despertar y el Dormir cuentan como hábitos: repetirlos cambia las horas de Mi Radar, las alarmas y el tiempo libre de esos días por los caminos que
  ya existen, sin pasos nuevos.
- No hay tope de cambios (D-170) y repetir no consume cupo ninguno.
- Las acciones de los objetivos siguen en Plan con su propia ventana; solo se incluyen si el dueño responde la pregunta 3 en ese sentido.
- Un APK anterior no respeta en sus alarmas locales los cambios solo-semana hasta actualizar; el servidor sí. Hace falta un APK nuevo y publicar primero el backend.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.

## Preguntas abiertas

**Las tres que cambian el alcance (marcadas arriba):**

| # | Pregunta | Opciones |
|---|---|---|
| 1 | ¿Cómo se planifica una semana concreta si hoy todo vale «todas las semanas»? (FR-001) | **A (recomendada)** «Todas las semanas» sigue siendo la base y se suma «Solo esta semana» como alcance opcional. B: cada semana tiene siempre su propio plan, copiado de la anterior (cambia la lógica actual). C: no agregar alcance; solo confirmar que el plan de siempre sigue (casi no hay nada que repetir). |
| 2 | ¿«Mes» es de calendario o del programa? (FR-014) | **A (recomendada)** de calendario. B: del programa (bloques de 4 semanas desde el Día 1). C: la persona elige. |
| 3 | ¿«Planificación» incluye las acciones de los objetivos de Plan, o solo los hábitos? | **A (recomendada)** solo hábitos (lo que dijo el dueño); las acciones siguen en Plan. B: hábitos y acciones. C: primero hábitos y las acciones en una spec aparte. |

**Dudas menores asumidas (no cambian el alcance; ver «Assumptions»)**: dónde aparece la oferta y si se acepta cualquier día; en qué mes se cuenta una semana que
cruza dos meses; si «Ahora no» se recuerda por cuenta o por teléfono; si «Deshacer» debe seguir disponible después de cerrar la app (hoy: sí, vía «Volver al plan de siempre»).
