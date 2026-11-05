# Feature Specification: Recordatorios de eventos en el chat, en la app y por WhatsApp

**Feature Branch**: `041-recordatorios-de-eventos` (backend y app)
**Created**: 2026-11-04
**Status**: Borrador de spec con las decisiones del dueño del 04/11/2026 ya incorporadas (tercera versión). **No se implementa nada todavía.** Quedan pocas preguntas abiertas (al final) y cosas que el dueño debe configurar fuera del código (el número nuevo en YCloud, Meta, la plantilla).
**Input** (pedido del dueño, 2026-11-04, textual, después de ver RenaserOs): «Quiero lo mismo. Revísalo y copiemos, pero de acuerdo a nuestra regla. No queremos todo; solo quiero: 1. que el link se autogenere (spec 040); **2. los recordatorios dentro del mismo chat y la app, y también con otro número que pondré.**»

**Cómo se entendió (confirmado por el dueño el 04/11/2026):** el recordatorio de un evento llega **a los que dicen «Voy»**, **por WhatsApp y también dentro de nuestro chat** (el chat de soporte de cada persona), además del aviso del teléfono que ya existe. El WhatsApp sale desde **otro número**, que el dueño conecta él mismo en **YCloud**.

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
| **Proveedor** | **YCloud** (plan gratuito) sobre la API de WhatsApp Business de Meta: `POST https://api.ycloud.com/v2/whatsapp/messages/sendDirectly`, cabecera `X-API-Key` (`_shared/ycloud.ts`). La razón: el número de la dueña se conectó por **coexistencia** (se sigue usando en la app de WhatsApp Business) y, sin ser proveedor tecnológico, eso solo se consigue por un proveedor. | **Copiamos YCloud como primer proveedor, detrás de un puerto** (`EnvioDeWhatsAppPort`) para poder cambiar a la API de Meta directa sin tocar el resto. Confirmado por el dueño (decisión 5). |
| **Número** | Uno solo, el de la empresa (+51 967 312 090, por coexistencia), guardado en una tabla `parametros` (no es un secreto). | **Otro número, que el dueño conecta él mismo en YCloud; no es el de RenaserOs** (decisión 5). Vive en la configuración del servidor (variable de entorno, no es secreto). Ver P-NUMERO. |
| **Plantillas** | Meta exige **plantillas aprobadas** para escribir primero. Una por tipo de aviso, categoría *Utility*, idioma `es`, con huecos `{{1}}…{{5}}` y un pie «responde BAJA». Después una versión 2 con **botones de respuesta rápida** («Ya lo hice», «No recibir más») cuya carga la arma el servidor en cada envío (`BAJA:<id>`). | Una sola plantilla de evento con 5 huecos y un botón «No recibir más» (el texto está más abajo); **la misma para los cuatro avisos** (cambia el hueco `{{4}}`). El texto de los huecos sale de `AvisoDeEvento` (el mismo que ve la app), no de una segunda copia. |
| **A quién** | A contactos y acuerdos que tienen la casilla `acepta_whatsapp` y un número. Nace **apagado**: `citas_whatsapp_activo = no`. | **A los que dijeron «Voy»** y tienen WhatsApp en la ficha, **sin que tengan que activar nada** (decisión 7; RenaserOs sí pide la casilla `acepta_whatsapp`, nosotros no). Se conserva la baja. El interruptor de Soporte TI nace apagado solo como seguro de operación. |
| **Cuándo** | Cada recordatorio tiene su regla (en cantidad y unidad: minutos, horas o días, con una hora si es «N días antes»). Un reloj cada 5 minutos envía lo que toca. Desde el 4 nov **sale a cualquier hora** (se quitó la franja de 07:00 a 21:59). **Lo que llega con más de 15 minutos de atraso, contados desde su hora, no sale**; el de una reunión que **ya empezó** se marca «No enviado — la reunión ya había empezado» (`citas_programar` / la cola); un aviso cuyo momento **ya había pasado al crear la cita** no se genera. | **Copiamos todo eso** con los cuatro avisos del dueño: **1 día, 1 hora, 30 min y 10 min antes** (decisión 8), a cualquier hora (decisión 9), tope de 15 minutos de atraso y nada después de empezar. «Ya comenzó» no se manda. Ver FR-014 a FR-016. |
| **Sin duplicados** | La fila se **aparta antes de llamar** a YCloud (`citas_whatsapp_tomar` la pasa a `ENVIANDO` con `for update skip locked`, y recién entonces se llama): `externalId` de YCloud **no** es una llave de idempotencia (dos veces el mismo `externalId` = dos mensajes). Lo que quedó más de 15 min «saliendo» **no se reenvía solo**. | **Igual**: se aparta antes de enviar, con clave única por recordatorio y persona (FR-020). |
| **Si falla** | Vuelve a «Para enviar hoy» con el motivo para mandarlo a mano. Solo se reintenta un 429 (máximo 3). Un 5xx o un corte **no** se reintenta: pudo haber salido (lo dice el mensaje «No se supo si salió»). | **Más simple**: aquí el WhatsApp es el tercer canal (ya llegó el aviso y el chat), así que **no hay reintentos ni “mandar a mano”**; solo se anota qué pasó (FR-023). |
| **Estados y BAJA** | Webhook firmado (`YCloud-Signature: t=…,s=…`, HMAC-SHA256 de `t.cuerpo` con el secreto del endpoint, 5 minutos de margen) que recibe `entregado/leído/falló` y las respuestas. Una «BAJA» solo vale si **responde a un recordatorio nuestro** (por el `context.id` = *wamid* del mensaje) o si a ese número le salió uno en los últimos 7 días: con coexistencia llegan todos los chats de la dueña. | **Copiamos** la firma, los estados y esa regla de la BAJA, pero la baja apaga **la preferencia de la persona** (el único lugar donde vive el permiso). |
| **Costos** | «Meta cobra cada plantilla que abre una conversación (utility, unos centavos de dólar)»; recargar la billetera de YCloud. | Igual (sección «Costos»). |
| **Secretos** | `YCLOUD_API_KEY` y el secreto del webhook en el archivo de secretos de la dueña y en los secretos de las funciones; **no están en el repositorio**. | En SSM, como el resto (FR-027). |

**Lo que NO copiamos:** la tabla de contactos con casillas propias (nosotros usamos el WhatsApp de la ficha y las preferencias de Yo), el botón «Ya lo hice», el resumen diario al equipo, el reloj propio y las reglas «si nos toca / si le toca al contacto» de las citas.

**Seguridad (para el dueño):** en el repo de RenaserOs **no se vio ninguna clave ni secreto** de YCloud, Meta ni Google; solo identificadores públicos (el ID de la cuenta de WhatsApp Business y el del proyecto de Supabase) y claves de demostración de Supabase local.

## Decisiones del dueño (04/11/2026)

Respuestas textuales del dueño, ya incorporadas en los requisitos. Lo que sigue sin decidir está en «Preguntas abiertas».

