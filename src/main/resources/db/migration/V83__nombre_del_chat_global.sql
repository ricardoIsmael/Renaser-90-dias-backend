-- ============================================================================
-- El chat general se llama «Formación Renaser Global» (D-221, pedido del dueño del 2026-09-29):
-- «Formación Renaser Global, grupo general donde estarán todos».
--
-- QUE PROBLEMA RESUELVE
-- ---------------------
-- La conversación GLOBAL guarda su nombre en `conversaciones.nombre` (el Admin la puede renombrar,
-- #28, `PATCH /api/v1/chat/conversations/global/name`). Hoy dice «Comunidad Global» (la sembró V47)
-- o «Global» (si la creó `Conversacion.crearGlobal` en una base nueva). El código nuevo crea la
-- GLOBAL ya con el nombre nuevo, pero la fila que existe no se entera sola.
--
-- POR QUE UNA MIGRACION Y NO EL ENDPOINT DE RENOMBRAR
-- ---------------------------------------------------
-- El endpoint lo haría a mano y en un solo entorno; así queda igual en todos (local, pruebas,
-- producción) y registrado cuándo se hizo. Después de esto el Admin la puede seguir renombrando.
--
-- POR QUE NO SE DERIVA AL LEER, COMO LOS NOMBRES DEL GRUPO Y DEL SOPORTE
-- ---------------------------------------------------------------------
-- Esos dos dependen de otra tabla (el mentor vigente, el nombre del aprendiz) y por eso se derivan
-- (regla 04). El de la comunidad no depende de nada: es un nombre elegido, y ya tiene su columna y su
-- forma de cambiarlo. Derivarlo le quitaría al Admin el renombrar que ya tiene.
--
-- Se pisa sea cual sea el nombre actual: el dueño definió este. Idempotente (correrla dos veces da
-- lo mismo). Sin columnas nuevas.
-- ============================================================================

SET search_path TO renaser, public;

UPDATE conversaciones
SET nombre = 'Formación Renaser Global'
WHERE tipo = 'GLOBAL';
