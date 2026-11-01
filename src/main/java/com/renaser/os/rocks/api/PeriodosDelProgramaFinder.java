package com.renaser.os.rocks.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Las semanas y los meses del programa de una persona, como los cuenta Plan (spec 031, D-306): quien más de un módulo necesita
 * para decir «qué semana» o «qué mes» sin reimplementar la cuenta. Todo es aritmética sobre el Día 1 (con el ajuste ya aplicado), la
 * duración de SU programa (90 o 365 días, spec 022) y «hoy» en la zona de la persona, que entran por parámetro: no lee ni guarda nada.
 *
 * <p><b>Semana</b>: de lunes a domingo; la 1 empieza el Día 1 y la última llega al último día del programa ({@code SemanaPrograma}): la 13
 * en 90 días y la 53 en 365. <b>Mes</b>: bloque de cuatro semanas y el último se lleva las que sobran (1–4, 5–8, 9–13 en 90 días; trece
 * meses en 365, el último hasta la 53). Pasada la última semana —decisión del dueño 031-8 B— el calendario sigue: la semana siguiente
 * empieza el día siguiente al último del programa y llega a su domingo, y los meses siguen en bloques de cuatro semanas.
 */
public interface PeriodosDelProgramaFinder {

    /** La semana que contiene {@code fecha}; una fecha anterior al Día 1 cuenta como la semana 1. */
    PeriodoDelPrograma semanaDe(LocalDate primerDia, int duracionDias, LocalDate fecha);

    /** El mes que contiene {@code fecha}. */
    PeriodoDelPrograma mesDe(LocalDate primerDia, int duracionDias, LocalDate fecha);

    /** El periodo de ese tipo que empieza exactamente en {@code inicio}; vacío si ninguno empieza ahí. */
    Optional<PeriodoDelPrograma> queEmpiezaEn(LocalDate primerDia, int duracionDias, Tipo tipo, LocalDate inicio);

    /** El periodo anterior del mismo tipo; vacío para el primero. */
    Optional<PeriodoDelPrograma> anteriorA(LocalDate primerDia, int duracionDias, PeriodoDelPrograma periodo);

    /** El periodo siguiente del mismo tipo si ya empezó a {@code hoy}; vacío si todavía no llega. */
    Optional<PeriodoDelPrograma> siguienteA(LocalDate primerDia, int duracionDias, PeriodoDelPrograma periodo, LocalDate hoy);

    /** Las semanas que componen un mes, en orden. */
    List<PeriodoDelPrograma> semanasDelMes(LocalDate primerDia, int duracionDias, PeriodoDelPrograma mes);

    /**
     * Las semanas y los meses que ya cerraron a {@code hoy} y cerraron hace {@code diasDeVentana} días o menos (un periodo cierra cuando
     * pasa su último día). Del más viejo al más nuevo; vacío si el Día 1 todavía no llega.
     */
    List<PeriodoDelPrograma> cerradosHastaHoy(LocalDate primerDia, int duracionDias, LocalDate hoy, int diasDeVentana);

    enum Tipo {
        SEMANA,
        MES
    }

    /**
     * @param numero número de la semana o del mes en el programa (desde 1)
     * @param inicio primer día, inclusivo; la semana 1 y la 14 pueden no empezar en lunes
     * @param fin    último día, inclusivo
     */
    record PeriodoDelPrograma(Tipo tipo, int numero, LocalDate inicio, LocalDate fin) {

        /** Cerró: ya pasó su último día. */
        public boolean cerroA(LocalDate hoy) {
            return hoy.isAfter(fin);
        }
    }
}
