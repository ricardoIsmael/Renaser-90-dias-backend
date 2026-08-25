package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;

interface SpringDataRegistroHabitoRepository extends JpaRepository<RegistroHabitoJpaEntity, UUID> {

    /**
     * Bloqueo pesimista para el camino de ESCRITURA (mismo patron que
     * {@code SpringDataPuntajeParticipanteRepository.findByIdParaEscritura}). Sin el, seis
     * requests concurrentes sobre el mismo registro leian todas PENDIENTE, todas pasaban la
     * validacion del dominio y todas completaban — verificado en vivo: 6 llamadas paralelas
     * devolvian 200, mientras que la 7a secuencial devolvia 409. Con un habito que otorga
     * puntos eso serian seis pagos por una sola accion del aprendiz.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RegistroHabitoJpaEntity r WHERE r.id = :id")
    Optional<RegistroHabitoJpaEntity> findByIdParaEscritura(@Param("id") UUID id);

    Optional<RegistroHabitoJpaEntity> findByParticipanteIdAndHabitoIdAndFechaEjecucion(UUID participanteId,
                                                                                        UUID habitoId,
                                                                                        LocalDate fechaEjecucion);

    List<RegistroHabitoJpaEntity> findByParticipanteIdAndFechaEjecucion(UUID participanteId, LocalDate fechaEjecucion);

    List<RegistroHabitoJpaEntity> findByEstadoAndFechaEjecucionLessThan(EstadoRegistroJpa estado, LocalDate fecha);

    /**
     * D-43 (docs/MODULOS_A_AVANZAR.md §8): UNA sola consulta para TODOS los participantes
     * pedidos — agrega por (participante, dia) los conteos crudos que
     * {@code ConteoDiarioHabitos}/{@code PorcentajeHabitos} (domain) necesitan para aplicar
     * la regla de negocio. El enum se pasa como parametro bindeado (no como literal JPQL) por
     * el mismo motivo que documenta {@code SpringDataRankingAprendizRepository}: evita
     * problemas de cast contra el tipo enum nativo de Postgres.
     */
    @Query("""
            SELECT r.participanteId AS participanteId,
                   r.fechaEjecucion AS fecha,
                   COUNT(r.id) AS totalRegistros,
                   SUM(CASE WHEN r.estado = :completado THEN 1L ELSE 0L END) AS completados,
                   SUM(CASE WHEN r.esOpcional = true AND r.estado <> :completado THEN 1L ELSE 0L END) AS opcionalesNoCompletados
            FROM RegistroHabitoJpaEntity r
            WHERE r.participanteId IN :participantes
              AND r.fechaEjecucion BETWEEN :desde AND :hasta
            GROUP BY r.participanteId, r.fechaEjecucion
            """)
    List<ConteoDiarioHabitosProjection> contarPorParticipanteYDiaEnRango(@Param("participantes") Collection<UUID> participantes,
                                                                           @Param("desde") LocalDate desde,
                                                                           @Param("hasta") LocalDate hasta,
                                                                           @Param("completado") EstadoRegistroJpa completado);
}
