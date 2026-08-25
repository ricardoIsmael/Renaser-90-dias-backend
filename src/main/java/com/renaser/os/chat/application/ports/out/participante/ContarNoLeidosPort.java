package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Map;

public interface ContarNoLeidosPort {

    /** Version EN LOTE (CLAUDE.MD del encargo: nunca N+1). Ids sin mensajes no-leidos
     * simplemente no aparecen en el mapa. */
    Map<ConversacionId, Long> contarNoLeidos(UserId usuarioId, List<ConversacionId> conversacionIds);
}
