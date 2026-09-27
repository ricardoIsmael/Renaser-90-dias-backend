-- La marca de «a este aprendiz ya se le dio la bienvenida en ESTE grupo» (D-191, 2026-09-26).
--
-- Que problema resuelve
-- ---------------------
-- El procedimiento de Operaciones OPE-01-01 pide que, cuando el aprendiz se integra a su grupo de
-- la Formacion, su mentor le de la bienvenida en el chat del grupo. En la app eso sale del listener
-- que ya reconcilia el grupo (`ComposicionDeCelulaCambiadaEvent`). Ese evento es de INVALIDACION:
-- dice «el grupo cambio», no «entro Ana», se reentrega desde el outbox de Modulith y llega cada vez
-- que el grupo cambia (otro aprendiz, rotacion de mentor). Sin una marca durable, cada una de esas
-- entregas volveria a mandar la bienvenida a todos.
--
-- Por que no se reusa `mensajes_bienvenida`
-- -----------------------------------------
-- Su PK es solo `usuario_destinatario_id` (V1): una bienvenida por persona en toda su vida, y esa
-- fila ya la ocupa la bienvenida del chat de soporte (D-174, G-2). La del grupo es por persona Y por
-- grupo: un aprendiz trasladado tiene que recibir la del grupo nuevo.
--
-- Por que una columna de `asignaciones_celula` y no una tabla
-- -----------------------------------------------------------
-- Decision del duenio (2026-09-26): sin tablas nuevas. Y es el lugar natural: cada fila APRENDIZ es
-- exactamente «este aprendiz en este grupo, desde tal momento». Un traslado abre OTRA fila (otro
-- intervalo), asi que la marca queda por pertenencia sin clave compuesta nueva. JPA no la mapea: la
-- lee y escribe `MarcaDeBienvenidaEnGrupoJdbcAdapter`, y el UPDATE de una asignacion por JPA no la
-- toca porque no esta entre sus columnas.
--
-- Por que este nombre
-- -------------------
-- `bienvenida_enviada_en`: un instante, como `enviado_en` de `recordatorios_evento`. NULL = pendiente.
-- La idempotencia es `UPDATE ... WHERE bienvenida_enviada_en IS NULL` en la misma transaccion que
-- guarda el mensaje: si dos entregas se cruzan, la segunda espera el lock de la fila, ve la marca y
-- no manda nada.
--
-- Por que el relleno
-- ------------------
-- Los aprendices que YA estan en un grupo al desplegar no deben recibir una bienvenida atrasada el
-- primer dia que su grupo cambie. Se marcan todas las pertenencias de APRENDIZ existentes (vigentes
-- y cerradas); solo las que se abran desde ahora quedan en NULL. Las filas de mentor, guia y soporte
-- no se marcan: la bienvenida es solo para aprendices, y el CHECK lo deja escrito.
ALTER TABLE renaser.asignaciones_celula
    ADD COLUMN bienvenida_enviada_en timestamptz;

UPDATE renaser.asignaciones_celula
   SET bienvenida_enviada_en = now()
 WHERE funcion = 'APRENDIZ'
   AND bienvenida_enviada_en IS NULL;

ALTER TABLE renaser.asignaciones_celula
    ADD CONSTRAINT asignaciones_bienvenida_solo_aprendiz
        CHECK (bienvenida_enviada_en IS NULL OR funcion = 'APRENDIZ');

COMMENT ON COLUMN renaser.asignaciones_celula.bienvenida_enviada_en IS
    'Cuando el mentor le dio la bienvenida en el chat del grupo (OPE-01-01, D-191). NULL = pendiente. Solo APRENDIZ.';
