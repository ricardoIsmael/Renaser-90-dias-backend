package com.renaser.os.calendar.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delega en el contrato publico de `users` (D-41).
 *
 * <p><b>Semantica preservada (CL-6):</b> NO se filtra por {@code inscrito}. El rol y el
 * estado existen para cualquier usuario, tenga o no fila de programa, y un ADMIN sin
 * inscripcion tiene que poder ver y administrar la agenda. Una version anterior de este
 * adaptador hacia INNER JOIN y por eso devolvia 404 a los administradores: el contrato
 * publico ya nace con esa leccion incorporada ({@code inscrito=false}, nunca vacio).
 */
@Component
class ConsultarProgresoParticipanteCalendarPersistenceAdapter implements ConsultarProgresoParticipanteCalendarPort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarProgresoParticipanteCalendarPersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public Optional<ProgresoParticipanteCalendar> deParticipante(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .map(ConsultarProgresoParticipanteCalendarPersistenceAdapter::aProgreso);
    }

    private static ProgresoParticipanteCalendar aProgreso(ParticipacionPrograma participacion) {
        return new ProgresoParticipanteCalendar(participacion.diaPrograma(), participacion.zona(),
                mapearRol(participacion.rol()), participacion.suspendido(), participacion.celulaId());
    }

    private static RolUsuario mapearRol(UserRole rol) {
        return switch (rol) {
            case ALCHEMIST -> RolUsuario.ALCHEMIST;
            case ADMIN -> RolUsuario.ADMIN;
            case MENTOR_LEAD -> RolUsuario.MENTOR_LEAD;
            case MENTOR -> RolUsuario.MENTOR;
            case TRAINEE -> RolUsuario.TRAINEE;
        };
    }
}
