# Feature Specification: Cronograma de pagos

**Feature Branch**: `032-cronograma-de-pagos`

**Created**: 2026-11-01

**Status**: Aprobada por el dueño el 2026-11-01 (con su maqueta «Mi ingreso» y sus respuestas a las 10 preguntas). En implementación: V158, D-307.

**Input**: Pedido del dueño del 2026-10-31 y del 2026-11-01, textual:

- «El sistema debe de tener cronograma de pagos junto con el diagnóstico como así.» «Prioritario: Pagos.»
- «Al momento de solicitud del usuario, sin cronograma o con cronograma de pagos; sin cronograma es que pagó y en Yo debe de salir su pago fue cancelado; con cronograma de
  pagos se calendariza de esta forma [la maqueta]… es por fecha como sale la cantidad y el administrador o alquimista puede decir se pagó o no… si es cronograma le debe de salir en
  Yo su cronograma y debe de tener recordatorios… si no paga no pasa nada, no se inhabilita la cuenta, ya que gerencia decidió eso… que le salga en Yo los recordatorios y probar
  los recordatorios.»

**Maqueta**: `~/.config/renaser-e2e/formaciones/maqueta-pagos-mi-ingreso.png`: la pantalla «Mi ingreso» dentro de Yo, con los pasos Diagnóstico → Acuerdo → Pagos, «Tus fechas de pago ·
Revisa el cronograma acordado.», una fila por cuota (calendario, fecha, monto y «✓ Pagado» o «Pendiente» con reloj), la casilla «Revisé mis fechas y montos.», el campo «Tu nombre» y
«Confirmar cronograma ›». («Datos de ejemplo · sin cobro real» es una nota de la maqueta y no va en la app.)

**Repos**: backend (`Renaser-90-dias-backend`, módulo `onboarding`) y app (`Renaser-90-dias-frontend-`: hoja «Aceptar» de Solicitudes, ficha de Administración, Yo → «Mi ingreso» y
Yo → Alarmas).

**Se apoya en**: 022 (formaciones y duración), 025 (hoja «Aceptar»), 026 (la marca del diagnóstico en `estado_onboarding`, `FichaAlAceptar`), 027 (términos = «Acuerdo») y la Caja Renaser
(patrón de «lo opera Administración, lo ve la persona»).

> **Corregido 2026-11-02 (D-315, decisión del dueño: «los pagos son asíncronos»).** (1) Al aceptar una solicitud «Con cronograma» las **cuotas son opcionales**: solo la moneda es
> obligatoria; la persona queda «con cronograma» sin cuotas y Administración las registra después desde la ficha («Agregar una cuota» / «Armar las cuotas»). Un cronograma sin cuotas
> lleva de 0 a 60 (`CronogramaDePagos.nuevo`); una vez con cuotas, la última no se quita. (2) «Mi ingreso» sin cuotas dice «Tu cronograma de pagos aparecerá aquí cuando
> Administración lo registre.» (sin lista ni confirmación), el paso de Pagos no está hecho y confirmar es 409. (3) La confirmación del cronograma es **solo** de «Mi ingreso»: la
> firma de los términos (spec 039) ya no la hace ni la ofrece.

## Contexto

- **No existía nada de pagos.** Quien lleva los cobros lo hacía fuera de la app (WhatsApp, hoja de cálculo). Las specs 022 y 025 los dejaron fuera de alcance a propósito; esta spec los pide.
- **La app no cobra ni mueve dinero.** Solo anota: un cronograma de cuotas por persona y quién las pagó, que Administración marca a mano.
- **No hay bloqueo de ningún tipo**: no pagar no inhabilita la cuenta ni pausa nada (decisión de gerencia).

## Clarifications

### Session 2026-11-01 (respuestas del dueño; sustituyen al borrador del 2026-11-01)

