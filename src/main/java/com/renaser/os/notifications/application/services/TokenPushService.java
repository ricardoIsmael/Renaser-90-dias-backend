package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase;
import com.renaser.os.notifications.application.ports.out.tokenpush.UpsertTokenPushPort;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenPushService implements RegistrarTokenPushUseCase {

    private final UpsertTokenPushPort upsertTokenPushPort;
    private final Clock clock;

    public TokenPushService(UpsertTokenPushPort upsertTokenPushPort, Clock clock) {
        this.upsertTokenPushPort = upsertTokenPushPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TokenPush registrar(RegistrarTokenPushCommand command) {
        TokenPush tokenPush = TokenPush.registrar(command.usuarioId(), command.token(), command.plataforma(), clock);
        return upsertTokenPushPort.upsertPorToken(tokenPush);
    }
}
