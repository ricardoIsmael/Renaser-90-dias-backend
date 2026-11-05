package com.renaser.os.calendar.application.ports.out.meet;

/**
 * Lo que Google devolvió al crear o leer la sala. {@code enlace} es {@code null} mientras Google no terminó de crearla ({@code pending}): se
 * vuelve a mirar más tarde (spec 040, FR-006).
 */
public record ResultadoDeSala(String enlace) {

    public boolean lista() {
        return enlace != null && !enlace.isBlank();
    }

    public static ResultadoDeSala pendiente() {
        return new ResultadoDeSala(null);
    }
}
