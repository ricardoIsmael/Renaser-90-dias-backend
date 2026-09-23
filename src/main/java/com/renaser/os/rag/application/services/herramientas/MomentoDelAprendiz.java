package com.renaser.os.rag.application.services.herramientas;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * El "ahora" de un aprendiz en SU zona (regla 02): todo lo que las herramientas de tiempo dicen
 * como hora ("vence a las 21:10") o como espera ("faltan 25 min") sale de aca, para que ninguna
 * lo calcule con la fecha del servidor ni lo deje en manos del modelo.
 *
 * @param ahora el instante del {@code Clock} inyectado
 * @param zona  la zona con la que {@code habits} calculo los plazos
 */
record MomentoDelAprendiz(Instant ahora, ZoneId zona) {

    private static final DateTimeFormatter HORA_MINUTO = DateTimeFormatter.ofPattern("HH:mm");

    MomentoDelAprendiz {
        Objects.requireNonNull(ahora, "ahora es obligatorio");
        Objects.requireNonNull(zona, "zona es obligatoria");
    }

    /** La fecha local del aprendiz, no la del servidor: a las 02:00 UTC en Lima todavia es ayer. */
    LocalDate hoy() {
        return ahora.atZone(zona).toLocalDate();
    }

    /** La proxima vez que su reloj marque {@code hora}: hoy si todavia no paso, si no manana. */
    Instant proxima(LocalTime hora) {
        Instant deHoy = ZonedDateTime.of(hoy(), hora, zona).toInstant();
        return deHoy.isBefore(ahora) ? ZonedDateTime.of(hoy().plusDays(1), hora, zona).toInstant() : deHoy;
    }

    /** "21:10", o "01:10 del dia siguiente" cuando el instante ya cae en otra fecha local. */
    String horaDe(Instant instante) {
        String hora = instante.atZone(zona).toLocalTime().format(HORA_MINUTO);
        return caeDespuesDeHoy(instante) ? hora + " del dia siguiente" : hora;
    }

    boolean caeDespuesDeHoy(Instant instante) {
        return instante.atZone(zona).toLocalDate().isAfter(hoy());
    }

    /**
     * Cuanto falta, redondeado hacia ABAJO: con 90 segundos por delante decir "2 min" haria creer
     * que sobra tiempo. Nunca negativo.
     */
    String faltaPara(Instant instante) {
        long minutos = Math.max(0, Duration.between(ahora, instante).toMinutes());
        if (minutos == 0) {
            return "menos de 1 min";
        }
        return minutos < 60 ? minutos + " min" : minutos / 60 + " h " + minutos % 60 + " min";
    }
}
