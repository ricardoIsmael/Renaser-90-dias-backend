-- ============================================================================
-- mensajes_renasia.agente: cada mensaje pertenece a UNO de los dos asistentes
-- ============================================================================
--
-- Que problema resuelve
-- ---------------------
-- El dueno del producto lo pidio textual (2026-09-04, D-102): "Sparkie: su objetivo es ayudar
-- en los cursos. El otro agente, que sera un chat aparte, sera durante su progreso de 90 dias.
-- No los juntes en un mismo." Hasta hoy habia UN asistente con dos modos (D-100) y UN historial
-- por persona: lo que se hablaba con el tutor dentro de un curso aparecia mezclado en el chat
-- general, y la memoria del modelo (ultimos 10 turnos) arrastraba turnos del otro modo. Para
-- que sean dos chats de verdad, cada mensaje tiene que decir con QUE agente se intercambio, y
-- el historial y la memoria se leen filtrando por ese valor.
--
-- Por que no se reusa una columna existente
-- -----------------------------------------
-- `conversaciones_renasia` es 1:1 con el usuario (PK = FK), asi que "una conversacion por
-- agente" obligaria a romper esa clave y tocar el baseline congelado (D-40). Las columnas
-- `marcado_por_usuario` / `nota_marca` / `anulado_por_admin` (D-49) no significan esto y estan
-- reservadas con sus valores por defecto. Una columna nueva en el mensaje es lo minimo que
-- separa los dos historiales sin cambiar la forma de la conversacion.
--
-- Por que estos nombres y tipos
-- -----------------------------
-- `agente text` con CHECK en vez de un tipo enum de Postgres (como `rol_mensaje_renasia`):
-- agregar un tercer agente manana es un ALTER del CHECK, no un ALTER TYPE con sus
-- restricciones transaccionales. Los valores van en ingles y SCREAMING_SNAKE porque son los
-- mismos que viajan por el wire (`agent` en el request y en el query param), espejo del enum
-- `AgenteConversacional` del dominio.
--
-- `DEFAULT 'COMPANION'`: las filas que ya existen (las 6 de las pruebas del dueno con el chat
-- de curso incluidas) pasan al acompanante. No hay forma de saber cuales fueron con el tutor —
-- D-100 no lo guardaba — y son datos de prueba, no de aprendices reales.
--
-- `mensajes_renasia_agente_idx (usuario_id, agente, creado_en)`: es la forma exacta de las dos
-- consultas de paginacion keyset (`WHERE usuario_id = ? AND agente = ? [AND creado_en < ?]
-- ORDER BY creado_en DESC`). El indice previo `mensajes_renasia_conv_idx (usuario_id, creado_en)`
-- se deja: sigue sirviendo al ON DELETE CASCADE de la conversacion y no estorba.
--
-- Que NO cambia
-- -------------
-- Ni la tabla `conversaciones_renasia` ni `fuentes_mensaje_renasia`. La cuota diaria en Redis
-- sigue siendo una sola por persona (es proteccion de abuso, no una cuenta por asistente).
-- ============================================================================

BEGIN;

SET search_path TO renaser, public;

ALTER TABLE mensajes_renasia
    ADD COLUMN agente text NOT NULL DEFAULT 'COMPANION';

ALTER TABLE mensajes_renasia
    ADD CONSTRAINT mensajes_renasia_agente_chk CHECK (agente IN ('COMPANION', 'COURSE_TUTOR'));

CREATE INDEX mensajes_renasia_agente_idx ON mensajes_renasia (usuario_id, agente, creado_en);

COMMIT;
