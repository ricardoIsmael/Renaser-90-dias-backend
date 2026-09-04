package com.renaser.os.users.infrastructure.adapter.out.persistence.ajustediaprograma;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.ajustediaprograma.LoadUltimoAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.ajustediaprograma.SaveAjusteDiaProgramaPort;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class AjusteDiaProgramaPersistenceAdapter implements SaveAjusteDiaProgramaPort, LoadUltimoAjusteDiaProgramaPort {

    private final SpringDataAjusteDiaProgramaRepository repository;
    private final AjusteDiaProgramaPersistenceMapper mapper;

    AjusteDiaProgramaPersistenceAdapter(SpringDataAjusteDiaProgramaRepository repository,
                                         AjusteDiaProgramaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AjusteDiaPrograma save(AjusteDiaPrograma ajuste) {
        return mapper.toDomain(repository.save(mapper.toEntity(ajuste)));
    }

    @Override
    public Optional<AjusteDiaPrograma> ultimoDe(UserId participanteId) {
        return repository.findFirstByParticipanteIdOrderByAjustadoEnDesc(participanteId.value())
                .map(mapper::toDomain);
    }
}
