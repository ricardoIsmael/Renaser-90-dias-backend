package com.renaser.os.rag.domain.model.espejosombra;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

/**
 * E-560 — la semana (lunes a domingo) cuyo informe del Espejo Sombra le toca generarse a UN participante, y cuándo.
 *
 * <p><b>El corte es la medianoche del domingo en la zona de la persona</b> (lunes 00:00 local), con la semana
 * completa: decisión del dueño del 2026-10-06. Antes el barrido corría el lunes 03:00 UTC con la fecha del servidor,
 * que en Lima es el domingo 22:00 —dos horas antes de cerrar la semana— y en otras zonas caía a cualquier hora
 * (regla 02 §1, la familia de E-91). Para Lima la regla nueva mueve el corte de domingo 22:00 a lunes 00:00; la
 * semana que se informa es la misma, pero ahora incluye las últimas dos horas del domingo.
 *
 * <p>Pura y derivada (regla 02 §2): depende solo del instante y de la zona. Correrla dos veces da lo mismo
 * ({@code EspejoSombraService.generar} no regenera un informe que ya existe) y correrla tarde se pone al día sola,
 * dentro de {@link #MARGEN_PARA_PONERSE_AL_DIA}: una noche con el servidor caído no pierde la semana.
 *
 * @param inicio el lunes de la semana de la que se genera el informe
 */
public record SemanaDelInforme(LocalDate inicio) {

    static final DayOfWeek DIA_DE_CORTE = DayOfWeek.MONDAY;

    /**
     * Cuánto después de su corte una semana sigue pidiendo generarse. Un día: cubre un servidor caído toda una
     * noche sin reintentar para siempre (un informe es definitivo; una semana sin entradas o con la IA caída se
     * vuelve a evaluar en cada barrido solo mientras dure este margen).
     */
    static final Duration MARGEN_PARA_PONERSE_AL_DIA = Duration.ofHours(24);

    public SemanaDelInforme {
        Objects.requireNonNull(inicio, "el lunes de la semana es obligatorio");
    }

    /**
     * La semana que pide generarse para alguien de esa zona en ese instante, o vacío si su último corte ya quedó
     * más atrás que el margen.
     */
    public static Optional<SemanaDelInforme> quePideGenerarseEn(ZoneId zona, Instant ahora) {
        Objects.requireNonNull(zona, "la zona del participante es obligatoria");
        ZonedDateTime corte = ultimoCorteHasta(ahora.atZone(zona));
        if (Duration.between(corte.toInstant(), ahora).compareTo(MARGEN_PARA_PONERSE_AL_DIA) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new SemanaDelInforme(corte.toLocalDate().minusWeeks(1)));
    }

    /** El lunes 00:00 más reciente que no es posterior a {@code ahoraLocal}, en la misma zona. */
    private static ZonedDateTime ultimoCorteHasta(ZonedDateTime ahoraLocal) {
        return ahoraLocal.toLocalDate().with(TemporalAdjusters.previousOrSame(DIA_DE_CORTE))
                .atStartOfDay(ahoraLocal.getZone());
    }
}
