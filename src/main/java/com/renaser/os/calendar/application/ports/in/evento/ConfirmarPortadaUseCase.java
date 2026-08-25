package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

public interface ConfirmarPortadaUseCase {

    EventoVista confirmar(UserId actorId, EventoId eventoId, String ruta);
}
