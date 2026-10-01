package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.Fuente;
import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor;
import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor.GrupoACargo;
import com.renaser.os.leadership.domain.model.atencion.AtencionDeTickets;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder.EvaluacionDeMentor;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.GrupoMedido;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.MedicionVigente;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder.FichaDeMentor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Arma los indicadores de cada mentor desde tres fuentes, cada una con su propio estado (SDD 002,
 * plan §5): grupos y semáforo ({@code mentoring}), atención de consultas ({@code support}) y evaluación
 * del mes ({@code mentoring}, motor único de {@code points}).
 *
 * <p><b>Una fuente que cae no vacía la respuesta</b> (RL-21): se marca como no disponible y el resto se
 * entrega. Por eso cada lectura va en su propio {@link #leer}.
 *
 * <p>Una lectura de grupos y una de tickets para todos los mentores; la evaluación sí es una por mentor,
 * porque el motor de {@code points} se arma desde los tramos de cada uno (es lo mismo que pide el
 * mentor para sí). El cuerpo de mentores es chico; si crece, ver D-241.
 */
@Component
class IndicadoresDeMentores {

    private static final Logger log = LoggerFactory.getLogger(IndicadoresDeMentores.class);

    private final MedicionDeMentoresFinder medicionFinder;
    private final EvaluacionDeMentorFinder evaluacionFinder;
    private final AtencionDeConsultas atencion;

    IndicadoresDeMentores(MedicionDeMentoresFinder medicionFinder, EvaluacionDeMentorFinder evaluacionFinder,
                          AtencionDeConsultas atencion) {
        this.medicionFinder = medicionFinder;
        this.evaluacionFinder = evaluacionFinder;
        this.atencion = atencion;
    }

    Lectura leer(List<FichaDeMentor> mentores, MesDelReporte mes) {
        Fuente<MedicionVigente> medicion = leer("los grupos y su semaforo", medicionFinder::vigente);
        Map<UserId, List<GrupoMedido>> gruposPorMentor = medicion.disponible()
                ? medicion.valor().grupos().stream().collect(Collectors.groupingBy(GrupoMedido::mentorId))
                : null;
        Map<UserId, Fuente<AtencionDeTickets>> atenciones = atencion.porMentor(mentores, gruposPorMentor, mes);
        List<IndicadoresDeMentor> filas = mentores.stream()
                .map(m -> indicadores(m, medicion, gruposPorMentor, atenciones.get(m.id()), mes))
                .toList();
        return new Lectura(medicion.valor(), filas);
    }

    private IndicadoresDeMentor indicadores(FichaDeMentor mentor, Fuente<MedicionVigente> medicion,
                                            Map<UserId, List<GrupoMedido>> gruposPorMentor,
                                            Fuente<AtencionDeTickets> deTickets, MesDelReporte mes) {
        Fuente<EvaluacionDeMentor> evaluacion =
                leer("la evaluacion del mentor", () -> evaluacionFinder.evaluacionDe(mentor.id(), mes.mes()));
        if (!medicion.disponible()) {
            return new IndicadoresDeMentor(mentor.id(), mentor.nombreCompleto(), mentor.avatarUrl(),
                    Fuente.noDisponible(), null, Fuente.noDisponible(), deTickets, evaluacion);
        }
        List<GrupoMedido> suyos = gruposPorMentor.getOrDefault(mentor.id(), List.of());
        return new IndicadoresDeMentor(mentor.id(), mentor.nombreCompleto(), mentor.avatarUrl(),
                Fuente.de(suyos.stream().map(IndicadoresDeMentores::aGrupo).toList()), aprendicesDe(suyos).size(),
                Fuente.de(medicion.valor().porMentor().get(mentor.id())), deTickets, evaluacion);
    }

    private static GrupoACargo aGrupo(GrupoMedido grupo) {
        return new GrupoACargo(grupo.grupoId(), grupo.nombre(), grupo.aprendices().size(), grupo.semaforo());
    }

    /** Sin repetir a quien está en dos de sus grupos (D-139). */
    static Set<UserId> aprendicesDe(List<GrupoMedido> grupos) {
        return grupos.stream().flatMap(g -> g.aprendices().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static <T> Fuente<T> leer(String que, Supplier<T> lectura) {
        try {
            return Fuente.de(lectura.get());
        } catch (RuntimeException e) {
            log.warn("[leadership] no se pudo leer {}: {}", que, e.toString());
            return Fuente.noDisponible();
        }
    }

    /** @param medicion null si la medición de grupos no respondió */
    record Lectura(MedicionVigente medicion, List<IndicadoresDeMentor> indicadores) {
    }
}