| # | Pregunta | Respuesta del dueño | Efecto en la spec |
|---|---|---|---|
| 5 | Número de WhatsApp (antes P1) y proveedor (antes P2) | **Otro número**, que el dueño va a conectar él mismo en **YCloud**. No es el de RenaserOs. El proveedor es YCloud. | FR-018, FR-028, «Lo que tú configuras». Queda abierto cuál es el número y cuándo lo conecta (P-NUMERO). |
| 6 | A quién le llega (antes P4 y P8) | «A los que dicen **Voy**», **por WhatsApp y también dentro de nuestro chat de Comunidad.** El dueño dijo que «falta definir», así que queda «Solo los que marcaron Voy» por defecto, con el diseño listo para pasar a toda la audiencia **cambiando un valor de configuración** (`EVENTOS_MENSAJES_AUDIENCIA`: `VOY` o `TODOS`), sin rehacer nada. | FR-001, FR-011, FR-015; la elección por evento (antiguo FR-011 y FR-040) se retira. |
| 7 | Consentimiento (antes P3) | «**No es obligatorio.** Regla de negocio: les llega dentro de la app como también por WhatsApp, ya que este es un programa aparte.» | **No hay interruptor de activación apagado por defecto.** Les llega a todos los que tienen WhatsApp en su ficha, sin que lo activen. **Se conserva la baja** (responder «BAJA», el botón «No recibir más» o un interruptor en Yo). FR-012, FR-027, FR-041, riesgos R-META y R-LEY. |
| 8 | Qué avisos se mandan (antes P5) | «Como se maneja dentro de RenaserOs»: **1 día antes, 1 hora antes, 30 min antes y 10 min antes.** El «ya comenzó» **no se manda.** | FR-014 reescrito («solo el último» deja de valer), FR-016, SC-007 y SC-008. Se copian de RenaserOs el tope de 15 minutos, no mandar de una reunión que ya empezó y cómo evita los duplicados. |
| 9 | Horario (antes P6) | **A cualquier hora.** | Se quita el límite de 07:00 a 21:59. FR-016. |
| 10 | Personal (antes P9) | «Por el momento solo aprendices, pero hazlo resiliente por si luego dicen a todos.» | Por defecto solo el rol aprendiz; **un parámetro** (`EVENTOS_MENSAJES_ROLES`) con la lista de roles que reciben, que se cambia sin código ni migración nueva. FR-015. |
| 11 | El mensaje en el chat (antes P8) | Va en **el chat de soporte de cada persona**, como un mensaje del programa firmado por su formación, igual que la Caja y el semáforo. | FR-001. |
| 12 | Tope mensual (antes P7) | **Sin tope por ahora.** El saldo de YCloud lo controla el dueño. | FR-025: el tope queda como parámetro opcional, **apagado**. |

**Riesgos que salen de estas decisiones (se anotan; no cambian la decisión):**

- **R-META · Meta pide permiso previo.** Las reglas de WhatsApp Business exigen que la empresa tenga el permiso (*opt-in*) de la persona antes de escribirle con una plantilla, y Meta baja la **calidad** del número —y puede limitar o bloquear el envío— si los usuarios lo bloquean o lo reportan. Con la decisión 7 no hay un permiso explícito aparte. Lo que reduce el riesgo, sin cambiar la decisión: (a) solo la categoría *Utility* (aviso de algo que la persona dijo que iba a hacer), (b) solo a los que dijeron «Voy», (c) el botón «No recibir más» en **todos** los mensajes, (d) que la Ficha Inicial y la Política de privacidad digan claramente que se escribe por WhatsApp (R-LEY), y (e) vigilar la calidad del número en el panel de Meta/YCloud. Si Meta baja la calidad, el primer remedio es pasar de `TODOS` a `VOY` o apagar el interruptor de Soporte TI. **El riesgo es del número nuevo, no de RenaserOs.**
- **R-LEY · Datos personales en el Perú.** La Ley 29733 de Protección de Datos Personales (y su reglamento vigente, a confirmar con el asesor legal del dueño) pide que la persona sepa **para qué** se usa su dato cuando se lo recoge. Hoy la ficha pide el WhatsApp y la política solo dice que se pide en la ficha. **Requisito de la política y de la ficha, no un bloqueo:** que digan que el número se usa para escribirle por WhatsApp sobre su programa (recordatorios de eventos) y cómo darse de baja. Se trata en «Privacidad».
- **R-COSTO · Cuatro mensajes por persona y por evento** y **sin tope**: el costo crece rápido con la audiencia y con el cambio a `TODOS`. Ver «Costos». La billetera de YCloud la recarga el dueño; si se acaba, se avisa a Administración (FR-024).

## Decisiones del equipo para poder avanzar (el dueño las puede cambiar)

Marcadas con su pregunta. Ninguna obliga a rehacer los datos.

| # | Decisión | Por qué | Pregunta |
|---|---|---|---|
| D1 | **El chat recibe el mismo mensaje que el WhatsApp** (los mismos cuatro avisos, a la misma audiencia), como mensaje del programa en el **chat de soporte de cada persona** (el de su formación vigente), **sin segundo push**. **Confirmado** (decisión 11). | Tiene precedente (semáforo, Caja, emergencia) y llega solo a quien debe. | — |
| D2 | ~~**El WhatsApp es un canal más, opcional y apagado de fábrica**~~ **Reemplazada por la decisión 7 del dueño:** el WhatsApp llega a quien dijo «Voy» sin que lo active; solo se puede dar de baja. | Decisión del dueño. | — |
| D3 | ~~Un solo WhatsApp por persona y por ocurrencia: el último aviso.~~ **Reemplazada por la decisión 8:** cuatro avisos (1 día, 1 hora, 30 y 10 min antes); «ya comenzó» no se manda. | Decisión del dueño. | — |
| D4 | **Quién:** por defecto quien dijo **«Voy»** (`EVENTOS_MENSAJES_AUDIENCIA = VOY`); un valor de configuración lo pasa a `TODOS`. | Decisión 6 del dueño. | — |
| D5 | ~~Horario de 07:00 a 21:59.~~ **Reemplazada por la decisión 9:** a cualquier hora; solo queda el tope de 15 minutos de atraso. | Decisión del dueño. | — |
| D6 | **Proveedor: YCloud**, detrás de un puerto. **Confirmado por el dueño** (decisión 5). | Es lo probado por el sistema hermano. | — |

## Clarifications

### Session 2026-11-04 (pedido del dueño)

- Q: ¿Qué recordatorios quiere? → A: «Los recordatorios dentro del mismo chat y la app, y también con otro número que pondré.»
- Q: ¿Copiamos RenaserOs? → A: «Copiemos, pero de acuerdo a nuestra regla. No queremos todo.»
- Q: ¿Y la grabación? → A: No entra (ver spec 040).

### Session 2026-11-04 (decisiones del dueño, segunda tanda)

