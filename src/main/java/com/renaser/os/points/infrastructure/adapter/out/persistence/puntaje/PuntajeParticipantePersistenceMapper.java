package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class PuntajeParticipantePersistenceMapper {

    PuntajeParticipante toDomain(PuntajeParticipanteJpaEntity e) {
        return PuntajeParticipante.rehydrate(UserId.of(e.getParticipanteId()), e.getCoherencia(),
                e.getPuntosLiga(), e.getRachaActual(), e.getRachaMaxima(), e.getActualizadoEn());
    }

    PuntajeParticipanteJpaEntity toEntity(PuntajeParticipante p) {
        return new PuntajeParticipanteJpaEntity(p.participanteId().value(), p.coherencia(), p.puntosLiga(),
                (short) p.rachaActual(), (short) p.rachaMaxima(), p.actualizadoEn());
    }
}
