package com.renaser.os.academy.infrastructure.adapter.out.persistence.recomendacion;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.recomendacion.RecomendacionAcademia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RecomendacionAcademiaPersistenceMapper {

    RecomendacionAcademia toDomain(RecomendacionAcademiaJpaEntity e) {
        return new RecomendacionAcademia(UserId.of(e.getParticipanteId()), e.getFecha(),
                LeccionId.of(e.getLeccionId()), e.getMotivo(), e.getCreadoEn());
    }

    RecomendacionAcademiaJpaEntity toEntity(RecomendacionAcademia r) {
        return new RecomendacionAcademiaJpaEntity(r.participanteId().value(), r.fecha(), r.leccionId().value(),
                r.motivo(), r.creadoEn());
    }
}
