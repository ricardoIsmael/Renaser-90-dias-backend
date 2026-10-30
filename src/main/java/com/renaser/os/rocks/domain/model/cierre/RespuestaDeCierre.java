package com.renaser.os.rocks.domain.model.cierre;

import java.util.Objects;

/**
 * Lo que la persona contesta de UNA acción que quedó pendiente: qué pasó, qué hará mañana y, si quiere, una nota (spec 017).
 * La nota la normaliza {@link NotaDeCierre}.
 */
public record RespuestaDeCierre(MotivoDeAccionPendiente motivo, PlanDeManana manana, String nota) {

    public RespuestaDeCierre {
        Objects.requireNonNull(motivo, "motivo es obligatorio");
        Objects.requireNonNull(manana, "manana es obligatorio");
        nota = NotaDeCierre.limpiar(nota);
    }
}
