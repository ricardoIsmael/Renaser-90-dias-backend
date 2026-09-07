package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamensual;

import com.renaser.os.rocks.application.ports.out.rocamensual.GuardarRocaMensualPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class RocaMensualPersistenceAdapter implements LoadRocaMensualPort, GuardarRocaMensualPort {

    private final SpringDataRocaMensualRepository repository;
    private final RocaMensualPersistenceMapper mapper;

    RocaMensualPersistenceAdapter(SpringDataRocaMensualRepository repository, RocaMensualPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<RocaMensual> deParticipante(UserId participanteId) {
        return repository.deParticipante(participanteId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RocaMensual> deMaestraYMes(RocaMaestraId rocaMaestraId, int numeroMes) {
        return repository.findByRocaMaestraIdAndNumeroMes(rocaMaestraId.value(), (short) numeroMes)
                .map(mapper::toDomain);
    }

    @Override
    public RocaMensual guardar(RocaMensual rocaMensual) {
        return mapper.toDomain(repository.save(mapper.toEntity(rocaMensual)));
    }
}
