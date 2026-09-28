package com.renaser.os.calendar.infrastructure.adapter.out.persistence.recordatorio;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataRecordatorioRepository extends JpaRepository<RecordatorioEventoJpaEntity, Long> {

    /**
     * {@code FOR UPDATE SKIP LOCKED} sobre {@code recordatorios_cola_idx} — idiom estandar de
     * Spring Data JPA/Hibernate: {@code PESSIMISTIC_WRITE} + el hint de lock timeout -2
     * (Hibernate lo traduce a SKIP LOCKED en el dialecto Postgres). Seguro con multiples
     * instancias del scheduler corriendo a la vez: cada una se lleva un lote disjunto.
     *
     * <p><b>Sin verificar todavia contra Postgres real</b> — ver LoadRecordatorioPort.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT r FROM RecordatorioEventoJpaEntity r WHERE r.enviarEn <= :hasta AND r.enviadoEn IS NULL "
            + "AND r.motivoCancelacion IS NULL ORDER BY r.enviarEn ASC")
    List<RecordatorioEventoJpaEntity> vencidosPendientes(@Param("hasta") Instant hasta, Pageable pageable);

    /**
     * <b>{@code flushAutomatically} no es decorativo (E-360, 2026-09-27).</b> {@code clearAutomatically} vacia
     * el contexto de persistencia despues del UPDATE, y vaciarlo CANCELA lo que la transaccion tenia pendiente
     * de escribir. El despacho publica {@code RecordatorioEventoDebidoEvent} en la misma transaccion, y Spring
     * Modulith guarda cada publicacion con un {@code persist} que recien se escribe al hacer flush: sin este
     * flush previo, marcar los recordatorios como enviados se llevaba todas las publicaciones del outbox sin
     * escribirlas. Los avisos salian igual (el listener se registra en memoria), pero ninguno quedaba en
     * {@code event_publication}: el que fallaba no se reintentaba nunca. Lo mismo vale para las otras tres
     * consultas de esta interfaz.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RecordatorioEventoJpaEntity r SET r.enviadoEn = :enviadoEn WHERE r.id IN :ids")
    void marcarEnviados(@Param("ids") List<Long> ids, @Param("enviadoEn") Instant enviadoEn);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RecordatorioEventoJpaEntity r SET r.motivoCancelacion = :motivo WHERE r.id IN :ids")
    int cancelarPorIds(@Param("ids") List<Long> ids, @Param("motivo") String motivo);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RecordatorioEventoJpaEntity r SET r.motivoCancelacion = :motivo "
            + "WHERE r.eventoId = :eventoId AND r.inicioOcurrencia = :inicioOcurrencia "
            + "AND r.enviadoEn IS NULL AND r.motivoCancelacion IS NULL")
    int cancelarPorOcurrencia(@Param("eventoId") UUID eventoId, @Param("inicioOcurrencia") Instant inicioOcurrencia,
                               @Param("motivo") String motivo);

    /** borrarPendientes() del repo viejo: solo lo que aun no salio, no esta cancelado, y su
     * `enviarEn` sigue en el futuro — lo que estaba a punto de despacharse se respeta. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RecordatorioEventoJpaEntity r WHERE r.eventoId = :eventoId AND r.enviadoEn IS NULL "
            + "AND r.motivoCancelacion IS NULL AND r.enviarEn > :ahora")
    int borrarPendientesFuturos(@Param("eventoId") UUID eventoId, @Param("ahora") Instant ahora);
}
