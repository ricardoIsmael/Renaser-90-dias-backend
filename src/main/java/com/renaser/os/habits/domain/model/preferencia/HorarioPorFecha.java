package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.horario.VentanaDelDia;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Que hace este aprendiz con este habito ESE dia: correrlo a otra hora, o no correrlo.
 *
 * <p>Excepcion puntual: no cambia la preferencia general ni otra fecha del mismo habito.
 *
 * <p><b>Ampliado 2026-09-07 (V38).</b> Antes solo podia decir "ese dia a otra hora" y por eso la
 * hora era obligatoria siempre. Ahora tambien puede decir "ese dia no va", y ahi la hora no aporta:
 * exigirla congelaria el horario de ese dia si despues cambia el general. La invariante no
 * desaparecio, se volvio condicional -- con {@code activo} la hora sigue siendo obligatoria.
 */
public record HorarioPorFecha(LocalDate fecha, PreferenciaHorario preferencia, boolean activo) {

    public HorarioPorFecha {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(preferencia, "preferencia es obligatoria");
        if (activo) {
            Objects.requireNonNull(preferencia.horaDisparo(), "horaDisparo es obligatoria");
        }
        // D-122: la regla vive en VentanaDelDia, no copiada aca. Lo que se escribe ya viene
        // normalizado por PreferenciaHorario; esto solo cubre una fila rehidratada de la base.
        VentanaDelDia.requireVentanaNoVacia(preferencia.horaDisparo(), preferencia.horaLimite());
    }

    /** Firma historica (V37): una excepcion de horario siempre dejaba el habito encendido. */
    public HorarioPorFecha(LocalDate fecha, PreferenciaHorario preferencia) {
        this(fecha, preferencia, true);
    }

    /**
     * MOVER la hora sigue siendo cosa de una fecha futura: el dia en curso no se reacomoda, que es
     * la regla que V37 puso para que nadie se cambie el horario cuando la ventana ya arranco.
     */
    public static void requirePlanificable(LocalDate fecha, LocalDate hoy) {
        if (!fecha.isAfter(hoy)) {
            throw new IllegalArgumentException("Solo puedes planificar horarios para una fecha futura");
        }
    }

    /**
     * APAGAR el dia si se permite HOY, y esa es la diferencia con {@link #requirePlanificable}.
     *
     * <p>No son la misma regla aunque toquen la misma tabla. La de arriba protege el orden del dia
     * en curso: correr la hora de algo que ya empezo es lo que no se puede. Bajar el interruptor no
     * corre nada -- dice "hoy no", que es justo lo que una pantalla del dia (Training) tiene que
     * poder hacer. Lo que no se puede es apagar el pasado, porque ya paso.
     */
    public static void requireApagable(LocalDate fecha, LocalDate hoy) {
        if (fecha.isBefore(hoy)) {
            throw new IllegalArgumentException("No puedes cambiar un dia que ya paso");
        }
    }
}
