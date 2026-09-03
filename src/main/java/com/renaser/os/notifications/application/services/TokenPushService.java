package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase;
import com.renaser.os.notifications.application.ports.out.tokenpush.UpsertTokenPushPort;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenPushService implements RegistrarTokenPushUseCase {

    private final UpsertTokenPushPort upsertTokenPushPort;
    private final ActorNotificacionesGuard actorGuard;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TokenPushService(UpsertTokenPushPort upsertTokenPushPort, ActorNotificacionesGuard actorGuard,
                             Clock clock, IdGenerator idGenerator) {
        this.upsertTokenPushPort = upsertTokenPushPort;
        this.actorGuard = actorGuard;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public TokenPush registrar(RegistrarTokenPushCommand command) {
        actorGuard.requireActivo(command.usuarioId());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        // Este id solo se usa en el camino de INSERT del upsert: si el token ya existe, el adaptador
        // conserva el id de la fila existente y este se descarta (TokenPushPersistenceAdapter).
        TokenPush tokenPush = TokenPush.registrar(TokenPushId.of(idGenerator.newId()), command.usuarioId(),
                command.token(), command.plataforma(), clock);
        return upsertTokenPushPort.upsertPorToken(tokenPush);
    }
}
