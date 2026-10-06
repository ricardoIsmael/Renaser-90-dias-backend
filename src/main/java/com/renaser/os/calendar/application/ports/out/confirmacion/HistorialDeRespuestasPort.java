package com.renaser.os.calendar.application.ports.out.confirmacion;

import com.renaser.os.calendar.domain.model.asistencia.RespuestaAnterior;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;

import java.time.Instant;
import java.util.List;

/**
 * Cada vez que alguien cambia su «Voy» / «No voy» (tabla {@code historial_confirmaciones_evento}, V93,
 * D-256). Append-only: no hay método para corregir ni borrar (lo borra la base al borrar el evento o la
 * cuenta).
 */
public interface HistorialDeRespuestasPort {

    /** Agrega la respuesta con su {@code actualizadoEn} como instante. */
    void registrar(Confirmacion respuesta);

    /** Todo el historial de la ocurrencia, de la más vieja a la más nueva. */
    List<RespuestaAnterior> deOcurrencia(EventoId eventoId, Instant inicioOcurrencia);
}
