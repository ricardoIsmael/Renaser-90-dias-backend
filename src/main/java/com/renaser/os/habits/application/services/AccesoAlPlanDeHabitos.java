package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;

import java.util.NoSuchElementException;

/**
 * La puerta del plan de hábitos hecho sin red (spec 048, fase 3): sin participación, 404; cuenta suspendida, 403. Es la misma que cada
 * servicio de siempre ({@code requireProgreso}); se repite aquí para que la cuenta suspendida y la inexistente respondan antes de tocar el envío.
 */
final class AccesoAlPlanDeHabitos {

    private AccesoAlPlanDeHabitos() {
    }

    static ProgresoParticipanteHabits exigir(ConsultarProgresoParticipanteHabitsPort progresoPort, UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
