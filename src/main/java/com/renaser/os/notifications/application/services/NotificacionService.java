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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ActorNotificacionesGuard actorGuard;
    private final Clock clock;
    /**
     * C-7: transaccion PROPIA (REQUIRES_NEW) para el {@code guardar} de {@link #emitir}. Mismo
     * criterio que {@code ConversacionService.crearDirectaConAmbosParticipantes} (C-10): si la
     * violacion del indice unico {@code notificaciones_origen_evento_uk} (V16, redelivery del
     * MISMO evento) se atrapara dentro de la transaccion de {@code emitir} (que es
     * {@code @Transactional}), Postgres ya la dejo abortada en cuanto el INSERT fallo, y
     * cualquier operacion posterior en esa misma transaccion explotaria con "current
     * transaction is aborted". Aislando el guardado, si pierde la carrera de deduplicacion solo
     * se deshace ESA transaccion chica.
     */
    private final TransactionTemplate transaccionPropia;

    public NotificacionService(LoadNotificacionPort loadNotificacionPort, SaveNotificacionPort saveNotificacionPort,
                                LoadPreferenciasPort loadPreferenciasPort, LoadTokenPushPort loadTokenPushPort,
                                PushPort pushPort, ActorNotificacionesGuard actorGuard, Clock clock,
                                PlatformTransactionManager transactionManager) {
        this.loadNotificacionPort = loadNotificacionPort;
        this.saveNotificacionPort = saveNotificacionPort;
        this.loadPreferenciasPort = loadPreferenciasPort;
        this.loadTokenPushPort = loadTokenPushPort;
        this.pushPort = pushPort;
        this.actorGuard = actorGuard;
        this.clock = clock;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    /**
     * SIN guard de actor a proposito (a diferencia de {@link #listar}/{@link #marcarLeida}/
     * {@link #marcarTodas}): no lo invoca un usuario, lo invocan los listeners de eventos de
     * OTROS modulos sobre un destinatario. Bloquear aca por cuenta suspendida rompería el
     * flujo del outbox de Modulith — un suspendido igual debe acumular su bandeja, lo que no
     * puede es leerla ni operarla (E-38).
     */
    @Override
    @Transactional
    public Optional<Notificacion> emitir(EmitirNotificacionCommand command) {
        boolean habilitada = loadPreferenciasPort.habilitadaPara(command.usuarioId(), command.tipo())
                .orElse(PreferenciaNotificacion.DEFAULT_HABILITADA);
        if (!habilitada) {
            return Optional.empty();
        }
        Notificacion notificacion = Notificacion.emitir(command.usuarioId(), command.tipo(), command.titulo(),
                command.cuerpo(), command.rutaApp(), command.origenEventoId(), clock);
        Optional<Notificacion> guardada = guardarIdempotente(notificacion);
        if (guardada.isEmpty()) {
            // C-7: ya existia una notificacion para el mismo (usuario, tipo, origenEventoId) --
            // el outbox reentrego un evento que ya procesamos. Ni la fila ni el push se repiten.
            log.debug("[notifications.NotificacionService] emision duplicada ignorada (origenEventoId={}, tipo={})",
                    command.origenEventoId(), command.tipo());
            return Optional.empty();
        }
        intentarPush(command);
        return guardada;
    }

    /** Aisla el INSERT en su propia transaccion (ver javadoc de {@link #transaccionPropia}) para
     * poder atrapar la violacion de {@code notificaciones_origen_evento_uk} sin abortar la
     * transaccion de {@link #emitir}. Solo puede chocar cuando {@code origenEventoId} no es
     * null -- el indice es parcial (C-7/V16). */
    private Optional<Notificacion> guardarIdempotente(Notificacion notificacion) {
        try {
            return Optional.of(transaccionPropia.execute(status -> saveNotificacionPort.guardar(notificacion)));
        } catch (DataIntegrityViolationException redeliveryDelMismoEvento) {
            return Optional.empty();
        }
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
        actorGuard.requireActivo(actorId);
        Instant desde = clock.now().minus(Notificacion.RETENCION_DIAS, ChronoUnit.DAYS);
        return loadNotificacionPort.bandeja(actorId, desde, Notificacion.LIMITE_BANDEJA);
    }

    @Override
    @Transactional
    public ResultadoLectura marcarLeida(UserId actorId, Long notificacionId) {
        actorGuard.requireActivo(actorId);
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
        actorGuard.requireActivo(actorId);
        return saveNotificacionPort.marcarTodasLeidas(actorId, clock.now());
    }
}
