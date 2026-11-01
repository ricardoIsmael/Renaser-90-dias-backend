-- Una fecha puede traer SOLO su aviso o SOLO su alarma, sin hora, y heredar el aviso. Spec 028, decision D-296 (continua V130).
--
-- QUE PROBLEMA RESUELVE. "Solo esta semana" guarda por fecha SOLO LO TOCADO, como el editor ya hace por dia de la semana (V111, V112): quien
-- cambia el aviso del sabado de esta semana y no mueve la hora no debe congelarla. Para eso una fila por fecha necesita poder decir
-- "ese dia, este aviso" sin hora y "ese dia, hereda el aviso" con hora. Dos reglas de V37 y V38 lo impiden:
--
--   * `recordatorio_activo` es NOT NULL (V37): toda fila decia siempre un aviso propio, aunque solo se hubiera querido mover la hora. Pasa a
--     admitir NULL = "hereda el aviso". Las filas que ya existen nunca son NULL y se leen igual que antes: aviso propio.
--   * `horarios_fecha_activo_exige_hora` (V38): una fila encendida exigia hora. Pasa a `..._exige_hora_aviso_o_alarma`, la regla que V111 y V112
--     ya aplicaron a `horario_semanal_habito`: una fila encendida tiene que decir ALGO propio (la hora, el aviso o como suena); si no, no dice
--     nada que lo general no diga ya y se BORRA, no se vacia. Una fila apagada ("ese dia no va") sigue sin necesitar nada.
--
-- SEMANTICA DE UNA FILA (la impone el dominio, ver `FechaPropia`):
--   recordatorio_activo NULL  -> hereda el aviso del dia de la semana o el general.
--   recordatorio_activo false -> ese dia sin aviso.
--   recordatorio_activo true  -> avisa con `antelaciones_recordatorio` (o, en una fila anterior a V130, con `minutos_recordatorio`).
--
-- COMPATIBILIDAD. Solo relaja: ninguna fila existente deja de cumplir. Quien escribia filas con la forma de antes (el PATCH con fecha y el
-- interruptor de un dia) sigue escribiendolas igual.

SET search_path TO renaser, public;

ALTER TABLE horarios_habito_por_fecha
    ALTER COLUMN recordatorio_activo DROP NOT NULL;

ALTER TABLE horarios_habito_por_fecha
    DROP CONSTRAINT horarios_fecha_activo_exige_hora;

ALTER TABLE horarios_habito_por_fecha
    ADD CONSTRAINT horarios_fecha_activo_exige_hora_aviso_o_alarma
    CHECK (NOT activo
           OR hora_disparo IS NOT NULL
           OR recordatorio_activo IS NOT NULL
           OR alarma_sonido IS NOT NULL
           OR alarma_vibracion IS NOT NULL
           OR alarma_voz IS NOT NULL
           OR alarma_repetir IS NOT NULL);

COMMENT ON COLUMN horarios_habito_por_fecha.recordatorio_activo IS
    'NULL = ese dia hereda el aviso (V131); false = ese dia sin aviso; true = avisa con antelaciones_recordatorio. Las filas anteriores a V131 nunca son NULL.';
