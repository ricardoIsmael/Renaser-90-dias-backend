package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.points.api.ColorSemaforo;

/**
 * El color con el que el líder ve a una persona en la lista de un grupo (spec 005, FR-006 y FR-007). Son los cuatro del
 * semáforo más {@link #NO_SE_MIDE}, para quien lleva su propio programa en ese grupo (su mentor o su guía): el semáforo
 * del grupo mide aprendices, y a ellos se les muestra su porcentaje y no un color.
 *
 * <p><b>El orden de las constantes es el de la lista</b>: rojo, amarillo, sin datos, verde y no se mide. «Sin datos»
 * queda donde lo deja la tabla del mentor (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.3): subirlo enterraría a los
 * amarillos debajo de filas a las que nadie puede ayudar esa semana.
 */
public enum ColorDeLaFila {
    ROJO,
    AMARILLO,
    SIN_DATOS,
    VERDE,
    NO_SE_MIDE;

    private static final String ETIQUETA_NO_SE_MIDE = "No se mide en el semáforo";

    /** El color de quien SÍ se mide: el mismo que el semáforo le dio. */
    public static ColorDeLaFila de(ColorSemaforo color) {
        return valueOf(color.name());
    }

    /** La palabra que acompaña al color: nunca viaja un color solo (RL-30). */
    public String etiqueta() {
        return this == NO_SE_MIDE ? ETIQUETA_NO_SE_MIDE : ColorSemaforo.valueOf(name()).etiqueta();
    }
}
