package com.renaser.os.habits.infrastructure.adapter.out.persistence.espiritu;

import com.renaser.os.habits.application.ports.out.espiritu.LoadRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.espiritu.SaveRegistroEspirituPort;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class RegistroEspirituPersistenceAdapter implements LoadRegistroEspirituPort, SaveRegistroEspirituPort {

    private final SpringDataRegistroEspirituRepository repository;
    private final RegistroEspirituPersistenceMapper mapper;

    RegistroEspirituPersistenceAdapter(SpringDataRegistroEspirituRepository repository,
                                        RegistroEspirituPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RegistroEspiritu> porParticipanteYDia(UserId participanteId, int dia) {
        return repository.findByParticipanteIdAndDia(participanteId.value(), (short) dia).map(mapper::toDomain);
    }

    @Override
    public Optional<RegistroEspiritu> ultimoDe(UserId participanteId) {
        return repository.findFirstByParticipanteIdOrderByDiaDesc(participanteId.value()).map(mapper::toDomain);
    }

    @Override
    public List<RegistroEspiritu> todosDe(UserId participanteId) {
        return repository.findByParticipanteId(participanteId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public RegistroEspiritu save(RegistroEspiritu registro) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(registro)));
    }
}
