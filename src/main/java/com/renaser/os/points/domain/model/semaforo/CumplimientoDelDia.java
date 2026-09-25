package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Un día cerrado de una persona, tal como se guarda en {@code semaforo_dias}: solo conteos. El
 * porcentaje y el color se derivan acá y en ningún otro lado (lección de V22).
 */
public record CumplimientoDelDia(LocalDate fecha, int habitosProgramados, int habitosCumplidos,
                                 int objetivosProgramados, int objetivosCumplidos) {

    public CumplimientoDelDia {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        exigirRango("habitos", habitosCumplidos, habitosProgramados);
        exigirRango("objetivos", objetivosCumplidos, objetivosProgramados);
    }

    /** Combina lo que reportaron {@code habits} y {@code rocks} para esa fecha; null = nada ese día. */
    public static CumplimientoDelDia de(LocalDate fecha, ConteoDelDia habitos, ConteoDelDia objetivos) {
        return new CumplimientoDelDia(fecha,
                habitos == null ? 0 : habitos.programados(), habitos == null ? 0 : habitos.cumplidos(),
                objetivos == null ? 0 : objetivos.programados(), objetivos == null ? 0 : objetivos.cumplidos());
    }

    public int programados() {
        return habitosProgramados + objetivosProgramados;
    }

    public int cumplidos() {
        return habitosCumplidos + objetivosCumplidos;
    }

    /** Tuvo algo programado: solo entonces tiene porcentaje y entra al promedio. */
    public boolean conDatos() {
        return programados() > 0;
    }

    public Optional<Integer> porcentaje() {
        return conDatos() ? Optional.of(ReglaDelSemaforo.porcentajeDelDia(cumplidos(), programados()))
                : Optional.empty();
    }

    public DiaDelSemaforo aDia() {
        return porcentaje()
                .map(p -> new DiaDelSemaforo(fecha, EstadoDiaSemaforo.MEDIDO, p, ReglaDelSemaforo.colorDelDia(p),
                        habitosProgramados, habitosCumplidos, objetivosProgramados, objetivosCumplidos))
                .orElseGet(() -> DiaDelSemaforo.sinPorcentaje(fecha, EstadoDiaSemaforo.SIN_DATOS));
    }

    private static void exigirRango(String que, int cumplidos, int programados) {
        if (programados < 0 || cumplidos < 0 || cumplidos > programados) {
            throw new IllegalArgumentException(que + " fuera de rango: " + cumplidos + "/" + programados);
        }
    }
}
