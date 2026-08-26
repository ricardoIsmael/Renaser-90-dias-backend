package com.renaser.os.users.infrastructure.adapter.out.persistence.identidadexterna;

import com.renaser.os.users.infrastructure.adapter.out.persistence.user.UserJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Queries nativas contra `identidades_externas`, en vez de una entidad JPA con clave compuesta
 * ({@code proveedor}, {@code sujeto_proveedor}): son dos consultas simples, y una {@code @IdClass}
 * agregaria complejidad de mapeo que no se usa en ningun otro lado (mismo criterio que
 * {@code SpringDataCredencialRepository}).
 *
 * <p>{@code Repository<UserJpaEntity, UUID>} es un marcador sin relacion real con la tabla que
 * consulta: {@code Repository} (a diferencia de {@code JpaRepository}) no genera ningun metodo
 * derivado de sus parametros de tipo, asi que no hace falta una entidad propia solo para
 * satisfacer la firma — mismo truco, mismo motivo, que en {@code SpringDataCredencialRepository}.
 */
interface SpringDataIdentidadExternaRepository extends Repository<UserJpaEntity, UUID> {

    @Query(value = """
            SELECT proveedor         AS proveedor,
                   sujeto_proveedor  AS sujetoProveedor,
                   usuario_id        AS usuarioId,
                   email_proveedor   AS emailProveedor,
                   vinculada_en      AS vinculadaEn
            FROM renaser.identidades_externas
            WHERE proveedor = :proveedor AND sujeto_proveedor = :sujetoProveedor
            """, nativeQuery = true)
    Optional<IdentidadExternaRow> buscar(@Param("proveedor") String proveedor,
                                          @Param("sujetoProveedor") String sujetoProveedor);

    @Modifying
    @Query(value = """
            INSERT INTO renaser.identidades_externas
                (proveedor, sujeto_proveedor, usuario_id, email_proveedor, vinculada_en)
            VALUES (:proveedor, :sujetoProveedor, :usuarioId, :emailProveedor, :vinculadaEn)
            """, nativeQuery = true)
    void insertar(@Param("proveedor") String proveedor, @Param("sujetoProveedor") String sujetoProveedor,
                   @Param("usuarioId") UUID usuarioId, @Param("emailProveedor") String emailProveedor,
                   @Param("vinculadaEn") Instant vinculadaEn);

    /** Proyeccion de lectura. `emailProveedor` puede venir null: es informativo (§2.2). */
    interface IdentidadExternaRow {
        String getProveedor();

        String getSujetoProveedor();

        UUID getUsuarioId();

        String getEmailProveedor();

        Instant getVinculadaEn();
    }
}
