# Feature Specification: Mi Radar automático y personalizado

**Feature Branch**: `019-radar-automatico` (backend y app)
**Created**: 2026-10-30
**Status**: Implementada, probada en el emulador (ver `tasks.md`; el flujo 16 del 403 está escrito y falta correrlo)
**Input**: Diseño final aprobado por el dueño (2026-10-30) sobre la vista previa
https://claude.ai/artifact/8GyvvTWsdqY4uTKx37QtV7 (copia: `vista-previa.html`). Modelo visual: el editor de un hábito en
Planificar. «Aplicar ese rediseño, nada más.»

Cambia lo que dejó la spec 012 (Mi Radar en Yo): ya no se eligen horas a mano. **Alcance: Mi Radar desde el día 8**
(pantalla, regla de horas, modo y veces, validación de hoy) y lo justo para que la mitad del Radar en Hoy (spec 016) y las
fichas del mentor, el líder y Administración muestren lo nuevo. No se toca Training, Plan, los días 1 a 7, los hábitos ni
otras pantallas.

## Clarifications

### Decisiones del dueño (2026-10-30)

- **Los días 1 a 7 no cambian** (08:00 a 19:00, obligatorio).
- **Desde el día 8** el interruptor «Encendido» sigue opcional, con la invitación del día 8 de hoy. Debajo, un **modo**
  (`ControlSegmentado`, sin animación al cambiar):
  - **Automático** (por defecto): pregunta cada hora en punto `h` con Despertar ≤ `h:00` < Dormir, recortada a 03:00–23:00,
    día por día, con el Despertar y el Dormir que la persona programó en sus hábitos (con la hora propia de cada día de la
    spec 008). 05:30 y 22:30 dan 17 veces (06 a 22); 08:00 y 23:00, 15. **Sin el tope de 12.**
  - **Personalizado**: la persona elige **cuántas veces por día** («Veces al día», − / +, botones de 48), de 1 a las horas
    que caben en el horario de ese día. Las horas **se reparten parejo** entre esas horas en punto: el índice es
    `round(i·(n_slots−1)/(n−1))`. **No se muestran las horas**, solo la cifra. Con días marcados, − / + cambia esos días;
    sin días marcados, todos los días que vienen. Con veces distintas entre los marcados, la cifra dice «—».
- **Un día sin hora de Despertar o de Dormir** (sin hora, apagado, pausado, o Dormir ≤ Despertar) **no tiene Mi Radar**,
  automáticamente y en los dos modos. No se inventa un horario por defecto.
- **Hoy y lo pasado no se editan**: candado, como en Planificar (`esPlanificable`). El servidor también lo rechaza con 409.
- **Pantalla Yo → Mi Radar**, igual que el editor de un hábito: cabecera «‹ Mi Radar» con «Ahora: 05:30 a 22:30», sin ✕;
  «Encendido» y el modo; «Hora» (el horario, solo lectura, con la estética de la rueda); «Veces al día» en Personalizado;
  la fila de siete días de Planificar con **solo cuántas veces pregunta ese día** (la cifra en dorado; candado en hoy y lo
  pasado; «—» en un día sin Radar; el lector de pantalla dice «Sábado 31, 15 veces»); «Todos los días» / «Solo sáb, dom»;
  «Sin Radar estos días» al marcar días; «Guardar · sáb, dom» fijo abajo.
- **Sin «Cómo suena»**: el Radar siempre suena, una vez por hora, como hoy. Sin repeticiones nuevas.
- **Se quitan** los textos de explicación («Lo ves tú, tu mentor…», «Aquí verás tus registros de hoy», «De 03:00 a 23:00,
  hasta 12 horas por día», «Toca un día para…»), la elección de horas a mano y la fila cortada bajo el botón flotante de
  SER (queda solo su relleno).
- **Yo → Mi Radar ya no muestra las respuestas de la persona** (ni los registros de hoy, ni «Historial», ni «Patrones»):
  eso lo ven su mentor y su líder en la ficha. En su lugar, **una sola línea «Hoy: 4 de 6»** (las veces que respondió hoy de
  las que le tocaban), en 16 px con cifras tabulares y sin colores de estado; el lector de pantalla dice «Hoy respondiste 4
  de 6 veces». Solo con Mi Radar encendido y un día con Radar (no apagado, no «Sin Radar» hoy, no los días 1 a 7, no con 0
  que tocan). Sale de lo que la app ya lee: las horas de hoy de `GET /radar/mi-radar` y las respuestas de hoy.
