package com.renaser.os.phasecontracts.application.ports.in.animal;

import com.renaser.os.shared.domain.UserId;

import java.net.URI;
import java.util.List;

/** Cambiar la imagen de una fase en dos pasos: pedir dónde subirla y confirmarla ya subida. */
public interface CambiarImagenDeAnimalUseCase {

    UrlDeSubida solicitarSubida(UserId actorId, int fase, String tipoContenido);

    List<AnimalDeFaseVista> confirmar(UserId actorId, int fase, String ruta);

    record UrlDeSubida(URI url, String ruta) {
    }
}
