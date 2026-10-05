package com.renaser.os.habits.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
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

    /**
     * `participantes_programa.programa_activado_en IS NOT NULL` — el reloj de los 90 dias
     * arrancó. NO significa "esta inscrito": un TRAINEE recien aprobado tiene fila y este campo
     * en `null` hasta que completa primer login + Ficha + Terminos.
     *
     * <p>Existe para E-169: el staff que activa su seguimiento personal
     * (`POST /api/v1/mentor/activate-tracking`) queda con este campo puesto, y es lo unico que
     * distingue a un mentor que SI cursa el programa de uno que no.
     *
     * <p>{@code fechaInicio} (D-254, 2026-10-05): `participantes_programa.fecha_inicio`. La necesita
     * la racha de cada habito para no contar dias anteriores al inicio del programa, igual que la
     * racha general ({@code points.RachaMostrada}). {@code null} cuando quien construye el progreso
     * no la conoce: la racha cae entonces a la ventana de 90 dias de la racha general.
     */
    record ProgresoParticipanteHabits(int diaPrograma, String timezone, RolParticipante rol, boolean suspendido,
                                       boolean programaActivado, LocalDate fechaInicio) {

        /** Firma anterior a D-254, sin fecha de inicio: la conservan los llamadores que no la usan. */
        public ProgresoParticipanteHabits(int diaPrograma, String timezone, RolParticipante rol, boolean suspendido,
                                          boolean programaActivado) {
            this(diaPrograma, timezone, rol, suspendido, programaActivado, null);
        }
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