- Q: ¿Qué número? → A: Otro, que el dueño conecta en YCloud.
- Q: ¿A quién? → A: «A los que dicen Voy», por WhatsApp y en el chat; por defecto solo ellos, con un valor para pasar a todos.
- Q: ¿Consentimiento? → A: «No es obligatorio»; les llega dentro de la app y por WhatsApp; se conserva la baja.
- Q: ¿Qué avisos? → A: 1 día, 1 hora, 30 y 10 minutos antes; no el «ya comenzó».
- Q: ¿Horario? → A: A cualquier hora.
- Q: ¿Personal? → A: Solo aprendices por ahora, con un parámetro para ampliarlo.
- Q: ¿Dónde va el mensaje del chat? → A: En el chat de soporte de cada persona, firmado por su formación.
- Q: ¿Tope mensual? → A: Sin tope; el saldo lo controla el dueño.

## Alcance

**Dentro**: (1) el recordatorio de un evento como mensaje en el chat de soporte de cada persona; (2) el WhatsApp con **cuatro avisos por ocurrencia** (1 día, 1 hora, 30 y 10 minutos antes), a quienes dijeron «Voy» (valor de configuración para pasar a toda la audiencia) y tienen un rol que recibe (por defecto solo aprendiz); la baja; los estados de entrega; el tope mensual opcional y apagado; la privacidad.
**Fuera**: cambiar cuándo se avisa (reglas de E2) o a quién (E3); WhatsApp para otras cosas (hábitos, alarmas, mensajes de chat, caja, semáforo); responder o conversar por WhatsApp (solo se atiende la baja); una bandeja de entrada de WhatsApp; el resumen diario al equipo; el SMS; el correo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El recordatorio también aparece en mi chat (Priority: P1)

Como aprendiz que dijo «Voy» a un evento, además del aviso del teléfono veo el recordatorio en mi chat de Renaser (1 día, 1 hora, 30 y 10 minutos antes), firmado con el nombre de mi formación, con la hora y el link si lo hay.

**Independent Test**: dado un evento de la Escuela con un aprendiz con soporte que dijo «Voy», al despachar su aviso de 10 minutos, `chat` guarda **un** mensaje del programa en su soporte con el texto del aviso; despachar de nuevo la misma fila (reentrega) no guarda otro.

**Acceptance Scenarios**:

1. **Given** un aprendiz del Diplomado que dijo «Voy», **When** llega su recordatorio, **Then** el mensaje del chat va firmado «Diplomado Renaser» y está en el soporte del Diplomado, nunca en el de la Escuela.
2. **Given** que apagó «Recordatorios de eventos» en Yo → Notificaciones, **Then** no recibe ni el aviso ni el mensaje.
3. **Given** una persona con un rol que no recibe (por defecto, mentor, líder o Administración), **Then** recibe el aviso de la app como hoy y no se crea ningún chat ni mensaje para ella.
4. **Given** el mensaje, **Then** no genera un segundo push (el aviso de la app ya fue el push) y cuenta como no leído en el chat.
5. **Given** una persona que **no** dijo «Voy» y `EVENTOS_MENSAJES_AUDIENCIA = VOY`, **Then** no recibe mensaje en el chat ni WhatsApp (sí el aviso de la app, como hoy).

### User Story 2 - Me llega el WhatsApp sin tener que activar nada (Priority: P1)

Como aprendiz que dijo «Voy» a una clase y tiene su WhatsApp en la ficha, me llegan mensajes por WhatsApp 1 día, 1 hora, 30 y 10 minutos antes, con la hora y el link, sin haber activado nada.

**Independent Test** (proveedor simulado): aprendiz con WhatsApp en la ficha que dijo «Voy», al despachar cada uno de los cuatro avisos el puerto recibe **una** llamada con la plantilla y sus 5 huecos; una reentrega no hace otra llamada.

**Acceptance Scenarios**:

1. **Given** una persona que dio de baja el WhatsApp (botón, «BAJA» o el interruptor de Yo), **Then** no sale nada por WhatsApp aunque tenga número y haya dicho «Voy».
2. **Given** una persona **sin WhatsApp en la ficha**, **Then** no sale nada, la fila queda `OMITIDO` con el motivo «Sin WhatsApp» y la app, en Yo → Notificaciones, le dice «Agrega tu WhatsApp en Editar perfil».
3. **Given** `EVENTOS_MENSAJES_AUDIENCIA = TODOS`, **Then** sale a toda la audiencia del evento con el rol permitido, haya dicho «Voy» o no.
4. **Given** que el aviso llega con más de 15 minutos de atraso, o que el evento ya empezó, **Then** no sale por WhatsApp ni por el chat (sí quedó el aviso de la app, si aplica).
5. **Given** un evento de la Escuela, **Then** el mensaje dice «te escribe Escuela Renaser»; de un Diplomado, «Diplomado Renaser».
6. **Given** un evento creado con menos de 1 hora de anticipación, **Then** no salen los avisos de «1 día» ni de «1 hora» (su momento ya había pasado al crearlo) y sí los que todavía llegan a tiempo.
7. **Given** un aviso a las 03:00 (cualquier hora), **Then** sale igual.
8. **Given** el rol «mentor» y `EVENTOS_MENSAJES_ROLES = [TRAINEE]`, **When** el parámetro pasa a `[TRAINEE, MENTOR]`, **Then** los mentores empiezan a recibir sin cambiar código ni migrar.

### User Story 3 - (retirada) Elijo si un evento avisa por WhatsApp (Priority: —)

**Retirada por la decisión 6 y la decisión 7 del dueño.** El formulario del evento **no cambia**: no hay elección `Ninguno / Solo los que van / Todos` por evento. A quién le llega lo decide un valor de configuración global (`EVENTOS_MENSAJES_AUDIENCIA`, por defecto `VOY`), así que pasar a toda la audiencia no obliga a rehacer ni migrar nada. Los números FR-011 y FR-040 se conservan con su nuevo contenido o como «retirado».

### User Story 4 - Me doy de baja (Priority: P1)

Como persona, toco «No recibir más» en el WhatsApp (o respondo «BAJA» a ese mensaje, o apago el interruptor en Yo → Notificaciones) y no me vuelve a llegar ninguno **por WhatsApp**.

**Acceptance Scenarios**:

1. **Given** el botón «No recibir más» de un recordatorio nuestro, **When** lo toco, **Then** mi preferencia de WhatsApp queda apagada (la misma que veo en Yo → Notificaciones) y me llega una sola respuesta de confirmación que dice que no recibiré más WhatsApp y que los mensajes del chat y los avisos de la app siguen (se apagan en Yo → Notificaciones).
2. **Given** que escribo «BAJA» **sin** responder a un mensaje nuestro y nunca nos llegó a ese número uno en los últimos 7 días, **Then** no pasa nada y no se me contesta.
3. **Given** un aviso con firma inválida o de hace más de 5 minutos, **Then** se rechaza y no se procesa nada.
4. **Given** que apagué el interruptor en Yo → Notificaciones, **Then** vale igual que el botón.
5. **Given** que di de baja el WhatsApp, **Then** puedo volver a encenderlo cuando quiera desde el mismo interruptor.

