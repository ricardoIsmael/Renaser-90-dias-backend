-- V168 — Las entregas de los recordatorios de eventos por WhatsApp (spec 041; D-322)
--
-- Por qué se toca la base: hay que (1) apartar la entrega ANTES de llamar al proveedor, para que una reentrega del *outbox*, dos pasadas o dos
-- instancias del servidor no manden dos veces un mensaje de pago (YCloud no usa `externalId` como llave de idempotencia), (2) anotar cómo terminó
-- cada envío (aceptado, entregado, leído, falló, no se supo) y (3) saber a qué persona pertenece un mensaje para que el botón «No recibir más» y un
-- «BAJA» que responde a ese mensaje apaguen SU preferencia. Ninguna columna existente sirve: `notificaciones` guarda la bandeja de la app, no el
-- estado de un envío externo.
--
-- Lo que NO guarda, a propósito (privacidad): ni el texto del mensaje ni el número de WhatsApp de la persona ni las respuestas que escriba. El número
-- vive en un solo lugar, la respuesta `whatsapp` de la Ficha Inicial (`respuestas_onboarding`), y se lee al enviar. Sin llaves foráneas a tablas de
-- otros módulos (`.claude/rules/01`): `evento_id` y `usuario_id` son identificadores sin FK, como en `notificaciones`. Retención de 12 meses (purga diaria).
--
-- `clave` es la llave de deduplicación: la misma que el aviso (`AvisoDeEvento.claveDeduplicacion`, una por fila de la cola de recordatorios), que
-- distingue cada uno de los cuatro avisos y cada persona. `id` viaja al proveedor como `externalId` y en la carga del botón («BAJA:<id>»).
-- `aviso_min` es cuál de los cuatro avisos fue (1440, 60, 30 o 10), para el resumen de Administración.

SET search_path TO renaser, public;

CREATE TABLE IF NOT EXISTS entregas_whatsapp (
    id             uuid        PRIMARY KEY,
    clave          uuid        NOT NULL,
    evento_id      uuid        NOT NULL,
    usuario_id     uuid        NOT NULL,
    aviso_min      integer     NOT NULL CHECK (aviso_min > 0),
    estado         text        NOT NULL CHECK (estado IN ('ENVIANDO', 'ACEPTADO', 'ENVIADO', 'ENTREGADO', 'LEIDO', 'FALLIDO', 'NO_SE_SUPO', 'OMITIDO')),
    motivo         varchar(200),
    wamid          text,
    proveedor_id   text,
    creado_en      timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT entregas_whatsapp_clave_uk UNIQUE (clave)
);

-- El resumen de un evento (`GET /calendar/events/{id}/whatsapp`) y el tope mensual cuentan por evento y por fecha.
CREATE INDEX IF NOT EXISTS entregas_whatsapp_evento_idx ON entregas_whatsapp (evento_id, estado);
CREATE INDEX IF NOT EXISTS entregas_whatsapp_creado_idx ON entregas_whatsapp (creado_en);
-- Un «BAJA» que responde a un mensaje nuestro llega con el wamid de ese mensaje.
CREATE INDEX IF NOT EXISTS entregas_whatsapp_wamid_idx ON entregas_whatsapp (wamid) WHERE wamid IS NOT NULL;

COMMENT ON TABLE entregas_whatsapp IS
    'Estado de cada recordatorio de evento enviado por WhatsApp (spec 041). Sin texto, sin numero, sin respuestas. Se purga a los 12 meses.';
