package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.points.api.ResumenPuntajeFinder;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementa {@link ResumenPuntajeFinder} con los MISMOS dos caminos que usa
 * {@code HomeAgregadoService}: los puntos salen de {@link ConsultarPuntajeUseCase} pidiendo el
 * propio puntaje (actor == participante), y la racha de {@link RachaMostrada}. No hay una tercera
 * copia de ninguna de las dos reglas.
 */
@Service
class ResumenPuntajeService implements ResumenPuntajeFinder {

    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final PuntajeConRachaDerivada puntajeConRacha;

    ResumenPuntajeService(ConsultarPuntajeUseCase consultarPuntajeUseCase,
                          ParticipacionProgramaFinder participacionProgramaFinder,
                          DiasConHabitoCumplidoFinder diasConHabitoCumplidoFinder) {
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.puntajeConRacha = new PuntajeConRachaDerivada(consultarPuntajeUseCase, diasConHabitoCumplidoFinder);
    }

    @Override
    public Optional<ResumenPuntaje> de(UserId participanteId, Instant ahora) {
        return participacionProgramaFinder.deParticipante(participanteId)
                .map(participacion -> resumenDe(participanteId, participacion, ahora));
    }

    private ResumenPuntaje resumenDe(UserId participanteId, ParticipacionPrograma participacion, Instant ahora) {
        var visible = puntajeConRacha.de(participanteId, participanteId, participacion, ahora);
        return new ResumenPuntaje(visible.puntosLiga(), visible.rachaActual(), visible.rachaMaxima());
    }
}
