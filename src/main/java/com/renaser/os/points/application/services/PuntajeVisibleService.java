package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeVisibleUseCase;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;

/** {@code GET /api/v1/points/{id}} con la racha real (E-216). Ver {@link PuntajeConRachaDerivada}. */
@Service
class PuntajeVisibleService implements ConsultarPuntajeVisibleUseCase {

    private final PuntajeConRachaDerivada puntajeConRacha;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final Clock clock;

    PuntajeVisibleService(ConsultarPuntajeUseCase consultarPuntajeUseCase,
                          ParticipacionProgramaFinder participacionProgramaFinder,
                          DiasConHabitoCumplidoFinder diasConHabitoCumplidoFinder, Clock clock) {
        this.puntajeConRacha = new PuntajeConRachaDerivada(consultarPuntajeUseCase, diasConHabitoCumplidoFinder);
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.clock = clock;
    }

    @Override
    public PuntajeVisible consultar(UserId actorId, UserId participanteId) {
        return puntajeConRacha.de(actorId, participanteId,
                participacionProgramaFinder.deParticipante(participanteId).orElse(null), clock.now());
    }
}
