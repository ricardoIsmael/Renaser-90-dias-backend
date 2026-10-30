# Feature Specification: Huecos de horario

**Feature Branch**: `015-huecos-de-horario` (backend; la app, solo si el dueño lo decide en la pregunta 3)
**Created**: 2026-10-29
**Status**: Borrador para que decida el dueño (3 puntos abiertos, ver «Puntos abiertos»)
**Input**: Pedido del dueño (2026-10-29), textual resumido: «De lo que planifican, buscar o encontrar un algoritmo de los
huecos de horario que dejan, para guardar esta información y recapitular. Si son por usuarios… primero ordenaría todos
los hábitos por hora… haría búsqueda binaria para capturar la diferencia de hora o minutos del próximo hábito… lo
guardaría en memoria y en base a eso puedo dar una conclusión o sugerir horario, pero esto es a futuro.»

## Clarifications

### Session 2026-10-29 (pedido del dueño y respuesta técnica)

- **Qué pidió el dueño.** Con lo que la persona planifica, calcular los huecos que le quedan, recapitular y, más
  adelante, sugerir horarios. Lo dijo «a futuro»: esta spec deja escrito qué se espera para que el dueño decida; no se
  construye nada hasta que lo apruebe.
- **Respuesta técnica que ya se le dio** (contexto, no decisión de producto):
  - **Ordenar** por hora de inicio con el ordenamiento estable del lenguaje. Son unos 20 ítems por día: ordenar cuesta
    O(n log n) y es instantáneo.
  - **Juntar** los ítems que se cruzan y **recorrer una sola vez**: el hueco es la distancia entre el fin del anterior y
    el inicio del siguiente, O(n). La búsqueda binaria no hace falta para armar la lista; queda para consultas puntuales
    («¿está libre a las 15:00?»).
  - **Lo que le falta a la idea original** (queda como requisito en esta spec):
    - la **duración** de cada ítem, con una duración por defecto para los que no la tienen;
    - la **ventana del día**, de Despertar a Dormir;
    - el **horario vigente de cada fecha**: horas distintas por día de la semana, días sin el hábito, cambios diferidos
      «desde mañana» (D-91) y pausas;
    - las **acciones del día** y los **eventos** con hora;
    - lo **planificado frente a lo real** (la hora en que se cumplió y el Radar);
    - un **hueco mínimo útil** (por ejemplo, 30 minutos);
    - la **zona horaria** de la persona;
    - **no duplicar datos derivables** (constitución IV): calcular cuando se pide y, como mucho, guardar un resumen
      semanal si se quieren comparar semanas;
    - que **cada sugerencia se explique**.
- **Lo que el dueño ya había decidido y esta spec respeta**: no crear tablas si se puede reutilizar (spec 012, tercera
  ronda); mover algo es una propuesta con botón y nunca se aplica solo (D-160); «Sin registro» no es falta (spec 012,
  FR-012).

### Lo que hay hoy (verificado en el código el 2026-10-29, `origin/master` en `21d6cc13`)

**No existe nada que calcule huecos a partir de lo que la persona planifica.** Lo más parecido:

- **`buscar_huecos_para_habitos` de SER** (D-160, módulo `rag`): cruza las horas ocupadas que la persona *dice* con la
  franja (inicio a límite) de cada hábito de un día. Su `AgendaOcupada` ya hace el algoritmo del dueño: ordena, junta los
  tramos que se cruzan o se tocan (`unir`) y saca lo libre con un solo recorrido (`libresEntre`). Límites para
  reutilizarla: vive en el dominio de `rag` (otro módulo solo puede importar `<módulo>/api/`), admite 12 tramos como
  máximo (un día trae unos 20 ítems) y no conoce la duración de nada.
- **`agenda_ocupada`** (V64, D-161): los tramos en que la persona dice estar ocupada (trabajo, clases), por día de la
  semana, en minutos y sin etiqueta. Solo la escribe SER al confirmar un botón y solo la lee `rag`.
- **«Mi agenda»** (solo en la app, `eventos/utils/miAgenda.ts`): siete días con eventos, hábitos con hora y acciones con
  hora, en orden de hora. Sin duración, sin huecos, en la zona del teléfono.

**De dónde salen las horas** (las que ya existen; ninguna se toca):

