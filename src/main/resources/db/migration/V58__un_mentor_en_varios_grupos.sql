-- ============================================================================
-- V58 — Un mentor puede liderar VARIOS grupos a la vez
-- ============================================================================
-- QUE INVARIANTES SE LEVANTAN, Y POR QUE SON DOS
--
-- La regla "un mentor lidera a lo sumo una celula" estaba escrita DOS VECES en la base, en
-- dos tablas distintas y con dos mecanismos distintos:
--
--   1. `celulas.mentor_id UNIQUE` (V1, baseline) — el comentario de la columna lo dice con
--      todas las letras: "un mentor lidera a lo sumo una célula". Como la declaracion es
--      inline, Postgres la nombro `celulas_mentor_id_key`.
--   2. `asignaciones_una_celula_por_mentor` (V45) — EXCLUDE sobre (usuario_id, rango) para
--      las filas con `funcion = 'MENTOR'`.
--
-- Levantar una sola no sirve de nada: la otra sigue rechazando el INSERT. Por eso esta
-- migracion toca las dos, y por eso conviene dejar escrito que eran dos — quien intente
-- revertir esto encontrando solo una va a creer que termino.
--
-- POR QUE SE LEVANTAN
--
-- Decision del dueno del producto (2026-09-17). Es el mismo movimiento que V56 hizo con el
-- aprendiz un dia antes, por la misma razon: la restriccion ya no describe el negocio, describe
-- el negocio viejo.
--
-- Es lo unico que se levanta. NO se toca:
--   · `asignaciones_un_mentor_por_celula`  — un grupo sigue teniendo UN SOLO mentor vigente;
--   · `asignaciones_celula_operacion_uk`   — repetir un comando sigue sin duplicar nada;
--   · `asignaciones_una_vez_en_cada_grupo` — la del aprendiz (V56), intacta.
--
-- POR QUE ESTA NO NECESITA REEMPLAZO Y LA DEL APRENDIZ SI
--
-- V56 no pudo borrar `asignaciones_un_grupo_por_aprendiz` a secas: tuvo que ACOTARLA al grupo
-- (`asignaciones_una_vez_en_cada_grupo`), porque sin eso quedaba legitima la misma persona dos
-- veces vigente en el MISMO grupo, y no habia ninguna otra restriccion que lo impidiera.
--
-- Con el mentor no hace falta, y la asimetria no es un descuido: `asignaciones_un_mentor_por_celula`
-- —que se conserva— ya prohibe DOS filas MENTOR solapadas en una misma celula, sin mirar de quien
-- son. Un mentor repetido en su propio grupo es un caso particular de eso, asi que la invariante
-- que V56 tuvo que reconstruir, aca ya estaba puesta desde V45. Agregar una segunda restriccion
-- que dijera lo mismo solo daria dos mensajes de error distintos para el mismo choque.
--
-- QUE NO HACE ESTA MIGRACION
--
-- No toca `celulas.mentor_id` como columna: sigue existiendo, sigue siendo la que lee "mi grupo"
-- en Comunidad para mostrar el nombre del mentor, y sigue siendo de UN solo valor — correcto,
-- porque un grupo sigue teniendo un mentor. Lo unico que se le quita es el UNIQUE, que no
-- describia al grupo sino al mentor.
--
-- No toca `participantes_programa.mentor_id` (el puntero del aprendiz a SU mentor): tambien es
-- de un solo valor y tambien sigue siendo correcto, porque nombra al mentor del grupo principal
-- del aprendiz, no "todos los grupos del mentor".
--
-- Migracion ADITIVA en datos: no borra, no reescribe, no rellena ninguna fila. Lo que antes
-- entraba, sigue entrando igual.
-- ============================================================================

SET search_path TO renaser, public;

ALTER TABLE celulas
    DROP CONSTRAINT celulas_mentor_id_key;

COMMENT ON COLUMN celulas.mentor_id IS
    'Mentor vigente del grupo. Un grupo tiene un solo mentor, pero desde V58 un mentor puede aparecer en varias filas: lidera varios grupos a la vez (decision del dueno, 2026-09-17). El UNIQUE que lo prohibia se quito aca.';

ALTER TABLE asignaciones_celula
    DROP CONSTRAINT asignaciones_una_celula_por_mentor;

COMMENT ON CONSTRAINT asignaciones_un_mentor_por_celula ON asignaciones_celula IS
    'Un solo MENTOR vigente por grupo. Desde V58 es la unica exclusion que queda sobre la funcion MENTOR: la reciproca (asignaciones_una_celula_por_mentor, un solo grupo por mentor) se levanto. Tambien cubre que el mismo mentor no este dos veces vigente en el mismo grupo.';
