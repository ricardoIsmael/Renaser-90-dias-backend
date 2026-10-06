package com.renaser.os.rag.domain.model.espejosombra;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

/**
 * E-560 — la semana (lunes a domingo) cuyo informe del Espejo Sombra le toca generarse a UN participante, y cuándo.
 *
 * <p><b>El corte es el domingo a las 22:00 en la zona de la persona</b>, no un instante UTC fijo (regla 02 §1). El
 * cron semanal viejo corría el lunes 03:00 UTC, que es exactamente el domingo 22:00 de Lima (UTC−5): para Lima
 * esta regla da el mismo instante y la misma semana que antes. Al oeste o al este de Lima, el lunes 03:00 UTC cae
 * a otra hora local (en Los Ángeles, el domingo 19:00; en Tokio, el lunes 12:00) y la semana "pasada" quedaba
 * cortada antes de tiempo o ya con otro domingo encima.
 *
 * <p>Pura y derivada (regla 02 §2): depende solo del instante y de la zona. Correrla dos veces da lo mismo
 * ({@code EspejoSombraService.generar} no regenera un informe que ya existe) y correrla tarde se pone al día sola,
 * dentro de {@link #MARGEN_PARA_PONERSE_AL_DIA}: una noche con el servidor caído no pierde la semana.
 *
 * @param inicio el lunes de la semana de la que se genera el informe
 */
public record SemanaDelInforme(LocalDate inicio) {

    static final DayOfWeek DIA_DE_CORTE = DayOfWeek.SUNDAY;
    static final LocalTime HORA_DE_CORTE = LocalTime.of(22, 0);
    private static final int DIAS_DESDE_EL_LUNES_AL_DOMINGO = 6;

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
     * más atrás que el margen, o si todavía no llega el siguiente.
     */
    public static Optional<SemanaDelInforme> quePideGenerarseEn(ZoneId zona, Instant ahora) {
        Objects.requireNonNull(zona, "la zona del participante es obligatoria");
        ZonedDateTime corte = ultimoCorteHasta(ahora.atZone(zona));
        if (Duration.between(corte.toInstant(), ahora).compareTo(MARGEN_PARA_PONERSE_AL_DIA) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new SemanaDelInforme(
                corte.toLocalDate().minusDays(DIAS_DESDE_EL_LUNES_AL_DOMINGO)));
    }

    /** El domingo 22:00 más reciente que no es posterior a {@code ahoraLocal}, en la misma zona. */
    private static ZonedDateTime ultimoCorteHasta(ZonedDateTime ahoraLocal) {
        LocalDateTime domingo = ahoraLocal.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DIA_DE_CORTE))
                .atTime(HORA_DE_CORTE);
        ZonedDateTime corte = domingo.atZone(ahoraLocal.getZone());
        return corte.isAfter(ahoraLocal) ? domingo.minusWeeks(1).atZone(ahoraLocal.getZone()) : corte;
    }
}
