package com.renaser.os.rag.domain.model.conversacion;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Cuanto puede hablar una persona con el acompanante por voz en vivo (D-162).
 *
 * <p>Dos limites distintos, y los dos importan:
 * <ul>
 *   <li>{@code porDia}: <b>10 minutos por persona y por dia</b>, decision del dueno del 2026-09-24
 *   (§8 de {@code docs/arquitectura/PROPUESTA_GEMINI_LIVE.md}). Pasado el limite, el orbe vuelve al
 *   flujo anterior (reconocimiento de voz del telefono, chat y voz Kore).</li>
 *   <li>{@code maximaPorSesion}: <b>15 minutos</b>, el tope de una sesion de solo audio de Gemini
 *   Live. Con la cuota de 10 minutos no se alcanza nunca; existe para que subir la cuota no deje
 *   sesiones que Google corta a mitad de una frase.</li>
 * </ul>
 *
 * <p><b>El dia es el de la persona, no el del servidor</b> (regla 02). Un cron o un contador que
 * use la fecha UTC renueva la cuota a las 19:00 de Lima.
 */
public record CuotaDeVozEnVivo(Duration porDia, Duration maximaPorSesion) {

    public CuotaDeVozEnVivo {
        Objects.requireNonNull(porDia, "porDia es obligatorio");
        Objects.requireNonNull(maximaPorSesion, "maximaPorSesion es obligatoria");
        if (porDia.isNegative() || maximaPorSesion.isNegative() || maximaPorSesion.isZero()) {
            throw new IllegalArgumentException("Los limites de la voz en vivo tienen que ser positivos");
        }
    }

    /** El dia calendario de la persona en ese instante. */
    public static LocalDate diaDe(Instant ahora, ZoneId zona) {
        return ahora.atZone(zona).toLocalDate();
    }

    /** Lo que le queda hoy; nunca negativo. */
    public Duration restante(Duration usadoHoy) {
        Duration restante = porDia.minus(usadoHoy);
        return restante.isNegative() ? Duration.ZERO : restante;
    }

    public boolean agotada(Duration usadoHoy) {
        return restante(usadoHoy).isZero();
    }

    /** Si una sesion que empezo en {@code inicio} ya llego al tope de Gemini Live. */
    public boolean sesionVencida(Instant inicio, Instant ahora) {
        return Duration.between(inicio, ahora).compareTo(maximaPorSesion) >= 0;
    }
}
