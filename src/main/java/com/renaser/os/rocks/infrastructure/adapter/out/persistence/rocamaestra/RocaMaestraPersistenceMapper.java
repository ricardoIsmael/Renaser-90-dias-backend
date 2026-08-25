package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RocaMaestraPersistenceMapper {

    RocaMaestra toDomain(RocaMaestraJpaEntity e) {
        return RocaMaestra.rehydrate(RocaMaestraId.of(e.getId()), UserId.of(e.getParticipanteId()),
                toDomainEje(e.getEje()), e.getObjetivo(), e.getCreadoEn());
    }

    RocaMaestraJpaEntity toEntity(RocaMaestra r) {
        return new RocaMaestraJpaEntity(r.id().value(), r.participanteId().value(), toJpaEje(r.eje()), r.objetivo(),
                r.creadoEn());
    }

    EjeObjetivoJpa toJpaEje(EjeObjetivo eje) {
        return switch (eje) {
            case CUERPO -> EjeObjetivoJpa.CUERPO;
            case TRABAJO -> EjeObjetivoJpa.TRABAJO;
            case RELACIONES -> EjeObjetivoJpa.RELACIONES;
        };
    }

    EjeObjetivo toDomainEje(EjeObjetivoJpa jpa) {
        return switch (jpa) {
            case CUERPO -> EjeObjetivo.CUERPO;
            case TRABAJO -> EjeObjetivo.TRABAJO;
            case RELACIONES -> EjeObjetivo.RELACIONES;
        };
    }
}
