package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.application.ports.out.preferencia.LoadCambioHorarioPendientePort;
import com.renaser.os.habits.application.ports.out.preferencia.SaveCambioHorarioPendientePort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class CambioHorarioPendientePersistenceAdapter
        implements LoadCambioHorarioPendientePort, SaveCambioHorarioPendientePort {

    private final SpringDataCambioHorarioPendienteRepository repository;
    private final CambioHorarioPendientePersistenceMapper mapper;

    CambioHorarioPendientePersistenceAdapter(SpringDataCambioHorarioPendienteRepository repository,
                                              CambioHorarioPendientePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<CambioHorarioPendiente> porParticipanteYHabito(UserId participanteId, HabitoId habitoId) {
        return repository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value())
                .map(mapper::toDomain);
    }

    @Override
    public CambioHorarioPendiente save(CambioHorarioPendiente cambio) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(cambio)));
    }

    @Override
    public void borrar(UserId participanteId, HabitoId habitoId) {
        repository.deleteByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value());
    }
}
