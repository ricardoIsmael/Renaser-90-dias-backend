package com.renaser.os.academy.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delega en el contrato publico de `users` (D-41).
 *
 * <p><b>Semantica preservada (AC-04):</b> aca NO se filtra por {@code inscrito}. La
 * fila de participante es opcional para todo rol que no sea TRAINEE, y el catalogo de
 * cursos igual tiene que responderle a un ADMIN o a un MENTOR sin programa activo. Lo
 * que si se conserva es que {@code diaPrograma} y {@code zona} lleguen en {@code null}
 * cuando no hay inscripcion: el gating por dia distingue "dia 0" de "sin programa", y
 * un default silencioso de 0 desbloquearia contenido que no corresponde.
 */
@Component
class ConsultarProgresoParticipanteAcademyPersistenceAdapter implements ConsultarProgresoParticipanteAcademyPort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarProgresoParticipanteAcademyPersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public Optional<ProgresoParticipanteAcademy> deParticipante(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .map(ConsultarProgresoParticipanteAcademyPersistenceAdapter::aProgreso);
    }

    private static ProgresoParticipanteAcademy aProgreso(ParticipacionPrograma participacion) {
        Integer diaPrograma = participacion.inscrito() ? participacion.diaPrograma() : null;
        return new ProgresoParticipanteAcademy(diaPrograma,
                participacion.inscrito() ? participacion.zona() : null,
                mapearRol(participacion.rol()), participacion.suspendido());
    }

    private static RolParticipante mapearRol(UserRole rol) {
        return switch (rol) {
            case ALCHEMIST -> RolParticipante.ALCHEMIST;
            case ADMIN -> RolParticipante.ADMIN;
            case MENTOR_LEAD -> RolParticipante.MENTOR_LEAD;
            case MENTOR -> RolParticipante.MENTOR;
            case TRAINEE -> RolParticipante.TRAINEE;
        };
    }
}
