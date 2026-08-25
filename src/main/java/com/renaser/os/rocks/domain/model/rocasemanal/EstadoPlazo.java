package com.renaser.os.rocks.domain.model.rocasemanal;

/**
 * ¿Se planificó dentro de la ventana esperada, o a destiempo? No es una
 * columna persistida (ver {@link VentanaPlanificacionSemanal}): se recalcula
 * a partir de `creadoEn` cada vez que hace falta saber si algo se puede
 * editar todavía.
 */
public enum EstadoPlazo {
    EN_PLAZO,
    A_DESTIEMPO
}
