package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;
import com.renaser.os.users.domain.model.user.User;

import java.util.Objects;

/**
 * Panel admin de aprendices (gap #7): detalle de UN aprendiz — a diferencia de
 * {@code ParticipacionProgramaFinder.deParticipante} (publico, sin gate, pensado para que
 * OTROS MODULOS lo consulten libremente), este caso de uso exige ADMIN/ALCHEMIST porque
 * se expone directo por HTTP.
 */
public interface GetTraineeDetailUseCase {

    TraineeDetail obtener(GetTraineeDetailCommand command);

    record GetTraineeDetailCommand(UserId actorId, UserId traineeId) {

        public GetTraineeDetailCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(traineeId, "traineeId es obligatorio");
        }
    }

    /**
     * {@code ultimoAjuste} (D-82) es {@code null} si a este aprendiz nunca le movieron el
     * dia a mano — que es el caso de la enorme mayoria. Va aca y no en un endpoint aparte
     * para que el panel muestre el dia y su explicacion en la misma pantalla, sin una
     * segunda llamada.
     */
    record TraineeDetail(User user, com.renaser.os.users.api.ParticipacionPrograma participacion,
                          AjusteDiaPrograma ultimoAjuste) {

        /** Firma historica (sin ultimo ajuste). */
        public TraineeDetail(User user, com.renaser.os.users.api.ParticipacionPrograma participacion) {
            this(user, participacion, null);
        }
    }
}
