-- V139 — El ranking de cada formación (spec 024; D-301)
--
-- Por qué se toca la base
-- -----------------------
-- `ranking_aprendices` guarda el corte diario del ranking: una fila por (fecha, tipo, aprendiz) con su posición y su puntaje, calculados entre TODOS los aprendices activos.
-- El dueño (2026-10-31) pidió un ranking por formación: cada una compara solo a los suyos, con la posición contada entre ellos.
--
-- Por qué la formación va EN la fila del corte y no se lee de la persona
-- ---------------------------------------------------------------------
-- La persona SÍ tiene su formación (`participantes_programa.formacion`), y para el ranking de hoy bastaría un JOIN. Pero este corte es HISTORIA: «el 12 de octubre Ana era la
-- 3.ª de la Escuela». Si Ana pasa al Diplomado, un JOIN a su fila de hoy movería también sus cortes viejos al Diplomado, y las posiciones de la Escuela de esos días
-- quedarían con un hueco. La regla 04 manda que la historia no se reescriba; por eso es la única fila donde la formación se GUARDA además de derivarse (la desnormalización
-- justificada que pide la spec, «un puntaje histórico»).
--
-- Qué pasa con lo que ya existe (FR-002, SC-009)
-- ----------------------------------------------
-- Todos los cortes existentes son de la Escuela (DEFAULT): eran los de todos los aprendices, y todos los aprendices de hoy están en ella. Sin cambios visibles.
--
-- Lo que NO se toca
-- -----------------
-- La clave primaria (fecha, tipo, participante) sigue: una persona está en una sola formación a la vez, así que no aparece dos veces el mismo día y tipo.
-- `ranking_celulas` tampoco: cada fila ya es de UN grupo, y el grupo tiene su formación (V125, `celulas.formacion`).

SET search_path TO renaser, public;

ALTER TABLE ranking_aprendices
    ADD COLUMN formacion text NOT NULL DEFAULT 'ESCUELA' REFERENCES formaciones (codigo);

COMMENT ON COLUMN ranking_aprendices.formacion IS
    'La formación en que estaba el aprendiz el día del corte (V139, spec 024). La posición se cuenta entre los aprendices de esta misma formación.';

CREATE INDEX ranking_apr_formacion_idx ON ranking_aprendices (formacion, tipo, fecha, posicion);
