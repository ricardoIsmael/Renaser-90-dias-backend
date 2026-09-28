package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.tokenpush.ConfirmarAlarmasLocalesUseCase;
import com.renaser.os.notifications.application.ports.in.tokenpush.RegistrarTokenPushUseCase;
import com.renaser.os.notifications.application.ports.in.tokenpush.RevocarTokensPushUseCase;
import com.renaser.os.notifications.application.ports.out.tokenpush.BorrarTokensPushDeUsuarioPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.ConfirmarAlarmasLocalesPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.UpsertTokenPushPort;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

@Service
public class TokenPushService implements RegistrarTokenPushUseCase, RevocarTokensPushUseCase,
        ConfirmarAlarmasLocalesUseCase {

    private static final Logger log = LoggerFactory.getLogger(TokenPushService.class);

    private final UpsertTokenPushPort upsertTokenPushPort;
    private final BorrarTokensPushDeUsuarioPort borrarTokensPushDeUsuarioPort;
    private final ConfirmarAlarmasLocalesPort confirmarAlarmasLocalesPort;
    private final ActorNotificacionesGuard actorGuard;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TokenPushService(UpsertTokenPushPort upsertTokenPushPort,
                             BorrarTokensPushDeUsuarioPort borrarTokensPushDeUsuarioPort,
                             ConfirmarAlarmasLocalesPort confirmarAlarmasLocalesPort,
                             ActorNotificacionesGuard actorGuard, Clock clock, IdGenerator idGenerator) {
        this.upsertTokenPushPort = upsertTokenPushPort;
        this.borrarTokensPushDeUsuarioPort = borrarTokensPushDeUsuarioPort;
        this.confirmarAlarmasLocalesPort = confirmarAlarmasLocalesPort;
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

    /**
     * SIN {@code requireActivo}, al reves que {@link #registrar}: esto se invoca JUSTO cuando la
     * cuenta dejo de estar activa. Pedir una cuenta activa para revocarla seria pedir que nunca se
     * revoque nada. Tampoco lo llama un usuario — lo llama el listener del cambio de estado.
     */
    @Override
    @Transactional
    public int revocarDe(UserId usuarioId) {
        int revocados = borrarTokensPushDeUsuarioPort.borrarDe(usuarioId);
        if (revocados > 0) {
            log.info("[notifications.TokenPushService] revocadas {} suscripcion(es) de push de {}",
                    revocados, usuarioId);
        }
        return revocados;
    }

    /**
     * D-217. Solo marca un token que YA es del actor: la confirmacion dice "este telefono tiene sus
     * alarmas", y aceptarla sobre un token ajeno le cortaria el respaldo del push a otra persona.
     * Un token que todavia no se registro (la app confirma antes de que termine su
     * {@code POST /push-tokens}) da 404 y la app lo reintenta en la proxima apertura.
     */
    @Override
    @Transactional
    public Instant confirmar(ConfirmarAlarmasLocalesCommand command) {
        actorGuard.requireActivo(command.usuarioId());
        Instant ahora = clock.now();
        if (confirmarAlarmasLocalesPort.confirmar(command.usuarioId(), command.token(), ahora) == 0) {
            throw new NoSuchElementException("Ese dispositivo no esta registrado para recibir avisos");
        }
        return ahora;
    }
}