### User Story 5 - Si WhatsApp falla, el resto sigue (Priority: P1)

**Acceptance Scenarios**:

1. **Given** que el proveedor está caído, **Then** la persona igual recibe el aviso de la app y el mensaje del chat, y la fila de WhatsApp queda `FALLIDO` o `NO_SE_SUPO` con el motivo; **no hay reintento automático** (FR-023).
2. **Given** que el saldo de YCloud se acabó o la plantilla no está aprobada, **Then** lo mismo, y Administración recibe **un** aviso por día (FR-024). Como no hay tope mensual, es la forma de enterarse de que hay que recargar.

## Requirements *(mandatory)*

### El chat

- **FR-001** Para cada **aviso de mensaje** (FR-014) de una ocurrencia, el sistema deberá escribir **un** mensaje del programa en el chat de soporte **de la formación vigente** de cada persona que corresponda según FR-015, con el mismo título y cuerpo que arma `AvisoDeEvento` y, si el evento tiene un link web (tipo `MEET`, `ZOOM`, `ENLACE` o `WEBINAR`), una línea «Entra aquí: …». El mensaje va firmado con el nombre de la formación (`NombreDelChat.delPrograma`). No hay mensaje de «evento nuevo». El aviso del teléfono que ya existe (E1 a E6) **no cambia** y sigue llegando a toda la audiencia según sus reglas.
- **FR-002** El mensaje del chat **no genera un segundo push** (`SIN_AVISO`): el push ya lo dio el aviso de la app. Cuenta como no leído en el chat.
- **FR-003** La preferencia de la persona manda: quien apagó `RECORDATORIO_EVENTO` no recibe el aviso **ni** el mensaje. Tampoco las cuentas suspendidas ni las de prueba.
- **FR-004** El mensaje se identifica por el recordatorio (id calculado a partir de `recordatorioId`, como `enviarUnaVez`): la reentrega del *outbox* no lo duplica **ni lo pierde** aunque el aviso de la app ya exista.
- **FR-005** Quien no tiene chat de soporte (por ejemplo el personal, si luego se le incluye en FR-015) no recibe mensaje en el chat y no se le crea uno; el aviso de la app sigue como hoy y, si aplica, el WhatsApp.
- **FR-006** `chat` no depende de `notifications`. La llamada va de `notifications` hacia un caso de uso nuevo de `chat.api` (la dependencia ya existe en esa dirección: `AvisosDeMensajesFinder`).

### Los avisos de mensaje (chat y WhatsApp): quién, cuándo y qué

