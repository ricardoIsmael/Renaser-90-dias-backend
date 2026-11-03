-- Una FECHA puede decir lo mismo que un dia de la semana: varios avisos y como suena la alarma. Spec 028, decision D-296.
--
-- QUE PROBLEMA RESUELVE. Pedido del dueno (2026-10-31): poder planificar UNA semana sin tocar las demas ("Solo esta semana") y repetirla
-- en la siguiente. Lo que el editor de Planificar guarda por dia de la semana (hora, aviso con varias antelaciones, "Como suena") lo
-- guarda `horario_semanal_habito` (V39, V111, V112) y vale para TODAS las semanas. Lo que se guarda por FECHA, `horarios_habito_por_fecha`
-- (V37, V38), solo sabe de una hora, un aviso con UN numero de minutos y apagar el dia: no alcanza para guardar "el sabado de esta semana,
-- 09:00, aviso 30 y a la hora, solo vibrar". Es el riesgo tecnico que la spec 028 dejo escrito.
--
-- POR QUE COLUMNAS EN LA MISMA TABLA Y NO UNA TABLA NUEVA. `horarios_habito_por_fecha` YA es la relacion participante x habito x fecha
-- y ya la leen los registros del dia, los avisos del servidor, Mi Radar y el tiempo libre. Una tabla de "planes de semana" duplicaria esa
-- clave y abriria una segunda respuesta a "como va este dia?", la misma duplicacion que V31, V40, V111 y V112 rechazaron. Las columnas
-- son las mismas que ya tiene `horario_semanal_habito`, con el mismo significado y los mismos CHECK.
--
--   antelaciones_recordatorio  smallint[]  todas las antelaciones (minutos), de la mas temprana a la mas tardia (formato de V81/V111).
--                                          NULL en toda fila anterior: vale `minutos_recordatorio`, que sigue siendo la mas temprana.
--   alarma_sonido, alarma_vibracion, alarma_voz, alarma_repetir  boolean NULL: como suena ESE dia (spec 009); cada campo NULL = como el
--                                          dia de la semana o la general.
--
-- LOS CHECK miran solo la fila (regla 04): ninguna antelacion negativa, antelaciones solo con aviso activo, sonido y vibracion no pueden
-- quedar los dos apagados (IS FALSE, porque un campo NULL "hereda" y `NULL = false` es NULL, que un CHECK deja pasar), y la voz necesita
-- el sonido.
--
-- COMPATIBILIDAD. Esta migracion es solo aditiva: toda fila existente queda con las columnas nuevas en NULL y no cambia de significado.
-- La V131 relaja las dos reglas de fila que no dejan guardar un dia que solo trae aviso o alarma.

SET search_path TO renaser, public;

ALTER TABLE horarios_habito_por_fecha
    ADD COLUMN antelaciones_recordatorio smallint[] NULL,
    ADD COLUMN alarma_sonido             boolean    NULL,
    ADD COLUMN alarma_vibracion          boolean    NULL,
    ADD COLUMN alarma_voz                boolean    NULL,
    ADD COLUMN alarma_repetir            boolean    NULL;

ALTER TABLE horarios_habito_por_fecha
    ADD CONSTRAINT horarios_fecha_antelaciones_no_negativas
    CHECK (antelaciones_recordatorio IS NULL OR 0 <= ALL (antelaciones_recordatorio));

ALTER TABLE horarios_habito_por_fecha
    ADD CONSTRAINT horarios_fecha_antelaciones_con_aviso
    CHECK (antelaciones_recordatorio IS NULL OR recordatorio_activo IS TRUE);

ALTER TABLE horarios_habito_por_fecha
    ADD CONSTRAINT horarios_fecha_alarma_sonido_o_vibracion
    CHECK (NOT (alarma_sonido IS FALSE AND alarma_vibracion IS FALSE));

ALTER TABLE horarios_habito_por_fecha
    ADD CONSTRAINT horarios_fecha_alarma_voz_con_sonido
    CHECK (NOT (alarma_voz IS TRUE AND alarma_sonido IS FALSE));

COMMENT ON COLUMN horarios_habito_por_fecha.antelaciones_recordatorio IS
    'Minutos antes de la hora de ESE dia, de la mas temprana a la mas tardia (formato de V81). Solo con recordatorio_activo = true. NULL en una fila anterior a V130: vale minutos_recordatorio. Ver V130.';
COMMENT ON COLUMN horarios_habito_por_fecha.alarma_sonido IS
    'Como suena la alarma ESE dia. NULL = como el dia de la semana o la general (por campo). Ver V130.';
COMMENT ON COLUMN horarios_habito_por_fecha.alarma_vibracion IS
    'NULL = como el dia de la semana o la general. Sonido y vibracion no pueden ser false a la vez. Ver V130.';
COMMENT ON COLUMN horarios_habito_por_fecha.alarma_voz IS
    'NULL = como el dia de la semana o la general. Necesita alarma_sonido. Ver V130.';
COMMENT ON COLUMN horarios_habito_por_fecha.alarma_repetir IS
    'NULL = como el dia de la semana o la general. Vuelve a sonar a los 5 y a los 10 minutos si no se atiende. Ver V130.';
