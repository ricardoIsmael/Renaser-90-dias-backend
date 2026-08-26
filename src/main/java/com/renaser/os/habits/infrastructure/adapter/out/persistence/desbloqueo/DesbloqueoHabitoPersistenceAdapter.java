package com.renaser.os.habits.infrastructure.adapter.out.persistence.desbloqueo;

import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class DesbloqueoHabitoPersistenceAdapter implements LoadDesbloqueoHabitoPort {

    private final SpringDataDesbloqueoHabitoRepository repository;
    private final DesbloqueoHabitoPersistenceMapper mapper;

    DesbloqueoHabitoPersistenceAdapter(SpringDataDesbloqueoHabitoRepository repository,
                                        DesbloqueoHabitoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<DesbloqueoHabito> deParticipante(UserId participanteId) {
        return repository.findByParticipanteId(participanteId.value()).stream().map(mapper::toDomain).toList();
    }
}
