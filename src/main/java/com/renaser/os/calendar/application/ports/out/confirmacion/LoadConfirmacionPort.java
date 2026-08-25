package com.renaser.os.calendar.application.ports.out.confirmacion;

import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LoadConfirmacionPort {

    /** Clave {@code eventoId|inicioOcurrencia} — mismo formato que findRsvpsForViewer() del repo viejo. */
    Map<String, EstadoConfirmacion> paraVisor(UserId usuarioId, Set<EventoId> eventoIds);

    /** Claves {@code inicioOcurrencia|usuarioId} de quienes ya confirmaron ASISTE — yaConfirmaron() del repo viejo. */
    Set<String> confirmadosAsistencia(EventoId eventoId, List<Instant> ocurrencias);
}
