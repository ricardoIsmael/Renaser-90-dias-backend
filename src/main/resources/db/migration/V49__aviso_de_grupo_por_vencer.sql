-- Aviso al administrador: a este grupo se le acaba el periodo.
--
-- El cliente lo pidio junto con los grupos programados: cuando un grupo esta por terminar su
-- estancia, que le llegue un aviso al administrador para que mueva a la gente o programe el
-- siguiente. Sin eso, los grupos se cierran solos el dia 30 y los alumnos se quedan sin grupo
-- hasta que alguien se acuerde.
--
-- Un valor mas en `tipo_notificacion`, NO una tabla de alertas propia -- misma decision que tomo
-- V46 con ACOMPANAMIENTO_ALUMNO, y por el mismo motivo: la bandeja, las preferencias, la
-- deduplicacion por `origen_evento_id` y la purga a 90 dias ya existen y se reutilizan tal cual.

-- ALTER TYPE ... ADD VALUE no puede correr dentro de un bloque transaccional junto con sentencias
-- que USEN el valor nuevo. Va solo, antes que nada, exactamente como en V46.
ALTER TYPE renaser.tipo_notificacion ADD VALUE IF NOT EXISTS 'GRUPO_POR_VENCER';
