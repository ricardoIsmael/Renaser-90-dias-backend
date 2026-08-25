package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.application.ports.out.curso.LoadSeccionCursoPort;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class SeccionCursoPersistenceAdapter implements LoadSeccionCursoPort {

    private final SpringDataSeccionCursoRepository repository;
    private final SeccionCursoPersistenceMapper mapper;

    SeccionCursoPersistenceAdapter(SpringDataSeccionCursoRepository repository, SeccionCursoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<SeccionCurso> byId(SeccionCursoId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<SeccionCurso> porCurso(CursoId cursoId) {
        return repository.findByCursoIdOrderByOrdenAsc(cursoId.value()).stream().map(mapper::toDomain).toList();
    }
}
