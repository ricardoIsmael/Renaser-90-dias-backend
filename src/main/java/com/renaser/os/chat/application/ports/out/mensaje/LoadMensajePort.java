package com.renaser.os.chat.application.ports.out.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LoadMensajePort {

    Optional<Mensaje> porId(MensajeId id);

    /** Paginacion keyset: los {@code limite} mensajes mas recientes anteriores a
     * {@code cursor} (null = pagina mas reciente), orden descendente por {@code creadoEn}. */
    List<Mensaje> pagina(ConversacionId conversacionId, Instant cursor, int limite);

    /** Version EN LOTE del ultimo mensaje por conversacion (una sola consulta — nunca N+1).
     * Ids sin ningun mensaje no aparecen en el mapa. */
    Map<ConversacionId, Mensaje> ultimosPorConversacion(List<ConversacionId> conversacionIds);
}
