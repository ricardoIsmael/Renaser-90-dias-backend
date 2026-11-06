package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.calendar.api.RecordatorioEventoDebidoEvent;
import com.renaser.os.notifications.application.ports.in.evento.AvisoDeMensajeDelEvento;

/** Desarma el evento de {@code calendar} en lo que el chat y el WhatsApp necesitan (spec 041). Solo para las filas que son aviso de mensaje. */
final class AvisoDeMensajeDelEventoMapper {

    private AvisoDeMensajeDelEventoMapper() {
    }

    static AvisoDeMensajeDelEvento de(RecordatorioEventoDebidoEvent evento) {
        return new AvisoDeMensajeDelEvento(evento.recordatorioId(), evento.eventoId(), evento.destinatarioId(), evento.inicioOcurrencia(),
                evento.zonaHoraria(), evento.tituloEvento(), evento.avisoDeMensajeMin(), evento.confirmoAsistencia(), evento.formacion(),
                evento.enlace(), evento.lugar(), evento.esDeAudienciaElegida());
    }
}
