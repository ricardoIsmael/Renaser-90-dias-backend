package com.renaser.os.habits.domain.model.radar;

/**
 * Cómo está Mi Radar un día de la semana (spec 019): el modo, cuántas veces si es Personalizado y si la persona lo apagó
 * para ese día. Valor inmutable. Lunes = 1 … domingo = 7 (el orden de {@link java.time.DayOfWeek#getValue()}).
 *
 * <ul>
 *   <li>{@link Modo#AUTOMATICO} (por defecto): pregunta cada hora en punto entre el Despertar y el Dormir de ese día.
 *       No guarda {@code veces}.</li>
 *   <li>{@link Modo#PERSONALIZADO}: pregunta {@code veces} veces, repartidas parejo entre esas horas. Si no llega
 *       {@code veces} se usa {@link #VECES_POR_DEFECTO}; el recorte a las horas que de verdad caben ese día lo hace
 *       {@link HorarioDelRadar}, porque depende del horario de la persona y este valor no lo conoce.</li>
 *   <li>{@code sinRadar}: la persona lo apagó para ese día, en cualquiera de los dos modos.</li>
 * </ul>
 *
 * <p>Lo que no cumple lanza {@link IllegalArgumentException}, que el servidor responde con 400 (FR-005).
 */
public record DiaDeMiRadar(int dia, Modo modo, Integer veces, boolean sinRadar) {

    /** Las que se proponen al pasar a Personalizado, recortadas a lo que cabe ese día (supuesto del equipo, spec 019). */
    public static final int VECES_POR_DEFECTO = 6;
    /** Las horas que caben entre las 03:00 y las 23:00: el máximo posible de un día. */
    public static final int VECES_MAXIMAS = 21;

    public enum Modo {
        AUTOMATICO, PERSONALIZADO
    }

    public DiaDeMiRadar {
        if (dia < 1 || dia > 7) {
            throw new IllegalArgumentException("El día de Mi Radar va de 1 (lunes) a 7 (domingo)");
        }
        modo = modo == null ? Modo.AUTOMATICO : modo;
        if (modo == Modo.AUTOMATICO) {
            veces = null;
        } else {
            veces = veces == null ? VECES_POR_DEFECTO : veces;
            if (veces < 1 || veces > VECES_MAXIMAS) {
                throw new IllegalArgumentException("Las veces por día van de 1 a " + VECES_MAXIMAS);
            }
        }
    }

    /** Un día como viene de fábrica: Automático y con Radar. */
    public static DiaDeMiRadar normal(int dia) {
        return new DiaDeMiRadar(dia, Modo.AUTOMATICO, null, false);
    }

    public boolean esNormal() {
        return modo == Modo.AUTOMATICO && !sinRadar;
    }

    /**
     * Lo que pidió la persona para este día, sobre lo que ya tenía: lo que no dice se queda como estaba (por eso el
     * guardado es «solo lo tocado»).
     */
    public DiaDeMiRadar con(Modo nuevoModo, Integer nuevasVeces, Boolean nuevoSinRadar) {
        return new DiaDeMiRadar(dia, nuevoModo == null ? modo : nuevoModo, nuevasVeces == null ? veces : nuevasVeces,
                nuevoSinRadar == null ? sinRadar : nuevoSinRadar);
    }
}
