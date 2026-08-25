package com.renaser.os.rocks.infrastructure.adapter.out.persistence.verdugo;

import com.renaser.os.rocks.application.ports.out.verdugo.LoadEventoVerdugoPort;
import com.renaser.os.rocks.application.ports.out.verdugo.SaveEventoVerdugoPort;
import com.renaser.os.rocks.domain.model.verdugo.EventoVerdugo;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
class EventoVerdugoPersistenceAdapter implements LoadEventoVerdugoPort, SaveEventoVerdugoPort {

    private final SpringDataEventoVerdugoRepository repository;
    private final EventoVerdugoPersistenceMapper mapper;

    EventoVerdugoPersistenceAdapter(SpringDataEventoVerdugoRepository repository,
                                     EventoVerdugoPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<EventoVerdugo> deParticipante(UserId participanteId) {
        return repository.findByParticipanteIdOrderByDisparadoEnDesc(participanteId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<EventoVerdugo> pendientesDeFecha(LocalDate fecha) {
        // disparado_en es timestamptz; se acota por UTC — mismo criterio que el resto
        // de schedulers de este repo (SnapshotRankingScheduler, cron zone UTC).
        var desde = fecha.atStartOfDay(ZoneOffset.UTC).toInstant();
        var hasta = fecha.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return repository.findPendientesEntre(desde, hasta).stream().map(mapper::toDomain).toList();
    }

    @Override
    public EventoVerdugo save(EventoVerdugo evento) {
        var saved = repository.saveAndFlush(mapper.toEntity(evento));
        return mapper.toDomain(saved);
    }
}
