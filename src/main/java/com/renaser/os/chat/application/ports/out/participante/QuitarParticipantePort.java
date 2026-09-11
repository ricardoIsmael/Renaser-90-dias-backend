package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

public interface QuitarParticipantePort {

    /**
     * Saca a alguien de la conversación. <b>Los mensajes que escribió NO se borran</b>: la
     * conversación es del grupo y su historia también. Solo deja de estar en la lista y de
     * recibir lo nuevo (plan.md §6).
     */
    void quitar(ConversacionId conversacionId, UserId usuarioId);
}
