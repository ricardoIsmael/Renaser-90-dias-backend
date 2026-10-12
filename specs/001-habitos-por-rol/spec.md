# Feature Specification: Hábitos globales por rol

**Feature Branch**: `001-habitos-por-rol` (sin rama propia todavía: spec-kit no crea ramas en este repo)

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "Hábitos globales por rol: hoy los hábitos del catálogo global llegan a
todo el que hace el programa sin importar su rol. Un ADMIN o ALQUIMISTA debe poder marcar, en cada
hábito global, a qué roles aplica, de forma escalable a roles futuros. CRUD completo del catálogo en
la app (Administración → Más opciones → «Hábitos del programa»)."

**Repos**: backend (`Renaser-90-dias-backend`, esta spec) y app (`Renaser-90-dias-frontend-`). La
spec vive solo acá; el frontend la referencia desde `specs/README` sin copiarla.

## Contexto

Hoy hay dos clases de hábitos:

- **Globales** (catálogo del programa): los define Administración y le llegan a **toda** persona que
  hace el programa, sea aprendiz o personal (mentor, líder de mentores, admin, alquimista: el
  personal también puede hacer su programa de 90 días).
- **Personales**: los crea cada persona para sí misma.

El dueño quiere que un hábito global pueda ser **solo para ciertos roles** (por ejemplo, uno solo
para mentores), y que mañana, si aparece un rol nuevo, se pueda usar sin rehacer esta función.

## Clarifications

### Session 2026-10-12

Respuestas del dueño del producto, en la conversación de diseño:

- Q: ¿Dónde vive la especificación? → A: En el backend, una sola spec para los dos repos.
- Q: Un hábito global sin ningún rol marcado, ¿qué significa? → A: Es para todos. Los hábitos de
  hoy siguen igual sin migrar datos.
- Q: ¿Dónde se administran los roles? → A: Backend + una pantalla en la app, en Administración.
- Q: ¿Los hábitos protegidos y los de regla propia (Clase diaria, Pastilla Renacer, Audioterapia,
  post en Comunidad, Despertar/Dormir) se pueden restringir? → A: Sí, cualquiera.
- Q: ¿Desde cuándo rige un cambio de roles (del hábito o de la persona)? → A: Desde el día siguiente
  en la zona de la persona; el registro de hoy se respeta.
- Q: ¿Qué pasa con el historial y la configuración cuando un hábito deja de aplicar? → A: Se conserva
  todo; si vuelve a aplicar, se recupera tal cual.
- Q: ¿Qué hace la pantalla de la app? → A: CRUD completo del catálogo global + elegir roles.
- Q: ¿Cambian los hábitos personales? → A: No.

### Session 2026-10-12 (clarify)

- Q: Si cambia la audiencia o el rol y el registro de hoy de una persona todavía no se generó, ¿hoy
  aplica la regla vieja o la nueva? → A: La vieja. "Desde mañana" es estricto: el cambio rige desde
  el día siguiente, en la zona de cada persona, al momento del cambio, sin importar si ya abrió la
  app. El sistema guarda desde cuándo rige cada cambio de audiencia y de rol.
- Q: ¿Cómo se elige "para todos" en la pantalla y qué pasa si se marcan los cinco roles? → A: Un
  interruptor «Para todos los roles» (encendido por defecto); al apagarlo se eligen roles (al menos
  uno). Marcar los cinco se guarda como cinco roles: un rol futuro no lo recibe.
- Q: Un hábito nuevo creado hoy con roles, ¿desde cuándo aplica su audiencia? → A: Desde que se
  crea (como hoy un hábito nuevo); solo los cambios posteriores rigen desde el día siguiente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Restringir un hábito global a ciertos roles (Priority: P1)

Un ADMIN o ALQUIMISTA elige un hábito del catálogo y marca a qué roles aplica (por ejemplo, solo
MENTOR y LÍDER DE MENTORES). Desde el día siguiente, solo las personas con esos roles que hacen el
programa lo reciben; para el resto deja de aparecer, sin perder lo que ya habían hecho.

**Why this priority**: Es el núcleo del pedido. Sin esto no hay "hábitos por rol".

**Independent Test**: Con el endpoint de administración (sin la pantalla), marcar un hábito como
"solo MENTOR"; verificar que al día siguiente un aprendiz no lo recibe y un mentor que hace el
programa sí, y que el registro que el aprendiz tenía hoy sigue completable.

