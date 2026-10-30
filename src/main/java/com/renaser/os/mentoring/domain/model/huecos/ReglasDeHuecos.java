package com.renaser.os.mentoring.domain.model.huecos;

/**
 * Los números de los huecos de horario (spec 015, supuestos). Ninguno lo inventó el código: son las propuestas de la
 * spec, escritas en un solo lugar para que el dueño pueda cambiarlas sin tocar el comportamiento.
 */
public final class ReglasDeHuecos {

    /** Hoy ningún hábito dice cuánto dura: {@code horaLimite} es el plazo para cumplirlo, no su duración. */
    public static final int MINUTOS_DE_UN_HABITO = 30;
    /** Una acción con solo la hora de inicio, o con la de fin anterior a la de inicio. */
    public static final int MINUTOS_DE_UNA_ACCION = 30;
    /** Un evento que no declara duración. */
    public static final int MINUTOS_DE_UN_EVENTO = 60;
    /** Un hueco menor a esto no se lista (sus minutos sí suman al tiempo libre del día). */
    public static final int MINUTOS_DEL_HUECO_UTIL = 30;
    /** Un hueco «se repite» cuando aparece en al menos esta cantidad de días de la semana. */
    public static final int DIAS_PARA_QUE_SE_REPITA = 3;

    private ReglasDeHuecos() {
    }
}
