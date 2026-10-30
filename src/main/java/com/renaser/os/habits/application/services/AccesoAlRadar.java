package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * La puerta del Codigo Renaser propio, una sola para el registro, el historial y Mi Radar (spec 012): la propia
 * persona y con el programa andando. Devuelve su progreso porque quien entra lo necesita (el dia del programa y la
 * zona deciden si puede registrar y a que hora).
 *
 * <p>Es la misma regla que antes vivia dentro de {@code RadarService}; se saco para que {@code MiRadarService} no la
 * copie. NO sirve a la lectura del acompanante: esa tiene su propia autorizacion (ARF-15).
 */
@Component
class AccesoAlRadar {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;

    AccesoAlRadar(ConsultarProgresoParticipanteHabitsPort progresoPort) {
        this.progresoPort = progresoPort;
    }

    ProgresoParticipanteHabits propio(UserId actorId, UserId participanteId) {
        requireSelf(actorId, participanteId);
        return requireParticipanteHabilitado(participanteId);
    }

    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede operar su Codigo Renaser");
        }
    }

    private ProgresoParticipanteHabits requireParticipanteHabilitado(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        /* E-169: la puerta no es el ROL, es tener el programa andando. `TRACK_PROGRAM_AS_STAFF`
           y `POST /api/v1/mentor/activate-tracking` existen para que el staff curse los 90 dias;
           preguntar solo por el rol dejaba la inscripcion construida y el uso prohibido.

           Se conserva la rama del rol en vez de reducirlo a `!programaActivado`: un TRAINEE recien
           aprobado tiene `programa_activado_en` en null hasta que termina primer login + Ficha +
           Terminos, y el cambio corto lo habria dejado fuera de su propio programa. Asi el cambio
           es ESTRICTAMENTE aditivo: nadie que hoy pase, deja de pasar. */
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            throw new NotAuthorizedException("El Codigo Renaser es exclusivo de aprendices");
        }
        return progreso;
    }
}