**Acceptance Scenarios**:

1. **Given** un hábito global sin roles (para todos) y un aprendiz que lo recibe hoy, **When** el
   admin lo restringe a MENTOR, **Then** el aprendiz conserva y puede completar el registro de hoy,
   y desde su día siguiente ya no se le genera ni aparece en su catálogo.
2. **Given** un hábito restringido a MENTOR, **When** un mentor con su programa activo empieza su
   día, **Then** se le genera el registro como a cualquier hábito global.
3. **Given** un hábito restringido, **When** el admin le quita todos los roles, **Then** desde el
   día siguiente vuelve a ser para todos.
4. **Given** un aprendiz que tenía el hábito con hora propia, días elegidos y pausa, **When** el
   hábito deja de aplicarle y luego vuelve a aplicarle, **Then** recupera su hora, sus días, su
   pausa y su racha sin hacer nada.
5. **Given** un usuario que no es ADMIN ni ALQUIMISTA (aprendiz, mentor, líder) o una cuenta
   suspendida, **When** intenta cambiar los roles de un hábito, **Then** el servidor lo rechaza.

---

### User Story 2 - Cada persona ve solo los hábitos de su rol (Priority: P1)

Una persona que hace el programa ve, en todas las vistas donde aparecen hábitos globales (Hoy,
Training, Plan, catálogo para elegir, alarmas, agenda), solo los que aplican a su rol, más sus
personales. Quien la acompaña (mentor, administración) ve lo mismo que ella.

**Why this priority**: Es la otra mitad del núcleo: si un hábito se filtra en un lugar y no en
otro, la persona ve uno que no puede completar o pierde puntos por uno que no le corresponde.

**Independent Test**: Con un hábito "solo MENTOR", comparar lo que ve un aprendiz en cada vista con
lo que ve un mentor que hace el programa; el aprendiz no lo ve en ninguna, el mentor en todas.

**Acceptance Scenarios**:

1. **Given** un hábito "solo MENTOR", **When** un aprendiz abre la lista de hábitos para elegir o
   planificar, **Then** no aparece.
2. **Given** el mismo hábito, **When** el mentor del aprendiz o administración revisan los hábitos
   del aprendiz, **Then** tampoco aparece en los días en que no le aplicaba.
3. **Given** un hábito que no aplica a una persona, **When** se calculan su coherencia, su semáforo,
   sus puntos posibles y "todos los hábitos del día", **Then** ese hábito no cuenta.
4. **Given** un hábito con regla propia (por ejemplo, Pastilla Renacer) restringido a otros roles,
   **When** la persona intenta su flujo propio, **Then** el sistema responde que ese hábito no le
   aplica, sin error interno y sin dar puntos.
5. **Given** los hábitos personales de una persona, **When** cambia el rol de cualquier hábito
   global, **Then** sus personales siguen idénticos.

---

### User Story 3 - Administrar el catálogo global desde la app (Priority: P2)

Un ADMIN o ALQUIMISTA entra a Administración → Más opciones → «Hábitos del programa» y puede:
ver el catálogo completo (activos e inactivos) con filtro por rol, crear un hábito global, editarlo,
activarlo o desactivarlo, eliminarlo si nunca se usó, y elegir a qué roles aplica.

**Why this priority**: Hoy esto no existe en la app ("en la web"). Sin la pantalla, la función de
P1 solo se usa llamando al servidor a mano.

**Independent Test**: Con una cuenta ADMIN en la app, crear un hábito "solo ALQUIMISTA", verlo en
la lista filtrada por ALQUIMISTA, editarlo, desactivarlo, reactivarlo y eliminarlo (sin uso).

**Acceptance Scenarios**:

1. **Given** una cuenta ADMIN o ALQUIMISTA, **When** abre Más opciones, **Then** ve la entrada
   «Hábitos del programa»; cualquier otro rol no la ve.
2. **Given** la lista, **When** elige el filtro "Mentor", **Then** ve los hábitos que aplican a
   mentores: los marcados para MENTOR y los que son para todos, distinguidos con un rótulo.
3. **Given** el formulario de crear, **When** deja un campo obligatorio vacío, **Then** se le indica
   cuál falta antes de enviar.
