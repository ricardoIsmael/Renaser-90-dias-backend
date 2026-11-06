package com.renaser.os.calendar.application.ports.in.audiencia;

import java.util.UUID;

/** Una persona que se puede invitar, como la muestra el buscador (spec 044): nombre, foto, rol en palabras y grupo. Nunca su color (FR-016). */
public record PersonaConvocable(UUID id, String nombre, String fotoUrl, String rol, String grupo) {
}
