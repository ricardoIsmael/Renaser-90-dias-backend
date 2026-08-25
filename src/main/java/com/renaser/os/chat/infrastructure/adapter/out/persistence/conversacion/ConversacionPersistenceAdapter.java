package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ConversacionPersistenceAdapter implements LoadConversacionPort, SaveConversacionPort {

    private final SpringDataConversacionRepository repository;
    private final SpringDataParticipanteConversacionRepository participanteRepository;
    private final ConversacionPersistenceMapper mapper;

    ConversacionPersistenceAdapter(SpringDataConversacionRepository repository,
                                    SpringDataParticipanteConversacionRepository participanteRepository,
                                    ConversacionPersistenceMapper mapper) {
        this.repository = repository;
        this.participanteRepository = participanteRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Conversacion> porId(ConversacionId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Conversacion> porClaveDirecta(String claveDirecta) {
        return repository.findByClaveDirecta(claveDirecta).map(mapper::toDomain);
    }

    @Override
    public Optional<Conversacion> porCelulaId(UUID celulaId) {
        return repository.findByCelulaId(celulaId).map(mapper::toDomain);
    }

    @Override
    public Optional<Conversacion> global() {
        return repository.findFirstByTipo(TipoConversacionJpa.GLOBAL).map(mapper::toDomain);
    }

    @Override
    public List<Conversacion> misConversaciones(UserId usuarioId) {
        List<UUID> ids = participanteRepository.conversacionIdsDeUsuario(usuarioId.value());
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByIdIn(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Conversacion save(Conversacion conversacion) {
        var guardada = repository.saveAndFlush(mapper.toEntity(conversacion));
        return mapper.toDomain(guardada);
    }
}
