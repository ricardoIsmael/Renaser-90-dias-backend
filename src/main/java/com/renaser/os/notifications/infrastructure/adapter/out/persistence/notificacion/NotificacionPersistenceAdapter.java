package com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion;

import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.application.ports.out.notificacion.SaveNotificacionPort;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Component
class NotificacionPersistenceAdapter implements LoadNotificacionPort, SaveNotificacionPort {

    /**
     * El único tipo que no se ve en la campana ({@link TipoNotificacion#seVeEnLaCampana}, D-221). Una
     * prueba recorre el enum y falla si aparece otro, para que esta constante no se quede corta.
     */
    static final TipoNotificacionJpa FUERA_DE_LA_CAMPANA = TipoNotificacionJpa.MENSAJE_CHAT;

    private final SpringDataNotificacionRepository repository;
    private final NotificacionPersistenceMapper mapper;

    NotificacionPersistenceAdapter(SpringDataNotificacionRepository repository,
                                    NotificacionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<Notificacion> bandeja(UserId usuarioId, Instant desde, int limite) {
        return repository
                .findByUsuarioIdAndTipoNotAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(usuarioId.value(),
                        FUERA_DE_LA_CAMPANA, desde, PageRequest.of(0, limite))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existeDe(Long id, UserId usuarioId) {
        return repository.existsByIdAndUsuarioId(id, usuarioId.value());
    }

    @Override
    public long contarNoLeidas(UserId usuarioId, Instant desde) {
        return repository.countByUsuarioIdAndTipoNotAndLeidaEnIsNullAndCreadoEnGreaterThanEqual(usuarioId.value(),
                FUERA_DE_LA_CAMPANA, desde);
    }

    @Override
    public boolean existePorOrigen(UserId usuarioId, TipoNotificacion tipo, UUID origenEventoId) {
        // Sin origen no hay deduplicacion que consultar: el indice unico es PARCIAL y solo cubre
        // las filas con `origen_evento_id` no nulo (C-7/V16).
        if (origenEventoId == null) {
            return false;
        }
        return repository.existsByUsuarioIdAndTipoAndOrigenEventoId(usuarioId.value(),
                TipoNotificacionJpa.valueOf(tipo.name()), origenEventoId);
    }

    @Override
    public Notificacion guardar(Notificacion notificacion) {
        var guardada = repository.save(mapper.toEntity(notificacion));
        return mapper.toDomain(guardada);
    }

    @Override
    public int marcarLeida(Long id, UserId usuarioId, Instant ahora) {
        return repository.marcarLeida(id, usuarioId.value(), ahora);
    }

    @Override
    public int marcarTodasLeidas(UserId usuarioId, Instant ahora) {
        return repository.marcarTodasLeidas(usuarioId.value(), ahora);
    }

    /**
     * <b>Con su transacción (E-425, 2026-09-29).</b> Sin ella, llamada desde el cron (que no abre
     * ninguna), el DELETE falla con «No active transaction for update or delete query»: la purga de 90
     * días no borraba nada (no se notó porque todavía no hay filas tan viejas). Las pruebas del adaptador corren con {@code @Transactional} de clase y
     * lo tapaban; lo encontró {@code MensajeDeChatAvisoIT}, que llama al cron sin transacción.
     */
    @Override
    @Transactional
    public int purgarAnterioresA(Instant limite) {
        return repository.deleteByCreadoEnBefore(limite);
    }

    @Override
    @Transactional
    public int purgarDeTipoAnterioresA(TipoNotificacion tipo, Instant limite) {
        return repository.deleteByTipoAndCreadoEnBefore(TipoNotificacionJpa.valueOf(tipo.name()), limite);
    }
}
