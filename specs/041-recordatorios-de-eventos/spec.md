# Feature Specification: Recordatorios de eventos en el chat, en la app y por WhatsApp

**Feature Branch**: `041-recordatorios-de-eventos` (backend y app)
**Created**: 2026-11-04
**Status**: Borrador de spec. **No se implementa nada todavía.** Hay preguntas para el dueño (al final) y cosas que él debe configurar fuera del código (Meta, el número, las plantillas).
**Input** (pedido del dueño, 2026-11-04, textual, después de ver RenaserOs): «Quiero lo mismo. Revísalo y copiemos, pero de acuerdo a nuestra regla. No queremos todo; solo quiero: 1. que el link se autogenere (spec 040); **2. los recordatorios dentro del mismo chat y la app, y también con otro número que pondré.**»

**Cómo lo entendimos** (confírmalo, es la pregunta P0): «dentro del chat y la app» = el recordatorio de un evento aparece **en el chat de Renaser** y **en la app** (bandeja y aviso del teléfono); «con otro número» = además sale **por WhatsApp**, desde un número de WhatsApp que el dueño pondrá, a la persona que tiene su WhatsApp en la ficha.

**Se apoya en**: la cola de recordatorios de `calendar/` (`recordatorios_evento`, `RecordatorioService`, `DespachoDeRecordatoriosService`), el aviso `RECORDATORIO_EVENTO` de `notifications/` (D-182, D-183, D-189, E-645), el chat del programa (`chat/`: mensajes firmados «Formación Renaser», chat de soporte), el WhatsApp de la Ficha Inicial (`WhatsappDeLaFichaFinder`, D-320), las preferencias de Yo → Notificaciones, los interruptores de Soporte TI y la formación de cada persona (specs 022, 024, 035). **No toca** la audiencia de los eventos, las reglas de recordatorio por tipo, la asistencia (D-256), ni la spec 040 (el link de Meet), aunque el WhatsApp lleva ese link.

## Contexto: lo que ya existe (verificado en el código el 2026-11-04)

| # | Qué hay hoy | Dónde |
|---|---|---|
| E1 | **Los recordatorios de eventos ya existen y llegan a la app**: bandeja y aviso del teléfono (tipo `RECORDATORIO_EVENTO`), uno por persona y por aviso, con la hora dicha en la zona del evento. | `RecordatorioEventoNotificationListener`, `AvisoDeEvento` |
| E2 | **Cuándo**: las reglas del evento, o las de su tipo si no trae propias: sesión especial y espontáneo 06:00 y 10 min antes; mentoría 10 min antes; Semana de Manifestación 1 día antes y 04:50. Más el aviso de «evento nuevo» al crearlo. | `ReglasPorTipoEvento`, `CalculadoraRecordatorios` |
| E3 | **A quién**: la audiencia del evento (todos, nivel, curso, roles o grupo), dentro de **su formación** (spec 024), más la elegibilidad del tipo (D-293). | `AudienciaDelEventoService` |
| E4 | **Sin duplicados ni pérdidas**: cada fila de la cola se marca enviada y se publica con el *outbox* de Spring Modulith (reintenta si falla); el aviso se deduplica por `recordatorioId` (`notificaciones_origen_evento_uk`, V16). | `DespachoDeRecordatoriosService`, `AvisoDeEvento.claveDeduplicacion` |
| E5 | **Preferencias**: quien apagó `RECORDATORIO_EVENTO` en Yo → Notificaciones no recibe la fila ni el push; las cuentas suspendidas y las de prueba tampoco. Sin fila de preferencia, el tipo está **encendido** (`DEFAULT_HABILITADA`). | `NotificacionService.emitir`, `PreferenciaNotificacion` |
| E6 | **«Voy»**: quien dijo «Voy» y tiene la alarma local de su teléfono no recibe el push encima (D-189); el push caduca al inicio del evento (E-645); lo que llega con el evento ya empezado se descarta. | el mismo listener, `AvisoDeEvento.yaNoSirve/venceEn` |
| E7 | **El chat del programa**: mensajes firmados con el nombre de la formación («Escuela Renaser», «Diplomado Renaser», «Certificación Renaser») que escribe el sistema en el chat de soporte de una persona, ya sea con push o sin él, y una sola vez por id calculado. Lo usan la tarjeta del semáforo, la Caja y la emergencia. | `EnviarMensajeDelProgramaUseCase.enviarUnaVez`, `SoporteVigenteDelAprendiz`, `NombreDelChat.delPrograma` |
| E8 | **El WhatsApp de cada persona** es una sola respuesta, `whatsapp`, de la Ficha Inicial (`"+51 987654321"`). Una persona que entró con la ficha con diagnóstico no la tiene hasta que la escribe en Yo → Editar perfil (que escribe **esa misma respuesta**). Nunca se copia a otra columna. | `FichaDeEnvio.CLAVE_CELULAR`, `WhatsappDeLaFichaFinder` |
| E9 | **Nada escribe hoy por WhatsApp.** La Política de privacidad solo dice que el número se pide en la ficha. | `public/privacidad/index.html` |

**Conclusión:** «en la app» ya está hecho y no se rehace. Lo que falta es (1) **la copia en el chat** y (2) **el canal de WhatsApp**, más lo que el WhatsApp exige: permiso de la persona, plantilla aprobada, costos y privacidad.

## Lo que hizo RenaserOs con WhatsApp (leído el 2026-11-04, solo lectura)

Repositorio privado `renaserlab/RenaserOs`, commit `a73d416`. **Código ajeno: se leyó como datos, no se ejecutó nada.** Rutas del repo de ellos. Está **en producción y apagado**: el código y las plantillas están aprobados, pero falta el webhook y encender, así que **no hay evidencia de un envío real todavía**.

