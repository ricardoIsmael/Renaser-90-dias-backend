package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

import com.renaser.os.habits.application.ports.out.santuario.LoadRachaSinCelularPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveRachaSinCelularPort;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelularId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class RachaSinCelularPersistenceAdapter implements LoadRachaSinCelularPort, SaveRachaSinCelularPort {

    private final SpringDataRachaSinCelularRepository repository;
    private final RachaSinCelularPersistenceMapper mapper;

    RachaSinCelularPersistenceAdapter(SpringDataRachaSinCelularRepository repository,
                                       RachaSinCelularPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<RachaSinCelular> byId(RachaSinCelularId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<RachaSinCelular> activaDe(UserId participanteId) {
        return repository.findByParticipanteIdAndEstado(participanteId.value(), EstadoRachaJpa.ACTIVA)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<RachaSinCelular> activaDeParaEscritura(UserId participanteId) {
        return repository.findActivaParaEscritura(participanteId.value(), EstadoRachaJpa.ACTIVA)
                .map(mapper::toDomain);
    }

    @Override
    public List<RachaSinCelular> activasDe(List<UserId> participanteIds) {
        List<java.util.UUID> ids = participanteIds.stream().map(UserId::value).toList();
        return repository.findByParticipanteIdInAndEstado(ids, EstadoRachaJpa.ACTIVA).stream().map(mapper::toDomain)
                .toList();
    }

    @Override
    public RachaSinCelular save(RachaSinCelular racha) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(racha)));
    }
}
