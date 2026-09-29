package com.renaser.os.chat.application.ports.out.semaforo;

import com.renaser.os.chat.domain.model.semaforo.ColorDeTarjeta;

/**
 * Los bytes de la tarjeta del semáforo de un color (D-223): el JPEG que viene con la aplicación
 * ({@code src/main/resources/semaforo/tarjetas/}). Nadie la sube a mano: el servidor la lleva al
 * almacenamiento la primera vez que la necesita.
 */
public interface ImagenDeLaTarjetaPort {

    String TIPO_CONTENIDO = "image/jpeg";

    byte[] imagen(ColorDeTarjeta color);
}
