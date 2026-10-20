-- El aprendiz elige el AVISO (recordatorio) de cada dia de la semana de un habito, no solo la hora.
--
-- QUE PROBLEMA RESUELVE. Pedido del dueno (2026-10-20), sobre la captura del editor de Training ->
-- Planificar: "los recordatorios tambien deben de ser por dia, no en general". V39 y V40 ya dejan que
-- cada dia de la semana tenga su HORA propia (y apagar el dia), pero el aviso seguia siendo uno solo por
-- habito: `preferencias_horario.recordatorio_activo` + `minutos_recordatorio` + `antelaciones_recordatorio`
-- (V81). "Gimnasio a las 07:00 con aviso 10 min antes de lunes a viernes, y el sabado a las 09:00 con
-- aviso 30 min antes" no se podia guardar. Spec 008, decision D-282.
--
-- POR QUE COLUMNAS Y NO UNA TABLA NUEVA. `horario_semanal_habito` YA es la relacion participante x habito
-- x dia de la semana (V39): su clave primaria es exactamente la clave del aviso por dia. Una tabla aparte
-- de avisos duplicaria esa clave y abriria una segunda respuesta a "como va este dia?", la misma
-- duplicacion que V31 y V40 rechazaron. Dos columnas nulas la extienden sin tocar ninguna fila existente.
--
-- POR QUE NULL SIGNIFICA "HEREDA". Igual que `hora_disparo` (V40) y por el mismo motivo: una fila que solo
-- trae el aviso no debe congelar la hora de ese dia, que sigue a la general si esta cambia. Y al reves,
-- una fila con hora y sin aviso sigue el aviso general. Las tres combinaciones (hora, aviso, las dos) son
-- validas; la precedencia es POR CAMPO, como la de la hora (ver `PreferenciaHorarioPersistenceAdapter`):
--   excepcion de FECHA exacta > dia de la semana (hora y aviso, cada uno si lo trae) > cambio general
--   vigente > preferencia general > catalogo.
--
--   recordatorio_activo         NULL  -> este dia usa el aviso general (nada propio).
--                               false -> este dia va SIN aviso, aunque el general avise.
--                               true  -> este dia avisa con `antelaciones_recordatorio`.
--   antelaciones_recordatorio   minutos antes de la hora, de la mas temprana a la mas tardia (el mismo
--                               formato y rango que `preferencias_horario.antelaciones_recordatorio`, V81).
--                               NULL salvo que `recordatorio_activo` sea true.
--
-- LOS CHECK miran solo la fila (regla 04):
--   * `horario_semanal_activo_exige_hora` (V40) pasa a `..._exige_hora_o_aviso`: una fila ENCENDIDA tiene
--     que decir algo propio, sea la hora o el aviso; si no, no dice nada que lo general no diga ya y se
--     borra, no se vacia. Una fila apagada sigue sin necesitar nada.
--   * un aviso activo tiene al menos una antelacion. COALESCE porque `cardinality(NULL)` es NULL, y un
--     CHECK que da NULL PASA: sin el, `recordatorio_activo = true` con el arreglo en NULL entraria.
--   * ninguna antelacion negativa, igual que V81.
-- Que las antelaciones esten ordenadas y dentro del rango lo impone el dominio (`RecordatorioDelDia`).
--
-- COMPATIBILIDAD. Toda fila existente queda con las dos columnas en NULL: ninguna cambia de significado
-- (sigue "hora propia y aviso general"). El APK publicado no manda ni lee las columnas nuevas, y guardar
-- la hora de un dia (PUT .../weekdays/{wd}) no las toca: son dos escrituras separadas a proposito.

SET search_path TO renaser, public;

ALTER TABLE horario_semanal_habito
    ADD COLUMN recordatorio_activo        boolean    NULL,
    ADD COLUMN antelaciones_recordatorio  smallint[] NULL;

ALTER TABLE horario_semanal_habito
    DROP CONSTRAINT horario_semanal_activo_exige_hora;

ALTER TABLE horario_semanal_habito
    ADD CONSTRAINT horario_semanal_activo_exige_hora_o_aviso
    CHECK (NOT activo OR hora_disparo IS NOT NULL OR recordatorio_activo IS NOT NULL);

ALTER TABLE horario_semanal_habito
    ADD CONSTRAINT horario_semanal_aviso_con_antelaciones
    CHECK (recordatorio_activo IS DISTINCT FROM true
           OR COALESCE(cardinality(antelaciones_recordatorio), 0) >= 1);

ALTER TABLE horario_semanal_habito
    ADD CONSTRAINT horario_semanal_aviso_antelaciones_no_negativas
    CHECK (antelaciones_recordatorio IS NULL OR 0 <= ALL (antelaciones_recordatorio));

COMMENT ON COLUMN horario_semanal_habito.recordatorio_activo IS
    'NULL = este dia usa el aviso general del habito; false = este dia va sin aviso; true = avisa con '
    'antelaciones_recordatorio. Se resuelve por campo, aparte de hora_disparo. Ver V111.';
COMMENT ON COLUMN horario_semanal_habito.antelaciones_recordatorio IS
    'Minutos antes de la hora del dia, de la mas temprana a la mas tardia (formato de V81). Solo con '
    'recordatorio_activo = true. Ver V111.';
