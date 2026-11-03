# Feature Specification: Acuerdo de transformación (términos nuevos, «Mi aceptación» y cronograma al firmar)

> **Corregido 2026-11-02 (D-315, decisión del dueño): el cronograma de pagos NO va en los términos.** Textual: «lo de los pagos no debe de salir en términos y condiciones
> ya que será asíncrono bro, eso saldrá luego que el administrador lo llene». Lo que dice esta spec de **firmar el cronograma en el mismo acto** (la historia 2, FR-006,
> FR-008, FR-009, SC-002, el supuesto 6, la sección «Mi cronograma de pagos» y la casilla de la cláusula 19, `agreement.schedule`, `scheduleFingerprint`, `scheduleSigned`,
> `cronograma_huella` y el 409 «Tu cronograma cambió mientras leías») **ya no rige**. Ahora: los términos y la firma no muestran ni exigen el cronograma, ni en el primer ingreso
> ni al volver a aceptar; «Mi aceptación» (nombre, programa y fechas) **se queda igual**; la persona lo ve y lo confirma en Yo → «Mi ingreso» (spec 032) cuando Administración
> lo registre, y al aceptar «Con cronograma» las cuotas son opcionales. Las 23 cláusulas no cambian (la 19 sigue hablando del acuerdo de pagos). El servidor ya no manda
> `schedule` ni lee `scheduleFingerprint` (un cliente que lo mande no falla: se ignora); la columna `aceptaciones_de_terminos.cronograma_huella` se deja sin borrar y sin
> usar. Ver D-315 en `docs/MODULOS_A_AVANZAR.md`.


**Feature Branch**: `039-acuerdo-de-transformacion` (backend y app)
**Created**: 2026-11-02
**Status**: Implementada (2026-11-02), pendiente de la verificación completa (`./mvnw verify`, Jest, `tsc`) y del E2E en el emulador. Sin preguntas abiertas: los supuestos de abajo se le avisan al dueño.
**Input**: Pedido del dueño (2026-11-02), textual: «Este será el nuevo términos y condiciones. Y cuando aceptes la solicitud con cronograma o sin cronograma: con cronograma, sale abajo para firmar también que estás de acuerdo; y sin cronograma, el de siempre de firmar. No te guíes explícitamente como mencionan, solamente agregar esa parte cuando el usuario, administrador o alquimista agregue a alguien dentro de la app.» Decisión: **D-312** (`docs/MODULOS_A_AVANZAR.md`).

**Se apoya en**: `027-terminos-editables` (versiones y constancia), `032-cronograma-de-pagos` (el cronograma y su confirmación) y `036-empieza-al-dia-siguiente` (el programa empieza mañana). Ninguna está fusionada todavía: esta rama parte de `036-empieza-al-dia-siguiente`, que ya trae las tres.

## Contexto

Hoy los términos son las 23 cláusulas de siempre (spec 027): la persona los lee, marca la casilla y firma con el dedo. El dueño entregó **un texto nuevo, el «Acuerdo de transformación»**, también de 23 cláusulas, con un bloque final **«MI ACEPTACIÓN»** (participante, programa, fechas, firma) y una cláusula 19 que habla de un **cronograma de pagos**. Pidió que el texto se use tal cual y que el cronograma se firme **en el mismo acto** solo para quien entra a la app aceptado con cronograma.

Lo que la plantilla del dueño deja como marcadores entre corchetes («[Nombre completo LLENADO POR la IA]», etc.) **no se toma literal**: los llena el **sistema** con los datos reales de la persona. No interviene ninguna IA.

## Clarifications

### Session 2026-11-02 (pedido del dueño)

- **El texto**: el nuevo reemplaza a los vigentes; las 23 cláusulas van numeradas tal cual y las de dos párrafos (18, 21, 22 y 23) los conservan.
- **«MI ACEPTACIÓN»**: lo arma el sistema al final de los términos, antes de firmar.
- **Con cronograma**: sale abajo y se firma con lo mismo. **Sin cronograma** (o «pagó completo»): la firma de siempre.
- **Solo al entrar a la app**: no se le pide a quien ya estaba dentro y recibe un cronograma después (eso sigue en «Mi ingreso», como en la spec 032).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entro sin cronograma: leo el acuerdo nuevo, veo mi aceptación y firmo (Priority: P1)

Como aprendiz aceptado «sin cronograma» (pagó completo), al terminar mi ficha leo los términos nuevos, veo «Mi aceptación» con mi nombre, mi programa y mis fechas, marco la casilla, firmo y sigo a «Tu programa empieza mañana».

**Independent Test**: con un aprendiz de la Escuela sin cronograma, `GET /onboarding/terms` trae la versión nueva y el bloque `agreement` sin `schedule`; aceptar guarda la versión, el programa («Formación RENASER · 90 días»), el inicio (mañana) y el fin (inicio + 89 días).

