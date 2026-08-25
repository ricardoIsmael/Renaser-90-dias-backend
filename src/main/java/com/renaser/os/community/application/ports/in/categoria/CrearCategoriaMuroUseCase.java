package com.renaser.os.community.application.ports.in.categoria;

import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase.CategoriaFilaAdmin;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface CrearCategoriaMuroUseCase {

    CategoriaFilaAdmin crear(CrearCategoriaMuroCommand command);

    record CrearCategoriaMuroCommand(@NotNull UserId actorId, @NotBlank String clave, @NotBlank String etiqueta,
                                      @NotBlank String emoji) {

        public CrearCategoriaMuroCommand {
            SelfValidating.validateConstructorArgs(CrearCategoriaMuroCommand.class, actorId, clave, etiqueta, emoji);
        }
    }
}
