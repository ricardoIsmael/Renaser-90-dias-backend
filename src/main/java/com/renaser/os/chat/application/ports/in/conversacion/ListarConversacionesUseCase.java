package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ListarConversacionesUseCase {

    /** Mis conversaciones, con el ultimo mensaje y el conteo de no-leidos resueltos en
     * lote (nunca N+1 — CLAUDE.MD del encargo). Orden: mas actividad reciente primero. */
    List<ConversacionResumen> listar(UserId actorId);

    /** {@code ultimoMensaje} es null si la conversacion todavia no tiene ningun mensaje. */
    /**
     * @param otroParticipante con quien habla el actor cuando la conversacion es DIRECTA; {@code
     *                         null} en grupos, que ya tienen nombre propio. Sin este dato el
     *                         cliente solo podia nombrar la conversacion si el ultimo mensaje era
     *                         del otro, y una bandeja llena de "Conversacion directa" no se usa.
     */
    record ConversacionResumen(Conversacion conversacion, Mensaje ultimoMensaje, long noLeidos,
                                UserId otroParticipante) {
    }
}
