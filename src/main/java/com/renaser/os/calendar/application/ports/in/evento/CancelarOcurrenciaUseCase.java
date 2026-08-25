package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface CancelarOcurrenciaUseCase {

    /** cancelOccurrence() del repo viejo: el admin cancela UNA fecha de una serie recurrente
     * (nunca un evento suelto — para eso esta {@link EliminarEventoUseCase}). */
    void cancelar(UserId actorId, EventoId eventoId, Instant inicioOcurrencia);
}
