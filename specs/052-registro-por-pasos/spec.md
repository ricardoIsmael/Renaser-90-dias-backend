# Spec 052 — «Solicitar acceso» por pasos

**Input**: pedido del dueño (2026-11-12): rediseñar el ingreso y el registro. Aprobó el diseño del **registro** («quedó perfecto»);
el **login** queda para después («luego lo vemos») y no se toca. Cambio **solo de la app**: el backend, los endpoints y los campos
no cambian.

## Decisiones del dueño (2026-11-12)

| # | Tema | Decisión |
|---|---|---|
| D1 | Forma | Un grupo de campos por pantalla, 4 pasos: «¿Cómo te llamas?» (nombres, apellidos), «Tu correo», «Tu WhatsApp» (+51 y número), «Tu contraseña». |
| D2 | Fondo y texto | Fondo liso (sin el fénix), solo el título grande, sin subtítulos. Arriba: flecha de volver, avance de 4 segmentos dorados y «N de 4». |
| D3 | Botón y teclado | «Continuar» / «Crear mi cuenta» abajo, siempre visible SOBRE el teclado; el teclado nunca tapa el campo ni el botón. |
| D4 | Final | El código de 6 dígitos de siempre y un «Listo, <nombre>» con «Te avisaremos cuando aprueben tu acceso.» y «Ir al inicio». |
| D5 | Login | Sin cambios por ahora (pospuesto por el dueño). |

## Lo que hay hoy (leído el 2026-11-12)

`LoginScreen` mezcla el login y «Solicitar acceso» en una sola vista con 5 campos (nombres, apellidos, correo, WhatsApp si el
servidor ofrece el canal, contraseña y confirmación) y un botón «Continuar y recibir código». Al enviar: `email-verification/send`
(tras comprobar que el correo está libre), luego `email-verification/confirm` con el código y `account-requests` con
`{email, fullName, phone, contrasena, verificationToken}`. La cuenta queda pendiente de aprobación.

## Requisitos

- **FR-001** «Solicitar acceso» MUST mostrar un paso por pantalla, en este orden: nombre (Nombres y Apellidos), correo, WhatsApp
  y contraseña. El paso de WhatsApp MUST aparecer solo si el servidor ofrece el canal (spec 043, FR-013); sin él son 3 pasos.
- **FR-002** Cada paso MUST validarse antes de avanzar, diciendo qué falta bajo el campo, con el borde de error; no se avanza con
  un paso incompleto. Los mensajes son los de siempre.
- **FR-003** Atrás (la flecha y el gesto del sistema) MUST volver al paso anterior sin perder lo escrito; desde el primero, al login.
  Desde el código, al último paso, con todo intacto.
- **FR-004** El botón MUST quedar fuera del scroll (visible sobre el teclado) y la pantalla MUST usar la altura que deja el teclado.
- **FR-005** En el último paso, la contraseña MUST tener ver/ocultar, un medidor de fuerza de 3 segmentos, una línea de ayuda con la
  regla del servidor (mínimo 12 caracteres) y el check «Acepto los términos», obligatorio para continuar. No hay campo de confirmación.
- **FR-006** El contrato MUST NOT cambiar: se pide el código al correo (con la comprobación de correo libre) y, al confirmarlo, se
  crea la solicitud con los mismos campos que hoy; un 409 de correo ocupado MUST llevar al paso del correo.
- **FR-007** Tras confirmar el código MUST mostrarse «Listo, <primer nombre>» con «Ir al inicio» (vuelve al login) y un enlace chico
  para consultar el estado de la solicitud.
- **FR-008** El login, la recuperación de contraseña y el alta con Google MUST quedar como están.

## Criterios de éxito

- **SC-001** Una persona completa el registro en 4 pantallas sin que el teclado tape un campo ni el botón, también en un teléfono chico.
- **SC-002** El cuerpo de `account-requests` es idéntico al de antes para los mismos datos.

## Fuera de alcance

El rediseño del login (con el fénix de fondo, tarjeta y Google/Apple), pospuesto por el dueño; la pantalla de recuperación.

## Notas de la implementación

- La contraseña conserva el mínimo de 12 caracteres del servidor (`@Size(min = 12)`); el diseño decía «8, con mayúscula y número».
- «Acepto los términos» es solo de la app (no viaja al servidor) y enlaza la Política de privacidad, la única página legal publicada.
