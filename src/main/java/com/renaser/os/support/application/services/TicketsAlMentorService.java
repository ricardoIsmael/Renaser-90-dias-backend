package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketsAlMentor;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase.AbrirTicketMentorCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementa {@link TicketsAlMentor} (2026-09-23) delegando en {@link ListarTicketsMentorUseCase} y
 * {@link AbrirTicketMentorUseCase}, los mismos que sirven a la app. Solo traduce.
 *
 * <p><b>El unico filtro propio, y por que.</b> {@code ListarTicketsMentorUseCase.propios} a un
 * MENTOR le devuelve los tickets de TODOS los aprendices (deuda de celula, ver su javadoc). El
 * contrato promete "los que abrio esta persona": si el acompanante lo llamara con una cuenta de
 * mentor, le pasaria al modelo lo que escribieron otros. Por eso se queda solo con los tickets cuyo
 * participante es quien pregunta. Para un aprendiz no cambia nada: el caso de uso ya le devuelve
 * solo los suyos.
 *
 * <p>Sin {@code @Transactional} propio: la transaccion es la del caso de uso.
 */
@Service
class TicketsAlMentorService implements TicketsAlMentor {

    private final ListarTicketsMentorUseCase listarUseCase;
    private final AbrirTicketMentorUseCase abrirUseCase;

    TicketsAlMentorService(ListarTicketsMentorUseCase listarUseCase, AbrirTicketMentorUseCase abrirUseCase) {
        this.listarUseCase = listarUseCase;
        this.abrirUseCase = abrirUseCase;
    }

    @Override
    public List<TicketAlMentor> propios(UserId aprendizId) {
        return listarUseCase.propios(aprendizId, null).tickets().stream()
                .filter(ticket -> ticket.participanteId().equals(aprendizId))
                .map(TicketsAlMentorService::aTicketAlMentor)
                .toList();
    }

    @Override
    public TicketAbierto abrir(UserId aprendizId, TextosDelTicket textos) {
        TicketMentor abierto = abrirUseCase.abrir(new AbrirTicketMentorCommand(aprendizId,
                textos.descripcionBloqueo(), textos.solucionesIntentadas(), textos.impactoMetaSmart()));
        return new TicketAbierto(abierto.id().value(), abierto.creadoEn());
    }

    private static TicketAlMentor aTicketAlMentor(TicketMentor ticket) {
        return new TicketAlMentor(ticket.id().value(),
                new TextosDelTicket(ticket.descripcionBloqueo(), ticket.solucionesIntentadas(),
                        ticket.impactoMetaSmart()),
                ticket.estado().estaRespondido(), ticket.respuestaMentor(), ticket.creadoEn(), ticket.respondidoEn());
    }
}
