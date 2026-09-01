package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.time.LocalDate;
import java.util.Objects;

/**
 * D-66: elige el Dia 1 del programa tras Terminos y Condiciones (dictado por el dueño
 * del proyecto, 2026-09-01). Cierra el gap documentado en docs/GAPS_FRONTEND_BACKEND.md
 * §7 ({@code POST /api/v1/onboarding/activate-program} "no existe").
 *
 * <p>Self-only por construccion: el comando no recibe un {@code traineeId} distinto de
 * {@code actorId}, igual criterio que {@link UpdateTraineeProfileUseCase} — nadie activa
 * el programa de otro por esta via.
 */
public interface ActivateProgramUseCase {

    ParticipacionPrograma activarPrograma(ActivateProgramCommand command);

    /**
     * {@code startDate} SIEMPRE viene del cliente — no hay valor por defecto ni
     * "mañana" implicito (a diferencia del backend viejo): la regla de negocio vigente
     * exige una eleccion explicita entre las 4 fechas de
     * {@link ConsultarActivacionProgramaUseCase}. Bean Validation no alcanza para
     * "obligatorio" sobre un tipo no-primitivo con self-validating puro, por eso el
     * chequeo es a mano en el constructor compacto (mismo patron que el resto del
     * modulo, no {@code @NotNull} + `@Valid` en el controller).
     */
    record ActivateProgramCommand(UserId actorId, LocalDate startDate) {

        public ActivateProgramCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(startDate, "startDate es obligatoria");
        }
    }
}
