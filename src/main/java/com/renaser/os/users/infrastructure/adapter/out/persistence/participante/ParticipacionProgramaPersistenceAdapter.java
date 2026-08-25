package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.participante.DeleteParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class ParticipacionProgramaPersistenceAdapter implements LoadParticipacionProgramaPort, SaveParticipacionProgramaPort,
        DeleteParticipacionProgramaPort {

    private final SpringDataParticipacionProgramaRepository repository;
    private final ParticipacionProgramaPersistenceMapper mapper;

    ParticipacionProgramaPersistenceAdapter(SpringDataParticipacionProgramaRepository repository,
                                             ParticipacionProgramaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ParticipacionPrograma> byParticipanteId(UserId participanteId) {
        return repository.findById(participanteId.value()).map(mapper::toDomain);
    }

    @Override
    public ParticipacionPrograma save(ParticipacionPrograma participacion) {
        var saved = repository.save(mapper.toEntity(participacion));
        return mapper.toDomain(saved);
    }

    @Override
    public boolean deleteByParticipanteId(UserId participanteId) {
        if (!repository.existsById(participanteId.value())) {
            return false;
        }
        repository.deleteById(participanteId.value());
        return true;
    }
}
