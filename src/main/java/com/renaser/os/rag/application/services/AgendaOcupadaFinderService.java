package com.renaser.os.rag.application.services;

import com.renaser.os.rag.api.AgendaOcupadaFinder;
import com.renaser.os.rag.application.ports.out.agenda.AgendaSemanalPort;
import com.renaser.os.rag.domain.model.agenda.AgendaOcupada;
import com.renaser.os.shared.domain.TramoDelDia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implementa {@link AgendaOcupadaFinder} sobre el mismo puerto que usa el acompañante: una lectura, sin escribir ni
 * decidir nada. Traduce los tramos de la agenda a los del día compartido en {@code shared}.
 */
@Service
class AgendaOcupadaFinderService implements AgendaOcupadaFinder {

    private final AgendaSemanalPort agendaPort;

    AgendaOcupadaFinderService(AgendaSemanalPort agendaPort) {
        this.agendaPort = agendaPort;
    }

    @Override
    public Map<DayOfWeek, List<TramoDelDia>> semanaDe(UserId participanteId) {
        Map<DayOfWeek, List<TramoDelDia>> semana = new EnumMap<>(DayOfWeek.class);
        agendaPort.de(participanteId).dias().forEach((dia, agenda) -> semana.put(dia, tramosDe(agenda)));
        return Map.copyOf(semana);
    }

    private static List<TramoDelDia> tramosDe(AgendaOcupada agenda) {
        return agenda.ocupados().stream().map(tramo -> new TramoDelDia(tramo.desde(), tramo.hasta())).toList();
    }
}
