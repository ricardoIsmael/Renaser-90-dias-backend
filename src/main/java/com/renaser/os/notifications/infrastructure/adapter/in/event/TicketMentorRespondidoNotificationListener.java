package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.support.api.TicketMentorRespondidoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Le avisa al aprendiz que su mentor le respondio el ticket (E-219: el evento se publicaba y nadie
 * lo escuchaba; el tipo {@code TICKET_RESPONDIDO} existia en el enum pero no lo emitia nadie).
 *
 * <p><b>Que dice.</b> Solo que hay respuesta, nunca su texto: el mismo cuerpo sale por push a la
 * pantalla bloqueada, y la respuesta es parte de una conversacion personal. Se lee en la app.
 *
 * <p>El id del ticket es el {@code origenEventoId}: el indice unico de {@code notificaciones}
 * descarta la reentrega del outbox (C-7). Un ticket se responde una sola vez.
 */
@Component
class TicketMentorRespondidoNotificationListener {

    static final String TITULO = "Tu mentor te respondió";
    static final String CUERPO = "Tu mentor respondió tu ticket. Ábrelo en la app para leer su respuesta.";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;

    TicketMentorRespondidoNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
    }

    @ApplicationModuleListener
    void on(TicketMentorRespondidoEvent event) {
        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(event.participanteId(),
                TipoNotificacion.TICKET_RESPONDIDO, TITULO, CUERPO, null, event.ticketId()));
    }
}
