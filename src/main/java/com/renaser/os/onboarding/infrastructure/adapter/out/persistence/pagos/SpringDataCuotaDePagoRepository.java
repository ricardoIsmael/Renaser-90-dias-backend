package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.pagos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataCuotaDePagoRepository extends JpaRepository<CuotaDePagoJpaEntity, UUID> {

    List<CuotaDePagoJpaEntity> findByUsuarioIdOrderByVenceElAscCreadaEnAscIdAsc(UUID usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CuotaDePagoJpaEntity c WHERE c.usuarioId = :usuarioId AND c.id = :id")
    void eliminarDeLaPersona(@Param("usuarioId") UUID usuarioId, @Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CuotaDePagoJpaEntity c WHERE c.usuarioId = :usuarioId")
    void eliminarTodasDeLaPersona(@Param("usuarioId") UUID usuarioId);
}
