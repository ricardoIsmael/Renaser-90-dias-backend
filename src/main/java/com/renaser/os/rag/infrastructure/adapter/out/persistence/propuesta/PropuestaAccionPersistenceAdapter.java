package com.renaser.os.rag.infrastructure.adapter.out.persistence.propuesta;

import com.renaser.os.rag.application.ports.out.propuesta.LoadPropuestaAccionPort;
import com.renaser.os.rag.application.ports.out.propuesta.PropuestaModificadaEnParaleloException;
import com.renaser.os.rag.application.ports.out.propuesta.SavePropuestaAccionPort;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sin {@code @Transactional} propio a proposito: cada {@code saveAndFlush} corre en la transaccion
 * corta de Spring Data y queda confirmado al volver. Es lo que necesita
 * {@code PropuestasAgenteService.confirmar}: la transicion a CONFIRMADA tiene que estar escrita
 * ANTES de ejecutar la accion, que a su vez no puede correr dentro de una transaccion larga (C-1).
 */
@Component
class PropuestaAccionPersistenceAdapter implements LoadPropuestaAccionPort, SavePropuestaAccionPort {

    private final SpringDataPropuestaAccionRepository repository;
    private final PropuestaAccionPersistenceMapper mapper;

    PropuestaAccionPersistenceAdapter(SpringDataPropuestaAccionRepository repository,
                                      PropuestaAccionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PropuestaAccion> porId(PropuestaAccionId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<PropuestaAccion> pendientesCreadasDesde(UserId participanteId, Instant desde) {
        return repository.pendientesCreadasDesde(participanteId.value(), desde).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Una version vieja la rechaza Hibernate al hacer el merge (o el {@code UPDATE ... WHERE
     * version = ?} si la carrera es mas fina), y Spring la traduce a
     * {@link OptimisticLockingFailureException}; aca se vuelve la excepcion del puerto.
     */
    @Override
    public PropuestaAccion save(PropuestaAccion propuesta) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(propuesta)));
        } catch (OptimisticLockingFailureException e) {
            throw new PropuestaModificadaEnParaleloException(
                    "La propuesta " + propuesta.id() + " cambio mientras se la resolvia", e);
        }
    }
}
