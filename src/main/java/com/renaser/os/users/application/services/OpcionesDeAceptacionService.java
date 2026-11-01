package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.application.ports.in.accountrequest.ConsultarOpcionesDeAceptacionUseCase;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import org.springframework.stereotype.Service;

import java.util.List;

/** Ver {@link ConsultarOpcionesDeAceptacionUseCase}. Es una lectura: no abre transacción de escritura. */
@Service
class OpcionesDeAceptacionService implements ConsultarOpcionesDeAceptacionUseCase {

    private final RequireAdminGuard requireAdminGuard;
    private final CatalogoDeFormacionesPort catalogo;
    private final IngresoPorAceptacion ingreso;

    OpcionesDeAceptacionService(RequireAdminGuard requireAdminGuard, CatalogoDeFormacionesPort catalogo, IngresoPorAceptacion ingreso) {
        this.requireAdminGuard = requireAdminGuard;
        this.catalogo = catalogo;
        this.ingreso = ingreso;
    }

    @Override
    public OpcionesDeAceptacion consultar(UserId actor) {
        requireAdminGuard.requireAdminActivo(actor);
        List<FormacionConfigurada> activas = catalogo.todas().stream().filter(FormacionConfigurada::activa).toList();
        return new OpcionesDeAceptacion(activas, ingreso.diagnosticoDisponible());
    }
}
