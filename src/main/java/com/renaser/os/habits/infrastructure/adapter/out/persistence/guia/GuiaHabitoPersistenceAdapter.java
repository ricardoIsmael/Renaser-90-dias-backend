package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
class GuiaHabitoPersistenceAdapter implements LoadGuiaHabitoPort {

    private final SpringDataGuiaHabitoRepository repository;
    private final GuiaHabitoPersistenceMapper mapper;

    GuiaHabitoPersistenceAdapter(SpringDataGuiaHabitoRepository repository, GuiaHabitoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<GuiaHabito> porHabitos(Collection<HabitoId> habitoIds) {
        if (habitoIds.isEmpty()) {
            return List.of();
        }
        List<UUID> valores = habitoIds.stream().map(HabitoId::value).toList();
        return repository.findByHabitoIdIn(valores).stream().map(mapper::toDomain).toList();
    }
}
