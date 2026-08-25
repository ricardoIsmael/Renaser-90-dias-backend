package com.renaser.os.chat.application.ports.out.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadConversacionPort {

    Optional<Conversacion> porId(ConversacionId id);

    Optional<Conversacion> porClaveDirecta(String claveDirecta);

    Optional<Conversacion> porCelulaId(UUID celulaId);

    Optional<Conversacion> global();

    /** Todas las conversaciones donde {@code usuarioId} es participante. */
    List<Conversacion> misConversaciones(UserId usuarioId);
}
