-- Responder a un mensaje del chat (D-251, pedido del dueño del 2026-10-05: «menú al mantener presionado un
-- mensaje: copiar y responder»).
--
-- Problema: la respuesta tiene que poder decir «Mensaje eliminado» cuando lo que citaba ya no está, y hoy la
-- base lo impide. `mensajes.respuesta_a_id` existe desde V1 con `REFERENCES mensajes (id) ON DELETE SET NULL`.
-- Cuando el mensaje citado se borra de verdad —la cuenta de su autor se elimina (D-243: sus mensajes de los
-- grupos y de la comunidad se borran, no se anonimizan; y la cascada de `emisor_id` hace lo mismo si se borra
-- la fila de `usuarios`)—, la FK pone `respuesta_a_id` en NULL y la respuesta queda idéntica a un mensaje que nunca citó nada: se pierde el hecho
-- de que era una respuesta. La app dibujaría «¡Sí, yo también!» suelto, sin saber a qué contestaba.
--
-- Qué se hace: se quita esa FK y `respuesta_a_id` pasa a ser lo que la persona respondió, para siempre.
-- Si el citado ya no está, el id queda colgando a propósito: el servidor no lo encuentra al armar el resumen
-- y responde `replyToDeleted: true` (sin el id: no se publica un id que ya no lleva a nada).
--
-- Por qué no una columna nueva (`es_respuesta boolean` + CHECK, que fue la primera idea):
--   · Duplicaba un dato que ya está en la fila: «es respuesta» es exactamente «respuesta_a_id no es NULL»
--     si la base no lo borra. Dos columnas que dicen lo mismo se desincronizan en cuanto un camino escribe
--     una sola (la lección de `fecha_graduacion_esperada`, V22).
--   · Con el CHECK, la versión anterior del backend —que sigue atendiendo ~50 s contra el esquema nuevo en
--     el despliegue sin corte, y la que quedaría si hubiera que volver atrás— no sabe de la columna: su
--     INSERT con `respuesta_a_id` violaría el CHECK. Quitar una FK no le cambia nada a esa versión.
--
-- Qué garantizaba la FK y quién lo garantiza ahora: que el citado existiera al responder. Lo exige el dominio
-- (`Cita.aResponder`), que además exige lo que la FK nunca exigió: que sea de la MISMA conversación y que
-- siga a la vista. Al leer, el resumen solo se arma si el citado es de la misma conversación de la respuesta
-- (`Cita.sePuedeMostrar`): una fila mal escrita a mano no puede mostrar texto de otro chat.
-- Una FK compuesta `(conversacion_id, respuesta_a_id) → (conversacion_id, id)` lo habría impuesto en la base,
-- pero exige un índice único nuevo sobre `mensajes` y vuelve a borrar el dato al eliminar el citado, que es
-- justo lo que se quiere evitar.
--
-- Los datos personales: el id que queda colgando no es de nadie (un UUID al azar de una fila que ya no
-- existe). El texto, el autor y el archivo del citado se borran con su fila, igual que antes.
--
-- El índice `mensajes_respuesta_idx` (parcial, V1) se conserva: es chico y sirve a cualquier «quién respondió
-- a este mensaje» que se agregue después. Hoy nadie consulta por esa columna (el resumen busca por PK).
--
-- Compatibilidad: ninguna app publicada manda `replyToId` (verificado en master, subida-produccion,
-- apk-preview y e2e-integ del front), así que no hay respuestas viejas que cambien de aspecto; y la versión
-- anterior del backend sigue funcionando igual contra este esquema.
--
-- Cada migración corre en su propia conexión: si el contenedor arrancó en un despliegue posterior, el
-- search_path que fija V1 no está puesto en la sesión (mismo preámbulo que V13 y V18).
SET search_path TO renaser, public;

ALTER TABLE mensajes DROP CONSTRAINT mensajes_respuesta_a_id_fkey;

COMMENT ON COLUMN mensajes.respuesta_a_id IS
    'A qué mensaje de la MISMA conversación responde este (D-251). Sin FK desde V92: si el citado se borra, '
    'el id queda y la respuesta dice «Mensaje eliminado». La misma conversación la exige el dominio (Cita).';
