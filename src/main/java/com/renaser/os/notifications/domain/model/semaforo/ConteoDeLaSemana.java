package com.renaser.os.notifications.domain.model.semaforo;

/**
 * Cuántos aprendices quedaron en cada color al cerrar la semana. Solo decide el caso del aviso:
 * ninguna de estas cifras llega nunca al texto.
 */
public record ConteoDeLaSemana(int verde, int amarillo, int rojo, int sinDatos) {

    /** Un conteo que no cierra (negativo o vacío) no afirma nada: queda el texto neutro. */
    public CasoDelAviso caso() {
        if (verde < 0 || amarillo < 0 || rojo < 0 || sinDatos < 0 || total() == 0) {
            return CasoDelAviso.NEUTRO;
        }
        if (amarillo + rojo > 0) {
            return CasoDelAviso.NECESITAN_APOYO;
        }
        if (sinDatos == total()) {
            return CasoDelAviso.SIN_REGISTROS;
        }
        return verde == total() ? CasoDelAviso.SIN_ALERTAS : CasoDelAviso.NEUTRO;
    }

    private long total() {
        return (long) verde + amarillo + rojo + sinDatos;
    }
}
