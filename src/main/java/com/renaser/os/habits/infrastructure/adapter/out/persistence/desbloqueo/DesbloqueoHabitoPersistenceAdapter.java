package com.renaser.os.habits.infrastructure.adapter.out.persistence.desbloqueo;

import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.desbloqueo.SaveDesbloqueoHabitoPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class DesbloqueoHabitoPersistenceAdapter implements LoadDesbloqueoHabitoPort, SaveDesbloqueoHabitoPort {

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

    @Override
    public Optional<DesbloqueoHabito> deParticipanteYHabito(UserId participanteId, HabitoId habitoId) {
        return repository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value())
                .map(mapper::toDomain);
    }

    @Override
    public void elegirSiFalta(UserId participanteId, HabitoId habitoId, int diaDesbloqueo, Instant elegidoEn,
                               Instant ahora) {
        repository.elegirSiFalta(participanteId.value(), habitoId.value(), diaDesbloqueo, elegidoEn, ahora);
    }
}
