package com.renaser.os.calendar.domain.model.meet;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * El código de una sala de Meet ({@code abc-defg-hij}) sale del enlace {@code https://meet.google.com/abc-defg-hij} (spec 040, FR-023): con
 * él se le pide a la Meet REST API quién puede entrar. Un enlace que no es de Meet no tiene código.
 */
public final class CodigoDeSalaMeet {

    private static final Pattern CODIGO = Pattern.compile("^[a-z]{3}-[a-z]{4}-[a-z]{3}$");

    private CodigoDeSalaMeet() {
    }

    public static Optional<String> deEnlace(String enlace) {
        if (enlace == null || enlace.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(enlace.trim());
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase("meet.google.com") || uri.getPath() == null) {
                return Optional.empty();
            }
            String codigo = uri.getPath().replaceFirst("^/", "");
            return CODIGO.matcher(codigo).matches() ? Optional.of(codigo) : Optional.empty();
        } catch (java.net.URISyntaxException e) {
            return Optional.empty();
        }
    }
}
