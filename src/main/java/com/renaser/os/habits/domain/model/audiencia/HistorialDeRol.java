package com.renaser.os.habits.domain.model.audiencia;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Los roles que tuvo una persona en el tiempo, anotados por habits al recibir {@code RolDeUsuarioCambiadoEvent}. Con
 * la misma regla que la audiencia: un cambio de rol de hoy rige desde mañana (spec 001, US4).
 */
public final class HistorialDeRol {

    private static final HistorialDeRol VACIO = new HistorialDeRol(List.of());

    private final List<TramoDeRol> tramos;

    private HistorialDeRol(List<TramoDeRol> tramos) {
        this.tramos = tramos;
    }

    public static HistorialDeRol vacio() {
        return VACIO;
    }

    public static HistorialDeRol de(List<TramoDeRol> tramos) {
        return new HistorialDeRol(tramos.stream().sorted(Comparator.comparing(TramoDeRol::rigeDesde)).toList());
    }

    /** Sin historial —nunca cambió de rol desde que existe esta función—, rige el rol actual. */
    public CodigoDeRol vigenteEl(LocalDate dia, ZoneId zona, CodigoDeRol actual) {
        CodigoDeRol vigente = tramos.isEmpty() ? actual : tramos.getFirst().rol();
        for (TramoDeRol tramo : tramos) {
            if (VigenciaPorDia.rigeEl(tramo.rigeDesde(), dia, zona)) {
                vigente = tramo.rol();
            }
        }
        return vigente;
    }
}
