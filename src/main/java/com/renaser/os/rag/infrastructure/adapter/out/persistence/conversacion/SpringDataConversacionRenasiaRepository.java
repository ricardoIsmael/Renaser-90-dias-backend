package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface SpringDataConversacionRenasiaRepository extends JpaRepository<ConversacionRenasiaJpaEntity, UUID> {

    /**
     * E-655: crear la conversacion sin chocar si ya esta, tambien cuando dos avisos de la misma persona la
     * crean a la vez (el scheduler publico 132 avisos para 81 personas en el mismo minuto: dos hilos veian
     * "sin conversacion" y el segundo reventaba con {@code conversaciones_renasia_pkey}, perdiendo el mensaje).
     * {@code ON CONFLICT} lo resuelve la base: si la primera transaccion todavia no confirmo, la segunda espera
     * y despues no hace nada. No se atrapa la excepcion de unicidad en Java: dentro de una transaccion de Spring
     * la deja marcada solo para rollback y el mensaje se perderia igual. Sin {@code clearAutomatically}: nadie
     * relee esta entidad en la misma transaccion.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO renaser.conversaciones_renasia (usuario_id, creado_en, actualizado_en)
            VALUES (:usuarioId, :creadoEn, :actualizadoEn)
            ON CONFLICT (usuario_id) DO NOTHING
            """, nativeQuery = true)
    int insertarSiFalta(@Param("usuarioId") UUID usuarioId, @Param("creadoEn") Instant creadoEn,
                        @Param("actualizadoEn") Instant actualizadoEn);
}