4. **Given** un hábito con registros en el historial, **When** intenta eliminarlo, **Then** el
   sistema no lo borra y le ofrece desactivarlo, explicando por qué.
5. **Given** cualquier vista del flujo, **When** usa el gesto atrás del sistema, **Then** vuelve un
   nivel (cierra la hoja o vuelve a la lista), nunca sale de la app.
6. **Given** un cambio guardado, **When** el servidor confirma, **Then** se muestra una confirmación
   en línea y la lista refleja el cambio, aclarando que rige desde mañana.

---

### User Story 4 - Cambio de rol de una persona (Priority: P2)

Cuando administración le cambia el rol a alguien que hace el programa (por ejemplo, de APRENDIZ a
MENTOR), sus hábitos globales pasan a ser los de su nuevo rol desde su día siguiente.

**Why this priority**: Es la consecuencia natural del filtro por rol; si no se cubre, la persona
quedaría con hábitos del rol anterior o sin los del nuevo.

**Independent Test**: Con un hábito "solo APRENDIZ" y otro "solo MENTOR", cambiar el rol de una
persona y verificar qué recibe hoy y mañana.

**Acceptance Scenarios**:

1. **Given** una persona con rol APRENDIZ y un registro de hoy de un hábito "solo APRENDIZ",
   **When** su rol cambia a MENTOR, **Then** ese registro de hoy se respeta y desde mañana recibe
   los de MENTOR y deja de recibir los de APRENDIZ.
2. **Given** el mismo cambio, **When** se consultan sus registros pasados, **Then** siguen ahí con
   sus puntos.

---

### User Story 5 - Un rol nuevo funciona sin rehacer la función (Priority: P3)

Si el producto agrega un rol nuevo, ese rol aparece automáticamente como opción al elegir roles de
un hábito y el filtro lo respeta, sin cambiar el módulo de hábitos ni publicar una app nueva.

**Why this priority**: El dueño lo marcó como crítico para el diseño, pero no hay rol nuevo hoy:
se valida con pruebas, no con un caso real.

**Independent Test**: En un entorno de prueba, agregar un rol ficticio al catálogo de roles y
verificar que la lista de roles elegibles lo incluye y que el filtro funciona con él.

**Acceptance Scenarios**:

1. **Given** un rol nuevo agregado al sistema, **When** el admin abre el selector de roles,
   **Then** el rol aparece con el nombre que define el servidor.
2. **Given** una app publicada antes del rol nuevo, **When** el servidor lo devuelve, **Then** la
   app lo muestra sin romperse.

### Edge Cases

- **Hábito restringido a un rol que nadie tiene hoy**: válido; no se genera para nadie.
- **Hábito marcado con los cinco roles actuales**: se guarda como cinco roles; equivale a "para
  todos hoy", pero NO incluye un rol futuro. La lista lo muestra con sus roles, no como «Para todos».
- **Cambio de roles varias veces el mismo día**: rige la última versión guardada ese día, desde el
  día siguiente de cada persona.
- **Persona que no abrió la app el día del cambio**: igual que quien sí la abrió; su día de hoy
  usa la audiencia de antes del cambio.
- **Rol de una persona sin historial previo** (antes del despliegue): su rol actual rige desde
  siempre.
- **Persona en otra zona horaria**: "mañana" es su día siguiente, no el del servidor.
- **Hábito desactivado y restringido**: desactivar gana; no se genera para nadie.
- **Personal que no hace el programa**: no recibe hábitos, con o sin rol marcado (sin cambio).
- **Hábito eliminado o desactivado mientras alguien lo tiene en su plan**: comportamiento de hoy,
  sin cambio.
- **Rol inexistente en el pedido**: el servidor lo rechaza con un mensaje claro.
- **App publicada que no conoce los roles**: sigue funcionando; solo ve lo que el servidor ya le
  filtró.
- **Alarmas locales ya programadas** de un hábito que deja de aplicar: se dejan de programar desde
  la siguiente sincronización.
- **Hábito con regla propia restringido**: sus endpoints propios responden "no aplica" a quien no
  le corresponde ese día.

## Requirements *(mandatory)*

### Functional Requirements

**Audiencia de un hábito global**

