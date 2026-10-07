-- Un mensaje del programa que no se refiere a nadie (D-262, pedido del dueño del 2026-10-07: el podio semanal
-- del ranking general en el grupo «Formación Renaser Global»).
--
-- Problema: `mensajes.emisor_id` es `NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE` (V1). Los mensajes del
-- programa (SISTEMA, D-199/D-204) guardan ahí a QUIÉN se refieren —el aprendiz que recibe la bienvenida o la
-- tarjeta del semáforo—, y la cascada los borra con su cuenta, lo que en esos casos es correcto: el mensaje es
-- sobre esa persona y en el chat de esa persona. El podio semanal no es sobre una persona: nombra a hasta cinco
-- y va al grupo de toda la comunidad. Guardarlo a nombre de uno (el primer puesto) haría que, si esa persona
-- elimina su cuenta (D-243), el podio desapareciera del grupo para todos, con los otros cuatro nombres. Y no hay
-- una cuenta «sistema»: D-199 descartó usar una de staff porque la cascada borraría sus mensajes al darla de baja.
--
-- Qué se hace: `emisor_id` admite NULL, pero solo en un mensaje de SISTEMA. Es el camino (a) que D-199 dejó
-- anotado («queda como camino si el dueño prefiere que la base no guarde a nadie»). Los mensajes del programa
-- que SÍ se refieren a alguien siguen guardando a esa persona (bienvenidas, semáforo, emergencias): no cambian.
--
-- Por qué no una columna nueva: «a quién se refiere» ya es `emisor_id` en todo mensaje de SISTEMA; una segunda
-- columna diría lo mismo con otro nombre y se desincronizaría (la lección de V22). El CHECK es lo único que se
-- agrega, y se evalúa con los datos de la fila (regla 04).
--
-- Compatibilidad:
--   · Las filas existentes tienen todas `emisor_id`: el CHECK se valida sin tocar ninguna.
--   · La versión anterior del backend (que atiende ~50 s contra este esquema en el despliegue sin corte) sigue
--     insertando siempre con emisor: nada de lo que escribe cambia.
--   · Volver atrás DESPUÉS de que salga un podio: la versión anterior no sabe leer un `emisor_id` NULL
--     (`UserId` lo rechaza) y fallaría al listar los mensajes del grupo general. Antes de volver a una versión
--     anterior a esta, borrar esas filas: `DELETE FROM renaser.mensajes WHERE emisor_id IS NULL` (son solo los
--     podios; el interruptor `RANKING_SEMANAL_ACTIVO` está apagado por defecto).
--   · La app no ve la diferencia: un mensaje del programa ya viaja con `senderId` = UUID nulo y «Formación
--     Renaser», sin importar qué guarde la base (`Mensaje.remitentePublico`).
--   · El borrado de una cuenta (`BorradoDeCuentaEnChatAdapter`) borra con `emisor_id = :id`: un NULL no
--     coincide con nadie, así que ninguna cuenta se lleva el podio. Y al decidir qué archivos siguen en uso
--     (`emisor_id IS DISTINCT FROM :id`), el podio cuenta como de otro: su imagen no se borra con nadie.

SET search_path TO renaser, public;

ALTER TABLE mensajes ALTER COLUMN emisor_id DROP NOT NULL;

ALTER TABLE mensajes
    ADD CONSTRAINT mensaje_sin_emisor_solo_del_programa CHECK (emisor_id IS NOT NULL OR tipo = 'SISTEMA');

COMMENT ON COLUMN mensajes.emisor_id IS
    'Quién lo escribió; en un mensaje de SISTEMA, a quién se refiere (D-199). NULL solo en un SISTEMA que no se '
    'refiere a nadie, como el podio semanal del grupo general (V95, D-262).';