| # | Pregunta | Decisión |
|---|---|---|
| 1 | ¿Dónde se decide el cronograma? | Al **aceptar** la solicitud, en la misma hoja de la 025: «Sin cronograma (pagó completo)» o «Con cronograma». Vale para cualquier rol; para el personal viene «Sin cronograma». La persona lo ve en **Yo → «Mi ingreso»**. |
| 2 | ¿Se cobra en la app? | **No. Solo se anota.** |
| 3 | ¿Plan modelo o persona por persona? | **Persona por persona.** Administración escribe fecha y monto de cada cuota; hay un atajo «N cuotas mensuales de X desde tal fecha» que arma las filas y después se editan. Sin planes modelo. |
| 4 | ¿Desde cuándo corren las fechas? | **Fechas fijas del calendario**, las que escribe Administración. |
| 5 | ¿Quién marca un pago? | Administración o Alquimista marcan cada cuota «Pagado» o la devuelven a «Pendiente». Queda quién y cuándo. La persona no sube comprobantes. |
| 6 | ¿Quién ve qué? | La persona ve el suyo; Administración y Alquimista ven todos; **nadie más** (mentor, guía, líder y Soporte TI, nada). |
| 7 | ¿Qué pasa si no paga? | **Solo recordatorios; nunca bloquea ni inhabilita.** Los recordatorios salen en Yo, la persona los configura y se prueban en el E2E. |
| 8 | ¿Y los que ya están dentro? | Quedan sin cronograma; Administración puede asignárselo, o marcarlo «sin cronograma», desde su ficha. |
| 9 | ¿Boletas o facturas? | No se emiten. |
| 10 | ¿Se recalcula algo solo? | **Nada.** Lo pagado no se toca y Administración edita a mano lo pendiente. |

Además, del dueño: «sin cronograma» se ve en Yo como **«Tu pago fue cancelado»** (en el Perú, «cancelado» es «pagado por completo»); la moneda es **S/ o US$, una por cronograma, sin
valor por defecto**; y la persona **confirma** el cronograma una sola vez («Revisé mis fechas y montos.», su nombre y «Confirmar cronograma»); si Administración cambia las cuotas, la
confirmación se vuelve a pedir; **nunca bloquea nada**.

### Lo que se asume (a la vista del dueño)

1. **Tres estados de una persona, no dos**: *sin anotar* (nadie decidió nada: las cuentas de antes, el personal que entra con «Sin cronograma» por defecto), *sin cronograma* (Administración
   anotó que pagó completo) y *con cronograma*. Solo los dos últimos muestran «Mi ingreso».
2. **Para el personal, dejar «Sin cronograma» (el valor que viene) no anota nada**: no verá «Tu pago fue cancelado». Solo se anota si Administración elige «Con cronograma» o lo asigna desde
   su ficha. Para el aprendiz, «Sin cronograma (pagó completo)» sí se anota.
3. **Una cuota solo tiene dos estados**: pendiente o pagada. No hay «vencida», «atrasada» ni «por vencer» (la maqueta no los tiene y la decisión 7 pide no tener tono de reproche). Lo que
   se avisa antes de la fecha lo hace el recordatorio local, que sabe qué día es en el teléfono.
4. **«Quién y cuándo»** es el último cambio de cada cuota (quien la marcó pagada o la devolvió a pendiente y cuándo). No hay una bitácora de cada cambio.
5. **El cronograma tiene como máximo 60 cuotas**, un monto positivo de hasta 9 999 999,99 y una fecha entre 2020 y 2100: topes técnicos para evitar errores de tipeo, no reglas de negocio.
6. **Cambiar el cronograma de quien ya pagó algo**: no se puede reemplazar entero ni pasar a «sin cronograma» si hay cuotas pagadas (una pagada no se edita ni se borra); sí se agregan,
   editan y quitan cuotas pendientes, pero debe quedar al menos una (para quitar el cronograma, «Sin cronograma»).
7. **Al eliminar una cuenta se borran sus pagos** junto con el resto de sus datos (D-243). Si hay una obligación legal de conservarlos, es otra decisión.
8. **Los recordatorios son solo de este teléfono** (alarmas locales como las de eventos y acciones): no hay aviso del servidor ni mensaje en el chat de soporte.
9. **Aceptar varias solicitudes a la vez** (025-3 C) con «Con cronograma» da **a todas el mismo cronograma** (las mismas fechas y montos). Cada una se acepta por separado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Veo mi cronograma en Yo y lo confirmo (Priority: P1)

