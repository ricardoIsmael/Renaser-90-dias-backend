package com.renaser.os.onboarding.domain.model.media;

/**
 * Espejo de los valores libres que admite la columna {@code medias_onboarding.clase}
 * (texto, no enum Postgres — ver baseline: "audio | firma | documento").
 *
 * <p>{@link #FOTO} se sumó con la Caja Renaser (D-219): la foto de la caja armada y el comprobante del
 * envío. La columna es texto libre, así que no hizo falta migrar.
 */
public enum ClaseMedia {
    AUDIO,
    FIRMA,
    DOCUMENTO,
    FOTO
}
