package com.renaser.os.chat.application.ports.out.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Formacion;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La bitácora de la bienvenida (D-210) en memoria, para las pruebas sin base: se agrega al final y lo
 * vigente es la última fila de cada pieza de cada formación (spec 035), como en {@code cambios_bienvenida}.
 */
public final class CambiosDeBienvenidaEnMemoria implements CambiosDeBienvenidaPort {

    private final List<CambioDeBienvenida> filas = new ArrayList<>();

    @Override
    public void registrar(CambioDeBienvenida cambio) {
        filas.add(cambio);
    }

    @Override
    public Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos(Formacion formacion) {
        Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos = new EnumMap<>(PiezaDeBienvenida.class);
        filas.stream().filter(cambio -> cambio.formacion() == formacion).forEach(cambio -> ultimos.put(cambio.pieza(), cambio));
        return ultimos;
    }

    @Override
    public Optional<CambioDeBienvenida> ultimo(Formacion formacion, PiezaDeBienvenida pieza) {
        return Optional.ofNullable(ultimos(formacion).get(pieza));
    }

    /** Todas las filas, en orden: la bitácora completa. */
    public List<CambioDeBienvenida> filas() {
        return List.copyOf(filas);
    }
}
