package com.renaser.os.notifications.application.ports.in.tokenpush;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * La app avisa que acaba de rearmar sus alarmas locales en este dispositivo (D-217). Mientras la
 * confirmacion este vigente (26 h), el aviso de inicio de un habito con recordatorio no se le empuja
 * a ese telefono: ya suena su alarma. Sin confirmar —o con el APK anterior, que nunca confirma—, el
 * push sale igual como respaldo.
 */
public interface ConfirmarAlarmasLocalesUseCase {

    /**
     * @return el instante anotado
     * @throws java.util.NoSuchElementException si el token no esta registrado a nombre del actor
     */
    Instant confirmar(ConfirmarAlarmasLocalesCommand command);

    record ConfirmarAlarmasLocalesCommand(@NotNull UserId usuarioId, @NotBlank String token) {

        public ConfirmarAlarmasLocalesCommand {
            SelfValidating.validateConstructorArgs(ConfirmarAlarmasLocalesCommand.class, usuarioId, token);
        }
    }
}
