package com.renaser.os.chat.infrastructure.adapter.out.semaforo;

import com.renaser.os.chat.application.ports.out.semaforo.ImagenDeLaTarjetaPort;
import com.renaser.os.chat.domain.model.semaforo.ColorDeTarjeta;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Las tres tarjetas de Operaciones (diseño de Canva, 1080×1080) como recurso de la aplicación, pasadas a
 * JPEG al 85 % para que cada una pese ~100 KB en vez de ~700 KB sin cambiar el diseño (D-223).
 */
@Component
class TarjetasDelSemaforoEnRecursos implements ImagenDeLaTarjetaPort {

    @Override
    public byte[] imagen(ColorDeTarjeta color) {
        try (InputStream entrada = new ClassPathResource(color.recurso()).getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falta la tarjeta del semáforo " + color.recurso(), e);
        }
    }
}
