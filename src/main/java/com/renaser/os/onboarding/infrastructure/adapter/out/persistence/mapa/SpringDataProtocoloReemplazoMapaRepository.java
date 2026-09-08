package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.mapa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataProtocoloReemplazoMapaRepository
        extends JpaRepository<ProtocoloReemplazoMapaJpaEntity, UUID> {

    List<ProtocoloReemplazoMapaJpaEntity> findByUsuarioIdOrderByCreadoEnAsc(UUID usuarioId);

    void deleteByUsuarioId(UUID usuarioId);
}
