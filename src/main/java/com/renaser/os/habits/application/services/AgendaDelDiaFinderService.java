package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.AgendaDelDiaFinder;
import com.renaser.os.habits.api.HabitoEnJuegoResumen;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Implementa la cara publica de `habits` hacia otros modulos (hoy, las herramientas del agente
 * de `rag`). Mismo patron exacto que {@link HabitosDelDiaFinderService}: una fachada delgada
 * sobre casos de uso ya probados, sin consultas ni reglas propias.
 *
 * <p>Que sea delgado es el punto. Todo lo que importa — que dia es hoy para esa persona, cuanto
 * paga el habito, si todavia se puede entregar, si el actor es su dueno — ya esta resuelto y
 * probado en {@code ConsultarTracksDelDiaConCatalogoUseCase} y {@code CompletarRegistroUseCase}.
 * Si esta clase creciera con logica propia, seria una segunda copia de esas reglas.
 */
@Service
public class AgendaDelDiaFinderService implements AgendaDelDiaFinder {

    private final ConsultarTracksDelDiaConCatalogoUseCase consultarTracksUseCase;
    private final CompletarRegistroUseCase completarRegistroUseCase;

    public AgendaDelDiaFinderService(ConsultarTracksDelDiaConCatalogoUseCase consultarTracksUseCase,
                                      CompletarRegistroUseCase completarRegistroUseCase) {
        this.consultarTracksUseCase = consultarTracksUseCase;
        this.completarRegistroUseCase = completarRegistroUseCase;
    }

    @Override
    public List<HabitoEnJuegoResumen> deHoyDe(UserId participanteId) {
        return consultarTracksUseCase.consultarHoyDe(participanteId).stream()
                .map(AgendaDelDiaFinderService::resumirDe)
                .toList();
    }

    @Override
    public int completar(UserId actorId, UUID registroId) {
        return completarRegistroUseCase.completar(new CompletarRegistroCommand(actorId,
                RegistroHabitoId.of(registroId), null, null)).puntosOtorgados();
    }

    private static HabitoEnJuegoResumen resumirDe(TrackDelDiaConCatalogo vista) {
        PuntosEnJuego enJuego = vista.puntosEnJuego();
        return new HabitoEnJuegoResumen(vista.registro().id().value(), vista.tituloHabito(),
                vista.registro().estado().name(), enJuego != null ? enJuego.siCompletaAhora() : null,
                enJuego != null ? enJuego.maximo() : null, enJuego != null ? enJuego.plazo() : null);
    }
}
