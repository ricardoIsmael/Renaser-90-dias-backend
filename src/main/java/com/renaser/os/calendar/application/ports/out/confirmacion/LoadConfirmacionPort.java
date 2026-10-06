package com.renaser.os.calendar.application.ports.out.confirmacion;

import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface LoadConfirmacionPort {

    /** Clave {@code eventoId|inicioOcurrencia} — mismo formato que findRsvpsForViewer() del repo viejo. */
    Map<String, EstadoConfirmacion> paraVisor(UserId usuarioId, Set<EventoId> eventoIds);

    /** Claves {@code inicioOcurrencia|usuarioId} de quienes ya confirmaron ASISTE — yaConfirmaron() del repo viejo. */
    Set<String> confirmadosAsistencia(EventoId eventoId, List<Instant> ocurrencias);

    /** La respuesta vigente de UNA persona a una ocurrencia (D-256: el historial se escribe solo si cambia). */
    Optional<EstadoConfirmacion> estadoDe(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId);

    /** Todas las respuestas vigentes a una ocurrencia, de cualquier estado (hoja «Quién respondió», D-256). */
    List<Confirmacion> deOcurrencia(EventoId eventoId, Instant inicioOcurrencia);
}
