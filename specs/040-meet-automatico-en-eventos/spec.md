# Feature Specification: Enlace de Meet automático al crear un evento (y la grabación, como decisión del dueño)

**Feature Branch**: `040-meet-automatico` (backend y app)
**Created**: 2026-11-04
**Status**: Borrador de spec. **No se implementa nada todavía.** La Fase 1 espera el visto bueno de la spec y una prueba corta con la cuenta real (SC-001); la Fase 2 espera la decisión del dueño.
**Input**: Pedido del dueño (2026-11-04), textual: «Al crear un evento, que se cree automáticamente el enlace de Google Meet y que la sesión se grabe automáticamente.» Lo quiere con **Google Cloud, no con Google Workspace**. Decisión firme posterior del dueño: **nada de Workspace, ni siquiera una licencia.** La cuenta de la organización hoy es un **Gmail normal**.

**Se apoya en**: el módulo de eventos (`calendar/`: `Evento`, `EventoService`, `EventoController`, `TipoUbicacion.MEET`), la asistencia (D-256), la consola de Soporte TI (interruptores) y los registros de auditoría. **No toca** audiencias, recordatorios, formaciones (spec 024) ni la asistencia.

## Contexto

Hoy quien crea un evento (ADMIN y ALCHEMIST, `MANAGE_CALENDAR`, D-186) **pega a mano** un link de Meet, Zoom o Drive en `valorUbicacion` (el servidor solo comprueba que empiece por `https://` o `http://`, `Evento.requireLinkWeb`, E-364). La app lo abre con «Unirme» y le pone el logo del servicio (`marcaDelLink`). El dueño quiere que el link de Meet **se cree solo** y que la sesión **se grabe sola**.

Lo averiguado, con su fuente (verificado el 2026-11-04):

