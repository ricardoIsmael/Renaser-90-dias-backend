package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

public interface ObtenerEventoUseCase {

    EventoVista obtener(UserId actorId, EventoId eventoId);
}
