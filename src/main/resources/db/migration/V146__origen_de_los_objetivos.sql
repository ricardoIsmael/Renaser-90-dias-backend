-- V146 — De dónde nace cada objetivo de 90 días (spec 026; D-303)
--
-- Por qué se toca la base
-- -----------------------
-- Los objetivos de 90 días nacen ahora del diagnóstico y llegan «por confirmar»: la persona los revisa y los puede reescribir UNA vez; al confirmarlos quedan fijos (Q2-A).
-- Para eso la roca maestra tiene que saber de dónde vino y si ya se confirmó. Antes la regla dependía del Mapa de Renacimiento (D-234/D-284), que se retira.
--
-- Por qué estas dos columnas
-- --------------------------
-- `origen` (PLAN | MAPA | DIAGNOSTICO) y `confirmada_en` (NULL = por confirmar). Todo lo que existe hoy queda CONFIRMADO (`confirmada_en = creado_en`): nadie pierde ni reinicia
-- nada, y un objetivo ya escrito sigue fijo como hasta ahora. Quien tiene el Mapa activo queda marcado `MAPA` (solo informativo: el Mapa no se borra).

SET search_path TO renaser, public;

ALTER TABLE rocas_maestras
    ADD COLUMN origen        text        NOT NULL DEFAULT 'PLAN' CHECK (origen IN ('PLAN', 'MAPA', 'DIAGNOSTICO')),
    ADD COLUMN confirmada_en timestamptz;

UPDATE rocas_maestras SET confirmada_en = creado_en;

UPDATE rocas_maestras r SET origen = 'MAPA'
 WHERE EXISTS (SELECT 1 FROM etapas_onboarding_completadas e
                WHERE e.usuario_id = r.participante_id AND e.flujo = 'mapa_dia7');

COMMENT ON COLUMN rocas_maestras.confirmada_en IS
    'NULL = el objetivo nació del diagnóstico y la persona aún no lo confirma: puede reescribirlo una vez (spec 026). Desde que se confirma es fijo.';
