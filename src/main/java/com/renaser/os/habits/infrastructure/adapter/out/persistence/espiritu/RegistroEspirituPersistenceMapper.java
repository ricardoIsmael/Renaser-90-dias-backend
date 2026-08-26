package com.renaser.os.habits.infrastructure.adapter.out.persistence.espiritu;

import com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspirituId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RegistroEspirituPersistenceMapper {

    RegistroEspiritu toDomain(RegistroEspirituJpaEntity e) {
        return RegistroEspiritu.rehydrate(RegistroEspirituId.of(e.getId()), UserId.of(e.getParticipanteId()),
                e.getDia().intValue(), e.getDesbloqueadoEn(), e.getFechaLimite(), e.getEntregadoEn(),
                e.getResumenTexto(), toDomainEstado(e.getEstado()), e.getCreadoEn(), e.getActualizadoEn());
    }

    RegistroEspirituJpaEntity toEntity(RegistroEspiritu r) {
        return new RegistroEspirituJpaEntity(r.id().value(), r.participanteId().value(), (short) r.dia(),
                r.desbloqueadoEn(), r.fechaLimite(), r.entregadoEn(), r.resumenTexto(), toJpaEstado(r.estado()),
                r.creadoEn(), r.actualizadoEn());
    }

    private EstadoRegistroEspirituJpa toJpaEstado(EstadoRegistroEspiritu estado) {
        return switch (estado) {
            case PENDIENTE -> EstadoRegistroEspirituJpa.PENDIENTE;
            case ENTREGADO -> EstadoRegistroEspirituJpa.ENTREGADO;
            case PERDIDO -> EstadoRegistroEspirituJpa.PERDIDO;
        };
    }

    private EstadoRegistroEspiritu toDomainEstado(EstadoRegistroEspirituJpa jpa) {
        return switch (jpa) {
            case PENDIENTE -> EstadoRegistroEspiritu.PENDIENTE;
            case ENTREGADO -> EstadoRegistroEspiritu.ENTREGADO;
            case PERDIDO -> EstadoRegistroEspiritu.PERDIDO;
        };
    }
}
