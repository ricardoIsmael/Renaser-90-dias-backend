package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.horario.VentanaDelDia;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El plan de UN habito en las fechas de una semana (spec 028): el plan de siempre ({@link SemanaDelHabito}, por dia de la semana)
 * con lo que la persona decidio solo para cada fecha ({@link FechaPropia}) encima. Existe para que ni la app ni los adaptadores
 * reimplementen la precedencia: la fecha exacta gana sobre el dia de la semana, campo por campo (hora, aviso, «Como suena»), y un dia
 * apagado en cualquiera de las dos capas sigue apagado.
 *
 * <p>La capa de fecha NUNCA enciende un dia que el plan de siempre apago —igual que la de V38, que solo resta—: ese caso se rechaza
 * al escribir ({@link CambioDeFecha}), y si llegara una fila asi desde la base se lee como lo que dice la capa de abajo.
 */
public final class PlanDeLaSemana {

    private PlanDeLaSemana() {
    }

    /**
     * Una fecha ya resuelta. {@code dia} trae lo mismo que un dia de la semana (hora, aviso, alarma y si son propios, del dia de la semana
     * o de la fecha); {@code propioDeLaFecha} dice si la fecha tiene fila propia («solo esta semana»); {@code editable}, si todavia
     * se puede cambiar (es posterior a hoy).
     */
    public record DiaDeLaFecha(LocalDate fecha, SemanaDelHabito.Dia dia, boolean propioDeLaFecha, boolean editable) {
    }

    /** Las siete fechas de la semana. {@code base} son los siete dias de la semana del habito, en cualquier orden. */
    public static List<DiaDeLaFecha> resolver(SemanaCalendaria semana, Collection<SemanaDelHabito.Dia> base,
                                              Collection<FechaPropia> filas, LocalDate hoy) {
        Map<DayOfWeek, SemanaDelHabito.Dia> porDia = base.stream()
                .collect(Collectors.toMap(SemanaDelHabito.Dia::diaSemana, Function.identity()));
        Map<LocalDate, FechaPropia> porFecha = filas.stream()
                .collect(Collectors.toMap(FechaPropia::fecha, Function.identity(), (a, b) -> b));
        return semana.fechas().stream()
                .map(fecha -> resolverFecha(fecha, porDia.get(fecha.getDayOfWeek()), porFecha.get(fecha), hoy))
                .toList();
    }

    /** Una fecha: el dia de la semana que le toca con la fila propia (o {@code null}) encima. */
    public static DiaDeLaFecha resolverFecha(LocalDate fecha, SemanaDelHabito.Dia base, FechaPropia fila, LocalDate hoy) {
        boolean editable = fecha.isAfter(hoy);
        if (fila == null) {
            return new DiaDeLaFecha(fecha, base, false, editable);
        }
        boolean conHora = fila.horaDisparo() != null;
        boolean conAviso = fila.activo() && fila.recordatorio() != null;
        var disparo = conHora ? fila.horaDisparo() : base.horaDisparo();
        var limite = conHora && fila.horaLimite() != null ? fila.horaLimite() : base.horaLimite();
        RecordatorioDelDia aviso = conAviso ? fila.recordatorio() : base.recordatorio();
        CombinacionDeAlarma alarma = fila.alarma() != null ? fila.alarma().aplicadoA(base.alarma()) : base.alarma();
        var dia = new SemanaDelHabito.Dia(base.diaSemana(), disparo, VentanaDelDia.limiteQueSigueAlDisparo(disparo, limite),
                base.horaPropia() || conHora || !fila.activo(), base.activo() && fila.activo(), aviso,
                base.recordatorioPropio() || conAviso, alarma, base.alarmaPropia() || fila.alarma() != null);
        return new DiaDeLaFecha(fecha, dia, true, editable);
    }
}
