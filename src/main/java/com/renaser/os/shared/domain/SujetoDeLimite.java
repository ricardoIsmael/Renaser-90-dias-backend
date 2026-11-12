package com.renaser.os.shared.domain;

import java.util.Objects;

/**
 * A quién se le cuenta un pedido: a una cuenta con sesión o, cuando no hay sesión, a su origen (la unidad de conteo de la IP, ver
 * {@link UnidadDeConteoPorIp}). La {@link #clave()} es lo que entra a la clave de Redis; lleva un prefijo para que una cuenta y un
 * origen nunca se confundan.
 *
 * <p>Un origen no es un dato personal que se guarde: solo vive como parte de una clave de Redis con TTL de minutos a un día.
 */
public record SujetoDeLimite(String clave, boolean esCuenta) {

    private static final int LARGO_MAXIMO = 80;

    public SujetoDeLimite {
        Objects.requireNonNull(clave, "clave");
        if (clave.isBlank() || clave.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La clave del sujeto va de 1 a " + LARGO_MAXIMO + " caracteres");
        }
    }

    public static SujetoDeLimite cuenta(String idDeLaCuenta) {
        return new SujetoDeLimite("c:" + acotar(idDeLaCuenta), true);
    }

    public static SujetoDeLimite origen(String unidadDeLaIp) {
        return new SujetoDeLimite("ip:" + acotar(unidadDeLaIp), false);
    }

    /** El id de la cuenta sin el prefijo; para un origen, un texto fijo (nunca la IP). */
    public String paraElRegistro() {
        return esCuenta ? clave.substring(2) : "sin_cuenta";
    }

    private static String acotar(String texto) {
        String limpio = texto == null || texto.isBlank() ? "desconocido" : texto.trim();
        return limpio.length() > LARGO_MAXIMO - 3 ? limpio.substring(0, LARGO_MAXIMO - 3) : limpio;
    }
}
