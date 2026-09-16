-- ============================================================================
-- V56 — Un aprendiz puede pertenecer a VARIOS grupos a la vez
-- ============================================================================
-- QUE INVARIANTE SE LEVANTA
--
-- `asignaciones_un_grupo_por_aprendiz` (V45) decia, en la base:
--
--     EXCLUDE USING gist (usuario_id WITH =, tstzrange(inicio, fin) WITH &&)
--     WHERE (funcion = 'APRENDIZ')
--
-- es decir: "un aprendiz no puede tener DOS pertenencias solapadas, ni siquiera en grupos
-- distintos". Cualquier INSERT de una segunda membresia vigente moria con
--
--   ERROR: conflicting key value violates exclusion constraint
--          "asignaciones_un_grupo_por_aprendiz"
--
-- POR QUE SE LEVANTA
--
-- Decision del dueno del producto (2026-09-16, confirmada dos veces): *"un estudiante o
-- aprendiz puede estar en varios grupos multiples a la vez"*. Deja de ser cierto que la
-- pertenencia a un grupo sea exclusiva, asi que la restriccion ya no describe el negocio —
-- describe el negocio VIEJO. Una restriccion que la operacion real contradice no protege
-- nada: solo obliga a esquivarla.
--
-- Es lo unico que se levanta. NO se toca:
--   · `asignaciones_un_mentor_por_celula`  — un grupo sigue teniendo un solo mentor;
--   · `asignaciones_una_celula_por_mentor` — un mentor sigue liderando un solo grupo;
--   · `asignaciones_celula_operacion_uk`   — repetir un comando sigue sin duplicar nada.
--
-- POR QUE NO ALCANZA CON BORRARLA, Y QUE LA REEMPLAZA
--
-- Borrarla a secas dejaria legitima una fila que NO lo es: la misma persona, dos veces
-- vigente, en el MISMO grupo. Eso no es "estar en varios grupos", es una membresia
-- duplicada — y se puede llegar a ella sin ningun bug, porque hay tres caminos que abren
-- pertenencias con claves de operacion distintas (`recepcion-alta|…` del ingreso
-- automatico, `alta-manual|…` del traslado del panel, `suma-a-grupo|…` del alta adicional
-- nueva). Como `asignaciones_celula_operacion_uk` es unico por
-- (clave_operacion, celula_id, usuario_id, funcion), dos claves DISTINTAS pasan las dos.
--
-- Por eso la invariante no se elimina: se ACOTA al grupo. La nueva
-- `asignaciones_una_vez_en_cada_grupo` agrega `celula_id WITH =` al mismo EXCLUDE, y dice
-- lo que sigue siendo verdad: "en UN grupo, una persona esta una sola vez a la vez".
--
-- El relevo exacto sigue sin solapar: tstzrange(inicio, fin) es '[)' por defecto, asi que
-- el traslado que cierra la pertenencia en el instante `T` y abre la nueva en el mismo `T`
-- —que es lo que hace `ComposicionDeCelulaService.asignar` cuando reasigna a alguien
-- DENTRO de su propio grupo— pasa igual que antes. Esta migracion no cambia una sola fila
-- ni rompe un solo camino existente.
--
-- POR QUE EL NOMBRE
--
-- `asignaciones_una_vez_en_cada_grupo` dice la regla que queda, no la que se fue. El nombre
-- viejo (`…_un_grupo_por_aprendiz`) se descarta entero en vez de reutilizarse: un nombre que
-- afirma lo contrario de lo que el constraint hace es peor que no tener constraint, porque
-- el proximo que lea el esquema va a creerle al nombre.
--
-- QUE NO HACE ESTA MIGRACION
--
-- No toca `participantes_programa.celula_id`. Esa columna es UNA sola y siete lecturas
-- dependen de ella (mi grupo, mis companeros, el chat del aprendiz, el conteo de miembros,
-- el ranking, la ficha, el calendario): sigue nombrando al grupo PRINCIPAL —el primero— y
-- el alta adicional no la mueve. Ver D-139 en `docs/MODULOS_A_AVANZAR.md` §8.
--
-- Migracion ADITIVA en datos: no borra, no reescribe, no rellena ninguna fila.
-- ============================================================================

-- Igual que V45: los operadores de btree_gist viven en `public` y las tablas en `renaser`.
-- Sin esto, `EXCLUDE USING gist (uuid WITH =)` no resuelve la clase de operadores.
SET search_path TO renaser, public;

ALTER TABLE asignaciones_celula
    DROP CONSTRAINT asignaciones_un_grupo_por_aprendiz,
    ADD CONSTRAINT asignaciones_una_vez_en_cada_grupo
        EXCLUDE USING gist (celula_id WITH =, usuario_id WITH =, tstzrange(inicio, fin) WITH &&)
        WHERE (funcion = 'APRENDIZ');

COMMENT ON CONSTRAINT asignaciones_una_vez_en_cada_grupo ON asignaciones_celula IS
    'Una persona, una sola pertenencia vigente POR GRUPO. Reemplaza a asignaciones_un_grupo_por_aprendiz (V45), que ademas prohibia estar en dos grupos distintos a la vez — cosa que desde 2026-09-16 es un caso valido (D-139).';
