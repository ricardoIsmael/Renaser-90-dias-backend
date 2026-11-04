package com.renaser.os.calendar.domain.model.meet;

/**
 * Quién puede entrar a la sala de Meet de un evento (spec 040, FR-023; decisión 2 del dueño del 04/11/2026: «los 2», se
 * elige en cada evento). {@link #ABIERTA} es el valor por defecto: se entra directo con el enlace. {@link #CON_PERMISO}: quien
 * organiza (la cuenta de Google de Renaser) admite a cada persona, así que alguien de Renaser debe estar dentro.
 */
public enum AccesoSalaMeet {

    ABIERTA,
    CON_PERMISO;

    /** Lo que se elige cuando se pide el enlace automático y no se dice nada. */
    public static AccesoSalaMeet porDefecto() {
        return ABIERTA;
    }
}
