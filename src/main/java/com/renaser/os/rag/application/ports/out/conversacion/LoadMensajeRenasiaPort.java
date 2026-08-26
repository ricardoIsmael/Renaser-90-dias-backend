package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface LoadMensajeRenasiaPort {

    /** Paginacion keyset: los {@code limite} mensajes mas recientes de la conversacion de
     * {@code usuarioId} anteriores a {@code cursor} (null = pagina mas reciente), orden
     * descendente por {@code creadoEn}. */
    List<MensajeRenasia> pagina(UserId usuarioId, Instant cursor, int limite);
}
