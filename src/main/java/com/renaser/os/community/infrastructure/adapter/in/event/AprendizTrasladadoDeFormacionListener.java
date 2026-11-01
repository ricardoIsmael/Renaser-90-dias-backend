package com.renaser.os.community.infrastructure.adapter.in.event;

import com.renaser.os.community.application.ports.in.acompanamiento.ReubicarAlTrasladarDeFormacionUseCase;
import com.renaser.os.users.api.AprendizTrasladadoDeFormacionEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Pasar a un aprendiz de formación lo saca de sus grupos y lo entra a la bienvenida de la nueva (spec 022). Por evento: {@code users} no sabe que
 * existen grupos, y el outbox de Modulith hace que no se pierda si {@code community} está caído.
 */
@Component
class AprendizTrasladadoDeFormacionListener {

    private final ReubicarAlTrasladarDeFormacionUseCase reubicar;

    AprendizTrasladadoDeFormacionListener(ReubicarAlTrasladarDeFormacionUseCase reubicar) {
        this.reubicar = reubicar;
    }

    @ApplicationModuleListener
    void on(AprendizTrasladadoDeFormacionEvent event) {
        reubicar.reubicar(event.aprendizId(), event.hacia(), event.occurredAt());
    }
}