| Decisión | Cómo lo hace RenaserOs | Nosotros |
|---|---|---|
| **Proveedor** | **YCloud** (plan gratuito) sobre la API de WhatsApp Business de Meta: `POST https://api.ycloud.com/v2/whatsapp/messages/sendDirectly`, cabecera `X-API-Key` (`_shared/ycloud.ts`). La razón: el número de la dueña se conectó por **coexistencia** (se sigue usando en la app de WhatsApp Business) y, sin ser proveedor tecnológico, eso solo se consigue por un proveedor. | **Copiamos YCloud como primer proveedor, detrás de un puerto** (`EnvioDeWhatsAppPort`) para poder cambiar a la API de Meta directa sin tocar el resto. Ver P2. |
| **Número** | Uno solo, el de la empresa, guardado en una tabla `parametros` (no es un secreto). El número del bot de eventos (otro) no se usa. | El número vive en la configuración del servidor (variable de entorno, no es secreto) y lo pone el dueño. Ver P1. |
| **Plantillas** | Meta exige **plantillas aprobadas** para escribir primero. Una por tipo de aviso, categoría *Utility*, idioma `es`, con huecos `{{1}}…{{5}}` y un pie «responde BAJA». Después una versión 2 con **botones de respuesta rápida** («Ya lo hice», «No recibir más») cuya carga la arma el servidor en cada envío (`BAJA:<id>`). | Una sola plantilla de evento con 5 huecos y un botón «No recibir más» (el texto está más abajo). El texto de los huecos sale de `AvisoDeEvento` (el mismo que ve la app), no de una segunda copia. |
| **A quién** | A contactos y acuerdos que tienen la casilla `acepta_whatsapp` y un número. Nace **apagado**: `citas_whatsapp_activo = no`. | A quien **activa** un interruptor propio en Yo → Notificaciones (apagado de fábrica) y tiene número en la ficha. Nace apagado también (interruptor de Soporte TI). |
| **Cuándo** | Cada recordatorio tiene su regla («1 día antes»…). Un reloj cada 5 minutos envía lo que toca; no manda de noche (después, cualquier hora); **lo que llega con más de 15 minutos de atraso no sale** y lo de una reunión que ya empezó tampoco. | Los avisos ya los programa el reloj de eventos (E2). Copiamos el **tope de 15 minutos** y «no después de empezar». El horario nocturno se pregunta (P6). |
| **Sin duplicados** | La fila se **aparta antes de llamar** a YCloud (pasa a `ENVIANDO`): `externalId` de YCloud **no** es una llave de idempotencia (dos veces el mismo `externalId` = dos mensajes). | **Igual**: se aparta antes de enviar, con clave única por recordatorio (FR-020). |
| **Si falla** | Vuelve a «Para enviar hoy» con el motivo para mandarlo a mano. Solo se reintenta un 429 (máximo 3). Un 5xx o un corte **no** se reintenta: pudo haber salido (lo dice el mensaje «No se supo si salió»). | **Más simple**: aquí el WhatsApp es el tercer canal (ya llegó el aviso y el chat), así que **no hay reintentos ni “mandar a mano”**; solo se anota qué pasó (FR-023). |
| **Estados y BAJA** | Webhook firmado (`YCloud-Signature: t=…,s=…`, HMAC-SHA256 de `t.cuerpo` con el secreto del endpoint, 5 minutos de margen) que recibe `entregado/leído/falló` y las respuestas. Una «BAJA» solo vale si **responde a un recordatorio nuestro** (por el `context.id` = *wamid* del mensaje) o si a ese número le salió uno en los últimos 7 días: con coexistencia llegan todos los chats de la dueña. | **Copiamos** la firma, los estados y esa regla de la BAJA, pero la baja apaga **la preferencia de la persona** (el único lugar donde vive el permiso). |
| **Costos** | «Meta cobra cada plantilla que abre una conversación (utility, unos centavos de dólar)»; recargar la billetera de YCloud. | Igual (sección «Costos»). |
| **Secretos** | `YCLOUD_API_KEY` y el secreto del webhook en el archivo de secretos de la dueña y en los secretos de las funciones; **no están en el repositorio**. | En SSM, como el resto (FR-027). |

**Lo que NO copiamos:** la tabla de contactos con casillas propias (nosotros usamos el WhatsApp de la ficha y las preferencias de Yo), el botón «Ya lo hice», el resumen diario al equipo, el reloj propio y las reglas «si nos toca / si le toca al contacto» de las citas.

**Seguridad (para el dueño):** en el repo de RenaserOs **no se vio ninguna clave ni secreto** de YCloud, Meta ni Google; solo identificadores públicos (el ID de la cuenta de WhatsApp Business y el del proyecto de Supabase) y claves de demostración de Supabase local.

## Decisiones del equipo para poder avanzar (el dueño las puede cambiar)

Marcadas con su pregunta. Ninguna obliga a rehacer los datos.

| # | Decisión | Por qué | Pregunta |
|---|---|---|---|
| D1 | **El chat recibe el mismo recordatorio que la app**, como mensaje del programa en el **chat de soporte de cada persona** (el de su formación vigente), **sin segundo push**. | Tiene precedente (semáforo, Caja, emergencia) y llega solo a quien debe, aunque la audiencia no sea «todos». | P8 |
| D2 | **El WhatsApp es un canal más, opcional y apagado de fábrica**: lo enciende **cada persona** en Yo → Notificaciones y **cada evento** lo elige al crearse (`Ninguno` por defecto). | Meta exige permiso (*opt-in*); y cuesta por mensaje. | P3, P4 |
| D3 | **Un solo WhatsApp por persona y por ocurrencia: el último aviso** (el más cercano al inicio), nunca el de «evento nuevo». | El aviso cercano es el que lleva el link y el más útil; uno solo controla el costo. | P5 |
| D4 | **Quién:** por defecto quien dijo **«Voy»** (`Solo los que van`); el evento puede elegir `Todos los de su audiencia`. Siempre además con el interruptor de la persona. | Costo y molestia. | P4 |
| D5 | **Horario:** solo de 07:00 a 21:59 (Lima) y hasta 15 minutos después de la hora del aviso. Fuera de eso, no sale por WhatsApp (sí por app y chat). | Copiado de RenaserOs (tope) y de la costumbre del programa. | P6 |
| D6 | **Proveedor: YCloud**, detrás de un puerto. | Es lo probado por el sistema hermano. | P2 |

