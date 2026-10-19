package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.points.api.SemanaDelSemaforo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Los siete días que el líder mira: UNA semana del semáforo, de sábado a viernes (spec 005, R-2). Siempre es una semana
 * entera y nunca una que todavía no empezó; no hay «los últimos 7 días» sueltos.
 *
 * <p>Sin fecha pedida, cada vista tiene su propio punto de partida (decisión del dueño del 2026-10-19):
 * <ul>
 *   <li>la LISTA del grupo abre en la última semana ya cerrada ({@link #paraLaLista}): es la que tiene color y
 *       porcentaje definitivos;</li>
 *   <li>la FICHA de una persona abre en la semana en curso ({@link #paraLaFicha}), con los días que faltan marcados
 *       como futuros.</li>
 * </ul>
 * La app, al abrir una ficha desde la lista, pasa el {@code hasta} de la lista, así que las dos hablan de los mismos
 * días (SC-002).
 *
 * <p>El «hoy» es el de la persona (o el del grupo, para la lista), no el del servidor (.claude/rules/02): lo resuelve
 * quien llama con {@code clock.now().atZone(zona).toLocalDate()}.
 *
 * @param cerrada  true solo si es una semana ya cerrada el sábado (su resultado ya no cambia)
 * @param hoyLocal el día de hoy en la zona de quien se mira; de él sale qué días son futuros
 */
public record VentanaDeLaFicha(LocalDate desde, LocalDate hasta, boolean cerrada, LocalDate hoyLocal) {

    public VentanaDeLaFicha {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
    }

    /**
     * @param semanaHasta null = la última semana ya cerrada; si viene, tiene que ser un viernes
     * @throws IllegalArgumentException si no es viernes o si esa semana todavía no empezó
     */
    public static VentanaDeLaFicha paraLaLista(LocalDate semanaHasta, LocalDate hoyLocal) {
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        return de(semanaHasta != null ? semanaHasta : SemanaDelSemaforo.ultimaCerradaAl(hoyLocal), hoyLocal);
    }

    /**
     * @param semanaHasta null = la semana en curso (la que contiene a hoy); si viene, tiene que ser un viernes
     * @throws IllegalArgumentException si no es viernes o si esa semana todavía no empezó
     */
    public static VentanaDeLaFicha paraLaFicha(LocalDate semanaHasta, LocalDate hoyLocal) {
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        return de(semanaHasta != null ? semanaHasta : SemanaDelSemaforo.cierreDe(hoyLocal), hoyLocal);
    }

    /** El sábado que abre la semana que contiene {@code fecha}: de ahí sale cuál fue la primera semana de alguien. */
    public static LocalDate sabadoQueAbreLaSemanaDe(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        return fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }

    /** Un día que todavía no llegó: la semana en curso lo trae para que se vea completa, pero no tiene nada. */
    public boolean esFutura(LocalDate dia) {
        return dia.isAfter(hoyLocal);
    }

    /** Hoy: el día corre y todavía no se cierra, así que el semáforo no lo mide. */
    public boolean esHoy(LocalDate dia) {
        return dia.equals(hoyLocal);
    }

    private static VentanaDeLaFicha de(LocalDate semanaHasta, LocalDate hoyLocal) {
        LocalDate desde = SemanaDelSemaforo.desde(semanaHasta);
        if (desde.isAfter(hoyLocal)) {
            throw new IllegalArgumentException("No hay semanas futuras: la del " + desde + " todavia no empezo");
        }
        return new VentanaDeLaFicha(desde, semanaHasta, !semanaHasta.isAfter(SemanaDelSemaforo.ultimaCerradaAl(hoyLocal)),
                hoyLocal);
    }
}
