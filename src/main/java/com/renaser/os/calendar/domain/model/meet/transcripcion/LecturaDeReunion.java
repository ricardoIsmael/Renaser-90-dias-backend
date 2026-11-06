package com.renaser.os.calendar.domain.model.meet.transcripcion;

/**
 * Lo que Google tiene de una sesión (spec 047, FR-003, FR-004), ya armado: el texto «Nombre: frase» de todos los tramos de esa fecha, en orden.
 *
 * @param conferencias                 cuántas conferencias (tramos) hubo en la sala dentro de la ventana de la fecha
 * @param entradas                     cuántas frases tenía la transcripción
 * @param texto                        el texto armado (vacío si no hubo transcripción)
 * @param enlaceDeLaGrabacion          el enlace de Drive de la primera grabación lista, o {@code null}
 * @param transcripcionEnPreparacion   Google todavía está escribiendo alguna transcripción ({@code STARTED} o {@code ENDED})
 */
public record LecturaDeReunion(int conferencias, int entradas, String texto, String enlaceDeLaGrabacion, boolean transcripcionEnPreparacion) {

    public static final LecturaDeReunion SIN_REUNION = new LecturaDeReunion(0, 0, "", null, false);

    public LecturaDeReunion {
        texto = texto == null ? "" : texto;
    }
}