- **FR-001**: Cada hábito global MUST poder tener un conjunto de roles destinatarios (cero o más).
- **FR-002**: Un hábito global sin roles MUST aplicar a todas las personas que hacen el programa,
  cualquiera sea su rol, incluidos roles futuros.
- **FR-003**: Un hábito global con roles MUST aplicar solo a las personas cuyo rol esté en ese
  conjunto.
- **FR-004**: Los roles elegibles MUST ser exactamente los roles que existen en el sistema; agregar
  un rol nuevo MUST hacerlo elegible y filtrable sin cambiar las reglas de hábitos.
- **FR-005**: Cualquier hábito global, incluidos los protegidos y los de regla propia, MUST poder
  restringirse.
- **FR-006**: Los hábitos personales MUST NOT verse afectados.

**Vigencia e historial**

- **FR-007**: Un cambio de roles de un hábito, o del rol de una persona, MUST regir desde el día
  siguiente en la zona horaria de cada persona; el registro de hoy ya generado MUST conservarse y
  poder completarse.
- **FR-008**: Cuando un hábito deja de aplicar a alguien, el sistema MUST conservar sus registros,
  puntos y rachas pasados, y su configuración (hora, días, pausa, nombre propio).
- **FR-009**: Cuando un hábito vuelve a aplicar, la persona MUST recuperar su configuración tal cual.
- **FR-010**: El sistema MUST registrar quién cambió los roles de un hábito y cuándo, y conservar
  las versiones anteriores de la audiencia con el instante desde el que rigen.
- **FR-010a**: El sistema MUST conservar desde cuándo rige el rol de cada persona que hace el
  programa, para decidir qué audiencia le aplica en cada uno de sus días.
- **FR-010c**: La audiencia con la que se crea un hábito MUST regir desde su creación; solo los
  cambios posteriores esperan al día siguiente.
- **FR-010b**: Para el día local D de una persona, el sistema MUST usar la audiencia y el rol
  vigentes al cierre de su día D−1 (en su zona), aunque el registro de D se genere más tarde.

**Dónde se aplica el filtro**

- **FR-011**: La generación diaria de registros MUST considerar solo los hábitos globales que
  aplican al rol de la persona ese día.
- **FR-012**: El catálogo que ve una persona, sus preferencias de horario, la agenda, las alarmas y
  toda lista de hábitos para elegir MUST mostrar solo los que le aplican.
- **FR-013**: Las vistas de acompañamiento (mentor, líder, administración) de los hábitos de una
  persona MUST mostrar lo mismo que ve esa persona.
- **FR-014**: Coherencia, semáforo, puntos posibles, "todos los hábitos del día" y celebraciones
  MUST contar solo los hábitos que aplican (consecuencia de FR-011).
- **FR-015**: Los flujos propios de un hábito (Clase diaria, Pastilla Renacer, Audioterapia, post en
  Comunidad, Despertar/Dormir) MUST responder "no aplica" a quien no le corresponde, sin dar puntos.

**Administración del catálogo**

- **FR-016**: Solo ADMIN y ALQUIMISTA MUST poder listar, crear, editar, activar, desactivar,
  eliminar y cambiar los roles de los hábitos globales; cualquier otro rol o cuenta suspendida MUST
  ser rechazado.
- **FR-017**: La creación y la edición MUST aceptar los roles junto con los campos que el catálogo
  ya acepta hoy (título al crear, descripción, tipo, categoría, exigencia de evidencia, opcional,
  obligatorio en intoxicación).
- **FR-018**: El listado de administración MUST incluir activos e inactivos, con sus roles, y MUST
  poder filtrarse por rol (un rol incluye los hábitos "para todos").
- **FR-019**: El sistema MUST ofrecer la lista de roles elegibles con su nombre para mostrar.
- **FR-020**: Eliminar un hábito con historial MUST rechazarse con un motivo claro que sugiera
  desactivarlo; eliminar uno sin historial MUST funcionar.
- **FR-021**: Un rol desconocido en un pedido MUST rechazarse sin guardar nada.

**Pantalla en la app**

- **FR-022**: La app MUST ofrecer «Hábitos del programa» en Administración → Más opciones, visible
  solo para quien puede administrar el catálogo.
- **FR-022a**: La audiencia se elige con un interruptor «Para todos los roles» (encendido por
  defecto al crear); apagado, MUST exigirse al menos un rol marcado antes de guardar. Marcar todos
  los roles existentes MUST guardarse como esa lista, no como "para todos".
