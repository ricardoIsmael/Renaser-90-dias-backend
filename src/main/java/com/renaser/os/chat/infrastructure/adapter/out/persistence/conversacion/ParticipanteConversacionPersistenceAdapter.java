package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class ParticipanteConversacionPersistenceAdapter
        implements AgregarParticipantePort, EsParticipantePort, MarcarLeidoPort, ContarNoLeidosPort {

    private final SpringDataParticipanteConversacionRepository repository;

    ParticipanteConversacionPersistenceAdapter(SpringDataParticipanteConversacionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void agregar(Participante participante) {
        UUID conversacionId = participante.conversacionId().value();
        UUID usuarioId = participante.usuarioId().value();
        if (repository.existsByConversacionIdAndUsuarioId(conversacionId, usuarioId)) {
            return;
        }
        repository.save(new ParticipanteConversacionJpaEntity(conversacionId, usuarioId,
                participante.ultimoLeidoEn(), participante.creadoEn()));
    }

    @Override
    public boolean esParticipante(ConversacionId conversacionId, UserId usuarioId) {
        return repository.existsByConversacionIdAndUsuarioId(conversacionId.value(), usuarioId.value());
    }

    @Override
    @Transactional
    public void marcarLeido(ConversacionId conversacionId, UserId usuarioId, Instant ahora) {
        repository.findById(new ParticipanteConversacionId(conversacionId.value(), usuarioId.value()))
                .ifPresent(entidad -> {
                    entidad.setUltimoLeidoEn(ahora);
                    repository.save(entidad);
                });
    }

    @Override
    public Map<ConversacionId, Long> contarNoLeidos(UserId usuarioId, List<ConversacionId> conversacionIds) {
        if (conversacionIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = conversacionIds.stream().map(ConversacionId::value).toList();
        Map<ConversacionId, Long> resultado = new LinkedHashMap<>();
        for (var fila : repository.contarNoLeidos(usuarioId.value(), ids)) {
            resultado.put(ConversacionId.of(fila.getConversacionId()), fila.getConteo());
        }
        return resultado;
    }
}
