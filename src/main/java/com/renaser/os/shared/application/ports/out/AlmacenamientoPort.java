package com.renaser.os.shared.application.ports.out;

import java.net.URI;
import java.time.Duration;

public interface AlmacenamientoPort {

    /** URL prefirmada para que el cliente suba un objeto (PUT). */
    URI firmarSubida(String ruta, String tipoContenido, Duration validez);

    /** URL prefirmada de lectura (GET) para un objeto ya subido. */
    URI firmarLectura(String ruta, Duration validez);

    /** Borra el objeto. Idempotente: borrar lo inexistente no falla. */
    void borrar(String ruta);
}
