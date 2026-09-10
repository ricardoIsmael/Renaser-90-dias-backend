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
     * @param otroParticipante         con quien habla el actor cuando la conversacion es DIRECTA;
     *                                 {@code null} en grupos, que ya tienen nombre propio.
     * @param otroParticipanteNombre   su nombre, RESUELTO AQUI.
     *
     * <p>El nombre viaja junto al id y no se deja que el cliente lo busque en el directorio
     * ({@code GET /chat/members}), como estaba al principio. Ese directorio exige que exista la
     * conversacion GLOBAL —{@code MiembroService.requireGlobal}— y donde no existe devuelve 404:
     * el listado de mensajes directos quedaba sin nombres por culpa de OTRA conversacion que no
     * tiene nada que ver. Una bandeja de DMs tiene que poder nombrarse sola.
     */
    record ConversacionResumen(Conversacion conversacion, Mensaje ultimoMensaje, long noLeidos,
                                UserId otroParticipante, String otroParticipanteNombre,
                                String otroParticipanteAvatar) {
    }
}
