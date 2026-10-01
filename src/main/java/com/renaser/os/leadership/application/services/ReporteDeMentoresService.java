package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase;
import com.renaser.os.leadership.application.ports.in.Fuente;
import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.leadership.domain.model.reporte.OrdenDelReporte;
import com.renaser.os.leadership.domain.model.reporte.OrdenDelReporte.Clasificacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder;
import com.renaser.os.users.api.FichaDeMentorFinder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * El reporte del cuerpo de mentores de un mes (SDD 002, RL-19..RL-23; D-241).
 *
 * <p>Sin instantáneas guardadas (propuesta PL-06): se calcula al pedirlo, con el corte a la vista. Los
 * mentores son los activos HOY, y los grupos y aprendices son los de hoy; lo que es del mes —atención,
 * evaluación, observaciones— es del mes pedido.
 */
@Service
public class ReporteDeMentoresService implements ConsultarReporteDeMentoresUseCase {

    private final AccesoDeLiderazgo acceso;
    private final FichaDeMentorFinder fichaDeMentorFinder;
    private final IndicadoresDeMentores indicadores;
    private final LeerObservacionesPort observaciones;
    private final AtencionDeTicketsFinder ticketsFinder;
    private final Clock clock;

    ReporteDeMentoresService(AccesoDeLiderazgo acceso, FichaDeMentorFinder fichaDeMentorFinder,
                             IndicadoresDeMentores indicadores, LeerObservacionesPort observaciones,
                             AtencionDeTicketsFinder ticketsFinder, Clock clock) {
        this.acceso = acceso;
        this.fichaDeMentorFinder = fichaDeMentorFinder;
        this.indicadores = indicadores;
        this.observaciones = observaciones;
        this.ticketsFinder = ticketsFinder;
        this.clock = clock;
    }

    @Override
    public ReporteDeMentores reporte(UserId actorId, String mesPedido) {
        acceso.requireLiderazgoActivo(actorId);
        Instant ahora = clock.now();
        MesDelReporte mes = MesDelReporte.pedido(mesPedido, MesDelReporte.ZONA_DEL_PROGRAMA, ahora);
        List<IndicadoresDeMentor> filas = indicadores.leer(fichaDeMentorFinder.mentoresActivos(), mes).indicadores();
        Fuente<Map<UserId, ConteoDeObservaciones>> conteos = IndicadoresDeMentores.leer("las observaciones del mes",
                () -> observaciones.conteoPorMentor(mes.desde(), mes.hasta()));
        Fuente<Integer> sinAtribucion = IndicadoresDeMentores.leer("las respuestas sin atribucion",
                () -> ticketsFinder.respondidosSinAtribucion(mes.desde(), mes.hasta()));

        Clasificacion<EntradaDelReporte> clasificacion = OrdenDelReporte.clasificar(
                filas.stream().map(fila -> new EntradaDelReporte(fila, conteoDe(conteos, fila.mentorId()))).toList(),
                ReporteDeMentoresService::porcentajeDe, entrada -> entrada.indicadores().nombre());
        return new ReporteDeMentores(mes.mes().toString(), mes.zona().getId(), mes.corte(), mes.cerrado(ahora),
                sinAtribucion.valor(), clasificacion.ordenados(), clasificacion.sinMuestra());
    }

    private static Fuente<ConteoDeObservaciones> conteoDe(Fuente<Map<UserId, ConteoDeObservaciones>> conteos,
                                                          UserId mentorId) {
        return conteos.disponible()
                ? Fuente.de(conteos.valor().getOrDefault(mentorId, ConteoDeObservaciones.NINGUNA))
                : Fuente.noDisponible();
    }

    /** null = sin muestra (evaluación no calculada o no disponible): queda fuera del orden (RL-23). */
    private static BigDecimal porcentajeDe(EntradaDelReporte entrada) {
        var evaluacion = entrada.indicadores().evaluacion();
        return evaluacion.disponible() ? evaluacion.valor().porcentaje() : null;
    }
}
