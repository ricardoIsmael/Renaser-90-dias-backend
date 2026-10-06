package com.renaser.os.phasecontracts.domain.model.animal;

/** El nombre que se muestra bajo «Tu animal:». Vacío = el que trae la app. */
public final class NombreDeAnimal {

    public static final int LARGO_MAXIMO = 24;

    private NombreDeAnimal() {
    }

    /** @return el nombre sin espacios sobrantes, o null si quedó vacío */
    public static String normalizar(String texto) {
        String limpio = texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.codePointCount(0, limpio.length()) > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El nombre del animal puede tener hasta " + LARGO_MAXIMO + " letras.");
        }
        return limpio;
    }
}
