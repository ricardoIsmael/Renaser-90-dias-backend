package com.renaser.os.calendar.application.ports.out.evento;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;

public interface SaveEventoPort {

    /** Upsert del agregado completo: fila {@code eventos} + reemplazo de sus tablas hijas
     * (recurrencia, dias_semana, roles_destino, reglas_recordatorio). */
    Evento guardar(Evento evento);

    /** Borra el evento y sus tablas hijas (FK ON DELETE CASCADE) — excepciones,
     * confirmaciones y recordatorios de este evento tambien caen. */
    void eliminar(EventoId id);

    /**
     * Pone (o quita, con {@code null}) el enlace de Meet que creó Google en un evento que lo pidió (spec 040, FR-003), sin reescribir el resto del
     * evento: lo hace el proceso que habla con Google, que no sabe nada del formulario.
     */
    void fijarEnlaceDeMeet(EventoId id, String enlace);
}
