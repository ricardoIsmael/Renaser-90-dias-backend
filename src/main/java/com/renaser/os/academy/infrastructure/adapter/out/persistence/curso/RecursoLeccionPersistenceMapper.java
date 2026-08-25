package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.RecursoLeccion;
import org.springframework.stereotype.Component;

@Component
class RecursoLeccionPersistenceMapper {

    RecursoLeccion toDomain(RecursoLeccionJpaEntity e) {
        return new RecursoLeccion(e.getId(), LeccionId.of(e.getLeccionId()), e.getNombre(), e.getUrl(),
                e.getOrden());
    }
}
