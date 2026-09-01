package com.renaser.os.rocks.domain.model.rocamaestra;

import java.util.UUID;

/**
 * Identidad de una Roca Maestra (tabla `rocas_maestras`) — el objetivo de un participante en un eje.
 *
 * <p>No genera el UUID: la generacion vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}. Este modulo ademas no crea
 * {@link RocaMaestra} (RK-1: las siembra {@code onboarding}), asi que solo se arma con
 * {@code of(UUID)} al rehidratar desde persistencia. CLAUDE.MD §5.4.7:
 * {@code domain/} sin aleatoriedad.
 */
public record RocaMaestraId(UUID value) {

    public RocaMaestraId {
        if (value == null) {
            throw new IllegalArgumentException("RocaMaestraId no puede ser null");
        }
    }

    public static RocaMaestraId of(UUID value) {
        return new RocaMaestraId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
