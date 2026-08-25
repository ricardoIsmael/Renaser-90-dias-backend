package com.renaser.os.notifications.infrastructure.adapter.in.rest.tokenpush;

import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase;
import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase.RegistrarTokenPushCommand;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/** Ruta fiel al contrato viejo: {@code POST /push-tokens} (`push-tokens/route.ts`, CHAT-07).
 * {@code usuarioId} sale siempre del actor resuelto (X-Actor-Id), nunca del body. */
@RestController
@RequestMapping("/api/v1/push-tokens")
public class TokenPushController {

    private final RegistrarTokenPushUseCase registrarTokenPushUseCase;

    public TokenPushController(RegistrarTokenPushUseCase registrarTokenPushUseCase) {
        this.registrarTokenPushUseCase = registrarTokenPushUseCase;
    }

    @PostMapping
    public TokenPushResponse registrar(@RequestHeader("X-Actor-Id") String actorId,
                                        @RequestBody @Valid RegistrarTokenPushRequest request) {
        PlataformaPush plataforma = request.platform() == null ? null
                : PlataformaPush.valueOf(request.platform().toUpperCase(Locale.ROOT));
        var tokenPush = registrarTokenPushUseCase.registrar(
                new RegistrarTokenPushCommand(UserId.of(actorId), request.token(), plataforma));
        return TokenPushResponse.from(tokenPush);
    }
}
