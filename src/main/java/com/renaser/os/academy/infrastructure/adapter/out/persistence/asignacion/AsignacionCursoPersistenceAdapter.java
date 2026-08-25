package com.renaser.os.academy.infrastructure.adapter.out.persistence.asignacion;

import com.renaser.os.academy.application.ports.out.asignacion.LoadAsignacionCursoPort;
import com.renaser.os.academy.domain.model.asignacion.AsignacionCurso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class AsignacionCursoPersistenceAdapter implements LoadAsignacionCursoPort {

    private final SpringDataAsignacionCursoRepository repository;
    private final AsignacionCursoPersistenceMapper mapper;

    AsignacionCursoPersistenceAdapter(SpringDataAsignacionCursoRepository repository,
                                       AsignacionCursoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<AsignacionCurso> porCurso(CursoId cursoId) {
        return repository.findByCursoId(cursoId.value()).stream().map(mapper::toDomain).toList();
    }
}
