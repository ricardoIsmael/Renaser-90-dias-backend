package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface LoadMensajeRenasiaPort {

    /** Paginacion keyset: los {@code limite} mensajes mas recientes que {@code usuarioId}
     * intercambio con {@code agente}, anteriores a {@code cursor} (null = pagina mas reciente),
     * orden descendente por {@code creadoEn}. Nunca devuelve mensajes del otro agente (D-102). */
    List<MensajeRenasia> pagina(UserId usuarioId, AgenteConversacional agente, Instant cursor, int limite);
}
