package com.renaser.os.users.domain.model.user;

import java.util.Locale;
import java.util.regex.Pattern;


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
