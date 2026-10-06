package com.renaser.os.calendar.application.ports.out.asistencia;

import com.renaser.os.calendar.domain.model.asistencia.CierreDeLista;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lo que se marcó al pasar lista de una ocurrencia, y si la lista está cerrada (V93, D-256). */
public interface LoadListaDeAsistenciaPort {

    List<MarcaDeAsistencia> marcas(EventoId eventoId, Instant inicioOcurrencia);

    Optional<MarcaDeAsistencia> marcaDe(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId);

    /** Vacío = la lista está abierta. */
    Optional<CierreDeLista> cierre(EventoId eventoId, Instant inicioOcurrencia);
}
