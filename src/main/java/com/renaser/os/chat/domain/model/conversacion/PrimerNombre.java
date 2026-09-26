package com.renaser.os.chat.domain.model.conversacion;

/**
 * El primer nombre de una persona, como lo usa Operaciones para nombrarla (D-173, D-174).
 *
 * <p>Solo la primera palabra, a pedido del dueño "para no romper": "María José Ñahui" es "María".
 * Se asume que el registro guarda nombres antes que apellidos, como pide la pantalla de alta.
 */
public final class PrimerNombre {

    private PrimerNombre() {
    }

    /** Vacío si no hay nombre legible. La inicial en mayúscula: el alta acepta "maría". */
    public static String de(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "";
        }
        String primero = nombreCompleto.strip().split("\\s+")[0];
        return primero.substring(0, 1).toUpperCase(java.util.Locale.forLanguageTag("es")) + primero.substring(1);
    }
}
