package com.renaser.os.onboarding.domain.model.terminos;

/** Una versión de los términos es el borrador que se edita (a lo sumo uno por formación, nadie más lo ve) o una publicada, que ya no se toca (spec 027). */
public enum EstadoDeVersion {
    BORRADOR,
    PUBLICADA
}
