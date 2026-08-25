package com.renaser.os.support.application.ports.in.ticketmentor;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface BuscarBibliotecaUseCase {

    List<String> buscar(BuscarBibliotecaCommand command);

    record BuscarBibliotecaCommand(@NotNull UserId actorId, @NotBlank @Size(max = 2000) String query) {

        public BuscarBibliotecaCommand {
            SelfValidating.validateConstructorArgs(BuscarBibliotecaCommand.class, actorId, query);
        }
    }
}
