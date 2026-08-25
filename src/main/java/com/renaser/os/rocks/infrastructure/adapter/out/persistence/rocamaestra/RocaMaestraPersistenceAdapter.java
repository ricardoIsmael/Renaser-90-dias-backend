package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra;

import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class RocaMaestraPersistenceAdapter implements LoadRocaMaestraPort {

    private final SpringDataRocaMaestraRepository repository;
    private final RocaMaestraPersistenceMapper mapper;

    RocaMaestraPersistenceAdapter(SpringDataRocaMaestraRepository repository, RocaMaestraPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<RocaMaestra> deParticipante(UserId participanteId) {
        return repository.findByParticipanteId(participanteId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RocaMaestra> deParticipanteYEje(UserId participanteId, EjeObjetivo eje) {
        return repository.findByParticipanteIdAndEje(participanteId.value(), mapper.toJpaEje(eje))
                .map(mapper::toDomain);
    }
}
