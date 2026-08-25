package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataTicketMentorRepository extends JpaRepository<TicketMentorJpaEntity, UUID> {

    List<TicketMentorJpaEntity> findByParticipanteIdAndCreadoEnBeforeOrderByCreadoEnDesc(UUID participanteId,
            Instant cursor, Limit limit);

    List<TicketMentorJpaEntity> findByParticipanteIdOrderByCreadoEnDesc(UUID participanteId, Limit limit);

    List<TicketMentorJpaEntity> findByCreadoEnBeforeOrderByCreadoEnDesc(Instant cursor, Limit limit);

    List<TicketMentorJpaEntity> findByOrderByCreadoEnDesc(Limit limit);

    @Query(nativeQuery = true, value = """
            select descripcion_bloqueo as descripcionBloqueo, respuesta_mentor as respuestaMentor
            from renaser.tickets_mentor
            where guardado_en_biblioteca
              and to_tsvector('spanish', descripcion_bloqueo || ' ' || coalesce(respuesta_mentor, ''))
                  @@ plainto_tsquery('spanish', :q)
            order by ts_rank(
                  to_tsvector('spanish', descripcion_bloqueo || ' ' || coalesce(respuesta_mentor, '')),
                  plainto_tsquery('spanish', :q)) desc
            limit :limite
            """)
    List<BibliotecaFtsRow> buscarEnBiblioteca(@Param("q") String query, @Param("limite") int limite);
}
