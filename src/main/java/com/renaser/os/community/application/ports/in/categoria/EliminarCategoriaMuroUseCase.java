package com.renaser.os.community.application.ports.in.categoria;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface EliminarCategoriaMuroUseCase {

    void eliminar(EliminarCategoriaMuroCommand command);

    record EliminarCategoriaMuroCommand(@NotNull UserId actorId, @NotBlank String clave) {

        public EliminarCategoriaMuroCommand {
            SelfValidating.validateConstructorArgs(EliminarCategoriaMuroCommand.class, actorId, clave);
        }
    }
}
