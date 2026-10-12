-- =====================================================================================================
-- V96 — Hábitos globales por rol (specs/001-habitos-por-rol, D-264)
-- =====================================================================================================
--
-- QUÉ RESUELVE
--   Hasta hoy un hábito del catálogo (ambito = 'SISTEMA') llegaba a toda persona que hace el programa,
--   cualquiera fuera su rol. El dueño pidió (2026-10-12) que un ADMIN o ALQUIMISTA pueda marcar a qué
--   roles aplica cada hábito global, con dos reglas que obligan a guardar HISTORIA, no un valor:
--     * un cambio de roles (del hábito o de la persona) rige desde el día siguiente en la zona de cada
--       persona, aunque su registro de hoy se genere después del cambio (vigencia estricta);
--     * lo pasado no se toca: un día viejo se tiene que poder resolver siempre igual.
--
-- POR QUÉ NO ALCANZA EL MODELO ACTUAL
--   * `habitos` no tiene dónde guardar la audiencia. Una columna ahí perdería las versiones anteriores,
--     y con ellas la regla "desde mañana" para quien todavía no generó su día.
--   * `usuarios.rol` es solo el rol ACTUAL: users no guarda historia de rol (regla 04: no hay historial
--     de cambios de cuenta y el outbox borra los eventos entregados). Sin historia no se puede saber qué
--     rol tenía alguien ayer. El precedente es `semaforo_pausas` (V72): el módulo que necesita el dato lo
--     anota al recibir el evento.
--
-- POR QUÉ ESTA FORMA
--   * `roles text[]` con los códigos de la API (TRAINEE, MENTOR, MENTOR_LEAD, ADMIN, ALCHEMIST), y no FK
--     a `roles(id)` como cursos y eventos: esa tabla está SUPERADA (D-21) y no admite versiones sin otra
--     tabla. Tampoco `rol_usuario[]`: no hay precedente de ese enum fuera de `usuarios`, obliga a castear
--     (ver ConsultarResumenParticipacionPersistenceAdapter: "operator does not exist:
--     renaser.rol_usuario = character varying") y ataría habits a los nombres en español. Con texto, un rol
--     nuevo no exige migrar esta tabla; qué códigos existen lo valida el caso de uso contra UserRole.
--   * Array vacío = para todos. Así los hábitos de hoy no necesitan filas (cero datos migrados) y un rol
--     futuro también los recibe.
--   * `cambiado_por` sin FK a `usuarios`: es historia, como `ajustes_dia_programa`; no debe impedir borrar
--     una cuenta ni desaparecer con ella.
--
-- NOMBRES
--   `audiencias_habito` (una fila = una versión de la audiencia de un hábito) e
--   `historial_rol_participante` (una fila = desde cuándo una persona tuvo un rol; la primera, con
--   rige_desde = epoch, es el rol que tenía antes de su primer cambio registrado).
--
-- APPEND-ONLY: ninguna de las dos se actualiza ni se borra desde el código. La única baja es el CASCADE
-- al eliminar un hábito, que solo es posible si nunca generó registros (registros_habito RESTRICT).
-- =====================================================================================================

CREATE TABLE renaser.audiencias_habito (
    id           uuid        PRIMARY KEY,
    habito_id    uuid        NOT NULL REFERENCES renaser.habitos (id) ON DELETE CASCADE,
    roles        text[]      NOT NULL DEFAULT '{}',
    rige_desde   timestamptz NOT NULL,
    cambiado_por uuid        NOT NULL,
    cambiado_en  timestamptz NOT NULL,
    CONSTRAINT audiencias_habito_roles_sin_nulos CHECK (array_position(roles, NULL) IS NULL)
);

COMMENT ON TABLE renaser.audiencias_habito IS
    'Versiones de la audiencia (roles) de un hábito SISTEMA. Vacío = para todos. Append-only (V96, D-264).';

CREATE INDEX audiencias_habito_vigencia_idx ON renaser.audiencias_habito (habito_id, rige_desde);

CREATE TABLE renaser.historial_rol_participante (
    usuario_id uuid        NOT NULL,
    rige_desde timestamptz NOT NULL,
    rol        text        NOT NULL,
    PRIMARY KEY (usuario_id, rige_desde)
);

COMMENT ON TABLE renaser.historial_rol_participante IS
    'Desde cuándo cada persona tuvo cada rol, anotado por habits con RolDeUsuarioCambiadoEvent (V96, D-264).';
