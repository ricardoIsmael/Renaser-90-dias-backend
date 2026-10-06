package com.renaser.os.calendar.application.ports.out.asistencia;

import com.renaser.os.calendar.domain.model.asistencia.CierreDeLista;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

public interface SaveListaDeAsistenciaPort {

    /** Crea o reemplaza la marca de esa persona en esa ocurrencia. */
    void guardar(MarcaDeAsistencia marca);

    /** Ausente = sin fila. Quitar una marca que no existe no hace nada. */
    void quitar(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId);

    void cerrar(EventoId eventoId, Instant inicioOcurrencia, CierreDeLista cierre);

    /** Reabrir una lista abierta no hace nada. */
    void reabrir(EventoId eventoId, Instant inicioOcurrencia);
}
