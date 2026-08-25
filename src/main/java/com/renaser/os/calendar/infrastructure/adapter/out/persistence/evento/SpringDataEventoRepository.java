package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataEventoRepository extends JpaRepository<EventoJpaEntity, UUID> {

    /** findEventsInRange() del repo viejo: candidatos que PODRIAN tener una ocurrencia en
     * [desde,hasta] — el podado fino (incluida `repeticiones`) lo hace ExpansorOcurrencias. */
    @Query(value = """
            SELECT e.* FROM renaser.eventos e
            WHERE e.estado = 'PUBLICADO'
              AND e.inicia_en <= :hasta
              AND (
                (NOT EXISTS (SELECT 1 FROM renaser.recurrencias_evento r WHERE r.evento_id = e.id) AND e.inicia_en >= :desde)
                OR EXISTS (SELECT 1 FROM renaser.recurrencias_evento r WHERE r.evento_id = e.id AND (r.hasta IS NULL OR r.hasta >= :desde))
              )
            ORDER BY e.inicia_en ASC
            """, nativeQuery = true)
    List<EventoJpaEntity> candidatosParaVisor(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** generar() del repo viejo: PUBLICADO y (arranca en la ventana O es recurrente O
     * notifica-al-crear y se creo hace poco). */
    @Query(value = """
            SELECT e.* FROM renaser.eventos e
            WHERE e.estado = 'PUBLICADO'
              AND (
                (e.inicia_en BETWEEN :ahora AND :hastaMax)
                OR EXISTS (SELECT 1 FROM renaser.recurrencias_evento r WHERE r.evento_id = e.id)
                OR (e.notificar_al_crear = true AND e.creado_en >= :desdeAnuncio)
              )
            """, nativeQuery = true)
    List<EventoJpaEntity> candidatosParaRecordatorios(@Param("ahora") Instant ahora, @Param("hastaMax") Instant hastaMax,
                                                        @Param("desdeAnuncio") Instant desdeAnuncio);
}
