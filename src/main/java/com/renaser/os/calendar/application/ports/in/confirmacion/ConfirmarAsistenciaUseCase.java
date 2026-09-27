package com.renaser.os.calendar.application.ports.in.confirmacion;

import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface ConfirmarAsistenciaUseCase {

    /** setRsvp() del repo viejo. Solo guarda la respuesta: desde D-189 ya no apaga los
     * recordatorios (se decide al entregarlos, segun si la persona tiene un telefono registrado). */
    void confirmar(UserId actorId, EventoId eventoId, Instant inicioOcurrencia, EstadoConfirmacion estado);
}