| Qué | Dónde está | Qué da y qué no |
|---|---|---|
| Horario de los hábitos de una fecha | `HorarioDelDiaFinder.deFecha(persona, fecha)` en `habits/api` (lo usa `consultar_horarios` de SER) | Ya resuelve la precedencia **fecha > día de la semana > cambio general diferido > preferencia > catálogo**, y marca apagado, pausado, obligatorio y el cambio programado. Acepta fechas futuras; `null` es hoy en la zona de la persona. Trae `horaDisparo` (puede ser `null`: «sin hora fija») y `horaLimite` (el **plazo para cumplir**, no cuánto dura). Una hora por hábito y día. |
| Sus fuentes | `preferencias_horario`, `horario_semanal_habito` (spec 008, V39/V40), `horarios_habito_por_fecha` (V37/V38), `cambios_horario_pendientes` (D-91) y `horarios_habito` (catálogo) | Se **sobrescriben**: no hay historia de qué hora regía en una semana ya vivida. `historial_cambios_horario` guarda la hora de los cambios que cuentan para la cuota semanal, no de todos. |
| Pausas | `desbloqueos_habito.pausado_en` / `pausado_hasta` (V23/V31), derivadas del calendario (`estaPausadoEl`) | El finder ya las marca. |
| Qué hábitos le tocan ese día | El generador diario (`RegistroService`) | Aplica además `todaviaNoLeToca` (hábito aún no desbloqueado) y el día elegido de los hábitos semanales (`dias_semanales_habito`); **el finder no marca esas dos**. Para hoy y días ya vividos, la lista exacta es la de `registros_habito`; para días por venir no hay filas. Los hábitos del programa solo existen del día 0 al 90 (`CHECK` de `registros_habito`). |
| Acciones del día | `rocas_diarias.hora_inicio` / `hora_fin` (ambas opcionales, sin validación de orden); `RocasDelAprendizFinder` da hoy y mañana; las agendadas a otro día salen de `GET /rocks/upcoming` | Una acción sin hora no entra a la agenda. |
| Eventos | `EventosDelParticipanteFinder.proximosDias` en `calendar/api` (hasta 90 días, hora local de la persona, audiencia y recurrencias ya resueltas, con su Voy / No voy) | `duracion_minutos` es opcional: sin él, `terminaLocal` viene `null`. Un evento puede cruzar la medianoche. |
| Lo real | `registros_habito.completado_en` y `rocas_diarias.completada_en` (instantes); `registros_radar.creado_en` | El Radar guarda la hora en que se respondió; la hora *prevista* llega con la spec 012, que todavía no está en `master`. La asistencia a eventos existe, pero **la persona no la ve** (D-256). |
| Zona | `participantes_programa.timezone` | El día de la persona es el de su zona (E-91, E-105). Un hábito nunca cruza la medianoche (D-122: disparo hasta 23:40, límite hasta 23:50). |
| Despertar y Dormir | Hábitos del catálogo con `clave_sistema` `WAKE_UP` y `SLEEP` (V26) | Tienen hora como cualquier otro hábito. |

**Precedentes de una foto guardada**: `semaforo_dias` y `semaforo_semanas` (V68) guardan el cierre de cada día y de cada
semana (sábado a viernes), append-only. Sirven de modelo, pero guardan conteos del semáforo, no horas.

**Dato clave para la pregunta 2**: como las horas se sobrescriben, **lo planificado de una semana ya vivida solo se puede
reconstruir con el horario que rige hoy**. Lo real (a qué hora cumplió) sí queda tal cual, porque ya está registrado.

## Decisiones del dueño (2026-10-29)

- **Pregunta 1:** en esta primera versión la persona **no** los ve. Solo su acompañamiento, en la ficha de la persona y
  en solo lectura: el mentor o la guía vigente, el líder de mentores, el administrador y el alquimista. Textual: «no lo
  ve todavía; solo en su ficha lo ven los mentores, y también los líderes, alquimista y administrador».
- **Pregunta 2:** el dueño preguntó cuál era la opción correcta y se inclinó por una tabla nueva. Es la correcta: las
  horas de los hábitos se sobrescriben y no queda historia del horario de cada semana, así que sin la foto semanal una
  semana pasada se compararía con el horario de hoy (opción B, con una tabla nueva).
- **Pregunta 3:** en la primera versión solo se calculan y se muestran (opción A).
- **Constancia de la consulta (2026-10-29, después de construir la primera versión):** cuando el líder de mentores, el
  administrador o el alquimista leen los huecos de una persona, queda constancia de la consulta, igual que al abrir su
  ficha semanal (spec 005, FR-016). Se reutiliza la tabla de consultas que ya existe (`consultas_de_ficha`, V109): sin
  tabla nueva. Ver FR-027.