- **FR-023**: La pantalla MUST permitir listar con filtro por rol, crear, editar, activar/desactivar,
  eliminar y elegir roles, con validación antes de enviar, confirmación en línea al guardar,
  diálogo de confirmación antes de eliminar y aviso de que el cambio rige desde mañana.
- **FR-024**: La pantalla MUST cumplir las reglas de la app: un solo scroll, gesto atrás que vuelve
  un nivel, áreas táctiles cómodas, tema claro y oscuro, español de Perú con tuteo.
- **FR-025**: La app MUST tolerar un servidor anterior a esta función (sin roles) y un servidor
  posterior con roles que no conoce.
- **FR-026**: La pantalla MUST NOT modificar los cinco tabs principales.

### Key Entities

- **Hábito global**: hábito del catálogo del programa. Atributos que ya tiene (título, descripción,
  tipo, categoría, evidencia, opcional, activo…) más su **audiencia**.
- **Audiencia del hábito**: conjunto de roles a los que aplica. Vacío = para todos. Tiene versiones:
  cada cambio guarda quién lo hizo y desde qué instante rige; las anteriores no se borran.
- **Rol vigente de la persona**: qué rol tenía cada persona desde qué instante; se usa para saber
  qué hábitos le aplicaban cada día.
- **Rol**: tipo de cuenta (APRENDIZ, MENTOR, LÍDER DE MENTORES, ADMIN, ALQUIMISTA y futuros), con
  código estable y nombre para mostrar. Cada persona tiene uno.
- **Registro diario del hábito**: lo que la persona hace cada día. Es historia: no cambia cuando
  cambia la audiencia.
- **Configuración personal del hábito**: hora, días, pausa, nombre propio. Se conserva aunque el
  hábito deje de aplicar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un admin restringe un hábito a uno o más roles en menos de 1 minuto desde la app.
- **SC-002**: Desde el día siguiente a un cambio, el 100 % de las personas fuera de la audiencia
  deja de recibir el hábito en todas las vistas, y el 100 % de las de la audiencia lo recibe.
- **SC-003**: El 100 % de los hábitos existentes sigue llegando a las mismas personas el día
  posterior al despliegue (ningún cambio sin acción del admin).
- **SC-004**: Ninguna persona pierde registros, puntos, rachas o configuración por un cambio de
  audiencia o de rol (0 registros pasados modificados).
- **SC-005**: Ningún usuario sin permiso logra cambiar el catálogo (todas las pruebas de rechazo
  pasan).
- **SC-006**: Un rol nuevo agregado al sistema queda elegible y filtrable sin cambiar las reglas, las
  tablas ni la pantalla de hábitos, y sin APK nuevo. (Corregido 2026-10-12: el módulo de hábitos solo
  suma la constante del rol a su espejo local de roles, research R-5.)

## Assumptions

- El rol que cuenta es el rol actual de la cuenta (`usuarios.rol`); hay un solo rol por usuario. Si
  algún día hay varios, la regla pasa a ser "aplica si alguno de sus roles está en la audiencia".
- "Hacer el programa" sigue definido como hoy (participación activa); esta función no cambia quién
  recibe hábitos, solo cuáles.
- El título no se puede editar después de crear, como hoy (la clave funcional depende de él en los
  hábitos de regla propia). Los campos que hoy no expone el CRUD (clave funcional, ícono, orden,
  protegido, horarios, guías) quedan fuera de esta pantalla.
- Los horarios y guías de cada hábito siguen administrándose como hoy (fuera de la app).
- "Desde mañana" es estricto (clarify 2026-10-12): para el día local D de una persona rige la
  audiencia y el rol vigentes al cerrar su día D−1. Un cambio hecho durante su día D rige desde D+1,
  aunque su registro de D se genere después del cambio.
- La app nueva necesita APK nuevo; el filtro del servidor protege también a los APK publicados.
- Sin notificación a las personas afectadas por un cambio de audiencia.

## Fuera de alcance

- Hábitos personales, horarios y guías del catálogo, clave funcional, íconos y orden.
- Multirol por usuario.
- Editar la matriz de permisos o crear roles desde la app.
- Avisar a las personas cuando un hábito empieza o deja de aplicarles.
