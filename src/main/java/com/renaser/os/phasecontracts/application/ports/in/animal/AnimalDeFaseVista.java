package com.renaser.os.phasecontracts.application.ports.in.animal;

import java.net.URI;

/** Lo que Yo y el panel necesitan de una fase: su número, el nombre y la imagen si están personalizados. */
public record AnimalDeFaseVista(int fase, String nombre, URI imagenUrl, String imagenRuta) {

    public boolean personalizada() {
        return imagenRuta != null;
    }
}
