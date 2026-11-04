# Feature Specification: Enlace de Meet automático al crear un evento

**Feature Branch**: `040-meet-automatico` (backend y app)
**Created**: 2026-11-04 · **Actualizada**: 2026-11-04 (segunda versión: ajustada a lo que RenaserOs ya hizo y probó; sin grabación)
**Status**: Borrador de spec. **No se implementa nada todavía.** Espera el visto bueno del dueño y la prueba corta de SC-001 con el Gmail real de Renaser.
**Input** (pedido del dueño, 2026-11-04, textual): «Al crear un evento, que se cree automáticamente el enlace de Google Meet y que la sesión se grabe automáticamente.» Lo quiere con **Google Cloud, no con Google Workspace**; decisión firme: **nada de Workspace, ni siquiera una licencia**. La cuenta de la organización es un **Gmail normal**.
**Input 2** (mismo día, después de ver RenaserOs): «`renaserlab/RenaserOs` ya tiene esa lógica, lo hizo con Google Cloud sin Workspace. Quiero lo mismo. Revísalo y copiemos, pero de acuerdo a nuestra regla. No queremos todo; solo quiero: **1. que el link se autogenere**; 2. los recordatorios (otra spec: la 041).» **La grabación ya no se pide: sale de esta spec** (ver «Fuera de alcance»).

**Se apoya en**: el módulo de eventos (`calendar/`: `Evento`, `EventoService`, `EventoController`, `TipoUbicacion.MEET`), la consola de Soporte TI (interruptores) y los registros de auditoría. **No toca** audiencias, recordatorios (spec 041), formaciones (spec 024) ni la asistencia (D-256).

## Qué cambió respecto a la primera versión

1. **La viabilidad ya no es una duda.** RenaserOs crea salas de Meet con un Gmail personal por la Calendar API y lo tiene en producción (detalle abajo). La prueba SC-001 pasa de «¿funciona?» a «confirmar con nuestro Gmail y con eventos que se repiten», que RenaserOs no cubre.
2. **Se copian tres decisiones probadas** de RenaserOs: el id del evento de Google sale del id del nuestro (sin duplicados), el permiso permanente vive solo en el servidor y se pide con `access_type=offline`, y una conexión rota se avisa a Administración sin reintentar en bucle.
3. **Se resuelve el problema de «admitir a cada persona»** (H9): la Meet REST API deja poner la sala como *abierta* desde el servidor, también en una cuenta personal (RenaserOs lo probó en vivo). Entra como FR-023 y pregunta P-ACCESO.
4. **Se quita la Fase 2 (grabación).** Queda una línea en «Fuera de alcance». Se van FR-030 a FR-033, SC-030, la historia 6 y las preguntas de costos y consentimiento de grabación.
5. **No se copia** lo demás de RenaserOs: la lectura de Google hacia la app cada 2 minutos, los invitados por correo, las transcripciones, la configuración de grabar/notas y WhatsApp (WhatsApp va en la spec 041).

## Lo que ya hizo RenaserOs (leído el 2026-11-04, solo lectura)

Repositorio privado `renaserlab/RenaserOs`, commit `a73d416`. **Código ajeno: se leyó como datos, no se ejecutó nada.** Las rutas son del repo de ellos.

