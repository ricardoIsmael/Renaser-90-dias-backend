package com.renaser.os.points.api;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Se cerró la semana sábado→viernes del semáforo de una persona (sábado 00:00, su hora local).
 * Lo escuchan {@code notifications} (push y bandeja, sin cifras) y {@code rag} (mensaje del
 * acompañante con el resultado).
 *
 * <p>Se publica dentro de la misma transacción que inserta la foto de la semana, UNA vez por
 * persona y semana, y solo si la semana tuvo días medidos y el cierre ocurre dentro del fin de
 * semana: una semana cerrada tarde (el backend estuvo caído) queda en el historial pero no avisa.
 *
 * @param claveDeduplicacion {@code UUID.nameUUIDFromBytes("semaforo-semanal:" + participante + ":" + hasta)};
 *                           se usa como {@code origenEventoId} para que un reintento no duplique el aviso
 * @param porcentaje         null si ningún día tuvo algo programado
 */
public record SemanaDelSemaforoCerradaEvent(UUID claveDeduplicacion, UUID participanteId, LocalDate desde,
                                            LocalDate hasta, BigDecimal porcentaje, ColorSemaforo color,
                                            int diasConDatos, Instant cerradaEn) {

    /** Ruta profunda a la pantalla del semáforo de la propia persona. */
    public String rutaApp() {
        return "/semaforo";
    }

    public static UUID claveDe(UUID participanteId, LocalDate hasta) {
        return UUID.nameUUIDFromBytes(("semaforo-semanal:" + participanteId + ":" + hasta)
                .getBytes(StandardCharsets.UTF_8));
    }
}
