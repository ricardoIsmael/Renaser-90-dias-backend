package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketmentor.EstadoTicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import org.springframework.stereotype.Component;

@Component
class TicketMentorPersistenceMapper {

    TicketMentor toDomain(TicketMentorJpaEntity e) {
        return TicketMentor.rehydrate(
                TicketMentorId.of(e.getId()),
                UserId.of(e.getParticipanteId()),
                e.getDescripcionBloqueo(),
                e.getSolucionesIntentadas(),
                e.getImpactoMetaSmart(),
                toDomainEstado(e.getEstado()),
                e.getRespuestaMentor(),
                e.getRespondidoEn(),
                e.isGuardadoEnBiblioteca(),
                e.getCreadoEn());
    }

    TicketMentorJpaEntity toEntity(TicketMentor t) {
        return new TicketMentorJpaEntity(
                t.id().value(),
                t.participanteId().value(),
                t.descripcionBloqueo(),
                t.solucionesIntentadas(),
                t.impactoMetaSmart(),
                toJpaEstado(t.estado()),
                t.respuestaMentor(),
                t.respondidoEn(),
                t.guardadoEnBiblioteca(),
                t.creadoEn());
    }

    private EstadoTicketMentorJpa toJpaEstado(EstadoTicketMentor estado) {
        return switch (estado) {
            case ABIERTO -> EstadoTicketMentorJpa.ABIERTO;
            case RESPONDIDO -> EstadoTicketMentorJpa.RESPONDIDO;
        };
    }

    private EstadoTicketMentor toDomainEstado(EstadoTicketMentorJpa jpa) {
        return switch (jpa) {
            case ABIERTO -> EstadoTicketMentor.ABIERTO;
            case RESPONDIDO -> EstadoTicketMentor.RESPONDIDO;
        };
    }
}
