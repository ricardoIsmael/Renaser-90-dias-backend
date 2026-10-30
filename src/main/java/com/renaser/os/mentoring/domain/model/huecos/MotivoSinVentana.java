package com.renaser.os.mentoring.domain.model.huecos;

/**
 * Por qué un día cuenta entero (de 00:00 a 24:00) en vez de ir de Despertar a Dormir (FR-007). La app lo necesita para
 * decirle al acompañante cuál de las dos horas pedirle a la persona («Falta su hora de Despertar»); sin ventana no
 * dibuja el sueño como tiempo libre.
 */
public enum MotivoSinVentana {
    SIN_DESPERTAR,
    SIN_DORMIR,
    SIN_DESPERTAR_NI_DORMIR,
    /** Las dos horas están, pero Dormir no es posterior a Despertar (una persona que trabaja de noche). */
    DORMIR_ANTES_DE_DESPERTAR
}
