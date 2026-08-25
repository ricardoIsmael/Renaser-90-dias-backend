package com.renaser.os.notifications.application.ports.in.tokenpush;

import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Registra/re-vincula el token Expo del dispositivo que llama — {@code usuarioId} sale
 * siempre del actor resuelto (X-Actor-Id), nunca del body (mismo blindaje de siempre). */
public interface RegistrarTokenPushUseCase {

    TokenPush registrar(RegistrarTokenPushCommand command);

    record RegistrarTokenPushCommand(@NotNull UserId usuarioId, @NotBlank String token,
                                      PlataformaPush plataforma) {

        public RegistrarTokenPushCommand {
            SelfValidating.validateConstructorArgs(RegistrarTokenPushCommand.class, usuarioId, token, plataforma);
        }
    }
}
