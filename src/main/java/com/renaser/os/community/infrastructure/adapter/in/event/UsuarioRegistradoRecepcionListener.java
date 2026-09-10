package com.renaser.os.community.infrastructure.adapter.in.event;

import com.renaser.os.community.application.ports.in.acompanamiento.IngresarAlGrupoDeRecepcionUseCase;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Quien se registra entra al grupo de bienvenida vigente.
 *
 * <p>Por evento y no llamando desde `users`: dar de alta a alguien no tiene por que saber que
 * existen grupos. El outbox de Modulith se encarga de que la entrada no se pierda si `community`
 * esta caido en ese momento — y de que reentregar sea seguro, para lo cual el caso de uso deriva
 * su clave de operacion del usuario y no del instante.
 */
@Component
class UsuarioRegistradoRecepcionListener {

    private final IngresarAlGrupoDeRecepcionUseCase ingresar;

    UsuarioRegistradoRecepcionListener(IngresarAlGrupoDeRecepcionUseCase ingresar) {
        this.ingresar = ingresar;
    }

    @ApplicationModuleListener
    void on(UsuarioRegistradoEvent event) {
        ingresar.ingresar(event.usuarioId());
    }
}