- **El 403 de Mi Radar dice por qué.** El cuerpo trae `codigo` (formato `ApiErrorConCodigo`): `PROGRAMA_NO_ACTIVADO` (el personal sin
  Día 1, con o sin fila de programa), `CUENTA_SUSPENDIDA` o `SIN_PERMISO`. En la app, Yo → Mi Radar: sin programa, una línea «Mi Radar
  empieza en tu día 8. Primero elige tu Día 1.» y el botón «Elegir mi Día 1» (el de siempre del programa; al elegir vuelve a leer y dice
  cuándo empieza); suspendida o sin permiso, una línea con el motivo y nada más; sin red o 5xx, «No pudimos cargar Mi Radar.» con
  «Reintentar». El personal sin programa **ve la fila «Mi Radar»** en Yo («Primero elige tu Día 1»). Nunca se ve el motivo anterior ni una
  pantalla vacía mientras se vuelve a leer. Con un backend anterior, un 403 **sin** `codigo` (o con uno que la app no conoce) se lee como siempre: «no hay
  Mi Radar», sin fila y sin cambios. La fila del personal sin programa aparece **solo cuando el servidor confirmó** `PROGRAMA_NO_ACTIVADO`.
- **Mi Radar y la mitad del Radar de Hoy son para todos los roles que hacen los 90 días** (aprendiz, guía, mentor, líder,
  administrador y alquimista con su programa propio): ninguna pieza filtra por rol; la puerta es tener el programa activado.
- **Un día sin Despertar (o sin Dormir)**: en lugar de «Hora», una tarjeta de una línea («Falta tu hora de Despertar») y,
  fijo abajo, «Programar mi Despertar», que lleva a Training → Planificar.
- **Hoy (mitad del Radar) y las fichas** muestran lo que toca: «encendido · 05:30 a 22:30 · 17 veces» o «Hoy no tienes
  Mi Radar».
- **Lo elegido a mano en la 012 nunca llegó a producción**: no hay datos que migrar.

### Supuestos del equipo (a confirmar con el dueño)

- **Por defecto al pasar a Personalizado:** `min(6, horas que caben)`.
- **El modo y las veces son de cada día de la semana.** El control de modo cambia los días marcados o, sin marcar, todos los
  que vienen; hoy y lo pasado conservan lo suyo. Así cambiar el modo un viernes no cambia el viernes (FR-009).
- **«Encendido» rige en el acto.** Es el permiso de la persona, no un día: apagarlo corta los avisos de inmediato.
- **El domingo** Planificar muestra la semana siguiente entera. Mi Radar también la muestra, pero el domingo mismo queda
  con candado: es una regla por día de la semana sin fecha, y cambiar «el domingo» cambiaría hoy.
- **«Sin Radar estos días»** (el interruptor de la vista previa) lo elige la persona y vale en los dos modos.
- **Alarmas ante un cambio del Despertar o el Dormir:** se rehacen cuando la app lee Mi Radar (al abrirla y al volver a
  primer plano); no se toca Planificar para avisar al instante.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mi Radar solo, con mi horario (Priority: P1)

Como persona en el día 8 o después, quiero encender Mi Radar y que me pregunte cada hora entre mi Despertar y mi Dormir,
sin elegir horas.

**Independent Test**: con Despertar 05:30 y Dormir 22:30 (día 10), encender Mi Radar: «Ahora: 05:30 a 22:30», cada día dice 17
y el teléfono avisa de 06:00 a 22:00.

**Acceptance Scenarios**:

1. **Given** el día 8 o después, **When** enciendo Mi Radar, **Then** el modo es Automático y veo mi horario en «Hora».
2. **Given** Despertar 06:00 y Dormir 22:00, **When** miro un día, **Then** pregunta de 06 a 21 (16 veces).
3. **Given** Despertar 02:00 y Dormir 23:30, **When** miro un día, **Then** pregunta de 03 a 23 (21 veces).
4. **Given** un día sin Despertar, apagado, en pausa, o con Dormir ≤ Despertar, **When** miro Mi Radar, **Then** ese día va
   con «—», dice «Falta tu hora de Despertar» (o Dormir) y no suena ni se registra.
5. **Given** los días 1 a 7, **When** abro la app, **Then** todo es como antes.

### User Story 2 - Elegir cuántas veces (Priority: P1)

**Acceptance Scenarios**:

1. **Given** Personalizado, **When** miro «Veces al día», **Then** veo la cifra y − / + de 48; nunca menos de 1 ni más de
   las que caben ese día.
