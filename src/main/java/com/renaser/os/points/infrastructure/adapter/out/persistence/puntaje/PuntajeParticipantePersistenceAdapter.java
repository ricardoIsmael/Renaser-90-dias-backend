package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import com.renaser.os.points.application.ports.out.puntaje.LoadPuntajePort;
import com.renaser.os.points.application.ports.out.puntaje.SavePuntajePort;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class PuntajeParticipantePersistenceAdapter implements LoadPuntajePort, SavePuntajePort {

    private final SpringDataPuntajeParticipanteRepository repository;
    private final PuntajeParticipantePersistenceMapper mapper;

    PuntajeParticipantePersistenceAdapter(SpringDataPuntajeParticipanteRepository repository,
                                           PuntajeParticipantePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PuntajeParticipante> byParticipanteId(UserId participanteId) {
        return repository.findById(participanteId.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<PuntajeParticipante> byParticipanteIdParaEscritura(UserId participanteId) {
        return repository.findByIdParaEscritura(participanteId.value()).map(mapper::toDomain);
    }

    @Override
    public PuntajeParticipante save(PuntajeParticipante puntaje) {
        var saved = repository.saveAndFlush(mapper.toEntity(puntaje));
        return mapper.toDomain(saved);
    }

    @Override
    public void crearFilaInicialSiFalta(PuntajeParticipante inicial) {
        repository.insertarInicialSiFalta(inicial.participanteId().value(), inicial.coherencia(),
                inicial.puntosLiga(), inicial.rachaActual(), inicial.rachaMaxima(), inicial.actualizadoEn());
    }
}
