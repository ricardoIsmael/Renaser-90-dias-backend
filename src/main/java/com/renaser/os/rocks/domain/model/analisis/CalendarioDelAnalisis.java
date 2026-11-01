package com.renaser.os.rocks.domain.model.analisis;

import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Las semanas y los meses del programa de UNA persona, tal como los cuenta Plan, y lo que sigue después (spec 031, D-306).
 *
 * <p>Las semanas 1 a 13 son las de {@link SemanaPrograma} (lunes a domingo; la 1 empieza el Día 1 y la 13 llega al último día de la
 * estructura de Plan). Los meses son bloques de cuatro semanas, con la 13 sumada al mes 3: 1–4, 5–8, 9–13. <b>Después de la 13</b> —
 * decisión del dueño 031-8 B: el análisis sigue mientras la persona use la app— el calendario continúa sin tope: la semana 14 empieza
 * al día siguiente del último de la 13 y llega a su domingo; de la 15 en adelante son de lunes a domingo; los meses siguen en bloques de
 * cuatro semanas (14–17, 18–21…). La duración de la formación (spec 022) no corta nada.
 *
 * <p>Pura y derivada (regla 02 §2): solo aritmética sobre el Día 1 y sobre «hoy», que entra en la zona de la persona. Correrla dos veces
 * da lo mismo.
 */
public final class CalendarioDelAnalisis {

    public enum Tipo {
        SEMANA,
        MES
    }

    /** @param numero número de la semana o del mes (desde 1); {@code inicio} y {@code fin} son inclusivos */
    public record Periodo(Tipo tipo, int numero, LocalDate inicio, LocalDate fin) {

        public Periodo {
            Objects.requireNonNull(tipo, "tipo es obligatorio");
            Objects.requireNonNull(inicio, "inicio es obligatorio");
            Objects.requireNonNull(fin, "fin es obligatorio");
        }

        /** Cerró: ya pasó su último día. */
        public boolean cerroA(LocalDate hoy) {
            return hoy.isAfter(fin);
        }
    }

    private static final int SEMANAS_POR_MES = 4;
    private static final int DIAS_POR_SEMANA = 7;
    private static final int ULTIMA_SEMANA_DE_PLAN = SemanaPrograma.ULTIMA_SEMANA;

    private final LocalDate primerDia;
    private final SemanaPrograma semanasDePlan;
    private final LocalDate inicioDeLaCatorce;
    private final LocalDate finDeLaCatorce;