2. **Given** 6 veces en un día de 06 a 22, **Then** pregunta a las 06, 09, 12, 16, 19 y 22.
3. **Given** días marcados, **When** toco − / +, **Then** cambian solo esos días; con veces distintas la cifra dice «—».

### User Story 3 - Hoy y lo pasado no se editan (Priority: P1)

**Acceptance Scenarios**:

1. **Given** hoy viernes, **When** miro la fila de días, **Then** lunes a viernes llevan candado y no se pueden marcar.
2. **Given** una petición que cambia hoy o un día pasado, **When** llega al servidor, **Then** responde 409 con «Hoy y los
   días que pasaron no se pueden cambiar».
3. **Given** una petición que repite sin cambios un día con candado, **Then** se acepta (no cambia nada).

### User Story 4 - Que Hoy y las fichas lo digan (Priority: P2)

**Acceptance Scenarios**:

1. **Given** Mi Radar encendido, **When** miro la mitad del Radar de Hoy o la ficha, **Then** dice «encendido · 05:30 a 22:30
   · 17 veces» (la ficha), o «Hoy no tienes Mi Radar» si ese día no tiene.

### Edge Cases

- Despertar o Dormir fuera de 03:00–23:00: el Radar se recorta a ese rango.
- Si cambia el Despertar o el Dormir, las veces guardadas se recortan a lo que cabe (nunca más de las horas del día).
- Registrar a mano fuera de una hora elegida, en un día con Radar y de 03:00 a 23:59, se guarda sin hora prevista (como hoy).
- Sin red al guardar: se avisa y no se pierde lo marcado.
- Android limita a 500 alarmas por app: 7 días × hasta 21 horas = 147, sin riesgo. iOS (64) no aplica: la app es solo Android.

## Requirements *(mandatory)*

- **FR-001**: Días 1 a 7 MUST NOT cambiar.
- **FR-002**: Las horas de un día MUST salir de su Despertar y su Dormir (`habits.api.HorarioDelDiaFinder`: `claveSistema`
  WAKE_UP / SLEEP, `horaDisparo`, apagado, pausado) con la regla de arriba; el servidor las calcula y la app las muestra.
- **FR-003**: Un día sin Despertar o sin Dormir válido MUST NOT tener Radar ni dejar registrar (409).
- **FR-004**: Personalizado MUST repartir las horas con `round(i·(n_slots−1)/(n−1))`, de 1 al máximo que cabe ese día.
- **FR-005**: El servidor MUST rechazar con 409 cualquier cambio a hoy o a un día pasado, y con 400 un modo o unas veces
  fuera de regla.
- **FR-006**: «Encendido» MUST seguir opcional y regir en el acto.
- **FR-007**: La app MUST mostrar solo la cifra de veces por día, sin horas, y MUST NOT ofrecer «Cómo suena».
- **FR-008**: Las alarmas locales MUST ser una por hora y día que toca (`renaser.radar.slot.d{1..7}.HH`) y rehacerse al
  cambiar modo, veces, Despertar o Dormir.
- **FR-009**: Hoy y las fichas MUST mostrar el horario y las veces de hoy.
- **FR-010**: Una app nueva con un backend anterior MUST NOT mostrar la fila «Mi Radar» ni romper los días 1 a 7.
- **FR-011**: El 403 de `GET`/`PUT /radar/mi-radar` (y de `POST /radar`) MUST decir por qué con `codigo` en el cuerpo: `PROGRAMA_NO_ACTIVADO` (el
  personal sin Día 1, con o sin fila de programa), `CUENTA_SUSPENDIDA` o `SIN_PERMISO`. El `message` no cambia. La app MUST leer un 403 sin
  motivo como «no hay Mi Radar».

## Success Criteria

- **SC-001**: 05:30–22:30 da 17; 06:00–22:00, 06–21; 02:00–23:30, 03–23; los días sin Despertar o Dormir, ninguna.
- **SC-002**: 6 de 17 da 06, 09, 12, 16, 19, 22.
- **SC-003**: Hoy y lo pasado se rechazan el 100 % de las veces en el servidor y no se pueden marcar en la app.
- **SC-004**: Los flujos de `.maestro/mi-radar/` y `codigo-renaser/40` pasan en el emulador.
- **SC-005**: Cada motivo del 403 tiene su prueba en `RadarMiRadarIT` (sin Día 1 con y sin fila, suspendida) y en la app (`useConfigDeMiRadar`,
  `miRadarScreen`, `miRadarEnYo`); el flujo `.maestro/mi-radar/16-personal-sin-programa.yaml` pasa en el emulador.
