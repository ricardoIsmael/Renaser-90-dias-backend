package com.renaser.os.calendar.domain.model.evento.audiencia;

/**
 * Los tres colores del semáforo que se pueden elegir como audiencia de un evento (spec 044, FR-009, decisión P8 del dueño). «Sin datos» y «En pausa» no
 * están a propósito: a una persona sin color se la invita por su nombre.
 */
public enum ColorDeAudiencia {
    VERDE,
    AMARILLO,
    ROJO
}
