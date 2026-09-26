package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementa {@link ConsultarHabitosVencidosPort} delegando en {@code habits.api.ObligacionesHistoricasFinder}
 * (D-41, D-177). Que es "vencido sin cumplir" lo decide {@code habits}
 * ({@code EstadoObligacion.vencidoSinCumplir}); aca solo se deja afuera lo que ese dia era opcional.
 *
 * <p>El finder no autoriza: quien llama es la propia persona hablando de si misma, con el
 * {@code actorId} de la conversacion autenticada.
 */
@Component
class ConsultarHabitosVencidosAdapter implements ConsultarHabitosVencidosPort {

    private final ObligacionesHistoricasFinder obligaciones;

    ConsultarHabitosVencidosAdapter(ObligacionesHistoricasFinder obligaciones) {
        this.obligaciones = obligaciones;
    }

    @Override
    public List<HabitoVencido> vencidosEntre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            return List.of();
        }
        return obligaciones.porParticipantesEntre(List.of(participanteId), desde, hasta).stream()
                .filter(o -> o.estado().vencidoSinCumplir() && !o.opcional())
                .map(ConsultarHabitosVencidosAdapter::aVencido)
                .toList();
    }

    private static HabitoVencido aVencido(ObligacionHabito obligacion) {
        return new HabitoVencido(obligacion.fecha(), obligacion.titulo());
    }
}
