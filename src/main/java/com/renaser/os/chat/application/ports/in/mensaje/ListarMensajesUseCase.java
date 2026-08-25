package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ListarMensajesUseCase {

    /** Paginacion keyset por {@code creadoEn} (nunca OFFSET). {@code cursor} null pide la
     * pagina mas reciente. */
    PaginaMensajes listar(UserId actorId, ConversacionId conversacionId, Instant cursor, int limite);

    record PaginaMensajes(List<Mensaje> mensajes, Instant siguienteCursor, boolean hayMas) {
    }
}
