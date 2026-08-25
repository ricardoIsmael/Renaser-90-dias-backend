package com.renaser.os.academy.infrastructure.adapter.out.persistence.progreso;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class ProgresoLeccionPersistenceMapper {

    ProgresoLeccion toDomain(ProgresoLeccionJpaEntity e) {
        return new ProgresoLeccion(UserId.of(e.getUsuarioId()), LeccionId.of(e.getLeccionId()), e.getCompletadaEn());
    }

    ProgresoLeccionJpaEntity toEntity(ProgresoLeccion p) {
        return new ProgresoLeccionJpaEntity(p.usuarioId().value(), p.leccionId().value(), p.completadaEn());
    }
}
