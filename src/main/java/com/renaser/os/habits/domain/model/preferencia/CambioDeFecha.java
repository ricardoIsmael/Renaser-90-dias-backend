package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.VentanaDelDia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Lo que la persona TOCO de una fecha en el editor de «Solo esta semana» (spec 028, D-296): cada campo en {@code null} es «no lo toque»
 * y se conserva lo que la fila ya tuviera, igual que el editor ya guarda «solo lo tocado» por dia de la semana (specs 008 y 009).
 *
 * <p>{@link #aplicarA} une el cambio con la fila que ya hubiera y con el dia de la semana que rige hoy ({@code base}), y devuelve la
 * fila resultante o vacio si no queda nada propio (hay que borrarla). Un valor IGUAL al que ya rige ese dia de la semana se guarda como
 * «hereda» para que un cambio posterior del plan de siempre siga llegando a esa fecha.
 *
 * @param volverAlPlanDeSiempre la fecha vuelve al plan de siempre: se borra la fila; los demas campos no cuentan
 * @param activo                {@code false} apaga el dia, {@code true} lo enciende; {@code null} no lo toca
 * @param horaDisparo           la hora nueva; {@code null} no la toca. {@code horaLimite} solo cuenta junto a ella
 * @param recordatorio          el aviso nuevo (con {@link RecordatorioDelDia#sinAviso()} para ese dia sin aviso); {@code null} no lo toca
 * @param alarma                la combinacion de «Como suena» nueva, entera; {@code null} no la toca
 */
public record CambioDeFecha(LocalDate fecha, boolean volverAlPlanDeSiempre, Boolean activo, LocalTime horaDisparo,
                            LocalTime horaLimite, RecordatorioDelDia recordatorio, CombinacionDeAlarma alarma) {

    /** La fecha vuelve al plan de siempre. */
    public static CambioDeFecha restablecer(LocalDate fecha) {
        return new CambioDeFecha(fecha, true, null, null, null, null, null);
    }

    /**
     * @param existente  la fila que la fecha ya tenia, o {@code null}
     * @param base       el dia de la semana de esa fecha tal como rige en el plan de siempre
     * @param desactivable {@code false} si el habito es obligatorio del programa: no se puede apagar
     * @return la fila que debe quedar, o vacio si la fecha no queda con nada propio (se borra)
     * @throws IllegalArgumentException si la hora no cabe en el dia o se enciende un dia apagado en el plan de siempre
     * @throws IllegalStateException    si se apaga un habito obligatorio
     */
    public Optional<FechaPropia> aplicarA(HabitoId habitoId, FechaPropia existente, SemanaDelHabito.Dia base,
                                          boolean desactivable) {
        if (volverAlPlanDeSiempre) {
            return Optional.empty();
        }
        boolean encendida = activoResultante(existente, base, desactivable);
        LocalTime disparo = existente != null ? existente.horaDisparo() : null;
        LocalTime limite = existente != null ? existente.horaLimite() : null;
        if (horaDisparo != null) {
            LocalTime nueva = VentanaDelDia.requireHoraDisparoDentroDelDia(horaDisparo);
            boolean igualALaDeSiempre = nueva.equals(base.horaDisparo());
            disparo = igualALaDeSiempre ? null : nueva;
            limite = igualALaDeSiempre ? null : VentanaDelDia.horaLimiteAjustada(nueva, horaLimite);
        }
        RecordatorioDelDia aviso = existente != null ? existente.recordatorio() : null;
        if (recordatorio != null) {
            aviso = recordatorio.equals(base.recordatorio()) ? null : recordatorio;
        }
        AjusteDeAlarma ajuste = existente != null ? existente.alarma() : null;
        if (alarma != null) {
            ajuste = alarma.equals(base.alarma()) ? null : AjusteDeAlarma.de(alarma);
        }
        if (!FechaPropia.dejaAlgoPropio(encendida, disparo, aviso, ajuste)) {
            return Optional.empty();
        }
        return Optional.of(new FechaPropia(habitoId, fecha, encendida, disparo, limite, aviso, ajuste));
    }

    private boolean activoResultante(FechaPropia existente, SemanaDelHabito.Dia base, boolean desactivable) {
        if (activo == null) {
            return existente == null || existente.activo();
        }
        if (!activo) {
            if (!desactivable) {
                throw new IllegalStateException("Este habito es obligatorio y no se puede apagar");
            }
            return false;
        }
        if (!base.activo()) {
            throw new IllegalArgumentException("Ese dia esta apagado en tu plan de siempre");
        }
        return true;
    }
}