## Clarifications

### Session 2026-11-04 (pedido del dueño)

- Q: ¿Qué recordatorios quiere? → A: «Los recordatorios dentro del mismo chat y la app, y también con otro número que pondré.»
- Q: ¿Copiamos RenaserOs? → A: «Copiemos, pero de acuerdo a nuestra regla. No queremos todo.»
- Q: ¿Y la grabación? → A: No entra (ver spec 040).

## Alcance

**Dentro**: (1) el recordatorio de un evento como mensaje en el chat; (2) el WhatsApp con el último aviso de cada ocurrencia, para quien lo activó; el interruptor de la persona; la elección por evento; la baja; los estados de entrega; el tope mensual; la privacidad.
**Fuera**: cambiar cuándo se avisa (reglas de E2) o a quién (E3); WhatsApp para otras cosas (hábitos, alarmas, mensajes de chat, caja, semáforo); responder o conversar por WhatsApp (solo se atiende la baja); una bandeja de entrada de WhatsApp; el resumen diario al equipo; el SMS; el correo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El recordatorio también aparece en mi chat (Priority: P1)

Como aprendiz, cuando se acerca un evento, además del aviso del teléfono veo el recordatorio en mi chat de Renaser, firmado con el nombre de mi formación, con la hora y el link si lo hay.

**Independent Test**: dado un evento de la Escuela con audiencia «todos» y un aprendiz con soporte, al despachar su recordatorio, `chat` guarda **un** mensaje del programa en su soporte con el texto del aviso; despachar de nuevo la misma fila (reentrega) no guarda otro.

**Acceptance Scenarios**:

1. **Given** un aprendiz del Diplomado, **When** llega su recordatorio, **Then** el mensaje del chat va firmado «Diplomado Renaser» y está en el soporte del Diplomado, nunca en el de la Escuela.
2. **Given** que apagó «Recordatorios de eventos» en Yo → Notificaciones, **Then** no recibe ni el aviso ni el mensaje.
3. **Given** una persona de la audiencia sin chat de soporte (personal), **Then** recibe el aviso de la app como hoy y no se crea ningún chat para ella.
4. **Given** el mensaje, **Then** no genera un segundo push (el aviso de la app ya fue el push) y cuenta como no leído en el chat.

### User Story 2 - Activo el WhatsApp y me llega el recordatorio (Priority: P1)

Como aprendiz, en Yo → Notificaciones activo «Recordatorios de eventos por WhatsApp». Si dije «Voy» a una clase con WhatsApp, 10 minutos antes me llega un mensaje con la hora y el link.

**Independent Test** (proveedor simulado): persona con WhatsApp en la ficha, interruptor activado y «Voy» a un evento con `Solo los que van`, al despachar su último aviso el puerto recibe **una** llamada con la plantilla y sus 5 huecos; una reentrega no hace otra llamada.

**Acceptance Scenarios**:

1. **Given** que no activó el interruptor, **Then** no sale nada por WhatsApp aunque tenga número y haya dicho «Voy».
2. **Given** que activó el interruptor pero **no tiene WhatsApp en la ficha**, **Then** no sale nada, la fila queda `OMITIDO` con el motivo «Sin WhatsApp» y la app, en Yo → Notificaciones, le dice «Agrega tu WhatsApp en Editar perfil» (sin dibujar el interruptor encendido).
3. **Given** un evento con `Todos los de su audiencia`, **Then** sale a todos los que activaron el interruptor, hayan dicho «Voy» o no.
4. **Given** que el aviso toca a las 06:00 (fuera de 07:00–21:59) o llega con más de 15 minutos de atraso, **Then** no sale por WhatsApp (sí por app y chat).
5. **Given** un evento de la Escuela, **Then** el mensaje dice «te escribe Escuela Renaser»; de un Diplomado, «Diplomado Renaser».

### User Story 3 - Elijo si un evento avisa por WhatsApp (Priority: P1)

Como Administración o Alquimista, al crear o editar un evento elijo `Ninguno` (por defecto), `Solo los que van` o `Todos los de su audiencia` para el WhatsApp.

**Acceptance Scenarios**:

1. **Given** que el interruptor de Soporte TI `eventos.whatsapp` está apagado o no hay número configurado, **Then** la app **no dibuja** la elección y el servidor responde 404 `FUNCIONALIDAD_NO_DISPONIBLE`.
2. **Given** un mentor o un aprendiz, **Then** no puede cambiarla (403), igual que el resto del formulario (D-186).
3. **Given** que cambio un evento ya creado de `Ninguno` a `Solo los que van`, **Then** rige para los avisos que todavía no salieron.

### User Story 4 - Me doy de baja (Priority: P1)

Como persona, toco «No recibir más» en el WhatsApp (o respondo «BAJA» a ese mensaje, o apago el interruptor en la app) y no me vuelve a llegar ninguno.

**Acceptance Scenarios**:

