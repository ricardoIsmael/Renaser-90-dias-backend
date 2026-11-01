package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;

import java.util.List;

/**
 * El catálogo de las tres formaciones (spec 022): nombre, descripción, estado y opciones de duración. Lo leen la sesión, el selector del personal, el
 * centro «Formaciones» de Administración y la aceptación de solicitudes (spec 025).
 */
public interface FormacionesFinder {

    /** Las tres, en su orden. */
    List<FormacionConfigurada> todas();

    FormacionConfigurada de(Formacion formacion);
}