> **Corregido 2026-10-29.** Las preguntas de abajo quedaron respondidas; se conservan como contexto.

## Puntos abiertos (los decide el dueño)

Cada uno cambia el resultado. Va primero la opción que recomienda el equipo.

**Pregunta 1 — ¿Quién ve los huecos de una persona?**

- **A (recomendada): solo la persona.** Es un dato que sale de su propio plan y nadie pidió abrirlo al acompañamiento.
  Abrirlo después es un cambio de permiso, no de cálculo. SER lo lee en nombre de la persona cuando conversa con ella.
- **B: la persona y su acompañamiento, solo lectura** (mentor o guía del grupo, líder de mentores, administrador y
  alquimista), la misma cadena de la spec 012. Sirve para acompañar («no tiene ni un hueco de 30 minutos en la
  semana»). Costo: es un dato nuevo que habría que sumar a lo que dice la Política de Privacidad, y gerencia no lo pidió.
- **C: la persona, y el líder ve solo un resumen por grupo, sin nombres** («el hueco más común del grupo es de 14:00 a
  15:00»). No expone a nadie, pero es una lectura más que construir.

**Pregunta 2 — ¿Se guarda un resumen de cada semana o solo se calcula cuando se pide?**

- **A (recomendada): solo se calcula cuando se pide.** Es lo que pidió el dueño («en memoria») y no crea tablas. Hay que
  decir en voz alta su límite: una semana ya vivida se recapitula con el horario que rige hoy; si la persona cambió sus
  horas en el medio, lo planificado de esa semana puede no ser lo que tenía entonces (lo real no cambia). La pantalla
  lo avisa.
- **B: guardar una foto por persona y semana al cerrarla** (minutos ocupados, libres útiles, huecos que se repiten y el
  horario que regía). Permite comparar semanas sin depender de lo que cambió después. Costo: una tabla nueva (la más
  parecida, `semaforo_semanas`, guarda otra cosa) y el dueño pidió reutilizar antes que crear.

**Pregunta 3 — ¿Qué se hace con los huecos en esta primera versión?**

- **A (recomendada): solo calcularlos y mostrarlos**: huecos del día y de la semana y la recapitulación, en lectura,
  sin sugerencias. Qué pantalla los muestra (un tab nuevo o dentro de uno existente) se decide aparte, porque tocar un
  tab pide la autorización escrita del dueño (constitución VI).
- **B: lo de A, y que SER los use** para contestar y sugerir cuando la persona conversa, en vez de preguntarle cada vez
  «¿a qué hora estás ocupada?». Lo que mueve sigue siendo una propuesta con botón.
- **C: lo de B, y la sugerencia dentro de la app** para las actividades que la persona prepara para mañana (por
  ejemplo, las tres de «Preparar mañana» del encargo de gerencia): la historia 3 completa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver mis huecos de un día y de una semana (Priority: P1)

Como persona que hace los 90 días, quiero ver en qué momentos de un día —y de los siete días de una semana— no tengo
nada planificado, para saber cuánto tiempo libre me queda de verdad antes de comprometerme con algo más.

**Why this priority**: es la base de todo lo demás. Sin los huecos bien calculados no hay recapitulación ni sugerencias,
y por sí sola ya le dice a la persona algo que hoy tiene que adivinar.

**Independent Test**: una persona con Despertar a las 06:00, un hábito a las 07:00, un evento de 10:00 a 11:00 y Dormir
a las 22:00 pide su día y lee los huecos 06:30–07:00, 07:30–10:00 y 11:00–22:00 (con 30 minutos por defecto para el
hábito y para Despertar), cada uno diciendo entre qué ítems quedó.

**Acceptance Scenarios**:

1. **Given** un día con varios ítems a distintas horas, **When** pido los huecos, **Then** veo la ventana del día, los
   bloques ocupados en orden y cada hueco con desde, hasta, minutos y entre qué ítems quedó.
2. **Given** dos ítems que se cruzan (el evento 10:00–11:30 y un hábito a las 11:00), **When** pido los huecos, **Then**
   se cuentan como un solo bloque 10:00–11:30 y ningún hueco se mete adentro.
3. **Given** un hueco de 20 minutos y un mínimo útil de 30, **When** pido los huecos, **Then** ese hueco no aparece en
   la lista, pero sus 20 minutos sí suman en el tiempo libre total del día.
