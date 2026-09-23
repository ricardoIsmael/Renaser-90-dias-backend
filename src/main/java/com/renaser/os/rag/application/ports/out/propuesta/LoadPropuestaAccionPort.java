package com.renaser.os.rag.application.ports.out.propuesta;

import com.renaser.os.rag.domain.model.propuesta.PropuestaAccion;
import com.renaser.os.rag.domain.model.propuesta.PropuestaAccionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lectura de las propuestas del acompanante (D-153). */
public interface LoadPropuestaAccionPort {

    Optional<PropuestaAccion> porId(PropuestaAccionId id);

    /** Las PENDIENTE de {@code participanteId} creadas en {@code desde} o despues, de la mas vieja a la mas nueva. */
    List<PropuestaAccion> pendientesCreadasDesde(UserId participanteId, Instant desde);
}
