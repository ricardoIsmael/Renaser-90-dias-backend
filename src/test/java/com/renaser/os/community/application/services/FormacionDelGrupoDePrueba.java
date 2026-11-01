package com.renaser.os.community.application.services;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.FormacionesFinder;

import java.util.List;

/**
 * Un {@link FormacionDelGrupo} para las pruebas que no hablan de formaciones (spec 022): todas las personas pertenecen a todas las formaciones,
 * así que ninguna validación las frena. Las pruebas del aislamiento arman el suyo con {@link #con(AlcanceDeFormacionFinder)}.
 */
final class FormacionDelGrupoDePrueba {

    private FormacionDelGrupoDePrueba() {
    }

    static FormacionDelGrupo permisivo() {
        return con(new AlcanceDeFormacionPermisivo());
    }

    static FormacionDelGrupo con(AlcanceDeFormacionFinder pertenencias) {
        return new FormacionDelGrupo(pertenencias, new CatalogoDePrueba());
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
