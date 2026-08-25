package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.academy.application.ports.out.curso.LoadRecursoLeccionPort;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.RecursoLeccion;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class RecursoLeccionPersistenceAdapter implements LoadRecursoLeccionPort {

    private final SpringDataRecursoLeccionRepository repository;
    private final RecursoLeccionPersistenceMapper mapper;

    RecursoLeccionPersistenceAdapter(SpringDataRecursoLeccionRepository repository,
                                      RecursoLeccionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<RecursoLeccion> porLeccion(LeccionId leccionId) {
        return repository.findByLeccionIdOrderByOrdenAsc(leccionId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Map<LeccionId, Integer> contarPorLecciones(List<LeccionId> leccionIds) {
        if (leccionIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = leccionIds.stream().map(LeccionId::value).toList();
        Map<LeccionId, Integer> resultado = new HashMap<>();
        for (Object[] fila : repository.contarPorLecciones(ids)) {
            resultado.put(LeccionId.of((String) fila[0]), ((Number) fila[1]).intValue());
        }
        return resultado;
    }
}
