package com.renaser.os.users.api;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * Un {@link AlcanceDeFormacionFinder} para las pruebas que hablan de UNA sola formación y no quieren filtros: todos pertenecen a la Escuela y nada más (spec 024).
 * Es el mundo de antes de las formaciones: una comunidad, un Muro, una Tribu.
 */
public class AlcanceDeFormacionSoloEscuela extends AlcanceDeFormacionPermisivo {

    @Override
    public AlcanceDeFormacion de(UserId actor) {
        return AlcanceDeFormacion.de(Formacion.ESCUELA);
    }

    @Override
    public Set<Formacion> formacionesDe(UserId persona) {
        return Set.of(Formacion.ESCUELA);
    }
}
