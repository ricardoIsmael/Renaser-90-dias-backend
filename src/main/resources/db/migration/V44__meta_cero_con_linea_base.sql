-- =============================================================================================
-- V44 · Una meta puede ser cero, si se sabe desde donde se arranco
-- =============================================================================================
--
-- QUE PROBLEMA RESUELVE
--
-- `roca_maestra_meta_positiva` (V35) exige `meta > 0`, asi que "saldar la deuda" -llegar a 0- se
-- rechazaba con un 400. El Mapa del Dia 7 ofrece `deuda` como tipo de resultado, y el final natural
-- de esa meta es exactamente cero: nadie se propone quedar debiendo 1.
--
-- POR QUE NO SE PODIA ANTES, Y POR QUE SI AHORA
--
-- Con la formula vieja del porcentaje (`avance / meta`), una meta de cero era una division por
-- cero. Por eso el CHECK tenia razon de ser cuando se escribio.
--
-- Desde V43 el porcentaje se mide contra el punto de partida:
--
--     |avance - linea_base| / |meta - linea_base|
--
-- Con `meta = 0` y `linea_base = 8000`, el divisor es 8000: perfectamente medible. Saldar 2000 de
-- una deuda de 8000 da 25 %. Lo que sigue sin poder medirse es una meta de cero SIN punto de
-- partida, y eso es lo que el CHECK nuevo conserva.
--
-- POR QUE NO SE TOCA `rocas_mensuales`
--
-- `roca_mensual_meta_positiva` (V36) queda como esta: esa tabla no tiene `linea_base` y por lo
-- tanto sigue usando la formula vieja, donde cero seria una division por cero. Ampliar el nivel
-- mensual es otra decision y no se toma de arrastre.
-- =============================================================================================

ALTER TABLE renaser.rocas_maestras
    DROP CONSTRAINT roca_maestra_meta_positiva;

ALTER TABLE renaser.rocas_maestras
    ADD CONSTRAINT roca_maestra_meta_no_negativa
        CHECK (meta IS NULL OR meta >= 0),
    -- Cero solo con punto de partida: es la condicion que hace que el porcentaje exista.
    ADD CONSTRAINT roca_maestra_meta_cero_exige_linea_base
        CHECK (meta IS NULL OR meta > 0 OR linea_base IS NOT NULL);

COMMENT ON COLUMN renaser.rocas_maestras.meta IS
    'Cifra a alcanzar en los 90 dias. NULL = objetivo puramente cualitativo. Puede ser 0 -saldar una '
    'deuda- pero solo con linea_base, porque el avance se mide contra el punto de partida (V43/V44).';
