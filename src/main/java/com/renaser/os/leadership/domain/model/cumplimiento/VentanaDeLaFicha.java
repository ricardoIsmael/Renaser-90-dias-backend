package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.points.api.SemanaDelSemaforo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Los siete días que el líder mira, y son SIEMPRE los del resumen «Semáforo por grupos» (spec 005, R-2; decisión del
 * dueño del 2026-10-19: «la ventana del resumen manda en todo»). Así el resumen, la lista del grupo y la ficha de la
 * persona hablan de los mismos días, con el mismo color y el mismo porcentaje:
 * <ul>
 *   <li>sin fecha, la ventana VIGENTE: los siete días que terminan ayer ({@code desde = hasta − 6}), aunque ayer no
 *       sea viernes;</li>
 *   <li>con un viernes, la semana del semáforo de sábado a viernes que termina ese día.</li>
 * </ul>
 * Ninguna llega al futuro: pedir un viernes que todavía no llegó es un error.
 *
 * <p>El «hoy» es el de la persona (o el del grupo, para la lista), no el del servidor (.claude/rules/02): lo resuelve
 * quien llama con {@code clock.now().atZone(zona).toLocalDate()}.
 *
 * @param vigente  true si se pidió sin fecha: el semáforo se lee como la ventana vigente y no como una semana
 * @param cerrada  true solo si el último día cae en una semana ya cerrada el sábado (su resultado ya no cambia); sale
 *                 del calendario, no de que cada persona tenga su foto del cierre
 * @param hoyLocal el día de hoy en la zona de quien se mira
 */
public record VentanaDeLaFicha(LocalDate desde, LocalDate hasta, boolean vigente, boolean cerrada, LocalDate hoyLocal) {

    public VentanaDeLaFicha {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
    }

    /**
     * @param semanaHasta null = la ventana vigente (los 7 días que terminan ayer); si viene, tiene que ser un viernes
     * @throws IllegalArgumentException si no es viernes o si todavía no llegó
     */
    public static VentanaDeLaFicha de(LocalDate semanaHasta, LocalDate hoyLocal) {
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        boolean vigente = semanaHasta == null;
        LocalDate hasta = vigente ? hoyLocal.minusDays(1) : semanaHasta;
        if (!vigente) {
            exigirQueSeaUnaSemanaQueLlego(hasta, hoyLocal);
        }
        boolean cerrada = !hasta.isAfter(SemanaDelSemaforo.ultimaCerradaAl(hoyLocal));
        return new VentanaDeLaFicha(hasta.minusDays(SemanaDelSemaforo.DIAS - 1L), hasta, vigente, cerrada, hoyLocal);
    }

    /** El sábado que abre la semana que contiene {@code fecha}: de ahí sale cuál fue la primera semana de alguien. */
    public static LocalDate sabadoQueAbreLaSemanaDe(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }

    /** Hoy: el día corre y todavía no se cierra, así que el semáforo no lo mide. */
    public boolean esHoy(LocalDate dia) {
        return dia.equals(hoyLocal);
    }

    private static void exigirQueSeaUnaSemanaQueLlego(LocalDate viernes, LocalDate hoyLocal) {
        SemanaDelSemaforo.exigirCierreValido(viernes);
        if (viernes.isAfter(hoyLocal)) {
            throw new IllegalArgumentException("No hay semanas futuras: la que termina el " + viernes + " todavia no llego");
        }
    }
}
