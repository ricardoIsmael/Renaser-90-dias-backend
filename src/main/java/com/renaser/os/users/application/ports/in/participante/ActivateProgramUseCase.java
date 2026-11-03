package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

import java.time.LocalDate;
import java.util.Objects;

/**
 * D-66: activa el programa tras Terminos y Condiciones. Cierra el gap documentado en docs/GAPS_FRONTEND_BACKEND.md
 * §7 ({@code POST /api/v1/onboarding/activate-program} "no existe").
 *
 * <p><b>Spec 036 (D-309, 2026-11-02): la persona ya no elige el Dia 1.</b> El programa empieza siempre MAÑANA en su
 * zona; la fecha la calcula el servidor. Antes elegia entre mañana, +2 y +3.
 *
 * <p>Self-only por construccion: el comando no recibe un {@code traineeId} distinto de
 * {@code actorId}, igual criterio que {@link UpdateTraineeProfileUseCase} — nadie activa
 * el programa de otro por esta via.
 */
public interface ActivateProgramUseCase {

    ParticipacionPrograma activarPrograma(ActivateProgramCommand command);

    /**
     * {@code startDate} es OPCIONAL (spec 036): ausente significa "mañana". Si viene, es la fecha que la persona vio en
     * pantalla y tiene que seguir siendo mañana; si no, el dominio rechaza (400) y la app la vuelve a leer.
     */
    record ActivateProgramCommand(UserId actorId, LocalDate startDate) {

        public ActivateProgramCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
        }
    }
}
