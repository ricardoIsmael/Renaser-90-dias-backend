-- =====================================================================================
-- Nivel MENSUAL del plan: el escalon que faltaba entre la Roca Maestra y la semanal.
--
-- QUE PROBLEMA RESUELVE
--
-- El plan del programa tiene cuatro niveles: tres objetivos maestros (uno por eje, a 90 dias),
-- tres mensuales, tres semanales y tres actividades diarias. El backend ya cumplia tres de los
-- cuatro: `rocas_maestras` (una por eje), `rocas_semanales` (el caso de uso exige exactamente 3,
-- una por eje) y `rocas_diarias` (3 por dia, ver BloqueoPlanificacion.ROCAS_REQUERIDAS_MANANA).
-- El mensual no existia en ninguna parte, asi que no habia forma de bajar de "a donde quiero
-- llegar en 90 dias" a "que tiene que estar logrado al cierre de este mes" sin saltar directo a
-- la semana.
--
-- POR QUE ESTA FORMA, ESPEJANDO A rocas_semanales
--
-- Cuelga de la Roca Maestra y no del participante, igual que la semanal: un objetivo mensual solo
-- significa algo como tramo de un objetivo de 90 dias. Con eso, "tres mensuales" sale solo de la
-- misma regla que ya da tres semanales: hay tres maestras (una por eje) y una mensual por maestra
-- y por mes. No hace falta ningun contador aparte que alguien pueda desincronizar.
--
-- POR QUE `numero_mes` VA DE 1 A 3
--
-- El programa dura 90 dias: mes 1 cierra al dia 30, mes 2 al 60 y mes 3 al 90, que es exactamente
-- como lo pidio el cliente. El CHECK lo impone en la base y no solo en el codigo, por el mismo
-- motivo que `numero_semana` en la tabla semanal.
--
-- POR QUE NO SE REUSA rocas_semanales CON UN CAMPO "TIPO"
--
-- Seria mezclar dos cosas con reglas distintas en la misma tabla: la semanal tiene ventana de
-- edicion, autoevaluacion de inicio y cierre, y acciones criticas 1..3; la mensual es un tramo
-- del objetivo grande y no tiene ninguna de esas. Un `tipo` obligaria a que la mitad de las
-- columnas fueran NULL segun la fila, que es justo lo que el baseline evito al sacar
-- critical_action_1/2/3 a su propia tabla (P-10).
-- =====================================================================================

CREATE TABLE renaser.rocas_mensuales (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    roca_maestra_id uuid        NOT NULL REFERENCES renaser.rocas_maestras (id) ON DELETE CASCADE,
    numero_mes      smallint    NOT NULL CHECK (numero_mes BETWEEN 1 AND 3),
    titulo          text        NOT NULL,
    -- Meta medible del mes, con la misma regla que la Roca Maestra (V35): las tres juntas o
    -- ninguna. Un tramo puede ser cualitativo, pero media meta no significa nada.
    meta            numeric(14, 2),
    avance          numeric(14, 2),
    unidad          varchar(20),
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (roca_maestra_id, numero_mes),
    CONSTRAINT roca_mensual_titulo_no_vacio
        CHECK (btrim(titulo) <> ''),
    CONSTRAINT roca_mensual_meta_positiva
        CHECK (meta IS NULL OR meta > 0),
    CONSTRAINT roca_mensual_avance_no_negativo
        CHECK (avance IS NULL OR avance >= 0),
    CONSTRAINT roca_mensual_unidad_no_vacia
        CHECK (unidad IS NULL OR btrim(unidad) <> ''),
    CONSTRAINT roca_mensual_meta_completa
        CHECK ((meta IS NULL AND avance IS NULL AND unidad IS NULL)
            OR (meta IS NOT NULL AND avance IS NOT NULL AND unidad IS NOT NULL))
);

-- El acceso real es "dame los objetivos mensuales de esta persona", que llega por las maestras.
-- El UNIQUE de arriba ya cubre (roca_maestra_id, numero_mes); este indice sirve al listado por
-- maestra sin filtrar mes.
CREATE INDEX rocas_mensuales_por_maestra_idx ON renaser.rocas_mensuales (roca_maestra_id, numero_mes);

COMMENT ON TABLE renaser.rocas_mensuales IS
    'Tramo mensual de una Roca Maestra. Mes 1 cierra al dia 30, mes 2 al 60, mes 3 al 90.';
