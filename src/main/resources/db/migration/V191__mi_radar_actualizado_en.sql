-- V191 — La hora lógica de Mi Radar, para decidir un choque (spec 048, fase 3, FR-019, D13).
--
-- QUÉ RESUELVE. Mi Radar guardado sin internet llega horas después. Si desde otro teléfono se cambió Mi Radar después de la hora
-- en que lo hiciste, gana lo del servidor y se avisa (D13); para saberlo hace falta la hora de la última vez que se tocó.
--
-- POR QUÉ UNA COLUMNA NUEVA. `participantes_programa` se actualiza por muchos otros motivos (día del programa, célula, zona…):
-- su fila no sirve de señal, y `mi_radar_encendido` / `dias_mi_radar` no guardan cuándo cambiaron. Mi Radar es una sola cosa
-- (encendido y días se guardan juntos), así que basta un instante para todo.
--
-- QUÉ GUARDA. `mi_radar_actualizado_en`: la hora lógica del último guardado (la hora real en que la persona lo hizo si vino de
-- la cola sin red, o la hora de siempre si no). NULL = nunca se tocó, se lee como «muy viejo». Sin backfill: lo ya guardado
-- sigue valiendo como viejo, que es lo correcto para cualquier envío sin red posterior.
SET search_path TO renaser, public;

ALTER TABLE participantes_programa ADD COLUMN IF NOT EXISTS mi_radar_actualizado_en TIMESTAMPTZ;
