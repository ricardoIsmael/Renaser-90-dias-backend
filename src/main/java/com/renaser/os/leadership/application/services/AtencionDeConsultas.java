package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.Fuente;
import com.renaser.os.leadership.domain.model.atencion.AtencionDeTickets;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.GrupoMedido;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketPendiente;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketRespondido;
import com.renaser.os.users.api.FichaDeMentorFinder.FichaDeMentor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La atención de las consultas de cada mentor (SDD 002, RL-09).
 *
 * <ul>
 *   <li><b>Pendientes</b>: los tickets abiertos HOY de los aprendices de sus grupos. Es lo que le toca
 *       responder ahora, así que se mira el presente.</li>
 *   <li><b>Respondidas y tiempo</b>: las que ÉL respondió en el mes, por {@code respondido_por} (V87).
 *       No se deducen del mentor de hoy del aprendiz: si el aprendiz cambió de grupo, el trabajo sigue
 *       siendo de quien respondió (RL-10).</li>
 * </ul>
 *
 * Dos lecturas para todo el cuerpo de mentores, nunca una por mentor.
 */
@Component
class AtencionDeConsultas {

    private final AtencionDeTicketsFinder ticketsFinder;
    private final Clock clock;

    AtencionDeConsultas(AtencionDeTicketsFinder ticketsFinder, Clock clock) {
        this.ticketsFinder = ticketsFinder;
        this.clock = clock;
    }

    /** @param gruposPorMentor null si no se pudo saber qué grupos lleva cada uno: la atención tampoco se sabe */
    Map<UserId, Fuente<AtencionDeTickets>> porMentor(List<FichaDeMentor> mentores,
                                                    Map<UserId, List<GrupoMedido>> gruposPorMentor, MesDelReporte mes) {
        Map<UserId, Fuente<AtencionDeTickets>> porMentor = new HashMap<>();
        Fuente<Leidos> leidos = gruposPorMentor == null ? Fuente.noDisponible()
                : IndicadoresDeMentores.leer("los tickets de mentoria", () -> leer(mentores, gruposPorMentor, mes));
        Instant ahora = clock.now();
        for (FichaDeMentor mentor : mentores) {
            porMentor.put(mentor.id(), leidos.disponible()
                    ? Fuente.de(medir(leidos.valor(), mentor.id(),
                            IndicadoresDeMentores.aprendicesDe(gruposPorMentor.getOrDefault(mentor.id(), List.of())), ahora))
                    : Fuente.noDisponible());
        }
        return porMentor;
    }

    private Leidos leer(List<FichaDeMentor> mentores, Map<UserId, List<GrupoMedido>> gruposPorMentor, MesDelReporte mes) {
        List<UserId> aprendices = gruposPorMentor.values().stream()
                .flatMap(grupos -> IndicadoresDeMentores.aprendicesDe(grupos).stream())
                .distinct()
                .toList();
        return new Leidos(ticketsFinder.pendientesDe(aprendices),
                ticketsFinder.respondidosPor(mentores.stream().map(FichaDeMentor::id).toList(), mes.desde(), mes.hasta()));
    }

    private static AtencionDeTickets medir(Leidos leidos, UserId mentorId, Set<UserId> susAprendices, Instant ahora) {
        List<Instant> abiertos = leidos.pendientes().stream()
                .filter(t -> susAprendices.contains(t.participanteId()))
                .map(TicketPendiente::creadoEn)
                .toList();
        List<Duration> respuestas = leidos.respondidos().stream()
                .filter(t -> t.respondidoPor().equals(mentorId))
                .map(t -> Duration.between(t.creadoEn(), t.respondidoEn()))
                .toList();
        return AtencionDeTickets.medir(abiertos, respuestas, ahora);
    }

    private record Leidos(List<TicketPendiente> pendientes, List<TicketRespondido> respondidos) {
    }
}
