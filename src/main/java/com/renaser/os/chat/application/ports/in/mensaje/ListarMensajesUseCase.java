package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ListarMensajesUseCase {

    /** Paginacion keyset por {@code creadoEn} (nunca OFFSET). {@code cursor} null pide la
     * pagina mas reciente. Cada mensaje viene con nombre/avatar del emisor y el preview
     * de respuesta ya resueltos (#29) — ver {@link MensajeEnriquecido}. */
    PaginaMensajes listar(UserId actorId, ConversacionId conversacionId, Instant cursor, int limite);

    record PaginaMensajes(List<MensajeEnriquecido> mensajes, Instant siguienteCursor, boolean hayMas) {
    }
}
