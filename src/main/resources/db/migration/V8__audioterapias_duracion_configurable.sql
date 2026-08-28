-- Habilita que la duracion de cada audioterapia semanal (hoy fija en 7 dias, confirmado por el
-- dueno del proyecto 2026-08-28) sea editable por semana individual desde un endpoint admin, en
-- vez de quedar hardcodeada en Java -- el negocio ajusta estos numeros seguido (ver
-- docs/MODULOS_A_AVANZAR.md D-48/D-49). Una columna sobre una tabla que ya existe, no una tabla
-- nueva: cada semana puede durar distinto (no tiene por que ser uniforme).

BEGIN;

SET search_path TO renaser, public;

ALTER TABLE audioterapias ADD COLUMN duracion_dias smallint NOT NULL DEFAULT 7 CHECK (duracion_dias > 0);

COMMENT ON COLUMN audioterapias.duracion_dias IS
    'Cuantos dias de programa se mantiene esta audioterapia antes de pasar a la siguiente. '
    'Editable por PATCH /api/v1/admin/audio-therapies/{semana} -- nunca hardcodear este numero en Java.';

COMMIT;
