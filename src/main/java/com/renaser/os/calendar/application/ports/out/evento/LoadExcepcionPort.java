package com.renaser.os.calendar.application.ports.out.evento;

import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.EventoId;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LoadExcepcionPort {

    List<Excepcion> porEvento(EventoId eventoId);

    Map<EventoId, List<Excepcion>> porEventos(Set<EventoId> eventoIds);
}
