package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.RolEnElChat;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Los integrantes de UNA conversación, para la «Info. del chat» de la app (pedido del dueño del 29/09:
 * «debe de salir para todos»). Los ve quien puede ver la conversación, con la regla de todo el módulo
 * ({@link AutorizarAccesoAConversacionUseCase}); a quien no, se le responde igual que en el resto del chat.
 *
 * <p>Quién es integrante, según el tipo, con la misma regla con la que se decide quién recibe un aviso:
 * <ul>
 *   <li><b>comunidad y 1 a 1:</b> sus participantes;</li>
 *   <li><b>grupo:</b> quienes pertenecen HOY al grupo (el mentor que rotó no aparece);</li>
 *   <li><b>soporte:</b> el aprendiz y quienes son ADMIN/ALCHEMIST ahora.</li>
 * </ul>
 * Solo cuentas activas. Nunca correo ni teléfono.
 */
public interface VerParticipantesDeConversacionUseCase {

    /**
     * @param consulta texto a buscar en el nombre (sin distinguir mayúsculas ni tildes); vacío = todos
     * @param pagina   desde 0
     * @param tamano   se acota a [1, 200]
     * @throws java.util.NoSuchElementException                    si la conversación no existe (404)
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si quien pide no puede verla o su
     *                                                             cuenta está suspendida (403)
     */
    PaginaDeParticipantes ver(UserId actorId, ConversacionId conversacionId, String consulta, int pagina, int tamano);

    /**
     * @param llevaTarjeta si se muestra con su tarjeta con nombre (D-206), que solo existe en grupos y soportes
     * @param avatarUrl    la foto que subió, para las conversaciones donde no hay tarjetas
     */
    record ParticipanteDelChat(UserId userId, String nombre, RolEnElChat rol, boolean esUnoMismo,
                               boolean llevaTarjeta, String avatarUrl) {
    }

    /** @param total cuántos son (con la búsqueda aplicada), no cuántos trae esta página */
    record PaginaDeParticipantes(List<ParticipanteDelChat> participantes, int total, int pagina, int tamano) {
    }
}