**Acceptance Scenarios**:

1. **Given** un aprendiz de la Escuela sin cronograma, **When** llega a Términos, **Then** ve el título «Términos y condiciones», «Lee atentamente las 23 cláusulas y sella tu aceptación», la introducción «Al inscribirme en Formación RENASER, …», las 23 cláusulas numeradas y, debajo, «MI ACEPTACIÓN» con las dos frases fijas y los cinco datos (participante, programa, inicio, finalización, aceptación), la firma y la casilla. **No** ve «Mi cronograma de pagos».
2. **Given** esa pantalla, **When** firma y toca «Continuar», **Then** queda la constancia con la versión, la hora, la firma y **lo que se mostró**: el programa, el inicio y el fin; sigue «Tu programa empieza mañana» con **la misma fecha de inicio**.
3. **Given** que pasó la medianoche con la pantalla abierta (mañana ya no es la fecha que se mostró), **When** toca «Continuar», **Then** el servidor no registra nada (409), la app relee y avisa «Pasó la medianoche y tus fechas cambiaron: léelas de nuevo.».

### User Story 2 - Entro con cronograma: lo veo y lo firmo en el mismo acto (Priority: P1)

Como aprendiz aceptado «con cronograma», debajo de «Mi aceptación» veo «Mi cronograma de pagos» (moneda y cuotas), marco «Acepto el cronograma de pagos registrado en NASER y me comprometo a cumplirlo.», firmo, y mi cronograma queda confirmado.

**Independent Test**: con un aprendiz con cronograma de dos cuotas, aceptar con la huella que leyó deja `estado_onboarding.pago_confirmado_*` con su nombre y esa huella, y «Mi ingreso» dice `CONFIRMED`.

**Acceptance Scenarios**:

1. **Given** un aprendiz con cronograma sin confirmar, **When** llega a Términos, **Then** ve «Mi cronograma de pagos» con la moneda y una fila por cuota (fecha y monto, como en «Mi ingreso»), y la casilla con la frase de la cláusula 19. Sin esa casilla, «Continuar» no avanza y dice qué falta.
2. **Given** que firma, **When** el servidor registra la aceptación, **Then** en la **misma transacción** queda confirmado el cronograma con la **misma confirmación de la spec 032** (su nombre completo, la hora y la huella de moneda, fechas y montos); «Mi ingreso» dice «Confirmaste tu cronograma el …» y no lo vuelve a pedir.
3. **Given** que Administración cambió el cronograma mientras leía, **When** firma, **Then** 409 «Tu cronograma cambió mientras leías: revísalo de nuevo.», no queda aceptación ni confirmación, y la app relee.
4. **Given** una persona sin cronograma o con «pagó completo», **Then** el servidor no confirma nada.
5. **Given** que Administración cambia el cronograma **después** de firmado, **Then** vale la regla de la spec 032: «Mi ingreso» pide confirmarlo de nuevo, sin volver a firmar los términos.

### User Story 3 - Quien ya estaba dentro acepta el texto nuevo (Priority: P1)

Como persona que ya usaba la app, al abrirla veo «Actualizamos nuestros términos» con el texto nuevo y «Mi aceptación» con mi programa y mis fechas reales (ya empecé), y firmo de nuevo.

**Acceptance Scenarios**:

1. **Given** una persona de la Escuela con el programa activado, **When** abre la app tras la publicación, **Then** el servidor le pide aceptar de nuevo (`NEW_VERSION`, bloquea), con la nota «Nuevo Acuerdo de transformación».
2. **Given** esa pantalla, **Then** «Mi aceptación» muestra su inicio **real** (el que tiene activado) y su fin (inicio + duración − 1, con la misma cuenta del servidor para el último día) y **no** la sección del cronograma, aunque tenga uno.
3. **Given** que acepta, **Then** la constancia guarda lo mostrado y no se toca su cronograma.

### User Story 4 - Releer y consultar la constancia (Priority: P2)

La persona relee en Yo → Mi onboarding → Términos lo que aceptó (texto, firma y, ahora, su programa y sus fechas). Administración ve en la constancia de la persona el programa y las fechas que firmó y si firmó el cronograma.

## Requirements *(mandatory)*