Como persona que entró con un cronograma de pagos, quiero ver en Yo → «Mi ingreso» qué fechas y montos me tocan, cuáles ya figuran pagados, y confirmar que los revisé.

**Independent Test**: con una cuenta con 3 cuotas (una pagada), abrir Yo → «Mi ingreso», ver las tres filas con su estado, marcar la casilla, escribir el nombre y confirmar; volver a abrir y ver «Confirmaste el 3 de noviembre».

**Acceptance Scenarios**:

1. **Given** una persona con cronograma, **When** abre Yo, **Then** ve la fila «Mi ingreso»; al abrirla ve los pasos (Diagnóstico solo si le tocó, Acuerdo, Pagos) con su estado real, «Tus fechas de pago · Revisa el cronograma acordado.» y una fila por cuota en orden de fecha, con la fecha, el monto con su moneda y «Pagado» (✓) o «Pendiente» (reloj).
2. **Given** una persona **sin cronograma** anotada, **When** abre «Mi ingreso», **Then** ve «Tu pago fue cancelado» y los pasos; sin cuotas ni confirmación.
3. **Given** una persona **sin anotar**, **When** abre Yo, **Then** no ve «Mi ingreso» y Yo queda como hoy.
4. **Given** la confirmación pendiente, **When** marca «Revisé mis fechas y montos.», escribe su nombre y toca «Confirmar cronograma», **Then** queda confirmado y la pantalla dice cuándo; no vuelve a pedirse.
5. **Given** un cronograma confirmado, **When** Administración agrega, edita o quita una cuota, **Then** la confirmación se vuelve a pedir con un aviso («Tu cronograma cambió. Revísalo y confírmalo de nuevo.»). Marcar una cuota pagada o pendiente **no** la invalida.
6. **Given** cualquier estado, **When** no confirma o no paga, **Then** no se le bloquea nada.
7. **Given** una persona, **When** pide algo de otra por id, **Then** no existe para ella (la ruta no lleva id: siempre es la propia).

### User Story 2 - Al aceptar, Administración decide el pago (Priority: P1)

Como Administración, quiero decidir en la hoja «Aceptar» si la persona pagó completo o tiene cronograma, y armar sus cuotas ahí mismo.

**Acceptance Scenarios**:

1. **Given** la hoja «Aceptar», **When** llega a «Pago», **Then** elige «Sin cronograma (pagó completo)» o «Con cronograma».
2. **Given** «Con cronograma», **When** lo arma, **Then** elige la moneda (S/ o US$, sin valor por defecto) y escribe las cuotas (fecha y monto), a mano o con «N cuotas mensuales de X desde tal fecha», y puede editar las filas después.
3. **Given** que acepta, **When** se completa, **Then** la cuenta, la formación, la ficha y el cronograma quedan en la **misma transacción**: o todo o nada.
4. **Given** una app anterior que no manda `payment`, **When** acepta, **Then** la persona queda sin anotar y nada falla.
5. **Given** «Con cronograma» sin moneda, sin cuotas o con un monto no positivo, **Then** el servidor responde 400 y no queda nada a medias.
6. **Given** un rol de personal, **When** abre la hoja, **Then** «Sin cronograma» viene marcado.

### User Story 3 - Administración marca los pagos y corrige el cronograma (Priority: P1)

Como Administración o Alquimista, quiero ver el cronograma de una persona en su ficha, marcar cada cuota pagada o pendiente y ajustar las pendientes.

**Acceptance Scenarios**:

1. **Given** la ficha, **When** la abro, **Then** veo la sección «Pagos» con la modalidad, la moneda y las cuotas, y quién y cuándo marcó cada pago.
2. **Given** una cuota pendiente, **When** la marco «Pagado», **Then** pasa a pagada con mi nombre y la hora; un doble toque deja una sola marca. Puedo devolverla a «Pendiente».
3. **Given** cuotas pendientes, **When** las agrego, edito o quito, **Then** el cambio se guarda; una pagada no se edita ni se quita (409) y no se puede quitar la última.
4. **Given** una persona sin anotar, **When** le asigno un cronograma o «Sin cronograma», **Then** queda anotado. Si ya tiene cuotas pagadas, no se puede reemplazar el cronograma (409).
5. **Given** cualquier otro rol (mentor, guía, líder, Soporte TI, aprendiz), **When** intenta algo de pagos, **Then** 403.

