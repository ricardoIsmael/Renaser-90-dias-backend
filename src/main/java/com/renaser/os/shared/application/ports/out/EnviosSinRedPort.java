package com.renaser.os.shared.application.ports.out;

import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spec 048 (FR-008, FR-016): los envíos de lo hecho sin red que ya llegaron —aplicados o no—, para registrar cada uno una sola vez
 * y devolver lo mismo si se repite. Una sola tabla para todas las áreas ({@code envios_sin_red}, V188 y V190). Vive en el núcleo
 * compartido porque lo usan {@code habits} y {@code rocks}.
 */
public interface EnviosSinRedPort {

    /**
     * Que dos pedidos con el mismo {@code idCliente} se turnen: toma un candado de la transacción en curso
     * ({@code pg_advisory_xact_lock}) que se suelta solo al terminar. Tiene que llamarse dentro de una transacción.
     */
    void tomarElCandado(UUID idCliente);

    /** Si ese envío de esa persona ya llegó: cómo quedó. Un envío de otra persona con el mismo id no se ve. */
    Optional<EnvioRegistrado> buscar(UUID idCliente, UserId participanteId);

    /** Lo recuerda. Si otro pedido con el mismo id ganó la carrera, no hace nada (el resultado ya es el mismo). */
    void registrar(EnvioNuevo envio);

    /** El envío de un hábito (fases 1 y 2): aplicado, con el registro y, si quedó sin puntos, el motivo. */
    default void registrar(UUID idCliente, UserId participanteId, UUID registroId, String motivoSinPuntos) {
        registrar(new EnvioNuevo(idCliente, participanteId, "COMPLETAR_HABITO", null, registroId, motivoSinPuntos, null, null,
                null, null));
    }

    /**
     * @param noAplicado nulo si se aplicó; si no, el código con el que se respondió el 409 (y se repite igual)
     * @param mensaje    el texto del 409 (solo si no se aplicó)
     */
    record EnvioRegistrado(UUID registroId, String motivoSinPuntos, CodigoNoAplicado noAplicado, String mensaje) {

        /** Un envío aplicado, como los de hábitos. */
        public EnvioRegistrado(UUID registroId, String motivoSinPuntos) {
            this(registroId, motivoSinPuntos, null, null);
        }

        public boolean aplicado() {
            return noAplicado == null;
        }
    }

    /**
     * @param tipo     qué se hizo ({@code CIERRE_DEL_DIA}, {@code PLAN_AGENDAR}…); nunca un texto de la persona
     * @param recurso  la clave de lo que tocó ({@code roca-diaria:<uuid>}, {@code cierre:<fecha>}…), o {@code null}
     * @param registroId solo para hábitos
     */
    record EnvioNuevo(UUID idCliente, UserId participanteId, String tipo, String recurso, UUID registroId, String motivoSinPuntos,
                      CodigoNoAplicado noAplicado, String mensaje, Instant hechoEn, OrigenDeLaHora origenDeLaHora) {
    }
}
