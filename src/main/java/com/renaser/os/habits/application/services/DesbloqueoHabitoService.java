package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/** Hueco #12 — ver javadoc de {@link ConsultarDesbloqueosHabitoUseCase} para el alcance (solo lectura, D-H2). */
@Service
public class DesbloqueoHabitoService implements ConsultarDesbloqueosHabitoUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadDesbloqueoHabitoPort loadPort;

    public DesbloqueoHabitoService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                    LoadDesbloqueoHabitoPort loadPort) {
        this.progresoPort = progresoPort;
        this.loadPort = loadPort;
    }

    @Override
    public PlanDesbloqueo consultar(UserId actorId) {
        requireProgreso(actorId);
        List<ItemDesbloqueo> items = loadPort.deParticipante(actorId).stream()
                .map(d -> new ItemDesbloqueo(d.habitoId(), d.diaDesbloqueo(), d.elegidoEn())).toList();
        return new PlanDesbloqueo(!items.isEmpty(), items);
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
