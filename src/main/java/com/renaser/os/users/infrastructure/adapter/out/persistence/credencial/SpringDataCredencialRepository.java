package com.renaser.os.users.infrastructure.adapter.out.persistence.credencial;

import com.renaser.os.users.infrastructure.adapter.out.persistence.user.UserJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Queries nativas contra las dos columnas de credencial de `usuarios`, en vez de una segunda
 * entidad JPA sobre la misma tabla: dos {@code @Entity} apuntando a `usuarios` en el mismo
 * contexto de persistencia es una fuente conocida de sorpresas en Hibernate, y acá no hace
 * falta — son dos columnas, una lectura y una escritura.
 *
 * <p>Extiende {@code Repository} y no {@code JpaRepository} a proposito: expone estos dos
 * metodos y nada mas. Un {@code JpaRepository} regalaria un {@code findAll()} de credenciales.
 */
interface SpringDataCredencialRepository extends Repository<UserJpaEntity, UUID> {

    /** El cast a text es necesario: `estado` es un enum nativo de Postgres, no varchar. */
    @Query(value = """
            SELECT id                AS id,
                   hash_contrasena   AS hashContrasena,
                   estado::text      AS estado
            FROM renaser.usuarios
            WHERE email = :email
            """, nativeQuery = true)
    Optional<CredencialRow> buscarPorEmail(@Param("email") String email);

    @Modifying
    @Query(value = """
            UPDATE renaser.usuarios
            SET hash_contrasena = :hash, contrasena_actualizada_en = :ahora
            WHERE id = :id
            """, nativeQuery = true)
    int actualizarHash(@Param("id") UUID id, @Param("hash") String hash, @Param("ahora") Instant ahora);

    /** Proyeccion de lectura. `hashContrasena` puede venir null: cuenta solo de login social. */
    interface CredencialRow {
        UUID getId();

        String getHashContrasena();

        String getEstado();
    }
}
