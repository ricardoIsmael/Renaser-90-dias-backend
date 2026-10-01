-- Quien respondio cada ticket de mentoria (SDD 002, RL-10; D-241).
--
-- Problema: `tickets_mentor` guarda la respuesta y la hora (`respuesta_mentor`, `respondido_en`) pero
-- no QUIEN respondio. Hasta hoy eso se deducia del puntero actual `participantes_programa.mentor_id`,
-- que nombra al mentor de HOY del aprendiz. Desde que los aprendices cambian de grupo (traslado del
-- dia 8, D-139, rotacion) ese puntero miente sobre el pasado: un ticket respondido por Luisa en la
-- recepcion pasaba a contarse como trabajo del mentor regular que recibio despues al aprendiz. La
-- gestion del Lider de Mentores mide justamente eso —cuantas consultas respondio cada mentor y en
-- cuanto tiempo—, asi que con el puntero le atribuiria el trabajo de uno a otro.
--
-- Por que una columna nueva y no reusar algo: ninguna columna de la fila dice quien escribio la
-- respuesta, y `respondido_en` solo dice cuando. No se puede reconstruir para atras (el historial de
-- `asignaciones_celula` dice quien acompanaba el grupo, no quien apreto «Responder»): las filas ya
-- respondidas quedan con NULL, y NULL significa «no se sabe», nunca «nadie». La gestion las cuenta
-- aparte como «sin registro de quien respondio», no las reparte.
--
-- Por que el nombre: mismo patron que `ajustes_dia_programa.ajustado_por` (V21) — el participio de
-- la accion + «por», apuntando a `usuarios`.
--
-- ON DELETE SET NULL y no RESTRICT: un ticket es del aprendiz y no se puede bloquear su borrado (ni el
-- del mentor) por una metrica. Perder la atribucion de un usuario eliminado es aceptable; la fila
-- vuelve a decir «no se sabe», que es cierto.
--
-- CHECK de fila: solo un ticket RESPONDIDO puede tener quien respondio. Lo contrario (RESPONDIDO sin
-- respondido_por) se permite a proposito: son las filas anteriores a esta migracion.
SET search_path TO renaser, public;

ALTER TABLE tickets_mentor
    ADD COLUMN respondido_por uuid REFERENCES usuarios (id) ON DELETE SET NULL;

ALTER TABLE tickets_mentor
    ADD CONSTRAINT tickets_mentor_respondido_por_coherente
        CHECK (respondido_por IS NULL OR estado = 'RESPONDIDO');

-- La lectura de la gestion es «lo que respondio este grupo de mentores entre tal y tal fecha».
CREATE INDEX tickets_mentor_respondido_por_idx
    ON tickets_mentor (respondido_por, respondido_en)
    WHERE respondido_por IS NOT NULL;

COMMENT ON COLUMN tickets_mentor.respondido_por IS
    'Quien respondio (V87, D-241). NULL = no se sabe: filas respondidas antes de V87. Nunca se deduce de participantes_programa.mentor_id.';
