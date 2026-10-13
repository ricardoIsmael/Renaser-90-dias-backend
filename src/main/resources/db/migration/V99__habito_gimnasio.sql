-- V99 — Activar el hábito de gimnasio (D-266, decisiones del dueño del 2026-10-13).
--
-- Que problema resuelve
-- ---------------------
-- El dueño pidió activar el hábito de gimnasio: una foto y nada más, opcional, todos los días, para
-- todos, y que cuente en un ranking propio (V98). El catálogo ya lo tiene desde V9 como
-- «ENTRENAMIENTO FÍSICO» (`ec0f2b08…`), apagado, con foto obligatoria y opcional: se reusa en vez de
-- crear otro (mismo criterio que V84 con KILÓMETROS DIARIOS).
--
-- Que cambia y por que
-- --------------------
-- * `titulo = 'GIMNASIO'`: el nombre que eligió el dueño (en mayúsculas, como el resto del catálogo).
-- * `clave_sistema = 'GYM'`: el ranking cuenta sus días por esta clave (`DiasDeGimnasioService`). No
--   tiene política propia: se completa con las reglas comunes (foto obligatoria por
--   `exigencia_evidencia`, opcional por `es_opcional`, que ya venían de V9).
-- * `activo = true`: sale para todos al desplegar (sin audiencia = para todos, D-264).
-- * Horario 07:00 – 23:59 (todos los días, días 1 a 90): paga completo en cualquier momento del día,
--   igual que KILÓMETROS DIARIOS (V86). Antes del ancla también es «a tiempo», así que ir al gimnasio
--   a las 05:30 paga los mismos puntos. 23:59 supera a propósito el tope de 23:50 de `VentanaDelDia`
--   (D-122), como V86; el panel lo reaplica si alguien edita ese horario.
--
-- Lo que NO hace
-- --------------
-- No toca registros (no hay: estaba apagado), ni la descripción (ya pide la foto del entrenamiento), ni
-- otros hábitos.
SET search_path TO renaser, public;

UPDATE habitos
SET titulo         = 'GIMNASIO',
    clave_sistema  = 'GYM',
    activo         = true,
    actualizado_en = now()
WHERE id = 'ec0f2b08-3a18-4ff4-8e2b-243e5220d211';

UPDATE horarios_habito
SET hora_disparo   = '07:00',
    hora_limite    = '23:59',
    actualizado_en = now()
WHERE habito_id = 'ec0f2b08-3a18-4ff4-8e2b-243e5220d211';
