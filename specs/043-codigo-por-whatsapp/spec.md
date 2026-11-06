# Feature Specification: El código de verificación también por WhatsApp

**Feature Branch**: `043-codigo-por-whatsapp` (backend y app)
**Created**: 2026-11-06
**Status**: Implementada en las ramas `043-codigo-por-whatsapp` de los dos repos. **Nace apagada** (`auth.codigo_whatsapp`): se prende cuando Meta apruebe la plantilla.
**Decisión**: D-325 (`docs/MODULOS_A_AVANZAR.md`).
**Input** (pedido del dueño, 2026-11-06, textual): «si no llega o está lleno el correo… otras empresas usan SMS o WhatsApp».

## Decisiones del dueño (06/11/2026)

| # | Pregunta | Respuesta | Efecto |
|---|---|---|---|
| 1 | ¿Por dónde? | **Solo WhatsApp.** Nada de SMS, ni Amazon SES, ni ayuda desde Administración por ahora. | Un solo canal, el de YCloud (spec 041). |
| 2 | ¿A qué número, al registrarse? | **Se pide el WhatsApp al solicitar acceso**, con el mismo selector de país de la Ficha Inicial (`PhoneCountryInput`). Si el código no llega por correo, se ofrece mandarlo a ese número. Ese número **rellena el WhatsApp de la Ficha Inicial**. | FR-003, FR-005, FR-006. |
| 3 | ¿A qué número, al recuperar la contraseña? | **Al WhatsApp que la cuenta ya tiene guardado** (el de la ficha o el del registro). **Nunca** a un número que se escriba en ese momento: eso permitiría robar cuentas. | FR-008, FR-009. |

## Contexto: lo que ya existe (verificado en el código el 2026-11-06)

| # | Qué hay hoy | Dónde |
|---|---|---|
| E1 | El código de 6 dígitos del alta: `POST /auth/email-verification/send` y `/confirm`. Vigencia **10 min**, **5 intentos**, una espera de 30 s y 5 envíos por hora por correo. | `VerificacionEmailService`, `CodigoVerificacionEmailRedisAdapter` |
| E2 | Recuperar la contraseña por código: `POST /auth/password/forgot`, `/verify-code` y `/reset-confirm` (D-102). Responde **202 siempre**: no revela si el correo tiene cuenta. | `ResetContrasenaService`, `ResetContrasenaPorCodigoController` |
| E3 | El WhatsApp por YCloud: puerto `EnvioDeWhatsAppPort`, `YCloudAdapter` (`POST /v2/whatsapp/messages/sendDirectly`, `X-API-Key`), número emisor `+51913409458`, normalización y máscara `+51 9•• ••• •21` (`NumeroDeWhatsApp`). | `notifications` (spec 041, D-322) |
| E4 | El WhatsApp de cada persona es **una sola respuesta**, `whatsapp`, de la Ficha Inicial (`"+51 987654321"`); la lee `WhatsappDeLaFichaFinder` y la escribe también Yo → Editar perfil. | `onboarding`, `users.api` |
| E5 | La solicitud de acceso ya tiene un campo `phone` (`solicitudes_cuenta.telefono`, opcional desde D-61) que hoy nadie llena. | `AccountRequest`, `SubmitAccountRequestRequest` |

## Cómo funciona

**Registro.** «Solicitar acceso» pide además el WhatsApp. Se pide el código al correo como siempre. En la pantalla del código, **60 segundos** después aparece «¿No te llegó? Envíamelo por WhatsApp» con el número enmascarado. Al tocarlo, el servidor manda **el mismo código** por WhatsApp y la persona lo escribe en las mismas casillas. Cuando Administración acepta la solicitud, el número queda en su Ficha Inicial.

**Recuperar la contraseña.** En la pantalla del código, a los 60 segundos aparece el mismo enlace, **sin número**: el servidor manda el código al WhatsApp guardado de esa cuenta, si tiene uno, y responde igual en todos los casos.

### Mismo código, no uno nuevo (por qué)

Si el código del correo sigue vigente, el WhatsApp manda **ese mismo** (`CodigoVerificacionEmailPort.vigente` / `CodigoResetContrasenaPort.vigente`, lectura sin consumir). Si ya venció, se genera uno nuevo con la **misma vigencia (10 min)** y los **mismos 5 intentos**, y se verifica por los endpoints de siempre. Así:

1. **No regala intentos.** Generar uno nuevo reinicia el contador de intentos fallidos; con el mismo, pedir por WhatsApp no da cinco intentos más para adivinar.
2. **No hay dos códigos válidos a la vez.** Si el correo llega tarde, dice lo mismo que el WhatsApp.
3. **No cambia la verificación**: `/email-verification/confirm` y `/password/verify-code` no se tocaron.

