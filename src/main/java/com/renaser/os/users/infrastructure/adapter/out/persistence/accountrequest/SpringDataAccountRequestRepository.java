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

    /**
     * Dueno de la solicitud: {@code usuario_id} (ex {@code supabase_user_id}, V11). Es
     * {@code NOT NULL UNIQUE} y NO tiene FK contra {@code usuarios}, asi que el borrado de la
     * cuenta tiene que nombrar esta fila explicitamente -- ningun {@code ON DELETE} la alcanza.
     *
     * <p>Consulta derivada y no {@code @Modifying @Query}: la derivada carga la entidad y la
     * saca por el contexto de persistencia (misma unidad de trabajo que el resto de la purga,
     * sin dejar una copia vieja en la cache de primer nivel), y el nombre del esquema lo pone
     * {@code @Table(schema = "renaser")} de la entidad en vez de quedar escrito a mano en un
     * SQL. Por el UNIQUE de la columna borra como maximo una fila.
     */
    void deleteByUsuarioId(UUID usuarioId);
}
