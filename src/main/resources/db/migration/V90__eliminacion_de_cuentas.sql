-- Eliminación de cuentas (D-243, decisiones del dueño del 2026-10-02; requisito de Google Play).
--
-- Problema: el borrado definitivo de una cuenta (el que hace el Admin en el acto, o el barrido a los
-- 30 días de que la persona cerró su cuenta) tiene que poder correr para CUALQUIER cuenta, y hoy hay
-- una que lo impide y una cosa que no queda registrada:
--
-- 1. `ajustes_dia_programa.ajustado_por` es NOT NULL con ON DELETE RESTRICT. Basta que un Admin haya
--    cambiado una vez el día del programa de alguien para que su cuenta ya no se pueda borrar nunca
--    (el DELETE de `usuarios` rompe con la FK). La fila del ajuste es historia del APRENDIZ, no del
--    Admin (V21: append-only, se corrige con otro ajuste), así que no se borra: pierde el autor. Por
--    eso pasa a NULLable con ON DELETE SET NULL, el mismo trato que ya tienen las otras columnas de
--    «quién lo hizo» del esquema (`asignaciones_celula.actor_id`, `auditoria_cambios_rol.actor_id`,
--    `tickets_mentor.respondido_por`). El código sigue sin UPDATE sobre la tabla: el SET NULL lo
--    hace la base al borrar la cuenta del autor.
--    No se tocó `observaciones_mentor.autor_id` (también RESTRICT): ahí las observaciones del
--    autor las borra `leadership` antes del DELETE de la cuenta (son texto que escribió esa
--    persona), así que la FK nunca llega a dispararse; cambiar la FK obligaba a volver nullable un
--    campo que la app del Líder lee como obligatorio.
--
-- 2. El dueño pidió que quede auditado quién eliminó a quién y cuándo, SIN guardar los datos
--    borrados. No sirve ninguna tabla existente: `auditoria_cambios_rol` cuelga de `usuarios` por FK
--    (SET NULL), así que perdería justamente el id que hay que conservar, y sus columnas son de
--    rol. Tabla nueva `auditoria_eliminacion_cuentas`:
--      · `cuenta_id` y `actor_id` SIN FK, a propósito: la cuenta ya no existe (y el actor puede
--        dejar de existir). Son UUID opacos: sin la fila de `usuarios` no dicen nombre ni correo.
--      · `rol` de la cuenta al momento del hecho (texto, no FK a `roles`): para poder decir «se
--        eliminó a un Admin» sin guardar quién era.
--      · `accion` con CHECK (no un enum de Postgres, mismo criterio que V89): CERRADA (la persona
--        cerró su cuenta y empezó la gracia), RECUPERADA (un Admin la recuperó dentro de la
--        gracia), ELIMINADA_POR_ADMIN (borrado inmediato desde Administración) y
--        ELIMINADA_AL_VENCER (la borró el barrido a los 30 días).
--      · `via`: desde dónde se cerró (APP o WEB); NULL en las demás acciones.
--      · Ni correo, ni nombre, ni teléfono: es lo que el dueño pidió no guardar.
--    Append-only: el código solo inserta.
SET search_path TO renaser, public;

ALTER TABLE ajustes_dia_programa ALTER COLUMN ajustado_por DROP NOT NULL;
ALTER TABLE ajustes_dia_programa DROP CONSTRAINT ajustes_dia_programa_ajustado_por_fkey;
ALTER TABLE ajustes_dia_programa
    ADD CONSTRAINT ajustes_dia_programa_ajustado_por_fkey
        FOREIGN KEY (ajustado_por) REFERENCES usuarios (id) ON DELETE SET NULL;

CREATE TABLE auditoria_eliminacion_cuentas (
    id           bigserial   PRIMARY KEY,
    cuenta_id    uuid        NOT NULL,
    rol          text        NOT NULL,
    accion       text        NOT NULL CHECK (accion IN ('CERRADA', 'RECUPERADA', 'ELIMINADA_POR_ADMIN',
                                                         'ELIMINADA_AL_VENCER')),
    via          text        CHECK (via IN ('APP', 'WEB')),
    actor_id     uuid,
    ocurrido_en  timestamptz NOT NULL,
    CONSTRAINT auditoria_eliminacion_via_solo_al_cerrar CHECK (via IS NULL OR accion = 'CERRADA')
);

CREATE INDEX auditoria_eliminacion_cuenta_idx ON auditoria_eliminacion_cuentas (cuenta_id, ocurrido_en);

COMMENT ON TABLE auditoria_eliminacion_cuentas IS
    'Quién cerró, recuperó o eliminó cada cuenta y cuándo (V90, D-243). Sin datos personales: solo ids opacos y el rol. Append-only.';
