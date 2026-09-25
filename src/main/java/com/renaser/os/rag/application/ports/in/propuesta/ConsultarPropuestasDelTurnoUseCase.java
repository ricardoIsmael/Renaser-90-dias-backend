package com.renaser.os.rag.application.ports.in.propuesta;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Las propuestas que nacieron durante un turno de conversacion, para mandarlas por el SSE como
 * evento {@code propuesta} antes del {@code fin} (fase 2, D-153).
 *
 * <p>Por que se consultan al terminar el turno y no se emiten desde la herramienta: la
 * herramienta corre dentro del bucle del proveedor de IA, que no tiene acceso al stream. Guardada
 * la propuesta, el turno la recoge al final con el mismo patron que ya usan las fuentes.
 */
public interface ConsultarPropuestasDelTurnoUseCase {

    /** Pendientes de {@code actorId} creadas en {@code desde} o despues, de la mas vieja a la mas nueva. */
    List<PropuestaCreada> pendientesCreadasDesde(UserId actorId, Instant desde);
}
