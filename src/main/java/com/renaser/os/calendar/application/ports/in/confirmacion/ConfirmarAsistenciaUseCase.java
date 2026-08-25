package com.renaser.os.calendar.application.ports.in.confirmacion;

import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface ConfirmarAsistenciaUseCase {

    /** setRsvp() del repo viejo. Con ASISTE apaga los recordatorios pendientes de ESTA
     * persona para ESTA ocurrencia (cancelarPorAsistencia). */
    void confirmar(UserId actorId, EventoId eventoId, Instant inicioOcurrencia, EstadoConfirmacion estado);
}
