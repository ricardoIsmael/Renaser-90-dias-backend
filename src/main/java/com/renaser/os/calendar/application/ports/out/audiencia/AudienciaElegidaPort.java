package com.renaser.os.calendar.application.ports.out.audiencia;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;

import java.util.Collection;
import java.util.Map;

/** Las partes y la lista fija de la audiencia elegida de cada evento (spec 044, V183). */
public interface AudienciaElegidaPort {

    /** La audiencia guardada; vacía si el evento no tiene ninguna. */
    AudienciaElegida de(EventoId evento);

    /** En lote, para listar sin una consulta por evento. Un evento sin audiencia guardada no aparece en el mapa. */
    Map<EventoId, AudienciaElegida> deEventos(Collection<EventoId> eventos);

    /** Reemplaza la audiencia guardada del evento por esta (en la transacción de quien llama). */
    void reemplazar(EventoId evento, AudienciaElegida audiencia);
}
