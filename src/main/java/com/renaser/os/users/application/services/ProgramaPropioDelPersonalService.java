package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import org.springframework.stereotype.Component;

/** Ver {@link ProgramaPropioDelPersonal}. La duración es la más corta que ofrece la formación (la única, en la Escuela y la Certificación). */
@Component
class ProgramaPropioDelPersonalService implements ProgramaPropioDelPersonal {

    private final CargarPertenenciaPort pertenencias;
    private final CatalogoDeFormacionesPort catalogo;

    ProgramaPropioDelPersonalService(CargarPertenenciaPort pertenencias, CatalogoDeFormacionesPort catalogo) {
        this.pertenencias = pertenencias;
        this.catalogo = catalogo;
    }

    @Override
    public Programa para(UserId persona) {
        Formacion formacion = pertenencias.de(persona)
                .flatMap(p -> ReglaDeAlcance.formacionActiva(p.asignadas()))
                .orElse(Formacion.POR_DEFECTO);
        return new Programa(formacion, catalogo.de(formacion).duracionPorDefecto());
    }
}
