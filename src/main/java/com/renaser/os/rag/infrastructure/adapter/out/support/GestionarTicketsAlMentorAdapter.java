package com.renaser.os.rag.infrastructure.adapter.out.support;

import com.renaser.os.rag.application.ports.out.soporte.GestionarTicketsAlMentorPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketsAlMentor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Implementa {@link GestionarTicketsAlMentorPort} delegando en {@code support.api} (D-41): {@code rag}
 * nunca toca la tabla de tickets. Es una traduccion y nada mas; el rol, la cuenta activa y los
 * largos los decide {@code support}.
 */
@Component
class GestionarTicketsAlMentorAdapter implements GestionarTicketsAlMentorPort {

    private final TicketsAlMentor tickets;

    GestionarTicketsAlMentorAdapter(TicketsAlMentor tickets) {
        this.tickets = tickets;
    }

    @Override
    public int largoMaximoDeCadaTexto() {
        return TicketsAlMentor.LARGO_MAXIMO_TEXTO;
    }

    @Override
    public List<TicketAlMentor> propios(UserId aprendizId) {
        return tickets.propios(aprendizId).stream()
                .map(t -> new TicketAlMentor(new TextosDelTicket(t.textos().descripcionBloqueo(),
                        t.textos().solucionesIntentadas(), t.textos().impactoMetaSmart()), t.respondido(),
                        t.respuestaMentor(), t.creadoEn(), t.respondidoEn()))
                .toList();
    }

    @Override
    public Instant abrir(UserId aprendizId, TextosDelTicket textos) {
        return tickets.abrir(aprendizId, new TicketsAlMentor.TextosDelTicket(textos.descripcionBloqueo(),
                textos.solucionesIntentadas(), textos.impactoMetaSmart())).creadoEn();
    }
}
