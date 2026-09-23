package com.renaser.os.rag.infrastructure.adapter.out.calendar;

import com.renaser.os.calendar.api.EventosDelParticipanteFinder;
import com.renaser.os.rag.application.ports.out.calendario.ConsultarEventosDelAprendizPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link ConsultarEventosDelAprendizPort} delegando en {@code calendar.api} (D-41). Que
 * eventos ve la persona, como se expanden las recurrencias y su confirmacion los resuelve
 * {@code calendar}; aca solo se traduce.
 */
@Component
class ConsultarEventosDelAprendizAdapter implements ConsultarEventosDelAprendizPort {

    private final EventosDelParticipanteFinder finder;

    ConsultarEventosDelAprendizAdapter(EventosDelParticipanteFinder finder) {
        this.finder = finder;
    }

    @Override
    public AgendaDeEventos proximosDias(UserId aprendizId, int dias) {
        EventosDelParticipanteFinder.AgendaDeEventos agenda = finder.proximosDias(aprendizId, dias);
        return new AgendaDeEventos(agenda.desde(), agenda.hasta(), agenda.eventos().stream()
                .map(e -> new EventoDeLaAgenda(e.titulo(), e.iniciaLocal(), e.terminaLocal(), e.asistencia()))
                .toList());
    }
}
