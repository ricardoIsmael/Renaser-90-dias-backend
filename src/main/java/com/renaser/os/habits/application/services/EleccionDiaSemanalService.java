package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.eleccion.ElegirDiaSemanalUseCase;
import com.renaser.os.habits.application.ports.out.eleccion.SaveEleccionDiaSemanalPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.domain.model.eleccion.EleccionDiaSemanal;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

/**
 * "Elige tu dia de la semana" (tabla `dias_semanales_habito`) — traduccion simplificada de
 * {@code chooseWeeklyHabitDay} (repo viejo). Ver javadoc de {@link ElegirDiaSemanalUseCase}
 * para lo que quedo simplificado.
 */
@Service
public class EleccionDiaSemanalService implements ElegirDiaSemanalUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final SaveEleccionDiaSemanalPort savePort;
    private final Clock clock;

    public EleccionDiaSemanalService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                      LoadHabitoPort loadHabitoPort, SaveEleccionDiaSemanalPort savePort,
                                      Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.savePort = savePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EleccionDiaSemanal elegir(ElegirDiaSemanalCommand command) {
        ProgresoParticipanteHabits progreso = requireProgreso(command.actorId());
        if (progreso.diaPrograma() == 0) {
            throw new NotAuthorizedException(
                    "El Dia 0 es una vista previa — podras elegir tu dia a partir del Dia 1");
        }
        Habito habito = requireHabito(command.habitoId());
        if (!habito.eleccionDiaSemanal()) {
            throw new IllegalArgumentException("Este habito no se elige por dia de la semana");
        }
        if (!habito.activo()) {
            throw new IllegalArgumentException("Este habito no esta activo");
        }

        ZoneId zona = ZoneId.of(progreso.timezone());
        Instant ahora = clock.now();
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        LocalDate semanaInicio = lunesDe(hoy);

        if (command.fechaElegida().isBefore(hoy) || command.fechaElegida().isAfter(semanaInicio.plusDays(6))) {
            throw new IllegalArgumentException("Elige un dia de esta semana que no haya pasado todavia");
        }

        savePort.borrarDeSemana(command.actorId(), command.habitoId(), semanaInicio);
        EleccionDiaSemanal eleccion = EleccionDiaSemanal.elegir(command.actorId(), command.habitoId(),
                command.fechaElegida(), semanaInicio, ahora);
        return savePort.save(eleccion);
    }

    /** WEEK_ANCHOR=MONDAY (weeklyChoice.ts) — el lunes de la semana de calendario de {@code fecha}. */
    private static LocalDate lunesDe(LocalDate fecha) {
        return fecha.minusDays(fecha.getDayOfWeek().getValue() - 1);
    }

    private Habito requireHabito(HabitoId id) {
        return loadHabitoPort.byId(id).orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + id));
    }

    private ProgresoParticipanteHabits requireProgreso(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
