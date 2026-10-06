package com.renaser.os.calendar.domain.model.confirmacion;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Hasta cuándo se puede responder «Voy / No voy» a una ocurrencia.
 *
 * <p>Una ocurrencia sigue siendo respondible durante el día del evento y las 12 h anteriores al
 * comienzo de ese día; pasado eso es «de días pasados». «El día del evento» se mide en la zona del
 * evento, no en UTC: la medianoche UTC cae a las 19:00 en Lima, y medirla en UTC hacía saltar el
 * corte 24 h a media tarde (E-550, regla 02 §1).
 */
public final class PlazoParaResponder {

    /** Mismo margen (12 h) que {@code setRsvp()} del repo viejo. */
    static final Duration MARGEN = Duration.ofHours(12);

    private PlazoParaResponder() {
    }

    public static boolean yaVencio(Instant inicioOcurrencia, Instant ahora, ZoneId zonaDelEvento) {
        return inicioOcurrencia.isBefore(corte(ahora, zonaDelEvento));
    }

    private static Instant corte(Instant ahora, ZoneId zonaDelEvento) {
        Instant comienzoDelDia = ahora.atZone(zonaDelEvento).toLocalDate()
                .atStartOfDay(zonaDelEvento).toInstant();
        return comienzoDelDia.minus(MARGEN);
    }
}
