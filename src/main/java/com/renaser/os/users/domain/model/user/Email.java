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

    /**
     * Parte de dominio, ya normalizada a minusculas. Es donde se pregunta si el correo puede
     * entregarse: los registros MX son del dominio, no del buzon. Seguro por construccion — el
     * formato ya se valido en {@link #normalize}, asi que siempre hay exactamente una arroba.
     */
    public String dominio() {
        return value.substring(value.indexOf('@') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
