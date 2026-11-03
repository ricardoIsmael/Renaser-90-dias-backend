# Feature Specification: Planificar la semana y el mes — repetir lo planificado

**Feature Branch**: `028-planificacion-semana-y-mes` (backend y app)
**Created**: 2026-10-31
**Status**: Clarificada el 2026-10-31 con las decisiones del dueño (ver «Decisiones del dueño»); lista para planificar e implementar. Las tres preguntas abiertas quedaron resueltas; «planificar el mes» pasó a ser el plan de **objetivos** de Plan.
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
- **El dueño hizo todas las preguntas antes de implementar** y respondió el 2026-10-31 (ver la tabla de abajo).

### Session 2026-11-02 (pedido del dueño, con la captura del editor de «Escritura libre nocturna»)

Textual: «el de todas las semanas, si yo guardo el de semana debería de salir marcado, cuidado; y dice todas las semanas, ¿a qué? debería
deslizar hacia la derecha y poder planificar ¿no?… y recuerda: mucho texto o entrada a veces no les conviene». Eligió: `Siempre | Por semana`,
con `‹ Semana del 9 al 15 nov ›` y la fila de días. Lo que cambia (la regla del servidor y la app; sin tablas nuevas):

- Los alcances se llaman **«Siempre»** (FR-001/FR-002, sin cambios de fondo) y **«Por semana»**. «Por semana» deja pasar de semana, con flechas o
  deslizando la fila de días, **desde la semana en curso hasta la última del programa de la persona** (90 días, 3 meses o 1 año): ya no es
  «solo esta semana» sino cualquier semana por venir. Cada cambio sigue valiendo únicamente para la semana que se ve (FR-003).
- El servidor acepta cualquier semana por venir **cuyo lunes caiga dentro del programa** (400 después del último día; contrato
  `habit-preferences-week.md`). La app lee cada semana una sola vez, al llegar a ella.
- Lo ya guardado por semana **sale marcado** (FR-005): al abrir el hábito, si una semana por venir ya tiene cambios propios, el editor abre en
  «Por semana» en esa semana, con sus días marcados y sus valores. Sin cambios propios abre en «Siempre».
- Menos texto: el editor ya no lleva párrafos de ayuda; quedan los rótulos, la fila de días con su punto, y los botones. «Cómo suena» no se
  dibuja cuando no hay aviso.

### Decisiones del dueño (2026-10-31, `decisiones-del-dueno-2026-10-31.md`, sección 028)

