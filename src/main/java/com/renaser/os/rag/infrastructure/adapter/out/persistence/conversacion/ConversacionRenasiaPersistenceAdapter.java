package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class ConversacionRenasiaPersistenceAdapter implements LoadConversacionRenasiaPort, SaveConversacionRenasiaPort {

    private final SpringDataConversacionRenasiaRepository repository;
    private final ConversacionRenasiaPersistenceMapper mapper;

    ConversacionRenasiaPersistenceAdapter(SpringDataConversacionRenasiaRepository repository,
                                           ConversacionRenasiaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ConversacionRenasia> porUsuarioId(UserId usuarioId) {
        return repository.findById(usuarioId.value()).map(mapper::toDomain);
    }

    @Override
    public ConversacionRenasia save(ConversacionRenasia conversacion) {
        var guardada = repository.saveAndFlush(mapper.toEntity(conversacion));
        return mapper.toDomain(guardada);
    }
}
