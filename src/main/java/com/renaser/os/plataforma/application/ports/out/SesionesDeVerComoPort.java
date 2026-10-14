package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.vercomo.SesionDeVerComo;

import java.util.Optional;

/**
 * Las sesiones de solo lectura de «ver como» (D-270), en el mismo almacén que todas las sesiones (Spring Session en
 * Redis). Abrir una NO toca la sesión de quien la pide: es otra sesión, de otra cuenta.
 */
public interface SesionesDeVerComoPort {

    /** @return el id de la sesión nueva (lo que la app manda en {@code X-Auth-Token}) */
    String abrir(SesionDeVerComo sesion);

    /** La sesión «ver como» con ese id, si existe y es de «ver como» (una sesión común no lo es). */
    Optional<SesionDeVerComo> leer(String idSesion);

    void cerrar(String idSesion);
}
