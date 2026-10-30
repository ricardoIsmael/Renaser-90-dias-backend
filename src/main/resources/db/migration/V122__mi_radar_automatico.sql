-- Mi Radar automatico: las horas ya no las elige la persona, las calcula el servidor con su Despertar y su Dormir.
-- Spec 019, decision D-292. Rehace lo que dejo la V119 (spec 012), que NO se edita: ya esta aplicada en bases locales.
--
-- QUE CAMBIA. La V119 guardaba las HORAS elegidas a mano (`horas_mi_radar smallint[]`, hasta 12) y, por dia de la semana,
-- horas propias (`dias_mi_radar`). El dueno aprobo (2026-10-30) otro diseno: Mi Radar pregunta cada hora en punto entre el
-- Despertar y el Dormir que la persona ya programo en sus habitos (modo AUTOMATICO) o un numero de veces por dia repartidas
-- parejo en ese horario (modo PERSONALIZADO). Las horas salen del horario de CADA dia (spec 008) y cambian cuando la
-- persona cambia su Despertar o su Dormir; guardarlas en la base las dejaria viejas. Lo que si hay que recordar de la
-- persona es:
--   - SI esta encendido (`mi_radar_encendido`): el permiso de la persona, no depende del dia;
--   - por dia de la semana, SOLO lo que no es de fabrica (`dias_mi_radar`): el modo, cuantas veces y si lo apago ese dia.
--
--   participantes_programa.mi_radar_encendido  boolean NOT NULL DEFAULT false  Encendido o apagado. Apagar NO borra los
--                                                       dias: al volver a encender los encuentra como estaban.
--   participantes_programa.dias_mi_radar       jsonb NULL   [{"dia":6,"modo":"PERSONALIZADO","veces":3,"sinRadar":false}]
--                                                       con lunes = 1 ... domingo = 7. Un dia que no esta es de fabrica:
--                                                       AUTOMATICO y con Radar. `veces` solo existe en PERSONALIZADO.
--                                                       NULL = ningun dia distinto. La base solo asegura que sea un arreglo;
--                                                       el dia 1..7 sin repetir, el modo y las veces los impone el dominio
--                                                       (`MiRadar`, `DiaDeMiRadar`), como en la V119.
--   participantes_programa.horas_mi_radar      SE ELIMINA, con sus dos CHECK.
--
-- SIN MIGRAR DATOS A MANO. Lo elegido a mano en la 012 nunca llego a produccion (decision del dueno): no hay personas cuyas
-- horas haya que convertir. Por si en una base local quedo algo, la conversion es la mas conservadora: encendido si tenia
-- horas, y todos los dias de fabrica (Automatico), que es lo que la nueva pantalla muestra al abrirla.
--
-- `registros_radar.hora_prevista` (V119) NO cambia: sigue siendo la hora local a la que respondia, que ahora calcula el
-- servidor con las horas del dia.

SET search_path TO renaser, public;

ALTER TABLE participantes_programa
    ADD COLUMN mi_radar_encendido boolean NOT NULL DEFAULT false;

UPDATE participantes_programa
SET mi_radar_encendido = (horas_mi_radar IS NOT NULL);

-- El CHECK de los dias miraba `horas_mi_radar`; se reemplaza por uno que mira solo la propia columna.
ALTER TABLE participantes_programa
    DROP CONSTRAINT participantes_programa_dias_mi_radar_validos;

UPDATE participantes_programa
SET dias_mi_radar = NULL;

ALTER TABLE participantes_programa
    ADD CONSTRAINT participantes_programa_dias_mi_radar_validos
        CHECK (dias_mi_radar IS NULL OR jsonb_typeof(dias_mi_radar) = 'array');

ALTER TABLE participantes_programa
    DROP CONSTRAINT participantes_programa_horas_mi_radar_validas;

ALTER TABLE participantes_programa
    DROP COLUMN horas_mi_radar;