1. **Given** el botón «No recibir más» de un recordatorio nuestro, **When** lo toco, **Then** mi preferencia de WhatsApp queda apagada (la misma que veo en Yo → Notificaciones) y me llega una sola respuesta de confirmación.
2. **Given** que escribo «BAJA» **sin** responder a un mensaje nuestro y nunca nos llegó a ese número uno en los últimos 7 días, **Then** no pasa nada y no se me contesta.
3. **Given** un aviso con firma inválida o de hace más de 5 minutos, **Then** se rechaza y no se procesa nada.

### User Story 5 - Si WhatsApp falla, el resto sigue (Priority: P1)

**Acceptance Scenarios**:

1. **Given** que el proveedor está caído, **Then** la persona igual recibe el aviso de la app y el mensaje del chat, y la fila de WhatsApp queda `FALLIDO` o `NO_SE_SUPO` con el motivo; **no hay reintento automático** (FR-023).
2. **Given** que el saldo se acabó o la plantilla no está aprobada, **Then** lo mismo, y Administración recibe **un** aviso por día (FR-024).

## Requirements *(mandatory)*

### El chat

- **FR-001** Cuando se entrega a una persona el recordatorio de un evento (el aviso `RECORDATORIO_EVENTO`, incluido el de «evento nuevo»), el sistema deberá escribir **un** mensaje del programa en su chat de soporte **de su formación vigente**, con el mismo título y cuerpo que el aviso de la app (`AvisoDeEvento`) y, si el evento tiene un link web (tipo `MEET`, `ZOOM`, `ENLACE` o `WEBINAR`), una línea «Entra aquí: …». El mensaje va firmado con el nombre de la formación (`NombreDelChat.delPrograma`).
- **FR-002** El mensaje del chat **no genera un segundo push** (`SIN_AVISO`): el push ya lo dio el aviso de la app. Cuenta como no leído en el chat.
- **FR-003** La preferencia de la persona manda: quien apagó `RECORDATORIO_EVENTO` no recibe el aviso **ni** el mensaje. Tampoco las cuentas suspendidas ni las de prueba.
- **FR-004** El mensaje se identifica por el recordatorio (id calculado a partir de `recordatorioId`, como `enviarUnaVez`): la reentrega del *outbox* no lo duplica **ni lo pierde** aunque el aviso de la app ya exista.
- **FR-005** Quien no tiene chat de soporte (el personal) no recibe mensaje en el chat y no se le crea uno; el aviso de la app sigue como hoy.
- **FR-006** `chat` no depende de `notifications`. La llamada va de `notifications` hacia un caso de uso nuevo de `chat.api` (la dependencia ya existe en esa dirección: `AvisosDeMensajesFinder`).

### El WhatsApp: quién, cuándo y qué

