package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataHorarioPorFechaRepository extends JpaRepository<HorarioPorFechaJpaEntity, HorarioPorFechaPk> {
    List<HorarioPorFechaJpaEntity> findByParticipanteIdAndHabitoIdInAndFecha(
            UUID participanteId, Collection<UUID> habitoIds, LocalDate fecha);

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
}
