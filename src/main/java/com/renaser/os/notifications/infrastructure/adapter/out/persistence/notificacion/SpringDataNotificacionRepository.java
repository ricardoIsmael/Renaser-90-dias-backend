package com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataNotificacionRepository extends JpaRepository<NotificacionJpaEntity, Long> {

    List<NotificacionJpaEntity> findByUsuarioIdAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(UUID usuarioId,
                                                                                                Instant desde,
                                                                                                Pageable pageable);

    boolean existsByIdAndUsuarioId(Long id, UUID usuarioId);

    long countByUsuarioIdAndLeidaEnIsNullAndCreadoEnGreaterThanEqual(UUID usuarioId, Instant desde);

    /** UPDATE atomico: solo mueve leidaEn si sigue null y es del usuario — ver
     * {@code SaveNotificacionPort.marcarLeida} para el porque (nunca "cargar y comparar"). */
    @Modifying
    @Query("update NotificacionJpaEntity n set n.leidaEn = :ahora "
            + "where n.id = :id and n.usuarioId = :usuarioId and n.leidaEn is null")
    int marcarLeida(@Param("id") Long id, @Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);

    @Modifying
    @Query("update NotificacionJpaEntity n set n.leidaEn = :ahora "
            + "where n.usuarioId = :usuarioId and n.leidaEn is null")
    int marcarTodasLeidas(@Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);

    @Modifying
    @Query("delete from NotificacionJpaEntity n where n.creadoEn < :limite")
    int deleteByCreadoEnBefore(@Param("limite") Instant limite);
}
