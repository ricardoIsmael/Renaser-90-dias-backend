package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.out.terminos.TerminosPort;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.TerminosPublicados;
import org.springframework.stereotype.Service;

/** Lo que {@code users} pregunta al aceptar una solicitud: ¿esa formación ya publicó sus términos? (spec 027, FR-021). */
@Service
class TerminosPublicadosService implements TerminosPublicados {

    private final TerminosPort terminos;

    TerminosPublicadosService(TerminosPort terminos) {
        this.terminos = terminos;
    }

    @Override
    public boolean estanPublicados(Formacion formacion) {
        return terminos.vigente(formacion).isPresent();
    }
}
