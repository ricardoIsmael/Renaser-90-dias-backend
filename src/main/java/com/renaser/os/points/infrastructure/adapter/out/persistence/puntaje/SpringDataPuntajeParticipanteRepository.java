package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface SpringDataPuntajeParticipanteRepository extends JpaRepository<PuntajeParticipanteJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PuntajeParticipanteJpaEntity p WHERE p.participanteId = :id")
    Optional<PuntajeParticipanteJpaEntity> findByIdParaEscritura(@Param("id") UUID id);

    /**
     * C-12: a diferencia de {@code save()} (merge, compite por PK con otro INSERT concurrente
     * sobre una fila inexistente), esto resuelve la carrera en la restriccion UNIQUE de
     * Postgres. {@code clearAutomatically}: la fila recien insertada (o la de quien gano la
     * carrera) todavia no esta en el contexto de persistencia de esta transaccion, pero se
     * limpia por prolijidad ante cualquier lectura posterior por otra via.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO renaser.puntajes_participante
                (participante_id, coherencia, puntos_liga, racha_actual, racha_maxima, actualizado_en)
            VALUES (:id, :coherencia, :puntosLiga, CAST(:rachaActual AS smallint), CAST(:rachaMaxima AS smallint),
                    :actualizadoEn)
            ON CONFLICT (participante_id) DO NOTHING
            """, nativeQuery = true)
    void insertarInicialSiFalta(@Param("id") UUID id, @Param("coherencia") BigDecimal coherencia,
                                 @Param("puntosLiga") int puntosLiga, @Param("rachaActual") int rachaActual,
                                 @Param("rachaMaxima") int rachaMaxima, @Param("actualizadoEn") Instant actualizadoEn);
}
