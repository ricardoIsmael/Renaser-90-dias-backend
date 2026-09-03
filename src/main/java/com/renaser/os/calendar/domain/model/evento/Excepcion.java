package com.renaser.os.calendar.domain.model.evento;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Override de UNA ocurrencia de una serie recurrente (tabla {@code excepciones_evento}):
 * cancelada, movida, reprogramada o retitulada. {@code inicioOcurrencia} es la clave — el
 * slot ORIGINAL, sin override, tal como lo genera {@link ExpansorOcurrencias} antes de
 * aplicar ninguna excepcion (evita el problema de "que pasa si muevo la misma fecha dos
 * veces": la clave nunca cambia, solo el contenido del override).
 */
public record Excepcion(UUID id, EventoId eventoId, Instant inicioOcurrencia, boolean cancelada, Instant nuevoInicio,
                         Integer nuevaDuracion, String nuevoTitulo) {

    public Excepcion {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        Objects.requireNonNull(inicioOcurrencia, "inicioOcurrencia es obligatorio");
        if (nuevaDuracion != null && nuevaDuracion <= 0) {
            throw new IllegalArgumentException("nuevaDuracion debe ser positiva");
        }
    }

    /** El {@code id} entra por parametro: lo genera el caso de uso con el puerto {@code IdGenerator}. */
    public static Excepcion cancelar(UUID id, EventoId eventoId, Instant inicioOcurrencia) {
        return new Excepcion(id, eventoId, inicioOcurrencia, true, null, null, null);
    }

    /** El {@code id} entra por parametro: lo genera el caso de uso con el puerto {@code IdGenerator}. */
    public static Excepcion reprogramar(UUID id, EventoId eventoId, Instant inicioOcurrencia, Instant nuevoInicio,
                                         Integer nuevaDuracion, String nuevoTitulo) {
        return new Excepcion(id, eventoId, inicioOcurrencia, false, nuevoInicio, nuevaDuracion, nuevoTitulo);
    }
}
