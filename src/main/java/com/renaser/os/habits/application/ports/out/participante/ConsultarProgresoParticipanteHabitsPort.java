package com.renaser.os.habits.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Copia PROPIA (deuda conocida, documentada en todo el repo) del patron de
 * lectura de `participantes_programa`/`usuarios` sin importar tipos internos de
 * `users` — replica exacta del patron de `phasecontracts`
 * (docs/MODULO_PHASECONTRACTS.md §2): `users.api.UserSummary` expone
 * `role()`/`status()` tipados con `UserRole`/`UserStatus`, que viven en un
 * paquete interno de `users` NO cubierto por `@NamedInterface("api")`.
 * Referenciarlos desde otro modulo rompe `ArchitectureTest.modulesDoNotLeakInternals`.
 */
public interface ConsultarProgresoParticipanteHabitsPort {

    Optional<ProgresoParticipanteHabits> deParticipante(UserId participanteId);

    /**
     * Padron para el barrido nocturno que genera los tracks del dia. En lote a proposito:
     * llamar a {@link #deParticipante} en un bucle seria un N+1.
     */
    List<UserId> participantesInscritosActivos();

    /** diaPrograma/timezone: participantes_programa. rol/suspendido: usuarios. */
    record ProgresoParticipanteHabits(int diaPrograma, String timezone, RolParticipante rol, boolean suspendido) {
    }

    /** Espejo LOCAL de `rol_usuario` — a proposito NO el UserRole de `users.domain`. */
    enum RolParticipante {
        ALCHEMIST,
        ADMIN,
        MENTOR_LEAD,
        MENTOR,
        TRAINEE
    }
}
