package com.renaser.os.habits.domain.model.audiencia;

import java.time.Instant;

/**
 * Desde qué instante una persona tuvo un rol. {@link #DESDE_SIEMPRE} marca el rol que tenía antes de su primer cambio
 * registrado: los días anteriores a ese cambio se resuelven con él.
 */
public record TramoDeRol(CodigoDeRol rol, Instant rigeDesde) {

    public static final Instant DESDE_SIEMPRE = Instant.EPOCH;

    public TramoDeRol {
        if (rol == null || rigeDesde == null) {
            throw new IllegalArgumentException("Un tramo de rol necesita rol e instante");
        }
    }
}
