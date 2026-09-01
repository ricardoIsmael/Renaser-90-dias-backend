package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataParticipacionProgramaRepository extends JpaRepository<ParticipacionProgramaJpaEntity, UUID> {

    /** D-66: pagina de participantes con el reloj del programa ACTIVADO — usada por
     * {@code AvanzarDiaProgramaUseCase} para no traer miles de filas a memoria de una
     * sola vez. Ordenada por `usuarioId` para que la paginacion sea estable entre
     * paginas (el conjunto no cambia de tamaño mientras se recorre: una fila
     * "activada" lo sigue estando siempre). */
    Page<ParticipacionProgramaJpaEntity> findByProgramaActivadoEnIsNotNull(Pageable pageable);
}
