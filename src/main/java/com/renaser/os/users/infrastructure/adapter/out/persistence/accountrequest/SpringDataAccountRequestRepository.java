package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

interface SpringDataAccountRequestRepository extends JpaRepository<AccountRequestJpaEntity, UUID> {

    @Query("select count(a) from AccountRequestJpaEntity a where a.ipSolicitud = :ip and a.creadoEn >= :since")
    long countByIpSolicitudAndCreadoEnAfter(String ip, Instant since);
}
