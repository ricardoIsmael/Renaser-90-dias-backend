package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.phasecontracts.application.ports.in.animal.AnimalDeFaseVista;
import com.renaser.os.phasecontracts.application.ports.out.animal.AnimalesDeFasePort;
import com.renaser.os.phasecontracts.domain.model.animal.AnimalDeFase;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** Las cuatro fases listas para mostrar: la imagen propia sale con URL de lectura firmada. */
@Component
class AnimalesDeFaseParaMostrar {

    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofHours(1);

    private final AnimalesDeFasePort animales;
    private final AlmacenamientoPort almacenamiento;

    AnimalesDeFaseParaMostrar(AnimalesDeFasePort animales, AlmacenamientoPort almacenamiento) {
        this.animales = animales;
        this.almacenamiento = almacenamiento;
    }

    List<AnimalDeFaseVista> todos() {
        return animales.todos().stream().map(this::vista).toList();
    }

    private AnimalDeFaseVista vista(AnimalDeFase animal) {
        var url = animal.tieneImagenPropia() ? almacenamiento.firmarLectura(animal.rutaImagen(), VALIDEZ_URL_LECTURA) : null;
        return new AnimalDeFaseVista(animal.fase().numero(), animal.nombre(), url, animal.rutaImagen());
    }
}
