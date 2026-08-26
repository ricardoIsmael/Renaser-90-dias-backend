package com.renaser.os.users.domain.model.user;

import java.time.Instant;
import java.util.Objects;

/**
 * Contrasena ya hasheada. Value object de {@link User}: existe para que el compilador impida
 * pasar una contrasena en claro donde va un hash. Quien hashea es el {@code PasswordEncoder}
 * de Spring, no esta clase. Puede no existir: quien entra solo por Google/Apple no tiene.
 */
public record Credencial(String hash, Instant actualizadaEn) {

    public Credencial {
        Objects.requireNonNull(actualizadaEn, "actualizadaEn es obligatorio");
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("El hash no puede ser vacio");
        }
    }

    @Override
    public String toString() {
        return "Credencial[oculta]";
    }
}
