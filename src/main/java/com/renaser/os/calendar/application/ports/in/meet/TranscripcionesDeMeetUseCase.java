package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** La transcripción de las sesiones por Meet (spec 047). */
public interface TranscripcionesDeMeetUseCase {

    /** El reloj: busca las transcripciones de las sesiones que terminaron (FR-002). Devuelve cuántas salas revisó. */
    int buscarPendientes(Instant ahora);

    /** El reloj diario: borra lo vencido y lo de salas borradas (FR-014, FR-015) y purga la constancia de lecturas de más de 12 meses. */
    int aplicarRetencion(Instant ahora);

    /** El estado de cada fecha del evento, sin texto (FR-012). Solo ADMIN y ALCHEMIST. */
    List<EstadoDeLaFecha> estados(UserId actor, EventoId eventoId);

    /** Sacar el texto de una fecha (P1-A): solo ADMIN y ALCHEMIST, con motivo, y queda constancia (FR-011, FR-013). */
    TranscripcionDescargada descargar(UserId actor, EventoId eventoId, LocalDate fecha, String motivo);

    /**
     * Borrar a pedido la transcripción de una fecha (FR-016: derecho de cancelación de la Ley 29733, o lo que Administración decida), con motivo y
     * constancia. Solo ADMIN y ALCHEMIST.
     */
    void borrarAPedido(UserId actor, EventoId eventoId, LocalDate fecha, String motivo);

    /** El estado de una fecha, sin texto (FR-012). */
    record EstadoDeLaFecha(LocalDate fecha, String estado, String motivo, Instant obtenidaEn, Instant borrarDespuesDe, boolean grabacion) {
    }

    record TranscripcionDescargada(LocalDate fecha, String texto, String enlaceDeLaGrabacion, boolean recortada) {

        @Override
        public String toString() {
            return "TranscripcionDescargada[" + fecha + "]";
        }
    }
}
