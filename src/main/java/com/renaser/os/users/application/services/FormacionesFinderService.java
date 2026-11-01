package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.FormacionesFinder;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import org.springframework.stereotype.Service;

import java.util.List;

/** El catálogo de las tres formaciones para los demás módulos (spec 022). */
@Service
class FormacionesFinderService implements FormacionesFinder {

    private final CatalogoDeFormacionesPort catalogo;

    FormacionesFinderService(CatalogoDeFormacionesPort catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public List<FormacionConfigurada> todas() {
        return catalogo.todas();
    }

    @Override
    public FormacionConfigurada de(Formacion formacion) {
        return catalogo.de(formacion);
    }
}