4. **Given** un hábito con hora propia el sábado y un cambio de hora diferido que rige desde el lunes, **When** pido el
   sábado y el lunes, **Then** cada día usa la hora que de verdad regirá ese día, la misma que muestra Planificar.
5. **Given** un hábito apagado o pausado ese día, o que todavía no se desbloquea, **When** pido el día, **Then** no
   ocupa tiempo.
6. **Given** un hábito sin hora fija, **When** pido el día, **Then** no ocupa nada y se lista aparte como «sin hora fija».
7. **Given** que guardé con SER que trabajo de lunes a viernes de 09:00 a 18:00, **When** pido un martes, **Then** esas
   horas cuentan como ocupadas y el día lo dice, para que no me muestre como libre la hora de mi trabajo.
8. **Given** una semana, **When** la pido, **Then** veo los siete días de lunes a domingo, cada uno como en el primer
   escenario, con el tiempo libre de cada día y el total de la semana.
9. **Given** una fecha por venir, **When** la pido, **Then** se calcula con el plan que tengo y se lee como plan, no como
   algo que ya pasó.

---

### User Story 2 - Recapitular mi semana: lo planificado frente a lo real (Priority: P2)

Como persona que terminó una semana, quiero ver a qué hora planifiqué cada cosa y a qué hora la hice, y qué huecos se
me repiten, para entender cómo uso mi tiempo.

**Why this priority**: es lo que el dueño llamó «recapitular». Sin la historia 1 no se puede, y sin ella la historia 1
solo mira hacia adelante.

**Independent Test**: con una semana de registros, la recapitulación lista cada hábito con hora, su hora planificada y
la real, dice cuántos se cumplieron dentro de su franja y marca el tramo de 14:00 a 15:00 como libre en 4 de los 7 días.

**Acceptance Scenarios**:

1. **Given** una semana ya vivida, **When** pido la recapitulación, **Then** cada hábito con hora muestra su hora
   planificada, la hora en que se cumplió y si fue dentro o fuera de su franja.
2. **Given** un hábito sin registro, **When** miro la recapitulación, **Then** dice «Sin registro», en gris, sin palabras
   de falta ni de culpa, y no cuenta en contra en ningún lugar.
3. **Given** un tramo libre durante al menos el mínimo útil en 3 o más días de la semana, **When** miro los huecos que
   se repiten, **Then** aparece ese tramo diciendo en cuántos días se apoya.
4. **Given** menos de 3 días con plan, **When** miro los huecos que se repiten, **Then** no se muestra ninguno y se
   dice por qué.
5. **Given** registros del Radar dentro de un hueco, **When** miro la recapitulación, **Then** puede decir qué energía
   anoté ahí, con cuántos registros la sostienen.
6. **Given** que el horario de un hábito cambió durante esa semana, **When** miro la recapitulación, **Then** se me
   avisa que lo planificado se muestra con el horario de hoy. *(Depende de la pregunta 2.)*
7. **Given** eventos a los que fui, **When** miro la recapitulación, **Then** no aparece mi asistencia: yo no la veo en
   ningún otro lugar.

---

### User Story 3 - Que me sugieran dónde poner una actividad (Priority: P3, a futuro)

Como persona que prepara su mañana, quiero que me propongan en qué hueco poner cada actividad (por ejemplo, las tres
de «Preparar mañana»), con la razón, para no tener que buscar yo dónde caben.

**Why this priority**: lo pidió el dueño como «a futuro». Necesita las historias 1 y 2 y, por la pregunta 3, puede no
entrar en la primera entrega.

**Independent Test**: con mañana con tres huecos de 40, 90 y 35 minutos y tres actividades de 30, 60 y 30 minutos,
pido la sugerencia y cada actividad cae en un hueco donde cabe, sin pisar nada, con su explicación; la persona
confirma o elige otro y solo entonces se agenda.

**Acceptance Scenarios**:

1. **Given** actividades con su duración (propia o por defecto) y un día con huecos, **When** pido sugerencias,
   **Then** cada actividad recibe un hueco donde cabe, dentro de la ventana del día y sin pisar ningún ítem.
2. **Given** una sugerencia, **When** la leo, **Then** dice por qué ese hueco: entre qué ítems queda, cuántos minutos le
   sobran y si coincide con un hueco que se me repite.
3. **Given** una sugerencia, **When** no hago nada, **Then** no se agenda ni se mueve nada. Solo se aplica si la confirmo
   o elijo otro hueco.
