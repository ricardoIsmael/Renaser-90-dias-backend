package com.renaser.os.onboarding.domain.model.media;

/**
 * Espejo de los 3 valores libres que admite la columna {@code medias_onboarding.clase}
 * (texto, no enum Postgres — ver baseline: "audio | firma | documento").
 */
public enum ClaseMedia {
    AUDIO,
    FIRMA,
    DOCUMENTO
}
