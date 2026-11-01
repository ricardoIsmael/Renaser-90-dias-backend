package com.renaser.os.users.infrastructure.adapter.out.persistence.accountrequest;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * La solicitud con el candado de escritura de la fila (spec 025, FR-018): dos administradores que deciden la misma solicitud a la vez se
     * turnan; el segundo la lee ya decidida y recibe el 409 en vez de chocar contra la inscripción que el primero acaba de crear.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountRequestJpaEntity a where a.id = :id")
    Optional<AccountRequestJpaEntity> bloquearPorId(UUID id);

    /**
     * La cola de Administración (más nueva primero). {@code estado} y {@code formacion} nulos = sin filtro. Con formación, las pendientes siempre
     * se ven (todavía no tienen formación) y las aceptadas son las que la eligieron (spec 025, US4). Nativa porque {@code formaciones_elegidas} es
     * un arreglo.
     */
    @Query(value = """
            SELECT s.* FROM renaser.solicitudes_cuenta s
            WHERE (CAST(:estado AS text) IS NULL OR s.estado::text = CAST(:estado AS text))
              AND (CAST(:formacion AS text) IS NULL OR s.estado = 'PENDIENTE' OR CAST(:formacion AS text) = ANY (s.formaciones_elegidas))
            ORDER BY s.creado_en DESC
            """, nativeQuery = true)
    List<AccountRequestJpaEntity> cola(String estado, String formacion, Pageable pageable);

    @Query(value = """
            SELECT count(*) FROM renaser.solicitudes_cuenta s
            WHERE (CAST(:estado AS text) IS NULL OR s.estado::text = CAST(:estado AS text))
              AND (CAST(:formacion AS text) IS NULL OR s.estado = 'PENDIENTE' OR CAST(:formacion AS text) = ANY (s.formaciones_elegidas))
            """, nativeQuery = true)
    long contarCola(String estado, String formacion);
}