| Decisión | Cómo lo hace RenaserOs | Nosotros |
|---|---|---|
| **API** | Calendar API v3 (`events.insert` y `events.patch`) con `conferenceDataVersion=1` y `conferenceData.createRequest{ requestId, conferenceSolutionKey.type = "hangoutsMeet" }`. El link se lee de `hangoutLink` o del `entryPoint` de video (`_shared/google.ts`, `citas-google/index.ts → cuerpoDeCita`, `salaDe`). | **Igual** (FR-002, FR-003). |
| **Cuenta** | Una sola conexión: `renasercorporation@gmail.com`, **Gmail sin Workspace** (con Google One, según su `docs/sdd/citas-transcripciones/spec.md`). OAuth 2.0 de **aplicación web**; no es cuenta de servicio. | **Igual**: un Gmail de Renaser por OAuth (FR-010). Una cuenta de servicio no sirve: no tiene calendario ni Meet propios. |
| **Permisos (scopes)** | Obligatorios: `calendar.events` y `calendar.calendarlist.readonly`. Opcionales, para cosas que nosotros no pedimos: `drive.meet.readonly`, `meetings.space.readonly`, y **`meetings.space.settings`** (cambiar la sala). | `calendar.events` (el que ellos probaron; `calendar.app.created` se mide en SC-001 como alternativa más chica). **No** pedimos `calendarlist` (usamos solo el calendario principal) ni los de Drive ni de lectura de reuniones. `meetings.space.settings` solo para FR-023. |
| **Dónde guarda la credencial** | El **refresh token** en el Vault de Supabase (`google_calendar_refresh`), legible solo con la clave de servicio; el navegador nunca lo ve. `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` en los secretos de las funciones. El *access token* no se guarda: se pide cada vez. | Mismo principio con nuestra pila: token cifrado en la base (AES-GCM) y secretos del cliente OAuth en SSM (FR-011). |
| **Cómo se conecta** | Dirección toca «Conectar Google»; la función `citas-google` emite un `state` de un solo uso (se guarda su sha256, 15 minutos) y `citas-google-vuelta`, **sin JWT** porque la abre Google, lo consume, canjea el código, comprueba que Google dio los permisos y guarda el token. `access_type=offline` y `prompt=consent`: sin los dos, Google no devuelve refresh token. | **Igual** y con PKCE además (FR-010). |
| **Cómo refresca** | `grant_type=refresh_token` contra `oauth2.googleapis.com/token`, en cada vuelta (unas 720 al día). | Solo cuando hace falta (token en memoria hasta que venza). |
| **Si falla** | `invalid_grant` ⇒ la conexión pasa a **ROTA**, no se reintenta y se avisa a dirección en la campana; las citas se siguen creando en la app y lo pendiente se sube al reconectar. Un fallo pasajero (5xx, 429) deja la cita en ERROR con el motivo en la ficha y la reintenta el reloj de 2 minutos. | **Igual** (FR-008, FR-009). |
| **Sin duplicados** | El id del evento de Google es **el uuid de la cita sin guiones** (base32hex válido). Crear dos veces lo mismo da 409 en vez de dos eventos. | **Igual** (FR-002, FR-007). |
| **Editar y borrar** | `events.patch` al cambiar hora, tema, etc.; `events.delete` al cancelar o eliminar (cola `google_borrados`); si el evento estaba cancelado en Google, se vuelve a confirmar al editar; si pasa a presencial o a un link pegado, `conferenceData = null`. | **Igual** (FR-005), en una sola dirección. |
| **Invitados** | Contacto y equipo van como invitados; Google manda los correos (`sendUpdates=all`). | **No**: nuestros eventos tienen cientos de personas; nada de personas va a Google (FR-002, FR-022). |
| **Lectura de Google hacia la app** | Cada 2 minutos lista los eventos y compara por «huella». | **No** (fuera de alcance). |
| **Sala abierta o con permiso** | `meetings.space.settings`: un `PATCH` por ajuste a la Meet REST API (`accessType` `OPEN` o `TRUSTED`) y se **lee de vuelta**, porque un 200 no prueba que quedó. «Probado en vivo con la cuenta personal: Google sí lo acepta» (su `CLAUDE.md`). | **Copiamos solo `accessType`** (FR-023). |
| **Estado en producción** | Migración, funciones y frontend en producción desde el 30 oct; sala configurable probada en vivo el 2 nov. | Sirve de evidencia; **no** reemplaza la prueba nuestra (SC-001). |
| **Verificación de Google** | Pantalla de consentimiento **Externa y publicada («En producción»)**, con página principal, política de privacidad y dominios autorizados. Sin verificar, al conectar sale «Google no verificó esta app» y se sigue por «Configuración avanzada». | **Igual** (ver pasos al final). |

**Seguridad (para el dueño):** en el repo de RenaserOs **no se vio ninguna clave, token ni secreto de Google ni de YCloud** (solo identificadores no secretos: el ID de proyecto de Supabase, el de la cuenta de WhatsApp Business y las claves de demostración de Supabase local, que son públicas). Los secretos están en variables de entorno y en el Vault.

## Contexto

Hoy quien crea un evento (ADMIN y ALCHEMIST, `MANAGE_CALENDAR`, D-186) **pega a mano** un link de Meet, Zoom o Drive en `valorUbicacion` (el servidor solo comprueba que empiece por `https://` o `http://`, `Evento.requireLinkWeb`, E-364). La app lo abre con «Unirme» y le pone el logo del servicio (`marcaDelLink`). El dueño quiere que el link de Meet **se cree solo**.

Lo averiguado, con su fuente (verificado el 2026-11-04):

