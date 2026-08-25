package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface RegistrarCoherenciaDiariaUseCase {

    void registrar(RegistrarCoherenciaDiariaCommand command);

    record RegistrarCoherenciaDiariaCommand(@NotNull UserId participanteId, @NotNull LocalDate fecha,
                                             @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal valor,
                                             boolean diaHabitosPerfecto) {

        public RegistrarCoherenciaDiariaCommand {
            SelfValidating.validateConstructorArgs(RegistrarCoherenciaDiariaCommand.class, participanteId, fecha,
                    valor, diaHabitosPerfecto);
        }
    }
}
