package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/** Contraparte de {@link AsignarAprendizCelulaUseCase} — gap #25. */
public interface QuitarAprendizCelulaUseCase {

    void quitar(QuitarAprendizCelulaCommand command);

    /**
     * {@code celulaId} NO es decorativo aunque la baja se pudiera resolver solo con el aprendiz.
     * Es el grupo del que se lo retira, y el caso de uso comprueba que sea el suyo: sin eso, una
     * pantalla vieja o dos administradores a la vez pueden pedir la baja desde el grupo
     * equivocado y borrarle la pertenencia real (SDD 003, ARF-06 / V13).
     */
    record QuitarAprendizCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId,
                                        @NotNull UserId traineeId) {

        public QuitarAprendizCelulaCommand {
            SelfValidating.validateConstructorArgs(QuitarAprendizCelulaCommand.class, actorId, celulaId, traineeId);
        }
    }
}