| # | Decisión | Efecto en esta spec |
|---|---|---|
| 028-1 **A** | «Todas las semanas» sigue como base y se suma «Solo esta semana»; se ofrece repetir la semana anterior. | Resuelve FR-001. Sin cambiar la lógica actual: el plan de siempre sigue siendo la base. |
| 028-2 | **«Planificar el mes» es el plan de OBJETIVOS de Plan** (textual: «se refiere al plan que tiene, al plan de objetivos»). El mes es el **mes del programa** (Mes 1 = semanas 1 a 4…), no el del calendario. | Resuelve FR-014 y reescribe la historia 3: el mes se planifica en **Plan** (objetivo del mes y sus semanas), no en el editor de hábitos. |
| 028-3 | Repetir la semana es de los **hábitos**; el mes es de los **objetivos** de Plan. | Resuelve FR-023: las acciones de los objetivos NO entran en «repetir la semana». |
| 028-4 **A**, 028-5 **A** | Las dudas menores, como están escritas en «Assumptions». | La oferta vive dentro de Planificar y se acepta cualquier día; «Ahora no» se recuerda por cuenta en el teléfono; «Deshacer» sigue como «Volver al plan de siempre». |
| Reglas | «Lo único bueno que ya está es la planificación de hábitos»: se reutiliza; sin tablas nuevas; sin romper la lógica; alarmas del teléfono bien (specs 007 a 010 y #33). | Ver «Restricciones del dueño». |

### Lo que hay hoy (verificado en el código y en `AGENTS.md` el 2026-10-31)

| Pieza | Hoy |
|---|---|
| Dónde se planifica | Training → engranaje → «Tus prácticas» → una dimensión → hoja «Planificar <dimensión>» → editor de **un** hábito: hora, días, recordatorio y «Cómo suena» (specs 008 a 011). |
| Qué alcance tiene lo que se guarda | **Todas las semanas**. El editor lo dice así: «Solo sáb, dom, todas las semanas. Hoy no cambia.». Existen cambios para una sola fecha (por ejemplo apagar un día), pero la persona no los usa para planificar una semana y no se repiten. |
| Qué días se pueden cambiar | De mañana en adelante. Hoy y los días pasados van con candado (D-91). El domingo la hoja muestra la semana siguiente entera. No hay tope de cambios (D-170). |
| Vista de la semana completa | **No existe.** Cada hábito se edita uno por uno; no hay un lugar donde ver todos los hábitos de una semana ni copiarlos a otra. |
| Quién lee lo planificado | Training (días que vienen y lista por hora), las alarmas del teléfono (specs 007 a 010), los avisos del servidor, Mi Radar (el Despertar y el Dormir de cada día, spec 019), el tiempo libre de las fichas (spec 015) y «¿Programó?» (spec 018). |
| Acciones de los objetivos | Se planifican en **Plan** («Organizar mi semana», «Agendar»), con su propia ventana (domingo 12:00 a lunes 09:00). No se repiten (decisión 028-3). |

**Lo que esto significa**: hoy lo planificado ya «se repite» porque vale para todas las semanas. Lo que **no existe** es poder
planificar **una semana concreta** distinta de lo normal (un viaje, unos exámenes, una semana de descanso), ni **repetir** esa semana
o **ajustar el objetivo de cada mes** desde un solo lugar. Eso es lo que esta spec agrega.

### Palabras que usa esta spec

- **Plan de siempre**: lo que el editor guarda hoy para todas las semanas.
- **Plan de una semana**: lo que rige en los siete días (lunes a domingo) de una semana concreta: el plan de siempre más los cambios
  que la persona hizo **solo para esa semana**.
- **Repetir una semana**: dar a otra semana el mismo plan de una semana.
- **Planificar el mes**: en Plan, ver el mes del programa con su objetivo y sus semanas y ajustar el objetivo de cualquiera de los tres meses.

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

### User Story 3 - Planifico mi mes en Plan, si lo deseo (Priority: P2)

Como participante, quiero ver mi mes del programa (por ejemplo «Mes 2 · Semanas 5 a 8») con su objetivo y sus semanas, y poder
ajustar el objetivo de cualquiera de mis tres meses, pero solo si quiero.

**Why this priority**: es el segundo punto del pedido y es opcional por definición («si lo desea»). El dueño aclaró que «planificar
el mes» es el plan de objetivos que ya tiene Plan (objetivo de 90 días → mes → semana → día); no se crea otra planificación.

**Independent Test**: en Plan, con el Mapa activo, tocar «Planificar mi mes». Se abre una hoja con los tres meses del programa y el
actual elegido: se lee el objetivo del mes del eje abierto y sus semanas (cada una con sus fechas y su estado). Cambiar el objetivo
del mes 2, guardar: Plan lo muestra al llegar a ese mes. Salir sin guardar no cambia nada.

**Acceptance Scenarios**:

1. **Given** Plan con el Mapa activo y un eje abierto, **When** toco «Planificar mi mes», **Then** veo los tres meses del programa
   (el actual elegido) y, del elegido, su rótulo («Mes 1 · Semanas 1 a 4»), sus fechas y el objetivo del eje abierto (la cifra
   propuesta o la tuya, y «Sin cifra» con su motivo cuando el servidor no calcula una).
2. **Given** la hoja, **When** miro las semanas del mes, **Then** cada una dice su número, sus fechas («3 a 9 nov») y su estado:
   «Revisada», «Esta semana», «Se arma el domingo» (la que viene) o «Todavía no llega»; con el objetivo semanal del eje abierto
   cuando ya existe.
3. **Given** un mes que todavía no empezó, **When** toco «Cambiar» en su objetivo, **Then** edito la cifra con el mismo
   formulario de hoy y se guarda para ese mes (el servidor ya lo acepta); el mes en curso se corrige igual que hoy.
4. **Given** que salgo sin guardar, **Then** no cambia nada.
5. **Given** que toco la semana en curso (o la que se arma el domingo), **Then** se abre lo de siempre para esa semana («Organizar mi
   semana»); no hay ningún flujo nuevo para armar semanas que no llegaron (la ventana semanal del domingo no cambia).
6. **Given** el Mapa sin activar, **Then** Plan sigue con «Primero tu Mapa de Renacimiento» y no ofrece «Planificar mi mes».
7. **Given** que nunca uso «Planificar mi mes», **Then** nada me lo exige ni me lo recuerda: Plan funciona como hoy.

---

### User Story 4 - Veo qué quedó planificado y lo deshago (Priority: P2)

Como participante, quiero ver de un vistazo cómo quedó una semana y poder volver al plan de siempre si me arrepiento.

**Why this priority**: da confianza para usar la US1 y la US3; sin una salida clara, la gente no se atreve a repetir.

**Independent Test**: repetir una semana, tocar «Deshacer» en la confirmación: la semana vuelve al plan de siempre y las alarmas también.
Más tarde, desde Planificar, «Volver al plan de siempre» en esa semana hace lo mismo.

**Acceptance Scenarios**:

1. **Given** una semana, **When** la miro desde la oferta o desde el editor, **Then** leo un resumen corto («3 hábitos con cambios solo
   esta semana») y puedo abrir el detalle por hábito.
2. **Given** una semana repetida, **When** toco «Deshacer» (o «Volver al plan de siempre» en una semana con cambios), **Then** queda igual
   al plan de siempre y las alarmas y avisos se ajustan.
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
- **Últimos días de la formación**: no se ofrece repetir una semana que quede fuera de ella.
- **Semana que cruza dos meses del calendario** (por ejemplo del 26 oct al 1 nov): no importa, el mes es el del programa; la semana se
  muestra siempre con sus fechas completas.
- **Semana 1 del programa que empieza a mitad de semana, y semana 13 que termina el día 90**: sus fechas se cuentan desde el Día 1 y hasta
  el día 90, no de lunes a domingo completos.
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
  defecto) y «Solo esta semana». *(Decisión del dueño 028-1 A: «todas las semanas» sigue como base.)*
