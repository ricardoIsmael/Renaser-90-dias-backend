package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.conversacion.ConsultarSituacionDelTurnoUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarTratoDeLaPersonaPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarTratoDeLaPersonaPort.TratoDeLaPersona;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.EstadoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoDeHoy;
import com.renaser.os.rag.application.ports.out.participante.HabitosDeHoy.HabitoPausado;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarCompuertaDeRocasPort;
import com.renaser.os.rag.domain.model.mapa.ResumenDelMapa;
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
    private final ConsultarTratoDeLaPersonaPort tratoPort;
    private final ConsultarMapaDeRenacimientoPort mapaPort;
    private final ConsultarCompuertaDeRocasPort compuertaPort;

    public SituacionDelTurnoService(ConsultarSituacionDelAprendizPort situacionPort,
                                    ConsultarAgendaHabitosPort agendaPort, GestionarPlanDeHabitosPort planPort,
                                    Clock clock, ConsultarTratoDeLaPersonaPort tratoPort,
                                    ConsultarMapaDeRenacimientoPort mapaPort,
                                    ConsultarCompuertaDeRocasPort compuertaPort) {
        this.situacionPort = situacionPort;
        this.agendaPort = agendaPort;
        this.planPort = planPort;
        this.clock = clock;
        this.tratoPort = tratoPort;
        this.mapaPort = mapaPort;
        this.compuertaPort = compuertaPort;
    }

    @Override
    public Optional<SituacionDelAprendiz> de(UserId participanteId) {
        return situacionPort.de(participanteId)
                .map(situacion -> conHabitosSiSePuede(situacion, participanteId))
                .map(situacion -> situacion.conTrato(tratoDe(participanteId)))
                .map(situacion -> situacion.conMapa(mapaDe(participanteId, situacion.diaPrograma())));
    }

    /**
     * D-233: la prioridad y el proximo hito de su Mapa, una linea. Si no se puede leer, {@code null}: el
     * prompt no dice nada del Mapa y el modelo, si lo necesita, llama a consultar_mi_mapa.
     */
    private ResumenDelMapa mapaDe(UserId participanteId, int diaPrograma) {
        try {
            return ResumenDelMapa.de(mapaPort.de(participanteId), diaPrograma)
                    .conRocasMaestras(rocasMaestrasDe(participanteId));
        } catch (RuntimeException falla) {
            log.warn("[rag] la situacion del turno sale sin el Mapa ({})", falla.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * D-247 (E-496): si {@code rocks} tiene sus Rocas Maestras, la llave para planificar objetivos. Sin ellas
     * la linea del Mapa lo dice y el modelo no propone planes que fallarian. {@code null} si no se pudo leer.
     */
    private Boolean rocasMaestrasDe(UserId participanteId) {
        try {
            return compuertaPort.rocasMaestrasCompletas(participanteId);
        } catch (RuntimeException falla) {
            log.warn("[rag] la situacion del turno sale sin saber de las Rocas Maestras ({})",
                    falla.getClass().getSimpleName());
            return null;
        }
    }

    /** E-457: sin dato, o si no se puede leer, neutro. Nunca se adivina el genero. */
    private TratoDeLaPersona tratoDe(UserId participanteId) {
        try {
            return tratoPort.de(participanteId);
        } catch (RuntimeException falla) {
            log.warn("[rag] la situacion del turno sale con trato neutro ({})", falla.getClass().getSimpleName());
            return TratoDeLaPersona.NEUTRO;
        }
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
