package com.renaser.os.habits.infrastructure.adapter.out.persistence.horario;

import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
class HorarioHabitoPersistenceAdapter implements LoadHorarioHabitoPort, SaveHorarioHabitoPort {

    private final SpringDataHorarioHabitoRepository repository;
    private final HorarioHabitoPersistenceMapper mapper;

    HorarioHabitoPersistenceAdapter(SpringDataHorarioHabitoRepository repository,
                                     HorarioHabitoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<HorarioHabito> porHabito(HabitoId habitoId) {
        return repository.findByHabitoId(habitoId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<HorarioHabito> porHabitos(Collection<HabitoId> habitoIds) {
        if (habitoIds.isEmpty()) {
            return List.of();
        }
        List<UUID> valores = habitoIds.stream().map(HabitoId::value).toList();
        return repository.findByHabitoIdIn(valores).stream().map(mapper::toDomain).toList();
    }

    @Override
    public HorarioHabito save(HorarioHabito horario) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(horario)));
    }
}
