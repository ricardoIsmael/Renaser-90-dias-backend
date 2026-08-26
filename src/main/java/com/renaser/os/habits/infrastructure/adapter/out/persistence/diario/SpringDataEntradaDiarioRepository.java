package com.renaser.os.habits.infrastructure.adapter.out.persistence.diario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataEntradaDiarioRepository extends JpaRepository<EntradaDiarioJpaEntity, UUID> {

    Optional<EntradaDiarioJpaEntity> findByParticipanteIdAndFechaAndTipo(UUID participanteId, LocalDate fecha,
                                                                          TipoEntradaDiarioJpa tipo);

    /** Rango cerrado en ambos extremos — usa `entradas_diario_perfil_fecha_idx`. */
    List<EntradaDiarioJpaEntity> findByParticipanteIdAndFechaBetweenOrderByFechaAsc(UUID participanteId,
                                                                                     LocalDate inicio, LocalDate fin);
}
