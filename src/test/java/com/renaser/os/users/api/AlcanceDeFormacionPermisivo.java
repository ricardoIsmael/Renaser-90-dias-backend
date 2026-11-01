package com.renaser.os.users.api;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Un {@link AlcanceDeFormacionFinder} para las pruebas que no hablan de formaciones (spec 022): todos ven todas las formaciones y todas las personas
 * pertenecen a todas, así que nada se filtra. Las pruebas del aislamiento arman el suyo con las formaciones que necesitan.
 */
public class AlcanceDeFormacionPermisivo implements AlcanceDeFormacionFinder {

    @Override
    public AlcanceDeFormacion de(UserId actor) {
        return AlcanceDeFormacion.todas();
    }

    @Override
    public Optional<Formacion> formacionDe(UserId persona) {
        return Optional.of(Formacion.POR_DEFECTO);
    }

    @Override
    public Set<Formacion> formacionesDe(UserId persona) {
        return EnumSet.allOf(Formacion.class);
    }

    @Override
    public Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance) {
        return new HashSet<>(personas);
    }

    @Override
    public void exigirQueLaVea(UserId actor, UserId objetivo) {
        // todos ven a todos
    }

    @Override
    public void exigirQueLaVea(UserId actor, Formacion formacionDelObjeto) {
        // todos ven todo
    }
}
