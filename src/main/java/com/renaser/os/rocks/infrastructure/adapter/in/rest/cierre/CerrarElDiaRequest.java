package com.renaser.os.rocks.infrastructure.adapter.in.rest.cierre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code PUT /api/v1/rocks/day-closure} (spec 017). {@code fecha} es el día que la persona estaba viendo: el servidor la compara con
 * «hoy» en su zona, no se confía en ella. {@code motivo} y {@code manana} son los nombres de las constantes del dominio; que sean uno
 * de los cuatro lo decide el dominio (400 con un mensaje claro). Los tamaños de acá solo frenan basura: el tope real de la nota (500) y
 * la regla de una respuesta por pendiente son del dominio.
 */
public record CerrarElDiaRequest(@NotNull LocalDate fecha, @NotNull @Size(max = 9) List<@Valid RespuestaRequest> acciones,
                                 @Size(max = 2000) String nota) {

    public record RespuestaRequest(@NotNull UUID id, @NotBlank String motivo, @NotBlank String manana,
                                   @Size(max = 2000) String nota) {
    }
}
