package com.renaser.os.calendar.application.ports.in.asistencia;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Hoja «Quién respondió» de una ocurrencia (D-256). Solo quien creó el evento, Admin, Alquimista y Líder de
 * mentores; el resto, 403.
 */
public interface VerRespuestasDelEventoUseCase {

    RespuestasDelEvento respuestas(UserId actor, EventoId eventoId, Instant inicioOcurrencia);

    /** @param inicioOcurrencia el slot de la serie tal como lo guarda la base */
    record RespuestasDelEvento(Instant inicioOcurrencia, List<PersonaConvocada> personas) {
    }
}
