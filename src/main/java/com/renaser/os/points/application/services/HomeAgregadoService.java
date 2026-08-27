package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.home.ConsultarResumenHomeUseCase;
import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeUseCase;
import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementa {@link ConsultarResumenHomeUseCase}. Reutiliza
 * {@link ConsultarPuntajeUseCase#consultar} pidiendo el propio puntaje del actor
 * (actorId == participanteId) — misma validacion de "cuenta activa" que ya tiene esa
 * consulta, sin duplicarla aca.
 */
@Service
public class HomeAgregadoService implements ConsultarResumenHomeUseCase {

    /**
     * Bloqueos documentados en vez de datos inventados — ver javadoc de
     * {@link ConsultarResumenHomeUseCase}. Cuando alguno de estos finders exista en el
     * modulo dueno, se agrega el campo real y se retira su entrada de esta lista.
     */
    static final List<String> BLOQUEOS = List.of(
            "habitosHoy: habits.api no expone un agregado de habitos del dia (gap #21, "
                    + "docs/PLAN_INTEGRACION_FRONTEND.md)",
            "proximoEventoCalendario: calendar.api solo publica RecordatorioEventoDebidoEvent, "
                    + "no un finder de proximo evento",
            "notificacionesNoLeidas: notifications todavia no declara paquete api (@NamedInterface)");

    private final ConsultarPuntajeUseCase consultarPuntajeUseCase;

    public HomeAgregadoService(ConsultarPuntajeUseCase consultarPuntajeUseCase) {
        this.consultarPuntajeUseCase = consultarPuntajeUseCase;
    }

    @Override
    public ResumenHome consultar(UserId actorId) {
        PuntajeParticipante puntaje = consultarPuntajeUseCase.consultar(actorId, actorId);
        return new ResumenHome(puntaje.puntosLiga(), puntaje.coherencia(), puntaje.rachaActual(),
                puntaje.rachaMaxima(), BLOQUEOS);
    }
}
