package com.renaser.os.community.application.ports.in.categoria;

import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase.CategoriaFilaAdmin;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface ActualizarCategoriaMuroUseCase {

    CategoriaFilaAdmin actualizar(ActualizarCategoriaMuroCommand command);

    record ActualizarCategoriaMuroCommand(@NotNull UserId actorId, @NotBlank String clave, String etiqueta,
                                           String emoji, Boolean activa) {

        public ActualizarCategoriaMuroCommand {
            SelfValidating.validateConstructorArgs(ActualizarCategoriaMuroCommand.class, actorId, clave, etiqueta,
                    emoji, activa);
        }
    }
}
