package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAccountRequestRepository extends JpaRepository<AccountRequestJpaEntity, UUID> {

    @Query("select count(a) from AccountRequestJpaEntity a where a.ipSolicitud = :ip and a.creadoEn >= :since")
    long countByIpSolicitudAndCreadoEnAfter(String ip, Instant since);

    boolean existsByEmail(String email);

    /**
     * Resuelve por la identidad del proveedor, nunca por correo (docs/MODULO_AUTH.md §6.4).
     * Devuelve como maximo una fila: el indice UNIQUE parcial
     * {@code solicitudes_origen_social_idx} (migracion V12) impide que dos solicitudes reclamen
     * la misma identidad social.
     */
    Optional<AccountRequestJpaEntity> findByProveedorAndSujetoProveedor(String proveedor, String sujetoProveedor);

    List<AccountRequestJpaEntity> findByEstado(EstadoSolicitudJpa estado, Pageable pageable);

    long countByEstado(EstadoSolicitudJpa estado);
}
