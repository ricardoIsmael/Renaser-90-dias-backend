-- V126 — Las formaciones del personal (spec 022; D-295)
--
-- Por qué se toca la base
-- -----------------------
-- Un aprendiz pertenece a UNA formación (una columna de `participantes_programa`, V125). El personal —mentor, guía de aprendiz y líder de mentores— puede
-- trabajar en UNA O VARIAS, las asigna Administración y, con varias, elige en cuál trabaja (se recuerda la última). Administración, Alquimista y Soporte TI
-- ven las tres y no tienen filas aquí.
--
-- Por qué no alcanza una columna
-- ------------------------------
-- «Varias formaciones por persona» es una relación N:M: no cabe en una columna de `usuarios` (un `text[]` no tiene integridad referencial ni dice quién
-- asignó). Por eso esta tabla, mínima: (usuario, formación) + si es la activa + quién y cuándo la asignó. La activa va aquí (`activa`) y no en `usuarios`
-- porque así no puede apuntar a una formación que la persona no tiene: es una de sus filas. El índice único parcial garantiza una sola activa por persona.
--
-- Qué pasa con el personal que ya existe (FR-005)
-- ----------------------------------------------
-- Todo el personal de hoy queda asignado a la Escuela y con ella activa: cambia nada para ellos.

SET search_path TO renaser, public;

CREATE TABLE formaciones_personal (
    usuario_id   uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    formacion    text        NOT NULL REFERENCES formaciones (codigo),
    activa       boolean     NOT NULL DEFAULT false,
    asignada_en  timestamptz NOT NULL DEFAULT now(),
    asignada_por uuid        REFERENCES usuarios (id) ON DELETE SET NULL,
    PRIMARY KEY (usuario_id, formacion)
);

CREATE UNIQUE INDEX formaciones_personal_una_activa_uk ON formaciones_personal (usuario_id) WHERE activa;
CREATE INDEX formaciones_personal_formacion_idx ON formaciones_personal (formacion);

COMMENT ON TABLE formaciones_personal IS
    'Formaciones en las que trabaja cada integrante del personal (V126, spec 022). Una fila activa como máximo por persona; la activa es una preferencia de uso, no un permiso.';

INSERT INTO formaciones_personal (usuario_id, formacion, activa)
SELECT u.id, 'ESCUELA', true
FROM usuarios u
WHERE u.rol::text IN ('MENTOR', 'GUIA_APRENDIZ', 'LIDER_MENTORES');