- **FR-002**: Con «Todas las semanas», lo que se guarda, las alarmas, los avisos y las reglas MUST ser exactamente los de las specs 008 a 011.
- **FR-003**: Un cambio «Solo esta semana» MUST regir únicamente en los días de esa semana que todavía se pueden cambiar y MUST NOT alterar el
  plan de siempre ni otras semanas.
- **FR-004**: Hoy y los días pasados MUST seguir con candado, para la hora, los días, el aviso y «Cómo suena».
- **FR-005**: Una semana con cambios propios MUST poder reconocerse y MUST poder volver al plan de siempre en un solo paso.

**Repetir la semana**

- **FR-006**: El sistema MUST ofrecer repetir la semana en curso en la siguiente cuando se cumplan las dos cosas: la semana modelo tiene
  cambios propios y repetirla dejaría a la siguiente distinta de como está. En cualquier otro caso MUST NOT ofrecerlo. Si la siguiente ya
  tiene cambios propios que la repetición reemplazaría, la oferta lo dice y pide confirmación explícita (FR-007; historia 1, escenario 4).
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

**Planificar el mes (en Plan, con el plan de objetivos)**

- **FR-014**: Plan MUST tener una entrada «Planificar mi mes» (solo con el Mapa activo) que abra una vista de los tres meses del programa,
  con el mes en curso elegido. *(Decisión 028-2: el mes es el del programa, Mes 1 = semanas 1 a 4; el plan es el de objetivos.)*
