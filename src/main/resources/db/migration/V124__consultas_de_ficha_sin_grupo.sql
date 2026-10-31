-- V124 — El líder de mentores ve a todos los aprendices, estén o no en un grupo (spec 021; D-294)
--
-- Por qué se toca la base
-- -----------------------
-- Abrir la ficha de una persona deja una fila en `consultas_de_ficha` (V109, spec 005 FR-016): quién miró, con qué rol, a
-- quién, desde qué grupo y a qué mentor se le avisa. Hasta hoy la ficha solo se abría desde un grupo con mentor, así que
-- `grupo_id` era siempre un grupo. El dueño pidió (2026-10-31) que el líder vea a todos los aprendices sin necesidad de
-- que estén en un grupo; para quien no está en ninguno no hay grupo que anotar, y inventar uno (un uuid cero, el id de
-- la propia persona) sería escribir un dato falso en un registro de auditoría. Por eso la columna acepta NULL.
--
-- Qué NO cambia
-- -------------
-- · La tabla sigue siendo append-only: `DROP NOT NULL` no dispara el trigger (que es por fila y por TRUNCATE) y no
--   reescribe ni una fila.
-- · Las filas de antes quedan como están (todas con grupo).
-- · El resumen de las 20:00 al mentor (`mentor_id IN (...)`) nunca lee una fila sin mentor, y una persona sin grupo no
--   tiene mentor: esas consultas se registran pero no avisan a nadie.

SET search_path TO renaser, public;

ALTER TABLE consultas_de_ficha ALTER COLUMN grupo_id DROP NOT NULL;

COMMENT ON COLUMN consultas_de_ficha.grupo_id IS
    'El grupo desde el que se abrió la ficha; NULL si la persona no estaba en ninguno (V124, spec 021).';
