package com.renaser.os.leadership.domain.model.cumplimiento;

import java.util.Optional;

/**
 * Si una persona «programó» su día, para la ficha del líder (spec 018, D-291): las dos cosas que el dueño pidió
 * contar, juntas —«Horas y acciones»—.
 *
 * <ul>
 *   <li><b>Hábitos con su hora</b>: de los hábitos que le tocan hoy (ni apagados ni en pausa), cuántos tienen una hora
 *       que ELLA eligió y no la que vino de fábrica con el catálogo.</li>
 *   <li><b>Acciones agendadas</b>: de las acciones de sus objetivos para hoy, cuántas tienen hora de inicio.</li>
 * </ul>
 *
 * <p><b>Cuándo es «Sí»</b> (supuesto propuesto en la spec 018, a confirmar con el dueño; los números viven acá y solo
 * acá): al menos la mitad de sus hábitos con hora propia <b>y</b> todas las acciones de hoy agendadas. Sin acciones hoy
 * esa mitad se cumple sola: la pregunta la decide lo que sí tiene.
 *
 * <p>Es un dato del día de HOY de la persona, no de la ventana de siete días que muestra la ficha: «programar» es
 * armar el día, y lo que pasó ayer ya está en los días de abajo.
 */
public record ProgramacionDelDia(int habitosConHora, int habitosTotal, int accionesConHora, int accionesTotal) {

    public ProgramacionDelDia {
        if (habitosTotal < 1 || habitosConHora < 0 || habitosConHora > habitosTotal) {
            throw new IllegalArgumentException("Los hábitos con hora (" + habitosConHora + ") deben estar entre 0 y los "
                    + habitosTotal + " del día, y el día tiene que tener al menos uno");
        }
        if (accionesTotal < 0 || accionesConHora < 0 || accionesConHora > accionesTotal) {
            throw new IllegalArgumentException("Las acciones con hora (" + accionesConHora + ") deben estar entre 0 y las "
                    + accionesTotal + " del día");
        }
    }

    /**
     * La programación de un día, o vacío si no hay nada que programar: sin hábitos que le toquen hoy (programa sin
     * empezar, todo apagado o en pausa) la pregunta no aplica y la ficha no la muestra. Nunca sale un «No» por falta
     * de datos.
     */
    public static Optional<ProgramacionDelDia> de(int habitosConHora, int habitosTotal, int accionesConHora,
                                                  int accionesTotal) {
        if (habitosTotal < 1) {
            return Optional.empty();
        }
        return Optional.of(new ProgramacionDelDia(habitosConHora, habitosTotal, accionesConHora, accionesTotal));
    }

    /** El «Sí» / «No» de la ficha. */
    public boolean programo() {
        return conLaMitadDeLosHabitos() && conTodasLasAcciones();
    }

    private boolean conLaMitadDeLosHabitos() {
        return habitosConHora * 2 >= habitosTotal;
    }

    private boolean conTodasLasAcciones() {
        return accionesConHora == accionesTotal;
    }
}