    private CalendarioDelAnalisis(LocalDate primerDia) {
        this.primerDia = Objects.requireNonNull(primerDia, "primerDia es obligatorio");
        this.semanasDePlan = SemanaPrograma.desde(primerDia);
        this.inicioDeLaCatorce = semanasDePlan.limites(ULTIMA_SEMANA_DE_PLAN).fin().plusDays(1);
        this.finDeLaCatorce = inicioDeLaCatorce.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /** @param primerDia el Día 1 del programa con el ajuste de día ya aplicado */
    public static CalendarioDelAnalisis desde(LocalDate primerDia) {
        return new CalendarioDelAnalisis(primerDia);
    }

    /** La semana (desde 1) que contiene {@code fecha}; una fecha anterior al Día 1 cuenta como la 1. */
    public int numeroDeSemanaDe(LocalDate fecha) {
        if (fecha.isBefore(primerDia)) {
            return 1;
        }
        if (!fecha.isAfter(semanasDePlan.limites(ULTIMA_SEMANA_DE_PLAN).fin())) {
            return semanasDePlan.numeroSemanaParaFecha(fecha);
        }
        if (!fecha.isAfter(finDeLaCatorce)) {
            return ULTIMA_SEMANA_DE_PLAN + 1;
        }
        long semanasPasadas = ChronoUnit.DAYS.between(finDeLaCatorce.plusDays(1), fecha) / DIAS_POR_SEMANA;
        return (int) (ULTIMA_SEMANA_DE_PLAN + 2 + semanasPasadas);
    }

    /** El mes (desde 1) al que pertenece la semana: 1–4, 5–8, 9–13, 14–17… */
    public int numeroDeMesDeLaSemana(int numeroDeSemana) {
        if (numeroDeSemana <= ULTIMA_SEMANA_DE_PLAN) {
            return Math.min((numeroDeSemana - 1) / SEMANAS_POR_MES + 1, 3);
        }
        return 4 + (numeroDeSemana - (ULTIMA_SEMANA_DE_PLAN + 1)) / SEMANAS_POR_MES;
    }

    public Periodo semana(int numero) {
        if (numero < 1) {
            throw new IllegalArgumentException("La semana empieza en 1: " + numero);
        }
        if (numero <= ULTIMA_SEMANA_DE_PLAN) {
            SemanaPrograma.LimitesSemana limites = semanasDePlan.limites(numero);
            return new Periodo(Tipo.SEMANA, numero, limites.inicio(), limites.fin());
        }
        if (numero == ULTIMA_SEMANA_DE_PLAN + 1) {
            return new Periodo(Tipo.SEMANA, numero, inicioDeLaCatorce, finDeLaCatorce);
        }
        LocalDate inicio = finDeLaCatorce.plusDays(1L + (long) DIAS_POR_SEMANA * (numero - (ULTIMA_SEMANA_DE_PLAN + 2)));
        return new Periodo(Tipo.SEMANA, numero, inicio, inicio.plusDays(DIAS_POR_SEMANA - 1L));
    }

    public Periodo mes(int numero) {
        if (numero < 1) {
            throw new IllegalArgumentException("El mes empieza en 1: " + numero);
        }
        int primera = primeraSemanaDelMes(numero);
        int ultima = numero == 3 ? ULTIMA_SEMANA_DE_PLAN : primera + SEMANAS_POR_MES - 1;
        return new Periodo(Tipo.MES, numero, semana(primera).inicio(), semana(ultima).fin());
    }

    public Periodo semanaDe(LocalDate fecha) {
        return semana(numeroDeSemanaDe(fecha));
    }

    public Periodo mesDe(LocalDate fecha) {
        return mes(numeroDeMesDeLaSemana(numeroDeSemanaDe(fecha)));
    }

    /** Las semanas que componen el mes, en orden. */
    public List<Periodo> semanasDelMes(int numeroDeMes) {
        Periodo mes = mes(numeroDeMes);
        List<Periodo> semanas = new ArrayList<>();
        for (int n = numeroDeSemanaDe(mes.inicio()); n <= numeroDeSemanaDe(mes.fin()); n++) {
            semanas.add(semana(n));
        }
        return semanas;
    }

    /**
     * Las semanas y los meses que ya cerraron a {@code hoy} y cerraron hace {@code diasDeVentana} días o menos, del más viejo al más
     * nuevo. Vacío mientras el Día 1 no llega.
     */
    public List<Periodo> cerrados(LocalDate hoy, int diasDeVentana) {
        List<Periodo> cerrados = new ArrayList<>();
        int actual = numeroDeSemanaDe(hoy);
        int desde = Math.max(1, actual - diasDeVentana / DIAS_POR_SEMANA - SEMANAS_POR_MES - 2);
        for (int n = desde; n <= actual; n++) {
            sumarSiCerroEnLaVentana(cerrados, semana(n), hoy, diasDeVentana);
        }
        for (int m = Math.max(1, numeroDeMesDeLaSemana(desde)); m <= numeroDeMesDeLaSemana(actual); m++) {
            sumarSiCerroEnLaVentana(cerrados, mes(m), hoy, diasDeVentana);
        }
        return cerrados;
    }

    private static void sumarSiCerroEnLaVentana(List<Periodo> lista, Periodo periodo, LocalDate hoy, int diasDeVentana) {
        if (periodo.cerroA(hoy) && ChronoUnit.DAYS.between(periodo.fin(), hoy) <= diasDeVentana) {
            lista.add(periodo);
        }
    }

    private static int primeraSemanaDelMes(int numeroDeMes) {
        if (numeroDeMes <= 3) {
            return (numeroDeMes - 1) * SEMANAS_POR_MES + 1;
        }
        return ULTIMA_SEMANA_DE_PLAN + 1 + (numeroDeMes - 4) * SEMANAS_POR_MES;
    }
}
