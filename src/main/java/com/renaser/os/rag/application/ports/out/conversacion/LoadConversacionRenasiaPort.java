package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadConversacionRenasiaPort {

    /** La identidad de la conversacion ES el usuario (PK = FK, 1:1 real). */
    Optional<ConversacionRenasia> porUsuarioId(UserId usuarioId);
}
