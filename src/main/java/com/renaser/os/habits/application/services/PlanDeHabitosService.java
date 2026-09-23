package com.renaser.os.habits.application.services;

import com.renaser.os.habits.domain.model.eleccion.SemanaDeEleccion;

import com.renaser.os.habits.api.PlanDeHabitosPort;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase;
import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase.ElegirDiaSemanalCommand;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase;
import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase.HabitoConDias;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.eleccion.LoadEleccionDiaSemanalPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementa {@link PlanDeHabitosPort} (2026-09-23). Fachada delgada, mismo patron que
 * {@link HorarioDelDiaFinderService}: las escrituras son las de {@link CambiarEstadoHabitoDelPlanUseCase}
 * y {@link ElegirDiaSemanalUseCase}, con todas sus guardas, y la lectura junta lo que ya existe
 * ({@link ConsultarMisHabitosUseCase} para titulo y banderas del habito, los desbloqueos para la
 * pausa, la eleccion de la semana). Sin {@code @Transactional}: cada caso de uso trae la suya.
 *
 * <p><b>Pausado HOY</b> se pregunta a {@link DesbloqueoHabito#estaPausadoEl}, la misma regla que
 * usa el generador del dia; no se reconstruye desde {@code pausadoHasta}.
 *
 * <p>Los dias elegibles salen de {@link SemanaDeEleccion}, la misma regla que hace cumplir
 * {@code EleccionDiaSemanalService} al elegir: no se le ofrece al aprendiz un boton que va a fallar.
 * El caso de uso la vuelve a correr al confirmar.
 */
@Service
public class PlanDeHabitosService implements PlanDeHabitosPort {

    private final ConsultarMisHabitosUseCase misHabitosUseCase;
    private final CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase;
    private final ElegirDiaSemanalUseCase elegirDiaUseCase;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    private final LoadEleccionDiaSemanalPort loadEleccionPort;
    private final Clock clock;

    public PlanDeHabitosService(ConsultarMisHabitosUseCase misHabitosUseCase,
                                CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase,
                                ElegirDiaSemanalUseCase elegirDiaUseCase,
                                ConsultarProgresoParticipanteHabitsPort progresoPort,
                                LoadDesbloqueoHabitoPort loadDesbloqueoPort,
                                LoadEleccionDiaSemanalPort loadEleccionPort, Clock clock) {
        this.misHabitosUseCase = misHabitosUseCase;
        this.cambiarEstadoUseCase = cambiarEstadoUseCase;
        this.elegirDiaUseCase = elegirDiaUseCase;
        this.progresoPort = progresoPort;
        this.loadDesbloqueoPort = loadDesbloqueoPort;
        this.loadEleccionPort = loadEleccionPort;
        this.clock = clock;
    }

    @Override
    public PlanDeHabitos planDe(UserId participanteId) {
        ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
        ZoneId zona = ZoneId.of(progreso.timezone());
        LocalDate hoy = clock.now().atZone(zona).toLocalDate();
        Map<HabitoId, Habito> visibles = misHabitosUseCase.consultar(participanteId).stream()
                .map(HabitoConDias::habito)
                .collect(Collectors.toMap(Habito::id, Function.identity(), (primero, repetido) -> primero,
                        LinkedHashMap::new));
        List<HabitoDelPlan> delPlan = loadDesbloqueoPort.deParticipante(participanteId).stream()
                .filter(desbloqueo -> visibles.containsKey(desbloqueo.habitoId()))
                .map(desbloqueo -> aHabitoDelPlan(visibles.get(desbloqueo.habitoId()), desbloqueo, hoy, zona))
                .toList();
        List<LocalDate> elegibles = SemanaDeEleccion.diasElegibles(progreso.diaPrograma(), hoy);
        List<HabitoSemanal> semanales = visibles.values().stream()
                .filter(Habito::eleccionDiaSemanal)
                .map(habito -> aHabitoSemanal(participanteId, habito, hoy, elegibles))
                .toList();
        return new PlanDeHabitos(hoy, delPlan, semanales);
    }

    @Override
    public void pausar(UserId actorId, UUID habitoId, LocalDate hastaInclusive) {
        cambiarEstadoUseCase.cambiarEstado(
                new CambiarEstadoHabitoCommand(actorId, HabitoId.of(habitoId), false, hastaInclusive));
    }

    @Override
    public void reactivar(UserId actorId, UUID habitoId) {
        cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoHabitoCommand(actorId, HabitoId.of(habitoId), true));
    }

    @Override
    public void elegirDiaSemanal(UserId actorId, UUID habitoId, LocalDate fecha) {
        elegirDiaUseCase.elegir(new ElegirDiaSemanalCommand(actorId, HabitoId.of(habitoId), fecha));
    }

    private static HabitoDelPlan aHabitoDelPlan(Habito habito, DesbloqueoHabito desbloqueo, LocalDate hoy,
                                                ZoneId zona) {
        return new HabitoDelPlan(habito.id().value(), habito.titulo(), !habito.desactivable(),
                desbloqueo.estaPausadoEl(hoy, zona), desbloqueo.pausadoHasta());
    }

    private HabitoSemanal aHabitoSemanal(UserId participanteId, Habito habito, LocalDate hoy,
                                         List<LocalDate> elegibles) {
        LocalDate diaElegido = loadEleccionPort.deHabitoEnSemana(participanteId, habito.id(),
                SemanaDeEleccion.lunesDe(hoy)).stream()
                .map(EleccionDiaSemanal::fechaEjecucion)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new HabitoSemanal(habito.id().value(), habito.titulo(), diaElegido, elegibles);
    }

    /** Ver la deuda del javadoc de la clase: espejo de la guarda de {@code EleccionDiaSemanalService}. */
    private ProgresoParticipanteHabits requireProgreso(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
