package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class PreferenciaHorarioPersistenceAdapter implements LoadPreferenciaHorarioPort, SavePreferenciaHorarioPort {

    private final SpringDataPreferenciaHorarioRepository repository;
    private final PreferenciaHorarioPersistenceMapper mapper;

    PreferenciaHorarioPersistenceAdapter(SpringDataPreferenciaHorarioRepository repository,
                                          PreferenciaHorarioPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PreferenciaHorario> porParticipanteYHabito(UserId participanteId, HabitoId habitoId) {
        return repository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value())
                .map(mapper::toDomain);
    }

    @Override
    public PreferenciaHorario save(PreferenciaHorario preferencia) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(preferencia)));
    }
}
