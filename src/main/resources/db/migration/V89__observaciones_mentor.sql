-- Observaciones del Lider de Mentores sobre un mentor (SDD 002, RL-15/RL-16; D-241).
--
-- Problema: el Lider sigue a cada mentor y le dice cuando algo va bien o mal (decision DL-03 del
-- dueño). Hoy eso no queda en ningun lado: lo que le dijo el mes pasado se pierde, y el reporte del
-- periodo no puede mostrar a quien se acompaño.
--
-- Por que una tabla nueva y no el chat: los mensajes del chat son conversacion, no seguimiento — no
-- tienen tipo (reconocimiento, sugerencia, alerta), se mezclan con todo lo demas, el Lider puede
-- registrar una observacion SIN mandarla, y el mentor puede borrar o no leer su chat. Tampoco sirve
-- `perfiles_mentor`: guarda el estado de hoy, no una historia.
--
-- APPEND-ONLY, igual que `ajustes_dia_programa` (V21): el codigo no tiene UPDATE ni DELETE. Una
-- observacion equivocada se corrige con OTRA, y las dos quedan a la vista.
--
-- Nombres:
--   · `tipo` con CHECK y no un enum de Postgres: tres valores cerrados por la propuesta PL-04 del SDD
--     002; si el dueño cambia la lista, un CHECK se reemplaza sin ALTER TYPE.
--   · `enviada_por_chat` + `mensaje_id`: el Lider decide si ademas se la manda por el chat directo
--     (PL-05). La app manda el mensaje por el endpoint del chat que ya existe y despues registra la
--     observacion con el id del mensaje. Sin FK a `mensajes`: si el chat borra el mensaje, la
--     observacion no se pierde.
--   · `clave_operacion`: el mismo envio repetido (doble toque, reintento sin red) no crea dos filas.
--     UNICA por autor.
--   · Sin columna de periodo: el mes sale de `creado_en` en la zona del reporte. Guardarlo aparte
--     duplicaria un dato derivable (regla 04: lo derivable se deriva en un solo lugar).
--
-- CHECK de texto 1..1000: se evalua con la fila. Que el destinatario sea MENTOR lo impone el dominio
-- (depende de otra tabla).
SET search_path TO renaser, public;

CREATE TABLE observaciones_mentor (
    id                uuid        PRIMARY KEY,
    mentor_id         uuid        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    autor_id          uuid        NOT NULL REFERENCES usuarios (id) ON DELETE RESTRICT,
    tipo              text        NOT NULL CHECK (tipo IN ('RECONOCIMIENTO', 'SUGERENCIA', 'ALERTA')),
    texto             text        NOT NULL CHECK (length(btrim(texto)) BETWEEN 1 AND 1000),
    enviada_por_chat  boolean     NOT NULL DEFAULT false,
    mensaje_id        uuid,
    clave_operacion   text        NOT NULL CHECK (length(clave_operacion) BETWEEN 1 AND 100),
    creado_en         timestamptz NOT NULL,
    CONSTRAINT observaciones_mentor_mensaje_coherente CHECK (mensaje_id IS NULL OR enviada_por_chat),
    CONSTRAINT observaciones_mentor_operacion_uk UNIQUE (autor_id, clave_operacion)
);

-- La ficha del mentor lee «lo ultimo que se le dijo»; el reporte, «lo de este mes».
CREATE INDEX observaciones_mentor_mentor_idx ON observaciones_mentor (mentor_id, creado_en DESC);
CREATE INDEX observaciones_mentor_creado_idx ON observaciones_mentor (creado_en);

COMMENT ON TABLE observaciones_mentor IS
    'Seguimiento del Lider de Mentores sobre cada mentor (V89, D-241). Append-only: se corrige con otra fila, nunca con UPDATE/DELETE.';
