package com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion;

import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.application.ports.out.notificacion.SaveNotificacionPort;
import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
class NotificacionPersistenceAdapter implements LoadNotificacionPort, SaveNotificacionPort {

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
                .findByUsuarioIdAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(usuarioId.value(), desde,
                        PageRequest.of(0, limite))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existeDe(Long id, UserId usuarioId) {
        return repository.existsByIdAndUsuarioId(id, usuarioId.value());
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

    @Override
    public int purgarAnterioresA(Instant limite) {
        return repository.deleteByCreadoEnBefore(limite);
    }
}
