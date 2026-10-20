-- V109 — El líder de mentores ve el cumplimiento de cada persona (spec 005; D-274)
--
-- Por qué se toca la base
-- -----------------------
-- El dueño pidió (2026-10-19) que el líder baje del grupo a la persona: ve su semana y sus evidencias, le avisa a
-- su mentor y le escribe. Tres cosas de eso no caben en lo que ya existe:
--
--   1. QUIÉN MIRÓ A QUIÉN. Ver la ficha de alguien es un acto que se registra (FR-016, «siempre: no se edita ni se
--      borra») y que alimenta el resumen que recibe el mentor a las 20:00 («Hoy tu líder revisó a …»). Ninguna
--      tabla guarda eso: `observaciones_mentor` es lo que el líder DICE, no lo que MIRA, y el outbox borra los
--      eventos entregados (`completion-mode: DELETE`), así que tampoco sirve como historia.
--   2. LA PERSONA Y LA SEMANA DE UNA OBSERVACIÓN. «Avisar a su mentor» es la observación de siempre (RL-14..RL-17,
--      V89), pero ahora trata de una persona y de una semana. Sin esas dos columnas habría que meterlas en el
--      texto, y el texto es libre.
--   3. UN TIPO DE AVISO PROPIO para el resumen al mentor. Es la llave de `preferencias_notificacion`: con otro
--      tipo, silenciar «Tu líder revisó a tu grupo» silenciaría también otra cosa.
--
-- Qué se agrega y por qué así
-- ---------------------------
-- · `consultas_de_ficha`: una fila por persona y por llamada del líder, de Administración o de Alquimista a la ficha
--   (cada cambio de semana es otra llamada y otra fila). APPEND-ONLY, como `ajustes_dia_programa` (V21) y
--   `observaciones_mentor` (V89), pero esta vez la base lo hace cumplir con un trigger (UPDATE, DELETE y TRUNCATE
--   fallan): es un registro de auditoría y un bug que lo reescriba no debe poder esconderse.
--     - Sin FK a `usuarios` en `consultor_id`, `persona_id` y `mentor_id`: el registro sobrevive al borrado de
--       cualquiera de las cuentas (D-243), que es justo cuando una auditoría más se necesita. Guarda solo ids, ningún
--       nombre ni texto.
--     - `mentor_id` es el mentor vigente del grupo al consultar, o NULL: es a quién se le avisa, y lo decide quien
--       escribe la fila, no se deduce después (el mentor puede cambiar).
--     - `rol_consultor` con los nombres del dominio (`UserRole`): el resumen solo cuenta al líder, y la fila dice con
--       qué rol se miró aunque el rol de la persona cambie después.
--     - `semana_desde`: el primer día de la ventana consultada, que no tiene que ser sábado (la ventana vigente del
--       semáforo son los 7 días que terminan ayer).
--     - Índice por `(mentor_id, consultada_en)`: el resumen horario lee solo las consultas de un mentor en su día.
-- · `observaciones_mentor.persona_id` y `.semana_desde`, NULL (las filas de antes no los tienen). `persona_id`
--   lleva FK con ON DELETE CASCADE, igual que `mentor_id`: si la cuenta de la persona se borra para siempre, también
--   se va lo escrito sobre ella (el texto nombra a la persona; `BorradoDeCuentaEnLeadershipAdapter` lo borra antes y
--   la cascada queda de red de seguridad, D-243).
--   `semana_desde` sin `persona_id` no tiene sentido y la base lo rechaza.
-- · `tipo_notificacion` gana `REVISION_DEL_LIDER` en vez de una tabla de avisos propia, como V46, V49, V59, V70 y V87.
--
-- Por qué estos nombres
-- ---------------------
-- `consultas_de_ficha` nombra el hecho (alguien consultó una ficha), no el uso (el resumen). `REVISION_DEL_LIDER`
-- nombra lo que el mentor recibe: «tu líder revisó a tu grupo».
--
-- La app instalada no se rompe con un valor desconocido: valida `type` como texto abierto (ver V70).

-- ALTER TYPE ... ADD VALUE va primero y sin BEGIN/COMMIT, igual que V46: el valor nuevo no se usa en esta migración.
ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'REVISION_DEL_LIDER';

SET search_path TO renaser, public;

CREATE TABLE consultas_de_ficha (
    id             uuid        PRIMARY KEY,
    consultor_id   uuid        NOT NULL,
    rol_consultor  varchar(32) NOT NULL CHECK (rol_consultor IN ('MENTOR_LEAD', 'ADMIN', 'ALCHEMIST')),
    persona_id     uuid        NOT NULL,
    grupo_id       uuid        NOT NULL,
    mentor_id      uuid,
    semana_desde   date        NOT NULL,
    consultada_en  timestamptz NOT NULL
);

CREATE INDEX consultas_de_ficha_mentor_idx ON consultas_de_ficha (mentor_id, consultada_en);

CREATE FUNCTION consultas_de_ficha_solo_se_agrega() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'consultas_de_ficha es append-only: no se edita ni se borra (spec 005, FR-016)'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER consultas_de_ficha_append_only
    BEFORE UPDATE OR DELETE ON consultas_de_ficha
    FOR EACH ROW EXECUTE FUNCTION consultas_de_ficha_solo_se_agrega();

CREATE TRIGGER consultas_de_ficha_sin_truncate
    BEFORE TRUNCATE ON consultas_de_ficha
    FOR EACH STATEMENT EXECUTE FUNCTION consultas_de_ficha_solo_se_agrega();

COMMENT ON TABLE consultas_de_ficha IS
    'Quién abrió la ficha de cumplimiento de quién (spec 005, D-274). Append-only por trigger: sin UPDATE, DELETE ni TRUNCATE.';

ALTER TABLE observaciones_mentor
    ADD COLUMN persona_id   uuid REFERENCES usuarios (id) ON DELETE CASCADE,
    ADD COLUMN semana_desde date,
    ADD CONSTRAINT observaciones_mentor_semana_con_persona CHECK (semana_desde IS NULL OR persona_id IS NOT NULL);

-- La cascada del borrado de una cuenta busca por esta columna; sin el índice recorrería la tabla entera.
CREATE INDEX observaciones_mentor_persona_idx ON observaciones_mentor (persona_id) WHERE persona_id IS NOT NULL;

COMMENT ON COLUMN observaciones_mentor.persona_id IS
    'La persona (aprendiz o quien lleva su programa) sobre la que trata la observación; NULL en las de antes (V109).';
COMMENT ON COLUMN observaciones_mentor.semana_desde IS
    'Primer día de la ventana de cumplimiento que el líder miraba al escribirla; solo con persona_id (V109).';
