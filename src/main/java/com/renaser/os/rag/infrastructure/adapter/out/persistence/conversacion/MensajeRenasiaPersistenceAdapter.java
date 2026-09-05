package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.FuenteMensaje;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
class MensajeRenasiaPersistenceAdapter implements SaveMensajeRenasiaPort, LoadMensajeRenasiaPort {

    private final SpringDataMensajeRenasiaRepository repository;
    private final SpringDataFuenteMensajeRenasiaRepository fuenteRepository;
    private final MensajeRenasiaPersistenceMapper mapper;

    MensajeRenasiaPersistenceAdapter(SpringDataMensajeRenasiaRepository repository,
                                      SpringDataFuenteMensajeRenasiaRepository fuenteRepository,
                                      MensajeRenasiaPersistenceMapper mapper) {
        this.repository = repository;
        this.fuenteRepository = fuenteRepository;
        this.mapper = mapper;
    }

    /** D-102: la pagina es de UN agente; el WHERE lo aplica la consulta, nunca un filtro en memoria. */
    @Override
    public List<MensajeRenasia> pagina(UserId usuarioId, AgenteConversacional agente, Instant cursor, int limite) {
        Pageable pageable = PageRequest.of(0, limite);
        List<MensajeRenasiaJpaEntity> filas = cursor == null
                ? repository.paginaSinCursor(usuarioId.value(), agente.name(), pageable)
                : repository.paginaConCursor(usuarioId.value(), agente.name(), cursor, pageable);
        Map<UUID, List<String>> leccionIdsPorMensaje = leccionIdsPorMensaje(filas);
        return filas.stream()
                .map(fila -> mapper.toDomain(fila, leccionIdsPorMensaje.getOrDefault(fila.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public MensajeRenasia save(MensajeRenasia mensaje) {
        var guardado = repository.saveAndFlush(mapper.toEntity(mensaje));
        List<FuenteMensajeRenasiaJpaEntity> fuentes = mapper.toFuenteEntities(mensaje);
        if (!fuentes.isEmpty()) {
            fuenteRepository.saveAll(fuentes);
        }
        return mapper.toDomain(guardado, mensaje.fuentes().stream().map(FuenteMensaje::leccionId).toList());
    }

    /** EN UNA SOLA consulta las fuentes de toda la pagina (nunca N+1). */
    private Map<UUID, List<String>> leccionIdsPorMensaje(List<MensajeRenasiaJpaEntity> filas) {
        if (filas.isEmpty()) {
            return Map.of();
        }
        List<UUID> mensajeIds = filas.stream().map(MensajeRenasiaJpaEntity::getId).toList();
        Map<UUID, List<String>> resultado = new LinkedHashMap<>();
        for (FuenteMensajeRenasiaJpaEntity fuente : fuenteRepository.findByMensajeIdIn(mensajeIds)) {
            resultado.computeIfAbsent(fuente.getMensajeId(), k -> new ArrayList<>())
                    .add(fuente.getLeccionId());
        }
        return resultado;
    }
}
