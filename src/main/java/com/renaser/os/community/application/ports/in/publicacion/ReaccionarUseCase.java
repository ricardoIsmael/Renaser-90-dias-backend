package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface ReaccionarUseCase {

    /** Tocar el mismo tipo que ya esta puesto lo saca; tocar el otro lo reemplaza
     * (wall/service.ts:412-443). */
    ResultadoReaccion reaccionar(ReaccionarCommand command);

    record ReaccionarCommand(@NotNull UserId actorId, @NotNull PublicacionId publicacionId,
                              @NotNull TipoReaccion tipo) {

        public ReaccionarCommand {
            SelfValidating.validateConstructorArgs(ReaccionarCommand.class, actorId, publicacionId, tipo);
        }
    }

    record ResultadoReaccion(boolean reaccionado, int likes, int dislikes) {
    }
}
