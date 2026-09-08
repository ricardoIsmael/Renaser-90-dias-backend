package com.renaser.os.habits.domain.model.horario;

import java.time.LocalTime;

/**
 * Los dos topes que hacen que la ventana de un habito quepa SIEMPRE dentro del dia calendario
 * (D-122, 2026-09-08).
 *
 * <p><b>El caso que la motiva.</b> Hay aprendices que trabajan de noche o de madrugada, y el
 * dueño del proyecto pidio "total libertad para configurar los habitos". La salida obvia
 * —dejar que la ventana cruce la medianoche (23:30 a 00:30)— se descarto por su propio
 * argumento: <i>"a las 12 ya es otro dia"</i>. Una ventana que cruza el cambio de dia obliga a
 * decidir a que jornada pertenece el registro, y eso contamina {@code VentanaEntrega}, la
 * expiracion, la generacion de tracks y el {@code registros_habito.dia_programa} que es
 * snapshot historico. Se resuelve antes: <b>ningun habito puede arrancar tan tarde que no
 * quepa</b>.
 *
 * <p><b>Por que un tope y un ajuste, y no dos rechazos.</b> La hora de disparo la elige la
 * persona en una rueda, asi que pasarse es una decision suya y merece un error. La hora limite,
 * en cambio, casi nunca la elige: es opcional ({@code limitTime} viaja null en la mayoria de los
 * habitos) y cuando existe suele venir arrastrada del catalogo. Rechazar por ella dejaba al
 * aprendiz trabado sin entender por que, asi que se acomoda sola al ultimo instante util del dia.
 *
 * <p>Esta clase existe para que la regla viva en UN lugar: antes la coherencia de las dos horas
 * estaba copiada en {@code HorarioHabito}, {@code HorarioSemanal} y {@code HorarioPorFecha}, y
 * {@code PreferenciaHorario} —el camino que usa el aprendiz— no la validaba en absoluto. Tres
 * copias y un hueco es exactamente la forma de E-156 y E-159.
 */
public final class VentanaDelDia {

    /** Mas tarde que esto no se puede empezar: no quedaria tiempo de completar antes de las 00:00. */
    public static final LocalTime ULTIMA_HORA_DE_DISPARO = LocalTime.of(23, 40);

    /** Ultimo instante util para entregar. Deja 10 minutos sobre {@link #ULTIMA_HORA_DE_DISPARO}. */
    public static final LocalTime ULTIMA_HORA_LIMITE = LocalTime.of(23, 50);

    private VentanaDelDia() {
    }

    /**
     * {@code null} sigue valiendo: es el horario "todo el dia" que usa casi todo el catalogo.
     *
     * @throws IllegalArgumentException si la hora de disparo pasa de {@link #ULTIMA_HORA_DE_DISPARO}
     */
    public static LocalTime requireHoraDisparoDentroDelDia(LocalTime horaDisparo) {
        if (horaDisparo != null && horaDisparo.isAfter(ULTIMA_HORA_DE_DISPARO)) {
            throw new IllegalArgumentException(
                    "horaDisparo no puede pasar de " + ULTIMA_HORA_DE_DISPARO + ", recibida: " + horaDisparo);
        }
        return horaDisparo;
    }

    /**
     * La hora limite que se guarda de verdad. Nunca falla: acomoda en vez de rechazar.
     *
     * <ul>
     *   <li>{@code null} se respeta — la mayoria de los habitos no vencen dentro del dia.</li>
     *   <li>Pasada de {@link #ULTIMA_HORA_LIMITE} (o antes/igual que el disparo, que es como
     *       llega "00:30" desde una rueda de 24 h), se acota a {@link #ULTIMA_HORA_LIMITE}.</li>
     * </ul>
     *
     * <p>Presupone una hora de disparo ya validada con
     * {@link #requireHoraDisparoDentroDelDia}: con ese tope, {@link #ULTIMA_HORA_LIMITE} siempre
     * es posterior al disparo, asi que el resultado nunca es una ventana vacia.
     */
    public static LocalTime horaLimiteAjustada(LocalTime horaDisparo, LocalTime horaLimite) {
        if (horaLimite == null) {
            return null;
        }
        if (horaLimite.isAfter(ULTIMA_HORA_LIMITE)) {
            return ULTIMA_HORA_LIMITE;
        }
        if (horaDisparo != null && !horaLimite.isAfter(horaDisparo)) {
            return ULTIMA_HORA_LIMITE;
        }
        return horaLimite;
    }

    /**
     * Guarda DEFENSIVA para los value objects que reciben una {@code PreferenciaHorario} ya
     * armada (incluida una rehidratada de la base, que no pasa por el ajuste). Lo que se escribe
     * hoy va normalizado por {@link #horaLimiteAjustada}, asi que desde el camino de escritura
     * esto no se dispara nunca.
     */
    public static void requireVentanaNoVacia(LocalTime horaDisparo, LocalTime horaLimite) {
        if (horaDisparo != null && horaLimite != null && !horaLimite.isAfter(horaDisparo)) {
            throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
        }
    }
}
