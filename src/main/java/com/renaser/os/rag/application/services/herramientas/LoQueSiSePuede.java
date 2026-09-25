package com.renaser.os.rag.application.services.herramientas;

/**
 * Como dicen las herramientas que algo NO se puede con un habito, y que se puede en su lugar
 * (D-165). Una sola redaccion para todas: la persona recibe el mismo motivo y la misma salida
 * pregunte por donde pregunte.
 *
 * <p>Existe por E-245: la herramienta de pausa devolvia "ese habito no esta en su plan" y el modelo
 * lo resumia en "no es posible pausarlo", sin motivo ni alternativa. Las reglas no son de aca: son
 * las de {@code habits} (obligatorios V18, cambio de hora desde manana D-91, apagar un dia hoy o a
 * futuro). Si una cambia alla, este texto se corrige en el mismo cambio.
 */
final class LoQueSiSePuede {

    /** Con un obligatorio del programa lo unico que se mueve es la hora. */
    static final String CON_UN_OBLIGATORIO = "Lo que si se puede es cambiarle la hora: desde manana o para un "
            + "dia futuro, porque el dia de hoy no se reacomoda.";

    /** Los de la base de su dia no se pausan, pero se apagan por dia y se cambian de hora. */
    static final String CON_UNO_DE_LA_BASE = "Si es un habito de la base de su dia, lo que si se puede es apagarlo "
            + "un dia puntual (hoy o uno futuro) o ciertos dias de la semana, o cambiarle la hora desde manana.";

    private LoQueSiSePuede() {
    }

    /** El motivo y la salida, juntos: nunca un "no se puede" a secas. */
    static String obligatorio(String titulo) {
        return "'" + titulo + "' es obligatorio del programa: no se puede apagar ningun dia ni pausar. "
                + CON_UN_OBLIGATORIO;
    }
}
