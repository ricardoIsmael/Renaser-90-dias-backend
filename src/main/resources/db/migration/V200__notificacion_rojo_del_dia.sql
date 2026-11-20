-- V200 — Tipo de notificación «aprendiz en rojo hoy» para el mentor (spec 057, FR-030)
--
-- Por qué se toca la base: el aviso de las 19:00 («<Nombre> está en rojo hoy») tiene que poder apagarse por separado —«Aprendices en rojo» en
-- Yo → Notificaciones—, y esa preferencia vive en `preferencias_notificacion`, cuya llave es el tipo. Reusar `ACOMPANAMIENTO_ALUMNO` apagaría
-- también el resumen de ausencias y evidencias vencidas. Mismo criterio que V46, V59, V70, V87, V109, V154 y V166: un valor más en el enum,
-- no una tabla.
--
-- Va en su propia migración porque ALTER TYPE ... ADD VALUE no se puede usar en la misma transacción que lo estrena (igual que V109, V154 y
-- V166). La app instalada valida `type` como texto abierto: un valor desconocido no la rompe.

SET search_path TO renaser, public;

ALTER TYPE tipo_notificacion ADD VALUE IF NOT EXISTS 'ROJO_DEL_DIA';
