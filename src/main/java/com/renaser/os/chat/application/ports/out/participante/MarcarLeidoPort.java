package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface MarcarLeidoPort {

    /**
     * Mueve la marca de lectura del participante hasta {@code ahora}, y <b>solo hacia adelante</b>
     * (D-208): de esa marca sale el ✓✓ de los mensajes de los demás, y un ✓✓ no puede volver a ✓
     * porque una lectura llegó tarde. Si no es participante, no hace nada.
     */
    void marcarLeido(ConversacionId conversacionId, UserId usuarioId, Instant ahora);
}
