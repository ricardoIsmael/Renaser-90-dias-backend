package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.AgendaDelDiaFinder;
import com.renaser.os.habits.api.HabitoEnJuegoResumen;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implementa {@link ConsultarAgendaHabitosPort} delegando en el contrato publico de
 * {@code habits} (D-41) — {@code rag} nunca consulta {@code registros_habito} de frente. Mismo
 * patron exacto que {@code LeerEntradasDiarioAdapter}.
 *
 * <p>Es una traduccion y nada mas: que dia es hoy para el aprendiz, cuanto paga cada habito y si
 * todavia se puede entregar lo resuelve {@code habits}, que es donde viven esas reglas.
 */
@Component
class ConsultarAgendaHabitosAdapter implements ConsultarAgendaHabitosPort {

    private final AgendaDelDiaFinder agendaDelDiaFinder;

    ConsultarAgendaHabitosAdapter(AgendaDelDiaFinder agendaDelDiaFinder) {
        this.agendaDelDiaFinder = agendaDelDiaFinder;
    }

    @Override
    public List<HabitoDelDia> deHoyDe(UserId participanteId) {
        return agendaDelDiaFinder.deHoyDe(participanteId).stream()
                .map(ConsultarAgendaHabitosAdapter::aHabitoDelDia)
                .toList();
    }

    @Override
    public int completar(UserId actorId, UUID registroId) {
        return agendaDelDiaFinder.completar(actorId, registroId);
    }

    private static HabitoDelDia aHabitoDelDia(HabitoEnJuegoResumen resumen) {
        return new HabitoDelDia(resumen.registroId(), resumen.titulo(), resumen.estado(), resumen.puntosEnJuego(),
                resumen.puntosMaximos(), resumen.plazo());
    }
}
