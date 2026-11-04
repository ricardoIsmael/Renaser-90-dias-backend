package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

public interface ReintentarSalaDeMeetUseCase {

    /**
     * «Reintentar» a mano (FR-008): vuelve a pedirle a Google lo que quedó sin hacer, sin esperar el turno del reintento automático. Si Google ya
     * había creado la sala sin que Renaser lo supiera, no queda duplicada (FR-007). Solo ADMIN y ALCHEMIST.
     */
    EventoConMeetVista reintentar(UserId actor, EventoId eventoId);
}
