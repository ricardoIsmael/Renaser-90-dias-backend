package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.api.ObservacionDelLiderEvent;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * El aviso para el teléfono del mentor cuando el líder le deja algo (spec 056, FR-019 y FR-025): una observación suya o un «Avisar a su mentor». Es el mismo
 * aviso para los dos, en la misma transacción que guarda la observación, y su clave de deduplicación sale de la observación: la redelivery del outbox no
 * crea otro aviso ni otro push.
 *
 * <p>El aviso no lleva el texto ({@link ObservacionDelLiderEvent}); apunta al chat donde quedó.
 *
 * <p><b>Sin push propio (decisión del dueño, 2026-11-18):</b> al mentor le llega un solo aviso, el del mensaje del chat; el evento queda
 * como constancia de la entrega y nadie lo convierte en notificación.
 */
@Component
class AvisosDeObservaciones {

    private final ApplicationEventPublisher eventos;

    AvisosDeObservaciones(ApplicationEventPublisher eventos) {
        this.eventos = eventos;
    }

    /** @param conversacionId el chat de dos donde quedó el texto; {@code null} si no se conoce (una app vieja ya mandó el mensaje) */
    void alMentor(ObservacionDeMentor observacion, UUID conversacionId) {
        UUID clave = UUID.nameUUIDFromBytes(("observacion-del-lider:" + observacion.id()).getBytes(StandardCharsets.UTF_8));
        eventos.publishEvent(new ObservacionDelLiderEvent(clave, observacion.id(), observacion.mentorId().value(), observacion.autorId().value(),
                observacion.tipo().name(), conversacionId, observacion.creadoEn()));
    }
}
