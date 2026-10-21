-- El aprendiz elige COMO SUENA la alarma de cada habito y de cada dia de la semana: sonido, vibracion, voz y
-- repetir. Spec 009, decision D-283.
--
-- QUE PROBLEMA RESUELVE. Pedido del dueno (2026-10-20), sobre el editor de Training -> Planificar: debajo del
-- recordatorio, unos interruptores grandes para elegir como avisa cada habito, "totalmente personalizable por
-- dia" y pensado para personas de 40 anos o mas. Hasta hoy la alarma suena siempre igual (el tono general de
-- Yo -> Alarmas) y esa eleccion vive solo en el telefono: con dos telefonos, cada uno suena distinto, y un dia
-- no puede sonar distinto de otro. "De lunes a viernes con voz y que repita, y el sabado solo vibrar" no se
-- podia guardar. La 008 (V111) resolvio lo mismo para la HORA y el AVISO; esto lo extiende a la combinacion.
--
-- POR QUE COLUMNAS EN LAS MISMAS DOS TABLAS Y NO UNA TABLA NUEVA. `preferencias_horario` ya es la
-- preferencia general de un habito de un participante, y `horario_semanal_habito` ya es la relacion
-- participante x habito x dia de la semana (V39): su clave primaria es exactamente la clave de la combinacion
-- por dia. Una tabla aparte duplicaria esas claves y abriria una segunda respuesta a "como va este dia?", la
-- misma duplicacion que V31, V40 y V111 rechazaron. Cuatro columnas nulas las extienden sin tocar ninguna fila
-- existente.
--
-- POR QUE CUATRO COLUMNAS Y NO UN JSON O UN BITMASK. Cada una es una respuesta de si o no que el dominio
-- (`CombinacionDeAlarma`) lee y valida por separado, y los CHECK de abajo necesitan mirarlas por separado. Un
-- arreglo o un entero obligaria a decodificarlo para cada regla.
--
-- POR QUE NULL SIGNIFICA "HEREDA". Igual que `hora_disparo` (V40) y `recordatorio_activo` (V111), y por el
-- mismo motivo: la precedencia es POR CAMPO, de abajo hacia arriba:
--   lo de hoy -> combinacion general del habito -> combinacion del dia de la semana.
-- En `preferencias_horario` NULL es "lo de hoy": sonido si, vibracion si, voz no, repetir no. (El tono que
-- suena con el sonido encendido sigue siendo el de Yo -> Alarmas, que el servidor no conoce; por eso la voz
-- por defecto es "no": "voz" es la que dice el nombre del habito.) En `horario_semanal_habito` NULL es "como
-- la general". Lo que la app guarda siempre es la combinacion ENTERA, y igual a la general se guarda como
-- NULL, de modo que un dia sin nada propio no deja fila.
--
--   alarma_sonido     true / false  suena el tono (false = no suena)
--   alarma_vibracion  true / false  vibra
--   alarma_voz        true / false  una voz dice el nombre del habito (necesita el sonido)
--   alarma_repetir    true / false  vuelve a sonar a los 5 y a los 10 minutos si no se atiende
--
-- LOS CHECK miran solo la fila (regla 04). Los dos primeros se repiten en las dos tablas:
--   * `..._alarma_sonido_o_vibracion`: sonido y vibracion no pueden quedar los dos apagados. Quien quiere
--     silencio usa "Sin aviso" (el recordatorio); una alarma que no suena ni vibra es una alarma perdida. Con
--     IS FALSE y no = false porque un campo NULL ("hereda") no apaga nada, y `NULL = false` es NULL, que un
--     CHECK deja pasar: sin IS FALSE el CHECK no frenaria ni lo que dice frenar.
--   * `..._alarma_voz_con_sonido`: la voz es un sonido, asi que "voz si, sonido no" en una misma fila es
--     contradictorio. (Que la mezcla de dos capas deje voz sin sonido lo arregla el dominio al resolver,
--     `AjusteDeAlarma.aplicadoA`: un CHECK no ve la otra capa.)
--   * `horario_semanal_activo_exige_hora_o_aviso` (V111) pasa a `..._exige_hora_aviso_o_alarma`: una fila
--     ENCENDIDA tiene que decir algo propio, sea la hora, el aviso o ahora como suena; si no, no dice nada que
--     lo general no diga ya y se borra, no se vacia. Una fila apagada sigue sin necesitar nada.
--
-- UNA FILA DE SOLO COMBINACION ES UNA FILA. El habito pasa a tener "algun dia de la semana con fila", que es la
-- regla con la que la app y el servidor eligen el esquema de alarmas por dia (spec 008, R6). No se excluye: la
-- alarma de un dia distinto necesita su propio canal, y eso solo se puede programar por dia.
--
-- COMPATIBILIDAD. Toda fila existente queda con las cuatro columnas en NULL: ninguna cambia de significado
-- (sigue sonando como hoy). El APK publicado no manda ni lee las columnas nuevas, y guardar la hora o el aviso
-- no las toca: son escrituras separadas a proposito.

