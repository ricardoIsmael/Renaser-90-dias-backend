package com.renaser.os.chat.infrastructure.adapter.out.redis;

import com.renaser.os.chat.domain.model.mensaje.Mensaje;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload liviano para el empuje en vivo por Redis Pub/Sub — no es el contrato REST
 * completo ({@code MensajeResponse}), a proposito: el cliente que ya esta mirando la
 * conversacion solo necesita saber "llego un mensaje nuevo" para refrescar; el detalle
 * completo (con paginacion keyset) sigue viniendo de {@code GET .../messages}.
 *
 * <p>El campo {@code event} se agrego el 2026-09-17, cuando la presencia empezo a viajar por
 * este mismo canal ({@link PresenciaFanoutPayload}). Va SIEMPRE y con valor fijo: un cliente
 * que recibe una forma que no reconoce tiene que poder descartarla sin adivinar, y adivinar
 * por "tiene campo texto" habria sido exactamente eso.
 *
 * <p>{@code senderId} es el de {@code Mensaje.remitentePublico}: en un mensaje del programa (D-199), el
 * UUID nulo y no la persona guardada, que si no descartaría el aviso como «eco propio». {@code type}
 * sale con el nombre del dominio ({@code SISTEMA}, {@code TEXTO}), no traducido como en el REST (E-333).
 */
record MensajeFanoutPayload(String event, UUID id, UUID conversationId, UUID senderId, String type,
                             String text, Instant createdAt) {

    static final String EVENTO = "MESSAGE";

    static MensajeFanoutPayload from(Mensaje mensaje) {
        return new MensajeFanoutPayload(EVENTO, mensaje.id().value(), mensaje.conversacionId().value(),
                mensaje.remitentePublico(), mensaje.tipo().name(), mensaje.texto(), mensaje.creadoEn());
    }
}