4. **Given** una actividad que no cabe en ningún hueco, **When** pido sugerencias, **Then** me lo dice y me muestra el
   hueco más largo, sin inventar una hora.
5. **Given** que pido sugerencias para hoy, **When** ya pasó parte del día, **Then** solo considera lo que queda desde
   ahora.

### Edge Cases

- **Ítems que se cruzan**: parcialmente, uno dentro de otro, con el mismo inicio y fin, o tocándose justo (uno termina
  a las 10:00 y el otro empieza a las 10:00). En todos se juntan en un bloque y no queda hueco de cero minutos.
- **Muchos ítems a la misma hora** (varios hábitos a las 06:00): ocupan el tramo una sola vez, no se suman.
- **Todo el día lleno**: no hay huecos; se dice «No queda ningún hueco de 30 minutos» y se muestra el bloque ocupado.
- **Día sin plan** (ningún ítem con hora): un solo hueco, la ventana entera, y se dice que no hay nada planificado en
  lugar de presentarlo como tiempo libre calculado.
- **Cruce de medianoche**: un evento o una acción puede empezar un día y terminar al siguiente; se parte en la
  medianoche y cada día cuenta su parte. Un hábito nunca cruza (D-122).
- **Ventana del día**: sin Despertar o sin Dormir, con alguno apagado, o con Dormir antes que Despertar (persona que
  trabaja de noche), la ventana es el día entero de 00:00 a 24:00.
- **Cambio de zona** (viaje): las horas se leen en la zona vigente de la persona, como los hábitos. En una zona con
  cambio de hora, un día de 23 o 25 horas se mide en las horas que de verdad tiene.
- **Hábito pausado**: los días de pausa no ocupa; al terminar la pausa vuelve solo, sin que nadie lo reactive.
- **Hábito aún no desbloqueado** y **hábito semanal con día elegido**: solo ocupan el día que les toca.
- **Programa sin Día 1**: no hay hábitos del programa que contar; se calculan los eventos, las acciones (si las hay) y
  las horas ocupadas guardadas, y se dice que todavía no eligió su Día 1.
- **Fecha antes del Día 1 o después del día 90**: sin hábitos del programa (no existen fuera de 0–90); lo demás se
  calcula y se dice que la fecha está fuera de sus 90 días.
- **Duración desconocida**: evento sin duración, acción con solo la hora de inicio, o con la hora de fin anterior a la
  de inicio: se usa la duración por defecto y no se rechaza nada.
- **Evento sin respuesta o «Quizás»** ocupa; «No voy» no (como «Mi agenda»).
- **Hoy**: los huecos que ya pasaron se muestran como pasados; para sugerir solo cuenta lo que queda.
- **Radar**: sus avisos (hasta 12 por día los días 1 a 7) no son actividades; si contaran, taparían todo el día.
- **Cuenta suspendida** o **persona sin permiso**: el servidor lo rechaza, como en el resto de la app.
- **Semana en curso** (unos días ya vividos y otros por venir): cada día se marca como vivido o por venir, y la
  recapitulación solo mira los vividos.

## Requirements *(mandatory)*

### Functional Requirements

**El cálculo (historia 1)**

- **FR-001**: Para una persona y una fecha, el sistema MUST entregar la ventana del día, los ítems que ocupan tiempo (con
  su hora y de dónde vienen), los huecos (desde, hasta, minutos y entre qué ítems quedaron) y el tiempo ocupado y libre
  del día.
- **FR-002**: Los ítems que ocupan tiempo MUST ser los hábitos con hora, las acciones del día con hora y los eventos con
  hora a los que la persona no dijo «No voy», más las horas ocupadas que la persona guardó con SER.
- **FR-003**: Cada ítem MUST usar el horario vigente de **esa** fecha, el mismo que ve la persona al planificar: hora
  propia por día de la semana o por fecha, cambio diferido desde su fecha. Para hoy y los días ya vividos, los hábitos
  que cuentan son los que de verdad le tocaron; para los días por venir, los que le tocarán según su plan. Un hábito
  apagado, pausado, todavía no desbloqueado o fuera de su día elegido MUST NOT ocupar tiempo.
- **FR-004**: Un hábito sin hora fija MUST NOT ocupar tiempo y MUST listarse aparte.
- **FR-005**: Cada ítem MUST tener una duración: la propia si la tiene (evento con duración, acción con inicio y fin) y,
  si no, una duración por defecto que se puede cambiar sin tocar el comportamiento.
