package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ContarNoLeidosPort {

    /** Version EN LOTE (CLAUDE.MD del encargo: nunca N+1). Ids sin mensajes no-leidos
     * simplemente no aparecen en el mapa. */
    Map<ConversacionId, Long> contarNoLeidos(UserId usuarioId, List<ConversacionId> conversacionIds);

    /**
     * D-221: cuántos mensajes lleva sin leer cada una de estas personas en UNA conversación, en una
     * consulta (el aviso de la comunidad va a todos). Los que no tienen nada sin leer no figuran.
     */
    Map<UserId, Long> noLeidosPorParticipante(ConversacionId conversacionId, Collection<UserId> usuarios);
}
