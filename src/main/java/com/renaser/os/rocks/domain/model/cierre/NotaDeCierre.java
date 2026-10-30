package com.renaser.os.rocks.domain.model.cierre;

/**
 * La nota opcional del cierre, sea la de una acción pendiente o la del día (spec 017). Una sola regla para las dos: se recorta, en
 * blanco es {@code null} (nunca una cadena vacía, que el {@code CHECK} de V121 no deja) y no pasa de 500 caracteres contados como
 * los cuenta una persona (un emoji es uno), igual que {@code EntradaDiario}. Es texto personal: no se loguea.
 */
public final class NotaDeCierre {

    public static final int MAXIMO = 500;

    private NotaDeCierre() {
    }

    /** @return la nota recortada, o {@code null} si no hay nada que guardar */
    public static String limpiar(String nota) {
        if (nota == null) {
            return null;
        }
        String limpia = nota.trim();
        if (limpia.isEmpty()) {
            return null;
        }
        int caracteres = limpia.codePointCount(0, limpia.length());
        if (caracteres > MAXIMO) {
            throw new IllegalArgumentException("La nota puede tener hasta " + MAXIMO + " caracteres y esta tiene " + caracteres);
        }
        return limpia;
    }
}