- **FR-006**: Los ítems que se cruzan o se tocan MUST juntarse en un solo bloque ocupado. El resultado MUST NOT depender
  del orden en que llegan los ítems; en todo cálculo, tiempo ocupado más tiempo libre MUST ser igual a la ventana del
  día, y ningún hueco MUST cruzarse con un ítem.
- **FR-007**: La ventana del día MUST ir de la hora de Despertar a la de Dormir; sin ellas, o si no son coherentes, el
  día entero.
- **FR-008**: Un hueco MUST listarse solo si mide al menos el mínimo útil; los menores MUST seguir sumando al tiempo
  libre total.
- **FR-009**: El día MUST ser el día de la persona en su zona y todas las horas MUST ser locales. Lo que cruza la
  medianoche MUST partirse y contarse en cada día.
- **FR-010**: Para una semana, el sistema MUST entregar los siete días de lunes a domingo, cada uno como en FR-001, con
  el tiempo libre por día y el total, marcando cuáles ya se vivieron y cuáles vienen. MUST aceptar fechas por venir.
- **FR-011**: Antes del Día 1, después del día 90 y sin Día 1 elegido, el sistema MUST NOT inventar hábitos del programa;
  MUST calcular lo demás y MUST decir que la fecha está fuera de sus 90 días o que aún no eligió su Día 1.
- **FR-012**: Calcular los huecos MUST ser de solo lectura: no crea ni cambia horarios, no gasta el cupo de cambios, no
  da puntos y no toca racha, semáforo ni coherencia.
- **FR-013**: Cada hueco y cada total MUST poder explicarse: entre qué ítems quedó y de dónde salió la hora de cada uno
  (propia de la persona, cambio programado o del programa).

**La recapitulación (historia 2)**

- **FR-014**: Para una semana ya vivida, el sistema MUST mostrar, por cada hábito con hora, su hora planificada, la hora
  real en que se cumplió y si fue dentro o fuera de su franja, y un resumen de la semana con cuántos fueron dentro,
  fuera y sin registro.
- **FR-015**: «Sin registro» MUST NOT presentarse ni contarse como falta en ningún lugar (mismo criterio que la spec
  012).
- **FR-016**: Un hueco que se repite MUST ser un tramo del día libre, durante al menos el mínimo útil, en al menos 3 días
  de la semana, y MUST decir en cuántos días se apoya. Con menos de 3 días con plan MUST NOT mostrarse ninguno.
- **FR-017**: Lo real MUST incluir la hora de cumplimiento de hábitos y acciones y, si los hay, los registros del Radar.
  El Radar MUST NOT contar como tiempo ocupado. La asistencia a eventos MUST NOT usarse, porque la persona no la ve.
- **FR-018**: La recapitulación de una semana ya vivida MUST decir con qué horario calculó lo planificado.
  Al cerrar cada semana MUST guardarse una foto por persona: los minutos ocupados y libres útiles, los huecos que se
  repiten y el horario que regía esa semana. La recapitulación de una semana pasada MUST salir de esa foto y no del
  horario de hoy. La foto es historia: MUST NOT reescribirse después (constitución IV). Decisión del dueño, pregunta 2.

**Las sugerencias (historia 3, a futuro)**

- **FR-019**: Dadas una o más actividades con su duración, el sistema MUST proponer para cada una un hueco donde quepa,
  dentro de la ventana, sin pisar ningún ítem y sin salir del día. MUST considerar solo el tiempo que queda desde
  ahora cuando el día es hoy.
- **FR-020**: Cada sugerencia MUST explicarse: entre qué ítems queda, cuántos minutos sobran y si coincide con un hueco
  que se repite.
- **FR-021**: Una sugerencia MUST NOT aplicarse sola: se agenda o se mueve algo solo cuando la persona confirma o elige
  otro hueco (D-160).
- **FR-022**: Si una actividad no cabe en ningún hueco, el sistema MUST decirlo y mostrar el hueco más largo, sin
  inventar una hora.
- **FR-023**: La primera versión MUST solo calcular y mostrar los huecos y la recapitulación. Las sugerencias (FR-019 a
  FR-022) y su uso por SER quedan para una versión siguiente. Decisión del dueño, pregunta 3.

**Quién lo ve y compatibilidad**

