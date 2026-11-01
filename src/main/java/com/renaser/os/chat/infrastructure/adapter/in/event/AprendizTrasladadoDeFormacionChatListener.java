package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.CambiarDeComunidadAlTrasladarUseCase;
import com.renaser.os.users.api.AprendizTrasladadoDeFormacionEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Pasar a un aprendiz de formación lo saca de la comunidad general y del soporte de la anterior y lo entra a los de la nueva (spec 024). Por evento:
 * {@code users} no sabe que existen chats, y el outbox de Modulith hace que no se pierda si {@code chat} está caído.
 */
@Component
class AprendizTrasladadoDeFormacionChatListener {

    private final CambiarDeComunidadAlTrasladarUseCase cambiar;

    AprendizTrasladadoDeFormacionChatListener(CambiarDeComunidadAlTrasladarUseCase cambiar) {
        this.cambiar = cambiar;
    }

    @ApplicationModuleListener
    void on(AprendizTrasladadoDeFormacionEvent event) {
        cambiar.alTrasladar(event.aprendizId(), event.desde(), event.hacia());
    }
}
