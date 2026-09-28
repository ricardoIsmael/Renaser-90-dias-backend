package com.renaser.os.onboarding.domain.model.caja;

import java.text.Normalizer;
import java.util.Locale;

/** Limpieza de los textos libres de la caja: sin espacios de más, vacío = no dicho, con tope de largo. */
final class TextoCorto {

    private TextoCorto() {
    }

    /** {@code null} si viene vacío. @throws IllegalArgumentException si pasa del largo (400) */
    static String opcional(String valor, int largoMaximo, String nombre) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.strip();
        if (limpio.codePointCount(0, limpio.length()) > largoMaximo) {
            throw new IllegalArgumentException(nombre + " puede tener hasta " + largoMaximo + " caracteres.");
        }
        return limpio;
    }

    /** @throws IllegalArgumentException si viene vacío o pasa del largo (400) */
    static String obligatorio(String valor, int largoMaximo, String nombre) {
        String limpio = opcional(valor, largoMaximo, nombre);
        if (limpio == null) {
            throw new IllegalArgumentException("Falta " + nombre.toLowerCase(Locale.ROOT) + ".");
        }
        return limpio;
    }

    /** Minúsculas y sin tildes: para comparar lo que escribió una persona («Perú», «PERU», «perú »). */
    static String normalizado(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
