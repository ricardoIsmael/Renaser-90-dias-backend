# Feature Specification: Recordatorio por día y «Cómo suena» para las acciones diarias de los objetivos

**Feature Branch**: `010-acciones-por-dia` (app; sale de `009-tipos-de-alarma`)
**Created**: 2026-10-20
**Status**: Draft
**Input**: Pedido del dueño (2026-10-20), después de probar la 008 y la 009 en el emulador: «tenemos que probar que esto también funcione con las acciones diarias que son los objetivos». Y aclaró: «me refiero a lo nuevo que estábamos agregando, que también lo tenga».

## Clarifications

### Session 2026-10-20

- **Las acciones diarias reciben lo mismo que los hábitos**: aviso por día (008) y «Cómo suena» con los cuatro interruptores dorados por día (009). Incluye vibrar por defecto (E-641).
- El dueño pidió avanzar sin más preguntas. Lo que no dijo se resuelve con los supuestos de abajo.

### Lo que hay hoy

`objetivos/notificaciones/recordatoriosDeAcciones.ts`, guardado en el teléfono por cuenta:

1. **«Recordarme mis acciones del día»**: una alarma diaria a una hora elegida, encendida o apagada. Es igual todos los días.
2. **«Aviso antes de cada acción con hora»**: sin aviso, 30 min, 10 min o a la hora, con varias opciones a la vez. Es una sola elección para todas las acciones y todos los días. Son alarmas de fecha.

El control (`RecordatorioDeAcciones`) aparece en Plan («Tus acciones») y en Yo → Alarmas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El recordatorio diario y el aviso, por día (Priority: P1)

Como participante, quiero que el recordatorio «Revisa las acciones de hoy» suene a las 07:00 de lunes a viernes y a las 09:00 el fin de semana, apagado el domingo si quiero. También quiero que el aviso antes de cada acción pueda ser distinto según el día.

**Independent Test**: marcar S y D en el recordatorio de acciones, poner 09:00 y guardar. El teléfono programa la alarma del sábado y del domingo a las 09:00, y la de los otros días a las 07:00.

**Acceptance Scenarios**:

1. **Given** el recordatorio diario a las 07:00, **When** marco S y D, elijo 09:00 y guardo, **Then** esos días suena a las 09:00 y los demás a las 07:00.
2. **Given** el aviso general «10 min antes», **When** marco S, elijo «30 min antes» y guardo, **Then** las acciones con hora del sábado avisan 30 min antes y las de los demás días, 10 min antes.
3. **Given** días marcados con avisos distintos, **When** miro la sección, **Then** dice «Estos días tienen avisos distintos» y no hay ninguna opción marcada.
4. **Given** un día que vuelvo a «como los demás días», **When** guardo, **Then** usa otra vez la hora y el aviso generales.
5. **Given** hoy, **When** quiero cambiar el día de hoy, **Then** está con candado, como en los hábitos. Rige desde la próxima vez.

### User Story 2 - Cómo suenan las alarmas de las acciones, por día (Priority: P1)

Como participante, quiero elegir para las alarmas de mis acciones Sonido, Vibración, Voz y Repetir, por día, con los mismos interruptores grandes de los hábitos.

**Independent Test**: el sábado con Sonido apagado: la alarma de una acción del sábado vibra sin sonar.

**Acceptance Scenarios**:

1. **Given** «Cómo suena» de las acciones, **When** lo veo, **Then** tiene las mismas cuatro filas, reglas y medidas que el de los hábitos: 17/15 px, filas de 56 px, «La voz necesita el sonido» y sonido o vibración obligatorio.
2. **Given** Voz encendida, **When** suena, **Then** dice «Tienes acciones de tus objetivos por hacer», la voz de objetivos que ya existe.
3. **Given** Repetir encendido, **When** no toco la alarma de una acción, **Then** suena otra vez a los 5 y a los 10 minutos. Si la toco o cumplo la acción, no.
4. **Given** días con combinaciones distintas, **When** los marco, **Then** el interruptor que difiere muestra «Varía».

### Edge Cases

- **Acción sin hora**: solo la cubre el recordatorio diario del día.
- **Acción movida o cumplida**: sus alarmas y repeticiones se quitan cuando la app lee la lista, como hoy.
- **Web**: no hay alarmas locales y no se muestra.
- **Cerrar sesión**: se borra todo, también lo nuevo.
- **Teléfono que borró las alarmas (007)**: al abrir la app vuelven, con su combinación.
- **Actualización desde la versión anterior**: las preferencias de hoy (hora, encendido, aviso) pasan a ser la configuración «general» y nada cambia hasta que la persona marque días. Excepción: vibrar por defecto (E-641) también vale para objetivos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El recordatorio diario de acciones MUST poder tener hora y encendido por día de la semana, con «general» para los días sin hora propia.
- **FR-002**: El aviso antes de cada acción con hora MUST poder elegirse por día de la semana, con las mismas opciones y la misma regla de herencia que en los hábitos (008).
- **FR-003**: «Cómo suena» MUST existir para las alarmas de acciones (diario y aviso de cada acción), por día, con las cuatro filas y las reglas de la 009.
- **FR-004**: La alarma MUST sonar, vibrar, usar la voz de objetivos y repetir según la combinación de su día, y ser tipo despertador con permiso (007).
- **FR-005**: El control MUST usar los mismos componentes que los hábitos (fila de días con campana y reloj, `InterruptorGrande`, «Cómo suena»), en Plan («Tus acciones») y en Yo → Alarmas, con la misma UX para mayores de 40.
- **FR-006**: Las preferencias de hoy MUST migrarse como configuración general, sin cambiar lo que suena hasta que la persona marque días.
- **FR-007**: Todo MUST seguir guardándose en el teléfono, por cuenta, como hoy. Esta spec no agrega servidor.

### Key Entities

- **Preferencias de acciones**: general (diario encendido, hora, antelaciones, combinación) y, por día de la semana, hora, encendido, antelaciones y combinación, cada una opcional.

## Success Criteria *(mandatory)*

- **SC-001**: En el emulador, el 100 % de las alarmas de acciones suenan a la hora y con la combinación de su día.
- **SC-002**: Repetir: 0 repeticiones después de tocar la alarma o de cumplir la acción.
- **SC-003**: Al actualizar sin tocar nada, las alarmas de acciones suenan a la misma hora que antes y ahora vibran.

## Assumptions

- Va solo en la app, guardado en el teléfono como hoy. Si el dueño quiere que valga en todos los teléfonos, va en otra spec con servidor.
- Se reutilizan la regla de días, la herencia y los componentes de la 008 y la 009.
- La voz de las acciones es la genérica de objetivos que ya existe; no se graban voces nuevas.
- Sin APK por esta spec. El APK nuevo va por las voces de los hábitos de la 009.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.
