package com.renaser.os.rag.application.ports.in.conversacion;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ObtenerHistorialUseCase {

    /** Paginacion keyset por {@code creadoEn} (hay indice: `mensajes_renasia_agente_idx`,
     * nunca OFFSET). {@code cursor} null pide la pagina mas reciente. Solo los mensajes de
     * {@code agente}: los dos asistentes tienen historial propio y nunca se mezclan (D-102). */
    PaginaMensajesRenasia obtenerHistorial(UserId actorId, AgenteConversacional agente, Instant cursor, int limite);

    record PaginaMensajesRenasia(List<MensajeRenasia> mensajes, Instant siguienteCursor, boolean hayMas) {
    }
}
