-- El aprendiz puede tener el MISMO habito a distinta hora segun el dia de la semana.
--
-- QUE PROBLEMA RESUELVE. Pedido del dueno (2026-09-07): "debe de poder elegir que dias sea a las 5
-- o a las 4". Hoy no se puede de ninguna forma:
--
--   * `preferencias_horario` tiene clave (participante, habito) -- literalmente UNA hora para toda
--     la semana. No hay donde poner "los lunes a las 5 y los martes a las 4".
--   * `horarios_habito_por_fecha` (V37) si distingue, pero por FECHA EXACTA: sirve para "el lunes
--     8 a las 5" y no se repite. La semana siguiente hay que volver a cargarlo.
--   * `horarios_habito` es del catalogo COMPARTIDO, lo escribe el panel admin y afecta a todos.
--
-- POR QUE UNA TABLA NUEVA, cuando la regla de este repo es no crearlas. Porque es una relacion
-- NUEVA de verdad: participante x habito x DIA DE LA SEMANA. Ninguna clave existente la expresa, y
-- meterla a la fuerza en cualquiera de las tres tablas de arriba significaria cambiarle la clave
-- primaria a una tabla que ya tiene claves foraneas apuntandole -- `cambios_horario_pendientes`
-- referencia a `preferencias_horario` con una FK compuesta (E-54). Eso si romperia lo que ya anda.
--
-- Las columnas que se pudieron resolver como columna se resolvieron como columna: `activo` por
-- fecha fue a `horarios_habito_por_fecha` (V38) y `pausado_desde` ni hizo falta. Esta es la que no
-- entra en ninguna fila existente.
--
-- POR QUE NO LLEVA `activo`. Apagar un dia YA se puede, por fecha, desde V38. Dos mecanismos para
-- lo mismo -- uno por fecha y otro por dia de semana -- serian dos respuestas posibles a "va hoy?",
-- que es exactamente la duplicacion que V31 rechazo. Esta tabla responde UNA pregunta: a que hora.
--
-- `dia_semana smallint 1..7`: ISO-8601, el mismo valor que `java.time.DayOfWeek.getValue()`
-- (1 = lunes). No se usa el `dow` de Postgres (0 = domingo) para que la traduccion Java<->SQL sea
-- la identidad y no haya un +1 escondido en un mapper.
--
-- HORAS NULABLES, y no es un descuido: `hora_limite` en NULL significa "ese dia no vence dentro
-- del dia", igual que en el resto del esquema. `hora_disparo` en NULL no tendria sentido -- una
-- fila sin hora de disparo no dice nada que la preferencia general no diga ya --, asi que va
-- NOT NULL: para volver al horario general se BORRA la fila, no se vacia.
--
-- PRECEDENCIA (la impone el dominio, ver `HorarioResuelto` y el adaptador de preferencias):
--   excepcion de FECHA exacta > horario de ese DIA DE SEMANA > cambio general vigente >
--   preferencia general > catalogo.
-- De mas especifico a mas general, y campo por campo: una fila de esta tabla que solo fija la hora
-- de disparo conserva la hora limite de la capa de abajo.

SET search_path TO renaser, public;

CREATE TABLE horario_semanal_habito (
    participante_id uuid     NOT NULL REFERENCES participantes_programa (usuario_id) ON DELETE CASCADE,
    habito_id       uuid     NOT NULL REFERENCES habitos (id) ON DELETE CASCADE,
    dia_semana      smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    hora_disparo    time     NOT NULL,
    hora_limite     time,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    actualizado_en  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (participante_id, habito_id, dia_semana),
    CONSTRAINT horario_semanal_horas_coherentes
        CHECK (hora_limite IS NULL OR hora_disparo < hora_limite)
);

-- El barrido nocturno pregunta "que dice el plan de este participante para ESTE dia de la semana"
-- una vez por participante, para todos sus habitos. Este es el indice de esa consulta.
CREATE INDEX horario_semanal_participante_dia_idx
    ON horario_semanal_habito (participante_id, dia_semana);

COMMENT ON TABLE horario_semanal_habito IS
    'Hora propia de un habito para UN dia de la semana, que se repite todas las semanas. Distinta '
    'de horarios_habito_por_fecha, que es una excepcion de una fecha y no se repite. Ver V39.';
COMMENT ON COLUMN horario_semanal_habito.dia_semana IS
    'ISO-8601: 1 = lunes ... 7 = domingo, el mismo valor que java.time.DayOfWeek.getValue(). Ver V39.';