SET search_path TO renaser, public;

ALTER TABLE preferencias_horario
    ADD COLUMN alarma_sonido     boolean NULL,
    ADD COLUMN alarma_vibracion  boolean NULL,
    ADD COLUMN alarma_voz        boolean NULL,
    ADD COLUMN alarma_repetir    boolean NULL;

ALTER TABLE preferencias_horario
    ADD CONSTRAINT preferencias_horario_alarma_sonido_o_vibracion
    CHECK (NOT (alarma_sonido IS FALSE AND alarma_vibracion IS FALSE));

ALTER TABLE preferencias_horario
    ADD CONSTRAINT preferencias_horario_alarma_voz_con_sonido
    CHECK (NOT (alarma_voz IS TRUE AND alarma_sonido IS FALSE));

ALTER TABLE horario_semanal_habito
    ADD COLUMN alarma_sonido     boolean NULL,
    ADD COLUMN alarma_vibracion  boolean NULL,
    ADD COLUMN alarma_voz        boolean NULL,
    ADD COLUMN alarma_repetir    boolean NULL;

ALTER TABLE horario_semanal_habito
    ADD CONSTRAINT horario_semanal_alarma_sonido_o_vibracion
    CHECK (NOT (alarma_sonido IS FALSE AND alarma_vibracion IS FALSE));

ALTER TABLE horario_semanal_habito
    ADD CONSTRAINT horario_semanal_alarma_voz_con_sonido
    CHECK (NOT (alarma_voz IS TRUE AND alarma_sonido IS FALSE));

ALTER TABLE horario_semanal_habito
    DROP CONSTRAINT horario_semanal_activo_exige_hora_o_aviso;

ALTER TABLE horario_semanal_habito
    ADD CONSTRAINT horario_semanal_activo_exige_hora_aviso_o_alarma
    CHECK (NOT activo
           OR hora_disparo IS NOT NULL
           OR recordatorio_activo IS NOT NULL
           OR alarma_sonido IS NOT NULL
           OR alarma_vibracion IS NOT NULL
           OR alarma_voz IS NOT NULL
           OR alarma_repetir IS NOT NULL);

COMMENT ON COLUMN preferencias_horario.alarma_sonido IS
    'Como suena la alarma del habito (general). NULL = lo de hoy (sonido si). Ver V112.';
COMMENT ON COLUMN preferencias_horario.alarma_vibracion IS
    'NULL = lo de hoy (vibracion si). Sonido y vibracion no pueden ser false a la vez. Ver V112.';
COMMENT ON COLUMN preferencias_horario.alarma_voz IS
    'Una voz dice el nombre del habito. NULL = lo de hoy (no). Necesita alarma_sonido. Ver V112.';
COMMENT ON COLUMN preferencias_horario.alarma_repetir IS
    'Vuelve a sonar a los 5 y a los 10 minutos si no se atiende. NULL = lo de hoy (no). Ver V112.';
COMMENT ON COLUMN horario_semanal_habito.alarma_sonido IS
    'Como suena la alarma ESE dia de la semana. NULL = como la general del habito (por campo). Ver V112.';
COMMENT ON COLUMN horario_semanal_habito.alarma_vibracion IS
    'NULL = como la general. Sonido y vibracion no pueden ser false a la vez. Ver V112.';
COMMENT ON COLUMN horario_semanal_habito.alarma_voz IS
    'NULL = como la general. Necesita alarma_sonido. Ver V112.';
COMMENT ON COLUMN horario_semanal_habito.alarma_repetir IS
    'NULL = como la general. Vuelve a sonar a los 5 y a los 10 minutos si no se atiende. Ver V112.';
