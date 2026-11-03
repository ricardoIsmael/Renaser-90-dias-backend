package com.renaser.os.leadership.domain.model.habitos;

import java.time.LocalDate;

/**
 * Los días que se pueden mirar en «Hábitos de todos» (spec 029, FR-011): hoy y hasta {@value #DIAS_HACIA_ATRAS} días hacia atrás, nunca el futuro. La
 * misma idea de ventana corta de la ficha (spec 005) pero de un día a la vez. El «hoy» es el del padrón (la zona por defecto): cada alumno cierra su día en
 * su zona, y eso ya lo dice el estado del registro.
 */
public record VentanaDeLosHabitos(LocalDate masAntigua, LocalDate masReciente) {

    public static final int DIAS_HACIA_ATRAS = 7;

    public static VentanaDeLosHabitos de(LocalDate hoy) {
        return new VentanaDeLosHabitos(hoy.minusDays(DIAS_HACIA_ATRAS), hoy);
    }

    /**
     * @param pedida null = hoy
     * @throws IllegalArgumentException si la fecha es futura o más vieja que la ventana (400)
     */
    public LocalDate fechaElegida(LocalDate pedida) {
        if (pedida == null) {
            return masReciente;
        }
        if (pedida.isAfter(masReciente) || pedida.isBefore(masAntigua)) {
            throw new IllegalArgumentException("Solo se pueden mirar los hábitos de hoy y de los " + DIAS_HACIA_ATRAS
                    + " días anteriores.");
        }
        return pedida;
    }

    public boolean esHoy(LocalDate fecha) {
        return masReciente.equals(fecha);
    }
}
