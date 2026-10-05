package com.renaser.os.chat.domain.model.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;

import java.util.Objects;
import java.util.Optional;

/**
 * El mensaje al que responde uno nuevo: «Responder» en el menú de un mensaje del chat (D-251, pedido del dueño
 * del 2026-10-05). Solo se construye con {@link #aResponder}, que es donde viven las reglas, así que
 * {@link Mensaje#escribir} no puede recibir una cita sin validar.
 *
 * <p>Las reglas:
 * <ul>
 *   <li><b>El citado existe y es de la MISMA conversación.</b> Las dos faltas se contestan con el mismo
 *       mensaje, a propósito: si «no existe» y «es de otra conversación» dijeran cosas distintas, el error
 *       contaría si un id existe en un chat ajeno. Antes de D-251 el primer caso era un 404 y el segundo un
 *       400 (en {@code MensajeService}).</li>
 *   <li><b>Quien responde puede verlo.</b> Que sea participante de la conversación ya lo exigió el caso de uso
 *       antes de llegar acá (403 si no); dentro de una conversación todos ven todos sus mensajes, así que
 *       alcanza con que el citado sea de esa conversación y siga a la vista ({@link Mensaje#sigueALaVista}):
 *       no se responde a lo que su autor borró ni a lo que retiró la moderación.</li>
 * </ul>
 * Todas las faltas son {@link IllegalArgumentException} (400): el pedido está mal formado para esta
 * conversación, no es un problema de permisos.
 */
public final class Cita {

    static final String NO_ESTA_EN_LA_CONVERSACION = "El mensaje que quieres responder no está en esta conversación";
    static final String YA_NO_SE_PUEDE_RESPONDER = "Ese mensaje fue eliminado y ya no se puede responder";

    private final Mensaje citado;

    private Cita(Mensaje citado) {
        this.citado = citado;
    }

    /**
     * @param pedido          el id que mandó la app ({@code replyToId})
     * @param encontrado      lo que encontró el puerto con ese id (vacío si no existe)
     * @param dondeSeResponde la conversación del mensaje nuevo
     */
    public static Cita aResponder(MensajeId pedido, Optional<Mensaje> encontrado, ConversacionId dondeSeResponde) {
        Objects.requireNonNull(pedido, "el id del mensaje citado es obligatorio");
        Mensaje citado = encontrado
                .filter(mensaje -> mensaje.id().equals(pedido) && mensaje.conversacionId().equals(dondeSeResponde))
                .orElseThrow(() -> new IllegalArgumentException(NO_ESTA_EN_LA_CONVERSACION));
        if (!citado.sigueALaVista()) {
            throw new IllegalArgumentException(YA_NO_SE_PUEDE_RESPONDER);
        }
        return new Cita(citado);
    }

    /**
     * Al leer: si {@code citado} se puede mostrar como la cita de {@code respuesta}. Es el que ella cita, es de
     * la MISMA conversación y sigue a la vista. Es lo que impide que una fila mal escrita (la base no exige la
     * misma conversación, V92) muestre en un chat el texto de otro. {@code false} si no es respuesta o si el
     * citado no se encontró ({@code null}, se borró): la app dice «Mensaje eliminado».
     */
    public static boolean sePuedeMostrar(Mensaje respuesta, Mensaje citado) {
        return respuesta.esRespuesta() && citado != null && citado.id().equals(respuesta.respuestaAId())
                && citado.conversacionId().equals(respuesta.conversacionId()) && citado.sigueALaVista();
    }

    /** El mensaje citado tal como estaba al responder: de él sale el resumen de la respuesta de enviar. */
    public Mensaje citado() {
        return citado;
    }

    boolean esDe(ConversacionId conversacion) {
        return citado.conversacionId().equals(conversacion);
    }

    @Override
    public String toString() {
        return "Cita[" + citado.id() + "]";
    }
}