### User Story 4 - Recordatorios de pago en el teléfono (Priority: P1)

Como persona con cronograma, quiero que el teléfono me avise antes de cada cuota pendiente, y elegir cuándo y a qué hora.

**Acceptance Scenarios**:

1. **Given** cuotas pendientes con fecha futura, **When** abro la app, **Then** el teléfono programa una alarma por cuota y aviso (por defecto 3 días antes y el mismo día, a las 09:00).
2. **Given** Yo → Alarmas → «Recordatorios de pago» (y la fila «Recordatorios de pago ›» de «Mi ingreso», que abre el mismo control en una hoja), **When** cambio el encendido, los avisos (3 días antes, 1 día antes, el mismo día, varios a la vez) o la hora, **Then** las alarmas se rehacen.
3. **Given** una cuota que Administración marcó pagada, **When** la app relee el cronograma (al abrir o volver al frente), **Then** sus alarmas se cancelan.
4. **Given** el aviso, **When** lo toco, **Then** se abre Yo → «Mi ingreso».
5. **Given** que cierro sesión, **Then** las alarmas de pago se sueltan.

### Edge Cases

- **Una cuota con la fecha ya pasada y pendiente** se muestra «Pendiente», sin tono de reproche, y no programa alarma.
- **Reloj**: las fechas son del calendario (sin hora ni zona); «hoy» para las alarmas es el del teléfono.
- **Cuenta suspendida**: no ve ni confirma nada (403); Administración sigue pudiendo leer y editar.
- **Soporte TI «ver como»** (solo lectura): puede ver «Mi ingreso» de la cuenta que mira; no puede confirmar.
- **Funcionalidad apagada** (interruptor `pagos`): «Mi ingreso» y las rutas de Administración muestran el aviso de mantenimiento; lo anotado no se pierde.
- **Backend anterior**: la app no ve ninguna fila «Mi ingreso» ni el bloque «Pago» en «Aceptar». **App anterior**: no manda `payment` y no ve nada nuevo.
- **Aislamiento entre formaciones**: Administración y Alquimista ven las tres; la persona, solo lo suyo; ningún otro rol ve nada.

## Requirements *(mandatory)*

- **FR-001**: Una persona MUST poder estar *sin anotar*, *sin cronograma* o *con cronograma*; solo los dos últimos muestran «Mi ingreso».
- **FR-002**: Un cronograma MUST tener una moneda (PEN o USD, sin valor por defecto) y de 1 a 60 cuotas, cada una con fecha del calendario, monto positivo (2 decimales) y estado pendiente o pagada.
- **FR-003**: Aceptar una solicitud MUST poder traer `payment` (`NONE` o `SCHEDULE` con moneda y cuotas) y aplicarlo en la misma transacción que el resto de la aceptación; un `payment` inválido MUST responder 400 sin dejar nada a medias. Sin `payment`, nada se anota.
- **FR-004**: Administración y Alquimista MUST poder, desde la ficha: ver el cronograma, asignarlo o marcarlo «sin cronograma» (si no hay cuotas pagadas), agregar, editar y quitar cuotas pendientes, y marcar cada cuota pagada o pendiente dejando quién y cuándo.
- **FR-005**: Una cuota pagada MUST NOT editarse ni quitarse; el cronograma MUST conservar al menos una cuota; marcar una cuota ya pagada MUST ser idempotente (no cambia quién ni cuándo).
- **FR-006**: Nada MUST recalcularse solo: ni fechas, ni montos, ni estados.
- **FR-007**: La persona MUST ver solo lo suyo (`GET /me/ingreso`, sin id). Mentor, guía, líder, Soporte TI y aprendiz MUST recibir 403 en las rutas de Administración; la cuenta suspendida, 403 en todo.
- **FR-008**: La persona MUST poder confirmar su cronograma una vez (casilla, nombre, hora); el servidor MUST guardar el nombre, la hora y una **huella** de las fechas y montos confirmados, y MUST considerar la confirmación vigente solo mientras la huella coincida con la de las cuotas de ahora.
- **FR-009**: Ninguna función de la app MUST bloquearse ni cambiar por no pagar o no confirmar.
- **FR-010**: `GET /me/ingreso` MUST dar los pasos (Diagnóstico solo si le tocó, Acuerdo, Pagos solo si hay pago anotado) con su estado real, para que la app dibuje la cabecera de la maqueta.
- **FR-011**: La app MUST programar alarmas locales de pago (una por cuota pendiente y aviso, por defecto 3 días antes y el mismo día, a las 09:00), con su propio canal con vibración; MUST cancelarlas al marcarse pagada la cuota (en la siguiente lectura), al cerrar sesión y al apagarlas; MUST ponerlas al día al abrir y al volver al frente; y tocar el aviso MUST abrir Yo → «Mi ingreso».
- **FR-012**: La configuración de los recordatorios (encendido, cuándo, hora) MUST poder cambiarse en Yo → Alarmas y en «Mi ingreso» (misma pieza), y MUST guardarse en el teléfono por cuenta.
- **FR-013**: La función MUST poder apagarse desde Soporte TI (clave `pagos`).
- **FR-014**: Una app anterior MUST seguir funcionando; la app nueva MUST tolerar un servidor que no informa de pagos (404 o sin campo) sin mostrar nada.
- **FR-015**: Las pruebas MUST verificar la autorización negativa por rol, el aislamiento entre personas y entre las tres formaciones, la atomicidad de aceptar con cronograma y la vigencia de la confirmación.

