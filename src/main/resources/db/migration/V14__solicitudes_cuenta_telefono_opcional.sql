-- solicitudes_cuenta.telefono deja de ser NOT NULL. Decision del dueno del proyecto (2026-09-01,
-- D-61): el registro tiene que ser lo mas liviano posible -- correo, nombre y contrasena -- y el
-- telefono se pide despues, en la Ficha Inicial del onboarding, junto con el resto de los datos
-- completos. Pedir el telefono en el alta agregaba friccion en el unico momento del flujo donde
-- la persona todavia no invirtio nada y abandona mas facil.
--
-- El sintoma concreto que cierra: el frontend ya dejo de mandarlo, asi que
-- POST /api/v1/account-requests llegaba con phone:null y respondia 400 -- nadie podia registrarse.
--
-- Efecto colateral buscado: el alta por proveedor social (Google/Apple/Facebook) queda
-- desbloqueada. Google no devuelve telefono y el boton social nunca lo mandaba, asi que
-- AutenticacionSocialService.requirePhoneParaAlta rechazaba toda cuenta nueva por Google -- y como
-- el `code` de OAuth es de un solo uso, ese primer intento fallido lo consumia igual. Es la
-- limitacion que docs/MODULO_AUTH.md §6.7 punto 3 describia y que A-8 dejaba abierta.
--
-- Lo que NO cambia: la columna se conserva y se sigue guardando el telefono cuando viene. Solo
-- deja de ser obligatorio. usuarios.telefono ya era nullable desde V1 y no se toca.
--
-- Por que se toca una BD declarada congelada (D-40): DROP NOT NULL es una operacion de catalogo
-- -- no reescribe la tabla, no mueve datos, no invalida indices. Y es estrictamente permisiva:
-- ninguna fila existente deja de ser valida. El riesgo real esta en el codigo que asumia la
-- columna llena, por eso esta migracion va en el mismo cambio que la baja del @NotBlank en el
-- DTO web, en el comando de aplicacion y del requireNotBlank en el agregado AccountRequest.

BEGIN;

-- Explicito a proposito, por el mismo motivo que V3/V11/V12/V13: si esta migracion corre sola en
-- un despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

ALTER TABLE solicitudes_cuenta ALTER COLUMN telefono DROP NOT NULL;

COMMENT ON COLUMN solicitudes_cuenta.telefono IS
    'WhatsApp; aterriza en usuarios.telefono al aprobar. OPCIONAL desde el 2026-09-01 (D-61): el '
    'alta pide solo correo, nombre y contrasena, y el telefono se recoge en la Ficha Inicial del '
    'onboarding. NULL significa "todavia no lo dio", no "no tiene".';

COMMIT;
