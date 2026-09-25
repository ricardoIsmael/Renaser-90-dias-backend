package com.renaser.os.habits.application.services;

import com.renaser.os.habits.domain.model.eleccion.SemanaDeEleccion;

import com.renaser.os.habits.api.PlanDeHabitosPort;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
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
 * {@link HorarioDelDiaFinderService}: las escrituras son las de {@link ElegirHabitoUseCase},
 * {@link CambiarEstadoHabitoDelPlanUseCase} y {@link ElegirDiaSemanalUseCase}, con todas sus guardas,
 * y la lectura junta lo que ya existe ({@link ConsultarMisHabitosUseCase} para titulo y banderas del
 * habito, los desbloqueos para la pausa, la eleccion de la semana). Sin {@code @Transactional}: cada
 * caso de uso trae la suya.
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
    private final ElegirHabitoUseCase elegirHabitoUseCase;
    private final CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase;
    private final ElegirDiaSemanalUseCase elegirDiaUseCase;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    private final LoadEleccionDiaSemanalPort loadEleccionPort;
    private final Clock clock;

    public PlanDeHabitosService(ConsultarMisHabitosUseCase misHabitosUseCase,
                                ElegirHabitoUseCase elegirHabitoUseCase,
                                CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase,
                                ElegirDiaSemanalUseCase elegirDiaUseCase,
                                ConsultarProgresoParticipanteHabitsPort progresoPort,
                                LoadDesbloqueoHabitoPort loadDesbloqueoPort,
                                LoadEleccionDiaSemanalPort loadEleccionPort, Clock clock) {
        this.misHabitosUseCase = misHabitosUseCase;
        this.elegirHabitoUseCase = elegirHabitoUseCase;
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
        Map<HabitoId, DesbloqueoHabito> desbloqueos = loadDesbloqueoPort.deParticipante(participanteId).stream()
                .collect(Collectors.toMap(DesbloqueoHabito::habitoId, Function.identity(), (primero, repetido) -> primero));
        List<HabitoDelPlan> habitos = visibles.values().stream()
                .map(habito -> aHabitoDelPlan(habito, desbloqueos.get(habito.id()), hoy, zona))
                .toList();
        List<LocalDate> elegibles = SemanaDeEleccion.diasElegibles(progreso.diaPrograma(), hoy);
        List<HabitoSemanal> semanales = visibles.values().stream()
                .filter(Habito::eleccionDiaSemanal)
                .map(habito -> aHabitoSemanal(participanteId, habito, hoy, elegibles))
                .toList();
        return new PlanDeHabitos(hoy, habitos, semanales);
    }

    /** Lo mismo que hace el interruptor de Plan (D-99): la fila primero, idempotente, y despues la pausa. */
    @Override
    public void pausar(UserId actorId, UUID habitoId, LocalDate hastaInclusive) {
        elegirHabitoUseCase.elegir(new ElegirHabitoCommand(actorId, HabitoId.of(habitoId), null));
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

    /** Sin fila en {@code desbloqueos_habito} no hay pausa: la tabla arranca vacia para todos (D-99). */
    private static HabitoDelPlan aHabitoDelPlan(Habito habito, DesbloqueoHabito desbloqueo, LocalDate hoy,
                                                ZoneId zona) {
        boolean pausadoHoy = desbloqueo != null && desbloqueo.estaPausadoEl(hoy, zona);
        LocalDate pausadoHasta = desbloqueo == null ? null : desbloqueo.pausadoHasta();
        return new HabitoDelPlan(habito.id().value(), habito.titulo(), !habito.desactivable(), pausadoHoy,
                pausadoHasta);
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