- **FR-024**: *(Corregido 2026-10-29: en la primera versión la persona no los ve.)* Los huecos y la recapitulación MUST
  verse solo en la ficha de la persona, para su acompañamiento. Cualquier otra persona o rol sin permiso,
  y una cuenta suspendida, MUST recibir un rechazo del servidor; la app no recalcula permisos.
  Además MUST poder leerlos, solo en lectura, su acompañamiento, con la misma cadena del Radar (spec 012): el mentor o
  la guía vigente, el líder de mentores, el administrador y el alquimista. Para el personal que hace su programa rige la
  spec 013. MUST sumarse a la Política de Privacidad antes de publicarlo. Decisión del dueño, pregunta 1.
  *(Corregido 2026-10-29: cuando lo lee el líder de mentores, el administrador o el alquimista queda constancia de la
  consulta; ver FR-027. La lectura del mentor o la guía no deja constancia, como tampoco la deja hoy su Radar del
  alumno ni la spec 005 la pide para sus rutas.)*
- **FR-025**: Lo que muestre la app, lo que use SER y lo que lea cualquier otro consumidor MUST salir del mismo cálculo
  del servidor: una sola regla.
- **FR-026**: Todo MUST ser aditivo: el APK publicado sigue igual con el backend nuevo, y una app nueva contra un backend
  anterior no muestra nada nuevo.
- **FR-027** *(Corregido 2026-10-29: antes la spec no decía nada de esto y la primera versión no lo registraba.)* El
  sistema MUST dejar constancia de cada vez que el líder de mentores, el administrador o el alquimista leen los huecos
  de una persona, con la misma constancia que deja la ficha de la semana (spec 005, FR-016): quién, con qué rol, a
  quién, de qué grupo, a qué mentor, el lunes de la semana que miró y cuándo. Reglas:
  - Reutiliza la tabla y el puerto de la ficha de la semana (`consultas_de_ficha`, append-only): MUST NOT crear una
    tabla nueva. La fila no distingue si lo que se miró fue la semana del semáforo o los huecos.
  - La constancia se deja DESPUÉS de leer y en la misma transacción. Una lectura que falla (semana que no se puede
    pedir, persona que no se encuentra, cuenta suspendida) y un rechazo (403 por rol o cuenta suspendida, 404 por grupo
    o persona ajena) MUST NOT dejarla; si no se puede dejar la constancia, MUST NOT entregarse la lectura.
  - Se anota el lunes que de verdad se miró (el resuelto, también cuando el pedido no trae semana), no el pedido.
  - Cada llamada es una fila: cambiar de semana o repetirla es otra.
  - Entra al resumen que el mentor recibe a las 20:00 («Tu líder revisó a tu grupo», spec 005, FR-017) con la regla de
    siempre: cuenta solo la consulta del **líder de mentores** (lo de administración y alquimista queda registrado pero
    no avisa) y junta a las personas **sin repetir a nadie**, con un solo aviso por mentor y día. Abrir la semana y los
    huecos de la misma persona el mismo día cuenta una vez.

### Key Entities

- **Ítem planificado**: algo de la persona con hora en un día —un hábito, una acción del día, un evento o un tramo
  ocupado que guardó con SER—. Tiene título, hora de inicio, duración (propia o por defecto) y de dónde viene la hora.
- **Ventana del día**: el tramo del día que cuenta para los huecos, de Despertar a Dormir o el día entero.
- **Hueco**: un tramo libre de al menos el mínimo útil, con su desde, su hasta, sus minutos y los ítems entre los que
  quedó. Se deriva; no se guarda.
- **Recapitulación de la semana**: por día y por hábito, lo planificado frente a lo real, más los huecos que se repiten y
  los totales. Se deriva; solo se guardaría si el dueño elige la opción B de la pregunta 2.
- **Sugerencia** *(a futuro)*: una actividad, el hueco propuesto y su explicación. No se guarda hasta que la persona la
  confirma.
- **Constancia de la consulta** *(FR-027)*: la fila de `consultas_de_ficha` que deja quien del liderazgo leyó los huecos
  de una persona. Ya existe (spec 005); no es una entidad nueva.

Ninguna entidad es una tabla nueva, salvo que el dueño elija guardar la foto semanal (pregunta 2, opción B). La
constancia de la consulta (FR-027) reutiliza `consultas_de_ficha`: sin tabla ni migración nuevas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 95 % de los pedidos de un día (de hasta 60 ítems) responde en menos de 1 segundo, y el de una semana
  en menos de 2.
