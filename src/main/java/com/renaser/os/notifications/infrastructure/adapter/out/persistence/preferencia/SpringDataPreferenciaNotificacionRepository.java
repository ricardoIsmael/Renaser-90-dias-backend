package com.renaser.os.notifications.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion.TipoNotificacionJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataPreferenciaNotificacionRepository
        extends JpaRepository<PreferenciaNotificacionJpaEntity, PreferenciaNotificacionId> {

    List<PreferenciaNotificacionJpaEntity> findByUsuarioId(UUID usuarioId);

    Optional<PreferenciaNotificacionJpaEntity> findByUsuarioIdAndTipo(UUID usuarioId, TipoNotificacionJpa tipo);
}
