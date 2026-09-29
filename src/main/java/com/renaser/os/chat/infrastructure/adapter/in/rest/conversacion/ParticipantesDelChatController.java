package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Los integrantes de una conversación, para la info del chat: de cualquier tipo, con la regla de quién la ve. */
@RestController
@RequestMapping("/api/v1/chat/conversations/{id}/participants")
public class ParticipantesDelChatController {

    private final VerParticipantesDeConversacionUseCase participantes;

    public ParticipantesDelChatController(VerParticipantesDeConversacionUseCase participantes) {
        this.participantes = participantes;
    }

    /** 403 si quien pide no puede ver la conversación o su cuenta está suspendida; 404 si no existe. */
    @RequiresPermission(value = Permission.USE_APP, scope = "quien puede ver la conversación")
    @GetMapping
    public ParticipantesDelChatResponse listar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        ConversacionId conversacion = ConversacionId.of(id);
        return ParticipantesDelChatResponse.from(conversacion, participantes.ver(actorId, conversacion, q, page, size));
    }
}
