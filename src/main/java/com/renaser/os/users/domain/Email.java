package com.renaser.os.users.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Value object de correo. Valida FORMATO (§5.4.3, nivel 3: invariante de dominio).
 * La unicidad no se valida aca: la garantiza el repositorio con un indice unico.
 *
 * Se normaliza a minusculas para que la unicidad sea real y no dependa de como
 * el aprendiz escribio el mail al registrarse.
 */
public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    public Email {
        value = normalize(value);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("El email no puede ser vacio");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Formato de email invalido: " + raw);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}
