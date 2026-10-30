# Feature Specification: Hoy con hábito y Radar, y Plan con las acciones del día

**Feature Branch**: `016-hoy-y-plan` (app; se apoya en las specs 012 y 014)
**Created**: 2026-10-29
**Status**: Aprobada por el dueño (vista previa `vista-previa.html`, opciones Hoy A y Plan B)
**Input**: Pedido del dueño (2026-10-29), sobre la vista previa https://claude.ai/artifact/DsJoJzwQqv9aEttzc4WMAV:
«así bro, pero más centrado, para que sea CX/UX» (Hoy, opción A) y «el de acá, el B, creo que me agrada más; procede y
la spec» (Plan, opción B).

## Clarifications

### Session 2026-10-29 (decisiones del dueño)

- **Hoy, opción A**: la tarjeta «Hábitos de hoy» se parte en dos mitades lado a lado, el hábito y el Radar, con el
  contenido **más centrado** en cada mitad.
- **Días 1 a 7**: tocar el Radar dice si ya completaste la hora; si no, salen las preguntas.
- **Desde el día 8**, el Radar sirve para planificar si está apagado y para responder si está encendido.
- **Plan, opción B**: en lugar del arco «Día 20 de 90», la tarjeta «Tus acciones de hoy» por eje (Negocio, Cuerpo y
  Relaciones), con su avance y la acción que sigue. Tocar un eje abre sus acciones de hoy, y se registra en Training.
- **Esto cambia la spec 012**: allí decía «desde el día 8, Hoy no muestra nada del Radar» (FR-006, SC-004). El dueño lo
  cambia: desde el día 8, Hoy muestra la media tarjeta del Radar. Se corrige en la 012 al fusionar.

### Lo que hay hoy (verificado el 2026-10-29)

- **Hoy**:
  - en los días 1 a 7, la tarjeta del Código Renaser (`TarjetaCodigoRenaser`) va aparte, y el formulario se abre solo y
    es obligatorio para el aprendiz;
  - «Hábitos de hoy» es una tarjeta con la cifra, el hábito que toca y lo que queda.
- **Plan**:
  - arriba, el arco del día (`ArcoDelDia`);
  - las acciones del día viven en `TarjetaAccionesDelDia`, por eje, con hoy y mañana;
  - las rocas diarias tienen hora opcional, estado y la regla de Pareto (la verde va primero).
- **Training** (spec 014) ya muestra y registra las acciones del día en su lista por hora.
- **Mi Radar** (spec 012): configuración por día, horas elegidas, hora prevista y la regla de un registro por hora.
  > **Corregido 2026-10-30 (spec 019).** Ya no hay «horas elegidas»: Mi Radar es Automático (cada hora entre el Despertar y el Dormir, sin
  > tope de 12) o Personalizado (cuántas veces por día, repartidas parejo). La mitad del Radar de Hoy sigue la misma regla y dice, por
  > ejemplo, «Encendido · 05:30 a 22:30 · 17 veces» o «Hoy no tienes Mi Radar».

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Hoy: el hábito y el Radar en una tarjeta (Priority: P1)

1. **Given** Hoy, **When** miro «Hábitos de hoy», **Then** veo dos mitades lado a lado, con el contenido centrado y la
   misma altura:
   - la izquierda, «Hábitos de hoy», con la cifra, el hábito que toca y lo que queda;
   - la derecha, el Radar.
   Tocar la izquierda abre Training.
2. **Given** los días 1 a 7 con la hora pendiente, **When** toco el Radar, **Then** se abren las cinco preguntas de
   siempre. La mitad dice la hora, «Innegociable» y cuándo cierra.
3. **Given** los días 1 a 7 con la hora registrada, **When** toco el Radar, **Then** veo lo que respondí y cuándo es el
   siguiente («Registrado»).
4. **Given** el día 8 o después con Mi Radar apagado, **When** toco el Radar, **Then** dice «Planifica tu Radar» y me
   lleva a Yo → Mi Radar.
5. **Given** el día 8 o después, encendido y con una hora elegida pendiente, **When** toco el Radar, **Then** respondo
   ahí mismo, con el formulario de siempre, que se puede cerrar.
6. **Given** el día 8 o después, encendido y sin hora pendiente ahora, **When** toco el Radar, **Then** se abre «Tu
   Radar de hoy»: las horas de hoy con su estado, «Registrar ahora» si se puede y «Cambiar mis horas» (Yo → Mi Radar).
