package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class CelulaPersistenceMapper {

    Celula toDomain(CelulaJpaEntity e) {
        return Celula.rehydrate(CelulaId.of(e.getId()), e.getNombre(),
                e.getMentorId() != null ? UserId.of(e.getMentorId()) : null, CohorteId.of(e.getCohorteId()),
                e.getUrlVideollamada(), e.getProximaSesionEn(), e.getCreadoEn(), e.getActualizadoEn());
    }

    CelulaJpaEntity toEntity(Celula c) {
        return new CelulaJpaEntity(c.id().value(), c.nombre(), c.mentorId() != null ? c.mentorId().value() : null,
                c.cohorteId().value(), c.urlVideollamada(), c.proximaSesionEn(), c.creadoEn(), c.actualizadoEn());
    }
}
