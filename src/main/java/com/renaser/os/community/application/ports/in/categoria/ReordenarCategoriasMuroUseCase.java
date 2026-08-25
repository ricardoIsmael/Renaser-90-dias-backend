package com.renaser.os.community.application.ports.in.categoria;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface ReordenarCategoriasMuroUseCase {

    /** {@code claves} es la lista COMPLETA en el orden final, no un delta — con un delta,
     * dos administradores reordenando a la vez dejan huecos o empates (wall-categories/schema.ts:67-73). */
    void reordenar(ReordenarCategoriasMuroCommand command);

    record ReordenarCategoriasMuroCommand(@NotNull UserId actorId, @NotEmpty List<String> claves) {

        public ReordenarCategoriasMuroCommand {
            SelfValidating.validateConstructorArgs(ReordenarCategoriasMuroCommand.class, actorId, claves);
        }
    }
}
