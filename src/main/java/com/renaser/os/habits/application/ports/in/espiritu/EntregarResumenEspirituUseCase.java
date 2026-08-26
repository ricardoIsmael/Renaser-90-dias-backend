package com.renaser.os.habits.application.ports.in.espiritu;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public interface EntregarResumenEspirituUseCase {

    /** Autoservicio. Fuera de plazo queda PENDIENTE en vez de fallar (ver {@code RegistroEspiritu.entregar}). */
    ResultadoEntrega entregar(EntregarResumenEspirituCommand command);

    record EntregarResumenEspirituCommand(@NotNull UserId actorId, @NotNull @Min(1) Integer dia,
                                           @NotBlank String resumenTexto) {
        public EntregarResumenEspirituCommand {
            SelfValidating.validateConstructorArgs(EntregarResumenEspirituCommand.class, actorId, dia, resumenTexto);
        }
    }

    record ResultadoEntrega(boolean aTiempo) {
    }
}
