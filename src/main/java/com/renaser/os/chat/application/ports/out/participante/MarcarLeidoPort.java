package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface MarcarLeidoPort {

    void marcarLeido(ConversacionId conversacionId, UserId usuarioId, Instant ahora);
}
