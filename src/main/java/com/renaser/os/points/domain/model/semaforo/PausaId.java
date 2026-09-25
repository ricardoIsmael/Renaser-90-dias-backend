package com.renaser.os.points.domain.model.semaforo;

import java.util.Objects;
import java.util.UUID;

/** Identidad de una {@link PausaDeMedicion}. El dominio no la genera (ArchitectureTest): llega de afuera. */
public record PausaId(UUID value) {

    public PausaId {
        Objects.requireNonNull(value, "value es obligatorio");
    }

    public static PausaId of(UUID value) {
        return new PausaId(value);
    }
}
