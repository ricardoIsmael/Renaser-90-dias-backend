package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conversacion.ConsultarSituacionDelTurnoUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.EstadoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoPausado;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Arma la situacion del turno: dia y fase del puerto de siempre, mas el estado de los habitos de hoy
 * (D-176). Usa los MISMOS puertos que las herramientas ({@code consultar_habitos_del_dia} y
 * {@code consultar_habitos_obligatorios}), asi que el prompt y las herramientas no pueden decir
 * cosas distintas del mismo habito: "hoy" lo resuelve {@code habits} en la zona de la persona.
 *
 * <p><b>Nunca rompe el turno.</b> Si los habitos no se pueden leer, la situacion sale sin ellos
 * ({@code habitos == null}), el prompt lo dice y el modelo vuelve a las herramientas. Sin
 * {@code @Transactional}: son dos lecturas cortas ANTES de hablar con el modelo (C-1).
 */
@Service
public class SituacionDelTurnoService implements ConsultarSituacionDelTurnoUseCase {

    private static final Logger log = LoggerFactory.getLogger(SituacionDelTurnoService.class);

    private final ConsultarSituacionDelAprendizPort situacionPort;
    private final ConsultarAgendaHabitosPort agendaPort;
    private final GestionarPlanDeHabitosPort planPort;
    private final Clock clock;

    public SituacionDelTurnoService(ConsultarSituacionDelAprendizPort situacionPort,
                                    ConsultarAgendaHabitosPort agendaPort, GestionarPlanDeHabitosPort planPort,
                                    Clock clock) {
        this.situacionPort = situacionPort;
        this.agendaPort = agendaPort;
        this.planPort = planPort;
        this.clock = clock;
    }

    @Override
    public Optional<SituacionDelAprendiz> de(UserId participanteId) {
        return situacionPort.de(participanteId).map(situacion -> conHabitosSiSePuede(situacion, participanteId));
    }

    private SituacionDelAprendiz conHabitosSiSePuede(SituacionDelAprendiz situacion, UserId participanteId) {
        try {
            return situacion.conHabitos(new HabitosDeHoy(deHoy(participanteId), pausados(participanteId)));
        } catch (RuntimeException falla) {
            log.warn("[rag] la situacion del turno sale sin los habitos de hoy ({})", falla.getClass().getSimpleName());
            return situacion;
        }
    }

    private List<HabitoDeHoy> deHoy(UserId participanteId) {
        Instant ahora = clock.now();
        return agendaPort.deHoyDe(participanteId).stream()
                .map(habito -> new HabitoDeHoy(habito.titulo(), estadoDe(habito, ahora), habito.seRegistraConFoto(),
                        habito.tituloDelPrograma()))
                .toList();
    }

    private List<HabitoPausado> pausados(UserId participanteId) {
        return planPort.planDe(participanteId).habitos().stream()
                .filter(HabitoDelPlan::pausadoHoy)
                .map(habito -> new HabitoPausado(habito.titulo(), habito.pausadoHasta()))
                .toList();
    }

    /**
     * Mismo criterio que {@code consultar_habitos_del_dia}: un pendiente con el plazo cumplido ya
     * vencio, aunque el barrido todavia no lo haya marcado EXPIRADO. Un estado que no se conoce se
     * dice pendiente, que es lo que obliga a consultar antes de darlo por hecho.
     */
    static EstadoDeHoy estadoDe(HabitoDelDia habito, Instant ahora) {
        return switch (habito.estado()) {
            case "COMPLETADO" -> EstadoDeHoy.HECHO;
            case "EXPIRADO" -> EstadoDeHoy.VENCIDO;
            case "FALLIDO" -> EstadoDeHoy.FALLIDO;
            default -> sinEntregar(habito, ahora);
        };
    }

    private static EstadoDeHoy sinEntregar(HabitoDelDia habito, Instant ahora) {
        if (habito.plazo() != null && !habito.plazo().isAfter(ahora)) {
            return EstadoDeHoy.VENCIDO;
        }
        return "EN_CURSO".equals(habito.estado()) ? EstadoDeHoy.EN_CURSO : EstadoDeHoy.PENDIENTE;
    }
}
