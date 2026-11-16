# Spec 054 — Aviso de «WhatsApp sin saldo» en el panel de Administración

**Input**: pedido del dueño (2026-11-16). Backend y app (rama `aviso-whatsapp-sin-saldo`). Decisión: D-361.

## Por qué

Entre el 13 y el 16/11/2026 los recordatorios por WhatsApp no llegaron: 35 envíos quedaron `FALLIDO` con el motivo «The account balance is insufficient.»
(la cuenta de YCloud se quedó sin saldo) y nadie se enteró. Los códigos por WhatsApp (recuperar la contraseña, confirmar el número en Yo) usan la misma
cuenta. Administración necesita verlo a tiempo para recargarla.

## Requisitos

- **FR-001**: Hay que avisar cuando, en las últimas 24 horas, existe al menos un envío de `entregas_whatsapp` en estado `FALLIDO` cuyo `motivo` contiene
  «balance is insufficient» (sin distinguir mayúsculas; el patrón vive en un solo lugar, `AvisoDeSaldoDeWhatsApp.PATRON_DEL_MOTIVO`). Los `OMITIDO` (por ejemplo
  «Sin WhatsApp») y las fallas de otro motivo no cuentan.
- **FR-002**: El aviso desaparece solo: si después de la última de esas fallas salió un envío bien (`ACEPTADO`, `ENVIADO`, `ENTREGADO` o `LEIDO`), no hay aviso.
- **FR-003**: La respuesta dice cuántos fallaron por saldo desde el último envío que salió bien (todos los de la ventana si ninguno salió bien) y desde cuándo
  (el primero de esos).
- **FR-004**: `GET /api/v1/admin/whatsapp/estado`, solo lectura, solo ADMIN y ALCHEMIST activos; mentor, líder, guía y aprendiz, una cuenta suspendida y quien no
  tiene sesión no lo ven (403/401). No depende del interruptor `eventos.whatsapp`.
- **FR-005**: La respuesta es `{ sinSaldo, fallaron, desde }` (`desde` en ISO-8601, UTC; `null` si no hay aviso). Sin números, nombres ni textos de mensaje.
- **FR-006**: En la app, el panel de Administración muestra arriba una tarjeta de aviso con el texto «Los mensajes por WhatsApp no están saliendo: la cuenta de
  YCloud no tiene saldo. Recárgala para que vuelvan a llegar los recordatorios y los códigos.», y debajo «N sin enviar desde el sábado 14 a las 10:20» (hora de
  Lima). Sin aviso no hay tarjeta ni hueco.
- **FR-007**: Solo Administración y Alquimista piden el estado y ven la tarjeta; ningún otro rol lo pide. Si la lectura falla, el panel no muestra nada (el aviso
  es un extra, no bloquea el panel).

## Lo que no cubre

- Lo que se vigila son los recordatorios de eventos: `entregas_whatsapp` exige un evento, así que un código por WhatsApp que falle por saldo no se anota ahí.
  Con la misma cuenta sin saldo, los recordatorios fallan antes o después y el aviso sale.
- No hay aviso por push ni por correo, ni migración nueva.
