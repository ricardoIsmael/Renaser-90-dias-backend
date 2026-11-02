package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Formacion;

import java.util.Map;
import java.util.Optional;

/**
 * La bitácora de lo que Administración cambió de la bienvenida y de la imagen de cada formación (D-210, spec 035,
 * {@code cambios_bienvenida}, V73 y V159). Solo se agrega: no hay UPDATE ni DELETE desde el código. Lo vigente de cada
 * pieza en una formación es su último cambio ({@link com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza}).
 */
public interface CambiosDeBienvenidaPort {

    void registrar(CambioDeBienvenida cambio);

    /** El último cambio de cada pieza de {@code formacion} que tuvo alguno; las que nunca cambiaron no están. */
    Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos(Formacion formacion);

    /** El último cambio de esa pieza en {@code formacion}, si tuvo alguno. */
    Optional<CambioDeBienvenida> ultimo(Formacion formacion, PiezaDeBienvenida pieza);
}