- **FR-001** El texto legal del dueño se publica **tal cual**, 23 cláusulas, con el nombre del programa en la introducción: Escuela → «Formación RENASER», Diplomado → «Diplomado RENASER», Certificación → «Certificación RENASER». «NASER» (la app) se deja como está.
- **FR-002** La migración **V162** crea la versión nueva de cada formación sobre las tablas de la spec 027 (sin tablas nuevas): la Escuela pasa a la versión siguiente con `pide_nueva_aceptacion = true`; el Diplomado y la Certificación, que no tenían términos publicados, quedan con la versión 1. La publicación queda a nombre del «sistema» (`publicada_por` nulo) con la fecha. Es **idempotente**.
- **FR-003** `GET /onboarding/terms` suma `agreement` (aditivo): participante, programa, fechas y, si corresponde, el cronograma a firmar. Lo arma el servidor; la app no calcula ni una fecha.
- **FR-004** Participante: el **nombre completo** del perfil. Programa: «Formación RENASER · 90 días», «Diplomado RENASER · 3 meses» / «· 1 año» o «Certificación RENASER · 90 días».
- **FR-005** Inicio: la regla de la spec 036 (mañana en la zona de la persona) o, si ya activó, la fecha que tiene. Fin: `ultimaFechaDelPrograma` (inicio + duración − 1 + ajuste); nunca «90 días» escritos a mano.
- **FR-006** El cronograma se ofrece para firmar **solo** si es el primer ingreso (`FIRST_TIME`), la persona tiene cronograma y no lo tiene ya confirmado con lo vigente.
- **FR-007** Al aceptar (`POST /onboarding/milestones`, hito `TERMINOS`), el servidor guarda con la constancia lo que se mostró: programa, inicio y fin (y la huella del cronograma si lo firmó). Si el inicio que la persona vio ya no es el de ahora, 409.
- **FR-008** Si el pedido trae la huella del cronograma, el servidor la compara con la vigente (409 si cambió) y confirma el cronograma con la confirmación de la spec 032 en la misma transacción. [sin prueba automática: ya no rige (D-315): el cronograma salió de los términos; lo que rige ahora —la firma no lo trae, no lo confirma y se ignora una huella que mande un cliente de la spec 039— lo prueban `AcuerdoDeTransformacionIT` «con cronograma y con cuotas…» y «una app de la spec 039 que todavía manda scheduleFingerprint…»]
- **FR-009** Sin la huella (una app anterior, quien ya estaba dentro, sin cronograma) no se confirma nada.
- **FR-010** El editor de Administración (spec 027) muestra y deja editar las 23 cláusulas, incluidos los dos párrafos de una cláusula.
- **FR-011** Compatibilidad: todo es aditivo. Una app anterior contra el servidor nuevo lee el texto nuevo y acepta como siempre (sin bloque ni cronograma). La app nueva contra un servidor anterior no tiene `agreement`: no dibuja el bloque.

### Edge cases

- **Medianoche**: «mañana» y la fecha de fin salen del mismo reloj del servidor en la zona de la persona; el pedido lleva el inicio visto y el servidor rechaza si ya no coincide. Entre firmar y activar (segundos) la fecha podría moverse: es un límite aceptado.
- **Personal sin programa** (Administración, Alquimista, mentor sin fila): «Mi aceptación» lleva su nombre, el programa de su formación sin duración y **sin fechas** (no se promete un inicio que no eligió).
- **Texto editado después**: cualquier edición posterior de Administración vuelve a ser una versión nueva (spec 027); la constancia de cada persona sigue apuntando a la que firmó.
- **Dos párrafos en una cláusula**: el texto de la cláusula lleva una línea en blanco entre párrafos; la app la respeta.

## Assumptions (se le avisan al dueño)

1. El texto nuevo **reemplaza** a los términos vigentes en las **tres** formaciones.
2. Donde la plantilla dice «(Formación, Diplomado o Certificación RENASER)» va el programa de la persona; «NASER» es el nombre de la app y se deja tal cual.
3. Se publica como **versión nueva en las tres formaciones** con una migración; quien ya estaba dentro tendrá que **aceptarlos de nuevo con su firma** al abrir la app (el flujo de la spec 027); el Diplomado y la Certificación, que no tenían términos publicados, quedan con estos.
4. El borrador del Diplomado y de la Certificación que la spec 027 dejó como copia de los términos viejos se descarta **solo si sigue idéntico** a esa copia; uno que Administración ya editó se conserva (su base queda vencida y habrá que volver a basarlo).
5. «Fecha de aceptación» se registra sola al firmar; no la elige nadie.
6. El nombre con el que se confirma el cronograma al firmar es el **nombre completo del perfil**, el mismo que se muestra como «Participante».
7. Quien ya estaba dentro y recibe un cronograma después lo confirma en «Mi ingreso», como hoy.

## Success Criteria

- **SC-001** El texto de cada formación coincide con el del dueño en sus 23 cláusulas y nadie ve un marcador entre corchetes ni llaves.
- **SC-002** Con cronograma, una sola firma deja la aceptación y la confirmación; «Mi ingreso» lo refleja sin pedir nada más. [sin prueba automática: ya no rige (D-315): el cronograma se confirma en Yo → «Mi ingreso» (spec 032), no al firmar los términos; ver las pruebas de FR-008]
- **SC-003** Las fechas de «Mi aceptación» y de «Tu programa empieza mañana» son las mismas.
- **SC-004** Para 90 días, 3 meses y 1 año el fin es inicio + 89, + 89 y + 364 días.
