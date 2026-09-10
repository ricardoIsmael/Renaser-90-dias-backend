package com.renaser.os.phasecontracts.application.ports.out.contrato;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface ConsultarProgresoParticipantePort {

    Optional<ProgresoParticipante> deParticipante(UserId participanteId);

    /**
     * diaPrograma: participantes_programa.dia_programa. suspendido: usuarios.estado = SUSPENDIDO.
     *
     * <p>{@code programaActivado} = {@code programa_activado_en IS NOT NULL}. Es distinto de tener
     * fila: una cuenta aprobada la tiene, pero el reloj no arranca hasta que firma los Terminos.
     * Los contratos de fase se firman contra el dia del programa, asi que sin el reloj corriendo
     * la pregunta "que fase te toca" no tiene respuesta — por eso el guard lo exige al staff que
     * cursa opcionalmente (SDD 003, ARF-16 / V04).
     */
    record ProgresoParticipante(int diaPrograma, RolParticipante rol, boolean suspendido,
                                 boolean programaActivado) {
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
