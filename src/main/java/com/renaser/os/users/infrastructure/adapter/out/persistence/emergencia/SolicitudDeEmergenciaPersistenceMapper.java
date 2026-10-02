package com.renaser.os.users.infrastructure.adapter.out.persistence.emergencia;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.emergencia.EstadoDeEmergencia;
import com.renaser.os.users.domain.model.emergencia.SolicitudDeEmergencia;
import org.springframework.stereotype.Component;

/** Mapper a mano: `smallint` contra `int`, el estado como texto y los UUID envueltos en `UserId`. */
@Component
class SolicitudDeEmergenciaPersistenceMapper {

    SolicitudDeEmergencia toDomain(SolicitudDeEmergenciaJpaEntity e) {
        return SolicitudDeEmergencia.rehydrate(e.getId(), UserId.of(e.getAprendizId()), e.getQueOcurrio(),
                e.getDiaPedido() == null ? null : e.getDiaPedido().intValue(), e.getDiaAlPedir(), EstadoDeEmergencia.valueOf(e.getEstado()), e.getCreadaEn(),
                e.getResueltaEn(), e.getResueltaPor() == null ? null : UserId.of(e.getResueltaPor()),
                e.getDiaAplicado() == null ? null : e.getDiaAplicado().intValue());
    }

    SolicitudDeEmergenciaJpaEntity toEntity(SolicitudDeEmergencia s) {
        return new SolicitudDeEmergenciaJpaEntity(s.id(), s.aprendizId().value(), s.queOcurrio(),
                s.diaPedido() == null ? null : s.diaPedido().shortValue(), (short) s.diaAlPedir(), s.estado().name(), s.creadaEn(), s.resueltaEn(),
                s.resueltaPor() == null ? null : s.resueltaPor().value(),
                s.diaAplicado() == null ? null : s.diaAplicado().shortValue());
    }
}
