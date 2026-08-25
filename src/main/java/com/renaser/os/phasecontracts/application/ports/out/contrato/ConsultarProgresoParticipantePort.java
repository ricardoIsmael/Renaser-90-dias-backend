package com.renaser.os.phasecontracts.application.ports.out.contrato;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface ConsultarProgresoParticipantePort {

    Optional<ProgresoParticipante> deParticipante(UserId participanteId);

    /** diaPrograma: participantes_programa.dia_programa. suspendido: usuarios.estado = SUSPENDIDO. */
    record ProgresoParticipante(int diaPrograma, RolParticipante rol, boolean suspendido) {
    }

    /**
     * Espejo LOCAL (a este modulo) del enum Postgres `rol_usuario` — a proposito NO
     * es el UserRole de `users.domain`, ver javadoc de la interfaz.
     */
    enum RolParticipante {
        ALCHEMIST,
        ADMIN,
        MENTOR_LEAD,
        MENTOR,
        TRAINEE
    }
}
