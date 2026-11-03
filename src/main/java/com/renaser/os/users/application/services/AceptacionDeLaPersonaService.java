package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.ConsultarAceptacionDeLaPersonaUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Lo que se eligió al aceptar a una persona, leído de la foto que guarda su solicitud (V140 y V142). Solo lectura: lo que la persona ES hoy (su formación, su
 * duración, su rol) vive en otras tablas y puede haber cambiado; esto es lo que se decidió ESE día.
 */
@Service
class AceptacionDeLaPersonaService implements ConsultarAceptacionDeLaPersonaUseCase {

    private final RequireAdminGuard requireAdminGuard;
    private final LoadAccountRequestPort solicitudes;
    private final LoadUserPort usuarios;

    AceptacionDeLaPersonaService(RequireAdminGuard requireAdminGuard, LoadAccountRequestPort solicitudes, LoadUserPort usuarios) {
        this.requireAdminGuard = requireAdminGuard;
        this.solicitudes = solicitudes;
        this.usuarios = usuarios;
    }

    @Override
    public Optional<AceptacionDeLaPersona> de(UserId actor, UserId persona) {
        requireAdminGuard.requireAdminActivo(actor);
        return solicitudes.aceptadaDe(persona).filter(solicitud -> solicitud.eleccion() != null).map(this::conQuienLaAcepto);
    }

    private AceptacionDeLaPersona conQuienLaAcepto(AccountRequest solicitud) {
        String quien = solicitud.reviewedBy() == null ? null : usuarios.byId(solicitud.reviewedBy()).map(User::fullName).orElse(null);
        return new AceptacionDeLaPersona(solicitud.eleccion(), quien, solicitud.reviewedAt());
    }
}