### Key Entities

- **Cuota** (`cuotas_de_pago`): una línea del cronograma de una persona: fecha, monto, estado, quién y cuándo cambió el estado por última vez.
- **Pago de la persona** (4 columnas de `estado_onboarding`): la modalidad (sin anotar / sin cronograma / con cronograma), la moneda y la confirmación (hora, nombre, huella).
- **Cronograma**: la moneda y las cuotas de una persona; no es una tabla propia.

## Success Criteria *(mandatory)*

- **SC-001**: Una persona con cronograma llega a su cronograma en **2 toques** desde Yo.
- **SC-002**: Marcar una cuota pagada toma **3 toques o menos** desde la ficha.
- **SC-003**: Aceptar «con cronograma» suma **un bloque** a la hoja de la 025 (y las cuotas con el atajo mensual, en menos de 30 segundos).
- **SC-004**: **0** personas ven el cronograma de otra; **0** roles ajenos a Administración y Alquimista leen o escriben pagos.
- **SC-005**: Para quien está *sin anotar*, la regresión de Yo, Alarmas y Solicitudes pasa **sin cambios**.
- **SC-006**: Cambiar las cuotas invalida la confirmación el **100 %** de las veces; marcar un pago, **0** veces.

## Fuera de alcance

- Cobrar dentro de la app, pasarelas, comprobantes que suba la persona, boletas o facturas.
- Bloquear o pausar cuentas por no pagar; intereses, moras, descuentos, becas; contabilidad.
- Planes modelo por formación y duración; recalcular cronogramas al cambiar la duración o la formación.
- Avisos del servidor (push, bandeja, chat) y resumen de atrasados para Administración.
- Una lista general de «quién debe» con contadores y planilla descargable.

## Assumptions

- Los de la sección «Lo que se asume» de arriba.
- Los datos viven en el módulo `onboarding`: la modalidad, la moneda y la confirmación en `estado_onboarding` (1:1 con `usuarios`, como la marca del diagnóstico de la 026) y las cuotas en una tabla nueva (`cuotas_de_pago`). Sin tabla de cronogramas, de planes ni de pagos aparte.
- Un permiso nuevo (`MANAGE_PAYMENTS`) y una funcionalidad nueva (`pagos`); lo opera solo ADMIN y ALCHEMIST, comprobado por el servicio además del interceptor (como la Caja y el diagnóstico, por el hueco A-1).
- «Mi ingreso» no reemplaza a «Mi onboarding» de Ajustes: ese sigue siendo el lugar de los documentos (ficha, Términos y el diagnóstico). «Mi ingreso» es la vista de los **pagos** con los pasos como orientación, sin repetir sus pantallas.
- Se usan los números reservados V158 (V159 y V160 quedan libres) y D-307.
