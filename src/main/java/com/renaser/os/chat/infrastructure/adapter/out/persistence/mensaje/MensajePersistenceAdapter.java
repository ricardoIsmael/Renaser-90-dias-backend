package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.mensaje.SaveMensajePort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class MensajePersistenceAdapter implements SaveMensajePort, LoadMensajePort {

    private final SpringDataMensajeRepository repository;
    private final MensajePersistenceMapper mapper;

    MensajePersistenceAdapter(SpringDataMensajeRepository repository, MensajePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Mensaje> porId(MensajeId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Mensaje> pagina(ConversacionId conversacionId, Instant cursor, int limite) {
        Pageable pageable = PageRequest.of(0, limite);
        List<MensajeJpaEntity> filas = cursor == null
                ? repository.paginaSinCursor(conversacionId.value(), pageable)
                : repository.paginaConCursor(conversacionId.value(), cursor, pageable);
        return filas.stream().map(mapper::toDomain).toList();
    }

    @Override
    public Map<ConversacionId, Mensaje> ultimosPorConversacion(List<ConversacionId> conversacionIds) {
        if (conversacionIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = conversacionIds.stream().map(ConversacionId::value).toList();
        Map<ConversacionId, Mensaje> resultado = new LinkedHashMap<>();
        for (MensajeJpaEntity fila : repository.ultimosPorConversacion(ids)) {
            Mensaje mensaje = mapper.toDomain(fila);
            resultado.put(mensaje.conversacionId(), mensaje);
        }
        return resultado;
    }

    @Override
    public Mensaje save(Mensaje mensaje) {
        var guardado = repository.saveAndFlush(mapper.toEntity(mensaje));
        return mapper.toDomain(guardado);
    }
}
