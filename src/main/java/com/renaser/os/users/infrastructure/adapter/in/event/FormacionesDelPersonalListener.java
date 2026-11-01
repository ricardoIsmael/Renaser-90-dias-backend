package com.renaser.os.users.infrastructure.adapter.in.event;

import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import com.renaser.os.users.application.ports.in.formacion.MantenerFormacionesDelPersonalUseCase;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * El personal nuevo queda asignado a una formación (spec 022, FR-007): sin ninguna no vería nada. El alta y el cambio de rol ya publican sus eventos;
 * este listener solo traduce cada uno a la misma pregunta.
 */
@Component
class FormacionesDelPersonalListener {

    private final MantenerFormacionesDelPersonalUseCase mantener;

    FormacionesDelPersonalListener(MantenerFormacionesDelPersonalUseCase mantener) {
        this.mantener = mantener;
    }

    @ApplicationModuleListener
    void on(UsuarioRegistradoEvent event) {
        mantener.alDarseDeAlta(event.usuarioId());
    }

    @ApplicationModuleListener
    void on(RolDeUsuarioCambiadoEvent event) {
        mantener.alCambiarDeRol(event.usuarioId(), event.rolNuevo());
    }
}
