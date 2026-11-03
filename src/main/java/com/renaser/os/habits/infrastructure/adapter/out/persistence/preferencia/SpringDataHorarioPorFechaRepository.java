package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataHorarioPorFechaRepository extends JpaRepository<HorarioPorFechaJpaEntity, HorarioPorFechaPk> {
    List<HorarioPorFechaJpaEntity> findByParticipanteIdAndHabitoIdInAndFecha(
            UUID participanteId, Collection<UUID> habitoIds, LocalDate fecha);

    /** H-01: la excepcion de ESA fecha para UN habito, de varias personas, en una consulta. */
    List<HorarioPorFechaJpaEntity> findByParticipanteIdInAndHabitoIdAndFecha(
            Collection<UUID> participanteIds, UUID habitoId, LocalDate fecha);

    @Query("SELECT DISTINCT h.habitoId FROM HorarioPorFechaJpaEntity h "
            + "WHERE h.participanteId = :participanteId AND h.fecha BETWEEN :desde AND :hasta")
    List<UUID> habitosEntre(UUID participanteId, LocalDate desde, LocalDate hasta);

    /**
     * Los que el aprendiz apago ESE dia (V38). Devuelve solo ids y en UNA consulta a proposito: la
     * llama el barrido nocturno, que recorre todo el padron.
     */
    @Query("SELECT h.habitoId FROM HorarioPorFechaJpaEntity h "
            + "WHERE h.participanteId = :participanteId AND h.fecha = :fecha AND h.activo = false")
    List<UUID> apagadosEn(UUID participanteId, LocalDate fecha);

    /** Spec 028: todas las fechas propias de la persona en un rango (una consulta para todos sus habitos). */
    List<HorarioPorFechaJpaEntity> findByParticipanteIdAndFechaBetween(UUID participanteId, LocalDate desde, LocalDate hasta);

    List<HorarioPorFechaJpaEntity> findByParticipanteIdAndHabitoIdAndFechaBetween(
            UUID participanteId, UUID habitoId, LocalDate desde, LocalDate hasta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM HorarioPorFechaJpaEntity h "
            + "WHERE h.participanteId = :participanteId AND h.fecha BETWEEN :desde AND :hasta")
    int borrarEntre(UUID participanteId, LocalDate desde, LocalDate hasta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM HorarioPorFechaJpaEntity h WHERE h.participanteId = :participanteId "
            + "AND h.habitoId = :habitoId AND h.fecha BETWEEN :desde AND :hasta")
    int borrarDeUnHabitoEntre(UUID participanteId, UUID habitoId, LocalDate desde, LocalDate hasta);

    /** H-06: borra de una vez las excepciones de UN habito en esas fechas exactas (antes, un `existsById` + `deleteById` + `flush` por fecha). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM HorarioPorFechaJpaEntity h WHERE h.participanteId = :participanteId "
            + "AND h.habitoId = :habitoId AND h.fecha IN :fechas")
    int borrarDeUnHabitoEn(UUID participanteId, UUID habitoId, Collection<LocalDate> fechas);
}