Límite aceptado: si después de pedirlo por WhatsApp la persona toca «Reenviar» al correo, se genera uno nuevo (como siempre) y el del WhatsApp deja de servir.

### Anti-enumeración en la recuperación (por qué la app no muestra el número)

El pedido de la brief fue: «si la cuenta no tiene WhatsApp, responde igual que si lo mandó, pero la app ya no ofrece la opción a quien no tiene número». Las dos mitades no se pueden cumplir a la vez: para no ofrecerla a quien no tiene número, la app tendría que **saber** si esa cuenta tiene número, y eso es justo lo que no se puede revelar (diría que el correo tiene cuenta, y parte de su teléfono). Se decidió:

- **La app ofrece el enlace a todos** en la recuperación (con el interruptor prendido), sin número.
- **El servidor responde 202 sin cuerpo siempre**: sin cuenta, sin contraseña (entra solo con Google o Apple), sin WhatsApp guardado, con el cupo de ese número agotado o si el proveedor falla.
- **El envío corre en otro hilo** (`CodigoPorWhatsApp.enviarSinEsperar`): lo que tarda YCloud no se suma a la respuesta, así tampoco el tiempo lo delata. Lo que sí ocurre en el hilo del pedido (leer la cuenta y su ficha en Postgres) cuesta milisegundos, del mismo orden que lo que ya hace `/forgot`.
- La app dice siempre lo mismo: «Si tu cuenta tiene un WhatsApp guardado, te enviamos el código ahí.».

En el **registro** sí se muestra el número enmascarado: lo acaba de escribir la propia persona y el correo todavía no es de nadie.

## Requirements

### Servidor

- **FR-001**: El interruptor de Soporte TI `auth.codigo_whatsapp` (área «Acceso», público como `registro`) **nace apagado**. Apagado, los tres endpoints responden **404 `FUNCIONALIDAD_NO_DISPONIBLE`** y no se manda nada.
- **FR-002**: `GET /api/v1/auth/whatsapp-code` (público) responde `{ "available": true|false }`: `true` solo si hay clave de YCloud, número emisor y plantilla del código (`WHATSAPP_PLANTILLA_CODIGO`).
- **FR-004**: El código viaja en la **plantilla de categoría AUTENTICACIÓN** (nombre en `WHATSAPP_PLANTILLA_CODIGO`, por defecto `renaser_codigo_v1`, idioma de `WHATSAPP_IDIOMA`): el código en el único hueco del cuerpo y otra vez en el botón **«Copiar código»** (componente `button`, `sub_type: url`, `index: 0`). Se reutiliza `YCloudAdapter`; no hay otro cliente.
- **FR-005**: `POST /api/v1/auth/whatsapp-code/registration` `{ email, whatsapp }` manda el código del alta al número escrito y responde **202 `{ "sentTo": "+51 9•• ••• •21" }`**. Un número sin forma de WhatsApp (`+` y de 8 a 15 dígitos) es **400**; sin configuración del proveedor, o si el proveedor lo rechaza, **409** con un texto para la persona. Un corte o un 5xx del proveedor («no se supo») cuenta como enviado y no se reintenta.
- **FR-006**: Al **aceptar** la solicitud, el WhatsApp que la persona escribió (`solicitudes_cuenta.telefono`) queda como la respuesta `whatsapp` de su Ficha Inicial, en la misma transacción, **sin pisar** uno que ya tenga y solo si tiene forma de número. La app rellena la ficha con lo guardado (ya lo hacía).
- **FR-007**: El código por WhatsApp es **el mismo del correo** mientras siga vigente; si no, uno nuevo con la misma vigencia (10 min) y los mismos intentos (5). Se verifica por `/email-verification/confirm` y `/password/verify-code`, sin cambios.
- **FR-008**: `POST /api/v1/auth/whatsapp-code/password` `{ email }` manda el código de la recuperación **solo** al WhatsApp guardado de la cuenta (la respuesta `whatsapp` de la ficha) y solo a una cuenta con contraseña. **El pedido no acepta un número.**
- **FR-009**: La recuperación responde **202 sin cuerpo** en todos los casos (con o sin cuenta, con o sin número, sin cupo del número) y **no espera al proveedor** (el envío va en otro hilo).
- **FR-010**: Límites (WhatsApp cuesta y no puede ser un cañón contra un número ajeno): **10 por hora por origen** (sumando alta y recuperación), en el alta **una espera de 60 s** y **3 por hora por correo**, **3 por hora por número**, en la recuperación **3 por hora por (origen, correo)**, y **300 por hora en total**. En el alta un límite es **429** con su texto; en la recuperación solo los de origen y (origen, correo) son 429 (no dependen de la cuenta); el del número y el global se saltan **en silencio**.
- **FR-011**: El número va **enmascarado** en toda respuesta y en todo registro (`+51 9•• ••• •21`); el código nunca se registra. Los comandos y las peticiones no imprimen el número.

