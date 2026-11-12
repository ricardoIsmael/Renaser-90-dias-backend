# Spec 052 — «Solicitar acceso» por pasos

**Input**: pedido del dueño (2026-11-12): rediseñar el ingreso y el registro. Aprobó el diseño del **registro** («quedó perfecto»);
el **login** queda para después («luego lo vemos») y no se toca. Cambio **solo de la app**: el backend, los endpoints y los campos
no cambian.

## Decisiones del dueño (2026-11-12)

| # | Tema | Decisión |
|---|---|---|
| D1 | Forma | Un grupo de campos por pantalla: «¿Cómo te llamas?» (nombres, apellidos), «Tu correo», «Tu WhatsApp» (+51 y número), «Verifica tu correo o WhatsApp» (el código de 6 dígitos) y «Tu contraseña» (con los términos). |
| D1b | Orden | **El contacto se verifica ANTES de crear la cuenta** (decisión del dueño del 12/11): primero el código, después la contraseña; un solo código. |
| D2 | Fondo y texto | Fondo liso (sin el fénix), solo el título grande, sin subtítulos. Arriba: flecha de volver, avance de 4 segmentos dorados y «N de 4». |
| D3 | Botón y teclado | «Continuar» / «Crear mi cuenta» abajo, siempre visible SOBRE el teclado; el teclado nunca tapa el campo ni el botón. |
| D4 | Final | «Crear mi cuenta» y un «Listo, <nombre>» con «Te avisaremos cuando aprueben tu acceso.» y «Ir al inicio». |
| D5 | Login | Sin cambios por ahora (pospuesto por el dueño). |

## Lo que hay hoy (leído el 2026-11-12)

`LoginScreen` mezcla el login y «Solicitar acceso» en una sola vista con 5 campos y un botón «Continuar y recibir código». Al enviar:
`email-verification/send` (tras comprobar que el correo está libre), `email-verification/confirm` con el código (devuelve el
`verificationToken`, válido 30 min) y `account-requests` con `{email, fullName, phone, contrasena, verificationToken}`. **El servidor ya
admite verificar primero y crear después**: el token no depende de la contraseña y se consume recién al crear la solicitud
(`AccountRequestService.requireEmailVerificado`). No hace falta ningún cambio de backend.

## Requisitos

- **FR-001** «Solicitar acceso» MUST mostrar un paso por pantalla, en este orden: nombre (Nombres y Apellidos), correo, WhatsApp,
  verificación del código y contraseña con los términos. El paso de WhatsApp MUST aparecer solo si el servidor ofrece el canal (spec 043,
  FR-013); sin él son 4 pasos. El avance («N de M») cuenta el paso del código.
- **FR-002** Cada paso MUST validarse antes de avanzar, diciendo qué falta bajo el campo, con el borde de error; no se avanza con
  un paso incompleto. Los mensajes son los de siempre.
- **FR-003** Atrás (la flecha y el gesto del sistema) MUST volver al paso anterior sin perder lo escrito; desde el primero, al login.
  Con el contacto ya verificado, atrás y adelante MUST saltar el paso del código (no se pide dos veces); si cambia el correo, el token
  MUST descartarse y volver a verificar.
- **FR-004** El botón MUST quedar fuera del scroll (visible sobre el teclado) y la pantalla MUST usar la altura que deja el teclado.
- **FR-005** En el último paso, la contraseña MUST tener ver/ocultar, un medidor de fuerza de 3 segmentos, una línea de ayuda con la
  regla del servidor (mínimo 12 caracteres) y el check «Acepto los términos», obligatorio para continuar. No hay campo de confirmación.
- **FR-006** El código MUST pedirse al correo tras el paso del WhatsApp (con la comprobación de correo libre; un 409 lleva al paso del
  correo) y MUST confirmarse ANTES de la contraseña: sin un código válido no se llega a la contraseña ni a «Listo». La pantalla del código
  conserva `CodigoOtpInput`, la cuenta atrás del reenvío, «Por WhatsApp» a los 60 s (spec 043) y el motivo del servidor ante un código
  malo. La solicitud MUST crearse al final con el token ya obtenido, con los mismos campos que hoy, sin pedir ni confirmar otro código;
  si el token venció, MUST volver al paso del código con el motivo del servidor.
- **FR-007** Tras crear la solicitud MUST mostrarse «Listo, <primer nombre>» con «Ir al inicio» (vuelve al login) y un enlace chico
  para consultar el estado de la solicitud.
- **FR-008** El login, la recuperación de contraseña y el alta con Google MUST quedar como están.

## Criterios de éxito

- **SC-001** Una persona completa el registro en 4 pantallas sin que el teclado tape un campo ni el botón, también en un teléfono chico.
- **SC-002** El cuerpo de `account-requests` es idéntico al de antes para los mismos datos, y el código se pide una sola vez.

## Fuera de alcance

El rediseño del login (con el fénix de fondo, tarjeta y Google/Apple), pospuesto por el dueño; la pantalla de recuperación.

## Notas de la implementación

- La contraseña conserva el mínimo de 12 caracteres del servidor (`@Size(min = 12)`); el diseño decía «8, con mayúscula y número».
- «Acepto los términos» es solo de la app (no viaja al servidor) y enlaza la Política de privacidad, la única página legal publicada.
