package com.renaser.os.leadership.domain.model.atencion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Cómo viene atendiendo un mentor las consultas (tickets de mentoría) de sus aprendices.
 *
 * <p><b>Mediana y no promedio</b> (SDD 002, plan §5): un ticket olvidado tres semanas no debe hundir
 * a quien respondió doce en una hora. Con su {@code n} a la vista ({@link #respondidas}).
 *
 * @param pendientes          tickets abiertos HOY de sus aprendices
 * @param diasDelMasAntiguo   días completos desde que se abrió el pendiente más viejo; null sin pendientes
 * @param respondidas         las que ÉL respondió en el período (atribuidas por V87, no por el mentor de hoy)
 * @param medianaHoras        mediana de horas entre abierto y respondido, 1 decimal; null sin respuestas.
 *                            Nunca 0 por falta de datos.
 */
public record AtencionDeTickets(int pendientes, Integer diasDelMasAntiguo, int respondidas, BigDecimal medianaHoras) {

    private static final BigDecimal SEGUNDOS_POR_HORA = BigDecimal.valueOf(3600);

    public AtencionDeTickets {
        if (pendientes < 0 || respondidas < 0) {
            throw new IllegalArgumentException("Una cantidad de tickets no puede ser negativa");
        }
    }

    /**
     * @param abiertos   cuándo se abrió cada ticket pendiente
     * @param respuestas cuánto tardó cada respuesta del período
     */
    public static AtencionDeTickets medir(Collection<Instant> abiertos, Collection<Duration> respuestas, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        Integer dias = abiertos.stream().min(Instant::compareTo)
                .map(masViejo -> (int) Math.max(0, Duration.between(masViejo, ahora).toDays()))
                .orElse(null);
        return new AtencionDeTickets(abiertos.size(), dias, respuestas.size(), medianaEnHoras(respuestas));
    }

    private static BigDecimal medianaEnHoras(Collection<Duration> respuestas) {
        if (respuestas.isEmpty()) {
            return null;
        }
        List<Long> segundos = respuestas.stream().map(d -> Math.max(0, d.toSeconds())).sorted().toList();
        int medio = segundos.size() / 2;
        BigDecimal mediana = segundos.size() % 2 == 1
                ? BigDecimal.valueOf(segundos.get(medio))
                : BigDecimal.valueOf(segundos.get(medio - 1) + segundos.get(medio)).divide(BigDecimal.TWO);
        return mediana.divide(SEGUNDOS_POR_HORA, 1, RoundingMode.HALF_UP);
    }
}