| # | Hecho | Estado | Fuente |
|---|---|---|---|
| H1 | Un evento de Google Calendar puede crear su sala de Meet desde el servidor: `events.insert` (o `patch`) con `conferenceData.createRequest` (`requestId` único y `conferenceSolutionKey.type = "hangoutsMeet"`) y el parámetro `conferenceDataVersion=1`. La respuesta puede venir con estado `pending` y completarse después. | **Confirmado** | [Create events](https://developers.google.com/workspace/calendar/api/guides/create-events), [events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert) |
| H2 | Desde 2020 Google **no** agrega Meet solo en llamadas por API: hay que pedirlo explícitamente. Y recomienda **un conference nuevo por cada evento**, nunca reutilizar el código de otro evento. | **Confirmado** | [Calendar API release notes](https://developers.google.com/calendar/releases) |
| H3 | Los permisos (scopes) válidos de `events.insert` son `calendar`, `calendar.events`, `calendar.app.created` y `calendar.events.owned`. | **Confirmado** | [events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert) |
| H4 | **Que esto funcione con un Gmail personal, sin Workspace, NO lo dice la documentación oficial en ninguna parte**: ni la guía de eventos ni la referencia mencionan el tipo de cuenta. La documentación sugiere un chequeo práctico: leer `conferenceProperties.allowedConferenceSolutionTypes` del calendario, y comprobar que `hangoutLink` llega tras el `createRequest`. Meet gratis existe para cuentas personales (invitar hasta 100 personas), así que se espera que funcione, pero **hay que probarlo con la cuenta real antes de construir** (SC-001). | **Sin confirmar en la documentación → prueba obligatoria** | [Create events](https://developers.google.com/workspace/calendar/api/guides/create-events), [Meet para cuentas personales](https://workspace.google.com/products/meet/) |
| H5 | La **Meet REST API** (`spaces.create`, scope `meetings.space.created`) existe y es GA, pero **la documentación tampoco dice si una cuenta personal puede usarla** (las guías solo hablan de «meeting organizer» y de Workspace en lo de grabaciones). **No se usa en esta spec**: la Calendar API es la vía. | **Sin confirmar** | [Meeting spaces overview](https://developers.google.com/workspace/meet/api/guides/meeting-spaces-overview), [spaces.create](https://developers.google.cn/meet/api/reference/rest/v2/spaces/create) |
| H6 | **Una reunión de Meet NO se puede grabar automáticamente sin Workspace.** Para grabar, un administrador de Google Workspace tiene que habilitar la grabación de la cuenta, y la ayuda oficial lista solo ediciones de Workspace (y algunas suscripciones de Google One/Workspace Individual): un Gmail gratuito no figura. | **Confirmado** | [Grabar en Meet](https://support.google.com/meet/answer/9308681) |
| H7 | La API de Meet tiene `config.artifactConfig.recordingConfig.autoRecordingGeneration = ON`, pero solo graba «cuando entra alguien con privilegio de grabar», o sea que además depende de H6. | **Confirmado** | [Spaces (referencia)](https://developers.google.com/workspace/meet/api/reference/rest/v2/spaces) |
| H8 | **Meet con cuenta personal corta las reuniones de 3 o más personas a los 60 minutos** (las de 1 a 1, a las 24 h); aviso a los 50 minutos; el límite lo da la cuenta de quien organiza. **Esto afecta a la Fase 1**: una sesión grupal de más de 60 minutos se corta. | **Confirmado en la ayuda de Google** (lo repiten fuentes de terceros) | [Funciones de Meet](https://support.google.com/meet/answer/13396001), [Requisitos](https://support.google.com/meet/answer/7317473) |
| H9 | En una reunión organizada con cuenta personal, **solo quien la creó puede admitir** a los participantes que no estaban invitados por Calendar. | **Confirmado** | [Meet: acceso](https://support.google.com/meet/answer/9302870) |
| H10 | Un refresh token de un proyecto con pantalla de consentimiento **externa** en estado **«Testing»** vence a los **7 días**; también vence si no se usa en 6 meses o si la persona lo revoca. | **Confirmado** | [OAuth 2.0](https://developers.google.com/identity/protocols/oauth2#expiration) |
| H11 | Cuotas de Calendar API: 600 pedidos por minuto por usuario y por proyecto; 1 000 000 por día por proyecto; errores `403/429 usageLimits`; Google pide reintentar con espera exponencial. Escribir muchas veces seguidas en un solo calendario puede dar límites operativos que la documentación no cuantifica. | **Confirmado** | [Cuotas](https://developers.google.com/workspace/calendar/api/guides/quota) |

**Conclusión de la investigación**

1. **El enlace automático sin Workspace es viable** (Fase 1), con la Calendar API y un Gmail autorizado por OAuth, **pero** hay dos advertencias que el dueño debe conocer: el soporte de cuentas personales **no está garantizado por escrito** (se confirma con una prueba de media hora, SC-001) y **toda sesión de 3 o más personas se corta a los 60 minutos** (H8) y obliga a que el anfitrión admita a la gente (H9).
2. **La grabación automática de una reunión de Meet no es posible sin Workspace** (H6, H7). Es una limitación de Google, no del código. Para grabar hay que **cambiar de herramienta de videollamada** (Fase 2, decisión del dueño): una propia en Google Cloud, Amazon Chime SDK o Zoom Pro. Cualquiera de ellas **reemplaza a Meet** para las sesiones que se graben.

## Clarifications

### Session 2026-11-04 (pedido y decisión del dueño)

- Q: ¿Con qué se hace el enlace? → A: Con **Google Cloud** (proyecto propio y la Calendar API), **sin Workspace**. Lo autoriza **un Gmail de Renaser**.
- Q: ¿Y si para grabar hiciera falta Workspace? → A: **Decisión firme del dueño: nada de Workspace, ni una licencia.** La opción «una licencia de Workspace solo para la cuenta organizadora» **queda descartada** y esta spec no la ofrece ni la recomienda. Grabar con Meet no es posible; la Fase 2 compara solo otras herramientas.
- Q: ¿Qué se hace primero? → A: La Fase 1 (enlace) es independiente y se puede construir y publicar sola. La Fase 2 queda como decisión del dueño.

## Alcance

**Dentro (Fase 1)**: crear, actualizar y borrar la sala de Meet de un evento desde el servidor; conectar y desconectar la cuenta organizadora; fallos, reintentos, cuotas y auditoría; el interruptor en el formulario de crear/editar evento.
**Dentro (Fase 2, solo como análisis)**: las opciones de grabación, para que el dueño elija. **Nada de la Fase 2 se construye con esta spec.**
**Fuera**: invitar a Calendar a los aprendices (no se agregan asistentes a Google: se entra por el link de Renaser); sincronizar el calendario de Google hacia Renaser; usar la Meet REST API; grabar con Meet; cambiar audiencias, recordatorios, asistencia o ranking.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Creo un evento y el link de Meet aparece solo (Priority: P1)

Como Administración o Alquimista, al crear un evento activo «Crear enlace de Meet», lo guardo y el evento queda con su link de Meet, sin pegar nada.

**Independent Test**: con la cuenta organizadora conectada y la Calendar API simulada, `POST /events` con `crearEnlaceMeet: true` devuelve el evento con `tipoUbicacion = MEET`, `valorUbicacion` = la URL `https://meet.google.com/…` y `meet.estado = CREADO`.

**Acceptance Scenarios**:

1. **Given** la cuenta conectada y el interruptor encendido, **When** guardo el evento, **Then** el servidor crea el evento en Renaser, pide la sala a Google, guarda el link en `valorUbicacion` y la app muestra «Unirme» como con cualquier link de Meet (sin cambios para el aprendiz).
2. **Given** que Google responde `pending`, **When** el servidor consulta el evento de Google unas veces seguidas dentro de un tope de tiempo, **Then** completa el link; si no llega, el evento queda `meet.estado = PENDIENTE` y un proceso en segundo plano lo completa (FR-006).
3. **Given** que activo «Crear enlace de Meet» **y** pego un link, **Then** el servidor responde 400 («Elige: crear el enlace de Meet o pegar uno.»).
4. **Given** que no hay cuenta conectada, **Then** el formulario ni muestra la opción (FR-014) y el servidor rechaza `crearEnlaceMeet` con 409 «Todavía no hay una cuenta de Google conectada.».

### User Story 2 - Si Google falla, el evento se crea igual y me avisan (Priority: P1)

**Acceptance Scenarios**:

1. **Given** que Google está caído o responde 5xx/429, **When** guardo el evento con Meet, **Then** el evento **se crea igual** (sin link), la respuesta trae `meet.estado = FALLIDO` con un mensaje en español claro, y la app dice «El evento se creó, pero no pudimos crear el enlace de Meet. Reintenta o pega uno.» con «Reintentar».
2. **Given** que el refresh token venció o fue revocado (`invalid_grant`), **Then** la cuenta pasa a `REVOCADA`, el intento falla sin reintentos inútiles y Administración recibe un aviso «La cuenta de Google de Renaser necesita volver a conectarse.» (FR-009).
3. **Given** un evento en `FALLIDO`, **When** Administración toca «Reintentar», **Then** se vuelve a pedir la sala con un `requestId` nuevo; si ya se había creado una sala sin que lo supiéramos, no queda duplicada (FR-007).

### User Story 3 - Edito o cancelo el evento y Google queda al día (Priority: P1)

**Acceptance Scenarios**:

1. **Given** un evento con Meet, **When** cambio el título, la fecha, la hora o la duración, **Then** el evento de Google se actualiza (`events.patch`) y **el link no cambia**.
2. **Given** un evento con Meet, **When** lo cancelo o lo borro, **Then** se borra el evento de Google (`events.delete`, sin avisar a nadie) y el link deja de estar asociado al evento de Renaser.
3. **Given** que Google falla al actualizar o borrar, **Then** la edición o el borrado en Renaser **se completan igual**, la sincronización queda `PENDIENTE` y se reintenta en segundo plano (FR-008); nunca se bloquea al usuario por Google.
4. **Given** un evento con Meet, **When** lo cambio a otra ubicación (Zoom, dirección, link pegado…), **Then** se borra el evento de Google y se limpia la asociación.

### User Story 4 - Conecto la cuenta de Google de Renaser una sola vez (Priority: P1)

Como Administración, conecto el Gmail de Renaser a la app (OAuth) una vez; desde entonces los eventos crean su Meet con esa cuenta.

**Acceptance Scenarios**:

1. **Given** que no hay cuenta, **When** Administración toca «Conectar cuenta de Google» y acepta en Google, **Then** el servidor guarda el refresh token **cifrado** y muestra «Conectada: renaserlab@…» con la fecha.
2. **Given** una cuenta conectada, **When** toca «Desconectar», **Then** se revoca el token en Google, se borra del servidor y las nuevas peticiones de Meet se rechazan hasta reconectar. Los links ya creados siguen existiendo en Google y en los eventos.
3. **Given** el estado de la cuenta, **Then** hay una pantalla simple con: conectada o no, el correo, la última vez que se usó y el último error.

### User Story 5 - El aprendiz no nota nada (Priority: P1)

Para el aprendiz, el mentor y el líder nada cambia: ven el evento con «Unirme» y el logo de Meet (`marcaDelLink`). No ven la palabra «automático», ni ninguna pantalla nueva.

### User Story 6 - La grabación (Priority: P3, decisión del dueño)

Ver «Fase 2». No se construye con esta spec.

## Requirements *(mandatory)* — Fase 1

### Funcionales

- **FR-001** Crear un evento puede pedir, con un campo nuevo `crearEnlaceMeet: true` en el pedido (`POST/PUT /api/v1/events`), que el servidor cree la sala de Meet: **primero** se guarda el evento en Renaser y **después** (fuera de la transacción de la base de datos) se llama a Google. Nunca se mantiene abierta una transacción esperando a Google.
- **FR-002** La sala se crea con la Calendar API: `events.insert` en el calendario principal de la cuenta organizadora con `conferenceDataVersion=1`, `conferenceData.createRequest.requestId` único (UUID) y `conferenceSolutionKey.type = "hangoutsMeet"`, `sendUpdates=none`. El evento de Google lleva solo `summary` (título), inicio y fin con su zona horaria y, si el evento de Renaser es recurrente, la regla de repetición equivalente (una sola sala para toda la serie). **No lleva asistentes** ni la descripción del evento: ningún dato de personas se envía a Google. Un conference **nuevo por evento**, nunca reutilizar un código (H2).
- **FR-003** El link devuelto (`hangoutLink`) se guarda en `valorUbicacion` con `tipoUbicacion = MEET`, validado por la misma regla `requireLinkWeb`, de modo que **el resto del sistema (listados, «Unirme», recordatorios, asistencia, ranking) lo trata como cualquier link de Meet pegado a mano**. Se guarda además el identificador del evento de Google en una tabla propia (FR-012).
- **FR-004** `crearEnlaceMeet` y un `valorUbicacion` pegado son **excluyentes** (400). Con `crearEnlaceMeet: true` el tipo de ubicación es `MEET`; con cualquier otro tipo el servidor responde 400.
- **FR-005** **Editar**: si cambian título, fecha/hora, duración, zona o recurrencia de un evento con Meet, el servidor actualiza el evento de Google (`events.patch`, `sendUpdates=none`); el link **no cambia**. Si se cambia a otra ubicación, se borra el evento de Google y se limpia la asociación. Si se enciende `crearEnlaceMeet` en un evento que no tenía Meet, se crea como en FR-002. **Cancelar o borrar** un evento (o una sola ocurrencia, `cancel-occurrence`) borra el evento de Google (o cancela esa instancia).
- **FR-006** **Respuesta `pending`**: tras el `insert`, si `conferenceData.createRequest.status.statusCode` no es `success`, el servidor consulta el evento (`events.get`) hasta 3 veces con 1 s de espera, dentro de un tope total de 8 s. Si no llega, el estado queda `PENDIENTE` y lo completa el reintento en segundo plano. Cada llamada a Google tiene tiempo máximo de 5 s.
- **FR-007** **Sin duplicados**: el `requestId` se guarda antes de llamar y se reutiliza si el mismo intento se repite (Google lo trata como idempotente); un «Reintentar» manual usa un `requestId` nuevo **solo si** el evento de Google no existe (se comprueba por el identificador guardado). Un doble toque manda un solo pedido.
- **FR-008** **Fallos y reintentos**. Si Google falla (red, 5xx, 429, `usageLimits`), el evento de Renaser **se crea, se edita o se borra igual**; el estado de la sala queda `FALLIDO` (crear) o `PENDIENTE_DE_SINCRONIZAR` (editar o borrar). Un proceso en segundo plano reintenta con espera exponencial y azar (1, 2, 4… hasta 15 minutos, máximo 8 intentos, deja de intentar cuando el evento ya pasó) y registra cada intento. La respuesta al usuario dice qué pasó, sin lenguaje técnico. «Reintentar» manual (`POST /api/v1/events/{id}/meet/retry`) solo está para ADMIN y ALCHEMIST.
- **FR-009** **Cuenta vencida o revocada** (`invalid_grant`, 401 con token renovado): la cuenta pasa a estado `REVOCADA`, **no se reintenta** (sería inútil), los eventos pendientes quedan `FALLIDO` con el motivo «Hay que volver a conectar la cuenta de Google», y se manda **un** aviso a los usuarios con permiso de administrar integraciones (sin spam: uno por día mientras siga revocada). Reconectar la cuenta reencola los pendientes de eventos que aún no pasaron.
- **FR-010** **Cuenta organizadora**: **una sola cuenta Gmail de Renaser** para toda la plataforma, conectada por OAuth 2.0 (flujo de código con `access_type=offline`, `prompt=consent`, PKCE y `state` firmado y de un solo uso, que ata la respuesta a quien inició la conexión). El permiso (scope) pedido es **el mínimo que alcance**; la prueba previa (SC-001) compara `calendar.app.created` y `calendar.events` y se usa el menor que funcione (H3). Conectar y desconectar: ADMIN y ALCHEMIST; solo el permiso, nunca el correo ni la contraseña del Gmail, pasan por la plataforma.
- **FR-011** **Secretos**. El refresh token se guarda **cifrado** (AES-256-GCM, un nonce por valor) en la base; la clave de cifrado vive fuera de la base y fuera del repositorio (variable de entorno cargada desde SSM, como el resto de secretos del servidor), y hay un identificador de versión de clave para poder rotarla. El `client_secret` de OAuth también va en el entorno, nunca en el repositorio. **Ningún registro (log) contiene tokens, el `client_secret`, el código de OAuth ni el link completo de Meet.** Un access token vive solo en memoria y se renueva con el refresh token. Una prueba busca tokens en el registro y falla si aparece alguno.
- **FR-012** **Datos** (migración nueva, sin tocar la tabla `eventos` más que lo necesario): (a) `integracion_google` — una fila: correo de la cuenta, `refresh_token` cifrado, versión de clave, scopes concedidos, estado (`ACTIVA`/`REVOCADA`), conectada por, cuándo, último uso, último error; (b) `eventos_meet` — por evento: identificador del evento de Google, estado (`CREADO`, `PENDIENTE`, `FALLIDO`, `PENDIENTE_DE_SINCRONIZAR`, `BORRADO`), `requestId`, intentos, próximo intento, último error (texto corto, sin datos personales); (c) `integracion_google_auditoria` — ver FR-016. Todo con `version` para concurrencia optimista donde corresponda.
- **FR-013** **Cuotas**. Se respeta el límite de Google (600 pedidos por minuto, H11) con un limitador propio más estricto (por ejemplo 30 por minuto) y espera exponencial ante `403/429 usageLimits`. Un evento recurrente es **un** evento de Google, no uno por ocurrencia. Con el uso previsto (decenas de eventos al mes) la cuota no se acerca; el limitador existe para que un error de bucle no agote la cuenta.
- **FR-014** **Disponibilidad**. Un endpoint `GET /api/v1/events/meet/status` (solo ADMIN/ALCHEMIST) dice si la opción está disponible: cuenta `ACTIVA` e interruptor de Soporte TI `eventos.meet` prendido. Con cualquiera de las dos cosas apagada, la app **no dibuja** la opción y el servidor responde 409 (cuenta) o 404 `FUNCIONALIDAD_NO_DISPONIBLE` (interruptor). Es una funcionalidad más del catálogo de Soporte TI, hija de `eventos`, **prendida de fábrica solo si hay cuenta conectada**.
- **FR-015** **Quién la usa**: crear, editar y borrar eventos con Meet — `MANAGE_CALENDAR` (ADMIN y ALCHEMIST), igual que hoy (D-186). Mentor, guía, líder y aprendiz ven el evento como siempre. Conectar, desconectar y ver el estado de la cuenta — ADMIN y ALCHEMIST.
- **FR-016** **Auditoría**. Cada acción deja una fila: crear/actualizar/borrar sala, reintento, conectar, desconectar, cuenta revocada; con quién (`actor` o «sistema»), cuándo, el evento, el resultado, el código HTTP de Google (no el cuerpo de la respuesta) y un identificador de correlación. Retención: 12 meses. **Sin tokens y sin el link completo.** Visible para ADMIN y ALCHEMIST en la misma pantalla de la cuenta.
- **FR-017** **Ocurrencias recurrentes**. Un evento recurrente comparte **una sola sala** en toda su serie (así funciona Calendar). Cancelar una ocurrencia cancela esa instancia en Google; cambiar la regla de repetición actualiza la regla. Si Google rechaza la regla equivalente, el estado queda `FALLIDO` con su motivo (no se crea un evento de Google sin repetición que no coincida con el de Renaser).
- **FR-018** **Zonas horarias**. Los instantes se envían a Google con la zona del evento (`ZoneId`), nunca con la del servidor; las reglas del repositorio sobre tiempo (`02-tiempo-zonas-y-schedulers`) valen para el proceso de reintentos.
- **FR-019** **Lo que ve la app** (`src/features/eventos/`): en el formulario de crear/editar evento, un interruptor «Crear enlace de Meet» (en el lugar del campo del link, que se esconde al encenderlo) **solo si el servidor dice que está disponible**; un evento con la sala en `FALLIDO` o `PENDIENTE` muestra a ADMIN/ALCHEMIST una línea corta con «Reintentar»; el aprendiz no ve nada nuevo (`Unirme` y `marcaDelLink` no cambian). Sin pantallas nuevas fuera de «Yo → Administración → Integraciones → Google» para conectar y ver el estado. Sin animaciones nuevas. *Cambia el formulario de Comunidad → Eventos: autorizado por el pedido del dueño del 2026-11-04.*
- **FR-020** **Compatibilidad**: un APK anterior contra el backend nuevo sigue funcionando (el campo nuevo es opcional); la app nueva contra un backend anterior no ve la opción (el estado responde 404).
- **FR-021** **Aviso honesto de los límites de Meet gratis** (H8, H9): al activar «Crear enlace de Meet», una línea corta dice «Con la cuenta gratuita de Meet, las sesiones de 3 o más personas se cortan a los 60 minutos y tú debes admitir a quienes entren.» cuando la duración del evento supera 60 minutos. No bloquea. *[sin prueba automática: la redacción final del aviso la aprueba el dueño]*
- **FR-022** **Privacidad de la Fase 1**: a Google solo llegan título, fecha, hora, zona y duración del evento; nunca nombres, correos, audiencia ni descripción (FR-002). Los participantes que entren a la sala quedan sujetos a las condiciones de Google Meet.

### Criterios de éxito

- **SC-001** **Prueba de viabilidad previa a construir**, con el Gmail real de Renaser y el proyecto de Google Cloud ya creado: (a) `calendars.get`/`calendarList` muestra `hangoutsMeet` entre los tipos permitidos; (b) un `events.insert` con `createRequest` devuelve `hangoutLink` y estado `success`; (c) con `calendar.app.created` o, si no alcanza, `calendar.events`; (d) un segundo teléfono entra al link y el anfitrión lo admite; (e) se anota el tiempo real de respuesta. Si (b) falla, esta spec **se detiene** y se informa al dueño antes de seguir. *[sin prueba automática: es una prueba manual con la cuenta real; el resultado se anota en el PR]*
- **SC-002** Con la cuenta conectada, crear un evento con «Crear enlace de Meet» deja el link en el evento en menos de 10 s en el 95 % de los intentos.
- **SC-003** Si Google falla o está caído, el evento se crea en el 100 % de los casos y la persona ve un mensaje claro; nunca se pierde un evento por un fallo de Google.
- **SC-004** Editar o cancelar un evento con Meet refleja el cambio en Google en el siguiente intento exitoso (máximo 15 minutos con reintentos) sin bloquear la edición.
- **SC-005** Un refresh token vencido o revocado se detecta en el primer uso, no genera más de un aviso por día y no provoca reintentos en bucle.
- **SC-006** Ningún token, secreto, código de OAuth o link completo aparece en los registros, ni la base guarda el refresh token en claro (prueba automática).
- **SC-007** Para el aprendiz, el mentor y el líder no cambia ninguna pantalla ni respuesta del servidor existente.
- **SC-008** Solo ADMIN y ALCHEMIST pueden crear/editar/borrar con Meet, ver el estado de la cuenta, conectarla o desconectarla; mentor, guía, líder y aprendiz reciben 403.
- **SC-009** Con el interruptor `eventos.meet` apagado, la opción desaparece de la app y el servidor responde 404 `FUNCIONALIDAD_NO_DISPONIBLE`.
- **SC-010** Cada acción de Fase 1 deja su fila de auditoría con actor, evento, resultado y código de Google.

## Fase 2 — La grabación automática (DECISIÓN DEL DUEÑO, no se construye con esta spec)

### Lo que hay que dejar claro

**Una reunión de Meet no se puede grabar automáticamente sin Workspace** (H6, H7). La opción de pagar «una sola licencia de Workspace» **está descartada por decisión del dueño** y no se ofrece. Grabar implica **cambiar de herramienta de videollamada para las sesiones que se graben**. Eso es una decisión de producto grande: no es un parche sobre Meet.

### Condiciones para comparar

Los costos son **estimaciones del autor con precios públicos del 2026-11-04**, no cotizaciones. Se supone un uso de ejemplo: **8 sesiones al mes, de 90 minutos, con 100 personas conectadas** (720 minutos de sesión, 72 000 minutos-persona al mes). El dueño debe corregir esos números; los costos escalan casi todos con los minutos-persona. «Quién la ve» es una pregunta abierta para el dueño (P3).

### Cómo llegaría la grabación al evento («Ver grabación»)

En las cuatro opciones es igual de simple en la app: cuando la grabación está lista, el servidor guarda su **enlace o archivo** en el evento (`grabacionUrl`, estado `PROCESANDO`/`LISTA`/`FALLIDA`) y, solo a las personas autorizadas, la app muestra el botón **«Ver grabación»** en el detalle del evento. Cambia **dónde vive el archivo** y **cómo se entera el servidor**:

| | (b) Videollamada propia en Google Cloud | (c) Amazon Chime SDK | (d) Zoom Pro |
|---|---|---|---|
| **Qué es** | LiveKit (Egress) o Jitsi (Jibri) en una VM o GKE de Google Cloud | Servicio de videollamada de AWS (donde ya está el backend) | Reuniones de Zoom con grabación en la nube |
| **¿Quién hace la videollamada?** | **Renaser**: la sala vive dentro de la app (hay que construir la pantalla de la llamada, con cámara, micrófono, chat y pantalla compartida) | **Renaser**: igual, hay que construir la pantalla de la llamada con el SDK | **Zoom**: se abre su app o su web; en Renaser solo se crea la reunión y se muestra «Unirme». `TipoUbicacion.ZOOM` ya existe |
| **Grabación automática** | Sí: LiveKit «auto egress» al crear la sala ([docs](https://docs.livekit.io/home/egress/overview/)); Jibri graba por persona-sala y necesita su propio script para subir el archivo | Sí: *media capture pipelines* hacia S3 ([precios](https://aws.amazon.com/chime/chime-sdk/pricing/)) | Sí: `settings.auto_recording = "cloud"` al crear la reunión por API (a confirmar en la referencia de Zoom en el spike) |
| **Dónde queda el archivo** | **Cloud Storage** (LiveKit lo soporta; Jibri necesita un script) | **S3** (la misma cuenta de AWS del backend) | **Nube de Zoom** (10 GB por usuario en Pro) o bajarla a Cloud Storage/S3 con un trabajo |
| **Costo mensual aproximado (con el ejemplo)** | **LiveKit Cloud (gestionado), plan Ship: US$ 50** (150 000 min-persona incluidos; 600 min de grabación incluidos, ~US$ 3 de exceso) → **~US$ 55–70** + almacenamiento. **LiveKit autohospedado en Google Cloud**: 1–2 VM de 4+ vCPU (~US$ 100–250) + salida de red (~US$ 50–100) → **~US$ 150–350**. **Jitsi/Jibri**: una VM por grabación simultánea + JVB → **~US$ 150–350** y más mantenimiento | Video: 72 000 × US$ 0,0017 = **~US$ 122**; grabación 1080p compuesta: 720 min × US$ 0,0125 = **~US$ 9** + unión de segmentos ~US$ 1,5 + S3 → **~US$ 135–145** | **Zoom Workplace Pro: US$ 14,16/mes (anual) o 16,99 (mensual)** por anfitrión, hasta **100 participantes**, reuniones de hasta 30 h ([precios](https://zoom.us/en/pricing)). Más de 100 personas exige el complemento de «reuniones grandes» (costo adicional, a cotizar) |
| **Esfuerzo de construcción** | **Alto**: pantalla de videollamada completa (cámara/audio en el móvil y la web), servidor LiveKit, monitoreo, calidad en redes móviles, token por sala, escalado; es otro producto. Estimación de equipo: semanas de trabajo, no días | **Alto**: SDK nativo para móvil (otro módulo nativo y APK nuevo), lógica de reuniones y asistentes, pipelines; hay que verificar el tope de asistentes por reunión | **Bajo–medio**: integración por API (crear reunión, leer grabaciones), sin pantalla propia; la persona usa la app de Zoom. Estimación: pocos días |
| **Control y privacidad** | Máximo: todo en cuentas de Renaser | Alto: en la cuenta AWS de Renaser | Medio: el video pasa y se guarda en Zoom |
| **Riesgos** | Complejidad operativa; si falla la sala, falla la sesión; permisos de cámara/micrófono en móviles de gama baja | Igual que (b) + dependencia del SDK; el producto **Chime** de AWS ha cambiado de alcance antes (verificar vigencia del SDK) | Pagar por anfitrión; tope de 100 participantes; el aprendiz instala/abre Zoom |
| **Qué pasa con la Fase 1 (enlace de Meet)** | Se mantiene solo para sesiones sin grabar | Ídem | Se podría **reemplazar** por reuniones de Zoom creadas por API (un solo flujo: enlace y grabación) |

**Lectura corta para decidir (no es una recomendación cerrada):** si lo que importa es **tener grabación pronto y con poco trabajo**, (d) Zoom Pro es lo único que se construye en días; si importa que **todo quede dentro de Renaser y de la nube propia**, (b) o (c) funcionan pero cuestan semanas de trabajo y mantenimiento y convierten a Renaser en una plataforma de videollamadas; (c) tiene la ventaja de estar en AWS, donde ya vive el backend, y (b) la de estar en Google Cloud, que es lo que el dueño pidió. La elección depende de las preguntas P2–P4 de abajo.

### Requisitos condicionales de la Fase 2 (valen solo cuando el dueño elija)

- **FR-030** Un evento puede marcar «Grabar la sesión»; la grabación empieza sola al abrirse la sala y queda como `grabacionUrl` + estado en el evento.
- **FR-031** «Ver grabación» solo la ven las personas que decida el dueño (pregunta P3) y se entrega por un enlace firmado de corta duración, nunca público.
- **FR-032** **Consentimiento**: antes de entrar a una sesión que se graba, la persona ve un aviso claro («Esta sesión se graba») y lo acepta; la sala indica que se está grabando. Retención y borrado de las grabaciones definidos por el dueño (pregunta P5).
- **FR-033** Tope de tamaño/tiempo y borrado de las grabaciones al eliminar la cuenta o el evento.
- **SC-030** Una sesión marcada «Grabar» deja su grabación visible en el evento a los 30 minutos de terminar, en el 95 % de los casos.

## Lo que el dueño debe hacer en Google Cloud (paso a paso, para la Fase 1)

Todo con **el Gmail de Renaser** (el que organizará las sesiones). No hace falta tarjeta de crédito para la Calendar API: no tiene costo por uso en este volumen.

1. Entra a <https://console.cloud.google.com/> con ese Gmail y acepta los términos.
2. Arriba, **Seleccionar proyecto → Proyecto nuevo**. Nombre: «Renaser Eventos». Sin organización. **Crear**.
3. Menú **APIs y servicios → Biblioteca**. Busca **«Google Calendar API»** y toca **Habilitar**.
4. **APIs y servicios → Pantalla de consentimiento de OAuth** (en la consola nueva se llama *Google Auth Platform → Marca*):
   - Tipo de usuario: **Externo** (con un Gmail no existe «Interno»).
   - Nombre de la app: «Renaser». Correo de asistencia: el Gmail. Correo del desarrollador: el Gmail.
   - **Permisos (scopes)**: agrega el que diga el equipo tras la prueba (SC-001): `…/auth/calendar.app.created` o `…/auth/calendar.events`.
   - **Publicación: pásala a «En producción»** (botón *Publicar app*). **Esto es importante**: en estado **«Prueba»** el permiso que se da a la app **vence cada 7 días** y las salas dejarían de crearse (H10).
   - **Verificación de Google**: `calendar.events` figura como permiso **sensible**; para una app que usa **una sola cuenta propia** no hace falta pasar la verificación completa: Google mostrará «Google no ha verificado esta app» al conectar y se continúa con *Avanzado → Ir a Renaser (no seguro)*. Esto es lo habitual en integraciones internas, pero **la condición exacta (límite de usuarios sin verificar, si el permiso elegido es sensible) la confirma el equipo en la consola durante la prueba SC-001**; si Google exigiera verificación, el equipo prepara lo que pida (dominio, política de privacidad y vídeo de la demostración) y se avisa al dueño.
5. **APIs y servicios → Credenciales → Crear credenciales → ID de cliente de OAuth**:
   - Tipo: **Aplicación web**. Nombre: «Renaser servidor».
   - **URI de redireccionamiento autorizado**: la dirección del backend que le dará el equipo (por ejemplo `https://api.…/api/v1/admin/integraciones/google/callback`) y otra de desarrollo.
   - Crea y **descarga el JSON**. Contiene el `client_id` y el `client_secret`. **Es un secreto: no lo mandes por chat ni correo ni lo subas a GitHub**; se entrega al equipo por el canal acordado para guardarlo en el servidor.
6. **Autorizar la cuenta**: cuando la función esté construida, en la app: Yo → Administración → Integraciones → Google → **Conectar cuenta de Google**, inicia sesión con el Gmail de Renaser y acepta el permiso. Se hace **una sola vez**.
7. Verifica en <https://myaccount.google.com/permissions> que «Renaser» aparece con el permiso. Si algún día cambias la contraseña del Gmail o quitas el acceso ahí, hay que **volver a conectar** (la app avisa).
8. Recomendado: activa la **verificación en dos pasos** de ese Gmail y guarda sus códigos de recuperación en un lugar seguro: de esa cuenta depende la creación de todas las salas.

## Privacidad: qué sumar a la Política de privacidad

Para la **Fase 1** (mínimo, antes de publicar): una frase en la sección de servicios de terceros: «Para crear el enlace de las sesiones en línea usamos Google (Calendar y Meet). A Google solo le enviamos el título, la fecha, la hora y la duración del evento; no le enviamos tus datos personales. Cuando entras a una sesión por Meet, Google trata tus datos según sus propias condiciones.»

Para la **Fase 2** (solo si el dueño elige grabar; **debe publicarse antes del APK**):
- Que las sesiones marcadas se **graban en audio y video** (incluyendo la voz, la imagen y lo que se comparta o escriba en la sala) y para qué (consulta de quienes no pudieron asistir, calidad del programa).
- **Consentimiento previo y visible** antes de entrar a una sesión grabada, con la alternativa de no entrar; el aviso permanente de «grabando».
- Quién puede ver la grabación, por cuánto tiempo se conserva, dónde se guarda y el proveedor (Google Cloud, AWS o Zoom, según lo elegido; la política ya nombra a los proveedores de IA y debe nombrar este).
- Cómo ejercer los derechos (acceso, rectificación, supresión) sobre las grabaciones en las que aparece la persona, con el correo de contacto que ya figura en la política (Ley de Protección de Datos Personales del Perú).
- Que la persona **no debe compartir datos sensibles** en una sesión grabada.

## Preguntas abiertas para el dueño

1. **P1 — El límite de 60 minutos.** Una reunión de Meet gratuita con 3 o más personas **se corta a los 60 minutos** y quien organiza debe **admitir** a cada persona que entra. ¿Tus sesiones duran menos de una hora, o necesitas que duren más? Si necesitas más de 60 minutos o más de unas pocas personas con comodidad, **Meet gratis no alcanza** y la Fase 1 pierde mucho valor (en ese caso Zoom Pro resuelve enlace, duración y grabación juntos).
2. **P2 — Tamaño de las sesiones.** ¿Cuántas sesiones al mes, de cuánto tiempo y con cuántas personas conectadas a la vez (50, 100, 300)? Define el costo y si Zoom Pro (hasta 100 personas) alcanza.
3. **P3 — Quién ve las grabaciones.** ¿Todo el que estaba invitado al evento, solo quien asistió, también el mentor/guía y el líder, o solo Administración? ¿Se ven para siempre o por un tiempo?
4. **P4 — ¿Construir o contratar?** Para grabar: ¿prefieres lo rápido y barato (Zoom Pro, la sesión sale de Renaser) o que la videollamada viva dentro de Renaser (propia en Google Cloud o Chime SDK, semanas de trabajo y mantenimiento)?
5. **P5 — Consentimiento y retención.** ¿Cuánto tiempo se guardan las grabaciones antes de borrarse (30, 90 días, un año)? ¿Se acepta el aviso de «Esta sesión se graba» como requisito para entrar?
6. **P6 — Quién tendrá la cuenta de Google.** ¿Quién queda a cargo del Gmail de Renaser (contraseña, verificación en dos pasos, recuperación)? De esa cuenta dependen todas las salas.
7. **P7 — Eventos recurrentes.** ¿Los eventos repetidos (por ejemplo, cada martes) usan **el mismo link** en toda la serie (así funciona Google) o necesitas un link distinto por semana? Con la Fase 1 propuesta, el link es el mismo para toda la serie.
8. **P8 — Para ver tras la prueba SC-001.** Si la prueba con tu Gmail muestra que Google exige verificar la app o que el Gmail no permite crear Meet por API, ¿pausamos esta función?
9. **P9 — Suscripciones personales de Google.** La ayuda oficial de grabación menciona también las suscripciones de Google One de 2 TB o más entre las cuentas que pueden grabar. Tú dijiste **nada de Workspace**; esta spec **no** usa nada de eso. Si quisieras que el equipo verifique si una suscripción de Google One permitiría la grabación automática de Meet (no está comprobado que lo permita por API), dilo; **no se hará salvo pedido tuyo**.

## Assumptions

- Se usa **una sola cuenta organizadora** para toda la plataforma; el volumen de eventos es de decenas al mes.
- El Gmail soporta `hangoutsMeet` en Calendar (**por confirmar en SC-001**).
- La Fase 1 se construye aparte de la Fase 2; la Fase 2 no bloquea a la Fase 1.
- Esta spec añade **una** decisión nueva: **D-321** (por confirmar el número al integrar, `docs/MODULOS_A_AVANZAR.md`).
- La trazabilidad (`specs/trazabilidad.json`) **no se toca en esta spec**: entra a la lista en el mismo cambio que le ponga sus marcas.

## Referencias

- Calendar API: [crear eventos](https://developers.google.com/workspace/calendar/api/guides/create-events), [events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert), [cuotas](https://developers.google.com/workspace/calendar/api/guides/quota), [notas de versión](https://developers.google.com/calendar/releases).
- Meet: [grabar](https://support.google.com/meet/answer/9308681), [funciones y límites](https://support.google.com/meet/answer/13396001), [REST API: espacios](https://developers.google.com/workspace/meet/api/reference/rest/v2/spaces), [novedades](https://developers.google.com/workspace/meet/release-notes).
- OAuth: [vencimiento de tokens](https://developers.google.com/identity/protocols/oauth2#expiration), [verificación de apps](https://support.google.com/cloud/answer/13463073).
- Alternativas: [LiveKit Egress](https://docs.livekit.io/home/egress/overview/), [LiveKit autohospedado](https://docs.livekit.io/transport/self-hosting/egress/), [LiveKit precios](https://livekit.com/pricing), [Jibri](https://github.com/jitsi/jibri), [Chime SDK precios](https://aws.amazon.com/chime/chime-sdk/pricing/), [Zoom precios](https://zoom.us/en/pricing).
