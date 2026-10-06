package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.animal;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;

/** {@code nombre} e {@code imagenUrl} nulos = la app usa los que trae incluidos. */
public record AnimalDeFaseResponse(int fase, String nombre, String imagenUrl, String imagenRuta,
                                   boolean personalizada) {

    static AnimalDeFaseResponse from(AnimalDeFaseVista v) {
        return new AnimalDeFaseResponse(v.fase(), v.nombre(), v.imagenUrl() == null ? null : v.imagenUrl().toString(),
                v.imagenRuta(), v.personalizada());
    }
}
