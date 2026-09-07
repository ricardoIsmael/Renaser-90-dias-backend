-- preferencias_horario tiene identidad participante+habito y representa el horario general.
-- Se agrega una tabla con fecha en la identidad para conservar ambos conceptos sin migrar
-- preferencias existentes ni extender un ajuste puntual a otras jornadas.
-- Una excepcion de horario pertenece a una fecha exacta, nunca se promueve al horario general.
CREATE TABLE renaser.horarios_habito_por_fecha (
    participante_id uuid NOT NULL REFERENCES renaser.participantes_programa(usuario_id) ON DELETE CASCADE,
    habito_id uuid NOT NULL REFERENCES renaser.habitos(id) ON DELETE CASCADE,
    fecha date NOT NULL,
    hora_disparo time NOT NULL,
    hora_limite time,
    recordatorio_activo boolean NOT NULL,
    minutos_recordatorio smallint CHECK (minutos_recordatorio >= 0),
    creado_en timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    PRIMARY KEY (participante_id, habito_id, fecha),
    CHECK (hora_limite IS NULL OR hora_disparo < hora_limite)
);
CREATE INDEX horarios_fecha_participante_fecha_idx
    ON renaser.horarios_habito_por_fecha(participante_id, fecha);
