package com.renaser.os.habits.infrastructure.adapter.out.persistence.renombre;

import com.renaser.os.habits.application.ports.out.renombre.LoadRenombreHabitoPort;
import com.renaser.os.habits.application.ports.out.renombre.SaveRenombreHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class RenombreHabitoPersistenceAdapter implements LoadRenombreHabitoPort, SaveRenombreHabitoPort {

    private final SpringDataRenombreHabitoRepository repository;
    private final RenombreHabitoPersistenceMapper mapper;

    RenombreHabitoPersistenceAdapter(SpringDataRenombreHabitoRepository repository,
                                      RenombreHabitoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RenombreHabito> porParticipanteYHabito(UserId participanteId, HabitoId habitoId) {
        return repository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value())
                .map(mapper::toDomain);
    }

    @Override
    public RenombreHabito save(RenombreHabito renombre) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(renombre)));
    }

    @Override
    public void borrar(UserId participanteId, HabitoId habitoId) {
        repository.deleteByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value());
    }
}
