package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
class LeccionPersistenceAdapter implements LoadLeccionPort {

    private final SpringDataLeccionRepository repository;
    private final LeccionPersistenceMapper mapper;

    LeccionPersistenceAdapter(SpringDataLeccionRepository repository, LeccionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Leccion> byId(LeccionId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Leccion> porCurso(CursoId cursoId) {
        return repository.findByCursoIdOrderByOrdenAsc(cursoId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Map<CursoId, Integer> contarTotalPorCurso() {
        Map<CursoId, Integer> resultado = new HashMap<>();
        for (Object[] fila : repository.contarPorCurso()) {
            resultado.put(CursoId.of((String) fila[0]), ((Number) fila[1]).intValue());
        }
        return resultado;
    }
}