7. **Given** un día sin Radar (spec 012, por día), **When** miro la mitad, **Then** dice «Hoy no tienes Mi Radar» y
   lleva a Yo → Mi Radar.
8. **Given** la letra grande del sistema, **When** miro la tarjeta, **Then** las mitades pasan una debajo de la otra y
   nada se corta.

### User Story 2 - Plan: las acciones de hoy por eje (Priority: P1)

1. **Given** Plan, **When** lo abro, **Then** ya no está el arco. En su lugar, «Tus acciones de hoy», con «Jueves 29 ·
   día 20 de 90» y «2 de 6», y una fila por eje con acciones hoy (Negocio `briefcase`, Cuerpo `activity`, Relaciones
   `heartHandshake`).
2. **Given** una fila, **When** la miro, **Then** dice «n de m», un símbolo por acción (✓ cumplida, círculo pendiente,
   reloj «se te pasó la hora», candado si espera a la verde por Pareto) y «Sigue: X · 16:00» o «Se te pasó: X».
3. **Given** que toco un eje, **When** se abre la hoja, **Then** veo sus acciones de hoy con su hora y su estado, y
   «Registrar en Training» me lleva a Training, a esa acción.
4. **Given** hoy sin acciones, **When** abro Plan, **Then** la tarjeta dice cómo agendarlas, sin inventar nada.
5. **Given** el resto de Plan, **When** lo miro, **Then** Fase actual, Prioridades clave y Arquitectura de tiempo
   quedan igual.

### Edge Cases

- **Programa sin Día 1**: la mitad del Radar no aparece. La tarjeta es la de hoy.
- **Personal que hace su programa**: igual que el aprendiz, pero el formulario se puede cerrar, como hoy.
- **Backend sin Mi Radar (sin la 012)**: desde el día 8 la tarjeta queda solo con los hábitos.
- **Cambio de hora o de día con Hoy abierto**: la mitad del Radar pasa a la hora o al día nuevo, como hoy.
- **El botón de SER**: no tapa la mitad derecha ni su chevron.
- **Web**: igual que en el teléfono, sin avisos.

## Requirements *(mandatory)*

- **FR-001**: Hoy MUST mostrar «Hábitos de hoy» partida en dos mitades iguales, con el contenido centrado, y apilarlas
  con la letra grande.
- **FR-002** *(corregido 2026-10-30: las horas de Mi Radar salen del Despertar y el Dormir, spec 019)*: La mitad del Radar MUST seguir las reglas de la spec 012 y las de la US1: días 1 a 7, apagado, encendido
  con hora pendiente, encendido sin hora y día sin Radar. Desde Hoy MUST NOT registrarse nada que la spec 012 rechace.
- **FR-003**: En los días 1 a 7, la tarjeta aparte del Código Renaser MUST NOT duplicarse con la mitad. El formulario
  sigue abriéndose solo y obligatorio, como hoy.
- **FR-004**: Plan MUST quitar el arco y mostrar «Tus acciones de hoy» por eje, con avance, símbolos con forma y la que
  sigue, y una hoja por eje que lleva a Training.
- **FR-005**: El registro de acciones MUST seguir siendo en Training (spec 014). Plan solo muestra.
- **FR-006**: Mismas reglas de §2 a §6 de `AGENTS.md`: un solo scroll, áreas de 48, tipo oración, claro y oscuro,
  retroceso del sistema y tuteo.
- **FR-007**: Sin pedidos nuevos al servidor que se repitan: se reutilizan las lecturas de Hoy, Plan y Mi Radar que ya
  existen.

## Success Criteria *(mandatory)*

- **SC-001**: En el emulador, los 5 casos de la US1 hacen lo que dice la tabla, a 390 y a 360 dp, en claro y oscuro y
  con letra grande.
- **SC-002**: Plan sin el arco muestra «n de m» igual al conteo real de acciones de hoy, el 100 % de las veces.
- **SC-003**: Ninguna pantalla nueva hace un pedido al servidor que no hacían ya Hoy y Plan.

## Assumptions

- **Autorización de los tabs**: el pedido del dueño del 2026-10-29 autoriza estos cambios en Hoy y Plan. Se anota en
  `AGENTS.md` (o en `docs/producto/` si el #48 ya está en master).
- Se construye sobre las specs 012 y 014. Va después de que estén en sus ramas con commit.
- Hace falta un APK nuevo, sin backend nuevo.
- Todo se prueba en local, con base clonada y build debug; nunca contra producción.