- **FR-015**: Del mes elegido la vista MUST mostrar su rótulo, sus fechas, el objetivo del mes del eje abierto (cifra propuesta o propia,
  o su motivo si no hay cifra) y sus semanas con fechas y estado; y MUST permitir corregir el objetivo de cualquiera de los tres meses con
  el mismo formulario y el mismo servicio que ya corrige el del mes en curso (`PUT /rocks/monthly/{eje}/{mes}`). Las acciones de la semana
  en curso (o la que se arma el domingo) se organizan con lo de siempre.
- **FR-016**: Planificar el mes MUST ser opcional: nada lo exige, no bloquea el uso de la app y no genera avisos nuevos.
- **FR-017**: Las semanas se cuentan como las cuenta el servidor (D-203: lunes a domingo, trece, la 13 hasta el día 90, la 1 desde el Día 1) y
  la vista MUST NOT ofrecer meses ni semanas fuera del programa. No se arman objetivos semanales de semanas que no llegaron: la ventana
  semanal (domingo 12:00 a lunes 09:00) no cambia.

**Alcance, formación y compatibilidad**

- **FR-018**: Todo MUST ocurrir dentro de la formación de la persona: solo sus hábitos, solo su plan. Ninguna lectura ni escritura MUST alcanzar el
  plan de otra persona ni de hábitos de otra formación.
- **FR-019**: Las alarmas del teléfono y los avisos del servidor de una semana repetida MUST ser los que saldrían de planificarla a mano (specs 007 a 010).
- **FR-020**: Los textos MUST ir en español de Perú, con tuteo y en tipo oración, con áreas de toque de 48 px, un solo scroll por pantalla, claro y oscuro, y
  el atrás del sistema subiendo un nivel por vez. Sin animaciones nuevas más allá de las hojas y confirmaciones en línea que la app ya usa.
- **FR-021**: Una app nueva contra un servidor anterior MUST comportarse como hoy (sin oferta ni «Solo esta semana»). Una app anterior contra el
  servidor nuevo MUST seguir funcionando como hoy.
- **FR-022**: Todo MUST documentarse en el mismo cambio (decisión D-nn y, si hay errores, E-nn) y la autorización del dueño sobre Training MUST anotarse en `AGENTS.md`.
- **FR-023**: Lo que se repite de una semana a otra MUST ser la planificación de los **hábitos**; las acciones de los objetivos que se agendan en
  Plan NO se repiten. *(Decisión 028-3: repetir la semana es de los hábitos; el mes es el plan de objetivos.)*

### Key Entities

- **Plan de siempre de un hábito**: la hora, los días, el recordatorio y «Cómo suena» que rigen todas las semanas. Ya existe.
- **Plan de una semana**: los cambios de un hábito para los días de una semana concreta. Pertenece a una persona, a un hábito y a una semana de su formación.
- **Oferta de repetición**: la propuesta de dar a una semana el plan de otra; tiene estado (ofrecida, aceptada o descartada) para un par de semanas.
- **Semana de la persona**: lunes a domingo, contada en la zona de la persona.
- **Mes del programa**: bloque de cuatro semanas del programa (la 13 se suma al mes 3), tal como lo cuenta Plan y el servidor de objetivos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde abrir Planificar, repetir una semana toma como máximo 2 toques y menos de 10 segundos.
- **SC-002**: Corregir el objetivo de un mes desde «Planificar mi mes» toma como máximo 3 toques desde Plan.
- **SC-003**: Una semana repetida y la misma semana planificada a mano dan exactamente lo mismo en hora, días, aviso y «Cómo suena» para los 7 días de al
  menos 3 hábitos (comparación automática en E2E): 0 diferencias.
