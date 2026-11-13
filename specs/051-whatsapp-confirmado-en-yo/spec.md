# Spec 051 — WhatsApp confirmado en Yo

**Feature Branch**: `whatsapp-en-yo` (backend y app)
**Created**: 2026-11-12
**Decisión**: D-352 (`docs/MODULOS_A_AVANZAR.md`); en la app, `docs/decisiones/` del 12/11/2026 (autoriza tocar `Yo`).
**Input** (decisión del dueño, 12/11/2026): las cuentas del personal (ALQUIMISTA/admin, MENTOR, GUÍA y cualquier rol sin Ficha Inicial)
no tienen WhatsApp, así que el código de recuperar la contraseña por WhatsApp (spec 043) nunca les llega: ese código lee la respuesta
`whatsapp` de la Ficha Inicial. En `Yo`, **cualquiera —personal o aprendiz— puede agregar o cambiar su número de WhatsApp y solo se
guarda después de confirmarlo con un código de 6 dígitos que llega por WhatsApp a ese número**. Ese número confirmado es el que usa la
recuperación.

## Decisiones tomadas (recomendada = la elegida)

| # | Pregunta | Decisión |
|---|---|---|
| D1 | ¿Dónde se guarda? | **Tabla propia `whatsapp_confirmados`** (V192, módulo `users`): una fila por cuenta, número UNIQUE, FK con CASCADE. No la respuesta de la Ficha (el personal no tiene ficha y «declarado» no es «confirmado») ni `usuarios.telefono` (nadie la lee; ambigua). |
| D2 | ¿Qué número usa la recuperación? | **El confirmado; sin él, el de la Ficha** (comportamiento de la spec 043). Los aprendices que ya funcionan siguen igual; la ficha no se toca. |
| D3 | ¿Número ya confirmado en otra cuenta? | **Se rechaza** con 409 `NUMERO_EN_USO`, y **solo al confirmar** (no al pedir el código): así no es un oráculo de «este número tiene cuenta». |
| D4 | ¿Cómo se manda el código? | El mismo canal, plantilla y adaptador de la spec 043 (`CodigoPorWhatsApp`). Código de 6 dígitos en Redis, 10 min, 5 intentos, con la mecánica compartida `AlmacenCodigoNumericoRedis`, clave `cuenta\|número` (espacio propio: un código de otro uso no sirve). |
| D5 | ¿Interruptor? | **`cuenta.whatsapp`** (área «Cuenta», de todos los roles, nace prendido). No se reusa `auth.codigo_whatsapp`: es público y de nadie en el catálogo. Aparte, `available` dice si el canal está configurado. |
| D6 | ¿Límites? | Por cuenta: espera de 60 s y 5 códigos/hora; confirmar, 10 intentos/hora. Y **las mismas claves de la 043** por origen (10/h), por número (3/h) y global (300/h): un número recibe 3 códigos por hora sumando los dos usos. Son del grupo ESCRITURA de la spec 050 (`/users/me/**`, sin cambios en el clasificador). |
| D8 | La spec 041 (FR-030) decía «ninguna tabla de números». | La 051 la **supera solo para esto** (decisión del dueño del 12/11): `whatsapp_confirmados`. Los recordatorios de eventos (041) siguen leyendo solo el número de la Ficha; la prueba de la 041 (`EntregasDeWhatsAppJdbcAdapterIT`) permite esta única tabla. |
| D7 | ¿Se puede quitar el número? | **No en esta spec**: solo agregar o cambiar. |

## Requirements

### Servidor

- **FR-001**: `GET /api/v1/users/me/whatsapp` (sesión, `USE_APP` propio) responde `{ "available": bool, "confirmed": "+51 9•• ••• •21" | null }`. `available` es `true` solo si hay clave, número emisor y plantilla. Nunca devuelve el número completo.
- **FR-002**: `POST /api/v1/users/me/whatsapp/code` `{ whatsapp }` manda un código al número escrito y responde **202 `{ "sentTo": "+51 9•• ••• •21" }`**. **No guarda el número.** Un número sin forma de WhatsApp (`+`, de 8 a 15 dígitos) es **422 `NUMERO_INVALIDO`**; sin canal, **409 `CANAL_NO_DISPONIBLE`**; si el proveedor lo rechaza, **409 `CODIGO_NO_SALIO`**; si ya es el confirmado de la cuenta, **409 `YA_CONFIRMADO`** y no se manda nada.
- **FR-003**: `POST /api/v1/users/me/whatsapp/confirm` `{ whatsapp, code }` guarda el número **solo si** el código es el de esa cuenta y ese número, y lo consume (un solo uso). Responde **200 `{ "confirmed": "+51 9•• ••• •21" }`**; el número anterior se reemplaza. Código equivocado, vencido, agotado o de otro número: **422 `CODIGO_INCORRECTO`**; número ya confirmado en otra cuenta: **409 `NUMERO_EN_USO`** (FR-007).
- **FR-004**: Siempre sobre la cuenta de la sesión: no hay id en la ruta ni en el cuerpo. Una cuenta suspendida recibe 403. Sin permiso o sin sesión, 401/403 por la matriz (`@RequiresPermission(USE_APP, "self")`).
- **FR-005**: La recuperación de la contraseña por WhatsApp (`POST /auth/whatsapp-code/password`, spec 043) manda el código al **número confirmado** de la cuenta; solo si no tiene, al de su Ficha. La respuesta sigue siendo 202 sin cuerpo en todos los casos (spec 043 FR-009).
- **FR-006**: Límites de D6: 429 con texto en tuteo al agotarlos.
- **FR-007**: Un número se confirma en una sola cuenta (UNIQUE en la base y 409 `NUMERO_EN_USO`, también ante dos confirmaciones a la vez).
- **FR-008**: El número va enmascarado en toda respuesta y registro; el código y el número no se imprimen (`toString` de peticiones y comandos).
- **FR-009**: Con `cuenta.whatsapp` apagado, los tres endpoints responden **503 `FUNCIONALIDAD_APAGADA`** (una funcionalidad normal apagada por Soporte TI; la app esconde la fila). Borrar la cuenta borra el número (FK CASCADE y `BorrarDatosPropiosDeCuenta`).

### App

- **FR-010**: En `Yo` hay una fila **«WhatsApp»** (para todos los roles) que muestra el número confirmado enmascarado o «Agregar». Sin canal disponible (`available: false`), con el interruptor apagado (503) o contra un servidor anterior (404), la fila no aparece.
- **FR-011**: Al tocarla se abre una hoja desde abajo: número con selector de país (+51 por defecto) → «Enviar código» → las seis casillas del código → «Confirmar» → estado confirmado. El código de un solo uso se pide, se escribe y se confirma sin salir de la hoja; el gesto de volver del sistema cierra la hoja o vuelve al paso anterior.
- **FR-012**: Un toque, un pedido: el botón se apaga mientras viaja; al confirmar, la fila relee el estado del servidor. Los errores del servidor se muestran con su texto (`codigo` y `message`): código incorrecto, número en uso, 429, canal caído.
- **FR-013**: En la recuperación de la contraseña por WhatsApp, el texto dice que el código va al WhatsApp confirmado en `Yo` o, si no hay, al de la ficha.

### Criterios de éxito

- **SC-001**: Una cuenta del personal agrega su número en `Yo`, lo confirma con el código y puede recuperar su contraseña por WhatsApp. [sin prueba automática: E2E en el emulador contra el backend local con un YCloud falso; capturas en el PR]
- **SC-002**: Un número sin confirmar nunca queda guardado: pedir el código no escribe nada y un código equivocado tampoco.
