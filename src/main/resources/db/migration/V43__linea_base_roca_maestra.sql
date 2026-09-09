-- =============================================================================================
-- V43 · El punto de partida de una meta medible
-- =============================================================================================
--
-- QUE PROBLEMA RESUELVE (E-166)
--
-- `MetaCuantitativa.porcentaje()` calculaba `avance * 100 / meta`, que asume que MAS ES MEJOR.
-- Con el objetivo "Al Dia 90 pesare 75 kg, partiendo de 82 kg" daba 82 * 100 / 75 = 109, acotado
-- a 100, y el Plan mostraba "100 % CUMPLIDO" el primer dia al lado de "Llevas 82 kg, Meta 75 kg".
-- Dos datos que se contradicen a la vista.
--
-- No es un caso de borde: el Mapa del Dia 7 ofrece `peso` y `deuda` como tipos de resultado, asi
-- que cualquiera que quiera BAJAR algo arranca por encima de su meta y ve 100 % desde el dia 1.
--
-- POR QUE NO SE REUSA UNA COLUMNA EXISTENTE
--
-- `avance` es el valor ACTUAL y se pisa en cuanto la persona lo actualiza: hoy 82 kg, en dos
-- semanas 80. Cuando eso pase, el 82 original ya no esta en ningun lado y no hay forma de saber
-- cuanto camino se recorrio. Medir una meta que baja necesita las tres cifras a la vez —de donde
-- salio, donde esta, a donde va— y solo hay lugar para dos.
--
-- `meta` tampoco sirve: es el destino, no el origen.
--
-- POR QUE ESTE NOMBRE
--
-- `linea_base` es el termino que ya usa el Mapa en el frontend (`ObjetivoSalud.lineaBase`,
-- `ObjetivoNegocio.lineaBase`) y el que aparece en pantalla como "Situacion actual". Se conserva
-- para que el mismo concepto se llame igual de punta a punta.
--
-- POR QUE ES NULLABLE, Y QUE PASA CON LAS FILAS QUE YA EXISTEN
--
-- Las rocas creadas antes de esta migracion no tienen punto de partida y no hay forma honesta de
-- inventarselo: poner `avance` como linea base diria que nadie avanzo nunca, y poner 0 diria que
-- todos arrancaron de cero. Con `linea_base` en NULL el dominio conserva la formula vieja, que es
-- exactamente lo que esas filas venian mostrando. Se corrigen solas la proxima vez que el aprendiz
-- edite su objetivo, porque el Mapa ya manda el dato.
--
-- La consecuencia buscada: NADA se rompe, y todo lo nuevo se mide bien.
-- =============================================================================================

ALTER TABLE renaser.rocas_maestras
    ADD COLUMN linea_base numeric(14, 2);

-- La linea base solo tiene sentido si hay meta: sin `meta`/`avance`/`unidad` el objetivo es
-- cualitativo y no hay nada que medir. Se apoya en el CHECK `roca_maestra_meta_completa` (V35),
-- que ya obliga a que esas tres vayan juntas o ninguna.
ALTER TABLE renaser.rocas_maestras
    ADD CONSTRAINT roca_maestra_linea_base_no_negativa
        CHECK (linea_base IS NULL OR linea_base >= 0),
    ADD CONSTRAINT roca_maestra_linea_base_exige_meta
        CHECK (linea_base IS NULL OR meta IS NOT NULL),
    -- Sin distancia entre partida y meta no hay avance que medir, y el porcentaje seria una
    -- division por cero. El dominio lo rechaza igual; esto lo impide tambien desde la base.
    ADD CONSTRAINT roca_maestra_linea_base_distinta_de_meta
        CHECK (linea_base IS NULL OR linea_base <> meta);

COMMENT ON COLUMN renaser.rocas_maestras.linea_base IS
    'Desde donde arranco el aprendiz. Con este dato el porcentaje mide el camino recorrido y funciona '
    'en las dos direcciones (bajar de peso, subir facturacion). NULL = fila anterior a V43: se usa la '
    'formula vieja avance/meta. Ver E-166.';
