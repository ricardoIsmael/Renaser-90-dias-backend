package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.application.ports.in.ConsultarDiasDelAprendizUseCase;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.DiasDelSemaforoFinder;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Los colores de los días de un aprendiz para su mentor (spec 055). Autoriza primero (acompañar hoy el grupo y que el
 * aprendiz sea de él) y lee después; los días salen del semáforo de {@code points} tal cual, sin una regla nueva.
 *
 * <p>El rango se recorta a [Día 1, hoy] en la zona del APRENDIZ (regla 02): el mentor no ve nada antes de que empezara su
 * programa ni días por venir, y los días fuera del programa no se devuelven.
 */
@Service
public class DiasDelAprendizService implements ConsultarDiasDelAprendizUseCase {

    private final AccesoAVistasDelSemaforo acceso;
    private final DiasDelSemaforoFinder semaforo;
    private final ParticipacionProgramaFinder participaciones;
    private final Clock clock;

    DiasDelAprendizService(AccesoAVistasDelSemaforo acceso, DiasDelSemaforoFinder semaforo,
                           ParticipacionProgramaFinder participaciones, Clock clock) {
        this.acceso = acceso;
        this.semaforo = semaforo;
        this.participaciones = participaciones;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public DiasDelAprendiz diasDe(ConsultaDiasDelAprendiz consulta) {
        Instant ahora = clock.now();
        UserId aprendiz = UserId.of(consulta.aprendizId());
        acceso.requireAcompananteVigente(consulta.actorId(), consulta.grupoId(), ahora);
        acceso.requireAprendizDelGrupo(aprendiz, consulta.grupoId(), ahora);
        exigirUnRangoValido(consulta.desde(), consulta.hasta());
        ParticipacionPrograma participacion = participaciones.deParticipante(aprendiz)
                .orElseThrow(() -> new NoSuchElementException("Aprendiz no encontrado"));
        LocalDate diaUno = participacion.diaUnoElegido();
        String zona = participacion.zona().getId();
        if (diaUno == null) {
            return new DiasDelAprendiz(zona, null, List.of());
        }
        LocalDate hoy = ahora.atZone(participacion.zona()).toLocalDate();
        LocalDate desde = consulta.desde().isBefore(diaUno) ? diaUno : consulta.desde();
        LocalDate hasta = consulta.hasta().isAfter(hoy) ? hoy : consulta.hasta();
        if (desde.isAfter(hasta)) {
            return new DiasDelAprendiz(zona, diaUno, List.of());
        }
        Optional<List<DiaDelSemaforo>> dias = semaforo.diasDe(aprendiz, desde, hasta);
        return new DiasDelAprendiz(zona, diaUno, dias.orElse(List.of()).stream()
                .filter(dia -> dia.estado() != EstadoDiaSemaforo.FUERA_DEL_PROGRAMA).toList());
    }

    private static void exigirUnRangoValido(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS) {
            throw RechazoConCodigoException.invalido("RANGO_INVALIDO",
                    "Pide entre 1 y " + DIAS_MAXIMOS + " días, y que «desde» no sea posterior a «hasta».");
        }
    }
}
