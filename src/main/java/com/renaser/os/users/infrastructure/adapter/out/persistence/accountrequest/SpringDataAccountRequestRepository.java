package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataAccountRequestRepository extends JpaRepository<AccountRequestJpaEntity, UUID> {

    @Query("select count(a) from AccountRequestJpaEntity a where a.ipSolicitud = :ip and a.creadoEn >= :since")
    long countByIpSolicitudAndCreadoEnAfter(String ip, Instant since);

    List<AccountRequestJpaEntity> findByEstado(EstadoSolicitudJpa estado, Pageable pageable);

    long countByEstado(EstadoSolicitudJpa estado);
}
