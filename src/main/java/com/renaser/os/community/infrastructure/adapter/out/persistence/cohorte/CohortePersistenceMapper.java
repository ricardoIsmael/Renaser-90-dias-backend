package com.renaser.os.community.infrastructure.adapter.out.persistence.cohorte;

import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import org.springframework.stereotype.Component;

@Component
class CohortePersistenceMapper {

    Cohorte toDomain(CohorteJpaEntity e) {
        return Cohorte.rehydrate(CohorteId.of(e.getId()), e.getNombre(), e.getFechaInicio(), e.getFechaFin(),
                toDomainEstado(e.getEstado()), e.getCreadoEn(), e.getActualizadoEn());
    }

    CohorteJpaEntity toEntity(Cohorte c) {
        return new CohorteJpaEntity(c.id().value(), c.nombre(), c.fechaInicio(), c.fechaFin(),
                toJpaEstado(c.estado()), c.creadoEn(), c.actualizadoEn());
    }

    EstadoCohorteJpa toJpaEstado(EstadoCohorte estado) {
        return switch (estado) {
            case PLANIFICADA -> EstadoCohorteJpa.PLANIFICADA;
            case ACTIVA -> EstadoCohorteJpa.ACTIVA;
            case COMPLETADA -> EstadoCohorteJpa.COMPLETADA;
        };
    }

    private EstadoCohorte toDomainEstado(EstadoCohorteJpa jpa) {
        return switch (jpa) {
            case PLANIFICADA -> EstadoCohorte.PLANIFICADA;
            case ACTIVA -> EstadoCohorte.ACTIVA;
            case COMPLETADA -> EstadoCohorte.COMPLETADA;
        };
    }
}
