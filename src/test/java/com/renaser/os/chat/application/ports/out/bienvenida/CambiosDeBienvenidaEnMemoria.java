package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La bitácora de la bienvenida (D-210) en memoria, para las pruebas sin base: se agrega al final y lo
 * vigente es la última fila de cada pieza, como en {@code cambios_bienvenida}.
 */
public final class CambiosDeBienvenidaEnMemoria implements CambiosDeBienvenidaPort {

    private final List<CambioDeBienvenida> filas = new ArrayList<>();

    @Override
    public void registrar(CambioDeBienvenida cambio) {
        filas.add(cambio);
    }

    @Override
    public Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos() {
        Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos = new EnumMap<>(PiezaDeBienvenida.class);
        filas.forEach(cambio -> ultimos.put(cambio.pieza(), cambio));
        return ultimos;
    }

    @Override
    public Optional<CambioDeBienvenida> ultimo(PiezaDeBienvenida pieza) {
        return Optional.ofNullable(ultimos().get(pieza));
    }

    /** Todas las filas, en orden: la bitácora completa. */
    public List<CambioDeBienvenida> filas() {
        return List.copyOf(filas);
    }
}
