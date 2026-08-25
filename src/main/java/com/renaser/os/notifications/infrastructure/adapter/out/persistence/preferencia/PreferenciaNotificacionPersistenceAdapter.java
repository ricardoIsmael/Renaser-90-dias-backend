package com.renaser.os.notifications.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.preferencia.SavePreferenciaPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class PreferenciaNotificacionPersistenceAdapter implements LoadPreferenciasPort, SavePreferenciaPort {

    private final SpringDataPreferenciaNotificacionRepository repository;
    private final PreferenciaNotificacionPersistenceMapper mapper;

    PreferenciaNotificacionPersistenceAdapter(SpringDataPreferenciaNotificacionRepository repository,
                                               PreferenciaNotificacionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<PreferenciaNotificacion> porUsuario(UserId usuarioId) {
        return repository.findByUsuarioId(usuarioId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Boolean> habilitadaPara(UserId usuarioId, TipoNotificacion tipo) {
        return repository.findByUsuarioIdAndTipo(usuarioId.value(), mapper.toJpaTipo(tipo))
                .map(PreferenciaNotificacionJpaEntity::isHabilitada);
    }

    @Override
    public void upsert(PreferenciaNotificacion preferencia) {
        repository.save(mapper.toEntity(preferencia));
    }
}
