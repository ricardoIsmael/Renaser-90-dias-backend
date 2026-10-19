package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.util.Objects;
import java.util.Optional;

/**
 * Lo que ya está guardado de los mensajes de una persona, lo justo para un día (D-277): el que se eligió hoy para su
 * grupo y, solo si todavía no hay uno, lo que vio en los 14 días anteriores.
 *
 * <p>El historial existe para que la elección NUEVA no repita; con el mensaje de hoy ya elegido no hace falta, así que
 * no se lee y {@link #vistos()} viene vacío. Leer las dos cosas por separado costaba una consulta de más en cada
 * lectura de {@code /home}, que es lo que SC-004 pide evitar.
 *
 * @param deHoy  id del mensaje elegido hoy para ese grupo, si hay
 * @param vistos lo visto en los últimos {@value MensajesVistos#DIAS_SIN_REPETIR} días; vacío si {@code deHoy} existe
 */
public record EleccionesDelDia(Optional<String> deHoy, MensajesVistos vistos) {

    public EleccionesDelDia {
        Objects.requireNonNull(deHoy, "deHoy es obligatorio");
        Objects.requireNonNull(vistos, "vistos es obligatorio");
    }

    /** Ya se eligió hoy: no se necesita el historial. */
    public static EleccionesDelDia yaElegido(String mensajeId) {
        return new EleccionesDelDia(Optional.of(mensajeId), MensajesVistos.ninguno());
    }

    /** Todavía no se eligió hoy: lo que vio hasta ayer sirve para no repetir. */
    public static EleccionesDelDia sinElegir(MensajesVistos vistos) {
        return new EleccionesDelDia(Optional.empty(), vistos);
    }
}
