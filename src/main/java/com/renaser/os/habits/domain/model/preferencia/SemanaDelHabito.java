package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.horario.VentanaDelDia;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Los SIETE dias de la semana de un habito ya resueltos: lo propio de cada dia y, donde no hay nada
 * propio, lo general. Existe para que la pantalla y la app no reimplementen la precedencia, que es una
 * regla del servidor: dos copias de la misma mezcla terminan separandose (V39).
 *
 * <p><b>Hora y aviso se resuelven por separado</b> (spec 008, D-282): un dia puede tener hora propia y
 * heredar el aviso, heredar la hora y tener aviso propio, o las dos. {@link Dia#horaPropia()} y
 * {@link Dia#recordatorioPropio()} dicen cual de las dos cosas viene de la fila del dia.
 *
 * <p><b>Spec 009 (D-283).</b> Como suena la alarma es un tercer campo, tambien por separado:
 * {@link Dia#alarma()} es la combinacion resuelta y {@link Dia#alarmaPropia()} dice si la fila del dia trae
 * la suya.
 */
public final class SemanaDelHabito {

    private SemanaDelHabito() {
    }

    /**
     * @param horaPropia          el dia tiene hora propia o esta apagado (V40: una fila apagada cuenta como
     *                            propia y muestra la hora general); {@code false} si hereda la hora
     * @param recordatorioPropio  la fila del dia trae su aviso (incluido «sin aviso»); {@code false} si hereda
     * @param alarma              como suena ese dia, ya resuelto: lo propio campo por campo y, donde no hay, lo general
     * @param alarmaPropia        la fila del dia trae su combinacion de alarma; {@code false} si hereda la general
     */
    public record Dia(DayOfWeek diaSemana, LocalTime horaDisparo, LocalTime horaLimite, boolean horaPropia,
                      boolean activo, RecordatorioDelDia recordatorio, boolean recordatorioPropio,
                      CombinacionDeAlarma alarma, boolean alarmaPropia) {

        /** Forma de la 008: sin la alarma, rige la de hoy. */
        public Dia(DayOfWeek diaSemana, LocalTime horaDisparo, LocalTime horaLimite, boolean horaPropia,
                   boolean activo, RecordatorioDelDia recordatorio, boolean recordatorioPropio) {
            this(diaSemana, horaDisparo, horaLimite, horaPropia, activo, recordatorio, recordatorioPropio,
                    CombinacionDeAlarma.COMO_HOY, false);
        }
    }

    /**
     * @param propios        las filas de dia de la semana del habito (pueden ser menos de siete)
     * @param disparoGeneral la hora que rige cuando el dia no trae la suya; {@code null} si no hay ninguna
     * @param limiteGeneral  la hora limite que rige cuando el dia no trae la suya
     * @param general        el aviso general del habito ({@link RecordatorioDelDia#delGeneral})
     */
    public static List<Dia> resolver(Collection<HorarioSemanal> propios, LocalTime disparoGeneral,
                                     LocalTime limiteGeneral, RecordatorioDelDia general) {
        return resolver(propios, disparoGeneral, limiteGeneral, general, CombinacionDeAlarma.COMO_HOY);
    }

    /**
     * @param alarmaGeneral como suena la alarma del habito ya resuelta ({@code COMO_HOY} si nadie eligio nada)
     */
    public static List<Dia> resolver(Collection<HorarioSemanal> propios, LocalTime disparoGeneral,
                                     LocalTime limiteGeneral, RecordatorioDelDia general,
                                     CombinacionDeAlarma alarmaGeneral) {
        Map<DayOfWeek, HorarioSemanal> porDia = propios.stream()
                .collect(Collectors.toMap(HorarioSemanal::diaSemana, Function.identity()));
        return Stream.of(DayOfWeek.values())
                .map(dia -> resolverDia(dia, porDia.get(dia), disparoGeneral, limiteGeneral, general, alarmaGeneral))
                .toList();
    }

    private static Dia resolverDia(DayOfWeek dia, HorarioSemanal propio, LocalTime disparoGeneral,
                                   LocalTime limiteGeneral, RecordatorioDelDia general,
                                   CombinacionDeAlarma alarmaGeneral) {
        if (propio == null) {
            return new Dia(dia, disparoGeneral, VentanaDelDia.limiteQueSigueAlDisparo(disparoGeneral, limiteGeneral),
                    false, true, general, false, alarmaGeneral, false);
        }
        // Respaldo por CAMPO, igual que en el adaptador: una fila que solo fija la hora de disparo conserva la
        // hora limite general, y una que solo APAGA o solo trae el aviso no trae hora: muestra la general.
        LocalTime disparo = propio.tieneHoraPropia() ? propio.preferencia().horaDisparo() : disparoGeneral;
        LocalTime limite = propio.preferencia().horaLimite() != null ? propio.preferencia().horaLimite()
                : limiteGeneral;
        RecordatorioDelDia aviso = propio.recordatorio() != null ? propio.recordatorio() : general;
        return new Dia(dia, disparo, VentanaDelDia.limiteQueSigueAlDisparo(disparo, limite),
                propio.tieneHoraPropia() || !propio.activo(), propio.activo(), aviso, propio.recordatorio() != null,
                alarmaGeneral.con(propio.alarma()), propio.alarma() != null);
    }
}
