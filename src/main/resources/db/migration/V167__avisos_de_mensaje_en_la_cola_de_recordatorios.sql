-- V167 — La cola de recordatorios también lleva los avisos de mensaje (chat y WhatsApp) (spec 041; D-322)
--
-- Por qué se toca la base: `recordatorios_evento` es la cola con que `calendar` decide QUÉ y CUÁNDO se avisa de un evento (una fila por persona,
-- ocurrencia e instante, con `SKIP LOCKED`, idempotente y con el outbox de Modulith). Los recordatorios por mensaje —1 día, 1 hora, 30 y 10 minutos
-- antes, en el chat de soporte y por WhatsApp— tienen la misma forma y el mismo ciclo de vida (se generan, se cancelan si el evento se edita o se
-- cancela, salen una vez), así que se reusa esa cola en vez de armar una segunda. Una cola aparte duplicaría el generador, el despacho, la
-- cancelación al editar y el *outbox*, y se desincronizaría de la primera en cuanto cambie una de las dos.
--
-- Qué se agrega (dos columnas, ninguna tabla):
--   * `aviso_en_app` — si esa fila es un aviso del teléfono (el de siempre). `true` para todo lo que ya existe: nada cambia para la app. Una fila que
--     solo existe para el mensaje (por ejemplo, la de «1 día antes», que ninguna regla del tipo de evento pide) nace en `false`, para que el aviso del
--     teléfono NO se multiplique. Si el instante de un aviso de mensaje coincide con el de una regla del evento (10 minutos antes de una mentoría)
--     es UNA sola fila, con las dos marcas, porque la llave única `(evento, ocurrencia, persona, enviar_en)` no cambia.
--   * `mensaje_min_antes` — cuántos minutos antes del inicio es este aviso de mensaje (1440, 60, 30, 10), o NULL si la fila no es de mensaje. Lo lee el
--     despacho y lo manda en el evento publicado; `notifications` decide el canal, la audiencia (quién dijo «Voy») y el tope de atraso.
--
-- Por qué estos nombres: `aviso_en_app` dice para qué canal es, y `mensaje_min_antes` nombra la unidad (minutos) y el sentido (antes del inicio),
-- igual que `MINUTOS_ANTES` de las reglas del evento.
--
-- Agregar una columna con valor constante por defecto no reescribe la tabla (Postgres 11+). Volver a correrla no cambia nada.

SET search_path TO renaser, public;

ALTER TABLE recordatorios_evento
    ADD COLUMN IF NOT EXISTS aviso_en_app      boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS mensaje_min_antes integer;

ALTER TABLE recordatorios_evento
    ADD CONSTRAINT recordatorios_evento_algun_aviso_ck
        CHECK (aviso_en_app OR mensaje_min_antes IS NOT NULL),
    ADD CONSTRAINT recordatorios_evento_mensaje_min_ck
        CHECK (mensaje_min_antes IS NULL OR mensaje_min_antes > 0);

COMMENT ON COLUMN recordatorios_evento.aviso_en_app IS
    'true = esta fila es un aviso del telefono (bandeja y push). false = existe solo para el mensaje de chat/WhatsApp (spec 041).';
COMMENT ON COLUMN recordatorios_evento.mensaje_min_antes IS
    'Minutos antes del inicio de este aviso de MENSAJE (chat y WhatsApp); NULL si la fila no es de mensaje (spec 041).';
