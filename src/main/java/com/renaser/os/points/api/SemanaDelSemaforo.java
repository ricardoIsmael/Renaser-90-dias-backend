package com.renaser.os.points.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * La semana del semáforo, en un solo lugar: va de SÁBADO a VIERNES y se cierra el sábado a las
 * 00:00 hora local de cada persona (pedido del dueño, 2026-09-25, D-168).
 *
 * <p>Es distinta a propósito de la semana lunes→domingo del seguimiento del mentor y de las rocas
 * semanales: el reporte sale el sábado a las 00:00 y cubre los siete días cerrados que terminan
 * ese viernes. Quien necesite la semana del semáforo la pide acá; nadie recalcula el corte.
 *
 * <p>Todas las fechas son fechas LOCALES del participante (regla 02 §1): quien llama resuelve
 * {@code clock.now().atZone(zona).toLocalDate()}, nunca {@code clock.today()}.
 */
public final class SemanaDelSemaforo {

    public static final DayOfWeek DIA_DE_CIERRE = DayOfWeek.FRIDAY;
    public static final int DIAS = 7;

    private SemanaDelSemaforo() {
    }

    /**
     * El viernes de la última semana ya cerrada al llegar {@code hoyLocal}. Un sábado devuelve el
     * viernes de ayer (la semana acaba de cerrar); un viernes devuelve el de hace siete días (la de
     * hoy todavía corre).
     */
    public static LocalDate ultimaCerradaAl(LocalDate hoyLocal) {
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        return hoyLocal.minusDays(1).with(TemporalAdjusters.previousOrSame(DIA_DE_CIERRE));
    }

    /** El viernes que cierra la semana que contiene {@code fecha}. */
    public static LocalDate cierreDe(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        return fecha.with(TemporalAdjusters.nextOrSame(DIA_DE_CIERRE));
    }

    /** El sábado que abre la semana que termina en {@code semanaHasta}. */
    public static LocalDate desde(LocalDate semanaHasta) {
        exigirCierreValido(semanaHasta);
        return semanaHasta.minusDays(DIAS - 1L);
    }

    public static boolean esCierreValido(LocalDate semanaHasta) {
        return semanaHasta != null && semanaHasta.getDayOfWeek() == DIA_DE_CIERRE;
    }

    public static void exigirCierreValido(LocalDate semanaHasta) {
        if (!esCierreValido(semanaHasta)) {
            throw new IllegalArgumentException("La semana del semaforo termina en viernes: " + semanaHasta);
        }
    }
}
