package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.SemanaDelSemaforo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * La foto de una semana sábado→viernes tomada al cerrarla (sábado 00:00 local): lo que se le
 * informó a la persona. Append-only: nunca se reescribe (V68). La toma
 * {@link MedicionDeLaPersona#fotoDe}.
 *
 * @param porcentaje   null si ningún día medido tuvo algo programado
 * @param diasConDatos días medidos con algo programado (los que entran al promedio)
 * @param diasMedidos  días de la semana dentro del programa y sin pausa
 */
public record FotoSemanal(LocalDate semanaHasta, LocalDate semanaDesde, BigDecimal porcentaje, int diasConDatos,
                          int diasMedidos, String versionFormula, Instant cerradaEn) {

    public FotoSemanal {
        SemanaDelSemaforo.exigirCierreValido(semanaHasta);
        if (!SemanaDelSemaforo.desde(semanaHasta).equals(semanaDesde)) {
            throw new IllegalArgumentException("La semana va de sabado a viernes: " + semanaDesde + ".." + semanaHasta);
        }
        if ((porcentaje == null) != (diasConDatos == 0)) {
            throw new IllegalArgumentException("Sin dias con datos no hay porcentaje, y viceversa");
        }
        if (diasConDatos < 0 || diasConDatos > diasMedidos || diasMedidos > SemanaDelSemaforo.DIAS) {
            throw new IllegalArgumentException("Dias fuera de rango: " + diasConDatos + "/" + diasMedidos);
        }
        Objects.requireNonNull(versionFormula, "versionFormula es obligatoria");
        Objects.requireNonNull(cerradaEn, "cerradaEn es obligatorio");
    }

    public ColorSemaforo color() {
        return ReglaDelSemaforo.colorDe(porcentaje);
    }

    /** Una semana entera pausada o fuera del programa se guarda, pero no avisa a nadie. */
    public boolean tuvoDiasMedidos() {
        return diasMedidos > 0;
    }

    public SemanaCerrada aSemanaCerrada() {
        return new SemanaCerrada(semanaDesde, semanaHasta, porcentaje, color(), diasConDatos, cerradaEn);
    }
}
