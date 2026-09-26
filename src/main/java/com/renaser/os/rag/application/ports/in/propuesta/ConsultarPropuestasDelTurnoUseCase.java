package com.renaser.os.rag.application.ports.in.propuesta;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase.PropuestaCreada;
import com.renaser.os.rag.domain.model.conversacion.DestinoDeEvidencia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

    /**
     * D-171: las tarjetas de camara que pidio {@code proponer_registrar_con_foto} (o, desde D-178,
     * {@code proponer_registrar_accion_con_foto}) en {@code desde} o
     * despues, de la mas vieja a la mas nueva y una sola por registro. Mismo momento de consulta que
     * las propuestas, pero no salen de la base: no hay nada que confirmar en el servidor.
     */
    List<PedidoDeEvidencia> evidenciasPedidasDesde(UserId actorId, Instant desde);

    /**
     * La tarjeta de la camara para un habito de hoy que exige evidencia o, desde D-178, para una
     * accion del dia (roca diaria).
     *
     * @param registroId  el registro del dia del habito, o el id de la roca diaria si
     *                    {@code destino} es {@link DestinoDeEvidencia#ROCA}
     * @param venceEn     el fin del dia local de la persona (despues, ese registro ya no es el de hoy)
     * @param conPregunta si la app pregunta "¿Que sentiste?" despues de la foto: solo en los rituales
     *                    (D-172). Una accion del dia nunca pregunta
     */
    record PedidoDeEvidencia(UUID registroId, String titulo, Instant pedidoEn, Instant venceEn,
                             boolean conPregunta, DestinoDeEvidencia destino) {

        public PedidoDeEvidencia {
            Objects.requireNonNull(destino, "destino no puede ser null");
        }

        /** El de un habito, como antes de D-178. */
        public PedidoDeEvidencia(UUID registroId, String titulo, Instant pedidoEn, Instant venceEn,
                                 boolean conPregunta) {
            this(registroId, titulo, pedidoEn, venceEn, conPregunta, DestinoDeEvidencia.HABITO);
        }
    }
}
