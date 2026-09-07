-- =====================================================================================
-- Roca Maestra: meta cuantitativa (meta / avance / unidad) y marca de actualizacion.
--
-- QUE PROBLEMA RESUELVE
--
-- La pantalla de Objetivos del Plan muestra el objetivo de 90 dias con una barra de avance
-- ("Llevas 19.500 USD - Meta 30.000 USD - 65% CUMPLIDO"), pero esos numeros no existian en
-- ninguna parte: `rocas_maestras` solo guardaba `eje` + `objetivo` (texto). La pantalla los
-- traia escritos a mano en el codigo del frontend, iguales para todos los aprendices y
-- perdidos al recargar. Esta migracion les da donde vivir.
--
-- POR QUE NO SE REUSA UNA COLUMNA EXISTENTE
--
-- `objetivo` es texto libre y es la frase del aprendiz ("Facturar 30.000 USD en contratos
-- high-ticket"). Meter el numero ahi obligaria a parsear la frase para dibujar la barra, que
-- es exactamente el tipo de dato derivado-de-un-string que se rompe solo. Y no hay ninguna
-- otra tabla con la meta: `rocas_semanales` tiene autoevaluaciones 1-10, que son otra cosa
-- (percepcion de la semana, no avance hacia una cifra).
--
-- POR QUE ESTOS NOMBRES
--
-- `meta` y `avance` en vez de `objetivo_valor`/`valor_actual`: `objetivo` ya esta tomado por
-- la frase y repetir la palabra con sufijos confunde las dos cosas al leer la fila. `unidad`
-- se guarda como texto libre corto (USD, kg, horas, clientes) y no como enum: el aprendiz
-- elige su propia unidad y no hay una lista cerrada que el negocio haya confirmado.
--
-- POR QUE LAS TRES SON OPCIONALES, PERO JUNTAS
--
-- Un objetivo puede ser puramente cualitativo ("recuperar la relacion con mi hijo") y no
-- tiene numero que ponerle. Pero una meta a medias no significa nada: un `avance` sin `meta`
-- no dibuja barra, y un numero sin `unidad` no se puede ni escribir en pantalla. Por eso el
-- CHECK exige las tres o ninguna, en vez de dejar que la aplicacion recuerde la regla.
--
-- `actualizado_en` no existia porque hasta hoy una Roca Maestra era un hecho inmutable (lo
-- decia el javadoc del agregado). Desde que se puede editar el objetivo y registrar avance,
-- saber cuando se toco por ultima vez deja de ser opcional.
-- =====================================================================================

ALTER TABLE renaser.rocas_maestras
    ADD COLUMN meta           numeric(14, 2),
    ADD COLUMN avance         numeric(14, 2),
    ADD COLUMN unidad         varchar(20),
    ADD COLUMN actualizado_en timestamptz NOT NULL DEFAULT now();

ALTER TABLE renaser.rocas_maestras
    ADD CONSTRAINT roca_maestra_meta_positiva
        CHECK (meta IS NULL OR meta > 0),
    ADD CONSTRAINT roca_maestra_avance_no_negativo
        CHECK (avance IS NULL OR avance >= 0),
    ADD CONSTRAINT roca_maestra_unidad_no_vacia
        CHECK (unidad IS NULL OR btrim(unidad) <> ''),
    ADD CONSTRAINT roca_maestra_meta_completa
        CHECK ((meta IS NULL AND avance IS NULL AND unidad IS NULL)
            OR (meta IS NOT NULL AND avance IS NOT NULL AND unidad IS NOT NULL));

COMMENT ON COLUMN renaser.rocas_maestras.meta IS
    'Cifra a alcanzar en los 90 dias. NULL = objetivo puramente cualitativo.';
COMMENT ON COLUMN renaser.rocas_maestras.avance IS
    'Cuanto lleva acumulado hacia la meta. Puede superarla; el porcentaje se acota en el dominio.';
COMMENT ON COLUMN renaser.rocas_maestras.unidad IS
    'Unidad de meta/avance, tal como la escribio el aprendiz: USD, kg, horas, clientes.';
