package com.renaser.os.leadership.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataObservacionMentorRepository extends JpaRepository<ObservacionMentorJpaEntity, UUID> {

    Optional<ObservacionMentorJpaEntity> findByAutorIdAndClaveOperacion(UUID autorId, String claveOperacion);

    List<ObservacionMentorJpaEntity> findByMentorIdOrderByCreadoEnDescIdDesc(UUID mentorId, Limit limit);

    List<ObservacionMentorJpaEntity> findByMentorIdAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(UUID mentorId,
                                                                                            Instant antesDe,
                                                                                            Limit limit);

    @Query("""
            select o.mentorId as mentorId, o.tipo as tipo, count(o) as cantidad
            from ObservacionMentorJpaEntity o
            where o.creadoEn >= :desde and o.creadoEn < :hasta
            group by o.mentorId, o.tipo
            """)
    List<ConteoPorTipo> contarPorMentorYTipo(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    interface ConteoPorTipo {
        UUID getMentorId();

        String getTipo();

        long getCantidad();
    }
}
