package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface CancelarOcurrenciaUseCase {

    /** cancelOccurrence() del repo viejo: el admin cancela UNA fecha de una serie recurrente
     * (nunca un evento suelto — para eso esta {@link EliminarEventoUseCase}). */
    void cancelar(UserId actorId, EventoId eventoId, Instant inicioOcurrencia);

    /** Spec 053 FR-014: cancela esta ocurrencia y todas las siguientes (la serie termina antes de ella); las pasadas quedan igual. */
    void cancelarDesde(UserId actorId, EventoId eventoId, Instant inicioOcurrencia);
}
