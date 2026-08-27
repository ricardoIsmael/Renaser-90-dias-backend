package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import com.renaser.os.habits.application.ports.out.adjunto.LoadAdjuntoGuiaPort;
import com.renaser.os.habits.application.ports.out.adjunto.SaveAdjuntoGuiaPort;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class AdjuntoGuiaPersistenceAdapter implements LoadAdjuntoGuiaPort, SaveAdjuntoGuiaPort {

    private final SpringDataAdjuntoGuiaRepository repository;
    private final AdjuntoGuiaPersistenceMapper mapper;

    AdjuntoGuiaPersistenceAdapter(SpringDataAdjuntoGuiaRepository repository, AdjuntoGuiaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<AdjuntoGuia> porGuias(Collection<GuiaHabitoId> guiaIds) {
        if (guiaIds.isEmpty()) {
            return List.of();
        }
        List<UUID> valores = guiaIds.stream().map(GuiaHabitoId::value).toList();
        return repository.findByGuiaIdIn(valores).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AdjuntoGuia> byId(AdjuntoGuiaId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public AdjuntoGuia save(AdjuntoGuia adjunto) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(adjunto)));
    }

    @Override
    public void eliminar(AdjuntoGuiaId id) {
        repository.deleteById(id.value());
    }
}
