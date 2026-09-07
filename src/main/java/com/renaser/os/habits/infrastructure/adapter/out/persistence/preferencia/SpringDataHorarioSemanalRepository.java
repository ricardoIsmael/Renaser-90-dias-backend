package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataHorarioSemanalRepository
        extends JpaRepository<HorarioSemanalJpaEntity, HorarioSemanalPk> {

    /**
     * Lo que rige para ESE dia de la semana, para varios habitos de un participante. UNA consulta:
     * la llama el barrido nocturno, que recorre todo el padron.
     */
    List<HorarioSemanalJpaEntity> findByParticipanteIdAndHabitoIdInAndDiaSemana(
            UUID participanteId, Collection<UUID> habitoIds, Short diaSemana);

    /** Los siete dias de un habito, para pintar la pantalla de una sola vez. */
    List<HorarioSemanalJpaEntity> findByParticipanteIdAndHabitoId(UUID participanteId, UUID habitoId);

    /**
     * Los que el aprendiz apago para ESE dia de la semana (V40). Solo ids y en UNA consulta: la
     * llama el barrido nocturno, que recorre todo el padron.
     */
    @Query("SELECT h.habitoId FROM HorarioSemanalJpaEntity h "
            + "WHERE h.participanteId = :participanteId AND h.diaSemana = :diaSemana AND h.activo = false")
    List<UUID> apagadosEnDiaSemana(UUID participanteId, Short diaSemana);
}
