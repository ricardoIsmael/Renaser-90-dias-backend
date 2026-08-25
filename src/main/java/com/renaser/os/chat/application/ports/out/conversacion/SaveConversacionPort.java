package com.renaser.os.chat.application.ports.out.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;

public interface SaveConversacionPort {

    Conversacion save(Conversacion conversacion);
}
