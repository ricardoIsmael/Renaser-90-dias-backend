package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.CifrasDeFormacionFinder;
import com.renaser.os.users.application.ports.out.formacion.CifrasDeFormacionPort;
import org.springframework.stereotype.Service;

/** Las cifras de personas por formación para el centro de Administración (spec 022). Solo lectura. */
@Service
class CifrasDeFormacionService implements CifrasDeFormacionFinder {

    private final CifrasDeFormacionPort cifras;

    CifrasDeFormacionService(CifrasDeFormacionPort cifras) {
        this.cifras = cifras;
    }

    @Override
    public CifrasDePersonas de(Formacion formacion) {
        return cifras.de(formacion);
    }

    @Override
    public int personalSinFormacion() {
        return cifras.personalSinFormacion();
    }
}