- **FR-010** **Interruptor de Soporte TI** `eventos.whatsapp` (hija de `eventos`, **apagada de fábrica**, política de procesos `SALTA`: apagada, lo que toca no se acumula para mandarse tarde).
- **FR-011** **Elección por evento.** El evento guarda `recordarPorWhatsapp` con tres valores: `NINGUNO` (por defecto), `VOY` y `TODOS`. Lo cambian quienes crean y editan eventos (`MANAGE_CALENDAR`, D-186) y solo se ofrece si `GET /api/v1/events/whatsapp/status` dice que está disponible (interruptor prendido **y** número configurado). Los eventos existentes quedan en `NINGUNO`. Es una columna nueva en `eventos` (FR-030).
- **FR-012** **Permiso de la persona (*opt-in*).** Un tipo de preferencia nuevo, `RECORDATORIO_EVENTO_WHATSAPP`, que aparece en Yo → Notificaciones, **apagado de fábrica** (a diferencia de todos los demás, que están encendidos sin fila: el tipo declara su valor por defecto y el servicio de preferencias lo respeta). Sin fila ⇒ no se escribe por WhatsApp. Es el **único** lugar donde vive el permiso.
- **FR-013** **El número.** Es el de la ficha (E8), leído con `WhatsappDeLaFichaFinder`, normalizado a formato internacional (`+51987654321`: sin espacios, empieza con `+`, 8 a 15 dígitos). Sin número o inválido ⇒ `OMITIDO` con el motivo; no se copia a otra tabla ni columna, y `usuarios.telefono` no se lee.
- **FR-014** **Qué aviso.** Solo el **último aviso de la ocurrencia** (el de `enviarEn` más cercano al inicio entre los avisos que genera la regla efectiva del evento) y nunca el de «evento nuevo». Lo decide `calendar` al despachar —sigue decidiendo «qué y cuándo»— y lo manda en el evento publicado; `notifications` decide «cómo». Si ese aviso cae fuera del horario de WhatsApp (FR-016), esa ocurrencia no sale por WhatsApp (p. ej. la alarma de las 04:50 de la Semana de Manifestación).
- **FR-015** **A quién.** De la audiencia del evento (E3, que ya incluye su formación y la elegibilidad): con `VOY`, quienes dijeron «Voy» a **esa** ocurrencia (se lee al despachar, no al programar); con `TODOS`, toda la audiencia. En los dos casos, solo quien activó FR-012 y tiene número (FR-013). Cuentas suspendidas y de prueba, nunca.
- **FR-016** **Horario y atraso.** Solo de 07:00 a 21:59 (hora de Lima) y hasta 15 minutos después de la hora del aviso. El de una ocurrencia que ya empezó se descarta (`AvisoDeEvento.yaNoSirve`). Lo que no sale por esto **no se difiere**: pierde sentido.
- **FR-017** **El mensaje.** Una plantilla aprobada por Meta (más abajo) con 5 huecos: nombre de pila, nombre de la formación del evento (`NombreDelChat.delPrograma`), título del evento, cuándo («empieza en 10 min, a las 19:00» / «es hoy a las 19:00»: el `cuando` de `AvisoDeEvento`, un solo lugar) y el link o el lugar («Entra aquí: https://meet.google.com/…» / «Lugar: …» / «Míralo en Renaser.»). Cada hueco se limpia (sin saltos de línea ni más de 4 espacios, recortado a 400 caracteres) porque Meta rechaza lo contrario. Más un botón de respuesta rápida «No recibir más» cuya carga la arma el servidor en cada envío.
- **FR-018** **Un puerto, un proveedor.** El envío vive detrás de `EnvioDeWhatsAppPort` (salida de `notifications`); el primer adaptador habla con YCloud (`POST /v2/whatsapp/messages/sendDirectly`, `X-API-Key`). El resto del sistema no sabe de YCloud. Las pruebas usan un adaptador falso.

### Sin duplicados y fallos

- **FR-020** **Se aparta antes de enviar.** Antes de llamar al proveedor, el sistema inserta la fila de entrega en `ENVIANDO` con clave única por recordatorio (la misma `claveDeduplicacion` del aviso). Si la fila ya existe (reentrega del *outbox*, dos pasadas, dos instancias del servidor), **no se llama al proveedor**. La llamada va **fuera** de la transacción de la base.
- **FR-021** El *listener* de WhatsApp **no lanza excepciones por fallas del proveedor** (el *outbox* reintentaría y mandaría dos veces): anota el resultado y termina. Solo lanza si no pudo ni apartar la fila.
- **FR-022** **Qué se anota.** `ENVIANDO`, `ACEPTADO`, `ENVIADO`, `ENTREGADO`, `LEIDO`, `FALLIDO` (con el motivo corto del proveedor), `NO_SE_SUPO` (cortes y 5xx: pudo salir; **no se reintenta**) y `OMITIDO` (con el motivo: sin permiso, sin número, fuera de horario, tope mensual, interruptor apagado). Un estado posterior no se deshace por un aviso tardío (el estado solo avanza).
- **FR-023** **Sin reintentos y sin “mandar a mano”.** A diferencia de RenaserOs, aquí el WhatsApp es un canal extra: ya llegaron el aviso y el chat, y un reenvío repetiría un mensaje de pago. Ni 429 se reintenta.
- **FR-024** **Avisos a Administración** (reuso de `OPERACION_PLATAFORMA`, sin tipo nuevo): cuando falla la clave, la plantilla o el saldo —el proveedor responde 401/403/«plantilla no aprobada»/«saldo insuficiente»— Administración y Alquimista reciben **un** aviso por día con el motivo, y el WhatsApp queda sin enviar hasta que se arregle.
- **FR-025** **Tope mensual.** Un máximo de mensajes por mes calendario (configuración `WHATSAPP_TOPE_MENSUAL`, valor por defecto bajo; ver P7). Al llegar, lo que sigue queda `OMITIDO` («Tope mensual») y Administración recibe un aviso (FR-024).

### La baja y los estados de entrega

- **FR-026** **Webhook** `POST /api/v1/webhooks/whatsapp`, **sin sesión** (lo abre el proveedor) y con una sola puerta: la **firma** `YCloud-Signature: t=<segundos>,s=<hex>` (HMAC-SHA256 de `t.<cuerpo crudo>` con el secreto del endpoint, comparación en tiempo constante, aceptada si coincide cualquiera de las firmas listadas y hay menos de 5 minutos de diferencia). Sin el secreto configurado no procesa nada (500, no queda abierto). Atiende dos eventos: el estado de un mensaje nuestro (entregado, leído, falló) y la respuesta de una persona.
- **FR-027** **La baja.** Vale (1) el toque del botón «No recibir más», cuya carga identifica la fila de entrega, y (2) un texto que sea **entero** «BAJA» (o «stop», «darme de baja», «no más», sin mayúsculas, tildes ni signos) **y** responda a un mensaje nuestro (`context.id` = *wamid* guardado). Otras conversaciones de ese número se ignoran y no se contestan. La baja pone **apagada** la preferencia `RECORDATORIO_EVENTO_WHATSAPP` de esa persona (FR-012) y le contesta **una vez** que no se le escribirá más. Un segundo toque no contesta. Ningún mensaje entrante se guarda.
- **FR-028** **Secretos.** `WHATSAPP_API_KEY`, `WHATSAPP_WEBHOOK_SECRETO`, `WHATSAPP_NUMERO_EMISOR` (no es secreto, pero tampoco va en el repositorio), el nombre de la plantilla y el idioma van en el entorno cargado desde SSM, como el resto. **Ningún registro (log) contiene la clave, el secreto, el cuerpo del mensaje ni el número completo** (se enmascara: `+51 9•• ••• •21`). Una prueba lo comprueba.
- **FR-029** **Quién lo ve.** Administración y Alquimista ven, en el detalle de un evento con WhatsApp, un resumen de conteos («38 enviados · 35 entregados · 2 fallaron · 4 sin número»), no nombres ni textos. Lo sirve `GET /api/v1/events/{id}/whatsapp` (`MANAGE_CALENDAR`; mentor, guía, líder y aprendiz reciben 403).

### Datos y compatibilidad

- **FR-030** **Datos** (migraciones nuevas, cada una con su `search_path` y su comentario de cabecera, sin editar ninguna anterior): (a) `eventos.recordar_por_whatsapp` (texto, `NINGUNO`/`VOY`/`TODOS`, `NOT NULL DEFAULT 'NINGUNO'`, con su `CHECK`); (b) el valor `RECORDATORIO_EVENTO_WHATSAPP` en el tipo `tipo_notificacion` (en su propia migración, como V154: un valor nuevo de *enum* no se puede usar en la misma transacción que lo crea); (c) una tabla `entregas_whatsapp` de `notifications`: clave única por recordatorio, usuario, evento, estado, motivo corto, *wamid* y fechas, **sin el texto del mensaje, sin el número y sin FK a tablas de otros módulos**. Retención de 12 meses (el mismo patrón de `PurgaNotificacionesScheduler`). **No se crea** ninguna tabla de contactos, de números ni de permisos.
- **FR-031** **Compatibilidad.** El campo nuevo del evento es opcional (un APK anterior lo omite y queda `NINGUNO`); la app nueva contra un backend anterior no ve ni la elección ni el interruptor (el estado responde 404 y el tipo de preferencia no viene en la lista). Los campos nuevos de `RecordatorioEventoDebidoEvent` (último aviso, modo WhatsApp, link, formación) son **tolerantes a publicaciones viejas del *outbox*** (`Boolean`/`String` que pueden faltar y se leen como «no», como ya hace `asistenciaConfirmada`).
- **FR-032** **Formación.** Nada de lo que ve la persona sale de la formación equivocada: nombre en el mensaje del chat y en la plantilla, chat de soporte de su formación vigente, audiencia dentro de su formación (E3). Una prueba de aislamiento por formación (Escuela, Diplomado y Certificación) cubre el chat y los huecos de la plantilla.
- **FR-033** **Arquitectura.** El envío, el webhook y la tabla viven en `notifications` (canales de salida); `calendar` solo suma datos al evento que ya publica; `chat` suma un caso de uso en su `api`. Ningún módulo lee tablas ajenas (regla 01). Cada clase respeta el techo de líneas de la regla 01 (clase ≤ 150, techo 300).

### Lo que ve la app (frontend)

- **FR-040** **Formulario de evento** (Comunidad → Eventos, solo ADMIN y ALCHEMIST): una elección «WhatsApp» con `Ninguno · Solo los que van · Todos` (un control segmentado), **solo si el servidor la ofrece**; sin animaciones nuevas. *Cambia el formulario de Comunidad → Eventos: lo autoriza el pedido del dueño del 2026-11-04.*
- **FR-041** **Yo → Notificaciones**: una fila «Recordatorios de eventos por WhatsApp», apagada de fábrica, **solo si el servidor la lista**. Con número en la ficha, la fila dice a qué número escribimos (enmascarado: «+51 9•• ••• •21»); sin número, queda apagada con «Agrega tu WhatsApp en Editar perfil». Al encenderla, una línea breve: «Te escribiremos solo por los eventos que marcaste con “Voy”. Puedes apagarlo cuando quieras.» (la redacción final la aprueba el dueño). *Toca Yo: lo autoriza el pedido del dueño del 2026-11-04.*
- **FR-042** **Chat**: nada nuevo; los mensajes llegan como los del programa. Se comprueba que el link se pueda tocar. *[sin prueba automática: depende de cómo dibuja el chat los enlaces; se revisa en el emulador]*
- **FR-043** Un APK anterior sigue funcionando; el interruptor de la fila y la elección por evento necesitan el APK nuevo.

### Criterios de éxito

- **SC-001** Un recordatorio entregado deja **un** mensaje en el chat de soporte de la formación vigente de la persona; reentregarlo diez veces deja uno solo.
- **SC-002** La persona que apagó `RECORDATORIO_EVENTO` no recibe aviso ni mensaje en el chat; la cuenta de prueba y la suspendida, tampoco.
- **SC-003** Sin el interruptor de la persona (FR-012) **nunca** sale un WhatsApp, aunque tenga número, diga «Voy» y el evento tenga `TODOS`.
- **SC-004** Con el interruptor encendido y sin número en la ficha, no sale nada y queda `OMITIDO («Sin WhatsApp»)`.
- **SC-005** Reentregar, correr dos instancias o repetir el despacho de un mismo recordatorio llama **una sola vez** al proveedor (prueba con el puerto falso que cuenta llamadas; sin “apartar antes” la prueba falla).
- **SC-006** Cuando el proveedor falla, responde 5xx o se corta, el aviso y el chat ya entregados no cambian y el listener no lanza excepción (no hay reentrega del *outbox*).
- **SC-007** Un aviso a las 06:00, uno con más de 15 minutos de atraso y uno de una ocurrencia que ya empezó no salen por WhatsApp.
- **SC-008** De los avisos de una ocurrencia sale por WhatsApp solo el último; el de «evento nuevo» nunca.
- **SC-009** Los huecos de la plantilla de un evento del Diplomado dicen «Diplomado Renaser» y los del Diplomado nunca llevan textos de la Escuela (aislamiento por formación).
- **SC-010** El webhook rechaza una firma inválida, una de hace más de 5 minutos y un cuerpo alterado; sin secreto configurado no procesa nada.
- **SC-011** «No recibir más» (toque) y «BAJA» respondiendo a un mensaje nuestro apagan la preferencia de esa persona y contestan una sola vez; «BAJA» suelta no hace nada ni contesta.
- **SC-012** Con el tope mensual alcanzado no sale ningún mensaje más ese mes y Administración recibe un solo aviso por día.
- **SC-013** Solo `MANAGE_CALENDAR` cambia `recordarPorWhatsapp` y ve el resumen de conteos; mentor, guía, líder y aprendiz reciben 403; con `eventos.whatsapp` apagado, 404 `FUNCIONALIDAD_NO_DISPONIBLE`.
- **SC-014** Ningún registro contiene la clave, el secreto, el cuerpo del mensaje ni un número completo (prueba automática).
- **SC-015** **Prueba en vivo**, solo con un número de prueba del dueño y **nunca contra producción**: un recordatorio sale solo, llega, se ve «Entregado» y «Leído», y el botón «No recibir más» apaga la preferencia. *[sin prueba automática: requiere Meta, el número y la plantilla aprobada; el resultado se anota en el PR]*

## La plantilla de WhatsApp (propuesta; la aprueba Meta)

Una sola, categoría **Utility**, idioma **Spanish (`es`)**, sin pie, con un botón *Quick reply*. Nombre propuesto: `renaser_evento_recordatorio_v1` (el nombre y el idioma se cambian por configuración si Meta los pide).

```text
Hola {{1}}, te escribe {{2}}. Tu evento «{{3}}» {{4}}. {{5}} ¡Te esperamos!
Botón de respuesta rápida: No recibir más
```

| Hueco | Ejemplo |
|---|---|
| `{{1}}` | Rosa |
| `{{2}}` | Escuela Renaser |
| `{{3}}` | Clase de manifestación |
| `{{4}}` | empieza en 10 min, a las 19:00 |
| `{{5}}` | Entra aquí: https://meet.google.com/abc-defg-hij |

La carga del botón la arma el servidor en cada envío (`BAJA:<id de la entrega>`); en la plantilla solo va el texto del botón. Meta puede rechazar la plantilla por «demasiadas variables para su longitud»: si pasa, lo más pequeño es alargar el texto fijo. **Cambiar el texto aprobado obliga a cambiar los huecos que arma el servidor.**

## Costos

- **Chat y app: cero.**
- **WhatsApp:** Meta cobra por cada mensaje de plantilla entregado (los de categoría *Utility* son los más baratos) según el país del destinatario, y el proveedor puede sumar su propia comisión. RenaserOs lo describe como «unos centavos de dólar» por plantilla. **No hay cifra confirmada en esta spec**: la tarifa vigente de Perú se lee en el panel de Meta o de YCloud al preparar la cuenta (a verificar).
- **Fórmula:** `costo del mes ≈ eventos del mes × personas que reciben WhatsApp por evento × tarifa por mensaje`. Con `Solo los que van` y un uso de ejemplo de 8 eventos al mes y 40 personas que dijeron «Voy» y activaron el interruptor, son **320 mensajes al mes**; el costo es esa cantidad por la tarifa. Con `Todos` y 100 personas serían 800.
- **Controles de costo** (todos en la spec): apagado de fábrica en tres niveles (interruptor de Soporte TI, elección por evento, interruptor de la persona), un solo mensaje por persona y ocurrencia, y el tope mensual (FR-025). Quien se da de baja no cuesta más.
- **Saldo:** el proveedor (YCloud) lleva una billetera que el dueño recarga; si se acaba, el envío falla con su motivo y se avisa una vez por día (FR-024).

## Privacidad: qué sumar a la Política de privacidad

Hoy la política solo dice que el teléfono (WhatsApp) se pide en la ficha y **no dice que escribimos por WhatsApp**. Antes de encender nada, en `public/privacidad/index.html` de la app (con su prueba `paginaPoliticaDePrivacidad`):

1. **En «Qué datos recogemos / para qué los usamos»:** que, **solo si la persona lo activa** en Notificaciones, usamos el WhatsApp de su ficha para recordarle los eventos a los que dijo «Voy» (o que se marcaron para todos), y cómo apagarlo (el interruptor o «No recibir más» / «BAJA»).
2. **En la tabla «Con quién los compartimos»:** una fila **YCloud (proveedor de WhatsApp Business) y Meta (WhatsApp)** — «Enviar los recordatorios por WhatsApp» — «Tu número de WhatsApp, tu nombre de pila, el título, la hora y el enlace o lugar del evento, y el nombre de tu formación; no enviamos nada más». Con la nota de que Meta y WhatsApp tratan esos datos según sus propias condiciones.
3. Que **no se guarda el texto del mensaje ni las respuestas**: solo el estado de entrega, por 12 meses.
4. La fila de **Google (Calendar y Meet)** de la spec 040 si ya está.

Debe publicarse **antes del APK** y antes de encender el WhatsApp. Permiso (*opt-in*): el interruptor de FR-012 es el consentimiento explícito que pide Meta; no se escribe a nadie por haber puesto su número en la ficha.

## Lo que el dueño tiene que configurar (fuera del código)

1. **Una cuenta de WhatsApp Business (WABA)** en un portafolio de Meta, con el negocio **verificado** si Meta lo pide para el volumen que se quiere (los límites de envío dependen de ello; a verificar en Meta).
2. **El número** que pondrá el dueño: aprobar su nombre visible y decidir si es solo API o si se **sigue usando en la app de WhatsApp Business** (coexistencia: solo se consigue por un proveedor como YCloud). Dime cuál es (P1).
3. **La cuenta del proveedor** (YCloud, o el que se elija en P2) conectada a ese número, con **saldo** en su billetera.
4. **La plantilla** de arriba creada y **aprobada por Meta** (puede tardar). El equipo entrega el texto exacto.
5. **La clave de la API y el secreto del webhook** (los entrega el proveedor): se pasan al equipo por el canal acordado, **nunca por chat, correo ni GitHub**, y se guardan en SSM. El equipo registra en el proveedor la dirección del webhook y los dos eventos que se atienden (estado del mensaje y mensaje recibido).
6. **Publicar la Política de privacidad** con lo de arriba.
7. **El APK nuevo** (la fila de Yo y la elección del formulario; la app no se actualiza por aire).
8. Después, **encender el interruptor `eventos.whatsapp`** en la consola de Soporte TI. Nace apagado.

## Preguntas abiertas para el dueño

La lista tiene **la respuesta que recomienda el equipo** primero.

1. **P0 — ¿Entendimos bien el pedido?** «Dentro del mismo chat y la app» = chat de Renaser + aviso del teléfono; «con otro número» = WhatsApp. ¿O querías otra cosa (por ejemplo, un chat de WhatsApp de grupo)?
2. **P1 — ¿Qué número y cómo está hoy?** ¿Es el de RenaserOs (+51 967 312 090, coexistencia) u otro? ¿Solo API, o se sigue usando en la app de WhatsApp Business? ¿Su cuenta de Meta ya está verificada?
3. **P2 — ¿Proveedor?** *Recomendado:* YCloud, el que ya usa RenaserOs (plan gratuito, coexistencia, flujo conocido). La alternativa es la API de Meta directa: sin intermediario, pero con más trabajo (webhook propio, plantillas por API) y sin coexistencia. Queda detrás de un puerto, así que se puede cambiar.
4. **P3 — ¿Consentimiento?** *Recomendado:* explícito, un interruptor apagado en Yo → Notificaciones (lo que pide Meta y la ley de datos personales). La alternativa (encendido para todos los que dieron su WhatsApp en la ficha) es más simple pero arriesga que Meta marque el número por quejas y no lo cubre el texto actual de la ficha.
5. **P4 — ¿A quién por defecto en cada evento?** *Recomendado:* `Solo los que van` (quienes dijeron «Voy»). La otra opción es `Todos los de su audiencia` (cuesta más y molesta más a quien no va). Cada evento puede cambiarlo.
6. **P5 — ¿Cuál aviso va por WhatsApp?** *Recomendado:* solo el último (el de 10 minutos antes en las sesiones, con el link). Alternativas: el primero (el de la mañana) o todos (más caro).
7. **P6 — ¿Horario?** *Recomendado:* 07:00 a 21:59, y hasta 15 minutos de atraso. Esto deja fuera por WhatsApp los avisos de las 06:00 (sesión especial y espontáneo, que sí salen por app y chat) y el de las 04:50 de la Semana de Manifestación. ¿Aceptas, o quieres que el de las 06:00 también salga?
8. **P7 — ¿Tope y saldo?** ¿Cuántos mensajes al mes como máximo (el equipo propone un tope bajo de arranque, p. ej. 500) y quién recarga la billetera del proveedor?
9. **P8 — ¿Dónde va el mensaje del chat?** *Recomendado:* en el chat de soporte de cada persona (como la Caja y el semáforo). La alternativa es un solo mensaje en la comunidad de la formación para eventos de «todos» (menos mensajes, pero llega a todos con su push de chat, duplicando el aviso, y no sirve para eventos de un grupo o de ciertos roles).
10. **P9 — ¿También al personal?** Los mentores, líderes y administradores que están en la audiencia: ¿reciben WhatsApp con las mismas reglas? *Recomendado:* sí (si activan el interruptor); no tienen chat de soporte, así que ellos reciben aviso de la app y WhatsApp, pero no mensaje en el chat.
11. **P10 — Texto de la plantilla y del permiso.** ¿Te sirve el texto propuesto y la línea del interruptor? Meta debe aprobar el texto final.

## Assumptions

- Hoy el WhatsApp de la ficha es el único dato de contacto por ese canal; quien no lo escribió no recibe nada (se arregla escribiéndolo en Yo → Editar perfil).
- El último aviso suele ser «N minutos antes» y el proveedor lo entrega en segundos; el tope de 15 minutos cubre una pasada atrasada del reloj de eventos (corre cada minuto, `DespacharRecordatoriosScheduler`).
- Los avisos de los eventos ya se generan y despachan bien (D-182, E-360); esta spec no los cambia. Editar un evento descarta los avisos pendientes y los vuelve a generar con la hora vigente (`EventoService`), así el WhatsApp no dice una hora vieja: **una prueba lo confirma**.
- Esta spec añade **una** decisión nueva: **D-322** (por confirmar el número al integrar, `docs/MODULOS_A_AVANZAR.md`; la 040 toma D-321).
- Los números de las migraciones los fija quien integre (hoy la última es `V165`; la 040 también pide una).
- La trazabilidad (`specs/trazabilidad.json`) **no se toca en esta spec**: entra a la lista en el mismo cambio que le ponga sus marcas.
- Las pruebas nunca llaman a Meta ni a YCloud: usan el puerto falso. Lo real se prueba solo en local con el número del dueño (SC-015).

## Referencias

- Código nuestro: `calendar/application/services/{RecordatorioService,DespachoDeRecordatoriosService,AudienciaDelEventoService}`, `calendar/api/RecordatorioEventoDebidoEvent`, `notifications/infrastructure/adapter/in/event/RecordatorioEventoNotificationListener`, `notifications/domain/model/evento/AvisoDeEvento`, `chat/application/ports/in/mensaje/EnviarMensajeDelProgramaUseCase`, `chat/application/services/{CajaEnSoporteService,SoporteVigenteDelAprendiz}`, `users/api/WhatsappDeLaFichaFinder`, `plataforma/domain/model/catalogo/EntradasDelCatalogo`.
- Specs y decisiones: 024 (comunidad por formación), 035 (identidad por formación), 040 (Meet automático), D-182/D-183/D-189/E-645 (avisos de eventos), D-320 (WhatsApp de la ficha), D-186 (quién gestiona eventos).
- RenaserOs (privado, solo lectura, commit `a73d416`): `supabase/functions/_shared/ycloud.ts`, `supabase/functions/citas-whatsapp/index.ts`, `supabase/functions/citas-whatsapp-webhook/index.ts`, `docs/sdd/citas-whatsapp/{spec,plan,conectar-ycloud}.md`.
- Proveedor: documentación de YCloud (envío directo, plantillas, webhooks) y de la WhatsApp Business Platform de Meta (plantillas *Utility*, *opt-in*, precios por mensaje), a leer al preparar la cuenta.
