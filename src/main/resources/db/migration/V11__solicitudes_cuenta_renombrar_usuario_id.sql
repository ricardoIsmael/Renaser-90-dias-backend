-- Renombra solicitudes_cuenta.supabase_user_id a usuario_id. Es el ultimo rastro de Supabase Auth
-- en el esquema: desde la decision del dueno del proyecto del 2026-08-26 (docs/MODULO_AUTH.md)
-- la identidad es propia, no hay proveedor externo, y ese UUID lo genera nuestro backend. El
-- nombre viejo describia un sistema que ya no participa y hacia creer que la columna guardaba
-- un id ajeno.
--
-- Por que se toca una BD declarada congelada (D-40): un RENAME COLUMN en Postgres es una
-- operacion de catalogo -- no reescribe la tabla, no mueve datos, no invalida los indices ni la
-- constraint UNIQUE, que siguen la columna. El riesgo real no es la base sino el codigo que la
-- lee: por eso este cambio va junto con el rename del campo en la entidad JPA, el dominio y el
-- body de POST /api/v1/users/invite, en el mismo commit. Aplicar la migracion sin ese codigo
-- (o al reves) rompe el arranque.
--
-- No confundir con usuario_creado_id, que sigue igual: aquella traza QUIEN quedo creado al
-- aprobar; esta es el usuario duenno de la solicitud, creado ya en el alta desde el 2026-08-27
-- (D-49) y borrado si la solicitud se rechaza (anti-squatting de correos).

BEGIN;

-- Explicito a proposito, por el mismo motivo que V3: si esta migracion corre sola en un
-- despliegue posterior, el search_path que fija V1 no esta puesto en la sesion.
SET search_path TO renaser, public;

ALTER TABLE solicitudes_cuenta RENAME COLUMN supabase_user_id TO usuario_id;

COMMENT ON COLUMN solicitudes_cuenta.usuario_id IS
    'Usuario duenno de esta solicitud. Lo crea el alta (D-49, 2026-08-27) y lo borra el rechazo '
    'para liberar el correo. Se llamaba supabase_user_id hasta el 2026-08-31, cuando la identidad '
    'dejo de venir de Supabase Auth (docs/MODULO_AUTH.md).';

COMMIT;
