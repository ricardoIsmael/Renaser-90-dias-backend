package com.renaser.os.phasecontracts.infrastructure.adapter.out.imagen;

import com.renaser.os.phasecontracts.application.ports.out.animal.ImagenDeAnimalPort;
import com.renaser.os.phasecontracts.domain.model.animal.CabeceraDeImagen;
import com.renaser.os.phasecontracts.domain.model.animal.ImagenDeAnimal;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
class ImagenesDeAnimalEnAlmacenamiento implements ImagenDeAnimalPort {

    private final AlmacenamientoPort almacenamiento;

    ImagenesDeAnimalEnAlmacenamiento(AlmacenamientoPort almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    @Override
    public void revisar(String ruta) {
        byte[] bytes = almacenamiento.leer(ImagenDeAnimal.exigirRutaPropia(ruta), ImagenDeAnimal.PESO_MAXIMO_EN_BYTES)
                .orElseThrow(() -> new NoSuchElementException("No hay ninguna imagen subida: súbela de nuevo."));
        ImagenDeAnimal.exigirQueSePuedaUsar(CabeceraDeImagen.de(bytes), bytes.length);
    }
}
