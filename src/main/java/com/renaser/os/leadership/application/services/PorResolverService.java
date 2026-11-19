package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarPorResolverUseCase;
import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor;
import com.renaser.os.leadership.domain.model.atencion.AtencionDeTickets;
import com.renaser.os.leadership.domain.model.mentor.PeorEvaluacion;
import com.renaser.os.leadership.domain.model.mentor.PeorEvaluacion.MentorEvaluado;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.GrupoMedido;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketPendiente;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lo que el líder tiene que resolver hoy (spec 056, FR-002). Sin {@code @Transactional} a propósito, como el padrón: cada fuente abre la
 * suya y una que falla no arrastra a las demás (RL-21); la que cae se devuelve en null, nunca como «nada que resolver».
 *
 * <p>Las consultas son las abiertas de los aprendices de los grupos con mentor, sin repetir una consulta aunque su aprendiz esté en dos
 * grupos; la evaluación y el umbral son los del reporte del mes ({@link PeorEvaluacion}).
 */
@Service
public class PorResolverService implements ConsultarPorResolverUseCase {

    private final AccesoDeLiderazgo acceso;
    private final IndicadoresDeMentores indicadores;
    private final AcompanamientoFinder acompanamiento;
    private final AtencionDeTicketsFinder tickets;
    private final Clock clock;

    PorResolverService(AccesoDeLiderazgo acceso, IndicadoresDeMentores indicadores, AcompanamientoFinder acompanamiento,
                       AtencionDeTicketsFinder tickets, Clock clock) {
        this.acceso = acceso;
        this.indicadores = indicadores;
        this.acompanamiento = acompanamiento;
        this.tickets = tickets;
        this.clock = clock;
    }

    @Override
    public PorResolver porResolver(UserId actorId) {
        acceso.requireLiderazgoActivo(actorId);
        Instant ahora = clock.now();
        AlcanceDeFormacion alcance = acceso.alcanceDe(actorId);
        MesDelReporte mes = MesDelReporte.enCurso(MesDelReporte.ZONA_DEL_PROGRAMA, ahora);
        IndicadoresDeMentores.Lectura lectura = indicadores.leer(acceso.mentoresDelAlcance(actorId), mes, alcance);
        return new PorResolver(
                IndicadoresDeMentores.leer("los grupos sin mentor", () -> gruposSinMentor(alcance, ahora)).valor(),
                consultasSinResponder(lectura.medicion(), ahora),
                PeorEvaluacion.de(evaluados(lectura.indicadores())).orElse(null));
    }

    private int gruposSinMentor(AlcanceDeFormacion alcance, Instant ahora) {
        List<GrupoAcompanado> enCurso = acompanamiento.gruposRegularesEnCurso(ahora);
        return (int) enCurso.stream().filter(grupo -> grupo.mentorId() == null && alcance.incluye(grupo.formacion())).count();
    }

    /** @param medicion null si la medición de grupos no respondió: sin ella no se sabe de qué aprendices son las consultas */
    private ConsultasSinResponder consultasSinResponder(MedicionVigente medicion, Instant ahora) {
        if (medicion == null) {
            return null;
        }
        Set<UserId> aprendices = new HashSet<>();
        medicion.grupos().forEach(grupo -> aprendices.addAll(grupo.aprendices()));
        List<TicketPendiente> abiertas = IndicadoresDeMentores.leer("los tickets de mentoria", () -> tickets.pendientesDe(aprendices)).valor();
        if (abiertas == null) {
            return null;
        }
        Map<UUID, Instant> abiertasPorId = abiertas.stream()
                .collect(Collectors.toMap(TicketPendiente::ticketId, TicketPendiente::creadoEn, (a, b) -> a));
        AtencionDeTickets atencion = AtencionDeTickets.medir(abiertasPorId.values(), List.of(), ahora);
        return new ConsultasSinResponder(atencion.pendientes(), atencion.diasDelMasAntiguo());
    }

    /** La evaluación del mes de cada mentor; sin porcentaje la que no se pudo calcular o cuya fuente cayó. */
    private static List<MentorEvaluado> evaluados(List<IndicadoresDeMentor> mentores) {
        return mentores.stream()
                .map(mentor -> new MentorEvaluado(mentor.mentorId().value(), mentor.nombre(),
                        mentor.evaluacion().disponible() ? mentor.evaluacion().valor().porcentaje() : null))
                .collect(Collectors.toList());
    }
}
