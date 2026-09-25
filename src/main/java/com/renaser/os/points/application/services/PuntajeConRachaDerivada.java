package com.renaser.os.points.application.services;

import com.renaser.os.points.api.DiasConHabitoCumplidoFinder;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeVisibleUseCase.PuntajeVisible;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.points.domain.model.puntaje.Racha;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;

import java.time.Instant;

/**
 * El puntaje guardado con la racha DERIVADA encima (E-216, 2026-09-23).
 *
 * <p>Las columnas {@code racha_actual}/{@code racha_maxima} de {@code puntajes_participante} valen 0
 * para todo el mundo: las avanzaria {@code RegistrarCoherenciaDiariaUseCase}, que no tiene ningun
 * llamador. La racha de verdad se deriva de los dias con habito cumplido ({@link RachaMostrada}, la
 * misma regla que {@code GET /api/v1/home}). Esta clase junta las dos cosas en un solo lugar para
 * {@code GET /api/v1/points/{id}} ({@link PuntajeVisibleService}) y para el acompanante
 * ({@link ResumenPuntajeService}): antes cada uno armaba lo suyo y el endpoint mostraba 0.
 *
 * <p>Los chequeos de acceso (dueno o administrativo activo, cuenta suspendida) siguen siendo los de
 * {@link ConsultarPuntajeUseCase}: esta clase lo llama primero.
 */
final class PuntajeConRachaDerivada {

    private final ConsultarPuntajeUseCase consultarPuntajeUseCase;
    private final RachaMostrada rachaMostrada;

    PuntajeConRachaDerivada(ConsultarPuntajeUseCase consultarPuntajeUseCase,
                            DiasConHabitoCumplidoFinder diasConHabitoCumplidoFinder) {
        this.consultarPuntajeUseCase = consultarPuntajeUseCase;
        this.rachaMostrada = new RachaMostrada(diasConHabitoCumplidoFinder);
    }

    /** {@code participacion} {@code null} = no inscrito: sin racha, igual que un programa sin empezar. */
    PuntajeVisible de(UserId actorId, UserId participanteId, ParticipacionPrograma participacion, Instant ahora) {
        PuntajeParticipante guardado = consultarPuntajeUseCase.consultar(actorId, participanteId);
        Racha racha = participacion == null ? Racha.NINGUNA : rachaMostrada.de(participanteId, participacion, ahora);
        return new PuntajeVisible(participanteId, guardado.coherencia(), guardado.puntosLiga(), racha.actual(),
                racha.maxima());
    }
}