- **SC-002**: En el 100 % de los casos de prueba, tiempo ocupado más tiempo libre es igual a la ventana, y no hay ningún
  hueco que se cruce con un ítem. Las pruebas incluyen todos los casos de borde de arriba.
- **SC-003**: Con el reloj en la madrugada UTC, el 100 % de los días calculados son el día de la persona en su zona (Lima
  incluida).
- **SC-004**: En el 100 % de las fechas de prueba (hora propia por día, por fecha, cambio diferido, hábito apagado,
  pausado o sin desbloquear), el horario usado es el mismo que muestra Planificar para esa fecha.
- **SC-005**: Pedir los huecos 100 veces no crea ni cambia ningún dato: ni horarios, ni cupo de cambios, ni puntos, ni
  racha, ni semáforo.
- **SC-006**: El 100 % de los huecos y de las sugerencias trae su explicación con los ítems vecinos.
- **SC-007**: Con menos de 3 días con plan no se muestra ningún hueco que se repite, y cada uno visible dice en cuántos
  días se apoya (100 %).
- **SC-008**: En una semana con registros, las horas reales de la recapitulación coinciden en el 100 % con las que
  quedaron registradas, y «Sin registro» no cuenta como falta en ninguna cifra.
- **SC-009**: Los intentos de leer los huecos de otra persona sin permiso (otro aprendiz, mentor de otro grupo, Soporte
  TI, cuenta suspendida) se rechazan el 100 % de las veces.
- **SC-010** *(historia 3)*: ninguna sugerencia cae sobre un ítem ni fuera de la ventana, y ninguna se aplica sin que la
  persona la confirme (100 %).
- **SC-011** *(FR-027)*: el 100 % de las lecturas de huecos del líder, del administrador y del alquimista que se
  entregan dejan una constancia; ninguna lectura que falla ni ningún rechazo la deja; y una persona que el líder abrió
  por la semana y por los huecos el mismo día aparece una sola vez en el resumen del mentor.

## Assumptions

- **Autorización de los tabs** (constitución VI): esta spec no agrega ninguna pantalla ni toca un tab. Si el dueño elige
  mostrar los huecos (pregunta 3), qué pantalla y en qué tab es una autorización aparte, que se anota en `AGENTS.md`
  con «lo que cambia y nada más».
- **Duración por defecto** (propuesta, hoy ningún hábito la tiene): 30 minutos para un hábito o una acción, y 60 para un
  evento sin duración. El dueño las puede cambiar sin tocar el comportamiento.
- **Hueco mínimo útil**: 30 minutos (propuesta).
- **Ventana del día**: de la hora de Despertar a la de Dormir, tal como están hoy en los hábitos del catálogo (`WAKE_UP`
  y `SLEEP`); si faltan o no son coherentes, el día entero.
- **Semana**: de lunes a domingo, como las semanas del programa (D-203) y la fila de días de Planificar. El semáforo
  cuenta de sábado a viernes y no se mezcla con esto.
- **Un hueco «se repite»** cuando aparece en 3 o más días de la semana (propuesta, el mismo mínimo que usa la spec 012
  para sus patrones).
- **Hábitos fuera del programa**: los hábitos del programa existen solo del día 0 al 90; los hábitos personales de la
  persona se tratan igual que los del catálogo.
- **Eventos**: ocupa todo evento al que la persona no dijo «No voy», como «Mi agenda».
- **Zona**: la vigente de la persona; no se guarda historia de zonas.
- **Se reutiliza, sin tablas nuevas**: el horario de los hábitos que ya resuelve el servidor, las acciones del día, los
  eventos, las horas ocupadas que guarda SER, los registros de hábitos y el Radar, y la constancia de la consulta
  (`consultas_de_ficha`, FR-027). La única tabla posible es la foto semanal de la pregunta 2, opción B.
- **El cálculo lo hace el servidor**, para que la app, SER y cualquier otro consumidor vean lo mismo (constitución I: el
  servidor decide, la app no recalcula).
- **Dependencias**: la spec 008 (hora por día de la semana) y la 010 (acciones por día) ya están; la 012 (hora prevista
  del Radar) y la 014 (Training por hora, que ordena el día por hora en la app) se están trabajando y comparten datos:
  hay que coordinar para no tener dos reglas del orden del día.
- Se prueba en local, con base clonada y build debug; nunca contra producción. El backend se publica primero; solo hace
  falta un APK nuevo si el dueño decide mostrar los huecos en la app.
