-- V123 — Dar su horario a los habitos del programa que estan activos y no tienen ninguna fila en `horarios_habito`.
-- Issue #44, E-659. No agrega tablas ni columnas: solo completa filas que faltan.
--
-- Que problema resuelve
-- ---------------------
-- `HabitoAdminService.crear` (Administracion -> «Habitos del programa») guardaba el habito y a quien le llega, pero no su
-- horario. `RegistroService.generarInterno` solo genera el registro del dia si algun horario vigente cubre ese dia, asi que
-- un habito activo SIN ninguna fila en `horarios_habito` no se genera para nadie: ni en Training ni en Hoy, por mucho que
-- tenga audiencia y este activo. (Poner hora desde Planificar responde 200 pero tampoco lo arregla: la preferencia
-- personal se apoya en el horario del catalogo, que no existe.) El codigo ya crea el horario al dar de alta un habito
-- (E-659); esta migracion arregla los que se dieron de alta antes del arreglo.
--
-- Que cambia y por que
-- --------------------
-- Una fila por habito de ESE tipo, con la misma forma con la que ahora nace uno nuevo y que ya tiene DESPERTAR:
--   dia_inicio = 1, dia_fin = NULL (abierto), tipo_dia = 'TODOS', hora_disparo = NULL, hora_limite = NULL.
-- Sin hora el habito vale todo el dia y aparece como «Toca para ponerle hora»; quien administra puede ponerle la hora
-- editando esa fila (`POST /admin/habits/schedules/{scheduleId}`) y cada persona puede moverla desde Planificar.
--
-- Por que no se reusa nada existente: la fila falta, no hay columna que reinterpretar. El nombre sigue la costumbre de
-- las migraciones de datos del catalogo (V84, V99).
--
-- Quien entra y quien no
-- ----------------------
-- * Solo `ambito = 'SISTEMA'`: un habito PERSONAL crea su horario en la misma transaccion desde siempre
--   (`MisHabitosService.crear`).
-- * Solo `activo = true`: es el que hoy esta invisible. Los inactivos no cambian de comportamiento con esta migracion.
--   OJO: activar desde el panel uno que no tenga horario (en la copia local hay cuatro heredados de V9: MANTRA MATUTINO,
--   MOVIMIENTO LIBRE, REGISTRO DE INTOXICACION CONSCIENTE y RITUAL DEL AGUA PRESENTE) repite el sintoma; quedo
--   anotado en E-659 como pendiente y no se arregla aqui porque el pedido fue solo los activos.
-- * Solo los que no tienen NINGUNA fila: nunca se toca el horario que ya existe.
-- * Idempotente: una segunda corrida no encuentra nada que completar.
--
-- No activa ni desactiva ningun habito, no crea registros (los genera la generacion normal de cada dia, que ya lee
-- `horarios_habito`) y no toca puntos ni rachas.
SET search_path TO renaser, public;

INSERT INTO horarios_habito (habito_id, dia_inicio, dia_fin, tipo_dia, hora_disparo, hora_limite)
SELECT h.id, 1, NULL, 'TODOS', NULL, NULL
FROM habitos h
WHERE h.ambito = 'SISTEMA'
  AND h.activo
  AND NOT EXISTS (SELECT 1 FROM horarios_habito x WHERE x.habito_id = h.id);