### App

- **FR-003**: Con el canal disponible (`GET /auth/whatsapp-code` → `available: true`), «Solicitar acceso» suma el campo **WhatsApp** con el selector de país de la ficha (`PhoneCountryInput`) y la validación de la ficha (`validarWhatsapp`) más la forma internacional del servidor; es obligatorio y viaja como `phone` de la solicitud (`"+51 987654321"`).
- **FR-012**: En la pantalla del código (alta y recuperación), **60 segundos** después de mandar el código aparece «¿No te llegó? Envíamelo por WhatsApp», un botón de **48** de alto. En el alta dice debajo a qué número va, enmascarado; en la recuperación no muestra ningún número.
- **FR-013**: Sin el interruptor prendido, sin configuración o contra un backend anterior (404, error de red), la app **queda exactamente como hoy**: ni el campo en «Solicitar acceso» ni el enlace en la pantalla del código.
- **FR-015**: Al tocarlo: en el alta, «Te enviamos el código por WhatsApp a +51 9•• ••• •21.»; en la recuperación, «Si tu cuenta tiene un WhatsApp guardado, te enviamos el código ahí.». Un error (429, 409, red) se muestra con el texto del servidor y se puede volver a tocar.
- **FR-014**: La Política de privacidad dice que el WhatsApp se pide al solicitar acceso y que se usa también para mandar códigos de verificación.

### Criterios de éxito

- **SC-001**: De punta a punta en el emulador, con un YCloud falso: solicitar acceso con WhatsApp → código por WhatsApp → la solicitud queda enviada; aceptada, la Ficha Inicial abre con ese WhatsApp; y recuperar la contraseña por WhatsApp. [sin prueba automática: E2E en el emulador contra el backend local y un YCloud falso; capturas en el PR]
- **SC-002**: Con el interruptor apagado, el servidor responde 404 y la app no muestra nada nuevo.
- **SC-003**: Ninguna respuesta ni ningún registro tiene el número completo.
- **SC-004**: Un WhatsApp de verdad llega con la plantilla aprobada. [sin prueba automática: requiere la plantilla aprobada por Meta; lo prueba el dueño con un teléfono de prueba, nunca contra producción]

## La plantilla de WhatsApp (la crea el dueño en YCloud; la aprueba Meta)

Meta tiene un formato fijo para la categoría **Autenticación**: no se escribe el texto libre, se eligen las opciones y Meta arma el mensaje.

| Campo | Valor |
|---|---|
| Nombre | `renaser_codigo_v1` (si Meta pide otro, se cambia `WHATSAPP_PLANTILLA_CODIGO`) |
| Categoría | **Authentication** |
| Idioma | **Spanish (es)** — el mismo de `WHATSAPP_IDIOMA` |
| Entrega del código | **Copy code** (botón «Copiar código») |
| Recomendación de seguridad | **Sí** («Por tu seguridad, no lo compartas.») |
| Vencimiento | **Sí, 10 minutos** («Este código caduca en 10 minutos.») — la vigencia real del código |
| Texto del botón | «Copiar código» |

Cómo se ve (lo arma Meta): **«*{{1}}* es tu código de verificación. Por tu seguridad, no lo compartas.»** — pie: «Este código caduca en 10 minutos.» — botón: «Copiar código».

Pasos: YCloud → WhatsApp → Templates → **Create template** → categoría *Authentication*, nombre `renaser_codigo_v1`, idioma *Spanish*, *Copy code*, marcar la recomendación de seguridad y el vencimiento en 10 minutos → enviar a revisión. Cuando diga **Approved**, prender `auth.codigo_whatsapp` en Soporte TI.

## Costos

Meta cobra cada mensaje de autenticación (en Perú, unos centavos de dólar). Los límites de FR-010 acotan el peor caso a **300 mensajes por hora** en total.

## Privacidad

La política suma que el WhatsApp se pide al solicitar acceso y que se usa para mandar códigos de verificación cuando no llegan al correo. El número no se copia a otra columna: es la respuesta `whatsapp` de la ficha (y, antes de aceptar, el teléfono de la solicitud).

## Assumptions (decididas por el equipo; el dueño las puede cambiar)

- El WhatsApp es **obligatorio** en «Solicitar acceso» cuando el canal está disponible (es el canal de respaldo y la ficha lo pide igual). Con el canal apagado no se pide, para que la app quede como hoy.
- Los números de los límites (10 / 3 / 3 / 300 por hora, espera de 60 s).
- El enlace aparece a los 60 s contados desde que se mandó el primer código; reenviar por correo no lo reinicia.
- Un alta por WhatsApp prueba que la persona controla **ese WhatsApp**, no el correo: la cuenta sigue pasando por la aprobación de Administración, que ve el teléfono en la solicitud.