- **SC-004**: Después de repetir, hoy, los días pasados, lo registrado, los puntos, la racha y el semáforo no cambian (E2E): 0 cambios.
- **SC-005**: El 100 % de las alarmas del teléfono de la semana repetida coinciden con las de planificarla a mano (verificado en el emulador).
- **SC-006**: En una prueba con dos personas (y, cuando existan, dos formaciones), repetir una semana en una deja 0 cambios en las horas, días, avisos y alarmas de la otra, y un hábito que no le corresponde a la persona se rechaza sin afectar nada.
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
4. **Todo queda dentro de la formación de la persona.** Criterio verificable por E2E: con dos personas (de formaciones distintas cuando existan), repetir una
   semana en una no cambia ni una hora, un día ni un aviso de la otra; un hábito que no pertenece a la formación de la persona no se puede
   planificar ni repetir (se rechaza sin afectar nada).
5. **El dueño hizo las preguntas antes de implementar**: respondió el 2026-10-31 (ver «Decisiones del dueño»). Lo que no está listado es la opción A.

## Assumptions

- «Por semana» es de lunes a domingo, contada en la zona de la persona (la regla de siempre de la app).
- El plan de siempre sigue siendo la base y gana donde la semana no tiene cambios propios.
- La oferta aparece dentro de Planificar (no como pantalla ni aviso nuevos). El recordatorio de los domingos que ya existe («Arma tu semana los domingos») abre Planificar
  como hoy; no se agrega ninguna notificación. *(a confirmar)*
- La oferta se puede aceptar cualquier día, no solo el domingo, mientras queden días por planificar en la semana destino. *(a confirmar)*
- «Ahora no» se recuerda por cuenta; en otro teléfono puede volver a aparecer una vez. Es un límite aceptado.
- Lo que hoy se puede cambiar para una sola fecha no alcanza para todo lo que el editor guarda por día de la semana (varios avisos, «Cómo suena»). El plan
  debe resolver cómo cubrir ese hueco ampliando lo existente, sin tabla nueva y sin cambiar lo que ya se guarda. Es el riesgo técnico principal de esta spec.
- El Despertar y el Dormir cuentan como hábitos: repetirlos cambia las horas de Mi Radar, las alarmas y el tiempo libre de esos días por los caminos que
  ya existen, sin pasos nuevos.
- No hay tope de cambios (D-170) y repetir no consume cupo ninguno.
- Las acciones de los objetivos siguen en Plan con su propia ventana y no se repiten (decisión 028-3).
- «Planificar mi mes» no inventa reglas de negocio: usa el servicio de objetivos mensuales que ya existe. Que el servidor acepte corregir meses que
  todavía no empezaron ya es así hoy (`PUT /rocks/monthly/{eje}/{mes}` no exige el mes en curso); la pantalla de hoy solo ofrecía el mes en curso.
- Esta spec se implementa sobre el estado actual del backend (una formación): el aislamiento por formación de las specs 022 y 023 se cumple hoy con la
  persona, su audiencia por rol y sus hábitos personales; cuando existan las formaciones, el filtro de «hábitos aplicables» ya es el punto único donde sumarlas.
- Un APK anterior no respeta en sus alarmas locales los cambios solo-semana hasta actualizar; el servidor sí. Hace falta un APK nuevo y publicar primero el backend.
- «Lo que hoy se guarda por fecha no alcanza» se resuelve ampliando la tabla que ya guarda las excepciones de una fecha con los mismos campos que el editor guarda por
  día de la semana (varios avisos y «Cómo suena»), sin tabla nueva (V130 y V131; ver el plan).
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.

## Preguntas abiertas

Ninguna. Las tres preguntas que cambiaban el alcance (FR-001, FR-014, FR-023) las respondió el dueño el 2026-10-31 (ver «Decisiones del dueño»); las dudas
menores quedaron como están en «Assumptions» (028-4 A y 028-5 A).
