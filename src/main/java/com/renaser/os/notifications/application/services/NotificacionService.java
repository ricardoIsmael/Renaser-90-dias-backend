package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.ListarNotificacionesUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.MarcarLeidaUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.MarcarTodasLeidasUseCase;
import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.application.ports.out.notificacion.SaveNotificacionPort;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.LoadTokenPushPort;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class NotificacionService implements EmitirNotificacionUseCase, ListarNotificacionesUseCase,
        MarcarLeidaUseCase, MarcarTodasLeidasUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final LoadNotificacionPort loadNotificacionPort;
    private final SaveNotificacionPort saveNotificacionPort;
    private final LoadPreferenciasPort loadPreferenciasPort;
    private final LoadTokenPushPort loadTokenPushPort;
    private final PushPort pushPort;
    private final Clock clock;

    public NotificacionService(LoadNotificacionPort loadNotificacionPort, SaveNotificacionPort saveNotificacionPort,
                                LoadPreferenciasPort loadPreferenciasPort, LoadTokenPushPort loadTokenPushPort,
                                PushPort pushPort, Clock clock) {
        this.loadNotificacionPort = loadNotificacionPort;
        this.saveNotificacionPort = saveNotificacionPort;
        this.loadPreferenciasPort = loadPreferenciasPort;
        this.loadTokenPushPort = loadTokenPushPort;
        this.pushPort = pushPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<Notificacion> emitir(EmitirNotificacionCommand command) {
        boolean habilitada = loadPreferenciasPort.habilitadaPara(command.usuarioId(), command.tipo())
                .orElse(PreferenciaNotificacion.DEFAULT_HABILITADA);
        if (!habilitada) {
            return Optional.empty();
        }
        Notificacion notificacion = Notificacion.emitir(command.usuarioId(), command.tipo(), command.titulo(),
                command.cuerpo(), command.rutaApp(), clock);
        Notificacion guardada = saveNotificacionPort.guardar(notificacion);
        intentarPush(command);
        return Optional.of(guardada);
    }

    /** Best-effort, nunca tumba la emision: el registro en la bandeja (arriba) es el contrato
     * real, el push es un empujon adicional (mismo criterio "fire-and-forget" que el repo viejo
     * aplicaba a Expo — `chat/repository.ts:sendExpoPushNotifications` no propaga sus fallos). */
    private void intentarPush(EmitirNotificacionCommand command) {
        try {
            var tokens = loadTokenPushPort.tokensDe(command.usuarioId());
            pushPort.enviar(tokens, command.titulo(), command.cuerpo());
        } catch (RuntimeException e) {
            log.warn("[notifications.NotificacionService] push best-effort fallo para tipo {}: {}", command.tipo(),
                    e.getMessage());
        }
    }

    @Override
    public List<Notificacion> listar(UserId actorId) {
        Instant desde = clock.now().minus(Notificacion.RETENCION_DIAS, ChronoUnit.DAYS);
        return loadNotificacionPort.bandeja(actorId, desde, Notificacion.LIMITE_BANDEJA);
    }

    @Override
    @Transactional
    public ResultadoLectura marcarLeida(UserId actorId, Long notificacionId) {
        Instant ahora = clock.now();
        int actualizadas = saveNotificacionPort.marcarLeida(notificacionId, actorId, ahora);
        if (actualizadas == 0 && !loadNotificacionPort.existeDe(notificacionId, actorId)) {
            throw new NoSuchElementException("Notificacion no encontrada");
        }
        // O bien se actualizo ahora, o ya estaba leida (idempotente) — en ambos casos, exito.
        return new ResultadoLectura(notificacionId, ahora);
    }

    @Override
    @Transactional
    public int marcarTodas(UserId actorId) {
        return saveNotificacionPort.marcarTodasLeidas(actorId, clock.now());
    }
}
