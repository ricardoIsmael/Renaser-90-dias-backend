-- V142 — La foto de lo elegido al aceptar guarda también el rol personalizado, con su nombre de ese día (spec 025; D-302)
--
-- Por qué se toca la base
-- -----------------------
-- V140 guarda `rol_elegido`, el rol BASE con el que entró la persona. Pero Administración puede aceptar con un rol personalizado («Mentor junior», que
-- hereda de Mentor, D-272) y el historial de «Solicitudes» y la ficha de la persona tienen que decir con cuál entró, no solo «Mentor». El dueño (31/10)
-- pidió que la foto de lo aceptado lo recuerde con su nombre de ESE momento: si después se renombra o se borra el rol, lo aceptado sigue diciendo lo que se
-- decidió ese día.
--
-- Por qué no alcanza el modelo actual / qué se reutiliza
-- ------------------------------------------------------
-- · El rol personalizado VIVO de la cuenta está en `usuarios.rol_personalizado_id` y su nombre en `roles_personalizados`; ambos cambian (se reasigna, se
--   renombra, se borra). Por eso es una foto, en la misma fila y por la misma razón que las columnas de V140: dato histórico, nunca la fuente del rol.
-- · No hay tabla nueva: son dos columnas de la fila que ya existe, que se leen y escriben siempre juntas con ella.
--
-- Por qué estos nombres y tipos
-- -----------------------------
-- · `rol_personalizado_id`: referencia al rol con `ON DELETE SET NULL`: si el rol se borra, el id se suelta pero el nombre queda (la foto sigue diciendo
--   cómo se llamaba). NULL cuando la persona entró con un rol base, que es lo normal.
-- · `rol_personalizado_nombre`: el nombre que tenía al aceptar. El CHECK exige que, si hay id, haya nombre.
-- Las solicitudes anteriores quedan con las dos en NULL (entraron con un rol base).

SET search_path TO renaser, public;

ALTER TABLE solicitudes_cuenta
    ADD COLUMN rol_personalizado_id     uuid REFERENCES roles_personalizados (id) ON DELETE SET NULL,
    ADD COLUMN rol_personalizado_nombre text CHECK (rol_personalizado_nombre IS NULL OR length(btrim(rol_personalizado_nombre)) BETWEEN 1 AND 60),
    ADD CONSTRAINT solicitudes_rol_personalizado_con_nombre CHECK (rol_personalizado_id IS NULL OR rol_personalizado_nombre IS NOT NULL);

COMMENT ON COLUMN solicitudes_cuenta.rol_personalizado_id IS
    'Rol personalizado con el que entró la persona (V142, spec 025). Se suelta si el rol se borra; el nombre queda. NULL = entró con un rol base.';
COMMENT ON COLUMN solicitudes_cuenta.rol_personalizado_nombre IS
    'Nombre que tenía ese rol personalizado al aceptar (V142, spec 025): foto histórica, no cambia si el rol se renombra.';
