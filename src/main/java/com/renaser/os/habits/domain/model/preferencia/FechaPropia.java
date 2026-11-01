package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.VentanaDelDia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Lo que una persona decidio solo para UNA fecha de un habito: «el sabado 7 a las 09:00, con aviso 30 y a la hora, solo vibrar» o
 * «el jueves no va». Es una fila de {@code horarios_habito_por_fecha} (V37, V38, V130, V131) vista desde el dominio (spec 028,
 * D-296) y es lo que hace posible «Solo esta semana» y repetir una semana.
 *
 * <p>Hermana de {@link HorarioSemanal} —el dia de la semana, que se repite todas las semanas— y con la misma forma: cada campo propio
 * en {@code null} es «no digo nada, hereda» y se resuelve por campo, de abajo hacia arriba. {@code recordatorio = null} es «hereda el
 * aviso»; {@link RecordatorioDelDia#sinAviso()} es «ese dia sin aviso». La fecha exacta gana sobre el dia de la semana: es la mas
 * especifica.
 *
 * <p>Una fila ENCENDIDA tiene que decir algo propio (la hora, el aviso o como suena): si no, no dice nada que lo general no diga
 * ya, y para volver a lo general se BORRA, no se vacia (el CHECK de V131). Una fila apagada ({@code activo = false}) no necesita nada.
 */
public record FechaPropia(HabitoId habitoId, LocalDate fecha, boolean activo, LocalTime horaDisparo, LocalTime horaLimite,
                          RecordatorioDelDia recordatorio, AjusteDeAlarma alarma) {

    public FechaPropia {
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        // Un ajuste que no dice nada es lo mismo que no tener ninguno.
        alarma = alarma != null && alarma.esVacio() ? null : alarma;
        if (activo && !(horaDisparo != null || recordatorio != null || alarma != null)) {
            throw new IllegalArgumentException("Una fecha encendida necesita hora, aviso o alarma propios");
        }
        // D-122: la regla vive en VentanaDelDia. Lo que se escribe ya viene normalizado; esto cubre una fila rehidratada.
        VentanaDelDia.requireVentanaNoVacia(horaDisparo, horaLimite);
    }

    /** Ese dia el habito no va; hereda todo lo demas. */
    public static FechaPropia apagada(HabitoId habitoId, LocalDate fecha) {
        return new FechaPropia(habitoId, fecha, false, null, null, null, null);
    }

    /** {@code true} si una fila encendida con estos campos todavia dice algo; si no, hay que borrarla. */
    public static boolean dejaAlgoPropio(boolean activo, LocalTime horaDisparo, RecordatorioDelDia recordatorio,
                                         AjusteDeAlarma alarma) {
        return !activo || horaDisparo != null || recordatorio != null || (alarma != null && !alarma.esVacio());
    }

    public boolean tieneHoraPropia() {
        return horaDisparo != null;
    }

    /** La misma decision, copiada a otra fecha (repetir una semana en la siguiente). */
    public FechaPropia enLaFecha(LocalDate otraFecha) {
        return new FechaPropia(habitoId, otraFecha, activo, horaDisparo, horaLimite, recordatorio, alarma);
    }
}
