package com.renaser.os.onboarding.domain.model.pagos;

/**
 * La moneda de un cronograma de pagos (spec 032): soles o dólares. Un cronograma lleva <b>una sola</b> y no hay valor por defecto: quien lo arma la elige
 * (decisión del dueño del 2026-11-01).
 */
public enum Moneda {

    PEN("S/"),
    USD("US$");

    private final String simbolo;

    Moneda(String simbolo) {
        this.simbolo = simbolo;
    }

    /** Cómo se escribe delante del monto: «S/» o «US$». */
    public String simbolo() {
        return simbolo;
    }

    /**
     * @param codigo {@code PEN} o {@code USD}, sin distinguir mayúsculas
     * @throws IllegalArgumentException (400) si falta o no es una de las dos
     */
    public static Moneda desde(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Elige la moneda del cronograma: S/ o US$");
        }
        for (Moneda moneda : values()) {
            if (moneda.name().equalsIgnoreCase(codigo.trim())) {
                return moneda;
            }
        }
        throw new IllegalArgumentException("La moneda del cronograma es PEN (S/) o USD (US$)");
    }
}
