package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

interface SpringDataParticipacionProgramaRepository extends JpaRepository<ParticipacionProgramaJpaEntity, UUID> {

    /** D-66: pagina de participantes con el reloj del programa ACTIVADO — usada por
     * {@code AvanzarDiaProgramaUseCase} para no traer miles de filas a memoria de una
     * sola vez. Ordenada por `usuarioId` para que la paginacion sea estable entre
     * paginas (el conjunto no cambia de tamaño mientras se recorre: una fila
     * "activada" lo sigue estando siempre). */
    Page<ParticipacionProgramaJpaEntity> findByProgramaActivadoEnIsNotNull(Pageable pageable);

    /**
     * D-197: el guardado del barrido del reloj, condicionado a que el ajuste y el inicio sigan
     * siendo los leidos. JPQL y no SQL nativo para que Hibernate tipe cada parametro por su
     * atributo (el enum {@code fase_programa} y los {@code null} de la graduacion).
     *
     * <p>{@code flushAutomatically}/{@code clearAutomatically}: un UPDATE masivo no pasa por el
     * contexto de persistencia. En el barrido no hay transaccion envolvente y da igual, pero dentro
     * de una (pruebas con {@code @Transactional}) una lectura posterior devolveria la entidad vieja.
     *
     * @return filas actualizadas: 0 si alguien ajusto el dia en el medio
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE ParticipacionProgramaJpaEntity p
               SET p.diaPrograma = :#{#e.diaPrograma},
                   p.fase = :#{#e.fase},
                   p.diaProgramaAvanzadoEl = :#{#e.diaProgramaAvanzadoEl},
                   p.programaCompletado = :#{#e.programaCompletado},
                   p.programaCompletadoEn = :#{#e.programaCompletadoEn},
                   p.diaPostPrograma = :#{#e.diaPostPrograma},
                   p.actualizadoEn = :#{#e.actualizadoEn}
             WHERE p.usuarioId = :#{#e.usuarioId}
               AND p.diasAjustePrograma = :#{#e.diasAjustePrograma}
               AND p.fechaInicio = :#{#e.fechaInicio}
            """)
    int guardarAvanceDelRelojSiNoSeAjusto(@Param("e") ParticipacionProgramaJpaEntity e);
}