| # | Hecho | Estado | Fuente |
|---|---|---|---|
| H1 | Un evento de Google Calendar puede crear su sala de Meet desde el servidor: `events.insert` (o `patch`) con `conferenceData.createRequest` (`requestId` único y `conferenceSolutionKey.type = "hangoutsMeet"`) y `conferenceDataVersion=1`. La respuesta puede venir con estado `pending` y completarse después. | **Confirmado** | [Create events](https://developers.google.com/workspace/calendar/api/guides/create-events), [events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert) |
| H2 | Desde 2020 Google **no** agrega Meet solo en llamadas por API: hay que pedirlo explícitamente. Y recomienda **un conference nuevo por cada evento**, nunca reutilizar el código de otro evento. | **Confirmado** | [Calendar API release notes](https://developers.google.com/calendar/releases) |
| H3 | Los permisos válidos de `events.insert` son `calendar`, `calendar.events`, `calendar.app.created` y `calendar.events.owned`. | **Confirmado** | [events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert) |
| H4 | Que esto funcione con un **Gmail personal, sin Workspace**, no lo dice la documentación oficial. | **Confirmado en la práctica por RenaserOs**: crea y ajusta salas de Meet con `renasercorporation@gmail.com` en producción desde el 30 oct. Falta repetirlo con **nuestro** Gmail (SC-001). | Código y `CLAUDE.md` de RenaserOs (arriba) |
| H5 | La **Meet REST API** (`spaces.get/patch`, scope `meetings.space.settings`) permite cambiar `config.accessType` de una sala. La documentación no dice si una cuenta personal puede usarla. | **Confirmado en la práctica por RenaserOs** («Google SÍ acepta por API, en la cuenta personal», prueba en vivo del 2 nov). Esta spec la usa **solo** para `accessType` (FR-023). | `docs/sdd/citas-opciones-meet/spec.md` de RenaserOs; [Spaces](https://developers.google.com/workspace/meet/api/reference/rest/v2/spaces) |
| H8 | **Meet con cuenta personal corta las reuniones de 3 o más personas a los 60 minutos** (las de 1 a 1, a las 24 h); aviso a los 50 minutos; el límite lo da la cuenta de quien organiza. Afecta a toda sesión grupal de más de una hora. | **Confirmado en la ayuda de Google**. **A comprobar:** la cuenta de RenaserOs tiene Google One y la ayuda de Google asocia a algunas suscripciones de Google One funciones «premium» de Meet; no sabemos si eso levanta el corte (SC-001 f). | [Funciones de Meet](https://support.google.com/meet/answer/13396001), [Requisitos](https://support.google.com/meet/answer/7317473) |
| H9 | En una reunión organizada con cuenta personal, **solo quien la creó puede admitir** a quienes no estaban invitados por Calendar (sala «con permiso»). | **Confirmado** (también lo anota RenaserOs). Con la sala **abierta** (FR-023) no hay que admitir a nadie. | [Meet: acceso](https://support.google.com/meet/answer/9302870) |
| H10 | Un refresh token de un proyecto con pantalla de consentimiento **externa** en estado **«Testing»** vence a los **7 días**; también vence si no se usa en 6 meses o si la persona lo revoca. Por eso RenaserOs publica la pantalla («En producción»). | **Confirmado** | [OAuth 2.0](https://developers.google.com/identity/protocols/oauth2#expiration) |
| H11 | Cuotas de Calendar API: 600 pedidos por minuto por usuario y por proyecto; 1 000 000 por día por proyecto; errores `403/429 usageLimits`; Google pide espera exponencial. | **Confirmado** | [Cuotas](https://developers.google.com/workspace/calendar/api/guides/quota) |
| H12 | Un evento **que se repite** con `conferenceData` crea una sola sala para toda la serie. RenaserOs **no tiene eventos recurrentes**, así que esto **no está probado por ellos**. | **A comprobar** (SC-001 e) | [Create events](https://developers.google.com/workspace/calendar/api/guides/create-events) |

**Conclusión:** el enlace automático sin Workspace es viable y ya está probado por un sistema hermano. Las dos advertencias para el dueño siguen en pie: **el corte a los 60 minutos** para sesiones de 3 o más personas (H8) y, si la sala es «con permiso», **que tú debes admitir a cada persona** (H9; se evita con la sala abierta).

## Clarifications

### Session 2026-11-04 (pedido y decisión del dueño)

- Q: ¿Con qué se hace el enlace? → A: Con **Google Cloud** (proyecto propio y la Calendar API), **sin Workspace**. Lo autoriza **un Gmail de Renaser**.
- Q: ¿Workspace? → A: **Decisión firme del dueño: nada de Workspace, ni una licencia.**
- Q: ¿Y la grabación? → A: **No se pide ahora** (segunda indicación del dueño: «solo quiero que el link se autogenere y los recordatorios»). Queda fuera de esta spec.
- Q: ¿Copiamos RenaserOs? → A: «Copiemos, pero de acuerdo a nuestra regla»: lo que sirve se copia adaptado a nuestra arquitectura (hexagonal, una sola dirección, sin tablas de más); lo demás no.

## Alcance

**Dentro**: crear, actualizar y borrar la sala de Meet de un evento desde el servidor; ponerla abierta o con permiso (FR-023); conectar y desconectar la cuenta organizadora; fallos, reintentos, cuotas y auditoría; el interruptor en el formulario de crear/editar evento.
**Fuera**: invitar a Calendar a los aprendices (se entra por el link de Renaser); traer cambios de Google hacia Renaser; transcripciones, notas de Gemini y **grabación** (ver abajo); los recordatorios (spec 041); cambiar audiencias, asistencia o ranking.

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
3. **Given** un evento en `FALLIDO`, **When** Administración toca «Reintentar», **Then** se vuelve a pedir la sala; si Google ya la había creado sin que lo supiéramos, no queda duplicada (FR-007).

### User Story 3 - Edito o cancelo el evento y Google queda al día (Priority: P1)

**Acceptance Scenarios**:

1. **Given** un evento con Meet, **When** cambio el título, la fecha, la hora o la duración, **Then** el evento de Google se actualiza (`events.patch`) y **el link no cambia**.
2. **Given** un evento con Meet, **When** lo cancelo o lo borro, **Then** se borra el evento de Google (sin avisar a nadie) y el link deja de estar asociado al evento de Renaser.
3. **Given** que Google falla al actualizar o borrar, **Then** la edición o el borrado en Renaser **se completan igual**, la sincronización queda `PENDIENTE_DE_SINCRONIZAR` y se reintenta en segundo plano (FR-008); nunca se bloquea al usuario por Google.
4. **Given** un evento con Meet, **When** lo cambio a otra ubicación (Zoom, dirección, link pegado…), **Then** se borra el evento de Google y se limpia la asociación.

### User Story 4 - Conecto la cuenta de Google de Renaser una sola vez (Priority: P1)

Como Administración, conecto el Gmail de Renaser a la app (OAuth) una vez; desde entonces los eventos crean su Meet con esa cuenta.

**Acceptance Scenarios**:

1. **Given** que no hay cuenta, **When** Administración toca «Conectar cuenta de Google» y acepta en Google, **Then** el servidor guarda el refresh token **cifrado** y muestra «Conectada: …@gmail.com» con la fecha.
2. **Given** una cuenta conectada, **When** toca «Desconectar», **Then** se revoca el token en Google, se borra del servidor y las nuevas peticiones de Meet se rechazan hasta reconectar. Los links ya creados siguen existiendo en Google y en los eventos.
3. **Given** el estado de la cuenta, **Then** hay una pantalla simple con: conectada o no, el correo, la última vez que se usó, el último error y cómo queda la sala (abierta o con permiso).

### User Story 5 - El aprendiz no nota nada (Priority: P1)

Para el aprendiz, el mentor y el líder nada cambia: ven el evento con «Unirme» y el logo de Meet (`marcaDelLink`). No ven la palabra «automático», ni ninguna pantalla nueva.

## Requirements *(mandatory)*

### Funcionales

- **FR-001** Crear un evento puede pedir, con un campo nuevo `crearEnlaceMeet: true` en el pedido (`POST/PUT /api/v1/events`), que el servidor cree la sala de Meet: **primero** se guarda el evento en Renaser y **después** (fuera de la transacción de la base de datos) se llama a Google. Nunca se mantiene abierta una transacción esperando a Google.
- **FR-002** La sala se crea con la Calendar API: `events.insert` en el calendario principal de la cuenta organizadora con `conferenceDataVersion=1`, `sendUpdates=none` y un cuerpo con: **`id` = el uuid del evento de Renaser sin guiones** (copiado de RenaserOs: es base32hex válido y hace que crear dos veces lo mismo dé 409 en vez de dos eventos), `summary` (título), inicio y fin con su zona horaria, `conferenceData.createRequest` con `requestId` único y `conferenceSolutionKey.type = "hangoutsMeet"`, y, si el evento de Renaser es recurrente, la regla de repetición equivalente (una sola sala para toda la serie, H12). **No lleva asistentes** ni la descripción del evento: ningún dato de personas se envía a Google. Un conference **nuevo por evento**, nunca reutilizar un código (H2).
- **FR-003** El link devuelto (`hangoutLink`, o el `entryPoint` de video) se guarda en `valorUbicacion` con `tipoUbicacion = MEET`, validado por la misma regla `requireLinkWeb`, de modo que **el resto del sistema (listados, «Unirme», recordatorios, asistencia, ranking) lo trata como cualquier link de Meet pegado a mano**. Se guarda además el estado de la sala en una tabla propia (FR-012).
- **FR-004** `crearEnlaceMeet` y un `valorUbicacion` pegado son **excluyentes** (400). Con `crearEnlaceMeet: true` el tipo de ubicación es `MEET`; con cualquier otro tipo el servidor responde 400.
- **FR-005** **Editar**: si cambian título, fecha/hora, duración, zona o recurrencia de un evento con Meet, el servidor actualiza el evento de Google (`events.patch`, `sendUpdates=none`); el link **no cambia**. Si el evento estaba cancelado en Google, se vuelve a confirmar (`status = confirmed`). Si se cambia a otra ubicación, se borra el evento de Google (o se manda `conferenceData = null`) y se limpia la asociación. Si se enciende `crearEnlaceMeet` en un evento que no tenía Meet, se crea como en FR-002. **Cancelar o borrar** un evento (o una sola ocurrencia, `cancel-occurrence`) borra el evento de Google (o cancela esa instancia).
- **FR-006** **Respuesta `pending`**: tras el `insert`, si `conferenceData.createRequest.status.statusCode` no es `success`, el servidor consulta el evento (`events.get`) hasta 3 veces con 1 s de espera, dentro de un tope total de 8 s. Si no llega, el estado queda `PENDIENTE` y lo completa el reintento en segundo plano. Cada llamada a Google tiene tiempo máximo de 5 s.
- **FR-007** **Sin duplicados**: como el id del evento de Google sale del de Renaser (FR-002), repetir la creación da 409; ante un 409 el servidor lee el evento (`events.get`) y toma su link en vez de crear otro. Un «Reintentar» manual hace lo mismo. Un doble toque manda un solo pedido.
- **FR-008** **Fallos y reintentos**. Si Google falla (red, 5xx, 429, `usageLimits`), el evento de Renaser **se crea, se edita o se borra igual**; el estado de la sala queda `FALLIDO` (crear) o `PENDIENTE_DE_SINCRONIZAR` (editar o borrar). Un proceso en segundo plano reintenta con espera exponencial y azar (1, 2, 4… hasta 15 minutos, máximo 8 intentos, deja de intentar cuando el evento ya pasó) y registra cada intento. La respuesta al usuario dice qué pasó, sin lenguaje técnico. «Reintentar» manual (`POST /api/v1/events/{id}/meet/retry`) solo está para ADMIN y ALCHEMIST.
- **FR-009** **Cuenta vencida o revocada** (`invalid_grant`, 401 con token renovado): la cuenta pasa a estado `REVOCADA`, **no se reintenta** (sería inútil), los eventos pendientes quedan `FALLIDO` con el motivo «Hay que volver a conectar la cuenta de Google», y se manda **un** aviso a Administración y Alquimista (sin spam: uno por día mientras siga revocada). Reconectar la cuenta reencola los pendientes de eventos que aún no pasaron.
- **FR-010** **Cuenta organizadora**: **una sola cuenta Gmail de Renaser** para toda la plataforma, conectada por OAuth 2.0 (flujo de código con `access_type=offline`, `prompt=consent`, PKCE y `state` firmado y de un solo uso, que ata la respuesta a quien inició la conexión). El permiso (scope) pedido es **`calendar.events`**, el que RenaserOs usa en producción; SC-001 compara `calendar.app.created` y se usa el menor que funcione (H3). Si FR-023 está encendido, además `meetings.space.settings`. **No** se piden permisos de Drive ni de lectura de reuniones. Conectar y desconectar: ADMIN y ALCHEMIST; solo el permiso, nunca el correo ni la contraseña del Gmail, pasan por la plataforma. Si Google no da todos los permisos pedidos, se revoca y se dice cuál faltó (como hace RenaserOs).
- **FR-011** **Secretos**. El refresh token se guarda **cifrado** (AES-256-GCM, un nonce por valor) en la base; la clave de cifrado vive fuera de la base y fuera del repositorio (variable de entorno cargada desde SSM, como el resto de secretos del servidor), y hay un identificador de versión de clave para poder rotarla. El `client_secret` de OAuth también va en el entorno, nunca en el repositorio. **Ningún registro (log) contiene tokens, el `client_secret`, el código de OAuth ni el link completo de Meet.** Un access token vive solo en memoria y se renueva con el refresh token. Una prueba busca tokens en el registro y falla si aparece alguno.
- **FR-012** **Datos** (migración nueva con su `search_path`, sin tocar la tabla `eventos` más que lo necesario): (a) `integracion_google` — una fila: correo de la cuenta, `refresh_token` cifrado, versión de clave, scopes concedidos, acceso de la sala (`ABIERTA`/`CON_PERMISO`), estado (`ACTIVA`/`REVOCADA`), conectada por, cuándo, último uso, último error; (b) `eventos_meet` — por evento: estado (`CREADO`, `PENDIENTE`, `FALLIDO`, `PENDIENTE_DE_SINCRONIZAR`, `BORRADO`), acceso aplicado, intentos, próximo intento, último error (texto corto, sin datos personales); (c) `integracion_google_auditoria` — ver FR-016. El id del evento de Google no se guarda: se deriva del de Renaser (FR-002). Todo con `version` para concurrencia optimista donde corresponda.
- **FR-013** **Cuotas**. Se respeta el límite de Google (600 pedidos por minuto, H11) con un limitador propio más estricto (por ejemplo 30 por minuto) y espera exponencial ante `403/429 usageLimits`. Un evento recurrente es **un** evento de Google, no uno por ocurrencia. Con el uso previsto (decenas de eventos al mes) la cuota no se acerca; el limitador existe para que un error de bucle no agote la cuenta.
- **FR-014** **Disponibilidad**. Un endpoint `GET /api/v1/events/meet/status` (solo ADMIN/ALCHEMIST) dice si la opción está disponible: cuenta `ACTIVA` e interruptor de Soporte TI `eventos.meet` prendido. Con cualquiera de las dos cosas apagada, la app **no dibuja** la opción y el servidor responde 409 (cuenta) o 404 `FUNCIONALIDAD_NO_DISPONIBLE` (interruptor). Es una funcionalidad más del catálogo de Soporte TI, hija de `eventos`, **prendida de fábrica solo si hay cuenta conectada**.
- **FR-015** **Quién la usa**: crear, editar y borrar eventos con Meet — `MANAGE_CALENDAR` (ADMIN y ALCHEMIST), igual que hoy (D-186). Mentor, guía, líder y aprendiz ven el evento como siempre. Conectar, desconectar y ver el estado de la cuenta — ADMIN y ALCHEMIST.
- **FR-016** **Auditoría**. Cada acción deja una fila: crear/actualizar/borrar sala, reintento, conectar, desconectar, cuenta revocada; con quién (`actor` o «sistema»), cuándo, el evento, el resultado, el código HTTP de Google (no el cuerpo de la respuesta) y un identificador de correlación. Retención: 12 meses. **Sin tokens y sin el link completo.** Visible para ADMIN y ALCHEMIST en la misma pantalla de la cuenta.
- **FR-017** **Ocurrencias recurrentes**. Un evento recurrente comparte **una sola sala** en toda su serie (así funciona Calendar, H12). Cancelar una ocurrencia cancela esa instancia en Google; cambiar la regla de repetición actualiza la regla. Si Google rechaza la regla equivalente, el estado queda `FALLIDO` con su motivo (no se crea un evento de Google sin repetición que no coincida con el de Renaser).
- **FR-018** **Zonas horarias**. Los instantes se envían a Google con la zona del evento (`ZoneId`), nunca con la del servidor; las reglas del repositorio sobre tiempo (`02-tiempo-zonas-y-schedulers`) valen para el proceso de reintentos.
- **FR-019** **Lo que ve la app** (`src/features/eventos/`): en el formulario de crear/editar evento, un interruptor «Crear enlace de Meet» (en el lugar del campo del link, que se esconde al encenderlo) **solo si el servidor dice que está disponible**; un evento con la sala en `FALLIDO` o `PENDIENTE` muestra a ADMIN/ALCHEMIST una línea corta con «Reintentar»; el aprendiz no ve nada nuevo (`Unirme` y `marcaDelLink` no cambian). Sin pantallas nuevas fuera de «Yo → Administración → Integraciones → Google» para conectar y ver el estado. Sin animaciones nuevas. *Cambia el formulario de Comunidad → Eventos: autorizado por el pedido del dueño del 2026-11-04.*
- **FR-020** **Compatibilidad**: un APK anterior contra el backend nuevo sigue funcionando (el campo nuevo es opcional); la app nueva contra un backend anterior no ve la opción (el estado responde 404).
- **FR-021** **Aviso honesto de los límites de Meet gratis** (H8): al activar «Crear enlace de Meet», una línea corta dice «Con la cuenta gratuita de Meet, las sesiones de 3 o más personas se cortan a los 60 minutos.» cuando la duración del evento supera 60 minutos. No bloquea. Si SC-001 f muestra que la cuenta de Renaser no tiene ese corte, el aviso se quita. *[sin prueba automática: la redacción final del aviso la aprueba el dueño]*
- **FR-022** **Privacidad**: a Google solo llegan título, fecha, hora, zona y duración del evento (y el id derivado del evento); nunca nombres, correos, audiencia ni descripción (FR-002). Los participantes que entren a la sala quedan sujetos a las condiciones de Google Meet.
- **FR-023** **Quién puede entrar a la sala** (copiado de `ajustarSala` de RenaserOs, reducido a un ajuste). Al terminar de crear la sala, si la cuenta concedió `meetings.space.settings`, el servidor pide a la Meet REST API `PATCH spaces/{código}` con `updateMask=config.accessType` y el valor elegido en la cuenta (`ABIERTA` ⇒ `OPEN`, `CON_PERMISO` ⇒ `TRUSTED`), y **lee la sala de vuelta** para guardar lo que Google confirmó (un 200 no prueba que quedó). El código de la sala sale del link. Si Google no lo acepta o falta el permiso, **el evento y el link se conservan**, el aviso queda en `eventos_meet` y la pantalla de la cuenta dice cómo quedó la sala. El valor por defecto lo decide el dueño (P-ACCESO). No hay ajuste por evento.

### Criterios de éxito

- **SC-001** **Confirmación previa a construir**, con el Gmail real de Renaser y el proyecto de Google Cloud ya creado (RenaserOs ya demostró que el mecanismo existe; esto confirma que funciona con **nuestra** cuenta y con lo que ellos no cubren): (a) `calendars.get` muestra `hangoutsMeet` entre los tipos permitidos; (b) un `events.insert` con `createRequest` y `id` derivado devuelve `hangoutLink` y estado `success`, y repetirlo da 409; (c) con `calendar.app.created` o, si no alcanza, `calendar.events`; (d) un segundo teléfono entra al link; (e) **un evento que se repite** con `createRequest` crea una sola sala para la serie; (f) se anota el corte de duración que muestra Meet con 3 o más personas (la cuenta de RenaserOs tiene Google One; la de Renaser, no sabemos); (g) `accessType = OPEN` queda aceptado y se lee de vuelta; (h) se anota el tiempo real de respuesta. Si (b) falla, esta spec **se detiene** y se informa al dueño antes de seguir. *[sin prueba automática: es una prueba manual con la cuenta real; el resultado se anota en el PR]*
- **SC-002** Con la cuenta conectada, crear un evento con «Crear enlace de Meet» deja el link en el evento en menos de 10 s en el 95 % de los intentos.
- **SC-003** Si Google falla o está caído, el evento se crea en el 100 % de los casos y la persona ve un mensaje claro; nunca se pierde un evento por un fallo de Google.
- **SC-004** Editar o cancelar un evento con Meet refleja el cambio en Google en el siguiente intento exitoso (máximo 15 minutos con reintentos) sin bloquear la edición.
- **SC-005** Un refresh token vencido o revocado se detecta en el primer uso, no genera más de un aviso por día y no provoca reintentos en bucle.
- **SC-006** Ningún token, secreto, código de OAuth o link completo aparece en los registros, ni la base guarda el refresh token en claro (prueba automática).
- **SC-007** Para el aprendiz, el mentor y el líder no cambia ninguna pantalla ni respuesta del servidor existente.
- **SC-008** Solo ADMIN y ALCHEMIST pueden crear/editar/borrar con Meet, ver el estado de la cuenta, conectarla o desconectarla; mentor, guía, líder y aprendiz reciben 403.
- **SC-009** Con el interruptor `eventos.meet` apagado, la opción desaparece de la app y el servidor responde 404 `FUNCIONALIDAD_NO_DISPONIBLE`.
- **SC-010** Cada acción deja su fila de auditoría con actor, evento, resultado y código de Google.
- **SC-011** Repetir la creación de la sala de un mismo evento (reintento, doble toque, respuesta perdida) deja **un solo** evento en Google y el mismo link (prueba con el Google simulado que responde 409).

## Fuera de alcance

- **Grabación automática de la sesión** (pedida el 2026-11-04 y retirada por el dueño el mismo día: «solo quiero el link y los recordatorios»). No se construye ni se compara nada. *Dato para cuando se retome:* con Meet no se puede por Workspace (descartado), pero RenaserOs afirma que su Gmail **con Google One** graba y transcribe en Meet, y la ayuda de Google lista algunas suscripciones de Google One como aptas; es una pista a verificar con el dueño, no una decisión.
- Invitar a Calendar a los aprendices; traer cambios de Google hacia Renaser; transcripciones, notas de Gemini y resúmenes con IA.
- Reemplazar Meet por otra herramienta de videollamada.

## Lo que el dueño debe hacer en Google Cloud (paso a paso)

Todo con **el Gmail de Renaser** (el que organizará las sesiones). No hace falta tarjeta de crédito: la Calendar API no cobra por uso en este volumen.

1. Entra a <https://console.cloud.google.com/> con ese Gmail y acepta los términos.
2. **Seleccionar proyecto → Proyecto nuevo**. Nombre: «Renaser Eventos». Sin organización. **Crear**.
3. **APIs y servicios → Biblioteca**: habilita **Google Calendar API** y, si se activa FR-023, **Google Meet REST API**.
4. **Pantalla de consentimiento de OAuth** (en la consola nueva: *Google Auth Platform → Marca*):
   - Tipo de usuario: **Externo** (con un Gmail no existe «Interno»).
   - Nombre: «Renaser». Correo de asistencia y del desarrollador: el Gmail.
   - **Página principal** y **política de privacidad**: Google las exige para publicar. Política: `https://renaser-90-dias-frontend-livid.vercel.app/privacidad/` (debe incluir lo de la sección «Privacidad» de abajo). Dominios autorizados: el de la web y el del servidor (RenaserOs también tuvo que declararlos).
   - **Permisos (scopes)**: `…/auth/calendar.events` (o `calendar.app.created` si SC-001 dice que alcanza) y, para FR-023, `…/auth/meetings.space.settings`.
   - **Publicación: pásala a «En producción»** (*Publicar app*). **Es importante**: en «Prueba» el permiso **vence cada 7 días** y las salas dejarían de crearse (H10; RenaserOs lo anota igual en su plan).
   - **Verificación**: para una sola cuenta propia no hace falta la verificación completa. Al conectar sale «Google no ha verificado esta app» y se sigue por *Avanzado → Ir a Renaser*. Si Google exigiera verificar, el equipo prepara lo que pida y se avisa al dueño.
5. **Credenciales → Crear credenciales → ID de cliente de OAuth**: tipo **Aplicación web**, nombre «Renaser servidor». **URI de redireccionamiento autorizado**: el que dé el equipo (por ejemplo `https://api.…/api/v1/admin/integraciones/google/callback`) y uno de desarrollo. Descarga el JSON: contiene `client_id` y `client_secret`. **Es un secreto: no lo mandes por chat ni correo ni lo subas a GitHub**; se entrega al equipo por el canal acordado para guardarlo en SSM.
6. Cuando la función esté construida: Yo → Administración → Integraciones → Google → **Conectar cuenta de Google**, entra con el Gmail de Renaser y acepta. Se hace **una sola vez**.
7. Verifica en <https://myaccount.google.com/permissions> que «Renaser» aparece. Si cambias la contraseña del Gmail o quitas el acceso ahí, hay que **volver a conectar** (la app avisa).
8. Activa la **verificación en dos pasos** de ese Gmail y guarda sus códigos de recuperación: de esa cuenta dependen todas las salas.

## Privacidad: qué sumar a la Política de privacidad

Antes de publicar, en la tabla «Con quién los compartimos» (`public/privacidad/index.html` de la app): una fila **Google (Calendar y Meet)** — «Crear el enlace de las sesiones en línea» — «El título, la fecha, la hora y la duración del evento; no le enviamos tus datos personales». Y una frase: «Cuando entras a una sesión por Meet, Google trata tus datos según sus propias condiciones.» Debe publicarse **antes** del APK y antes de publicar la pantalla de consentimiento de Google.

## Preguntas abiertas para el dueño

1. **P1 — El límite de 60 minutos.** Una reunión de Meet gratuita con 3 o más personas **se corta a los 60 minutos**. ¿Tus sesiones duran menos de una hora? Si duran más, SC-001 f dirá si tu Gmail tiene ese corte; si lo tiene, la función pierde mucho valor. (La grabación ya no entra en esta conversación.)
2. **P-ACCESO — ¿La sala abierta o con permiso?** *Abierta*: entra quien tenga el link, sin que nadie lo admita (lo práctico con cientos de aprendices; el link solo se ve dentro de la app, pero si alguien lo comparte, entra cualquiera). *Con permiso*: tú o quien esté dentro con la cuenta de Renaser debe admitir a cada persona. **Recomendación del equipo: abierta.** Se puede cambiar después sin tocar los eventos ya creados (solo valdría para los nuevos).
3. **P6 — Quién tendrá la cuenta de Google.** ¿Quién queda a cargo del Gmail de Renaser (contraseña, verificación en dos pasos, recuperación)? De esa cuenta dependen todas las salas.
4. **P7 — Eventos recurrentes.** ¿Los eventos repetidos (por ejemplo, cada martes) usan **el mismo link** en toda la serie (así funciona Google) o necesitas un link distinto por semana? Con esta spec, el mismo para toda la serie.
5. **P8 — Si SC-001 falla.** Si la prueba con tu Gmail muestra que Google exige verificar la app o que el Gmail no permite crear Meet por API (RenaserOs hace creer que sí se puede), ¿pausamos esta función?

## Assumptions

- Se usa **una sola cuenta organizadora** para toda la plataforma; el volumen de eventos es de decenas al mes.
- El Gmail de Renaser soporta `hangoutsMeet` en Calendar (lo hace el de RenaserOs; **se confirma en SC-001**).
- Esta spec añade **una** decisión nueva: **D-321** (por confirmar el número al integrar, `docs/MODULOS_A_AVANZAR.md`). La spec 041 (recordatorios) tomaría la siguiente.
- La trazabilidad (`specs/trazabilidad.json`) **no se toca en esta spec**: entra a la lista en el mismo cambio que le ponga sus marcas.
- La arquitectura sigue la regla 01: el puerto de salida «sala de reunión» vive en `calendar` y el adaptador de Google en su `infrastructure`; ningún módulo toca tablas ajenas.

## Referencias

- Calendar API: [crear eventos](https://developers.google.com/workspace/calendar/api/guides/create-events), [events.insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert), [cuotas](https://developers.google.com/workspace/calendar/api/guides/quota), [notas de versión](https://developers.google.com/calendar/releases).
- Meet: [REST API: espacios](https://developers.google.com/workspace/meet/api/reference/rest/v2/spaces), [funciones y límites](https://support.google.com/meet/answer/13396001), [acceso](https://support.google.com/meet/answer/9302870).
- OAuth: [vencimiento de tokens](https://developers.google.com/identity/protocols/oauth2#expiration), [verificación de apps](https://support.google.com/cloud/answer/13463073).
- RenaserOs (privado, solo lectura, commit `a73d416`): `supabase/functions/_shared/google.ts`, `supabase/functions/citas-google/index.ts`, `supabase/functions/citas-google-vuelta/index.ts`, `docs/sdd/citas-google-calendar/{spec,plan}.md`, `docs/sdd/citas-opciones-meet/spec.md`.
