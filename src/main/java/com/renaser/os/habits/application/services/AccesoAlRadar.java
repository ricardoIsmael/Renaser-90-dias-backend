package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

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

    static final String MENSAJE_SIN_PROGRAMA = "Mi Radar empieza en tu día 8. Primero elige tu Día 1.";

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
        /* Sin fila de programa: el personal que nunca empezo el suyo (`POST /mentor/activate-tracking` o elegir su Dia 1). El actor sale
           de la sesion, asi que «no existe» solo puede ser eso: es el mismo motivo que un programa sin activar y la app lo lleva a
           elegir su Dia 1 (spec 019). Antes era un 404 que la app leia como «no hay Mi Radar». */
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NotAuthorizedException(MENSAJE_SIN_PROGRAMA, NotAuthorizedException.PROGRAMA_NO_ACTIVADO));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida", NotAuthorizedException.CUENTA_SUSPENDIDA);
        }
        /* E-169: la puerta no es el ROL, es tener el programa andando. `TRACK_PROGRAM_AS_STAFF`
           y `POST /api/v1/mentor/activate-tracking` existen para que el staff curse los 90 dias;
           preguntar solo por el rol dejaba la inscripcion construida y el uso prohibido.

           Se conserva la rama del rol en vez de reducirlo a `!programaActivado`: un TRAINEE recien
           aprobado tiene `programa_activado_en` en null hasta que termina primer login + Ficha +
           Terminos, y el cambio corto lo habria dejado fuera de su propio programa. Asi el cambio
           es ESTRICTAMENTE aditivo: nadie que hoy pase, deja de pasar. */
        if (progreso.rol() != RolParticipante.TRAINEE && !progreso.programaActivado()) {
            /* Spec 019: el 403 dice POR QUE (`PROGRAMA_NO_ACTIVADO`) para que la app lleve al personal a elegir su Dia 1 en vez de
               callar. El texto lo lee quien no conoce el codigo (un APK anterior). */
            throw new NotAuthorizedException(MENSAJE_SIN_PROGRAMA, NotAuthorizedException.PROGRAMA_NO_ACTIVADO);
        }
        return progreso;
    }
}
