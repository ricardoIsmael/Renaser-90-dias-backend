package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;

import java.util.Map;
import java.util.Optional;

/**
 * La bitácora de lo que Administración cambió de la bienvenida (D-210, {@code cambios_bienvenida}, V73).
 * Solo se agrega: no hay UPDATE ni DELETE desde el código. Lo vigente de cada pieza es su último cambio
 * ({@link com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza}).
 */
public interface CambiosDeBienvenidaPort {

    void registrar(CambioDeBienvenida cambio);

    /** El último cambio de cada pieza que tuvo alguno; las que nunca cambiaron no están. */
    Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos();

    /** El último cambio de esa pieza, si tuvo alguno. */
    Optional<CambioDeBienvenida> ultimo(PiezaDeBienvenida pieza);
}
