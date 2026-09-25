package com.renaser.os.rag.application.services.herramientas;

/**
 * Como dicen las herramientas que algo NO se puede con un habito, y que se puede en su lugar
 * (D-165). Una sola redaccion para todas: la persona recibe el mismo motivo y la misma salida
 * pregunte por donde pregunte.
 *
 * <p>Existe por E-245: la herramienta de pausa devolvia "ese habito no esta en su plan" y el modelo
 * lo resumia en "no es posible pausarlo", sin motivo ni alternativa. Las reglas no son de aca: son
 * las de {@code habits} (obligatorios V18; la hora nunca cambia para hoy, D-91; apagar un dia, hoy o
 * a futuro; pausar, cualquier habito no obligatorio, como el interruptor de Plan, D-99). Si una
 * cambia alla, este texto se corrige en el mismo cambio.
 */
final class LoQueSiSePuede {

    /**
     * Con un obligatorio del programa lo unico que se mueve es la hora, y gasta cupo. El dia no: en la
     * bateria del 2026-09-25 el modelo ofrecio "cambiar el dia" de la audioterapia, que no se elige.
     */
    static final String CON_UN_OBLIGATORIO = "Lo que si se puede es cambiarle la hora (el dia en que le toca no "
            + "se mueve), si le quedan cambios esta semana: como horario general desde manana, o solo para un dia "
            + "futuro. El dia de hoy no se reacomoda.";

    /** Todo lo que no es obligatorio. */
    static final String CON_LOS_DEMAS = "Cualquier habito que no sea obligatorio se puede pausar (hasta una fecha "
            + "o sin fin), apagar un dia puntual (hoy o uno futuro) o ciertos dias de la semana, y cambiarle la "
            + "hora desde manana.";

    private LoQueSiSePuede() {
    }

    /** El motivo y la salida, juntos: nunca un "no se puede" a secas. */
    static String obligatorio(String titulo) {
        return "'" + titulo + "' es obligatorio del programa: no se puede apagar ningun dia ni pausar. "
                + CON_UN_OBLIGATORIO;
    }

    /**
     * Un habito pausado no se pide ningun dia: apagarlo o encenderlo un dia no cambia nada (en la
     * bateria del 2026-09-25 se propuso apagar un dia un habito pausado desde hacia dos semanas).
     */
    static String pausado(String titulo) {
        return "'" + titulo + "' esta pausado: mientras dure la pausa no se le pide ningun dia, asi que apagarlo o "
                + "encenderlo un dia no cambia nada. Si quiere volver a hacerlo, lo que corresponde es reactivarlo; "
                + "si quiere que la pausa dure hasta otra fecha, se vuelve a pausar con esa fecha.";
    }
}
