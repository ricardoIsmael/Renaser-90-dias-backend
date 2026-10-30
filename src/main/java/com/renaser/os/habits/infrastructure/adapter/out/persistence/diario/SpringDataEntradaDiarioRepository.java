package com.renaser.os.habits.infrastructure.adapter.out.persistence.diario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataEntradaDiarioRepository extends JpaRepository<EntradaDiarioJpaEntity, UUID> {

    Optional<EntradaDiarioJpaEntity> findByParticipanteIdAndFechaAndTipo(UUID participanteId, LocalDate fecha,
                                                                          TipoEntradaDiarioJpa tipo);

    /**
     * Rango cerrado en ambos extremos — usa `entradas_diario_perfil_fecha_idx` — SIN las de un tipo. Es lo que lee el Espejo Sombra
     * de {@code rag}: el cierre del dia (EVALUACION_DIA, spec 017) es lo que la persona le cuenta a su acompanamiento, no material
     * para que una IA lo analice.
     */
    List<EntradaDiarioJpaEntity> findByParticipanteIdAndFechaBetweenAndTipoNotOrderByFechaAsc(UUID participanteId,
                                                                                               LocalDate inicio,
                                                                                               LocalDate fin,
                                                                                               TipoEntradaDiarioJpa excluido);

    /** Las de UN tipo en un rango cerrado, por fecha (spec 017: las marcas de «dia cerrado»). */
    List<EntradaDiarioJpaEntity> findByParticipanteIdAndTipoAndFechaBetweenOrderByFechaAsc(UUID participanteId,
                                                                                            TipoEntradaDiarioJpa tipo,
                                                                                            LocalDate inicio,
                                                                                            LocalDate fin);
}