- **FR-010** **Interruptor de Soporte TI** `eventos.whatsapp` (hija de `eventos`, política de procesos `SALTA`: apagada, lo que toca no se acumula para mandarse tarde). **Nace apagada** porque sin el número, la clave y la plantilla aprobada no hay cómo enviar; es un **seguro de operación**, no un consentimiento de la persona. Se enciende al terminar «Lo que el dueño tiene que configurar». También sirve para frenar los envíos de golpe si Meta baja la calidad del número (R-META).
- **FR-011** **Quién recibe, por configuración (no por evento).** *(Reemplaza la elección por evento.)* Un parámetro `EVENTOS_MENSAJES_AUDIENCIA` con dos valores: `VOY` (**por defecto**: quienes dijeron «Voy» a **esa** ocurrencia) y `TODOS` (toda la audiencia del evento). Cambiarlo es **cambiar un valor**, sin código ni migración nueva: se lee en cada despacho, con `VOY` como valor si no existe. El formulario del evento **no cambia** y la tabla `eventos` **no suma ninguna columna**.
- **FR-012** **Sin permiso previo; con baja.** *(Reemplaza el *opt-in* de la versión anterior; decisión 7.)* **No hay interruptor de activación apagado por defecto.** El tipo de preferencia `RECORDATORIO_EVENTO_WHATSAPP` existe en Yo → Notificaciones y está **encendido de fábrica** (sin fila de preferencia = encendido, como los demás tipos). Quien lo apaga —con el interruptor, el botón «No recibir más» o «BAJA»— deja de recibir **por WhatsApp**; los mensajes del chat y los avisos de la app dependen de `RECORDATORIO_EVENTO`. Es el único lugar donde vive la baja.
- **FR-013** **El número.** Es el de la ficha (E8), leído con `WhatsappDeLaFichaFinder`, normalizado a formato internacional (`+51987654321`: sin espacios, empieza con `+`, 8 a 15 dígitos). Sin número o inválido ⇒ `OMITIDO` con el motivo; no se copia a otra tabla ni columna, y `usuarios.telefono` no se lee.
- **FR-014** **Qué avisos.** *(Reemplaza «solo el último».)* Por cada ocurrencia, hasta **cuatro avisos de mensaje**: **1 día, 1 hora, 30 minutos y 10 minutos antes** de la hora de inicio (lista configurable `EVENTOS_MENSAJES_ANTELACIONES_MIN`, por defecto `[1440, 60, 30, 10]`; «1 día» es exactamente 24 horas antes). **El «ya comenzó» no se manda.** Lo decide `calendar` —sigue decidiendo «qué y cuándo»— al crear y al editar el evento, y lo manda en el evento publicado al despachar; `notifications` decide «cómo». Como en RenaserOs: un aviso **cuyo momento ya había pasado cuando se creó o se editó el evento no se genera** (un evento creado 20 minutos antes no recibe el de «1 día» ni el de «1 hora»); editar un evento descarta los avisos pendientes y los vuelve a generar con la hora vigente. Estos cuatro avisos son **independientes** de las reglas del aviso del teléfono (E2), que no cambian.
- **FR-015** **A quién.** De la audiencia del evento (E3, que ya incluye su formación y la elegibilidad), con estas tres condiciones a la vez: (a) **según FR-011**: `VOY` ⇒ quienes dijeron «Voy» a esa ocurrencia (se lee **al despachar**, no al programar: quien dice «Voy» después del aviso de 1 día recibe los siguientes; quien cambia a «No voy» deja de recibirlos); `TODOS` ⇒ toda la audiencia; (b) **según el rol**: solo los roles de `EVENTOS_MENSAJES_ROLES`, **por defecto solo el aprendiz** (`TRAINEE`); es una **lista de roles** que se cambia sin código ni migración nueva, para que sumar mentores, guías, líderes o Administración sea cambiar el valor; (c) para el WhatsApp, además, que no haya dado de baja (FR-012) y tenga número (FR-013). Cuentas suspendidas y de prueba, nunca.
- **FR-016** **Atraso y empezado; sin horario.** *(Reemplaza el horario de 07:00 a 21:59; decisión 9.)* Los avisos salen **a cualquier hora**, también de madrugada. Se conservan las dos reglas de RenaserOs: (a) **hasta 15 minutos después de la hora del aviso** (el tope se cuenta desde su hora, también al cruzar la medianoche); lo que llega más tarde (el día que se enciende, una caída del servidor) **no sale ni se difiere**; (b) el aviso de una ocurrencia que **ya empezó** se descarta (`AvisoDeEvento.yaNoSirve`). Lo descartado queda `OMITIDO` con su motivo («Llegó tarde» / «El evento ya empezó»).
- **FR-017** **El mensaje.** Una plantilla aprobada por Meta (más abajo) con 5 huecos: nombre de pila, nombre de la formación del evento (`NombreDelChat.delPrograma`), título del evento, cuándo («empieza en 10 min, a las 19:00» / «empieza en 30 min, a las 19:00» / «empieza en 1 hora, a las 19:00» / «es mañana a las 19:00»: el `cuando` de `AvisoDeEvento`, un solo lugar) y el link o el lugar («Entra aquí: https://meet.google.com/…» / «Lugar: …» / «Míralo en Renaser.»). Cada hueco se limpia (sin saltos de línea ni más de 4 espacios, recortado a 400 caracteres) porque Meta rechaza lo contrario. Más un botón de respuesta rápida «No recibir más» cuya carga la arma el servidor en cada envío. **La misma plantilla sirve a los cuatro avisos.**
- **FR-018** **Un puerto, un proveedor.** El envío vive detrás de `EnvioDeWhatsAppPort` (salida de `notifications`); el primer adaptador habla con **YCloud** (decisión 5: `POST /v2/whatsapp/messages/sendDirectly`, `X-API-Key`). El resto del sistema no sabe de YCloud. Las pruebas usan un adaptador falso.

### Sin duplicados y fallos

- **FR-020** **Se aparta antes de enviar** (copiado de RenaserOs: la fila se aparta y recién entonces se llama al proveedor; lo que quedó «saliendo» y no se supo cómo terminó **nunca se reenvía solo**). Antes de llamar al proveedor, el sistema inserta la fila de entrega en `ENVIANDO` con clave única por recordatorio (la misma `claveDeduplicacion` del aviso, que distingue cada uno de los cuatro avisos y cada persona). Si la fila ya existe (reentrega del *outbox*, dos pasadas, dos instancias del servidor), **no se llama al proveedor**. La llamada va **fuera** de la transacción de la base.
- **FR-021** El *listener* de WhatsApp **no lanza excepciones por fallas del proveedor** (el *outbox* reintentaría y mandaría dos veces): anota el resultado y termina. Solo lanza si no pudo ni apartar la fila.
- **FR-022** **Qué se anota.** `ENVIANDO`, `ACEPTADO`, `ENVIADO`, `ENTREGADO`, `LEIDO`, `FALLIDO` (con el motivo corto del proveedor), `NO_SE_SUPO` (cortes y 5xx: pudo salir; **no se reintenta**) y `OMITIDO` (con el motivo: sin permiso, sin número, fuera de horario, tope mensual, interruptor apagado). Un estado posterior no se deshace por un aviso tardío (el estado solo avanza).
- **FR-023** **Sin reintentos y sin “mandar a mano”.** A diferencia de RenaserOs, aquí el WhatsApp es un canal extra: ya llegaron el aviso y el chat, y un reenvío repetiría un mensaje de pago. Ni 429 se reintenta.
- **FR-024** **Avisos a Administración** (reuso de `OPERACION_PLATAFORMA`, sin tipo nuevo): cuando falla la clave, la plantilla o el saldo —el proveedor responde 401/403/«plantilla no aprobada»/«saldo insuficiente»— Administración y Alquimista reciben **un** aviso por día con el motivo, y el WhatsApp queda sin enviar hasta que se arregle.
- **FR-025** **Tope mensual, opcional y apagado** (decisión 12: sin tope por ahora; el saldo de YCloud lo controla el dueño). Un máximo de mensajes por mes calendario en la configuración `EVENTOS_WHATSAPP_TOPE_MENSUAL`; **vacío o ausente = sin tope** (valor por defecto). Si algún día se llena, al llegar lo que sigue queda `OMITIDO` («Tope mensual») y Administración recibe un aviso (FR-024).

### La baja y los estados de entrega

- **FR-026** **Webhook** `POST /api/v1/webhooks/whatsapp`, **sin sesión** (lo abre el proveedor) y con una sola puerta: la **firma** `YCloud-Signature: t=<segundos>,s=<hex>` (HMAC-SHA256 de `t.<cuerpo crudo>` con el secreto del endpoint, comparación en tiempo constante, aceptada si coincide cualquiera de las firmas listadas y hay menos de 5 minutos de diferencia). Sin el secreto configurado no procesa nada (500, no queda abierto). Atiende dos eventos: el estado de un mensaje nuestro (entregado, leído, falló) y la respuesta de una persona.
- **FR-027** **La baja.** Vale (1) el toque del botón «No recibir más», cuya carga identifica la fila de entrega, y (2) un texto que sea **entero** «BAJA» (o «stop», «darme de baja», «no más», sin mayúsculas, tildes ni signos) **y** responda a un mensaje nuestro (`context.id` = *wamid* guardado). Otras conversaciones de ese número se ignoran y no se contestan. La baja pone **apagada** la preferencia `RECORDATORIO_EVENTO_WHATSAPP` de esa persona (FR-012) y le contesta **una vez** que no se le escribirá más por WhatsApp (y que el chat y los avisos de la app siguen). Un segundo toque no contesta. Ningún mensaje entrante se guarda.
- **FR-028** **Secretos.** `WHATSAPP_API_KEY` (de YCloud), `WHATSAPP_WEBHOOK_SECRETO`, `WHATSAPP_NUMERO_EMISOR` (el número **nuevo** del dueño) (no es secreto, pero tampoco va en el repositorio), el nombre de la plantilla y el idioma van en el entorno cargado desde SSM, como el resto. **Ningún registro (log) contiene la clave, el secreto, el cuerpo del mensaje ni el número completo** (se enmascara: `+51 9•• ••• •21`). Una prueba lo comprueba.
- **FR-029** **Quién lo ve.** Administración y Alquimista ven, en el detalle de un evento con WhatsApp, un resumen de conteos («38 enviados · 35 entregados · 2 fallaron · 4 sin número»), no nombres ni textos. Lo sirve `GET /api/v1/events/{id}/whatsapp` (`MANAGE_CALENDAR`; mentor, guía, líder y aprendiz reciben 403).

### Datos y compatibilidad

- **FR-030** **Datos** (migraciones nuevas, cada una con su `search_path` y su comentario de cabecera, sin editar ninguna anterior): (a) *(retirado: ya no hay columna en `eventos`; la audiencia es un parámetro, FR-011)*; (b) el valor `RECORDATORIO_EVENTO_WHATSAPP` en el tipo `tipo_notificacion` (en su propia migración, como V154: un valor nuevo de *enum* no se puede usar en la misma transacción que lo crea); (c) una tabla `entregas_whatsapp` de `notifications`: clave única por recordatorio y usuario (que distingue cada uno de los cuatro avisos), evento, aviso (1 día, 1 hora, 30 o 10 minutos), estado, motivo corto, *wamid* y fechas, **sin el texto del mensaje, sin el número y sin FK a tablas de otros módulos**. Retención de 12 meses (el mismo patrón de `PurgaNotificacionesScheduler`). **No se crea** ninguna tabla de contactos, de números ni de permisos.
- **FR-031** **Compatibilidad.** El evento y el formulario **no cambian**, así que un APK anterior funciona sin tocar nada (solo la fila de Yo necesita el APK nuevo); la app nueva contra un backend anterior no ve el interruptor (el tipo de preferencia no viene en la lista). Los campos nuevos de `RecordatorioEventoDebidoEvent` (qué aviso de mensaje es, link, formación) son **tolerantes a publicaciones viejas del *outbox*** (`Boolean`/`String` que pueden faltar y se leen como «no», como ya hace `asistenciaConfirmada`).
- **FR-032** **Formación.** Nada de lo que ve la persona sale de la formación equivocada: nombre en el mensaje del chat y en la plantilla, chat de soporte de su formación vigente, audiencia dentro de su formación (E3). Una prueba de aislamiento por formación (Escuela, Diplomado y Certificación) cubre el chat y los huecos de la plantilla.
- **FR-033** **Arquitectura.** El envío, el webhook y la tabla viven en `notifications` (canales de salida); `calendar` solo suma los cuatro avisos de mensaje a lo que ya publica; `chat` suma un caso de uso en su `api`. Ningún módulo lee tablas ajenas (regla 01). Cada clase respeta el techo de líneas de la regla 01 (clase ≤ 150, techo 300).

### Lo que ve la app (frontend)

- **FR-040** *(retirado por la decisión 6)* **El formulario de evento no cambia.** No hay elección de WhatsApp por evento; a quién llega lo decide un valor de configuración (FR-011).
- **FR-041** **Yo → Notificaciones**: una fila «Recordatorios de eventos por WhatsApp», **encendida de fábrica** (sin fila de preferencia = encendida), **solo si el servidor la lista**. Con número en la ficha, la fila dice a qué número escribimos (enmascarado: «+51 9•• ••• •21»); sin número, dice «Agrega tu WhatsApp en Editar perfil». Una línea breve: «Te escribiremos por WhatsApp un día, una hora, 30 y 10 minutos antes de los eventos a los que dijiste «Voy». Puedes apagarlo cuando quieras.» (la redacción final la aprueba el dueño). *Toca Yo: lo autoriza el pedido del dueño del 2026-11-04.*
- **FR-042** **Chat**: nada nuevo; los mensajes llegan como los del programa. Se comprueba que el link se pueda tocar. *[sin prueba automática: depende de cómo dibuja el chat los enlaces; se revisa en el emulador]*
- **FR-043** Un APK anterior sigue funcionando; solo la fila de Yo necesita el APK nuevo.
- **FR-044** **Aviso informativo de la finalidad (R-LEY).** La Ficha Inicial (donde se pide el WhatsApp) y la Política de privacidad dicen, en una frase, que el número se usa para escribirle por WhatsApp los recordatorios de sus eventos y que puede darse de baja. Es **requisito de la política y de la ficha, no un bloqueo** de esta función. *[sin prueba automática de la ficha; la política lleva su prueba `paginaPoliticaDePrivacidad`]*

### Criterios de éxito

- **SC-001** Un recordatorio entregado deja **un** mensaje en el chat de soporte de la formación vigente de la persona; reentregarlo diez veces deja uno solo.
- **SC-002** La persona que apagó `RECORDATORIO_EVENTO` no recibe aviso ni mensaje en el chat; la cuenta de prueba y la suspendida, tampoco.
- **SC-003** La persona que dio de baja el WhatsApp (`RECORDATORIO_EVENTO_WHATSAPP` apagada) **nunca** recibe un WhatsApp, aunque tenga número, diga «Voy» y la audiencia sea `TODOS`. Quien **no** la ha tocado y cumple las condiciones de FR-015 lo recibe **sin haber activado nada**.
- **SC-004** Sin número en la ficha no sale nada por WhatsApp y queda `OMITIDO («Sin WhatsApp»)`; el chat y el aviso de la app llegan igual.
- **SC-005** Reentregar, correr dos instancias o repetir el despacho de un mismo recordatorio llama **una sola vez** al proveedor (prueba con el puerto falso que cuenta llamadas; sin “apartar antes” la prueba falla).
- **SC-006** Cuando el proveedor falla, responde 5xx o se corta, el aviso y el chat ya entregados no cambian y el listener no lanza excepción (no hay reentrega del *outbox*).
- **SC-007** Un aviso con más de 15 minutos de atraso y uno de una ocurrencia que ya empezó no salen por WhatsApp ni por el chat. Un aviso a las 03:00 sí sale (no hay horario). No se manda ningún mensaje de «ya comenzó».
- **SC-008** De una ocurrencia salen, como máximo, **cuatro** avisos por persona (1 día, 1 hora, 30 y 10 minutos antes). Un evento creado 20 minutos antes de empezar genera solo los que todavía llegan a tiempo (el de 10 minutos). Editar la hora del evento descarta los pendientes y los regenera con la hora nueva.
- **SC-009** Los huecos de la plantilla de un evento del Diplomado dicen «Diplomado Renaser» y los del Diplomado nunca llevan textos de la Escuela (aislamiento por formación).
- **SC-010** El webhook rechaza una firma inválida, una de hace más de 5 minutos y un cuerpo alterado; sin secreto configurado no procesa nada.
- **SC-011** «No recibir más» (toque) y «BAJA» respondiendo a un mensaje nuestro apagan la preferencia de esa persona y contestan una sola vez; «BAJA» suelta no hace nada ni contesta.
- **SC-012** Sin `EVENTOS_WHATSAPP_TOPE_MENSUAL` no hay tope (valor por defecto); con un tope puesto, al alcanzarlo no sale ningún mensaje más ese mes y Administración recibe un solo aviso por día.
- **SC-013** Solo `MANAGE_CALENDAR` ve el resumen de conteos del evento; mentor, guía, líder y aprendiz reciben 403; con `eventos.whatsapp` apagado, 404 `FUNCIONALIDAD_NO_DISPONIBLE`.
- **SC-014** Ningún registro contiene la clave, el secreto, el cuerpo del mensaje ni un número completo (prueba automática).
- **SC-015** **Prueba en vivo**, solo con el número nuevo del dueño conectado en YCloud, a un teléfono de prueba y **nunca contra producción**: los cuatro avisos de un evento de prueba salen solos, llegan, se ve «Entregado» y «Leído», y el botón «No recibir más» apaga la preferencia. *[sin prueba automática: requiere Meta, el número y la plantilla aprobada; el resultado se anota en el PR]*
- **SC-016** **Quién recibe es configuración.** Con `EVENTOS_MENSAJES_ROLES = [TRAINEE]` el mentor, el líder y Administración no reciben mensaje ni WhatsApp; al cambiar el valor a incluir `MENTOR` (sin desplegar código ni migrar) el mentor lo recibe en el siguiente aviso. Con `EVENTOS_MENSAJES_AUDIENCIA = VOY` solo reciben quienes dijeron «Voy» a esa ocurrencia; al cambiarlo a `TODOS`, toda la audiencia (en ambos casos, solo los roles permitidos).
- **SC-017** Quien dijo «Voy» después del aviso de 1 día recibe los de 1 hora, 30 y 10 minutos y no el de 1 día; quien pasó a «No voy» antes del aviso de 30 minutos no lo recibe.

## La plantilla de WhatsApp (propuesta; la aprueba Meta)

Una sola, categoría **Utility**, idioma **Spanish (`es`)**, sin pie, con un botón *Quick reply*. Nombre: `renaser_evento_recordatorio_v2` (el nombre y el idioma se cambian por configuración si Meta los pide).

> **Corregido 2026-11-05 (creación en YCloud con el dueño).** El primer intento, `renaser_evento_recordatorio_v1`, falló al enviarse y su nombre quedó bloqueado; además Meta rechazó el texto corto por «demasiadas variables para su longitud». Quedó creada como `renaser_evento_recordatorio_v2`, idioma `es`, con el texto fijo alargado de abajo. **Los 5 huecos y su orden no cambian**, así que el servidor arma lo mismo.

```text
Hola {{1}}, te escribe {{2}} para recordarte un evento al que confirmaste tu asistencia. El evento «{{3}}» {{4}}. {{5}} Si ya no quieres recibir estos recordatorios por WhatsApp, toca el botón de abajo. ¡Te esperamos!
Botón de respuesta rápida: No recibir más
```

| Hueco | Ejemplo |
|---|---|
| `{{1}}` | Rosa |
| `{{2}}` | Escuela Renaser |
| `{{3}}` | Clase de manifestación |
| `{{4}}` | empieza en 10 min, a las 19:00 (o «empieza en 30 min, a las 19:00», «empieza en 1 hora, a las 19:00», «es mañana a las 19:00») |
| `{{5}}` | Entra aquí: https://meet.google.com/abc-defg-hij |

La carga del botón la arma el servidor en cada envío (`BAJA:<id de la entrega>`); en la plantilla solo va el texto del botón. Meta puede rechazar la plantilla por «demasiadas variables para su longitud»: si pasa, lo más pequeño es alargar el texto fijo. **Cambiar el texto aprobado obliga a cambiar los huecos que arma el servidor.**

## Costos

- **Chat y app: cero.**
- **WhatsApp:** Meta cobra por cada mensaje de plantilla entregado (los de categoría *Utility* son los más baratos) según el país del destinatario, y el proveedor puede sumar su propia comisión. RenaserOs lo describe como «unos centavos de dólar» por plantilla. **No hay cifra confirmada en esta spec**: la tarifa vigente de Perú se lee en el panel de Meta o de YCloud al preparar la cuenta (a verificar). Meta también tiene reglas de mensajes gratuitos dentro de la ventana de 24 horas que abre quien nos escribe primero; no cuentan para estos avisos, que los iniciamos nosotros.
- **Fórmula:** `costo del mes ≈ eventos del mes × personas que reciben × 4 avisos × tarifa por mensaje`. **Ahora son cuatro mensajes por persona y por evento** (antes uno). Con `VOY` y un uso de ejemplo de 8 eventos al mes y 40 personas que dijeron «Voy» y tienen WhatsApp, son **1 280 mensajes al mes**; con `TODOS` y 100 personas por evento, **3 200**. Cambiar a `TODOS` multiplica el costo, y **sin tope** (decisión 12) lo único que lo frena es el saldo de la billetera.
- **Controles de costo:** el interruptor de Soporte TI `eventos.whatsapp` (apaga todo de golpe), la audiencia `VOY` por defecto, la lista de roles (solo aprendices) y el tope mensual **opcional y apagado** (FR-025), que se puede llenar si el gasto sorprende. Quien se da de baja no cuesta más.
- **Saldo:** YCloud lleva una billetera que **el dueño recarga y controla**; si se acaba, el envío falla con su motivo y se avisa a Administración **una vez por día** (FR-024).
## Privacidad: qué sumar a la Política de privacidad y a la ficha

Hoy la política solo dice que el teléfono (WhatsApp) se pide en la ficha y **no dice que escribimos por WhatsApp**. Como la decisión del dueño es que no haya activación previa (decisión 7), la **información clara** pasa a ser lo que sostiene la función frente a la persona, a Meta y a la ley (R-META, R-LEY). Antes de encender nada, en `public/privacidad/index.html` de la app (con su prueba `paginaPoliticaDePrivacidad`):

1. **En «Qué datos recogemos / para qué los usamos»:** que usamos el WhatsApp de la ficha para recordarle por WhatsApp los eventos a los que dijo «Voy» (1 día, 1 hora, 30 y 10 minutos antes) y cómo darse de baja (el botón «No recibir más», responder «BAJA» o el interruptor de Yo → Notificaciones).
2. **En la tabla «Con quién los compartimos»:** una fila **YCloud (proveedor de WhatsApp Business) y Meta (WhatsApp)** — «Enviar los recordatorios por WhatsApp» — «Tu número de WhatsApp, tu nombre de pila, el título, la hora y el enlace o lugar del evento, y el nombre de tu formación; no enviamos nada más». Con la nota de que Meta y WhatsApp tratan esos datos según sus propias condiciones.
3. Que **no se guarda el texto del mensaje ni las respuestas**: solo el estado de entrega, por 12 meses.
4. La fila de **Google (Calendar y Meet)** de la spec 040 si ya está.
5. **Ley 29733 (datos personales):** la finalidad debe estar informada **cuando se recoge** el dato. La Ficha Inicial, donde se pide el «WhatsApp principal», debe decir en una frase que se usará para escribirle por WhatsApp sobre su programa (FR-044). Para quien ya llenó la ficha antes, vale la política publicada y el botón «No recibir más» de cada mensaje. **Es un requisito de texto, no un bloqueo**; lo valida el asesor legal del dueño.

Debe publicarse **antes del APK** y antes de encender el WhatsApp. Sin interruptor de activación, **no se escribe a nadie que no tenga WhatsApp en la ficha**, y el botón de baja va en todos los mensajes.

## Lo que el dueño tiene que configurar (fuera del código)

1. **Un número nuevo de WhatsApp, que no sea el de RenaserOs**, conectado por el dueño en **YCloud**. Hay dos formas: (a) **solo API** (el número deja de usarse en la app de WhatsApp normal), o (b) **coexistencia** (se sigue usando en la app de WhatsApp Business; solo se logra por un proveedor como YCloud y, según YCloud, **puede no tener el mismo rendimiento ni las mismas funciones que un número de API normal**: hay que confirmar los límites antes de enviar a todos). Decide cuál (P-NUMERO). El número no debe estar ya conectado a otra API.
2. **Verificar la cuenta de Meta (el negocio)** si Meta lo pide: de ella dependen los límites de envío diarios. Con cuatro avisos por persona y varios eventos el mismo día conviene tener la verificación hecha antes del primer evento real (a confirmar en el panel de Meta).
3. **La plantilla** de arriba (*Utility*, `es`) creada y **aprobada por Meta** en esa cuenta (puede tardar). El equipo entrega el texto exacto. Si Meta la reclasifica a *Marketing*, cuesta más y exige permiso explícito: es la señal para pasar a `VOY` o replantear.
4. **La clave de la API (`WHATSAPP_API_KEY`) y el secreto del webhook** que da YCloud: se pasan al equipo por el canal acordado, **nunca por chat, correo ni GitHub**, y se guardan en SSM. El equipo registra en YCloud la dirección del webhook y los dos eventos que se atienden (estado del mensaje y mensaje recibido).
5. **Saldo en la billetera de YCloud**, que el dueño recarga (no hay tope en la app).
6. **Publicar la Política de privacidad** con lo de arriba y **ajustar el texto de la Ficha Inicial** (FR-044).
7. **El APK nuevo** (solo la fila de Yo; la app no se actualiza por aire).
8. Después, **encender el interruptor `eventos.whatsapp`** en la consola de Soporte TI. Nace apagado.

## Preguntas abiertas para el dueño

Quedaron resueltas las antiguas P0, P1 (parcialmente), P2, P3, P4, P5, P6, P7, P8 y P9 (ver «Decisiones del dueño (04/11/2026)»). Siguen abiertas solo estas:

1. **P-NUMERO — ¿Cuál es el número y cuándo lo conectas?** Sin el número conectado en YCloud y la plantilla aprobada no se puede probar nada real (SC-015). ¿Es solo API o coexistencia (con la app de WhatsApp Business)? ¿La cuenta de Meta ya está verificada?
2. **P-PERSONAL — ¿Cuándo entra el personal?** Hoy solo aprendices. Cuando digas «a todos» (o a un rol concreto), es cambiar `EVENTOS_MENSAJES_ROLES`; el personal no tiene chat de soporte, así que recibiría el aviso de la app y el WhatsApp, pero no el mensaje del chat. ¿Algún rol primero?
3. **P-AUDIENCIA — ¿Pasamos a toda la audiencia?** Dijiste que «falta definir». Queda «Solo los que marcaron Voy». Cuando lo decidas, es cambiar `EVENTOS_MENSAJES_AUDIENCIA` a `TODOS` (ojo: multiplica el costo y el riesgo de reportes; ver R-META).
4. **P-TEXTO — El texto final de la plantilla y de la línea de Yo.** ¿Te sirve el texto propuesto? Meta debe aprobar el texto final, y el de la fila de Yo y el del aviso en la ficha los revisas tú.
5. **P-DIA — «1 día antes».** Lo entendimos como exactamente 24 horas antes de la hora de inicio (no «a las 9 de la mañana de la víspera»). ¿Está bien?

## Assumptions

- Hoy el WhatsApp de la ficha es el único dato de contacto por ese canal; quien no lo escribió no recibe nada por WhatsApp (se arregla escribiéndolo en Yo → Editar perfil).
- Los **cuatro avisos de mensaje** (chat y WhatsApp) son un calendario propio, distinto del aviso del teléfono (E2). **Supuesto:** el chat sigue los mismos cuatro momentos que el WhatsApp; si el dueño prefiere que el chat copie los avisos de la app, es un cambio de configuración de qué lista usa el chat.
- «1 día antes» es 24 horas antes de la hora de inicio (P-DIA).
- El proveedor entrega en segundos; el tope de 15 minutos cubre una pasada atrasada del reloj de eventos (corre cada minuto, `DespacharRecordatoriosScheduler`). Los envíos que coinciden (los cuatro avisos de varios eventos en la misma hora) se sacan de a pocos para no pasar los límites del proveedor.
- La configuración nueva se lee con su valor por defecto cuando no existe (`EVENTOS_MENSAJES_AUDIENCIA = VOY`, `EVENTOS_MENSAJES_ROLES = [TRAINEE]`, `EVENTOS_MENSAJES_ANTELACIONES_MIN = [1440, 60, 30, 10]`, `EVENTOS_WHATSAPP_TOPE_MENSUAL` vacío), así que cambiarla no exige código ni migración.
- Los avisos de los eventos ya se generan y despachan bien (D-182, E-360); esta spec no los cambia. Editar un evento descarta los avisos pendientes y los vuelve a generar con la hora vigente (`EventoService`), así los mensajes no dicen una hora vieja: **una prueba lo confirma** (SC-008).
- Esta spec añade **una** decisión nueva: **D-322** (por confirmar el número al integrar, `docs/MODULOS_A_AVANZAR.md`; la 040 toma D-321).
- Los números de las migraciones los fija quien integre (hoy la última es `V165`; la 040 también pide una).
- La trazabilidad (`specs/trazabilidad.json`) **no se toca en esta spec**: entra a la lista en el mismo cambio que le ponga sus marcas.
- Las pruebas nunca llaman a Meta ni a YCloud: usan el puerto falso. Lo real se prueba solo en local con el número nuevo del dueño (SC-015).

## Referencias

- Código nuestro: `calendar/application/services/{RecordatorioService,DespachoDeRecordatoriosService,AudienciaDelEventoService}`, `calendar/api/RecordatorioEventoDebidoEvent`, `notifications/infrastructure/adapter/in/event/RecordatorioEventoNotificationListener`, `notifications/domain/model/evento/AvisoDeEvento`, `chat/application/ports/in/mensaje/EnviarMensajeDelProgramaUseCase`, `chat/application/services/{CajaEnSoporteService,SoporteVigenteDelAprendiz}`, `users/api/WhatsappDeLaFichaFinder`, `plataforma/domain/model/catalogo/EntradasDelCatalogo`.
- Specs y decisiones: 024 (comunidad por formación), 035 (identidad por formación), 040 (Meet automático), D-182/D-183/D-189/E-645 (avisos de eventos), D-320 (WhatsApp de la ficha), D-186 (quién gestiona eventos).
- RenaserOs (privado, solo lectura, commit `a73d416`): `supabase/functions/_shared/ycloud.ts`, `supabase/functions/citas-whatsapp/index.ts`, `supabase/functions/citas-whatsapp-webhook/index.ts`, `docs/sdd/citas-whatsapp/{spec,plan,conectar-ycloud}.md`.
- Proveedor: documentación de YCloud (envío directo, plantillas, webhooks) y de la WhatsApp Business Platform de Meta (plantillas *Utility*, *opt-in*, precios por mensaje), a leer al preparar la cuenta.
