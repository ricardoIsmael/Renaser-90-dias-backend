package com.renaser.os.calendar.domain.model.meet.transcripcion;

import com.renaser.os.calendar.domain.model.evento.EventoId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una sala de Meet (evento + fecha) cuya sesión ya terminó y todavía no tiene transcripción resuelta (spec 047, FR-002).
 *
 * @param salaId                  la fila de {@code eventos_meet}
 * @param inicio                  cuándo empieza la sesión de esa fecha
 * @param fin                     cuándo termina (inicio + duración; sin duración, 60 minutos)
 * @param transcripcionConfirmada lo que Google confirmó al crear la sala (spec 040, FR-036): {@code false} = la cuenta no dejó transcribir
 * @param actual                  lo que ya se sabía de su transcripción, o {@code null} si es la primera vez
 */
public record PendienteDeTranscripcion(UUID salaId, EventoId eventoId, LocalDate fecha, String enlace, Instant inicio, Instant fin,
                                       Boolean transcripcionConfirmada, TranscripcionDeSesion actual) {

    /** Se buscan las conferencias que empiezan desde media hora antes hasta tres horas después del fin agendado. */
    public static final Duration ANTES_DEL_INICIO = Duration.ofMinutes(30);
    public static final Duration DESPUES_DEL_FIN = Duration.ofHours(3);

    public Instant desde() {
        return inicio.minus(ANTES_DEL_INICIO);
    }

    public Instant hasta() {
        return fin.plus(DESPUES_DEL_FIN);
    }
}
