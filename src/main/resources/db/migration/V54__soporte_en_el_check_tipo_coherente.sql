-- ============================================================================
-- V54 — `tipo_coherente` acepta la rama SOPORTE
-- ============================================================================
-- QUE PROBLEMA RESUELVE
--
-- V53 agrego el valor `SOPORTE` a `tipo_conversacion`, pero eso solo no alcanza para poder
-- insertar una: `conversaciones` tiene el CHECK `tipo_coherente` (V1:1286-1290) que enumera los
-- tres tipos uno por uno, asi que una fila con `tipo = 'SOPORTE'` no satisface NINGUNA de sus tres
-- ramas y el INSERT muere con
--
--   ERROR: new row for relation "conversaciones" violates check constraint "tipo_coherente"
--
-- Sin esta migracion, V53 es un valor de enum que nadie puede escribir.
--
-- POR QUE UNA MIGRACION APARTE Y NO DENTRO DE V53
--
-- Porque nombrar 'SOPORTE' en la expresion del CHECK es USAR el valor, y Postgres no deja usar un
-- valor de enum en la misma transaccion que lo creo:
--
--   ERROR: unsafe use of new value "SOPORTE" of enum type renaser.tipo_conversacion
--   HINT:  New enum values must be committed before they can be used.
--
-- Cada migracion de Flyway es una transaccion, asi que separarlas es exactamente lo que hace falta:
-- cuando esta corre, V53 ya comiteo. Detalle completo en la cabecera de V53.
--
-- POR QUE SE REESCRIBE EL CHECK ENTERO Y NO SE AGREGA UN SEGUNDO CHECK
--
-- Dos CHECK sobre la misma columna se leen como un AND, no como un OR: un
-- `CHECK (tipo <> 'SOPORTE' OR ...)` adicional dejaria el original rechazando igual todas las
-- filas SOPORTE. La invariante es una sola —"cada tipo trae exactamente su identificador y ningun
-- otro"— y tiene que quedar en una sola expresion, legible de arriba abajo.
--
-- POR QUE LA RAMA SOPORTE ES IGUAL A LA DE DIRECTA
--
-- Porque el soporte se identifica por `clave_directa` (`'soporte:' || <uuid del aprendiz>`), que es
-- la columna que ya trae el UNIQUE que lo hace irrepetible por persona. La justificacion de reusar
-- esa columna esta completa en la cabecera de V53.
--
-- SIN `NOT VALID`: la restriccion se valida contra las filas existentes a proposito. Son las tres
-- de siempre (CELULA/DIRECTA/GLOBAL) y ya cumplian el CHECK anterior, que esta version solo
-- AMPLIA — ninguna fila puede volverse invalida, y validarlas ahora evita que un dato corrupto
-- viejo se descubra recien dentro de seis meses.
--
-- Migracion ADITIVA en efecto: no borra ni reescribe ninguna fila.
-- ============================================================================

SET search_path TO renaser, public;

ALTER TABLE conversaciones DROP CONSTRAINT tipo_coherente;

ALTER TABLE conversaciones ADD CONSTRAINT tipo_coherente CHECK (
    (tipo = 'CELULA'  AND celula_id IS NOT NULL AND clave_directa IS NULL)
 OR (tipo = 'DIRECTA' AND clave_directa IS NOT NULL AND celula_id IS NULL)
 OR (tipo = 'GLOBAL'  AND celula_id IS NULL AND clave_directa IS NULL)
 OR (tipo = 'SOPORTE' AND clave_directa IS NOT NULL AND celula_id IS NULL)
);

COMMENT ON CONSTRAINT tipo_coherente ON conversaciones IS
    'Cada tipo trae exactamente su identificador: CELULA -> celula_id, DIRECTA y SOPORTE -> clave_directa, GLOBAL -> ninguno. SOPORTE se suma en V54.';
