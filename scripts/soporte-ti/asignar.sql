-- =====================================================================================
-- Da el rol Soporte TI (SOPORTE_TI en la base, IT_SUPPORT en la API) a UNA cuenta existente.
-- Spec 002, D-267. Lo corre el dueño a mano, contra la base que corresponda; nunca la app.
--
-- POR QUE UN SCRIPT Y NO UN ENDPOINT
--
--   El dueño decidió (2026-10-13) que el rol lo tenga solo su cuenta de programador y que se
--   asigne desde la base: ningún endpoint lo da, lo quita, lo invita ni aprueba con él
--   (FR-022), ni Admin ni Alquimista. El correo NO queda en el repo ni en una migración:
--   entra por la variable de psql `correo`.
--
-- USO (psql 12+):
--
--   psql "$URL_DE_LA_BASE" -v ON_ERROR_STOP=1 -v correo='tu-correo@dominio.com' \
--        -f scripts/soporte-ti/asignar.sql
--
-- QUÉ VERIFICA ANTES DE CAMBIAR NADA (si algo falla, aborta sin tocar la fila):
--
--   * que exista una cuenta con ese correo y esté ACTIVA;
--   * que no deje la plataforma sin Administración: si la cuenta es el último ADMIN o
--     ALQUIMISTA activo, se aborta. Soporte TI todavía NO pasa los guards de Administración de
--     los servicios (D-267, pendiente): pasar al único administrador a Soporte TI dejaría a
--     nadie aprobando solicitudes ni armando grupos.
--
-- DESPUÉS: el cambio rige en el próximo pedido (el rol se lee de la base en cada pedido). No
-- emite RolDeUsuarioCambiadoEvent: si la cuenta era ADMIN o ALQUIMISTA, sigue anotada en los
-- chats de soporte, pero ya no los puede abrir (el acceso a un SOPORTE se decide por el rol de
-- ahora, AutorizacionDeConversacionService).
--
-- PARA QUITARLO: un UPDATE con el rol que corresponda, también a mano, por ejemplo
--   UPDATE renaser.usuarios SET rol = 'ALQUIMISTA' WHERE lower(email) = lower(:'correo');
-- =====================================================================================

\if :{?correo}
\else
  \echo 'Falta el correo: -v correo=tu-correo@dominio.com (no se cambió nada)'
  \quit
\endif

BEGIN;

-- psql no reemplaza variables dentro de un bloque $$: el correo pasa por un ajuste local a esta transacción.
SELECT set_config('soporte_ti.correo', :'correo', true);

DO $$
DECLARE
    cuenta      record;
    admins_vivos integer;
BEGIN
    SELECT id, rol::text AS rol, estado::text AS estado
      INTO cuenta
      FROM renaser.usuarios
     WHERE lower(email) = lower(current_setting('soporte_ti.correo'));

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No hay ninguna cuenta con ese correo';
    END IF;
    IF cuenta.estado <> 'ACTIVO' THEN
        RAISE EXCEPTION 'La cuenta no está activa (estado %): actívala antes por la app', cuenta.estado;
    END IF;

    SELECT count(*) INTO admins_vivos
      FROM renaser.usuarios
     WHERE rol IN ('ADMIN', 'ALQUIMISTA') AND estado = 'ACTIVO' AND id <> cuenta.id;
    IF cuenta.rol IN ('ADMIN', 'ALQUIMISTA') AND admins_vivos = 0 THEN
        RAISE EXCEPTION 'Es el último ADMIN/ALQUIMISTA activo: la plataforma quedaría sin Administración';
    END IF;

    UPDATE renaser.usuarios SET rol = 'SOPORTE_TI' WHERE id = cuenta.id;
    RAISE NOTICE 'Cuenta % pasó de % a SOPORTE_TI', cuenta.id, cuenta.rol;
END
$$;

COMMIT;
