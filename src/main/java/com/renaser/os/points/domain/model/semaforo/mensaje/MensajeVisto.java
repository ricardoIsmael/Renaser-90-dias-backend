package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.time.LocalDate;
import java.util.Objects;

/** Un mensaje que se le mostró a la persona: qué día (fecha local), cuál, y por qué canal. */
public record MensajeVisto(LocalDate fecha, String mensajeId, CanalDelMensaje canal) {

    public MensajeVisto {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(mensajeId, "mensajeId es obligatorio");
        Objects.requireNonNull(canal, "canal es obligatorio");
    }
}
