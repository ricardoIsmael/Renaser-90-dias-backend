package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

public interface EsParticipantePort {

    boolean esParticipante(ConversacionId conversacionId, UserId usuarioId);
}
