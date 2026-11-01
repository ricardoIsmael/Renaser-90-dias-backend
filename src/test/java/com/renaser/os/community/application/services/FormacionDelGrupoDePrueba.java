package com.renaser.os.community.application.services;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.FormacionesFinder;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Un {@link FormacionDelGrupo} para las pruebas que no hablan de formaciones (spec 022): todas las personas pertenecen a todas las formaciones,
 * así que ninguna validación las frena. Las pruebas del aislamiento arman el suyo con {@link #con(AlcanceDeFormacionFinder)}.
 */
final class FormacionDelGrupoDePrueba {

    private FormacionDelGrupoDePrueba() {
    }

    static FormacionDelGrupo permisivo() {
        return con(new Permisivo());
    }

    static FormacionDelGrupo con(AlcanceDeFormacionFinder pertenencias) {
        return new FormacionDelGrupo(pertenencias, new CatalogoDePrueba());
    }

    /** Nadie pertenece a nada: para probar que se rechaza. */
    static final class Permisivo implements AlcanceDeFormacionFinder {

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
        }

        @Override
        public void exigirQueLaVea(UserId actor, Formacion formacionDelObjeto) {
        }
    }

    static final class CatalogoDePrueba implements FormacionesFinder {

        @Override
        public List<FormacionConfigurada> todas() {
            return List.of(de(Formacion.ESCUELA), de(Formacion.DIPLOMADO), de(Formacion.CERTIFICACION));
        }

        @Override
        public FormacionConfigurada de(Formacion formacion) {
            return new FormacionConfigurada(formacion, nombre(formacion), "", true, List.of(90), 1);
        }

        private static String nombre(Formacion formacion) {
            return switch (formacion) {
                case ESCUELA -> "Escuela Renaser";
                case DIPLOMADO -> "Diplomado Renaser";
                case CERTIFICACION -> "Certificación Renaser";
            };
        }
    }
}
